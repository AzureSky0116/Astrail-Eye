package dev.astrail.eye.ui.component;

import java.util.HashMap;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Immediate-mode drawing primitives used by the HUD card and the settings screen.
 *
 * <p>Edges are resolved with sub-pixel coverage instead of per-pixel steps: the
 * design canvas is resampled to the physical screen at a non-integer ratio, so
 * a 1px-cell staircase would alias into visible pixels. Corners and strokes are
 * supersampled at {@value #AA_SAMPLES}x{@value #AA_SAMPLES} per pixel so the
 * resampled edges stay smooth at every GUI scale.
 */
public final class UiDraw {
    private static final int AA_SAMPLES = 4;

    /**
     * Farthest supersample from its pixel centre: the diagonal corner sits at
     * 0.375 px on each axis, {@code 0.375 * sqrt(2)} px away.
     */
    private static final double MAX_SUBSAMPLE_OFFSET = 0.375D * Math.sqrt(2.0D);

    /**
     * Corner coverage tables keyed by radius, row-major over the r x r corner.
     * All UI runs on Minecraft's render thread, so this cache needs no
     * synchronization and grows without eviction.
     */
    private static final HashMap<Integer, float[]> CORNER_COVERAGE_CACHE = new HashMap<>();

    /**
     * Stroke coverage masks keyed by rect geometry, radius and stroke width in
     * tenths of a design pixel. Render-thread only, same as the corner cache.
     */
    private static final HashMap<StrokeMaskKey, byte[]> STROKE_MASK_CACHE = new HashMap<>();

    /** Geometry of a precomputed stroke mask; the width is rounded to 0.1 px. */
    private record StrokeMaskKey(int x, int y, int width, int height, int radius, int strokeTenths) {
    }

    private UiDraw() {
    }

    /** Fill calls issued this frame by the primitives, for the debug counter. */
    private static int frameFillCount;

    /** Resets the per-frame primitive fill counter. Render thread only. */
    public static void beginFrame() {
        frameFillCount = 0;
    }

    /** Returns the number of primitive fills issued since {@link #beginFrame()}. */
    public static int endFrame() {
        return frameFillCount;
    }

    /** Funnel for every primitive fill so the debug counter stays exact. */
    private static void emit(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color) {
        frameFillCount++;
        graphics.fill(x0, y0, x1, y1, color);
    }

    public static void shadow(GuiGraphicsExtractor graphics, UiRect rect, int radius) {
        // Three faint, concentric layers read as a soft falloff. They stay
        // horizontally centred on the card: the old +2px x offset pushed the
        // shadow out from under one edge only, which showed up as a hard
        // asymmetric rim rather than a shadow.
        fillRounded(graphics, rect.expand(6).translate(0, 5), radius + 6, 0x14000000);
        fillRounded(graphics, rect.expand(3).translate(0, 4), radius + 3, 0x22000000);
        fillRounded(graphics, rect.expand(1).translate(0, 2), radius + 1, 0x38000000);
    }

    /**
     * Draws a panel surface with an optional 1.5px anti-aliased border ring.
     *
     * <p>The ring straddles the fill edge instead of being painted underneath
     * it, so both sides of the stroke get the same sub-pixel coverage and the
     * edge reads as a clean outline at any scale.
     */
    public static void card(GuiGraphicsExtractor graphics, UiRect rect, int radius, int fill, int border) {
        shadow(graphics, rect, radius);
        fillRounded(graphics, rect, radius, fill);
        if ((border >>> 24) != 0 && rect.width() > 2 && rect.height() > 2) {
            fillStrokeRounded(graphics, rect, radius, 1.5F, border);
        }
    }

    /**
     * Antialiased rounded rectangle.
     *
     * <p>The straight middle band is painted at full alpha; every corner cell
     * is resolved by sub-pixel coverage with no solid/partial step, so the arc
     * stays smooth even when the design canvas is upscaled onto the physical
     * pixel grid.
     */
    public static void fillRounded(GuiGraphicsExtractor graphics, UiRect rect, int radius, int color) {
        if (rect.width() <= 0 || rect.height() <= 0) return;
        color = UiMotion.applyFrameOpacity(color);
        if ((color >>> 24) == 0) return;
        int r = Math.max(0, Math.min(radius, Math.min(rect.width(), rect.height()) / 2));
        if (r == 0) {
            graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), color);
            return;
        }
        graphics.fill(rect.x(), rect.y() + r, rect.right(), rect.bottom() - r, color);
        float[] corners = cornerCoverage(r);
        for (int row = 0; row < r; row++) {
            int topY = rect.y() + row;
            int bottomY = rect.bottom() - row - 1;
            if (rect.width() - 2 * r > 0) {
                graphics.fill(rect.x() + r, topY, rect.right() - r, topY + 1, color);
                graphics.fill(rect.x() + r, bottomY, rect.right() - r, bottomY + 1, color);
            }
            int rowBase = row * r;
            int runStart = -1;
            int runAlpha = 0;
            for (int column = 0; column <= r; column++) {
                int shaded = 0;
                if (column < r) {
                    float coverage = corners[rowBase + column];
                    if (coverage > 0.0F) {
                        shaded = coverageColor(color, coverage);
                        if ((shaded >>> 24) == 0) shaded = 0;
                    }
                }
                if (shaded == runAlpha && runStart >= 0) continue;
                if (runStart >= 0) {
                    emitLeftRightRuns(graphics, rect, topY, bottomY, runStart, column, runAlpha);
                }
                runStart = shaded == 0 ? -1 : column;
                runAlpha = shaded;
            }
        }
    }

    /** Emits one merged horizontal run mirrored on both sides of a corner row. */
    private static void emitLeftRightRuns(
        GuiGraphicsExtractor graphics,
        UiRect rect,
        int topY,
        int bottomY,
        int runStart,
        int runEnd,
        int shaded
    ) {
        if (runEnd <= runStart) return;
        int left = rect.x() + runStart;
        int right = rect.right() - runEnd;
        int leftEnd = rect.x() + runEnd;
        int rightEnd = rect.right() - runStart;
        emit(graphics, left, topY, leftEnd, topY + 1, shaded);
        emit(graphics, right, topY, rightEnd, topY + 1, shaded);
        emit(graphics, left, bottomY, leftEnd, bottomY + 1, shaded);
        emit(graphics, right, bottomY, rightEnd, bottomY + 1, shaded);
    }

    /**
     * Antialiased filled disc with a sub-pixel centre and radius. Every pixel
     * is resolved by sub-pixel coverage with no solid/partial step, so small
     * discs like the toggle knob keep a clean edge at any GUI scale.
     */
    public static void circle(GuiGraphicsExtractor graphics, float centerX, float centerY, float radius, int color) {
        color = UiMotion.applyFrameOpacity(color);
        if (radius <= 0.0F || (color >>> 24) == 0) return;
        float outer = radius + 0.5F;
        int top = (int) Math.floor(centerY - outer);
        int bottom = (int) Math.ceil(centerY + outer);
        double innerSquared = (radius - MAX_SUBSAMPLE_OFFSET) * (radius - MAX_SUBSAMPLE_OFFSET);
        double outerSquared = (radius + MAX_SUBSAMPLE_OFFSET) * (radius + MAX_SUBSAMPLE_OFFSET);
        for (int y = top; y < bottom; y++) {
            double dy = y + 0.5D - centerY;
            double halfChord = halfChord(radius + 0.5D, dy);
            if (halfChord <= 0.0D) continue;
            int from = (int) Math.floor(centerX - halfChord);
            int to = (int) Math.ceil(centerX + halfChord);
            int interiorFrom = to;
            int interiorTo = from;
            double innerSq = innerSquared - dy * dy;
            if (innerSq > 0.0D) {
                double innerHalf = Math.sqrt(innerSq);
                interiorFrom = (int) Math.ceil(centerX - 0.5D - innerHalf);
                interiorTo = (int) Math.floor(centerX - 0.5D + innerHalf) + 1;
                if (interiorFrom < interiorTo) {
                    emit(graphics, interiorFrom, y, interiorTo, y + 1, color);
                }
            }
            for (int x = from; x < interiorFrom; x++) {
                double dx = x + 0.5D - centerX;
                double distanceSquared = dx * dx + dy * dy;
                if (distanceSquared >= outerSquared) continue;
                int shaded = coverageColor(color, discPixelCoverage(centerX, centerY, radius, x, y));
                if ((shaded >>> 24) == 0) continue;
                emit(graphics, x, y, x + 1, y + 1, shaded);
            }
            for (int x = interiorTo; x < to; x++) {
                double dx = x + 0.5D - centerX;
                double distanceSquared = dx * dx + dy * dy;
                if (distanceSquared >= outerSquared) continue;
                int shaded = coverageColor(color, discPixelCoverage(centerX, centerY, radius, x, y));
                if ((shaded >>> 24) == 0) continue;
                emit(graphics, x, y, x + 1, y + 1, shaded);
            }
        }
    }

    /**
     * Anti-aliased rounded-rect ring of {@code strokeWidth} design pixels,
     * centred on the rect's outline.
     *
     * <p>The ring is resolved from the signed distance to the rounded outline
     * with per-pixel supersampling, so a 1.5px stroke stays visible and smooth
     * instead of collapsing into a flickering hairline at sub-pixel scale.
     *
     * <p>Emission merges pixels along the stroke direction: the coverage of a
     * straight edge depends only on the perpendicular coordinate, so every
     * straight run collapses into one wide fill instead of one per pixel. Only
     * the corner arcs keep per-pixel cells. This keeps the per-frame element
     * count proportional to the outline length instead of its area, which is
     * what allows the GUI render state to stay cheap at 60fps.
     */
    public static void fillStrokeRounded(GuiGraphicsExtractor graphics, UiRect rect, int radius, float strokeWidth, int color) {
        color = UiMotion.applyFrameOpacity(color);
        if ((color >>> 24) == 0 || strokeWidth <= 0.0F || rect.width() <= 0 || rect.height() <= 0) return;
        byte[] mask = strokeMask(rect, radius, strokeWidth);
        float r = Math.min(radius, Math.min(rect.width(), rect.height()) / 2.0F);
        float halfW = rect.width() * 0.5F - r;
        float halfH = rect.height() * 0.5F - r;
        float centerX = (rect.x() + rect.right()) * 0.5F;
        float centerY = (rect.y() + rect.bottom()) * 0.5F;
        float halfStroke = strokeWidth * 0.5F;
        float band = halfStroke + 0.8F;
        int alpha = (color >>> 24) & 0xFF;
        int y0 = Math.max(rect.y(), (int) Math.ceil(centerY - halfH - r - band - 0.5F));
        int y1 = Math.min(rect.bottom(), (int) Math.floor(centerY + halfH + r + band - 0.5F) + 1);
        int midTop = Math.max(rect.y(), (int) Math.ceil(centerY - halfH - band));
        int midBottom = Math.min(rect.bottom(), (int) Math.floor(centerY + halfH + band) + 1);
        int uniformTop = Math.max(midTop, (int) Math.ceil(centerY - halfH));
        int uniformBottom = Math.min(midBottom, (int) Math.floor(centerY + halfH) + 1);
        float inner = halfW - band;
        float outer = halfW + r + band;
        boolean useColumns = halfH >= band && uniformTop < uniformBottom;
        if (useColumns) {
            emitStrokeColumns(graphics, rect, mask, uniformTop, uniformBottom,
                Math.max(rect.x(), (int) Math.ceil(centerX - outer - 0.5F)),
                Math.min(rect.right(), (int) Math.floor(centerX - inner - 0.5F) + 1), color, alpha);
            emitStrokeColumns(graphics, rect, mask, uniformTop, uniformBottom,
                Math.max(rect.x(), (int) Math.ceil(centerX + inner - 0.5F)),
                Math.min(rect.right(), (int) Math.floor(centerX + outer - 0.5F) + 1), color, alpha);
            for (int y = midTop; y < uniformTop; y++) {
                emitStrokeRow(graphics, rect, mask, y,
                    Math.max(rect.x(), (int) Math.ceil(centerX - outer - 0.5F)),
                    Math.min(rect.right(), (int) Math.floor(centerX - inner - 0.5F) + 1), color, alpha);
                emitStrokeRow(graphics, rect, mask, y,
                    Math.max(rect.x(), (int) Math.ceil(centerX + inner - 0.5F)),
                    Math.min(rect.right(), (int) Math.floor(centerX + outer - 0.5F) + 1), color, alpha);
            }
            for (int y = uniformBottom; y < midBottom; y++) {
                emitStrokeRow(graphics, rect, mask, y,
                    Math.max(rect.x(), (int) Math.ceil(centerX - outer - 0.5F)),
                    Math.min(rect.right(), (int) Math.floor(centerX - inner - 0.5F) + 1), color, alpha);
                emitStrokeRow(graphics, rect, mask, y,
                    Math.max(rect.x(), (int) Math.ceil(centerX + inner - 0.5F)),
                    Math.min(rect.right(), (int) Math.floor(centerX + outer - 0.5F) + 1), color, alpha);
            }
        } else {
            for (int y = midTop; y < midBottom; y++) {
                emitStrokeRow(graphics, rect, mask, y,
                    Math.max(rect.x(), (int) Math.ceil(centerX - outer - 0.5F)),
                    Math.min(rect.right(), (int) Math.floor(centerX - inner - 0.5F) + 1), color, alpha);
                emitStrokeRow(graphics, rect, mask, y,
                    Math.max(rect.x(), (int) Math.ceil(centerX + inner - 0.5F)),
                    Math.min(rect.right(), (int) Math.floor(centerX + outer - 0.5F) + 1), color, alpha);
            }
        }
        for (int y = y0; y < midTop; y++) {
            emitStrokeRow(graphics, rect, mask, y, rect.x(), rect.right(), color, alpha);
        }
        for (int y = midBottom; y < y1; y++) {
            emitStrokeRow(graphics, rect, mask, y, rect.x(), rect.right(), color, alpha);
        }
    }

    /**
     * Emits the straight side bands as vertical runs. Coverage depends only on
     * the distance to the side edge for rows strictly inside the straight
     * span, so every column carries the same mask value from top to bottom;
     * adjacent equal-alpha columns collapse into one tall fill.
     */
    private static void emitStrokeColumns(
        GuiGraphicsExtractor graphics,
        UiRect rect,
        byte[] mask,
        int yTop,
        int yBottom,
        int x0,
        int x1,
        int color,
        int alpha
    ) {
        int rowBase = (yTop - rect.y()) * rect.width();
        int runStart = -1;
        int runAlpha = 0;
        for (int x = x0; x < x1; x++) {
            int coverage = mask[rowBase + (x - rect.x())] & 0xFF;
            int shaded = coverage == 0 ? 0 : Math.round(alpha * coverage / 255.0F);
            if (shaded != runAlpha) {
                if (runStart >= 0) {
                    emit(graphics, runStart, yTop, x, yBottom, color & 0x00FFFFFF | runAlpha << 24);
                }
                runStart = shaded == 0 ? -1 : x;
                runAlpha = shaded;
            }
        }
        if (runStart >= 0) {
            emit(graphics, runStart, yTop, x1, yBottom, color & 0x00FFFFFF | runAlpha << 24);
        }
    }

    /**
     * Emits one horizontal span of the ring, merging adjacent pixels with equal
     * alpha into a single wide fill. Cap rows carry the top and bottom edges
     * (uniform along x) and the corner arcs (per-pixel), and thin shapes fall
     * back to this path for their short side bands.
     */
    private static void emitStrokeRow(
        GuiGraphicsExtractor graphics,
        UiRect rect,
        byte[] mask,
        int y,
        int x0,
        int x1,
        int color,
        int alpha
    ) {
        int rowBase = (y - rect.y()) * rect.width();
        int runStart = -1;
        int runAlpha = 0;
        for (int x = x0; x < x1; x++) {
            int coverage = mask[rowBase + (x - rect.x())] & 0xFF;
            int shaded = coverage == 0 ? 0 : Math.round(alpha * coverage / 255.0F);
            if (shaded != runAlpha) {
                if (runStart >= 0) {
                    emit(graphics, runStart, y, x, y + 1, color & 0x00FFFFFF | runAlpha << 24);
                }
                runStart = shaded == 0 ? -1 : x;
                runAlpha = shaded;
            }
        }
        if (runStart >= 0) {
            emit(graphics, runStart, y, x1, y + 1, color & 0x00FFFFFF | runAlpha << 24);
        }
    }

    private static byte[] strokeMask(UiRect rect, int radius, float strokeWidth) {
        int strokeTenths = Math.round(strokeWidth * 10.0F);
        return STROKE_MASK_CACHE.computeIfAbsent(
            new StrokeMaskKey(rect.x(), rect.y(), rect.width(), rect.height(), radius, strokeTenths),
            UiDraw::computeStrokeMask
        );
    }

    /** Renders the ring once into a dense 0-255 coverage mask over the rect. */
    private static byte[] computeStrokeMask(StrokeMaskKey key) {
        byte[] mask = new byte[key.width * key.height];
        UiRect rect = new UiRect(key.x, key.y, key.width, key.height);
        float strokeWidth = key.strokeTenths / 10.0F;
        float r = Math.min(key.radius, Math.min(rect.width(), rect.height()) / 2.0F);
        float halfW = rect.width() * 0.5F - r;
        float halfH = rect.height() * 0.5F - r;
        float centerX = (rect.x() + rect.right()) * 0.5F;
        float centerY = (rect.y() + rect.bottom()) * 0.5F;
        float halfStroke = strokeWidth * 0.5F;
        float band = halfStroke + 0.8F;
        int y0 = Math.max(rect.y(), (int) Math.ceil(centerY - halfH - r - band - 0.5F));
        int y1 = Math.min(rect.bottom(), (int) Math.floor(centerY + halfH + r + band - 0.5F) + 1);
        for (int y = y0; y < y1; y++) {
            float dy = y + 0.5F - centerY;
            if (Math.abs(dy) <= halfH + band) {
                float inner = halfW - band;
                float outer = halfW + r + band;
                maskStrokeBand(mask, rect, centerX, centerY, y,
                    centerX - outer, centerX - inner, r, halfStroke);
                maskStrokeBand(mask, rect, centerX, centerY, y,
                    centerX + inner, centerX + outer, r, halfStroke);
            } else {
                maskStrokeBand(mask, rect, centerX, centerY, y,
                    rect.x(), rect.right(), r, halfStroke);
            }
        }
        return mask;
    }

    /** Evaluates the ring for one horizontal run into the mask. */
    private static void maskStrokeBand(
        byte[] mask,
        UiRect rect,
        float centerX,
        float centerY,
        int y,
        float xFrom,
        float xTo,
        float r,
        float halfStroke
    ) {
        float halfW = rect.width() * 0.5F - r;
        float halfH = rect.height() * 0.5F - r;
        int x0 = Math.max(rect.x(), (int) Math.ceil(xFrom - 0.5F));
        int x1 = Math.min(rect.right(), (int) Math.floor(xTo - 0.5F) + 1);
        int rowBase = (y - rect.y()) * rect.width();
        for (int x = x0; x < x1; x++) {
            float dx = x + 0.5F - centerX;
            float dy = y + 0.5F - centerY;
            float band = Math.abs(sdfRoundedRect(dx, dy, halfW, halfH, r)) - halfStroke;
            if (band >= 0.8F) continue;
            float coverage = band <= -0.8F ? 1.0F : ringCoverage(dx, dy, halfW, halfH, r, halfStroke);
            if (coverage <= 0.02F) continue;
            mask[rowBase + (x - rect.x())] = (byte) Math.round(coverage * 255.0F);
        }
    }

    /** Coverage table for one corner of radius {@code r}, computed once. */
    private static float[] cornerCoverage(int r) {
        float[] table = CORNER_COVERAGE_CACHE.get(r);
        if (table == null) {
            table = new float[r * r];
            for (int row = 0; row < r; row++) {
                for (int column = 0; column < r; column++) {
                    table[row * r + column] = discPixelCoverage(r, r, r, column, row);
                }
            }
            CORNER_COVERAGE_CACHE.put(r, table);
        }
        return table;
    }

    /** Half the horizontal chord of a circle of {@code radius} at height {@code dy}. */
    private static double halfChord(double radius, double dy) {
        double squared = radius * radius - dy * dy;
        return squared <= 0.0D ? -1.0D : Math.sqrt(squared);
    }

    /** Fraction of the pixel centred at ({@code pixelX}, {@code pixelY}) inside the disc. */
    private static float discPixelCoverage(float centerX, float centerY, float radius, float pixelX, float pixelY) {
        int hits = 0;
        for (int sy = 0; sy < AA_SAMPLES; sy++) {
            float subY = pixelY + (sy + 0.5F) / AA_SAMPLES - 0.5F;
            for (int sx = 0; sx < AA_SAMPLES; sx++) {
                float subX = pixelX + (sx + 0.5F) / AA_SAMPLES - 0.5F;
                float dx = subX - centerX;
                float dy = subY - centerY;
                if (dx * dx + dy * dy <= radius * radius) hits++;
            }
        }
        return hits / (float) (AA_SAMPLES * AA_SAMPLES);
    }

    /** Supersampled coverage of the rounded-outline band for one pixel. */
    private static float ringCoverage(float dx, float dy, float halfW, float halfH, float r, float halfStroke) {
        int hits = 0;
        for (int sy = 0; sy < AA_SAMPLES; sy++) {
            float subY = dy + (sy + 0.5F) / AA_SAMPLES - 0.5F;
            for (int sx = 0; sx < AA_SAMPLES; sx++) {
                float subX = dx + (sx + 0.5F) / AA_SAMPLES - 0.5F;
                if (Math.abs(sdfRoundedRect(subX, subY, halfW, halfH, r)) <= halfStroke) hits++;
            }
        }
        return hits / (float) (AA_SAMPLES * AA_SAMPLES);
    }

    /** Signed distance to the outline of an r-rounded rect of half extents. */
    private static float sdfRoundedRect(float dx, float dy, float halfW, float halfH, float r) {
        float qx = Math.abs(dx) - halfW;
        float qy = Math.abs(dy) - halfH;
        float ax = Math.max(qx, 0.0F);
        float ay = Math.max(qy, 0.0F);
        float outside = (float) Math.sqrt(ax * ax + ay * ay);
        float inside = Math.min(Math.max(qx, qy), 0.0F);
        return outside + inside - r;
    }

    private static int coverageColor(int color, float coverage) {
        int alpha = Math.round(((color >>> 24) & 0xFF) * coverage);
        return color & 0x00FFFFFF | alpha << 24;
    }

    public static int lerpColor(int from, int to, float amount) {
        float t = Math.max(0.0F, Math.min(1.0F, amount));
        int a = Math.round(((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t);
        int r = Math.round(((from >>> 16) & 0xFF) + (((to >>> 16) & 0xFF) - ((from >>> 16) & 0xFF)) * t);
        int g = Math.round(((from >>> 8) & 0xFF) + (((to >>> 8) & 0xFF) - ((from >>> 8) & 0xFF)) * t);
        int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return a << 24 | r << 16 | g << 8 | b;
    }
}

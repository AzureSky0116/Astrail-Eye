package dev.astrail.eye.ui.component;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Standalone Inter renderer backed by a 4096px, 4x antialiased glyph atlas. */
public final class TextRenderer {
    private static final Identifier ATLAS = Identifier.fromNamespaceAndPath(
        "astrail-eye", "textures/font/inter_ui_atlas.png"
    );
    private static final String METRICS_RESOURCE = "/assets/astrail-eye/ui/inter_ui_metrics.json";
    private static final float SHADOW_X = 0.65F;
    private static final float SHADOW_Y = 0.9F;
    private static final float SHADOW_ALPHA = 0.22F;

    private static final TextRenderer INSTANCE = load();

    private final int atlasWidth;
    private final int atlasHeight;
    private final float rasterScale;
    private final Map<String, CompiledStyle> styles;

    /**
     * Dense codepoint-to-slot table shared by every style.
     *
     * <p>The atlas covers 96 codepoints but they are not contiguous (ASCII plus
     * U+2026), so indexing per-style glyph and kerning tables by raw codepoint
     * would waste two orders of magnitude of memory each. One shared table maps
     * every codepoint the atlas knows to a compact slot, which lets each style
     * hold flat arrays and keeps a glyph lookup allocation free.
     */
    private final int[] codepointSlots;

    private TextRenderer(AtlasMetrics metrics) {
        atlasWidth = metrics.atlasWidth;
        atlasHeight = metrics.atlasHeight;
        rasterScale = metrics.rasterScale;

        int[] slots = buildSlotTable(metrics.styles);
        int slotCount = 0;
        for (int slot : slots) slotCount = Math.max(slotCount, slot + 1);
        Map<String, CompiledStyle> compiled = new HashMap<>(metrics.styles.size());
        for (Map.Entry<String, StyleMetrics> entry : metrics.styles.entrySet()) {
            compiled.put(entry.getKey(), CompiledStyle.compile(entry.getValue(), slots, slotCount));
        }
        codepointSlots = slots;
        styles = Map.copyOf(compiled);
    }

    private static int[] buildSlotTable(Map<String, StyleMetrics> styles) {
        TreeSet<Integer> codepoints = new TreeSet<>();
        for (StyleMetrics style : styles.values()) {
            if (style.glyphs == null) continue;
            for (String key : style.glyphs.keySet()) codepoints.add(Integer.parseInt(key));
        }
        if (codepoints.isEmpty()) throw new IllegalStateException("Inter atlas declares no glyphs");
        int[] slots = new int[codepoints.last() + 1];
        Arrays.fill(slots, -1);
        int next = 0;
        for (int codepoint : codepoints) slots[codepoint] = next++;
        return slots;
    }

    private int slot(int codepoint) {
        return codepoint >= 0 && codepoint < codepointSlots.length ? codepointSlots[codepoint] : -1;
    }

    public static TextRenderer instance() {
        return INSTANCE;
    }

    /** Draws text from a left edge and a visual line center. */
    public void drawLeftText(
        GuiGraphicsExtractor graphics,
        String text,
        float x,
        float centerY,
        UiTextStyle style,
        int color
    ) {
        color = UiMotion.applyFrameOpacity(color);
        if (text == null || text.isEmpty() || (color >>> 24) == 0) return;
        CompiledStyle metrics = metrics(style);
        float baseline = baselineForCenter(centerY, metrics);
        drawRun(graphics, text, x + SHADOW_X, baseline + SHADOW_Y, metrics, shadowColor(color));
        drawRun(graphics, text, x, baseline, metrics, color);
    }

    /** Draws text centered horizontally around x and vertically around centerY. */
    public void drawCenteredText(
        GuiGraphicsExtractor graphics,
        String text,
        float centerX,
        float centerY,
        UiTextStyle style,
        int color
    ) {
        drawLeftText(graphics, text, centerX - width(text, style) * 0.5F, centerY, style, color);
    }

    /** Draws text ending at rightX and vertically centered around centerY. */
    public void drawRightText(
        GuiGraphicsExtractor graphics,
        String text,
        float rightX,
        float centerY,
        UiTextStyle style,
        int color
    ) {
        drawLeftText(graphics, text, rightX - width(text, style), centerY, style, color);
    }

    public float width(String text, UiTextStyle style) {
        if (text == null || text.isEmpty()) return 0.0F;
        CompiledStyle metrics = metrics(style);
        float width = 0.0F;
        int previous = -1;
        for (int offset = 0; offset < text.length();) {
            int codepoint = text.codePointAt(offset);
            int slot = slot(codepoint);
            width += metrics.kerning(previous, slot);
            width += metrics.glyph(slot).advance();
            previous = slot;
            offset += Character.charCount(codepoint);
        }
        return width;
    }

    public String trim(String text, float maxWidth, UiTextStyle style) {
        if (text == null || text.isEmpty() || maxWidth <= 0.0F) return "";
        if (width(text, style) <= maxWidth) return text;
        String ellipsis = "\u2026";
        int end = text.length();
        while (end > 0) {
            end = text.offsetByCodePoints(end, -1);
            String candidate = text.substring(0, end).stripTrailing();
            if (width(candidate + ellipsis, style) <= maxWidth) return candidate + ellipsis;
        }
        return width(ellipsis, style) <= maxWidth ? ellipsis : "";
    }

    float ascent(UiTextStyle style) {
        return metrics(style).ascent;
    }

    float descent(UiTextStyle style) {
        return metrics(style).descent;
    }

    float lineHeight(UiTextStyle style) {
        return metrics(style).lineHeight;
    }

    private void drawRun(
        GuiGraphicsExtractor graphics,
        String text,
        float x,
        float baseline,
        CompiledStyle metrics,
        int color
    ) {
        float cursor = x;
        int previous = -1;
        for (int offset = 0; offset < text.length();) {
            int codepoint = text.codePointAt(offset);
            int slot = slot(codepoint);
            GlyphMetrics glyph = metrics.glyph(slot);
            cursor += metrics.kerning(previous, slot);
            if (glyph.width() > 0 && glyph.height() > 0) {
                graphics.pose().pushMatrix();
                graphics.pose().translate(cursor + glyph.bearingX(), baseline + glyph.bearingY());
                graphics.pose().scale(1.0F / rasterScale, 1.0F / rasterScale);
                graphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    ATLAS,
                    0,
                    0,
                    glyph.x(),
                    glyph.y(),
                    glyph.width(),
                    glyph.height(),
                    glyph.width(),
                    glyph.height(),
                    atlasWidth,
                    atlasHeight,
                    color
                );
                graphics.pose().popMatrix();
            }
            cursor += glyph.advance();
            previous = slot;
            offset += Character.charCount(codepoint);
        }
    }

    private CompiledStyle metrics(UiTextStyle style) {
        CompiledStyle metrics = styles.get(style.atlasName());
        if (metrics == null) throw new IllegalStateException("Missing Inter style: " + style.atlasName());
        return metrics;
    }

    private static float baselineForCenter(float centerY, CompiledStyle metrics) {
        return centerY + (metrics.ascent - metrics.descent) * 0.5F;
    }

    private static int shadowColor(int color) {
        int alpha = Math.round((color >>> 24) * SHADOW_ALPHA);
        return alpha << 24;
    }

    private static TextRenderer load() {
        try (InputStream input = TextRenderer.class.getResourceAsStream(METRICS_RESOURCE)) {
            if (input == null) throw new IOException("Missing " + METRICS_RESOURCE);
            AtlasMetrics metrics = new Gson().fromJson(
                new InputStreamReader(input, StandardCharsets.UTF_8), AtlasMetrics.class
            );
            if (metrics.atlasWidth < 2048 || metrics.atlasHeight < 2048 || metrics.rasterScale < 4) {
                throw new IOException("Inter atlas does not meet the high-resolution contract");
            }
            return new TextRenderer(metrics);
        } catch (IOException | RuntimeException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    private static final class AtlasMetrics {
        private int atlasWidth;
        private int atlasHeight;
        private float rasterScale;
        private Map<String, StyleMetrics> styles;
    }

    /** Raw shape of one style in {@code inter_ui_metrics.json}, used only at load. */
    private static final class StyleMetrics {
        private float ascent;
        private float descent;
        private float lineHeight;
        private Map<String, float[]> glyphs;
        private Map<String, Float> kerning;
    }

    /**
     * One style resolved into flat, slot-indexed tables.
     *
     * <p>Text is re-measured and redrawn from scratch every frame, and the glow
     * styles stamp the same run 25 times for the shadow rings and bloom. Resolving
     * a glyph through string keys would therefore allocate a boxed key, a
     * concatenated kerning key and a metrics record per glyph per pass; at that
     * multiplier the lookups, not the blits, dominate the draw. Everything is
     * built once here so drawing only reads arrays.
     */
    private static final class CompiledStyle {
        private static final int FALLBACK_CODEPOINT = '?';

        private final float ascent;
        private final float descent;
        private final float lineHeight;
        private final int slotCount;
        private final GlyphMetrics[] glyphs;
        private final float[] kerning;
        private final GlyphMetrics fallback;

        private CompiledStyle(
            float ascent,
            float descent,
            float lineHeight,
            int slotCount,
            GlyphMetrics[] glyphs,
            float[] kerning,
            GlyphMetrics fallback
        ) {
            this.ascent = ascent;
            this.descent = descent;
            this.lineHeight = lineHeight;
            this.slotCount = slotCount;
            this.glyphs = glyphs;
            this.kerning = kerning;
            this.fallback = fallback;
        }

        static CompiledStyle compile(StyleMetrics raw, int[] codepointSlots, int slotCount) {
            GlyphMetrics[] glyphs = new GlyphMetrics[slotCount];
            if (raw.glyphs != null) {
                for (Map.Entry<String, float[]> entry : raw.glyphs.entrySet()) {
                    int slot = codepointSlots[Integer.parseInt(entry.getKey())];
                    glyphs[slot] = toGlyph(entry.getValue());
                }
            }
            int fallbackSlot = FALLBACK_CODEPOINT < codepointSlots.length
                ? codepointSlots[FALLBACK_CODEPOINT]
                : -1;
            GlyphMetrics fallback = fallbackSlot < 0 ? null : glyphs[fallbackSlot];
            if (fallback == null) throw new IllegalStateException("Inter style has no fallback glyph");

            float[] kerning = new float[slotCount * slotCount];
            if (raw.kerning != null) {
                for (Map.Entry<String, Float> entry : raw.kerning.entrySet()) {
                    String key = entry.getKey();
                    int separator = key.indexOf(':');
                    if (separator < 0) throw new IllegalStateException("Invalid kerning key: " + key);
                    int left = slotOf(codepointSlots, key.substring(0, separator));
                    int right = slotOf(codepointSlots, key.substring(separator + 1));
                    if (left < 0 || right < 0) continue;
                    kerning[left * slotCount + right] = entry.getValue();
                }
            }
            return new CompiledStyle(
                raw.ascent, raw.descent, raw.lineHeight, slotCount, glyphs, kerning, fallback
            );
        }

        private static int slotOf(int[] codepointSlots, String codepoint) {
            int value = Integer.parseInt(codepoint);
            return value >= 0 && value < codepointSlots.length ? codepointSlots[value] : -1;
        }

        private static GlyphMetrics toGlyph(float[] values) {
            if (values == null || values.length != 7) throw new IllegalStateException("Invalid glyph metrics");
            return new GlyphMetrics(
                Math.round(values[0]), Math.round(values[1]), Math.round(values[2]), Math.round(values[3]),
                values[4], values[5], values[6]
            );
        }

        /** Glyph for a slot, falling back to {@code ?} for anything the atlas omits. */
        private GlyphMetrics glyph(int slot) {
            if (slot < 0 || slot >= slotCount) return fallback;
            GlyphMetrics glyph = glyphs[slot];
            return glyph == null ? fallback : glyph;
        }

        /** Kerning between two slots; zero when either side is absent or unpaired. */
        private float kerning(int left, int right) {
            if (left < 0 || right < 0 || left >= slotCount || right >= slotCount) return 0.0F;
            return kerning[left * slotCount + right];
        }
    }

    private record GlyphMetrics(
        int x,
        int y,
        int width,
        int height,
        float bearingX,
        float bearingY,
        float advance
    ) {}
}

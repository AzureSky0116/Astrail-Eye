package dev.astrail.eye.hud;

/**
 * Pure anchor and scale math for the top-left statistics card, ported from
 * the Astrail HudGeometry. The card draws in a fixed design space scaled by
 * {@code ELEMENT_SCALE x (window / 1920x1080 viewport fit) / guiScale}, so its
 * on-screen size tracks the physical window rather than the GUI scale setting.
 */
public final class HudGeometry {
    public static final int STATS_CARD_WIDTH = 270;
    public static final int STATS_CARD_HEIGHT = 54;
    public static final float ELEMENT_SCALE = 1.5F;
    public static final int STATS_LEFT_MARGIN_PHYSICAL = 16;
    public static final int STATS_TOP_MARGIN_PHYSICAL = 16;

    private static final float REFERENCE_VIEWPORT_WIDTH = 1_920.0F;
    private static final float REFERENCE_VIEWPORT_HEIGHT = 1_080.0F;

    /** The card's anchor point in GUI-scaled screen coordinates. */
    public record Anchor(float x, float y) {
    }

    private HudGeometry() {
    }

    /** Conversion from physical design pixels to GUI-scaled coordinates. */
    public static float physicalToScaled(float windowWidth, float windowHeight, float guiScale) {
        float viewportScale = Math.min(
            windowWidth / REFERENCE_VIEWPORT_WIDTH,
            windowHeight / REFERENCE_VIEWPORT_HEIGHT
        );
        return viewportScale / Math.max(1.0F, guiScale);
    }

    /** Scale applied to the card's design-space content. */
    public static float displayScale(float physicalToScaled) {
        return ELEMENT_SCALE * physicalToScaled;
    }

    /** The stats card's built-in top-left anchor. */
    public static Anchor statsDefaultAnchor(int screenWidth, int screenHeight, float physicalToScaled) {
        return new Anchor(
            STATS_LEFT_MARGIN_PHYSICAL * physicalToScaled,
            STATS_TOP_MARGIN_PHYSICAL * physicalToScaled
        );
    }

    /** Top-left corner of the compact stats card, clamped fully on screen. */
    public static Anchor statsAnchor(int screenWidth, int screenHeight, float physicalToScaled) {
        float scale = displayScale(physicalToScaled);
        Anchor fallback = statsDefaultAnchor(screenWidth, screenHeight, physicalToScaled);
        return new Anchor(
            clamp(fallback.x(), 0.0F, Math.max(0.0F, screenWidth - STATS_CARD_WIDTH * scale)),
            clamp(fallback.y(), 0.0F, Math.max(0.0F, screenHeight - STATS_CARD_HEIGHT * scale))
        );
    }

    private static float clamp(float value, float minimum, float maximum) {
        if (maximum < minimum) {
            return maximum;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }
}

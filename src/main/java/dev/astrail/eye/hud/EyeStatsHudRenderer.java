package dev.astrail.eye.hud;

import dev.astrail.eye.api.module.HudStatsProvider;
import dev.astrail.eye.api.module.ModuleState;
import dev.astrail.eye.core.module.ModuleManager;
import dev.astrail.eye.ui.component.TextRenderer;
import dev.astrail.eye.ui.component.UiDraw;
import dev.astrail.eye.ui.component.UiMotion;
import dev.astrail.eye.ui.component.UiRect;
import dev.astrail.eye.ui.component.UiTextStyle;
import dev.astrail.eye.ui.component.UiTheme;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Compact top-left statistics card for the enabled Auto Eye module. Ported
 * from the Astrail ModuleStatsHudRenderer; the card, anchor math, reveal and
 * text drawing are kept identical, with the draggable-layout editor removed.
 */
public final class EyeStatsHudRenderer {
    private static final TextRenderer UI_TEXT = TextRenderer.instance();
    private static final long REVEAL_MILLIS = 180L;
    private static final UiRect STATS_CARD = new UiRect(
        0, 0, HudGeometry.STATS_CARD_WIDTH, HudGeometry.STATS_CARD_HEIGHT
    );
    private static final UiRect FIRST_DIVIDER = new UiRect(90, 26, 1, 18);
    private static final UiRect SECOND_DIVIDER = new UiRect(180, 26, 1, 18);

    private final ModuleManager modules;
    private boolean suppressed = true;
    private long revealStartedAt;

    public EyeStatsHudRenderer(ModuleManager modules) {
        this.modules = modules;
    }

    public void register() {
        HudElementRegistry.attachElementAfter(
            VanillaHudElements.OVERLAY_MESSAGE,
            Identifier.fromNamespaceAndPath("astrail-eye", "eye_stats"),
            this::extractRenderState
        );
    }

    private void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        HudStatsProvider provider = activeProvider();
        if (client.player == null || provider == null) {
            suppressed = true;
            return;
        }
        long now = System.currentTimeMillis();
        if (suppressed) {
            suppressed = false;
            revealStartedAt = now;
        }
        float reveal = UiMotion.easeOutQuint((now - revealStartedAt) / (float) REVEAL_MILLIS);
        float physicalToScaled = HudGeometry.physicalToScaled(
            client.getWindow().getWidth(),
            client.getWindow().getHeight(),
            (float) client.getWindow().getGuiScale()
        );
        float scale = HudGeometry.displayScale(physicalToScaled);
        HudGeometry.Anchor anchor = HudGeometry.statsAnchor(
            client.getWindow().getGuiScaledWidth(),
            client.getWindow().getGuiScaledHeight(),
            physicalToScaled
        );
        graphics.pose().pushMatrix();
        graphics.pose().translate(anchor.x(), anchor.y());
        graphics.pose().scale(scale, scale);
        float previous = UiMotion.pushFrameOpacity(reveal);
        drawStats(
            graphics,
            provider.hudStatsTitle(),
            provider.hudStatCount() > 0 ? provider.hudStatLabel(0) : "-",
            provider.hudStatCount() > 0 ? provider.hudStatValue(0) : 0,
            provider.hudStatCount() > 1 ? provider.hudStatLabel(1) : "-",
            provider.hudStatCount() > 1 ? provider.hudStatValue(1) : 0,
            provider.hudStatCount() > 2 ? provider.hudStatLabel(2) : "-",
            provider.hudStatCount() > 2 ? provider.hudStatValue(2) : 0
        );
        UiMotion.popFrameOpacity(previous);
        graphics.pose().popMatrix();
    }

    private HudStatsProvider activeProvider() {
        for (var module : modules.all()) {
            if (module.state() == ModuleState.ENABLED && module instanceof HudStatsProvider provider) {
                return provider;
            }
        }
        return null;
    }

    private static void drawStats(
        GuiGraphicsExtractor graphics,
        String title,
        String firstLabel,
        int firstValue,
        String secondLabel,
        int secondValue,
        String thirdLabel,
        int thirdValue
    ) {
        UiDraw.fillRounded(graphics, STATS_CARD, 6, 0xE60B111A);
        UI_TEXT.drawLeftText(graphics, title, 12, 14, UiTextStyle.AUXILIARY_MEDIUM, UiTheme.TEXT_SOFT);
        UiDraw.fillRounded(graphics, FIRST_DIVIDER, 0, 0x58405265);
        UiDraw.fillRounded(graphics, SECOND_DIVIDER, 0, 0x58405265);
        drawStat(graphics, firstLabel, firstValue, 12, 86);
        drawStat(graphics, secondLabel, secondValue, 102, 176);
        drawStat(graphics, thirdLabel, thirdValue, 192, 258);
    }

    private static void drawStat(
        GuiGraphicsExtractor graphics,
        String label,
        int value,
        float left,
        float right
    ) {
        UI_TEXT.drawLeftText(graphics, label, left, 37, UiTextStyle.AUXILIARY, UiTheme.MUTED);
        UI_TEXT.drawRightText(
            graphics, Integer.toString(value), right, 37, UiTextStyle.AUXILIARY_SEMIBOLD, UiTheme.TEXT
        );
    }
}

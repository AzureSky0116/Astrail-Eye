package dev.astrail.eye.ui.screen;

import dev.astrail.eye.api.module.ModuleState;
import dev.astrail.eye.api.setting.BooleanSetting;
import dev.astrail.eye.api.setting.ConfirmActionSetting;
import dev.astrail.eye.api.setting.KeybindSetting;
import dev.astrail.eye.api.setting.NumberSetting;
import dev.astrail.eye.core.AstrailEyeRuntime;
import dev.astrail.eye.feature.macro.eye.AutoEyeModule;
import dev.astrail.eye.ui.component.TextRenderer;
import dev.astrail.eye.ui.component.UiDraw;
import dev.astrail.eye.ui.component.UiMotion;
import dev.astrail.eye.ui.component.UiRect;
import dev.astrail.eye.ui.component.UiTextStyle;
import dev.astrail.eye.ui.component.UiTheme;
import dev.astrail.eye.ui.layout.UiScaleSystem;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Original single-card settings screen for the Auto Eye module, opened with
 * Right Shift. Layout, controls and motion are authored from scratch; only the
 * shared Inter font atlas and drawing primitives come from the Astrail asset
 * set.
 */
public final class EyeSettingsScreen extends Screen {
    private static final TextRenderer UI_TEXT = TextRenderer.instance();
    private static final boolean REDUCE_MOTION = Boolean.getBoolean("astrail-eye.reduceMotion");

    /**
     * Frosted-background blur, opt-in with {@code -Dastrail-eye.blur=true}.
     *
     * <p>The panel fill is 95% opaque and sits on a dark full-screen overlay,
     * so the blurred world behind is essentially invisible, while the vanilla
     * blur pass compiles its shader on first use (a visible hitch when the
     * screen opens) and then re-blurs the whole frame every tick. Off by
     * default; the opening animation stays identical either way.
     */
    private static final boolean BLUR_BACKGROUND = Boolean.getBoolean("astrail-eye.blur");

    private static final int PANEL_X = 408;
    private static final int PANEL_Y = 242;
    private static final int PANEL_WIDTH = 720;
    private static final int PANEL_HEIGHT = 540;
    private static final int CONTENT_X = PANEL_X + 36;
    private static final int CONTENT_WIDTH = PANEL_WIDTH - 72;
    private static final int CONTROLS_RIGHT = CONTENT_X + CONTENT_WIDTH;
    private static final int HEADER_TITLE_Y = PANEL_Y + 44;
    private static final int HEADER_SUBTITLE_Y = PANEL_Y + 70;
    private static final int DIVIDER_Y = PANEL_Y + 96;
    private static final int ROWS_Y = DIVIDER_Y + 22;
    private static final int ROW_STEP = 78;
    private static final int CONTROL_H = 40;
    private static final int FOOTER_Y = PANEL_Y + PANEL_HEIGHT - 28;

    private static final UiRect PANEL = new UiRect(PANEL_X, PANEL_Y, PANEL_WIDTH, PANEL_HEIGHT);
    private static final UiRect STATUS_HIT = new UiRect(CONTROLS_RIGHT - 140, HEADER_TITLE_Y - 20, 140, CONTROL_H);
    private static final UiRect MODULE_HIT = new UiRect(CONTROLS_RIGHT - 140, ROWS_Y, 140, CONTROL_H);
    private static final UiRect KEYBIND_HIT = new UiRect(CONTROLS_RIGHT - 240, ROWS_Y + ROW_STEP, 240, CONTROL_H);
    private static final UiRect SNEAK_HIT = new UiRect(CONTROLS_RIGHT - 64, ROWS_Y + 2 * ROW_STEP, 64, CONTROL_H);
    private static final UiRect SLIDER_HIT = new UiRect(CONTROLS_RIGHT - 240, ROWS_Y + 3 * ROW_STEP + 8, 192, 24);
    private static final UiRect VALUE_HIT = new UiRect(CONTROLS_RIGHT - 44, ROWS_Y + 3 * ROW_STEP + 6, 44, 28);
    private static final UiRect RESET_HIT = new UiRect(CONTROLS_RIGHT - 140, ROWS_Y + 4 * ROW_STEP, 140, CONTROL_H);

    private final AstrailEyeRuntime runtime;
    private final Screen parent;
    private final AutoEyeModule module;
    private final BooleanSetting alwaysSneak;
    private final NumberSetting weaponSlot;
    private final ConfirmActionSetting resetCounter;
    private long openedAt = System.currentTimeMillis();
    private boolean closing;
    private boolean closed;
    private long closeStartedAt;
    private UiScaleSystem scaleSystem = UiScaleSystem.fit(1, 1);
    private int cachedViewportWidth = -1;
    private int cachedViewportHeight = -1;
    private boolean capturingKeybind;
    private NumberSetting draggingSlider;
    private long lastFrameNanos = System.nanoTime();
    private float sneakProgress;
    private int debugFrames;

    public EyeSettingsScreen(AstrailEyeRuntime runtime, Screen parent) {
        super(Component.literal("Astrail Eye"));
        this.runtime = runtime;
        this.parent = parent;
        this.module = runtime.eyeModule();
        this.alwaysSneak = settingOf(this.module, "always_sneak", BooleanSetting.class);
        this.weaponSlot = settingOf(this.module, "weapon_slot", NumberSetting.class);
        this.resetCounter = settingOf(this.module, "reset_counter", ConfirmActionSetting.class);
        this.sneakProgress = alwaysSneak.get() ? 1.0F : 0.0F;
    }

    private static <T> T settingOf(AutoEyeModule module, String id, Class<T> type) {
        for (var setting : module.settings()) {
            if (id.equals(setting.id()) && type.isInstance(setting)) return type.cast(setting);
        }
        throw new IllegalStateException("Missing setting: " + id);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        float visibility = closing ? closingProgress() : 1.0F;
        if (BLUR_BACKGROUND && !closing) graphics.blurBeforeThisStratum();
        graphics.fill(0, 0, width, height, UiMotion.multiplyAlpha(0x7203070D, visibility));
        graphics.fillGradient(
            0, 0, width, height,
            UiMotion.multiplyAlpha(0x74101B2D, visibility),
            UiMotion.multiplyAlpha(0xA004080F, visibility)
        );
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (width != cachedViewportWidth || height != cachedViewportHeight) {
            scaleSystem = UiScaleSystem.fit(width, height);
            cachedViewportWidth = width;
            cachedViewportHeight = height;
        }
        UiDraw.beginFrame();
        long extractStartNanos = System.nanoTime();
        int designMouseX = (int) Math.floor(scaleSystem.designX(mouseX));
        int designMouseY = (int) Math.floor(scaleSystem.designY(mouseY));
        updateMotion();
        if (closing && closingProgress() <= 0.0F) {
            finishClose();
            return;
        }
        scaleSystem.push(graphics);
        float presentation = openingProgress() * closingProgress();
        float previousFrameOpacity = UiMotion.pushFrameOpacity(closing ? closingProgress() : 1.0F);
        try {
            graphics.pose().translate(
                UiScaleSystem.DESIGN_WIDTH * 0.5F,
                UiScaleSystem.DESIGN_HEIGHT * 0.5F + (1.0F - presentation) * 12.0F
            );
            float entrance = REDUCE_MOTION ? 1.0F : 0.965F + 0.035F * presentation;
            graphics.pose().scale(entrance, entrance);
            graphics.pose().translate(-UiScaleSystem.DESIGN_WIDTH * 0.5F, -UiScaleSystem.DESIGN_HEIGHT * 0.5F);
            drawPanel(graphics, designMouseX, designMouseY);
        } finally {
            UiMotion.popFrameOpacity(previousFrameOpacity);
            scaleSystem.pop(graphics);
        }
        if (!closing && presentation < 1.0F) {
            int alpha = Math.round((1.0F - presentation) * 150.0F);
            graphics.fill(0, 0, width, height, alpha << 24);
        }
        if (++debugFrames % 60 == 0) {
            double extractMs = (System.nanoTime() - extractStartNanos) / 1_000_000.0;
            System.out.println("[AstrailEye] settings extract: " + String.format("%.2f", extractMs)
                + " ms, fills/frame: " + UiDraw.endFrame() + " (alwaysOn-instrumentation)");
        } else {
            UiDraw.endFrame();
        }
    }

    private void drawPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        UiDraw.card(graphics, PANEL, 16, 0xF20E141C, 0x46334954);
        UI_TEXT.drawLeftText(graphics, "Astrail Eye", CONTENT_X, HEADER_TITLE_Y, UiTextStyle.SETTINGS_TITLE, UiTheme.TEXT);
        UI_TEXT.drawLeftText(graphics, "Auto Eye - Stationary", CONTENT_X + 2, HEADER_SUBTITLE_Y, UiTextStyle.DESCRIPTION, UiTheme.MUTED);
        graphics.fill(CONTENT_X, DIVIDER_Y, CONTROLS_RIGHT, DIVIDER_Y + 1, 0x2A2F3B49);

        boolean enabled = module.state() == ModuleState.ENABLED;
        int statusColor = enabled ? UiTheme.SUCCESS : UiTheme.MUTED;
        String statusText = enabled ? "ACTIVE" : "IDLE";
        UiDraw.fillRounded(graphics, STATUS_HIT, 12, 0xC8121820);
        UiDraw.fillRounded(graphics, STATUS_HIT.inset(4), 8, UiMotion.multiplyAlpha(statusColor, 0.12F));
        UiDraw.fillStrokeRounded(graphics, STATUS_HIT, 12, 1.5F, UiMotion.multiplyAlpha(UiTheme.BORDER, 0.6F));
        drawSheen(graphics, STATUS_HIT, 12);
        UiDraw.circle(graphics, STATUS_HIT.x() + 18, STATUS_HIT.y() + 20, 4, statusColor);
        UI_TEXT.drawCenteredText(graphics, statusText, STATUS_HIT.x() + STATUS_HIT.width() * 0.5F + 10,
            STATUS_HIT.y() + 20, UiTextStyle.MENU_ACTIVE, statusColor);

        drawRowLabel(graphics, 0, "Module", "Start or stop farming in place");
        drawRowLabel(graphics, 1, "Toggle Keybind", "Bind a key to toggle the module in game");
        drawRowLabel(graphics, 2, "Always Sneak", "Hold sneak while standing still");
        drawRowLabel(graphics, 3, "Weapon Slot", "Hotbar slot with the attack weapon");
        drawRowLabel(graphics, 4, "Statistics", "");

        boolean moduleHover = MODULE_HIT.contains(mouseX, mouseY);
        boolean keybindHover = KEYBIND_HIT.contains(mouseX, mouseY);
        boolean sneakHover = SNEAK_HIT.contains(mouseX, mouseY);
        boolean sliderHover = SLIDER_HIT.contains(mouseX, mouseY);
        boolean resetHover = RESET_HIT.contains(mouseX, mouseY);
        drawPillButton(graphics, MODULE_HIT, enabled ? "Disable" : "Enable", moduleHover, enabled ? UiTheme.DANGER : UiTheme.SUCCESS);

        KeybindSetting keybind = module.keybind();
        boolean capturing = capturingKeybind;
        int keyFill = capturing ? UiTheme.BLUE_SURFACE : keybindHover ? UiTheme.CONTROL_HOVER : UiTheme.CONTROL;
        int keyBorder = capturing
            ? UiDraw.lerpColor(UiTheme.BLUE, UiTheme.BLUE_LIGHT,
                (float) (0.5D + 0.5D * Math.sin(System.currentTimeMillis() / 300.0D)))
            : UiTheme.BORDER;
        UiDraw.fillRounded(graphics, KEYBIND_HIT, 10, keyFill);
        UiDraw.fillStrokeRounded(graphics, KEYBIND_HIT, 10, 1.5F, capturing
            ? keyBorder
            : UiMotion.multiplyAlpha(UiTheme.BORDER, 0.75F));
        int keyTextColor = capturing ? UiTheme.TEXT
            : keybind.get() == KeybindSetting.UNBOUND ? UiTheme.FAINT : UiTheme.TEXT_SOFT;
        UI_TEXT.drawLeftText(graphics,
            capturing ? "Press Key" : keybind.displayValue(),
            KEYBIND_HIT.x() + 14, KEYBIND_HIT.y() + 20,
            UiTextStyle.AUXILIARY_SEMIBOLD,
            keyTextColor);

        drawSwitch(graphics, SNEAK_HIT, sneakProgress, sneakHover);

        NumberSetting slotSetting = weaponSlot;
        double progress = (slotSetting.get() - slotSetting.minimum())
            / Math.max(0.000001D, slotSetting.maximum() - slotSetting.minimum());
        drawSlider(graphics, SLIDER_HIT, (float) progress, sliderHover);
        UiDraw.fillRounded(graphics, VALUE_HIT, 10, 0xD5121820);
        UiDraw.fillRounded(graphics, VALUE_HIT.inset(1), 9, 0xE01A2129);
        UiDraw.fillStrokeRounded(graphics, VALUE_HIT, 10, 1.5F, UiMotion.multiplyAlpha(UiTheme.BORDER, 0.75F));
        UI_TEXT.drawCenteredText(graphics,
            Integer.toString(slotSetting.intValue()),
            VALUE_HIT.x() + VALUE_HIT.width() * 0.5F, VALUE_HIT.y() + VALUE_HIT.height() * 0.5F,
            UiTextStyle.AUXILIARY_SEMIBOLD, UiTheme.TEXT_SOFT);

        String stats = "Zealot " + module.hudStatValue(0)
            + "   Bruiser " + module.hudStatValue(1)
            + "   Eyes " + module.hudStatValue(2);
        UI_TEXT.drawLeftText(graphics, stats, CONTENT_X, ROWS_Y + 4 * ROW_STEP + 44, UiTextStyle.AUXILIARY_MEDIUM, UiTheme.TEXT_SOFT);
        drawPillButton(graphics, RESET_HIT, resetCounter.displayValue(), resetHover, resetCounter.armed() ? UiTheme.WARNING : UiTheme.MUTED);

        UI_TEXT.drawCenteredText(graphics, "Right Shift / Esc - Close", PANEL_X + PANEL_WIDTH / 2, FOOTER_Y, UiTextStyle.AUXILIARY, UiTheme.FAINT);
    }

    private void drawRowLabel(GuiGraphicsExtractor graphics, int row, String title, String description) {
        int centerY = ROWS_Y + row * ROW_STEP + 20;
        UI_TEXT.drawLeftText(graphics, title, CONTENT_X, centerY, UiTextStyle.SETTING_LABEL, UiTheme.TEXT_SOFT);
        UI_TEXT.drawLeftText(graphics, description, CONTENT_X, centerY + 24, UiTextStyle.DESCRIPTION, UiTheme.FAINT);
    }

    private void drawPillButton(GuiGraphicsExtractor graphics, UiRect rect, String label, boolean hover, int accent) {
        UiDraw.fillRounded(graphics, rect, 12, hover ? 0xE0273140 : 0xC8121820);
        UiDraw.fillRounded(graphics, rect.inset(4), 8, UiMotion.multiplyAlpha(accent, 0.16F));
        UiDraw.fillStrokeRounded(graphics, rect, 12, 1.5F,
            UiMotion.multiplyAlpha(hover ? UiTheme.BORDER_BRIGHT : UiTheme.BORDER, hover ? 0.85F : 0.6F));
        drawSheen(graphics, rect, 12);
        UI_TEXT.drawCenteredText(graphics, label, rect.x() + rect.width() * 0.5F, rect.y() + rect.height() * 0.5F,
            UiTextStyle.MENU_ACTIVE, hover ? UiTheme.TEXT : UiTheme.TEXT_SOFT);
    }

    private void drawSheen(GuiGraphicsExtractor graphics, UiRect rect, int radius) {
        int sheenHeight = Math.max(1, (rect.height() - 2) / 2 - 1);
        UiDraw.fillRounded(graphics,
            new UiRect(rect.x() + 1, rect.y() + 1, rect.width() - 2, sheenHeight),
            Math.max(0, Math.min(radius - 1, sheenHeight / 2)),
            UiMotion.multiplyAlpha(0xFFFFFF, 0.05F));
    }

    private void drawSwitch(GuiGraphicsExtractor graphics, UiRect rect, float progress, boolean hover) {
        UiRect track = new UiRect(rect.x(), rect.y() + 8, rect.width(), 24);
        float amount = UiMotion.clamp01(progress);
        UiDraw.fillRounded(graphics, track, 12, hover ? 0xE0202A35 : 0xD5121820);
        int accent = UiDraw.lerpColor(0x44556A80, 0x883B9BFF, amount);
        UiDraw.fillRounded(graphics, track, 12, UiMotion.multiplyAlpha(accent, 0.85F * amount));
        UiDraw.fillStrokeRounded(graphics, track, 12, 1.5F, UiMotion.multiplyAlpha(UiTheme.BORDER, 0.65F));
        float knobCenterX = rect.x() + 16.0F + amount * (rect.width() - 32.0F);
        float knobCenterY = rect.y() + 20;
        int knobColor = UiDraw.lerpColor(0xFF9AA5B2, 0xFFF2F7FC, amount);
        UiDraw.circle(graphics, knobCenterX, knobCenterY + 1.5F, 12, 0x45000000);
        UiDraw.circle(graphics, knobCenterX, knobCenterY, 12, knobColor);
    }

    private void drawSlider(GuiGraphicsExtractor graphics, UiRect rect, float progress, boolean hover) {
        UiRect track = new UiRect(rect.x(), rect.y() + 6, rect.width(), 12);
        UiDraw.fillRounded(graphics, track, 6, hover ? 0xE0202A35 : 0xD5121820);
        int fillWidth = Math.round(track.width() * UiMotion.clamp01(progress));
        if (fillWidth > 0) {
            UiDraw.fillRounded(graphics, new UiRect(track.x(), track.y(), fillWidth, track.height()), 6, 0x993B9BFF);
        }
        UiDraw.fillStrokeRounded(graphics, track, 6, 1.0F, UiMotion.multiplyAlpha(UiTheme.BORDER, 0.65F));
        int knobX = track.x() + Math.round((track.width() - 18) * UiMotion.clamp01(progress));
        float knobCenterY = rect.y() + rect.height() * 0.5F;
        UiDraw.circle(graphics, knobX + 9, knobCenterY + 1.0F, 9, 0x40000000);
        UiDraw.circle(graphics, knobX + 9, knobCenterY, 9, UiTheme.BLUE_LIGHT);
        UiDraw.fillStrokeRounded(graphics, new UiRect(
            Math.round(knobX), Math.round(knobCenterY - 9), 18, 18
        ), 9, 1.0F, UiMotion.multiplyAlpha(0x80C9E5FF, 0.5F));
    }

    private void updateMotion() {
        long now = System.nanoTime();
        float delta = Math.max(0.0F, Math.min(1.0F / 20.0F, (now - lastFrameNanos) / 1_000_000_000.0F));
        lastFrameNanos = now;
        sneakProgress = UiMotion.damp(sneakProgress, alwaysSneak.get() ? 1.0F : 0.0F, delta, 0.09F);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = scaleSystem.designX(event.x());
        double mouseY = scaleSystem.designY(event.y());
        int button = event.button();
        if (closing || openingProgress() < 0.96F) return true;
        if (capturingKeybind) {
            if (KEYBIND_HIT.contains(mouseX, mouseY)) return true;
            capturingKeybind = false;
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return PANEL.contains(mouseX, mouseY);
        if (MODULE_HIT.contains(mouseX, mouseY)) {
            if (module.state() == ModuleState.ENABLED) {
                runtime.modules().setEnabled(module.metadata().id(), false);
            } else {
                runtime.modules().setEnabled(module.metadata().id(), true);
            }
            return true;
        }
        if (KEYBIND_HIT.contains(mouseX, mouseY)) {
            capturingKeybind = true;
            return true;
        }
        if (SNEAK_HIT.contains(mouseX, mouseY)) {
            alwaysSneak.set(!alwaysSneak.get());
            runtime.configurationChanged();
            return true;
        }
        if (SLIDER_HIT.contains(mouseX, mouseY) || VALUE_HIT.contains(mouseX, mouseY)) {
            draggingSlider = weaponSlot;
            updateSliderFromMouse(mouseX);
            return true;
        }
        if (RESET_HIT.contains(mouseX, mouseY)) {
            resetCounter.activate();
            runtime.configurationChanged();
            return true;
        }
        return PANEL.contains(mouseX, mouseY);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (closing) return false;
        if (draggingSlider != null) {
            updateSliderFromMouse(scaleSystem.designX(event.x()));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (closing) return false;
        if (draggingSlider != null && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            draggingSlider = null;
            runtime.configurationChanged();
            return true;
        }
        return false;
    }

    private void updateSliderFromMouse(double mouseX) {
        if (draggingSlider == null || SLIDER_HIT.width() <= 0) return;
        double progress = Math.max(0.0D, Math.min(1.0D, (mouseX - SLIDER_HIT.x()) / SLIDER_HIT.width()));
        double updated = draggingSlider.minimum()
            + progress * (draggingSlider.maximum() - draggingSlider.minimum());
        draggingSlider.set(updated);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (closing || openingProgress() < 0.96F) return true;
        if (capturingKeybind) {
            if (event.key() == GLFW.GLFW_KEY_RIGHT_SHIFT) return true;
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                capturingKeybind = false;
                return true;
            }
            if (event.key() == GLFW.GLFW_KEY_DELETE
                || event.key() == GLFW.GLFW_KEY_BACKSPACE) {
                module.keybind().set(KeybindSetting.UNBOUND);
            } else {
                module.keybind().set(event.key());
            }
            capturingKeybind = false;
            runtime.configurationChanged();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_RIGHT_SHIFT || event.key() == GLFW.GLFW_KEY_ESCAPE) {
            return closeFromMenuKey();
        }
        return super.keyPressed(event);
    }

    private boolean closeFromMenuKey() {
        runtime.suppressMenuUntilRelease();
        onClose();
        return true;
    }

    private float openingProgress() {
        if (REDUCE_MOTION) return 1.0F;
        return UiMotion.easeOutQuint((System.currentTimeMillis() - openedAt) / 220.0F);
    }

    private float closingProgress() {
        if (!closing) return 1.0F;
        if (REDUCE_MOTION) return 0.0F;
        float progress = UiMotion.clamp01((System.currentTimeMillis() - closeStartedAt) / 160.0F);
        return 1.0F - UiMotion.easeInOutCubic(progress);
    }

    private void finishClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void onClose() {
        if (closing || closed) return;
        draggingSlider = null;
        runtime.configurationChanged();
        runtime.saveConfig();
        if (REDUCE_MOTION) {
            closed = true;
            finishClose();
            return;
        }
        closing = true;
        closeStartedAt = System.currentTimeMillis();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean isInGameUi() {
        return true;
    }
}

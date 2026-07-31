package dev.astrail.eye.core;

import dev.astrail.eye.api.event.ChatMessageEvent;
import dev.astrail.eye.api.event.ClientTickEvent;
import dev.astrail.eye.api.module.BackgroundRunning;
import dev.astrail.eye.api.module.DisableReason;
import dev.astrail.eye.api.module.ModuleState;
import dev.astrail.eye.api.service.ConfigurationService;
import dev.astrail.eye.api.service.InputService;
import dev.astrail.eye.api.service.InteractionService;
import dev.astrail.eye.api.service.RotationService;
import dev.astrail.eye.api.setting.KeybindSetting;
import dev.astrail.eye.core.config.ConfigStore;
import dev.astrail.eye.core.event.EventBus;
import dev.astrail.eye.core.module.ModuleManager;
import dev.astrail.eye.feature.macro.eye.AutoEyeModule;
import dev.astrail.eye.hud.EyeStatsHudRenderer;
import dev.astrail.eye.platform.minecraft.MinecraftInputService;
import dev.astrail.eye.platform.minecraft.MinecraftInteractionService;
import dev.astrail.eye.platform.minecraft.MinecraftRotationService;
import dev.astrail.eye.ui.screen.EyeSettingsScreen;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AstrailEyeRuntime {
    private static final Logger LOGGER = LoggerFactory.getLogger("AstrailEye");
    private static final int CONFIG_FLUSH_INTERVAL_TICKS = 100;

    private final EventBus events = new EventBus();
    private final ModuleManager modules = new ModuleManager();
    private final MinecraftInputService inputs = new MinecraftInputService();
    private final MinecraftRotationService rotations = new MinecraftRotationService();
    private final MinecraftInteractionService interactions = new MinecraftInteractionService();
    private final Services services = new Services(events, inputs, rotations, interactions);
    private final ConfigStore config = new ConfigStore(
        FabricLoader.getInstance().getConfigDir(),
        modules
    );
    private final AutoEyeModule eyeModule;
    private final EyeStatsHudRenderer statsHud;
    private final Map<String, Boolean> moduleKeyStates = new HashMap<>();

    private boolean menuKeyWasDown;
    private boolean suppressMenuUntilRelease;
    private int ticksSinceConfigFlush;

    public record Services(
        EventBus events,
        InputService inputs,
        RotationService rotations,
        InteractionService interactions
    ) {
    }

    public AstrailEyeRuntime() {
        eyeModule = new AutoEyeModule(
            events,
            inputs,
            rotations,
            interactions,
            config::markDirty
        );
        statsHud = new EyeStatsHudRenderer(modules);
    }

    public void initialize() {
        modules.register(eyeModule);
        config.load();
        modules.onChange(config::markDirty);
        statsHud.register();
        ClientReceiveMessageEvents.GAME.register((message, overlay) ->
            events.post(new ChatMessageEvent(message.getString(), overlay))
        );
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick(client));
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> worldChanged());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> stop());
        LOGGER.info("Astrail Eye initialized for Minecraft 26.2");
    }

    private void tick(Minecraft client) {
        handleMenuKey(client);
        handleModuleKeys(client);
        events.post(new ClientTickEvent(client, 0L));
        rotations.tick();
        if (++ticksSinceConfigFlush >= CONFIG_FLUSH_INTERVAL_TICKS) {
            ticksSinceConfigFlush = 0;
            config.flushIfDirty();
        }
    }

    private void handleMenuKey(Minecraft client) {
        boolean pressed = InputConstants.isKeyDown(client.getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT);
        if (!pressed) {
            suppressMenuUntilRelease = false;
        }
        Screen currentScreen = client.gui.screen();
        boolean canOpen = currentScreen == null || currentScreen instanceof TitleScreen;
        if (!suppressMenuUntilRelease && pressed && !menuKeyWasDown && canOpen) {
            client.gui.setScreen(new EyeSettingsScreen(this, currentScreen));
        }
        menuKeyWasDown = pressed;
    }

    private void handleModuleKeys(Minecraft client) {
        boolean hasScreen = client.gui.screen() != null;
        int keyCode = eyeModule.keybind().get();
        boolean pressed = keyCode != KeybindSetting.UNBOUND
            && keyCode != GLFW.GLFW_KEY_RIGHT_SHIFT
            && InputConstants.isKeyDown(client.getWindow(), keyCode);
        boolean previous = moduleKeyStates.getOrDefault(eyeModule.metadata().id(), false);
        moduleKeyStates.put(eyeModule.metadata().id(), pressed);
        if (!hasScreen && pressed && !previous) {
            try {
                modules.toggle(eyeModule.metadata().id());
            } catch (RuntimeException error) {
                LOGGER.error("Failed to toggle module", error);
            }
        }
    }

    public boolean shouldKeepWorldRunning() {
        for (var module : modules.all()) {
            if (module instanceof BackgroundRunning && module.state() == ModuleState.ENABLED) {
                return true;
            }
        }
        return false;
    }

    private void worldChanged() {
        modules.disableWorldScoped();
        inputs.clear();
        rotations.clear();
    }

    private void stop() {
        modules.disableAll(DisableReason.CLIENT_STOPPING);
        inputs.clear();
        rotations.clear();
        config.saveAndFlush();
    }

    public ModuleManager modules() {
        return modules;
    }

    public AutoEyeModule eyeModule() {
        return eyeModule;
    }

    public InputService inputs() {
        return inputs;
    }

    public void configurationChanged() {
        config.markDirty();
    }

    public void saveConfig() {
        config.save();
    }

    public void suppressMenuUntilRelease() {
        suppressMenuUntilRelease = true;
        menuKeyWasDown = true;
    }
}

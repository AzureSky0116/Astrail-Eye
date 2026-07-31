package dev.astrail.eye.core.module;

import dev.astrail.eye.api.module.ClientModule;
import dev.astrail.eye.api.module.DisableReason;
import dev.astrail.eye.api.module.ModuleMetadata;
import dev.astrail.eye.api.module.ModuleState;
import dev.astrail.eye.api.setting.Setting;
import dev.astrail.eye.api.setting.KeybindSetting;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractModule implements ClientModule {
    private final Logger logger;
    private final ModuleMetadata metadata;
    private final List<Setting<?>> settings = new ArrayList<>();
    private final KeybindSetting keybind;
    private ModuleState state = ModuleState.DISABLED;
    private ModuleScope scope;

    protected AbstractModule(ModuleMetadata metadata, Setting<?>... settings) {
        this(metadata, KeybindSetting.UNBOUND, settings);
    }

    protected AbstractModule(ModuleMetadata metadata, int defaultKey, Setting<?>... settings) {
        this.metadata = metadata;
        this.keybind = new KeybindSetting(defaultKey);
        this.settings.add(this.keybind);
        this.settings.addAll(List.of(settings));
        this.logger = LoggerFactory.getLogger("Astrail/" + metadata.id());
    }

    @Override
    public final ModuleMetadata metadata() {
        return metadata;
    }

    @Override
    public final synchronized ModuleState state() {
        return state;
    }

    @Override
    public final List<Setting<?>> settings() {
        return List.copyOf(settings);
    }

    @Override
    public final KeybindSetting keybind() {
        return keybind;
    }

    protected final void addSettings(Setting<?>... settings) {
        this.settings.addAll(List.of(settings));
    }

    @Override
    public final synchronized void enable() {
        if (state == ModuleState.ENABLED || state == ModuleState.ENABLING) {
            return;
        }
        state = ModuleState.ENABLING;
        ModuleScope newScope = new ModuleScope();
        try {
            onEnable(newScope);
            scope = newScope;
            state = ModuleState.ENABLED;
            logger.info("Enabled");
        } catch (Throwable error) {
            closeAfterFailure(newScope, error);
            state = ModuleState.FAILED;
            throw new IllegalStateException("Failed to enable module " + metadata.id(), error);
        }
    }

    @Override
    public final synchronized void disable(DisableReason reason) {
        if (state == ModuleState.DISABLED || state == ModuleState.DISABLING) {
            return;
        }
        state = ModuleState.DISABLING;
        Throwable failure = null;
        try {
            onDisable(reason);
        } catch (Throwable error) {
            failure = error;
        } finally {
            if (scope != null) {
                try {
                    scope.close();
                } catch (Throwable closeError) {
                    if (failure == null) {
                        failure = closeError;
                    } else {
                        failure.addSuppressed(closeError);
                    }
                }
            }
            scope = null;
            state = ModuleState.DISABLED;
        }
        if (failure != null) {
            logger.error("Disable cleanup failed: reason={}", reason, failure);
        } else {
            logger.info("Disabled: reason={}", reason);
        }
    }

    protected abstract void onEnable(ModuleScope scope) throws Exception;

    protected void onDisable(DisableReason reason) throws Exception {
    }

    private static void closeAfterFailure(ModuleScope scope, Throwable original) {
        try {
            scope.close();
        } catch (Throwable cleanupError) {
            original.addSuppressed(cleanupError);
        }
    }
}

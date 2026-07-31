package dev.astrail.eye.api.module;

import dev.astrail.eye.api.setting.Setting;
import dev.astrail.eye.api.setting.KeybindSetting;
import java.util.List;

public interface ClientModule {
    ModuleMetadata metadata();

    ModuleState state();

    List<Setting<?>> settings();

    KeybindSetting keybind();

    void enable();

    void disable(DisableReason reason);
}

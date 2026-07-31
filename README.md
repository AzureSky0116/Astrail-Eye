# Astrail Eye

Standalone **Auto Eye** farming mod for Minecraft **26.2** (Fabric, client-side only).

## Features

- **Auto Eye (Stationary mode)** — start/stop in-place farming from the settings GUI or a configurable toggle keybind
- **Always Sneak** — automatically holds sneak while standing still
- **Weapon Slot** — hotbar slot used for the attack weapon
- **Custom settings GUI** — fully custom-drawn interface with an optional background blur (`-Dastrail-eye.blur=true`)
- **Performance engineering**:
  - Merged-run fill emission (up to ~3.5× fewer GUI render-state elements per frame)
  - O(1) GUI stratum fast path mixin (kills the vanilla O(n²) element-chain walk that caused heavy FPS drops while the GUI is open)

## Requirements

| Component | Version |
|---|---|
| Minecraft | 26.2 |
| Fabric Loader | >= 0.19.3 |
| Fabric API | >= 0.154.2+26.2 |
| Java | >= 25 |

## Usage

1. Download the jar from [Releases](../../releases) and drop it into `.minecraft/mods/`.
2. Open the settings GUI (bound key, see controls in-game) to configure the module.
3. Optional JVM flags:
   - `-Dastrail-eye.blur=true` — enable background blur behind the settings GUI.

## Building from source

```sh
./gradlew build
```

The mod jar is produced at `build/libs/astrail-eye-26.2.jar`.

## License

[MIT](LICENSE)

# Astrail Eye

[中文说明](README.zh-CN.md) | [English](#)

![Version](https://img.shields.io/badge/version-26.2-2ea44f)
![Minecraft](https://img.shields.io/badge/Minecraft-26.2-e34c26)
![Fabric](https://img.shields.io/badge/Fabric-0.19.3--0.154.2%2B26.2-dbd0b4)
![Java](https://img.shields.io/badge/Java-25-f89820)
![License](https://img.shields.io/badge/license-MIT-blue)

An independent **Auto Eye** farming mod for **Minecraft 26.2 / Fabric** (client-side
only). It farms Zealots in the End for **Summoning Eyes** from a fixed firing
position — automatically acquiring targets, leading its aim, prioritizing Special
Zealots, counting kills and eyes, and rendering the stats on a HUD.

## What is Zealot farming?

> Source: [Hypixel SkyBlock Wiki — Zealot](https://hypixel-skyblock.fandom.com/wiki/Zealot),
> [Zealot Bruiser](https://hypixel-skyblock.fandom.com/wiki/Zealot_Bruiser),
> [Summoning Eye](https://hypixel-skyblock.fandom.com/wiki/Summoning_Eye)

**Zealots** are Enderman-type mobs found exclusively in the **Dragon's Nest** (The
End). Level 55 with 13,000 HP, they spawn in waves of 16 every 11 seconds. They are
farmed for one reason: **Summoning Eyes**.

Killing a Zealot has a base **1/420** chance (a **Zealot Bruiser**: **1/380**) of
spawning a **Special Zealot** — a rare portal-frame-carrying variant (2,000 HP).
The spawn chance can be pushed higher by killing Zealots with multiple hits
(+11.1%), kill streaks without a special (doubles after 420 kills), a level 100
Enderman Pet (+25%), the *Zealuck* Dragon Essence perk (+2–10%) and the *Tracking*
stat.

Special Zealots are announced in chat — *"A special Zealot has spawned nearby!"*
plus the Wither sound — and **always drop 1 Summoning Eye** (a 100% drop; the
contributor receives it even if someone else lands the kill). Their kill is also
announced in chat: *"RARE DROP! Summoning Eye"*.

The **Summoning Eye** is an Epic item used at the **Ender Altar** in the Dragon's
Nest to summon Ender Dragons (seven types), to upgrade the Ender Dragon Pet, and
as a crafting ingredient — and it trades on the Bazaar. That makes Zealot farming
one of the classic money-making methods of SkyBlock, and hitting the specials is
the entire game.

## Features

- **Auto Eye (stationary)** — farms in place: picks targets, aims with velocity
  lead, attacks, re-acquires. Never moves; safe to AFK in one spot.
- **Special Zealot priority** — when the chat announces a special Zealot, it is
  locked on first for 3 seconds, with the aim snapped to it.
- **Attack Once mode** (default on) — attacks each Zealot once, then immediately
  selects the next target; attack rate capped at 1–10 hits/sec (default 4).
- **Always Sneak** — holds sneak while standing still, so endermen are not
  provoked by eye contact.
- **Weapon Slot** — which hotbar slot holds the attack weapon (1–9).
- **Kill tracking** — separate Zealot / Bruiser kill counters + Summoning Eye
  counter, persisted across restarts, resettable from the GUI (confirm required).
- **Stats HUD** — compact top-left card showing `Zealot / Bruiser / Eyes`.
- **Custom settings GUI** — fully custom-drawn interface with a smooth reveal
  animation and an optional frosted background blur (`-Dastrail-eye.blur=true`).
- **Performance engineering**:
  - merged-run fill emission — up to ~3.5× fewer GUI render-state elements per frame;
  - O(1) GUI stratum fast-path mixin — removes the vanilla O(n²) element-chain
    walk that tanked FPS while the GUI was open.

## Settings

| Setting | Type | Default | Description |
|---|---|---|---|
| Weapon Slot | number | 1 | Hotbar slot containing the attack weapon (1–9) |
| Attack Once | toggle | on | Attack each Zealot once, then immediately pick another target |
| Attack Speed | number | 4 | Max attacks per second while Attack Once is on (1–10, step 0.5) |
| Always Sneak | toggle | off | Keep sneak held while standing still |
| Keybind | key | unbound | Toggle the Auto Eye module |
| Reset Counter | confirm | — | Clear Zealot / Bruiser / Eye totals (double-confirm within 5 s) |

Kill totals persist to the config file automatically; they are restored on next
launch.

## HUD

A compact stats card (top-left) with three rows, updated live:

```
Auto Eye
Zealot  1,234
Bruiser    56
Eyes      145
```

## Usage

1. Install [Fabric Loader 0.19.3+](https://fabricmc.net/use/) for Minecraft 26.2
   and drop `astrail-eye-26.2.jar` plus [Fabric API 0.154.2+26.2](https://modrinth.com/mod/fabric-api)
   into your `mods/` folder.
2. Launch, join Hypixel SkyBlock, and stand in the Dragon's Nest (or the Zealot
   Bruiser Hideout). The module only runs while a Zealot area is detected.
3. Open the settings GUI with the module keybind (bind one in the GUI), pick your
   weapon slot and attack style, and toggle **Auto Eye** on.
4. Watch the chat for *"A special Zealot has spawned nearby!"* — the mod snaps to
   the special and grabs the eye.

Optional JVM flag:

```
-Dastrail-eye.blur=true    # frosted blur behind the settings GUI (off by default)
```

## Building from source

Requirements: **JDK 25** and an internet connection.

```bash
git clone https://github.com/AzureSky0116/Astrail-Eye.git
cd Astrail-Eye
./gradlew build
```

The mod jar is produced at `build/libs/astrail-eye-26.2.jar`.

## How it works

1. **Area detection** — every tick, the module inspects the scoreboard sidebar
   for `Dragon's Nest` / `Zealot Bruiser Hideout` (with 10 s memory) and scans
   nearby entities. Farming only runs while a Zealot area is active.
2. **Targeting** — Zealots are detected as `Enderman` entities whose name (or the
   name of an adjacent armor stand) contains "zealot". Targets are scored by
   special status, angular distance to the crosshair, then range. A special
   Zealot is identified by its carried block being an end portal frame.
3. **Special lock-on** — on the chat announcement, `specialAlertTicks = 60`
   (3 s) flips the priority to the special Zealot above everything else.
4. **Aiming** — the aim point is the target center at 62% height, led by its
   velocity (up to 8 ticks of lead); the module fires once the crosshair is
   within 2.5° of the lead point.
5. **Attack Once** — each target receives one hit, then a 20-tick sweep window
   blocks re-targeting it, and the next Zealot is engaged, paced by the attack
   speed setting (`20 ticks ÷ hits/sec`).
6. **Counting** — a damage-based tracker attributes kills to Zealot vs Bruiser;
   chat lines containing `RARE DROP! Summoning Eye` increment the eye counter.
   Totals persist to the config file and feed the HUD.
7. **Performance** — a merged-run fill emission cuts GUI render-state elements,
   and a mixin replaces the vanilla O(n²) GUI stratum walk with an O(1) fast
   path, keeping FPS stable while the settings GUI is open.

## Verification baseline

- Release jar: `build/libs/astrail-eye-26.2.jar`
- SHA-256: `67B569D1D4F4919F1BD6E1FB98E05979417D0F8D4A8493AA3D364D4EBA217768`

## Disclaimer

This mod automates in-game combat for Zealot farming. Automated gameplay may be
against the rules of Hypixel or other servers and can result in punishment up to
a ban — check the server rules and use it at your own risk. This is an independent
educational project, not affiliated with Hypixel Inc. or Mojang Studios.

## License

[MIT](LICENSE)

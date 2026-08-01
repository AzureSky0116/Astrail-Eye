# Astrail Eye

[English](README.md) | [中文](#)

![Version](https://img.shields.io/badge/version-26.2-2ea44f)
![Minecraft](https://img.shields.io/badge/Minecraft-26.2-e34c26)
![Fabric](https://img.shields.io/badge/Fabric-0.19.3--0.154.2%2B26.2-dbd0b4)
![Java](https://img.shields.io/badge/Java-25-f89820)
![License](https://img.shields.io/badge/license-MIT-blue)

面向 **Minecraft 26.2 / Fabric** 的独立 **Auto Eye 刷怪 Mod**（纯客户端）。在末地
固定站位自动刷 Zealot（狂热者）刷 **Summoning Eye（召唤之眼）**——自动索敌、
预测瞄准、优先锁定 Special Zealot、统计击杀与掉眼数量，并在 HUD 上实时显示。

## 什么是 Zealot 刷怪？

> 资料出处：[Hypixel SkyBlock Wiki — Zealot](https://hypixel-skyblock.fandom.com/wiki/Zealot)、
> [Zealot Bruiser](https://hypixel-skyblock.fandom.com/wiki/Zealot_Bruiser)、
> [Summoning Eye](https://hypixel-skyblock.fandom.com/wiki/Summoning_Eye)

**Zealot（狂热者）** 是只出现在末地 **Dragon's Nest（龙巢）** 的末影人型怪物：
55 级、13,000 HP，每 11 秒刷新一波 16 只。刷它们的唯一目的就是 **Summoning Eye**。

击杀普通 Zealot 有基础 **1/420** 概率（击杀 **Zealot Bruiser** 则为 **1/380**）
触发生成 **Special Zealot**——手持传送门框架的稀有变种（2,000 HP）。生成率可通过
多种方式提升：多段攻击击杀（+11.1%）、连续击杀无特殊（420 只后翻倍）、100 级
末影人宠物（+25%）、龙之精华商店 *Zealuck* 天赋（+2–10%）以及 *Tracking* 属性。

Special Zealot 生成时聊天栏会提示 *"A special Zealot has spawned nearby!"* 并伴随
Wither 音效，且**必定掉落 1 个 Summoning Eye**（100% 掉落，击杀贡献者必得，即使
补刀的是别人）；击杀时聊天栏提示 *"RARE DROP! Summoning Eye"*。

**Summoning Eye** 是史诗物品：在龙巢的 **Ender Altar（末地祭坛）** 可召唤七种
末影龙、用于升级 Ender Dragon Pet、也是合成材料，且可在 Bazaar 交易。因此刷 Zealot
是 SkyBlock 的经典赚钱方式之一——而"抓到特殊"就是这门生意的全部。

## 功能

- **Auto Eye（定点模式）**——原地刷怪：自动索敌、带提前量的预测瞄准、攻击、
  重新索敌，全程不移动，适合固定点位挂机。
- **Special Zealot 优先**——聊天栏提示特殊狂热者出现后 3 秒内优先锁定它，准星直接甩过去。
- **Attack Once 模式**（默认开启）——每只 Zealot 只攻击一次便立刻换下一个目标，
  攻击频率上限 1–10 次/秒（默认 4）。
- **Always Sneak（自动潜行）**——站定时自动按住潜行，避免与末影人对视而被激怒。
- **Weapon Slot（武器槽位）**——指定热键栏 1–9 中的攻击武器槽。
- **击杀统计**——Zealot / Bruiser 击杀数分列计数 + Summoning Eye 计数，
  跨重启持久化，可在 GUI 中重置（需二次确认）。
- **统计 HUD**——左上角紧凑卡片实时显示 `Zealot / Bruiser / Eyes` 三栏。
- **自绘设置 GUI**——完全自定义绘制，带平滑展开动画；可选磨砂背景模糊
  （`-Dastrail-eye.blur=true`）。
- **性能工程**：
  - 合并填充发射（merged-run fill emission）——每帧 GUI 渲染状态元素减少约 3.5 倍；
  - O(1) GUI 分层快速路径 Mixin——消除原版 O(n²) 元素链遍历导致的打开 GUI 掉帧。

## 设置

| 设置 | 类型 | 默认 | 说明 |
|---|---|---|---|
| Weapon Slot | 数字 | 1 | 攻击武器所在热键栏槽位（1–9） |
| Attack Once | 开关 | 开 | 每只 Zealot 攻击一次后立即换目标 |
| Attack Speed | 数字 | 4 | Attack Once 模式下的每秒攻击次数上限（1–10，步进 0.5） |
| Always Sneak | 开关 | 关 | 站定时保持潜行 |
| Keybind | 按键 | 未绑定 | 切换 Auto Eye 模块开关 |
| Reset Counter | 确认 | — | 清零 Zealot / Bruiser / Eye 统计（5 秒内二次确认） |

击杀统计自动持久化到配置文件，下次启动自动恢复。

## HUD

左上角紧凑统计卡片，三行实时更新：

```
Auto Eye
Zealot  1,234
Bruiser    56
Eyes      145
```

## 使用方法

1. 为 Minecraft 26.2 安装 [Fabric Loader 0.19.3+](https://fabricmc.net/use/)，
   把 `astrail-eye-26.2.jar` 和 [Fabric API 0.154.2+26.2](https://modrinth.com/mod/fabric-api)
   一起放进 `mods/` 文件夹。
2. 启动游戏，进入 Hypixel SkyBlock，站到龙巢（或 Zealot Bruiser Hideout）。
   模块只有在检测到 Zealot 区域时才会工作。
3. 用模块按键（在 GUI 中绑定）打开设置界面，选好武器槽位与攻击模式，开启
   **Auto Eye**。
4. 留意聊天栏的 *"A special Zealot has spawned nearby!"*——Mod 会自动甩准星
   锁定特殊狂热者，把眼拿下。

可选 JVM 参数：

```
-Dastrail-eye.blur=true    # 设置界面背后的磨砂模糊（默认关闭）
```

## 从源码构建

环境要求：**JDK 25** 与网络连接。

```bash
git clone https://github.com/AzureSky0116/Astrail-Eye.git
cd Astrail-Eye
./gradlew build
```

构建产物：`build/libs/astrail-eye-26.2.jar`。

## 工作原理

1. **区域检测**——每 tick 检查计分板侧边栏是否含 `Dragon's Nest` /
   `Zealot Bruiser Hideout`（10 秒记忆），并扫描附近实体；仅在 Zealot 区域激活刷怪。
2. **索敌**——Zealot 判定为名字（或其旁盔甲架名字）含 "zealot" 的末影人实体；
   按"是否特殊 → 与准星角距离 → 距离"打分选目标。特殊狂热者以手持方块是否为
   末地传送门框架来识别。
3. **特殊锁定**——收到聊天提示后 `specialAlertTicks = 60`（3 秒），优先级直接
   压过其他目标。
4. **瞄准**——瞄准点为目标中心 62% 高度，并按其速度做最多 8 tick 的提前量；
   准星进入提前点 2.5° 内才开火。
5. **Attack Once**——每个目标只打一下，随后 20 tick 的扫描窗口内不再重复选它，
   按攻击速度设置（20 tick ÷ 次/秒）的节奏切下一个目标。
6. **统计**——基于伤害的追踪器把击杀归入 Zealot / Bruiser 两类；聊天栏含
   `RARE DROP! Summoning Eye` 的行递增眼睛计数。总数持久化到配置文件并喂给 HUD。
7. **性能**——合并填充发射削减 GUI 渲染状态元素；Mixin 把原版 O(n²) 的 GUI
   分层遍历替换为 O(1) 快速路径，打开设置界面时帧率保持稳定。

## 验证基线

- 发布 jar：`build/libs/astrail-eye-26.2.jar`
- SHA-256：`67B569D1D4F4919F1BD6E1FB98E05979417D0F8D4A8493AA3D364D4EBA217768`

## 免责声明

本 Mod 自动操作游戏内战斗以刷取 Zealot。自动游玩可能违反 Hypixel 或其他服务器的
规则，并可能导致包括封号在内的处罚——请查阅服务器规则并自行承担风险。这是一个
独立的教学性质项目，与 Hypixel Inc. 或 Mojang Studios 无关。

## 许可证

[MIT](LICENSE)

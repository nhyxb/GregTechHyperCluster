# GregTech HyperCluster 0.1 贴图与 Aseprite 源文件清单

本目录存放 0.1（HV 首发闭环）的全部 **7 张 `16×16` RGBA** Minecraft / GTCEu Modern 贴图与对应的 **5 个分层 `.aseprite` 源文件**，供工程拷贝进 `src/main/resources/assets/hypercluster/textures/`。

总览预览图（含 3 件物品 6x/2x/1x 槽位对比，以及两台 HV 机器叠加机壳的 6x/2x/1x 对比）见 [contact_sheet.png](textures/previews/contact_sheet.png)。

## 交付清单

| 中文名 | 数量 | 导出贴图（16×16 RGBA PNG） | 分层源文件（`.aseprite`） | 目标资源路径（`modid = hypercluster`） |
| --- | :---: | --- | --- | --- |
| 矿处遥测日志 | 1 | `textures/item/mining_telemetry_log.png` | `textures/source/mining_telemetry_log.aseprite` | `assets/hypercluster/textures/item/mining_telemetry_log.png` |
| 空白载体 | 1 | `textures/item/blank_carrier.png` | `textures/source/blank_carrier.aseprite` | `assets/hypercluster/textures/item/blank_carrier.png` |
| 矿处边缘芯片 | 1 | `textures/item/mining_edge_chip.png` | `textures/source/mining_edge_chip.aseprite` | `assets/hypercluster/textures/item/mining_edge_chip.png` |
| 微型烧录台正面（待机） | 1 | `textures/block/machines/micro_burner/overlay_front.png` | `textures/source/micro_burner_front.aseprite`（Frame 1 / `idle`） | `assets/hypercluster/textures/block/machines/micro_burner/overlay_front.png` |
| 微型烧录台正面（运行） | 1 | `textures/block/machines/micro_burner/overlay_front_active.png` | `textures/source/micro_burner_front.aseprite`（Frame 2 / `active`） | `assets/hypercluster/textures/block/machines/micro_burner/overlay_front_active.png` |
| HV 示范洗矿机正面（待机） | 1 | `textures/block/machines/telemetry_ore_washer/overlay_front.png` | `textures/source/telemetry_ore_washer_front.aseprite`（Frame 1 / `idle`） | `assets/hypercluster/textures/block/machines/telemetry_ore_washer/overlay_front.png` |
| HV 示范洗矿机正面（运行） | 1 | `textures/block/machines/telemetry_ore_washer/overlay_front_active.png` | `textures/source/telemetry_ore_washer_front.aseprite`（Frame 2 / `active`） | `assets/hypercluster/textures/block/machines/telemetry_ore_washer/overlay_front_active.png` |

## 设计说明与后续复用指南

### 1. 矿处遥测日志（`mining_telemetry_log`）
- **轮廓与构图**：`12×14` 竖版倒角工业数据卡 + 底部 3 组金色触点金手指，与横版 `14×12` 芯片载体在轮廓方向上天然区分。
- **分层复用（化工 / 装配 / 能源日志）**：
  - `card_outline`、`card_body`、`label_bay` 三层为通用数据卡壳体，后续无需修改。
  - 新增其他领域日志时，只需替换 `domain_band`（顶部 `y=3` 领域色条）与 `domain_emblem`（`x=4..11, y=5..10` 的 `8×6` 凹槽徽标）：
    - **矿处（Mining）**：琥珀铜金 `#E58220` / `#FFC745` + 暗石矿脉纹样（已完成）
    - **化工（Chemical）**：建议青绿/翠绿 + 烧瓶/分子环纹样
    - **装配（Assembly）**：建议钴蓝 + 齿轮/焊点回路纹样
    - **能源（Energy）**：建议绯红或电蓝 + 闪电/能量棒纹样

### 2. 空白载体（`blank_carrier`）与矿处边缘芯片（`mining_edge_chip`）
- **完全一致的外轮廓**：两张贴图共享完全相同的 `carrier_outline` 与 `carrier_substrate`（`14×12` 浅陶瓷基板，右上角 2px 定位倒角），Alpha 轮廓逐像素一致。
- **烧录前后一眼区分**：
  - `blank_carrier`：高明度浅色陶瓷基板（`#F2F7F6` / `#DDE7E6`）+ 中央明显凹陷的空白芯片位与浅灰空焊盘。
  - `mining_edge_chip`：在同位置压装高对比深炭黑硅芯片（`#12161F` / `#1F2633`）、两侧 6 根 + 底部 3 组高亮金色金属触点（`#FFE169` / `#E0A422`）以及斜切芯片表面的琥珀橙矿处标记（`#F07E18` / `#FFC947`），与矿处遥测日志同色系呼应。
  - 后续扩展其他领域边缘芯片时，可沿用 `mining_edge_chip.aseprite`，仅调整 `mining_mark` 图层颜色。

### 3. 微型烧录台正面（`micro_burner` 正面透明覆盖贴图）
- **GTCEu Modern 兼容**：遵循 `workableTieredHullRenderer("hypercluster:block/machines/micro_burner")` 约定，提供 `overlay_front.png`（待机）与 `overlay_front_active.png`（运行）。
- **透明边距**：外围 2px 边框及面板四角保持 100% 透明（`alpha = 0`），直接叠加在 HV 橙金机壳（或其他电压等级机壳）上即可露出原版机壳金属边框与铆钉。
- **动作与状态反馈**：
  - `bezel_frame` / `chamber_rails`：深冷钢凹陷面板与双竖导轨。
  - `chip_slot` / `press_head` / `burn_glow`：待机时压头升起在上方（`y=5..7`），露出下方黑色空插槽（`y=10`）；运行时压头沿双导轨下压 2px（`y=7..9`）紧压已插入的浅色载体，并在触点接触面产生收束的金色烧录光缝。
  - `status_lights`：右上角双状态灯（烧录灯 + 运行灯）待机时暗淡熄灭，运行时点亮为暖琥珀灯与青翠运行灯。

### 4. HV 示范洗矿机正面（`telemetry_ore_washer` 正面透明覆盖贴图）
- **GTCEu Modern 兼容**：遵循 `workableTieredHullRenderer("hypercluster:block/machines/telemetry_ore_washer")` 约定，提供 `overlay_front.png`（待机）与 `overlay_front_active.png`（运行），外围 2px 边框保持 100% 透明，沿用 `micro_burner` 同套冷钢色阶（`#141820` / `#3B4557` / `#5A687D` / `#8A99AE` / `#C2D0E0`）与左上主光源。
- **与 `micro_burner` 的轮廓区分**：采用**顶部收口漏斗进料口**（`y=2..3` 收窄露出左右 HV 金壳肩部）+ **左侧竖长视窗洗矿腔**（`x=2..10`）+ **右侧阶梯式竖向遥测灯带模块**（`x=11..13, y=4..12`），与 `micro_burner` 的平顶方框压头结构一眼区分。
- **分层结构与动效反馈**（`telemetry_ore_washer_front.aseprite`，5 图层 + `idle`/`active` 动画标签）：
  - `bezel_frame`：冷钢阶梯外框、顶部进料漏斗与右侧遥测灯槽。
  - `water_chamber`：待机时进料口空置、下方为平静深蓝水池带微弱水平反光；运行时进料口落入琥珀金粉碎矿料，水池转为翻涌高亮蔚蓝冲洗水层。
  - `washing_roller`：高对比螺旋刮板洗矿滚轮（`y=5..7`），待机静止，运行时刮板相位右移并增强湿润高光，呈现旋转搓洗质感。
  - `water_spray`（仅运行帧）：顶喉中心水嘴向下冲刷滚轮并在下方激起白青水花浪峰。
  - `telemetry_strip`：右侧竖向双段遥测状态灯带（`x=12, y=5..11`），待机为暗色熄灭玻璃；运行时上段点亮青色遥测链路灯（`#D4FFFF` / `#32ECF2` / `#149EB0`），下段点亮与 `mining_telemetry_log` 同色系的矿处琥珀金采集灯（`#FFD04A` / `#F08A20` / `#B54E14`）。

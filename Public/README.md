# GregTech HyperCluster 0.1 贴图与 Aseprite 源文件清单

本目录存放 0.1（HV 首发闭环）的 5 张 `16×16` RGBA Minecraft / GTCEu Modern 贴图与对应的分层 `.aseprite` 源文件，供后续 P00/P05 工程直接拷贝进 `src/main/resources/assets/<modid>/textures/`。

预览总览图见 [contact_sheet.png](textures/previews/contact_sheet.png)。

## 交付清单

| 中文名 | 数量 | 导出贴图（16×16 RGBA PNG） | 分层源文件（`.aseprite`） | 目标资源路径（建工程后） |
| --- | :---: | --- | --- | --- |
| 矿处遥测日志 | 1 | `textures/item/mining_telemetry_log.png` | `textures/source/mining_telemetry_log.aseprite` | `assets/<modid>/textures/item/mining_telemetry_log.png` |
| 空白载体 | 1 | `textures/item/blank_carrier.png` | `textures/source/blank_carrier.aseprite` | `assets/<modid>/textures/item/blank_carrier.png` |
| 矿处边缘芯片 | 1 | `textures/item/mining_edge_chip.png` | `textures/source/mining_edge_chip.aseprite` | `assets/<modid>/textures/item/mining_edge_chip.png` |
| 微型烧录台正面（待机） | 1 | `textures/block/machines/micro_burner/overlay_front.png` | `textures/source/micro_burner_front.aseprite`（Frame 1 / `idle`） | `assets/<modid>/textures/block/machines/micro_burner/overlay_front.png` |
| 微型烧录台正面（运行） | 1 | `textures/block/machines/micro_burner/overlay_front_active.png` | `textures/source/micro_burner_front.aseprite`（Frame 2 / `active`） | `assets/<modid>/textures/block/machines/micro_burner/overlay_front_active.png` |

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
- **GTCEu Modern 兼容**：遵循 `workableTieredHullRenderer("<modid>:block/machines/micro_burner")` 约定，提供 `overlay_front.png`（待机）与 `overlay_front_active.png`（运行）。
- **透明边距**：外围 2px 边框及面板四角保持 100% 透明（`alpha = 0`），直接叠加在 HV 橙金机壳（或其他电压等级机壳）上即可露出原版机壳金属边框与铆钉。
- **动作与状态反馈**：
  - `bezel_frame` / `chamber_rails`：深冷钢凹陷面板与双竖导轨。
  - `chip_slot` / `press_head` / `burn_glow`：待机时压头升起在上方（`y=5..7`），露出下方黑色空插槽（`y=10`）；运行时压头沿双导轨下压 2px（`y=7..9`）紧压已插入的浅色载体，并在触点接触面产生收束的金色烧录光缝。
  - `status_lights`：右上角双状态灯（烧录灯 + 运行灯）待机时暗淡熄灭，运行时点亮为暖琥珀灯与青翠运行灯。

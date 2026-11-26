# GregTech HyperCluster 0.1 + 0.2 贴图与 Aseprite 源文件清单

本目录存放 **0.1（HV 首发闭环）** 与 **0.2（EV 数据工坊）** 的全部 **17 张 `16×16` RGBA** Minecraft / GTCEu Modern 贴图与对应的 **13 个分层 `.aseprite` 源文件**，供工程拷贝进 `src/main/resources/assets/hypercluster/textures/`。

全量总览预览图（9 件物品 `5x / 2x / 1x` 槽位对比 + 4 台机器 8 张透明正面覆盖贴图 + HV 橙金 / EV 钛紫 / 不锈钢机壳实装对比）见 [contact_sheet.png](textures/previews/contact_sheet.png)。

## 交付清单

### 一、物品贴图（9 张 `16×16 RGBA`）

| 阶段 | 中文名 | 导出贴图（`16×16 RGBA`） | 分层源文件（`.aseprite`） | 目标资源路径（`modid = hypercluster`） |
| :---: | --- | --- | --- | --- |
| 0.1 | 矿处遥测日志 | `textures/item/mining_telemetry_log.png` | `textures/source/mining_telemetry_log.aseprite` | `assets/hypercluster/textures/item/mining_telemetry_log.png` |
| 0.2 | 化工遥测日志 | `textures/item/chemical_telemetry_log.png` | `textures/source/chemical_telemetry_log.aseprite` | `assets/hypercluster/textures/item/chemical_telemetry_log.png` |
| 0.2 | 清洁数据集 | `textures/item/clean_dataset.png` | `textures/source/clean_dataset.aseprite` | `assets/hypercluster/textures/item/clean_dataset.png` |
| 0.2 | 高纯矿处数据集 | `textures/item/high_purity_mining_dataset.png` | `textures/source/high_purity_mining_dataset.aseprite` | `assets/hypercluster/textures/item/high_purity_mining_dataset.png` |
| 0.2 | 高纯化工数据集 | `textures/item/high_purity_chemical_dataset.png` | `textures/source/high_purity_chemical_dataset.aseprite` | `assets/hypercluster/textures/item/high_purity_chemical_dataset.png` |
| 0.2 | 乱码噪点 | `textures/item/corrupted_noise.png` | `textures/source/corrupted_noise.aseprite` | `assets/hypercluster/textures/item/corrupted_noise.png` |
| 0.1 | 空白载体 | `textures/item/blank_carrier.png` | `textures/source/blank_carrier.aseprite` | `assets/hypercluster/textures/item/blank_carrier.png` |
| 0.1 | 矿处边缘芯片 | `textures/item/mining_edge_chip.png` | `textures/source/mining_edge_chip.aseprite` | `assets/hypercluster/textures/item/mining_edge_chip.png` |
| 0.2 | 化工边缘芯片 | `textures/item/chemical_edge_chip.png` | `textures/source/chemical_edge_chip.aseprite` | `assets/hypercluster/textures/item/chemical_edge_chip.png` |

### 二、机器正面透明贴图（8 张 `16×16 RGBA`）

| 阶段 | 机器与状态 | 导出贴图（`16×16 RGBA`） | 分层源文件（`.aseprite`） | 目标资源路径（`modid = hypercluster`） |
| :---: | --- | --- | --- | --- |
| 0.1 HV | 微型烧录台（待机） | `textures/block/machines/micro_burner/overlay_front.png` | `textures/source/micro_burner_front.aseprite`（`idle`） | `assets/hypercluster/textures/block/machines/micro_burner/overlay_front.png` |
| 0.1 HV | 微型烧录台（运行） | `textures/block/machines/micro_burner/overlay_front_active.png` | `textures/source/micro_burner_front.aseprite`（`active`） | `assets/hypercluster/textures/block/machines/micro_burner/overlay_front_active.png` |
| 0.1 HV | HV 示范洗矿机（待机） | `textures/block/machines/telemetry_ore_washer/overlay_front.png` | `textures/source/telemetry_ore_washer_front.aseprite`（`idle`） | `assets/hypercluster/textures/block/machines/telemetry_ore_washer/overlay_front.png` |
| 0.1 HV | HV 示范洗矿机（运行） | `textures/block/machines/telemetry_ore_washer/overlay_front_active.png` | `textures/source/telemetry_ore_washer_front.aseprite`（`active`） | `assets/hypercluster/textures/block/machines/telemetry_ore_washer/overlay_front_active.png` |
| 0.2 EV | 遥测化学反应釜（待机） | `textures/block/machines/telemetry_chemical_reactor/overlay_front.png` | `textures/source/telemetry_chemical_reactor_front.aseprite`（`idle`） | `assets/hypercluster/textures/block/machines/telemetry_chemical_reactor/overlay_front.png` |
| 0.2 EV | 遥测化学反应釜（运行） | `textures/block/machines/telemetry_chemical_reactor/overlay_front_active.png` | `textures/source/telemetry_chemical_reactor_front.aseprite`（`active`） | `assets/hypercluster/textures/block/machines/telemetry_chemical_reactor/overlay_front_active.png` |
| 0.2 EV | 数据清洗塔主控（待机） | `textures/block/machines/data_scrubbing_tower/overlay_front.png` | `textures/source/data_scrubbing_tower_front.aseprite`（`idle`） | `assets/hypercluster/textures/block/machines/data_scrubbing_tower/overlay_front.png` |
| 0.2 EV | 数据清洗塔主控（运行） | `textures/block/machines/data_scrubbing_tower/overlay_front_active.png` | `textures/source/data_scrubbing_tower_front.aseprite`（`active`） | `assets/hypercluster/textures/block/machines/data_scrubbing_tower/overlay_front_active.png` |

---

## 视觉规范、轮廓区分与后续复用指南

### 1. 原始遥测日志家族（`*_telemetry_log`）
- **统一轮廓**：`12×14` 竖版右上切角工业数据卡 + 底部 3 组金手指（`card_outline` / `card_body` / `label_bay` 三层逐像素复用）。
- **领域色与徽标**（仅替换 `domain_band` 与 `domain_emblem`）：
  - **矿处（Mining）**：琥珀铜金 `#E58220` / `#FFC745` + 暗石矿脉纹样（`mining_telemetry_log`）
  - **化工（Chemical）**：青绿翠色 `#19B88E` / `#5CF2BC` + 平底锥形烧瓶纹样（`chemical_telemetry_log`）
  - **后续扩展**：装配日志（钴蓝 + 齿轮/焊点回路）、能源日志（绯红/电蓝 + 闪电/能量棒）。

### 2. 数据集模块家族（`clean_dataset` / `high_purity_*_dataset`）与乱码噪点（`corrupted_noise`）
- **对称方盒轮廓（`12×12`, `x=2..13, y=2..13`）**：采用无大斜切、无底舌的规整正方光盘盒廓形，与切角竖版原始日志和横版宽芯片载体一眼区分；3 张数据集 Alpha 外轮廓逐像素一致。
  - `clean_dataset`：浅灰蓝规范方盒（`#E5F0FA` / `#BACFE3` / `#86A2BF`）+ 中央居中主轴蓝银光盘盘面（3 图层：`module_outline` / `module_frame` / `optical_platter`）。
  - `high_purity_mining_dataset` & `high_purity_chemical_dataset`：沿用完全相同的 `12×12` 外轮廓，将外框升级为高反光**亮金边框**（`#FFF5B5` / `#FFD347` / `#DE961B` / `#995B10`），内嵌深色高纯光学腔与橙金矿点 / 青绿烧瓶徽标（4 图层：`module_outline` / `gold_frame` / `dataset_core` / `domain_emblem`）。
- **乱码噪点（`corrupted_noise`）**：打破一切规整容器轮廓，采用横向扫描线撕裂的不规则锯齿碎片造型 + 暗紫/品红故障马赛克与青白静电撕裂线（3 图层：`glitch_outline` / `mosaic_tiles` / `scanline_tear`），在物品栏中一眼识别为待回收废料。

### 3. 载体与边缘芯片家族（`blank_carrier` / `*_edge_chip`）
- **完全一致外轮廓（`14×12`, `x=1..14, y=2..13`）**：`blank_carrier`、`mining_edge_chip`、`chemical_edge_chip` 共享同一套浅陶瓷基板与 Alpha 外轮廓。
  - `blank_carrier`：高明度浅陶瓷基板 + 明显凹陷空白芯片位与未镀金焊盘。
  - `mining_edge_chip` / `chemical_edge_chip`：压装高对比深炭黑硅芯片与两侧/底部金色金属触点，分别以琥珀橙斜纹（`mining_mark`）与化工青绿斜纹（`chemical_mark`）标识领域专精。

### 4. 四台机器正面透明覆盖贴图（均保留外围 2px 100% 透明边距）
所有机器统一沿用 GTCEu Modern `workableTieredHullRenderer` 规范与冷钢色阶（`#141820` / `#3B4557` / `#5A687D` / `#8A99AE` / `#C2D0E0`）、左上统一光照方向，四者轮廓与核心视觉元素完全区分：
- **微型烧录台（`micro_burner`, HV）**：平顶倒角方框 + 双竖导轨压头下压 2px + 底部水平插槽烧录光缝 + 右上角水平状态灯。
- **HV 示范洗矿机（`telemetry_ore_washer`, HV）**：顶部收口漏斗进料口 + 水平螺旋洗矿滚轮 + 蔚蓝冲洗水窗 + 右侧竖排**青 + 琥珀金**遥测采集灯带。
- **遥测化学反应釜（`telemetry_chemical_reactor`, EV）**：顶部实心搅拌电机穹顶（与洗矿机敞口漏斗区分）+ 中央竖向搅拌轴与旋转桨叶 + 翠绿翻涌化学视窗 + 右侧同家族竖排**青 + 化工青绿**遥测采集灯带（与 EV 钛紫机壳形成强互补对比，4 图层：`bezel_frame` / `chemical_bath` / `agitator_stirrer` / `telemetry_strip`）。
- **数据清洗塔主控（`data_scrubbing_tower`, EV 多方块主控）**：重型直角装甲 `12×12` 多方块主控轮廓 + 左右四层对称清洗过滤格栅 + 贯穿纵高的中央**蓝青垂直数据流灯柱**（运行态点亮流动高纯数据包与格栅内缘蓝青反光，3 图层：`bulkhead_frame` / `filter_grilles` / `data_stream_column`）。

# GregTech HyperCluster 0.1 + 0.2 + 0.2.1 + 0.3 贴图与 Aseprite 源文件清单

本目录存放 **0.1（HV 首发闭环）**、**0.2（EV 数据工坊）**、**0.2.1（EV 数据工坊扩展）** 与 **0.3（IV 自建算力 / 智算机房 Tier I–II）** 的全部 **38 张 `16×16` RGBA** Minecraft / GTCEu Modern 贴图与对应的 **29 个分层 `.aseprite` 源文件**，供工程拷贝进 `src/main/resources/assets/hypercluster/textures/`。

全量总览预览图（16 件物品 `4x / 2x / 1x` 槽位对比 + 0.1/0.2/0.2.1/0.3 全部机器叠加 HV 橙金 / EV 钛紫 / IV 蓝机壳对比 + 架空地板 2×2/3×3 无缝平铺与 42U 机柜冷/热面并排对比）见 [contact_sheet.png](textures/previews/contact_sheet.png)。

---

## 交付清单

### 一、物品贴图（16 张 `16×16 RGBA`）

| 阶段 | 中文名 | 导出贴图（`16×16 RGBA`） | 分层源文件（`.aseprite`） | 目标资源路径（`modid = hypercluster`） |
| :---: | --- | --- | --- | --- |
| 0.1 | 矿处遥测日志 | `textures/item/mining_telemetry_log.png` | `textures/source/mining_telemetry_log.aseprite` | `assets/hypercluster/textures/item/mining_telemetry_log.png` |
| 0.2 | 化工遥测日志 | `textures/item/chemical_telemetry_log.png` | `textures/source/chemical_telemetry_log.aseprite` | `assets/hypercluster/textures/item/chemical_telemetry_log.png` |
| 0.2 | 清洁数据集 | `textures/item/clean_dataset.png` | `textures/source/clean_dataset.aseprite` | `assets/hypercluster/textures/item/clean_dataset.png` |
| 0.2 | 高纯矿处数据集 | `textures/item/high_purity_mining_dataset.png` | `textures/source/high_purity_mining_dataset.aseprite` | `assets/hypercluster/textures/item/high_purity_mining_dataset.png` |
| 0.2 | 高纯化工数据集 | `textures/item/high_purity_chemical_dataset.png` | `textures/source/high_purity_chemical_dataset.aseprite` | `assets/hypercluster/textures/item/high_purity_chemical_dataset.png` |
| 0.2.1 | 黄金标注样本 | `textures/item/golden_dataset.png` | `textures/source/golden_dataset.aseprite` | `assets/hypercluster/textures/item/golden_dataset.png` |
| 0.2 | 乱码噪点 | `textures/item/corrupted_noise.png` | `textures/source/corrupted_noise.aseprite` | `assets/hypercluster/textures/item/corrupted_noise.png` |
| 0.1 | 空白载体 | `textures/item/blank_carrier.png` | `textures/source/blank_carrier.aseprite` | `assets/hypercluster/textures/item/blank_carrier.png` |
| 0.1 | 矿处边缘芯片 | `textures/item/mining_edge_chip.png` | `textures/source/mining_edge_chip.aseprite` | `assets/hypercluster/textures/item/mining_edge_chip.png` |
| 0.2.1 | 已对齐矿处边缘芯片 | `textures/item/aligned_mining_edge_chip.png` | `textures/source/aligned_mining_edge_chip.aseprite` | `assets/hypercluster/textures/item/aligned_mining_edge_chip.png` |
| 0.2 | 化工边缘芯片 | `textures/item/chemical_edge_chip.png` | `textures/source/chemical_edge_chip.aseprite` | `assets/hypercluster/textures/item/chemical_edge_chip.png` |
| 0.2.1 | 已对齐化工边缘芯片 | `textures/item/aligned_chemical_edge_chip.png` | `textures/source/aligned_chemical_edge_chip.aseprite` | `assets/hypercluster/textures/item/aligned_chemical_edge_chip.png` |
| 0.2.1 | 矿处 LoRA 卡 | `textures/item/mining_lora_card.png` | `textures/source/mining_lora_card.aseprite` | `assets/hypercluster/textures/item/mining_lora_card.png` |
| 0.2.1 | 化工 LoRA 卡 | `textures/item/chemical_lora_card.png` | `textures/source/chemical_lora_card.aseprite` | `assets/hypercluster/textures/item/chemical_lora_card.png` |
| 0.3 | IV 风冷计算刀片 | `textures/item/iv_compute_blade.png` | `textures/source/iv_compute_blade.aseprite` | `assets/hypercluster/textures/item/iv_compute_blade.png` |
| 0.3 | 绿色零食包 | `textures/item/green_snack_pack.png` | `textures/source/green_snack_pack.aseprite` | `assets/hypercluster/textures/item/green_snack_pack.png` |

### 二、机房结构方块贴图（4 张 `16×16 RGBA` 全不透明）

| 阶段 | 中文名与用途 | 导出贴图（`16×16 RGBA`） | 分层源文件（`.aseprite`） | 目标资源路径（`modid = hypercluster`） |
| :---: | --- | --- | --- | --- |
| 0.3 IV | 防静电架空地板顶面 | `textures/block/datacenter/raised_floor_top.png` | `textures/source/raised_floor_top.aseprite` | `assets/hypercluster/textures/block/datacenter/raised_floor_top.png` |
| 0.3 IV | 防静电架空地板侧面 | `textures/block/datacenter/raised_floor_side.png` | `textures/source/raised_floor_side.aseprite` | `assets/hypercluster/textures/block/datacenter/raised_floor_side.png` |
| 0.3 IV | 42U 算力机柜进风面（冷面） | `textures/block/datacenter/rack_casing_front_intake.png` | `textures/source/rack_casing_front_intake.aseprite` | `assets/hypercluster/textures/block/datacenter/rack_casing_front_intake.png` |
| 0.3 IV | 42U 算力机柜出风面（热面） | `textures/block/datacenter/rack_casing_back_exhaust.png` | `textures/source/rack_casing_back_exhaust.aseprite` | `assets/hypercluster/textures/block/datacenter/rack_casing_back_exhaust.png` |

### 三、机器与仓室正面透明贴图（18 张 `16×16 RGBA`，外围 2px 全透明）

| 阶段 | 机器 / 仓室与状态 | 导出贴图（`16×16 RGBA`） | 分层源文件（`.aseprite`） | 目标资源路径（`modid = hypercluster`） |
| :---: | --- | --- | --- | --- |
| 0.1 HV | 微型烧录台（待机） | `textures/block/machines/micro_burner/overlay_front.png` | `textures/source/micro_burner_front.aseprite`（`idle`） | `assets/hypercluster/textures/block/machines/micro_burner/overlay_front.png` |
| 0.1 HV | 微型烧录台（运行） | `textures/block/machines/micro_burner/overlay_front_active.png` | `textures/source/micro_burner_front.aseprite`（`active`） | `assets/hypercluster/textures/block/machines/micro_burner/overlay_front_active.png` |
| 0.1 HV | HV 示范洗矿机（待机） | `textures/block/machines/telemetry_ore_washer/overlay_front.png` | `textures/source/telemetry_ore_washer_front.aseprite`（`idle`） | `assets/hypercluster/textures/block/machines/telemetry_ore_washer/overlay_front.png` |
| 0.1 HV | HV 示范洗矿机（运行） | `textures/block/machines/telemetry_ore_washer/overlay_front_active.png` | `textures/source/telemetry_ore_washer_front.aseprite`（`active`） | `assets/hypercluster/textures/block/machines/telemetry_ore_washer/overlay_front_active.png` |
| 0.2 EV | 遥测化学反应釜（待机） | `textures/block/machines/telemetry_chemical_reactor/overlay_front.png` | `textures/source/telemetry_chemical_reactor_front.aseprite`（`idle`） | `assets/hypercluster/textures/block/machines/telemetry_chemical_reactor/overlay_front.png` |
| 0.2 EV | 遥测化学反应釜（运行） | `textures/block/machines/telemetry_chemical_reactor/overlay_front_active.png` | `textures/source/telemetry_chemical_reactor_front.aseprite`（`active`） | `assets/hypercluster/textures/block/machines/telemetry_chemical_reactor/overlay_front_active.png` |
| 0.2 EV | 数据清洗塔主控（待机） | `textures/block/machines/data_scrubbing_tower/overlay_front.png` | `textures/source/data_scrubbing_tower_front.aseprite`（`idle`） | `assets/hypercluster/textures/block/machines/data_scrubbing_tower/overlay_front.png` |
| 0.2 EV | 数据清洗塔主控（运行） | `textures/block/machines/data_scrubbing_tower/overlay_front_active.png` | `textures/source/data_scrubbing_tower_front.aseprite`（`active`） | `assets/hypercluster/textures/block/machines/data_scrubbing_tower/overlay_front_active.png` |
| 0.2.1 EV | 神经微调刻录舱主控（待机） | `textures/block/machines/lora_engraving_chamber/overlay_front.png` | `textures/source/lora_engraving_chamber_front.aseprite`（`idle`） | `assets/hypercluster/textures/block/machines/lora_engraving_chamber/overlay_front.png` |
| 0.2.1 EV | 神经微调刻录舱主控（运行） | `textures/block/machines/lora_engraving_chamber/overlay_front_active.png` | `textures/source/lora_engraving_chamber_front.aseprite`（`active`） | `assets/hypercluster/textures/block/machines/lora_engraving_chamber/overlay_front_active.png` |
| 0.2.1 EV | 沙盒红队测试舱主控（待机） | `textures/block/machines/red_team_sandbox/overlay_front.png` | `textures/source/red_team_sandbox_front.aseprite`（`idle`） | `assets/hypercluster/textures/block/machines/red_team_sandbox/overlay_front.png` |
| 0.2.1 EV | 沙盒红队测试舱主控（运行） | `textures/block/machines/red_team_sandbox/overlay_front_active.png` | `textures/source/red_team_sandbox_front.aseprite`（`active`） | `assets/hypercluster/textures/block/machines/red_team_sandbox/overlay_front_active.png` |
| 0.3 IV | 风冷算力机柜主控（待机） | `textures/block/machines/air_cooled_compute_rack/overlay_front.png` | `textures/source/air_cooled_compute_rack_front.aseprite`（`idle`） | `assets/hypercluster/textures/block/machines/air_cooled_compute_rack/overlay_front.png` |
| 0.3 IV | 风冷算力机柜主控（运行） | `textures/block/machines/air_cooled_compute_rack/overlay_front_active.png` | `textures/source/air_cooled_compute_rack_front.aseprite`（`active`） | `assets/hypercluster/textures/block/machines/air_cooled_compute_rack/overlay_front_active.png` |
| 0.3 IV | 推理协处理器仓（待机） | `textures/block/machines/inference_hatch/overlay_front.png` | `textures/source/inference_hatch_front.aseprite`（`idle`） | `assets/hypercluster/textures/block/machines/inference_hatch/overlay_front.png` |
| 0.3 IV | 推理协处理器仓（运行） | `textures/block/machines/inference_hatch/overlay_front_active.png` | `textures/source/inference_hatch_front.aseprite`（`active`） | `assets/hypercluster/textures/block/machines/inference_hatch/overlay_front_active.png` |
| 0.3 IV | 机房精密空调主控（待机） | `textures/block/machines/precision_crac/overlay_front.png` | `textures/source/precision_crac_front.aseprite`（`idle`） | `assets/hypercluster/textures/block/machines/precision_crac/overlay_front.png` |
| 0.3 IV | 机房精密空调主控（运行） | `textures/block/machines/precision_crac/overlay_front_active.png` | `textures/source/precision_crac_front.aseprite`（`active`） | `assets/hypercluster/textures/block/machines/precision_crac/overlay_front_active.png` |

---

## 视觉规范、轮廓区分与复用说明

### 1. 0.1–0.2.1 物品家族（日志 / 数据集 / 噪点 / 芯片 / LoRA 卡）
- **原始遥测日志（`*_telemetry_log`）**：`12×14` 竖版切角数据卡 + 底部 3 组金手指（`card_outline` / `card_body` / `label_bay` 三层复用；矿处为琥珀铜金+矿脉，化工为青绿+锥形烧瓶）。
- **数据集模块（`clean_dataset` / `high_purity_*_dataset` / `golden_dataset`）**：`12×12` 对称正方光盘盒廓形，Alpha 外轮廓逐像素一致；`clean_dataset` 为浅灰蓝方盒+蓝银光盘，高纯版换装亮金边框+橙金矿点/青绿烧瓶；**0.2.1 `golden_dataset`** 升级为通体流金金属外壳 + 中央亮白十字星标高光（3 图层：`module_outline` / `golden_casing` / `star_emblem`），与普通/高纯版一眼拉开稀有度层级。
- **乱码噪点（`corrupted_noise`）**：不规则横向扫描线撕裂锯齿碎片 + 暗紫/品红故障马赛克与青白静电撕裂线。
- **载体、边缘芯片与已对齐芯片（`blank_carrier` / `*_edge_chip` / `aligned_*_edge_chip`）**：`14×12` 横版陶瓷基板；空载体浅色中空芯片槽，未对齐芯片压装深炭黑硅片+金触点+琥珀/青绿领域斜纹；**0.2.1 `aligned_*_edge_chip`** 复用同款芯片底图并叠加独立 `alignment_shield_seal` 图层（6 图层：`carrier_outline` / `carrier_substrate` / `metal_contacts` / `dark_ic_chip` / `mining_mark` 或 `chemical_mark` / `alignment_shield_seal`），在左上角增加青绿认证封条高光、右下角压印带亮白勾选符（`✓`）的青绿校验盾标，整图色数严格控制在 ≤16 色，物品栏 `1×` 缩放下亦可瞬间区分对齐前后。
- **0.2.1 LoRA 扩展插卡（`mining_lora_card` / `chemical_lora_card`）**：独立 `10×14` 窄长竖向扩展卡廓形（左上角斜切定位角 + 右侧外探 4 针金手指排针），彻底区别于底部金手指的数据卡、正方数据集和横版陶瓷芯片；两张卡 Alpha 外轮廓逐像素一致，仅切换 `domain_emblem` 为矿处橙金 / 化工青绿（4 图层：`card_outline` / `card_pcb` / `side_pins` / `domain_emblem`）。

### 2. 0.3 IV 核心物品
- **IV 风冷计算刀片（`iv_compute_blade`）**：修长横版 `14×10` T 型热插拔服务器刀片廓形（左侧带锁扣拉手与幽蓝状态灯，上部 4 道银灰铝合金散热鳍片，下部深灰 PCB + 幽蓝电路纹，右侧 3 组后端背板金手指），与竖卡、方盒、芯片板及 LoRA 竖卡轮廓完全区分（5 图层：`blade_outline` / `blade_chassis` / `heatsink_fins` / `blue_circuitry` / `gold_fingers`）。
- **绿色零食包（`green_snack_pack`）**：`12×14` 鼓胀枕式鲜绿色铝箔包装袋 + 上下清晰白色锯齿压纹封口 + 正面暖黄复古笑脸小徽标，致敬机房镇宅玄学彩蛋（4 图层：`pouch_outline` / `green_foil_body` / `white_crimped_seals` / `front_label`）。

### 3. 0.3 IV 机房结构方块（`block/datacenter/`）
- **防静电架空地板（`raised_floor_top` / `raised_floor_side`）**：
  - 顶面为四象限冷灰金属镂空防滑格栅，沿 `x=7`、`y=7` 铺设贯通边缘的细微青色 ESD 接地总线，2×2 / 3×3 无缝平铺可自然连接成连续机房防静电接地网格。
  - 侧面顶部 4px 为带青色接地边条的薄金属踏板，下方为深暗色镂空走线腔、双钢制可调支架腿、横向无缝连杆与隐约可见的底层光纤/电缆托架。
- **42U 算力机柜冷/热面（`rack_casing_front_intake` / `rack_casing_back_exhaust`）**：
  - 共享统一 42U 深灰黑外框，但内部肌理与通道标识完全对立：进风冷面采用细密穿孔网门 + 右侧竖拉手 + 顶部**冰蓝色**冷通道标识条；出风热面采用双列粗犷水平散热百叶格栅（内槽带微弱暗红热腔阴影）+ 顶部**橙红色**热通道标识条，防止玩家摆反冷热通道。

### 4. 机器与仓室正面透明覆盖贴图（均保留外围 2px 100% 透明）
全部遵循 GTCEu Modern `workableTieredHullRenderer` 规范与统一冷钢色阶、左上主光照方向：
- **0.1 HV**：`micro_burner`（平顶方框压头烧录槽）、`telemetry_ore_washer`（收口漏斗+螺旋滚轮水窗+右侧青/橙遥测条）。
- **0.2 EV**：`telemetry_chemical_reactor`（实心搅拌穹顶+竖轴搅拌桨+翠绿翻涌液面+右侧青/青绿遥测条）、`data_scrubbing_tower`（直角装甲主控+四层对称格栅+中央蓝青垂直数据流灯柱）。
- **0.2.1 EV（适配 GTCEu EV 紫色机壳）**：
  - **`lora_engraving_chamber`**：八角倒角光学刻录舱，顶部聚光透镜组件 + 底部晶圆载台；运行态透镜泛青白强光并向下射出收敛青金激光刻录束，晶圆中心焦斑高亮爆发，顶部点亮青/橙金双侧指示灯（2 图层：`chamber_bezel` / `optics_and_stage`）。
  - **`red_team_sandbox`**：重型直角防爆外框 + 中部防爆观察窗 + 底部斜向黄黑工业警示条 + 顶部校验状态条；运行态窗内亮起连续锯齿状紫红对抗波形，顶部亮起鲜明翠绿校验通过灯（3 图层：`blast_bulkhead` / `hazard_stripe` / `window_and_pass_lamp`）。
- **0.3 IV（适配 GTCEu IV 蓝色机壳）**：
  - **`air_cooled_compute_rack`**：4 层独立刀片插槽与左侧拉手，运行态点亮幽蓝刀片 LED/进风微光与底部整条青色 `CWU/t` 算力指示条（2 图层：`rack_bezel` / `blade_slots_and_leds`）。
  - **`inference_hatch`**：经典八角倒角多方块仓室轮廓，左区芯片插槽 + 右区圆形光纤接口，待机暗灰，运行态点亮左侧橙金运算灯/芯片核心与右侧青色光纤核心（2 图层：`hatch_frame` / `chip_and_optical_ports`）。
  - **`precision_crac`**：顶部温控数字小屏 + 右上状态灯，下方大口径圆形工业出风风机，运行态切换风机旋转相位并点亮冰蓝冷风高光与状态屏（3 图层：`crac_bezel` / `temp_screen` / `blower_fan`）。

# GregTech HyperCluster 0.1 + 0.2 + 0.2.1 + 0.3 + 0.4 贴图与 Aseprite 源文件清单

本目录存放 **0.1（HV 首发闭环）**、**0.2（EV 数据工坊）**、**0.2.1（EV 数据工坊扩展）**、**0.3（IV 自建算力 / 智算机房 Tier I–II）** 与 **0.4（IV 闭源云 / API 卫星地面站与 Token 计费）** 的全部 **47 张 `16×16` RGBA 基础贴图**、**5 张 `32×32` RGBA LDLib 2×2 CTM 衔接图集**（合计 **52 张贴图**）与对应的 **41 个分层 `.aseprite` 源文件**（36 个 `16×16` + 5 个 `32×32`），供工程拷贝进 `src/main/resources/assets/hypercluster/textures/`。

- 基础贴图全量总览（18 件物品 `4x / 2x / 1x` 槽位对比 + 0.1–0.4 全部机器叠加 HV 橙金 / EV 钛紫 / IV 蓝机壳对比 + 架空地板/机柜冷热面 + 0.4 卫星站钢架/反射面板/馈源接收头预览）见 [contact_sheet.png](textures/previews/contact_sheet.png)。
- 5 个结构方块的 **LDLib 2×2 CTM 衔接图集与 3×3 / T 型 / L 型 / 多方块立面实测拼接预览**见 [ctm_preview.png](textures/previews/ctm_preview.png)。

---

## 交付清单

### 一、物品贴图（18 张 `16×16 RGBA`）

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
| 0.4 | Token 充值晶卡 | `textures/item/token_credit_card.png` | `textures/source/token_credit_card.aseprite` | `assets/hypercluster/textures/item/token_credit_card.png` |
| 0.4 | API 凭证卡 | `textures/item/api_credential_card.png` | `textures/source/api_credential_card.aseprite` | `assets/hypercluster/textures/item/api_credential_card.png` |

### 二、结构方块贴图（7 张 `16×16 RGBA` 全不透明：0.3 机房 4 张 + 0.4 卫星站 3 张）

| 阶段 | 中文名与用途 | 导出贴图（`16×16 RGBA`） | 分层源文件（`.aseprite`） | 目标资源路径（`modid = hypercluster`） |
| :---: | --- | --- | --- | --- |
| 0.3 IV | 防静电架空地板顶面 | `textures/block/datacenter/raised_floor_top.png` | `textures/source/raised_floor_top.aseprite` | `assets/hypercluster/textures/block/datacenter/raised_floor_top.png` |
| 0.3 IV | 防静电架空地板侧面 | `textures/block/datacenter/raised_floor_side.png` | `textures/source/raised_floor_side.aseprite` | `assets/hypercluster/textures/block/datacenter/raised_floor_side.png` |
| 0.3 IV | 42U 算力机柜进风面（冷面） | `textures/block/datacenter/rack_casing_front_intake.png` | `textures/source/rack_casing_front_intake.aseprite` | `assets/hypercluster/textures/block/datacenter/rack_casing_front_intake.png` |
| 0.3 IV | 42U 算力机柜出风面（热面） | `textures/block/datacenter/rack_casing_back_exhaust.png` | `textures/source/rack_casing_back_exhaust.aseprite` | `assets/hypercluster/textures/block/datacenter/rack_casing_back_exhaust.png` |
| 0.4 IV | 卫星站钢架方块（四边无缝） | `textures/block/cloud/satellite_dish_frame.png` | `textures/source/satellite_dish_frame.aseprite` | `assets/hypercluster/textures/block/cloud/satellite_dish_frame.png` |
| 0.4 IV | 抛物面天线反射面板 | `textures/block/cloud/satellite_dish_panel.png` | `textures/source/satellite_dish_panel.aseprite` | `assets/hypercluster/textures/block/cloud/satellite_dish_panel.png` |
| 0.4 IV | 高频馈源接收头 | `textures/block/cloud/satellite_feed_horn.png` | `textures/source/satellite_feed_horn.aseprite` | `assets/hypercluster/textures/block/cloud/satellite_feed_horn.png` |

### 三、机器与仓室正面透明贴图（22 张 `16×16 RGBA`，外围 2px 全透明）

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
| 0.4 IV | API 卫星地面站主控（待机） | `textures/block/machines/api_satellite_station/overlay_front.png` | `textures/source/api_satellite_station_front.aseprite`（`idle`） | `assets/hypercluster/textures/block/machines/api_satellite_station/overlay_front.png` |
| 0.4 IV | API 卫星地面站主控（运行） | `textures/block/machines/api_satellite_station/overlay_front_active.png` | `textures/source/api_satellite_station_front.aseprite`（`active`） | `assets/hypercluster/textures/block/machines/api_satellite_station/overlay_front_active.png` |
| 0.4 IV | 本地 Fallback 兜底网关（待机） | `textures/block/machines/local_fallback_gateway/overlay_front.png` | `textures/source/local_fallback_gateway_front.aseprite`（`idle`） | `assets/hypercluster/textures/block/machines/local_fallback_gateway/overlay_front.png` |
| 0.4 IV | 本地 Fallback 兜底网关（运行） | `textures/block/machines/local_fallback_gateway/overlay_front_active.png` | `textures/source/local_fallback_gateway_front.aseprite`（`active`） | `assets/hypercluster/textures/block/machines/local_fallback_gateway/overlay_front_active.png` |

### 四、LDLib 2×2 CTM 衔接图集（5 张 `32×32 RGBA` 全不透明）

| 阶段 | 中文名与拼接效果 | 导出图集（`32×32 RGBA`） | 分层源文件（`.aseprite`） | 目标资源路径（`modid = hypercluster`） |
| :---: | --- | --- | --- | --- |
| 0.3 IV | 防静电架空地板顶面 CTM | `textures/block/datacenter/raised_floor_top_ctm.png` | `textures/source/raised_floor_top_ctm.aseprite` | `assets/hypercluster/textures/block/datacenter/raised_floor_top_ctm.png` |
| 0.3 IV | 42U 算力机柜进风面（冷面）CTM | `textures/block/datacenter/rack_casing_front_intake_ctm.png` | `textures/source/rack_casing_front_intake_ctm.aseprite` | `assets/hypercluster/textures/block/datacenter/rack_casing_front_intake_ctm.png` |
| 0.3 IV | 42U 算力机柜出风面（热面）CTM | `textures/block/datacenter/rack_casing_back_exhaust_ctm.png` | `textures/source/rack_casing_back_exhaust_ctm.aseprite` | `assets/hypercluster/textures/block/datacenter/rack_casing_back_exhaust_ctm.png` |
| 0.4 IV | 卫星站钢架方块 CTM | `textures/block/cloud/satellite_dish_frame_ctm.png` | `textures/source/satellite_dish_frame_ctm.aseprite` | `assets/hypercluster/textures/block/cloud/satellite_dish_frame_ctm.png` |
| 0.4 IV | 抛物面天线反射面板 CTM | `textures/block/cloud/satellite_dish_panel_ctm.png` | `textures/source/satellite_dish_panel_ctm.aseprite` | `assets/hypercluster/textures/block/cloud/satellite_dish_panel_ctm.png` |

---

## 视觉规范、轮廓区分与复用说明

### 1. 0.1–0.2.1 物品家族（日志 / 数据集 / 噪点 / 芯片 / LoRA 卡）
- **原始遥测日志（`*_telemetry_log`）**：`12×14` 竖版切角数据卡 + 底部 3 组金手指（`card_outline` / `card_body` / `label_bay` 三层复用；矿处为琥珀铜金+矿脉，化工为青绿+锥形烧瓶）。
- **数据集模块（`clean_dataset` / `high_purity_*_dataset` / `golden_dataset`）**：`12×12` 对称正方光盘盒廓形，Alpha 外轮廓逐像素一致；`clean_dataset` 为浅灰蓝方盒+蓝银光盘，高纯版换装亮金边框+橙金矿点/青绿烧瓶；**0.2.1 `golden_dataset`** 升级为通体流金金属外壳 + 中央亮白十字星标高光（3 图层：`module_outline` / `golden_casing` / `star_emblem`），与普通/高纯版一眼拉开稀有度层级。
- **乱码噪点（`corrupted_noise`）**：不规则横向扫描线撕裂锯齿碎片 + 暗紫/品红故障马赛克与青白静电撕裂线。
- **载体、边缘芯片与已对齐芯片（`blank_carrier` / `*_edge_chip` / `aligned_*_edge_chip`）**：`14×12` 横版陶瓷基板；空载体浅色中空芯片槽，未对齐芯片压装深炭黑硅片+金触点+琥珀/青绿领域斜纹；**0.2.1 `aligned_*_edge_chip`** 复用同款芯片底图并叠加独立 `alignment_shield_seal` 图层（6 图层：`carrier_outline` / `carrier_substrate` / `metal_contacts` / `dark_ic_chip` / `mining_mark` 或 `chemical_mark` / `alignment_shield_seal`），在左上角增加青绿认证封条高光、右下角压印带亮白勾选符（`✓`）的青绿校验盾标，整图色数严格控制在 ≤16 色，物品栏 `1×` 缩放下亦可瞬间区分对齐前后。
- **0.2.1 LoRA 扩展插卡（`mining_lora_card` / `chemical_lora_card`）**：独立 `10×14` 窄长竖向扩展卡廓形（左上角斜切定位角 + 右侧外探 4 针金手指排针），彻底区别于底部金手指的数据卡、正方数据集和横版陶瓷芯片；两张卡 Alpha 外轮廓逐像素一致，仅切换 `domain_emblem` 为矿处橙金 / 化工青绿（4 图层：`card_outline` / `card_pcb` / `side_pins` / `domain_emblem`）。

### 2. 0.3 / 0.4 IV 核心物品
- **IV 风冷计算刀片（`iv_compute_blade`）**：修长横版 `14×10` T 型热插拔服务器刀片廓形（左侧带锁扣拉手与幽蓝状态灯，上部 4 道银灰铝合金散热鳍片，下部深灰 PCB + 幽蓝电路纹，右侧 3 组后端背板金手指），与竖卡、方盒、芯片板及 LoRA 竖卡轮廓完全区分（5 图层：`blade_outline` / `blade_chassis` / `heatsink_fins` / `blue_circuitry` / `gold_fingers`）。
- **绿色零食包（`green_snack_pack`）**：`12×14` 鼓胀枕式鲜绿色铝箔包装袋 + 上下清晰白色锯齿压纹封口 + 正面暖黄复古笑脸小徽标，致敬机房镇宅玄学彩蛋（4 图层：`pouch_outline` / `green_foil_body` / `white_crimped_seals` / `front_label`）。
- **0.4 Token 充值晶卡（`token_credit_card`）**：`14×10` 横版斜切角科技晶卡廓形（右上/左下 2px 科技晶卡切角），采用高饱和暖色系琥珀金—深古铜色相偏移色板（9 色），与全部冷钢/冷蓝数据卡一眼区分；中央深古铜嵌入式芯片槽内点亮纯白高光亮金 `T` 字 Token 芯片徽标，左右辅以金色微电路走线（4 图层：`card_outline` / `amber_crystal_body` / `circuit_accent_lines` / `token_chip_emblem`）。
- **0.4 API 凭证卡（`api_credential_card`）**：`14×10` 横版圆角安全密钥卡廓形，深靛蓝暗夜卡体配上下黑金金属饰条与右侧 2×2 金色安全芯片触点，左侧纯白镂空钥匙图标、底部居中对称白色分段加密条纹（12 色），供后续多凭证池与 403 熔断体系使用（4 图层：`card_outline` / `indigo_card_body` / `gold_security_trim` / `key_and_crypto_stripes`）。

### 3. 0.3 / 0.4 IV 结构方块（`block/datacenter/` 与 `block/cloud/`）
- **0.3 防静电架空地板（`raised_floor_top` / `raised_floor_side`）**：
  - 顶面为四象限冷灰金属镂空防滑格栅，沿 `x=7`、`y=7` 铺设贯通边缘的细微青色 ESD 接地总线，2×2 / 3×3 无缝平铺可自然连接成连续机房防静电接地网格。
  - 侧面顶部 4px 为带青色接地边条的薄金属踏板，下方为深暗色镂空走线腔、双钢制可调支架腿、横向无缝连杆与隐约可见的底层光纤/电缆托架。
- **0.3 42U 算力机柜冷/热面（`rack_casing_front_intake` / `rack_casing_back_exhaust`）**：
  - 共享统一 42U 深灰黑外框，但内部肌理与通道标识完全对立：进风冷面采用细密穿孔网门 + 右侧竖拉手 + 顶部**冰蓝色**冷通道标识条；出风热面采用双列粗犷水平散热百叶格栅（内槽带微弱暗红热腔阴影）+ 顶部**橙红色**热通道标识条，防止玩家摆反冷热通道。
- **0.4 卫星站钢架方块（`satellite_dish_frame`）**：
  - 全不透明 `16×16` 浅灰+深钨钢交叉斜撑（X 型）重型桁架（9 色），外围 2px 工字钢梁带四角铆钉与 `x=7 / y=7` 中段接缝刻度，中心配对称螺栓节点板；2×2 与 3×3 四边无缝平铺可连续拼成卫星基座塔架（3 图层：`recess_cavity` / `cross_bracing` / `outer_girder_frame`）。
- **0.4 抛物面天线反射面板（`satellite_dish_panel`）**：
  - 全不透明 `16×16` 极简冷白航天金属板（10 色），叠加居中对称细微同心抛物面弧线与贯通 `x=7 / y=7` 的柔和浅青精密格线，与下方钢架竖缝逐像素对齐，3×3 / 5×5 拼接呈现干净的冷白抛物面天线盘面（3 图层：`metal_plate` / `cyan_grid_lines` / `concentric_arcs`）。
- **0.4 高频馈源接收头（`satellite_feed_horn`）**：
  - 全不透明 `16×16` 卫星锅中心朝天焦点方块（11 色）：底部为带对称螺栓与散热槽的深灰重金属底座，中段为阶梯波导领环，顶部为高对比发光青色同轴射频收发环与中心高频发射头，在冷白反射面板中心构成醒目视觉锚点（3 图层：`dark_metal_base` / `waveguide_collar` / `cyan_rf_ring`）。

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
- **0.4 IV（适配 GTCEu IV 蓝色机壳）**：
  - **`api_satellite_station`**：顶部 1px 切角控制面板 + 宽底刷卡底座；上方宽屏左区为倾斜小型抛物面天线与双层上行波纹，右区为 3 格递增独立垂直信号条，底部为水平横向 Token 读卡插槽。待机暗态可读轮廓（8 色），运行态右侧信号格亮起鲜明青绿色，天线波纹与底部读卡口亮起高亮琥珀金（14 色；3 图层：`station_bezel` / `antenna_and_signal` / `token_card_slot`）。
  - **`local_fallback_gateway`**：重型 `12×12` 全直角网络网关面板，顶部左右分置主路（云端）/ 备路（本地）双状态指示灯，下方暗色路由矩阵内绘制 Y 型双向上行分流箭头直指两侧通道。待机暗态清晰可辨（8 色），运行态同步点亮左侧青蓝云端主路灯/左分支与右侧琥珀金本地备路灯/右分支（13 色；3 图层：`gateway_bezel` / `dual_status_leds` / `routing_arrows`）。

### 5. LDLib 2×2 CTM 衔接图集规范与校验（`32×32 RGBA` 全不透明）
5 张 CTM 图集均严格复用了各自基础 `16×16` `.aseprite` 的色板（零新增杂色）、左上光照与分层结构，并已直接对照本工程锁定的 **LDLib `1.0.40.b`（`Connections.java` / `Submap.java`）** 与 **GTCEu `7.4.0` 官方 `*_ctm.png`** 字节码与 UV 索引完成校验：
- **标准 LDLib 2×2 象限映射（4 个 `16×16` 瓦片）**：
  - 左上 `(0, 0)`：`V && H && D`（`submap 0, 1, 4, 5`）—— 四周全连通的**内部无缝块**（无外边框）。
  - 右上 `(16, 0)`：`V && !H`（`submap 2, 3, 6, 7`）—— 仅**上下垂直连通**、左右有外边框（用于竖柱及大面左右边缘）。
  - 左下 `(0, 16)`：`!V && H`（`submap 8, 9, 12, 13`）—— 仅**左右水平连通**、上下有外边框（用于横梁及大面上下边缘；42U 机柜的冰蓝/橙红通道标识条仅位于此象限与基础贴图的顶边）。
  - 右下 `(16, 16)`：`V && H && !D`（`submap 10, 11, 14, 15`）—— 四边均连通但**内角缺失（四角点）**，用于拼出 T 型 / L 型凹角与内转角封口。
  - 孤立单块与连续大面的四个外拐角（`!V && !H`，`submap 16..19`）由 LDLib 自动采样对应 `16×16` 基础贴图的四个 `8×8` 象限，因此各 CTM 图集均在 `x=7|8`、`y=7|8` 接缝处与基础贴图逐像素咬合。
- **各方块 CTM 特征**：
  - `raised_floor_top_ctm`（3 图层，9 色）：内部无缝贯通防滑穿孔格栅与连续青色 ESD 接地总线，仅外围保留亮/暗金属收边框。
  - `rack_casing_front_intake_ctm`（3 图层，7 色 ⊆ 基础 9 色）：内部连成一整面同相位深灰黑穿孔网门，外框为重型 42U 边框，右侧延伸连续拉手竖条，冰蓝色冷通道标识条仅出现在顶边（并在 L 型凹角处带暗槽封口）。
  - `rack_casing_back_exhaust_ctm`（3 图层，7 色 ⊆ 基础 10 色）：内部连成整面同相位水平散热百叶与微暗红热腔，仅外缘保留重型背板边框，橙红色热通道标识条仅出现在顶边（L 型凹角带暗槽封口）。
  - `satellite_dish_frame_ctm`（3 图层，9 色）：外缘保留完整 2px 钨钢主梁边框，内部去除重复粗边，45° 交叉斜撑自然延伸并在四块交汇处组成与中心完全同构的 4×4 螺栓节点板，拼出连续航天钻石桁架。
  - `satellite_dish_panel_ctm`（3 图层，9 色 ⊆ 基础 10 色）：内部连成极简纯净冷白反射面板、无断层淡青细格线与对齐的微弱同心校准弧，外缘统一收出 2px 冷钢金属框线。
- 接入工程时只需为对应的基础 `16×16` 贴图配置标准 `.png.mcmeta`（`{"ldlib": {"connection": "hypercluster:block/<dir>/<name>_ctm"}}`）即可与 LDLib 渲染管线原生联动；完整 3×3 / T 型 / L 型 / 条带 / 多方块立面效果见 [ctm_preview.png](textures/previews/ctm_preview.png)。

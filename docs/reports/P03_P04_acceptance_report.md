# P03 + P04 验收报告

- 日期：2026-11-26
- 代码基线：`main`（P03+P04 提交），Minecraft `1.20.1` / Forge `47.4.0` / GTCEu Modern `7.4.0`（slim 构件）
- 前置结论来源：[P01_P02_acceptance_report.md](P01_P02_acceptance_report.md)（P02 决策：0.1 采用附属自有 HV 示范矿机）
- 回归入口：`./gradlew runGametest`（**17 个 GameTest 全部通过**：P01/P02 基线 7 个 + P03/P04 新增 10 个）
- 原始证据：本目录 [p03_p04_probe_evidence.log](p03_p04_probe_evidence.log)；P02 基线证据 [p02_probe_evidence.log](p02_probe_evidence.log)

---

## 任务一 P03：HV 微型烧录台 + 芯片省水洗矿配方

### 1. HV 微型烧录台（`hypercluster:hv_micro_burner`）

- 机器：GTCEu `SimpleTieredMachine`（HV 档位）+ `OC_NON_PERFECT`，标准机器窗口（烧录配方 UI + 电池槽）、能量与配方逻辑全部复用 GTCEu；注册于 `dev/hypercluster/common/data/HyperClusterMachines.java`。
- 贴图：`Public/textures/block/machines/micro_burner/overlay_front(.png / _active.png)` 接入 `src/main/resources/assets/hypercluster/textures/block/machines/micro_burner/`；`runData` 生成的模型数据：HV 外壳 + `overlay_front`（idle/suspend）与 `overlay_front_active`（working/waiting）变体齐备（见 `src/generated/resources/assets/hypercluster/models/block/machine/hv_micro_burner.json`）。
- 专属配方类型 `hypercluster:micro_burner`（`HyperClusterRecipeTypes`，IO 2物品入/1物品出，EU IN）。首版烧录配方：

| 配方 | 输入 | 输出 | 电力 |
| --- | --- | --- | --- |
| `hypercluster:micro_burner/burn_mining_edge_chip` | 1×矿处遥测日志 + 1×空白载体 | 1×矿处边缘芯片 | HV 档 512 EU/t × 128 t（约 65,536 EU，HV 机器无超频空间） |

### 2. 芯片收益对照配方（以 P01 基准配方为参照）

基准（P01 记录，`gtceu:hv_ore_washer` 实测）：`wash_copper_crushed_ore_to_purified_ore` = 1×破碎铜矿石 + 编程电路 #1 + 1000 mB 水 → 1×纯净破碎铜矿石 + 1×石粉（1/3 钴粉副产）。

新配方（注册在附属自有类型 `hypercluster:telemetry_ore_washing` 内、由 `hv_telemetry_ore_washer` 运行）：

| 配方 ID | 输入 | 流体 | 输出 |
| --- | --- | --- | --- |
| `hypercluster:telemetry_ore_washing/wash_copper_crushed_ore_to_purified_ore`（基础镜像） | 1×破碎铜矿石 + 编程电路 **#1** | 1000 mB 水 | 1×纯净破碎铜矿石 + 1×石粉（1/3 钴粉） |
| `hypercluster:telemetry_ore_washing/wash_copper_crushed_ore_to_purified_ore_chip`（芯片增强） | 1×破碎铜矿石 + **1×矿处边缘芯片（notConsumable，可取回）** + 编程电路 **#2** | **100 mB 水（省水 90%）** | 与基础版完全一致 |

- **严格隔离机制**：与计划的“明确的编程电路号”方案一致 —— 基础版电路 #1、芯片版电路 #2，同一输入永不悄悄命中错误配方。GameTest `baseAndChipRecipesStrictlySeparated` 实测：输入槽同时放芯片且电路为 #1 时，机器只会跑基础版（耗 1000 mB、芯片原位保留）；`chipWashUsesChipAndLessWater` 实测电路 #2 + 芯片时只跑芯片版（恰好耗 100 mB、芯片完好）。
- JEI 对照：两类配方同属一个机器与配方类型，输入/流体差异一目了然（新配方不需要玩家自备电路的定义歧义 — 两配方都以电路号作显式选择器）。

### 3. P03 其余交付

- 语言：机器名/工具提示/配方类型名（`hypercluster.micro_burner`、`hypercluster.telemetry_ore_washing`）双语补齐，`runData` 生成 en_us/en_ud，zh_cn 手工维护。
- `./gradlew runData` ✓、`./gradlew build` ✓。

## 任务二 P04：附属自有 HV 示范洗矿机 + 精确遥测结算与持久化

### 1. 示范洗矿机（`hypercluster:hv_telemetry_ore_washer`）

- 继承 `SimpleTieredMachine`（HV），同一机器同时运行基础洗矿镜像配方与芯片省水配方（见上表）。
- 贴图：`Public/` 尚未提供 `telemetry_ore_washer/` 素材 —— 按 P04 条款以 micro_burner 的 overlay 临时占位接入 `assets/hypercluster/textures/block/machines/telemetry_ore_washer/`（正式素材到位后替换同名文件即可）。

### 2. 精确结算（`TelemetryRecipeLogic`，无 Mixin）

- `TelemetryOreWasherMachine` 覆写 `createRecipeLogic()` 返回自类。子类完整复制 7.4.0 的 `RecipeLogic#onRecipeFinish()` 并只加一处修改：捕获原本被忽略的 `handleRecipeIO(lastRecipe, IO.OUT)` 结果，**仅当（a）输出真实落槽成功 且（b）配方属于 hypercluster 白名单且含常规产物** 时，向缓冲写入 1 份 `hypercluster:mining_telemetry_log`。完全不走 `afterWorking()` 计数。
- 被排除情形（结构保证 + 实测确认）：堵仓（IO.OUT 失败 0 份）、取消（`onRecipeFinish` 根本不会运行，0 份）、断电未完、空转、无常规产物配方。
- 已知的 7.4.0 子类化副作用：收尾失败时无法更新私有的 `waitingReason`（仅 GUI 状态文字，不影响结算与加工行为）。

### 3. 日志缓冲与生命周期

- 缓冲：`CustomItemStackHandler`（@Persisted，随方块实体 NBT 持久化），容量 64（一整叠），GUI 中为只取不存的槽位；悬停显示 `缓冲: n / 64` 且满时显示“缓冲已满，新批次日志将丢弃”。来源 `TelemetryOreWasherMachine`。
- 缓冲满时机器照常加工（GameTest `fullBufferKeepsWorking`：预置 64 后连跑 3 批，缓冲保持 64、产物 3、输入耗尽，3 份日志按要求丢弃）。
- 拆除机器：`onMachineRemoved()` 复用 GT 的 `clearInventory` 把缓冲内日志一次性弹出到世界，重装从空缓冲开始（同一存储语义：电池/输入/输出亦如此）。
- 持久化：以 `BlockEntity#saveWithFullMetadata` + `BlockEntity#load` 在测试内模拟保存/重载/区块卸载路径：3 份日志 → 清空 → 读档成 3，重复读同一存档仍为 3（**不翻倍**），存档 NBT 中包含 `hypercluster:mining_telemetry_log`。

### 4. Forge GameTest 回归（`./gradlew runGametest`，17/17 通过）

| # | 场景 | 判定 | 实测细节 |
| --- | --- | --- | --- |
| S1 | 连续 10 批正常完成 | **恰好 10 份日志**，产物 10/10 | `tenSettledBatchesTenLogs` |
| S2 | 输出堵仓 | **0 份日志**，产物丢失、机器停转、输入残留 1 批 | `jammedOutputZeroLogs`（buffer=0） |
| S3 | 中途取消 | **0 份日志**，产物 0、输入已耗 | `interruptedBatchZeroLogs`（interrupt 时 progress=0/0、buffer=0，机器 IDLE） |
| S4 | 断电→恢复 | **恰好 1 份日志**，产物 1/1 | `powerLossOneLog`（断电期间 WAITING，恢复后完成并结算） |
| S5 | 两机同 tick | **各 1 份，不串机** | `twoWashersSameTick`：逐 tick 采样证明两机在同一 gameTime（12715）入账，此前任意 tick 双方均为 0 |
| S6 | 缓冲满 | 机器继续工作，**不超存**（64/64），3 批照跑、3 份日志丢弃 | `fullBufferKeepsWorking` |
| S7 | 卸载/重启持久化 | 3 份日志存档→清空→重载回 3；**重复读档不翻倍** | `bufferPersistenceNoDuplication` |
| S8 | 烧录台闭环 | 消耗 1 日志 + 1 载体 → 产出 1 芯片，输入空 | `microBurnerProducesChip` |
| S9 | 芯片省水配方 | **100 mB 水**恰好耗尽；芯片 `notConsumable` 完好可取回；产物 1/1；1 份日志 | `chipWashUsesChipAndLessWater` |
| S9b | 基础/芯片严格隔离 | 电路 #1 + 芯片在场只跑基础版（水余 100/1100、芯片完好、1 份日志） | `baseAndChipRecipesStrictlySeparated` |

另：P01/P02 的 7 个基线测试（物品注册/堆叠/工具提示、GT 洗矿基准、P02 探针 5 场景）在本次回归中全部保持通过。

### 5. 复现步骤

```bash
source ./env.sh
./gradlew runData        # 生成机器模型/语言（含两台机器 blockstate+model）
./gradlew build          # 打包
./gradlew runGametest    # 17 个 GameTest 全部通过（判定见上表）
grep "HC-P03\|HC-P04" run/gametest/logs/latest.log   # 或本目录 p03_p04_probe_evidence.log
```

### 6. 已知边界与移交事项

- `telemetry_ore_washer` 正面贴图为 micro_burner 临时占位（P04 条款允许），正式素材放入 `Public/textures/block/machines/telemetry_ore_washer/` 后复制到 assets 即换。
- 7.4.0 子类无法更新收尾失败的 `waitingReason`（纯状态文字差异）；如需完整对齐需上游开放 setter（已记入 MEMORY）。
- 基础洗矿镜像配方仅铜一种材料（P01 基准白名单），后续材料与“常规产物白名单”扩充在 P05 做。
- JEI 中的配方页展示需真机客户端确认一次（服务器端已验证配方注入与匹配语义）。

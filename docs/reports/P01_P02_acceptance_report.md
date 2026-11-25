# P01 + P02 验收报告

- 日期：2026-11-26
- 代码基线：`main` 分支（P01+P02 提交），Minecraft `1.20.1` / Forge `47.4.0` / GTCEu Modern `7.4.0`（slim 构件，LDLib `1.0.40.b`）
- 探针复现入口：`./gradlew runGametest`（后台执行完毕后按日志判定）
- 原始证据：本文件所在目录 [p02_probe_evidence.log](p02_probe_evidence.log)（GameTestServer 输出的 `[HC-PROBE]` 行与测试结论）

---

## 任务一 P01：首批物品注册与试验配方确定

### 1. 三件套物品已经正式注册

| 注册 ID | 英文名 | 中文名 | 模型 | 可堆叠 |
| --- | --- | --- | --- | --- |
| `hypercluster:mining_telemetry_log` | Mining Telemetry Log | 矿处遥测日志 | `minecraft:item/generated` + `hypercluster:item/mining_telemetry_log`（16×16） | 64 ✓ |
| `hypercluster:blank_carrier` | Blank Carrier | 空白载体 | 同上，`blank_carrier` | 64 ✓ |
| `hypercluster:mining_edge_chip` | Mining Edge Chip | 矿处边缘芯片 | 同上，`mining_edge_chip` | 64 ✓ |

- 贴图来源为 `Public/textures/item/`（16×16 RGBA），已按 `src/main/resources/assets/hypercluster/textures/item/*.png` 接入；`./gradlew runData` 生成的三个 item model 均指向对应贴图（见 `src/generated/resources/assets/hypercluster/models/item/`）。
- 语言文件：`en_us.json` / `en_ud.json` 由 Registrate 生成（物品名 + `item.hypercluster.<name>.tooltip` 工具提示行）；`zh_cn.json` 手工维护（物品名 + 提示）。三件套共用 `TooltipItem`（`dev/hypercluster/common/item/TooltipItem.java`），在 `appendHoverText` 追加一行灰色可翻译提示。
- 堆叠实测（GameTest `telemetryItemsRegistered`）：三个物品 `getMaxStackSize()==64`，30 + 34 两份同类堆叠合并为 64 且无残留；tooltip 解析出可翻译键。服务器端 `Language` 实测解析到 `Mining Telemetry Log / Blank Carrier / Mining Edge Chip`。

### 2. HV 矿处常规配方基准（P01 交付 #3）

- **选定机器**：HV Electric Ore Washer（HV 洗矿机，`gtceu:hv_ore_washer`，定义于 GTCEu `GTMachines.ORE_WASHER[GTValues.HV]`，`SimpleTieredMachine` + `OC_NON_PERFECT`）。
- **选定配方**：`gtceu:ore_washer/wash_copper_crushed_ore_to_purified_ore`（洗矿：破碎铜矿石 → 纯净破碎铜矿石）。

| 项 | 原配方（配方书口径，GameTest 实测） | HV 机器上超频后（实测） |
| --- | --- | --- |
| 输入物品 | 1× `crushed_copper_ore` + 1× 编程电路 #1（不消耗） | 同左 |
| 输入流体 | 1000 mB 水 | 同左 |
| 耗时 | 400 t | 100 t |
| EU | 16 EU/t（共 6,400 EU） | 256 EU/t（每批 25,600 EU） |
| 常规输出 | 1× `purified_copper_ore` + 1× `stone_dust` | 同左 |
| 概率输出 | 1× 钴粉（1/3 概率副产） | 同左 |

> 该配方可由 GTCEu 7.4.0 运行时配方生成器稳定复现（`OreRecipeHandler.processCrushedOre`，对所有具备破碎矿的材料自动生成），是标准“矿处”链路中确定常规产物、用水明确、耗时可控的配方。后续芯片对照配方将以其为基准：同输入、省水（100 mB 档）、产物一致。

### 3. P01 验收结论

- `./gradlew runData`：**通过**（写入 3 个 item model + en_us/en_ud 语言）。
- `./gradlew build`：**通过**，产物 `build/libs/gregtech-hypercluster-0.1.0.jar` 内含模型、贴图、双语语言与 MODS 元数据。
- 可复现的 GameTest 证明：注册存在、堆叠合并到 64、工具提示键与英文名解析正确（见证据日志）。
- 开发世界 `/give` 手动验收步骤（供人工确认贴图显示）：

```text
/give @s hypercluster:mining_telemetry_log
/give @s hypercluster:blank_carrier
/give @s hypercluster:mining_edge_chip
```

预期：物品名称分别为“矿处遥测日志 / 空白载体 / 矿处边缘芯片”（中文客户端），悬停出现对应灰色提示行，同类可堆叠至 64。本环境下已完成到“服务端注册 + 资源装载无报错”的自动化验证；显色渲染需在带显示环境的开发客户端人工确认（资源文件本身已逐项核对，见上表）。

---

## 任务二 P02：遥测结算探针与接入方案拍板

### 1. 探针方法（Forge GameTest，可复现）

- 入口：`./gradlew runGametest` —— 在 MDG `gametest` run（`build.gradle`）里以 `forge.gameTestServer=true` 启动 Forge GameTestServer（1.20.1 原生路径，无第三方框架）。
- 探针：`dev/hypercluster/probe/SettlementProbe.java` 在 GameTest 批次启动时为 `GTMachines.ORE_WASHER[HV]` 包一层 `afterWorking`（`MachineDefinition#setAfterWorking` 公开 setter），逐次记录：机器位置、机器偏移计时、世界 gameTime、配方 ID、progress/duration、连续批次计数、状态；不使用 mixin、不改动 GTCEu 运行逻辑。
- 测试：`dev/hypercluster/gametest/SettlementProbeTests.java`，7 个用例，模板结构为内置 `data/hypercluster/structures/hc_floor.nbt`（12×5×12 基岩地板）。机器输入/电量由测试直接驱动：注入破碎铜矿石 + 编程电路 #1 + 水，并用每 20t 的“电网”对 HV 电容补能（断电测试用开关关闭并清空电容）。
- 运行结论（本次）：**7 个用例全部通过**（`All 7 required tests passed`）。

### 2. 五种场景实测结果表

| 场景 | 机器 / 配方 | 成功完成批次 | 实际产物（净化矿/石粉） | `afterWorking` 触发次数 | 是否重复/误计 |
| --- | --- | --- | --- | --- | --- |
| S1 连续正常完成 10 批 | `gtceu:hv_ore_washer` + `wash_copper_crushed_ore_to_purified_ore` | 10 | 10 / 10 | 10（间隔精确 100t，first=1906 → last=2806，span=900） | 无重复、无漏计；`consecutiveRecipes` 随 0→9 单调递增 |
| S2 输出塞满堵仓（首批中途塞满 3 个输出槽） | 同上（投 2 批） | 1 批“走完流程” | **0 / 0**（该批产物被静默丢弃） | 1（progress=100/100 触发） | **误计**：计数触发但产物丢失；机器转 IDLE、输入残留 1 批 |
| S3 中途手动取消（`interruptRecipe()`，进度 49/100） | 同上（投 1 批） | 0 | **0 / 0** | **1**（progress=49 时即触发） | **误计**：取消的批次同样触发 `afterWorking`，且输入已被消耗 |
| S3b 界面暂停（`setWorkingEnabled(false)`） | 同上（投 1 批） | 1（当前批正常结算后挂起） | 1 / 1 | 1 | 无误计（暂停语义=“完成本批后挂起”，随后 SUSPEND） |
| S4 断电→恢复（t40 断电、t320 恢复） | 同上（投 1 批） | 1 | 1 / 1 | 1（恢复后仅在真正完成时触发） | 无重复；断电期间 WAITING、进度回退到 1（`regressWhenWaiting=true`），恢复后经 runDelay 退避（60/120/180t 阶梯）续跑，最终恰好结算一次 |
| S5 两台机器同 tick 完成 | 两台 `hv_ore_washer`，同批注入、同协议输入 | 每台 1 | 每台各 1 / 1 | 每台 1，**gameTime 完全相同（=1906）** | 无重复、无串机；两机 `getOffsetTimer()` 相差 ≤8 属机器放置相位，不影响结算归属 |

### 3. 配套行为要点（实测来源见证据日志）

1. `afterWorking()` 发生在输出结算**之前**（事件时刻 progress==duration、状态 WORKING，输出槽内仍为空）；正常产物在其后同一 tick 内落槽。
2. `onRecipeFinish()` 链式续批：同 tick 内 `checkRecipe` 通过则直接 `setupRecipe` 开始下一批；连续 10 批的触发点精确间隔 100t，无额外/丢失触发。
3. `onRecipeFinish()` **忽略** `handleRecipeIO(IO.OUT)` 的返回值：堵仓时输出既进不了槽（插入前有模拟校验）也不回退输入，产物静默丢弃；开发 guida 上机器随后转 IDLE（waitingReason=输出不足）。
4. `interruptRecipe()` 同样调用 `machine.afterWorking()`；随后 progress/duration 清零、状态 IDLE，输入已在开工时被消耗。
5. 堵仓/断料导致的收尾把 `consecutiveRecipes` 归零（onRecipeFinish 的 IDLE 分支）；机器空闲后的下一次 `findAndHandleRecipe()` 会把 `lastRecipe/lastOriginRecipe` 置空——遥测侧若要引用配方需在结算时刻自行携带。
6. 两台机器的结算互不串扰；按 position 记账即可区分。

### 4. 决策：0.1 首版采用 **附属自有 HV 示范矿机**

理由（以实测为据）：

1. **堵仓误计无法从公开扩展点可靠排除**。afterWorking 只在“逻辑上完成”时触发，输出的落槽结果被 `onRecipeFinish` 忽略，第三方覆盖板拿不到“真正结算成功”的回执；S2 已实测到 1 次触发、0 产物。按计划验收条款（堵仓不得发日志），失败。
2. **取消同样误计**。S3 实测 `interruptRecipe()` 在进度 49/100 触发 afterWorking、产物为 0、输入已消耗。即便叠加“progress==duration 才计数”的补丁（可排除 S3），仍排除不了 S2 —— 判据不完备。
3. **结算时刻与产物落槽不同相位**。afterWorking 先于 IO.OUT，任何“在事件点读输出槽”的实现都会读到空槽;要区分 S2，只能再观察落槽差异并处理自动输出/手工取出的干扰，复杂度和误计风险都高。
4. **自有示范矿机把结算判定收进我们控制的代码**。自有 Tiered 机器可覆写 `createRecipeLogic()`（GTCEu 提供的标准扩展点），在自类里掌握 `onRecipeFinish` 的实际 IO.OUT 结果，堵仓/取消/断电情形可以精确只给“真实落槽的批次”记账 —— 逐项满足计划验收第 2 条。S1 中 100t/批的确定性、S5 同 tick 归属，说明该路径上按 position+gameTime 记账是足够的。
5. **烧录链与物品 ID 不变**。P01 的三件套、本次基准配方与本次探针资产全部继续沿用；通用遥测覆盖板转入后续版本，待 GTCEu 提供更明确的“输出结算回执”类扩展点（或在锁定版本内窄验证导轨）后再评估。

### 5. 复现步骤

```bash
source ./env.sh
./gradlew runData          # P01 资源生成
./gradlew build            # P01 打包
./gradlew runGametest      # P02 结算探针（7 个 GameTest 全部通过）
grep "HC-PROBE" run/gametest/logs/latest.log   # 或本目录 p02_probe_evidence.log
```

### 6. 已知边界与遗留

- `run/gametest/eula.txt` 按仓库 README 的开发流程置为 `eula=true`（本机开发运行目录，不代表对 Mojang EULA 的代接受）；`run/server/` 的 eula 仍保持用户自管状态。注意：P00 记录中 `runServer` 完成启动的验收在当时并未真正发生（`run/server/logs/latest.log` 显示 EULA 未同意即退出）。
- 纯显色渲染（物品图标在手持/JEI 中的像素级显示）需在带显示的客户端人工复核一次；自动化验证已覆盖注册、模型-贴图引用、双语语言装载与服务端名称解析。
- 概率副产（钴粉 1/3）不影响 0.1 遥测口径：日志按“成功结算的批次”计，不按产物种类计。S2/S3 的口径处理由 0.1 自有矿机的结算判定实现承接。

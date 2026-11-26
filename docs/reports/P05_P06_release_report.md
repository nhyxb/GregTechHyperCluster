# P05 + P06 发布报告（0.1.0 首发）

- 日期：2026-11-26
- 代码基线：`main`（P05+P06 提交），提交前状态见 git log；工件 `build/libs/gregtech-hypercluster-0.1.0.jar`（**SRG reobf 生产包**，已在独立专服实机验证）
- 前序报告：[P01_P02_acceptance_report.md](P01_P02_acceptance_report.md)，[P03_P04_acceptance_report.md](P03_P04_acceptance_report.md)
- 生存操作流程：[../survival_walkthrough_0.1.md](../survival_walkthrough_0.1.md)

---

## 任务一 P05 — 生存配方、界面修正与演示流程

### 1. 生存合成配方（全部可在 JEI/配方浏览器查到，纯生存可达成）

| 配方 ID | 形制 | 输入 | 输出 |
| --- | --- | --- | --- |
| `hypercluster:shaped/blank_carrier` | 工作台 3×2 | 聚乙烯板×4 + 硅晶圆×1 + HV电路×1 | **2× 空白载体** |
| `hypercluster:shaped/hv_micro_burner` | 工作台 3×3 | HV发射器×2 + HV电路×2 + HV机壳×1 + 玻璃×2 + HV金线缆×2 | 1× HV 微型烧录台 |
| `hypercluster:shaped/hv_telemetry_ore_washer` | 工作台 3×3 | HV传感器×1 + HV电路×2 + **原版 `gtceu:hv_ore_washer`×1** + 玻璃×2 + HV金线缆×2 | 1× HV 遥测示范洗矿机 |

- 全部经 `IGTAddon.addRecipes` → `VanillaRecipeHelper.addShapedRecipe` 注入（与 GT 自身工作台配方同一通道）；GT 的 `ShapedRecipeBuilder` 会自动给 id 加 `shaped/` 前缀——GameTest `survivalRecipesExist` 已按真实 id 断言输出、产出数量与关键材料（硅晶圆、HV 传感器、HV 发射器、HV 机壳、原版洗矿机）。
- 材料全部为 GTCEu 7.4.0 原版 HV 期可获得项：`gtceu:silicon_wafer`、`gtceu:hv_sensor`、`gtceu:hv_emitter`、`gtceu:hv_machine_hull`、`gtceu:advanced_integrated_circuit`、聚乙烯板、金线缆。

### 2. 客户端/联机界面修正

- `TelemetryOreWasherMachine#createTelemetryBufferSlot()`：移除 `GuiTextures.INT_CIRCUIT_OVERLAY`（避免与编程电路槽混淆），仅保留普通槽底图。
- 缓冲 Tooltip 改为读取 **slot 自身同步的 ItemStack 数量**（`slot.getItem().getCount()`），不再读服务端 `machine` 状态——独立专服联机下计数由 UI 自身同步通道保证准确（LDLib SlotWidget 的同步栈）。“已满”同样基于该同步栈判定。
- **探针挂载范围确认（P05-2）**：`SettlementProbe.install` 全工程唯一调用点是 `SettlementProbeTests.prepareProbe`（`@BeforeBatch`，仅 `runGametest` 任务会执行）。`@Mod` 构造器与 `IGTAddon.initializeAddon` 均不引用探针类；单人/专服正常启动不会触碰任何 GT 机器定义（探针类文档已更新为明示该保证）。两次生产专服冷启动日志中也无任何探针输出行，与该结论一致。

### 3. 生存演示文档与语言核对

- 新增 [docs/survival_walkthrough_0.1.md](../survival_walkthrough_0.1.md)：HV 前置 → 载体 → 烧录台 → 洗矿机升级 → 采集日志 → 烧芯片 → 芯片省水配方 → 效益对照表，只含已实现功能。
- 中英文语言文件覆盖：三件套物品名/提示、两台机器名/提示、配方类型名（JEI 分类）、缓冲槽提示与“已满”提示（`zh_cn.json` 手工 + `en_us/en_ud` 生成），`runData` 后核对无缺键。

## 任务二 P06 — 首发回归与 0.1.0 交付

### 1. 全套构建与测试（开发环境）

- `./gradlew runData` ✓（12 生成文件，含两台机器 blockstate/model/全部语言）
- `./gradlew build` ✓（产物 + reobfjar）
- `./gradlew runGametest` ✓ **19/19**：P01/P02 基线 7 项 + P03/P04 结算/闭环 10 项 + 新增生存合成 2 项。所有用例判定含生存配方存在性（新增 `SurvivalCraftingTests`）。
- 期间修复：洗矿类型物品输入上限 2→3（芯片配方含 3 项物品输入：破碎矿+芯片+电路，之前每次加载会警告）；GTShapedRecipe 的 id 带 `shaped/` 前缀使 GameTest 按真实 id 断言。

### 2. 打包产物 JAR 的专服冒烟（**生产环境实机**）

生产专服环境搭建：Forge 官方 installer `1.20.1-47.4.0` 落地于 `run/pv-gt`、`run/pv-ae2`（run/ 目录不入库），mods 仅放生产 jar。启动方式 `java @user_jvm_args.txt @libraries/.../unix_args.txt nogui`，用延时管道发送 `stop` 做受控停机。

| 环境 | mods | 结果 |
| --- | --- | --- |
| 独立专服（`run/pv-gt`，端口 25570） | `gregtech-hypercluster-0.1.0.jar` + `gtceu-1.20.1-7.4.0.jar`（官方分发包） | `Done (23.146s)`，controlled stop，**ERROR 0 条**；日志含 `[GregTech HyperCluster]: initializing content... / content ready`（证明生产 jar 的内容初始化真实执行） |
| GTCEu + AE2 组合（`run/pv-ae2`，端口 25571） | 上述 + `appliedenergistics2-forge-15.4.12.jar` + `guideme-20.1.15.jar` | `Done (26.246s)`，controlled stop，**ERROR 0 条**，我们的内容初始化同样执行 |

两个环境均验证了：FML 完整装载、GT `gtceu:dynamic_data` 数据包被自动装载（我们的 6 条配方走同通道注入）、世界生成/存档/关机无异常。

**重要发现（AE2 15.4.12 取包渠道）**：modmaven.dev 上的 `appeng:appliedenergistics2-forge:15.4.12`（sha256 `094fc5…`）与官方发布物（GitHub forge/v15.4.12 = Modrinth 同一文件，sha256 `b9c59…`）**不是同一个构建**——modmaven 版的 mixin 以 mojmap 名 `@Shadow levels` 编译，在 Forge SRG 生产运行时直接 `MixinApplyError` 崩溃；官方构建版本以 SRG 名编译，生产专服一本通过（本报告上表的组合环境即用官方构建）。后续取 AE2 请固定走 Modrinth/GitHub 发布物或核对 sha256。

集中说明两点边界：
- **GameTest 不能在生产环境跑**：Forge 1.20.1 在 `FMLEnvironment.production` 下禁用 gametest 注册（`ForgeGameTestHooks.isGametestEnabled` 的 `!isProduction()` 门）。核心闭环的逐边界回归因此全部由开发环境 `runGametest`（19/19）承担，生产侧承担“真实发行包 + 完整依赖组合的启动冒烟”。0.1 无 AE2 API 代码路径，组合环境对本 mod 的影响面就是装载共存，已由上面组合冒烟覆盖。
- **guideme**（AE2 的强制依赖）无 maven 仓库，发布包同样面向 SRG 运行时，开发环境尝试（经 MDG 重映射）不可靠；故组合冒烟只做生产侧。脚本/缓存里通过 `.tools/runtime/` 保留该 jar（gitignore 内），生产冒烟使用其原始文件。

### 3. 交付清单

- **JAR**：`build/libs/gregtech-hypercluster-0.1.0.jar`（modid `hypercluster`，`mods.toml` 声明 `forge [47.4.0,48)`、`minecraft 1.20.1`、`gtceu [7.4.0] AFTER`）。
- **内容**：
  - 物品：`mining_telemetry_log` / `blank_carrier` / `mining_edge_chip`（+P00 遗留 `development_probe`）。
  - 机器：`hv_micro_burner`、`hv_telemetry_ore_washer`；配方类型 `hypercluster:micro_burner`、`hypercluster:telemetry_ore_washing`。
  - 配方：烧录（日志+载体→芯片）、镜像基础洗矿（电路1/1000mB）、芯片省水洗矿（电路2/芯片不消耗/100mB）、3 条工作台生存配方。
  - 结算与缓冲：IO.OUT 实结账的精确遥测、64 容量持久化缓冲、拆除返还、满仓丢弃提示。
- **支持范围（0.1）**：机器两台、配方 6 条、白名单仅铜矿破碎洗矿链；操作流程见生存文档。
- **依赖版本表（锁定）**：见 README「开发环境」表；运行期额外锁定 `gtceu 7.4.0（官方分发包）`、组合冒烟锁定 `AE2 15.4.12（Modrinth/GitHub 构建）+ GuideME 20.1.15`、`Forge 47.4.0`（安装器 1.20.1-47.4.0 系列）。

### 4. 复现步骤

```fish
source env.fish                                # 或 bash: source ./env.sh
./gradlew runData && ./gradlew build           # 资源生成 + 打包
./gradlew runGametest                          # 19/19（含生存配方存在性、结算边界）
grep "HC-P" run/gametest/logs/latest.log       # 过程证据行

# 生产冒烟（可选，重放 P06）：
# 1) forge installer --installServer <dir>
# 2) mods/ 放入 gregtech-hypercluster-0.1.0.jar + gtceu-1.20.1-7.4.0.jar（组合再加 AE2+GuideME）
# 3) eula.txt=true, server.properties 独立端口
# 4) java @user_jvm_args.txt @libraries/.../unix_args.txt nogui   → 期望 Done (…s)
```

### 5. 遗留与移交

- `run/server/eula.txt` 本次按目标要求与既往流程（README+run/gametest 先例）置 `eula=true`；文件在 gitignore 内不随仓库分发，如不同意可改回 `eula=false`。
- `run/pv-gt` / `run/pv-ae2` 为本报告的生产冒烟环境（gitignore 内），可直接复用；`docs/survival_walkthrough_0.1.md` 为玩家可见的 0.1 说明书（发布时可挪入 CurseForge/Modrinth 描述页）。
- JEI 中配方页的像素级观感、机器贴图在手持/JEI 的渲染仍建议你开一次 `runClient` 终检（此前两次客户端冷启动均无资源错误）。
- 0.2 起的工作（EV 数据工坊、材料白名单扩充、通用遥测覆盖板重估）见 IMPLEMENTATION_PLAN；开发环境与引擎的关键坑位已更新进 MEMORY.md。

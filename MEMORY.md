# 项目记忆

## GitHub 仓库

- 已创建公开仓库 [nhyxb/GregTechHyperCluster](https://github.com/nhyxb/GregTechHyperCluster)（2026-11-25，初始提交 6ace997，35 个文件）。
- 远端 `origin` 使用 HTTPS 协议；默认分支 `main`。gh CLI 已登录账号 `nhyxb`。

## Git 身份

- 本仓库本地已配置 `user.name=nhyxb`、`user.email=77566653+nhyxb@users.noreply.github.com`（GitHub noreply 邮箱）。
- 全局未配置 git 身份；其他项目首次提交前需要另行配置。

## P01–P04 已完成（2026-11-26）

- 三件套物品（`mining_telemetry_log` / `blank_carrier` / `mining_edge_chip`，堆叠 64 + TooltipItem 提示行）。验收报告 `docs/reports/P01_P02_acceptance_report.md`、`docs/reports/P03_P04_acceptance_report.md`，探针证据 `docs/reports/*_probe_evidence.log`。
- **机器**：`hypercluster:hv_micro_burner`（SimpleTieredMachine HV）与 `hypercluster:hv_telemetry_ore_washer`（HV + TelemetryRecipeLogic + 64 格日志缓冲），注册在 `HGTMachines`（`dev/hypercluster/common/data/HyperClusterMachines.java`）。机器模型由 registrate 生成 blockstate+model（gtceu:machine loader，HV 外壳 + overlay_front(_active)）。
- **配方类型**（`HyperClusterRecipeTypes`，本命名空间）：`micro_burner`（1 日志+1 载体→1 芯片，512EU/t×128t）；`telemetry_ore_washing`：基础镜像（电路#1+1000mB 水）与芯片版（电路#2+芯片 notConsumable+100mB 水，省水 90%），铜材料，产物与 GT 基准一致。配方经 `IGTAddon.addRecipes` 注入。
- **精确结算**：`TelemetryRecipeLogic` 复制 7.4.0 `onRecipeFinish` 并捕获被忽略的 `handleRecipeIO(OUT)` 结果 —— 真实落槽 + hypercluster 白名单 + 常规产物时每批 1 份日志进缓冲。堵仓/取消/断电未完/空转均 0 份。缓冲 `CustomItemStackHandler`（@Persisted 容量 64，GUI 只取槽，满则丢弃；拆除弹出到世界）。
- **GameTest 回归**：`./gradlew runGametest` 17/17 通过（P01/P02 基线 7 + P03/P04 10：十批十日志、堵仓 0、取消 0、断电 1、同 tick 各 1、缓冲满照跑、存档重载不翻倍、烧录闭环、芯片省水配方与严格隔离）。
- **基准配方**（P01，机器 `gtceu:hv_ore_washer`）：`gtceu:ore_washer/wash_copper_crushed_ore_to_purified_ore`：1 破碎铜矿石 + 编程电路#1 + 1000mB 水 → 1 纯净破碎铜矿石 + 1 石粉（1/3 钴粉副产）；原配方 400t@16EU/t，HV OC_NON_PERFECT 后 100t@256EU/t。

## 关键技术要点（GTCEu 7.4.0）

- 附属机器/配方类型注册发生在 `IGTAddon.initializeAddon()`（运行于 GT CommonProxy.init 内、其自身内容注册并 freeze 之后）：需要先 `GTRegistries.MACHINES/RECIPE_TYPES/RECIPE_CATEGORIES.unfreeze()`，注册完再 `freeze()`——直接注册会抛 "registry ... has been frozen"。
- `GTRegistrate.machine(String, Function<IMachineBlockEntity, MetaMachine>)` 两个参数重载 + `.tier().langValue().editableUI(SimpleTieredMachine.EDITABLE_UI_CREATOR.apply(path, type)).rotationState().recipeType().recipeModifier().workableTieredHullModel(rl).tooltips().register()`；模型自动生成（overlay 路径 `assets/<modid>/textures/block/machines/<dir>/overlay_front(.png/_active.png)`）。
- RecipeLogic 子类可覆写 `createRecipeLogic()`（WorkableTieredMachine 的 protected 扩展点）；但 `waitingReason` 是私有字段无法在子类回写（仅 GUI 状态文字差异）。
- 机器空闲后下一次 `findAndHandleRecipe()` 会把 `lastRecipe/lastOriginRecipe` 置空 —— 测试若要读取配方须在该时刻前或改用副作用断言。
- `BlockEntity.load/saveWithFullMetadata` 在 1.20.1 是 public，可用于测试存档往返。
- GameTest 模板结构放 `src/main/resources/data/<modid>/structures/<name>.nbt`（gzip NBT，根必须带名字 TAG_Compound，DataVersion 3465）；`@GameTestHolder("hypercluster")` + `template = "<name>"`。
- `org.jetbrains.annotations` 不在编译类路径，用 `javax.annotation.Nullable`；GTMaterials 路径 `com.gregtechceu.gtceu.common.data.GTMaterials`。

## 0.1.0 已发布（P05+P06 完成，2026-11-26）

- 生存合成配方（JEI 可查，id 带 `shaped/` 前缀——GT 的 ShapedRecipeBuilder 行为，GameTest 按此断言）：`blank_carrier`（聚乙烯板+硅晶圆+HV电路→×2）、`hv_micro_burner`、`hv_telemetry_ore_washer`（消费原版 `gtceu:hv_ore_washer`）。
- UI 修复：缓冲槽用普通槽底图（去掉电路 overlay）；Tooltip 读 `slot.getItem()`（LDLib 同步栈）保证专服联机计数正确。
- 生产冒烟组合已在 `run/pv-gt`（GT only）与 `run/pv-ae2`（+AE2）冷启动至 Done、零 ERROR；生产环境 Forge 禁用 gametest（`!isProduction()` 门），核心闭环回归固定走 dev `runGametest`（19/19）。
- runServer /run_server eula 已按目标要求置 true（gitignore 内，不随仓库分发）。
- **AE2 取包坑**：modmaven.dev 的 `appliedenergistics2-forge:15.4.12`（sha `094fc5…`）≠ 官方发布物（GitHub=Modrinth، sha `b9c59…`）；modmaven 版 mixin 用 mojmap 名 `@Shadow levels`，在 Forge SRG 生产运行时直接 MixinApplyError 崩溃。固定走 Modrinth/GitHub，或核对 sha256。
- GuideME（AE2 强制依赖，20.1.7–20.2.0）无 maven：发布包为 SRG 运行时构建，**不能**进 dev classpath（dev 是 mojmap 名，加载即 NoSuchFieldError）；生产专服直接放 mods/ 即可。下载缓存在 `.tools/runtime/`（gitignore）。
- Forge 专服生产环境字段/方法均为 SRG 名（`f_xxx_/m_xxx_`）；我们的 jar 由 MDG `reobfJar` 生成 SRG 引用，已验证与生产环境兼容（jar 内类可见 `f_/m_` 引用）。
- P06 后 `HyperCluster.LOGGER` 存在，初始化双 INFO 日志可供打包排障：`[GregTech HyperCluster]: initializing content... / content ready`。

## 0.1.0 发版修复（2026-11-26）

- **移除 P00 占位物品 `development_probe`**（注册/中英语言/生成模型/README 示例全部清理，`/give` 示例改为 `hypercluster:mining_telemetry_log`）。
- **运行贴图不切换的根因**（`Can't find id for 'hypercluster:<machine>[recipe_logic_status=idle]' in IdMapper` 堆栈 → `MachineRenderStatePayload.writePayload`）：GTCEu 7.4.0 只在 `GTMachines.init()` 末尾把它**自己**机器的全部 `MachineRenderState` 塞进 `MachineDefinition.RENDER_STATE_REGISTRY`；附属机器在 `IGTAddon.initializeAddon`（更晚）注册 → render state 不在注册表 → 服务端渲染态 payload 序列化直接抛异常 → 客户端收不到 `working`，正面贴图永远停在 `overlay_front`。**修复**：`HyperClusterMachines.registerRenderStates()` 镜像 GT 的补注册循环（未来每加一台机器都要挂进去，或写个遍历 `GTRegistries.MACHINES` 中 hypercluster 条目的循环）。回归：`SurvivalCraftingTests.renderStatesRegisteredForSync` 对每台机器断言 4/4 状态在注册表；20/20 gametest 全绿、IdMapper 异常 0 次。

## 开发测试规范（长期执行）

- **客户端冒烟测试严禁占用宿主机真实桌面**：所有 `./gradlew runClient` 的自动化冒烟一律在 Xvfb 虚拟屏内跑，例如：
  `env -u WAYLAND_DISPLAY LIBGL_ALWAYS_SOFTWARE=1 xvfb-run -a -s "-screen 0 1280x720x24" ./gradlew runClient`
  本环境 openSUSE 的 `xorg-x11-server-Xvfb` 包只带 `/usr/bin/Xvfb`、无 `xvfb-run` 脚本，等价做法：手动 `Xvfb :99 -screen 0 1280x720x24 &` 后 `env -u WAYLAND_DISPLAY LIBGL_ALWAYS_SOFTWARE=1 DISPLAY=:99 ./gradlew runClient`，结束后 kill 掉 Xvfb，不留后台进程。若以后装了 `xvfb-run`（需要用户确认安装来源），直接用标准命令即可。

## 环境注意事项

- `run/server/eula.txt` 从未置 true（P00 当时 runServer 实际在 EULA 检查处退出）；`run/gametest/eula.txt` 已按 README 流程置 true。
- 机器有 `DISPLAY=:1` 与 NVIDIA GPU：`runClient` 可启动到主菜单并完整装载 GT 资源（两台机器 overlay 贴图零报错）。
- **用户使用 fish shell**：环境脚本用仓库内 `env.fish`（与 `env.sh` 语义一致；`return 1` 而非 exit）。系统默认 java 是 OpenJDK 25 —— 必须先 source 环境脚本再跑 `./gradlew`，否则 Gradle 8.10 的 Groovy 在 Java 25 上报 "Unsupported class file major version 69"，且会误用 `~/.gradle` 重新下载 wrapper。
- 待移交：JEI 配方页真机确认；材料白名单扩充与缓冲满 UI 强化留给 P05。`telemetry_ore_washer` 正式贴图已交付并同步（Public 清单共 7 张贴图）。

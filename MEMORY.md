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

## 环境注意事项

- `run/server/eula.txt` 从未置 true（P00 当时 runServer 实际在 EULA 检查处退出）；`run/gametest/eula.txt` 已按 README 流程置 true。
- 机器有 `DISPLAY=:1` 与 NVIDIA GPU：`runClient` 可启动到主菜单并完整装载 GT 资源（两台机器 overlay 贴图零报错）。
- 待移交：`telemetry_ore_washer` 正面贴图为 micro_burner 占位（正式素材放入 `Public/textures/block/machines/telemetry_ore_washer/` 后复制到 assets 即换）；JEI 配方页真机确认；材料白名单扩充与缓冲满 UI 强化留给 P05。

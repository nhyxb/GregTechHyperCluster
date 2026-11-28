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

## 0.2 核心闭环已完成（2026-11-26，0.2.0）

- **基类抽取**：`TelemetryMachine`（持久化缓冲 + `TelemetryRecipeLogic` 结算，logItem 可配）；HV 洗矿机与 `TelemetryChemicalReactorMachine`（EV）共同继承。6 新物品（化工日志/清洁数据集/两条高纯数据集/乱码噪点/化工芯片）+ EV 反应釜 `ev_telemetry_chemical_reactor` + 多方块 `data_scrubbing_tower`（3×5×3：EV 钛机壳+玻璃+标准 5 类 hatch）全部落地。
- **EV 化工基准**：GTCEu 7.4.0 `chemical_reactor/indium_concentrate_separation`（600 EU/t×50t，电路#1，4 铝粉+1000mB 铟浓缩液→小堆铟粉+4 亚硫酸铝粉+铅锌溶液）；芯片版电路#2+不消耗 chip，**辅助铝粉减半 4→2**。清洗塔：日志+蒸馏水→高纯数据集+clean+90% 噪点；噪点×4→硅粉。
- **runGametest 29/29**（0.1 的 20 + `DataWorkshopTests` 9，新模板 `hc_field` 13×9×13）；Xvfb 客户端冒烟零内容 ERROR；版本号已 bump 0.2.0。
- **留坑（0.2.1）**：黄金样本、微调刻录舱、红队测试舱；塔输入混配约束余量在 `DATA_SCRUBBING_RECIPES.setMaxIOSize(4,3,2,0)`。

## 0.2.1 微调与红队对齐已完成（2026-11-26，0.2.1）

- **0.2 三修正**：clean-dataset 载体 2→4；zh_cn 统一「化学反应釜」+烧录台双样本说明；芯片配方亚硫酸铝 4→2（铝守恒 2→2）。
- **5 新物品**（golden_dataset / mining·chemical_lora_card / aligned_mining·chemical_edge_chip）+ **两座 3×3×3 舱**：`lora_engraving_chamber`（数据集+载体+黄金样本+500mB 蒸馏水→LoRA 卡）、`red_team_sandbox`（噪点×2+芯片→已对齐芯片）；共用 `chamberPattern`（aisle 底层 `"XSX","XXX","XXX"` + 中层 `"XGX","X#X","XGX"` + 顶层实心，控制器在底层 (1,0,0)）。
- **电路 #3**（LoRA，卡不消耗）：洗矿钴 2/3；化反铟 ×2 且铝守恒。**电路 #4**（已对齐芯片，不消耗）：化反 450 EU/t、洗矿 12 EU/t（=75% 基准）。黄金样本 = 清洗配方追加 1/10 chanced。
- **runGametest 35/35**；Xvfb 冒烟零内容 ERROR；0.2.1 jar 交付。0.2.1 正式贴图后补交付（物品 + 两舱 overlay，含 Aseprite 源），已替换占位并复跑冒烟通过；**0.3 机房套件美术已在 Public untracked**（air_cooled_compute_rack / inference_hatch / precision_crac / datacenter×4 / IV 刀片 / 零食包），留待 0.3 入库。
- GT 配方 fraction 任意 a/b 均可（"2/3" 直接可用）；Content.content 对物品是 **vanilla `net.minecraft.world.item.crafting.Ingredient`**（用 `getItems()` 匹配），chance/maxChance 内部缩放但比值不变（1/10 精确、2/3≈0.6667 用区间断言）。

## GTCEu 7.4.0 多方块三坑（长期有效）

- **维护仓 veto 配方**：`IMaintenanceMachine.modifyRecipe` 在 `enableMaintenance` 时对有任何问题的 hatch 返回 null → `WorkableMultiblockMachine.doModifyRecipe` 遍历 parts 一票否决 → 多方块「成型但永远 IDLE」第一嫌疑。**`ALL_PROBLEMS = 0`（bitmask 1=已修复）**，新维护仓初始 6 问题全在，玩家必须先修复 hatch 才能跑配方；测试用 `fixAllMaintenanceProblems()` 模拟。问题排查钩子：手动打印 `machine.fullModifyRecipe(candidate)`，NULL 即查 parts 的 modifyRecipe。
- **GameTestServer 里 MWSD 异步成型不可靠**（`MultiblockWorldSavedData` 的 250ms executor + 相位门永远不推进，`error=UNINIT`）；测试用「`getPatternLock().lock()` + `checkPatternWithLock()` + `setFlipped(isNeededFlip)` + `onStructureFormed()`」同步复刻 `asyncCheckPattern` 成型段（见 `DataWorkshopTests.ensureTowerFormed`），生产环境不受影响。
- **配方档位判定**：`RecipeHelper.getRecipeEUtTier(recipe) <= overclockMachine.getMaxOverclockTier()`（机器 tier=能源仓电压下取整档）否则 modifier 返回 NULL。**EV=V[4]=2048 EU/t**；2048 档配方在 2048 机器不超频满速跑。塔 hatch 聚合 IO 记得：import bus/export bus/流体仓/能源仓/维护仓各自 ability predicate 独立 min/max，维护用 `autoAbilities(true,false,false)`。

## GameTest 结构模板 NBT 格式（hc_floor/hc_field 双例可对拍）

- `size` 必须是 **TAG_List[TAG_Int]**（写成 TAG_Int_Array 一律 `Failed to load structure`）；`entities` 空表为 TAG_List[TAG_Compound] + len 0；`blocks` 每项 `{pos: TAG_List[Int], state: TAG_Int}`；palette 为 TAG_List[Compound] `{Name}`；根=headless Compound，gzip，DataVersion 3465。

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

- **⚠️ Xvfb 与本会话 bash 的 PID 命名空间**：每次 `bash` 调用都在独立的 `bwrap --unshare-pid` 命名空间里（私有 `/tmp`），**上一次调用启动的 Xvfb 已经随之死亡**。因此「先起 Xvfb、再单独调一次 runClient」会以 `glfwInit failed` 失败——不是显卡问题。正确做法是**把启动 Xvfb 与 runClient 写进同一个脚本一次执行**，并在同一脚本内收尾 kill。
  - **私有 `/tmp` 同样不跨调用**：上一次调用写下的 `/tmp/xxx.log` 下一次读到的是「没有那个文件」。中间产物（清洗后的日志、grep 结果）要么在同一次调用内生成并使用，要么写进仓库 `run/validation/`。
- **可复用的客户端冒烟入口**是 `bash .github/scripts/client-smoke.sh run/validation/client-smoke`。脚本用独立游戏目录绕过首次辅助功能引导，以 Forge 实际渲染主菜单的标记完成验收。配置和排障见 [README 的持续集成章节](README.md#持续集成)。
- **日志误报陷阱**：narrator（`flite_cmu_us_kal16`）的堆栈行里内嵌**整条 `-cp` 类路径**，其中含 `ldlib`、`text2speech` 等字样。直接 `grep -i ldlib` 会命中它，看起来像 ldlib 报错。**先剔除 narrator 连续行（以 `[HH:MM:SS]` 起始的行为边界）再 grep**，否则会误判内容资源加载失败。
- **客户端冒烟测试严禁占用宿主机真实桌面**：所有 `./gradlew runClient` 的自动化冒烟一律在 Xvfb 虚拟屏内跑，例如：
  `env -u WAYLAND_DISPLAY LIBGL_ALWAYS_SOFTWARE=1 xvfb-run -a -s "-screen 0 1280x720x24" ./gradlew runClient`
  本环境 openSUSE 的 `xorg-x11-server-Xvfb` 包只带 `/usr/bin/Xvfb`、无 `xvfb-run` 脚本，等价做法：手动 `Xvfb :99 -screen 0 1280x720x24 &` 后 `env -u WAYLAND_DISPLAY LIBGL_ALWAYS_SOFTWARE=1 DISPLAY=:99 ./gradlew runClient`，结束后 kill 掉 Xvfb，不留后台进程。若以后装了 `xvfb-run`（需要用户确认安装来源），直接用标准命令即可。

- 自定义机器界面复用 `MachineUi`。多方块的 `DISPLAY` 只包含带裁剪的可滚动状态文本，物品槽、按钮和固定宽度标签放入独立操作区。`MachineUiTests` 在 `runGametest` 中直接构造四台机器的页面，检查控件包围盒、最小 3 px 间距和单格仓室的居中对齐。更新标签时也用 Xvfb 检查客户端字体的实际宽度。

## 环境注意事项

- `run/server/eula.txt` 从未置 true（P00 当时 runServer 实际在 EULA 检查处退出）；`run/gametest/eula.txt` 已按 README 流程置 true。
- 机器有 `DISPLAY=:1` 与 NVIDIA GPU：`runClient` 可启动到主菜单并完整装载 GT 资源（两台机器 overlay 贴图零报错）。
- **用户使用 fish shell**：环境脚本用仓库内 `env.fish`（与 `env.sh` 语义一致；`return 1` 而非 exit）。系统默认 java 是 OpenJDK 25 —— 必须先 source 环境脚本再跑 `./gradlew`，否则 Gradle 8.10 的 Groovy 在 Java 25 上报 "Unsupported class file major version 69"，且会误用 `~/.gradle` 重新下载 wrapper。
- 移交后待办：`telemetry_ore_washer` 正式贴图与 0.2 全套（17 张：9 物品 + 4 台机器 8 张 overlay）均已交付并接入 `src/main/resources`（`Public/README.md` 是美术清单与分层复用指南）。

## 0.3 IV 自建算力已完成（2026-11-27，0.3.0）

- **交付**：`raised_floor`/`rack_casing_intake`/`rack_casing_exhaust` 方块、`iv_compute_blade`/`green_snack_pack` 物品、`air_cooled_compute_rack`（3×5×2 多方块，4 刀片 0→24 CWU/t 线性，实现 IOpticalComputationProvider，同 tick 预算 + 降额原因枚举）、`precision_crac`（3×3×1，PUE Tier I 1.80→II 1.45，环境冷源 ×0.6/×2.0，零食 -5%）、`iv_inference_hatch`（挂塔部件：芯片 2/8 CWU/t → EU/t -25%/-50%，白名单 hc_inference_boost 标签，逐 tick 计费 + 断算看门狗回退）。全部 9 机器渲染态注册 + runGametest **44/44**（新增 hypercluster_03_datacenter 8 例 + 生存配方零 HPCA 检查）；Xvfb 冒烟零内容 ERROR（仅 vanilla text2speech narrator 报错，历史版本同样存在）。报告 `docs/reports/0.3_acceptance_report.md`。
- **配方**：机柜/空调/推理仓/刀片/地板全部 IV 原版材料可合成；科研站 32 CWU/t 配方只能由机柜"供算"不能驱动跑完（原生 totalCWU 门限 available>=sum），留 0.5+ 聚合。
- **多方块三坑补充（长期有效）**：
  - `FactoryBlockPattern.start()` 默认是 **(LEFT, UP, FRONT)**：aisle 走 FRONT(z)、string 走 UP(y)、char 走 LEFT(x 西向镜像)；GT 原版 research_station/HPCA 全用默认。我方 rack/CRAC 用显式 `start(RIGHT, UP, BACK)`（char→东、string→上、aisle→南）。摆结构前先确认这三个参数。
  - **光学管道 max 2 连接且不自动连接先放的方块**：GameTest 里必须显式 `pipe.setConnection(side, true, false)`（side=管→方块方向）；接收仓 getOpticalNetProvider 要求管道 isConnected(朝接收仓面)，且 walker 只有在 handler facing == 指向源仓方向时才跳过"把自己当 provider"的环路。
  - **GameTest 框架雷**：在 runAtTickTime 回调内部再调 helper.runAtTickTime 会损坏 GameTestInfo 的 Object2LongOpenHashMap → NPE 崩掉整个 GameTestServer（wrapped is null）。所有调度必须在测试方法体（tick 0）注册。
  - 电路隔离：`circuitMeta` 配方与同输入无电路配方**不互斥**（lookup 谁先谁赢）；推理增强改用配方 data 标签 + 部件 modifyRecipe 白名单，不再用电路号变体。
  - CWU 计费原生路径：receiver 仓的 NotifiableComputationContainer.handleRecipeInner 每 tick handleTickRecipeIO 计费；配方带 cwu tickInput 即自动逐 tick 扣。部件 modifyRecipe 可以给配方**加上** cwu tickInput + 折扣 EUt（配 matchContents 只查原配方，加法安全）。
  - 1.20.1 Biome API：`getPrecipitationAt(BlockPos)` + `getModifiedClimateSettings().temperature()`（getBaseTemperature 已移除）。
  - 维护仓 `setMaintenanceProblems((byte)0)` = 6 问题全在（位=已修复）。
- **0.3 遗留**：断算回退会 interrupt 当前批次（原版语义丢输入/进度）；零食开关是系统属性 -Dhypercluster.snackBonus；科研站跑配方需 32 CWU/t（双机柜/网络交换机聚合留 0.5+）。

## 0.4 IV 闭源云已完成（2026-11-27，0.4.0）

- **交付**：`satellite_dish_frame/panel/feed_horn` 方块、`api_credential_card`（右击站点控制器绑定 UUID）/`token_credit_card`（NBT 整数 Token）物品、`api_satellite_station`（3×3×3 固定多方块：钢架+反射面板+顶面中心馈源，LV/MV 能源仓即可驱动，馈源露天直视天空否则拒联机；数据变现=高纯数据集自动出售 50k/份、晶卡整额存取）与 `local_fallback_gateway`（IV 被动部件，429/缺额/型号下线 → 本地 ×4 降速零计费兜底）。Token 账本 `CloudTokenLedger`（SavedData 挂 overworld `hypercluster_cloud_ledger`，跨站跨维度共享）：UUID 账户 long 余额、一次性 5,000,000 体验金（标志持久化唯一）、订单三态 hold/settle/release（订单 ID 去重不双扣、HELD 精确退款不吞单、≤512 条有界）、Tier 1–5 并发（累计消费 50 万/200 万/500 万/2000 万 → 1–5 会话，429 超限暂停不计费，会话运行态重启清零）。计费纯函数 `CloudPricing`：基准 25,000/100t；缓存键 (playerUuid, machinePos, recipeId, modelId, tier) 连续第 2 轮起 ×0.1；Batch ×0.5 账单 ×2 耗时**乘法叠加**（缓存+批处理=5%）；档位 Medium ×1 / ExtraHigh ×2 单价 ×0.5 耗时 120 EU/t；雨 ×1.5 雷 ×2.0 耗时；Y>120 ×0.95 账单 ×0.9 耗时。模型 `CloudModels`：omni_flash 永不下线 + legacy_llama 游戏日生命周期（-Dhypercluster.legacyRetireDay=3/leavingSoonDays=1，LEAVING_SOON 仍可用显倒计时、RETIRED 拦截不计费）。runGametest **56/56**（新增 hypercluster_04_cloud 11 例单例批次 + 生存批 1 例）；Xvfb 冒烟零内容 ERROR（仅历史 narrator 报错）；0.4 正式贴图（Public/ 9 张 + 7 Aseprite 源，`block/cloud/` 路径）已交付并直接接入，无占位。报告 `docs/reports/0.4_acceptance_report.md`。

## GTCEu 7.4.0 + LDLib 1.0.40.b 原生 CTM 标准配置（0.4.1 接入，长期复用模板）

- **零代码接入**：方块不需要任何 Java/Mixin 改动（GTCEu 的 `computer_casing`/`fusion_casing_mk2/3`/`cleanroom_glass`/`plascrete` 全是普通 registrate block + 普通模型）。LDLib 自带 `SpriteResourceLoaderMixin` 扫描全部 `.png.mcmeta` 的 `ldlib.connection` 并用 `SingleFile` 源自动把 CTM 图缝进 blocks 图集；`ModelEvent$ModifyBakingResult` 时给引用了带 connection 贴图的模型包 `CustomBakedModel`。机器 `workableCasingModel` 引用 CTM 贴图同样生效（GTCEu 自家 cleanroom 机器就是 `workableCasingModel(plascrete)` + plascrete CTM）。
- **标准模板**（与基础 16×16 贴图同目录）：
  - `<name>.png.mcmeta`：`{"ldlib": {"connection": "hypercluster:block/<dir>/<name>_ctm"}}`（4 空格缩进，GTCEu 同款）；
  - `<name>_ctm.png`：`32×32 RGBA`。
- **32×32 图集布局（LDLib `Submap.uvs` 20 格 + `Connections` 语义，字节码逐格验证）**：4×4 网格、每格 `8×8px` 对应面内一个 `8×8` texel 象限（1:1，非 2× 超采样）：
  - `(0,0)(0,1)(1,0)(1,1)`（左上 16×16）= 四面全连通的**无缝内部**（submap 0/1/4/5，无任何边框）；
  - 右上列 `(0,2)(0,3)(1,2)(1,3)`（submap 2/3/6/7）= 仅上下连通、保留**左右外边框**；
  - 左下行 `(2,0)(2,1)(3,0)(3,1)`（submap 8/9/12/13）= 仅左右连通、保留**上下外边框**；
  - 右下块 `(2,2)(2,3)(3,2)(3,3)`（submap 10/11/14/15）= 四边连通但**对角缺块**（画 2×2 角点小残桩，T/L 凹角封口）；
  - **孤立单块不走 CTM 图**：submap 16–19（`idx>15`）直接采样**原基础贴图**的四个 8×8 象限 —— CTM 图集只画连接态，美术在 x=7|8、y=7|8 中缝与基础贴图逐像素咬合即可。
- **已接入 5 方块**（0.4.1，2026-11-27）：`raised_floor`（仅顶/底面 `raised_floor_top` 启用，侧面 `raised_floor_side` 保持独立）、`rack_casing_intake`/`rack_casing_exhaust`（cubeAll 全六面）、`satellite_dish_frame`/`satellite_dish_panel`。资源在 `assets/hypercluster/textures/block/{datacenter,cloud}/`；美术源 `Public/textures/source/*_ctm.aseprite`，效果预览 `Public/textures/previews/ctm_preview.png`。
- 验收基线：runData/build 零变更、runGametest 56/56、Xvfb 冒烟 blocks 图集正常缝合零 ldlib/ctm/TextureAtlas 报错（唯一 ERROR 为历史 narrator 噪音）。

## GameTest 环境三坑（长期有效，0.4 实测踩坑）

- **`run/gametest/server.properties` 必须 `level-type=minecraft\flat`**：gametest 场地在 y=-60，`normal` 世界上方是天然深板岩（y≈-31..-20），任何露天/天空判定会随机被地形挡（0.4 馈源露天校验因此批量假失败）。已改 flat 并删除旧 `run/gametest/world`（含历史残留结构）。**gametest 世界跨多次 runGametest 持久化**（同一 run/gametest 目录），依赖世界级全局状态（SavedData）的测试必须每测试随机 UUID/订单 ID。
- **vanilla 批内测试并行、单例批次之间也可能交错**：跨测试共享世界状态会互相污染（欢迎金/会话/余额）。0.4 云批次按单例批次（`hypercluster_04_cloud_NN_*`）+ 每测试随机 UUID 隔离后稳定全绿。
- 测试 `System.out` 诊断打印走 **gradle 控制台 stdout**，不进 `latest.log`；排查时抓 gradle 输出（`./gradlew runGametest 2>&1 | grep ...`）。

## GTCEu 7.4.0 多方块四坑补充（长期有效）

- **`Predicates.abilities(...).setExactLimit(1)` = 必需件（min=max=1）**；可选部件要用 `.setMinGlobalLimited(0).setMaxGlobalLimited(1)`（0.3 塔的推理仓模式）。0.4 兜底网关用 exact 曾导致所有无网关站点 "Minimum: 1" 拒绝成型。
- **`CompoundTag.putUUID` 存的是 int 数组**，读回必须 `hasUUID/getUUID`——`getString` 返回空串，UUID.fromString 直接抛异常（0.4 账本订单簿往返曾因此静默丢单）。
- **`GameTestHelper.setBlock/getBlockEntity` 收 ARENA 相对坐标**；机器 `getPos()/hornPos()` 等是世界绝对坐标，转换用 `pos.subtract(helper.absolutePos(BlockPos.ZERO))`。
- 测试内多步时序：**机器每 tick 会立刻消费槽位并启动新轮次**（取消退还会被下一 tick 的缓存命中轮次立即再扣走），改账前后必须在同一 tick 回调内完成断言或清槽防重启。

## 视觉与结构长期规范（0.5 起）

- 新增或重塑的大型多方块以用途确定轮廓，优先选择阶梯收腰、切角、桁架或中空结构。0.5 的立柱、龙门和防撞肋复用 GTCEu 的 `Predicates.frames(GTMaterials.Titanium/Steel)`，塔芯单独使用 `CASING_TITANIUM_PIPE`。
- 每座多方块同时提供运行态粒子与多帧 `overlay_front_active.png`。粒子放在控制器的 `animateTick(RandomSource)`，从同步的 `RECIPE_LOGIC_STATUS` 判断 `WORKING`，并按控制器朝向推算世界坐标。此回调由客户端派发。工作贴图用 16 像素宽的竖向帧条与同名 `.png.mcmeta`；GTCEu 默认模型的 `WAITING` 也可引用工作贴图，粒子只在 `WORKING` 时出现。
- 自定义结构外壳按上文 0.4.1 的 LDLib 模板提供 CTM，并在 Xvfb 客户端验证图集加载。
- GTCEu 7.4.0 自动读取机器描述键 `<modid>.machine.<name>.tooltip`。在语言文件保留主描述，注册时仅向 `.tooltips(...)` 添加额外信息。回归入口为 `SurvivalCraftingTests.machineDescriptionTooltipsNotDuplicated`。
- 0.5 的尺寸、控制器及馈源坐标见 [验收报告](docs/reports/0.5_acceptance_report.md)。`hc_field` 模板为 15×12×15。其基岩地板在 GameTest 相对 y=1，底面含必需空气格的红队舱和卫星站摆在 y=2；要求露天的测试沿用平坦世界。改变场地或大幅改形后，用新的测试世界运行可排除残留方块。
- GTCEu 的全局能力计数以 `SimplePredicate` 实例为键。若把同一组最低仓室需求分别写到两个字符的独立谓词里，会变成每组都要满足最低数量。红队舱在底座检查最低数量，上层只接受可选仓室。

## 0.6.0 LuV 智算与训练已完成（2026-11-27，0.6.0）

- **交付**：`liquid_rack_casing`/`ups_battery_casing` 方块（原生 LDLib CTM，模板同 0.4.1）、`luv_liquid_blade`/`open_weight_module` 物品、`liquid_cooled_compute_rack`（5×5×3，4 刀片 0→64 CWU/t，24 GB 显存/片，交换带宽 64 CWU/t，**PUE 1.20**）、`ups_power_array`（5×4×3，浮充 4096 EU/t、满 4,000,000 EU、放电 8192 EU/t、**60s/1200t 备电**）、`tensor_pretraining_cluster`（7×6×7 阶梯收腰，2400t 训练，**每 25% 落 NBT Checkpoint**，OOM 显存闸门，UPS 保护续训，产出带领域/精度/版本 NBT 的模块）、`cloud_offload_node`（LuV 部件，租富余 CWU/t 按 1.20÷PUE 折算 Token）。runGametest **72/72**；手册新增双语第五章（21 个双语文件）。报告 `docs/reports/0.6_acceptance_report.md`。
- **冷却液是真实输入**：机柜供算时每 tick 喝 1 mB，`LiquidDatacenterRating.coolantEfficiency` 认 `distilled_water`(1.00)/`pcb_coolant`(0.85)，其他一律 0。断液先给 20 tick 容错窗口再归零（`NO_COOLANT`），补液即恢复。
- **⚠️ GT 流体仓排液坑（长期有效）**：`NotifiableFluidTank.drain()` 受 `canCapOutput()` 门控——**输入仓（capabilityIO=IN）直接返回 EMPTY**；`drainInternal(int,...)` 会遍历所有储罐、不按流体匹配。可靠做法是**直接写底层**：`tank.getStorages()[slot].setFluid(remain)` + `tank.onContentsChanged()`。0.6 机柜与预训练集群都用这条路喝冷却液。
- **多方块图案与测试放置必须同源**：0.6 起 GameTest 用 `buildStructure(rows, chars, controllerBlock)` 从**同一份图案数组**生成摆放（控制器落在图案 `'S'` 格），否则手写坐标会和 `FactoryBlockPattern` 漂移。`start(RIGHT, BACK, UP)` 下 **aisle=y 层、每层 3 个 5 字符串对应 z**，即 `rows[y][z]`。
- **可选部件字符要独立**：把可选部件（外包节点）挂在已被 `T` 谓词占用的格子上会整体成型失败且报错指向无关格子；正确做法是让承载它的外层谓词（此处 `L` 机柜外壳）追加 `.or(Predicates.abilities(CLOUD_OFFLOAD).setMinGlobalLimited(0).setMaxGlobalLimited(1))`。
- **先摆部件再成型**：多方块成型后拒绝结构编辑，带可选部件的结构必须在 `ensureFormed` 之前把部件放好。
- **测试场地边界**：结构贴场地边缘时，GT 的正反两种匹配变体可能探到场地外（bedrock），表现为「某台机器单独摆就成型、加挂部件就不成型」。把结构往内挪或加外墙余量。

## 0.6.1 机房拓扑补完 + TB 显存 + 模型注册表（2026-11-27，0.6.1）

- **交付**：`vram_rack_casing`/`optical_switch_casing`（原生 CTM）、`iv_hbm_module`(4 TB)/`luv_hbm_module`(16 TB)/`jailbreak_card` 物品、`vram_storage_rack`（5×5×3，4 槽，16/64 TB）、`optical_fabric_switch`（5×4×3，聚合 16 格内 IV 24/LuV 64，**带宽 128 CWU/t**，超限 **503 + 300 ticks**）、`moe_router_rack`（5×5×5，部署槽 + 4 LoRA + DeepThink）、`AIModelDefinition`/`AIModels` 十台模型。runGametest **79/79**；手册新增双语第六章。报告 `docs/reports/0.6.1_acceptance_report.md`。
- **显存口径迁移**：`VRAM_PER_BLADE = 0`（刀片纯算力），集群显存只由 `VramStorageRackMachine` 提供；训练 OOM 门槛 **96 TB**（`VramRating.TRAINING_JOB_TB`），两座满插 LuV 柜过关。显存须 `state == ONLINE` 才计入池。
- **模型注册表**：`AIModelDefinition` 是**运行时注册表**（非 MC registry），`Camp.LOCAL` 占显存/零 Token、`Camp.API` 零本地显存/计 Token；`Trait` 枚举承载各模型专属机制。`CloudModels.MODELS` 现为 API 阵营视图，`defaultModel()` 跳过带 `LIFECYCLE` 的型号，避免新站启动即 RETIRED。
- **GameTest 场地共享**：`hc_field` 由测试批次共享。0.6.1 的 16 格聚合会串场。0.6.2 拓扑测试在各自场地内铺明确的地板，验证具体 CWU、TB 和断链归零。
- **⚠️ 机器状态在 tick 中刷新**：在同一 `runAtTickTime` 回调里改槽位再读 `state` 会读到**上一 tick** 的判定。改完必须在**后续 tick** 断言（0.6.1 路由柜 OOM/ONLINE 曾因此假失败）。
- **占位图流程**：美术未到位时可程序化生成 16×16 / 16×64 占位图先行通关；正式素材到位后用 `cmp` 逐字节核对替换，并以 jar 体积变化佐证打进了交付物。

## GTCEu 7.4.0 开发与验收补充

- GT 配方的物品 output content 使用 `Ingredient`。需从输入复制 NBT 的产物可在结算后打标签。用 `GTRecipeBuilder.inputItems/outputItems` 时，显式传物品或 `ItemStack`，避免 `ItemEntry` / `Supplier` 误入 `Object` 重载。
- 多方块的 `EnergyContainerList` 为聚合视图。GameTest 模拟断电时操作能源仓本身并停止补电回调。
- 客户端日志时间戳受语言设置影响。冒烟验证用 `/ERROR]` 匹配严重级别，再识别历史 narrator 错误。分步打包时在 `runData` 后另跑一次 `build`，确保 JAR 收入更新的生成资源。

## 机房架构工程规范（0.6.2 起）

- **标准总线与专用仓 I/O**。多方块批量物料经 GTCEu `IMPORT_ITEMS` / `EXPORT_ITEMS`。已完成的任务在输出不可用时保留待结算状态，腾出容量后继续。主控界面用于状态、启停和配置。刀片、HBM、权重、LoRA 等驻留硬件通过支持 Forge 物品能力的机房硬件仓装卸。硬件仓连接原有持久化库存，旧存档安装仓口即可取回已装载硬件。
- **房内地板暗线与跨房原生光路**。底座全部实心支承格必须直接压在同一片正交连通、同高、无缺口的矩形架空地板上，短边至少 5 格、长边至多 24 格、长宽比至多 3:1。放置、拆除、区块装卸及结构变化触发缓存失效。跨房及对外用算使用 GTCEu 原生算力传输仓、连续光学管道、原生算力接收仓；断链归零。交换机将本地与光路来源聚合至 128 CWU/t，并保留 503 冷却 300 tick。
- GTCEu 7.4.0 的原生配方接收仓用 `requestCWUt(Integer.MAX_VALUE, true)` 探测可用额度。交换机把这次模拟读取按带宽上限处理，真实超限请求才执行常规 503 判定。测试原生光路时显式连通两个端口及每段管道。

## 0.6.3 音效 + MoE 大屏已完成（2026-11-28，0.6.3）

- **交付**：`HyperClusterSounds`（8 个 `SoundEntry`）、`HyperClusterDataGenerators`（`sounds.json` 由 GTCEu `SoundEntryProvider` 生成）、`MachineLoopSound`/`MachineLoopSounds`/`MachineAlerts`、`MoeScreenRender`（6×6 鲸娘大屏）、`SoundRegistrationTests`（4 例）。runGametest **91/91**；四道门禁 + Xvfb 冒烟全绿。报告 `docs/reports/0.6.3_acceptance_report.md`。分支 `feat/0.6.3-audio-screen-docs`。
- **⚠️ GTCEu 音效注册时机**：`IGTAddon#registerSounds()` 由 `GTSoundEntries.init()` 调用，**早于** `initializeAddon()`，是唯一可注册窗口（之后 `GTRegistries.SOUNDS` 已 freeze）。`GTRegistry` 的查询方法是 **`containKey`**，不是 `containsKey`。
- **⚠️ 非配方机器没有原生工作音效**：`RecipeLogic#updateSound()` 只看配方类型的 `setSound`。运行 `DUMMY_RECIPES` 的机器（风冷/液冷机柜、卫星站）永远报 IDLE，必须自行用同款 `AutoReleasedSound` 驱动；`shouldWorkingPlaySound()` 定义在 `IRecipeLogicMachine` 而非 `MetaMachine`。
- **⚠️ 动态渲染器在 7.4.0 的形态**：接口名不是 `IDynamicRender`——是 `DynamicRender<T,S>` 抽象类（`client/renderer/machine/`）。挂载点为 `GTMachineModels.createWorkableCasingMachineModel(...).andThen(b -> b.addDynamicRenderer(Supplier))`。**类型必须在构造期注册**（`DynamicRenderManager.register`，幂等守卫），否则 **runData 期**解码模型 JSON 的 `dynamic_renders` 就抛 "is not registered"。
- **⚠️ 用 `.model(...)` 替代 `workableCasingModel(...)` 会丢模型属性**：`workableCasingModel` 内部会 `modelProperty(RECIPE_LOGIC_STATUS, IDLE)`；直接 `.model()` 必须自己补 `.modelProperty(...)`，否则 datagen 报 "Cannot get property recipe_logic_status as it does not exist"。
- **⚠️ GameTest 里读 `assets/` 不能用服务端 `ResourceManager`**：它按 `PackType.SERVER_DATA` 构建，只看得见 `data/`。校验 `sounds.json` / `.ogg` 存在性要用类加载器（`HyperCluster.class.getResourceAsStream("/assets/...")`），语义等同真实 jar。
- **音效母带恢复**：`Public/sounds/*.ogg` 曾丢失，但其无损 PCM 母带保留在合成脚本的 `scratch/wav_tmp/*.wav`；按 `encode_ogg()` 原参数 `ffmpeg -ac 1 -ar 44100 -c:a libvorbis -q:a 6.0` 重编即可逐样本等价还原。

## 双许可结构（0.6.3 起，长期有效）

- 代码 [LICENSE.MD](LICENSE.MD) = LGPL-3.0；素材 [Public/ASSETS_LICENSE.md](Public/ASSETS_LICENSE.md) = CC0 1.0。
- **例外**：`whale_maid_screen_96.png` 及其 `.aseprite` 源与预览图基于外部参考图重绘，**不在 CC0 内**，待原作者授权。新增/搬运该贴图时勿当作 CC0 素材。

# GregTech HyperCluster

GregTech CEu Modern 的 Minecraft 1.20.1 Forge 附属模组。首版范围和开发顺序见[实施计划](IMPLEMENTATION_PLAN.md)，贴图和分层源文件见[资源清单](Public/README.md)。

## 开发环境

| 组件 | 版本 |
| --- | --- |
| Minecraft | 1.20.1 |
| Forge | 47.4.0 |
| GTCEu Modern | 7.4.0 |
| LDLib / Registrate / Configuration | 1.0.40.b / MC1.20-1.3.11 / 2.2.0 |
| Java（Gradle、编译和游戏） | 17 |
| Java（NeoFormRuntime 生成开发产物） | 21 |
| Gradle Wrapper / ModDevGradle | 8.10 / 2.0.91 |
| Parchment | 1.20.1-2023.09.03 |

工程基于 [GTCEu 1.20.1 官方模板 19cc903](https://github.com/GregTechCEu/1.20.1-Addon-Template/tree/19cc9032b50b378033f95e1e8e0de1ab4e3bf062)。模组 ID 为 `hypercluster`，Java 包为 `dev.hypercluster`。

在项目根目录执行以下命令。环境脚本优先使用 `.tools/jdk-17`，也接受指向 Java 17 JDK 的 `JAVA_HOME`。Java 21 可放在 `.tools/jdk-21`，也可通过 `HYPERCLUSTER_JDK_21` 指定。Gradle 则可通过 Foojay 自动下载缺少的工具链。IDE 可导入 `build.gradle` 并选择 Java 17 作为 Gradle JVM。

bash/zsh：

```bash
source ./env.sh
./gradlew runData
./gradlew build
./gradlew runClient
./gradlew runGametest
```

fish：

```fish
source env.fish
./gradlew runData
./gradlew build
./gradlew runClient
./gradlew runGametest
```

资源生成输出在 `src/generated/resources/`，构建产物在 `build/libs/`。首次构建会下载 Gradle 和开发依赖。在启用命令的测试世界里可用 `/give @s hypercluster:mining_telemetry_log` 验证物品注册和模型。`runGametest` 以 headless 方式跑全部回归（Forge GameTestServer，目前 35 个用例：物品/配方、P02 结算探针、P03/P04 闭环与边界、P05 生存配方存在性、0.2 EV 数据工坊全链、0.2.1 微调与红队对齐链）。

开发客户端、专服和数据生成分别使用 `run/client/`、`run/server/`、`run/data/`，日志写入各自的 `logs/`。运行 `./gradlew runServer` 首次启动时，Minecraft 会生成 `run/server/eula.txt`。确认同意其中链接的 EULA 后，在该文件设 `eula=true` 并再次启动。

## 0.1.0 内容与支持范围

- **物品**：`mining_telemetry_log` 矿处遥测日志、`blank_carrier` 空白载体、`mining_edge_chip` 矿处边缘芯片（均可堆叠 64，带中英工具提示）。
- **机器**：`hv_micro_burner` HV 微型烧录台、`hv_telemetry_ore_washer` HV 遥测示范洗矿机（SimpleTieredMachine HV）。
- **配方**（JEI 可查，全部 `hypercluster` 命名空间）：
  - 烧录：1 日志 + 1 空白载体 + HV 电力 → 1 芯片（`micro_burner` 类型）；
  - 遥测洗矿类型 `telemetry_ore_washing`：镜像基础洗矿（破碎铜矿石 + 电路1 + 1000mB 水 → 纯净破碎铜矿石 + 石粉 ± 1/3 钴粉）与芯片省水版（电路2 + 芯片不消耗 + **100mB 水**，产物一致）；
  - 生存合成：空白载体（聚乙烯板+硅晶圆+HV电路 ×2）、微型烧录台（HV 机壳/发射器/电路/线缆/玻璃）、遥测洗矿机（**原版 hv_ore_washer** + HV 传感器/电路/线缆/玻璃）。
- **精确结算**：软化排除堵仓/取消/断电未完/空转（0.1 份日志），仅在输出真实落槽后每批 +1 日志；64 容量持久化缓冲（GUI 只取槽、满仓丢弃并提示、拆除返还、存档重载不翻倍）。
- 完整生存流程见 [docs/survival_walkthrough_0.1.md](docs/survival_walkthrough_0.1.md)；各阶段验收见 [docs/reports/](docs/reports/P05_P06_release_report.md)。

## 0.2 核心内容（EV 数据工坊）

- **新增物品**（均堆叠 64 + 中英工具提示）：`chemical_telemetry_log` 化工遥测日志、`clean_dataset` 清洁数据集、`high_purity_mining_dataset` / `high_purity_chemical_dataset` 高纯领域数据集、`corrupted_noise` 乱码噪点、`chemical_edge_chip` 化工边缘芯片。
- **新增机器**：`ev_telemetry_chemical_reactor` EV 遥测示范化学反应釜（SimpleTieredMachine EV，复用基类 `TelemetryMachine` 与 `TelemetryRecipeLogic` 的 IO.OUT 精确结算，禁 Mixin）；`data_scrubbing_tower` 数据清洗塔（GT 固定结构多方块 3×5×3：EV 钛机壳 + 玻璃 + 输入/输出总线、流体输入仓、能源仓、维护仓）。
- **配方**（全部 `hypercluster` 命名空间，JEI 可查）：
  - `telemetry_chemical_processing`：镜像 GTCEu 7.4.0 实配 `chemical_reactor/indium_concentrate_separation`（600 EU/t × 50t，电路#1，4 铝粉 + 1000mB 铟浓缩液 → 小堆铟粉 + 4 亚硫酸铝粉 + 1000mB 铅锌溶液）与芯片版（电路#2 + 芯片不消耗，**辅助铝粉减半 4→2**，产物种类数量不变）；
  - `data_scrubbing`：1 原始日志（矿处/化工）+ 1000mB 蒸馏水 + EV 电力 → 1 高纯领域数据集 + 1 清洁数据集 ± 90% 乱码噪点；乱码噪点 ×4 + 250mB 蒸馏水 → 1 硅粉（所有输出可处理）；
  - 微型烧录台扩展：高纯化工数据集 + 空白载体 → 化工边缘芯片；高纯矿处数据集 + 空白载体 → 矿处边缘芯片（128 EU/t × 64t，比原始日志版**能耗 1/4、耗时 1/2**）；
  - 生存合成：清洁数据集可替代日志位置合成空白载体 ×2；EV 化工反冲炉（消费原版 gtceu:ev_chemical_reactor）；数据清洗塔主控（EV 机壳 + EV 电路 ×2 + 玻璃 + 钛板）。
- **验收**：`./gradlew runData`、`build`、`runGametest`（29/29）全绿；渲染态同步注册覆盖全部 4 台机器并由回归断言（防 0.1 贴图不同步复发）；详细报告见 [docs/reports/0.2_core_acceptance_report.md](docs/reports/0.2_core_acceptance_report.md)。

## 0.2.1 内容（微调与红队对齐）

- **新增物品**（均堆叠 64 + 中英工具提示）：`golden_dataset` 黄金标注样本、`mining_lora_card` / `chemical_lora_card` 领域 LoRA 卡、`aligned_mining_edge_chip` / `aligned_chemical_edge_chip` 已对齐边缘芯片（美术正式贴图已接入）。
- **清洗塔副产扩展**：两条清洗配方在 9/10 乱码噪点之外，以 **1/10** 追加副产黄金标注样本。
- **新增机器**（EV 固定多方块，共用 3×3×3 舱室结构：EV 钛机壳 + 玻璃 + 标准 5 类仓室）：
  - `lora_engraving_chamber` 神经微调刻录舱（配方类型 `lora_engraving`）：高纯领域数据集 + 空白载体 + 黄金样本 + 500mB 蒸馏水 + EV 电力 → 对应领域 LoRA 卡；
  - `red_team_sandbox` 沙盒红队测试舱（配方类型 `red_team_testing`）：2× 乱码噪点（对抗样本）+ 普通边缘芯片 → **已对齐边缘芯片**。
- **电路 `#3` LoRA 专精配方**（不消耗 LoRA 卡，与 `#1/#2` 严格隔离）：洗矿机稀有副产钴粉概率 **1/3 → 2/3**；化反在同量 2 铝粉下**小堆铟粉翻倍**（铝守恒 2→2）。
- **电路 `#4` 已对齐芯片收益**（受控、无恶性幻觉炉渣）：不消耗已对齐芯片，化反 600→**450 EU/t**、洗矿 16→**12 EU/t**（均降 **25%**），产物与基准一致，界面工具提示标明已通过红队对抗校验。
- **0.2 三处修正**：清洁数据集载体配方产量 2→**4**；中文文案统一「化学反应釜」并说明烧录台双样本输入；芯片省料配方亚硫酸铝输出 4→**2**（铝守恒）。
- **验收**：`runData` / `build`（`gregtech-hypercluster-0.2.1.jar`）/ `runGametest`（35/35）全绿，Xvfb 客户端冒烟零内容报错；报告见 [docs/reports/0.2.1_acceptance_report.md](docs/reports/0.2.1_acceptance_report.md)。

## 运行锁定组合（P06 实测）

| 组合 | 版本 | 状态 |
| --- | --- | --- |
| Forge 专服（生产 jar） | Forge 47.4.0 + GTCEu 7.4.0 官方分发包 + 本模组 0.1.0 | 专服冷启动至 `Done`，零 ERROR（`run/pv-gt`） |
| 上述 + AE2 | AE2 15.4.12（**Modrinth/GitHub 构建**）+ GuideME 20.1.15 | 专服冷启动至 `Done`，零 ERROR（`run/pv-ae2`） |

> 注意：modmaven.dev 上的 `appeng:appliedenergistics2-forge:15.4.12` 与官方发布物不是同一构建，其 mixin 在 Forge 生产环境会崩溃；取包请走 Modrinth/GitHub 或核对 sha256（详见发布报告）。

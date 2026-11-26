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

资源生成输出在 `src/generated/resources/`，构建产物在 `build/libs/`。首次构建会下载 Gradle 和开发依赖。在启用命令的测试世界里可用 `/give @s hypercluster:mining_telemetry_log` 验证物品注册和模型。`runGametest` 以 headless 方式跑全部回归（Forge GameTestServer，目前 19 个用例：物品/配方、P02 结算探针、P03/P04 闭环与边界、P05 生存配方存在性）。

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

## 运行锁定组合（P06 实测）

| 组合 | 版本 | 状态 |
| --- | --- | --- |
| Forge 专服（生产 jar） | Forge 47.4.0 + GTCEu 7.4.0 官方分发包 + 本模组 0.1.0 | 专服冷启动至 `Done`，零 ERROR（`run/pv-gt`） |
| 上述 + AE2 | AE2 15.4.12（**Modrinth/GitHub 构建**）+ GuideME 20.1.15 | 专服冷启动至 `Done`，零 ERROR（`run/pv-ae2`） |

> 注意：modmaven.dev 上的 `appeng:appliedenergistics2-forge:15.4.12` 与官方发布物不是同一构建，其 mixin 在 Forge 生产环境会崩溃；取包请走 Modrinth/GitHub 或核对 sha256（详见发布报告）。

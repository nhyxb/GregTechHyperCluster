# 项目记忆

## GitHub 仓库

- 已创建公开仓库 [nhyxb/GregTechHyperCluster](https://github.com/nhyxb/GregTechHyperCluster)（2026-11-25，初始提交 6ace997，35 个文件）。
- 远端 `origin` 使用 HTTPS 协议；默认分支 `main`。gh CLI 已登录账号 `nhyxb`。

## Git 身份

- 本仓库本地已配置 `user.name=nhyxb`、`user.email=77566653+nhyxb@users.noreply.github.com`（GitHub noreply 邮箱）。
- 全局未配置 git 身份；其他项目首次提交前需要另行配置。

## P01/P02 已完成（2026-11-26）

- 三件套物品已注册（`mining_telemetry_log` / `blank_carrier` / `mining_edge_chip`，堆叠 64，TooltipItem 追加 `item.hypercluster.<name>.tooltip`；en_us/en_ud 由 Registrate 生成，zh_cn.json 手工维护）。验收报告在 `docs/reports/P01_P02_acceptance_report.md`。
- **基准配方**：机器 `gtceu:hv_ore_washer`（HV 洗矿机），配方 `gtceu:ore_washer/wash_copper_crushed_ore_to_purified_ore`：1 破碎铜矿石 + 编程电路#1 + 1000mB 水 → 1 纯净破碎铜矿石 + 1 石粉（常规），钴粉为 1/3 概率副产；原配方 400t @16EU/t，HV 上 OC_NON_PERFECT 后 100t @256EU/t。
- **P02 决策**：0.1 首发采用附属自有 HV 示范矿机（非通用遥测覆盖板）。实测 7.4.0 上 `afterWorking()` 先于输出结算、`interruptRecipe()` 与堵仓批次都会触发它且产物为空/丢失（堵仓时 `onRecipeFinish` 忽略 IO.OUT 失败，产物静默丢弃）。P04 实现自有矿机时应覆写 `createRecipeLogic()`/读取实际 IO.OUT 结果记账。
- 探针资产：`dev/hypercluster/probe/SettlementProbe.java`（包装 `MachineDefinition#setAfterWorking`，公开 setter，无需 mixin）、`dev/hypercluster/gametest/SettlementProbeTests.java`（7 用例）、结构模板 `src/main/resources/data/hypercluster/structures/hc_floor.nbt`（NBT 根须为带名字的 TAG_Compound，gzip）。
- GameTest 复现命令：`./gradlew runGametest`（`forge.gameTestServer=true` + `forge.enabledGameTestNamespaces=hypercluster`），输出 grep `HC-PROBE`。

## 环境注意事项

- `run/server/eula.txt` 从未置为 true（P00 当时 runServer 实际在 EULA 检查处退出，未完成“完成启动”验收）；`run/gametest/eula.txt` 已按 README 流程置 true。
- 机器有 `DISPLAY=:1` 与 NVIDIA GPU：`./gradlew runClient` 可启动到主菜单（GTCEu 完整资源装载无缺失纹理错误）。
- GTCEu 7.4.0 的 1.20.1 源码可从 GitHub tag `v7.4.0-1.20.1` 拉取；MachineDefinition 有公开 `setAfterWorking` 等 setter（jar 实物为准）。

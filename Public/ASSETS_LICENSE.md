# 素材许可（Assets License）

本文件规定 GregTech HyperCluster 的**美术与音频素材**授权。代码授权见仓库根目录的 [LICENSE.MD](../LICENSE.MD)（LGPL-3.0）。本项目采用**代码与素材分离的双许可结构**。

---

## 1. CC0 1.0 Universal（适用素材）

下列目录中**由本项目原创（含 AI 生成，无外部第三方作者）**的工业贴图、连接纹理（CTM）、动画帧与程序化合成音效，均以 **CC0 1.0 Universal（公共领域贡献）** 发布：

- `Public/` 下的纹理与预览图（`textures/block/`、`textures/item/`、`textures/previews/`、`textures/source/`）
- `src/main/resources/assets/hypercluster/` 下的全部纹理、模型引用与音频资源

具体包括但不限于：

| 类别 | 内容 |
| --- | --- |
| 机器外壳与结构方块 | 机房架空地板、42U 机柜进/出风面、液冷机柜壳、VRAM 机柜壳、光交机柜壳、UPS 电池壳、卫星天线钢架/反射面板/馈源、各类控制器正面与工作动画帧 |
| 连接纹理（CTM） | 全部 `<name>_ctm.png` 32×32 图集及其 `.png.mcmeta` 声明 |
| 物品图标 | 遥测日志、数据集、边缘芯片、LoRA 卡、计算刀片、HBM 显存条、权重模块、凭证/晶卡等 |
| 动画贴图 | 各控制器 `overlay_front_active.png` 多帧竖条 |
| 预览图 | `Public/textures/previews/` 下的 `contact_sheet.png`、`ctm_preview.png`、`active_animations_sheet.png` |
| MoE 发光大屏 | `whale_maid_screen_96.png`（96×96）及其 `.aseprite` 源文件与预览图：原始参考图由 OpenAI Image 生成，96×96 像素画由 Gemini + Aseprite MCP 绘制，无外部第三方作者 |
| 音效 | `Public/sounds/` 与 `assets/hypercluster/sounds/` 下 8 个 `.ogg` |

### 音效说明

8 个 `.ogg` 音效均为**程序化合成**（numpy/scipy 数字信号处理生成，非采样自任何外部录音或商业音库），已统一为：

- 单声道（mono）、44100 Hz、Ogg Vorbis；
- 响度归一至 **−14.0 LUFS**，真峰值 ≤ **−1.2 dBTP**，无削波；
- 6 个工作循环音（`datacenter_air_hum`、`datacenter_liquid_hum`、`pretraining_cluster`、`satellite_uplink`、`laser_burn`、`red_team_scan`）做了等功率交叉淡化与过零对齐，可无缝循环、无爆音；
- 2 个单次音（`rack_click`、`terminal_alert`）带干净静音尾部。

因其完全由算法生成、不含任何第三方素材，故可无条件置于 CC0。

### CC0 1.0 摘要

在法律允许的最大范围内，上述素材的作者放弃全部著作权及相关权利。你可以：

- **复制、修改、分发、演绎**，包括商业用途；
- **无需署名**（署名仍受欢迎，但非义务）；
- **无需保留本声明**。

素材按「现状」提供，不附带任何明示或默示担保。

完整法律文本：<https://creativecommons.org/publicdomain/zero/1.0/legalcode>

---

## 2. 明确排除条款（NOT CC0）

### 2.1 第三方内容

本项目的下列内容**不属于**本项目，各自遵循其原授权：

- GregTech CEu Modern、LDLib、Registrate、Configuration 等依赖模组的全部资源与代码；
- Minecraft / Forge 的原始资源；
- `Public/textures/` 中若存在任何来源非本项目原创的素材（当前不存在）。

---

## 3. 贡献

向本项目提交美术或音频素材，即表示你确认：

1. 该素材为你原创，或你拥有足以按上述条款授权的权利；
2. 你同意该素材以 **CC0 1.0 Universal** 发布（除非在提交时明确声明例外，且该例外被维护者接受）。

请勿提交来源不明、未经授权的第三方素材。

---

## 4. 摘要

| 范围 | 授权 |
| --- | --- |
| 代码（Java 源码、构建脚本） | [LGPL-3.0](../LICENSE.MD) |
| 原创工业贴图 / CTM / 动画 / 预览图（含 AI 生成的鲸娘大屏贴图） | **CC0 1.0** |
| 8 个程序化合成音效 | **CC0 1.0** |
| 第三方依赖 | 各自原授权 |

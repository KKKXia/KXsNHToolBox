<h1 align="center">KXsNHToolBox</h1>

<p align="center">
  <a href="https://github.com/KKKXia/KXsNHToolBox/releases">
    <img src="https://img.shields.io/github/v/release/KKKXia/KXsNHToolBox" alt="Latest Release">
  </a>
  <a href="https://github.com/KKKXia/KXsNHToolBox/stargazers">
    <img src="https://img.shields.io/github/stars/KKKXia/KXsNHToolBox?logo=github" alt="GitHub Stars">
  </a>
  <a href="https://github.com/KKKXia/KXsNHToolBox/blob/master/LICENSE">
    <img src="https://img.shields.io/github/license/KKKXia/KXsNHToolBox?logo=apache" alt="License">
  </a>

## 项目概述
KXsNHToolbox 是一个为 GT NewHorizon 开发的工具型模组

通过添加一系列作者认为有用的 ~~（大概吧）~~ 功能改善游戏体验

> [!WARNING]
> 本模组非GTNH官方模组，请勿在官方场合讨论相关内容

## 核心功能

### 新增物品

<details>
<summary><b> 扩展显示元件 </b></summary>
<ul>
  <li> 由显示元件与存储总线无序合成而来 </li>
  <li> 右键存储总线（物品/流体）直接复制存储总线标记内容，空间不足时在聊天栏提示 </li>
  <li> 其他内容与原版显示元件一致 </li>
</ul>
</details>

### 新增功能

<details>
<summary><b> 浮空放置 </b></summary>
<div align="center">
  <img src="image/浮空放置操作示意图.png" width="100%" />
</div>
<ul>
  <li> 默认按下 `G` 键激活/关闭浮空放置模式 </li>
  <li> 右键点击可在视角方向的绿色框中放置手中的可放置物品 </li>
  <li> 使用方向键调整已放置方块的位置，调整过程中再按 `G` 可重置，重新选择位置 </li>
  <li> 相关配置（`config/NHToolbox.cfg`）：`rayTraceDistance` 放置距离、`enablePreviewBox` 预览框、`enableParticles` 粒子效果、`consumeItemsInSurvival` 生存模式消耗物品 </li>
</ul>
</details>

<details>
<summary><b> 背包栏位锁定 </b></summary>
<ul>
  <li> 默认按下 `鼠标中键` 锁定/解锁鼠标指向的栏位，按键可在按键设置的 `NHToolbox` 分类中修改 </li>
  <li> 锁定会记住该栏位里的物品：之后只接受同一种物品，其它物品既放不进去、也整理不进去；空栏位无法锁定 </li>
  <li> 物品被取空后，栏位里保留一个淡化图标提示锁定内容，四周渲染一圈细边框 </li>
  <li> 对玩家背包（快捷栏 + 主物品栏）与背包模组的背包格都生效，状态按栏位下标保存，重进游戏依然有效 </li>
  <li> 所有容器界面通用（箱子、熔炉、工作台、AE 终端……），并且不只是"拦住"：shift 快捷移动、InventoryTweaks 的空格/ctrl 快捷搬运与整理、从地面拾取、AE 终端取物都会优先把同种物品送进锁定格 </li>
  <li> 默认关闭：在 `config/NHToolbox.cfg` 中把 `enableSlotLock` 改为 `true` 并重启游戏 </li>
  <li> 规则在客户端执行：单机与本地服务器两端一致；多人服务器未安装本模组时以服务端为准 </li>
</ul>
</details>

### 适配版本
| GTNH 版本  | 起始兼容版本 | 最新兼容版本 |                                                                   下载                                                                   | 维护状态 |
|:----------:|:------------:|:------------:|:----------------------------------------------------------------------------------------------------------------------------------------:|:--------:|
| 2.9-bate-1 |    0.2.0     |  0.3.0-pre1  | [![0.3.0-pre1](https://img.shields.io/badge/release-v0.3.0pre1-00FF00)](https://github.com/KKKXia/KXsNHToolBox/releases/tag/v0.3.0-pre1) |    ❌️    |
| 2.9-bate-3 |  0.3.0-pre3  |  0.3.0-pre3  | [![0.3.0-pre3](https://img.shields.io/badge/release-v0.3.0pre3-00FF00)](https://github.com/KKKXia/KXsNHToolBox/releases/tag/v0.3.0-pre3) |    ✔️    |
---

# Network Terminal 只适配单一 guiScale

## 错误现象

网络终端 GUI 在 `guiScale=2` 下观感较接近设计稿，但切到其他 GUI 缩放后，面板比例、左侧网络列行高和底部按钮宽度明显失衡。

## 触发场景

打开网络终端后切换 Minecraft GUI 缩放，尤其是 `guiScale=3/4`，固定 GUI 坐标最小宽高会把面板撑成过大的物理尺寸；左侧网络列在缩窄后仍使用通用行高和最小 120 宽按钮，容易出现厚重色块或横向溢出。

## 根本原因

- `NetworkUiKit.terminalPanelWidth` / `terminalPanelHeight` 原先用固定 GUI 坐标 min/max 做 clamp，没有把 Minecraft GUI 坐标会随 `guiScale` 放大成物理像素这一点纳入计算。
- `NetworkTerminalUI.renderNetworkRail` 原先复用通用 `ROW_H` 构建左侧过滤框、网络行和默认按钮，导致 rail 的视觉密度无法单独调整。
- 左侧默认网络按钮原先使用 `Math.max(120, layout.railW - 12)`，当 rail 宽度收缩到 96 左右时，按钮天然可能宽于容器。
- 后续只把面板外框按 `2.0f / guiScale` 缩小仍不够：MUI2 内部文字、按钮、列表行和滚动条依然按当前 Minecraft `guiScale` 渲染，导致 `guiScale=3` 的内部控件比 `guiScale=2` 更厚、更大。

## 修复方案

- 在 `NetworkUiKit.java` 中以 `guiScale=2` 作为参考逻辑骨架；`guiScale>2` 时保持同一套 `terminalPanelWidth` / `terminalPanelHeight` / `terminalLayout`，不再重新压缩内部布局。
- 在 `NetworkTerminalUI.java` 中通过 `ReferenceScaledPanel` 覆盖 `ModularPanel.getScale()`，用 `terminalVisualScale(guiScale)` 做面板级 transform，并乘回 `super.getScale()` 保留 MUI2 原有开场动画和命中测试路径。
- 为左侧 rail 单独定义 `RAIL_HEADER_H`、`RAIL_FILTER_H`、`RAIL_ROW_H`、`RAIL_ACTION_H`，并用 `terminalRailChromeHeight()` 统一计算列表可用高度。
- 用 `railActionWidth()` 限制默认网络按钮始终位于 rail 内部。
- 在 `NetworkUiKitTest.java` 增加高 GUI 缩放、参考视觉缩放、rail 紧凑高度、最小边界和按钮宽度断言。

## 预防措施

- 修改 MUI2 网络终端布局时，不要只在一个 `guiScale` 下凭截图调固定数值；必须验证 `terminalPanelWidth`、`terminalPanelHeight` 和 `terminalLayout` 在至少 `guiScale=2/3` 下的边界。
- 高 GUI 缩放问题不能只看外框物理大小，还要确认内部文字、控件高度和滚动条是否一起缩放；优先使用面板级 transform 保持视觉和命中测试一致。
- 左侧 rail 的控件高度应继续使用 rail 专用常量，不要回退到通用 `ROW_H`。
- 新增会影响面板尺寸、按钮宽度、列表高度的改动时，同步补充或更新 `NetworkUiKitTest` 中的尺寸断言。

# MUI2 TextWidget SIZING padding 溢出

## 错误现象

游戏日志中反复出现（非致命，未阻断渲染）：
```
[modularui2/]: MUI [SIZING][TextWidget]: Margin/padding is set on both sides on axis Y, but total size exceeds parent size.
[modularui2/]: Failed to resize widget. Affected resize node tree:
```

发生在打开 Network Terminal GUI 时，ResizeNode tree 显示受影响 widget 为 TextWidget，位于 ExpanderResizer(Column) 内嵌套的 ExpanderResizer(Row) 中。

## 触发场景

- 打开 Network Terminal（`NetworkTerminalUI.create(te)`）
- 打开 Network Tab（`NetworkTabUI.create(te)`）
- 面板切换（SELECTION → MEMBERS → SETTINGS 等）

## 根本原因

1. **固定高度 Row + TextWidget padding 叠加溢出**：代码中多处 `Flow.row().height(24)` 或 `.height(38)` 等固定高度行，内部 TextWidget 的 `.padding()` 设置与 MUI2 主题默认 padding 叠加，导致 TextWidget 的垂直 margin+padding 超过父 Row 可用高度。

2. **受影响位置**（`NetworkTerminalUI.java`）：
   - `buildNavButtons()` — nav 按钮 `padding(5, 10)`（垂直=5，水平=10）
   - `updateNetworkBar()` — 网络信息栏 `padding(6, 12)`（垂直=6，水平=12）
   - `renderSelection()` — 列表头 `padding(4, 12)`（垂直=4，水平=12）
   - `formRow()` — 表单行 `padding(4, 12)`（垂直=4，水平=12）
   - `buildPanel()` — 底部操作区 `padding(10, 12)`（垂直=10，水平=12）

3. **受影响位置**（`NetworkTabUI.java`）：
   - `buildPanel()` — root `padding(14)`（四边 14px）
   - `buildSummaryRow()` — 摘要行 `.height(56)` + summaryBox `.padding(10)`
   - `rebuildBottom()` — 选中栏 `padding(8)` + `.height(38)`

4. **MUI2 机制**：`Box.padding(vertical, horizontal)` 和 `Box.margin` 与 widget 的默认 min height（18px）以及主题 padding 叠加时，如果总和超过父容器分配高度，MUI2 报 SIZING 错误但**不阻断布局**——widget 仍会渲染，只是尺寸被裁剪。

## 修复方向（未实施）

- 减少固定高度 row 上的 padding 值，或使用相对高度
- 将部分 padding 移到 row 内部子元素而非 row 自身
- 对导航按钮等固定高度 widget 使用 `margin` 替代 `padding`（margin 不计入内容尺寸）
- 使用 `Box.all(0)` 显式清零不必要的 padding

## 影响评估

- **严重程度**：低 — 日志报错但 GUI 正常渲染，不影响交互
- **用户体验**：无明显视觉异常
- **建议**：后续迭代修复，当前可接受发布

## 相关提交

`3bf55d7` — `[Migrate]: Qz-UILib → ModularUI2 网络 UI 迁移`

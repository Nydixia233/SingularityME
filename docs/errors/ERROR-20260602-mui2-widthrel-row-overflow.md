# MUI2 widthRel 子项横向溢出

## 错误现象

网络终端主页在游戏内显示时，右列信息行被推到面板右侧外部，截图中只能看到右侧残留的标签；导航选中态只显示一个小方块，文字没有被选中背景包住。

## 触发场景

将浏览器参考稿中的两列 CSS grid 直接迁移为 ModularUI2 `Flow.row()`，并在同一行中放入两个 `widthRel(1f)` 的信息行子项；导航按钮也未设置固定宽度，只依赖 overlay 文本视觉展示。

## 根本原因

MUI2 `Flow.row()` 中的 `widthRel(1f)` 子项不是 CSS grid 的 `1fr 1fr`。每个子项都会按父级宽度 100% 求解，两个子项相加后超过父级，第二列被推出可视区域。导航按钮没有稳定宽度时，背景 drawable 只覆盖按钮自身求解出的小尺寸区域，overlay 文字不会反向撑开背景。

相关源码位置：

- `src/main/java/com/github/singularityme/client/ui/NetworkTerminalUI.java`：`renderHome()` 曾在同一行放置两个 `infoRow()`。
- `src/main/java/com/github/singularityme/client/ui/NetworkTerminalUI.java`：`makeNavBtn()` 曾只设置高度不设置宽度。
- `docs/modularui2/02-layout-sizing.md`：相对父级尺寸和 `Flow` 主轴行为说明。

## 修复方案

- 网络终端主页改回单列紧凑信息行，保留 36px 行高和固定标签宽度，避免横向溢出。
- 新增 `NetworkUiKit.navButtonWidth(int panelWidth, int buttonCount)`，根据面板宽度为导航按钮计算稳定宽度。
- `makeNavBtn()` 显式设置按钮宽度，让选中背景覆盖完整按钮区域。
- HTML 参考页同步改为单列主页布局，避免再次用 CSS grid 误导 MUI2 实装。

## 预防措施

- 浏览器参考稿只能表达视觉方向，不能直接把 CSS grid/flex 的分栏语义等价迁移到 MUI2。
- 在 `Flow.row()` 中不要并排放置多个 `widthRel(1f)` 子项来模拟等分列；需要分栏时优先使用固定宽度或经游戏内验证过的布局模式。
- 导航、工具栏按钮等有背景状态的控件必须设置稳定宽度，不能只依赖 overlay 文本。

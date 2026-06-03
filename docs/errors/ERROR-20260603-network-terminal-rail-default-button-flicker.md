# Network Terminal 默认按钮切换闪烁

## 错误现象

在 Network Terminal 左侧网络栏切换网络时，底部“设为默认/取消默认”按钮会短暂闪烁，表现为按钮在切换瞬间消失又出现。

## 触发场景

打开网络终端，在左侧网络列表中点击不同网络；`selectNetwork()` 选中网络后会刷新内容区（`src/main/java/com/github/singularityme/client/ui/NetworkTerminalUI.java:1213`）。

## 根本原因

左侧网络栏的按钮属于稳定 chrome，但旧实现把它和网络列表行放在同一次整体重建里：切换网络触发 `renderContent()` 后进入 `renderNetworkRail()`，父容器先 `removeAll()`，再重新创建列表和默认按钮。MUI2 在这一帧附近会重新布局和绘制 widget 树，导致按钮可见闪烁。

修复后的结构把稳定 chrome 与易变列表分离：`renderNetworkRail()` 只在缺少持久组件时构建 chrome（`src/main/java/com/github/singularityme/client/ui/NetworkTerminalUI.java:345`），`buildNetworkRailChrome()` 只负责一次性创建标题、过滤框、列表容器和默认按钮（`src/main/java/com/github/singularityme/client/ui/NetworkTerminalUI.java:355`），后续刷新只重建列表行（`src/main/java/com/github/singularityme/client/ui/NetworkTerminalUI.java:397`）并更新按钮文字/背景（`src/main/java/com/github/singularityme/client/ui/NetworkTerminalUI.java:408`）。

## 修复方案

- 在 `TerminalState` 中保存 `railList` 和 `railDefaultButton` 的持久引用。
- 切换网络时保留默认按钮 widget 实例，只更新 `overlay` 和 `background`。
- 新增回归测试 `keepsRailDefaultButtonStableAcrossNetworkSelectionRenders`，断言两次网络栏渲染前后默认按钮是同一个 `ButtonWidget` 实例（`src/test/java/com/github/singularityme/client/ui/NetworkTerminalUITest.java:20`）。

## 预防措施

- MUI2 GUI 中固定位置的操作按钮、标题、过滤框等 chrome，不应随列表数据刷新整棵重建。
- 需要变更状态时优先更新已有 widget 的文本、背景或数据；只有结构确实变化时才 `removeAll()` 父容器。
- 对肉眼可见的闪烁问题，增加“widget 实例稳定性”测试，避免回归成 remove/re-add 模式。

# Network Tab 密码行切换触发 MUI2 resize 失败

## 错误现象

设备网络选择 GUI 中，点击加密网络的“输入密码后分配”后，客户端聊天栏提示：

```text
ModularUI: Failed to resize sub tree of widget 'Column' of screen 'ModularScreen#singularityme:network_tab'
```

日志中的 resize tree 显示同一个选择栏 `Column` 底部同时存在旧 `ButtonWidget` 和新密码输入 `Row`。

## 触发场景

- 打开设备 `NetworkTabUI`。
- 选择一个当前玩家可自助加入的加密网络。
- 点击“输入密码后分配”，共享选择栏进入密码输入模式。

## 根本原因

`NetworkSelectionSurface.rebuildActions()` 原先直接调用 `root.getChildren().remove(...)` 移除底部旧 action 按钮。MUI2 的 `ParentWidget.remove(int)` 会触发子 widget 的 `dispose()` 和 `onChildRemove()` 生命周期，但直接修改 `getChildren()` 返回的列表不会触发这些钩子。

因此旧 action 按钮虽然从 Java 子列表里消失，但 MUI2 resize node 树没有完整解绑；密码行加入后，布局树里残留旧按钮，导致 `network_tab` 的选择栏 `Column` resize 失败。

相关源码位置：

- `src/main/java/com/github/singularityme/client/ui/NetworkSelectionSurface.java:141` 创建稳定 `actionArea`。
- `src/main/java/com/github/singularityme/client/ui/NetworkSelectionSurface.java:237` 在 `rebuildActions()` 中只重建 `actionArea` 内部内容。
- `src/test/java/com/github/singularityme/client/ui/NetworkSelectionSurfaceTest.java:22` 覆盖进入密码模式时底部槽位保持稳定。

## 修复方案

- 在共享选择栏根 `Column` 中固定保留一个 `actionArea` 槽位。
- `rebuildActions()` 不再直接删根节点子项，而是在 `actionArea` 内部调用 `removeAll()`。
- 密码行、结果提示、默认/分配按钮只在 `actionArea` 内部切换；根 `Column` 的子节点结构保持稳定。
- 新增回归测试 `keepsStableActionAreaWhenEnteringPasswordMode`，确认进入密码模式前后根节点第 4 个子节点仍是同一个 `actionArea`。

## 预防措施

- MUI2 中不要直接修改 `getChildren()` 返回的列表；移除子节点时使用 `remove(int)`、`remove(IWidget)` 或 `removeAll()`。
- 对会在同一位置切换不同控件形态的区域，优先使用稳定槽位，只重建槽位内部。
- 遇到 resize tree 里出现“已经逻辑移除但仍在树中”的 widget，优先检查是否绕过了父控件生命周期 API。

# NEI 精确 GUI 类匹配导致奇点终端集成失效

## 错误现象

奇点样板终端无法使用 NEI 合成表覆盖，奇点 ME/合成/样板终端也缺少 NEI 书签容器拉取支持。

## 触发场景

玩家打开奇点合成终端或奇点样板终端，点击 NEI 配方覆盖到终端合成网格；或在奇点终端内使用 NEI 书签拉取/请求物品。

## 根本原因

AE2 的 NEI 集成按具体 GUI 类注册，NEI 查询处理器时也使用当前 GUI 的精确类。奇点 GUI 继承 AE2 原 GUI，但类名分别是 `GuiSingularityTerminal`、`GuiSingularityCraftingTerminal`、`GuiSingularityPatternTerminal`，不会自动命中 AE2 对原版 GUI 类的注册。

修复前 `ClientProxy.postInit` 只调用了公共配方注册，没有补充奇点 GUI 的客户端 NEI 注册；修复后入口见 `src/main/java/com/github/singularityme/proxy/ClientProxy.java:34`。

## 修复方案

新增 `SingularityNeiCompat`，在客户端 postInit 且 NEI 已加载时执行：

- 为奇点合成终端和奇点样板终端注册 crafting overlay 与 AE2 的 `NEICraftingHandler`，见 `src/main/java/com/github/singularityme/client/integration/SingularityNeiCompat.java:49`。
- 为奇点 ME/合成/样板终端注册 AE2 的 `NEIAETerminalBookmarkContainerHandler`，见 `src/main/java/com/github/singularityme/client/integration/SingularityNeiCompat.java:59`。
- 保持 Drive 与 Storage Bus 的 AE2 原版优先级标签 Y 偏移为 66，见 `src/main/java/com/github/singularityme/gui/GuiSingularityDrive.java:25` 与 `src/main/java/com/github/singularityme/gui/GuiSingularityStorageBus.java:48`。

## 预防措施

- 后续新增继承 AE2 GUI 的奇点终端时，必须检查 NEI、书签、覆盖处理器是否按精确 GUI 类注册。
- 使用测试锁定注册清单和 AE2 原版标签坐标，见 `src/test/java/com/github/singularityme/client/integration/SingularityNeiCompatTest.java` 与 `src/test/java/com/github/singularityme/gui/SingularityAe2GuiParityTest.java`。

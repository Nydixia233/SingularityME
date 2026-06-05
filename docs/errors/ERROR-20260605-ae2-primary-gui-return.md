# AE2 子 GUI 返回路径缺少奇点 PrimaryGui

## 错误现象

在奇点终端发起自动合成后，合成计划确认页点击“开始”或“取消”会停留在确认页，不会像 AE2 原版一样返回原终端 GUI。

## 触发场景

使用奇点 ME 终端、奇点合成终端或奇点样板终端进入 AE2 的合成数量/合成确认子 GUI。相同根因也会影响通过 `PacketSwitchGuis` 打开的其他 AE2 子 GUI，例如奇点硬盘驱动器/存储总线的优先级页，以及导入/输出/存储总线的矿辞过滤页。

## 根本原因

AE2 子 GUI 的返回不是只依赖 `ContainerOpenContext`。本地反编译确认：

- `PacketSwitchGuis.serverPacketData` 打开子 GUI 后，会调用当前容器的 `AEBaseContainer.createPrimaryGui()`，再把结果写入子 GUI 的 `setPrimaryGui()`。
- `ContainerCraftConfirm.switchToOriginalGUI()` 以及取消按钮发送的无参 `PacketSwitchGuis` 都依赖当前容器的 `getPrimaryGui().open(player)` 返回原界面。
- `AEBaseContainer.createPrimaryGui()` 默认通过 `GuiBridge.getGuiByContainerClass(this.getClass())` 查找原 GUI；奇点容器是 AE2 容器子类或自定义容器，无法命中 AE2 的精确容器类映射。

因此子 GUI 持有的是无法重新打开奇点 Forge GUI ID 的默认 `PrimaryGui`，点击开始/取消后没有正确回到原奇点 GUI。

## 修复方案

新增奇点专用 `PrimaryGui`，直接通过本模组 Forge GUI ID 重新打开原设备 GUI：

- `src/main/java/com/github/singularityme/gui/SingularityPrimaryGui.java:54` 根据当前容器的 `ContainerOpenContext` 创建奇点原 GUI 指针。
- `src/main/java/com/github/singularityme/gui/SingularityPrimaryGui.java:87` 覆盖 `open(EntityPlayer)`，调用 `player.openGui(SingularityME.instance, guiID, world, x, y, z)`。

所有会进入 AE2 子 GUI 的奇点设备容器都覆盖 `createPrimaryGui()`，返回 `SingularityPrimaryGui`：

- `src/main/java/com/github/singularityme/gui/ContainerSingularityTerminal.java:22`
- `src/main/java/com/github/singularityme/gui/ContainerSingularityCraftingTerminal.java:23`
- `src/main/java/com/github/singularityme/gui/ContainerSingularityPatternTerminal.java:23`
- `src/main/java/com/github/singularityme/gui/ContainerSingularityStorageBus.java:93`
- `src/main/java/com/github/singularityme/gui/ContainerSingularityInterface.java:19`
- `src/main/java/com/github/singularityme/gui/ContainerSingularityExportBus.java:42`
- `src/main/java/com/github/singularityme/gui/ContainerSingularityDrive.java:50`
- `src/main/java/com/github/singularityme/gui/ContainerSingularityImportBus.java:41`

接口设备此前服务端直接使用 AE2 `ContainerInterface`，已改为极薄封装 `ContainerSingularityInterface`，主体行为仍沿用 AE2，仅覆盖原 GUI 指针；入口见 `src/main/java/com/github/singularityme/gui/SingularityGuiHandler.java:99`。

## 预防措施

- 奇点设备只要复用 AE2 原生 GUI 或通过 `PacketSwitchGuis` 打开 AE2 子 GUI，就必须同时设置 `ContainerOpenContext` 并覆盖 `createPrimaryGui()`。
- 不能依赖 AE2 的 `GuiBridge.getGuiByContainerClass` 自动识别奇点子类；AE2 对 GUI/容器映射多处使用精确类或固定桥接枚举。
- 回归测试 `src/test/java/com/github/singularityme/gui/SingularityPrimaryGuiTest.java:54` 锁定所有 AE2 风格奇点容器必须覆盖 `createPrimaryGui()`，避免后续设备或重构漏掉返回路径。

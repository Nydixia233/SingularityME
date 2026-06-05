# AE2 合成子容器与总线集成入口未完全对齐

## 错误现象

- 没有 `CRAFT` 权限的玩家仍可从奇点终端进入 AE2 合成计划并提交任务。
- 奇点输出总线不能把物品插入直连的奇点接口。
- 奇点输入/输出总线 tick 频率快于 AE2 原版；存储总线也存在同类硬编码配置漂移风险。

## 触发场景

- 玩家在奇点 ME/合成/样板终端中对可合成物品发起自动合成，下单流程进入 AE2 原生 `ContainerCraftAmount` 与 `ContainerCraftConfirm` 后，原先只在奇点终端容器层的权限拦截不再覆盖后续子 GUI。
- 奇点输出总线面对奇点接口导出物品时，AE2 `InventoryAdaptor` 没有把自定义 `IInterfaceHost` Tile 当作原版接口处理。
- 服务端运行时，输入/输出总线的 `TickingRequest` 仍使用 `1,20` 硬编码，而不是 AE2 的 `TickRates.ImportBus` / `TickRates.ExportBus`。

## 根本原因

- 合成下单不是单一终端容器动作。`src/main/java/com/github/singularityme/gui/SingularityTerminalPermissionGuards.java:29` 现在提供共享 CRAFT 权限判断；修复前只有 `ContainerSingularityTerminal` 等奇点终端容器调用该逻辑，AE2 子容器未接入。
- `src/mixin/java/com/github/singularityme/mixin/mixins/late/ae2/MixinPacketCraftRequest.java:26` 现在在 AE2 开始计算计划前检查权限，`src/mixin/java/com/github/singularityme/mixin/mixins/late/ae2/MixinContainerCraftConfirm.java:22` 现在在最终 `startJob(boolean)` 提交前兜底；修复前这两条入口都缺失。
- `src/main/java/com/github/singularityme/tile/SingularityBusTargetAdapters.java:23` 现在显式把直连的 `IInterfaceHost + ISidedInventory` 目标包装为 `AdaptorDualityInterface`；修复前输入/输出总线直接调用 AE2 `InventoryAdaptor.getAdaptor`，而 AE2 原生特判只覆盖原版接口 Tile 与线缆接口 part。
- `src/main/java/com/github/singularityme/tile/TileSingularityImportBus.java:301`、`src/main/java/com/github/singularityme/tile/TileSingularityExportBus.java:338`、`src/main/java/com/github/singularityme/tile/TileSingularityStorageBus.java:593` 现在读取 AE2 `TickRates`；修复前存在硬编码 tick 范围。

## 修复方案

- 把自动合成 CRAFT 权限判断抽成共享 helper，既供奇点终端容器使用，也供 AE2 late mixin 使用。
- 新增 late mixin 拦截 `PacketCraftRequest.serverPacketData` 与 `ContainerCraftConfirm.startJob(boolean)`，分别阻止无权限玩家进入合成计划计算和最终提交任务。
- 新增奇点总线目标 adaptor helper，对直连奇点接口走 `AdaptorDualityInterface`，输入/输出总线统一调用该 helper。
- 输入、输出、存储总线的 tick 请求改为读取 AE2 `TickRates`，并用回归测试临时改写 `TickRates` 验证不再依赖默认硬编码值。

## 预防措施

- 任何“从奇点 GUI 进入 AE2 子 GUI”的流程，都必须把权限守卫延伸到 AE2 子容器或发包入口，不能只守原始奇点容器。
- 审查 AE2 兼容时要关注“精确类型特判”：原版只认识自己的 Tile/Part 时，奇点镜像设备需要显式桥接同一条内部路径。
- 对 AE2 原版已有 `TickRates`、配置表或枚举驱动的设备行为，奇点对应设备不得复制默认数字，必须读取同一配置源。

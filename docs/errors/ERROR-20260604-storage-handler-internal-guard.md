# 存储 Handler 继承入口绕过 Guard

## 错误现象

审查终端 monitor 默认方法递归问题后，发现 Drive / Storage Bus 的 AE2 handler 包装还有同类入口遗漏：设备贡献不可用或外部存储不可访问时，外层 handler 已拒绝存取与查询，但继承来的 `getInternal()` 仍会暴露底层 AE2 库存对象。

## 触发场景

- `TileSingularityDrive` 的 `DriveWatcher` 在贡献退役、频道/电力不可用或区块访问被暂停时，外部代码仍可通过 `IMEInventoryHandler#getInternal()` 拿到底层存储元件 handler。
- `TileSingularityStorageBus` 的 `GuardedStorageBusInventoryHandler` 在目标区块或相邻库存不可访问时，外部代码仍可通过 `IMEInventoryHandler#getInternal()` 拿到被包装的外部库存 handler。
- 该入口通常不由玩家 GUI 直接调用，但 AE2 内部或兼容模组拿到 `IMEInventoryHandler` 后可能绕过我们在 `injectItems` / `extractItems` / `getAvailableItems` 上加的 guard。

## 根本原因

- `DriveWatcher` 继承自 AE2 `MEInventoryHandler`（见 `src/main/java/com/github/singularityme/tile/TileSingularityDrive.java:443`），已覆盖存取、可见物品、单项查询、外部网络库存等入口，但遗漏了 `getInternal()`。
- `GuardedStorageBusInventoryHandler` 继承自 AE2 `StorageBusInventoryHandler`（见 `src/main/java/com/github/singularityme/tile/TileSingularityStorageBus.java:939`），同样遗漏了 `getInternal()`。
- AE2 `MEInventoryHandler#getInternal()` 会直接返回构造时传入的底层 `IMEInventoryHandler`；如果 guard 子类不覆盖，调用方可以绕过外层可用性判断。

## 修复方案

- 在 `DriveWatcher#getInternal()` 中加入与其它入口一致的可用性检查：不可用时返回 `null`，可用时才返回 `super.getInternal()`（见 `src/main/java/com/github/singularityme/tile/TileSingularityDrive.java:500`）。
- 在 `GuardedStorageBusInventoryHandler#getInternal()` 中加入相同 guard（见 `src/main/java/com/github/singularityme/tile/TileSingularityStorageBus.java:997`）。
- 新增 `TileSingularityInventoryGuardTest.driveWatcherDoesNotExposeInternalInventoryWhenUnavailable` 和 `TileSingularityInventoryGuardTest.storageBusHandlerDoesNotExposeInternalInventoryWhenUnavailable`，先复现 `getInternal()` 泄露，再验证修复（见 `src/test/java/com/github/singularityme/tile/TileSingularityInventoryGuardTest.java:23`、`:32`）。
- 为让 Storage Bus 类能在单测 JVM 中加载，补充测试运行时 BuildCraft 依赖；这是已有编译依赖的测试 runtime 补齐，不改变生产依赖语义（见 `dependencies.gradle:11`）。

## 预防措施

- 审查 AE2 `IMEInventoryHandler` 包装器时，不只看 `injectItems` / `extractItems` / `getAvailableItems`，还要逐项检查会返回内部句柄的 `getInternal()`、`getExternalNetworkInventory()` 和 `getAvailableItemsWithPriority()`。
- 对“权限、安全、过滤、可用性 guard”类 wrapper，至少覆盖一个测试：当 guard 条件为 false 时，所有能返回底层库存引用的入口都不能泄露原始 delegate。
- 以后新增 `MEInventoryHandler` / `StorageBusInventoryHandler` 子类时，先用 `javap -p` 或源码核对父类方法表，再决定哪些继承方法必须重新加 guard。

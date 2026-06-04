# 终端 Monitor 包装遗漏默认方法委托导致断线

## 错误现象

玩家从奇点 ME 终端中直接取物品时，客户端被踢回标题画面，并显示 `A fatal error has occured, this connection is terminated`。

## 触发场景

多人联机环境中打开已接入奇点网络的 `TileSingularityTerminal`，通过 AE2 终端 GUI 取物品或触发终端库存同步。服务端 `fml-server-latest.log` 在 AE2 channel 处理包时抛出 `StackOverflowError`。

## 根本原因

- `src/main/java/com/github/singularityme/tile/TileSingularityTerminal.java` 中的 `SecuredTerminalMonitor` 包装了 AE2 `IMEMonitor`，用于在 `injectItems` / `extractItems` 时检查 `INJECT` / `EXTRACT` 权限。
- AE2 rv3 的 `IMEInventory#getAvailableItems(out, iteration)` 默认实现会调用一参 `getAvailableItems(out)`；`IMEMonitor#getAvailableItems(out)` 又会转回父接口默认实现，包装类未显式覆盖时会形成默认方法递归。
- 终端取物前后 AE2 容器会同步库存列表，触发 `getAvailableItems`，最终在服务端包处理线程中 `StackOverflowError`，FML 终止该连接。

## 修复方案

- 在 `SecuredTerminalMonitor` 中显式委托 `getAvailableItems`、`getAvailableItem`、模糊查询、优先级列表和相关 inventory handler 默认入口到底层 `delegate`。
- 新增 `TileSingularityTerminalTest.securedMonitorDelegatesAvailableItemsWithIterationId`，先复现缺失委托导致的 `StackOverflowError`，再锁定修复后会调用底层 monitor。

## 预防措施

- 以后包装 AE2 `IMEMonitor` / `IMEInventoryHandler` 时，不能只实现抽象方法；必须检查并显式委托带默认实现的方法，尤其是 `getAvailableItems` 系列。
- 对用于权限、安全、过滤的 monitor 包装器，应至少有一个单元测试覆盖 `getAvailableItems(out, iteration)`，避免终端同步路径在游戏内才暴露递归。

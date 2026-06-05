# AE2 总线调度与矿辞状态细节未对齐

## 错误现象

奇点输出总线在 ROUNDROBIN/RANDOM 调度和 fuzzy 导出时，每 tick 尝试次数少于 AE2 原版；奇点存储总线拆装矿辞过滤卡后，可能无法恢复当前过滤文本。

## 触发场景

- 输出总线安装调度升级并切到 ROUNDROBIN 时，只要第一个可导出槽成功，本 tick 就提前停止，后续槽不会继续消耗速度升级提供的导出预算。
- 输出总线切到 RANDOM 时，每 tick 只随机尝试一个过滤槽，而 AE2 原版会在预算未耗尽时继续随机抽槽尝试。
- 输出总线安装 fuzzy 升级时，多个 fuzzy 匹配物品只尝试第一个，后续匹配物品不会参与导出。
- 存储总线使用矿辞过滤文本后，`previousOreFilterString` 保存的是旧值而不是当前值，拆下/装回矿辞卡时恢复行为偏离 AE2。

## 根本原因

奇点输出总线早期实现只按功能可用性复刻了导出路径，没有完全复刻 AE2 `PartBaseExportBus` 的循环控制语义：

- `src/main/java/com/github/singularityme/tile/TileSingularityExportBus.java:460` 现在统一通过 `runScheduledExports` 执行槽位尝试；修复前 ROUNDROBIN 在首次成功后直接返回，RANDOM 每 tick 只抽一次槽。
- `src/main/java/com/github/singularityme/tile/TileSingularityExportBus.java:581` 现在通过 `findExportCandidates` 保留全部 fuzzy 匹配；修复前只取 `findFuzzy(...).iterator().next()`。
- `src/main/java/com/github/singularityme/tile/TileSingularityStorageBus.java:816` 的 `setFilter` 现在把当前过滤文本同步写入 `oreFilterString` 与 `previousOreFilterString`；修复前 `previous` 保存的是覆盖前旧值。

## 修复方案

- 新增 AE2 风格调度 helper：DEFAULT/ROUNDROBIN/RANDOM 都按 `availableSlots` 循环，并在剩余导出预算耗尽时停止；ROUNDROBIN 按实际尝试次数推进 `nextSlot`。
- fuzzy 导出候选改为快照集合，逐个尝试所有 fuzzy 匹配项，避免只导出第一个匹配。
- 存储总线 `setFilter` 归一化 null 文本后，同时更新当前值与 previous 值。
- 新增回归测试锁定以上行为，见 `src/test/java/com/github/singularityme/tile/TileSingularityExportBusParityTest.java:40`。

## 预防措施

- 对有 AE2 原版对应物的奇点设备，审查时不能只看 GUI 和主要功能路径；升级卡、调度、fuzzy、矿辞、优先级等“边角状态机”也必须逐项对照上游。
- 遇到 AE2 内部以计数器驱动的行为时，优先抽成窄 helper 并补单元测试，避免后续优化又把“循环到预算耗尽”改回“首次成功就返回”。

# Singularity ME 架构白皮书

**版本**：1.0
**日期**：2026-05-28
**面向读者**：熟悉原版 AE2 Grid / Node / Channel / Quantum Bridge 机制的开发者或高级玩家

---

## 一、全局网络的构成

### 1. 网络归属机制

**边界定义**

原版 AE2 中，网络的边界由 ME Controller 和多面体结构（Multiblock）定义——Controller 是网络的根，所有通过线缆物理可达的设备被路径计算算法纳入同一张 Grid。网络的边界就是线缆的物理边界。

奇点网络中，网络的边界由**玩家身份**定义。一个玩家（或队伍）的所有奇点设备自动归属于同一张 Grid，无论它们身处哪个维度、相距多远。不存在"物理可达"的概念——玩家的 UUID 就是网络的边界。这个设计等价于：每位玩家生来就拥有一张隐形的、全宇宙覆盖的 ME 网络，"加入网络"不需要线缆，只需要"你是你"。

**玩家绑定与生命周期**

原版中网络的存在完全取决于物理设备——最后一个设备被拆除，Grid 自动销毁。网络和玩家身份没有任何关联。

奇点网络通过 AE2 内部的 `GridNode.getPlayerID()` 机制绑定玩家。当玩家在 AE2 安全终端中设置所有权后，AE2 框架会自动将 playerID 写入该玩家放置的所有 GridNode。`SingularityNetworkManager` 维护一个 `playerID → SingularityGrid` 的全局映射表，设备放置时根据节点的 playerID 查找或创建对应的网格。网络的生命周期与玩家登录状态无关——服务器重启后，`SingularityNetworkData`（基于 Minecraft WorldSavedData）从磁盘恢复所有已知网络的设备坐标，在区块加载前预先创建空网格骨架，设备所在的区块加载时重新填充。

**SingularityGrid 与 Grid 的结构关系**

原版的 `Grid` 是 AE2 的核心类——它持有所有 GridCache（能量缓存、存储缓存、合成缓存等），以 pivot 节点为锚点，随着节点的加入和离开动态维护缓存的一致性。

`SingularityGrid` **不是** `Grid` 的子类，它是 `Grid` 的**包装器（Wrapper）**。它实现 `IGrid` 接口，内部持有一个真实的 `Grid` 实例（称为 internalGrid），所有 `IGrid` 方法（`getCache`、`postEvent`、`getNodes` 等）全部委托给 internalGrid。这种设计的原因在于：原版 `Grid` 的构造函数立即执行 `center.setGrid(this)` 并注册到全局 TickHandler——如果继承 `Grid`，我们无法控制这个初始化时机。包装器模式让我们可以"先准备好一切，再创建 Grid"，精确控制锚点设置和节点注入的顺序。

---

### 2. 核心组件职责

**SingularityNetworkManager — 网格生命周期管理器**

原版 AE2 没有这个概念。在原版中，Grid 由 AE2 内部的 `GridStorage` 和 TickHandler 自动管理——你不需要显式告诉 AE2 "请把设备 A 放进网格 X"，线缆连接和路径计算自动完成这一切。

`SingularityNetworkManager` 是奇点网络的总调度中心，它是一个枚举单例（`enum`），负责：

- **节点注册**：`registerNode(playerID, node)` — 根据 playerID 查找或创建 SingularityGrid，调用 `grid.adoptNode(node)` 将节点强制注入该 Grid。这里有一个双层操作：先用 `computeIfAbsent` 获取或创建 Grid，再调用 `adoptNode`，而 `adoptNode` 内部先 `ensureInternalGrid()` 确保内部 Grid 存在，再通过反射调用 `GridNode.setGrid(internalGrid)` 将节点注入。
- **节点注销**：`unregisterNode(playerID, node)` — 从 Grid 的 adoptedNodes 集合中移除节点，调用 `node.destroy()` 断开与 internalGrid 的连接。如果这是最后一个节点，销毁整个 SingularityGrid。
- **世界卸载防御**：`onWorldUnload(world)` — 遍历所有 playerGrid，找出属于该 World 的节点，先调用 `retireSingularityContribution()` 让设备清理缓存状态，再注销节点。这是对 AE2 内部 `TickHandler.unloadWorld` 可能直接 destroy 节点的防御。
- **失效节点修剪**：`pruneInvalidNodes()` — 周期性扫描所有 adopted 节点，检查其 TileEntity 是否仍然有效（未被破坏、区块仍在加载），将失效节点清除。

**SingularityAnchorNode — 虚拟锚点**

原版 AE2 中，Grid 需要一个 pivot 节点作为"锚"——通常是 Controller。pivot 是 Grid 构造函数的参数，Grid 围绕它构建所有缓存。如果一个 Grid 没有任何节点，pivot 也就不存在，Grid 就会从 AE2 的内部注册表中移除。

`SingularityAnchorNode` 是一个**不绑定任何真实世界方块的虚拟节点**。它实现 `IGridBlock` 接口，充当 internalGrid 的 pivot。它的关键设计决策：

- **假坐标**：`getLocation()` 返回 `(0, -256, 0, Integer.MIN_VALUE)`——一个在正常游戏中不可能出现的维度和坐标。这个设计是为了防御 AE2 的 `WorldEvent.Unload` 清理逻辑：当某个维度卸载时，AE2 会遍历所有 GridNode，销毁属于该维度的节点。虚拟锚点的坐标永远不可能匹配任何真实维度，因此永远不会被意外清理。
- **不可世界访问**：`isWorldAccessible()` 返回 `false`，告知 AE2 的路径计算算法不要尝试通过线缆发现这个节点。
- **无能量消耗**：`getIdlePowerUsage()` 返回 `0.0`。虚拟锚点不消耗任何 AE 能量——它只是一个结构性的"挂钩"，让 internalGrid 有一个合法的 pivot 可以依附。
- **替代了 Controller 和 Quantum Bridge**：在原版中，Controller 提供 pivot + 频道容量，Quantum Bridge 提供跨维度点对点连接。虚拟锚点接管了 pivot 的职责，但它不提供频道（频道限制已被 Mixin 绕过），也不提供跨维度连接（跨维度由 Grid 的全局性本身实现）。

**SingularityGrid — 网格生命周期差异**

原版 Grid 的生命周期完全由节点的存在驱动：最后一个 `GridNode` 离开时（`Grid.remove()` 检测到节点列表为空），Grid 自动从全局存储中移除。这是 AE2 内部的自动 GC 机制。

`SingularityGrid` 不能依赖这个自动 GC。原因有二：第一，虚拟锚点作为一个永久节点存在于 Grid 中，原版 Grid 永远不会因为"无节点"而自动销毁；第二，SingularityGrid 本身是一个包装器，它持有对内部 Grid 的引用和 adoptedNodes 集合，即使内部 Grid 被意外销毁，SingularityGrid 的外部状态（playerID、adoptedNodes 快照）仍然需要显式清理。

因此，SingularityGrid 的 GC 是**手动触发**的：`unregisterNode()` 在移除节点后检查 `getAdoptedNodeCount() == 0`，如果为零，调用 `grid.destroy()` 遍历所有残留节点逐个销毁，然后丢弃 internalGrid 引用。`SingularityNetworkManager.playerGrids` 中的条目通过 `computeIfPresent` 在返回 null 时自动从 ConcurrentHashMap 中移除。

---

### 3. 跨维度机制

**全域覆盖原理**

原版 AE2 的跨维度通信依赖 Quantum Bridge——两个多方块结构分别放置在两个维度，通过量子纠缠建立一对一的点对点连接。每个 Quantum Bridge 对消耗 200 AE/t 的维护能量，且只能连接一对端点。

奇点网络的跨维度是**架构性的，而非设备性的**。它不需要任何额外设备，因为 Grid 本身就不关心节点的物理位置。关键机制如下：

- 所有奇点设备的 `GridNode` 在注册时都被反射注入到**同一个 internalGrid** 中。原版 AE2 内部的 `Grid` 类本身没有"维度"的概念——它只管理一组 `IGridNode` 对象，每个节点携带自己的 `DimensionalCoord`。Grid 的各个 GridCache（特别是 `StorageGridCache` 和 `CraftingGridCache`）在遍历节点时，只关心节点的存在与否，不关心维度。
- 因此，"跨维度"对奇点网络来说是一个伪命题——从 Grid 的视角看，所有节点都在"同一个网络"中，维度信息只是节点的一个属性字段，不影响缓存的构建和查询。下界的 Drive 节点和主世界的 Terminal 节点在 Grid 内部是平等的成员，StorageGridCache 构建物品索引时一视同仁地遍历它们。

**数据同步机制**

奇点网络的数据同步**完全依赖 AE2 原版的事件系统**，而非额外的轮询或推送层。具体而言：

- 当 Drive 中存储元件的内容变化时，原版的 `MENetworkCellArrayUpdate` 事件被投递到 Grid，触发 `StorageGridCache` 重建物品索引。
- 当 Interface 中的合成样板变化时，`MENetworkCraftingPatternChange` 事件通知 `CraftingGridCache` 刷新。
- 当 Storage Bus 的目标库存变化时，存储总线的内部 handler 检测到差异后触发 `postAlteration`，同样走原版事件通道。

奇点网络**没有**引入自己的同步协议。它完全依赖 AE2 已有的、基于事件的增量更新机制。跨维度的"同步"本质上是：事件在 Grid 内部广播，所有注册了对应事件监听器的 GridCache 收到通知后自行刷新——这个过程天然跨维度，因为所有维度的节点共享同一个 Grid 实例。

---

### 4. 能量与维护

**与原版 Controller 耗能模型的核心差异**

原版 AE2 中，ME Controller 是网络的能量入口和消耗中心。Controller 本身有基础待机功耗（约 6 AE/t），网络中的每个设备（终端、总线、驱动器等）叠加各自的 idle drain。频道数量不影响功耗，但设备数量直接决定了总消耗。能量通过 Energy Acceptor（能量接收器）或 Vibration Chamber 注入 Controller，再由 Controller 分配给网络。

奇点网络的能量模型在两个层面与原版不同：

**第一层：能量来源。** 奇点网络默认没有任何能量来源——`SingularityAnchorNode` 的 `getIdlePowerUsage()` 返回 `0.0`。这不是"免费能量"的设计，而是"默认无能量"的设计。在放置 PowerCore 之前，网络处于 0 AE 状态，所有需要能量的操作（物品导入、导出、合成）都无法执行。只有当玩家放置并激活 `TileSingularityPowerCore` 后，GT EU 被转换为 AE 注入网络，设备才开始工作。PowerCore 实现 `IAEPowerStorage`，以 `isAEPublicPowerStorage() = true` 和 `AccessRestriction.READ` 将自身注册为"只读公共储能"——电网可以从中提取能量，但不能反向注入。

**第二层：设备功耗计算。** 奇点设备的功耗计算与 AE2 原版保持一致——每个设备在 `getProxy().setIdlePowerUsage(x)` 中声明自己的基础功耗，原版 AE2 的 `EnergyGridCache` 自动累加所有节点的 `idlePowerUsage` 得到网络总需求，按 tick 从已注册的 `IAEPowerStorage` 中提取。奇点网络**不加收任何额外能量税**——没有"跨维度传输损耗"、没有"全局网络维护费"、没有"节点距离加权扣费"。能量消耗仅取决于连接的设备数量，与维度分布无关。

---

## 二、设备联网的完整生命周期

### 1. 放置设备（Register）

**原版流程**

一个 AE2 设备（以 ME Drive 为例）被放置后：
1. 方块的 `onReady()` 触发，AE2 代理（`AENetworkProxy`）扫描相邻方块，寻找线缆连接。
2. 如果找到线缆，代理创建 `GridNode`，向 AE2 的 `GridPropagator` 发起路径计算请求。
3. 路径计算算法从 GridNode 沿线缆拓扑向外搜索，寻找同一网络中的 Controller。
4. 找到 Controller 后，通过 `GridStorage` 获取或创建 Grid，将新节点加入。
5. `GridNode.setGrid(grid)` 建立双向连接，节点收到 `MENetworkChannelsChanged` 事件，确认频道分配。
6. 如果无可用频道（超过 8 通道限制），节点状态设为"离线"，设备不工作。

**奇点流程**

一个奇点设备（以 Singularity Drive 为例）被放置后：
1. `onReady()` 被调用（在 `super.onReady()` 之前，确保代理先完成设置）。
2. 设备从 AE2 代理获取其 `GridNode`。在 GTNH AE2 中，节点携带 `playerID`（由安全系统写入），这是玩家身份的来源。
3. 调用 `SingularityNetworkManager.INSTANCE.registerNode(playerID, node)`：
   - Manager 查找 `playerGrids.get(playerID)`。如果是首次放置，`computeIfAbsent` 创建新的 `SingularityGrid(playerID)`。
   - `grid.adoptNode(node)` 被调用。
   - `adoptNode` 内部首先调用 `ensureInternalGrid()`，确保 internalGrid 存在且锚点有效。
   - 然后通过 `AEReflection.setGrid(node, internalGrid)` 反射调用 `GridNode.setGrid()`，将节点强制注入 internalGrid。
4. 路径计算、线缆扫描、Controller 发现——所有这些原版流程**被完全跳过**。节点不需要寻找 Controller，因为 Grid 已经通过 Manager 直接指定。

**频道管理**

频道检查被**选择性跳过**，而非完全废除。`MixinPathGridCache` 拦截 AE2 的路径缓存计算，调用 `SingularityNetworkManager.isSingularityGrid(grid)` 判断当前 Grid 是否为奇点网格。如果是，channel 计算逻辑返回一个虚拟的"无限频道"结果，节点永远不会因为频道不足而离线。这个过程仅发生在奇点网络的 internalGrid 上——原版 AE2 网络（通过线缆连接的物理网络）的频道限制不受影响。

奇点网络用 `SingularityAnchorNode` 的 `DENSE_CAPACITY` 标志作为辅助，但这个标志本身不绕过频道限制——它只是向 AE2 声明"这个网格有能力承载密集频道"，真正的绕过逻辑在 Mixin 中。

---

### 2. 跨维度放置

**原版行为**

在原版 AE2 中，不同维度是完全隔离的。每个维度有自己独立的 `GridStorage`（实际上是同一个 JVM 实例，但 Grid 按物理连接拓扑划分）。玩家在地狱放置一个 ME Drive 后，需要：
1. 在地狱搭建独立的 ME 网络（Controller + 线缆 + Drive）。
2. 在地狱和主世界分别建造 Quantum Bridge 多方块结构。
3. 用 Quantum Link Chamber 连接两端，消耗 200 AE/t 维护能量。
4. 两个网络在逻辑上合并，物品可以通过 Quantum Bridge 跨维度传输。

**奇点行为**

奇点设备在末地、地狱、暮色森林放置后，**立即**加入与主世界设备相同的 SingularityGrid。不需要任何额外设备、任何手动链接、任何能量消耗。实现原理是：

- `SingularityNetworkManager` 是 JVM 级单例（enum），不绑定任何 World 实例。`playerGrids` 这个 ConcurrentHashMap 对所有维度的所有 World 可见。
- 当末地的一个 chunk 加载、其中的 Singularity Drive 调用 `onReady()` → `registerNode()` 时，它找到的 `SingularityGrid` 对象就是主世界第一个设备创建的那个——因为 `playerID` 相同，`computeIfAbsent` 直接返回已有实例。
- `adoptNode()` 将末地的 `GridNode` 注入同一个 `internalGrid`。这个 internalGrid 的 pivot（虚拟锚点）的 DimensionalCoord 是 `(0, -256, 0, Integer.MIN_VALUE)`，在任何 WorldServer 的正常坐标范围之外——但它有效，AE2 的 Grid 接受它。
- 不同维度节点的 `GridNode` 对象属于不同的 `WorldServer` 实例，但它们都是同一个 `Grid` 对象的成员。AE2 内部的 `GridCache` 遍历 `Grid.getNodes()` 时，对维度信息完全透明。

**Grid 跨越 WorldServer 边界**

这是奇点架构最深层的技巧：`SingularityGrid` 实例是一个普通的 Java 对象，存储在 `ConcurrentHashMap` 中，不在任何 World 的管辖范围内。它持有的 `internalGrid` 也是普通 Java 对象，其 pivot（虚拟锚点）的坐标故意选择了不可能匹配任何真实维度的值。因此：
- `WorldEvent.Unload` 尝试按维度清理节点时，虚拟锚点不受影响（坐标不匹配）。
- 但真实的设备节点（如末地的 Drive）确实属于某个 World。当末地卸载时，AE2 内部的 `TickHandler.unloadWorld` 会遍历所有 `GridNode`，销毁属于该 World 的节点。这就是 `SingularityNetworkManager.onWorldUnload()` 的存在意义——它在 AE2 强制销毁节点之前，主动调用 `retireSingularityContribution()` + `unregisterNode()`，确保节点被优雅地注销而非粗暴断开。

---

### 3. 区块卸载（Unregister）

**原版流程**

当一个 AE2 设备所在的区块被卸载：
1. Minecraft 调用 `TileEntity.onChunkUnload()`。
2. AE2 的 `AENetworkTile.onChunkUnload()` 调用 `getProxy().onChunkUnload()`。
3. 代理销毁其持有的 `GridNode`——调用 `node.destroy()`。
4. `node.destroy()` 通知 Grid："我离开了"。
5. Grid 从内部节点列表中移除该节点，更新所有受影响的 GridCache。
6. 当该区块重新加载时，`onReady()` 重新触发，节点重新创建，重新走路径计算→加入 Grid 的流程。

这个流程中，Grid 只是被动接收节点的加入和离开通知，不做任何防御性处理。

**奇点流程**

奇点设备的卸载流程比原版多了一层"贡献撤销"：

1. `onChunkUnload()` 触发。
2. 首先调用 `retireSingularityContribution()`——如果该设备实现了 `ISingularityContributionHost` 且当前 `contributionRetired == false`：
   - **Drive**：通知电网能量缓存其存储阵列即将消失，触发 `MENetworkCellArrayUpdate`。
   - **Interface**：调用 `removeStorageInterceptors()` 从 NetworkMonitor 中移除自己的存储拦截器，调用 `postCraftingPatternChange()` 通知合成缓存样板已不可用。
   - **CraftingCore**：调用 `destroySyntheticCluster()` 销毁其虚拟 CPU 集群，通知 `MENetworkCraftingCpuChange`。
   - **StorageBus**：使其外部库存 handler 失效，通知存储缓存。
3. 然后调用 `unregister()` → `SingularityNetworkManager.unregisterNode(playerID, node)`，将节点从 `adoptedNodes` 和 internalGrid 中移除。
4. 最后调用 `super.onChunkUnload()`。

**为什么需要主动调用 unregisterNode**

原版 AE2 中，`node.destroy()` 会自动从底层 Grid 中移除节点——因为节点是通过正常的路径计算加入的，Grid 内部维护了完整的反向索引。但在奇点架构中，节点是通过**反射**强制注入的——`AEReflection.setGrid(node, internalGrid)` 直接设置了 `GridNode` 的内部 `grid` 字段。虽然原版的 destroy 链也能清理这个连接，但 `SingularityGrid` 还维护了自己的 `adoptedNodes` 集合（用于计数和快照），这个集合必须在 destroy 之前同步更新。此外，`SingularityNetworkManager` 需要知道"这个节点确实已经离开"，才能决定是否销毁空 Grid——如果依赖被动 destroy，Manager 无法感知 Grid 内部的变化。

**防御 AE2 的 WorldEvent.Unload 直接销毁**

当整个维度卸载时，AE2 的 `TickHandler.unloadWorld` 可能直接调用 `node.destroy()`，**不经过** `TileEntity.onChunkUnload()`。这绕过了奇点设备的 `retireSingularityContribution()` 调用，导致贡献（存储拦截器、合成样板、CPU 集群）未被清理。

防御措施在 `SingularityNetworkManager.onWorldUnload(world)` 中：它接收 World 卸载事件，在 AE2 的 TickHandler 之前（或之后——取决于事件顺序，但至少提供了一个清理机会），遍历所有 playerGrid 中属于该 World 的 adopted 节点，先调用 `retireContribution()`（检查节点绑定的 TileEntity 是否实现了 `ISingularityContributionHost`），再调用 `unregisterNode()`。`pruneInvalidNodes()` 作为周期性的第二道防线，扫描 TileEntity 已经无效但节点仍在 adopted 集合中的情况。

---

### 4. 完全清空（Empty Grid GC）

**原版行为**

在原版 AE2 中，Grid 的 GC 是全自动的。当最后一个 `GridNode` 通过 `node.destroy()` 离开 Grid 时，`Grid.remove()` 检测到内部节点计数归零，调用自身的销毁逻辑——从 `GridStorage` 中注销、从 TickHandler 中取消注册、释放所有 GridCache。这个过程是 AE2 内部的实现细节，模组开发者无需干预。

**奇点网络为什么不能自动销毁**

原因在于虚拟锚点。`SingularityAnchorNode` 作为一个 `GridNode`，始终存在于 internalGrid 中。即使所有真实的奇点设备都已拆除，internalGrid 的 `getNodes()` 仍然至少包含这个锚点节点。从原版 Grid 的视角看，"至少还有一个节点"——GC 条件永远不会触发。

因此，空的 SingularityGrid 的检测与销毁是手动实现的：

- 在 `unregisterNode()` 中，移除节点后立即检查 `grid.getAdoptedNodeCount() == 0`。
- 如果为零，调用 `grid.destroy()`——它遍历 `adoptedNodes` 的剩余快照（此时应为空），销毁锚点节点，清空 internalGrid 引用。
- `playerGrids.compute()` 在 `unregisterNode` 返回 null 时自动将对应条目从 ConcurrentHashMap 中移除。

**玩家下线与设备全部拆除**

- **玩家下线**：SingularityGrid **保留**在服务器内存中（通过 `ConcurrentHashMap` 持有）。玩家离线并不影响网格状态——他们的设备仍然在 World 中、区块仍然可能被加载。如果所有设备所在的区块都被卸载且所有维度都无人，网格在内存中只是一个空壳（internalGrid 仍然存活但 adoptedNodes 为空？不——实际上设备节点被 unregister 了，所以 adoptedNodes 应该为空，Grid 被销毁）。更精确地说：如果玩家离线且所有设备所在区块被卸载，chunk unload 触发 unregister 流程，最后一个设备注销时网格自动销毁。
- **设备全部拆除（玩家在线）**：最后一个设备被破坏时，`invalidate()` → `retireSingularityContribution()` → `unregisterNode()` → `grid.getAdoptedNodeCount() == 0` → `grid.destroy()` → `playerGrids` 移除条目。网格完全释放。
- **服务器重启**：`SingularityNetworkData` 从磁盘恢复设备坐标。在服务器启动、第一个设备所在区块加载时，`registerNode()` 重新创建 SingularityGrid。如果玩家的所有设备都在未加载的区块中，网格在内存中不存在——直到玩家走到设备附近、区块加载、`onReady()` 触发。

---

## 总结：奇点网络对原版 AE2 网络架构的本质升格

奇点网络在以下四个维度上对原版 AE2 网络完成了架构性的升格：

**第一，从物理拓扑到身份拓扑。** 原版 AE2 的网络是一张由线缆和 Controller 定义的物理图——网络边界是线缆长度，加入网络的条件是"物理连接"。奇点网络将网络定义为玩家身份的延伸——认识网络的方式从"接线"变为"持有"，网络的拓扑从物理图变为星型图（所有设备直接连接到以玩家身份为根的虚拟中心）。

**第二，从维度隔离到全域统一。** 原版 AE2 中，维度是一道硬边界——跨维度需要专门的设备（Quantum Bridge）、消耗维护能量、建立点对点链接。奇点网络将 Grid 从 World 的管辖中抽离出来，放入一个跨 World 的全局注册表中，使得维度概念对网络透明化。设备在哪个维度不再影响网络的统一性——所有维度共享同一个 Grid 实例。

**第三，从频道约束到无限承载。** 原版 AE2 的 8 频道限制（以及后续的密集线缆 32 频道）是网络规模的硬上限，迫使玩家在基地设计中精心规划线缆拓扑。奇点网络通过 Mixin 绕过了频道计算，让设备数量不再受频道约束——网络的规模上限从"频道数量"变为"服务器性能"，从设计约束变为工程约束。

**第四，从自动生命周期到托管生命周期。** 原版 AE2 中，Grid 的生命周期完全由节点驱动——节点来则建、节点去则毁，模组开发者不需要关心。奇点网络由于引入虚拟锚点和跨 World 引用，不能依赖原版的自动 GC，必须实现自己的生命周期管理：显式的创建（首次放置）、显式的注销（最后一个设备拆除）、防御性的清理（World 卸载、失效节点修剪）。这种"托管"模式使得网络状态可追踪、可诊断、可恢复——Probe 设备的存在就是这一升格的直接体现：在原版 AE2 中，"我的网络现在有多少个节点"不是轻易能回答的问题；在奇点网络中，这个信息随时可用。

---

## 三、补充机制

### 1. 节点注入：AEReflection

**为什么需要反射注入**

AE2 的 `GridNode.setGrid()` 方法是 **package-private**（`appeng.me` 包内可见）。在正常 AE2 流程中，只有 `GridPropagator`（位于同一包内）能调用它——路径计算完成后，`GridPropagator` 将节点与 Grid 建立双向连接。奇点架构绕过路径计算，直接从 `SingularityNetworkManager` 将节点注入 Grid，这意味着必须通过反射突破访问限制。

**AEReflection 的实现**

`AEReflection` 是一个工具类，在 `static` 块中缓存三个反射句柄：

| 反射目标 | 访问的 AE2 内部 | 用途 |
|----------|----------------|------|
| `GridNode.setGrid(Grid)` | `Method` | 将奇点设备的 GridNode 注入 internalGrid |
| `CraftingCPUCluster.addTile(TileCraftingTile)` | `Method` | 将奇点 CraftingCore 注册到合成 CPU 集群 |
| `CraftingCPUCluster.accelerator` | `Field` | 设置合成加速器数量 |

如果反射失败（例如 AE2 内部重构了这些方法），`setGridMethod` 为 null，`setGrid()` 调用变为空操作——节点无法注入 Grid，设备不会工作。**这不是优雅降级而是功能阻断**：反射失败意味着整个模组无法运行。

**为什么不用 Access Transformer**

Access Transformer（AT）可以在编译时打开 package-private 方法的访问权限，生成公开访问的 stub。`singularityme_at.cfg` 已配置对这些方法的 AT。但 AT 仅在**运行时**生效——FML 加载时修改类的访问标志。在**编译期**，开发环境中的 AE2 dev jar 仍反映原始的访问限制，编译器会拒绝直接调用 `GridNode.setGrid()`。因此，反射仍然是必要的编译期变通。

**风险**

1. **AE2 重构风险**：`GridNode.setGrid()` 的签名或语义变更会直接导致反射失败。GTNH AE2 分支相对稳定，但任何对该方法的修改都需在 Singularity ME 侧同步测试。
2. **多线程风险**：`setGridMethod.invoke(node, grid)` 没有额外的同步保护。AE2 内部的 `GridNode.setGrid()` 本身有一定线程安全性（通过 Grid 内部的锁），但反射调用绕过了编译期类型检查。
3. **CraftingCPUCluster 反射**：`addTile` 和 `accelerator` 字段是比 GridNode 更深层的内部实现。CraftingCPUCluster 在 AE2 中没有公开的扩展 API，这意味着奇点 CraftingCore 的集成高度依赖 GTNH AE2 的具体版本——升级 AE2 时需要回归测试合成功能。

---

### 2. 幻影节点：PhantomSingularityNode

**问题：区块卸载导致 Grid 被意外销毁**

当设备所在区块卸载时，被破坏前的 `GridNode.destroy()` 调用会从 internalGrid 中移除节点。如果 Grid 的 `adoptedNodes` 降为零，`SingularityGrid` 会触发 destroy 流程——即使这些设备只是暂时的区块卸载而非永久拆除。玩家离开基地再回来时，奇点网格已经不存在了。

**解决方案：PhantomSingularityNode**

`PhantomSingularityNode` 是一个轻量级占位符，在真实节点被注销时插入 Grid，**阻止空 Grid 销毁**。它不包含任何运行时代码——只是一个携带坐标和设备类型的数据对象：

```java
public final class PhantomSingularityNode {
    public final String key;    // "dim:x:y:z" — 用作 Map key
    public final int playerID;  // 所属玩家
    public final int x, y, z;   // 设备坐标
    public final int dim;       // 维度 ID
    public final String deviceType; // "Drive", "Terminal", etc.
}
```

**生命周期**

1. **创建**：`SingularityNetworkManager` 在 `unregisterNode()` 中检测到注销的不是最后一个节点时，为被注销的节点创建一个 Phantom。
2. **存储**：Phantom 存入 `SingularityGrid.phantomNodes`（`Map<String, PhantomSingularityNode>`），以 `"dim:x:y:z"` 为 key。
3. **恢复**：当区块重新加载、设备重新 `onReady()` → `registerNode()` 时，`SingularityNetworkManager` 检查 `phantomNodes` 中是否有对应 key 的 Phantom。如果有，在 adopt 真实节点前先移除 Phantom。
4. **清理**：如果 Phantom 对应的设备被永久拆除（`invalidate()` 而非 `onChunkUnload()`），Phantom 在 `unregisterNode(permanent=true)` 中被移除。

**Phase 2 扩展计划**

当前 Phantom 只保留坐标——它不能存储设备的运行时状态。Phase 2 计划为 Phantom 添加存储快照功能：在设备卸载时缓存其库存内容（如 Drive 中元件的物品列表），使终端在设备离线时仍能显示"幽灵库存"（灰色图标 + "该设备所在区块未加载"提示）。

---

### 3. 权限模型：SecurityPermissions 与 NetworkMeta

**为什么需要权限模型**

奇点网络允许多人协作：一个玩家可以将自己的网络共享给其他玩家，让他们放置设备、访问库存。原版 AE2 的安全终端提供了一套基于 `IPlayerRegistry` 的权限体系，但它与物理网络绑定——权限作用于"这张 Grid"。奇点网络需要自己的权限模型，因为网络是玩家级别的抽象，而非物理级别的。

当前实现不再维护 OWNER / ADMIN / MEMBER / BLOCKED 角色枚举，而是复用 AE2 原生 `appeng.api.config.SecurityPermissions` 五个权限维度：`INJECT`、`EXTRACT`、`CRAFT`、`BUILD`、`SECURITY`。权限集合与 NBT / packet 中的 bit 表示由 `PermissionBits` 统一转换（见 `src/main/java/com/github/singularityme/core/PermissionBits.java:12`）。

**NetworkMeta — 每个网络的元数据**

`SingularityNetworkRegistry.NetworkMeta` 是存储在每个 networkID 下的元数据容器：

| 字段 | 类型 | 说明 |
|------|------|------|
| `ownerPlayerID` | `int` | 网络创建者，不可变更（当前版本） |
| `name` | `String` | 网络显示名称 |
| `color` | `int` | RGB 颜色（用于 UI 区分多网络） |
| `security` | `SecurityLevel` | PUBLIC / PRIVATE |
| `permissions` | `Map<Integer, EnumSet<SecurityPermissions>>` | 显式授权表，owner 不写入表中 |

**SecurityLevel**

| 级别 | 含义 |
|------|------|
| `PUBLIC` | 所有玩家视为拥有全部 AE2 权限 |
| `PRIVATE` | owner 内建全权限；其他玩家必须拥有至少一项显式授权才能使用 |

**权限检查流程**

权限检查分为三层，避免把"能看见"、"能使用"、"能执行某项操作"混在一起：

1. `canViewNetwork(networkID, playerID)` 决定网络列表是否显示：PUBLIC 全员可见，PRIVATE 需要能使用。
2. `canUseNetwork(networkID, playerID)` 决定普通设备 GUI 是否可打开：PUBLIC 或 owner 放行，PRIVATE 需要任意一项权限。
3. `hasPermission(networkID, playerID, permission)` 决定具体操作是否可执行：BUILD 管放置/破坏/配置，INJECT/EXTRACT 管终端和自动设备的存取，CRAFT 管合成请求，SECURITY 管授权表和网络设置。

这些入口由 `SingularityNetworkRegistry` 提供（见 `src/main/java/com/github/singularityme/core/SingularityNetworkRegistry.java:139`），方块、Tile 和容器侧通过 `SingularityPermissionHelper` 复用服务端检查（见 `src/main/java/com/github/singularityme/core/SingularityPermissionHelper.java:16`）。

旧存档迁移时，旧 `ENCRYPTED` 降级为 `PRIVATE`；旧 admin 迁移为全权限，旧 member 迁移为 BUILD / CRAFT / INJECT / EXTRACT，封禁和密码不再保留（见 `src/main/java/com/github/singularityme/core/SingularityNetworkRegistry.java:272`）。

**当前局限**

- 所有权转让未实现；owner 权限内建且不可被授权表修改。
- 权限变更不广播——其他在线玩家不会实时收到权限更新通知（需重新打开 Network Terminal）。


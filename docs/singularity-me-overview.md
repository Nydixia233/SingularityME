# Singularity ME — 模组总结文档

## 一、背景与动机

Singularity ME 是一个运行于 **Minecraft 1.7.10 / GTNH（GregTech New Horizons）** 整合包环境下的附属模组，核心出发点只有一个：

> **让玩家在不铺设任何 ME 线缆的情况下，使用完整的 AE2 物流功能。**

标准 AE2 网络要求所有设备通过 ME 线缆物理连接，形成一张有形的网络拓扑。这在大型 GTNH 存档中意味着大量的线缆布线工作，且设备必须集中部署。Singularity ME 的目标是打破这一限制——每个奇点设备放下即用，无需任何线缆，所有设备自动归属于该玩家的全局私有网络。

模组的设计原则是**加法而非修改**：不改动 AE2 的任何现有行为，只新增一套平行设备体系。

---

## 二、核心特色

### 1. 玩家全局奇点网格（SingularityGrid）

每位玩家拥有一张独立的 **SingularityGrid**，这是模组最核心的概念。

- 网格在玩家首次放置奇点设备时自动创建，此后持续存在
- 所有奇点设备放置后，在 `onReady()` 阶段通过 `SingularityNetworkManager` 自动注册到该玩家的网格
- 网格内置一个 **SingularityAnchorNode**，实现 `IAEPowerStorage` 接口，向 AE2 的能量系统虚报无限电力——网格永远处于供电状态，无需额外的能量基础设施
- 网格绕过 AE2 的 8 通道限制（通过 `MixinPathGridCache`），设备数量不受通道约束

### 2. 无线缆、即放即用

奇点设备是**完整的方块**（Block），而非 AE2 的线缆附件（Cable Part）。

- 放置即接入网格，破坏即自动注销
- 设备朝向通过方块 metadata 存储，决定其与相邻容器的交互面
- 不依赖任何 ME 线缆、ME 控制器或 ME 频道

### 3. GT EU 能量输入（奇点能量核心）

奇点网格虽然内置虚拟无限电力，但模组同时提供了一个**真实的 GT EU 能量接入点**：

- **奇点能量核心**（Singularity Power Core）接受 GregTech EU 电力输入
- 内置 40,000 AE 缓冲，每 tick 将 GT EU 转换并注入奇点网格
- 实现 `IEnergyConnected` 接口，兼容 GT 的标准电力网络
- 这使得玩家可以选择"真实供电"模式，而非依赖虚拟无限电力

---

## 三、设备清单

模组提供 7 种奇点设备，每种都是 AE2 对应设备的方块化版本：

| 奇点设备 | 对应 AE2 设备 | 主要功能 |
|---------|-------------|---------|
| 奇点存储总线 | Storage Bus | 将相邻容器的物品暴露到奇点网格 |
| 奇点终端 | ME Terminal | 访问奇点网格中的所有物品 |
| 奇点输入总线 | Import Bus | 从相邻容器抽取物品存入网格 |
| 奇点输出总线 | Export Bus | 从网格向相邻容器输出物品 |
| 奇点 ME 接口 | ME Interface | 物品缓冲与自动合成接口 |
| 奇点驱动器 | ME Drive | 安装存储元件，扩展网格容量 |
| 奇点能量核心 | Energy Acceptor | GT EU → AE 能量转换 |

---

## 四、功能对齐（与 GTNH AE2 一致）

除特色功能外，所有设备的行为与 GTNH AE2 原版保持一致：

**升级卡系统**

- 存储总线：CAPACITY（最多 5 张，过滤槽从 18 扩展至 63）、FUZZY、INVERTER
- 输入总线：CAPACITY（最多 2 张，过滤槽从 1 扩展至 9）、SPEED、FUZZY、REDSTONE
- 输出总线：CAPACITY、SPEED、FUZZY、REDSTONE、CRAFTING

**过滤与控制**

- 存储总线：读写模式（READ/WRITE/READ_WRITE）、提取模式（LOOSE/STRICT）、存储过滤器、模糊匹配、黑白名单（INVERTER 卡）、优先级
- 输入总线：物品过滤、红石控制（IGNORE/LOW/HIGH/PULSE）、模糊匹配、速度控制
- 输出总线：物品过滤、红石控制、调度模式（DEFAULT/ROUNDROBIN/RANDOM）、模糊匹配、自动合成请求（CRAFTING 卡）

**GUI 完整性**

- 所有 GUI 继承 AE2 的 `GuiUpgradeable` / `ContainerUpgradeable` 基类
- 存储总线 GUI 高度 251px，7 行 × 9 列过滤槽，含 clear/partition/priority 子界面
- 输入/输出总线 GUI 按钮可见性与 AE2 完全一致（按升级卡安装情况动态显示）

**WAILA 集成**

- 所有奇点设备支持 WAILA 悬停提示
- 显示：节点激活状态、网格是否存在、供电状态、已存储电力、物品类型数量、设备朝向

---

## 五、技术架构

### 节点注入机制

AE2 的 `GridNode.setGrid()` 是包私有方法，无公开 API 可用。模组通过 `AEReflection` 类以反射方式调用该方法，将奇点设备的 AE2 节点强制注入到 SingularityGrid 的内部网格对象中。

```
设备.onReady()
  → SingularityNetworkManager.registerNode(playerID, node)
  → SingularityGrid.adoptNode(node)
  → AEReflection.setGrid(node, internalGrid)  // 反射调用包私有方法
```

### 能量虚拟化

`SingularityAnchorNode` 同时实现 `IGridBlock`、`IGridHost` 和 `IAEPowerStorage`。AE2 的 `EnergyGridCache.addNode()` 在检测到 `machine instanceof IAEPowerStorage` 时自动将其注册为电力提供者。`extractAEPower()` 直接返回请求量，实现无限电力虚拟化。

### 通道绕过

`MixinPathGridCache` 拦截 AE2 的通道分配逻辑，对 `SingularityNetworkManager.isSingularityGrid()` 返回 true 的网格跳过 8 通道限制检查。

### 1.7.10 兼容性

1.7.10 没有 Forge Capability 系统，模组使用 `IInventory` / `ISidedInventory` 接口进行相邻容器检测（优先检查 `ISidedInventory`）。NBT 序列化通过 `@TileEvent(TileEventType.WORLD_NBT_WRITE/READ)` 注解实现（`AEBaseTile` 的 `writeToNBT`/`readFromNBT` 是 final 方法，不可覆盖）。

---

## 六、合成配方

所有奇点设备均通过 **GregTech 装配线**合成，定位于 **LuV 科技层级**（第 6 层，倒数第 3 层），与 AE2 原版设备的科技定位相当。

合成材料包含：

- LuV 级量子计算机电路（`Circuit_Masterquantumcomputer`）
- IV 级精英纳米计算机电路（`Circuit_Elitenanocomputer`）
- 对应 AE2 原版设备（存储总线、输入总线、输出总线、接口、终端、能量接受器）
- 高级 GT 材料

---

## 七、设计理念总结

| 理念 | 体现 |
|------|------|
| **加法原则** | 不修改 AE2 任何现有行为，只新增设备 |
| **透明性** | 奇点设备的行为与 AE2 原版完全一致，玩家无需重新学习 |
| **无侵入性** | 奇点网格与普通 AE2 网络完全隔离，互不干扰 |
| **玩家隔离** | 每位玩家拥有独立的私有网格，设备归属明确 |
| **科技定位** | LuV 合成门槛确保该功能是后期奖励，而非早期捷径 |
| **GTNH 原生** | 完全基于 GTNH 的 AE2 分支（rv3-beta-952-GTNH），与整合包深度兼容 |

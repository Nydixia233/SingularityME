# Singularity ME — 模组总结文档

## 一、背景与动机

Singularity ME 是一个运行于 **Minecraft 1.7.10 / GTNH（GregTech New Horizons）** 整合包环境下的附属模组，核心出发点只有一个：

> **让玩家在不铺设任何 ME 线缆的情况下，使用完整的 AE2 物流功能。**

标准 AE2 网络要求所有设备通过 ME 线缆物理连接，形成一张有形的网络拓扑。这在大型 GTNH 存档中意味着大量的线缆布线工作，且设备必须集中部署。Singularity ME 的目标是打破这一限制——每个奇点设备放下即用，无需任何线缆，设备自动归属到玩家所选网络中。每位玩家可拥有多张独立的奇点网格。

模组的设计原则是**加法而非修改**：不改动 AE2 的任何现有行为，只新增一套平行设备体系。

---

## 二、核心特色

### 1. 玩家奇点网格（SingularityGrid）

每位玩家可创建多张独立的 **SingularityGrid**，以网络 ID 区分，这是模组最核心的概念。

- 网格在服务端启动时从持久数据恢复，或在设备分配网络后创建，此后持续存在
- 所有奇点设备放置后，在 `onReady()` 阶段通过 `SingularityNetworkManager` 注册到设备所选的网络
- 网格内置一个 **SingularityAnchorNode**，实现 `IAEPowerStorage` 接口，作为网格的结构性锚点。能量实际由 **奇点能量核心**（PowerCore）提供，未放置 PowerCore 时网格处于 0 AE 状态
- 网格绕过 AE2 的 8 通道限制（通过 `MixinPathGridCache`），设备数量不受通道约束

### 2. 无线缆、即放即用

奇点设备是**完整的方块**（Block），而非 AE2 的线缆附件（Cable Part）。

- 放置即接入网格，破坏即自动注销
- 设备朝向通过方块 metadata 存储，决定其与相邻容器的交互面
- 不依赖任何 ME 线缆、ME 控制器或 ME 频道
- 普通 AE 物理网络桥接设备已从主线移除；奇点网络继续保持与原版 AE 网络隔离

### 3. GT EU 能量输入（奇点能量核心）

奇点网格默认无能量来源，需要 **奇点能量核心**（Singularity Power Core）提供 GT EU 电力接入：

- 接受 GregTech EU 电力输入，每 tick 将 GT EU 转换并注入奇点网格
- 内置 3 个能量元件槽位，总缓冲 = 元件数量 × 基础容量 × CONFIG 倍率：
  - **普通能量元件**：每个 200,000 AE（最多 64 个）
  - **致密能量元件**：每个 1,600,000 AE（最多 64 个）
  - **创造能量元件**：几近无限容量（限 1 个）
- 实现 `IEnergyConnected` 接口，兼容 GT 的标准电力网络
- 玩家可选择"真实供电"模式，按需扩展能量缓冲

---

## 三、设备清单

模组提供 11 种奇点设备（不含调试探针），每种都是 AE2 对应设备的方块化版本：

| 奇点设备 | 对应 AE2 设备 | 主要功能 |
|---------|-------------|---------|
| 奇点存储总线 | ME Storage Bus | 将相邻容器的物品暴露到奇点网格 |
| 奇点终端 | ME Terminal | 访问奇点网格中的所有物品 |
| 奇点合成终端 | ME Crafting Terminal | 终端内直接进行合成操作 |
| 奇点样板终端 | ME Pattern Terminal | 创建和管理合成样板 |
| 奇点网络终端 | — | 网络选择、成员管理、网络设置（基于 Qz UILib） |
| 奇点输入总线 | ME Import Bus | 从相邻容器抽取物品存入网格 |
| 奇点输出总线 | ME Export Bus | 从网格向相邻容器输出物品 |
| 奇点接口 | ME Interface | 物品缓冲与自动合成接口 |
| 奇点驱动器 | ME Drive | 安装存储元件，扩展网格容量 |
| 奇点合成核心 | Crafting CPU | 提供合成 CPU 算力 |
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

- GUI 根据设备类型分别继承不同基类：总线类继承 `GuiUpgradeable`，终端类继承 AE2 原生 `GuiMEMonitorable`/`GuiCraftingTerm`/`GuiPatternTerm`，驱动器/合成核心/能量核心继承 `AEBaseGui`，网络终端使用 Qz UILib 自建界面
- 存储总线 GUI 高度 251px，7 行 × 9 列过滤槽，含 clear/partition/priority 子界面
- 输入/输出总线 GUI 按钮可见性与 AE2 完全一致（按升级卡安装情况动态显示）

**WAILA 集成**

- 所有奇点设备支持 WAILA 悬停提示
- 显示：节点激活状态、网格是否存在、供电状态、已存储电力、物品类型数量、设备朝向

---

## 五、技术架构

模组的核心技术架构——包括节点反射注入、虚拟锚点、能量虚拟化、通道绕过、跨维度机制、幻影节点、权限模型等——详见 **[架构白皮书](singularity-me-architecture-whitepaper.md)**。

以下为最简摘要：

- **节点注入**：通过 `AEReflection` 反射调用 AE2 的 package-private 方法 `GridNode.setGrid()`，将设备节点强制注入奇点网格，完全跳过路径计算和线缆扫描。
- **能量虚拟化**：`SingularityAnchorNode` 充当虚拟锚点（坐标 `0,-256,0,MIN_VALUE`），`TileSingularityPowerCore` 将 GT EU 转换为 AE 能量，通过 `IAEPowerStorage` 接口注入网格。
- **通道绕过**：`MixinPathGridCache` 拦截 AE2 通道分配逻辑，使奇点网格设备不受 8 通道限制。
- **1.7.10 兼容**：无 Forge Capability 系统，使用 `IInventory` / `ISidedInventory` 进行容器检测；NBT 序列化通过 `@TileEvent` 注解实现。

---

## 六、合成配方

所有奇点设备均通过 **GregTech 装配线**合成，定位于 **LuV 科技层级**（第 6 层，倒数第 3 层），与 AE2 原版设备的科技定位相当。

> **配方以游戏内 NEI 为准**。以下仅为合成材料的大致方向，具体数量和精确配方请查询 NEI（Not Enough Items）。

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
| **玩家隔离** | 设备按网络分组，每个网络独立管理，归属明确 |
| **科技定位** | LuV 合成门槛确保该功能是后期奖励，而非早期捷径 |
| **GTNH 原生** | 当前构建基于 GTNH 的 AE2 分支（rv3-beta-944-GTNH），与整合包深度兼容 |

---

## 八、当前开发阶段

**Phase 1（已完成）**：
- 12 种奇点设备的基础功能（注册、联网、GUI、WAILA）
- 核心网络模型（SingularityGrid、AnchorNode、NetworkManager）
- 跨维度支持、能量系统、Mixin 频道绕过
- 网络终端 UI（Qz UILib 实现）

**Phase 2（规划中）**：
- 加密网络安全验证 UI
- Phantom 节点存储快照（离线设备库存可见）
- 网络所有权转让
- 多网络之间的物品路由/优先级
- AE2FC 流体兼容深度集成

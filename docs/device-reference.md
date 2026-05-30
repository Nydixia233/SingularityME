# Singularity ME — 设备参考手册

**面向**：开发者
**最后更新**：2026-05-31

本文档列出所有 12 种奇点设备（11 种生产设备 + 1 种调试探针）的详细技术参数。

---

## 设备总览

| # | 设备 | Block 类 | TileEntity 类 | AE 功耗 (idle) | 父类 |
|---|------|----------|---------------|----------------|------|
| 1 | 奇点能量核心 | `BlockSingularityPowerCore` | `TileSingularityPowerCore` | 0.0 AE/t | `AENetworkTile` |
| 2 | 奇点驱动器 | `BlockSingularityDrive` | `TileSingularityDrive` | 动态(见下文) | `AENetworkInvTile` |
| 3 | 奇点存储总线 | `BlockSingularityStorageBus` | `TileSingularityStorageBus` | 1.0 AE/t | `AENetworkInvTile` |
| 4 | 奇点输入总线 | `BlockSingularityImportBus` | `TileSingularityImportBus` | 1.0 AE/t | `AENetworkInvTile` |
| 5 | 奇点输出总线 | `BlockSingularityExportBus` | `TileSingularityExportBus` | 1.0 AE/t | `AENetworkInvTile` |
| 6 | 奇点终端 | `BlockSingularityTerminal` | `TileSingularityTerminal` | 0.5 AE/t | `AENetworkTile` |
| 7 | 奇点合成终端 | `BlockSingularityCraftingTerminal` | `TileSingularityCraftingTerminal` | 0.5 AE/t | `TileSingularityTerminal` |
| 8 | 奇点样板终端 | `BlockSingularityPatternTerminal` | `TileSingularityPatternTerminal` | 0.5 AE/t | `TileSingularityTerminal` |
| 9 | 奇点网络终端 | `BlockSingularityNetworkTerminal` | `TileSingularityNetworkTerminal` | 0.0 AE/t | `TileEntity` |
| 10 | 奇点合成核心 | `BlockSingularityCraftingCore` | `TileSingularityCraftingCore` | 动态(见下文) | `TileCraftingStorageTile` |
| 11 | 奇点 ME 接口 | `BlockSingularityInterface` | `TileSingularityInterface` | 1.0 AE/t | `AENetworkInvTile` |
| 12 | 奇点探针 *(调试)* | `BlockSingularityProbe` | `TileSingularityProbe` | 0.0 AE/t | `AENetworkTile` |

> **注**：功耗为设备待机值。网络总功耗 = 所有设备 idle 功耗之和，由 AE2 的 `EnergyGridCache` 自动计算。

---

## 1. 奇点能量核心（Power Core）

**功能**：接受 GT EU 电力，转换为 AE 能量注入奇点网格。没有 PowerCore 时，整个网格处于 0 AE 状态，所有设备无法工作。

**能量元件槽位**（3 个）：

| 槽位 | 元件类型 | 每件容量 | 最大堆叠 |
|------|---------|---------|---------|
| 0 | 普通能量元件 (Energy Cell) | 200,000 AE | 64 |
| 1 | 致密能量元件 (Dense Energy Cell) | 1,600,000 AE | 64 |
| 2 | 创造能量元件 (Creative Energy Cell) | ~9.22×10¹⁴ AE（近无限） | 1 |

- 总缓冲 = Σ(堆叠数 × 单件容量)，无额外倍率。
- 实现 `IEnergyConnected`，接受 GT 标准电力输入。
- 实现 `IAEPowerStorage`（只读公共储能），电网可从中提取能量，不可反向注入。

**升级卡**：不支持

**已知限制**：
- GT EU → AE 的转换率目前为硬编码，未做成配置项。
- 创造能量元件不检测是否为真正的创造模式物品——只要物品匹配 `energyCellCreative` 即视为无限。

---

## 2. 奇点驱动器（Drive）

**功能**：安装 AE2 存储元件，为奇点网格提供物品存储容量。最多 10 个元件槽位。

**存储元件**：支持标准 AE2 存储元件（1k/4k/16k/64k 及流体版本，取决于 compat flag）。

**功耗**：
- 无元件时：0.0 AE/t
- 有元件时：按元件数量和类型动态计算，由 AE2 的 `MEInventoryHandler` 累加。

**升级卡**：不支持（Drive 本身不直接使用升级卡，升级卡插入存储元件中）

**已知限制**：
- 元件类型检测通过 `ICellHandler.isCell()`，兼容所有标准 AE2 存储元件。
- 卸载时先 retire 贡献（通知 `MENetworkCellArrayUpdate`），再注销节点。

---

## 3. 奇点存储总线（Storage Bus）

**功能**：将相邻容器的物品暴露到奇点网格。相当于 AE2 Storage Bus 的独立方块版本。

**过滤槽**：18 个基础 + 每张 CAPACITY 升级卡 +9 个（最多 5 张 → 63 个过滤槽）。

**升级卡支持**：

| 升级卡 | 最大数量 | 效果 |
|--------|---------|------|
| CAPACITY | 5 | 过滤槽 +9 个/张（18→27→36→45→54→63） |
| FUZZY | 1 | 启用模糊匹配（忽略耐久/NBT 差异） |
| INVERTER | 1 | 过滤模式切换为黑名单 |
| STICKY | 1 | 保持过滤配置在容器变更后不丢失 |
| ORE_FILTER | 1 | 启用矿物词典匹配 |

**已知限制**：
- 容器交互依赖相邻方块实现 `IInventory` 接口。
- 不直接支持 GT 的非标准库存（如 GT 机器内部 buffer），需 GT 侧提供适配。

---

## 4. 奇点输入总线（Import Bus）

**功能**：从相邻容器抽取物品存入奇点网格。

**过滤槽**：1 个基础 + 每张 CAPACITY 升级卡 +4 个（最多扩展到 `filterInv.getSizeInventory()`）。

**升级卡支持**：

| 升级卡 | 最大数量 | 效果 |
|--------|---------|------|
| CAPACITY | 2 | 过滤槽 +4 个/张 |
| SPEED | 1 | 每 tick 抽取 1 个物品 |
| SUPERSPEED | 1 | 每 tick 抽取 8 个物品（叠加 SPEED 后生效） |
| SUPERLUMINALSPEED | 1 | 每 tick 抽取 64 个物品（叠加前两级后生效） |
| FUZZY | 1 | 启用模糊匹配 |
| REDSTONE | 1 | 接受红石信号控制 |
| ORE_FILTER | 1 | 启用矿物词典匹配 |

**已知限制**：
- 抽取速度层级：基础 1 → SPEED 2 → SUPERSPEED 8 → SUPERLUMINALSPEED 64（每 tick）。

---

## 5. 奇点输出总线（Export Bus）

**功能**：从奇点网格向相邻容器输出物品。行为与 AE2 Export Bus 一致。

**升级卡支持**：

| 升级卡 | 最大数量 | 效果 |
|--------|---------|------|
| CAPACITY | 1 | 扩展过滤/调度槽 |
| SPEED | 1 | 每 tick 输出 1 个物品 |
| SUPERSPEED | 1 | 每 tick 输出 8 个物品 |
| SUPERLUMINALSPEED | 1 | 每 tick 输出 64 个物品 |
| FUZZY | 1 | 启用模糊匹配 |
| REDSTONE | 1 | 接受红石信号控制 |
| CRAFTING | 1 | 允许触发自动合成以补充输出物品 |
| ORE_FILTER | 1 | 启用矿物词典匹配 |

**已知限制**：
- 与 Import Bus 共用相同的 speed 层级逻辑。

---

## 6. 奇点终端（Terminal）

**功能**：访问奇点网格中的所有物品，等同于 AE2 ME Terminal。

**升级卡**：不支持（终端类设备不含升级卡槽）

**已知限制**：
- GUI 继承 `GuiUpgradeable`，使用 AE2 原生终端界面。
- 终端响应速度取决于网络规模和服务器 tick rate。

---

## 7. 奇点合成终端（Crafting Terminal）

**功能**：与终端相同，但界面内置 3×3 合成格，可直接在终端中进行合成操作。

**父类**：`TileSingularityTerminal`，额外实现 `ICraftingTerminal`。

**升级卡**：不支持

**已知限制**：
- 合成格的物品实际上由玩家背包和网格库存共同提供。

---

## 8. 奇点样板终端（Pattern Terminal）

**功能**：创建和管理合成样板（Pattern）。将配方编码为样板后，可放入 Interface 供 CraftingCore 自动合成。

**父类**：`TileSingularityTerminal`。

**升级卡**：不支持

**已知限制**：
- 样板通过 AE2 标准 `ICraftingPatternProvider` 机制存储，无需特殊处理。

---

## 9. 奇点网络终端（Network Terminal）

**功能**：管理奇点网络的成员、安全级别和默认网络选择。**这是唯一不继承 AE2 TileEntity 体系而直接继承 `TileEntity` 的设备**——它的 UI 基于 Qz UILib 纯自定义界面。

**UI 实现**：`QzNetworkTerminalScreens` / `QzNetworkTabScreens`，通过 `UiDocumentScreens.createDocumentScreen()` 渲染。

**升级卡**：不支持

**已知限制**：
- 不消耗 AE 能量（无 `GridNode`），仅依赖方块实体本身。
- Phase 2 计划：加密网络密码验证 UI、成员在线状态显示。

---

## 10. 奇点合成核心（Crafting Core）

**功能**：为奇点网格提供合成 CPU 算力。等同于 AE2 Crafting CPU 多方块结构的单方块版本。

**功耗**：
- 无活跃合成任务时：0.0 AE/t
- 有任务时：按正在使用的合成处理单元数量动态计算。

**升级卡**：不支持

**已知限制**：
- 通过反射（`AEReflection.addCraftingTile` / `setCraftingAccelerators`）注入到 AE2 的 `CraftingCPUCluster` 中，依赖 AT 打开相关字段的访问权限。
- 虚拟 CPU 集群在设备卸载时主动销毁（`destroySyntheticCluster()`），防御原版 GC 无法感知自定义 CPU 的问题。

---

## 11. 奇点 ME 接口（Interface）

**功能**：物品缓冲与自动合成请求入口。等同于 AE2 ME Interface。

**核心实现**：内部委托给 AE2 的 `DualityInterface`，通过 `IPrimaryGuiIconProvider` 提供图标一致性。

**升级卡**：不支持

**已知限制**：
- 卸载时需调用 `removeStorageInterceptors()` 清理存储拦截器，调用 `postCraftingPatternChange()` 通知合成缓存样板不可用。

---

## 12. 奇点探针（Probe）*[调试]*

**功能**：调试工具，右键打开后显示当前奇点网格的状态信息（节点数、能量、网络 ID 等）。无合成配方，仅创造模式可用。

**升级卡**：不支持

**已知限制**：
- 探针信息读取自 `SingularityNetworkManager`，仅服务端有效。
- 无 WAILA 集成（与其他设备不同，探针的 WAILA 信息通过 `WailaSingularityProbeProvider` 单独提供）。

---

## 升级卡速查表

| 升级卡 | 支持设备 |
|--------|---------|
| CAPACITY | StorageBus(×5), ImportBus(×2), ExportBus(×1) |
| SPEED | ImportBus, ExportBus |
| SUPERSPEED | ImportBus, ExportBus |
| SUPERLUMINALSPEED | ImportBus, ExportBus |
| FUZZY | StorageBus, ImportBus, ExportBus |
| REDSTONE | ImportBus, ExportBus |
| INVERTER | StorageBus |
| STICKY | StorageBus |
| CRAFTING | ExportBus |
| ORE_FILTER | StorageBus, ImportBus, ExportBus |

> 以上升级卡行为与 GTNH AE2 原版一致。未列出的设备（终端类、PowerCore、Drive、Interface、CraftingCore、Probe）不支持升级卡。

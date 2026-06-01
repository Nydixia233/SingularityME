# Singularity ME — 开发者指南

**目标读者**：想要贡献代码或深入理解项目的开发者
**前置阅读**：[模组概览](singularity-me-overview.md) → [架构白皮书](singularity-me-architecture-whitepaper.md)

---

## 一、项目结构

```
src/
├── main/
│   └── java/com/github/singularityme/
│       ├── SingularityME.java          ← @Mod 主类，preInit/init/postInit
│       ├── block/                      ← 12 个 Block 类
│       ├── tile/                       ← 12 个 TileEntity + 核心接口
│       │   ├── ISingularityNetworkDevice.java       ← 设备必须实现
│       │   └── ISingularityContributionHost.java    ← 有缓存贡献的设备实现
│       ├── core/                       ← 核心逻辑（单例）
│       │   ├── SingularityNetworkManager.java       ← 网格生命周期总调度
│       │   ├── SingularityNetworkRegistry.java      ← 网络元数据与权限管理
│       │   ├── SingularityNetworkData.java          ← WorldSavedData 持久化
│       │   ├── SingularityNetworkDefaults.java      ← 默认网络分配
│       │   ├── AEReflection.java                    ← AE2 内部反射工具
│       │   ├── AccessLevel.java                     ← 权限枚举
│       │   └── SecurityLevel.java                   ← 网络安全级别枚举
│       ├── grid/                       ← 网格模型
│       │   ├── SingularityGrid.java                 ← Grid 包装器
│       │   ├── SingularityAnchorNode.java           ← 虚拟锚点
│       │   └── PhantomSingularityNode.java          ← 卸载设备占位符
│       ├── energy/                     ← 能量相关
│       ├── gui/                        ← AE2 原生 GUI（GuiUpgradeable 子类）
│       ├── client/                     ← 客户端
│       │   ├── render/                              ← 方块/物品渲染
│       │   └── ui/                                  ← ModularUI2 网络界面
│       ├── network/                    ← 网络包
│       │   ├── SingularityChannel.java              ← 包通道注册
│       │   └── packet/                              ← 具体包定义
│       ├── init/                       ← 注册与初始化
│       │   ├── EventHandler.java                    ← Forge 事件监听
│       │   ├── RecipeHandler.java                   ← GT Assembly Line 配方
│       │   ├── SingularityPrototypeRegistry.java    ← AE2 物品原型注册
│       │   ├── SingularityUpgradeMirror.java        ← 升级卡镜像映射
│       │   ├── WailaRegistrar.java                  ← WAILA 注册
│       │   └── WailaSingularityProbeProvider.java   ← 探针 WAILA 提供者
│       ├── proxy/                     ← 客户端/服务端代理
│       ├── capability/                ← Forge Capability 注册
│       └── mixin/                     ← Mixin（子源集 src/mixin/）
│           └── mixins/late/ae2/
│               └── MixinPathGridCache.java          ← 绕过频道限制
└── resources/
    ├── assets/singularityme/
    │   ├── lang/                     ← 语言文件
    │   └── textures/                 ← 贴图
    └── mcmod.info                    ← Forge 模组元数据
```

---

## 二、关键接口与扩展点

### ISingularityNetworkDevice

```java
public interface ISingularityNetworkDevice {
    int getNetworkID();       // 0 = 未分配
    void setNetworkID(int id);
    int getGridOwnerPlayerID(); // -1 = 未知
}
```

**所有奇点设备必须实现**。`SingularityNetworkManager.registerNode()` 依赖此接口获取 `networkID`。不实现此接口的设备无法加入奇点网格。

### ISingularityContributionHost

```java
public interface ISingularityContributionHost {
    void retireSingularityContribution(); // 卸载前清理缓存
    boolean isContributionLoaded();       // 是否已加载贡献
}
```

**只有"持有缓存状态"的设备才需要实现**——Drive（存储拦截器）、Interface（合成样板）、CraftingCore（虚拟 CPU）、StorageBus（外部库存）。如果设备不需要在卸载时做任何清理，可以不实现此接口。

### 其他重要扩展点

| 接口 | 位置 | 用途 |
|------|------|------|
| `IEnergyConnected` | GT API | PowerCore 接收 GT EU |
| `IAEPowerStorage` | AE2 API | PowerCore 作为电网能量源 |
| `IChestOrDrive` | AE2 API | Drive 的元件状态机 |
| `ICraftingTerminal` | AE2 API | CraftingTerminal 的合成格 |
| `IGridTickable` | AE2 API | 需要 per-tick 逻辑的设备（PowerCore、Drive、Interface、CraftingCore） |

---

## 三、添加新设备的完整步骤

以添加 "Singularity Level Emitter"（奇点电平发射器）为例：

### Step 1：创建 TileEntity

```java
// src/.../tile/TileSingularityLevelEmitter.java
public class TileSingularityLevelEmitter extends AENetworkTile
    implements ISingularityNetworkDevice, ISingularityContributionHost {

    private int networkID = 0;
    private int gridOwnerPlayerID = -1;

    @Override
    public void onReady() {
        super.onReady();
        if (worldObj.isRemote) return;
        GridNode node = (GridNode) getProxy().getNode();
        if (node != null && node.getPlayerID() >= 0) {
            gridOwnerPlayerID = SingularityNetworkManager.INSTANCE
                .registerNode(node.getPlayerID(), networkID, node);
        }
    }

    @Override
    public void onChunkUnload() {
        retireSingularityContribution();
        unregisterNode();
        super.onChunkUnload();
    }

    @Override
    public void invalidate() {
        retireSingularityContribution();
        unregisterNode(true);
        super.invalidate();
    }

    // ... ISingularityNetworkDevice / ISingularityContributionHost 方法 ...
}
```

### Step 2：创建 Block 类

```java
// src/.../block/BlockSingularityLevelEmitter.java
public class BlockSingularityLevelEmitter extends BaseSingularityBlock {
    // 参考现有 Block 类，继承 BaseSingularityBlock
    // 设置面向属性（用于决定与相邻容器交互面）
}
```

### Step 3：注册方块与 TileEntity

在 `CommonProxy.java` 中：
```java
BlockSingularityLevelEmitter levelEmitter = new BlockSingularityLevelEmitter();
// 注册 Block + ItemBlock
GameRegistry.registerBlock(levelEmitter, ItemBlockSingularity.class, "SingularityLevelEmitter");
GameRegistry.registerTileEntity(TileSingularityLevelEmitter.class, "SingularityLevelEmitter");
```

### Step 4：创建 GUI（如需要）

- **AE2 风格 GUI**：在 `gui/` 下创建 `GuiSingularityLevelEmitter`（继承 `GuiUpgradeable`）和 `ContainerSingularityLevelEmitter`（继承 `ContainerUpgradeable`）。
- **ModularUI2 风格**：在 `client/ui/` 下创建 UI 定义，参考 `NetworkTerminalUI.java`，详见 [`modularui2/README.md`](modularui2/README.md)。
- 在 `SingularityGuiHandler.java` 中注册 GUI ID。

### Step 5：添加语言文件

`resources/assets/singularityme/lang/zh_CN.lang`：
```
tile.singularityme.SingularityLevelEmitter.name=奇点电平发射器
```

`en_US.lang`：
```
tile.singularityme.SingularityLevelEmitter.name=Singularity Level Emitter
```

### Step 6：添加配方（如需要）

在 `RecipeHandler.java` 中使用 GT Assembly Line API 注册合成配方。如果不提供合成配方，设备仅创造模式可用（如 Probe）。

---

## 四、调试技巧

### 使用 SingularityProbe

Probe 是最直接的调试工具。放置后右键，显示：
- 当前玩家所有网络列表
- 每个网络的节点数、能量状态
- `adoptedNodes` 快照

### 日志过滤

所有 Singularity ME 日志使用 logger 名称 `SingularityME`。在开发环境过滤：

```
[SingularityME]
```

### 关键日志点

| 日志内容 | 含义 |
|----------|------|
| `Loaded SingularityNetworkRegistry: X network(s)` | 服务端启动时从磁盘恢复的网络数 |
| `registerNode:` | 设备注册到网络 |
| `unregisterNode:` | 设备注销 |
| `pruneInvalidNodes:` | 周期性清理失效节点 |
| `Failed to reflect` | 反射失败——模组无法正常工作 |

### 常见调试场景

1. **设备不工作** → 检查 Probe 确认网络是否存在、是否有 PowerCore 供电
2. **跨维度失效** → 检查两个维度的设备是否在同一 `playerID` 下
3. **物品不显示在终端** → 检查 Drive 是否有存储元件、StorageBus 是否正确匹配容器
4. **合成不执行** → 检查 CraftingCore 是否在线、Interface 是否有样板

---

## 五、代码规范

遵循项目 `AGENTS.md` 中的规范，关键点：

- **类名**：大驼峰，英文
- **方法/变量**：小驼峰，英文
- **常量**：全大写，下划线分隔
- **注释**：类与方法必须中文注释；复杂逻辑加行内注释
- **缩进**：4 空格
- **大括号**：左不换行，右独占一行

---

## 六、禁止事项

### ❌ 不要直接操作 `internalGrid`

`SingularityGrid` 的 `internalGrid` 是包装器内部的真实 AE2 Grid 实例。**任何对 internalGrid 的直接访问必须在 SingularityGrid 类内部进行**，外部代码必须通过 `SingularityGrid` 的公共方法操作。直接操作 internalGrid 可能导致：
- 绕过贡献追踪（storage interceptor 泄漏）
- 破坏 adoptedNodes 与 internalGrid 节点列表的一致性
- 使 Phantom 占位机制失效

### ❌ 不要绕过 `SingularityNetworkManager`

设备注册、注销、网络迁移**必须**通过 `SingularityNetworkManager.INSTANCE`。不要：
- 直接调用 `AEReflection.setGrid()` 注入节点（Manager 维护了 adoptedNodes 和 Phantom）
- 直接调用 `node.destroy()` 而不同步更新 Manager（会导致 Grid 残留）

### ❌ 不要硬编码 NBT 键值

使用有意义的常量名定义 NBT 键。参考现有代码中的 `SingularityNetworkDefaults.NBT_KEY` 等常量。

### ❌ 不要在客户端线程访问 `SingularityNetworkManager`

Manager 是服务端单例。在客户端代码中访问它会导致：
- `playerGrids` 为空（客户端没有网格数据）
- NPE 或异常状态

---

## 七、构建与测试

```powershell
# 编译（跳过格式检查）
$env:GRADLE_USER_HOME = "$env:USERPROFILE\.gradle"
.\gradlew.bat compileJava compileMixinJava -x spotlessJavaCheck

# 完整构建
.\gradlew.bat build -x spotlessJavaCheck

# 部署到测试实例
.\deploy-mod.bat -Once

# 带兼容模式运行
.\gradlew.bat runClient '-Psingularityme.compat.ae2fc=true'
```

---

## 八、能量系统实现

> 能量模型的概念说明见 [架构白皮书 §一.4](singularity-me-architecture-whitepaper.md)。以下为 tick 级实现细节。

### 总体流程

```
GT EnergyNet (EU/t)
    ↓ IEnergyConnected.injectEnergyUnits()
TileSingularityPowerCore.buffer (double, EU)
    ↓ getTickingRequest() → tickingRequest()
GT EU → AE 转换 (硬编码比率)
    ↓ IAEPowerStorage.injectAEPower()
SingularityGrid.extractVirtualAEPower()
    ↓ EnergyGridCache 自动分配
各设备 idlePowerUsage 消耗
```

### EU 输入（IEnergyConnected）

`TileSingularityPowerCore` 实现 `IEnergyConnected`，这是 GregTech 的标准能量接收接口。GT 的 EnergyNet 每 tick 调用 `injectEnergyUnits()`，将最多 1 Amp 的 EU 注入 `buffer` 字段（`double` 类型）。

### Tick 处理（IGridTickable）

PowerCore 注册 `TickingRequest` 以最快频率执行：

```java
public TickingRequest getTickingRequest(IGridNode node) {
    return new TickingRequest(1, 20, false);
}
```

每 tick 的 `tickingRequest()` 中：
1. **检查贡献可用性**：如果 chunk 不可达（如区块未加载）→ 跳过，`buffer` 保持。
2. **EU → AE 转换**：以固定的硬编码比率将 `buffer` 中的 EU 转换为 AE 能量，通过 `IAEPowerStorage.injectAEPower()` 注入 internalGrid。
3. **钳位 buffer**：`clampBufferToCapacity()` 确保 buffer ≤ 理论最大容量。

### AE 能量分配（EnergyGridCache）

PowerCore 作为 `IAEPowerStorage`（`isAEPublicPowerStorage() = true`，`AccessRestriction.READ`）在 Grid 中注册。AE2 的 `EnergyGridCache` 每 tick：
1. 累加所有节点的 `idlePowerUsage`。
2. 从已注册的 `IAEPowerStorage` 中按需提取。
3. PowerCore 的 `extractAEPower()` 被调用，从其内部缓冲中扣除。

### 虚拟能量存储

`SingularityGrid.extractVirtualAEPower()` 作为代理，将能量请求转发给 `effectivePowerCore`（当前有效的 PowerCore 实例）。如果 PowerCore 不在线或 `contributionRetired == true`，返回 `0.0`——网格无能量。

### 能量元件的角色

PowerCore 的静态容量由 3 个能量元件槽位决定：
- 未放置任何元件 → 最大缓冲 = 0 AE → 电网无法从中提取能量。
- 放置 1 个普通能量元件 → 最大缓冲 = 200,000 AE。
- 创造能量元件 → 缓冲永久保持在 `Long.MAX_VALUE / 10000` ≈ 9.22×10¹⁴ AE，等同于无限。

### 多 PowerCore 叠加

PowerCore 本身**不消耗** AE 能量（`setIdlePowerUsage(0.0)`）——它只是一个能量**转换器 + 缓冲器**。如果有多个 PowerCore 在同一网格中（同一玩家的多台 PowerCore），每个都独立接收 EU、独立注入 AE——网格的总能量输入是多 PowerCore 累加。

### 已知问题

- EU → AE 转换比率硬编码，不可配置。调整需修改源码。
- `buffer` 字段通过 NBT 持久化。如果 PowerCore 在卸载时被 GT EnergyNet 断开，重新加载后需等待 GT 重新检测连接。

---

## 九、相关资源

- [GTNH 开发指南](https://github.com/GTNewHorizons/ExampleMod1.7.10)（官方 MDK 模板）
- [AE2 非官方 GTNH 版 API](https://github.com/GTNewHorizons/Applied-Energistics-2)
- [ModularUI2 仓库](https://github.com/GTNewHorizons/ModularUI2)（GTNH fork，界面库源码）
- [Mixin 官方文档](https://github.com/SpongePowered/Mixin/wiki)

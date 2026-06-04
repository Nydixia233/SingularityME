# AI 记忆文档

本文件只保留跨任务长期稳定的高层导航和边界信息。遇到具体任务时，Agent 必须主动读取对应文档和源码确认现状。

## 文档体系导航

| 需要什么 | 去哪里找 |
|----------|----------|
| 文档全局入口 | `docs/README.md` |
| 模组总览 | `docs/singularity-me-overview.md` |
| 架构设计 | `docs/singularity-me-architecture-whitepaper.md` |
| 兼容配置 | `docs/compat-profile.md` |
| MUI2 集成方式 | `docs/modularui2/README.md` + 本文件"ModularUI2 集成"章节 |
| 错误记录索引 | `docs/errors/README.md` |

## 项目定位

- Minecraft 1.7.10 / GTNH / LWJGL3ify 环境下的 AE2 附属模组。
- 根包：`com.github.singularityme`，Mod ID：`singularityme`。
- 核心目标：让玩家在不铺设 ME 线缆的情况下使用完整 AE2 物流功能。
- 设计原则：加法而非修改——不改动 AE2 现有行为，只新增平行设备体系。
- 核心概念：玩家可创建多张奇点网格（SingularityGrid），以网络 ID 区分；设备即放即用、破坏即注销。
- 网格在服务端启动时从 WorldSavedData 预恢复，或设备分配网络后创建。
- UI 层基于 GTNH ModularUI2 2.3.63，同时包含 AE2 原生 GUI（`GuiUpgradeable`、`GuiMEMonitorable` 等）。
- 网络 UI（Network Tab + Network Terminal）使用 ModularUI2 `ModularScreen` + `GuiScreenWrapper`，纯客户端面板，不走 MUI2 sync handler。

## 对外入口边界

- `SingularityME.java` — `@Mod` 主类，preInit/init/postInit 生命周期入口
- 方块注册在 `block/`（11 种设备 + 1 调试探针），TileEntity 在 `tile/`
- GUI 分两类：AE2 原版 GUI（`gui/`，继承 `GuiUpgradeable`）和 MUI2 网络 UI（`client/ui/`，`NetworkTabUI` + 8 标签 `NetworkTerminalUI`）
- 网络 UI 通过 `ModularScreen` + `GuiScreenWrapper` 创建，保留 `static GuiScreen create(TileEntity)` 和 `static boolean receiveNetworkData(PacketNetworkTabData)` 静态接口；网络终端状态页另有 `receiveNetworkStatus(PacketNetworkStatus)` 静态入口
- 核心逻辑在 `core/`：SingularityNetworkManager、SingularityGrid、SecurityLevel，以及基于 AE2 `SecurityPermissions` 的权限表（见 `src/main/java/com/github/singularityme/core/SingularityNetworkRegistry.java:20`）。
- 能量模型：`SingularityAnchorNode` 作为结构性锚点，实际能量由 `TileSingularityPowerCore` 通过元件叠加提供（普通 200k AE/个、致密 1.6M AE/个、创造无限）

## 主动读取原则

- 涉及 AE2 兼容 → 先读 `docs/compat-profile.md`
- 涉及 NEI/AE2 终端 GUI 兼容 → 注意 NEI overlay/bookmark 按精确 `GuiContainer` 类注册；奇点 GUI 继承 AE2 原 GUI 后仍需显式注册子类，当前入口见 `src/main/java/com/github/singularityme/client/integration/SingularityNeiCompat.java:49`
- 涉及 UI 布局 → 参考 `client/ui/` 下 Java 文件 + `docs/html-reference/` 下 HTML 预览 + `docs/modularui2/README.md`
- 涉及网络协议 → 先读 `network/packet/` 下的包定义
- 需要具体类/入口/目录位置 → 用 Glob/Grep/Read 现查，不在本文件维护索引

## 运行与验证

- Windows 环境，PowerShell。
- `GRADLE_USER_HOME` 设为 `$env:USERPROFILE\.gradle`。
- 常用命令：
  - 编译：`$env:GRADLE_USER_HOME="$env:USERPROFILE\.gradle"; ./gradlew.bat compileJava compileMixinJava -x spotlessJavaCheck`
  - 构建：`$env:GRADLE_USER_HOME="$env:USERPROFILE\.gradle"; ./gradlew.bat build -x spotlessJavaCheck`
  - Spotless：`$env:GRADLE_USER_HOME="$env:USERPROFILE\.gradle"; ./gradlew.bat spotlessJavaCheck`
  - 本地联机一键启动：`./start-test-env.bat`（默认先构建、部署，再启动 GTNH 测试服务器与 `GTNH290test` Prism 实例；目标路径由脚本参数和 `scripts/` 默认配置管理）
  - 部署：`./deploy-mod.bat -Once`（自动部署到脚本当前配置的多人联机测试目标）
- ModularUI2 依赖通过 GTNH Maven 仓库解析，`dependencies.gradle` 中声明 `com.github.GTNewHorizons:ModularUI2:2.3.63-1.7.10:dev`
- 当前默认部署目标由 `scripts/deploy-built-mod.ps1` 管理，用于两个 PrismLauncher 客户端实例和一个 GTNH 测试服务端；文档不记录本地绝对路径。

## ModularUI2 集成

- 包名：`com.cleanroommc.modularui`（GTNH fork）
- 网络 UI 走 **client-only screen** 路线：`ModularScreen` + `GuiScreenWrapper`（非 `GuiContainer`），不使用 MUI2 sync handler
- 创建屏幕配方：  
  ```java
  ModularScreen screen = new ModularScreen("singularityme",
      (ModularGuiContext ctx) -> buildPanel(te));  // 返回 ModularPanel
  screen.getContext().setSettings(new UISettings());
  return new GuiScreenWrapper(screen);
  ```
- `GuiScreenWrapper` 构造时 `screen.construct(this)` 跳过 `ModularContainer` 初始化，纯客户端面板无需 sync
- 数据刷新沿用 packet → client 线程 `func_152344_a` → 静态接收入口 → 重建 widget 子树。网络列表走 `PacketNetworkTabData`，网络终端 HOME/CONNECTION/STATISTICS/HEALTH 状态页走 `PacketNetworkStatus`，请求以选中的 `networkID` 为键，不以终端方块坐标为键。

### 已知的 MUI2 API 陷阱

| 问题 | 说明 | 状态 |
|------|------|------|
| `IPositioned` sizing 链式类型退化 | 源码签名返回泛型 `W`，但在部分 Widget、raw type 或 Java 静态类型推断下会退化为 `IPositioned`，导致 `.width()` / `.expanded()` 后不能继续 `.background()` / `.color()` 等 Widget 专属方法 | 已规避：分步调用或先设 background 再 sizing |
| MUI2 两参数 `padding` / `margin` 顺序是水平、垂直 | 左右留白应写 `.padding(horizontal, 0)`；`.padding(0, horizontal)` 实际是上下留白 | 已记录：见 `docs/errors/ERROR-20260603-mui2-padding-argument-order.md` |
| `Rectangle` 不支持圆角+边框并存 | 原 Qz UI 大量圆角+边框效果丢失 | 接受直角边框 |
| SIZING TextWidget padding overflow | 见下方已知问题 | 待修复 |

### 已知的 MUI2 SIZING 问题

| 问题 | 根因 | 状态 |
|------|------|------|
| TextWidget `margin/padding set on both sides on axis Y exceeds parent size` | row 固定高度（24px）内 TextWidget 的 padding 与主题默认 padding 叠加导致垂直溢出 | 日志报错但未阻断渲染，待排查修复 |

## 设计决策

- **NBT 键值**：不为了"看起来更像上游"而重命名已有键。如果必须迁移，先加旧键读取兼容，再在单独提交中切换写入。优先采用上游委托模式（如 `DualityInterface.writeToNBT`）。

## 本文件的维护规则

- 只记录长期稳定边界和导航指针，不记录阶段流水账、类清单、修复日志
- 有明确归属的信息写回原文档，不在此重复
- 更新时优先做删减和归并，避免按日期追加
- 临时调试记录和一次性试验结论不沉淀到本文件

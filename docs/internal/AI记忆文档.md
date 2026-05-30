# AI 记忆文档

本文件只保留跨任务长期稳定的高层导航和边界信息。遇到具体任务时，Agent 必须主动读取对应文档和源码确认现状。

## 文档体系导航

| 需要什么 | 去哪里找 |
|----------|----------|
| 文档全局入口 | `docs/README.md` |
| 模组总览 | `docs/singularity-me-overview.md` |
| 架构设计 | `docs/singularity-me-architecture-whitepaper.md` |
| 兼容配置 | `docs/compat-profile.md` |
| 审查报告 | `docs/SingularityME-审查报告-20260528-三层深度审计.md` |
| 错误记录索引 | `docs/errors/README.md` |
| 三种 AI 角色定义 | `prompts/architect.md` |

## 项目定位

- Minecraft 1.7.10 / GTNH / LWJGL3ify 环境下的 AE2 附属模组。
- 根包：`com.github.singularityme`，Mod ID：`singularityme`。
- 核心目标：让玩家在不铺设 ME 线缆的情况下使用完整 AE2 物流功能。
- 设计原则：加法而非修改——不改动 AE2 现有行为，只新增平行设备体系。
- 核心概念：玩家可创建多张奇点网格（SingularityGrid），以网络 ID 区分；设备即放即用、破坏即注销。
- 网格在服务端启动时从 WorldSavedData 预恢复，或设备分配网络后创建。
- UI 层基于 Qz UILib 4.1.3-LTS，同时包含 AE2 原生 GUI（`GuiUpgradeable`、`GuiMEMonitorable` 等）。

## 对外入口边界

- `SingularityME.java` — `@Mod` 主类，preInit/init/postInit 生命周期入口
- 方块注册在 `block/`（11 种设备 + 1 调试探针），TileEntity 在 `tile/`
- GUI 分两类：AE2 原版 GUI（`gui/`，继承 `GuiUpgradeable`）和 Qz UILib 网页式 UI（`client/ui/`）
- UI 通过 `UiDocumentScreens.createDocumentScreen()` 创建（网络终端、网络标签页）
- 核心逻辑在 `core/`：SingularityNetworkManager、SingularityGrid、SecurityLevel、AccessLevel
- 能量模型：`SingularityAnchorNode` 作为结构性锚点，实际能量由 `TileSingularityPowerCore` 通过元件叠加提供（普通 200k AE/个、致密 1.6M AE/个、创造无限）

## 主动读取原则

- 涉及 AE2 兼容 → 先读 `docs/compat-profile.md`
- 涉及 UI 布局 → 参考 `client/ui/` 下 Java 文件 + `docs/html-reference/` 下 HTML 预览
- 涉及网络协议 → 先读 `network/packet/` 下的包定义
- 涉及审查结论 → 先读审查报告
- 需要具体类/入口/目录位置 → 用 Glob/Grep/Read 现查，不在本文件维护索引

## 运行与验证

- Windows 环境，PowerShell。
- `GRADLE_USER_HOME` 设为 `$env:USERPROFILE\.gradle`。
- 常用命令：
  - 编译：`$env:GRADLE_USER_HOME="$env:USERPROFILE\.gradle"; ./gradlew.bat compileJava compileMixinJava -x spotlessJavaCheck`
  - 构建：`$env:GRADLE_USER_HOME="$env:USERPROFILE\.gradle"; ./gradlew.bat build -x spotlessJavaCheck`
  - Spotless：`$env:GRADLE_USER_HOME="$env:USERPROFILE\.gradle"; ./gradlew.bat spotlessJavaCheck`
  - 部署：`./deploy-mod.bat -Once`
  - 目标实例：`$env:APPDATA\PrismLauncher\instances\<实例名>\.minecraft\mods`
- Qz UILib 依赖通过本地 Maven 仓库解析（`mavenLocal()`），官方 JAR 名 `qz_uilib-4.1.3-LTS.jar`
- 修复后的 Qz UILib 在实例 mods 文件夹中，如需重新修补参考本文件末尾的已知问题列表

## 已知的 Qz UILib 问题与 workaround

| 问题 | 根因代码 | 状态 |
|------|----------|------|
| flex 子元素 maxWidth/minWidth 不生效 | `FlexLayoutHelper.resolveContentMainSize()` 未调用 `applyWidthConstraints` | Qz 源码已修补 |
| 列 flex 用自然内容高度覆盖 flex-basis | `FlexLayoutHelper.layoutColumnFlexChildren()` L228-232 | Qz 源码已修补 |
| 框架给根元素默认 `overflow-y:auto` | `UiDocumentScreens` 文档注释 | Java 侧显式设 `overflow:hidden` |
| 滚动容器需 `height:auto()` 而非 `px(0)` | `UiStyleLength.Type` 区分 PIXEL/AUTO | Java 侧 `scrollBox()` 使用 `auto()` |

## 设计决策

- **NBT 键值**：不为了"看起来更像上游"而重命名已有键。如果必须迁移，先加旧键读取兼容，再在单独提交中切换写入。优先采用上游委托模式（如 `DualityInterface.writeToNBT`）。

## 本文件的维护规则

- 只记录长期稳定边界和导航指针，不记录阶段流水账、类清单、修复日志
- 有明确归属的信息写回原文档，不在此重复
- 更新时优先做删减和归并，避免按日期追加
- 临时调试记录和一次性试验结论不沉淀到本文件

# ModularUI2 界面开发指南

> 面向「在本项目里写/改网络界面」的开发者。内容基于本仓库实际代码
> （`client/ui/NetworkTabUI.java`、`NetworkTerminalUI.java`、`NetworkUiKit.java`）与
> `docs/errors/` 错误记录，不引入未经验证的 MUI2 API 断言。

## 1. 定位与边界

- **库**：GTNH fork 的 ModularUI2，包名 **`com.cleanroommc.modularui`**（保留了上游 CleanroomMC 包名）。
- **依赖坐标**（`dependencies.gradle`）：`com.github.GTNewHorizons:ModularUI2:2.3.63-1.7.10:dev`，由 GTNH Maven 自动解析，无需手动安装。
- **本项目用法**：走 **client-only screen** 路线——`ModularScreen` + `GuiScreenWrapper`，**不使用** MUI2 的 sync handler / `ModularContainer`。数据完全靠项目自有的 packet 系统（`network/packet/`）驱动。
- **何时用**：需要 AE2 原生 GUI 之外的自定义界面（如网络终端、网络标签页）。常规总线/终端仍继承 AE2 的 `GuiUpgradeable` / `GuiMEMonitorable`，不在本指南范围。

## 2. client-only 接入配方

界面通过 Forge 老流程打开（`Block.onBlockActivated → player.openGui → SingularityGuiHandler`）。
`SingularityGuiHandler.getClientGuiElement(...)` 返回一个 `GuiScreenWrapper`：

```java
public static GuiScreen create(final TileEntity te) {
    // ... 提取 x/y/z/dim ...
    final ModularScreen screen = new ModularScreen("singularityme", (ModularGuiContext ctx) -> {
        final TabState state = new TabState(x, y, z, dim);
        activeState = new WeakReference<>(state);   // 静态引用，供 packet 回调找到
        return state.buildPanel();                  // 返回 ModularPanel
    });
    screen.getContext().setSettings(new UISettings());   // 必须：否则 excludeArea 时 NPE
    final GuiScreenWrapper wrapper = new GuiScreenWrapper(screen);
    // 打开后请求服务端数据（client 线程）
    Minecraft.getMinecraft().func_152344_a(() -> {
        final TabState s = activeState == null ? null : activeState.get();
        if (s != null) s.requestNetworkData();
    });
    return wrapper;
}
```

要点：
- `GuiScreenWrapper extends GuiScreen`，构造时 `screen.construct(this)` 会**跳过** `ModularContainer` 初始化，纯客户端面板无需 sync。
- 服务端那个空 `AEBaseContainer`（`ContainerSingularityNetworkTab` 等）仍按 Forge 老流程开，与客户端 wrapper 解耦。
- 每个界面保留静态接口：
  - `static GuiScreen create(TileEntity)` — 创建屏幕
  - `static boolean receiveNetworkData(PacketNetworkTabData)` — 接收网络列表数据
  - `static boolean receiveNetworkStatus(PacketNetworkStatus)` — （仅 `NetworkTerminalUI`）接收设备状态/能量等数据

## 3. Widget 速查表

本项目实际用到的 widget（均在 `com.cleanroommc.modularui` 下）：

### 布局容器

| Widget / API | 用途 | 本项目调用示例 |
|---|---|---|
| `Flow.column()` / `Flow.row()` | 纵/横向 flex 容器。**注意**：构造器默认调用 `sizeRel(1f, 1f)`，子元素宽高默认依赖父容器。 | `NetworkTabUI.buildPanel()` root |
| `.childPadding(int)` | 子元素间距（= CSS gap） | `Flow.column().childPadding(10)` |
| `.expanded()` | 占满主轴剩余空间（= flex-grow:1） | `contentArea = Flow.column().widthRel(1f).expanded()` |
| `.coverChildrenHeight()` | 高度由子元素决定（默认 minSize=8px） | `NetworkTerminalUI` 的 `bottomArea` |
| `.widthRel(f)` / `.heightRel(f)` | 相对父容器比例尺寸 | `.widthRel(1f)` |
| `.mainAxisAlignment(Alignment.MainAxis.*)` | 主轴对齐（START/CENTER/END/SPACE_BETWEEN） | 列表头 SPACE_BETWEEN |
| `.crossAxisAlignment(Alignment.CrossAxis.*)` | 交叉轴对齐 | 行内 CENTER 垂直居中 |
| `ListWidget` | 纵向滚动列表 | `networkList`，行动态增删 |

### 基础控件

| Widget / API | 用途 | 本项目调用示例 |
|---|---|---|
| `TextWidget(IKey.str(s))` | 文本，`.color(argb)` 上色 | 行内文本、标题 |
| `IKey.dynamicKey(Supplier<IKey>)` | 内容随状态动态刷新的文本 | 摘要框设备名/默认网络名 |
| `ButtonWidget<>()` | 按钮 | `.overlay(IKey.str(t))` / `.child(rowContent)` 作整行点击 |
| `.onMousePressed(mb -> {...; return true;})` | 点击回调。禁用时应在回调内 `if (!isEnabled()) return false;` 阻止事件消费 | 选中网络行、发包 |
| `.disableHoverBackground()` | 关闭默认 hover 高亮（自管背景时） | 所有自绘行/导航按钮 |
| `.setEnabled(boolean)` | 启用/禁用按钮 | `selectBtn.setEnabled(canAssign)` |
| `TextFieldWidget().value(StringValue)` | 文本输入，绑定到 `StringValue` | 成员名、网络名、过滤文本 |
| `.autoUpdateOnChange(true)` | 输入即时回写 `StringValue` | 成员名/网络名输入 |

### Drawable / 样式

| API | 用途 | 本项目定义位置 |
|---|---|---|
| `Rectangle().color(argb)` | 纯色背景 | `NetworkUiKit.Styles.listBg()` 等 |
| `Rectangle().cornerRadius(int)` | 圆角背景 | 所有 `Styles.*` 方法 |
| `Rectangle().verticalGradient(top, bottom)` | 垂直渐变背景 | `Styles.panelBg()`, `rowBg()`, `headerGradient()` |
| `Circle().color(argb).segments(int)` | 圆形（用于状态点） | `Styles.statusDot()` |
| `ShadowDrawable(inner, spread, shadowColor)` | 为背景叠加投影 | `new ShadowDrawable(Styles.panelBg(), 5, 0x80000000)` |

### 本项目扩展组件（`NetworkUiKit`）

| 方法 | 用途 |
|---|---|
| `fixedRow(int height)` | 创建固定高度行（仅保留水平 padding） |
| `textRow()` | 创建自适应文本行（避免垂直 padding 溢出） |
| `badge(String text, int bgColor)` | 彩色圆角徽章通用组件 |
| `securityBadge(NetworkEntry)` | 安全级别徽章（绿/橙/紫色背景） |
| `accessBadge(NetworkEntry)` | 访问级别徽章（蓝/青/红色背景） |
| `idPill(int networkID)` | ID 胶囊（如 `#1`，深底圆角） |
| `currentBadge()` / `defaultBadge()` | `*` 绿色 / `D` 蓝色标记徽章 |

颜色统一用 ARGB int，集中在 `NetworkUiKit.Palette`。背景样式集中在 `NetworkUiKit.Styles`。

## 4. 数据刷新模式

服务端 → 客户端走既有 packet，UI 不碰网络层。本项目存在**两条独立刷新路径**和**两种重建策略**。

### 路径一：网络列表数据（`PacketNetworkTabData`）

```
PacketNetworkTabData.Handler.onMessage   // 收到数据，client 线程
  → Minecraft.func_152344_a(...)         // 调度到主线程
  → NetworkTabUI.receiveNetworkData(pkt) // 命中当前打开的面板才处理
  → state.receive(pkt)                   // 更新本地状态字段
  → rebuildAll()                         // 重建 widget 子树
```

- **命中判定**：`receiveNetworkData` 用 `currentScreen instanceof GuiScreenWrapper w && w.getScreen().isPanelOpen("network_tab")` 确认是本面板再刷新。
- 两个 UI 共享同一个 packet 类型；`Handler.onMessage` 中按优先级分发给 `NetworkTabUI` 和 `NetworkTerminalUI`。

### 路径二：网络状态数据（`PacketNetworkStatus`，仅 `NetworkTerminalUI`）

```
PacketNetworkStatus.Handler.onMessage
  → NetworkTerminalUI.receiveNetworkStatus(pkt)
  → state.receiveStatus(pkt)             // networkStatus 字段赋值
  → renderContent()                      // 全量重建当前面板
```

- 网络状态（设备列表、能量、在线率等）走独立 packet，以 `selectedNetworkID` 为键。
- 切换网络时 `networkStatus = null` + 请求新数据，避免显示旧数据。

### 两种重建策略

| 策略 | 使用位置 | 特点 |
|------|---------|------|
| `rebuildAll()`（增量） | `NetworkTabUI` | 只重建 `networkList` + `bottomArea`，不动标题/摘要行 |
| `renderContent()`（全量） | `NetworkTerminalUI` | `contentArea.removeAll()` 后根据 `currentPanel` switch 重建整个内容区 |

### `panelFirstRender` 机制

`NetworkTerminalUI` 中 SETTINGS / CREATE 面板用 `panelFirstRender` 标志位，仅首次进入时从网络数据重置输入框值，后续数据刷新不覆盖用户正在编辑的内容。面板切换时该标志位置 `true`，`renderContent()` 结束时置 `false`。

- **列表重建三步**：`list.removeAll()` → 循环 `list.child(buildRow(e))` → `list.scheduleResize()`。
- **动态文本**：随状态变化的单条文本用 `IKey.dynamicKey(() -> IKey.str(...))`，不必重建整行（见 `summaryBox`）。
- **必须在 client 线程**：packet handler 已用 `func_152344_a` 调度，沿用即可。

## 5. Qz → MUI2 迁移对照

读旧设计或迁移残留代码时的等价写法对照：

| 旧 Qz-UILib 写法 | MUI2 等价写法 |
|---|---|
| `document.div()` + `.style().setXxx()` | `Flow.column()` / `Flow.row()` + 链式属性 |
| CSS `display:flex; flex-direction:column` | `Flow.column()` |
| `flex-grow:1` / `flex:1 1 0` | `.expanded()` |
| `width:100%` | `.widthRel(1f)` |
| `gap` | `.childPadding(int)` |
| `justify-content` | `.mainAxisAlignment(Alignment.MainAxis.*)` |
| `align-items` | `.crossAxisAlignment(Alignment.CrossAxis.*)` |
| `overflow-y:scroll` 滚动容器 | `ListWidget`（自带滚动） |
| `ElementNode` 色块 / 徽章 | **推荐** `NetworkUiKit.badge(text, color)` / `securityBadge(entry)` / `accessBadge(entry)`；静态状态点用 `statusDotWidget(color)` 或 `TextWidget.color(argb)`；可点击色板必须用 `ButtonWidget` 本体背景 + overlay |
| `DocumentButtonControl` + `setClickHandler` | `ButtonWidget<>().overlay(...).onMousePressed(...)` |
| `DocumentTextInputControl` | `TextFieldWidget().value(StringValue)` |
| `QzNetworkUiKit.MaskedInput`（旧密码掩码） | 当前 PUBLIC/PRIVATE 模型不再使用密码输入；普通文本仍用 `TextFieldWidget().value(StringValue)` |
| `clearChildren()` + 重新 append | `removeAll()` + `child(...)` + `scheduleResize()` |
| `setBackgroundColor(argb)` | `.background(new Rectangle().color(argb))` |

类名映射：`QzNetworkTabScreens` → `NetworkTabUI`、`QzNetworkTerminalScreens` → `NetworkTerminalUI`、`QzNetworkUiKit` → `NetworkUiKit`。

> 注意多面板切换：终端用**手动 `currentPanel` 枚举 + `renderContent()` switch** 重建内容区，不是 MUI2 的 `PagedWidget`。新增面板照此模式即可。

## 6. 已知陷阱

| 陷阱 | 说明 | 规避写法 |
|---|---|---|
| **`coverChildrenHeight()` Column 的子元素高度依赖父（卡死）** | Flow 构造器默认 `sizeRel(1f, 1f)`，无显式 `.height()` 的 Row 停留在 `heightRel(1f)` → `heightDependsOnParent()=true`。Column 在主轴上 `canCoverByDefaultSize(Y)=false`（源码 `Flow` L250-252：`axis.getOther() == this.axis`），无法用子元素默认尺寸兜底 → `!hasIndependentChildY && !coverByDefaultSizeY` 分支 → 布局循环不收敛 → GUI 卡死。Row 的 `coverChildrenHeight()` 覆盖交叉轴不受此限（`canCoverByDefaultSize` 对交叉轴返回 true），故 `navBar`(Row) 不挂而 `bottomArea`(Column) 挂。 | **必须**：`coverChildrenHeight()` Column 的每个子 Row 加显式 `.height(Palette.ROW_H)`；或将 Column 本身从 `coverChildrenHeight()` 改为固定 `.height(n)`。优先使用 `NetworkUiKit.fixedRow(int)` 创建固定高度行。 |
| **`coverChildrenHeight()` 空容器初始高度=8px** | `coverChildrenHeight()` 默认 minSize=8（`IPositioned` L41），空容器通过 `coverChildrenForEmpty()` 获得高度=8px。动态添加子元素后可能不触发父列重新布局。 | 初始就放入占位子元素；或已知子元素数量/高度恒定时改用固定 `.height(n)`。 |
| **`ButtonWidget.setEnabled()` 不阻止已注册的回调** | `onMousePressed` 设置在 `setEnabled` 之前时，禁用的按钮仍会执行回调。 | 回调内显式检查 `isEnabled()`：`mb -> { if (!isEnabled()) return false; ... }`。 |
| `IPositioned` sizing 链式类型退化 | 源码签名返回泛型 `W`，但在部分 Widget、raw type 或链式推断场景下静态类型可能退化为 `IPositioned`，导致 `.width(int)` / `.expanded()` / `ListWidget.widthRel()` 之后不能再接 `.background()` / `.color()` 等 Widget 方法 | 先设 background 再设 sizing；或拆成多步、用局部变量赋值（见 `nameWidget.expanded()` 单独成行） |
| `Rectangle` 不支持圆角 + 边框并存 | 原 Qz UI 的圆角+描边效果无法直接复刻 | 接受直角；或两层 `Rectangle` 叠加；或使用 `ShadowDrawable` 叠加投影 |
| 不要复用旧密码流 | 当前网络模型没有密码加入/设置路径，过时原型中的 password mode 不应再迁回主 UI | 使用 PUBLIC/PRIVATE + 权限位；输入框仅承载成员名、网络名、过滤文本 |
| TextWidget padding 在固定高度行内垂直溢出 | 固定高度 Row 内 TextWidget 的 padding 与主题默认 padding 叠加，日志报 `[SIZING] Margin/padding ... exceeds parent size`（不阻断渲染） | 优先使用 `NetworkUiKit.fixedRow(int)` / `textRow()`；垂直留白放到父容器 margin；详见 `docs/errors/ERROR-20260601-mui2-sizing-padding-overflow.md` |
| 输入框被 render 重置 | 每次 `renderContent()` 若无条件 `setStringValue` 会清空用户输入 | 用 `panelFirstRender` 标志位，仅首次进入面板时重置（见 `renderSettings`） |

## 7. 参考

- [ModularUI2 仓库](https://github.com/GTNewHorizons/ModularUI2)（GTNH fork，源码与示例）
- **本项目核心类**：
  - `NetworkUiKit.Palette` — 颜色/尺寸常量（`BG_*`, `TEXT_*`, `BTN_*`, `SECURITY_*`, `ACCESS_*`, `ROW_H` 等）
  - `NetworkUiKit.Styles` — 样式工厂（`panelBg()`, `cardBg()`, `listBg()`, `rowBg(int)`, `inputBg()`, `headerGradient(int)`, `swatch(int)`, `statusDot(int)`）
  - `NetworkUiKit.badge()` / `securityBadge()` / `accessBadge()` / `idPill()` — 徽章/胶囊组件
  - `ShadowDrawable` — 面板投影 drawable
- `docs/html-reference/` — 两个界面的视觉设计规格（`network-tab.html` / `network-terminal.html` / `shared-palette.css`）
- `docs/errors/README.md` — MUI2 相关错误索引
- 范例代码：`src/main/java/com/github/singularityme/client/ui/`（`NetworkTabUI` / `NetworkTerminalUI` / `NetworkUiKit` / `ShadowDrawable`）

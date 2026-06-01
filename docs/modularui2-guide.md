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
- 每个界面保留两个静态接口：`static GuiScreen create(TileEntity)` 与 `static boolean receiveNetworkData(PacketNetworkTabData)`。

## 3. Widget 速查表

本项目实际用到的 widget（均在 `com.cleanroommc.modularui` 下）：

| Widget / API | 用途 | 本项目调用示例 |
|---|---|---|
| `ModularPanel(String name)` | 根面板，`name` 用于 `isPanelOpen()` 判定 | `new ModularPanel("network_tab").size(w,h).background(...)` |
| `Flow.column()` / `Flow.row()` | 纵/横向 flex 容器 | `NetworkTabUI.buildPanel()` root |
| `.childPadding(int)` | 子元素间距（= CSS gap） | `Flow.column().childPadding(10)` |
| `.expanded()` | 占满主轴剩余空间（= flex-grow:1） | `contentArea = Flow.column().widthRel(1f).expanded()` |
| `.widthRel(f)` / `.heightRel(f)` | 相对父容器比例尺寸 | `.widthRel(1f)` |
| `.mainAxisAlignment(Alignment.MainAxis.*)` | 主轴对齐（START/CENTER/END/SPACE_BETWEEN） | 列表头 SPACE_BETWEEN |
| `.crossAxisAlignment(Alignment.CrossAxis.*)` | 交叉轴对齐 | 行内 CENTER 垂直居中 |
| `ListWidget` | 纵向滚动列表 | `networkList`，行动态增删 |
| `TextWidget(IKey.str(s))` | 文本，`.color(argb)` 上色 | 行内徽章、标题 |
| `IKey.dynamicKey(Supplier<IKey>)` | 内容随状态动态刷新的文本 | 摘要框设备名/默认网络名 |
| `ButtonWidget<>()` | 按钮 | `.overlay(IKey.str(t))` / `.child(rowContent)` 作整行点击 |
| `.onMousePressed(mb -> {...; return true;})` | 点击回调 | 选中网络行、发包 |
| `.disableHoverBackground()` | 关闭默认 hover 高亮（自管背景时） | 所有自绘行/导航按钮 |
| `.setEnabled(boolean)` | 启用/禁用按钮 | `selectBtn.setEnabled(canAssign)` |
| `TextFieldWidget().value(StringValue)` | 文本输入，绑定到 `StringValue` | 密码、成员名、网络名 |
| `.autoUpdateOnChange(true)` | 输入即时回写 `StringValue` | 密码框 |
| `Rectangle().color(argb)` | 纯色背景 drawable | `.background(new Rectangle().color(Palette.BG_PANEL))` |

颜色统一用 ARGB int（如 `0xEE141923`），集中在 `NetworkUiKit.Palette`。

## 4. 数据刷新模式

服务端 → 客户端走既有 packet，UI 不碰网络层：

```
PacketNetworkTabData.Handler.onMessage   // 收到数据，client 线程
  → Minecraft.func_152344_a(...)         // 调度到主线程
  → NetworkTabUI.receiveNetworkData(pkt) // 命中当前打开的面板才处理
  → state.receive(pkt)                   // 更新本地状态字段
  → rebuildAll()                         // 重建 widget 子树
```

- **命中判定**：`receiveNetworkData` 用 `currentScreen instanceof GuiScreenWrapper w && w.getScreen().isPanelOpen("network_tab")` 确认是本面板再刷新。
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
| `ElementNode` 色块 / 徽章 | `TextWidget(IKey.str(...)).color(argb)` 或 `Rectangle().color(argb)` 背景 |
| `DocumentButtonControl` + `setClickHandler` | `ButtonWidget<>().overlay(...).onMousePressed(...)` |
| `DocumentTextInputControl` | `TextFieldWidget().value(StringValue)` |
| `QzNetworkUiKit.MaskedInput`（密码掩码） | **无内建等价**——当前明文显示（见陷阱表） |
| `clearChildren()` + 重新 append | `removeAll()` + `child(...)` + `scheduleResize()` |
| `setBackgroundColor(argb)` | `.background(new Rectangle().color(argb))` |

类名映射：`QzNetworkTabScreens` → `NetworkTabUI`、`QzNetworkTerminalScreens` → `NetworkTerminalUI`、`QzNetworkUiKit` → `NetworkUiKit`。

> 注意多面板切换：终端用**手动 `currentPanel` 枚举 + `renderContent()` switch** 重建内容区，不是 MUI2 的 `PagedWidget`。新增面板照此模式即可。

## 6. 已知陷阱

| 陷阱 | 说明 | 规避写法 |
|---|---|---|
| sizing 方法返回 `IPositioned` 而非 `W` | `.width(int)` / `.expanded()` / `ListWidget.widthRel()` 之后链断裂，不能再接 `.background()`/`.color()` 等 Widget 方法 | 先设 background 再设 sizing；或拆成多步、用局部变量赋值（见 `nameWidget.expanded()` 单独成行） |
| `Rectangle` 不支持圆角 + 边框并存 | 原 Qz UI 的圆角+描边效果无法直接复刻 | 接受直角；或两层 `Rectangle` 叠加 |
| 无内建密码掩码 | `TextFieldWidget` 无 password 模式，密码当前**明文显示** | 已知限制；`NetworkUiKit.maskPassword()` 备有等长掩码工具，如需接入再 wire |
| TextWidget padding 在固定高度行内垂直溢出 | 固定高度 `Flow.row().height(24)` 内 TextWidget 的 padding 与主题默认 padding 叠加，日志报 `[SIZING] Margin/padding ... exceeds parent size`（不阻断渲染） | 减小固定高度行的 padding；或用 `margin` 替代 `padding`；详见 `docs/errors/ERROR-20260601-mui2-sizing-padding-overflow.md` |
| 输入框被 render 重置 | 每次 `renderContent()` 若无条件 `setStringValue` 会清空用户输入 | 用 `panelFirstRender` 标志位，仅首次进入面板时重置（见 `renderSettings`） |

## 7. 参考

- [ModularUI2 仓库](https://github.com/GTNewHorizons/ModularUI2)（GTNH fork，源码与示例）
- `docs/html-reference/` — 两个界面的视觉设计规格（`network-tab.html` / `network-terminal.html` / `shared-palette.css`）
- `docs/errors/README.md` — MUI2 相关错误索引
- 范例代码：`src/main/java/com/github/singularityme/client/ui/`（`NetworkTabUI` / `NetworkTerminalUI` / `NetworkUiKit`）


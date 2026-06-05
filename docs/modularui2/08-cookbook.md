# 08. 本项目实战配方

## 打开 client-only 面板

```java
public static GuiScreen create(final TileEntity te) {
    final int x = te == null ? 0 : te.xCoord;
    final int y = te == null ? 0 : te.yCoord;
    final int z = te == null ? 0 : te.zCoord;
    final int dim = te != null && te.getWorldObj() != null && te.getWorldObj().provider != null
        ? te.getWorldObj().provider.dimensionId : 0;

    final ModularScreen screen = new ModularScreen("singularityme", ctx -> {
        final MyState state = new MyState(x, y, z, dim);
        activeState = new WeakReference<>(state);
        return state.buildPanel();
    });
    screen.getContext().setSettings(new UISettings());
    return new GuiScreenWrapper(screen);
}
```

## packet 刷新模式

```text
request packet -> server handler -> response packet
  -> client thread func_152344_a
  -> static receive
  -> state.receive
  -> removeAll + child + scheduleResize
```

列表重建模板：

```java
list.removeAll();
for (final Entry entry : entries) {
    list.child(buildRow(entry));
}
list.scheduleResize();
```

## 安全尺寸写法

固定高度行：

```java
Flow.row()
    .childPadding(8)
    .widthRel(1f)
    .height(Palette.ROW_H)
    .padding(12, 0)
    .crossAxisAlignment(Alignment.CrossAxis.CENTER);
```

自适应文本行：

```java
Flow.row()
    .coverChildrenHeight()
    .margin(2, 0)
    .crossAxisAlignment(Alignment.CrossAxis.CENTER);
```

列表主体：

```java
final ListWidget list = new ListWidget();
list.background(Styles.listBg());
list.widthRel(1f);
list.expanded();
```

## Qz -> MUI2 迁移对照

| 旧 Qz-UILib 写法 | MUI2 等价写法 |
|------------------|---------------|
| `document.div()` + style | `Flow.column()` / `Flow.row()` |
| CSS `display:flex` | `Flow` |
| `flex-grow:1` | `.expanded()` |
| `width:100%` | `.widthRel(1f)` |
| `gap` | `.childPadding(int)` |
| `justify-content` | `.mainAxisAlignment(...)` |
| `align-items` | `.crossAxisAlignment(...)` |
| 滚动容器 | `ListWidget` |
| 色块 / 徽章 | 静态状态点用 `statusDotWidget(color)` 或 `TextWidget("■")`；徽章用 `NetworkUiKit.badge(...)`；可点击色板用 `ButtonWidget` 本体背景 + `overlay(Styles.swatch(color))` |
| button click | `ButtonWidget().onMousePressed(...)` |
| text input | `TextFieldWidget().value(StringValue)` |
| clear + append | `removeAll()` + `child(...)` + `scheduleResize()` |
| `setBackgroundColor(argb)` | `.background(new Rectangle().color(argb))` |
| 旧密码掩码控件 | 当前网络模型不使用密码输入；成员/网络名/过滤文本用普通 `TextFieldWidget` |

## 多面板终端模式

网络终端使用：

- `enum Panel`
- `Panel currentPanel`
- `renderContent()` switch
- `panelFirstRender`

该模式比 `PagedWidget` 更适合本项目，因为部分面板切换需要发 `PacketRequestNetworkStatus`，部分面板需要保留输入，部分面板需要重置创建表单。

## 按钮工厂

```java
private static ButtonWidget<?> makeBtn(String text, int w, Runnable action, boolean enabled) {
    return new ButtonWidget<>()
        .overlay(IKey.str(text))
        .width(w).height(Palette.ROW_H).padding(12, 0)
        .background(Styles.rowBg(enabled ? Palette.BTN_NORMAL : Palette.BTN_DISABLED))
        .onMousePressed(mb -> {
            if (!enabled) return false;
            action.run();
            return true;
        });
}
```

注意：如果需要运行时启用/禁用，使用局部 `button` 变量并调用 `setEnabled(boolean)`，不要只捕获创建时的 `enabled` 值。

## 可点击色板

颜色选择这类小控件必须让点击目标和主要视觉落在同一个 `ButtonWidget` 上：

```java
new ButtonWidget<>()
    .width(Palette.SWATCH_BUTTON_SIZE).height(Palette.SWATCH_BUTTON_SIZE)
    .padding((Palette.SWATCH_BUTTON_SIZE - Palette.SWATCH_INNER_SIZE) / 2)
    .background(selected
        ? Styles.rowBg(NetworkUiKit.lighten(color, 0.18f))
        : Styles.rowBg(Palette.BG_ROW))
    .overlay(Styles.swatch(color))
    .disableHoverBackground()
    .onMousePressed(mb -> {
        onSelect.accept(color & 0xFFFFFF);
        return true;
    });
```

不要用 `ButtonWidget.child(Flow.row().background(...))` 承载可见色块。MUI2 的 hover/click 命中链会先命中有可见背景的内部 `Flow`，普通 `Flow` 不是可交互控件且会阻断事件继续到外层按钮。

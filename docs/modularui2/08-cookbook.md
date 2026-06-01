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
    .padding(0, 12)
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
| 色块 / 徽章 | `TextWidget("■")` 或 `Flow` + `Rectangle` |
| button click | `ButtonWidget().onMousePressed(...)` |
| text input | `TextFieldWidget().value(StringValue)` |
| clear + append | `removeAll()` + `child(...)` + `scheduleResize()` |
| `setBackgroundColor(argb)` | `.background(new Rectangle().color(argb))` |
| 密码掩码控件 | 暂无内建等价，当前明文 |

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
        .width(w).height(Palette.ROW_H).padding(0, 12)
        .background(Styles.rowBg(enabled ? Palette.BTN_NORMAL : Palette.BTN_DISABLED))
        .onMousePressed(mb -> {
            if (!enabled) return false;
            action.run();
            return true;
        });
}
```

注意：如果需要运行时启用/禁用，使用局部 `button` 变量并调用 `setEnabled(boolean)`，不要只捕获创建时的 `enabled` 值。

# 03. 核心 Widget 目录

## Flow

源码：`widgets/layout/Flow.java:20`。

用途：本项目所有网络 UI 的主布局容器。`NetworkTabUI` 用 `Flow.column()` 组织标题、摘要、列表、底部栏；`NetworkTerminalUI` 用 `Flow.row()` 组织导航、列表行和表单行。

推荐写法：

```java
final Flow row = Flow.row()
    .childPadding(8)
    .widthRel(1f)
    .height(Palette.ROW_H)
    .padding(12, 0)
    .crossAxisAlignment(Alignment.CrossAxis.CENTER);
```

避免：父级 `coverChildrenHeight()` 下塞没有固定高度的复杂子树。

## ModularPanel

源码：`screen/ModularPanel.java:63`。

顶层面板一般设置固定尺寸和背景，然后挂载一个 root `Flow`：

```java
panel = new ModularPanel("network_terminal")
    .size(panelW, panelH)
    .background(new ShadowDrawable(Styles.panelBg(), 6, 0x80000000));
panel.child(root);
```

Panel name 会被 `isPanelOpen("network_terminal")` 用于 packet 刷新命中判定。

## TextWidget

源码：`widgets/TextWidget.java:20`。

常用：

- `new TextWidget(IKey.str(text)).color(argb)`
- `new TextWidget(IKey.dynamicKey(() -> IKey.str(value)))`
- 对需要占满剩余空间的文本，分步调用 `nameWidget.expanded()`。

注意：固定高度行内不要给文本所在链路加过大的垂直 padding，见 [02-layout-sizing.md](02-layout-sizing.md)。

## ButtonWidget

源码：`widgets/ButtonWidget.java:16`。

常用：

```java
return new ButtonWidget<>()
    .overlay(IKey.str(text))
    .width(w).height(Palette.ROW_H).padding(12, 0)
    .background(Styles.rowBg(Palette.BTN_NORMAL))
    .disableHoverBackground()
    .onMousePressed(mb -> {
        action.run();
        return true;
    });
```

本项目有两种按钮模式：

- `overlay(IKey.str(text))`：普通命令按钮。
- `child(rowContent)`：整行可点击，如网络列表行。

## TextFieldWidget

源码：`widgets/textfield/TextFieldWidget.java:35`。

本项目用 `StringValue` 绑定：

```java
private static TextFieldWidget makeInput(StringValue val) {
    return new TextFieldWidget()
        .value(val)
        .height(Palette.ROW_H).expanded()
        .background(Styles.inputBg())
        .autoUpdateOnChange(true);
}
```

当前用途：成员名、网络名、过滤文本等普通输入。旧密码加入流已从当前网络模型移除，不要再为 Network Tab/Terminal 引入 password mode。

## ListWidget

源码：`widgets/ListWidget.java:34`。

本项目用法：

```java
final ListWidget list = new ListWidget();
list.background(Styles.listBg());
list.widthRel(1f);
list.expanded();
for (final NetworkEntry entry : networks) {
    list.child(buildSelectionRow(entry));
}
```

刷新列表时：

1. `removeAll()`
2. 循环 `child(...)`
3. `scheduleResize()`

`ListWidget.java:182` 和 `ListWidget.java:191` 的源码路径显示，列表自身在子元素变动时也会触发 resize，但项目重建列表后显式调用更稳。

## SliderWidget / ProgressWidget

本项目当前没有直接使用 Slider。进度条用两个 `Flow.row()` 叠出 track / fill：

```java
final Flow track = Flow.row().widthRel(1f).height(14).background(Styles.listBg());
final Flow fill = Flow.row().widthRel(clamped).heightRel(1f).background(Styles.rowBg(color));
track.child(fill);
```

这样避免引入交互 slider，也更符合状态页只读展示。

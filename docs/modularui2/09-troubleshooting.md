# 09. 排错清单

## resize 死锁 / 卡住

现象：打开界面时 MUI2 resize 树反复报错或界面不稳定。

根因：父级 `coverChildren` 与子级相对尺寸、`expanded()` 或无显式尺寸形成循环依赖。`DimensionSizer.java:401` 对 expanded + coverChildren 组合有冲突警告。

修法：

- 父级用 `coverChildrenHeight()` 时，子级给固定高度。
- 内容区用固定父尺寸 + 子级 `expanded()`。
- 列表用 `widthRel(1f)` + `expanded()`，不要让列表父级包络列表高度。

## TextWidget SIZING padding 溢出

现象：

```text
[SIZING][TextWidget]: Margin/padding is set on both sides on axis Y, but total size exceeds parent size.
```

根因：固定高度行内叠加垂直 padding、主题默认 padding 和 TextWidget 尺寸。详见 `docs/errors/ERROR-20260601-mui2-sizing-padding-overflow.md`。

修法：

- 固定高度行使用 `.padding(horizontal, 0)`；MUI2 两参数顺序是水平、垂直。
- 垂直留白放到外层 `margin`。
- 复用 `NetworkUiKit.fixedRow(int)` / `NetworkUiKit.textRow()`。

## 链式方法断裂

现象：`.width()` / `.expanded()` / `ListWidget.widthRel()` 后不能继续 `.background()` 或 `.color()`。

根因：sizing 方法来自 `IPositioned<W>`。源码签名返回 `W`，但在部分 Widget、raw type 或链式推断场景下，Java 静态类型可能退化为 `IPositioned`。2.3.63 字节码经 `javap` 看到的是擦除后的接口返回类型，不能据此反推源码 API 是“直接返回 `IPositioned`”。

修法：

```java
final TextWidget nameWidget = new TextWidget(IKey.str(name)).color(Palette.TEXT_PRIMARY);
nameWidget.expanded();
```

或：

```java
final ListWidget list = new ListWidget();
list.background(Styles.listBg());
list.widthRel(1f);
list.expanded();
```

## 密码明文显示

现象：网络加入、创建、设置密码时输入框显示原文。

根因：当前使用的 `TextFieldWidget` 未接入 password mode。`NetworkUiKit.maskPassword()` 只是等长掩码工具，没有改变输入控件渲染。

修法：如要真正掩码，需要定制输入 Widget 或确认 MUI2 对应版本是否有 password API，再改 `TextFieldWidget` 渲染/显示值路径。

## ProgressWidget 不绘制或交互过重

现象：只读状态页使用进度控件时行为不符合预期。

修法：本项目推荐用两个 `Flow` 绘制 track / fill。它只依赖相对宽度，不引入 slider 或 value sync。

## 输入框被刷新清空

现象：用户正在编辑名称/密码，服务端回包后输入被重置。

根因：`renderContent()` 每次都无条件把 `StringValue` 写回服务端旧值。

修法：用 `panelFirstRender` 或明确的“切换面板”事件控制初始化；普通数据刷新不重置本地输入。

## 入口链接

历史错误索引：`docs/errors/README.md`。
MUI2 sizing 历史记录：`docs/errors/ERROR-20260601-mui2-sizing-padding-overflow.md`。

## 文档核对经验

- 先看源码签名，再用 2.3.63 dev jar 字节码复核；遇到泛型 API 时要区分源码签名和 `javap` 擦除签名。
- 写 padding/margin 经验时必须回到 `IPositioned.padding(int, int)` 与 `Box.all(int, int)`，确认第一个参数进 left/right、第二个参数进 top/bottom。
- 可点击控件的可见背景要挂在可交互 Widget 本体上；普通子 `Flow` 有可见背景时可能成为 hover/click 命中链的阻断层。
- 文档中不要写本地绝对路径；源码镜像只能描述为相对路径或版本依据。

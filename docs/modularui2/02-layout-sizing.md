# 02. 布局与尺寸求解

## 核心模型

MUI2 的尺寸求解以 Widget 的 `resizer()` 为中心。常用链式方法大多来自 `IPositioned`：

- `api/widget/IPositioned.java:36` 起定义 `coverChildrenWidth/Height`。
- `api/widget/IPositioned.java:93` 定义 `expanded()`。
- `api/widget/IPositioned.java:334` 定义 `widthRel(float)`。
- `widget/sizer/StandardResizer.java:23` 是默认 resizer。
- `widget/sizer/DimensionSizer.java:19` 维护单轴尺寸状态。

可以把每个 Widget 视为一个四元组：`x / y / width / height`。尺寸来源可能是固定值、父级相对值、主轴剩余空间、子元素包络，或布局引擎二次计算的结果。

## Flow 布局

`widgets/layout/Flow.java:20` 定义 `Flow`。它类似轻量 flex：

- `Flow.column()` / `Flow.row()` 决定主轴。
- `childPadding(int)` 相当于 gap。
- `mainAxisAlignment` 控制主轴排布。
- `crossAxisAlignment` 控制交叉轴对齐。
- `expanded()` 在 `Flow` 父级中吃掉主轴剩余空间。

`Flow.java:163` 会检查当前轴是否依赖子元素；如果同一轴还开启 wrap，源码会警告，因为包络子元素和自动换行互相牵制。

## 合法与高风险尺寸组合

| 父级模式 | 子级模式 | 结果 | 说明 |
|----------|----------|------|------|
| 固定 `width/height` | 固定尺寸 | 合法 | 最稳定 |
| 固定尺寸 | `widthRel/heightRel` | 合法 | 相对父级求解 |
| `Flow` 主轴固定 | 子级 `expanded()` | 合法 | 剩余空间分配 |
| 交叉轴固定 | 子级 `widthRel(1f)` 或 `heightRel(1f)` | 合法 | 常用于列表行 |
| 父级 `coverChildrenHeight()` | 子级固定高 | 合法 | 内容自适应高度 |
| 父级 `coverChildrenHeight()` | 子级 `heightRel(1f)` | 高风险 | 父等子，子又等父 |
| 父级 `coverChildren()` | 子级 `expanded()` | 高风险 | `DimensionSizer.java:401` 对 expanded + coverChildren 有冲突警告 |
| 父级 `coverChildren` | 子级无显式尺寸且依赖父级 | 可能死锁 | ResizeNode 无法收敛 |

死锁经验结论：`coverChildren` 父级乘以无显式尺寸子级，会形成循环依赖。Network Terminal 曾出现 resize 树卡住，本质就是父级想由子级决定尺寸，而子级尺寸又回头依赖父级。

## `coverChildren` 机制

`StandardResizer.java:181` 和 `StandardResizer.java:186` 分别处理空子元素和布局子元素的包络求解，`StandardResizer.java:295` 进入 layout-aware 的子元素包络路径。`DimensionSizer.java:306` 处理空元素时的 coverChildren 最小值。

实践规则：

- 用 `coverChildrenHeight()` 包导航栏、按钮栏时，子级必须有明确高度。
- 用 `coverChildrenWidth()` 做徽章时，内部文字可以包络，但父行不要再依赖该徽章反推出整行高度。
- 列表主体用 `widthRel(1f)` + `expanded()`，不要让列表和父内容区互相 `coverChildren`。

## padding / margin 与 SIZING 溢出

MUI2 会把 margin、padding、主题默认 padding 纳入可用尺寸。当固定高度行里继续给 `TextWidget` 或其父级加垂直 padding，就可能出现：

```text
[SIZING][TextWidget]: Margin/padding is set on both sides on axis Y, but total size exceeds parent size.
```

项目错误记录见 `docs/errors/ERROR-20260601-mui2-sizing-padding-overflow.md`。修法已经沉淀为：

- 固定高度行只保留水平 padding：`.height(36).padding(12, 0)`。MUI2 两参数顺序是 `padding(horizontal, vertical)`，不是上/左右。
- 需要垂直留白时放在父级 `margin` 或外层容器。
- 小文本行使用 `NetworkUiKit.textRow()`；固定行使用 `NetworkUiKit.fixedRow(int)`。

## 链式断裂陷阱

`IPositioned` 的默认方法返回接口泛型，很多 Widget sizing 方法在 Java 类型推断下会变成 `IPositioned`，导致 `.width()` 之后不能继续调用 Widget 专属方法。旧 guide 中已验证：

```java
final ListWidget list = new ListWidget();
list.background(Styles.listBg());
list.widthRel(1f);
list.expanded();
```

推荐：先设置 Widget 专属属性，再分步设置尺寸；或用局部变量接住。

# MUI2 文档误导项审计

## 错误现象

MUI2 指南和 AI 记忆文档中存在几类会误导后续实现的说法：把 sizing 链式断裂写成“API 返回 `IPositioned` 而非 `W`”，在文档中写入本地绝对路径，旧 cookbook 仍建议用 `Flow + Rectangle` 表达可点击色块，padding 错误记录没有同步后续色板对齐修正。

## 触发场景

在复盘 Network Terminal 反复 UI 修复时，需要重新核对 MUI2 2.3.63 的布局、padding、hover/click 与 TextField 行为，并用这些一手证据修正文档。

## 根本原因

1. 之前把 `javap` 看到的泛型擦除结果当成源码签名。`com/cleanroommc/modularui/api/widget/IPositioned.java:329`、`:334`、`:359`、`:520` 的源码签名返回泛型 `W`；2.3.63 字节码擦除后显示接口返回类型，不能据此写成“源码 API 返回 `IPositioned`”。
2. 文档直接记录了本机源码镜像绝对路径，违反项目规范“文档中不得写入本地绝对路径”。
3. 旧 cookbook 没有吸收色块点击事故的教训。MUI2 的命中链会把有可见背景的普通子 `Flow` 当作 hover/click 阻断层，详见 `com/cleanroommc/modularui/widget/AbstractParentWidget.java:54`、`com/cleanroommc/modularui/screen/viewport/ModularGuiContext.java:409`、`com/cleanroommc/modularui/screen/ModularPanel.java:391`。
4. padding 修复记录停留在中间状态，未说明后续 `NetworkUiKit.colorSwatchRow` 已改为 `paddingLeft(formInputOffset())` 对齐表单输入起点。

## 修复方案

- 将“sizing 方法返回 `IPositioned`”改为“源码签名返回 `W`，但 Java 静态类型可能退化为 `IPositioned`”。
- 删除文档中的本地绝对路径，改为描述源码镜像相对路径和 2.3.63 dev jar 字节码复核方式。
- 在 cookbook、drawing、troubleshooting 中补充可点击色板必须把视觉背景挂在 `ButtonWidget` 本体上的规则。
- 更新 padding 错误记录，补充后续 `8daf933` 的表单色板对齐修正。

## 预防措施

- 写 MUI2 API 结论前同时核对源码签名和目标版本字节码；涉及泛型时必须区分源码签名、擦除签名和 Java 调用点静态类型。
- 文档只记录可迁移依据，不记录本地机器路径。
- UI 事故记录要在后续修正发生后回填最终状态，避免历史中间方案变成新的“标准答案”。
- 对可点击控件做文档模板时，先确认可见背景、hover 命中和点击回调是否在同一个交互 Widget 上。

# MUI2 padding 参数顺序误判

## 错误现象

网络终端各列表行与表单胶囊要求内容与自身背景左右边界保持 4px，但实际游戏内左侧圆点、编号胶囊、连接列表文本、设置/创建页颜色字段、主页健康提醒行等仍贴近行背景边界。

同类误判也会让设置/创建页的颜色色板行出现“看得见但点不到”：色板行固定高度为 `Palette.ROW_H`，但 `.padding(0, 12)` 实际是上下各 12px，导致 26px 高的颜色按钮被放进几乎没有可用高度的父行内，点击区域在游戏里被裁掉或偏离。

## 触发场景

在 `NetworkTerminalUI` 和 `NetworkTabUI` 中构建列表行、表单只读字段、主页信息行和摘要栏时，误将 `.padding(0, 4)` / `.padding(0, 8)` / `.padding(0, 10)` 当作"垂直 0、水平 N"使用，并进一步把部分间距补到 `badge/idPill` 的外部 margin 上，导致行背景内部仍没有左右留白。

## 根本原因

MUI2 两参数 padding 的真实顺序是 `padding(horizontal, vertical)`：

- 本地 ModularUI2 2.3.63 dev jar 的 `javap` 输出显示，`IPositioned.padding(int, int)` 调用 `Box.all(int, int)`。
- `Box.all(int, int)` 将第一个参数写入 left/right，将第二个参数写入 top/bottom。

因此 `.padding(0, 4)` 表示水平 0、垂直 4，而不是水平 4。此前项目文档中 `.padding(0, horizontal)` 的写法也会误导后续实现。

## 修复方案

- 新增 `Palette.LIST_ROW_PADDING_H = 4`，并将 `NETWORK_ROW_INSET` 作为兼容别名指向它。
- 列表行与表单/信息胶囊背景自身使用 `.padding(Palette.LIST_ROW_PADDING_H, 0)`，覆盖网络左栏、选择列表、连接列表、成员列表、旧网络选择页、颜色只读字段、安全级别分段字段、主页信息行、健康提醒行和选中摘要栏。
- `badge()` 与 `idPill()` 使用 `.padding(Palette.BADGE_PADDING_H, 0)` 保证文字与自身胶囊左右边界的 4px 内距。
- 删除 `BADGE_MARGIN_H`，避免把"行背景内部留白"误实现成"胶囊控件外部间距"。
- 色板行最初改为 `.padding(12, 0)` 以保证颜色按钮完整落在父行点击区域内；后续 `8daf933` 又改为 `.paddingLeft(formInputOffset())`，让色板起点与表单输入框起点一致。
- 在 `NetworkUiKitTest` 中增加 `treatsTwoArgumentPaddingAsHorizontalThenVertical`，并覆盖列表、表单胶囊、信息胶囊的水平内距，直接断言 MUI2 padding 的参数顺序和视觉约束。

## 预防措施

涉及 MUI2 `padding(int, int)` / `margin(int, int)` 时，必须按 `horizontal, vertical` 书写；固定高度行需要左右留白时写 `.padding(horizontal, 0)`。不要用组件外部 margin 修复行背景内部留白问题，先确认背景绘制在哪一层，再把 padding 放到同一层。

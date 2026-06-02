# MUI2 padding 参数顺序误判

## 错误现象

网络终端各列表行要求内容与行背景左右边界保持 4px，但实际游戏内左侧圆点、编号胶囊、连接列表文本仍贴近行背景边界。

## 触发场景

在 `NetworkTerminalUI` 和 `NetworkTabUI` 中构建列表行时，误将 `.padding(0, 4)` 当作"垂直 0、水平 4"使用，并进一步把间距补到 `badge/idPill` 的外部 margin 上，导致行背景内部仍没有左右留白。

## 根本原因

MUI2 两参数 padding 的真实顺序是 `padding(horizontal, vertical)`：

- 本地 ModularUI2 2.3.63 dev jar 的 `javap` 输出显示，`IPositioned.padding(int, int)` 调用 `Box.all(int, int)`。
- `Box.all(int, int)` 将第一个参数写入 left/right，将第二个参数写入 top/bottom。

因此 `.padding(0, 4)` 表示水平 0、垂直 4，而不是水平 4。此前项目文档中 `.padding(0, horizontal)` 的写法也会误导后续实现。

## 修复方案

- 新增 `Palette.LIST_ROW_PADDING_H = 4`，并将 `NETWORK_ROW_INSET` 作为兼容别名指向它。
- 列表行背景自身使用 `.padding(Palette.LIST_ROW_PADDING_H, 0)`，覆盖网络左栏、选择列表、连接列表、成员列表和旧网络选择页。
- `badge()` 与 `idPill()` 使用 `.padding(Palette.BADGE_PADDING_H, 0)` 保证文字与自身胶囊左右边界的 4px 内距。
- 删除 `BADGE_MARGIN_H`，避免把"行背景内部留白"误实现成"胶囊控件外部间距"。
- 在 `NetworkUiKitTest` 中增加 `treatsTwoArgumentPaddingAsHorizontalThenVertical`，直接断言 MUI2 padding 的参数顺序。

## 预防措施

涉及 MUI2 `padding(int, int)` / `margin(int, int)` 时，必须按 `horizontal, vertical` 书写；固定高度行需要左右留白时写 `.padding(horizontal, 0)`。不要用组件外部 margin 修复行背景内部留白问题，先确认背景绘制在哪一层，再把 padding 放到同一层。

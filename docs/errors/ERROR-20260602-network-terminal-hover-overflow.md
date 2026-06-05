# Network Terminal 背景 hover 闪烁与主页内宽裁切

## 错误现象

网络终端打开后，鼠标移到 GUI 背景再移开会出现视觉闪烁；主页信息栏和指标卡在右侧被裁切。

## 触发场景

使用 ModularUI2 `ModularScreen` + `GuiScreenWrapper` 渲染网络终端时，面板、导航栏、列表视口等容器绘制了自定义背景，但仍保留默认 hover 背景；主页内容区同时给 `ListWidget` 设置了内边距，子项仍按外框宽度计算。

## 根本原因

1. `Widget.background(...)` 只替换普通背景，不等于关闭 hover 主题背景。被动容器在鼠标进入/离开时仍可能绘制 MUI2 hover 背景层，和 Minecraft 世界背景叠加后造成闪烁。相关修复点见 `src/main/java/com/github/singularityme/client/ui/NetworkTerminalUI.java:115`、`:189`、`:199`、`:221`。
2. `contentViewport.padding(Palette.CONTENT_VIEWPORT_PAD)` 会让真实可用宽度少于 `layout.contentW`，但主页两列和指标卡曾按外框宽度布局，导致 `widthRel` 子项总宽超过内框。相关修复点见 `src/main/java/com/github/singularityme/client/ui/NetworkUiKit.java:116`、`:310`，以及 `src/main/java/com/github/singularityme/client/ui/NetworkTerminalUI.java:224`、`:431`、`:454`。

## 修复方案

1. 为网络终端屏幕包装器重写 `drawWorldBackground`，绘制稳定的深色渐变遮罩，避免依赖 Minecraft 默认背景在鼠标状态变化时重绘。
2. 对面板、导航栏、网络列、内容视口、列表与被动行容器调用 `disableHoverBackground()`，避免非交互背景参与 hover 状态渲染。
3. 新增 `Palette.CONTENT_VIEWPORT_PAD` 和 `NetworkUiKit.terminalContentInnerWidth(int)`，主页信息两列与指标卡统一按内容区内宽计算。
4. 增加 `NetworkUiKitTest.computesContentInnerWidthForHomeRows` 和 `exposesReferenceSurfaceColors`，锁定内宽计算与参考稿表面色。

## 预防措施

以后在 MUI2 中给被动容器添加自定义背景时，同时确认是否需要 `disableHoverBackground()`；在 `ListWidget` 或有 padding 的容器内做固定宽度子布局时，必须先扣除容器内边距，并用单元测试覆盖实际可用宽度。

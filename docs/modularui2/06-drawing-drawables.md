# 06. 绘制与 Drawable

## GuiDraw 原语

源码：`drawable/GuiDraw.java:39` 定义 `GuiDraw` 工具类。

关键入口：

- `GuiDraw.java:120` 单色圆角矩形。
- `GuiDraw.java:125` 上下渐变圆角矩形。
- `GuiDraw.java:129` 左右渐变圆角矩形。
- `GuiDraw.java:132` 四角渐变圆角矩形。
- `GuiDraw.java:572` 阴影绘制 `drawDropShadow`。

项目没有直接调用 `GuiDraw`，而是通过 `Rectangle`、`Circle` 和自定义 `ShadowDrawable` 封装，避免 UI 类里散落底层绘制参数。

## Rectangle

源码：`drawable/Rectangle.java:23`。`Rectangle.java:145` 将圆角矩形绘制映射到 `GuiDraw.drawRoundedRect`。

本项目集中在 `NetworkUiKit.Styles` 中创建：

```java
public static IDrawable rowBg(final int color) {
    return new Rectangle()
        .cornerRadius(Palette.BORDER_RADIUS_ROW)
        .verticalGradient(lighten(color, 0.08f), color);
}
```

旧 guide 已记录：`Rectangle` 不支持“圆角 + 边框”直接并存。需要描边时可用两层 drawable 或接受直角/无描边。

## Circle

本项目用 `Circle` 创建状态圆点：

```java
return new Circle()
    .color(color)
    .segments(12);
```

适合小状态标记。网络终端左侧网络列和网络选择行使用 `statusDotWidget(color)` 包装 `Circle`；Network Tab 行、设备行和部分健康提示仍使用 `TextWidget("■")`，原因是它随字体行高更容易对齐。可点击色板不要把可见色块放进按钮内部普通 `Flow`，应把点击目标和主要视觉背景挂在同一个 `ButtonWidget` 上。

## UITexture 与 9-slice

MUI2 支持纹理 drawable，包括可拉伸 9-slice。网络 UI 当前全是程序化矩形、渐变和文本，没有引入纹理资源。若未来要复刻 AE2 纹理边框，应优先查 `UITexture` / `AdaptableUITexture`，而不是把纹理坐标硬编码进 UI 类。

## Stencil 与裁剪

列表滚动由 `ListWidget` 处理，网络 UI 不直接用 stencil。自定义裁剪适合复杂预览、图表或局部滚动区域；普通列表不要重复造裁剪层。

## 自定义 IDrawable

本项目已有自定义 `ShadowDrawable`，用于面板阴影和背景组合。推荐模式：

- UI 类只传入颜色、半径、透明度。
- drawable 内部处理绘制细节。
- 不在状态对象里缓存可变 drawable；`NetworkUiKit.Styles` 每次返回新实例。

## GraphDrawable

适合折线图、历史曲线。网络终端统计页目前只显示当前能量和设备计数；如果未来加入能量历史曲线，优先使用 MUI2 自带图形 drawable 或封装自定义 `IDrawable`，不要在 `renderStatistics()` 里直接写 OpenGL。

# 04. 进阶 Widget 目录

## PagedWidget / PageButton

源码：`widgets/PagedWidget.java:14`。

适合：多个固定页之间切换，页面数量稳定，且希望框架维护当前页。
本项目取舍：网络终端没有用 `PagedWidget`，而是 `Panel currentPanel` + `renderContent()` switch。原因是每个面板切换前后都要主动发包、清空局部状态、控制 `panelFirstRender`，手写状态机更直观。

## Expandable

源码：`widgets/Expandable.java:38` 默认 `coverChildren()`，`widgets/Expandable.java:127` 管理展开状态。

适合：折叠面板、可展开详情。
风险：默认依赖子元素尺寸，和复杂相对尺寸组合容易放大 `coverChildren` 循环依赖。网络 UI 暂不使用。

## Dialog / ColorPickerDialog

源码：`widgets/ColorPickerDialog.java:64` 起可以看到颜色选择器用多个 `Flow.row().widthRel(1f).height(12)` 组合滑条。

本项目当前用固定 8 色按钮代替 color picker：

```java
row.child(new ButtonWidget<>()
    .width(28).height(24)
    .background(selected ? Styles.rowBg(color) : Styles.swatch(color))
    .disableHoverBackground()
    .onMousePressed(mb -> {
        selectedColor = color & 0xFFFFFF;
        renderContent();
        return true;
    }));
```

理由：GTNH 低分辨率 GUI 中，固定调色板更稳定，也能避免弹窗焦点和尺寸求解额外复杂度。

## DropdownWidget / Menu

源码：`widgets/menu/DropdownWidget.java:69` 可见展开时会 `scheduleResize()`；`widgets/menu/ContextMenuButton.java:32` 的注释建议搭配 `widthRel(1f)` 与 `coverChildrenHeight()`。

适合：选项较多的枚举。
本项目设置页目前用 cycle button 行为模拟选择安全等级；如果未来安全级别、排序字段显著增多，可以考虑接入 Dropdown。

## SortableListWidget

源码：`widgets/SortableListWidget.java:101`、`:134` 在拖拽变动时调用 `scheduleResize()`。

适合：可排序列表。
本项目网络列表只读排序，暂不使用拖拽排序。

## Slot 系列

MUI2 提供 ItemSlot、FluidSlot、Phantom 等槽位能力，适合 `ModularContainer` 路线。本项目网络 UI 不处理物品槽，常规 AE2 机器 GUI 仍走 AE2 原生 `GuiUpgradeable` / `GuiMEMonitorable`，所以不在网络 UI 中引入 Slot。

## Display 系列

Item / Fluid / Entity display 适合展示物品、流体、实体预览。网络状态页当前展示设备类型、坐标、能量和成员信息，使用文本和颜色即可。若未来健康页需要展示设备方块图标，应优先查 MUI2 Display 系列，而不是手写 OpenGL 绘制。

## RichText / ScrollingText / Transform / Schema / Value / Void

这些控件适合长文本、滚动文本、变换容器、声明式 schema、值容器和占位元素。本项目当前网络 UI 以短标签和列表行为主，暂未使用。若要展示帮助说明，优先考虑 `RichText` 或 `ScrollingText`，不要用多层 `TextWidget` 拼长段落。

# MUI2 色块视觉子节点抢占点击目标

## 错误现象

Network Terminal 的设置页和创建页中，颜色色块显示正常，但玩家点击色块后没有反应，颜色字段不会更新。

## 触发场景

在 ModularUI2 终端 GUI 中打开设置页或创建页，点击颜色色板中的任意色块。前一次只修正了 `padding(int, int)` 参数顺序后，色块仍然无法响应点击。

## 根本原因

`NetworkUiKit.colorSwatchRow`（`src/main/java/com/github/singularityme/client/ui/NetworkUiKit.java:1047`）旧实现把可见色块背景画在 `ButtonWidget` 的内部 `Flow` 上，而 `ButtonWidget` 本体背景是 `IDrawable.NONE`。

MUI2 的 hover/click 命中链会优先命中有可见背景的内部 `Flow`；该 `Flow` 不是 `Interactable`，并且会阻断事件继续到外层按钮，导致“视觉上看得到色块，但点击不到真正按钮”。

源码依据：`com/cleanroommc/modularui/widget/AbstractParentWidget.java:54` 的 `canHover()` 会把可见背景视为可 hover；`com/cleanroommc/modularui/screen/viewport/ModularGuiContext.java:409` 的 `getHoveredWidgets(...)` 在遇到不可 hover-through 的命中层后停止；`com/cleanroommc/modularui/screen/ModularPanel.java:391` 的点击分发会在不可 click-through 的层上截断后续控件。

## 修复方案

- 移除色块按钮内部的装饰 `Flow`。
- 将选中/未选中背景挂到 `ButtonWidget` 本体。
- 使用按钮本体的 `overlay(Styles.swatch(color))` 画内部 22px 色块，并用按钮 padding 形成内缩效果。
- 在 `NetworkUiKitTest.rendersColorSwatchesOnClickableButtonItself`（`src/test/java/com/github/singularityme/client/ui/NetworkUiKitTest.java:430`）中断言：色块按钮本体背景和 overlay 可见，且不再包含会抢占命中的子节点。

## 预防措施

在 MUI2 中制作可点击小控件时，点击目标和主要视觉背景应尽量落在同一个 `ButtonWidget` 上。不要把可见背景放到按钮内部的普通 `Flow` 子节点中；如果需要内缩视觉，优先使用按钮 padding + overlay，而不是子 Flow 嵌套。

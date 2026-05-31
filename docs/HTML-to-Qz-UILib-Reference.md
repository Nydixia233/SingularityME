# HTML → Qz-UILib API 对照文档

> 帮助 Web 前端开发者把 HTML/CSS 概念映射到 Qz-UILib 的 Java API 调用。
>
> **版本**：基于 Qz-UILib 4.1.3-LTS
> **最后更新**：2026-05-31

---

## 目录

1. [概述：Qz-UILib 与浏览器的核心差异](#1-概述qz-uelib-与浏览器的核心差异)
2. [标签对照表](#2-标签对照表)
3. [属性对照表](#3-属性对照表)
4. [CSS 样式转换规则](#4-css-样式转换规则)
   - [4a. 布局属性](#4a-布局属性-uistylechangeimpactlayout)
   - [4b. 视觉属性](#4b-视觉属性-uistylechangeimpactpaint)
   - [4c. 文字属性](#4c-文字属性)
   - [4d. 动画与过渡](#4d-动画与过渡)
   - [4e. 不支持的 CSS 特性](#4e-不支持的-css-特性)
5. [选择器对照表](#5-选择器对照表)
6. [事件系统对照](#6-事件系统对照)
7. [特殊组件转换](#7-特殊组件转换)
8. [文本内容模式](#8-文本内容模式)
9. [常见陷阱与最佳实践](#9-常见陷阱与最佳实践)
10. [快速检索索引](#10-快速检索索引)

---

## 1. 概述：Qz-UILib 与浏览器的核心差异

### 三个关键差异

| 维度 | 浏览器 | Qz-UILib |
|------|--------|----------|
| **标记语言** | HTML 字符串由解析器构建 DOM | 通过 Java API（`document.div()`, `element.appendChild()`）构建 UI 树 |
| **脚本** | JavaScript 操作 DOM、注册事件 | Java lambda 表达式注册事件回调 |
| **样式** | CSS 文件或 `<style>` 标签 | `element.style().setXxx(...)` 方法链 或 `UiStyleSheet` 对象 |

### 心智模型

```
浏览器：     HTML 字符串  →  解析  →  DOM 树  →  CSS 选择器匹配  →  布局  →  绘制
Qz-UILib：   Java API     →  直接构建 UI 树  →  UiSelector 匹配  →  布局  →  绘制
```

Qz-UILib **不解析** HTML/CSS 字符串（在本地文档场景），而是提供了一套与 DOM/CSSOM 语义对等的 Java 类型系统。开发者需要把"脑子里的 HTML/CSS"翻译成方法调用。

---

## 2. 标签对照表

### 容器元素

| HTML 标签 | Qz-UILib 等效 API | 说明 |
|-----------|-------------------|------|
| `<div>` | `document.div()` | 通用块容器，默认 `display: block` |
| `<span>` | `document.span()` | 通用行内容器，无默认 display |
| `<p>` | `document.p()` | 段落 |

```java
// HTML: <div class="container"><span>Hello</span></div>
ElementNode div = document.div();
div.setClassName("container");
ElementNode span = document.span();
span.appendChild(document.text("Hello"));
div.appendChild(span);
```

### 标题元素

| HTML 标签 | Qz-UILib 等效 API | 说明 |
|-----------|-------------------|------|
| `<h1>`–`<h6>` | `document.h1()` … `document.h6()` | 标题（**语义标签，无默认字体大小/粗细**） |

> ⚠️ 与浏览器不同，`<h1>` 不会自动变大变粗，需手动设置 `fontWeight` 等样式。

### 列表元素

| HTML 标签 | Qz-UILib 等效 API | 说明 |
|-----------|-------------------|------|
| `<ul>` | `document.ul()` | 无序列表 |
| `<ol>` | `document.ol()` | 有序列表 |
| `<li>` | `document.li()` | 列表项 |

### 表格元素

| HTML 标签 | Qz-UILib 等效 API | 说明 |
|-----------|-------------------|------|
| `<table>` | `document.table()` | 表格容器 |
| `<thead>` | `document.thead()` | 表头 |
| `<tbody>` | `document.tbody()` | 表体 |
| `<tfoot>` | `document.tfoot()` | 表尾 |
| `<tr>` | `document.tr()` | 表行 |
| `<th>` | `document.th()` | 表头单元格 |
| `<td>` | `document.td()` | 表数据单元格 |

### 表单控件

| HTML 标签 | Qz-UILib 等效 API | 说明 |
|-----------|-------------------|------|
| `<button>` | `document.button()` | 按钮。框架会自动挂接 `DocumentButtonControl` |
| `<a>` | `document.a()` | 链接。有 `href` 属性时自动可聚焦，角色为 `link` |
| `<input>` | `document.input()` | 文本输入。框架会自动挂接 `DocumentTextInputControl` |
| `<textarea>` | `document.textarea()` | 多行文本。注意：**无软换行，长行会横向滚动** |
| `<select>` | `document.select()` | 下拉选择 |
| `<option>` | `document.option()` | 选择项 |

```java
// HTML: <button id="submit" class="primary">提交</button>
ElementNode button = document.button();
button.setId("submit");
button.setClassName("primary");
button.appendChild(document.text("提交"));
button.addEventListener("click", e -> {
    // 处理点击
});
```

```java
// HTML: <a href="https://example.com">链接</a>
ElementNode link = document.a();
link.setAttribute("href", "https://example.com");
link.appendChild(document.text("链接"));
```

### 媒体元素

| HTML 标签 | Qz-UILib 等效 API | 说明 |
|-----------|-------------------|------|
| `<img>` | `document.img()` | 图片。支持 ResourceLocation 和 HTTP URL |
| `<video>/<audio>` | **不支持** | 无多媒体播放能力 |

```java
// HTML: <img src="textures/gui/icon.png" alt="图标" width="16" height="16">
ElementNode img = document.img();
img.setAttribute("src", "minecraft:textures/gui/icon.png");  // ResourceLocation 格式
img.setAttribute("alt", "图标");
img.setAttribute("width", "16");
img.setAttribute("height", "16");
```

```java
// HTTP 远程图片
ElementNode remoteImg = document.img();
remoteImg.setAttribute("src", "https://example.com/image.png");
```

### 不支持的 HTML 元素

| HTML 标签 | 原因 |
|-----------|------|
| `<iframe>` | 无外部内容嵌入机制 |
| `<script>` | 无 JS 执行环境 |
| `<style>` | 用 `UiStyleSheet` 替代 |
| `<form>` | 无表单提交机制 |
| `<video>/<audio>` | 无多媒体播放管线 |
| `<canvas>` | 无 2D 绘图上下文 |
| `<svg>` | 无矢量图形引擎 |

---

## 3. 属性对照表

| HTML 属性 | Qz-UILib 等效 | 说明 |
|-----------|---------------|------|
| `id="foo"` | `element.setId("foo")` 或 `setAttribute("id", "foo")` | 两者等价；支持 `document.getElementById("foo")` 查询 |
| `class="a b"` | `element.setClassName("a b")` 或 `element.getClassList().add("a")` | 修改类名自动触发样式重算 |
| `style="..."` | `element.style().setXxx(...)` | 无字符串解析，全部方法调用 |
| `href="..."` | `setAttribute("href", "...")` | 仅 `<a>` 有效；有 href 时自动可聚焦，角色变为 `link` |
| `src="..."` | `setAttribute("src", "...")` | 仅 `<img>` 有效；支持 `namespace:path` 或 HTTP URL |
| `alt="..."` | `setAttribute("alt", "...")` | 图片加载失败时显示替代文本 |
| `width` / `height` | `setAttribute("width", "16")` | `<img>` 上设置固有尺寸；其他元素用样式 |
| `disabled` | `setAttribute("disabled", "")` | 仅 button/input/textarea/select 有效 |
| `placeholder` | `setAttribute("placeholder", "...")` | 仅 input/textarea 有效 |
| `type` | `setAttribute("type", "checkbox")` | `<input>` 类型（影响语义角色） |
| `tabindex` | `setAttribute("tabindex", "0")` | 影响 Tab 键焦点顺序；`-1` 表示可编程聚焦但不入 Tab 序 |
| `role` | `setAttribute("role", "button")` | ARIA 语义角色 |
| `aria-label` | `setAttribute("aria-label", "...")` | 可访问名称 |
| `aria-hidden` | `setAttribute("aria-hidden", "true")` | 从语义树隐藏 |
| `aria-disabled` | `setAttribute("aria-disabled", "true")` | ARIA 禁用状态 |
| `aria-readonly` | `setAttribute("aria-readonly", "true")` | ARIA 只读 |
| `aria-required` | `setAttribute("aria-required", "true")` | ARIA 必填 |
| `aria-multiline` | `setAttribute("aria-multiline", "true")` | ARIA 多行 |
| `onclick="..."` | **不支持** | 用 `addEventListener("click", handler)` 替代 |
| `onload` / `onerror` | **不支持** | 无 JS 事件属性 |
| `data-*` | `setAttribute("data-foo", "bar")` | 支持自定义数据属性 |

### 常用属性操作示例

```java
// ID 和 class
element.setId("my-element");
element.setClassName("card highlighted");

// 属性
element.setAttribute("disabled", "");
element.setAttribute("placeholder", "请输入...");
element.setAttribute("tabindex", "0");
element.setAttribute("aria-label", "关闭按钮");

// 读取属性
String href = element.getAttribute("href");
boolean hasDisabled = element.hasAttribute("disabled");

// 移除属性
element.removeAttribute("disabled");
```

---

## 4. CSS 样式转换规则

样式通过 `element.style()` 获取 `UiStyleDeclaration` 对象后链式调用 setter。

```java
element.style()
    .setWidth(UiStyleLength.px(200))
    .setHeight(UiStyleLength.px(100))
    .setBackgroundColor(0xFF333333)
    .setBorderRadius(UiStyleLength.px(4));
```

### 颜色格式

Qz-UILib 使用 **ARGB int**（`0xAARRGGBB`），不是 CSS 字符串。

| CSS 写法 | Qz-UILib 写法 |
|----------|---------------|
| `#333333` | `0xFF333333` |
| `rgba(255,0,0,0.5)` | `0x80FF0000` |
| `red` | `0xFFFF0000` |
| `transparent` | `0x00000000` |

### 长度单位

通过 `UiStyleLength` 工厂方法创建。**仅支持 `px` 和 `%`**，无 `em/rem/vw/vh`。

| 写法 | 含义 |
|------|------|
| `UiStyleLength.px(16)` | 16 像素 |
| `UiStyleLength.percent(50)` | 50%（相对包含块） |
| `UiStyleLength.auto()` | auto（由布局算法决定） |
| `UiStyleLength.zero()` | 0 |

### 4a. 布局属性（`UiStyleChangeImpact.LAYOUT`）

修改这些属性触发文档**重新布局**。

| CSS 属性 | Java 方法 | 支持值 | 备注 |
|-----------|-----------|--------|------|
| `display` | `setDisplay(...)` | `UiDisplay.FLEX`, `BLOCK`, `TABLE`, `INLINE`, `NONE` | 无 `grid` |
| `position` | `setPosition(...)` | `UiPosition.RELATIVE`, `ABSOLUTE`, `FIXED`, `STICKY` | 全支持 |
| `width` | `setWidth(UiStyleLength)` | px / % / auto | |
| `height` | `setHeight(UiStyleLength)` | px / % / auto | |
| `min-width` | `setMinWidth(UiStyleLength)` | px / % | |
| `max-width` | `setMaxWidth(UiStyleLength)` | px / % / auto | |
| `min-height` | `setMinHeight(UiStyleLength)` | px / % | |
| `max-height` | `setMaxHeight(UiStyleLength)` | px / % / auto | |
| `top` | `setTop(UiStyleLength)` | px / % | 需 `position` 非 static |
| `right` | `setRight(UiStyleLength)` | px / % | 需 `position` 非 static |
| `bottom` | `setBottom(UiStyleLength)` | px / % | 需 `position` 非 static |
| `left` | `setLeft(UiStyleLength)` | px / % | 需 `position` 非 static |
| `margin` | `setMargin(UiStyleLength)` | px / % | 四边相同 |
| `margin`（四边独立） | `setMargin(UiStyleInsets)` 或 `setMarginTop/Right/Bottom/Left(...)` | px / % | 支持单边设置 |
| `padding` | `setPadding(UiStyleLength)` | px / % | 四边相同 |
| `padding`（四边独立） | `setPadding(UiStyleInsets)` 或 `setPaddingTop/Right/Bottom/Left(...)` | px / % | 支持单边设置 |
| `z-index` | `setZIndex(int)` | 整数 | |
| `box-sizing` | `setBoxSizing(...)` | `UiBoxSizing.CONTENT_BOX`, `BORDER_BOX` | |
| `overflow-x` | `setOverflowX(...)` | `UiOverflow.VISIBLE`, `HIDDEN`, `SCROLL`, `AUTO` | |
| `overflow-y` | `setOverflowY(...)` | `UiOverflow.VISIBLE`, `HIDDEN`, `SCROLL`, `AUTO` | |
| `flex-direction` | `setFlexDirection(...)` | `UiFlexDirection.ROW`, `COLUMN`, `ROW_REVERSE`, `COLUMN_REVERSE` | |
| `flex-wrap` | `setFlexWrap(...)` | `UiFlexWrap.NOWRAP`, `WRAP`, `WRAP_REVERSE` | |
| `justify-content` | `setJustifyContent(...)` | `UiJustifyContent.FLEX_START`, `CENTER`, `FLEX_END`, `SPACE_BETWEEN`, `SPACE_AROUND`, `SPACE_EVENLY` | |
| `align-items` | `setAlignItems(...)` | `UiAlignItems.FLEX_START`, `CENTER`, `FLEX_END`, `STRETCH`, `BASELINE` | |
| `align-self` | `setAlignSelf(...)` | 同 `align-items` | 覆盖父容器对齐 |
| `flex-grow` | `setFlexGrow(float)` | ≥0 数值 | |
| `flex-shrink` | `setFlexShrink(float)` | ≥0 数值 | |
| `flex-basis` | `setFlexBasis(UiStyleLength)` | px / % / auto | |
| `order` | `setOrder(int)` | 整数 | 默认 0，越小越前 |
| `gap` | `setGap(UiStyleLength)` | px | 同时设置 row + column gap |
| `row-gap` | `setRowGap(UiStyleLength)` | px | |
| `column-gap` | `setColumnGap(UiStyleLength)` | px | |
| `vertical-align` | `setVerticalAlign(...)` | 竖直对齐 | |
| `aspect-ratio` | `setAspectRatio(float)` | 数值 | 宽高比约束 |
| `border-collapse` | `setBorderCollapse(...)` | 表格边框合并 | |

#### 不支持布局属性

| CSS 属性 | 原因 |
|-----------|------|
| `float` | 用 flex 替代 |
| `grid-*` | 无 CSS Grid 布局 |
| `gap` 的 row+column 不同值 | `setGap()` 只能设同一值；需分别调用 `setRowGap`/`setColumnGap` |

### 4b. 视觉属性（`UiStyleChangeImpact.PAINT`）

修改这些属性只触发**重绘**，不重新布局。

| CSS 属性 | Java 方法 | 支持值 | 备注 |
|-----------|-----------|--------|------|
| `background-color` | `setBackgroundColor(int)` | ARGB int（如 `0xFF333333`） | 无 `rgba()` 字符串 |
| `background-image` | `setBackgroundImage(UiBackgroundImage)` | 单张图片 | 无渐变、无多背景 |
| `color`（文字颜色） | `setTextColor(int)` | ARGB int | **继承属性** |
| `border-color` | `setBorderColor(int)` | ARGB int | 四边统色 |
| `border-color`（四边独立） | `setBorderColors(UiBorderColors)` | ARGB int 数组 | |
| `border-width` | `setBorderWidth(UiStyleLength)` | px | 四边统一 |
| `border-width`（四边独立） | `setBorderWidthSides(UiStyleInsets)` | px | |
| `border-style` | `setBorderStyle(...)` | `UiBorderStyle.SOLID`, `DASHED`, `DOTTED`, `DOUBLE`, `NONE` | |
| `border-radius` | `setBorderRadius(UiStyleLength)` | px | 四角统一 |
| `border-radius`（四角独立） | `setBorderRadiusCorners(UiBorderRadius)` | px | |
| `opacity` | `setOpacity(float)` | 0.0–1.0 | |
| `box-shadow` | `setBoxShadow(UiBoxShadow)` | 单值 | **不支持多重阴影** |
| `text-shadow` | `setTextShadow(UiTextShadow)` | 单值 | **不支持多重阴影** |
| `outline` | `setOutline(UiOutline)` | 轮廓样式 | |
| `visibility` | `setVisibility(...)` | `UiVisibility.VISIBLE`, `HIDDEN`, `COLLAPSE` | `hidden` 保留空间但不可见、不响应命中 |
| `cursor` | `setCursor(...)` | `UiCursor.DEFAULT`, `POINTER`, `TEXT`, `MOVE`, `NOT_ALLOWED`, `WAIT`, `CROSSHAIR`, `NONE` 等 | 无自定义图片光标 |
| `object-fit` | `setObjectFit(...)` | `UiObjectFit.FILL`, `CONTAIN`, `COVER`, `SCALE_DOWN`, `NONE` | 仅 `<img>` 有效 |
| `backdrop-filter: blur()` | `setBackdropBlurRadius(UiStyleLength)` | px | 仅 blur；无其他 filter |
| `backdrop-filter: saturate()` | `setBackdropSaturation(float)` | ≥0 数值 | |
| `pointer-events` | `setPointerEvents(...)` | `UiPointerEvents.AUTO`, `NONE` | |
| `transform` | `setTransform(UiTransform)` | translate / scale / rotate | **无 skew、无 3D 变换** |
| `scrollbar-color` | `setScrollbarColor(UiScrollbarColor)` | 滚动条颜色 | **继承属性** |
| `scrollbar-width` | `setScrollbarWidth(...)` | 滚动条宽度 | |
| `list-style-type` | `setListStyleType(...)` | 列表标记样式 | **继承属性** |

### 4c. 文字属性

| CSS 属性 | Java 方法 | 支持值 | 备注 |
|-----------|-----------|--------|------|
| `font-weight` | `setFontWeight(...)` | `UiFontWeight.NORMAL`, `BOLD` 或数值 | **继承属性** |
| `font-style` | `setFontStyle(...)` | `UiFontStyle.NORMAL`, `ITALIC` | **继承属性** |
| `font-family` | **不支持** | — | 使用 Minecraft 默认字体 |
| `font-size` | **不支持** | — | 字体大小由 Minecraft 渲染管线决定 |
| `text-align` | `setTextAlign(...)` | `UiTextAlign.LEFT`, `CENTER`, `RIGHT`, `JUSTIFY` | **继承属性** |
| `text-decoration` | `setTextDecoration(...)` | `UiTextDecoration.NONE`, `UNDERLINE`, `OVERLINE`, `LINE_THROUGH` | |
| `text-overflow` | `setTextOverflow(...)` | `UiTextOverflow.CLIP`, `ELLIPSIS` | 需配合 `overflow: hidden` |
| `white-space` | `setWhiteSpace(...)` | `UiWhiteSpace.NORMAL`, `NOWRAP`, `PRE`, `PRE_WRAP`, `PRE_LINE` | **继承属性** |
| `line-height` | `setLineHeight(UiStyleLength)` | px / auto | **继承属性**；auto 跟随字体默认 |
| `letter-spacing` | `setLetterSpacing(UiStyleLength)` | px | **继承属性** |
| `text-transform` | `setTextTransform(...)` | `UiTextTransform.NONE`, `UPPERCASE`, `LOWERCASE`, `CAPITALIZE` | **继承属性** |
| `text-indent` | `setTextIndent(UiStyleLength)` | px | **继承属性** |
| `word-break` | `setWordBreak(...)` | `UiWordBreak.NORMAL`, `BREAK_ALL`, `KEEP_ALL` | **继承属性** |
| `overflow-wrap` | `setOverflowWrap(...)` | `UiOverflowWrap.NORMAL`, `BREAK_WORD` | **继承属性** |

### 4d. 动画与过渡

| CSS 属性 | Java 方法 | 备注 |
|-----------|-----------|------|
| `transition` | `setTransition(property, durationMs)` | 便捷方法：单属性 + 时长 |
| `transition-property` | `setTransitionProperties(...)` | 过渡属性列表 |
| `transition-duration` | `setTransitionDurationMillis(long)` | 毫秒 |
| `transition-delay` | `setTransitionDelayMillis(long)` | 毫秒 |
| `transition-timing-function` | `setTransitionTimingFunction(...)` | 缓动函数 |
| `transition`（分属性） | `setTransitions(DocumentTransitionSpec...)` | per-property 四元组 |
| `animation` | `setAnimation(name, durationMs)` | 便捷方法 |
| `animation-name` | `setAnimationName(String)` | 对应 `registerKeyframes` 的名称 |
| `animation-duration` | `setAnimationDurationMillis(long)` | 毫秒 |
| `animation-delay` | `setAnimationDelayMillis(long)` | 毫秒 |
| `animation-iteration-count` | `setAnimationIterationCount(int)` | `0` = infinite |
| `animation-fill-mode` | `setAnimationFillMode(...)` | |
| `animation-timing-function` | `setAnimationTimingFunction(...)` | 缓动函数 |
| `animation-direction` | `setAnimationDirection(...)` | 播放方向 |
| `@keyframes` | `document.registerKeyframes(DocumentKeyframes)` | Java API 定义关键帧 |
| `transform` | `setTransform(UiTransform)` | translate / scale / rotate |

```java
// 定义 keyframes
DocumentKeyframes fadeIn = DocumentKeyframes.create("fadeIn")
    .addKeyframe(0.0f, new DocumentKeyframes.KeyframeState()
        .setOpacity(0.0f))
    .addKeyframe(1.0f, new DocumentKeyframes.KeyframeState()
        .setOpacity(1.0f));
document.registerKeyframes(fadeIn);

// 使用动画
element.style()
    .setAnimationName("fadeIn")
    .setAnimationDurationMillis(300)
    .setAnimationFillMode(DocumentAnimationFillMode.FORWARDS);
```

### 4e. 不支持的 CSS 特性

| CSS 特性 | 原因 |
|-----------|------|
| `@media` 查询 | 无响应式设计支持 |
| `@font-face` | 使用 Minecraft 字体管线 |
| CSS 变量 `var(--x)` | 通过 `UiStyleVariables` Java API 替代 |
| CSS 渐变 `linear-gradient()` / `radial-gradient()` | 不支持 |
| 多重 `box-shadow` | 仅支持单值 |
| 多重 `text-shadow` | 仅支持单值 |
| 属性选择器 `[attr=val]` | 选择器不支持 |
| 兄弟选择器 `+` / `~` | 选择器不支持 |
| `,` 分组选择器 | 选择器不支持 |
| 自定义属性 `@property` | 不支持 |
| `@supports` | 不支持 |
| `@container` | 不支持 |
| `::marker` 伪元素 | 仅支持 `::before` / `::after` |
| `filter`（除 blur） | 仅支持 `backdrop-filter: blur()` |
| `clip-path` | 不支持 |
| `mix-blend-mode` | 不支持 |
| `3D transform` | 仅 2D translate / scale / rotate |

---

## 5. 选择器对照表

Qz-UILib 的 `UiSelector` 支持 CSS 选择器的一个子集。可通过 `UiSelector.parse("selector")` 从字符串解析。

| CSS 选择器 | Qz-UILib 支持 | 示例 | 说明 |
|------------|---------------|------|------|
| 标签选择器 `div` | ✅ | `UiSelector.tag("div")` 或 `UiSelector.parse("div")` | |
| 类选择器 `.foo` | ✅ | `UiSelector.className("foo")` 或 `UiSelector.parse(".my-class")` | |
| ID 选择器 `#foo` | ✅ | `UiSelector.id("foo")` 或 `UiSelector.parse("#my-id")` | |
| 通配符 `*` | ✅ | `UiSelector.parse("*")` | |
| 后代选择器 `A B` | ✅ | `UiSelector.parse("div span")` | 祖先-后代关系 |
| 子选择器 `A > B` | ✅ | `UiSelector.parse("div > span")` | 直接父子关系 |
| 伪类 `:hover` | ✅ | `UiSelector.parse("button:hover")` | |
| `:focus` | ✅ | `UiSelector.parse("input:focus")` | |
| `:focus-visible` | ✅ | `UiSelector.parse("button:focus-visible")` | 键盘导航获得焦点 |
| `:active` | ✅ | `UiSelector.parse("button:active")` | mousedown→mouseup 间 |
| `:disabled` | ✅ | `UiSelector.parse("input:disabled")` | |
| `:first-child` | ✅ | `UiSelector.parse("li:first-child")` | 结构伪类 |
| `:last-child` | ✅ | `UiSelector.parse("li:last-child")` | 结构伪类 |
| `:nth-child(An+B)` | ✅ | `UiSelector.parse("li:nth-child(2n+1)")` | 结构伪类（含 `odd`/`even`） |
| 伪元素 `::before` | ✅ | `UiSelector.parse("div::before")` | 仅文本内容 |
| `::after` | ✅ | `UiSelector.parse("div::after")` | 仅文本内容 |
| 属性选择器 `[attr]` | ❌ | — | |
| 属性值选择器 `[attr=val]` | ❌ | — | |
| 兄弟选择器 `+` / `~` | ❌ | — | |
| 分组选择器 `A, B` | ❌ | — | 需分别 `addRule` |

### 特异性计算

按 CSS 标准计算：`(ID 数, 类/伪类数, 标签数)`。

```
#header .nav a:hover  →  (1, 2, 1)
div > span            →  (0, 0, 2)
*                     →  (0, 0, 0)
```

### 样式规则注册

```java
UiStyleSheet sheet = new UiStyleSheet();
sheet.addRule(UiSelector.parse(".card"), declaration -> {
    declaration
        .setBackgroundColor(0xFF444444)
        .setBorderRadius(UiStyleLength.px(8))
        .setPadding(UiStyleLength.px(12));
});
sheet.addRule(UiSelector.parse(".card:hover"), declaration -> {
    declaration.setBackgroundColor(0xFF555555);
});
document.addStyleSheet(sheet);
```

### 选择器不支持逗号分组的 workaround

```java
// CSS: .card, .panel { background: #333; }
// 错误：UiSelector.parse(".card, .panel") ❌
// 正确：分别注册
Consumer<UiStyleDeclaration> style = d -> d.setBackgroundColor(0xFF333333);
sheet.addRule(UiSelector.parse(".card"), style);
sheet.addRule(UiSelector.parse(".panel"), style);
```

---

## 6. 事件系统对照

Qz-UILib 的事件系统模拟 DOM Level 2 事件模型，支持冒泡/捕获阶段。

| HTML/JS 写法 | Qz-UILib Java 写法 |
|---------------|---------------------|
| `el.onclick = fn` | **不支持属性事件**；用 `addEventListener` |
| `el.addEventListener("click", fn)` | `element.addEventListener("click", e -> { ... })` |
| `el.addEventListener("click", fn, true)` | `element.addEventListener("click", handler, true)`（捕获阶段） |
| `e.stopPropagation()` | `event.stopPropagation()` |
| `e.preventDefault()` | `event.preventDefault()` |
| `e.target` | `event.getTarget()` |
| `e.currentTarget` | `event.getCurrentTarget()` |

### 支持的事件类型

| 事件类别 | 事件名 | 说明 |
|----------|--------|------|
| 鼠标 | `click` | 点击 |
| 鼠标 | `mousedown`, `mouseup` | 按下/释放 |
| 鼠标 | `mousemove` | 移动 |
| 鼠标 | `mouseenter`, `mouseleave` | 进入/离开（不冒泡） |
| 鼠标 | `mouseover`, `mouseout` | 进入/离开（冒泡） |
| 鼠标 | `dblclick` | 双击 |
| 键盘 | `keydown`, `keyup` | 键盘按下/释放 |
| 焦点 | `focus`, `blur` | 获得/失去焦点（不冒泡） |
| 焦点 | `focusin`, `focusout` | 获得/失去焦点（冒泡） |
| 变更 | `change` | 值变更 |
| 拖拽 | `dragstart`, `drag`, `dragend` | 拖拽生命周期 |
| 动画 | `animationstart`, `animationend` | 动画开始/结束 |

### 典型事件处理示例

```java
// 点击事件
element.addEventListener("click", event -> {
    System.out.println("clicked!");
});

// 键盘事件
element.addEventListener("keydown", event -> {
    if (event.getKeyCode() == Keyboard.KEY_RETURN) {
        // 处理回车
    }
});

// 拖拽
element.addEventListener("dragstart", event -> {
    event.setDragData("text/plain", "拖拽数据");
});
```

---

## 7. 特殊组件转换

Qz-UILib 内置了一些原生控件，它们由框架自动挂接到对应标签上。

| HTML 写法 | Qz-UILib 等效 | 说明 |
|-----------|---------------|------|
| `<input type="text">` | `document.input()` → 自动挂接 `DocumentTextInputControl` | 含光标闪烁、placeholder、maxlength |
| `<button>` | `document.button()` → 自动挂接 `DocumentButtonControl` | 含键盘激活（Enter/Space）、焦点环 |
| `<select>` | `document.select()` → 自动挂接 `DocumentSelectControl` | 下拉弹出选择 |
| `<input type="checkbox">` | `DocumentCheckboxControl` | 复选框 |
| `<input type="radio">` | `DocumentRadioGroupControl` | 单选组 |
| `<input type="range">` | `DocumentSliderControl` | 滑块 |
| `<textarea>` | `document.textarea()` → 自动挂接 `DocumentTextAreaControl` | 多行文本（**无软换行**） |
| `<table>` | `DocumentTableControl` | 表格组件 |
| Minecraft 物品栏格子 | `DocumentInventorySlotGridControl` | 无 HTML 等效 |
| Tooltip | `DocumentTooltipOverlayControl` | 无 HTML 等效 |
| `<img>` | `document.img()` + `DocumentImageElementSupport` | 支持 ResourceLocation 和 HTTP URL |

### 文本框示例

```java
ElementNode input = document.input();
input.setAttribute("placeholder", "请输入名称...");
input.setAttribute("maxlength", "32");
input.style()
    .setWidth(UiStyleLength.px(200))
    .setBorderColor(0xFF666666)
    .setBorderWidth(UiStyleLength.px(1));
input.addEventListener("change", event -> {
    String value = input.getAttribute("value");
    System.out.println("输入值：" + value);
});
```

---

## 8. 文本内容模式

Qz-UILib 特有的文本处理机制，无 HTML 等效。

| 模式 | API | 说明 |
|------|-----|------|
| 原始文本（Raw） | `document.rawText("Hello")` | 不解析 `§` Minecraft 格式码 |
| Minecraft 格式文本 | `document.minecraftText("§aGreen §lBold")` | 解析 `§` 颜色/样式码 |
| 默认模式 | `document.text("...")` | 由 `setDefaultTextContentMode()` 决定 |

```java
// 设置文档级默认
document.useMinecraftTextByDefault();
document.useRawTextByDefault();

// 创建特定模式的文本节点
ElementNode span = document.span();
span.appendChild(document.rawText("普通文本"));        // 不解析 §
span.appendChild(document.minecraftText("§c红色文字")); // 解析 §
```

---

## 9. 常见陷阱与最佳实践

### 9.1 颜色陷阱

❌ **错误**：
```java
element.style().setBackgroundColor("#333333");  // 编译错误：需要 int
```

✅ **正确**：
```java
element.style().setBackgroundColor(0xFF333333);  // ARGB int
```

> 提示：`0xFF______` 前缀表示完全不透明。半透明用 `0x80______`（50%）。

### 9.2 长度单位陷阱

❌ **错误**：
```java
element.style().setWidth(UiStyleLength.px(50%));  // 编译错误
element.style().setWidth(UiStyleLength.em(1.5));   // 不存在此方法
```

✅ **正确**：
```java
element.style().setWidth(UiStyleLength.percent(50));  // 50%
element.style().setWidth(UiStyleLength.px(100));      // 100px
element.style().setWidth(UiStyleLength.auto());       // auto
```

### 9.3 标题无默认样式

❌ **期望** `<h1>` 自动又大又粗：
```java
ElementNode title = document.h1();
title.appendChild(document.text("标题"));  // 看起来和普通文本一样
```

✅ **正确**：手动设置样式
```java
ElementNode title = document.h1();
title.appendChild(document.text("标题"));
title.style()
    .setFontWeight(UiFontWeight.BOLD)
    .setFontSize(...); // 注意：font-size 在 Qz-UILib 中不支持，字体大小由 Minecraft 决定
```

### 9.4 选择器不支持逗号分组

❌ **错误**：
```java
sheet.addRule(UiSelector.parse("div, span, p"), ...);  // 抛出异常
```

✅ **正确**：
```java
Consumer<UiStyleDeclaration> common = d -> d.setTextColor(0xFFFFFFFF);
sheet.addRule(UiSelector.parse("div"), common);
sheet.addRule(UiSelector.parse("span"), common);
sheet.addRule(UiSelector.parse("p"), common);
```

### 9.5 textarea 无软换行

`<textarea>` 在 Qz-UILib 中不会自动换行，长行会横向滚动。如需换行，业务侧自行处理。

### 9.6 图片 src 格式

- **本地纹理**：`namespace:path` 格式，如 `minecraft:textures/gui/icons.png`
- **远程图片**：HTTP/HTTPS URL，如 `https://example.com/image.png`
- **加载状态**：通过 `data-img-load-revision` 属性监听重载

### 9.7 事件注册没有 HTML 属性方式

❌ **不支持**：
```java
element.setAttribute("onclick", "handleClick()");  // 无效果
```

✅ **正确**：
```java
element.addEventListener("click", e -> handleClick());
```

### 9.8 flex 子元素尺寸约束

在 Qz-UILib 的 flex 布局中，`maxWidth`/`minWidth`/`flex-basis` 的交互与浏览器有细微差异，建议：
- 显式设置 `flex-basis` 而非依赖 auto 推算
- 若列 flex 子元素高度异常，尝试设 `flex-basis: auto()` 或 `height: auto()`

### 9.9 滚动容器

创建可滚动容器时，推荐使用 `height: auto()` 而非 `height: px(0)`：

```java
scrollBox.style()
    .setOverflowY(UiOverflow.AUTO)
    .setHeight(UiStyleLength.auto());  // 不要用 px(0)
```

### 9.10 `font-family` 和 `font-size` 无效

字体由 Minecraft 的原版字体渲染管线决定。尝试设置 `font-family` 不会有任何效果。所有文字将使用 Minecraft 默认的位图字体渲染。

---

## 10. 快速检索索引

### HTML 标签索引

| 标签 | 章节 |
|------|------|
| `<a>` | [§2 标签对照表](#2-标签对照表), [§3 属性对照表](#3-属性对照表) |
| `<button>` | [§2 标签对照表](#2-标签对照表), [§7 特殊组件](#7-特殊组件转换) |
| `<div>` | [§2 标签对照表](#2-标签对照表) |
| `<h1>`–`<h6>` | [§2 标签对照表](#2-标签对照表), [§9.3 标题无默认样式](#93-标题无默认样式) |
| `<iframe>` | [§2 不支持的 HTML 元素](#不支持的-html-元素) |
| `<img>` | [§2 标签对照表](#2-标签对照表), [§9.6 图片 src 格式](#96-图片-src-格式) |
| `<input>` | [§2 标签对照表](#2-标签对照表), [§7 特殊组件](#7-特殊组件转换) |
| `<li>` / `<ol>` / `<ul>` | [§2 标签对照表](#2-标签对照表) |
| `<option>` | [§2 标签对照表](#2-标签对照表) |
| `<p>` | [§2 标签对照表](#2-标签对照表) |
| `<script>` | [§2 不支持的 HTML 元素](#不支持的-html-元素) |
| `<select>` | [§2 标签对照表](#2-标签对照表), [§7 特殊组件](#7-特殊组件转换) |
| `<span>` | [§2 标签对照表](#2-标签对照表) |
| `<style>` | [§2 不支持的 HTML 元素](#不支持的-html-元素), [§5 选择器对照表](#5-选择器对照表) |
| `<table>` / `<tr>` / `<td>` / `<th>` | [§2 标签对照表](#2-标签对照表), [§7 特殊组件](#7-特殊组件转换) |
| `<textarea>` | [§2 标签对照表](#2-标签对照表), [§9.5 textarea 无软换行](#95-textarea-无软换行) |
| `<video>` / `<audio>` | [§2 不支持的 HTML 元素](#不支持的-html-元素) |

### CSS 属性索引

| CSS 属性 | 章节 |
|----------|------|
| `align-items` | [§4a 布局属性](#4a-布局属性-uistylechangeimpactlayout) |
| `align-self` | [§4a 布局属性](#4a-布局属性-uistylechangeimpactlayout) |
| `animation` / `animation-*` | [§4d 动画与过渡](#4d-动画与过渡) |
| `background-color` | [§4b 视觉属性](#4b-视觉属性-uistylechangeimpactpaint) |
| `background-image` | [§4b 视觉属性](#4b-视觉属性-uistylechangeimpactpaint) |
| `backdrop-filter` | [§4b 视觉属性](#4b-视觉属性-uistylechangeimpactpaint) |
| `border-*` | [§4b 视觉属性](#4b-视觉属性-uistylechangeimpactpaint) |
| `border-radius` | [§4b 视觉属性](#4b-视觉属性-uistylechangeimpactpaint) |
| `box-shadow` | [§4b 视觉属性](#4b-视觉属性-uistylechangeimpactpaint) |
| `box-sizing` | [§4a 布局属性](#4a-布局属性-uistylechangeimpactlayout) |
| `color` | [§4b 视觉属性](#4b-视觉属性-uistylechangeimpactpaint) |
| `cursor` | [§4b 视觉属性](#4b-视觉属性-uistylechangeimpactpaint) |
| `display` | [§4a 布局属性](#4a-布局属性-uistylechangeimpactlayout) |
| `flex-*` | [§4a 布局属性](#4a-布局属性-uistylechangeimpactlayout) |
| `font-family` | [§4e 不支持](#4e-不支持的-css-特性), [§9.10](#910-font-family-和-font-size-无效) |
| `font-size` | [§4e 不支持](#4e-不支持的-css-特性), [§9.10](#910-font-family-和-font-size-无效) |
| `font-style` | [§4c 文字属性](#4c-文字属性) |
| `font-weight` | [§4c 文字属性](#4c-文字属性) |
| `gap` / `row-gap` / `column-gap` | [§4a 布局属性](#4a-布局属性-uistylechangeimpactlayout) |
| `grid-*` | [§4e 不支持](#4e-不支持的-css-特性) |
| `height` / `min-height` / `max-height` | [§4a 布局属性](#4a-布局属性-uistylechangeimpactlayout) |
| `justify-content` | [§4a 布局属性](#4a-布局属性-uistylechangeimpactlayout) |
| `letter-spacing` | [§4c 文字属性](#4c-文字属性) |
| `line-height` | [§4c 文字属性](#4c-文字属性) |
| `list-style-type` | [§4b 视觉属性](#4b-视觉属性-uistylechangeimpactpaint) |
| `margin` | [§4a 布局属性](#4a-布局属性-uistylechangeimpactlayout) |
| `object-fit` | [§4b 视觉属性](#4b-视觉属性-uistylechangeimpactpaint) |
| `opacity` | [§4b 视觉属性](#4b-视觉属性-uistylechangeimpactpaint) |
| `outline` | [§4b 视觉属性](#4b-视觉属性-uistylechangeimpactpaint) |
| `overflow` / `overflow-x` / `overflow-y` | [§4a 布局属性](#4a-布局属性-uistylechangeimpactlayout) |
| `overflow-wrap` | [§4c 文字属性](#4c-文字属性) |
| `padding` | [§4a 布局属性](#4a-布局属性-uistylechangeimpactlayout) |
| `pointer-events` | [§4b 视觉属性](#4b-视觉属性-uistylechangeimpactpaint) |
| `position` / `top` / `right` / `bottom` / `left` | [§4a 布局属性](#4a-布局属性-uistylechangeimpactlayout) |
| `text-align` | [§4c 文字属性](#4c-文字属性) |
| `text-decoration` | [§4c 文字属性](#4c-文字属性) |
| `text-indent` | [§4c 文字属性](#4c-文字属性) |
| `text-overflow` | [§4c 文字属性](#4c-文字属性) |
| `text-shadow` | [§4b 视觉属性](#4b-视觉属性-uistylechangeimpactpaint) |
| `text-transform` | [§4c 文字属性](#4c-文字属性) |
| `transform` | [§4b 视觉属性](#4b-视觉属性-uistylechangeimpactpaint) |
| `transition` / `transition-*` | [§4d 动画与过渡](#4d-动画与过渡) |
| `vertical-align` | [§4a 布局属性](#4a-布局属性-uistylechangeimpactlayout) |
| `visibility` | [§4b 视觉属性](#4b-视觉属性-uistylechangeimpactpaint) |
| `white-space` | [§4c 文字属性](#4c-文字属性) |
| `width` / `min-width` / `max-width` | [§4a 布局属性](#4a-布局属性-uistylechangeimpactlayout) |
| `word-break` | [§4c 文字属性](#4c-文字属性) |
| `z-index` | [§4a 布局属性](#4a-布局属性-uistylechangeimpactlayout) |

### CSS 选择器索引

| 选择器 | 章节 |
|--------|------|
| 标签选择器 `div` | [§5 选择器对照表](#5-选择器对照表) |
| 类选择器 `.foo` | [§5 选择器对照表](#5-选择器对照表) |
| ID 选择器 `#foo` | [§5 选择器对照表](#5-选择器对照表) |
| 通配符 `*` | [§5 选择器对照表](#5-选择器对照表) |
| 后代 `A B` | [§5 选择器对照表](#5-选择器对照表) |
| 子代 `A > B` | [§5 选择器对照表](#5-选择器对照表) |
| 伪类 `:hover` / `:focus` / `:active` / `:disabled` | [§5 选择器对照表](#5-选择器对照表) |
| `:focus-visible` | [§5 选择器对照表](#5-选择器对照表) |
| `:first-child` / `:last-child` | [§5 选择器对照表](#5-选择器对照表) |
| `:nth-child()` | [§5 选择器对照表](#5-选择器对照表) |
| `::before` / `::after` | [§5 选择器对照表](#5-选择器对照表) |
| 属性选择器 `[attr]` | [§5 不支持](#5-选择器对照表) |
| 兄弟选择器 `+` / `~` | [§5 不支持](#5-选择器对照表) |
| 分组 `A, B` | [§5 不支持](#5-选择器对照表), [§9.4 workaround](#94-选择器不支持逗号分组) |

---

> **文档维护提示**：当 Qz-UILib 版本升级新增 API 时，对应更新本文档。验证方式见 `docs/internal/AI记忆文档.md` 中的主动读取原则。

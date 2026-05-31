# 研究报告：Qz 游戏内样式 vs HTML Web 样式差异

> **版本**：2026-05-31  
> **范围**：Qz UILib 4.1.3-LTS 游戏内渲染 与 HTML/CSS Web 原型的样式差异系统性对比  
> **对照基准**：`docs/html-reference/`（HTML/CSS 原型）↔ `src/main/java/.../client/ui/`（Java 实现）  
> **姊妹文档**：如需 HTML 标签/CSS 属性 → Qz API 的完整逐项对照，请参阅 [`HTML-to-Qz-UILib-Reference.md`](./HTML-to-Qz-UILib-Reference.md)

---

## 文件映射关系

| HTML 原型 | Java 实现 | 职责 |
|---|---|---|
| `docs/html-reference/shared-palette.css` | `QzNetworkUiKit.java`（`Palette` 内部类） | 颜色/尺寸常量 |
| `docs/html-reference/network-tab.html` | `QzNetworkTabScreens.java` | 网络标签页 UI |
| `docs/html-reference/network-terminal.html` | `QzNetworkTerminalScreens.java` | 网络终端 5 面板 UI |

---

## 第一章：颜色系统差异

### 1.1 颜色格式转换

CSS 使用 `#RRGGBB` + `opacity` 分离表示，Qz 使用 `0xAARRGGBB` 单整数表示。

| 语义 | CSS 变量 | CSS 值（RGB + opacity） | Java 常量 | Java 值（ARGB） |
|---|---|---|---|---|
| 面板背景 | `--bg-panel` | `#141923` + opacity 0.93 | `BG_PANEL` | `0xEE141923` |
| 遮罩背景 | `--bg-overlay` | `rgba(5,7,11,0.67)` | `BG_OVERLAY` | `0xAA05070B` |
| 列表背景 | `--bg-list` | `#0D1219` | `BG_LIST` | `0xFF0D1219` |
| 行背景 | `--bg-row` | `#151D27` | `BG_ROW` | `0xFF151D27` |
| 输入框背景 | `--bg-input` | `#0D1117` | `BG_INPUT` | `0xFF0D1117` |
| ID 胶囊背景 | `--bg-id-pill` | `#0B1016` | `BG_ID_PILL` | `0xFF0B1016` |

**转换公式**：

$$Alpha_{hex} = \text{round}(opacity \times 255)$$

例如 $0.93 \times 255 = 237.15 \approx 237 = 0\text{xEE}$，$0.67 \times 255 = 170.85 \approx 171 = 0\text{xAA}$。

> **注意**：CSS 的 `opacity` 作用于整个元素（含子元素），而 Qz 的颜色 Alpha 通道仅作用于该颜色本身。两者在叠加效果上有微妙差异，对于本项目（背景色）影响可忽略。

### 1.2 `darken()` 行为差异

这是本项目中发现的一个重要语义差异。

**CSS/JS 版**（`network-tab.html` 中 JS 的暗化逻辑）：

```js
// JS 模拟：仅压暗 RGB，保留原始 Alpha
function darken(color, factor) {
  const r = Math.floor(((color >> 16) & 0xFF) * factor);
  const g = Math.floor(((color >> 8) & 0xFF) * factor);
  const b = Math.floor((color & 0xFF) * factor);
  return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
}
```

**Java 版**（`QzNetworkUiKit.darken()`）：

```java
public static int darken(final int color, final float factor) {
    final int r = Math.max(0, Math.min(255, (int) (((color >> 16) & 0xFF) * factor)));
    final int g = Math.max(0, Math.min(255, (int) (((color >> 8) & 0xFF) * factor)));
    final int b = Math.max(0, Math.min(255, (int) ((color & 0xFF) * factor)));
    return 0xFF000000 | r << 16 | g << 8 | b;  // ← 强制 Alpha = 0xFF
}
```

**关键差异**：Java 版强制 `Alpha = 0xFF`，丢弃原始 Alpha 通道。

**影响场景**：当前项目中对 `BG_PANEL`（`0xEE141923`）调用 `darken()` 后，结果变为完全不透明。但由于 `darken()` 目前仅用于 `summaryBox` 背景色（配合 `darken(entryColor, 0.18F)`），且 `entryColor` 的 Alpha 通道本身即为 `0xFF`，因此**当前未触发实际 bug**。若未来对半透明颜色调用 `darken()`，需注意此差异。

| 对比维度 | CSS/JS | Java |
|---|---|---|
| RGB 处理 | ✅ 压暗 | ✅ 压暗 |
| Alpha 保留 | ✅ 保留原始 Alpha | ❌ 强制 `0xFF` |
| 边界裁剪 | `Math.floor`（隐式） | `Math.max(0, Math.min(255, ...))` |

### 1.3 颜色常量组织

| 维度 | CSS | Java |
|---|---|---|
| 作用域 | `:root` 伪类下的 CSS 自定义属性 | `Palette` 静态内部类 |
| 引用方式 | `var(--text-primary)` | `Palette.TEXT_PRIMARY` |
| 命名风格 | `--text-primary`（kebab-case） | `TEXT_PRIMARY`（UPPER_SNAKE_CASE） |
| 类型安全 | 无（字符串） | 有（`int` 编译期检查） |
| 颜色值透明度 | RGB + 分离 opacity | ARGB 一体化 |

两者语义完全对应，迁移时**按名称查找即可**。例如 CSS 的 `--text-muted` → Java 的 `Palette.TEXT_MUTED`。

> **CSS 额外常量**：CSS 中定义了间距变量（`--gap-sm`、`--pad-md` 等），Java 中未定义对应的间距常量类，而是在各处直接使用 `UiStyleLength.px(8)` 等字面量。CSS 原型中的间距值供参考，Java 实现独立维护。

---

## 第二章：尺寸与布局系统差异

### 2.1 尺寸单位映射

| CSS 写法 | Qz 等价写法 | 备注 |
|---|---|---|
| `width: 720px` | `.setWidth(UiStyleLength.px(720))` | 直接对应，px 值是整数 |
| `width: 90%` | `.setWidth(UiStyleLength.percent(0.90F))` | 百分比用 0.0~1.0 浮点数 |
| `height: 100%` | `.setHeight(UiStyleLength.percent(1.0F))` | 同上 |
| `width: min(90vw, 720px)` | `.setWidth(percent(0.90F))` + `.setMaxWidth(px(720))` | 拆成两个独立属性 |
| `height: min(90vh, 500px)` | `.setHeight(percent(0.90F))` + `.setMaxHeight(px(500))` | 同上 |
| `height: auto` | `.setHeight(UiStyleLength.auto())` | 滚动容器关键设置（见 §6） |
| `calc(100% - 16px)` | `UiStyleLength.calc(1.0F, -16)` | 第一个参数是百分比系数，第二个是像素偏移 |

**百分比语义对比**：

| CSS | Qz |
|---|---|
| `width: 90%` — 相对**包含块**宽度 | `percent(0.90F)` — 相对**父元素**宽度 |
| `width: 90vw` — 相对**视口**宽度 | ❌ 无 vw/vh 单位，但可用 `percent()` 相对根元素模拟 |

> 本项目中的 `min(90vw, 720px)` 实际效果是在宽屏上限制为 720px，在窄屏上自适应缩放。Java 中利用 `percent(0.90F)` + `setMaxWidth(px(720))` 复现了相同行为。

### 2.2 Flexbox 差异

| CSS 属性 | Qz API | 支持状态 | 注意事项 |
|---|---|---|---|
| `display: flex` | `UiDisplay.FLEX` | ✅ 完整支持 | |
| `flex-direction: column` | `UiFlexDirection.COLUMN` | ✅ 完整支持 | `ROW` / `COLUMN` / `ROW_REVERSE` / `COLUMN_REVERSE` |
| `align-items: center` | `UiAlignItems.CENTER` | ✅ 完整支持 | |
| `justify-content: space-between` | `UiJustifyContent.SPACE_BETWEEN` | ✅ 完整支持 | |
| `flex-grow: 1` | `.setFlexGrow(1.0F)` | ✅ 完整支持 | |
| `flex-shrink: 0` | `.setFlexShrink(0.0F)` | ✅ 完整支持 | |
| `gap: 10px` | `.setGap(UiStyleLength.px(10))` | ✅ 完整支持 | 同时设 row-gap 和 column-gap |
| `row-gap: 8px` | `.setRowGap(UiStyleLength.px(8))` | ✅ 完整支持 | |
| `column-gap: 6px` | `.setColumnGap(UiStyleLength.px(6))` | ✅ 完整支持 | |
| `flex-wrap: wrap` | `UiFlexWrap.WRAP` | ✅ 支持 | 项目中 `action-row` 使用 |
| `flex-basis: 0` | `.setFlexBasis(UiStyleLength.px(0))` | ⚠️ 有历史 bug | Qz 源码已修补，`flex-basis` 正确生效 |
| `min-width: 0` | `.setMinWidth(UiStyleLength.px(0))` | ⚠️ 有历史 bug | Qz 源码已修补，`minWidth` 正确生效 |
| `box-sizing: border-box` | `UiBoxSizing.BORDER_BOX` | ✅ 完整支持 | 项目中面板/框架均设为此值 |

### 2.3 样式声明方式：简写 vs 逐属性

CSS 支持大量简写属性，Qz 必须逐个调用 setter。

#### padding / margin

| CSS 简写 | Qz 等价（逐属性） |
|---|---|
| `padding: 0 8px` | `.setPaddingTop(px(0))` + `.setPaddingBottom(px(0))` + `.setPaddingLeft(px(8))` + `.setPaddingRight(px(8))` |
| `padding: 14px` | `.setPadding(UiStyleLength.px(14))`（四边统一时存在简写 API） |
| `margin: 8px` | `.setMargin(UiStyleLength.px(8))`（四边统一时存在简写 API） |

#### border

| CSS 简写 | Qz 等价（逐属性） |
|---|---|
| `border: 1px solid #536B7F` | `.setBorderWidth(px(1))` + `.setBorderStyle(SOLID)` + `.setBorderColor(0xFF536B7F)` |
| `border-radius: 6px` | `.setBorderRadius(UiStyleLength.px(6))`（四角统一） |

#### flex

| CSS 简写 | Qz 等价（逐属性） |
|---|---|
| `flex: 1 1 0` | `.setFlexGrow(1.0F)` + `.setFlexShrink(1.0F)` + `.setFlexBasis(px(0))` |
| `flex: 1` | `.setFlexGrow(1.0F)` + `.setFlexShrink(1.0F)` + `.setFlexBasis(px(0))`（等效） |

#### overflow

| CSS 简写 | Qz 等价（逐属性） |
|---|---|
| `overflow: hidden` | `.setOverflowX(UiOverflow.HIDDEN)` + `.setOverflowY(UiOverflow.HIDDEN)` |
| `overflow-y: auto` | `.setOverflowY(UiOverflow.AUTO)`（单向直接支持） |
| `overflow-y: scroll` | `.setOverflowY(UiOverflow.SCROLL)`（单向直接支持） |

### 2.4 CSS 属性在 Qz 中不可用的替代方案

| CSS 属性 | Qz 状态 | 替代方案 |
|---|---|---|
| `position: fixed/absolute` | ✅ 支持 | `UiPosition.ABSOLUTE` / `FIXED` / `RELATIVE` / `STICKY`，配合 `setTop/Right/Bottom/Left` 使用 |
| `inset: 0` | ✅ 支持 | 分别设 `setTop(0)` + `setRight(0)` + `setBottom(0)` + `setLeft(0)` |
| `box-sizing: border-box`（全局 reset） | ⚠️ 需逐元素设置 | 每个需要 border-box 的节点显式调用 `.setBoxSizing(BORDER_BOX)` |

---

## 第三章：视觉效果差异

### 3.1 悬浮高亮：`filter: brightness()` → `box-shadow inset`

**CSS 原型**：

```css
.network-row:hover { filter: brightness(1.1); }
```

**Qz 实现**（`installComponentStyleSheet()`）：

```java
.addRule(".net-row:hover",
    new UiStyleDeclaration()
        .setBoxShadow(UiBoxShadow.inset(0, 0, 0, 1, 0x18FFFFFF)));
```

**原理**：Qz 不支持 CSS `filter`（仅有 `backdrop-blur` 和 `backdrop-saturation`，两者均作用于元素**背后**而非元素自身）。改用内阴影 `inset(0,0,0,1, 0x18FFFFFF)`，其中 `spread=1` 将阴影扩展至覆盖整个元素，`0x18` ≈ 9.4% 不透明度的白色叠加，视觉上近似 `brightness(1.1)`。

| 参数 | 含义 | 值 |
|---|---|---|
| offsetX, offsetY | 无偏移 | 0, 0 |
| blur | 无模糊 | 0 |
| spread | 覆盖整个元素 | 1 |
| color | 半透明白色 | `0x18FFFFFF`（~9.4% 白色） |

### 3.2 色块悬浮放大

**CSS 原型**：

```css
.swatch { transition: transform 0.15s; }
.swatch:hover { transform: scale(1.25); }
```

**Qz 实现**（`installComponentStyleSheet()`）：

```java
.addRule(".swatch-btn:hover",
    new UiStyleDeclaration()
        .setTransform(UiTransform.of(0.0F, 0.0F, 1.22F, 1.22F, 0.0F)));
```

**差异说明**：

| 维度 | CSS | Qz |
|---|---|---|
| 缩放倍率 | `scale(1.25)` | `(scaleX=1.22, scaleY=1.22)` |
| 动画 | `transition: transform 0.15s` | 未配置 transition（即时变化） |
| 动画可行性 | — | ✅ Qz 支持 transition 系统（`transition-specs`），项目中当前未启用 |

> `UiTransform.of(translateX, translateY, scaleX, scaleY, rotateDeg)` 五个参数依次为：水平偏移、垂直偏移、水平缩放、垂直缩放、旋转角度。

### 3.3 导航按钮悬浮：背景叠加技巧

**CSS 原型**：

```css
.nav-btn:hover { background: rgba(255,255,255,0.05); }
```

**Qz 实现**（`installComponentStyleSheet()`）：

```java
.addRule(".nav-btn:hover",
    new UiStyleDeclaration()
        .setBoxShadow(UiBoxShadow.inset(0, 0, 0, 999, 0x0DFFFFFF)));
```

**原理**：Qz 中 `:hover` 规则**无法覆盖内联样式**设置的 `background-color`。但导航按钮的背景色通过 Java 内联样式动态切换（active=激活色、非 active=透明），`:hover` 规则设置 `background-color` 会被内联样式覆盖。改用 `spread=999` 的超大内阴影模拟半透明白色叠加，`0x0D` ≈ 5.1% 不透明度。

> **注意**：`spread=999` 是一个技巧性用法，确保无论元素多大，阴影都能完全覆盖。这在 CSS 中也是合法做法（`inset 0 0 0 999px rgba(...)`），但 Qz 渲染引擎可能对超大 spread 有性能影响，当前因按钮尺寸固定且较小（`height: 30px`），可接受。

### 3.4 视觉效果支持状态汇总

| CSS 效果 | Qz 状态 | 备注 |
|---|---|---|
| `transition` | ✅ 支持 | 通过 `transition-specs` 配置属性、时长、时间函数；项目中当前未使用 |
| `@keyframes` / `animation` | ✅ 支持 | `DocumentKeyframes` + `animation-duration`（单位：纳秒）；`animation-iteration-count: 0` 表示无限循环 |
| `filter: brightness()` | ❌ 不支持 | 改用 `box-shadow inset` 半透明白色叠加（§3.1） |
| `filter: blur()` | ❌ 不支持 | 仅有 `backdrop-blur`（作用于元素背后内容，非元素自身） |
| `backdrop-filter: blur()` | ✅ `setBackdropBlurRadius()` | 毛玻璃效果，作用于元素背后 |
| `backdrop-filter: saturate()` | ✅ `setBackdropSaturation()` | 背后内容饱和度 |
| `linear-gradient()` | ❌ 不支持 | 纯色背景 + 边框区分层次 |
| `radial-gradient()` | ❌ 不支持 | 同上 |
| `background-image` | ✅ 支持（有限） | 仅支持单张宿主图片，拉伸填充，无 `size`/`position`/`repeat` 控制 |
| `box-shadow`（外阴影） | ✅ 支持 | `UiBoxShadow.of(offsetX, offsetY, blur, spread, color)` |
| `box-shadow: inset` | ✅ 支持 | `UiBoxShadow.inset(offsetX, offsetY, blur, spread, color)` |
| 多重 `box-shadow` | ❌ 不支持 | 仅支持单个阴影 |
| `opacity` | ✅ 支持 | `setOpacity(float)` 0.0~1.0；也可通过颜色 Alpha 通道替代（更高效） |
| `z-index` | ✅ 支持 | `setZIndex(int)` |
| `cursor: pointer` | ✅ 支持 | `UiCursor.POINTER` |
| `cursor: not-allowed` | ✅ 支持 | `UiCursor.NOT_ALLOWED` |
| `cursor: text` | ✅ 支持 | `UiCursor.TEXT` |
| `visibility: hidden` | ✅ 支持 | `UiVisibility.HIDDEN`——元素不可见但仍占据布局空间、不响应命中 |

---

## 第四章：字体与文本渲染差异

### 4.1 字体属性支持矩阵

| CSS 属性 | Qz 支持状态 | Qz API | 备注 |
|---|---|---|---|
| `font-family` | ❌ 不支持 | — | 固定 Minecraft 字体渲染管线，无法切换字体 |
| `font-size` | ❌ 不支持 | — | 固定大小，由 MC 字体系统 + GUI scale 决定 |
| `font-weight: bold` | ✅ | `UiFontWeight.BOLD` | 渲染效果依赖 MC 字体是否有粗体变体（Unicode 字体通常有） |
| `font-weight: normal` | ✅ | `UiFontWeight.NORMAL` | 默认值 |
| `font-style: italic` | ✅ | `UiFontStyle.ITALIC` | 同上，依赖字体变体 |
| `line-height` | ✅ | `setLineHeight(UiStyleLength)` | `auto` 跟随字体默认行高 |
| `letter-spacing` | ✅ | `setLetterSpacing(UiStyleLength)` | 字间距 |
| `text-indent` | ✅ | `setTextIndent(UiStyleLength)` | 首行缩进 |
| `color` | ✅ | `setTextColor(int)` | ARGB 格式 |
| `text-align` | ✅ | `UiTextAlign.START / CENTER / END` | 等价 CSS 的 `left` / `center` / `right` |
| `white-space: nowrap` | ✅ | `UiWhiteSpace.NOWRAP` | |
| `white-space: pre` | ✅ | `UiWhiteSpace.PRE` | 保留空白与换行 |
| `white-space: normal` | ✅ | `UiWhiteSpace.NORMAL` | 默认值 |
| `text-overflow: ellipsis` | ✅ | `UiTextOverflow.ELLIPSIS` | **仅在 `NOWRAP` 时生效** |
| `text-overflow: clip` | ✅ | `UiTextOverflow.CLIP` | 默认值 |
| `text-decoration: underline` | ✅ | `UiTextDecoration.UNDERLINE` | |
| `text-decoration: line-through` | ✅ | `UiTextDecoration.LINE_THROUGH` | |
| `text-decoration: overline` | ✅ | `UiTextDecoration.OVERLINE` | |
| `text-transform: uppercase` | ✅ | `UiTextTransform.UPPERCASE` | |
| `text-transform: lowercase` | ✅ | `UiTextTransform.LOWERCASE` | |
| `text-transform: capitalize` | ✅ | `UiTextTransform.CAPITALIZE` | |
| `word-break: break-all` | ✅ | `UiWordBreak.BREAK_ALL` | |
| `word-break: keep-all` | ✅ | `UiWordBreak.KEEP_ALL` | |
| `overflow-wrap: break-word` | ✅ | `UiOverflowWrap.BREAK_WORD` | |
| `overflow-wrap: anywhere` | ✅ | `UiOverflowWrap.ANYWHERE` | |
| `text-shadow` | ✅ | `UiTextShadow` | 单个阴影，不支持多重阴影 |
| `vertical-align: baseline` | ✅ | `UiVerticalAlign.BASELINE` | |
| `vertical-align: top` | ✅ | `UiVerticalAlign.TOP` | |
| `vertical-align: middle` | ✅ | `UiVerticalAlign.MIDDLE` | |
| `vertical-align: bottom` | ✅ | `UiVerticalAlign.BOTTOM` | |

### 4.2 文本截断三件套

这是项目中最常用的文本处理模式，CSS 与 Qz 所需属性完全对应：

| 步骤 | CSS | Qz |
|---|---|---|
| 1. 禁止换行 | `white-space: nowrap` | `.setWhiteSpace(UiWhiteSpace.NOWRAP)` |
| 2. 隐藏溢出 | `overflow: hidden` | `.setOverflowX(HIDDEN)` + `.setOverflowY(HIDDEN)` |
| 3. 省略号 | `text-overflow: ellipsis` | `.setTextOverflow(UiTextOverflow.ELLIPSIS)` |
| 4. 收缩适应 | `min-width: 0` | `.setMinWidth(UiStyleLength.px(0))` |

> **注意**：Qz 中 `text-overflow: ellipsis` 仅在 `white-space: NOWRAP` 时生效。且文本元素必须设 `min-width: 0`（或 `flex-shrink > 0`）才能在父容器空间不足时正确收缩。

### 4.3 CSS 字体属性的处理策略

迁移时的标准做法：

1. **删除** `font-family` 声明——Minecraft 固定字体
2. **删除** `font-size` 声明——由 MC 字体系统决定
3. **保留** `font-weight: bold` / `font-style: italic`——Qz 有对应 API
4. **保留** `line-height` / `letter-spacing`——Qz 直接支持
5. **保留** `text-align` / `white-space` / `text-overflow`——Qz 直接支持

---

## 第五章：交互与状态管理差异

### 5.1 动画系统

Qz **确实支持**动画系统，包含 `transition` 和 `keyframe` 动画两种机制。

| CSS 概念 | Qz 等价 | 说明 |
|---|---|---|
| `transition-property` | `transition-specs` 列表 | 配置哪些属性参与过渡 |
| `transition-duration` | `duration`（`Long`，纳秒） | ⚠️ 单位是**纳秒**，非毫秒。$150\text{ms} = 150\,000\,000\text{ns}$ |
| `transition-delay` | `delay`（`Long`，纳秒） | 同上 |
| `transition-timing-function` | `easing` | `ease` / `ease-in` / `ease-out` / `linear` 等 |
| `@keyframes` | `DocumentKeyframes` | 通过 `document.registerKeyframes()` 注册，Java API 定义关键帧 |
| `animation-duration` | `Long`（纳秒） | 同上单位注意 |
| `animation-iteration-count: infinite` | `0` | 0 表示无限循环 |
| `animation-iteration-count: N` | `N` | 正整数表示播放 N 次 |

**本项目使用情况**：项目中当前**未使用** transition 动画系统。所有状态变化（悬浮高亮、色块放大、按钮切换）均为**即时生效**，无过渡动画。这是有意的设计选择——Minecraft GUI 通常不使用 CSS 式过渡动画，即时反馈更符合游戏 UI 习惯。

如需为色块悬浮添加放大动画（API 详情见 [`HTML-to-Qz-UILib-Reference.md`](./HTML-to-Qz-UILib-Reference.md#4d-动画与过渡)）：

```java
// 在 swatch-btn 的默认样式中添加 transition 配置
swatchBtn.style()
    .setTransition(DocumentAnimationProperty.TRANSFORM, 150L);  // 150ms
```

### 5.2 全量重建 vs 增量更新

| 维度 | HTML/JS 原型 | Java/Qz 实现 |
|---|---|---|
| 更新策略 | 全量重建：每次状态变化 `innerHTML` 重新渲染 | 混合策略：数据包刷新时**全量重建**；单选/悬浮态**增量更新** |
| 典型场景 | `render()` 函数重写整个 `networkList.innerHTML` | `renderList()` 清除并重建所有行；`updateSelectedRow()` 仅修改 CSS class |
| 性能考量 | 浏览器 DOM diff 优化，无感知 | 游戏内 DOM 操作有性能代价，需主动管理更新粒度 |
| 行选中态 | CSS `.selected` class 切换 | Java 通过 `rowNodes` Map 精确定位节点，修改其样式（边框色、背景色） |

**Java 增量更新示例**（选自 `QzNetworkTabScreens`）：

```java
// 数据包刷新 → 全量重建
private void renderList() {
    this.list.clearChildren();
    this.rowNodes.clear();
    for (final NetworkEntry entry : this.networks) {
        final ElementNode row = networkRow(entry);
        this.rowNodes.put(entry.networkID, row);
        this.list.append(row);
    }
}

// 点击行 → 增量更新（仅修改两个节点的边框/背景）
private void updateSelection() {
    for (final Map.Entry<Integer, ElementNode> e : this.rowNodes.entrySet()) {
        final boolean selected = e.getKey() == this.selectedNetworkID;
        e.getValue().style()
            .setBorderColor(selected ? entryColor(...) : Palette.BORDER_ROW)
            .setBackgroundColor(selected ? darken(entryColor(...), 0.12F) : Palette.BG_ROW);
    }
}
```

### 5.3 焦点管理

| 维度 | CSS/HTML | Qz |
|---|---|---|
| 声明焦点样式 | `:focus-visible` 伪类 | 无对应伪类；通过 `DocumentButtonControl.setFocusBorderColor()` 设置 |
| 自动聚焦 | `autofocus` 属性 | `element.focus()` 命令式调用 |
| 焦点可见指示 | 浏览器默认 focus ring | 需手动设置 `focusBorderColor` |
| Tab 键导航 | 浏览器原生支持 | 需手动实现 Tab 顺序（项目中未实现） |

**当前项目做法**：

- 按钮焦点边框色统一设为 `Palette.BTN_FOCUS_BORDER`（`0xFF9ED0FF`）
- 未实现 Tab 键焦点切换，交互依赖鼠标点击

### 5.4 密码输入框

| 维度 | HTML | Qz |
|---|---|---|
| 原生支持 | `<input type="password">` 自动掩码 | `DocumentTextInputControl` 无密码模式 |
| 掩码实现 | 浏览器内置 | `QzNetworkUiKit.MaskedInput` 包装器手动实现 |
| 掩码字符 | `•`（浏览器默认） | 自定义（项目中用 `*`） |
| 实际文本 | 浏览器自动管理 | `MaskedInput` 内部存储真实文本，UI 显示掩码文本 |

**`MaskedInput` 工作原理**：包装 `DocumentTextInputControl`，拦截文本变更事件，在内部存储真实文本，将显示文本替换为等长掩码字符。输入/删除操作同步维护两份文本。提交时从包装器获取真实密码。

---

## 第六章：已知 Bug 与修补方案

| 编号 | Bug 描述 | 影响场景 | 根因 | 状态 | Workaround |
|---|---|---|---|---|---|
| B1 | flex 子元素 `maxWidth`/`minWidth` 不生效 | 所有使用 `min-width: 0` 防溢出的场景 | `FlexLayoutHelper.resolveContentMainSize()` 未调用 `applyWidthConstraints` | ✅ Qz 源码已修补 | 修补后仍需**显式设置** `setMinWidth(px(0))` |
| B2 | 列 flex 用自然内容高度覆盖 `flex-basis` | 滚动列表容器高度计算错误 | `FlexLayoutHelper.layoutColumnFlexChildren()` L228-232 | ✅ Qz 源码已修补 | 滚动容器用 `height: auto()` |
| B3 | 框架给根元素默认 `overflow-y: auto` | 根元素出现意外滚动条 | `UiDocumentScreens` 框架设计 | ⚠️ 设计行为 | **每个界面根元素**显式设置 `setOverflowX/Y(HIDDEN)` |
| B4 | 滚动容器需 `height: auto()` 而非 `px(0)` | `flex: 1 1 0` 的滚动容器无法正确计算高度 | `UiStyleLength.Type` 区分 PIXEL/AUTO | ⚠️ API 设计 | 使用 `UiStyleLength.auto()` |

### B3 详细说明：框架默认 overflow

Qz 框架在创建文档屏幕时，会给根元素设置默认 `overflow-y: auto`。这意味着如果不显式覆盖，根元素会出现滚动条。

**本项目所有界面的固定写法**（`build()` 开头）：

```java
root.style()
    .setOverflowX(UiOverflow.HIDDEN)
    .setOverflowY(UiOverflow.HIDDEN)
    // ... 其他样式
```

> **教训**：如果界面出现意外的外层滚动条，首先检查根元素是否覆盖了 `overflow`。

### B4 详细说明：滚动容器高度

在 CSS 中，`flex: 1 1 0` + `overflow-y: auto` 的滚动容器会自动计算高度。在 Qz 中，需要额外设置 `height: auto()`：

```java
// ❌ 错误：滚动容器无高度
list.style()
    .setFlexGrow(1.0F).setFlexShrink(1.0F).setFlexBasis(px(0))
    .setOverflowY(UiOverflow.SCROLL);

// ✅ 正确：显式 height: auto()
list.style()
    .setFlexGrow(1.0F).setFlexShrink(1.0F).setFlexBasis(px(0))
    .setHeight(UiStyleLength.auto())  // ← 关键
    .setOverflowY(UiOverflow.SCROLL);
```

原因：Qz 的 flex 布局在计算交叉轴尺寸时，不会自动将 `flex-basis: 0` 的元素拉伸到剩余空间——需要 `height: auto()` 告诉引擎"在 flex 容器中自动计算你的高度"。

---

## 第七章：迁移检查清单

从 HTML/CSS 原型迁移到 Qz UILib Java 实现时，逐项检查：

### 颜色

- [ ] `#RRGGBB` + `opacity` → `0xAARRGGBB`，$Alpha = \text{round}(opacity \times 255)$
- [ ] CSS `var(--xxx)` → Java `Palette.XXX`，按名称查找
- [ ] 检查 `darken()` 调用：当前版本强制 `Alpha=0xFF`，确保被暗化的颜色本身不透明

### 尺寸

- [ ] `px` 值直接对应 `UiStyleLength.px(int)`
- [ ] `%` 值用 `UiStyleLength.percent(float)`，参数 0.0~1.0
- [ ] `min(a, b)` 拆成 `setWidth`/`setHeight` + `setMaxWidth`/`setMaxHeight`
- [ ] `calc(100% - 16px)` 用 `UiStyleLength.calc(1.0F, -16)`

### 布局

- [ ] `gap` / `row-gap` / `column-gap` 直接支持
- [ ] `flex-wrap: wrap` 直接支持
- [ ] `min-width: 0` 必须**显式设置**（Qz 历史 bug 虽已修补，仍需显式声明）
- [ ] 所有需要 `border-box` 的元素逐元素设置 `UiBoxSizing.BORDER_BOX`
- [ ] 简写 CSS 属性按 §2.3 逐属性展开

### 视觉效果

- [ ] `filter: brightness()` → `box-shadow inset` 半透明白色（§3.1）
- [ ] `:hover` 伪类 → 移入 `UiStyleSheet` 的 addRule（不能用内联样式实现悬浮态）
- [ ] `transition` → 可选保留（Qz 支持），项目中当前未使用，保持即时变化
- [ ] `linear-gradient` / `radial-gradient` → 纯色背景 + 边框区分层次
- [ ] `background-image` → 有限支持（单张拉伸），无 size/position 控制
- [ ] 多重 `box-shadow` → 仅支持单个阴影，优先保留最关键的

### 字体

- [ ] 删除 `font-family` — MC 固定字体
- [ ] 删除 `font-size` — MC 字体系统决定
- [ ] `font-weight: bold` → `UiFontWeight.BOLD`
- [ ] `font-style: italic` → `UiFontStyle.ITALIC`
- [ ] `line-height` / `letter-spacing` 可保留
- [ ] 文本截断三件套：`NOWRAP` + `HIDDEN` + `ELLIPSIS` + `minWidth(0)`

### 根元素

- [ ] **必须**显式设置 `setOverflowX(HIDDEN)` + `setOverflowY(HIDDEN)`（§6 B3）
- [ ] 根元素设 `width/height: percent(1.0F)` 充满屏幕
- [ ] 根元素设 `display: FLEX` + `alignItems: CENTER` + `justifyContent: CENTER` 居中面板

### 滚动容器

- [ ] `flex: 1 1 0` 的滚动容器必须用 `height: auto()` 而非 `px(0)`（§6 B4）
- [ ] 设 `minHeight` 防止空列表时滚动容器塌陷
- [ ] 设 `overflowY: SCROLL` + `overflowX: HIDDEN`

### 悬浮效果

- [ ] 移入 `UiStyleSheet` 的 `:hover` 规则
- [ ] 不能用内联样式覆盖 `:hover` 规则的属性（如 `background-color`）——改用 `box-shadow` 技巧
- [ ] 注册样式表：`document.addStyleSheet(...)` 或在 build 末尾调用 `installComponentStyleSheet()`

### 其他

- [ ] `z-index` → Qz 支持 `setZIndex(int)`，可直接迁移
- [ ] `cursor` → Qz 支持 `UiCursor.POINTER` / `NOT_ALLOWED` / `TEXT`，可直接迁移
- [ ] `opacity` → Qz 支持 `setOpacity(float)`，可直接迁移（也可用颜色 Alpha 通道替代，更高效）
- [ ] `cursor: pointer` 仅对可交互元素设置，装饰性元素不需要
- [ ] 表单验证（必填项检查）在 Java 侧手动实现，无 HTML5 constraint validation

---

## 附录：关键文件索引

| 文件 | 路径 |
|---|---|
| CSS 颜色原型 | `docs/html-reference/shared-palette.css` |
| 网络标签页 HTML 原型 | `docs/html-reference/network-tab.html` |
| 网络终端 HTML 原型 | `docs/html-reference/network-terminal.html` |
| Palette 常量 + DOM 工具 | `src/main/java/com/github/singularityme/client/ui/QzNetworkUiKit.java` |
| 网络标签页实现 | `src/main/java/com/github/singularityme/client/ui/QzNetworkTabScreens.java` |
| 网络终端实现 | `src/main/java/com/github/singularityme/client/ui/QzNetworkTerminalScreens.java` |
| AI 记忆文档（含 Qz 已知问题） | `docs/internal/AI记忆文档.md` |
| 错误记录索引 | `docs/errors/README.md` |

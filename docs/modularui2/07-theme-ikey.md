# 07. Theme 与 IKey

## Theme 系统

源码入口：

- `theme/Theme.java:11`
- `theme/ThemeBuilder.java:14`
- `theme/ThemeAPI.java:19`
- `theme/ThemeManager.java:42`

MUI2 支持主题与 JSON 加载。本项目当前没有接入主题文件，而是使用 `NetworkUiKit.Palette` 和 `NetworkUiKit.Styles` 硬编码 ARGB 值。理由是网络 UI 属于模组内部固定风格，且 GTNH 1.7.10 环境下主题资源热加载收益有限。

## 项目色彩策略

集中定义：

- 背景色、行色、输入框色。
- 文本主次色。
- 按钮、危险按钮、禁用按钮。
- 安全等级与访问等级色。
- 固定高度、徽章尺寸、圆角半径。

这样做的目的不是绕开主题系统，而是让网络标签页和网络终端保持一致，同时避免在每个面板里散落魔法数。

## IKey 文本

网络 UI 的文本全部经 `IKey` 进入 Widget：

```java
new TextWidget(IKey.str(NetworkUiKit.tr("gui.singularityme.network_tab.title")))
```

动态文本：

```java
new TextWidget(IKey.dynamicKey(() -> IKey.str(displayDeviceName())))
```

`IKey.str` 适合已经翻译好的字符串；翻译入口统一由 `NetworkUiKit.tr` 和 `NetworkUiKit.trf` 包装 `StatCollector`。这样 UI 层不用关心 lang key 格式。

## WidgetTheme 取舍

本项目未使用 `WidgetTheme` 做按钮或输入框主题，主要因为：

- MUI2 网络 UI 只服务两个 screen，硬编码样式更直接。
- 当前视觉来自 `docs/html-reference/`，与 Minecraft 低分辨率 GUI 强绑定。
- 已经踩过 `Rectangle` 圆角、边框、padding 组合坑，集中工厂更好控。

如果未来要开放玩家自定义主题，应把 `Palette` 转为 semantic token，再由 `Theme` 或 JSON 配置注入，而不是让业务 UI 直接读取 JSON。

## 文本与容器尺寸

`TextWidget` 通常不负责换行和截断。短标签、徽章、按钮文案应保证在固定尺寸内可读；长文本应放入 `RichText` / `ScrollingText` 或列表。网络 UI 当前避免写长说明文本，主要依靠导航标签、状态行和按钮动作表达。

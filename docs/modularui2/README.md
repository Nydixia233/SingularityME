# ModularUI2 源码级开发指南

> 面向在 Singularity ME 中维护网络界面的开发者。本指南以本地源码检出
> `E:\Minecraft Mod\GTNH Source\ModularUI2-master\src\main\java\com\cleanroommc\modularui\`
> 为规范引用源，当前检出含 507 个 `.java` 文件；项目运行依赖为
> `com.github.GTNewHorizons:ModularUI2:2.3.63-1.7.10:dev`。

所有 `file:line` 引用均以 `ModularUI2-master` 当前检出为准。阅读者复核时应回到该目录确认行号，因为源码检出是 point-in-time dump，不带 git tag 信息；早期用 2.3.55 sources jar 只做交叉验证，不作为本文档引用路径。

## 一句话能力总览

ModularUI2 是 GTNH fork 的客户端/容器 UI 框架：它提供面板、布局求解、Widget、Drawable、主题、值绑定和服务端同步链路。本项目网络 UI 只采用 `ModularScreen + GuiScreenWrapper` 的 client-only 路线，数据刷新继续走自有 packet。

## 阅读路径

| 文件 | 内容 | 建议读者 |
|------|------|----------|
| [01-architecture.md](01-architecture.md) | 接入边界、生命周期、client-only 与容器同步的取舍 | 第一次改网络 UI |
| [02-layout-sizing.md](02-layout-sizing.md) | 布局求解、尺寸组合、死锁与 padding 溢出 | 排查 resize / SIZING 问题 |
| [03-widgets-core.md](03-widgets-core.md) | Flow、Text、Button、Input、List、Slider、Progress | 日常写界面 |
| [04-widgets-advanced.md](04-widgets-advanced.md) | Paged、Expandable、Dialog、Dropdown、Slot、Display、RichText | 需要更复杂控件 |
| [05-value-sync.md](05-value-sync.md) | `IValue` 与 `SyncHandler` 两条数据路径 | 判断是否要接入 MUI2 同步 |
| [06-drawing-drawables.md](06-drawing-drawables.md) | `GuiDraw`、`Rectangle`、纹理、裁剪、自定义 drawable | 处理视觉样式 |
| [07-theme-ikey.md](07-theme-ikey.md) | Theme、WidgetTheme、JSON 主题、`IKey` 文本 | 做主题化和文本 |
| [08-cookbook.md](08-cookbook.md) | 本项目实战配方、Qz 迁移对照、安全尺寸写法 | 直接照抄模板 |
| [09-troubleshooting.md](09-troubleshooting.md) | 错误现象、根因、修法、历史记录链接 | 遇到问题先查 |

## 本项目取舍

- 已用：`ModularPanel`、`Flow`、`ListWidget`、`TextWidget`、`ButtonWidget`、`TextFieldWidget`、`StringValue`、`Rectangle`、`Circle`、`IKey.dynamicKey`、自定义 `IDrawable`。
- 未用但可用：`PagedWidget`、`DropdownWidget`、`ColorPickerDialog`、`SortableListWidget`、`RichText`、`GraphDrawable`、Slot / Display 系列。
- 取舍：不使用 `ModularContainer`、`PanelSyncManager`、`SyncHandler` 作为网络 UI 的主数据通道；原因是本项目已有 packet 协议，网络标签页和网络终端都是纯客户端面板。

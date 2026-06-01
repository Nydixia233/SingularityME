# 01. 架构与接入边界

## 版本与包名

- 库包名：`com.cleanroommc.modularui`。
- 运行依赖：`dependencies.gradle` 中的 `com.github.GTNewHorizons:ModularUI2:2.3.63-1.7.10:dev`。
- 规范源码：`E:\Minecraft Mod\GTNH Source\ModularUI2-master\src\main\java\com\cleanroommc\modularui\`。

## 本项目采用的 client-only 路线

网络标签页和网络终端通过 Forge 旧 GUI 流程打开，但客户端返回的是 MUI2 screen wrapper：

```java
public static GuiScreen create(final TileEntity te) {
    final ModularScreen screen = new ModularScreen("singularityme", (ModularGuiContext ctx) -> {
        final TabState state = new TabState(x, y, z, dim);
        activeState = new WeakReference<>(state);
        return state.buildPanel();
    });
    screen.getContext().setSettings(new UISettings());
    return new GuiScreenWrapper(screen);
}
```

项目蓝本：

- `src/main/java/com/github/singularityme/client/ui/NetworkTabUI.java`
- `src/main/java/com/github/singularityme/client/ui/NetworkTerminalUI.java`
- `src/main/java/com/github/singularityme/client/ui/NetworkUiKit.java`

MUI2 源码依据：

- `screen/ModularScreen.java:58` 定义 `ModularScreen`，持有 screen 名称、上下文、面板工厂和 panel map。
- `screen/GuiScreenWrapper.java:12` 定义 wrapper；构造时调用 `screen.construct(this)`，以普通 `GuiScreen` 方式接入 Minecraft。
- `screen/ModularContainer.java:38` 是容器同步路线的入口；本项目网络 UI 不走它。
- `screen/ModularPanel.java:63` 是顶层 panel，继承 parent widget，可作为 viewport。

## 生命周期

1. 方块右键触发 `player.openGui`。
2. `SingularityGuiHandler.getClientGuiElement` 创建 `GuiScreenWrapper`。
3. `GuiScreenWrapper` 构造时让 `ModularScreen` 构建 panel。
4. UI 打开后，项目代码在 client 线程调度请求包。
5. 服务端回包由 packet handler 调度回 client 线程，再调用静态 `receive...` 入口。
6. 状态对象更新字段并重建 Widget 子树。

## 静态入口约定

每个网络 UI 类保留静态入口，便于 packet handler 找到当前打开的界面：

- `static GuiScreen create(TileEntity)`
- `static boolean receiveNetworkData(PacketNetworkTabData)`
- 网络终端额外保留 `static boolean receiveNetworkStatus(PacketNetworkStatus)`

命中判定使用 `Minecraft.getMinecraft().currentScreen instanceof GuiScreenWrapper`，再检查 `w.getScreen().isPanelOpen("network_tab")` 或 `network_terminal`。这样 packet 不会误刷其他屏幕。

## 与 ModularContainer 的取舍

`ModularContainer` 路线适合需要服务端 Slot、自动同步、服务端校验的传统容器 UI。本项目网络 UI 的数据已经由自有 packet 明确定义，且没有物品槽交互，所以取舍如下：

| 能力 | client-only 路线 | ModularContainer 路线 |
|------|------------------|-----------------------|
| 打开成本 | 低，无容器同步树 | 需要容器和 sync 注册 |
| 数据来源 | 项目 packet | `SyncHandler` / slot / value sync |
| 服务端校验 | 在 packet handler 中完成 | 容器与 handler 共同承担 |
| 适用场景 | 网络列表、状态页、设置页 | 机器库存、物品槽、服务端实时值 |

## 最小接入骨架

```java
final ModularScreen screen = new ModularScreen("singularityme", ctx -> {
    final MyState state = new MyState(x, y, z, dim);
    activeState = new WeakReference<>(state);
    return state.buildPanel();
});
screen.getContext().setSettings(new UISettings());
return new GuiScreenWrapper(screen);
```

注意：`UISettings` 必须设置；旧 guide 已记录过缺省 settings 在 exclude area 路径上可能触发 NPE。

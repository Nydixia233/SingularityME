# 05. Value 与 Sync

## 两条数据路径

MUI2 有两类常见数据机制：

| 路径 | 代表类型 | 适用场景 | 本项目状态 |
|------|----------|----------|------------|
| 客户端值绑定 | `IValue<T>`、`StringValue` | 输入框、本地临时状态 | 已用 |
| 容器同步 | `SyncHandler`、`PanelSyncManager`、`ModularContainer` | 服务端权威值、Slot、机器状态 | 网络 UI 取舍不用 |

源码依据：

- `api/value/IValue.java:8` 定义 `IValue<T>`。
- `api/value/sync/IValueSyncHandler.java:14` 将 value 与 sync handler 结合。
- `value/sync/SyncHandler.java:32` 是同步 handler 基类。
- `value/sync/PanelSyncManager.java:35` 管理 panel 同步注册。
- `screen/ModularContainer.java:38` 是容器同步路线入口。

## 本项目的客户端值绑定

输入框只保存本地临时值，提交时由 packet 发给服务端：

```java
TextFieldWidget memberField = new TextFieldWidget()
    .value(memberNameVal)
    .widthRel(1f).height(36)
    .background(Styles.inputBg())
    .autoUpdateOnChange(true);
```

典型字段：

- `NetworkTerminalUI.TerminalState.filterVal`
- `NetworkTerminalUI.TerminalState.memberNameVal`
- `createNameVal`
- `settingsNameVal`

## 自有 packet 刷新链路

网络 UI 的权威数据来自项目 packet：

```text
Packet handler
  -> Minecraft.func_152344_a(...)
  -> NetworkTerminalUI.receiveNetworkData(packet)
  -> state.receive(packet)
  -> renderContent()
```

状态页额外使用 `PacketNetworkStatus`，请求时以选中 `networkID` 为键，而不是以终端方块坐标为键。这是网络终端和网络标签页的一个长期边界。

## 为什么不用 SyncHandler

放弃：

- MUI2 自动同步生命周期。
- Slot 和服务端容器集成。
- 容器级交互校验。

换来：

- 纯客户端面板，打开成本低。
- 复用项目已有 packet 协议。
- 网络列表、成员、设置、健康状态的刷新逻辑统一。

如果未来要做带物品槽的 MUI2 机器界面，应重新评估 `ModularContainer + SyncHandler`，不要把网络 UI 的 client-only 结论直接套到 Slot GUI。

## 输入被刷新重置的预防

网络终端用 `panelFirstRender` 区分“切换到面板第一次渲染”和“数据刷新导致重渲染”。例如设置页只在首次进入时从选中网络写入输入框，避免 packet 刷新清空玩家正在输入的内容。

经验规则：

- 本地输入值放 `StringValue`。
- 服务端回包更新列表和状态字段。
- 重建内容时只在明确的初始化点重置输入。
- 提交操作走 packet handler 做服务端校验。

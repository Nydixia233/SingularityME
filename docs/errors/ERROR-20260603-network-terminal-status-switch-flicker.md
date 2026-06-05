# Network Terminal 切换网络时 loading 中间帧闪烁

## 错误现象

网络终端左侧切换网络时，主页内容会闪一下，出现一帧 `正在加载网络状态...` 或信息块高度变化。

## 触发场景

在 Network Terminal 的主页或连接页中，点击左侧网络列表切换到另一个网络。网络列表的选中态立即变化，但对应的 `PacketNetworkStatus` 需要等待服务端回包。

## 根本原因

旧实现把 `selectedNetworkID` 切换后立即将 `networkStatus` 置空并调用 `renderContent()`，导致主页先按空状态渲染，再在 `PacketNetworkStatus` 返回后二次渲染。相关旧触发点在 `src/main/java/com/github/singularityme/client/ui/NetworkTerminalUI.java:393` 与 `:705` 的网络行点击入口，状态请求与接收逻辑在 `src/main/java/com/github/singularityme/client/ui/NetworkTerminalUI.java:1202`、`:1207`。

## 修复方案

1. 增加 `statusCache`，切换网络时优先复用已收到的状态快照；未命中缓存时也不先清空成 loading 文本。
2. `requestNetworkStatus()` 只负责发请求，不再主动清空 `networkStatus`。
3. `receiveStatus()` 先把回包写入缓存，再确认回包仍属于当前选中网络，避免快速连续切换时过期状态覆盖当前界面。
4. 主页顶部能量/在线率和下方状态相关信息在等待回包时使用 `-` 占位，保持行数和布局稳定。

## 预防措施

以后做 packet 驱动的客户端 UI 切换时，不要把旧状态直接清空后立刻重绘；优先保留缓存快照或稳定骨架占位。新增状态展示格式时，应在 `NetworkUiKitTest` 中覆盖 pending、正常数据和边界数据，避免 loading 中间帧再次回归。

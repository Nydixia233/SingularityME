# Network Terminal 权限行不可编辑与授权后目标玩家未刷新

## 错误现象

Network Terminal 权限页中，添加玩家后需要先选中成员、再去底部点击 `B/C/I/E/S` 和保存，底部控件在紧凑界面里容易点不到；同时 owner 给其他玩家授权后，目标玩家的客户端可能仍然看不到或无法立即访问该私有网络。

## 触发场景

打开 Network Terminal，进入可管理的私有网络权限页：

- 成员列表中玩家名前显示“拥有者”或组合权限标记，右侧显示 `#id`，权限修改入口在底部编辑区。
- 通过玩家名添加或修改权限后，服务端只刷新发起操作的玩家，未主动刷新被授权玩家的 `PacketNetworkTabData`。

## 根本原因

- `src/main/java/com/github/singularityme/client/ui/NetworkTerminalUI.java:794` 的成员页仍保留“选中成员 + 底部编辑器 + 保存”的二段式交互，权限按钮不在成员行自身，导致玩家误以为列表里的权限不可点。
- 旧成员行把权限 badge 放在名字前，右侧放 `NetworkUiKit.idPill(playerID)`，信息优先级与实际操作不匹配。
- `src/main/java/com/github/singularityme/network/packet/PacketSetPermissions.java:46` 和 `src/main/java/com/github/singularityme/network/packet/PacketGrantPermissionByName.java:46` 写入权限后只给 requester 发送 `PacketNetworkTabData`，目标玩家客户端仍可能持有旧网络列表。
- `PacketNetworkTabData.resolvePlayerName` 只捕获 `RuntimeException`；在普通单测环境中 AE2 静态初始化可能抛出 `LinkageError`，导致 DTO 构造无法回落到 `#id`。

## 修复方案

- 成员行改为普通 `Flow` 行，左侧显示玩家名，右侧显示 owner badge 或 5 个独立权限胶囊，顺序固定为 `B C I E S`。
- 移除底部权限编辑器、保存按钮和成员 `#id` 胶囊；点击成员行右侧权限胶囊即刻发送 `PacketSetPermissions`。
- 新增 `NetworkUiKit.togglePermissionBit` 和 `Palette.PERMISSION_CHIP_W`，统一权限 bit 切换和胶囊稳定尺寸。
- `PacketSetPermissions` 与 `PacketGrantPermissionByName` 使用 `setPlayerPermissions` 返回值判断是否写入成功，成功后给目标在线玩家发送自己的 `PacketNetworkTabData`。
- 新增 `PacketNetworkTabData.PRESERVE_DEVICE_CONTEXT`，目标刷新只更新网络列表，不打断目标玩家设备分配页当前设备上下文和当前选中目标网络。
- `PacketNetworkTabData.resolvePlayerName` 和目标玩家查找捕获 `RuntimeException | LinkageError`，在 AE2 registry 不可用时稳定回落。

## 预防措施

- 权限列表这种高频管理动作应优先做成列表内联可点击控件，避免把核心操作藏到底部二段式编辑区。
- 授权类服务端包写入成功后应同时刷新 requester 与 target 的客户端快照；只刷新发起者会造成跨玩家状态不一致。
- DTO 构造中用于显示的玩家名解析必须是非关键路径，依赖外部 registry 时要能 fallback 到 `#id`。
- 回归测试同时覆盖 UI 控件树、权限 bit 切换、授权后目标玩家 DTO 可见性，以及成功写入后是否需要刷新目标玩家。

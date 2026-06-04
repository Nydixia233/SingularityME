# 无权限破坏被拒绝后客户端残留透明假方块

## 错误现象

无权限玩家破坏已分配到他人网络的奇点设备后，owner 视角设备仍存在；无权限玩家本机视角方块变透明/像空气，但走上去会被卡住或位置回弹。

## 触发场景

多人联机中，玩家尝试破坏自己没有 `BUILD` 权限的奇点网络设备。服务端通过 `removedByPlayer` 拒绝破坏，但发起玩家客户端已经做了本地挖掘预测，把方块临时移成空气。

## 根本原因

- `src/main/java/com/github/singularityme/block/BlockSingularityPartLike.java:68` 等 `removedByPlayer` 入口只在服务端调用 `SingularityPermissionHelper.checkBuild` 并 `return false`。
- `return false` 能阻止服务端真正删除方块，所以 owner 视角正确；但没有向破坏发起玩家重发 `S23PacketBlockChange`，导致该玩家客户端继续保留“空气外观”。
- 服务端实体碰撞仍按真实方块计算，于是出现“看起来透明、走上去卡住”的客户端/服务端状态不一致。

## 修复方案

- 新增 `src/main/java/com/github/singularityme/block/SingularityBlockSyncHelper.java`，在服务端拒绝破坏时向发起玩家重发 `S23PacketBlockChange`，并在存在 TileEntity 描述包时同步描述包。
- `BlockSingularityPartLike`、`BlockSingularityDrive`、`BlockSingularityPowerCore`、`BlockSingularityCraftingCore` 的权限拒绝分支统一调用该 helper 后再返回 `false`。
- 补充 `SingularityBlockSyncHelperTest`，锁定只在“服务端 + 可发包玩家”场景执行回滚同步。

## 预防措施

- 任何阻止客户端可预测操作（破坏、放置、旋转等）的服务端权限拒绝，都要考虑是否需要给发起客户端补发状态回滚包。
- 对 `removedByPlayer` 这类 Forge/Minecraft 双端入口，不能只看服务端最终状态；还要验证发起客户端是否被同步回真实状态。

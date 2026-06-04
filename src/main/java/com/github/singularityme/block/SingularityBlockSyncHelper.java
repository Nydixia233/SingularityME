package com.github.singularityme.block;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/** 奇点设备方块的服务端客户端状态重同步工具。 */
final class SingularityBlockSyncHelper {

    private SingularityBlockSyncHelper() {}

    /** 判断拒绝破坏后是否需要向发起玩家重发方块状态。 */
    static boolean shouldResyncDeniedBreak(final boolean serverSide, final boolean packetCapablePlayer) {
        return serverSide && packetCapablePlayer;
    }

    /** 服务端拒绝破坏时，回滚发起玩家客户端的方块预测移除状态。 */
    static void resyncDeniedBreak(final World world, final EntityPlayer player, final int x, final int y, final int z) {
        if (world == null
            || !shouldResyncDeniedBreak(!world.isRemote, player instanceof EntityPlayerMP)) {
            return;
        }
        final EntityPlayerMP serverPlayer = (EntityPlayerMP) player;
        serverPlayer.playerNetServerHandler.sendPacket(new S23PacketBlockChange(x, y, z, world));

        final TileEntity te = world.getTileEntity(x, y, z);
        if (te != null) {
            final Packet packet = te.getDescriptionPacket();
            if (packet != null) {
                serverPlayer.playerNetServerHandler.sendPacket(packet);
            }
        }
    }
}

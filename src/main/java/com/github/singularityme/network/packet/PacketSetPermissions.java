package com.github.singularityme.network.packet;

import net.minecraft.entity.player.EntityPlayerMP;

import com.github.singularityme.core.PermissionBits;
import com.github.singularityme.core.SingularityNetworkRegistry;
import com.github.singularityme.network.SingularityChannel;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** 客户端请求设置某个非 owner 玩家在网络中的 AE2 权限位。 */
public class PacketSetPermissions implements IMessage {

    public int networkID;
    public int targetPlayerID;
    public int permissionBits;

    public PacketSetPermissions() {}

    public PacketSetPermissions(final int networkID, final int targetPlayerID, final int permissionBits) {
        this.networkID = networkID;
        this.targetPlayerID = targetPlayerID;
        this.permissionBits = permissionBits;
    }

    @Override
    public void fromBytes(final ByteBuf buf) {
        this.networkID = buf.readInt();
        this.targetPlayerID = buf.readInt();
        this.permissionBits = buf.readInt();
    }

    @Override
    public void toBytes(final ByteBuf buf) {
        buf.writeInt(this.networkID);
        buf.writeInt(this.targetPlayerID);
        buf.writeInt(this.permissionBits);
    }

    public static final class Handler implements IMessageHandler<PacketSetPermissions, IMessage> {

        @Override
        public IMessage onMessage(final PacketSetPermissions msg, final MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            final int playerID = NetworkTabPacketHelper.getPlayerID(player);
            if (playerID < 0) return null;
            final SingularityNetworkRegistry registry = NetworkTabPacketHelper.getRegistry(player);
            final boolean changed = registry.setPlayerPermissions(
                msg.networkID,
                playerID,
                msg.targetPlayerID,
                PermissionBits.fromBits(msg.permissionBits));
            if (NetworkTabPacketHelper.shouldSendPermissionRefresh(changed, playerID, msg.targetPlayerID)) {
                NetworkTabPacketHelper.sendPermissionRefresh(registry, playerID, msg.targetPlayerID);
            }
            SingularityChannel.CHANNEL.sendTo(new PacketNetworkTabData(registry, playerID, 0), player);
            return null;
        }
    }
}

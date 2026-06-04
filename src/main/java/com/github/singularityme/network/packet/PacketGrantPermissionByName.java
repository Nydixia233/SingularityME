package com.github.singularityme.network.packet;

import net.minecraft.entity.player.EntityPlayerMP;

import com.github.singularityme.core.PermissionBits;
import com.github.singularityme.core.SingularityNetworkRegistry;
import com.github.singularityme.network.SingularityChannel;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** 客户端按在线玩家名添加授权，服务端解析玩家 ID 并写入权限位。 */
public class PacketGrantPermissionByName implements IMessage {

    public int networkID;
    public String playerName = "";
    public int permissionBits = PermissionBits.DEFAULT_MEMBER_BITS;

    public PacketGrantPermissionByName() {}

    public PacketGrantPermissionByName(final int networkID, final String playerName, final int permissionBits) {
        this.networkID = networkID;
        this.playerName = playerName == null ? "" : playerName;
        this.permissionBits = permissionBits;
    }

    @Override
    public void fromBytes(final ByteBuf buf) {
        this.networkID = buf.readInt();
        this.playerName = PacketSetNetworkSettings.readString(buf);
        this.permissionBits = buf.readInt();
    }

    @Override
    public void toBytes(final ByteBuf buf) {
        buf.writeInt(this.networkID);
        PacketSetNetworkSettings.writeString(buf, this.playerName);
        buf.writeInt(this.permissionBits);
    }

    public static final class Handler implements IMessageHandler<PacketGrantPermissionByName, IMessage> {

        @Override
        public IMessage onMessage(final PacketGrantPermissionByName msg, final MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            final int playerID = NetworkTabPacketHelper.getPlayerID(player);
            if (playerID < 0) return null;

            final SingularityNetworkRegistry registry = NetworkTabPacketHelper.getRegistry(player);
            final EntityPlayerMP target = player.mcServer.getConfigurationManager()
                .func_152612_a(msg.playerName.trim());
            if (target != null) {
                final int targetID = NetworkTabPacketHelper.getPlayerID(target);
                if (targetID >= 0) {
                    final boolean changed = registry.setPlayerPermissions(
                        msg.networkID,
                        playerID,
                        targetID,
                        PermissionBits.fromBits(msg.permissionBits));
                    if (NetworkTabPacketHelper.shouldSendPermissionRefresh(changed, playerID, targetID)) {
                        NetworkTabPacketHelper.sendPermissionRefresh(registry, playerID, targetID);
                    }
                }
            }
            SingularityChannel.CHANNEL.sendTo(new PacketNetworkTabData(registry, playerID, 0), player);
            return null;
        }
    }
}

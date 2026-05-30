package com.github.singularityme.network.packet;

import net.minecraft.entity.player.EntityPlayerMP;

import com.github.singularityme.core.AccessLevel;
import com.github.singularityme.core.SingularityNetworkRegistry;
import com.github.singularityme.network.SingularityChannel;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class PacketSetMemberRole implements IMessage {

    public int networkID;
    public int targetPlayerID;
    public int accessLevelOrdinal;

    public PacketSetMemberRole() {}

    public PacketSetMemberRole(final int networkID, final int targetPlayerID, final int accessLevelOrdinal) {
        this.networkID = networkID;
        this.targetPlayerID = targetPlayerID;
        this.accessLevelOrdinal = accessLevelOrdinal;
    }

    @Override
    public void fromBytes(final ByteBuf buf) {
        this.networkID = buf.readInt();
        this.targetPlayerID = buf.readInt();
        this.accessLevelOrdinal = buf.readInt();
    }

    @Override
    public void toBytes(final ByteBuf buf) {
        buf.writeInt(this.networkID);
        buf.writeInt(this.targetPlayerID);
        buf.writeInt(this.accessLevelOrdinal);
    }

    public static final class Handler implements IMessageHandler<PacketSetMemberRole, IMessage> {

        @Override
        public IMessage onMessage(final PacketSetMemberRole msg, final MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            final int playerID = NetworkTabPacketHelper.getPlayerID(player);
            if (playerID < 0) return null;
            final SingularityNetworkRegistry registry = NetworkTabPacketHelper.getRegistry(player);
            final AccessLevel role = AccessLevel.fromOrdinal(msg.accessLevelOrdinal);
            if (role == AccessLevel.NONE) {
                registry.removeMember(msg.networkID, playerID, msg.targetPlayerID);
            } else {
                registry.setMemberRole(msg.networkID, playerID, msg.targetPlayerID, role);
            }
            SingularityChannel.CHANNEL.sendTo(new PacketNetworkTabData(registry, playerID, 0), player);
            return null;
        }
    }
}

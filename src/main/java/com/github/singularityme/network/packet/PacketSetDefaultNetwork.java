package com.github.singularityme.network.packet;

import net.minecraft.entity.player.EntityPlayerMP;

import com.github.singularityme.core.SingularityNetworkRegistry;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class PacketSetDefaultNetwork implements IMessage {

    public int networkID;

    public PacketSetDefaultNetwork() {}

    public PacketSetDefaultNetwork(final int networkID) {
        this.networkID = networkID;
    }

    @Override
    public void fromBytes(final ByteBuf buf) {
        this.networkID = buf.readInt();
    }

    @Override
    public void toBytes(final ByteBuf buf) {
        buf.writeInt(this.networkID);
    }

    public static final class Handler implements IMessageHandler<PacketSetDefaultNetwork, IMessage> {

        @Override
        public IMessage onMessage(final PacketSetDefaultNetwork msg, final MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            final int playerID = NetworkTabPacketHelper.getPlayerID(player);
            if (playerID < 0) return null;

            final SingularityNetworkRegistry registry = NetworkTabPacketHelper.getRegistry(player);
            if (msg.networkID == 0 || registry.canAccess(msg.networkID, playerID)) {
                registry.setDefaultNetworkID(playerID, msg.networkID);
            }
            NetworkTabPacketHelper.sendNetworkTabData(player, 0);
            return null;
        }
    }
}

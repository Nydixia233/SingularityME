package com.github.singularityme.network.packet;

import net.minecraft.entity.player.EntityPlayerMP;

import com.github.singularityme.core.SingularityNetworkRegistry;
import com.github.singularityme.network.SingularityChannel;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class PacketDeleteNetwork implements IMessage {

    public int networkID;

    public PacketDeleteNetwork() {}

    public PacketDeleteNetwork(final int networkID) {
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

    public static final class Handler implements IMessageHandler<PacketDeleteNetwork, IMessage> {

        @Override
        public IMessage onMessage(final PacketDeleteNetwork msg, final MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            final int playerID = NetworkTabPacketHelper.getPlayerID(player);
            if (playerID < 0) return null;
            final SingularityNetworkRegistry registry = NetworkTabPacketHelper.getRegistry(player);
            registry.deleteNetwork(msg.networkID, playerID);
            SingularityChannel.CHANNEL.sendTo(new PacketNetworkTabData(registry, playerID, 0), player);
            return null;
        }
    }
}

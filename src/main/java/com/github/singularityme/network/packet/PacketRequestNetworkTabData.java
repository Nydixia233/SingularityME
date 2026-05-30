package com.github.singularityme.network.packet;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class PacketRequestNetworkTabData implements IMessage {

    public int x;
    public int y;
    public int z;
    public int dim;

    public PacketRequestNetworkTabData() {}

    public PacketRequestNetworkTabData(final int x, final int y, final int z, final int dim) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.dim = dim;
    }

    @Override
    public void fromBytes(final ByteBuf buf) {
        this.x = buf.readInt();
        this.y = buf.readInt();
        this.z = buf.readInt();
        this.dim = buf.readInt();
    }

    @Override
    public void toBytes(final ByteBuf buf) {
        buf.writeInt(this.x);
        buf.writeInt(this.y);
        buf.writeInt(this.z);
        buf.writeInt(this.dim);
    }

    public static final class Handler implements IMessageHandler<PacketRequestNetworkTabData, IMessage> {

        @Override
        public IMessage onMessage(final PacketRequestNetworkTabData msg, final MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            NetworkTabPacketHelper.sendNetworkTabDataForLocation(player, msg.dim, msg.x, msg.y, msg.z);
            return null;
        }
    }
}

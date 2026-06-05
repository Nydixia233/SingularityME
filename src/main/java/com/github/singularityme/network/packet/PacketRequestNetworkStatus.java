package com.github.singularityme.network.packet;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** 客户端请求当前选中网络的设备与能量状态。 */
public class PacketRequestNetworkStatus implements IMessage {

    public int networkID;

    public PacketRequestNetworkStatus() {}

    public PacketRequestNetworkStatus(final int networkID) {
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

    /** 服务端处理状态请求，并按 networkID 回发只读快照。 */
    public static final class Handler implements IMessageHandler<PacketRequestNetworkStatus, IMessage> {

        @Override
        public IMessage onMessage(final PacketRequestNetworkStatus msg, final MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            NetworkTabPacketHelper.sendNetworkStatus(player, msg.networkID);
            return null;
        }
    }
}

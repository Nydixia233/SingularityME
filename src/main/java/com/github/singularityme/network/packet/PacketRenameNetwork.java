package com.github.singularityme.network.packet;

import net.minecraft.entity.player.EntityPlayerMP;

import com.github.singularityme.core.SingularityNetworkRegistry;
import com.github.singularityme.network.SingularityChannel;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class PacketRenameNetwork implements IMessage {

    public int networkID;
    public String name = "";

    public PacketRenameNetwork() {}

    public PacketRenameNetwork(final int networkID, final String name) {
        this.networkID = networkID;
        this.name = name == null ? "" : name;
    }

    @Override
    public void fromBytes(final ByteBuf buf) {
        this.networkID = buf.readInt();
        this.name = PacketSetNetworkSettings.readString(buf);
    }

    @Override
    public void toBytes(final ByteBuf buf) {
        buf.writeInt(this.networkID);
        PacketSetNetworkSettings.writeString(buf, this.name);
    }

    public static final class Handler implements IMessageHandler<PacketRenameNetwork, IMessage> {

        @Override
        public IMessage onMessage(final PacketRenameNetwork msg, final MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            final int playerID = NetworkTabPacketHelper.getPlayerID(player);
            if (playerID < 0) return null;
            final SingularityNetworkRegistry registry = NetworkTabPacketHelper.getRegistry(player);
            final String trimmed = msg.name.trim();
            if (!trimmed.isEmpty()) {
                registry.renameNetwork(msg.networkID, playerID, trimmed);
            }
            SingularityChannel.CHANNEL.sendTo(new PacketNetworkTabData(registry, playerID, 0), player);
            return null;
        }
    }
}

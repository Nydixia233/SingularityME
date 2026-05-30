package com.github.singularityme.network.packet;

import net.minecraft.entity.player.EntityPlayerMP;

import com.github.singularityme.core.SecurityLevel;
import com.github.singularityme.core.SingularityNetworkRegistry;
import com.github.singularityme.network.SingularityChannel;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class PacketSetNetworkSettings implements IMessage {

    public int networkID;
    public int color;
    public int securityOrdinal;
    public String passwordHash = "";

    public PacketSetNetworkSettings() {}

    public PacketSetNetworkSettings(final int networkID, final int color, final int securityOrdinal,
        final String passwordHash) {
        this.networkID = networkID;
        this.color = color & 0xFFFFFF;
        this.securityOrdinal = securityOrdinal;
        this.passwordHash = passwordHash == null ? "" : passwordHash;
    }

    @Override
    public void fromBytes(final ByteBuf buf) {
        this.networkID = buf.readInt();
        this.color = buf.readInt();
        this.securityOrdinal = buf.readInt();
        this.passwordHash = readString(buf);
    }

    @Override
    public void toBytes(final ByteBuf buf) {
        buf.writeInt(this.networkID);
        buf.writeInt(this.color & 0xFFFFFF);
        buf.writeInt(this.securityOrdinal);
        writeString(buf, this.passwordHash);
    }

    static String readString(final ByteBuf buf) {
        final int len = buf.readShort();
        final byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    static void writeString(final ByteBuf buf, final String value) {
        final byte[] bytes = (value == null ? "" : value).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.writeShort(bytes.length);
        buf.writeBytes(bytes);
    }

    public static final class Handler implements IMessageHandler<PacketSetNetworkSettings, IMessage> {

        @Override
        public IMessage onMessage(final PacketSetNetworkSettings msg, final MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            final int playerID = NetworkTabPacketHelper.getPlayerID(player);
            if (playerID < 0) return null;
            final SingularityNetworkRegistry registry = NetworkTabPacketHelper.getRegistry(player);
            registry.setNetworkSettings(
                msg.networkID,
                playerID,
                msg.color,
                SecurityLevel.fromOrdinal(msg.securityOrdinal),
                msg.passwordHash);
            SingularityChannel.CHANNEL.sendTo(new PacketNetworkTabData(registry, playerID, 0), player);
            return null;
        }
    }
}

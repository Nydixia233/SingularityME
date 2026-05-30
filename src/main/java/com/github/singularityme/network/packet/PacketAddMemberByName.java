package com.github.singularityme.network.packet;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import com.github.singularityme.core.AccessLevel;
import com.github.singularityme.core.SingularityNetworkRegistry;
import com.github.singularityme.network.SingularityChannel;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class PacketAddMemberByName implements IMessage {

    public int networkID;
    public String playerName = "";

    public PacketAddMemberByName() {}

    public PacketAddMemberByName(final int networkID, final String playerName) {
        this.networkID = networkID;
        this.playerName = playerName == null ? "" : playerName;
    }

    @Override
    public void fromBytes(final ByteBuf buf) {
        this.networkID = buf.readInt();
        this.playerName = PacketSetNetworkSettings.readString(buf);
    }

    @Override
    public void toBytes(final ByteBuf buf) {
        buf.writeInt(this.networkID);
        PacketSetNetworkSettings.writeString(buf, this.playerName);
    }

    public static final class Handler implements IMessageHandler<PacketAddMemberByName, IMessage> {

        @Override
        public IMessage onMessage(final PacketAddMemberByName msg, final MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            final int playerID = NetworkTabPacketHelper.getPlayerID(player);
            if (playerID < 0) return null;
            final SingularityNetworkRegistry registry = NetworkTabPacketHelper.getRegistry(player);
            final EntityPlayerMP target = MinecraftServer.getServer()
                .getConfigurationManager()
                .func_152612_a(msg.playerName.trim());
            if (target != null) {
                final int targetID = NetworkTabPacketHelper.getPlayerID(target);
                if (targetID >= 0) {
                    registry.setMemberRole(msg.networkID, playerID, targetID, AccessLevel.MEMBER);
                }
            }
            SingularityChannel.CHANNEL.sendTo(new PacketNetworkTabData(registry, playerID, 0), player);
            return null;
        }
    }
}

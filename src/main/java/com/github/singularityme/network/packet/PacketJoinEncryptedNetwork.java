package com.github.singularityme.network.packet;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.github.singularityme.core.SingularityNetworkRegistry;
import com.github.singularityme.network.SingularityChannel;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Client to server packet for joining an encrypted Singularity network.
 */
public class PacketJoinEncryptedNetwork implements IMessage {

    public int x;
    public int y;
    public int z;
    public int dim;
    public int networkID;
    public String passwordHash = "";

    public PacketJoinEncryptedNetwork() {}

    public PacketJoinEncryptedNetwork(final int x, final int y, final int z, final int dim, final int networkID,
        final String passwordHash) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.dim = dim;
        this.networkID = networkID;
        this.passwordHash = passwordHash == null ? "" : passwordHash;
    }

    @Override
    public void fromBytes(final ByteBuf buf) {
        this.x = buf.readInt();
        this.y = buf.readInt();
        this.z = buf.readInt();
        this.dim = buf.readInt();
        this.networkID = buf.readInt();
        final int hashLen = buf.readShort();
        final byte[] hashBytes = new byte[hashLen];
        buf.readBytes(hashBytes);
        this.passwordHash = new String(hashBytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    @Override
    public void toBytes(final ByteBuf buf) {
        buf.writeInt(this.x);
        buf.writeInt(this.y);
        buf.writeInt(this.z);
        buf.writeInt(this.dim);
        buf.writeInt(this.networkID);
        final byte[] hashBytes = this.passwordHash.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.writeShort(hashBytes.length);
        buf.writeBytes(hashBytes);
    }

    public static final class Handler implements IMessageHandler<PacketJoinEncryptedNetwork, IMessage> {

        @Override
        public IMessage onMessage(final PacketJoinEncryptedNetwork msg, final MessageContext ctx) {
            handle(msg, ctx.getServerHandler().playerEntity);
            return null;
        }

        private static void handle(final PacketJoinEncryptedNetwork msg, final EntityPlayerMP player) {
            final World world = NetworkTabPacketHelper.getLoadedWorld(msg.dim);
            if (world == null) {
                NetworkTabPacketHelper.sendNetworkTabData(player, 0);
                return;
            }

            final int playerID = NetworkTabPacketHelper.getPlayerID(player);
            if (playerID < 0) return;

            final SingularityNetworkRegistry registry = NetworkTabPacketHelper.getRegistry(player);
            registry.joinEncryptedNetwork(msg.networkID, playerID, msg.passwordHash);

            final TileEntity te = NetworkTabPacketHelper.getTileEntityIfLoaded(world, msg.x, msg.y, msg.z);
            final int deviceNetworkID = NetworkTabPacketHelper.getDeviceNetworkID(te);
            SingularityChannel.CHANNEL.sendTo(new PacketNetworkTabData(registry, playerID, deviceNetworkID), player);
        }
    }
}

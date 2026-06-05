package com.github.singularityme.network.packet;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

import com.github.singularityme.core.SecurityLevel;
import com.github.singularityme.core.SingularityNetworkRegistry;
import com.github.singularityme.network.SingularityChannel;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Client → Server packet: player wants to create a new named network.
 *
 * <p>
 * Payload: int x, int y, int z, int dim (device location for reply), String name
 *
 * <p>
 * On success the server creates the network and replies with a fresh
 * {@link PacketNetworkTabData} so the GUI updates immediately.
 */
public class PacketCreateNetwork implements IMessage {

    public int x;
    public int y;
    public int z;
    public int dim;
    public String name = "";
    public int color = SingularityNetworkRegistry.DEFAULT_COLOR;
    public int securityOrdinal = SecurityLevel.PRIVATE.ordinal();

    public PacketCreateNetwork() {}

    public PacketCreateNetwork(final int x, final int y, final int z, final int dim, final String name) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.dim = dim;
        this.name = name;
    }

    public PacketCreateNetwork(final int x, final int y, final int z, final int dim, final String name, final int color,
        final int securityOrdinal) {
        this(x, y, z, dim, name);
        this.color = color & 0xFFFFFF;
        this.securityOrdinal = securityOrdinal;
    }

    @Override
    public void fromBytes(final ByteBuf buf) {
        this.x = buf.readInt();
        this.y = buf.readInt();
        this.z = buf.readInt();
        this.dim = buf.readInt();
        final int nameLen = buf.readShort();
        final byte[] nameBytes = new byte[nameLen];
        buf.readBytes(nameBytes);
        this.name = new String(nameBytes, java.nio.charset.StandardCharsets.UTF_8);
        this.color = buf.readInt();
        this.securityOrdinal = buf.readInt();
    }

    @Override
    public void toBytes(final ByteBuf buf) {
        buf.writeInt(this.x);
        buf.writeInt(this.y);
        buf.writeInt(this.z);
        buf.writeInt(this.dim);
        final byte[] nameBytes = this.name.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.writeShort(nameBytes.length);
        buf.writeBytes(nameBytes);
        buf.writeInt(this.color & 0xFFFFFF);
        buf.writeInt(this.securityOrdinal);
    }

    // ---- Handler (runs on SERVER) ----

    public static final class Handler implements IMessageHandler<PacketCreateNetwork, IMessage> {

        @Override
        public IMessage onMessage(final PacketCreateNetwork msg, final MessageContext ctx) {
            // FML server-side packet handlers run on the server thread — call directly
            handle(msg, ctx.getServerHandler().playerEntity);
            return null;
        }

        private static void handle(final PacketCreateNetwork msg, final EntityPlayerMP player) {
            final World world = NetworkTabPacketHelper.getLoadedWorld(msg.dim);
            if (world == null) {
                NetworkTabPacketHelper.sendNetworkTabData(player, 0);
                return;
            }

            final int playerID = NetworkTabPacketHelper.getPlayerID(player);
            if (playerID < 0) return;

            final String trimmedName = msg.name.trim();
            if (trimmedName.isEmpty()) {
                NetworkTabPacketHelper.sendNetworkTabDataForLocation(player, msg.dim, msg.x, msg.y, msg.z);
                return;
            }

            final SingularityNetworkRegistry registry = NetworkTabPacketHelper.getRegistry(player);
            registry.createNetwork(
                playerID,
                trimmedName,
                msg.color,
                SecurityLevel.fromOrdinal(msg.securityOrdinal));

            // Get the current networkID of the device so the reply packet is accurate
            final net.minecraft.tileentity.TileEntity te = NetworkTabPacketHelper
                .getTileEntityIfLoaded(world, msg.x, msg.y, msg.z);
            final int deviceNetworkID = NetworkTabPacketHelper.getDeviceNetworkID(te);

            // Reply with updated network list
            final PacketNetworkTabData reply = new PacketNetworkTabData(registry, playerID, deviceNetworkID);
            SingularityChannel.CHANNEL.sendTo(reply, player);
        }
    }
}

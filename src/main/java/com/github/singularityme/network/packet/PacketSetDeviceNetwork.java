package com.github.singularityme.network.packet;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.github.singularityme.core.SingularityNetworkRegistry;
import com.github.singularityme.tile.ISingularityNetworkDevice;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Client → Server packet: player has selected a new network for a device.
 *
 * <p>
 * Payload: int x, int y, int z, int dim, int newNetworkID
 *
 * <p>
 * The server validates that the requesting player has access to the target network
 * before applying the change.
 */
public class PacketSetDeviceNetwork implements IMessage {

    public int x;
    public int y;
    public int z;
    public int dim;
    public int newNetworkID;

    public PacketSetDeviceNetwork() {}

    public PacketSetDeviceNetwork(final int x, final int y, final int z, final int dim, final int newNetworkID) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.dim = dim;
        this.newNetworkID = newNetworkID;
    }

    @Override
    public void fromBytes(final ByteBuf buf) {
        this.x = buf.readInt();
        this.y = buf.readInt();
        this.z = buf.readInt();
        this.dim = buf.readInt();
        this.newNetworkID = buf.readInt();
    }

    @Override
    public void toBytes(final ByteBuf buf) {
        buf.writeInt(this.x);
        buf.writeInt(this.y);
        buf.writeInt(this.z);
        buf.writeInt(this.dim);
        buf.writeInt(this.newNetworkID);
    }

    // ---- Handler (runs on SERVER) ----

    public static final class Handler implements IMessageHandler<PacketSetDeviceNetwork, IMessage> {

        @Override
        public IMessage onMessage(final PacketSetDeviceNetwork msg, final MessageContext ctx) {
            // FML server-side packet handlers run on the server thread — call directly
            handle(msg, ctx.getServerHandler().playerEntity);
            return null;
        }

        private static void handle(final PacketSetDeviceNetwork msg, final EntityPlayerMP player) {
            final World world = NetworkTabPacketHelper.getLoadedWorld(msg.dim);
            if (world == null) {
                NetworkTabPacketHelper.sendNetworkTabData(player, 0);
                return;
            }

            final TileEntity te = NetworkTabPacketHelper.getTileEntityIfLoaded(world, msg.x, msg.y, msg.z);
            final int currentNetworkID = NetworkTabPacketHelper.getDeviceNetworkID(te);
            if (!(te instanceof ISingularityNetworkDevice device)) {
                NetworkTabPacketHelper.sendNetworkTabData(player, currentNetworkID);
                return;
            }

            final int playerID = NetworkTabPacketHelper.getPlayerID(player);
            if (playerID < 0) return;

            final SingularityNetworkRegistry registry = NetworkTabPacketHelper.getRegistry(player);
            if (msg.newNetworkID < 0 || (msg.newNetworkID != 0 && !registry.canAccess(msg.newNetworkID, playerID))) {
                NetworkTabPacketHelper.sendNetworkTabData(player, currentNetworkID);
                return;
            }

            device.setNetworkID(msg.newNetworkID);
            NetworkTabPacketHelper.sendNetworkTabData(player, device.getNetworkID());
        }
    }
}

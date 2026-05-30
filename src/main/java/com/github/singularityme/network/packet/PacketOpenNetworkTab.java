package com.github.singularityme.network.packet;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.github.singularityme.SingularityME;
import com.github.singularityme.gui.ContainerSingularityNetworkTab;
import com.github.singularityme.tile.ISingularityNetworkDevice;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Client-to-server request for opening a device Network tab.
 *
 * <p>
 * Forge 1.7.10 opens only a local GUI when FMLNetworkHandler.openGui is called
 * from a client-side GUI. The server must perform this switch so the client and
 * server agree on the active container.
 */
public class PacketOpenNetworkTab implements IMessage {

    public int x;
    public int y;
    public int z;
    public int dim;

    public PacketOpenNetworkTab() {}

    public PacketOpenNetworkTab(final int x, final int y, final int z, final int dim) {
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

    public static final class Handler implements IMessageHandler<PacketOpenNetworkTab, IMessage> {

        @Override
        public IMessage onMessage(final PacketOpenNetworkTab msg, final MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            final World world = NetworkTabPacketHelper.getLoadedWorld(msg.dim);
            if (world == null) {
                NetworkTabPacketHelper.sendNetworkTabData(player, 0);
                return null;
            }

            final TileEntity te = NetworkTabPacketHelper.getTileEntityIfLoaded(world, msg.x, msg.y, msg.z);
            final int currentNetworkID = NetworkTabPacketHelper.getDeviceNetworkID(te);
            if (!(te instanceof ISingularityNetworkDevice)) {
                NetworkTabPacketHelper.sendNetworkTabData(player, currentNetworkID);
                return null;
            }

            player.openGui(SingularityME.instance, ContainerSingularityNetworkTab.GUI_ID, world, msg.x, msg.y, msg.z);
            return null;
        }
    }
}

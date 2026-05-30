package com.github.singularityme.network.packet;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

import com.github.singularityme.core.SingularityNetworkRegistry;
import com.github.singularityme.network.SingularityChannel;
import com.github.singularityme.tile.ISingularityNetworkDevice;

import appeng.api.AEApi;

public final class NetworkTabPacketHelper {

    private NetworkTabPacketHelper() {}

    public static int getPlayerID(final EntityPlayerMP player) {
        return AEApi.instance()
            .registries()
            .players()
            .getID(player);
    }

    public static World getLoadedWorld(final int dim) {
        return DimensionManager.getWorld(dim);
    }

    public static SingularityNetworkRegistry getRegistry(final EntityPlayerMP player) {
        World registryWorld = null;
        try {
            final MinecraftServer server = MinecraftServer.getServer();
            if (server != null) {
                registryWorld = server.worldServerForDimension(0);
            }
        } catch (final RuntimeException ignored) {}
        if (registryWorld == null) {
            registryWorld = player.getServerForPlayer();
        }
        return SingularityNetworkRegistry.get(registryWorld);
    }

    public static TileEntity getTileEntityIfLoaded(final World world, final int x, final int y, final int z) {
        if (world == null || !world.blockExists(x, y, z)) return null;
        return world.getTileEntity(x, y, z);
    }

    public static int getDeviceNetworkID(final TileEntity te) {
        if (te instanceof ISingularityNetworkDevice device) return device.getNetworkID();
        return 0;
    }

    public static void sendNetworkTabData(final EntityPlayerMP player, final int deviceNetworkID) {
        final int playerID = getPlayerID(player);
        if (playerID < 0) return;
        SingularityChannel.CHANNEL
            .sendTo(new PacketNetworkTabData(getRegistry(player), playerID, deviceNetworkID), player);
    }

    public static void sendNetworkTabDataForLocation(final EntityPlayerMP player, final int dim, final int x,
        final int y, final int z) {
        final World world = getLoadedWorld(dim);
        final TileEntity te = getTileEntityIfLoaded(world, x, y, z);
        sendNetworkTabData(player, getDeviceNetworkID(te));
    }
}

package com.github.singularityme.network.packet;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

import com.github.singularityme.core.SingularityNetworkManager;
import com.github.singularityme.core.SingularityNetworkRegistry;
import com.github.singularityme.core.SingularityNetworkRegistry.NetworkMeta;
import com.github.singularityme.grid.PhantomSingularityNode;
import com.github.singularityme.grid.SingularityGrid;
import com.github.singularityme.network.SingularityChannel;
import com.github.singularityme.tile.ISingularityNetworkDevice;

import appeng.api.AEApi;
import appeng.api.networking.IGridHost;
import appeng.me.GridNode;

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

    public static void sendNetworkStatus(final EntityPlayerMP player, final int networkID) {
        final int playerID = getPlayerID(player);
        if (playerID < 0) return;

        final SingularityNetworkRegistry registry = getRegistry(player);
        final SingularityGrid grid;
        if (networkID == 0) {
            grid = SingularityNetworkManager.INSTANCE.getGridForPlayer(playerID);
        } else {
            if (!registry.canUseNetwork(networkID, playerID)) {
                sendEmptyNetworkStatus(player, networkID);
                return;
            }
            final NetworkMeta meta = registry.getNetwork(networkID);
            if (meta == null) {
                sendEmptyNetworkStatus(player, networkID);
                return;
            }
            grid = SingularityNetworkManager.INSTANCE.getGridForPlayer(meta.ownerPlayerID, networkID);
        }

        if (grid == null) {
            sendEmptyNetworkStatus(player, networkID);
            return;
        }

        final List<PacketNetworkStatus.DeviceInfo> devices = new ArrayList<>();
        for (final GridNode node : grid.getAdoptedNodeSnapshot()) {
            final IGridHost machine = node.getMachine();
            if (!(machine instanceof TileEntity te)) continue;
            final World world = te.getWorldObj();
            final int dim = world == null || world.provider == null ? 0 : world.provider.dimensionId;
            devices.add(
                new PacketNetworkStatus.DeviceInfo(
                    machine.getClass()
                        .getSimpleName(),
                    te.xCoord,
                    te.yCoord,
                    te.zCoord,
                    dim,
                    true));
        }
        for (final PhantomSingularityNode phantom : grid.getPhantomNodeSnapshot()) {
            devices.add(
                new PacketNetworkStatus.DeviceInfo(
                    phantom.deviceType,
                    phantom.x,
                    phantom.y,
                    phantom.z,
                    phantom.dim,
                    false));
        }
        devices.sort(
            Comparator.comparing((PacketNetworkStatus.DeviceInfo info) -> !info.loaded)
                .thenComparing(info -> info.type)
                .thenComparingInt(info -> info.dim)
                .thenComparingInt(info -> info.x)
                .thenComparingInt(info -> info.y)
                .thenComparingInt(info -> info.z));

        SingularityChannel.CHANNEL.sendTo(
            new PacketNetworkStatus(networkID, grid.getVirtualAECurrentPower(), grid.getVirtualAEMaxPower(), devices),
            player);
    }

    private static void sendEmptyNetworkStatus(final EntityPlayerMP player, final int networkID) {
        SingularityChannel.CHANNEL.sendTo(new PacketNetworkStatus(networkID, 0.0, 0.0, new ArrayList<>()), player);
    }
}

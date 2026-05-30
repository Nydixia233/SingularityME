package com.github.singularityme.tile;

import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.github.singularityme.core.SingularityNetworkRegistry;

public final class SingularityNetworkDefaults {

    public static final String NBT_KEY = "singularityDefaultNetworkApplied";

    private SingularityNetworkDefaults() {}

    public static int resolveDefaultNetworkID(final TileEntity te, final int playerID) {
        if (te == null || playerID < 0) return 0;
        final World world = te.getWorldObj();
        if (world == null || world.isRemote) return 0;

        final World registryWorld = getOverworld(world);
        if (registryWorld == null) return 0;
        return SingularityNetworkRegistry.get(registryWorld)
            .resolveAccessibleDefaultNetworkID(playerID);
    }

    private static World getOverworld(final World fallback) {
        try {
            final MinecraftServer server = MinecraftServer.getServer();
            if (server != null) {
                final World overworld = server.worldServerForDimension(0);
                if (overworld != null) return overworld;
            }
        } catch (final RuntimeException ignored) {}
        return fallback;
    }
}

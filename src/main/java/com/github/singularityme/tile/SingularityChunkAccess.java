package com.github.singularityme.tile;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.util.ForgeDirection;

final class SingularityChunkAccess {

    private SingularityChunkAccess() {}

    static boolean isTileStillLoaded(final TileEntity te) {
        if (te == null || te.isInvalid()) return false;
        final World world = te.getWorldObj();
        if (world == null || world.isRemote) return false;
        if (!world.blockExists(te.xCoord, te.yCoord, te.zCoord)) return false;
        return world.getTileEntity(te.xCoord, te.yCoord, te.zCoord) == te;
    }

    static boolean isHostChunkNetworkAccessible(final TileEntity host) {
        if (!isTileStillLoaded(host)) return false;
        return isChunkNetworkAccessible(host.getWorldObj(), host.xCoord >> 4, host.zCoord >> 4);
    }

    static boolean isAdjacentTargetChunkNetworkAccessible(final TileEntity host, final ForgeDirection facing) {
        if (!isTileStillLoaded(host)) return false;
        return isChunkNetworkAccessible(
            host.getWorldObj(),
            (host.xCoord + facing.offsetX) >> 4,
            (host.zCoord + facing.offsetZ) >> 4);
    }

    static TileEntity getLoadedAdjacentTileIfAccessible(final TileEntity host, final ForgeDirection facing) {
        if (!isHostChunkNetworkAccessible(host) || !isAdjacentTargetChunkNetworkAccessible(host, facing)) return null;
        return getLoadedAdjacentTile(host, facing);
    }

    static TileEntity getLoadedAdjacentTile(final TileEntity host, final ForgeDirection facing) {
        if (!isTileStillLoaded(host)) return null;

        final World world = host.getWorldObj();
        final int targetX = host.xCoord + facing.offsetX;
        final int targetY = host.yCoord + facing.offsetY;
        final int targetZ = host.zCoord + facing.offsetZ;
        if (!world.blockExists(targetX, targetY, targetZ)) return null;

        final TileEntity target = world.getTileEntity(targetX, targetY, targetZ);
        return isTileStillLoaded(target) ? target : null;
    }

    static boolean isChunkNetworkAccessible(final World world, final int chunkX, final int chunkZ) {
        if (world == null || world.isRemote
            || !world.getChunkProvider()
                .chunkExists(chunkX, chunkZ)) {
            return false;
        }
        return isChunkWatched(world, chunkX, chunkZ) || isSpawnChunk(world, chunkX, chunkZ)
            || countForgeTickets(world, chunkX, chunkZ) > 0;
    }

    private static boolean isChunkWatched(final World world, final int chunkX, final int chunkZ) {
        return world instanceof WorldServer serverWorld && serverWorld.getPlayerManager()
            .func_152621_a(chunkX, chunkZ);
    }

    private static boolean isSpawnChunk(final World world, final int chunkX, final int chunkZ) {
        if (world.provider == null || !world.provider.canRespawnHere()) return false;
        if (!DimensionManager.shouldLoadSpawn(world.provider.dimensionId)) return false;
        final int centerX = (chunkX << 4) + 8;
        final int centerZ = (chunkZ << 4) + 8;
        return Math.abs(centerX - world.getSpawnPoint().posX) <= 128
            && Math.abs(centerZ - world.getSpawnPoint().posZ) <= 128;
    }

    private static int countForgeTickets(final World world, final int chunkX, final int chunkZ) {
        try {
            final var tickets = ForgeChunkManager.getPersistentChunksFor(world)
                .get(new ChunkCoordIntPair(chunkX, chunkZ));
            return tickets == null ? 0 : tickets.size();
        } catch (final RuntimeException ignored) {
            return 0;
        }
    }
}

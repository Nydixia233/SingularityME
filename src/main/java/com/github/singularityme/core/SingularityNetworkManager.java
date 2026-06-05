package com.github.singularityme.core;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.singularityme.grid.PhantomSingularityNode;
import com.github.singularityme.grid.SingularityGrid;
import com.github.singularityme.tile.ISingularityContributionHost;
import com.github.singularityme.tile.TileSingularityPowerCore;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.me.GridNode;

/**
 * Server-level singleton that maps each {@link NetworkKey} to its {@link SingularityGrid}.
 *
 * <p>
 * All ME devices assigned to the same registry network are redirected to the
 * corresponding owner grid here. {@code networkID = 0} is the unassigned sentinel
 * and never joins a runtime grid.
 */
public enum SingularityNetworkManager {

    INSTANCE;

    private static final Logger LOG = LogManager.getLogger("SingularityME");

    // NetworkKey (ownerPlayerID + networkID) -> runtime SingularityGrid
    private final Map<NetworkKey, SingularityGrid> grids = new ConcurrentHashMap<>();
    private volatile int lastPrunedStaleNodeCount = 0;

    public void onServerStarting() {
        grids.clear();
        // Pre-warm grids from persisted data so SingularityGrid objects exist
        // before the first chunk loads. Nodes will be adopted as chunks load via onReady().
        try {
            final MinecraftServer server = MinecraftServer.getServer();
            if (server != null) {
                final World overworld = server.worldServerForDimension(0);
                if (overworld != null) {
                    final SingularityNetworkData data = SingularityNetworkData.get(overworld);
                    final SingularityNetworkRegistry registry = SingularityNetworkRegistry.get(overworld);
                    int prewarmed = 0;
                    for (final NetworkKey key : data.getKnownNetworkKeys()) {
                        if (key.networkID == 0 || registry.getNetwork(key.networkID) == null) continue;
                        getOrCreateGrid(key);
                        prewarmed++;
                    }
                    if (prewarmed > 0) {
                        LOG.info("[SingularityME] Pre-warmed {} SingularityGrid(s) from persisted data.", prewarmed);
                    }
                }
            }
        } catch (final Exception e) {
            LOG.warn("[SingularityME] Failed to pre-warm SingularityGrids on server start.", e);
        }
    }

    public void onServerStopping() {
        for (SingularityGrid grid : grids.values()) {
            grid.destroy();
        }
        grids.clear();
    }

    /**
     * Called when an AE2 tile entity's proxy becomes ready (onReady hook).
     * Ensures the node is assigned to the correct SingularityGrid.
     *
     * @param playerID  AE2 internal player ID (from {@code GridNode.getPlayerID()})
     * @param networkID per-player network index (0 = unassigned)
     * @param node      the GridNode to register
     */
    public int registerNode(final int playerID, final int networkID, final GridNode node) {
        if (playerID < 0 || networkID == 0 || node == null) return -1;

        final int gridOwnerID = resolveGridOwnerID(playerID, networkID, getWorldFromNode(node));
        if (gridOwnerID < 0) return -1;

        final NetworkKey key = new NetworkKey(gridOwnerID, networkID);
        final boolean isNew = !grids.containsKey(key);
        final SingularityGrid grid = grids.computeIfAbsent(key, k -> new SingularityGrid(gridOwnerID, networkID));
        if (isNew) {
            LOG.info(
                "[SingularityME] Created new SingularityGrid for playerID={} networkID={}",
                gridOwnerID,
                networkID);
        }

        // Remove any phantom that was standing in for this device while the chunk was unloaded.
        removePhantomForNode(grid, node);

        grid.adoptNode(node);
        LOG.info(
            "[SingularityME] Adopted node {} into SingularityGrid (playerID={}, networkID={}, totalNodes={})",
            node,
            gridOwnerID,
            networkID,
            grid.getAdoptedNodeCount());

        // Persist device location so the grid can be pre-warmed on next server start.
        persistDeviceRecord(key, node, true);
        return gridOwnerID;
    }

    /**
     * Legacy overload for callers that have not been updated to pass a network ID.
     * It resolves to the unassigned sentinel and therefore does not join a grid.
     */
    public int registerNode(final int playerID, final GridNode node) {
        return registerNode(playerID, 0, node);
    }

    /**
     * Called when an AE2 tile entity is removed or chunk-unloaded.
     *
     * @param playerID  AE2 internal player ID
     * @param networkID per-player network index
     * @param node      the GridNode to unregister
     * @param permanent true when the device is being destroyed (invalidate path),
     *                  false when the chunk is merely unloading (the device still exists on disk)
     */
    public void unregisterNode(final int playerID, final int networkID, final GridNode node, final boolean permanent) {
        unregisterNodeForOwner(playerID, networkID, node, permanent);
    }

    public void unregisterNodeForOwner(final int gridOwnerID, final int networkID, final GridNode node,
        final boolean permanent) {
        unregisterNodeForOwner(gridOwnerID, networkID, node, permanent, null);
    }

    private void unregisterNodeForOwner(final int gridOwnerID, final int networkID, final GridNode node,
        final boolean permanent, final World worldHint) {
        if (gridOwnerID < 0 || networkID == 0 || node == null) return;

        final NetworkKey key = new NetworkKey(gridOwnerID, networkID);
        final SingularityGrid[] gridToDestroy = new SingularityGrid[1];
        final SingularityGrid[] nodeOwner = new SingularityGrid[1];
        final boolean[] detachedNode = new boolean[1];
        grids.compute(key, (k, grid) -> {
            if (grid == null) return null;
            if (!permanent && grid.hasNode(node)) {
                insertPhantomForNode(k, grid, node, worldHint);
            }
            detachedNode[0] = grid.detachNode(node);
            if (detachedNode[0]) {
                nodeOwner[0] = grid;
            }
            if (grid.getAdoptedNodeCount() == 0) {
                gridToDestroy[0] = grid;
                return null;
            }
            return grid;
        });

        if (detachedNode[0] && nodeOwner[0] != null) {
            nodeOwner[0].destroyDetachedNode(node);
        }

        if (gridToDestroy[0] != null) {
            gridToDestroy[0].destroy();
            LOG.info(
                "[SingularityME] Released empty SingularityGrid for playerID={} networkID={}",
                gridOwnerID,
                networkID);
        }

        // Only remove the persisted record when the device is permanently destroyed.
        if (permanent) {
            persistDeviceRecord(key, node, false);
        }
    }

    /**
     * Legacy overload for the unassigned sentinel (networkID=0).
     */
    public void unregisterNode(final int playerID, final GridNode node, final boolean permanent) {
        unregisterNode(playerID, 0, node, permanent);
    }

    /**
     * Backwards-compatible overload — treats the call as permanent (invalidate path).
     */
    public void unregisterNode(final int playerID, final GridNode node) {
        unregisterNode(playerID, 0, node, true);
    }

    /**
     * Defensive cleanup for abrupt dimension/world unloads where AE2 may destroy
     * GridNodes without the owning tile completing its normal unload path first.
     */
    public void onWorldUnload(final World world) {
        if (world == null || world.isRemote) return;

        for (final Map.Entry<NetworkKey, SingularityGrid> entry : new ArrayList<>(grids.entrySet())) {
            final NetworkKey key = entry.getKey();
            final SingularityGrid grid = entry.getValue();
            if (grid == null) continue;

            for (final GridNode node : grid.getAdoptedNodeSnapshot()) {
                if (!isNodeInWorld(node, world)) continue;

                retireContribution(node);
                // World unload is not permanent — the device still exists on disk.
                unregisterNodeForOwner(key.playerID, key.networkID, node, false, world);
            }
        }
    }

    /**
     * Periodic safety net for stale TileEntity-backed nodes that survive normal chunk-unload callbacks.
     */
    public int pruneInvalidNodes() {
        int pruned = 0;

        for (final Map.Entry<NetworkKey, SingularityGrid> entry : new ArrayList<>(grids.entrySet())) {
            final NetworkKey key = entry.getKey();
            final SingularityGrid grid = entry.getValue();
            if (grid == null) continue;

            for (final GridNode node : grid.getAdoptedNodeSnapshot()) {
                if (!isNodeStale(node)) continue;

                retireContribution(node);
                unregisterNode(key.playerID, key.networkID, node, true);
                pruned++;
            }
        }

        this.lastPrunedStaleNodeCount = pruned;
        if (pruned > 0) {
            LOG.info("[SingularityME] Pruned {} stale SingularityGrid node(s)", pruned);
        }
        return pruned;
    }

    public int countStaleNodes(final SingularityGrid grid) {
        if (grid == null) return 0;
        int stale = 0;
        for (final GridNode node : grid.getAdoptedNodeSnapshot()) {
            if (isNodeStale(node)) {
                stale++;
            }
        }
        return stale;
    }

    public int getLastPrunedStaleNodeCount() {
        return this.lastPrunedStaleNodeCount;
    }

    public boolean isNodeStale(final GridNode node) {
        if (node == null) return true;
        try {
            final IGridHost machine = node.getMachine();
            if (machine == null) return true;
            if (machine instanceof TileEntity te) {
                return !isTileEntityStillLoaded(te);
            }
            return false;
        } catch (final RuntimeException e) {
            LOG.warn("[SingularityME] Failed to inspect SingularityGrid node liveness: {}", node, e);
            return true;
        }
    }

    private boolean isTileEntityStillLoaded(final TileEntity te) {
        if (te == null || te.isInvalid()) return false;
        final World world = te.getWorldObj();
        if (world == null || world.isRemote) return false;
        if (!world.blockExists(te.xCoord, te.yCoord, te.zCoord)) return false;
        return world.getTileEntity(te.xCoord, te.yCoord, te.zCoord) == te;
    }

    private boolean isNodeInWorld(final GridNode node, final World world) {
        if (node == null) return false;
        try {
            return node.getWorld() == world;
        } catch (final RuntimeException e) {
            LOG.warn("[SingularityME] Failed to inspect SingularityGrid node world during unload: {}", node, e);
            return false;
        }
    }

    private void retireContribution(final GridNode node) {
        try {
            final IGridHost machine = node.getMachine();
            if (machine instanceof ISingularityContributionHost host && host.isContributionLoaded()) {
                host.retireSingularityContribution();
            }
        } catch (final RuntimeException e) {
            LOG.warn("[SingularityME] Failed to retire Singularity contribution during world unload: {}", node, e);
        }
    }

    // ---- Grid accessors ----

    /** Returns the grid for the given key, or {@code null} if none exists. */
    public SingularityGrid getGrid(final NetworkKey key) {
        return grids.get(key);
    }

    /** Legacy lookup for the unassigned sentinel; normally returns {@code null}. */
    public SingularityGrid getGridForPlayer(final int playerID) {
        if (playerID < 0) return null;
        return grids.get(NetworkKey.defaultFor(playerID));
    }

    /** Returns the grid for the given player+network, or {@code null} if none exists. */
    public SingularityGrid getGridForPlayer(final int playerID, final int networkID) {
        if (playerID < 0 || networkID == 0) return null;
        return grids.get(new NetworkKey(playerID, networkID));
    }

    public SingularityGrid getOrCreateGrid(final NetworkKey key) {
        if (key.playerID < 0 || key.networkID == 0) return null;

        final boolean isNew = !grids.containsKey(key);
        final SingularityGrid grid = grids.computeIfAbsent(key, k -> new SingularityGrid(k.playerID, k.networkID));
        if (isNew) {
            LOG.info(
                "[SingularityME] Created new SingularityGrid for playerID={} networkID={}",
                key.playerID,
                key.networkID);
        }
        grid.ensureInternalGrid();
        return grid;
    }

    /** Legacy creation path for the unassigned sentinel; {@link #getOrCreateGrid} rejects it. */
    public SingularityGrid getOrCreateGridForPlayer(final int playerID) {
        return getOrCreateGrid(NetworkKey.defaultFor(playerID));
    }

    public boolean hasGrid(final int playerID) {
        if (playerID < 0) return false;
        return grids.containsKey(NetworkKey.defaultFor(playerID));
    }

    public boolean hasGrid(final int playerID, final int networkID) {
        if (playerID < 0 || networkID == 0) return false;
        return grids.containsKey(new NetworkKey(playerID, networkID));
    }

    public int resolveGridOwnerID(final int devicePlayerID, final int networkID, final World world) {
        if (devicePlayerID < 0 || networkID == 0) return -1;
        final World overworld = getOverworld(world);
        if (overworld == null) return devicePlayerID;
        final SingularityNetworkRegistry.NetworkMeta meta = SingularityNetworkRegistry.get(overworld)
            .getNetwork(networkID);
        return meta == null ? devicePlayerID : meta.ownerPlayerID;
    }

    public World getWorldFromNode(final GridNode node) {
        if (node == null) return null;
        try {
            final IGridHost machine = node.getMachine();
            if (machine instanceof TileEntity te) return te.getWorldObj();
            return node.getWorld();
        } catch (final RuntimeException ignored) {
            return null;
        }
    }

    /**
     * Returns true if the given AE2 Grid is the internal grid of any SingularityGrid.
     * Used by MixinPathGridCache to bypass the ad-hoc channel limit.
     */
    public boolean isSingularityGrid(final IGrid grid) {
        return getSingularityGrid(grid) != null;
    }

    public SingularityGrid getSingularityGrid(final IGrid grid) {
        if (grid == null) return null;
        for (SingularityGrid sg : grids.values()) {
            if (sg.getInternalGrid() == grid) return sg;
        }
        return null;
    }

    public void refreshPowerCoreContribution(final TileSingularityPowerCore core) {
        final SingularityGrid grid = getGridForPowerCore(core);
        if (grid == null) return;
        grid.refreshPowerCoreContribution(core);
    }

    private SingularityGrid getGridForPowerCore(final TileSingularityPowerCore core) {
        if (core == null || core.getNetworkID() == 0) return null;

        int ownerID = core.getGridOwnerPlayerID();
        if (ownerID < 0) {
            try {
                final IGridNode node = core.getProxy()
                    .getNode();
                final int playerID = node instanceof GridNode gridNode ? gridNode.getPlayerID() : -1;
                ownerID = resolveGridOwnerID(playerID, core.getNetworkID(), core.getWorldObj());
            } catch (final RuntimeException ignored) {
                ownerID = -1;
            }
        }

        return ownerID < 0 ? null : getGridForPlayer(ownerID, core.getNetworkID());
    }

    /**
     * Records or removes a device entry in {@link SingularityNetworkData}.
     *
     * @param record true to add the record, false to remove it.
     */
    private void persistDeviceRecord(final NetworkKey key, final GridNode node, final boolean record) {
        try {
            final IGridHost machine = node.getMachine();
            if (!(machine instanceof TileEntity te)) return;
            final World world = te.getWorldObj();
            if (world == null || world.isRemote) return;
            // Always persist to the overworld's map storage regardless of which dimension the TE is in.
            final World overworld = getOverworld(world);
            if (overworld == null) return;
            final SingularityNetworkData data = SingularityNetworkData.get(overworld);
            final int dim = world.provider.dimensionId;
            if (record) {
                data.recordDevice(key, te.xCoord, te.yCoord, te.zCoord, dim);
            } else {
                data.removeDevice(key, te.xCoord, te.yCoord, te.zCoord, dim);
            }
        } catch (final Exception e) {
            LOG.warn("[SingularityME] Failed to persist device record for key={}", key, e);
        }
    }

    private static World getOverworld(final World anyWorld) {
        try {
            final MinecraftServer server = MinecraftServer.getServer();
            return server == null ? null : server.worldServerForDimension(0);
        } catch (final Exception e) {
            return null;
        }
    }

    /**
     * Creates a {@link PhantomSingularityNode} for the given node and inserts it into the grid.
     * Called just before the real node is removed on chunk/world unload.
     */
    private void insertPhantomForNode(final NetworkKey key, final SingularityGrid grid, final GridNode node,
        final World worldHint) {
        try {
            final IGridHost machine = node.getMachine();
            if (!(machine instanceof TileEntity te)) return;
            final World world = worldHint == null ? te.getWorldObj() : worldHint;
            if (world == null) return;
            final int dim = world.provider.dimensionId;
            final String deviceType = machine.getClass()
                .getSimpleName();
            final PhantomSingularityNode phantom = new PhantomSingularityNode(
                key.playerID,
                te.xCoord,
                te.yCoord,
                te.zCoord,
                dim,
                deviceType);
            grid.addPhantom(phantom);
            LOG.debug(
                "[SingularityME] Inserted phantom for {} (playerID={} networkID={})",
                phantom,
                key.playerID,
                key.networkID);
        } catch (final Exception e) {
            LOG.warn("[SingularityME] Failed to insert phantom for node during unregister.", e);
        }
    }

    /**
     * Removes any phantom that was standing in for the given node's position.
     * Called when the real node is re-registered on chunk reload.
     */
    private void removePhantomForNode(final SingularityGrid grid, final GridNode node) {
        try {
            final IGridHost machine = node.getMachine();
            if (!(machine instanceof TileEntity te)) return;
            final World world = te.getWorldObj();
            if (world == null) return;
            final int dim = world.provider.dimensionId;
            grid.removePhantom(te.xCoord, te.yCoord, te.zCoord, dim);
        } catch (final Exception e) {
            LOG.warn("[SingularityME] Failed to remove phantom for node on re-registration.", e);
        }
    }
}

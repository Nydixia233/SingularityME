package com.github.singularityme.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.singularityme.grid.SingularityGrid;

import appeng.api.networking.IGrid;
import appeng.me.GridNode;

/**
 * Server-level singleton that maps each player ID to their SingularityGrid.
 * All ME devices owned by the same player are redirected to their grid here.
 */
public enum SingularityNetworkManager {

    INSTANCE;

    private static final Logger LOG = LogManager.getLogger("SingularityME");

    // playerID (AE2 internal int) -> their global SingularityGrid
    private final Map<Integer, SingularityGrid> playerGrids = new ConcurrentHashMap<>();

    public void onServerStarting() {
        playerGrids.clear();
    }

    public void onServerStopping() {
        for (SingularityGrid grid : playerGrids.values()) {
            grid.destroy();
        }
        playerGrids.clear();
    }

    /**
     * Called when an AE2 tile entity's proxy becomes ready (onReady hook).
     * Ensures the node is assigned to the player's SingularityGrid.
     */
    public void registerNode(int playerID, GridNode node) {
        if (playerID < 0) return;

        boolean isNew = !playerGrids.containsKey(playerID);
        SingularityGrid grid = playerGrids.computeIfAbsent(playerID, SingularityGrid::new);
        if (isNew) {
            LOG.info("[SingularityME] Created new SingularityGrid for playerID={}", playerID);
        }
        grid.adoptNode(node);
        LOG.info(
            "[SingularityME] Adopted node {} into SingularityGrid (playerID={}, totalNodes={})",
            node,
            playerID,
            grid.getAdoptedNodeCount());
    }

    /**
     * Called when an AE2 tile entity is removed or chunk-unloaded.
     */
    public void unregisterNode(int playerID, GridNode node) {
        SingularityGrid grid = playerGrids.get(playerID);
        if (grid != null) {
            grid.releaseNode(node);
        }
    }

    public SingularityGrid getGridForPlayer(int playerID) {
        return playerGrids.get(playerID);
    }

    public boolean hasGrid(int playerID) {
        return playerGrids.containsKey(playerID);
    }

    /**
     * Returns true if the given AE2 Grid is the internal grid of any player's SingularityGrid.
     * Used by MixinPathGridCache to bypass the ad-hoc channel limit.
     */
    public boolean isSingularityGrid(IGrid grid) {
        if (grid == null) return false;
        for (SingularityGrid sg : playerGrids.values()) {
            if (sg.getInternalGrid() == grid) return true;
        }
        return false;
    }
}

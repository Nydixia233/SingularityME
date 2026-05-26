package com.github.singularityme.tile;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.singularityme.core.SingularityNetworkManager;

import appeng.me.GridNode;
import appeng.tile.TileEvent;
import appeng.tile.events.TileEventType;
import appeng.tile.grid.AENetworkTile;

/**
 * Debug tile entity for Phase 0.5 verification.
 *
 * <p>
 * When placed (with a player owner set), it registers its AE2 node into the player's
 * SingularityGrid. Logs the active state once it first goes active, confirming that the energy
 * virtualisation in {@link com.github.singularityme.grid.SingularityAnchorNode} is working
 * correctly.
 *
 * <p>
 * Obtain via {@code /give <player> singularityme:singularity_probe 1 0}. No crafting recipe is
 * intentionally provided.
 */
public class TileSingularityProbe extends AENetworkTile {

    private static final Logger LOG = LogManager.getLogger("SingularityME");

    /** Set to true once we have logged the first active-state confirmation. */
    private boolean loggedActive = false;

    // ---- AE2 lifecycle hooks ----

    @Override
    public void onReady() {
        // super.onReady() calls proxy.onReady() which creates the GridNode and sets playerID
        // from the owner stored by Block.onBlockPlacedBy -> proxy.setOwner(player)
        super.onReady();

        if (worldObj.isRemote) return;

        GridNode node = (GridNode) getProxy().getNode();
        if (node == null) {
            LOG.warn("[SingularityME][Probe] onReady: node is null — proxy may not have initialised");
            return;
        }
        int playerID = node.getPlayerID();
        if (playerID < 0) {
            LOG.warn("[SingularityME][Probe] onReady: playerID={} — block was not placed by a real player?", playerID);
            return;
        }

        SingularityNetworkManager.INSTANCE.registerNode(playerID, node);
        LOG.info(
            "[SingularityME][Probe] onReady: registered node (playerID={}, isActive={})",
            playerID,
            getProxy().isActive());
    }

    @Override
    public void onChunkUnload() {
        unregister();
        super.onChunkUnload();
    }

    @Override
    public void invalidate() {
        unregister();
        super.invalidate();
    }

    /**
     * Poll isActive() until the energy grid settles (usually within 1-2 ticks).
     * AEBaseTile.updateEntity() is final; use @TileEvent(TileEventType.TICK) instead.
     */
    @TileEvent(TileEventType.TICK)
    public void tick() {
        if (worldObj.isRemote || loggedActive) return;

        if (getProxy().isActive()) {
            LOG.info(
                "[SingularityME][Probe] isActive=true — SingularityGrid energy virtualisation OK at ({},{},{})",
                xCoord,
                yCoord,
                zCoord);
            loggedActive = true;
        }
    }

    // ---- helpers ----

    private void unregister() {
        if (worldObj == null || worldObj.isRemote) return;
        GridNode node = (GridNode) getProxy().getNode();
        if (node != null) {
            SingularityNetworkManager.INSTANCE.unregisterNode(node.getPlayerID(), node);
        }
    }
}

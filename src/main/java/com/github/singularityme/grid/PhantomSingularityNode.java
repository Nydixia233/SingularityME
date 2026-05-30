package com.github.singularityme.grid;

/**
 * Lightweight placeholder for a Singularity device whose chunk has been unloaded.
 *
 * <p>
 * When a chunk unloads, the real {@link appeng.me.GridNode} is destroyed by AE2.
 * Rather than removing the device from the {@link SingularityGrid} entirely (which
 * would cause the grid to be destroyed if it was the last node), a PhantomSingularityNode
 * is inserted to keep the grid alive until the chunk reloads.
 *
 * <p>
 * Phase 1 goal: prevent {@link SingularityGrid} from being destroyed on chunk unload.
 * Phase 2 (future): store a storage snapshot so terminals can still see offline device contents.
 */
public final class PhantomSingularityNode {

    /** Unique key format used as map key: "dim:x:y:z". */
    public final String key;

    public final int playerID;
    public final int x;
    public final int y;
    public final int z;
    public final int dim;
    public final String deviceType;

    public PhantomSingularityNode(final int playerID, final int x, final int y, final int z, final int dim,
        final String deviceType) {
        this.playerID = playerID;
        this.x = x;
        this.y = y;
        this.z = z;
        this.dim = dim;
        this.deviceType = deviceType;
        this.key = dim + ":" + x + ":" + y + ":" + z;
    }

    @Override
    public String toString() {
        return "PhantomSingularityNode{playerID=" + playerID
            + ", pos=["
            + x
            + ","
            + y
            + ","
            + z
            + "] dim="
            + dim
            + ", type="
            + deviceType
            + "}";
    }
}

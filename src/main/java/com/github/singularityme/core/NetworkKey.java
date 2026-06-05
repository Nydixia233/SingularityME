package com.github.singularityme.core;

/**
 * Composite key that identifies a specific Singularity network.
 *
 * <p>
 * A network is uniquely identified by the combination of the registry owner
 * player ID and a registry-assigned network index. {@code networkID = 0} is the
 * unassigned sentinel and is not adopted into a runtime grid.
 *
 * <p>
 * Instances are immutable and safe to use as {@link java.util.HashMap} keys.
 */
public final class NetworkKey {

    /** AE2 internal player ID (from {@code GridNode.getPlayerID()}). */
    public final int playerID;

    /**
     * Registry network index. {@code 0} = unassigned sentinel; positive values are
     * assigned by {@link SingularityNetworkRegistry}.
     */
    public final int networkID;

    public NetworkKey(final int playerID, final int networkID) {
        this.playerID = playerID;
        this.networkID = networkID;
    }

    /** Convenience factory for the unassigned sentinel key of a player. */
    public static NetworkKey defaultFor(final int playerID) {
        return new NetworkKey(playerID, 0);
    }

    @Override
    public boolean equals(final Object o) {
        if (!(o instanceof NetworkKey other)) return false;
        return playerID == other.playerID && networkID == other.networkID;
    }

    @Override
    public int hashCode() {
        return 31 * playerID + networkID;
    }

    @Override
    public String toString() {
        return "NetworkKey{playerID=" + playerID + ", networkID=" + networkID + "}";
    }
}

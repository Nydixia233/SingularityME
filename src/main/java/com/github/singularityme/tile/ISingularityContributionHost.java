package com.github.singularityme.tile;

/**
 * Implemented by Singularity devices that contribute cached state to the player grid.
 *
 * <p>
 * World-unload cleanup uses this narrow contract so it can retire inventories,
 * patterns, and CPUs without knowing each tile's internal storage details.
 */
public interface ISingularityContributionHost {

    void retireSingularityContribution();

    boolean isContributionLoaded();
}

package com.github.singularityme.core;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import appeng.me.Grid;
import appeng.me.GridNode;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.tile.crafting.TileCraftingTile;

/**
 * Reflection helpers for accessing package-private AE2 internals.
 * AT opens these at runtime, but reflection is needed at compile time since the
 * dev jar does not reflect AT changes.
 */
public final class AEReflection {

    private static final Logger LOG = LogManager.getLogger("SingularityME");

    private static Method setGridMethod;
    private static Method addCraftingTileMethod;
    private static Field craftingAcceleratorField;

    static {
        try {
            setGridMethod = GridNode.class.getDeclaredMethod("setGrid", Grid.class);
            setGridMethod.setAccessible(true);
        } catch (NoSuchMethodException e) {
            LOG.error("[SingularityME] Failed to reflect GridNode.setGrid - mod will not function", e);
        }

        try {
            addCraftingTileMethod = CraftingCPUCluster.class.getDeclaredMethod("addTile", TileCraftingTile.class);
            addCraftingTileMethod.setAccessible(true);
            craftingAcceleratorField = CraftingCPUCluster.class.getDeclaredField("accelerator");
            craftingAcceleratorField.setAccessible(true);
        } catch (NoSuchMethodException | NoSuchFieldException e) {
            LOG.error("[SingularityME] Failed to reflect CraftingCPUCluster internals", e);
        }
    }

    public static void setGrid(final GridNode node, final Grid grid) {
        if (setGridMethod == null) return;
        try {
            setGridMethod.invoke(node, grid);
        } catch (Exception e) {
            LOG.error("[SingularityME] GridNode.setGrid reflection failed", e);
        }
    }

    public static boolean addCraftingTile(final CraftingCPUCluster cluster, final TileCraftingTile tile) {
        if (addCraftingTileMethod == null) return false;
        try {
            addCraftingTileMethod.invoke(cluster, tile);
            return true;
        } catch (Exception e) {
            LOG.error("[SingularityME] CraftingCPUCluster.addTile reflection failed", e);
            return false;
        }
    }

    public static void setCraftingAccelerators(final CraftingCPUCluster cluster, final int accelerators) {
        if (craftingAcceleratorField == null) return;
        try {
            craftingAcceleratorField.setInt(cluster, Math.max(0, accelerators));
        } catch (Exception e) {
            LOG.error("[SingularityME] CraftingCPUCluster.accelerator reflection failed", e);
        }
    }

    private AEReflection() {}
}

package com.github.singularityme.core;

import java.lang.reflect.Method;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import appeng.me.Grid;
import appeng.me.GridNode;

/**
 * Reflection helpers for accessing package-private AE2 internals.
 * AT opens these at runtime, but reflection is needed at compile time
 * since the dev jar doesn't reflect AT changes.
 */
public final class AEReflection {

    private static final Logger LOG = LogManager.getLogger("SingularityME");

    private static Method setGridMethod;

    static {
        try {
            setGridMethod = GridNode.class.getDeclaredMethod("setGrid", Grid.class);
            setGridMethod.setAccessible(true);
        } catch (NoSuchMethodException e) {
            LOG.error("[SingularityME] Failed to reflect GridNode.setGrid — mod will not function", e);
        }
    }

    public static void setGrid(GridNode node, Grid grid) {
        if (setGridMethod == null) return;
        try {
            setGridMethod.invoke(node, grid);
        } catch (Exception e) {
            LOG.error("[SingularityME] GridNode.setGrid reflection failed", e);
        }
    }

    private AEReflection() {}
}

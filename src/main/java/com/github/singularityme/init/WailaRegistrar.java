package com.github.singularityme.init;

import com.github.singularityme.tile.TileSingularityDrive;
import com.github.singularityme.tile.TileSingularityExportBus;
import com.github.singularityme.tile.TileSingularityImportBus;
import com.github.singularityme.tile.TileSingularityInterface;
import com.github.singularityme.tile.TileSingularityPowerCore;
import com.github.singularityme.tile.TileSingularityProbe;
import com.github.singularityme.tile.TileSingularityStorageBus;
import com.github.singularityme.tile.TileSingularityTerminal;

import mcp.mobius.waila.api.IWailaRegistrar;

/**
 * Static callback registered via FMLInterModComms so WAILA can call it during
 * its own initialisation phase.
 *
 * <p>
 * Registration in {@code CommonProxy.init()}:
 *
 * <pre>
 * FMLInterModComms.sendMessage("Waila", "register", "com.github.singularityme.init.WailaRegistrar.callbackRegister");
 * </pre>
 */
public final class WailaRegistrar {

    private WailaRegistrar() {}

    /**
     * Called by WAILA via reflection. Must be {@code public static void} and accept
     * exactly one {@link IWailaRegistrar} parameter.
     */
    public static void callbackRegister(final IWailaRegistrar registrar) {
        // Probe: full grid diagnostics
        registrar.registerBodyProvider(WailaSingularityProbeProvider.INSTANCE, TileSingularityProbe.class);
        registrar.registerNBTProvider(WailaSingularityProbeProvider.INSTANCE, TileSingularityProbe.class);

        // All other tiles: show facing direction
        for (Class<?> cls : new Class<?>[] { TileSingularityStorageBus.class, TileSingularityImportBus.class,
            TileSingularityExportBus.class, TileSingularityInterface.class, TileSingularityPowerCore.class,
            TileSingularityDrive.class, TileSingularityTerminal.class, }) {
            registrar.registerBodyProvider(WailaSingularityProbeProvider.INSTANCE, cls);
            registrar.registerNBTProvider(WailaSingularityProbeProvider.INSTANCE, cls);
        }
    }
}

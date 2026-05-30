package com.github.singularityme.init;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

import com.github.singularityme.core.SingularityNetworkManager;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public class EventHandler {

    private static final EventHandler INSTANCE = new EventHandler();
    private int pruneTickCounter = 0;

    public static void register() {
        MinecraftForge.EVENT_BUS.register(INSTANCE);
        FMLCommonHandler.instance()
            .bus()
            .register(INSTANCE);
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        // Nodes stay in the grid across logout — the grid persists server-side.
        // Nothing to do here for Phase 1; Phase 2 will handle cross-dimension cleanup.
    }

    @SubscribeEvent
    public void onWorldUnload(final WorldEvent.Unload event) {
        if (event.world == null || event.world.isRemote) return;
        SingularityNetworkManager.INSTANCE.onWorldUnload(event.world);
    }

    @SubscribeEvent
    public void onServerTick(final TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (++this.pruneTickCounter < 400) return;
        this.pruneTickCounter = 0;
        SingularityNetworkManager.INSTANCE.pruneInvalidNodes();
    }
}

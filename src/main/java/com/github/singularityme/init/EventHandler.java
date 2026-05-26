package com.github.singularityme.init;

import net.minecraftforge.common.MinecraftForge;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;

public class EventHandler {

    private static final EventHandler INSTANCE = new EventHandler();

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
}

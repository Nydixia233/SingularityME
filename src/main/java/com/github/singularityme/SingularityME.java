package com.github.singularityme;

import com.github.singularityme.core.SingularityNetworkManager;
import com.github.singularityme.init.EventHandler;
import com.github.singularityme.proxy.CommonProxy;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;

@Mod(
    modid = "singularityme",
    name = "Singularity ME",
    version = Tags.VERSION,
    dependencies = "required-after:appliedenergistics2;required-after:gregtech;required-after:gtnhlib")
public class SingularityME {

    @Mod.Instance("singularityme")
    public static SingularityME instance;

    @SidedProxy(
        clientSide = "com.github.singularityme.proxy.ClientProxy",
        serverSide = "com.github.singularityme.proxy.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
        EventHandler.register();
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        SingularityNetworkManager.INSTANCE.onServerStarting();
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        SingularityNetworkManager.INSTANCE.onServerStopping();
    }
}

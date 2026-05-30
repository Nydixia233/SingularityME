package com.github.singularityme.proxy;

import com.github.singularityme.client.render.RenderSingularityCraftingCore;
import com.github.singularityme.client.render.RenderSingularityCraftingCoreTESR;
import com.github.singularityme.client.render.RenderSingularityDrive;
import com.github.singularityme.client.render.RenderSingularityPartLikeBlock;
import com.github.singularityme.tile.TileSingularityCraftingCore;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        RenderSingularityPartLikeBlock.register();
        RenderSingularityDrive.register();
        RenderSingularityCraftingCore.register();
        ClientRegistry
            .bindTileEntitySpecialRenderer(TileSingularityCraftingCore.class, new RenderSingularityCraftingCoreTESR());
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);
    }
}

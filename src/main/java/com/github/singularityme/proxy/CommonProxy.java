package com.github.singularityme.proxy;

import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;

import com.github.singularityme.SingularityME;
import com.github.singularityme.block.BlockSingularityCraftingCore;
import com.github.singularityme.block.BlockSingularityCraftingTerminal;
import com.github.singularityme.block.BlockSingularityDrive;
import com.github.singularityme.block.BlockSingularityExportBus;
import com.github.singularityme.block.BlockSingularityImportBus;
import com.github.singularityme.block.BlockSingularityInterface;
import com.github.singularityme.block.BlockSingularityNetworkTerminal;
import com.github.singularityme.block.BlockSingularityPatternTerminal;
import com.github.singularityme.block.BlockSingularityPowerCore;
import com.github.singularityme.block.BlockSingularityProbe;
import com.github.singularityme.block.BlockSingularityStorageBus;
import com.github.singularityme.block.BlockSingularityTerminal;
import com.github.singularityme.gui.SingularityGuiHandler;
import com.github.singularityme.init.RecipeHandler;
import com.github.singularityme.network.SingularityChannel;
import com.github.singularityme.tile.TileSingularityCraftingCore;
import com.github.singularityme.tile.TileSingularityCraftingTerminal;
import com.github.singularityme.tile.TileSingularityDrive;
import com.github.singularityme.tile.TileSingularityExportBus;
import com.github.singularityme.tile.TileSingularityImportBus;
import com.github.singularityme.tile.TileSingularityInterface;
import com.github.singularityme.tile.TileSingularityNetworkTerminal;
import com.github.singularityme.tile.TileSingularityPatternTerminal;
import com.github.singularityme.tile.TileSingularityPowerCore;
import com.github.singularityme.tile.TileSingularityProbe;
import com.github.singularityme.tile.TileSingularityStorageBus;
import com.github.singularityme.tile.TileSingularityTerminal;

import appeng.api.AEApi;
import appeng.core.CreativeTab;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLInterModComms;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.registry.GameRegistry;

public class CommonProxy {

    public static BlockSingularityProbe blockProbe;
    public static BlockSingularityStorageBus blockStorageBus;
    public static BlockSingularityTerminal blockTerminal;
    public static BlockSingularityImportBus blockImportBus;
    public static BlockSingularityExportBus blockExportBus;
    public static BlockSingularityInterface blockInterface;
    public static BlockSingularityPowerCore blockPowerCore;
    public static BlockSingularityDrive blockDrive;
    public static BlockSingularityCraftingTerminal blockCraftingTerminal;
    public static BlockSingularityPatternTerminal blockPatternTerminal;
    public static BlockSingularityCraftingCore blockCraftingCore;
    public static BlockSingularityNetworkTerminal blockNetworkTerminal;

    public void preInit(FMLPreInitializationEvent event) {
        // Debug probe: no crafting recipe, obtain via /give.
        blockProbe = new BlockSingularityProbe();
        registerBlock(blockProbe, "singularity_probe");
        GameRegistry.registerTileEntity(TileSingularityProbe.class, "singularityme:singularity_probe");

        blockStorageBus = new BlockSingularityStorageBus();
        registerBlock(blockStorageBus, "singularity_storage_bus");
        GameRegistry.registerTileEntity(TileSingularityStorageBus.class, "singularityme:singularity_storage_bus");

        blockTerminal = new BlockSingularityTerminal();
        registerBlock(blockTerminal, "singularity_terminal");
        GameRegistry.registerTileEntity(TileSingularityTerminal.class, "singularityme:singularity_terminal");

        blockCraftingTerminal = new BlockSingularityCraftingTerminal();
        registerBlock(blockCraftingTerminal, "singularity_crafting_terminal");
        GameRegistry
            .registerTileEntity(TileSingularityCraftingTerminal.class, "singularityme:singularity_crafting_terminal");

        blockPatternTerminal = new BlockSingularityPatternTerminal();
        registerBlock(blockPatternTerminal, "singularity_pattern_terminal");
        GameRegistry
            .registerTileEntity(TileSingularityPatternTerminal.class, "singularityme:singularity_pattern_terminal");

        blockNetworkTerminal = new BlockSingularityNetworkTerminal();
        registerBlock(blockNetworkTerminal, "singularity_network_terminal");
        GameRegistry
            .registerTileEntity(TileSingularityNetworkTerminal.class, "singularityme:singularity_network_terminal");

        blockImportBus = new BlockSingularityImportBus();
        registerBlock(blockImportBus, "singularity_import_bus");
        GameRegistry.registerTileEntity(TileSingularityImportBus.class, "singularityme:singularity_import_bus");

        blockExportBus = new BlockSingularityExportBus();
        registerBlock(blockExportBus, "singularity_export_bus");
        GameRegistry.registerTileEntity(TileSingularityExportBus.class, "singularityme:singularity_export_bus");

        blockInterface = new BlockSingularityInterface();
        registerBlock(blockInterface, "singularity_interface");
        GameRegistry.registerTileEntity(TileSingularityInterface.class, "singularityme:singularity_interface");

        blockPowerCore = new BlockSingularityPowerCore();
        registerBlock(blockPowerCore, "singularity_power_core");
        GameRegistry.registerTileEntity(TileSingularityPowerCore.class, "singularityme:singularity_power_core");

        blockDrive = new BlockSingularityDrive();
        registerBlock(blockDrive, "singularity_drive");
        GameRegistry.registerTileEntity(TileSingularityDrive.class, "singularityme:singularity_drive");

        blockCraftingCore = new BlockSingularityCraftingCore();
        registerBlock(blockCraftingCore, "singularity_crafting_core");
        GameRegistry.registerTileEntity(TileSingularityCraftingCore.class, "singularityme:singularity_crafting_core");
    }

    private void registerBlock(final Block block, final String name) {
        if (CreativeTab.instance != null) {
            block.setCreativeTab(CreativeTab.instance);
        }
        GameRegistry.registerBlock(block, ItemBlock.class, name);
    }

    public void init(FMLInitializationEvent event) {
        NetworkRegistry.INSTANCE.registerGuiHandler(SingularityME.instance, SingularityGuiHandler.INSTANCE);
        SingularityChannel.register();
        FMLInterModComms
            .sendMessage("Waila", "register", "com.github.singularityme.init.WailaRegistrar.callbackRegister");
    }

    public void postInit(FMLPostInitializationEvent event) {
        RecipeHandler.registerRecipes();
    }

    public void loadComplete(FMLLoadCompleteEvent event) {
        AEApi.instance()
            .registries()
            .interfaceTerminal()
            .register(TileSingularityInterface.class);
        com.github.singularityme.init.SingularityUpgradeMirror.mirrorAll();
    }
}

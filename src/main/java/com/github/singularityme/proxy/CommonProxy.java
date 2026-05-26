package com.github.singularityme.proxy;

import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

import com.github.singularityme.SingularityME;
import com.github.singularityme.block.BlockSingularityDrive;
import com.github.singularityme.block.BlockSingularityExportBus;
import com.github.singularityme.block.BlockSingularityImportBus;
import com.github.singularityme.block.BlockSingularityInterface;
import com.github.singularityme.block.BlockSingularityPowerCore;
import com.github.singularityme.block.BlockSingularityProbe;
import com.github.singularityme.block.BlockSingularityStorageBus;
import com.github.singularityme.block.BlockSingularityTerminal;
import com.github.singularityme.gui.SingularityGuiHandler;
import com.github.singularityme.init.RecipeHandler;
import com.github.singularityme.tile.TileSingularityDrive;
import com.github.singularityme.tile.TileSingularityExportBus;
import com.github.singularityme.tile.TileSingularityImportBus;
import com.github.singularityme.tile.TileSingularityInterface;
import com.github.singularityme.tile.TileSingularityPowerCore;
import com.github.singularityme.tile.TileSingularityProbe;
import com.github.singularityme.tile.TileSingularityStorageBus;
import com.github.singularityme.tile.TileSingularityTerminal;

import appeng.api.config.Upgrades;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLInterModComms;
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

    public void preInit(FMLPreInitializationEvent event) {
        // Debug probe — no crafting recipe, obtain via /give
        blockProbe = new BlockSingularityProbe();
        GameRegistry.registerBlock(blockProbe, ItemBlock.class, "singularity_probe");
        GameRegistry.registerTileEntity(TileSingularityProbe.class, "singularityme:singularity_probe");

        // Singularity Storage Bus
        blockStorageBus = new BlockSingularityStorageBus();
        GameRegistry.registerBlock(blockStorageBus, ItemBlock.class, "singularity_storage_bus");
        GameRegistry.registerTileEntity(TileSingularityStorageBus.class, "singularityme:singularity_storage_bus");

        // Singularity Terminal
        blockTerminal = new BlockSingularityTerminal();
        GameRegistry.registerBlock(blockTerminal, ItemBlock.class, "singularity_terminal");
        GameRegistry.registerTileEntity(TileSingularityTerminal.class, "singularityme:singularity_terminal");

        // Singularity Import Bus
        blockImportBus = new BlockSingularityImportBus();
        GameRegistry.registerBlock(blockImportBus, ItemBlock.class, "singularity_import_bus");
        GameRegistry.registerTileEntity(TileSingularityImportBus.class, "singularityme:singularity_import_bus");

        // Singularity Export Bus
        blockExportBus = new BlockSingularityExportBus();
        GameRegistry.registerBlock(blockExportBus, ItemBlock.class, "singularity_export_bus");
        GameRegistry.registerTileEntity(TileSingularityExportBus.class, "singularityme:singularity_export_bus");

        // Singularity ME Interface
        blockInterface = new BlockSingularityInterface();
        GameRegistry.registerBlock(blockInterface, ItemBlock.class, "singularity_interface");
        GameRegistry.registerTileEntity(TileSingularityInterface.class, "singularityme:singularity_interface");

        // Singularity Power Core
        blockPowerCore = new BlockSingularityPowerCore();
        GameRegistry.registerBlock(blockPowerCore, ItemBlock.class, "singularity_power_core");
        GameRegistry.registerTileEntity(TileSingularityPowerCore.class, "singularityme:singularity_power_core");

        // Singularity Drive
        blockDrive = new BlockSingularityDrive();
        GameRegistry.registerBlock(blockDrive, ItemBlock.class, "singularity_drive");
        GameRegistry.registerTileEntity(TileSingularityDrive.class, "singularityme:singularity_drive");
    }

    public void init(FMLInitializationEvent event) {
        NetworkRegistry.INSTANCE.registerGuiHandler(SingularityME.instance, SingularityGuiHandler.INSTANCE);
        // Register WAILA tooltip provider for TileSingularityProbe
        FMLInterModComms
            .sendMessage("Waila", "register", "com.github.singularityme.init.WailaRegistrar.callbackRegister");
    }

    public void postInit(FMLPostInitializationEvent event) {
        RecipeHandler.registerRecipes();
        registerUpgrades();
    }

    private static void registerUpgrades() {
        // Import Bus
        Upgrades.CAPACITY.registerItem(new ItemStack(blockImportBus), 2);
        Upgrades.SPEED.registerItem(new ItemStack(blockImportBus), 4);
        Upgrades.SUPERSPEED.registerItem(new ItemStack(blockImportBus), 4);
        Upgrades.SUPERLUMINALSPEED.registerItem(new ItemStack(blockImportBus), 4);
        Upgrades.ORE_FILTER.registerItem(new ItemStack(blockImportBus), 1);
        Upgrades.FUZZY.registerItem(new ItemStack(blockImportBus), 1);
        Upgrades.REDSTONE.registerItem(new ItemStack(blockImportBus), 1);

        // Export Bus
        Upgrades.CAPACITY.registerItem(new ItemStack(blockExportBus), 2);
        Upgrades.SPEED.registerItem(new ItemStack(blockExportBus), 4);
        Upgrades.SUPERSPEED.registerItem(new ItemStack(blockExportBus), 4);
        Upgrades.SUPERLUMINALSPEED.registerItem(new ItemStack(blockExportBus), 4);
        Upgrades.ORE_FILTER.registerItem(new ItemStack(blockExportBus), 1);
        Upgrades.FUZZY.registerItem(new ItemStack(blockExportBus), 1);
        Upgrades.REDSTONE.registerItem(new ItemStack(blockExportBus), 1);
        Upgrades.CRAFTING.registerItem(new ItemStack(blockExportBus), 1);

        // Storage Bus
        Upgrades.CAPACITY.registerItem(new ItemStack(blockStorageBus), 5);
        Upgrades.FUZZY.registerItem(new ItemStack(blockStorageBus), 1);
        Upgrades.INVERTER.registerItem(new ItemStack(blockStorageBus), 1);
        Upgrades.STICKY.registerItem(new ItemStack(blockStorageBus), 1);
    }
}

package com.github.singularityme.init;

import static gregtech.api.util.GTRecipeBuilder.INGOTS;
import static gregtech.api.util.GTRecipeBuilder.MINUTES;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;
import static gregtech.api.util.GTRecipeConstants.AssemblyLine;
import static gregtech.api.util.GTRecipeConstants.RESEARCH_ITEM;
import static gregtech.api.util.GTRecipeConstants.SCANNING;

import net.minecraft.item.ItemStack;

import com.github.singularityme.proxy.CommonProxy;

import appeng.api.AEApi;
import appeng.api.definitions.IItemDefinition;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.enums.TierEU;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.recipe.Scanning;

/**
 * Registers GT assembly line recipes for all Singularity ME blocks.
 * All recipes are LuV tier (512V), researched from the corresponding AE2 part.
 */
public final class RecipeHandler {

    private RecipeHandler() {}

    /** Called from {@link CommonProxy#postInit}. */
    public static void registerRecipes() {
        registerStorageBus();
        registerTerminal();
        registerNetworkTerminal();
        registerCraftingTerminal();
        registerPatternTerminal();
        registerImportBus();
        registerExportBus();
        registerInterface();
        registerPowerCore();
        registerDrive();
        registerCraftingCore();
    }

    // ---- helpers ----

    /** AE2 part item: appliedenergistics2:item.ItemMultiPart at given damage. */
    private static ItemStack ae2Part(final int damage) {
        ItemStack s = GTModHandler.getModItem("appliedenergistics2", "item.ItemMultiPart", 1);
        if (s != null) s.setItemDamage(damage);
        return s;
    }

    private static ItemStack ae2Definition(final IItemDefinition definition) {
        return definition == null ? null
            : definition.maybeStack(1)
                .orNull();
    }

    private static ItemStack plate(final Materials mat, final int n) {
        return GTOreDictUnificator.get(OrePrefixes.plate, mat, n);
    }

    private static ItemStack plateDouble(final Materials mat, final int n) {
        return GTOreDictUnificator.get(OrePrefixes.plateDouble, mat, n);
    }

    private static ItemStack circuit(final int n) {
        // LuV tier circuit: Master Quantum Computer
        return ItemList.Circuit_Masterquantumcomputer.get(n);
    }

    private static ItemStack circuitIV(final int n) {
        return ItemList.Circuit_Elitenanocomputer.get(n);
    }

    // ---- recipes ----

    /**
     * 奇点存储总线 — researched from ME Storage Bus (damage 220).
     * Exposes an adjacent container to the player's global SingularityGrid.
     */
    private static void registerStorageBus() {
        ItemStack research = ae2Part(220); // ME Storage Bus
        if (research == null) return;

        GTValues.RA.stdBuilder()
            .metadata(RESEARCH_ITEM, research)
            .metadata(SCANNING, new Scanning(2 * MINUTES, TierEU.RECIPE_IV))
            .itemInputs(
                research.copy(),
                circuit(4),
                circuitIV(2),
                plate(Materials.Iridium, 4),
                plate(Materials.NaquadahAlloy, 4),
                ItemList.Field_Generator_LuV.get(1),
                ItemList.Emitter_LuV.get(2),
                GTOreDictUnificator.get(OrePrefixes.wireGt04, Materials.YttriumBariumCuprate, 4))
            .fluidInputs(Materials.SolderingAlloy.getMolten(4 * INGOTS), Materials.Naquadah.getMolten(2 * INGOTS))
            .itemOutputs(new ItemStack(CommonProxy.blockStorageBus, 1))
            .eut(TierEU.RECIPE_LuV)
            .duration(30 * SECONDS)
            .addTo(AssemblyLine);
    }

    /**
     * 奇点终端 — researched from ME Terminal (damage 380).
     * Provides a GUI to view and interact with the player's SingularityGrid.
     */
    private static void registerTerminal() {
        ItemStack research = ae2Part(380); // ME Terminal
        if (research == null) return;

        GTValues.RA.stdBuilder()
            .metadata(RESEARCH_ITEM, research)
            .metadata(SCANNING, new Scanning(2 * MINUTES, TierEU.RECIPE_IV))
            .itemInputs(
                research.copy(),
                circuit(4),
                circuitIV(2),
                plate(Materials.Iridium, 4),
                plateDouble(Materials.NaquadahAlloy, 2),
                ItemList.Sensor_LuV.get(2),
                ItemList.Emitter_LuV.get(2),
                GTOreDictUnificator.get(OrePrefixes.wireGt04, Materials.YttriumBariumCuprate, 4))
            .fluidInputs(Materials.SolderingAlloy.getMolten(4 * INGOTS), Materials.Naquadah.getMolten(2 * INGOTS))
            .itemOutputs(new ItemStack(CommonProxy.blockTerminal, 1))
            .eut(TierEU.RECIPE_LuV)
            .duration(30 * SECONDS)
            .addTo(AssemblyLine);
    }

    private static void registerNetworkTerminal() {
        ItemStack research = new ItemStack(CommonProxy.blockTerminal, 1);

        GTValues.RA.stdBuilder()
            .metadata(RESEARCH_ITEM, research)
            .metadata(SCANNING, new Scanning(2 * MINUTES, TierEU.RECIPE_IV))
            .itemInputs(
                research.copy(),
                circuit(6),
                circuitIV(4),
                plate(Materials.Iridium, 4),
                plateDouble(Materials.NaquadahAlloy, 2),
                ItemList.Sensor_LuV.get(4),
                ItemList.Emitter_LuV.get(2),
                GTOreDictUnificator.get(OrePrefixes.wireGt04, Materials.YttriumBariumCuprate, 8))
            .fluidInputs(Materials.SolderingAlloy.getMolten(6 * INGOTS), Materials.Naquadah.getMolten(3 * INGOTS))
            .itemOutputs(new ItemStack(CommonProxy.blockNetworkTerminal, 1))
            .eut(TierEU.RECIPE_ZPM)
            .duration(45 * SECONDS)
            .addTo(AssemblyLine);
    }

    private static void registerCraftingTerminal() {
        ItemStack research = ae2Definition(
            AEApi.instance()
                .definitions()
                .parts()
                .craftingTerminal());
        if (research == null) return;

        GTValues.RA.stdBuilder()
            .metadata(RESEARCH_ITEM, research)
            .metadata(SCANNING, new Scanning(2 * MINUTES, TierEU.RECIPE_IV))
            .itemInputs(
                research.copy(),
                new ItemStack(CommonProxy.blockTerminal, 1),
                circuit(4),
                circuitIV(2),
                plate(Materials.Iridium, 4),
                plateDouble(Materials.NaquadahAlloy, 2),
                ItemList.Sensor_LuV.get(2),
                ItemList.Emitter_LuV.get(2),
                GTOreDictUnificator.get(OrePrefixes.wireGt04, Materials.YttriumBariumCuprate, 4))
            .fluidInputs(Materials.SolderingAlloy.getMolten(4 * INGOTS), Materials.Naquadah.getMolten(2 * INGOTS))
            .itemOutputs(new ItemStack(CommonProxy.blockCraftingTerminal, 1))
            .eut(TierEU.RECIPE_LuV)
            .duration(30 * SECONDS)
            .addTo(AssemblyLine);
    }

    private static void registerPatternTerminal() {
        ItemStack research = ae2Definition(
            AEApi.instance()
                .definitions()
                .parts()
                .patternTerminal());
        if (research == null) return;

        GTValues.RA.stdBuilder()
            .metadata(RESEARCH_ITEM, research)
            .metadata(SCANNING, new Scanning(2 * MINUTES, TierEU.RECIPE_IV))
            .itemInputs(
                research.copy(),
                new ItemStack(CommonProxy.blockTerminal, 1),
                circuit(4),
                circuitIV(4),
                plate(Materials.Iridium, 4),
                plateDouble(Materials.NaquadahAlloy, 2),
                ItemList.Field_Generator_LuV.get(1),
                ItemList.Emitter_LuV.get(2),
                GTOreDictUnificator.get(OrePrefixes.wireGt04, Materials.YttriumBariumCuprate, 6))
            .fluidInputs(Materials.SolderingAlloy.getMolten(6 * INGOTS), Materials.Naquadah.getMolten(3 * INGOTS))
            .itemOutputs(new ItemStack(CommonProxy.blockPatternTerminal, 1))
            .eut(TierEU.RECIPE_LuV)
            .duration(45 * SECONDS)
            .addTo(AssemblyLine);
    }

    /**
     * 奇点输入总线 — researched from ME Import Bus (damage 240).
     * Pulls items from an adjacent container into the SingularityGrid.
     */
    private static void registerImportBus() {
        ItemStack research = ae2Part(240); // ME Import Bus
        if (research == null) return;

        GTValues.RA.stdBuilder()
            .metadata(RESEARCH_ITEM, research)
            .metadata(SCANNING, new Scanning(2 * MINUTES, TierEU.RECIPE_IV))
            .itemInputs(
                research.copy(),
                circuit(4),
                circuitIV(2),
                plate(Materials.Iridium, 4),
                plate(Materials.NaquadahAlloy, 4),
                ItemList.Electric_Pump_LuV.get(2),
                ItemList.Sensor_LuV.get(1),
                GTOreDictUnificator.get(OrePrefixes.wireGt04, Materials.YttriumBariumCuprate, 4))
            .fluidInputs(Materials.SolderingAlloy.getMolten(4 * INGOTS), Materials.Naquadah.getMolten(2 * INGOTS))
            .itemOutputs(new ItemStack(CommonProxy.blockImportBus, 1))
            .eut(TierEU.RECIPE_LuV)
            .duration(30 * SECONDS)
            .addTo(AssemblyLine);
    }

    /**
     * 奇点输出总线 — researched from ME Export Bus (damage 260).
     * Pushes items from the SingularityGrid into an adjacent container.
     */
    private static void registerExportBus() {
        ItemStack research = ae2Part(260); // ME Export Bus
        if (research == null) return;

        GTValues.RA.stdBuilder()
            .metadata(RESEARCH_ITEM, research)
            .metadata(SCANNING, new Scanning(2 * MINUTES, TierEU.RECIPE_IV))
            .itemInputs(
                research.copy(),
                circuit(4),
                circuitIV(2),
                plate(Materials.Iridium, 4),
                plate(Materials.NaquadahAlloy, 4),
                ItemList.Electric_Pump_LuV.get(2),
                ItemList.Emitter_LuV.get(1),
                GTOreDictUnificator.get(OrePrefixes.wireGt04, Materials.YttriumBariumCuprate, 4))
            .fluidInputs(Materials.SolderingAlloy.getMolten(4 * INGOTS), Materials.Naquadah.getMolten(2 * INGOTS))
            .itemOutputs(new ItemStack(CommonProxy.blockExportBus, 1))
            .eut(TierEU.RECIPE_LuV)
            .duration(30 * SECONDS)
            .addTo(AssemblyLine);
    }

    /**
     * 奇点 ME 接口 — researched from ME Interface (damage 440).
     * Connects the SingularityGrid to an adjacent machine for autocrafting.
     */
    private static void registerInterface() {
        ItemStack research = ae2Part(440); // ME Interface
        if (research == null) return;

        GTValues.RA.stdBuilder()
            .metadata(RESEARCH_ITEM, research)
            .metadata(SCANNING, new Scanning(2 * MINUTES, TierEU.RECIPE_IV))
            .itemInputs(
                research.copy(),
                circuit(4),
                circuitIV(4),
                plate(Materials.Iridium, 4),
                plateDouble(Materials.NaquadahAlloy, 2),
                ItemList.Field_Generator_LuV.get(2),
                ItemList.Emitter_LuV.get(2),
                GTOreDictUnificator.get(OrePrefixes.wireGt04, Materials.YttriumBariumCuprate, 8))
            .fluidInputs(Materials.SolderingAlloy.getMolten(8 * INGOTS), Materials.Naquadah.getMolten(4 * INGOTS))
            .itemOutputs(new ItemStack(CommonProxy.blockInterface, 1))
            .eut(TierEU.RECIPE_LuV)
            .duration(1 * MINUTES)
            .addTo(AssemblyLine);
    }

    /**
     * 奇点驱动器 — researched from ME Drive Array.
     * Accepts AE2 storage cells and contributes their capacity to the SingularityGrid.
     */
    private static void registerDrive() {
        ItemStack research = GTModHandler.getModItem("appliedenergistics2", "tile.BlockDrive", 1);
        if (research == null) return;

        GTValues.RA.stdBuilder()
            .metadata(RESEARCH_ITEM, research)
            .metadata(SCANNING, new Scanning(2 * MINUTES, TierEU.RECIPE_IV))
            .itemInputs(
                research.copy(),
                circuit(4),
                circuitIV(2),
                plateDouble(Materials.Iridium, 4),
                plate(Materials.NaquadahAlloy, 8),
                ItemList.Field_Generator_LuV.get(2),
                ItemList.Sensor_LuV.get(2),
                GTOreDictUnificator.get(OrePrefixes.wireGt04, Materials.YttriumBariumCuprate, 8))
            .fluidInputs(Materials.SolderingAlloy.getMolten(8 * INGOTS), Materials.Naquadah.getMolten(4 * INGOTS))
            .itemOutputs(new ItemStack(CommonProxy.blockDrive, 1))
            .eut(TierEU.RECIPE_LuV)
            .duration(1 * MINUTES)
            .addTo(AssemblyLine);
    }

    private static void registerCraftingCore() {
        ItemStack research = ae2Definition(
            AEApi.instance()
                .definitions()
                .blocks()
                .craftingUnit());
        if (research == null) return;

        GTValues.RA.stdBuilder()
            .metadata(RESEARCH_ITEM, research)
            .metadata(SCANNING, new Scanning(3 * MINUTES, TierEU.RECIPE_IV))
            .itemInputs(
                research.copy(),
                circuit(8),
                circuitIV(4),
                plateDouble(Materials.Iridium, 4),
                plateDouble(Materials.NaquadahAlloy, 4),
                ItemList.Field_Generator_LuV.get(2),
                ItemList.Sensor_LuV.get(2),
                ItemList.Emitter_LuV.get(2),
                GTOreDictUnificator.get(OrePrefixes.wireGt04, Materials.YttriumBariumCuprate, 8))
            .fluidInputs(
                Materials.SolderingAlloy.getMolten(8 * INGOTS),
                Materials.Naquadah.getMolten(4 * INGOTS),
                Materials.Osmium.getMolten(2 * INGOTS))
            .itemOutputs(new ItemStack(CommonProxy.blockCraftingCore, 1))
            .eut(TierEU.RECIPE_LuV)
            .duration(1 * MINUTES)
            .addTo(AssemblyLine);
    }

    /**
     * 奇点能量核心 — researched from ME Energy Acceptor block.
     * Draws EU from GT cables and converts it to AE power for the SingularityGrid.
     */
    private static void registerPowerCore() {
        // Energy Acceptor is a block item: appliedenergistics2:tile.BlockEnergyAcceptor
        ItemStack research = GTModHandler.getModItem("appliedenergistics2", "tile.BlockEnergyAcceptor", 1);
        if (research == null) return;

        GTValues.RA.stdBuilder()
            .metadata(RESEARCH_ITEM, research)
            .metadata(SCANNING, new Scanning(3 * MINUTES, TierEU.RECIPE_IV))
            .itemInputs(
                research.copy(),
                circuit(4),
                circuitIV(4),
                plateDouble(Materials.Iridium, 4),
                plateDouble(Materials.NaquadahAlloy, 4),
                ItemList.Field_Generator_LuV.get(2),
                ItemList.Electric_Motor_LuV.get(2),
                GTOreDictUnificator.get(OrePrefixes.cableGt04, Materials.YttriumBariumCuprate, 4))
            .fluidInputs(
                Materials.SolderingAlloy.getMolten(8 * INGOTS),
                Materials.Naquadah.getMolten(4 * INGOTS),
                Materials.Osmium.getMolten(2 * INGOTS))
            .itemOutputs(new ItemStack(CommonProxy.blockPowerCore, 1))
            .eut(TierEU.RECIPE_LuV)
            .duration(1 * MINUTES)
            .addTo(AssemblyLine);
    }

}

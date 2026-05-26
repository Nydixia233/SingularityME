package com.github.singularityme.gui;

import static appeng.util.Platform.isServer;

import net.minecraft.entity.player.InventoryPlayer;

import com.github.singularityme.tile.TileSingularityImportBus;

import appeng.api.config.RedstoneMode;
import appeng.api.config.Settings;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStack;
import appeng.container.implementations.ContainerUpgradeable;
import appeng.container.interfaces.IVirtualSlotHolder;
import appeng.tile.inventory.IAEStackInventory;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

/**
 * Container for the Singularity Import Bus GUI.
 *
 * <p>
 * Provides 9 virtual ghost filter slots. An empty filter imports all items from
 * the adjacent container. Populated slots restrict imports to only the specified
 * item types. Supports redstone mode via {@link appeng.api.util.IConfigurableObject}.
 */
public class ContainerSingularityImportBus extends ContainerUpgradeable implements IVirtualSlotHolder {

    public final IAEStack<?>[] virtualSlotsClient = new IAEStack<?>[9];
    final TileSingularityImportBus importBus;

    public ContainerSingularityImportBus(final InventoryPlayer ip, final TileSingularityImportBus te) {
        super(ip, te);
        this.importBus = te;
    }

    // ---- ContainerUpgradeable overrides ----

    @Override
    public int availableUpgrades() {
        return 4;
    }

    @Override
    protected int getHeight() {
        return 184;
    }

    @Override
    protected void loadSettingsFromHost(final appeng.api.util.IConfigManager cm) {
        this.rsMode = (RedstoneMode) cm.getSetting(Settings.REDSTONE_CONTROLLED);
        if (importBus.getInstalledUpgrades(appeng.api.config.Upgrades.FUZZY) > 0) {
            this.fzMode = (appeng.api.config.FuzzyMode) cm.getSetting(Settings.FUZZY_MODE);
        }
    }

    @Override
    public void detectAndSendChanges() {
        if (isServer()) {
            this.loadSettingsFromHost(importBus.getConfigManager());
            this.updateVirtualSlots(
                StorageName.CONFIG,
                importBus.getAEInventoryByName(StorageName.CONFIG),
                virtualSlotsClient);
        }
        this.standardDetectAndSendChanges();
    }

    // ---- IVirtualSlotHolder ----

    @Override
    public void receiveSlotStacks(final StorageName invName, final Int2ObjectMap<IAEStack<?>> slotStacks) {
        final IAEStackInventory config = importBus.getAEInventoryByName(StorageName.CONFIG);
        for (var entry : slotStacks.int2ObjectEntrySet()) {
            config.putAEStackInSlot(entry.getIntKey(), entry.getValue());
        }
        if (isServer()) {
            this.updateVirtualSlots(StorageName.CONFIG, config, virtualSlotsClient);
        }
    }
}

package com.github.singularityme.gui;

import static appeng.util.Platform.isServer;

import net.minecraft.entity.player.InventoryPlayer;

import com.github.singularityme.block.BlockSingularityExportBus;
import com.github.singularityme.tile.TileSingularityExportBus;

import appeng.api.config.RedstoneMode;
import appeng.api.config.SchedulingMode;
import appeng.api.config.SecurityPermissions;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStack;
import appeng.container.PrimaryGui;
import appeng.container.implementations.ContainerUpgradeable;
import appeng.container.interfaces.IVirtualSlotHolder;
import appeng.container.slot.OptionalSlotFake;
import appeng.tile.inventory.IAEStackInventory;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

/**
 * Container for the Singularity Export Bus GUI.
 *
 * <p>
 * Provides 9 virtual ghost filter slots. Supports redstone control and scheduling
 * mode via {@link appeng.api.util.IConfigurableObject}.
 */
public class ContainerSingularityExportBus extends ContainerUpgradeable implements IVirtualSlotHolder {

    public final IAEStack<?>[] virtualSlotsClient = new IAEStack<?>[9];
    final TileSingularityExportBus exportBus;

    public ContainerSingularityExportBus(final InventoryPlayer ip, final TileSingularityExportBus te) {
        super(ip, te);
        this.exportBus = te;
    }

    @Override
    public PrimaryGui createPrimaryGui() {
        return SingularityPrimaryGui.create(BlockSingularityExportBus.GUI_ID, this);
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
        this.setSchedulingMode((SchedulingMode) cm.getSetting(Settings.SCHEDULING_MODE));
        this.cMode = (YesNo) cm.getSetting(Settings.CRAFT_ONLY);
        this.fzMode = (appeng.api.config.FuzzyMode) cm.getSetting(Settings.FUZZY_MODE);
    }

    // setSchedulingMode is package-private in ContainerUpgradeable, so shadow the field directly.
    private void setSchedulingMode(final SchedulingMode mode) {
        this.schedulingMode = mode;
    }

    @Override
    public void detectAndSendChanges() {
        this.verifyPermissions(SecurityPermissions.BUILD, false);

        if (isServer()) {
            this.loadSettingsFromHost(exportBus.getConfigManager());
            this.updateVirtualSlots(
                StorageName.CONFIG,
                exportBus.getAEInventoryByName(StorageName.CONFIG),
                virtualSlotsClient);
        }
        this.clearDisabledFakeSlots();
        this.standardDetectAndSendChanges();
    }

    private void clearDisabledFakeSlots() {
        for (final Object slot : this.inventorySlots) {
            if (slot instanceof OptionalSlotFake fakeSlot && !fakeSlot.isEnabled()
                && fakeSlot.getDisplayStack() != null) {
                fakeSlot.clearStack();
            }
        }
    }

    // ---- IVirtualSlotHolder ----

    @Override
    public void receiveSlotStacks(final StorageName invName, final Int2ObjectMap<IAEStack<?>> slotStacks) {
        final IAEStackInventory config = exportBus.getAEInventoryByName(StorageName.CONFIG);
        for (var entry : slotStacks.int2ObjectEntrySet()) {
            config.putAEStackInSlot(entry.getIntKey(), entry.getValue());
        }
        if (isServer()) {
            this.updateVirtualSlots(StorageName.CONFIG, config, virtualSlotsClient);
        }
    }
}

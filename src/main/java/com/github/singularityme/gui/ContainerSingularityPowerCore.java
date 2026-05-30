package com.github.singularityme.gui;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import com.github.singularityme.tile.TileSingularityPowerCore;

import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.container.slot.AppEngSlot;

/**
 * Container for the Singularity Power Core component slots.
 */
public class ContainerSingularityPowerCore extends AEBaseContainer {

    public static final int GUI_WIDTH = 176;
    public static final int GUI_HEIGHT = 166;
    public static final int PLAYER_INVENTORY_Y = 84;

    private static final int[] SLOT_X = { 62, 80, 98 };
    private static final int SLOT_Y = 27;

    private final TileSingularityPowerCore core;

    @GuiSync(1)
    public long syncedCurrentBuffer = 0;

    @GuiSync(2)
    public long syncedMaxBuffer = 0;

    public ContainerSingularityPowerCore(final InventoryPlayer ip, final TileSingularityPowerCore te) {
        super(ip, te, null);
        this.core = te;

        for (int slot = 0; slot < TileSingularityPowerCore.COMPONENT_SLOT_COUNT; slot++) {
            this.addSlotToContainer(new SlotPowerCoreCell(te, slot, SLOT_X[slot], SLOT_Y));
        }

        bindPlayerInventory(ip, 0, PLAYER_INVENTORY_Y);
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        this.syncedCurrentBuffer = clampToLong(this.core.getStoredAEPower());
        this.syncedMaxBuffer = clampToLong(this.core.getMaxAEPower());
    }

    private static long clampToLong(final double value) {
        if (!Double.isFinite(value) || value >= Long.MAX_VALUE) return Long.MAX_VALUE;
        if (value <= 0.0) return 0L;
        return (long) value;
    }

    private static final class SlotPowerCoreCell extends AppEngSlot {

        private final TileSingularityPowerCore core;
        private final int slot;

        private SlotPowerCoreCell(final TileSingularityPowerCore core, final int slot, final int x, final int y) {
            super(core.getPowerComponentInventory(), slot, x, y);
            this.core = core;
            this.slot = slot;
        }

        @Override
        public boolean isItemValid(final ItemStack stack) {
            if (this.slot != TileSingularityPowerCore.SLOT_CREATIVE_ENERGY_CELL
                && this.core.hasCreativePowerComponent()) {
                return false;
            }
            return this.core.isValidComponent(this.slot, stack);
        }

        @Override
        public int getSlotStackLimit() {
            return this.core.getComponentSlotLimit(this.slot);
        }
    }
}

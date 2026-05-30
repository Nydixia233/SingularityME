package com.github.singularityme.gui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import com.github.singularityme.tile.TileSingularityCraftingCore;

import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.container.slot.AppEngSlot;

/**
 * Container for the single-block Singularity Crafting Core.
 */
public class ContainerSingularityCraftingCore extends AEBaseContainer {

    public static final int GUI_WIDTH = 176;
    public static final int GUI_HEIGHT = 199;
    public static final int COMPONENT_COLS = 9;
    public static final int COMPONENT_ROWS = 2;
    public static final int COMPONENT_START_X = 8;
    public static final int COMPONENT_START_Y = 25;
    public static final int PLAYER_INVENTORY_X = 8;
    public static final int PLAYER_INVENTORY_Y = 117;

    private final TileSingularityCraftingCore core;

    @GuiSync(1)
    public long storageBytes = 0;

    @GuiSync(2)
    public int coProcessors = 0;

    @GuiSync(3)
    public int monitorCount = 0;

    @GuiSync(4)
    public boolean busy = false;

    public ContainerSingularityCraftingCore(final InventoryPlayer ip, final TileSingularityCraftingCore te) {
        super(ip, te, null);
        this.core = te;

        for (int row = 0; row < COMPONENT_ROWS; row++) {
            for (int col = 0; col < COMPONENT_COLS; col++) {
                final int slot = col + row * 9;
                this.addSlotToContainer(
                    new CraftingComponentSlot(te, slot, COMPONENT_START_X + col * 18, COMPONENT_START_Y + row * 18));
            }
        }

        bindPlayerInventory(ip, 0, PLAYER_INVENTORY_Y);
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        this.storageBytes = this.core.getStorageBytes();
        this.coProcessors = this.core.getConfiguredCoProcessors();
        this.monitorCount = this.core.getMonitorCount();
        this.busy = this.core.isCoreBusy();
    }

    private static final class CraftingComponentSlot extends AppEngSlot {

        private final TileSingularityCraftingCore core;

        private CraftingComponentSlot(final TileSingularityCraftingCore core, final int slot, final int x,
            final int y) {
            super(core.getComponentInventory(), slot, x, y);
            this.core = core;
        }

        @Override
        public boolean isItemValid(final ItemStack stack) {
            return !this.core.isComponentLocked() && this.core.isValidComponent(stack);
        }

        @Override
        public boolean canTakeStack(final EntityPlayer player) {
            return !this.core.isComponentLocked();
        }

        @Override
        public int getSlotStackLimit() {
            return 1;
        }
    }
}

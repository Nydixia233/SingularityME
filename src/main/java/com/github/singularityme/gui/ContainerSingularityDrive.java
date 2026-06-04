package com.github.singularityme.gui;

import net.minecraft.entity.player.InventoryPlayer;

import com.github.singularityme.block.BlockSingularityDrive;
import com.github.singularityme.tile.TileSingularityDrive;

import appeng.container.AEBaseContainer;
import appeng.container.PrimaryGui;
import appeng.container.guisync.GuiSync;
import appeng.container.slot.SlotRestrictedInput;
import appeng.container.slot.SlotRestrictedInput.PlacableItemType;

/**
 * Container for the Singularity Drive GUI.
 *
 * <p>
 * Provides 10 cell slots (2 columns × 5 rows) that only accept AE2 storage cells.
 * Supports priority control via AE2's priority GUI tab.
 */
public class ContainerSingularityDrive extends AEBaseContainer {

    @GuiSync(1)
    public long priorityValue = 0;

    private final TileSingularityDrive drive;

    public ContainerSingularityDrive(final InventoryPlayer ip, final TileSingularityDrive te) {
        super(ip, te, null);
        this.drive = te;

        // 10 cell slots: 2 columns × 5 rows, starting at (71, 14)
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 2; col++) {
                addSlotToContainer(
                    new SlotRestrictedInput(
                        PlacableItemType.STORAGE_CELLS,
                        te.getCellInventory(),
                        col + row * 2,
                        71 + col * 18,
                        14 + row * 18,
                        getInventoryPlayer()));
            }
        }

        bindPlayerInventory(ip, 0, 199 - 82);
    }

    @Override
    public PrimaryGui createPrimaryGui() {
        return SingularityPrimaryGui.create(BlockSingularityDrive.GUI_ID, this);
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        if (drive != null) {
            priorityValue = drive.getPriorityValue();
        }
    }

    public int getPriorityValue() {
        return (int) priorityValue;
    }

    public void setPriorityFromGui(final int value) {
        if (drive != null) {
            drive.setPriorityValue(value);
        }
    }
}

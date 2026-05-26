package com.github.singularityme.gui;

import net.minecraft.entity.player.InventoryPlayer;

import com.github.singularityme.tile.TileSingularityDrive;

import appeng.client.gui.AEBaseGui;

/**
 * GUI for the Singularity Drive.
 *
 * <p>
 * Displays 10 cell slots and the current priority (read-only display).
 * Reuses the AE2 drive background texture.
 */
public class GuiSingularityDrive extends AEBaseGui {

    public GuiSingularityDrive(final InventoryPlayer ip, final TileSingularityDrive te) {
        super(new ContainerSingularityDrive(ip, te));
        this.ySize = 199;
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        ContainerSingularityDrive c = (ContainerSingularityDrive) this.inventorySlots;
        this.fontRendererObj.drawString("P:" + c.getPriorityValue(), 8, 50, 0x404040);
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.bindTexture("appliedenergistics2", "guis/drive.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);
    }
}

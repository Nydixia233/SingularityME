package com.github.singularityme.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;

import com.github.singularityme.tile.TileSingularityDrive;

import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.core.localization.GuiColors;
import appeng.core.localization.GuiText;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketSwitchGuis;

/**
 * GUI for the Singularity Drive.
 *
 * <p>
 * Displays 10 cell slots, an AE2-style priority tab, and a Network tab for
 * assigning this device to a Singularity network.
 */
public class GuiSingularityDrive extends AEBaseGui {

    public static final int PRIORITY_TAB_Y_OFFSET = 66;
    private static final int NETWORK_TAB_Y_OFFSET = 22;

    private GuiTabButton priority;
    private GuiTabButton networkTab;
    private final TileSingularityDrive te;

    public GuiSingularityDrive(final InventoryPlayer ip, final TileSingularityDrive te) {
        super(new ContainerSingularityDrive(ip, te));
        this.te = te;
        this.ySize = 199;
    }

    @Override
    protected void actionPerformed(final GuiButton button) {
        super.actionPerformed(button);

        if (button == this.priority) {
            NetworkHandler.instance.sendToServer(new PacketSwitchGuis(GuiBridge.GUI_PRIORITY));
        } else if (button == this.networkTab) {
            NetworkTabClientActions.open(this.te);
        }
    }

    @Override
    public void initGui() {
        super.initGui();

        this.buttonList.add(
            this.priority = new GuiTabButton(
                this.guiLeft + 154,
                this.guiTop + PRIORITY_TAB_Y_OFFSET,
                2 + 4 * 16,
                GuiText.Priority.getLocal(),
                itemRender));

        this.buttonList.add(
            this.networkTab = new GuiTabButton(
                this.guiLeft + 154,
                this.guiTop + NETWORK_TAB_Y_OFFSET,
                2 + 11 * 16,
                "Network",
                itemRender));
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.fontRendererObj
            .drawString(this.getGuiDisplayName(GuiText.Drive.getLocal()), 8, 6, GuiColors.DriveTitle.getColor());
        this.fontRendererObj
            .drawString(GuiText.inventory.getLocal(), 8, this.ySize - 96 + 3, GuiColors.DriveInventory.getColor());
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.bindTexture("appliedenergistics2", "guis/drive.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);
    }
}

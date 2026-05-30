package com.github.singularityme.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;

import com.github.singularityme.tile.TileSingularityTerminal;

import appeng.client.gui.implementations.GuiMEMonitorable;
import appeng.client.gui.widgets.GuiTabButton;

/**
 * Singularity Terminal GUI — wraps AE2's {@link GuiMEMonitorable} and adds a
 * Network tab button so the player can reassign this terminal to a different
 * Singularity network without leaving the device.
 */
public class GuiSingularityTerminal extends GuiMEMonitorable {

    private final TileSingularityTerminal te;
    private GuiTabButton btnNetworkTab;

    public GuiSingularityTerminal(final InventoryPlayer ip, final TileSingularityTerminal te) {
        super(ip, te);
        this.te = te;
    }

    @Override
    public void initGui() {
        super.initGui();

        this.btnNetworkTab = new GuiTabButton(
            this.guiLeft + this.xSize + 33,
            this.guiTop + 104,
            2 + 11 * 16,
            "Network",
            itemRender);
        this.buttonList.add(this.btnNetworkTab);
    }

    @Override
    protected void actionPerformed(final GuiButton button) {
        if (button == this.btnNetworkTab) {
            NetworkTabClientActions.open(this.te);
            return;
        }
        super.actionPerformed(button);
    }
}

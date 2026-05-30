package com.github.singularityme.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;

import com.github.singularityme.tile.TileSingularityInterface;

import appeng.client.gui.implementations.GuiInterface;
import appeng.client.gui.widgets.GuiTabButton;

/**
 * Singularity Interface GUI — wraps AE2's {@link GuiInterface} and adds a
 * Network tab button for Singularity network assignment.
 */
public class GuiSingularityInterface extends GuiInterface {

    private final TileSingularityInterface te;
    private GuiTabButton btnNetworkTab;

    public GuiSingularityInterface(final InventoryPlayer ip, final TileSingularityInterface te) {
        super(ip, te);
        this.te = te;
    }

    @Override
    protected void addButtons() {
        super.addButtons();

        this.btnNetworkTab = new GuiTabButton(this.guiLeft + 154, this.guiTop + 22, 2 + 11 * 16, "Network", itemRender);
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

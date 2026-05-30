package com.github.singularityme.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;

import com.github.singularityme.tile.TileSingularityPatternTerminal;

import appeng.client.gui.implementations.GuiPatternTerm;
import appeng.client.gui.widgets.GuiTabButton;

/**
 * Singularity Pattern Terminal GUI — wraps AE2's {@link GuiPatternTerm} and
 * adds a Network tab button for Singularity network assignment.
 */
public class GuiSingularityPatternTerminal extends GuiPatternTerm {

    private final TileSingularityPatternTerminal te;
    private GuiTabButton btnNetworkTab;

    public GuiSingularityPatternTerminal(final InventoryPlayer ip, final TileSingularityPatternTerminal te) {
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

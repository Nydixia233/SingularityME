package com.github.singularityme.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;

import com.github.singularityme.tile.TileSingularityCraftingTerminal;

import appeng.client.gui.implementations.GuiCraftingTerm;
import appeng.client.gui.widgets.GuiTabButton;

/**
 * Singularity Crafting Terminal GUI — wraps AE2's {@link GuiCraftingTerm} and
 * adds a Network tab button for Singularity network assignment.
 */
public class GuiSingularityCraftingTerminal extends GuiCraftingTerm {

    private final TileSingularityCraftingTerminal te;
    private GuiTabButton btnNetworkTab;

    public GuiSingularityCraftingTerminal(final InventoryPlayer ip, final TileSingularityCraftingTerminal te) {
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

package com.github.singularityme.gui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import com.github.singularityme.tile.TileSingularityNetworkTerminal;

import appeng.container.AEBaseContainer;

/**
 * Slotless server-side container for the Network Terminal screen.
 */
public class ContainerSingularityNetworkTerminal extends AEBaseContainer {

    public ContainerSingularityNetworkTerminal(final InventoryPlayer ip, final TileSingularityNetworkTerminal te) {
        super(ip, te, null);
    }

    @Override
    public ItemStack slotClick(final int slotId, final int clickedButton, final int mode, final EntityPlayer player) {
        return null;
    }

    @Override
    public ItemStack transferStackInSlot(final EntityPlayer player, final int slotId) {
        return null;
    }
}

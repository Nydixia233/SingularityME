package com.github.singularityme.gui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import appeng.container.AEBaseContainer;

/**
 * Slotless server-side container for the Network Tab screen.
 *
 * <p>
 * The client screen requests network data after it is fully open. This container only keeps Forge's OpenGui flow valid
 * and defensively swallows slot clicks from clients that still send vanilla window packets.
 */
public class ContainerSingularityNetworkTab extends AEBaseContainer {

    /** GUI ID registered in {@link SingularityGuiHandler}. */
    public static final int GUI_ID = 10;

    public ContainerSingularityNetworkTab(final InventoryPlayer ip, final TileEntity te) {
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

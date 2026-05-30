package com.github.singularityme.tile;

import java.util.List;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import appeng.api.AEApi;
import appeng.api.parts.ICraftingTerminal;
import appeng.tile.TileEvent;
import appeng.tile.events.TileEventType;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.inventory.InvOperation;

/**
 * Singularity variant of AE2's Crafting Terminal.
 *
 * <p>
 * The network/storage host behavior is inherited from {@link TileSingularityTerminal};
 * this class only adds the 3x3 crafting matrix expected by AE2's native crafting
 * terminal container and GUI.
 */
public class TileSingularityCraftingTerminal extends TileSingularityTerminal implements ICraftingTerminal {

    private final AppEngInternalInventory craftingGrid = new AppEngInternalInventory(this, 9);

    @Override
    public IInventory getInventoryByName(final String name) {
        if ("crafting".equals(name)) {
            return this.craftingGrid;
        }
        return null;
    }

    @Override
    public void onChangeInventory(final IInventory inv, final int slot, final InvOperation mc,
        final ItemStack removedStack, final ItemStack newStack) {
        super.onChangeInventory(inv, slot, mc, removedStack, newStack);
    }

    @Override
    public ItemStack getPrimaryGuiIcon() {
        return AEApi.instance()
            .definitions()
            .parts()
            .craftingTerminal()
            .maybeStack(1)
            .orNull();
    }

    @Override
    public void addTerminalDrops(final List<ItemStack> drops) {
        super.addTerminalDrops(drops);
        for (final ItemStack stack : this.craftingGrid) {
            if (stack != null) {
                drops.add(stack);
            }
        }
    }

    @TileEvent(TileEventType.WORLD_NBT_WRITE)
    public void writeCraftingTerminalNBT(final NBTTagCompound data) {
        this.craftingGrid.writeToNBT(data, "craftingGrid");
    }

    @TileEvent(TileEventType.WORLD_NBT_READ)
    public void readCraftingTerminalNBT(final NBTTagCompound data) {
        this.craftingGrid.readFromNBT(data, "craftingGrid");
    }
}

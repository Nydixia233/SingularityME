package com.github.singularityme.tile;

import java.util.List;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import appeng.api.AEApi;
import appeng.api.parts.IPatternTerminal;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStack;
import appeng.tile.TileEvent;
import appeng.tile.events.TileEventType;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.inventory.IAEStackInventory;
import appeng.tile.inventory.IIAEStackInventory;
import appeng.tile.inventory.InvOperation;

/**
 * Singularity variant of AE2's Pattern Terminal.
 *
 * <p>
 * It keeps the ordinary Singularity terminal network host and implements the
 * pattern inventories/flags consumed by AE2's native Pattern Terminal container.
 */
public class TileSingularityPatternTerminal extends TileSingularityTerminal
    implements IPatternTerminal, IIAEStackInventory {

    private final IAEStackInventory crafting = new IAEStackInventory(this, 9, StorageName.CRAFTING_INPUT);
    private final IAEStackInventory output = new IAEStackInventory(this, 3, StorageName.CRAFTING_OUTPUT);
    private final AppEngInternalInventory pattern = new AppEngInternalInventory(this, 2);

    private boolean craftingMode = true;
    private boolean substitute = false;
    private boolean beSubstitute = false;

    @Override
    public boolean isCraftingRecipe() {
        return this.craftingMode;
    }

    @Override
    public void setCraftingRecipe(final boolean craftingMode) {
        this.craftingMode = craftingMode;
        this.markDirty();
    }

    @Override
    public boolean isSubstitution() {
        return this.substitute;
    }

    @Override
    public boolean canBeSubstitution() {
        return this.beSubstitute;
    }

    @Override
    public void setSubstitution(final boolean canSubstitute) {
        this.substitute = canSubstitute;
        this.markDirty();
    }

    @Override
    public void setCanBeSubstitution(final boolean beSubstitute) {
        this.beSubstitute = beSubstitute;
        this.markDirty();
    }

    @Override
    public IInventory getInventoryByName(final String name) {
        if ("pattern".equals(name)) {
            return this.pattern;
        }
        return null;
    }

    @Override
    public void exPatternTerminalCall(final IAEStack<?>[] in, final IAEStack<?>[] out) {
        this.markDirty();
    }

    @Override
    public void onChangeInventory(final IInventory inv, final int slot, final InvOperation mc,
        final ItemStack removedStack, final ItemStack newStack) {
        if (inv == this.pattern && slot == 1 && this.worldObj != null) {
            this.loadPatternFromItem(this.pattern.getStackInSlot(1), this.worldObj, this.crafting, this.output);
        }
        super.onChangeInventory(inv, slot, mc, removedStack, newStack);
    }

    @Override
    public void saveAEStackInv() {
        this.markDirty();
    }

    @Override
    public IAEStackInventory getAEInventoryByName(final StorageName name) {
        return switch (name) {
            case CRAFTING_INPUT -> this.crafting;
            case CRAFTING_OUTPUT -> this.output;
            default -> null;
        };
    }

    @Override
    public ItemStack getPrimaryGuiIcon() {
        return AEApi.instance()
            .definitions()
            .parts()
            .patternTerminal()
            .maybeStack(1)
            .orNull();
    }

    @Override
    public void addTerminalDrops(final List<ItemStack> drops) {
        super.addTerminalDrops(drops);
        for (final ItemStack stack : this.pattern) {
            if (stack != null) {
                drops.add(stack);
            }
        }
    }

    @TileEvent(TileEventType.WORLD_NBT_WRITE)
    public void writePatternTerminalNBT(final NBTTagCompound data) {
        data.setBoolean("craftingMode", this.craftingMode);
        data.setBoolean("substitute", this.substitute);
        data.setBoolean("beSubstitute", this.beSubstitute);
        this.pattern.writeToNBT(data, "pattern");
        this.output.writeToNBT(data, "outputList");
        this.crafting.writeToNBT(data, "craftingGrid");
    }

    @TileEvent(TileEventType.WORLD_NBT_READ)
    public void readPatternTerminalNBT(final NBTTagCompound data) {
        this.craftingMode = data.hasKey("craftingMode") ? data.getBoolean("craftingMode") : true;
        this.substitute = data.getBoolean("substitute");
        this.beSubstitute = data.getBoolean("beSubstitute");
        this.pattern.readFromNBT(data, "pattern");
        this.output.readFromNBT(data, "outputList");
        this.crafting.readFromNBT(data, "craftingGrid");
    }
}

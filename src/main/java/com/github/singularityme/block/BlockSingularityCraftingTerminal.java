package com.github.singularityme.block;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.github.singularityme.tile.TileSingularityCraftingTerminal;

/**
 * Block shell for the Singularity Crafting Terminal.
 */
public class BlockSingularityCraftingTerminal extends BlockSingularityTerminal {

    public static final int GUI_ID = 7;

    public BlockSingularityCraftingTerminal() {
        setBlockName("singularity_crafting_terminal");
        setBlockTextureName("appliedenergistics2:ItemPart.CraftingTerminal");
    }

    @Override
    public TileEntity createNewTileEntity(final World world, final int metadata) {
        return new TileSingularityCraftingTerminal();
    }

    @Override
    protected int getGuiId() {
        return GUI_ID;
    }
}

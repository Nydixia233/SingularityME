package com.github.singularityme.block;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.github.singularityme.tile.TileSingularityPatternTerminal;

/**
 * Block shell for the Singularity Pattern Terminal.
 */
public class BlockSingularityPatternTerminal extends BlockSingularityTerminal {

    public static final int GUI_ID = 8;

    public BlockSingularityPatternTerminal() {
        setBlockName("singularity_pattern_terminal");
        setBlockTextureName("appliedenergistics2:ItemPart.PatternTerminal");
    }

    @Override
    public TileEntity createNewTileEntity(final World world, final int metadata) {
        return new TileSingularityPatternTerminal();
    }

    @Override
    protected int getGuiId() {
        return GUI_ID;
    }
}

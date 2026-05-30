package com.github.singularityme.block;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.github.singularityme.tile.TileSingularityNetworkTerminal;

/**
 * Personal management terminal for Singularity network metadata.
 */
public class BlockSingularityNetworkTerminal extends BlockSingularityTerminal {

    public static final int GUI_ID = 12;

    public BlockSingularityNetworkTerminal() {
        super();
        setBlockName("singularity_network_terminal");
        setBlockTextureName("appliedenergistics2:ItemPart.Terminal");
    }

    @Override
    public TileEntity createNewTileEntity(final World world, final int metadata) {
        return new TileSingularityNetworkTerminal();
    }

    @Override
    protected int getGuiId() {
        return GUI_ID;
    }
}

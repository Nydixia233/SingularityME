package com.github.singularityme.block;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.github.singularityme.tile.TileSingularityProbe;

import appeng.me.helpers.IGridProxyable;

/**
 * Debug block for Phase 0.5 verification — not craftable, obtain via {@code /give}.
 *
 * <p>
 * Placing the block stores the player owner in the AENetworkProxy so that the node
 * receives a valid playerID when {@link TileSingularityProbe#onReady()} is called.
 */
public class BlockSingularityProbe extends Block implements ITileEntityProvider {

    public BlockSingularityProbe() {
        super(Material.iron);
        setBlockName("singularity_probe");
        setBlockTextureName("singularityme:singularity_probe");
        setHardness(2.0f);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int metadata) {
        return new TileSingularityProbe();
    }

    @Override
    public boolean hasTileEntity(int metadata) {
        return true;
    }

    /**
     * Called when a player places the block. Sets the proxy owner so that AE2 can resolve
     * the playerID from the EntityPlayer's GameProfile when the node initialises.
     */
    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(world, x, y, z, placer, stack);
        if (placer instanceof EntityPlayer) {
            TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof IGridProxyable gp) {
                gp.getProxy()
                    .setOwner((EntityPlayer) placer);
            }
        }
    }

    @Override
    public boolean renderAsNormalBlock() {
        return true;
    }

    @Override
    public boolean isOpaqueCube() {
        return true;
    }
}

package com.github.singularityme.block;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.github.singularityme.SingularityME;
import com.github.singularityme.tile.TileSingularityExportBus;

import appeng.me.helpers.IGridProxyable;

/**
 * Singularity Export Bus block — extracts items from the player's global
 * SingularityGrid and pushes them into the adjacent container each tick.
 */
public class BlockSingularityExportBus extends Block implements ITileEntityProvider {

    public static final int GUI_ID = 4;

    public BlockSingularityExportBus() {
        super(Material.iron);
        setBlockName("singularity_export_bus");
        setBlockTextureName("appliedenergistics2:ItemPart.ExportBus");
        setHardness(2.0f);
    }

    @Override
    public TileEntity createNewTileEntity(final World world, final int metadata) {
        return new TileSingularityExportBus();
    }

    @Override
    public boolean hasTileEntity(final int metadata) {
        return true;
    }

    @Override
    public void onBlockPlacedBy(final World world, final int x, final int y, final int z, final EntityLivingBase placer,
        final ItemStack stack) {
        super.onBlockPlacedBy(world, x, y, z, placer, stack);

        world.setBlockMetadataWithNotify(
            x,
            y,
            z,
            BlockSingularityStorageBus.facingFromPlacer(placer)
                .ordinal(),
            2);

        if (placer instanceof EntityPlayer) {
            TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof IGridProxyable gp) {
                gp.getProxy()
                    .setOwner((EntityPlayer) placer);
            }
        }
    }

    @Override
    public boolean onBlockActivated(final World world, final int x, final int y, final int z, final EntityPlayer player,
        final int side, final float fx, final float fy, final float fz) {
        if (!world.isRemote) {
            player.openGui(SingularityME.instance, GUI_ID, world, x, y, z);
        }
        return true;
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

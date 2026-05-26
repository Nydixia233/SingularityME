package com.github.singularityme.block;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.github.singularityme.SingularityME;
import com.github.singularityme.tile.TileSingularityImportBus;

import appeng.me.helpers.IGridProxyable;

/**
 * Singularity Import Bus block — pulls items from the adjacent container into
 * the player's global SingularityGrid each tick.
 */
public class BlockSingularityImportBus extends Block implements ITileEntityProvider {

    public static final int GUI_ID = 6;

    public BlockSingularityImportBus() {
        super(Material.iron);
        setBlockName("singularity_import_bus");
        setBlockTextureName("appliedenergistics2:ItemPart.ImportBus");
        setHardness(2.0f);
    }

    @Override
    public TileEntity createNewTileEntity(final World world, final int metadata) {
        return new TileSingularityImportBus();
    }

    @Override
    public boolean hasTileEntity(final int metadata) {
        return true;
    }

    @Override
    public void onBlockPlacedBy(final World world, final int x, final int y, final int z, final EntityLivingBase placer,
        final ItemStack stack) {
        super.onBlockPlacedBy(world, x, y, z, placer, stack);

        ForgeDirection facing = BlockSingularityStorageBus.facingFromPlacer(placer);
        world.setBlockMetadataWithNotify(x, y, z, facing.ordinal(), 2);

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

    @Override
    public boolean onBlockActivated(final World world, final int x, final int y, final int z, final EntityPlayer player,
        final int side, final float fx, final float fy, final float fz) {
        if (!world.isRemote) {
            player.openGui(SingularityME.instance, GUI_ID, world, x, y, z);
        }
        return true;
    }
}

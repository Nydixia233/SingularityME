package com.github.singularityme.block;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.github.singularityme.SingularityME;
import com.github.singularityme.tile.TileSingularityPowerCore;

import appeng.me.helpers.IGridProxyable;
import appeng.util.Platform;

/**
 * Singularity Power Core block — draws EU from adjacent GT cables and converts
 * it to AE power for the player's SingularityGrid.
 */
public class BlockSingularityPowerCore extends Block implements ITileEntityProvider {

    public static final int GUI_ID = 11;

    public BlockSingularityPowerCore() {
        super(Material.iron);
        setBlockName("singularity_power_core");
        setBlockTextureName("appliedenergistics2:BlockEnergyAcceptor");
        setHardness(2.0f);
    }

    @Override
    public boolean onBlockActivated(final World world, final int x, final int y, final int z, final EntityPlayer player,
        final int side, final float hitX, final float hitY, final float hitZ) {
        if (!world.isRemote) {
            player.openGui(SingularityME.instance, GUI_ID, world, x, y, z);
        }
        return true;
    }

    @Override
    public void breakBlock(final World world, final int x, final int y, final int z, final Block block,
        final int metadata) {
        final TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileSingularityPowerCore core) {
            final List<ItemStack> drops = new ArrayList<>();
            core.addPowerComponentDrops(drops);
            Platform.spawnDrops(world, x, y, z, drops);
        }
        super.breakBlock(world, x, y, z, block, metadata);
    }

    @Override
    public TileEntity createNewTileEntity(final World world, final int metadata) {
        return new TileSingularityPowerCore();
    }

    @Override
    public boolean hasTileEntity(final int metadata) {
        return true;
    }

    @Override
    public void onBlockPlacedBy(final World world, final int x, final int y, final int z, final EntityLivingBase placer,
        final ItemStack stack) {
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

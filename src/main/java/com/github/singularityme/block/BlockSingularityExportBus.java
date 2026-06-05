package com.github.singularityme.block;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.github.singularityme.SingularityME;
import com.github.singularityme.core.SingularityPermissionHelper;
import com.github.singularityme.tile.TileSingularityExportBus;

import appeng.me.helpers.IGridProxyable;

/**
 * Singularity Export Bus block — extracts items from the player's global
 * SingularityGrid and pushes them into the adjacent container each tick.
 */
public class BlockSingularityExportBus extends BlockSingularityPartLike {

    public static final int GUI_ID = 4;
    private static int renderTypeId;

    public BlockSingularityExportBus() {
        super(SingularityPartGeometry.Kind.EXPORT_BUS);
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
    public int onBlockPlaced(final World world, final int x, final int y, final int z, final int side, final float hitX,
        final float hitY, final float hitZ, final int metadata) {
        return ForgeDirection.getOrientation(side)
            .getOpposite()
            .ordinal();
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
    public boolean onBlockActivated(final World world, final int x, final int y, final int z, final EntityPlayer player,
        final int side, final float fx, final float fy, final float fz) {
        if (BlockSingularityStorageBus.tryWrenchRotate(world, x, y, z, player)) return true;
        if (!world.isRemote) {
            final TileEntity te = world.getTileEntity(x, y, z);
            if (!SingularityPermissionHelper.checkUse(world, te, player)) return true;
            player.openGui(SingularityME.instance, GUI_ID, world, x, y, z);
        }
        return true;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return super.renderAsNormalBlock();
    }

    @Override
    public int getRenderType() {
        return renderTypeId == 0 ? super.getRenderType() : renderTypeId;
    }

    public static void setRenderTypeId(final int renderTypeId) {
        BlockSingularityExportBus.renderTypeId = renderTypeId;
    }

    @Override
    public boolean isOpaqueCube() {
        return super.isOpaqueCube();
    }
}

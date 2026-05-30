package com.github.singularityme.block;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.github.singularityme.SingularityME;
import com.github.singularityme.tile.TileSingularityTerminal;

import appeng.me.helpers.IGridProxyable;
import appeng.util.Platform;

/**
 * Singularity Terminal block — right-click to open the AE2 terminal GUI backed
 * by the player's global SingularityGrid.
 */
public class BlockSingularityTerminal extends BlockSingularityPartLike {

    public static final int GUI_ID = 1;
    private static int renderTypeId;

    public BlockSingularityTerminal() {
        super(SingularityPartGeometry.Kind.TERMINAL);
        setBlockName("singularity_terminal");
        setBlockTextureName("appliedenergistics2:ItemPart.Terminal");
        setHardness(2.0f);
    }

    @Override
    public TileEntity createNewTileEntity(final World world, final int metadata) {
        return new TileSingularityTerminal();
    }

    protected int getGuiId() {
        return GUI_ID;
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

        TileEntity te = world.getTileEntity(x, y, z);
        if (placer instanceof EntityPlayer) {
            if (te instanceof IGridProxyable gp) {
                gp.getProxy()
                    .setOwner((EntityPlayer) placer);
            }
        }
        if (te instanceof TileSingularityTerminal terminal) {
            terminal.setPlacementSpin(placer, ForgeDirection.getOrientation(world.getBlockMetadata(x, y, z)));
        }
    }

    @Override
    public boolean onBlockActivated(final World world, final int x, final int y, final int z, final EntityPlayer player,
        final int side, final float hitX, final float hitY, final float hitZ) {
        if (!player.isSneaking()
            && BlockSingularityStorageBus.isAEWrench(player, player.getCurrentEquippedItem(), x, y, z)) {
            if (!world.isRemote) {
                TileEntity te = world.getTileEntity(x, y, z);
                if (te instanceof TileSingularityTerminal terminal) {
                    terminal.rotateSpin();
                }
            }
            return true;
        }
        if (!world.isRemote) {
            player.openGui(SingularityME.instance, getGuiId(), world, x, y, z);
        }
        return true;
    }

    @Override
    public void breakBlock(final World world, final int x, final int y, final int z, final Block block,
        final int metadata) {
        final TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileSingularityTerminal terminal) {
            final List<ItemStack> drops = new ArrayList<>();
            terminal.addTerminalDrops(drops);
            Platform.spawnDrops(world, x, y, z, drops);
        }
        super.breakBlock(world, x, y, z, block, metadata);
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
        BlockSingularityTerminal.renderTypeId = renderTypeId;
    }

    @Override
    public boolean isOpaqueCube() {
        return super.isOpaqueCube();
    }
}

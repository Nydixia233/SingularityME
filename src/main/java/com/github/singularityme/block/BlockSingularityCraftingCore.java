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
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.github.singularityme.SingularityME;
import com.github.singularityme.core.SingularityPermissionHelper;
import com.github.singularityme.tile.TileSingularityCraftingCore;

import appeng.me.helpers.IGridProxyable;
import appeng.util.Platform;

/**
 * Single-block Crafting CPU for a player's SingularityGrid.
 */
public class BlockSingularityCraftingCore extends Block implements ITileEntityProvider {

    public static final int GUI_ID = 9;
    private static int renderTypeId;

    public BlockSingularityCraftingCore() {
        super(Material.iron);
        setBlockName("singularity_crafting_core");
        setBlockTextureName("appliedenergistics2:BlockCraftingUnit");
        setHardness(6.0f);
        setResistance(6.0f);
        setHarvestLevel("pickaxe", 2);
    }

    @Override
    public TileEntity createNewTileEntity(final World world, final int metadata) {
        return new TileSingularityCraftingCore();
    }

    @Override
    public boolean hasTileEntity(final int metadata) {
        return true;
    }

    @Override
    public void onBlockPlacedBy(final World world, final int x, final int y, final int z, final EntityLivingBase placer,
        final ItemStack stack) {
        super.onBlockPlacedBy(world, x, y, z, placer, stack);
        final ForgeDirection[] orientation = orientationFromPlacer(placer);
        world.setBlockMetadataWithNotify(x, y, z, orientation[0].ordinal(), 2);
        final TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileSingularityCraftingCore core) {
            core.setOrientation(orientation[0], orientation[1]);
        }
        if (placer instanceof EntityPlayer && te instanceof IGridProxyable gp) {
            gp.getProxy()
                .setOwner((EntityPlayer) placer);
        }
    }

    private ForgeDirection[] orientationFromPlacer(final EntityLivingBase placer) {
        ForgeDirection up = ForgeDirection.UP;
        final int rotation = MathHelper.floor_double((placer.rotationYaw * 4.0F) / 360.0F + 2.5D) & 3;
        ForgeDirection forward = switch (rotation) {
            default -> ForgeDirection.SOUTH;
            case 1 -> ForgeDirection.WEST;
            case 2 -> ForgeDirection.NORTH;
            case 3 -> ForgeDirection.EAST;
        };

        if (placer.rotationPitch > 65.0F) {
            up = forward.getOpposite();
            forward = ForgeDirection.UP;
        } else if (placer.rotationPitch < -65.0F) {
            up = forward.getOpposite();
            forward = ForgeDirection.DOWN;
        }

        return new ForgeDirection[] { forward, up };
    }

    @Override
    public boolean onBlockActivated(final World world, final int x, final int y, final int z, final EntityPlayer player,
        final int side, final float hitX, final float hitY, final float hitZ) {
        if (!world.isRemote) {
            final TileEntity te = world.getTileEntity(x, y, z);
            if (!SingularityPermissionHelper.checkUse(world, te, player)) return true;
            player.openGui(SingularityME.instance, GUI_ID, world, x, y, z);
        }
        return true;
    }

    @Override
    public boolean removedByPlayer(final World world, final EntityPlayer player, final int x, final int y, final int z,
        final boolean willHarvest) {
        final TileEntity te = world.getTileEntity(x, y, z);
        if (!world.isRemote && !SingularityPermissionHelper.checkBuild(world, te, player)) return false;
        return super.removedByPlayer(world, player, x, y, z, willHarvest);
    }

    @Override
    public void breakBlock(final World world, final int x, final int y, final int z, final Block block,
        final int metadata) {
        final TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileSingularityCraftingCore core) {
            final List<ItemStack> drops = new ArrayList<>();
            core.addCoreDrops(drops);
            core.destroyForBlockBreak();
            Platform.spawnDrops(world, x, y, z, drops);
        }
        super.breakBlock(world, x, y, z, block, metadata);
    }

    @Override
    public boolean renderAsNormalBlock() {
        return true;
    }

    @Override
    public int getRenderType() {
        return renderTypeId == 0 ? super.getRenderType() : renderTypeId;
    }

    public static void setRenderTypeId(final int renderTypeId) {
        BlockSingularityCraftingCore.renderTypeId = renderTypeId;
    }

    @Override
    public boolean isOpaqueCube() {
        return true;
    }
}

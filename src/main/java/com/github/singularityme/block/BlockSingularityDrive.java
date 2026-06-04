package com.github.singularityme.block;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.github.singularityme.SingularityME;
import com.github.singularityme.core.SingularityPermissionHelper;
import com.github.singularityme.tile.TileSingularityDrive;

import appeng.me.helpers.IGridProxyable;
import appeng.util.Platform;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Singularity Drive block — accepts AE2 storage cells and contributes their
 * capacity to the player's global SingularityGrid.
 */
public class BlockSingularityDrive extends Block implements ITileEntityProvider {

    public static final int GUI_ID = 5;
    private static int renderTypeId;
    @SideOnly(Side.CLIENT)
    private IIcon topIcon;
    @SideOnly(Side.CLIENT)
    private IIcon bottomIcon;
    @SideOnly(Side.CLIENT)
    private IIcon sideIcon;
    @SideOnly(Side.CLIENT)
    private IIcon frontIcon;
    @SideOnly(Side.CLIENT)
    private IIcon backIcon;

    public BlockSingularityDrive() {
        super(Material.iron);
        setBlockName("singularity_drive");
        setBlockTextureName("appliedenergistics2:BlockDrive");
        setHardness(2.0f);
    }

    @Override
    public TileEntity createNewTileEntity(final World world, final int metadata) {
        return new TileSingularityDrive();
    }

    @Override
    public boolean hasTileEntity(final int metadata) {
        return true;
    }

    @Override
    public void onBlockPlacedBy(final World world, final int x, final int y, final int z, final EntityLivingBase placer,
        final ItemStack stack) {
        super.onBlockPlacedBy(world, x, y, z, placer, stack);
        final ForgeDirection[] orientation = driveOrientationFromPlacer(placer);
        world.setBlockMetadataWithNotify(x, y, z, orientation[0].ordinal(), 2);

        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileSingularityDrive drive) {
            drive.setOrientation(orientation[0], orientation[1]);
        }
        if (placer instanceof EntityPlayer) {
            if (te instanceof IGridProxyable gp) {
                gp.getProxy()
                    .setOwner((EntityPlayer) placer);
            }
        }
    }

    private ForgeDirection[] driveOrientationFromPlacer(final EntityLivingBase placer) {
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
        final int side, final float fx, final float fy, final float fz) {
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
        if (!world.isRemote && !SingularityPermissionHelper.checkBuild(world, te, player)) {
            SingularityBlockSyncHelper.resyncDeniedBreak(world, player, x, y, z);
            return false;
        }
        return super.removedByPlayer(world, player, x, y, z, willHarvest);
    }

    @Override
    public void breakBlock(final World world, final int x, final int y, final int z, final Block block,
        final int metadata) {
        final TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileSingularityDrive drive) {
            final List<ItemStack> drops = new ArrayList<>();
            drive.addCellDrops(drops);
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
        BlockSingularityDrive.renderTypeId = renderTypeId;
    }

    @Override
    public boolean isOpaqueCube() {
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(final IIconRegister registry) {
        this.topIcon = registry.registerIcon("appliedenergistics2:BlockDrive");
        this.bottomIcon = registry.registerIcon("appliedenergistics2:BlockDriveBottom");
        this.sideIcon = registry.registerIcon("appliedenergistics2:BlockDriveSide");
        this.frontIcon = registry.registerIcon("appliedenergistics2:BlockDriveFront");
        this.backIcon = this.topIcon;
        this.blockIcon = this.topIcon;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(final int side, final int metadata) {
        return iconForLocalSide(ForgeDirection.getOrientation(side));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(final IBlockAccess world, final int x, final int y, final int z, final int side) {
        final TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileSingularityDrive drive) {
            return iconForLocalSide(
                mapRotation(drive.getForward(), drive.getUp(), ForgeDirection.getOrientation(side)));
        }
        return getIcon(side, world.getBlockMetadata(x, y, z));
    }

    @SideOnly(Side.CLIENT)
    private IIcon iconForLocalSide(final ForgeDirection side) {
        return switch (side) {
            case DOWN -> this.bottomIcon;
            case UP -> this.topIcon;
            case NORTH -> this.backIcon;
            case SOUTH -> this.frontIcon;
            default -> this.sideIcon;
        };
    }

    private ForgeDirection mapRotation(final ForgeDirection forward, final ForgeDirection up,
        final ForgeDirection side) {
        if (forward == null || up == null || forward == ForgeDirection.UNKNOWN || up == ForgeDirection.UNKNOWN) {
            return side;
        }

        final int westX = forward.offsetY * up.offsetZ - forward.offsetZ * up.offsetY;
        final int westY = forward.offsetZ * up.offsetX - forward.offsetX * up.offsetZ;
        final int westZ = forward.offsetX * up.offsetY - forward.offsetY * up.offsetX;
        ForgeDirection west = ForgeDirection.UNKNOWN;
        for (final ForgeDirection candidate : ForgeDirection.VALID_DIRECTIONS) {
            if (candidate.offsetX == westX && candidate.offsetY == westY && candidate.offsetZ == westZ) {
                west = candidate;
                break;
            }
        }

        if (side == forward) return ForgeDirection.SOUTH;
        if (side == forward.getOpposite()) return ForgeDirection.NORTH;
        if (side == up) return ForgeDirection.UP;
        if (side == up.getOpposite()) return ForgeDirection.DOWN;
        if (side == west) return ForgeDirection.WEST;
        if (side == west.getOpposite()) return ForgeDirection.EAST;
        return ForgeDirection.UNKNOWN;
    }
}

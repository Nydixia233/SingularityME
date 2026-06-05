package com.github.singularityme.block;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.github.singularityme.core.SingularityPermissionHelper;
import com.github.singularityme.tile.TileSingularityInterface;

import appeng.tile.grid.AENetworkInvTile;
import appeng.util.Platform;

public abstract class BlockSingularityPartLike extends Block implements ITileEntityProvider {

    private final SingularityPartGeometry.Kind geometryKind;

    protected BlockSingularityPartLike(final SingularityPartGeometry.Kind geometryKind) {
        super(Material.iron);
        this.geometryKind = geometryKind;
    }

    protected ForgeDirection getPartFace(final IBlockAccess world, final int x, final int y, final int z) {
        final ForgeDirection side = ForgeDirection.getOrientation(world.getBlockMetadata(x, y, z));
        return side == ForgeDirection.UNKNOWN ? ForgeDirection.SOUTH : side;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public void setBlockBoundsBasedOnState(final IBlockAccess world, final int x, final int y, final int z) {
        SingularityPartGeometry.setUnionBounds(this, this.geometryKind, getPartFace(world, x, y, z));
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBoxFromPool(final World world, final int x, final int y, final int z) {
        return SingularityPartGeometry.getUnionBox(this.geometryKind, getPartFace(world, x, y, z))
            .offset(x, y, z);
    }

    @Override
    public AxisAlignedBB getSelectedBoundingBoxFromPool(final World world, final int x, final int y, final int z) {
        return getCollisionBoundingBoxFromPool(world, x, y, z);
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
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addCollisionBoxesToList(final World world, final int x, final int y, final int z,
        final AxisAlignedBB mask, final List out, final Entity entity) {
        for (final SingularityPartGeometry.Box box : SingularityPartGeometry
            .getBoxes(this.geometryKind, getPartFace(world, x, y, z))) {
            final AxisAlignedBB bb = box.toAABB(x, y, z);
            if (mask.intersectsWith(bb)) {
                out.add(bb);
            }
        }
    }

    @Override
    public MovingObjectPosition collisionRayTrace(final World world, final int x, final int y, final int z,
        final Vec3 start, final Vec3 end) {
        MovingObjectPosition closest = null;
        double closestDistance = 0.0D;

        for (final SingularityPartGeometry.Box box : SingularityPartGeometry
            .getBoxes(this.geometryKind, getPartFace(world, x, y, z))) {
            box.applyTo(this);
            final MovingObjectPosition hit = super.collisionRayTrace(world, x, y, z, start, end);
            if (hit != null) {
                final double distance = start.squareDistanceTo(hit.hitVec);
                if (closest == null || distance < closestDistance) {
                    closest = hit;
                    closestDistance = distance;
                }
            }
        }

        SingularityPartGeometry.setUnionBounds(this, this.geometryKind, getPartFace(world, x, y, z));
        return closest;
    }

    @Override
    public void breakBlock(final World world, final int x, final int y, final int z, final Block block,
        final int metadata) {
        final TileEntity te = world.getTileEntity(x, y, z);
        final List<ItemStack> drops = new ArrayList<>();

        if (te instanceof TileSingularityInterface iface) {
            iface.getDrops(world, x, y, z, drops);
        } else if (te instanceof AENetworkInvTile invTile) {
            collectInventoryDrops(invTile.getInternalInventory(), drops);
        }

        if (!drops.isEmpty()) {
            Platform.spawnDrops(world, x, y, z, drops);
        }
        super.breakBlock(world, x, y, z, block, metadata);
    }

    private static void collectInventoryDrops(final IInventory inventory, final List<ItemStack> drops) {
        if (inventory == null) return;
        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
            final ItemStack stack = inventory.getStackInSlot(slot);
            if (stack != null) {
                drops.add(stack);
            }
        }
    }
}

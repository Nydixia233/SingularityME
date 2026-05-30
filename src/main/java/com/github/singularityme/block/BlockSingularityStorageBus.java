package com.github.singularityme.block;

import net.minecraft.block.Block;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.github.singularityme.SingularityME;
import com.github.singularityme.tile.TileSingularityStorageBus;

import appeng.api.implementations.items.IAEWrench;
import appeng.me.helpers.IGridProxyable;

/**
 * Singularity Storage Bus block.
 *
 * <p>
 * Block metadata (0–5) stores the {@link ForgeDirection} ordinal of the face the bus is attached
 * to. The bus exposes the inventory on that face to the player's global SingularityGrid.
 */
public class BlockSingularityStorageBus extends BlockSingularityPartLike {

    public static final int GUI_ID = 2;
    private static int renderTypeId;

    public BlockSingularityStorageBus() {
        super(SingularityPartGeometry.Kind.STORAGE_BUS);
        setBlockName("singularity_storage_bus");
        setBlockTextureName("appliedenergistics2:ItemPart.StorageBus");
        setHardness(2.0f);
    }

    @Override
    public int onBlockPlaced(final World world, final int x, final int y, final int z, final int side, final float hitX,
        final float hitY, final float hitZ, final int metadata) {
        return ForgeDirection.getOrientation(side)
            .getOpposite()
            .ordinal();
    }

    @Override
    public TileEntity createNewTileEntity(final World world, final int metadata) {
        return new TileSingularityStorageBus();
    }

    @Override
    public boolean hasTileEntity(final int metadata) {
        return true;
    }

    /**
     * Store the facing direction in metadata and set the proxy owner so AE2 can resolve the
     * playerID from the placer's GameProfile.
     */
    @Override
    public void onBlockPlacedBy(final World world, final int x, final int y, final int z, final EntityLivingBase placer,
        final ItemStack stack) {
        super.onBlockPlacedBy(world, x, y, z, placer, stack);

        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof IGridProxyable gp && placer instanceof EntityPlayer player) {
            gp.getProxy()
                .setOwner(player);
        }
    }

    /**
     * Derives a {@link ForgeDirection} from the placer's look direction.
     * Shared by all bus blocks that store facing in block metadata.
     */
    public static ForgeDirection facingFromPlacer(final EntityLivingBase placer) {
        if (!(placer instanceof EntityPlayer)) return ForgeDirection.DOWN;
        double pitch = placer.rotationPitch;
        if (pitch > 45) return ForgeDirection.DOWN;
        if (pitch < -45) return ForgeDirection.UP;
        int yaw = Math.floorMod((int) Math.floor(placer.rotationYaw + 180 + 45), 360) / 90;
        return switch (yaw) {
            case 0 -> ForgeDirection.SOUTH;
            case 1 -> ForgeDirection.WEST;
            case 2 -> ForgeDirection.NORTH;
            default -> ForgeDirection.EAST;
        };
    }

    /**
     * Returns true if the player is holding an AE2 wrench they can use on this block.
     * Mirrors {@code Platform.isWrench} but limited to the IAEWrench path that has no
     * mod-integration dependencies, which is sufficient for sneak-rotate behavior.
     */
    public static boolean isAEWrench(final EntityPlayer player, final ItemStack held, final int x, final int y,
        final int z) {
        if (held == null) return false;
        return held.getItem() instanceof IAEWrench wrench && wrench.canWrench(held, player, x, y, z);
    }

    /**
     * Sneak-wrench rotates the facing through all six {@link ForgeDirection} values.
     * Returns true if the rotation was performed (caller should swallow the click).
     */
    public static boolean tryWrenchRotate(final World world, final int x, final int y, final int z,
        final EntityPlayer player) {
        if (!player.isSneaking()) return false;
        final ItemStack held = player.getCurrentEquippedItem();
        if (!isAEWrench(player, held, x, y, z)) return false;
        if (world.isRemote) return true;
        final int meta = world.getBlockMetadata(x, y, z);
        final int next = (meta + 1) % 6;
        world.setBlockMetadataWithNotify(x, y, z, next, 2);
        return true;
    }

    @Override
    public boolean onBlockActivated(final World world, final int x, final int y, final int z, final EntityPlayer player,
        final int side, final float fx, final float fy, final float fz) {
        if (tryWrenchRotate(world, x, y, z, player)) {
            if (!world.isRemote) {
                TileEntity te = world.getTileEntity(x, y, z);
                if (te instanceof TileSingularityStorageBus storageBus) {
                    storageBus.onAdjacentStorageChanged();
                }
            }
            return true;
        }
        if (!world.isRemote) {
            player.openGui(SingularityME.instance, GUI_ID, world, x, y, z);
        }
        return true;
    }

    @Override
    public void onNeighborBlockChange(final World world, final int x, final int y, final int z, final Block neighbor) {
        super.onNeighborBlockChange(world, x, y, z, neighbor);
        if (world.isRemote) return;

        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileSingularityStorageBus storageBus) {
            storageBus.onAdjacentStorageChanged();
        }
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
        BlockSingularityStorageBus.renderTypeId = renderTypeId;
    }

    @Override
    public boolean isOpaqueCube() {
        return super.isOpaqueCube();
    }
}

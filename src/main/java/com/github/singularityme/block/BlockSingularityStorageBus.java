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
import com.github.singularityme.tile.TileSingularityStorageBus;

import appeng.me.helpers.IGridProxyable;

/**
 * Singularity Storage Bus block.
 *
 * <p>
 * Block metadata (0–5) stores the {@link ForgeDirection} ordinal of the face the bus is attached
 * to. The bus exposes the inventory on that face to the player's global SingularityGrid.
 */
public class BlockSingularityStorageBus extends Block implements ITileEntityProvider {

    public static final int GUI_ID = 2;

    public BlockSingularityStorageBus() {
        super(Material.iron);
        setBlockName("singularity_storage_bus");
        setBlockTextureName("appliedenergistics2:ItemPart.StorageBus");
        setHardness(2.0f);
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

        world.setBlockMetadataWithNotify(x, y, z, facingFromPlacer(placer).ordinal(), 2);

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

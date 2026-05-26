package com.github.singularityme.init;

import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.github.singularityme.core.SingularityNetworkManager;
import com.github.singularityme.grid.SingularityGrid;
import com.github.singularityme.tile.TileSingularityProbe;

import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.storage.IStorageGrid;
import appeng.me.GridNode;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.api.IWailaDataProvider;

/**
 * WAILA tooltip provider for Singularity ME tiles.
 *
 * <p>
 * For {@link TileSingularityProbe}: displays full grid diagnostics (playerID, active state,
 * power, item count).
 *
 * <p>
 * For all other Singularity tiles: displays the block's facing direction (read from block
 * metadata) so the player can tell which side the bus is pointing at.
 *
 * <p>
 * Registered via {@link WailaRegistrar}.
 */
public class WailaSingularityProbeProvider implements IWailaDataProvider {

    public static final WailaSingularityProbeProvider INSTANCE = new WailaSingularityProbeProvider();

    private WailaSingularityProbeProvider() {}

    // ---- IWailaDataProvider ----

    @Override
    public ItemStack getWailaStack(final IWailaDataAccessor accessor, final IWailaConfigHandler config) {
        return null;
    }

    @Override
    public List<String> getWailaHead(final ItemStack stack, final List<String> tooltip,
        final IWailaDataAccessor accessor, final IWailaConfigHandler config) {
        return tooltip;
    }

    @Override
    public List<String> getWailaBody(final ItemStack stack, final List<String> tooltip,
        final IWailaDataAccessor accessor, final IWailaConfigHandler config) {
        NBTTagCompound tag = accessor.getNBTData();

        // Probe-specific diagnostics
        if (tag.hasKey("sme_playerID")) {
            int playerID = tag.getInteger("sme_playerID");
            boolean nodeActive = tag.getBoolean("sme_nodeActive");
            boolean gridExists = tag.getBoolean("sme_gridExists");
            boolean powered = tag.getBoolean("sme_powered");
            double stored = tag.getDouble("sme_storedPower");
            double max = tag.getDouble("sme_maxPower");
            int itemTypes = tag.getInteger("sme_itemTypes");

            tooltip.add("§7[SingularityME 探针]§r");
            tooltip.add("玩家 ID: §e" + playerID + "§r");
            tooltip.add("节点 active: " + (nodeActive ? "§a是§r" : "§c否§r"));
            tooltip.add("网格: " + (gridExists ? "§a存在§r" : "§c不存在§r"));
            if (gridExists) {
                tooltip.add("供电: " + (powered ? "§a是§r" : "§c否§r"));
                String storedStr = stored > 1e15 ? "∞" : String.format("%.0f", stored);
                String maxStr = max > 1e15 ? "∞" : String.format("%.0f", max);
                tooltip.add("AE 存储: §b" + storedStr + "§r / §b" + maxStr + "§r AE");
                tooltip.add("物品种类: §b" + itemTypes + "§r");
            }
        }

        // Facing direction for bus/drive tiles
        if (tag.hasKey("sme_facing")) {
            int facingOrd = tag.getInteger("sme_facing");
            ForgeDirection facing = ForgeDirection.getOrientation(facingOrd);
            String facingName = switch (facing) {
                case NORTH -> "北 (North)";
                case SOUTH -> "南 (South)";
                case WEST -> "西 (West)";
                case EAST -> "东 (East)";
                case UP -> "上 (Up)";
                case DOWN -> "下 (Down)";
                default -> facing.name();
            };
            tooltip.add("§7朝向: §f" + facingName + "§r");
        }

        return tooltip;
    }

    @Override
    public List<String> getWailaTail(final ItemStack stack, final List<String> tooltip,
        final IWailaDataAccessor accessor, final IWailaConfigHandler config) {
        return tooltip;
    }

    /**
     * Called server-side: collect tile state into the NBT tag sent to the client.
     *
     * <p>
     * For {@link TileSingularityProbe}: full grid diagnostics.
     * For all other tiles: just the facing direction from block metadata.
     */
    @Override
    public NBTTagCompound getNBTData(final EntityPlayerMP player, final TileEntity te, final NBTTagCompound tag,
        final World world, final int x, final int y, final int z) {

        if (te instanceof TileSingularityProbe probe) {
            GridNode node = (GridNode) probe.getProxy()
                .getNode();
            int playerID = node != null ? node.getPlayerID() : -1;

            tag.setInteger("sme_playerID", playerID);
            tag.setBoolean(
                "sme_nodeActive",
                probe.getProxy()
                    .isActive());

            SingularityGrid sg = playerID >= 0 ? SingularityNetworkManager.INSTANCE.getGridForPlayer(playerID) : null;
            tag.setBoolean("sme_gridExists", sg != null);

            if (sg != null) {
                IEnergyGrid eg = sg.getCache(IEnergyGrid.class);
                if (eg != null) {
                    tag.setBoolean("sme_powered", eg.isNetworkPowered());
                    tag.setDouble("sme_storedPower", eg.getStoredPower());
                    tag.setDouble("sme_maxPower", eg.getMaxStoredPower());
                }
                IStorageGrid storage = sg.getCache(IStorageGrid.class);
                if (storage != null) {
                    try {
                        tag.setInteger(
                            "sme_itemTypes",
                            storage.getItemInventory()
                                .getStorageList()
                                .size());
                    } catch (Exception ignored) {
                        tag.setInteger("sme_itemTypes", -1);
                    }
                }
            }
        } else {
            // For all other Singularity tiles: write facing from block metadata
            tag.setInteger("sme_facing", world.getBlockMetadata(x, y, z));
        }

        return tag;
    }
}

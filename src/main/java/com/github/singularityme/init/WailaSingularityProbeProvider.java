package com.github.singularityme.init;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.StatCollector;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.util.ForgeDirection;

import com.github.singularityme.core.SingularityNetworkManager;
import com.github.singularityme.core.SingularityNetworkRegistry;
import com.github.singularityme.core.SingularityNetworkRegistry.NetworkMeta;
import com.github.singularityme.grid.SingularityGrid;
import com.github.singularityme.init.SingularityPrototypeRegistry.DeviceInfo;
import com.github.singularityme.init.SingularityPrototypeRegistry.Stats;
import com.github.singularityme.init.SingularityPrototypeRegistry.SyncLine;
import com.github.singularityme.init.SingularityPrototypeRegistry.SyncStatus;
import com.github.singularityme.init.SingularityPrototypeRegistry.UpgradeMirrorState;
import com.github.singularityme.tile.ISingularityNetworkDevice;
import com.github.singularityme.tile.TileSingularityCraftingCore;
import com.github.singularityme.tile.TileSingularityCraftingTerminal;
import com.github.singularityme.tile.TileSingularityDrive;
import com.github.singularityme.tile.TileSingularityExportBus;
import com.github.singularityme.tile.TileSingularityImportBus;
import com.github.singularityme.tile.TileSingularityInterface;
import com.github.singularityme.tile.TileSingularityPatternTerminal;
import com.github.singularityme.tile.TileSingularityPowerCore;
import com.github.singularityme.tile.TileSingularityProbe;
import com.github.singularityme.tile.TileSingularityStorageBus;
import com.github.singularityme.tile.TileSingularityTerminal;

import appeng.api.AEApi;
import appeng.api.config.Upgrades;
import appeng.api.implementations.IUpgradeableHost;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.storage.IStorageGrid;
import appeng.me.GridNode;
import appeng.me.helpers.IGridProxyable;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.api.IWailaDataProvider;

/**
 * WAILA provider for Singularity devices.
 *
 * <p>
 * Ordinary devices always show Status, Power, then Function. The Probe additionally shows
 * network-wide diagnostics and Prototype Sync details.
 */
public class WailaSingularityProbeProvider implements IWailaDataProvider {

    public static final WailaSingularityProbeProvider INSTANCE = new WailaSingularityProbeProvider();

    private static final String COLOR_GRAY = "\u00a77";
    private static final String COLOR_WHITE = "\u00a7f";
    private static final String COLOR_YELLOW = "\u00a7e";
    private static final String COLOR_GREEN = "\u00a7a";
    private static final String COLOR_RED = "\u00a7c";
    private static final String COLOR_AQUA = "\u00a7b";
    private static final String COLOR_PURPLE = "\u00a75";
    private static final String COLOR_RESET = "\u00a7r";
    private static final String NBT_SEPARATOR = "\n";
    private static volatile boolean chunksToUnloadFieldResolved = false;
    private static volatile Field chunksToUnloadField = null;

    private WailaSingularityProbeProvider() {}

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
        final NBTTagCompound tag = accessor.getNBTData();
        final boolean isProbe = tag.getBoolean("sme_isProbe");

        if (tag.hasKey("sme_common")) {
            addStatusSection(tooltip, tag);
            addPowerSection(tooltip, tag);
            addFunctionSection(tooltip, tag);
        }

        if (isProbe) {
            addProbeDiagnostics(tooltip, tag);
            addSyncDiagnostics(tooltip, tag);
        }

        return tooltip;
    }

    @Override
    public List<String> getWailaTail(final ItemStack stack, final List<String> tooltip,
        final IWailaDataAccessor accessor, final IWailaConfigHandler config) {
        return tooltip;
    }

    @Override
    public NBTTagCompound getNBTData(final EntityPlayerMP player, final TileEntity te, final NBTTagCompound tag,
        final World world, final int x, final int y, final int z) {
        writeCommonData(te, tag, world, x, y, z);
        writeFunctionData(te, tag, world, x, y, z);
        if (te instanceof TileSingularityProbe) {
            tag.setBoolean("sme_isProbe", true);
            writeProbeData(te, tag);
            writeSyncData(te, tag);
        }
        return tag;
    }

    private static void addStatusSection(final List<String> tooltip, final NBTTagCompound tag) {
        tooltip.add(COLOR_GRAY + "[" + tr("waila.singularityme.section.status") + "]" + COLOR_RESET);
        tooltip.add(
            trf("waila.singularityme.status.player_id", COLOR_YELLOW + tag.getInteger("sme_playerID") + COLOR_RESET));
        tooltip.add(trf("waila.singularityme.status.placer", formatPlayerIdentity(tag, "sme_placer")));
        tooltip.add(trf("waila.singularityme.status.network", formatNetworkIdentity(tag)));
        if (tag.getInteger("sme_gridOwnerID") >= 0
            && tag.getInteger("sme_gridOwnerID") != tag.getInteger("sme_playerID")) {
            tooltip.add(trf("waila.singularityme.status.grid_owner", formatPlayerIdentity(tag, "sme_gridOwner")));
        }
        tooltip.add(trf("waila.singularityme.status.grid", existsMissing(tag.getBoolean("sme_gridExists"))));
        tooltip.add(trf("waila.singularityme.status.node_active", yesNo(tag.getBoolean("sme_nodeActive"))));
        tooltip.add(trf("waila.singularityme.status.powered", yesNo(tag.getBoolean("sme_networkPowered"))));
        tooltip.add(trf("waila.singularityme.status.contribution", yesNo(tag.getBoolean("sme_contributionLoaded"))));
    }

    private static void addPowerSection(final List<String> tooltip, final NBTTagCompound tag) {
        tooltip.add(COLOR_GRAY + "[" + tr("waila.singularityme.section.power") + "]" + COLOR_RESET);
        tooltip.add(
            trf(
                "waila.singularityme.power.device_idle",
                COLOR_AQUA + formatDouble(tag.getDouble("sme_deviceIdle")) + COLOR_RESET));
        tooltip.add(
            trf(
                "waila.singularityme.power.network",
                COLOR_AQUA + formatDouble(tag.getDouble("sme_storedPower")) + COLOR_RESET,
                COLOR_AQUA + formatDouble(tag.getDouble("sme_maxPower")) + COLOR_RESET));
    }

    private static void addFunctionSection(final List<String> tooltip, final NBTTagCompound tag) {
        tooltip.add(COLOR_GRAY + "[" + tr("waila.singularityme.section.function") + "]" + COLOR_RESET);
        if (tag.hasKey("sme_facing")) {
            final ForgeDirection facing = ForgeDirection.getOrientation(tag.getInteger("sme_facing"));
            tooltip.add(trf("waila.singularityme.facing", COLOR_WHITE + translatedFacing(facing) + COLOR_RESET));
        }

        final String kind = tag.getString("sme_kind");
        if ("drive".equals(kind)) {
            tooltip.add(
                trf(
                    "waila.singularityme.drive.cells",
                    COLOR_AQUA + tag.getInteger("sme_driveCells") + COLOR_RESET,
                    COLOR_AQUA + tag.getInteger("sme_driveCellSlots") + COLOR_RESET));
            tooltip.add(trf("waila.singularityme.priority", COLOR_AQUA + tag.getInteger("sme_priority") + COLOR_RESET));
        } else if ("storage_bus".equals(kind)) {
            tooltip.add(trf("waila.singularityme.priority", COLOR_AQUA + tag.getInteger("sme_priority") + COLOR_RESET));
            tooltip.add(trf("waila.singularityme.upgrades", COLOR_AQUA + tag.getString("sme_upgrades") + COLOR_RESET));
            tooltip.add(
                trf("waila.singularityme.storage_bus.host_loaded", yesNo(tag.getBoolean("sme_storageBusHostLoaded"))));
            tooltip.add(
                trf(
                    "waila.singularityme.storage_bus.target_chunk_loaded",
                    yesNo(tag.getBoolean("sme_storageBusTargetChunkLoaded"))));
            tooltip.add(
                trf(
                    "waila.singularityme.storage_bus.target_loaded",
                    yesNo(tag.getBoolean("sme_storageBusTargetLoaded"))));
            tooltip.add(
                trf(
                    "waila.singularityme.storage_bus.host_accessible",
                    yesNo(tag.getBoolean("sme_storageBusHostAccessible"))));
            tooltip.add(
                trf(
                    "waila.singularityme.storage_bus.target_accessible",
                    yesNo(tag.getBoolean("sme_storageBusTargetAccessible"))));
        } else if ("import_bus".equals(kind) || "export_bus".equals(kind)) {
            tooltip.add(tr("waila.singularityme." + kind + ".mode"));
            tooltip.add(trf("waila.singularityme.upgrades", COLOR_AQUA + tag.getString("sme_upgrades") + COLOR_RESET));
        } else if ("interface".equals(kind)) {
            tooltip.add(trf("waila.singularityme.priority", COLOR_AQUA + tag.getInteger("sme_priority") + COLOR_RESET));
            tooltip.add(trf("waila.singularityme.interface.busy", yesNo(tag.getBoolean("sme_interfaceBusy"))));
        } else if ("power_core".equals(kind)) {
            tooltip.add(
                trf(
                    "waila.singularityme.power_core.buffer",
                    COLOR_AQUA + formatDouble(tag.getDouble("sme_powerCoreStored")) + COLOR_RESET,
                    COLOR_AQUA + formatDouble(tag.getDouble("sme_powerCoreMax")) + COLOR_RESET));
        } else if ("crafting_core".equals(kind)) {
            tooltip.add(
                trf(
                    "waila.singularityme.crafting_core.storage",
                    COLOR_AQUA + formatStorage(tag.getLong("sme_craftingCoreStorage")) + COLOR_RESET));
            tooltip.add(
                trf(
                    "waila.singularityme.crafting_core.coprocessors",
                    COLOR_AQUA + tag.getInteger("sme_craftingCoreCoProcessors") + COLOR_RESET));
            tooltip.add(
                trf(
                    "waila.singularityme.crafting_core.monitors",
                    COLOR_AQUA + tag.getInteger("sme_craftingCoreMonitors") + COLOR_RESET));
            tooltip.add(trf("waila.singularityme.crafting_core.busy", yesNo(tag.getBoolean("sme_craftingCoreBusy"))));
        } else if ("terminal".equals(kind) || "crafting_terminal".equals(kind) || "pattern_terminal".equals(kind)) {
            tooltip.add(tr("waila.singularityme." + kind + ".mode"));
            tooltip.add(
                trf(
                    "waila.singularityme.terminal.view_cells",
                    COLOR_AQUA + tag.getInteger("sme_viewCells") + COLOR_RESET));
        } else if ("probe".equals(kind)) {
            tooltip.add(tr("waila.singularityme.probe.mode"));
        }
    }

    private static void addProbeDiagnostics(final List<String> tooltip, final NBTTagCompound tag) {
        tooltip.add(COLOR_GRAY + "[" + tr("waila.singularityme.probe.title") + "]" + COLOR_RESET);
        tooltip.add(trf("waila.singularityme.probe.nodes", COLOR_AQUA + tag.getInteger("sme_nodeCount") + COLOR_RESET));
        tooltip.add(
            trf(
                "waila.singularityme.probe.idle_usage",
                COLOR_AQUA + formatDouble(tag.getDouble("sme_idleUsage")) + COLOR_RESET));
        tooltip.add(
            trf(
                "waila.singularityme.probe.avg_usage",
                COLOR_AQUA + formatDouble(tag.getDouble("sme_avgUsage")) + COLOR_RESET,
                COLOR_AQUA + formatDouble(tag.getDouble("sme_avgInjection")) + COLOR_RESET));
        tooltip.add(
            trf("waila.singularityme.probe.item_types", COLOR_AQUA + tag.getInteger("sme_itemTypes") + COLOR_RESET));
        tooltip
            .add(trf("waila.singularityme.probe.cpu_count", COLOR_AQUA + tag.getInteger("sme_cpuCount") + COLOR_RESET));
        tooltip.add(
            trf(
                "waila.singularityme.probe.stale_nodes",
                COLOR_AQUA + tag.getInteger("sme_staleNodes") + COLOR_RESET,
                COLOR_AQUA + tag.getInteger("sme_lastPrunedStaleNodes") + COLOR_RESET));
        tooltip.add(
            trf(
                "waila.singularityme.probe.storage_bus_contributions",
                COLOR_GREEN + tag.getInteger("sme_storageBusLoaded") + COLOR_RESET,
                COLOR_RED + tag.getInteger("sme_storageBusRetired") + COLOR_RESET));
        tooltip.add(
            trf(
                "waila.singularityme.probe.device_counts",
                COLOR_AQUA + tag.getString("sme_deviceCounts") + COLOR_RESET));
        if (tag.hasKey("sme_storageBusDetails")) {
            tooltip.add(COLOR_GRAY + "[Storage Bus Chunks]" + COLOR_RESET);
            addSplitLines(tooltip, tag.getString("sme_storageBusDetails"));
        }
    }

    private static void addSyncDiagnostics(final List<String> tooltip, final NBTTagCompound tag) {
        if (tag.hasKey("sme_syncTotal")) {
            tooltip.add(COLOR_GRAY + "[Singularity Sync Summary]" + COLOR_RESET);
            tooltip.add("Sync items: " + COLOR_AQUA + tag.getInteger("sme_syncTotal") + COLOR_RESET);
            tooltip.add("Auto mirror: " + COLOR_GREEN + tag.getInteger("sme_syncAutoMirror") + COLOR_RESET);
            tooltip.add("Delegate: " + COLOR_AQUA + tag.getInteger("sme_syncDelegate") + COLOR_RESET);
            tooltip.add("Adapt: " + COLOR_YELLOW + tag.getInteger("sme_syncAdapt") + COLOR_RESET);
            tooltip.add("Feature exempt: " + COLOR_PURPLE + tag.getInteger("sme_syncFeature") + COLOR_RESET);
            tooltip.add("Forbidden: " + COLOR_RED + tag.getInteger("sme_syncForbidden") + COLOR_RESET);
            tooltip.add("Not supported: " + COLOR_RED + tag.getInteger("sme_syncUnsupported") + COLOR_RESET);
            tooltip.add("Verify: " + COLOR_YELLOW + tag.getInteger("sme_syncVerify") + COLOR_RESET);
            tooltip.add("Mirror safe: " + COLOR_GREEN + tag.getInteger("sme_syncMirrorSafe") + COLOR_RESET);
            tooltip.add("Custom mirror: " + COLOR_YELLOW + tag.getInteger("sme_syncCustomMirror") + COLOR_RESET);
            tooltip.add("Singularity only: " + COLOR_PURPLE + tag.getInteger("sme_syncSingularityOnly") + COLOR_RESET);
        }

        if (tag.hasKey("sme_syncPrototype")) {
            tooltip.add(COLOR_GRAY + "[Singularity Prototype Sync]" + COLOR_RESET);
            tooltip.add("AE2 Prototype: " + COLOR_AQUA + tag.getString("sme_syncPrototype") + COLOR_RESET);
            addSplitLines(tooltip, tag.getString("sme_syncLines"));
            addSplitLines(tooltip, tag.getString("sme_syncUpgradeMirror"));
        }
    }

    private static void writeCommonData(final TileEntity te, final NBTTagCompound tag, final World world, final int x,
        final int y, final int z) {
        if (!(te instanceof IGridProxyable proxyable)) return;

        tag.setBoolean("sme_common", true);
        tag.setInteger("sme_facing", world.getBlockMetadata(x, y, z));
        tag.setDouble(
            "sme_deviceIdle",
            proxyable.getProxy()
                .getIdlePowerUsage());

        final IGridNode node = proxyable.getProxy()
            .getNode();
        final int playerID = node instanceof GridNode gridNode ? gridNode.getPlayerID() : -1;
        tag.setInteger("sme_playerID", playerID);
        writeNetworkIdentityData(te, tag, world, playerID);
        tag.setBoolean(
            "sme_nodeActive",
            proxyable.getProxy()
                .isActive());

        final SingularityGrid sg = getGridForDevice(te, playerID);
        tag.setBoolean("sme_gridExists", sg != null);
        tag.setBoolean("sme_contributionLoaded", isContributionLoaded(te, node, sg));

        if (sg != null) {
            final IEnergyGrid eg = sg.getCache(IEnergyGrid.class);
            if (eg != null) {
                tag.setBoolean("sme_networkPowered", eg.isNetworkPowered());
                tag.setDouble("sme_storedPower", sg.getVirtualAECurrentPower());
                tag.setDouble("sme_maxPower", sg.getVirtualAEMaxPower());
                tag.setDouble("sme_idleUsage", eg.getIdlePowerUsage());
                tag.setDouble("sme_avgUsage", eg.getAvgPowerUsage());
                tag.setDouble("sme_avgInjection", eg.getAvgPowerInjection());
            }
        }
    }

    private static void writeNetworkIdentityData(final TileEntity te, final NBTTagCompound tag, final World world,
        final int playerID) {
        int networkID = 0;
        int gridOwnerID = playerID;

        if (te instanceof ISingularityNetworkDevice device) {
            networkID = device.getNetworkID();
            gridOwnerID = device.getGridOwnerPlayerID() >= 0 ? device.getGridOwnerPlayerID() : playerID;
        }

        tag.setInteger("sme_networkID", networkID);
        tag.setInteger("sme_placerID", playerID);
        tag.setInteger("sme_gridOwnerID", gridOwnerID);
        tag.setString("sme_placerName", playerName(playerID));
        tag.setString("sme_gridOwnerName", playerName(gridOwnerID));

        final NetworkMeta meta = getNetworkMeta(world, networkID);
        if (meta != null) {
            tag.setBoolean("sme_networkKnown", true);
            tag.setString("sme_networkName", meta.name);
            tag.setInteger("sme_networkOwnerID", meta.ownerPlayerID);
        }
    }

    private static NetworkMeta getNetworkMeta(final World world, final int networkID) {
        if (networkID == 0) return null;
        final World registryWorld = getOverworld(world);
        if (registryWorld == null) return null;
        try {
            return SingularityNetworkRegistry.get(registryWorld)
                .getNetwork(networkID);
        } catch (final RuntimeException ignored) {
            return null;
        }
    }

    private static World getOverworld(final World fallback) {
        try {
            final MinecraftServer server = MinecraftServer.getServer();
            if (server != null) {
                final World overworld = server.worldServerForDimension(0);
                if (overworld != null) return overworld;
            }
        } catch (final RuntimeException ignored) {}
        return fallback;
    }

    private static void writeFunctionData(final TileEntity te, final NBTTagCompound tag, final World world, final int x,
        final int y, final int z) {
        if (te instanceof TileSingularityDrive drive) {
            tag.setString("sme_kind", "drive");
            tag.setInteger("sme_driveCellSlots", drive.getCellCount());
            tag.setInteger("sme_driveCells", countStacks(drive.getCellInventory()));
            tag.setInteger("sme_priority", drive.getPriorityValue());
        } else if (te instanceof TileSingularityStorageBus bus) {
            tag.setString("sme_kind", "storage_bus");
            tag.setInteger("sme_priority", bus.getPriorityValue());
            tag.setString("sme_upgrades", installedUpgrades(bus));
            tag.setBoolean("sme_storageBusHostLoaded", bus.isHostStillLoaded());
            tag.setBoolean("sme_storageBusTargetChunkLoaded", bus.isTargetChunkLoaded());
            tag.setBoolean("sme_storageBusTargetLoaded", bus.isTargetStillLoaded());
            tag.setBoolean("sme_storageBusHostAccessible", bus.isHostChunkNetworkAccessible());
            tag.setBoolean("sme_storageBusTargetAccessible", bus.isTargetChunkNetworkAccessible());
        } else if (te instanceof TileSingularityImportBus bus) {
            tag.setString("sme_kind", "import_bus");
            tag.setString("sme_upgrades", installedUpgrades(bus));
        } else if (te instanceof TileSingularityExportBus bus) {
            tag.setString("sme_kind", "export_bus");
            tag.setString("sme_upgrades", installedUpgrades(bus));
        } else if (te instanceof TileSingularityInterface iface) {
            tag.setString("sme_kind", "interface");
            tag.setInteger("sme_priority", iface.getPriority());
            tag.setBoolean("sme_interfaceBusy", iface.isBusy());
        } else if (te instanceof TileSingularityPowerCore powerCore) {
            tag.setString("sme_kind", "power_core");
            tag.setDouble("sme_powerCoreStored", powerCore.getStoredAEPower());
            tag.setDouble("sme_powerCoreMax", powerCore.getMaxAEPower());
        } else if (te instanceof TileSingularityCraftingCore core) {
            tag.setString("sme_kind", "crafting_core");
            tag.setLong("sme_craftingCoreStorage", core.getStorageBytes());
            tag.setInteger("sme_craftingCoreCoProcessors", core.getConfiguredCoProcessors());
            tag.setInteger("sme_craftingCoreMonitors", core.getMonitorCount());
            tag.setBoolean("sme_craftingCoreBusy", core.isCoreBusy());
        } else if (te instanceof TileSingularityPatternTerminal terminal) {
            tag.setString("sme_kind", "pattern_terminal");
            tag.setInteger("sme_viewCells", countStacks(terminal.getViewCellStorage()));
        } else if (te instanceof TileSingularityCraftingTerminal terminal) {
            tag.setString("sme_kind", "crafting_terminal");
            tag.setInteger("sme_viewCells", countStacks(terminal.getViewCellStorage()));
        } else if (te instanceof TileSingularityTerminal terminal) {
            tag.setString("sme_kind", "terminal");
            tag.setInteger("sme_viewCells", countStacks(terminal.getViewCellStorage()));
        } else if (te instanceof TileSingularityProbe) {
            tag.setString("sme_kind", "probe");
        }
    }

    private static void writeProbeData(final TileEntity te, final NBTTagCompound tag) {
        if (!(te instanceof IGridProxyable proxyable)) return;
        final IGridNode node = proxyable.getProxy()
            .getNode();
        final int playerID = node instanceof GridNode gridNode ? gridNode.getPlayerID() : -1;
        final SingularityGrid sg = getGridForDevice(te, playerID);
        if (sg == null) return;

        tag.setInteger("sme_nodeCount", sg.getAdoptedNodeCount());
        final SingularityNetworkManager manager = SingularityNetworkManager.INSTANCE;
        tag.setInteger("sme_staleNodes", manager.countStaleNodes(sg));
        tag.setInteger("sme_lastPrunedStaleNodes", manager.getLastPrunedStaleNodeCount());
        final int[] storageBusCounts = countStorageBusContributions(sg);
        tag.setInteger("sme_storageBusLoaded", storageBusCounts[0]);
        tag.setInteger("sme_storageBusRetired", storageBusCounts[1]);
        tag.setString("sme_storageBusDetails", buildStorageBusDetails(sg));
        final IStorageGrid storage = sg.getCache(IStorageGrid.class);
        if (storage != null) {
            try {
                tag.setInteger(
                    "sme_itemTypes",
                    storage.getItemInventory()
                        .getStorageList()
                        .size());
            } catch (final RuntimeException ignored) {
                tag.setInteger("sme_itemTypes", -1);
            }
        }
        final ICraftingGrid crafting = sg.getCache(ICraftingGrid.class);
        tag.setInteger("sme_cpuCount", crafting == null ? 0 : countMachines(sg, TileSingularityCraftingCore.class));
        tag.setString("sme_deviceCounts", buildDeviceCounts(sg));
    }

    private static boolean isContributionLoaded(final TileEntity te, final IGridNode node, final SingularityGrid sg) {
        if (te instanceof TileSingularityDrive drive) return drive.isContributionLoaded();
        if (te instanceof TileSingularityStorageBus bus) return bus.isContributionLoaded();
        if (te instanceof TileSingularityInterface iface) return iface.isContributionLoaded();
        if (te instanceof TileSingularityCraftingCore core) return core.isContributionLoaded();
        return sg != null && node instanceof GridNode gridNode && sg.hasNode(gridNode);
    }

    private static SingularityGrid getGridForDevice(final TileEntity te, final int playerID) {
        if (playerID < 0) return null;
        if (te instanceof ISingularityNetworkDevice device) {
            final int networkID = device.getNetworkID();
            if (networkID == 0) return null;
            final int ownerID = device.getGridOwnerPlayerID() >= 0 ? device.getGridOwnerPlayerID() : playerID;
            return SingularityNetworkManager.INSTANCE.getGridForPlayer(ownerID, networkID);
        }
        return null;
    }

    private static String buildDeviceCounts(final SingularityGrid sg) {
        final StringJoiner joiner = new StringJoiner(", ");
        joiner.add("Drive " + countMachines(sg, TileSingularityDrive.class));
        joiner.add("StorageBus " + countMachines(sg, TileSingularityStorageBus.class));
        joiner.add("Import " + countMachines(sg, TileSingularityImportBus.class));
        joiner.add("Export " + countMachines(sg, TileSingularityExportBus.class));
        joiner.add("Interface " + countMachines(sg, TileSingularityInterface.class));
        joiner.add("Core " + countMachines(sg, TileSingularityCraftingCore.class));
        joiner.add("Power " + countMachines(sg, TileSingularityPowerCore.class));
        joiner.add("Terminal " + countMachines(sg, TileSingularityTerminal.class));
        return joiner.toString();
    }

    private static int countMachines(final SingularityGrid sg, final Class<?> machineClass) {
        try {
            return sg.getMachines(machineClass.asSubclass(IGridHost.class))
                .size();
        } catch (final RuntimeException ignored) {
            return 0;
        }
    }

    private static int[] countStorageBusContributions(final SingularityGrid sg) {
        final int[] counts = new int[2];
        if (sg == null) return counts;

        for (final GridNode node : sg.getAdoptedNodeSnapshot()) {
            try {
                if (node.getMachine() instanceof TileSingularityStorageBus bus) {
                    if (bus.isContributionLoaded()) {
                        counts[0]++;
                    } else {
                        counts[1]++;
                    }
                }
            } catch (final RuntimeException ignored) {}
        }
        return counts;
    }

    private static String buildStorageBusDetails(final SingularityGrid sg) {
        final StringJoiner details = new StringJoiner(NBT_SEPARATOR);
        if (sg == null) return "";

        int index = 0;
        for (final GridNode node : sg.getAdoptedNodeSnapshot()) {
            try {
                if (!(node.getMachine() instanceof TileSingularityStorageBus bus)) continue;

                final World world = bus.getWorldObj();
                final int dim = world == null || world.provider == null ? Integer.MIN_VALUE
                    : world.provider.dimensionId;
                final int hostChunkX = bus.xCoord >> 4;
                final int hostChunkZ = bus.zCoord >> 4;
                final ChunkDiagnostics host = chunkDiagnostics(world, hostChunkX, hostChunkZ);
                final TargetChunk target = targetChunk(bus);
                final ChunkDiagnostics targetDiag = target == null ? ChunkDiagnostics.unknown()
                    : chunkDiagnostics(world, target.chunkX, target.chunkZ);

                details.add(
                    "#" + (++index)
                        + " dim="
                        + dim
                        + " pos="
                        + bus.xCoord
                        + ","
                        + bus.yCoord
                        + ","
                        + bus.zCoord
                        + " loaded="
                        + bool(bus.isContributionLoaded())
                        + " host="
                        + chunkSummary(host)
                        + " target="
                        + chunkSummary(targetDiag)
                        + " targetTile="
                        + bool(bus.isTargetStillLoaded())
                        + " access="
                        + bool(bus.isHostChunkNetworkAccessible() && bus.isTargetChunkNetworkAccessible()));
            } catch (final RuntimeException ignored) {}
        }
        return details.toString();
    }

    private static TargetChunk targetChunk(final TileSingularityStorageBus bus) {
        final World world = bus.getWorldObj();
        if (world == null || !bus.isHostStillLoaded()) return null;
        final ForgeDirection facing = ForgeDirection
            .getOrientation(world.getBlockMetadata(bus.xCoord, bus.yCoord, bus.zCoord));
        final int targetX = bus.xCoord + facing.offsetX;
        final int targetZ = bus.zCoord + facing.offsetZ;
        return new TargetChunk(targetX >> 4, targetZ >> 4);
    }

    private static ChunkDiagnostics chunkDiagnostics(final World world, final int chunkX, final int chunkZ) {
        if (world == null || world.isRemote) return ChunkDiagnostics.unknown();
        return new ChunkDiagnostics(
            isChunkLoaded(world, chunkX, chunkZ),
            isChunkWatched(world, chunkX, chunkZ),
            isSpawnChunk(world, chunkX, chunkZ),
            countForgeTickets(world, chunkX, chunkZ),
            isQueuedForUnload(world, chunkX, chunkZ));
    }

    private static boolean isChunkLoaded(final World world, final int chunkX, final int chunkZ) {
        return world.getChunkProvider()
            .chunkExists(chunkX, chunkZ);
    }

    private static boolean isChunkWatched(final World world, final int chunkX, final int chunkZ) {
        return world instanceof WorldServer serverWorld && serverWorld.getPlayerManager()
            .func_152621_a(chunkX, chunkZ);
    }

    private static boolean isSpawnChunk(final World world, final int chunkX, final int chunkZ) {
        if (world.provider == null || !world.provider.canRespawnHere()) return false;
        if (!DimensionManager.shouldLoadSpawn(world.provider.dimensionId)) return false;
        final int centerX = (chunkX << 4) + 8;
        final int centerZ = (chunkZ << 4) + 8;
        return Math.abs(centerX - world.getSpawnPoint().posX) <= 128
            && Math.abs(centerZ - world.getSpawnPoint().posZ) <= 128;
    }

    private static int countForgeTickets(final World world, final int chunkX, final int chunkZ) {
        try {
            return ForgeChunkManager.getPersistentChunksFor(world)
                .get(new ChunkCoordIntPair(chunkX, chunkZ))
                .size();
        } catch (final RuntimeException ignored) {
            return -1;
        }
    }

    private static int isQueuedForUnload(final World world, final int chunkX, final int chunkZ) {
        if (!(world instanceof WorldServer serverWorld)) return -1;
        final Field field = getChunksToUnloadField();
        if (field == null) return -1;

        try {
            final Object raw = field.get(serverWorld.theChunkProviderServer);
            if (!(raw instanceof Set<?>set)) return -1;
            return set.contains(Long.valueOf(ChunkCoordIntPair.chunkXZ2Int(chunkX, chunkZ))) ? 1 : 0;
        } catch (final IllegalAccessException | RuntimeException ignored) {
            return -1;
        }
    }

    private static Field getChunksToUnloadField() {
        if (chunksToUnloadFieldResolved) return chunksToUnloadField;
        synchronized (WailaSingularityProbeProvider.class) {
            if (chunksToUnloadFieldResolved) return chunksToUnloadField;
            for (final String name : new String[] { "chunksToUnload", "field_73248_b" }) {
                try {
                    final Field field = ChunkProviderServer.class.getDeclaredField(name);
                    field.setAccessible(true);
                    chunksToUnloadField = field;
                    break;
                } catch (final NoSuchFieldException ignored) {}
            }
            chunksToUnloadFieldResolved = true;
            return chunksToUnloadField;
        }
    }

    private static String chunkSummary(final ChunkDiagnostics diagnostics) {
        return "loaded=" + bool(diagnostics.loaded)
            + ",watched="
            + bool(diagnostics.watched)
            + ",spawn="
            + bool(diagnostics.spawn)
            + ",tickets="
            + diagnostics.tickets
            + ",queued="
            + state(diagnostics.queued);
    }

    private static String bool(final boolean value) {
        return value ? "Y" : "N";
    }

    private static String state(final int value) {
        return value < 0 ? "?" : value == 0 ? "N" : "Y";
    }

    private static final class TargetChunk {

        private final int chunkX;
        private final int chunkZ;

        private TargetChunk(final int chunkX, final int chunkZ) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }
    }

    private static final class ChunkDiagnostics {

        private final boolean loaded;
        private final boolean watched;
        private final boolean spawn;
        private final int tickets;
        private final int queued;

        private ChunkDiagnostics(final boolean loaded, final boolean watched, final boolean spawn, final int tickets,
            final int queued) {
            this.loaded = loaded;
            this.watched = watched;
            this.spawn = spawn;
            this.tickets = tickets;
            this.queued = queued;
        }

        private static ChunkDiagnostics unknown() {
            return new ChunkDiagnostics(false, false, false, -1, -1);
        }
    }

    private static int countStacks(final IInventory inventory) {
        int count = 0;
        if (inventory == null) return 0;
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            if (inventory.getStackInSlot(i) != null) {
                count++;
            }
        }
        return count;
    }

    private static void writeSyncData(final TileEntity te, final NBTTagCompound tag) {
        final DeviceInfo info = SingularityPrototypeRegistry.getDeviceInfo(te);
        if (info == null) return;

        tag.setString("sme_syncPrototype", info.getName());
        final StringJoiner lines = new StringJoiner(NBT_SEPARATOR);
        for (final SyncLine line : info.getLines()) {
            lines.add(line.format());
        }
        tag.setString("sme_syncLines", lines.toString());

        final StringJoiner upgradeLines = new StringJoiner(NBT_SEPARATOR);
        for (final UpgradeMirrorState state : info.getUpgradeStates()) {
            upgradeLines.add("Upgrade " + state.format());
        }
        tag.setString("sme_syncUpgradeMirror", upgradeLines.toString());

        final Stats stats = SingularityPrototypeRegistry.getStats();
        tag.setInteger("sme_syncTotal", stats.getTotal());
        tag.setInteger("sme_syncAutoMirror", stats.get(SyncStatus.AUTO_MIRROR));
        tag.setInteger("sme_syncDelegate", stats.get(SyncStatus.DELEGATE));
        tag.setInteger("sme_syncAdapt", stats.get(SyncStatus.ADAPT_REQUIRED));
        tag.setInteger("sme_syncFeature", stats.get(SyncStatus.FEATURE_EXEMPT));
        tag.setInteger("sme_syncForbidden", stats.get(SyncStatus.FORBIDDEN));
        tag.setInteger("sme_syncUnsupported", stats.get(SyncStatus.NOT_SUPPORTED));
        tag.setInteger("sme_syncVerify", stats.get(SyncStatus.VERIFY));
        tag.setInteger("sme_syncMirrorSafe", stats.get(SyncStatus.MIRROR_SAFE));
        tag.setInteger("sme_syncCustomMirror", stats.get(SyncStatus.CUSTOM_MIRROR));
        tag.setInteger("sme_syncSingularityOnly", stats.get(SyncStatus.SINGULARITY_ONLY));
    }

    private static void addSplitLines(final List<String> tooltip, final String lines) {
        if (lines == null || lines.isEmpty()) return;
        for (final String line : lines.split(NBT_SEPARATOR)) {
            if (!line.isEmpty()) {
                tooltip.add(colorForSyncLine(line) + line + COLOR_RESET);
            }
        }
    }

    private static String colorForSyncLine(final String line) {
        if (line.contains(SyncStatus.NOT_SUPPORTED.name())) return COLOR_RED;
        if (line.contains(SyncStatus.FORBIDDEN.name())) return COLOR_RED;
        if (line.contains(SyncStatus.VERIFY.name())) return COLOR_YELLOW;
        if (line.contains(SyncStatus.ADAPT_REQUIRED.name())) return COLOR_YELLOW;
        if (line.contains(SyncStatus.FEATURE_EXEMPT.name())) return COLOR_PURPLE;
        if (line.contains(SyncStatus.AUTO_MIRROR.name())) return COLOR_GREEN;
        if (line.contains(SyncStatus.MIRROR_SAFE.name())) return COLOR_GREEN;
        if (line.contains(SyncStatus.DELEGATE.name())) return COLOR_AQUA;
        return COLOR_WHITE;
    }

    private static String installedUpgrades(final IUpgradeableHost host) {
        final StringJoiner installed = new StringJoiner(", ");
        for (final Upgrades upgrade : Upgrades.values()) {
            final int count = host.getInstalledUpgrades(upgrade);
            if (count > 0) {
                installed.add(upgrade + " x" + count);
            }
        }
        final String out = installed.toString();
        return out.isEmpty() ? tr("waila.singularityme.none") : out;
    }

    private static String formatStorage(final long storage) {
        return storage == Long.MAX_VALUE ? tr("waila.singularityme.infinity") : Long.toString(storage);
    }

    private static String formatDouble(final double value) {
        if (value > 1e15) return tr("waila.singularityme.infinity");
        return String.format("%.1f", value);
    }

    private static String formatNetworkIdentity(final NBTTagCompound tag) {
        final int networkID = tag.getInteger("sme_networkID");
        if (networkID == 0) {
            return COLOR_RED + tr("gui.singularityme.network_tab.default") + COLOR_RESET;
        }
        final String name = tag.hasKey("sme_networkKnown") ? tag.getString("sme_networkName") : "#" + networkID;
        return COLOR_AQUA + name + " (#" + networkID + ")" + COLOR_RESET;
    }

    private static String formatPlayerIdentity(final NBTTagCompound tag, final String keyPrefix) {
        final int playerID = tag.getInteger(keyPrefix + "ID");
        if (playerID < 0) return COLOR_RED + tr("waila.singularityme.unknown") + COLOR_RESET;
        final String name = tag.getString(keyPrefix + "Name");
        final String label = name == null || name.isEmpty() ? "#" + playerID : name;
        return COLOR_YELLOW + label + COLOR_GRAY + " (#" + playerID + ")" + COLOR_RESET;
    }

    private static String playerName(final int playerID) {
        if (playerID < 0) return "";
        try {
            final EntityPlayer player = AEApi.instance()
                .registries()
                .players()
                .findPlayer(playerID);
            if (player != null) return player.getCommandSenderName();
        } catch (final RuntimeException ignored) {}
        return "";
    }

    private static String tr(final String key) {
        return StatCollector.translateToLocal(key);
    }

    private static String trf(final String key, final Object... args) {
        return StatCollector.translateToLocalFormatted(key, args);
    }

    private static String yesNo(final boolean value) {
        return value ? COLOR_GREEN + tr("waila.singularityme.yes") + COLOR_RESET
            : COLOR_RED + tr("waila.singularityme.no") + COLOR_RESET;
    }

    private static String existsMissing(final boolean value) {
        return value ? COLOR_GREEN + tr("waila.singularityme.exists") + COLOR_RESET
            : COLOR_RED + tr("waila.singularityme.missing") + COLOR_RESET;
    }

    private static String translatedFacing(final ForgeDirection facing) {
        return switch (facing) {
            case NORTH -> tr("waila.singularityme.direction.north");
            case SOUTH -> tr("waila.singularityme.direction.south");
            case WEST -> tr("waila.singularityme.direction.west");
            case EAST -> tr("waila.singularityme.direction.east");
            case UP -> tr("waila.singularityme.direction.up");
            case DOWN -> tr("waila.singularityme.direction.down");
            default -> facing.name();
        };
    }
}

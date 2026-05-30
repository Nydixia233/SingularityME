package com.github.singularityme.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.storage.MapStorage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Persists the set of Singularity device locations per network key across server restarts.
 *
 * <p>
 * Saved to {@code world/data/singularityme_networks.dat} via Minecraft's {@link WorldSavedData}
 * mechanism. The data is intentionally minimal: only enough to pre-create each
 * {@link SingularityGrid} on server start so the grid exists before the first chunk loads.
 *
 * <p>
 * Runtime routing still uses {@code GridNode.getPlayerID()} + the TE's stored networkID —
 * this class is not in the hot path.
 */
public class SingularityNetworkData extends WorldSavedData {

    public static final String DATA_NAME = "singularityme_networks";
    private static final Logger LOG = LogManager.getLogger("SingularityME");

    /** NetworkKey → list of device records belonging to that network. */
    private final Map<NetworkKey, List<DeviceRecord>> deviceRecords = new HashMap<>();

    public SingularityNetworkData() {
        super(DATA_NAME);
    }

    /** Required no-arg constructor variant used by WorldSavedData deserialization. */
    public SingularityNetworkData(final String name) {
        super(name);
    }

    // ---- Static accessor ----

    /**
     * Loads (or creates) the singleton instance from the overworld's map storage.
     * Must only be called server-side.
     */
    public static SingularityNetworkData get(final World world) {
        final MapStorage storage = world.mapStorage;
        SingularityNetworkData data = (SingularityNetworkData) storage
            .loadData(SingularityNetworkData.class, DATA_NAME);
        if (data == null) {
            data = new SingularityNetworkData();
            storage.setData(DATA_NAME, data);
        }
        return data;
    }

    // ---- Mutation ----

    /**
     * Records that a device at the given coordinates belongs to the given network key.
     * Called from {@link SingularityNetworkManager#registerNode} on the invalidate path.
     */
    public void recordDevice(final NetworkKey key, final int x, final int y, final int z, final int dim) {
        final List<DeviceRecord> list = deviceRecords.computeIfAbsent(key, k -> new ArrayList<>());
        final DeviceRecord rec = new DeviceRecord(x, y, z, dim);
        if (!list.contains(rec)) {
            list.add(rec);
            markDirty();
        }
    }

    /**
     * Removes the device record when a device is permanently destroyed (not just chunk-unloaded).
     */
    public void removeDevice(final NetworkKey key, final int x, final int y, final int z, final int dim) {
        final List<DeviceRecord> list = deviceRecords.get(key);
        if (list == null) return;
        if (list.remove(new DeviceRecord(x, y, z, dim))) {
            if (list.isEmpty()) {
                deviceRecords.remove(key);
            }
            markDirty();
        }
    }

    /** Returns all NetworkKeys that have at least one recorded device. */
    public Iterable<NetworkKey> getKnownNetworkKeys() {
        return new ArrayList<>(deviceRecords.keySet());
    }

    // ---- NBT serialization ----

    @Override
    public void readFromNBT(final NBTTagCompound nbt) {
        deviceRecords.clear();
        final NBTTagList playerList = nbt.getTagList("players", 10 /* TAG_Compound */);
        for (int i = 0; i < playerList.tagCount(); i++) {
            final NBTTagCompound playerTag = playerList.getCompoundTagAt(i);
            final int playerID = playerTag.getInteger("pid");
            final int networkID = playerTag.hasKey("nid") ? playerTag.getInteger("nid") : 0;
            final NetworkKey key = new NetworkKey(playerID, networkID);
            final NBTTagList devList = playerTag.getTagList("devices", 10);
            final List<DeviceRecord> records = new ArrayList<>(devList.tagCount());
            for (int j = 0; j < devList.tagCount(); j++) {
                final NBTTagCompound devTag = devList.getCompoundTagAt(j);
                records.add(
                    new DeviceRecord(
                        devTag.getInteger("x"),
                        devTag.getInteger("y"),
                        devTag.getInteger("z"),
                        devTag.getInteger("dim")));
            }
            if (!records.isEmpty()) {
                deviceRecords.put(key, records);
            }
        }
        LOG.info(
            "[SingularityME] Loaded SingularityNetworkData: {} network(s) with recorded devices.",
            deviceRecords.size());
    }

    @Override
    public void writeToNBT(final NBTTagCompound nbt) {
        final NBTTagList playerList = new NBTTagList();
        for (final Map.Entry<NetworkKey, List<DeviceRecord>> entry : deviceRecords.entrySet()) {
            if (entry.getValue()
                .isEmpty()) continue;
            final NBTTagCompound playerTag = new NBTTagCompound();
            playerTag.setInteger("pid", entry.getKey().playerID);
            playerTag.setInteger("nid", entry.getKey().networkID);
            final NBTTagList devList = new NBTTagList();
            for (final DeviceRecord rec : entry.getValue()) {
                final NBTTagCompound devTag = new NBTTagCompound();
                devTag.setInteger("x", rec.x);
                devTag.setInteger("y", rec.y);
                devTag.setInteger("z", rec.z);
                devTag.setInteger("dim", rec.dim);
                devList.appendTag(devTag);
            }
            playerTag.setTag("devices", devList);
            playerList.appendTag(playerTag);
        }
        nbt.setTag("players", playerList);
    }

    // ---- Inner record type ----

    private static final class DeviceRecord {

        final int x, y, z, dim;

        DeviceRecord(final int x, final int y, final int z, final int dim) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.dim = dim;
        }

        @Override
        public boolean equals(final Object o) {
            if (!(o instanceof DeviceRecord other)) return false;
            return x == other.x && y == other.y && z == other.z && dim == other.dim;
        }

        @Override
        public int hashCode() {
            int result = x;
            result = 31 * result + y;
            result = 31 * result + z;
            result = 31 * result + dim;
            return result;
        }
    }
}

package com.github.singularityme.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/** 验证奇点网络元数据时间戳的创建、更新与存档兼容。 */
public class SingularityNetworkRegistryTimestampTest {

    /** 创建网络时必须写入创建时间和最后修改时间，并随 NBT 持久化。 */
    @Test
    public void persistsCreatedAndModifiedTimestamps() {
        final SingularityNetworkRegistry registry = new SingularityNetworkRegistry("test_registry");
        final int networkID = registry.createNetwork(42, "Alpha");
        final SingularityNetworkRegistry.NetworkMeta meta = registry.getNetwork(networkID);

        assertTrue(meta.createdAtMillis > 0L);
        assertTrue(meta.lastModifiedMillis >= meta.createdAtMillis);

        final NBTTagCompound saved = new NBTTagCompound();
        registry.writeToNBT(saved);

        final SingularityNetworkRegistry loaded = new SingularityNetworkRegistry("test_registry");
        loaded.readFromNBT(saved);
        final SingularityNetworkRegistry.NetworkMeta loadedMeta = loaded.getNetwork(networkID);

        assertEquals(meta.createdAtMillis, loadedMeta.createdAtMillis);
        assertEquals(meta.lastModifiedMillis, loadedMeta.lastModifiedMillis);
    }

    /** 重命名和设置网络属性属于用户可见修改，必须刷新最后修改时间。 */
    @Test
    public void updatesLastModifiedWhenMetadataChanges() {
        final SingularityNetworkRegistry registry = new SingularityNetworkRegistry("test_registry");
        final int networkID = registry.createNetwork(42, "Alpha");
        final SingularityNetworkRegistry.NetworkMeta meta = registry.getNetwork(networkID);
        final long createdAt = meta.createdAtMillis;
        final long firstModifiedAt = meta.lastModifiedMillis;

        registry.renameNetwork(networkID, 42, "Beta");

        assertEquals(createdAt, meta.createdAtMillis);
        assertTrue(meta.lastModifiedMillis >= firstModifiedAt);
    }

    /** 旧存档缺少时间戳时应保留未知哨兵值，避免 UI 显示虚假的当前时间。 */
    @Test
    public void keepsUnknownTimestampForLegacyNetworkNbt() {
        final NBTTagCompound saved = new NBTTagCompound();
        saved.setInteger("nextID", 2);

        final NBTTagCompound network = new NBTTagCompound();
        network.setInteger("id", 1);
        network.setInteger("owner", 42);
        network.setString("name", "Legacy");
        network.setInteger("color", SingularityNetworkRegistry.DEFAULT_COLOR);
        network.setInteger("security", SecurityLevel.PRIVATE.ordinal());

        final NBTTagList networks = new NBTTagList();
        networks.appendTag(network);
        saved.setTag("networks", networks);

        final SingularityNetworkRegistry loaded = new SingularityNetworkRegistry("test_registry");
        loaded.readFromNBT(saved);
        final SingularityNetworkRegistry.NetworkMeta meta = loaded.getNetwork(1);

        assertEquals(0L, meta.createdAtMillis);
        assertEquals(0L, meta.lastModifiedMillis);
    }
}

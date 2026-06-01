package com.github.singularityme.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import net.minecraft.nbt.NBTTagCompound;

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
}

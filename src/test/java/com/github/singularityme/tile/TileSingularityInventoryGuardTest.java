package com.github.singularityme.tile;

import static org.junit.Assert.assertNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;

import org.junit.Test;

import appeng.api.config.AccessRestriction;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStackType;
import appeng.me.storage.MEInventoryHandler;

/** 验证奇点存储贡献的 AE2 handler 包装不会通过继承入口泄露底层库存。 */
public class TileSingularityInventoryGuardTest {

    /** Drive handler 退役或区块不可访问时，getInternal 也必须服从贡献可用性检查。 */
    @Test
    public void driveWatcherDoesNotExposeInternalInventoryWhenUnavailable() throws Exception {
        final IMEInventoryHandler<IAEItemStack> delegate = inventoryHandlerProxy();
        final MEInventoryHandler<IAEItemStack> guarded = newDriveWatcher(delegate);

        assertNull(guarded.getInternal());
    }

    /** Storage Bus handler 目标不可访问时，getInternal 也必须服从外部存储访问检查。 */
    @Test
    public void storageBusHandlerDoesNotExposeInternalInventoryWhenUnavailable() throws Exception {
        final IMEInventoryHandler<IAEItemStack> delegate = inventoryHandlerProxy();
        final MEInventoryHandler<IAEItemStack> guarded = newStorageBusHandler(delegate);

        assertNull(guarded.getInternal());
    }

    @SuppressWarnings("unchecked")
    private static IMEInventoryHandler<IAEItemStack> inventoryHandlerProxy() {
        return (IMEInventoryHandler<IAEItemStack>) Proxy.newProxyInstance(
            TileSingularityInventoryGuardTest.class.getClassLoader(),
            new Class<?>[] { IMEInventoryHandler.class },
            (proxy, method, args) -> switch (method.getName()) {
                case "getAccess" -> AccessRestriction.READ_WRITE;
                case "isPrioritized", "canAccept", "validForPass", "getSticky", "isAutoCraftingInventory" -> false;
                case "getPriority", "getSlot" -> 0;
                default -> null;
            });
    }

    @SuppressWarnings("unchecked")
    private static MEInventoryHandler<IAEItemStack> newDriveWatcher(final IMEInventoryHandler<IAEItemStack> delegate)
        throws Exception {
        final TileSingularityDrive drive = allocate(TileSingularityDrive.class);
        setBooleanField(drive, "contributionRetired", true);
        final Class<?> cls = Class.forName(TileSingularityDrive.class.getName() + "$DriveWatcher");
        final Constructor<?> ctor = cls.getDeclaredConstructor(TileSingularityDrive.class, IMEInventory.class,
            IAEStackType.class);
        ctor.setAccessible(true);
        return (MEInventoryHandler<IAEItemStack>) ctor.newInstance(drive, delegate, null);
    }

    @SuppressWarnings("unchecked")
    private static MEInventoryHandler<IAEItemStack> newStorageBusHandler(
        final IMEInventoryHandler<IAEItemStack> delegate) throws Exception {
        final TileSingularityStorageBus storageBus = allocate(TileSingularityStorageBus.class);
        setBooleanField(storageBus, "contributionRetired", true);
        final Class<?> cls = Class.forName(TileSingularityStorageBus.class.getName() + "$GuardedStorageBusInventoryHandler");
        final Constructor<?> ctor = cls.getDeclaredConstructor(TileSingularityStorageBus.class, IMEInventory.class,
            IAEStackType.class);
        ctor.setAccessible(true);
        return (MEInventoryHandler<IAEItemStack>) ctor.newInstance(storageBus, delegate, null);
    }

    private static void setBooleanField(final Object target, final String fieldName, final boolean value)
        throws Exception {
        final Field field = target.getClass()
            .getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setBoolean(target, value);
    }

    private static <T> T allocate(final Class<T> cls) throws Exception {
        final Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        final sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        return cls.cast(unsafe.allocateInstance(cls));
    }
}

package com.github.singularityme.tile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;

/** 验证奇点终端对 AE2 monitor 的权限包装不会破坏库存查询委托。 */
public class TileSingularityTerminalTest {

    /** 包装后的 monitor 必须显式委托 getAvailableItems，否则 AE2 默认方法会互相递归并踢出客户端。 */
    @Test
    public void securedMonitorDelegatesAvailableItemsWithIterationId() throws Exception {
        final AtomicInteger calls = new AtomicInteger();
        final IItemList<IAEItemStack> list = itemListProxy();
        final IMEMonitor<IAEItemStack> delegate = monitorProxy(calls);
        final IMEMonitor<IAEItemStack> secured = newSecuredMonitor(delegate);

        final IItemList<IAEItemStack> result = secured.getAvailableItems(list, 42);

        assertSame(list, result);
        assertEquals(1, calls.get());
    }

    @SuppressWarnings("unchecked")
    private static IItemList<IAEItemStack> itemListProxy() {
        return (IItemList<IAEItemStack>) Proxy.newProxyInstance(
            TileSingularityTerminalTest.class.getClassLoader(),
            new Class<?>[] { IItemList.class },
            (proxy, method, args) -> switch (method.getName()) {
                case "iterator" -> java.util.Collections.emptyIterator();
                case "size" -> 0;
                case "isSorted", "hasWriteAccess" -> false;
                default -> null;
            });
    }

    @SuppressWarnings("unchecked")
    private static IMEMonitor<IAEItemStack> monitorProxy(final AtomicInteger calls) {
        return (IMEMonitor<IAEItemStack>) Proxy.newProxyInstance(
            TileSingularityTerminalTest.class.getClassLoader(),
            new Class<?>[] { IMEMonitor.class },
            (proxy, method, args) -> {
                if ("getAvailableItems".equals(method.getName()) && args != null && args.length == 2) {
                    calls.incrementAndGet();
                    return args[0];
                }
                return null;
            });
    }

    @SuppressWarnings("unchecked")
    private static IMEMonitor<IAEItemStack> newSecuredMonitor(final IMEMonitor<IAEItemStack> delegate)
        throws Exception {
        final TileSingularityTerminal terminal = allocateTerminalWithoutConstructor();
        final Class<?> cls = Class.forName(TileSingularityTerminal.class.getName() + "$SecuredTerminalMonitor");
        final Constructor<?> ctor = cls.getDeclaredConstructor(TileSingularityTerminal.class, IMEMonitor.class);
        ctor.setAccessible(true);
        return (IMEMonitor<IAEItemStack>) ctor.newInstance(terminal, delegate);
    }

    private static TileSingularityTerminal allocateTerminalWithoutConstructor() throws Exception {
        final Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        final sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        return (TileSingularityTerminal) unsafe.allocateInstance(TileSingularityTerminal.class);
    }
}

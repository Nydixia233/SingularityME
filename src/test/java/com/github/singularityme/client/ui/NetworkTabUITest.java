package com.github.singularityme.client.ui;

import static org.junit.Assert.assertEquals;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;

import org.junit.Test;

import com.github.singularityme.core.PermissionBits;
import com.github.singularityme.core.SecurityLevel;
import com.github.singularityme.network.packet.PacketNetworkTabData;
import com.github.singularityme.network.packet.PacketNetworkTabData.NetworkEntry;

/** 验证设备网络分配页的数据刷新语义。 */
public class NetworkTabUITest {

    /** 权限刷新包只更新网络列表，不应打断玩家当前选中的目标网络。 */
    @Test
    public void preserveDeviceContextKeepsCurrentSelection() throws Exception {
        final Object state = newTabState();
        setField(state, "deviceNetworkID", 7);
        setField(state, "selectedNetworkID", 8);

        final PacketNetworkTabData packet = new PacketNetworkTabData();
        packet.deviceNetworkID = PacketNetworkTabData.PRESERVE_DEVICE_CONTEXT;
        packet.defaultNetworkID = 0;
        packet.networks.add(NetworkEntry.unassigned(2));
        packet.networks.add(entry(7));
        packet.networks.add(entry(8));

        receive(state, packet);

        assertEquals(7, getField(state, "deviceNetworkID"));
        assertEquals(8, getField(state, "selectedNetworkID"));
    }

    private static Object newTabState() throws Exception {
        final Class<?> cls = Class.forName(NetworkTabUI.class.getName() + "$TabState");
        final Constructor<?> ctor = cls.getDeclaredConstructor(int.class, int.class, int.class, int.class);
        ctor.setAccessible(true);
        return ctor.newInstance(0, 0, 0, 0);
    }

    private static NetworkEntry entry(final int networkID) {
        return new NetworkEntry(
            networkID,
            1,
            false,
            "Network " + networkID,
            0x4A90E2,
            SecurityLevel.PRIVATE.ordinal(),
            PermissionBits.DEFAULT_MEMBER_BITS,
            false,
            false,
            false,
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            "#1",
            0L,
            0L);
    }

    private static void receive(final Object state, final PacketNetworkTabData packet) throws Exception {
        final Method method = state.getClass().getDeclaredMethod("receive", PacketNetworkTabData.class);
        method.setAccessible(true);
        method.invoke(state, packet);
    }

    private static Object getField(final Object target, final String name) throws Exception {
        final Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setField(final Object target, final String name, final Object value) throws Exception {
        final Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}

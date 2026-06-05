package com.github.singularityme.network.packet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/** 验证网络状态包的序列化行为。 */
public class PacketNetworkStatusTest {

    @Test
    public void roundTripsStatusPayload() {
        final PacketNetworkStatus original = new PacketNetworkStatus(
            42,
            128.5,
            256.0,
            Arrays.asList(
                new PacketNetworkStatus.DeviceInfo("TileSingularityDrive", 1, 2, 3, 0, true),
                new PacketNetworkStatus.DeviceInfo("TileSingularityPowerCore", -4, 5, 6, 7, false)));

        final ByteBuf buf = Unpooled.buffer();
        original.toBytes(buf);

        final PacketNetworkStatus decoded = new PacketNetworkStatus();
        decoded.fromBytes(buf);

        assertEquals(42, decoded.networkID);
        assertEquals(128.5, decoded.currentPower, 0.0001);
        assertEquals(256.0, decoded.maxPower, 0.0001);
        assertEquals(2, decoded.devices.size());

        final PacketNetworkStatus.DeviceInfo loaded = decoded.devices.get(0);
        assertEquals("TileSingularityDrive", loaded.type);
        assertEquals(1, loaded.x);
        assertEquals(2, loaded.y);
        assertEquals(3, loaded.z);
        assertEquals(0, loaded.dim);
        assertTrue(loaded.loaded);

        final PacketNetworkStatus.DeviceInfo phantom = decoded.devices.get(1);
        assertEquals("TileSingularityPowerCore", phantom.type);
        assertEquals(-4, phantom.x);
        assertEquals(5, phantom.y);
        assertEquals(6, phantom.z);
        assertEquals(7, phantom.dim);
        assertFalse(phantom.loaded);
    }
}

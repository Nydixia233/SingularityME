package com.github.singularityme.network.packet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/** 验证统一加入网络包的二进制读写契约。 */
public class PacketJoinNetworkTest {

    /** 坐标、维度、网络 ID、密码 hash 和加入后分配标记必须稳定往返。 */
    @Test
    public void roundTripsJoinPayload() {
        final PacketJoinNetwork packet = new PacketJoinNetwork(1, 2, 3, -1, 42, "abc123", true);

        final ByteBuf buf = Unpooled.buffer();
        packet.toBytes(buf);

        final PacketJoinNetwork decoded = new PacketJoinNetwork();
        decoded.fromBytes(buf);

        assertEquals(1, decoded.x);
        assertEquals(2, decoded.y);
        assertEquals(3, decoded.z);
        assertEquals(-1, decoded.dim);
        assertEquals(42, decoded.networkID);
        assertEquals("abc123", decoded.passwordHash);
        assertTrue(decoded.assignDeviceAfterJoin);
        assertEquals(0, buf.readableBytes());
    }

    /** 空密码 hash 和不分配设备标记也必须稳定往返。 */
    @Test
    public void roundTripsEmptyPasswordWithoutAssign() {
        final PacketJoinNetwork packet = new PacketJoinNetwork(4, 5, 6, 0, 9, null, false);

        final ByteBuf buf = Unpooled.buffer();
        packet.toBytes(buf);

        final PacketJoinNetwork decoded = new PacketJoinNetwork();
        decoded.fromBytes(buf);

        assertEquals("", decoded.passwordHash);
        assertFalse(decoded.assignDeviceAfterJoin);
        assertEquals(0, buf.readableBytes());
    }
}

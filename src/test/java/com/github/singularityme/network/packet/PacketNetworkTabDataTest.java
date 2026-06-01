package com.github.singularityme.network.packet;

import static org.junit.Assert.assertEquals;

import java.util.Collections;

import org.junit.Test;

import com.github.singularityme.network.packet.PacketNetworkTabData.NetworkEntry;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/** 验证网络标签页数据包的二进制读写契约。 */
public class PacketNetworkTabDataTest {

    /** 时间戳字段必须在 toBytes/fromBytes 中对称传输，避免客户端 ByteBuf 下溢或显示缺失。 */
    @Test
    public void roundTripsNetworkEntryTimestamps() {
        final PacketNetworkTabData packet = new PacketNetworkTabData();
        packet.deviceNetworkID = 9;
        packet.defaultNetworkID = 9;
        packet.networks.add(new NetworkEntry(
            9,
            42,
            true,
            "Alpha",
            0x123456,
            1,
            0,
            true,
            Collections.singletonList(43),
            Collections.singletonList(44),
            Collections.singletonList(45),
            "Owner",
            Collections.singletonList("Admin"),
            Collections.singletonList("Member"),
            Collections.singletonList("Blocked"),
            1_700_000_000_000L,
            1_700_000_123_456L));

        final ByteBuf buf = Unpooled.buffer();
        packet.toBytes(buf);

        final PacketNetworkTabData decoded = new PacketNetworkTabData();
        decoded.fromBytes(buf);

        final NetworkEntry entry = decoded.networks.get(0);
        assertEquals(1_700_000_000_000L, entry.createdAtMillis);
        assertEquals(1_700_000_123_456L, entry.lastModifiedMillis);
        assertEquals(0, buf.readableBytes());
    }

    /** 恶意或损坏的负长度字符串不应触发 NegativeArraySizeException。 */
    @Test
    public void clampsNegativeNameLengthToEmptyString() {
        final ByteBuf buf = Unpooled.buffer();
        buf.writeInt(9);
        buf.writeInt(9);
        buf.writeInt(1);
        buf.writeInt(9);
        buf.writeInt(42);
        buf.writeBoolean(true);
        buf.writeInt(0x123456);
        buf.writeInt(1);
        buf.writeInt(0);
        buf.writeBoolean(false);
        buf.writeShort(-1);
        writeEmptyListsAndNames(buf);
        buf.writeLong(0L);
        buf.writeLong(0L);

        final PacketNetworkTabData decoded = new PacketNetworkTabData();
        decoded.fromBytes(buf);

        assertEquals("", decoded.networks.get(0).name);
    }

    /** 负长度玩家名同样按空字符串处理，避免损坏包崩溃客户端。 */
    @Test
    public void clampsNegativeNestedStringLengthToEmptyString() {
        final ByteBuf buf = Unpooled.buffer();
        buf.writeInt(9);
        buf.writeInt(9);
        buf.writeInt(1);
        buf.writeInt(9);
        buf.writeInt(42);
        buf.writeBoolean(true);
        buf.writeInt(0x123456);
        buf.writeInt(1);
        buf.writeInt(0);
        buf.writeBoolean(false);
        writeString(buf, "Alpha");
        buf.writeInt(0);
        buf.writeInt(0);
        buf.writeInt(0);
        buf.writeShort(-1);
        buf.writeInt(0);
        buf.writeInt(0);
        buf.writeInt(0);
        buf.writeLong(0L);
        buf.writeLong(0L);

        final PacketNetworkTabData decoded = new PacketNetworkTabData();
        decoded.fromBytes(buf);

        assertEquals("", decoded.networks.get(0).ownerName);
    }

    private static void writeEmptyListsAndNames(final ByteBuf buf) {
        buf.writeInt(0);
        buf.writeInt(0);
        buf.writeInt(0);
        writeString(buf, "Owner");
        buf.writeInt(0);
        buf.writeInt(0);
        buf.writeInt(0);
    }

    private static void writeString(final ByteBuf buf, final String value) {
        final byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.writeShort(bytes.length);
        buf.writeBytes(bytes);
    }
}

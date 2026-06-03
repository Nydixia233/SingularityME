package com.github.singularityme.network.packet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import org.junit.Test;

import com.github.singularityme.core.PermissionBits;
import com.github.singularityme.network.packet.PacketNetworkTabData.NetworkEntry;

import appeng.api.config.SecurityPermissions;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/** 验证网络标签页数据包的二进制读写契约。 */
public class PacketNetworkTabDataTest {

    /** 新 DTO 字段必须在 toBytes/fromBytes 中对称传输。 */
    @Test
    public void roundTripsPermissionEntryPayload() {
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
            PermissionBits.DEFAULT_MEMBER_BITS,
            true,
            true,
            true,
            Collections.singletonList(43),
            Collections.singletonList("Builder"),
            Collections.singletonList(PermissionBits.toBits(java.util.EnumSet.of(SecurityPermissions.BUILD))),
            "Owner",
            1_700_000_000_000L,
            1_700_000_123_456L));

        final ByteBuf buf = Unpooled.buffer();
        packet.toBytes(buf);

        final PacketNetworkTabData decoded = new PacketNetworkTabData();
        decoded.fromBytes(buf);

        final NetworkEntry entry = decoded.networks.get(0);
        assertEquals(PermissionBits.DEFAULT_MEMBER_BITS, entry.myPermissionBits);
        assertTrue(entry.canManagePermissions);
        assertEquals(Collections.singletonList(43), entry.authorizedPlayerIDs);
        assertEquals(Collections.singletonList("Builder"), entry.authorizedPlayerNames);
        assertEquals(1_700_000_000_000L, entry.createdAtMillis);
        assertEquals(1_700_000_123_456L, entry.lastModifiedMillis);
        assertEquals(0, buf.readableBytes());
    }

    /** 恶意或损坏的负长度字符串不应触发 NegativeArraySizeException。 */
    @Test
    public void clampsNegativeNameLengthToEmptyString() {
        final ByteBuf buf = Unpooled.buffer();
        writeEntryPrefix(buf);
        buf.writeShort(-1);
        writeEmptyAuthorizationLists(buf);
        writeString(buf, "Owner");
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
        writeEntryPrefix(buf);
        writeString(buf, "Alpha");
        writeEmptyAuthorizationLists(buf);
        buf.writeShort(-1);
        buf.writeLong(0L);
        buf.writeLong(0L);

        final PacketNetworkTabData decoded = new PacketNetworkTabData();
        decoded.fromBytes(buf);

        assertEquals("", decoded.networks.get(0).ownerName);
    }

    private static void writeEntryPrefix(final ByteBuf buf) {
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
        buf.writeBoolean(false);
        buf.writeBoolean(false);
    }

    private static void writeEmptyAuthorizationLists(final ByteBuf buf) {
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

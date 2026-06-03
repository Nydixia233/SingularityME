package com.github.singularityme.network.packet;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.github.singularityme.core.PermissionBits;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/** 验证按玩家名授权网络包的二进制契约。 */
public class PacketGrantPermissionByNameTest {

    /** networkID、玩家名与默认权限位必须稳定往返。 */
    @Test
    public void roundTripsPlayerNameAndPermissionBits() {
        final PacketGrantPermissionByName packet =
            new PacketGrantPermissionByName(7, "Builder", PermissionBits.DEFAULT_MEMBER_BITS);

        final ByteBuf buf = Unpooled.buffer();
        packet.toBytes(buf);

        final PacketGrantPermissionByName decoded = new PacketGrantPermissionByName();
        decoded.fromBytes(buf);

        assertEquals(7, decoded.networkID);
        assertEquals("Builder", decoded.playerName);
        assertEquals(PermissionBits.DEFAULT_MEMBER_BITS, decoded.permissionBits);
        assertEquals(0, buf.readableBytes());
    }
}

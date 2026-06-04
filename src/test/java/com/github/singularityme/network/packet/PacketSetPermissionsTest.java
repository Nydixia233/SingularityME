package com.github.singularityme.network.packet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.github.singularityme.core.PermissionBits;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/** 验证权限写入网络包的二进制契约。 */
public class PacketSetPermissionsTest {

    /** networkID、目标玩家 ID 与权限位必须稳定往返。 */
    @Test
    public void roundTripsPermissionBits() {
        final PacketSetPermissions packet = new PacketSetPermissions(7, 42, PermissionBits.DEFAULT_MEMBER_BITS);

        final ByteBuf buf = Unpooled.buffer();
        packet.toBytes(buf);

        final PacketSetPermissions decoded = new PacketSetPermissions();
        decoded.fromBytes(buf);

        assertEquals(7, decoded.networkID);
        assertEquals(42, decoded.targetPlayerID);
        assertEquals(PermissionBits.DEFAULT_MEMBER_BITS, decoded.permissionBits);
        assertEquals(0, buf.readableBytes());
    }

    /** 权限写入成功后应刷新被授权玩家；失败或目标就是操作者时不重复推送。 */
    @Test
    public void refreshesTargetOnlyAfterSuccessfulPermissionChange() {
        assertTrue(NetworkTabPacketHelper.shouldSendPermissionRefresh(true, 1, 2));
        assertFalse(NetworkTabPacketHelper.shouldSendPermissionRefresh(false, 1, 2));
        assertFalse(NetworkTabPacketHelper.shouldSendPermissionRefresh(true, 1, 1));
        assertFalse(NetworkTabPacketHelper.shouldSendPermissionRefresh(true, 1, -1));
    }
}

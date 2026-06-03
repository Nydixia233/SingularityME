package com.github.singularityme.network.packet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/** 验证网络操作结果包的二进制读写契约。 */
public class PacketNetworkActionResultTest {

    /** 动作类型、结果码、成功标记和消息 key 必须稳定往返。 */
    @Test
    public void roundTripsActionResultPayload() {
        final PacketNetworkActionResult packet = new PacketNetworkActionResult(
            NetworkActionType.ASSIGN_DEVICE,
            NetworkActionResult.DEVICE_ASSIGNED,
            42);

        final ByteBuf buf = Unpooled.buffer();
        packet.toBytes(buf);

        final PacketNetworkActionResult decoded = new PacketNetworkActionResult();
        decoded.fromBytes(buf);

        assertEquals(NetworkActionType.ASSIGN_DEVICE, decoded.actionType);
        assertEquals(NetworkActionResult.DEVICE_ASSIGNED, decoded.result);
        assertTrue(decoded.result.success);
        assertEquals(42, decoded.networkID);
        assertEquals(NetworkActionResult.DEVICE_ASSIGNED.translationKey, decoded.messageKey);
        assertEquals(0, buf.readableBytes());
    }

    /** 失败结果也必须保留明确消息 key，供 UI 内联提示使用。 */
    @Test
    public void roundTripsFailureResultPayload() {
        final PacketNetworkActionResult packet = new PacketNetworkActionResult(
            NetworkActionType.JOIN,
            NetworkActionResult.BAD_PASSWORD,
            7);

        final ByteBuf buf = Unpooled.buffer();
        packet.toBytes(buf);

        final PacketNetworkActionResult decoded = new PacketNetworkActionResult();
        decoded.fromBytes(buf);

        assertEquals(NetworkActionResult.BAD_PASSWORD, decoded.result);
        assertFalse(decoded.result.success);
        assertEquals(NetworkActionResult.BAD_PASSWORD.translationKey, decoded.messageKey);
        assertEquals(0, buf.readableBytes());
    }
}

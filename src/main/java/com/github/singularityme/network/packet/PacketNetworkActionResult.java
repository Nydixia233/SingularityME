package com.github.singularityme.network.packet;

import java.nio.charset.StandardCharsets;

import net.minecraft.client.Minecraft;

import com.github.singularityme.client.ui.NetworkTabUI;
import com.github.singularityme.client.ui.NetworkTerminalUI;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/** 服务端回传给客户端的网络操作结果，用于 GUI 内联提示。 */
public class PacketNetworkActionResult implements IMessage {

    public NetworkActionType actionType = NetworkActionType.JOIN;
    public NetworkActionResult result = NetworkActionResult.SUCCESS;
    public int networkID;
    public String messageKey = NetworkActionResult.SUCCESS.translationKey;

    /** Forge 反序列化构造器。 */
    public PacketNetworkActionResult() {}

    /** 创建网络操作结果包。 */
    public PacketNetworkActionResult(final NetworkActionType actionType, final NetworkActionResult result,
        final int networkID) {
        this.actionType = actionType == null ? NetworkActionType.JOIN : actionType;
        this.result = result == null ? NetworkActionResult.NETWORK_NOT_FOUND : result;
        this.networkID = networkID;
        this.messageKey = this.result.translationKey;
    }

    @Override
    public void fromBytes(final ByteBuf buf) {
        this.actionType = NetworkActionType.fromOrdinal(buf.readInt());
        this.result = NetworkActionResult.fromOrdinal(buf.readInt());
        this.networkID = buf.readInt();
        this.messageKey = readString(buf);
    }

    @Override
    public void toBytes(final ByteBuf buf) {
        buf.writeInt(this.actionType.ordinal());
        buf.writeInt(this.result.ordinal());
        buf.writeInt(this.networkID);
        writeString(buf, this.messageKey);
    }

    private static String readString(final ByteBuf buf) {
        final int len = Math.max(0, buf.readShort());
        final byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeString(final ByteBuf buf, final String value) {
        final byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        buf.writeShort(bytes.length);
        buf.writeBytes(bytes);
    }

    /** 客户端处理操作结果，并路由给当前打开的网络 GUI。 */
    public static final class Handler implements IMessageHandler<PacketNetworkActionResult, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(final PacketNetworkActionResult message, final MessageContext ctx) {
            Minecraft.getMinecraft()
                .func_152344_a(() -> {
                    if (NetworkTabUI.receiveActionResult(message)) {
                        return;
                    }
                    NetworkTerminalUI.receiveActionResult(message);
                });
            return null;
        }
    }
}

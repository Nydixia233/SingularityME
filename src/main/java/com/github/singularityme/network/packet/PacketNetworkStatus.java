package com.github.singularityme.network.packet;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.Minecraft;

import com.github.singularityme.client.ui.NetworkTerminalUI;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/** 服务端回发给网络终端的设备状态快照。 */
public class PacketNetworkStatus implements IMessage {

    public int networkID;
    public double currentPower;
    public double maxPower;
    public List<DeviceInfo> devices = new ArrayList<>();

    public PacketNetworkStatus() {}

    public PacketNetworkStatus(final int networkID, final double currentPower, final double maxPower,
        final List<DeviceInfo> devices) {
        this.networkID = networkID;
        this.currentPower = currentPower;
        this.maxPower = maxPower;
        this.devices = new ArrayList<>(devices);
    }

    @Override
    public void fromBytes(final ByteBuf buf) {
        this.networkID = buf.readInt();
        this.currentPower = buf.readDouble();
        this.maxPower = buf.readDouble();
        final int count = buf.readInt();
        this.devices = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            this.devices.add(
                new DeviceInfo(
                    readString(buf),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readBoolean()));
        }
    }

    @Override
    public void toBytes(final ByteBuf buf) {
        buf.writeInt(this.networkID);
        buf.writeDouble(this.currentPower);
        buf.writeDouble(this.maxPower);
        buf.writeInt(this.devices.size());
        for (final DeviceInfo info : this.devices) {
            writeString(buf, info.type);
            buf.writeInt(info.x);
            buf.writeInt(info.y);
            buf.writeInt(info.z);
            buf.writeInt(info.dim);
            buf.writeBoolean(info.loaded);
        }
    }

    private static String readString(final ByteBuf buf) {
        final int len = buf.readUnsignedShort();
        final byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeString(final ByteBuf buf, final String value) {
        final byte[] bytes = (value != null ? value : "").getBytes(StandardCharsets.UTF_8);
        buf.writeShort(bytes.length);
        buf.writeBytes(bytes);
    }

    /** 单个设备的只读状态行。 */
    public static final class DeviceInfo {

        public final String type;
        public final int x;
        public final int y;
        public final int z;
        public final int dim;
        public final boolean loaded;

        public DeviceInfo(final String type, final int x, final int y, final int z, final int dim,
            final boolean loaded) {
            this.type = type != null ? type : "";
            this.x = x;
            this.y = y;
            this.z = z;
            this.dim = dim;
            this.loaded = loaded;
        }
    }

    /** 客户端处理状态快照，并交给当前打开的网络终端。 */
    public static final class Handler implements IMessageHandler<PacketNetworkStatus, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(final PacketNetworkStatus message, final MessageContext ctx) {
            Minecraft.getMinecraft()
                .func_152344_a(() -> NetworkTerminalUI.receiveNetworkStatus(message));
            return null;
        }
    }
}

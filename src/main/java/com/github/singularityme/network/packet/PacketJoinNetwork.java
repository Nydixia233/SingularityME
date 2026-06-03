package com.github.singularityme.network.packet;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.github.singularityme.core.SingularityNetworkRegistry;
import com.github.singularityme.core.SingularityNetworkRegistry.JoinNetworkResult;
import com.github.singularityme.network.SingularityChannel;
import com.github.singularityme.tile.ISingularityNetworkDevice;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** 客户端请求加入公开或加密奇点网络，可选择加入成功后立即分配当前设备。 */
public class PacketJoinNetwork implements IMessage {

    public int x;
    public int y;
    public int z;
    public int dim;
    public int networkID;
    public String passwordHash = "";
    public boolean assignDeviceAfterJoin;

    /** Forge 反序列化构造器。 */
    public PacketJoinNetwork() {}

    /** 创建加入网络请求包。 */
    public PacketJoinNetwork(final int x, final int y, final int z, final int dim, final int networkID,
        final String passwordHash, final boolean assignDeviceAfterJoin) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.dim = dim;
        this.networkID = networkID;
        this.passwordHash = passwordHash == null ? "" : passwordHash;
        this.assignDeviceAfterJoin = assignDeviceAfterJoin;
    }

    @Override
    public void fromBytes(final ByteBuf buf) {
        this.x = buf.readInt();
        this.y = buf.readInt();
        this.z = buf.readInt();
        this.dim = buf.readInt();
        this.networkID = buf.readInt();
        this.passwordHash = PacketSetNetworkSettings.readString(buf);
        this.assignDeviceAfterJoin = buf.readBoolean();
    }

    @Override
    public void toBytes(final ByteBuf buf) {
        buf.writeInt(this.x);
        buf.writeInt(this.y);
        buf.writeInt(this.z);
        buf.writeInt(this.dim);
        buf.writeInt(this.networkID);
        PacketSetNetworkSettings.writeString(buf, this.passwordHash);
        buf.writeBoolean(this.assignDeviceAfterJoin);
    }

    /** 服务端处理加入网络请求，并回传操作结果与最新网络列表。 */
    public static final class Handler implements IMessageHandler<PacketJoinNetwork, IMessage> {

        @Override
        public IMessage onMessage(final PacketJoinNetwork msg, final MessageContext ctx) {
            handle(msg, ctx.getServerHandler().playerEntity);
            return null;
        }

        static void handle(final PacketJoinNetwork msg, final EntityPlayerMP player) {
            final int playerID = NetworkTabPacketHelper.getPlayerID(player);
            if (playerID < 0) return;

            final SingularityNetworkRegistry registry = NetworkTabPacketHelper.getRegistry(player);
            final JoinNetworkResult joinResult = registry.joinNetwork(msg.networkID, playerID, msg.passwordHash);
            NetworkActionResult result = toActionResult(joinResult);
            int deviceNetworkID = 0;

            final World world = NetworkTabPacketHelper.getLoadedWorld(msg.dim);
            final TileEntity te = world == null ? null
                : NetworkTabPacketHelper.getTileEntityIfLoaded(world, msg.x, msg.y, msg.z);
            deviceNetworkID = NetworkTabPacketHelper.getDeviceNetworkID(te);

            if (joinResult.isSuccess() && msg.assignDeviceAfterJoin) {
                if (te instanceof ISingularityNetworkDevice device) {
                    device.setNetworkID(msg.networkID);
                    deviceNetworkID = device.getNetworkID();
                    result = NetworkActionResult.DEVICE_ASSIGNED;
                } else {
                    result = NetworkActionResult.DEVICE_UNAVAILABLE;
                }
            }

            SingularityChannel.CHANNEL.sendTo(
                new PacketNetworkActionResult(NetworkActionType.JOIN, result, msg.networkID),
                player);
            SingularityChannel.CHANNEL.sendTo(new PacketNetworkTabData(registry, playerID, deviceNetworkID), player);
        }

        private static NetworkActionResult toActionResult(final JoinNetworkResult result) {
            return switch (result) {
                case JOINED -> NetworkActionResult.JOINED;
                case ALREADY_MEMBER -> NetworkActionResult.ALREADY_MEMBER;
                case NETWORK_NOT_FOUND -> NetworkActionResult.NETWORK_NOT_FOUND;
                case PRIVATE_NETWORK -> NetworkActionResult.PRIVATE_NETWORK;
                case PASSWORD_REQUIRED -> NetworkActionResult.PASSWORD_REQUIRED;
                case BAD_PASSWORD -> NetworkActionResult.BAD_PASSWORD;
                case BLOCKED -> NetworkActionResult.BLOCKED;
            };
        }
    }
}

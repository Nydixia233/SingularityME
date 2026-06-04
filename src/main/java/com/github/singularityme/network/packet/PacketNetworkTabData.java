package com.github.singularityme.network.packet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

import com.github.singularityme.client.ui.NetworkTabUI;
import com.github.singularityme.client.ui.NetworkTerminalUI;
import com.github.singularityme.core.PermissionBits;
import com.github.singularityme.core.SingularityNetworkRegistry;
import com.github.singularityme.core.SingularityNetworkRegistry.NetworkMeta;

import appeng.api.AEApi;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/** 服务端发给客户端的可见网络列表与调用者权限快照。 */
public class PacketNetworkTabData implements IMessage {

    /** 未分配设备的哨兵 networkID。 */
    public static final int DEFAULT_NETWORK_ID = 0;
    /** 仅刷新网络列表时保留客户端现有设备上下文。 */
    public static final int PRESERVE_DEVICE_CONTEXT = -1;

    public int deviceNetworkID;
    public int defaultNetworkID;
    public List<NetworkEntry> networks = new ArrayList<>();

    public PacketNetworkTabData() {}

    /**
     * 构造发给指定玩家的网络列表；授权表只在玩家可管理时下发，避免信息泄漏。
     */
    public PacketNetworkTabData(final SingularityNetworkRegistry registry, final int playerID,
        final int deviceNetworkID) {
        this.deviceNetworkID = deviceNetworkID;
        this.defaultNetworkID = registry.resolveAccessibleDefaultNetworkID(playerID);

        this.networks.add(NetworkEntry.unassigned(playerID));

        for (final int networkID : registry.getVisibleNetworksForPlayer(playerID)) {
            final NetworkMeta meta = registry.getNetwork(networkID);
            if (meta == null) continue;
            final boolean canManage = meta.canManagePermissions(playerID);
            final List<Integer> authorizedIDs = canManage ? meta.getAuthorizedPlayers() : Collections.emptyList();
            this.networks.add(
                new NetworkEntry(
                    networkID,
                    meta.ownerPlayerID,
                    meta.ownerPlayerID == playerID,
                    meta.name,
                    meta.color,
                    meta.security.ordinal(),
                    PermissionBits.toBits(meta.getPermissions(playerID)),
                    canManage,
                    canManage,
                    meta.ownerPlayerID == playerID,
                    authorizedIDs,
                    resolvePlayerNames(authorizedIDs),
                    resolvePermissionBits(registry, networkID, authorizedIDs),
                    resolvePlayerName(meta.ownerPlayerID),
                    meta.createdAtMillis,
                    meta.lastModifiedMillis));
        }
    }

    /** 把 AE2 playerID 解析为玩家名；离线时返回 "#id" 作 fallback，不阻塞主线程。 */
    private static String resolvePlayerName(final int playerID) {
        if (playerID < 0) return "#" + playerID;
        try {
            final EntityPlayer player = AEApi.instance()
                .registries()
                .players()
                .findPlayer(playerID);
            return player != null ? player.getCommandSenderName() : "#" + playerID;
        } catch (final RuntimeException | LinkageError e) {
            return "#" + playerID;
        }
    }

    private static List<String> resolvePlayerNames(final List<Integer> ids) {
        if (ids == null || ids.isEmpty()) return Collections.emptyList();
        final List<String> names = new ArrayList<>(ids.size());
        for (final int id : ids) {
            names.add(resolvePlayerName(id));
        }
        return names;
    }

    private static List<Integer> resolvePermissionBits(final SingularityNetworkRegistry registry, final int networkID,
        final List<Integer> ids) {
        if (ids == null || ids.isEmpty()) return Collections.emptyList();
        final List<Integer> bits = new ArrayList<>(ids.size());
        for (final int id : ids) {
            bits.add(PermissionBits.toBits(registry.getPlayerPermissions(networkID, id)));
        }
        return bits;
    }

    @Override
    public void fromBytes(final ByteBuf buf) {
        this.deviceNetworkID = buf.readInt();
        this.defaultNetworkID = buf.readInt();
        final int count = buf.readInt();
        this.networks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            final int networkID = buf.readInt();
            final int ownerPlayerID = buf.readInt();
            final boolean isOwner = buf.readBoolean();
            final int color = buf.readInt();
            final int securityOrdinal = buf.readInt();
            final int myPermissionBits = buf.readInt();
            final boolean canManagePermissions = buf.readBoolean();
            final boolean canEditSettings = buf.readBoolean();
            final boolean canDeleteNetwork = buf.readBoolean();
            final String name = readString(buf);
            final List<Integer> authorizedPlayerIDs = readIntList(buf);
            final List<String> authorizedPlayerNames = readStringList(buf);
            final List<Integer> authorizedPlayerPermBits = readIntList(buf);
            final String ownerName = readString(buf);
            final long createdAtMillis = buf.readLong();
            final long lastModifiedMillis = buf.readLong();
            this.networks.add(
                new NetworkEntry(
                    networkID,
                    ownerPlayerID,
                    isOwner,
                    name,
                    color,
                    securityOrdinal,
                    myPermissionBits,
                    canManagePermissions,
                    canEditSettings,
                    canDeleteNetwork,
                    authorizedPlayerIDs,
                    authorizedPlayerNames,
                    authorizedPlayerPermBits,
                    ownerName,
                    createdAtMillis,
                    lastModifiedMillis));
        }
    }

    @Override
    public void toBytes(final ByteBuf buf) {
        buf.writeInt(this.deviceNetworkID);
        buf.writeInt(this.defaultNetworkID);
        buf.writeInt(this.networks.size());
        for (final NetworkEntry entry : this.networks) {
            buf.writeInt(entry.networkID);
            buf.writeInt(entry.ownerPlayerID);
            buf.writeBoolean(entry.isOwner);
            buf.writeInt(entry.color);
            buf.writeInt(entry.securityOrdinal);
            buf.writeInt(entry.myPermissionBits);
            buf.writeBoolean(entry.canManagePermissions);
            buf.writeBoolean(entry.canEditSettings);
            buf.writeBoolean(entry.canDeleteNetwork);
            writeString(buf, entry.name);
            writeIntList(buf, entry.authorizedPlayerIDs);
            writeStringList(buf, entry.authorizedPlayerNames);
            writeIntList(buf, entry.authorizedPlayerPermBits);
            writeString(buf, entry.ownerName);
            buf.writeLong(entry.createdAtMillis);
            buf.writeLong(entry.lastModifiedMillis);
        }
    }

    private static List<Integer> readIntList(final ByteBuf buf) {
        final int count = buf.readInt();
        final List<Integer> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            out.add(buf.readInt());
        }
        return out;
    }

    private static void writeIntList(final ByteBuf buf, final List<Integer> values) {
        buf.writeInt(values.size());
        for (final int value : values) {
            buf.writeInt(value);
        }
    }

    private static String readString(final ByteBuf buf) {
        final int len = safeLength(buf.readShort());
        final byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static int safeLength(final int len) {
        return Math.max(0, len);
    }

    private static void writeString(final ByteBuf buf, final String value) {
        final byte[] bytes = (value != null ? value : "").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.writeShort(bytes.length);
        buf.writeBytes(bytes);
    }

    private static List<String> readStringList(final ByteBuf buf) {
        final int count = buf.readInt();
        final List<String> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            out.add(readString(buf));
        }
        return out;
    }

    private static void writeStringList(final ByteBuf buf, final List<String> values) {
        buf.writeInt(values.size());
        for (final String value : values) {
            writeString(buf, value);
        }
    }

    /** 单个网络条目的不可变 GUI 展示快照。 */
    public static final class NetworkEntry {

        public final int networkID;
        public final int ownerPlayerID;
        public final boolean isOwner;
        public final String name;
        public final int color;
        public final int securityOrdinal;
        public final int myPermissionBits;
        public final boolean canManagePermissions;
        public final boolean canEditSettings;
        public final boolean canDeleteNetwork;
        public final List<Integer> authorizedPlayerIDs;
        public final List<String> authorizedPlayerNames;
        public final List<Integer> authorizedPlayerPermBits;
        /** 网络 owner 的玩家名；离线时为 "#id"。 */
        public final String ownerName;
        /** 网络创建时间戳（毫秒）。0 表示旧数据或未分配网络无时间戳。 */
        public final long createdAtMillis;
        /** 网络最后修改时间戳（毫秒）。0 表示旧数据或未分配网络无时间戳。 */
        public final long lastModifiedMillis;

        /** 构造未分配哨兵条目。 */
        public static NetworkEntry unassigned(final int playerID) {
            return new NetworkEntry(
                DEFAULT_NETWORK_ID,
                playerID,
                true,
                "Unassigned",
                0x777777,
                1,
                0,
                false,
                false,
                false,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                "#" + playerID,
                0L,
                0L);
        }

        public NetworkEntry(final int networkID, final int ownerPlayerID, final boolean isOwner, final String name,
            final int color, final int securityOrdinal, final int myPermissionBits, final boolean canManagePermissions,
            final boolean canEditSettings, final boolean canDeleteNetwork) {
            this(
                networkID,
                ownerPlayerID,
                isOwner,
                name,
                color,
                securityOrdinal,
                myPermissionBits,
                canManagePermissions,
                canEditSettings,
                canDeleteNetwork,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                "#" + ownerPlayerID,
                0L,
                0L);
        }

        public NetworkEntry(final int networkID, final int ownerPlayerID, final boolean isOwner, final String name,
            final int color, final int securityOrdinal, final int myPermissionBits, final boolean canManagePermissions,
            final boolean canEditSettings, final boolean canDeleteNetwork, final List<Integer> authorizedPlayerIDs,
            final List<String> authorizedPlayerNames, final List<Integer> authorizedPlayerPermBits,
            final String ownerName, final long createdAtMillis, final long lastModifiedMillis) {
            this.networkID = networkID;
            this.ownerPlayerID = ownerPlayerID;
            this.isOwner = isOwner;
            this.name = name;
            this.color = color & 0xFFFFFF;
            this.securityOrdinal = securityOrdinal;
            this.myPermissionBits = myPermissionBits;
            this.canManagePermissions = canManagePermissions;
            this.canEditSettings = canEditSettings;
            this.canDeleteNetwork = canDeleteNetwork;
            this.authorizedPlayerIDs = new ArrayList<>(authorizedPlayerIDs);
            this.authorizedPlayerNames = new ArrayList<>(authorizedPlayerNames);
            this.authorizedPlayerPermBits = new ArrayList<>(authorizedPlayerPermBits);
            this.ownerName = ownerName != null ? ownerName : "#" + ownerPlayerID;
            this.createdAtMillis = createdAtMillis;
            this.lastModifiedMillis = Math.max(createdAtMillis, lastModifiedMillis);
        }
    }

    /** 客户端处理器：把数据路由到当前打开的网络 UI。 */
    public static final class Handler implements IMessageHandler<PacketNetworkTabData, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(final PacketNetworkTabData message, final MessageContext ctx) {
            Minecraft.getMinecraft()
                .func_152344_a(() -> {
                    if (NetworkTabUI.receiveNetworkData(message)) {
                        return;
                    }
                    NetworkTerminalUI.receiveNetworkData(message);
                });
            return null;
        }
    }
}

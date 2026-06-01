package com.github.singularityme.network.packet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

import com.github.singularityme.client.ui.NetworkTabUI;
import com.github.singularityme.client.ui.NetworkTerminalUI;
import com.github.singularityme.core.SingularityNetworkRegistry;
import com.github.singularityme.core.SingularityNetworkRegistry.NetworkMeta;

import appeng.api.AEApi;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * Server → Client packet that delivers the network list for the Network Tab GUI.
 *
 * <p>
 * Payload:
 * <ul>
 * <li>int — current networkID of the device being configured</li>
 * <li>int — number of network entries (N)</li>
 * <li>N × { int networkID, int ownerPlayerID, boolean isOwner, String name }</li>
 * </ul>
 *
 * <p>
 * The unassigned sentinel (id=0) is always prepended as the first entry.
 */
public class PacketNetworkTabData implements IMessage {

    /** Sentinel networkID for unassigned devices. */
    public static final int DEFAULT_NETWORK_ID = 0;

    public int deviceNetworkID;
    public int defaultNetworkID;
    public List<NetworkEntry> networks = new ArrayList<>();

    public PacketNetworkTabData() {}

    /**
     * Constructs a packet for the given player and device.
     *
     * @param registry        the server-side registry
     * @param playerID        AE2 playerID of the requesting player
     * @param deviceNetworkID the networkID currently assigned to the device
     */
    public PacketNetworkTabData(final SingularityNetworkRegistry registry, final int playerID,
        final int deviceNetworkID) {
        this.deviceNetworkID = deviceNetworkID;
        this.defaultNetworkID = registry.resolveAccessibleDefaultNetworkID(playerID);

        // Always include the unassigned sentinel (id=0) first.
        this.networks.add(new NetworkEntry(DEFAULT_NETWORK_ID, playerID, true, "Unassigned", 0x777777, 2, 4, false));

        // Add all explicitly created networks visible to the player.
        for (final int nid : registry.getVisibleNetworksForPlayer(playerID)) {
            final NetworkMeta meta = registry.getNetwork(nid);
            if (meta == null) continue;
            final boolean isOwner = meta.ownerPlayerID == playerID;
            this.networks.add(
                new NetworkEntry(
                    nid,
                    meta.ownerPlayerID,
                    isOwner,
                    meta.name,
                    meta.color,
                    meta.security.ordinal(),
                    meta.getAccessLevel(playerID)
                        .ordinal(),
                    meta.passwordHash != null,
                    meta.getAdmins(),
                    meta.getMembers(),
                    meta.getBlocked(),
                    resolvePlayerName(meta.ownerPlayerID),
                    resolvePlayerNames(meta.getAdmins()),
                    resolvePlayerNames(meta.getMembers()),
                    resolvePlayerNames(meta.getBlocked()),
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
        } catch (final Exception e) {
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

    @Override
    public void fromBytes(final ByteBuf buf) {
        this.deviceNetworkID = buf.readInt();
        this.defaultNetworkID = buf.readInt();
        final int count = buf.readInt();
        this.networks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            final int nid = buf.readInt();
            final int owner = buf.readInt();
            final boolean isOwner = buf.readBoolean();
            final int color = buf.readInt();
            final int security = buf.readInt();
            final int access = buf.readInt();
            final boolean passwordProtected = buf.readBoolean();
            final int nameLen = buf.readShort();
            final byte[] nameBytes = new byte[nameLen];
            buf.readBytes(nameBytes);
            final String name = new String(nameBytes, java.nio.charset.StandardCharsets.UTF_8);
            final List<Integer> admins = readIntList(buf);
            final List<Integer> members = readIntList(buf);
            final List<Integer> blocked = readIntList(buf);
            final String ownerName = readString(buf);
            final List<String> adminNames = readStringList(buf);
            final List<String> memberNames = readStringList(buf);
            final List<String> blockedNames = readStringList(buf);
            final long createdAtMillis = buf.readLong();
            final long lastModifiedMillis = buf.readLong();
            this.networks.add(
                new NetworkEntry(
                    nid,
                    owner,
                    isOwner,
                    name,
                    color,
                    security,
                    access,
                    passwordProtected,
                    admins,
                    members,
                    blocked,
                    ownerName,
                    adminNames,
                    memberNames,
                    blockedNames,
                    createdAtMillis,
                    lastModifiedMillis));
        }
    }

    @Override
    public void toBytes(final ByteBuf buf) {
        buf.writeInt(this.deviceNetworkID);
        buf.writeInt(this.defaultNetworkID);
        buf.writeInt(this.networks.size());
        for (final NetworkEntry e : this.networks) {
            buf.writeInt(e.networkID);
            buf.writeInt(e.ownerPlayerID);
            buf.writeBoolean(e.isOwner);
            buf.writeInt(e.color);
            buf.writeInt(e.securityOrdinal);
            buf.writeInt(e.accessLevelOrdinal);
            buf.writeBoolean(e.isPasswordProtected);
            final byte[] nameBytes = e.name.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            buf.writeShort(nameBytes.length);
            buf.writeBytes(nameBytes);
            writeIntList(buf, e.adminPlayerIDs);
            writeIntList(buf, e.memberPlayerIDs);
            writeIntList(buf, e.blockedPlayerIDs);
            writeString(buf, e.ownerName);
            writeStringList(buf, e.adminNames);
            writeStringList(buf, e.memberNames);
            writeStringList(buf, e.blockedNames);
            buf.writeLong(e.createdAtMillis);
            buf.writeLong(e.lastModifiedMillis);
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
        final int len = buf.readShort();
        final byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
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

    // ---- Inner types ----

    /** Immutable snapshot of one network entry for display in the GUI. */
    public static final class NetworkEntry {

        public final int networkID;
        public final int ownerPlayerID;
        public final boolean isOwner;
        public final String name;
        public final int color;
        public final int securityOrdinal;
        public final int accessLevelOrdinal;
        public final boolean isPasswordProtected;
        public final List<Integer> adminPlayerIDs;
        public final List<Integer> memberPlayerIDs;
        public final List<Integer> blockedPlayerIDs;
        /** 网络 owner 的玩家名；离线时为 "#id"。 */
        public final String ownerName;
        /** 网络创建时间戳（毫秒）。0 表示旧数据或未分配网络无时间戳。 */
        public final long createdAtMillis;
        /** 网络最后修改时间戳（毫秒）。0 表示旧数据或未分配网络无时间戳。 */
        public final long lastModifiedMillis;
        /** admins 对应的玩家名列表，顺序与 adminPlayerIDs 一致。 */
        public final List<String> adminNames;
        /** members 对应的玩家名列表，顺序与 memberPlayerIDs 一致。 */
        public final List<String> memberNames;
        /** blocked 对应的玩家名列表，顺序与 blockedPlayerIDs 一致。 */
        public final List<String> blockedNames;

        public NetworkEntry(final int networkID, final int ownerPlayerID, final boolean isOwner, final String name,
            final int color, final int securityOrdinal, final int accessLevelOrdinal,
            final boolean isPasswordProtected) {
            this(
                networkID,
                ownerPlayerID,
                isOwner,
                name,
                color,
                securityOrdinal,
                accessLevelOrdinal,
                isPasswordProtected,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                "#" + ownerPlayerID,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                0L,
                0L);
        }

        public NetworkEntry(final int networkID, final int ownerPlayerID, final boolean isOwner, final String name,
            final int color, final int securityOrdinal, final int accessLevelOrdinal, final boolean isPasswordProtected,
            final List<Integer> adminPlayerIDs, final List<Integer> memberPlayerIDs,
            final List<Integer> blockedPlayerIDs) {
            this(
                networkID,
                ownerPlayerID,
                isOwner,
                name,
                color,
                securityOrdinal,
                accessLevelOrdinal,
                isPasswordProtected,
                adminPlayerIDs,
                memberPlayerIDs,
                blockedPlayerIDs,
                "#" + ownerPlayerID,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                0L,
                0L);
        }

        public NetworkEntry(final int networkID, final int ownerPlayerID, final boolean isOwner, final String name,
            final int color, final int securityOrdinal, final int accessLevelOrdinal, final boolean isPasswordProtected,
            final List<Integer> adminPlayerIDs, final List<Integer> memberPlayerIDs,
            final List<Integer> blockedPlayerIDs, final String ownerName, final List<String> adminNames,
            final List<String> memberNames, final List<String> blockedNames) {
            this(
                networkID,
                ownerPlayerID,
                isOwner,
                name,
                color,
                securityOrdinal,
                accessLevelOrdinal,
                isPasswordProtected,
                adminPlayerIDs,
                memberPlayerIDs,
                blockedPlayerIDs,
                ownerName,
                adminNames,
                memberNames,
                blockedNames,
                0L,
                0L);
        }

        public NetworkEntry(final int networkID, final int ownerPlayerID, final boolean isOwner, final String name,
            final int color, final int securityOrdinal, final int accessLevelOrdinal, final boolean isPasswordProtected,
            final List<Integer> adminPlayerIDs, final List<Integer> memberPlayerIDs,
            final List<Integer> blockedPlayerIDs, final String ownerName, final List<String> adminNames,
            final List<String> memberNames, final List<String> blockedNames, final long createdAtMillis,
            final long lastModifiedMillis) {
            this.networkID = networkID;
            this.ownerPlayerID = ownerPlayerID;
            this.isOwner = isOwner;
            this.name = name;
            this.color = color & 0xFFFFFF;
            this.securityOrdinal = securityOrdinal;
            this.accessLevelOrdinal = accessLevelOrdinal;
            this.isPasswordProtected = isPasswordProtected;
            this.adminPlayerIDs = new ArrayList<>(adminPlayerIDs);
            this.memberPlayerIDs = new ArrayList<>(memberPlayerIDs);
            this.blockedPlayerIDs = new ArrayList<>(blockedPlayerIDs);
            this.ownerName = ownerName != null ? ownerName : "#" + ownerPlayerID;
            this.createdAtMillis = createdAtMillis;
            this.lastModifiedMillis = Math.max(createdAtMillis, lastModifiedMillis);
            this.adminNames = new ArrayList<>(adminNames);
            this.memberNames = new ArrayList<>(memberNames);
            this.blockedNames = new ArrayList<>(blockedNames);
        }
    }

    // ---- Handler (runs on CLIENT) ----

    public static final class Handler implements IMessageHandler<PacketNetworkTabData, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(final PacketNetworkTabData message, final MessageContext ctx) {
            Minecraft.getMinecraft()
                .func_152344_a(() -> {
                    if (NetworkTabUI.receiveNetworkData(message)) {
                        return;
                    }
                    if (NetworkTerminalUI.receiveNetworkData(message)) {
                        return;
                    }
                });
            return null;
        }
    }
}

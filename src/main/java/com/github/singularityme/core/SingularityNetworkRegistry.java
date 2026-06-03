package com.github.singularityme.core;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.storage.MapStorage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import appeng.api.config.SecurityPermissions;

/**
 * 持久化奇点网络元数据与 AE2 风格权限表。
 *
 * <p>owner 内建全部权限，不写入授权表；PUBLIC 网络在 Registry 层统一视为所有玩家拥有全部权限。</p>
 */
public class SingularityNetworkRegistry extends WorldSavedData {

    public static final String DATA_NAME = "singularityme_network_registry";
    public static final int DEFAULT_COLOR = 0xB6A8B2;
    private static final Logger LOG = LogManager.getLogger("SingularityME");

    /** 下一个可分配的 networkID；0 保留为未分配哨兵。 */
    private int nextNetworkID = 1;

    /** networkID -> 网络元数据。 */
    private final Map<Integer, NetworkMeta> networks = new HashMap<>();

    /** playerID -> 新放置设备的默认 networkID。 */
    private final Map<Integer, Integer> playerDefaults = new HashMap<>();

    public SingularityNetworkRegistry() {
        super(DATA_NAME);
    }

    public SingularityNetworkRegistry(final String name) {
        super(name);
    }

    /** 获取或创建当前世界的网络注册表存档。 */
    public static SingularityNetworkRegistry get(final World world) {
        final MapStorage storage = world.mapStorage;
        SingularityNetworkRegistry data = (SingularityNetworkRegistry) storage
            .loadData(SingularityNetworkRegistry.class, DATA_NAME);
        if (data == null) {
            data = new SingularityNetworkRegistry();
            storage.setData(DATA_NAME, data);
        }
        return data;
    }

    /** 创建默认私有网络。 */
    public synchronized int createNetwork(final int ownerPlayerID, final String name) {
        return createNetwork(ownerPlayerID, name, DEFAULT_COLOR, SecurityLevel.PRIVATE);
    }

    /** 创建指定颜色与安全级别的网络。 */
    public synchronized int createNetwork(final int ownerPlayerID, final String name, final int color,
        final SecurityLevel security) {
        final int id = nextNetworkID++;
        final long now = System.currentTimeMillis();
        networks.put(
            id,
            new NetworkMeta(
                ownerPlayerID,
                name,
                color,
                security == null ? SecurityLevel.PRIVATE : security,
                now,
                now));
        markDirty();
        LOG.info("[SingularityME] Created network id={} name='{}' owner={}", id, name, ownerPlayerID);
        return id;
    }

    /** 仅 owner 可删除网络。 */
    public synchronized boolean deleteNetwork(final int networkID, final int requestingPlayerID) {
        final NetworkMeta meta = networks.get(networkID);
        if (meta == null || meta.ownerPlayerID != requestingPlayerID) return false;
        networks.remove(networkID);
        playerDefaults.values()
            .removeIf(id -> id == networkID);
        markDirty();
        LOG.info("[SingularityME] Deleted network id={} by playerID={}", networkID, requestingPlayerID);
        return true;
    }

    /** SECURITY 持有者或 owner 可重命名网络。 */
    public synchronized boolean renameNetwork(final int networkID, final int requestingPlayerID, final String newName) {
        final NetworkMeta meta = networks.get(networkID);
        if (meta == null || !meta.canManagePermissions(requestingPlayerID)) return false;
        meta.name = newName;
        meta.touch();
        markDirty();
        return true;
    }

    /** SECURITY 持有者或 owner 可修改网络颜色与公开/私有类型。 */
    public synchronized boolean setNetworkSettings(final int networkID, final int requestingPlayerID, final int color,
        final SecurityLevel security) {
        final NetworkMeta meta = networks.get(networkID);
        if (meta == null || !meta.canManagePermissions(requestingPlayerID)) return false;
        meta.color = color & 0xFFFFFF;
        meta.security = security == null ? SecurityLevel.PRIVATE : security;
        meta.touch();
        markDirty();
        return true;
    }

    /** 设置某玩家在目标网络中的权限；owner 永远不可被修改。 */
    public synchronized boolean setPlayerPermissions(final int networkID, final int requesterID, final int targetID,
        final EnumSet<SecurityPermissions> perms) {
        final NetworkMeta meta = networks.get(networkID);
        if (meta == null || !meta.canManagePermissions(requesterID)) return false;
        if (targetID == meta.ownerPlayerID) return false;
        meta.setPermissions(targetID, perms);
        markDirty();
        return true;
    }

    /** 获取玩家在网络中的权限集合。 */
    public synchronized EnumSet<SecurityPermissions> getPlayerPermissions(final int networkID, final int playerID) {
        final NetworkMeta meta = networks.get(networkID);
        return meta == null ? EnumSet.noneOf(SecurityPermissions.class) : meta.getPermissions(playerID);
    }

    public synchronized NetworkMeta getNetwork(final int networkID) {
        return networks.get(networkID);
    }

    /** 网络列表可见性：PUBLIC 全可见，PRIVATE 需要至少一项权限或 owner 身份。 */
    public synchronized boolean canViewNetwork(final int networkID, final int playerID) {
        if (networkID == 0) return false;
        final NetworkMeta meta = networks.get(networkID);
        return meta != null && meta.canViewNetwork(playerID);
    }

    /** 是否可打开 GUI/使用网络。 */
    public synchronized boolean canUseNetwork(final int networkID, final int playerID) {
        if (networkID == 0) return false;
        final NetworkMeta meta = networks.get(networkID);
        return meta != null && meta.canUseNetwork(playerID);
    }

    /** 检查玩家是否拥有指定 AE2 权限。 */
    public synchronized boolean hasPermission(final int networkID, final int playerID,
        final SecurityPermissions permission) {
        if (networkID == 0 || permission == null) return false;
        final NetworkMeta meta = networks.get(networkID);
        return meta != null && meta.hasPermission(playerID, permission);
    }

    /** 返回请求玩家可见的网络 ID 列表。 */
    public synchronized List<Integer> getVisibleNetworksForPlayer(final int playerID) {
        final List<Integer> result = new ArrayList<>();
        for (final Map.Entry<Integer, NetworkMeta> entry : networks.entrySet()) {
            if (entry.getValue()
                .canViewNetwork(playerID)) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    public synchronized int getDefaultNetworkID(final int playerID) {
        return playerDefaults.getOrDefault(playerID, 0);
    }

    /** Registry 层仅检查 canUseNetwork；PacketSetDefaultNetwork 在发包层追加 BUILD 校验。 */
    public synchronized boolean setDefaultNetworkID(final int playerID, final int networkID) {
        if (playerID < 0) return false;
        if (networkID == 0) {
            playerDefaults.remove(playerID);
        } else {
            if (!canUseNetwork(networkID, playerID)) return false;
            playerDefaults.put(playerID, networkID);
        }
        markDirty();
        return true;
    }

    /** 默认网络失去访问权时自动解析为未分配。 */
    public synchronized int resolveAccessibleDefaultNetworkID(final int playerID) {
        final int networkID = getDefaultNetworkID(playerID);
        return networkID != 0 && canUseNetwork(networkID, playerID) ? networkID : 0;
    }

    @Override
    public void readFromNBT(final NBTTagCompound nbt) {
        nextNetworkID = nbt.getInteger("nextID");
        if (nextNetworkID < 1) nextNetworkID = 1;
        networks.clear();
        playerDefaults.clear();

        final NBTTagList list = nbt.getTagList("networks", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            final NBTTagCompound tag = list.getCompoundTagAt(i);
            final int id = tag.getInteger("id");
            final int owner = tag.getInteger("owner");
            final String name = tag.getString("name");
            final int color = tag.hasKey("color") ? tag.getInteger("color") & 0xFFFFFF : DEFAULT_COLOR;
            final SecurityLevel security = tag.hasKey("security")
                ? migrateSecurity(tag.getInteger("security"))
                : SecurityLevel.PRIVATE;
            final long createdAt = tag.hasKey("createdAtMillis") ? tag.getLong("createdAtMillis") : 0L;
            final long lastModifiedAt = tag.hasKey("lastModifiedMillis") ? tag.getLong("lastModifiedMillis") : createdAt;
            final NetworkMeta meta = new NetworkMeta(owner, name, color, security, createdAt, lastModifiedAt);

            if (tag.hasKey("permissions")) {
                readPermissionList(tag, meta);
            } else {
                migrateLegacyMembers(tag, meta);
            }
            networks.put(id, meta);
        }

        if (nbt.hasKey("playerDefaults")) {
            final NBTTagList defaults = nbt.getTagList("playerDefaults", 10);
            for (int i = 0; i < defaults.tagCount(); i++) {
                final NBTTagCompound tag = defaults.getCompoundTagAt(i);
                final int playerID = tag.getInteger("playerID");
                final int networkID = tag.getInteger("networkID");
                if (playerID >= 0 && networkID > 0) {
                    playerDefaults.put(playerID, networkID);
                }
            }
        }
        LOG.info(
            "[SingularityME] Loaded SingularityNetworkRegistry: {} network(s), {} default(s), nextID={}",
            networks.size(),
            playerDefaults.size(),
            nextNetworkID);
    }

    @Override
    public void writeToNBT(final NBTTagCompound nbt) {
        nbt.setInteger("nextID", nextNetworkID);
        final NBTTagList list = new NBTTagList();
        for (final Map.Entry<Integer, NetworkMeta> entry : networks.entrySet()) {
            final NetworkMeta meta = entry.getValue();
            final NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("id", entry.getKey());
            tag.setInteger("owner", meta.ownerPlayerID);
            tag.setString("name", meta.name);
            tag.setInteger("color", meta.color & 0xFFFFFF);
            tag.setInteger("security", meta.security.ordinal());
            tag.setLong("createdAtMillis", meta.createdAtMillis);
            tag.setLong("lastModifiedMillis", meta.lastModifiedMillis);
            writePermissionList(tag, meta);
            list.appendTag(tag);
        }
        nbt.setTag("networks", list);

        final NBTTagList defaults = new NBTTagList();
        for (final Map.Entry<Integer, Integer> entry : playerDefaults.entrySet()) {
            final NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("playerID", entry.getKey());
            tag.setInteger("networkID", entry.getValue());
            defaults.appendTag(tag);
        }
        nbt.setTag("playerDefaults", defaults);
    }

    /** 旧存档中 0=PUBLIC、1=ENCRYPTED、2=PRIVATE；新格式中 1 也代表 PRIVATE。 */
    private static SecurityLevel migrateSecurity(final int oldOrdinal) {
        return oldOrdinal == 0 ? SecurityLevel.PUBLIC : SecurityLevel.PRIVATE;
    }

    private static void readPermissionList(final NBTTagCompound tag, final NetworkMeta meta) {
        final NBTTagList permList = tag.getTagList("permissions", 10);
        for (int j = 0; j < permList.tagCount(); j++) {
            final NBTTagCompound permissionTag = permList.getCompoundTagAt(j);
            meta.setPermissions(
                permissionTag.getInteger("playerID"),
                PermissionBits.fromBits(permissionTag.getInteger("perms")));
        }
    }

    private static void writePermissionList(final NBTTagCompound tag, final NetworkMeta meta) {
        final NBTTagList permList = new NBTTagList();
        for (final Map.Entry<Integer, EnumSet<SecurityPermissions>> entry : meta.permissions.entrySet()) {
            final NBTTagCompound permissionTag = new NBTTagCompound();
            permissionTag.setInteger("playerID", entry.getKey());
            permissionTag.setInteger("perms", PermissionBits.toBits(entry.getValue()));
            permList.appendTag(permissionTag);
        }
        tag.setTag("permissions", permList);
    }

    private static void migrateLegacyMembers(final NBTTagCompound tag, final NetworkMeta meta) {
        final EnumSet<SecurityPermissions> adminPerms = EnumSet.allOf(SecurityPermissions.class);
        final EnumSet<SecurityPermissions> memberPerms = EnumSet.of(
            SecurityPermissions.BUILD,
            SecurityPermissions.CRAFT,
            SecurityPermissions.INJECT,
            SecurityPermissions.EXTRACT);
        for (final int playerID : readIntList(tag, "admins")) {
            meta.setPermissions(playerID, adminPerms);
        }
        for (final int playerID : readIntList(tag, "members")) {
            if (!meta.permissions.containsKey(playerID)) {
                meta.setPermissions(playerID, memberPerms);
            }
        }
    }

    private static List<Integer> readIntList(final NBTTagCompound tag, final String key) {
        final List<Integer> result = new ArrayList<>();
        if (!tag.hasKey(key)) return result;
        final NBTTagList list = tag.getTagList(key, 10);
        for (int i = 0; i < list.tagCount(); i++) {
            result.add(
                list.getCompoundTagAt(i)
                    .getInteger("id"));
        }
        return result;
    }

    public static final class NetworkMeta {

        public final int ownerPlayerID;
        public String name;
        public int color;
        public SecurityLevel security;
        public final long createdAtMillis;
        public long lastModifiedMillis;
        private final Map<Integer, EnumSet<SecurityPermissions>> permissions = new HashMap<>();

        NetworkMeta(final int ownerPlayerID, final String name, final int color, final SecurityLevel security,
            final long createdAtMillis, final long lastModifiedMillis) {
            this.ownerPlayerID = ownerPlayerID;
            this.name = name;
            this.color = color & 0xFFFFFF;
            this.security = security == null ? SecurityLevel.PRIVATE : security;
            this.createdAtMillis = createdAtMillis;
            this.lastModifiedMillis = Math.max(createdAtMillis, lastModifiedMillis);
        }

        /** 标记名称、颜色、安全级别等网络元数据已经变更；授权表变化不刷新该时间。 */
        void touch() {
            lastModifiedMillis = Math.max(lastModifiedMillis, System.currentTimeMillis());
        }

        /** 返回玩家拥有的权限集合；PUBLIC 网络对所有玩家视为全权限。 */
        public EnumSet<SecurityPermissions> getPermissions(final int playerID) {
            if (security == SecurityLevel.PUBLIC || playerID == ownerPlayerID) {
                return EnumSet.allOf(SecurityPermissions.class);
            }
            final EnumSet<SecurityPermissions> perms = permissions.get(playerID);
            return perms == null ? EnumSet.noneOf(SecurityPermissions.class) : EnumSet.copyOf(perms);
        }

        /** 检查玩家是否拥有指定权限。 */
        public boolean hasPermission(final int playerID, final SecurityPermissions permission) {
            if (permission == null) return false;
            if (security == SecurityLevel.PUBLIC || playerID == ownerPlayerID) return true;
            final EnumSet<SecurityPermissions> perms = permissions.get(playerID);
            return perms != null && perms.contains(permission);
        }

        /** 有任意权限即可打开 GUI/使用网络。 */
        public boolean canUseNetwork(final int playerID) {
            if (security == SecurityLevel.PUBLIC || playerID == ownerPlayerID) return true;
            final EnumSet<SecurityPermissions> perms = permissions.get(playerID);
            return perms != null && !perms.isEmpty();
        }

        /** PUBLIC 全员可见；PRIVATE 需要能使用。 */
        public boolean canViewNetwork(final int playerID) {
            return security == SecurityLevel.PUBLIC || canUseNetwork(playerID);
        }

        /** owner 或 SECURITY 持有者可管理授权表及网络设置。 */
        public boolean canManagePermissions(final int playerID) {
            return playerID == ownerPlayerID || hasPermission(playerID, SecurityPermissions.SECURITY);
        }

        /** 修改授权表；owner 权限内建，不进入表。 */
        void setPermissions(final int playerID, final EnumSet<SecurityPermissions> perms) {
            if (playerID == ownerPlayerID) return;
            if (perms == null || perms.isEmpty()) {
                permissions.remove(playerID);
            } else {
                permissions.put(playerID, EnumSet.copyOf(perms));
            }
        }

        /** 返回显式授权玩家 ID。 */
        public List<Integer> getAuthorizedPlayers() {
            return new ArrayList<>(permissions.keySet());
        }
    }
}

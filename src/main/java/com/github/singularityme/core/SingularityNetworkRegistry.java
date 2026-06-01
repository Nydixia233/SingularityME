package com.github.singularityme.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
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

/**
 * Persists Singularity network metadata (names, owners, members) across server restarts.
 */
public class SingularityNetworkRegistry extends WorldSavedData {

    public static final String DATA_NAME = "singularityme_network_registry";
    public static final int DEFAULT_COLOR = 0xB6A8B2;
    private static final Logger LOG = LogManager.getLogger("SingularityME");

    /** Next networkID to assign. Starts at 1; 0 is reserved for unassigned devices. */
    private int nextNetworkID = 1;

    /** networkID -> metadata for all explicitly created networks. */
    private final Map<Integer, NetworkMeta> networks = new HashMap<>();

    /** playerID -> preferred networkID for newly placed devices. */
    private final Map<Integer, Integer> playerDefaults = new HashMap<>();

    public SingularityNetworkRegistry() {
        super(DATA_NAME);
    }

    public SingularityNetworkRegistry(final String name) {
        super(name);
    }

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

    public synchronized int createNetwork(final int ownerPlayerID, final String name) {
        return createNetwork(ownerPlayerID, name, DEFAULT_COLOR, SecurityLevel.PRIVATE, null);
    }

    public synchronized int createNetwork(final int ownerPlayerID, final String name, final int color,
        final SecurityLevel security, final String passwordHash) {
        final int id = nextNetworkID++;
        final long now = System.currentTimeMillis();
        networks.put(
            id,
            new NetworkMeta(
                ownerPlayerID,
                name,
                color,
                security == null ? SecurityLevel.PRIVATE : security,
                cleanHash(passwordHash),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                now,
                now));
        markDirty();
        LOG.info("[SingularityME] Created network id={} name='{}' owner={}", id, name, ownerPlayerID);
        return id;
    }

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

    public synchronized boolean renameNetwork(final int networkID, final int requestingPlayerID, final String newName) {
        final NetworkMeta meta = networks.get(networkID);
        if (meta == null || !meta.canEdit(requestingPlayerID)) return false;
        meta.name = newName;
        meta.touch();
        markDirty();
        return true;
    }

    public synchronized boolean setNetworkSettings(final int networkID, final int requestingPlayerID, final int color,
        final SecurityLevel security, final String passwordHash) {
        final NetworkMeta meta = networks.get(networkID);
        if (meta == null || !meta.canEdit(requestingPlayerID)) return false;
        meta.color = color & 0xFFFFFF;
        meta.security = security == null ? SecurityLevel.PRIVATE : security;
        meta.passwordHash = cleanHash(passwordHash);
        meta.touch();
        markDirty();
        return true;
    }

    public synchronized boolean addMember(final int networkID, final int ownerPlayerID, final int memberPlayerID) {
        return setMemberRole(networkID, ownerPlayerID, memberPlayerID, AccessLevel.MEMBER);
    }

    public synchronized boolean removeMember(final int networkID, final int requestingPlayerID,
        final int memberPlayerID) {
        final NetworkMeta meta = networks.get(networkID);
        if (meta == null || !meta.canManageMembers(requestingPlayerID, memberPlayerID)) return false;
        final boolean changed = meta.memberPlayerIDs.remove(Integer.valueOf(memberPlayerID))
            | meta.adminPlayerIDs.remove(Integer.valueOf(memberPlayerID))
            | meta.blockedPlayerIDs.remove(Integer.valueOf(memberPlayerID));
        if (changed) markDirty();
        return changed;
    }

    public synchronized boolean setMemberRole(final int networkID, final int requestingPlayerID,
        final int targetPlayerID, final AccessLevel role) {
        final NetworkMeta meta = networks.get(networkID);
        if (meta == null || role == null || !meta.canManageMembers(requestingPlayerID, targetPlayerID)) return false;
        if (targetPlayerID == meta.ownerPlayerID) return false;

        meta.adminPlayerIDs.remove(Integer.valueOf(targetPlayerID));
        meta.memberPlayerIDs.remove(Integer.valueOf(targetPlayerID));
        meta.blockedPlayerIDs.remove(Integer.valueOf(targetPlayerID));

        switch (role) {
            case ADMIN -> meta.adminPlayerIDs.add(targetPlayerID);
            case MEMBER -> meta.memberPlayerIDs.add(targetPlayerID);
            case BLOCKED -> meta.blockedPlayerIDs.add(targetPlayerID);
            case NONE -> {}
            case OWNER -> {
                return false;
            }
        }
        markDirty();
        return true;
    }

    public synchronized boolean joinEncryptedNetwork(final int networkID, final int playerID,
        final String passwordHash) {
        final NetworkMeta meta = networks.get(networkID);
        if (meta == null || meta.security != SecurityLevel.ENCRYPTED) return false;
        if (meta.getAccessLevel(playerID) == AccessLevel.BLOCKED) return false;
        if (meta.canAccess(playerID)) return true;
        if (meta.passwordHash == null || !meta.passwordHash.equals(cleanHash(passwordHash))) return false;
        meta.memberPlayerIDs.add(playerID);
        markDirty();
        return true;
    }

    public synchronized NetworkMeta getNetwork(final int networkID) {
        return networks.get(networkID);
    }

    public synchronized List<Integer> getNetworksForPlayer(final int playerID) {
        final List<Integer> result = new ArrayList<>();
        for (final Map.Entry<Integer, NetworkMeta> entry : networks.entrySet()) {
            final NetworkMeta meta = entry.getValue();
            if (meta.getAccessLevel(playerID) == AccessLevel.OWNER || meta.getAccessLevel(playerID) == AccessLevel.ADMIN
                || meta.getAccessLevel(playerID) == AccessLevel.MEMBER) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    public synchronized List<Integer> getPublicNetworks() {
        final List<Integer> result = new ArrayList<>();
        for (final Map.Entry<Integer, NetworkMeta> entry : networks.entrySet()) {
            if (entry.getValue().security == SecurityLevel.PUBLIC) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    public synchronized List<Integer> getVisibleNetworksForPlayer(final int playerID) {
        final List<Integer> result = getNetworksForPlayer(playerID);
        for (final Map.Entry<Integer, NetworkMeta> entry : networks.entrySet()) {
            final int networkID = entry.getKey();
            final NetworkMeta meta = entry.getValue();
            if ((meta.security == SecurityLevel.PUBLIC || meta.security == SecurityLevel.ENCRYPTED)
                && !result.contains(networkID)) {
                result.add(networkID);
            }
        }
        return result;
    }

    public synchronized boolean hasAccess(final int networkID, final int playerID) {
        return canAccess(networkID, playerID);
    }

    public synchronized boolean canAccess(final int networkID, final int playerID) {
        if (networkID == 0) return false;
        final NetworkMeta meta = networks.get(networkID);
        return meta != null && meta.canAccess(playerID);
    }

    public synchronized AccessLevel getAccessLevel(final int networkID, final int playerID) {
        final NetworkMeta meta = networks.get(networkID);
        return meta == null ? AccessLevel.NONE : meta.getAccessLevel(playerID);
    }

    public synchronized int getDefaultNetworkID(final int playerID) {
        return playerDefaults.getOrDefault(playerID, 0);
    }

    public synchronized boolean setDefaultNetworkID(final int playerID, final int networkID) {
        if (playerID < 0) return false;
        if (networkID == 0) {
            playerDefaults.remove(playerID);
        } else {
            if (!canAccess(networkID, playerID)) return false;
            playerDefaults.put(playerID, networkID);
        }
        markDirty();
        return true;
    }

    public synchronized int resolveAccessibleDefaultNetworkID(final int playerID) {
        final int networkID = getDefaultNetworkID(playerID);
        return networkID != 0 && canAccess(networkID, playerID) ? networkID : 0;
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
                ? SecurityLevel.fromOrdinal(tag.getInteger("security"))
                : SecurityLevel.PRIVATE;
            final String passwordHash = tag.hasKey("passwordHash") ? cleanHash(tag.getString("passwordHash")) : null;
            final long createdAt = tag.hasKey("createdAtMillis") ? tag.getLong("createdAtMillis") : 0L;
            final long lastModifiedAt = tag.hasKey("lastModifiedMillis") ? tag.getLong("lastModifiedMillis") : createdAt;
            final List<Integer> admins = readIntList(tag, "admins");
            final List<Integer> members = readIntList(tag, "members");
            final List<Integer> blocked = readIntList(tag, "blocked");
            networks.put(
                id,
                new NetworkMeta(
                    owner,
                    name,
                    color,
                    security,
                    passwordHash,
                    admins,
                    members,
                    blocked,
                    createdAt,
                    lastModifiedAt));
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
            if (meta.passwordHash != null) {
                tag.setString("passwordHash", meta.passwordHash);
            }
            writeIntList(tag, "admins", meta.adminPlayerIDs);
            writeIntList(tag, "members", meta.memberPlayerIDs);
            writeIntList(tag, "blocked", meta.blockedPlayerIDs);
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

    private static void writeIntList(final NBTTagCompound tag, final String key, final List<Integer> values) {
        final NBTTagList list = new NBTTagList();
        for (final int value : values) {
            final NBTTagCompound entry = new NBTTagCompound();
            entry.setInteger("id", value);
            list.appendTag(entry);
        }
        tag.setTag(key, list);
    }

    public static String sha256Hex(final String input) {
        if (input == null || input.isEmpty()) return null;
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            final StringBuilder out = new StringBuilder(bytes.length * 2);
            for (final byte b : bytes) {
                out.append(String.format("%02x", b & 0xFF));
            }
            return out.toString();
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static String cleanHash(final String hash) {
        if (hash == null) return null;
        final String trimmed = hash.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase();
    }

    public static final class NetworkMeta {

        public final int ownerPlayerID;
        public String name;
        public int color;
        public SecurityLevel security;
        public String passwordHash;
        public final long createdAtMillis;
        public long lastModifiedMillis;
        public final List<Integer> adminPlayerIDs;
        public final List<Integer> memberPlayerIDs;
        public final List<Integer> blockedPlayerIDs;

        NetworkMeta(final int ownerPlayerID, final String name, final int color, final SecurityLevel security,
            final String passwordHash, final List<Integer> adminPlayerIDs, final List<Integer> memberPlayerIDs,
            final List<Integer> blockedPlayerIDs, final long createdAtMillis, final long lastModifiedMillis) {
            this.ownerPlayerID = ownerPlayerID;
            this.name = name;
            this.color = color & 0xFFFFFF;
            this.security = security == null ? SecurityLevel.PRIVATE : security;
            this.passwordHash = cleanHash(passwordHash);
            this.createdAtMillis = createdAtMillis;
            this.lastModifiedMillis = Math.max(createdAtMillis, lastModifiedMillis);
            this.adminPlayerIDs = adminPlayerIDs;
            this.memberPlayerIDs = memberPlayerIDs;
            this.blockedPlayerIDs = blockedPlayerIDs;
        }

        /** 标记名称、颜色、安全级别、密码等网络元数据已经变更；成员关系变化不刷新该时间。 */
        void touch() {
            lastModifiedMillis = Math.max(lastModifiedMillis, System.currentTimeMillis());
        }

        public AccessLevel getAccessLevel(final int playerID) {
            if (ownerPlayerID == playerID) return AccessLevel.OWNER;
            if (blockedPlayerIDs.contains(playerID)) return AccessLevel.BLOCKED;
            if (adminPlayerIDs.contains(playerID)) return AccessLevel.ADMIN;
            if (memberPlayerIDs.contains(playerID)) return AccessLevel.MEMBER;
            return AccessLevel.NONE;
        }

        public boolean canAccess(final int playerID) {
            final AccessLevel level = getAccessLevel(playerID);
            if (level == AccessLevel.BLOCKED) return false;
            if (level != AccessLevel.NONE) return true;
            return security == SecurityLevel.PUBLIC;
        }

        public boolean canEdit(final int playerID) {
            final AccessLevel level = getAccessLevel(playerID);
            return level == AccessLevel.OWNER || level == AccessLevel.ADMIN;
        }

        public boolean canManageMembers(final int playerID, final int targetPlayerID) {
            final AccessLevel actor = getAccessLevel(playerID);
            final AccessLevel target = getAccessLevel(targetPlayerID);
            if (actor == AccessLevel.OWNER) return target != AccessLevel.OWNER;
            if (actor == AccessLevel.ADMIN) {
                return target == AccessLevel.MEMBER || target == AccessLevel.NONE || target == AccessLevel.BLOCKED;
            }
            return false;
        }

        public List<Integer> getAdmins() {
            return Collections.unmodifiableList(adminPlayerIDs);
        }

        public List<Integer> getMembers() {
            return Collections.unmodifiableList(memberPlayerIDs);
        }

        public List<Integer> getBlocked() {
            return Collections.unmodifiableList(blockedPlayerIDs);
        }
    }
}

package com.github.singularityme.core;

import static appeng.api.config.SecurityPermissions.BUILD;
import static appeng.api.config.SecurityPermissions.CRAFT;
import static appeng.api.config.SecurityPermissions.EXTRACT;
import static appeng.api.config.SecurityPermissions.INJECT;
import static appeng.api.config.SecurityPermissions.SECURITY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.EnumSet;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import org.junit.Test;

import appeng.api.config.SecurityPermissions;

/** 验证奇点网络的 AE2 权限制语义与旧存档迁移。 */
public class SingularityNetworkRegistryPermissionsTest {

    /** owner 不进入授权表，但永远拥有全部权限。 */
    @Test
    public void ownerHasAllPermissions() {
        final SingularityNetworkRegistry registry = new SingularityNetworkRegistry("test_registry");
        final int networkID = registry.createNetwork(1, "Private", 0x123456, SecurityLevel.PRIVATE);

        assertEquals(EnumSet.allOf(SecurityPermissions.class), registry.getPlayerPermissions(networkID, 1));
        for (final SecurityPermissions permission : SecurityPermissions.values()) {
            assertTrue(registry.hasPermission(networkID, 1, permission));
        }
    }

    /** owner 可授予和撤销普通玩家的细粒度权限。 */
    @Test
    public void grantAndRevokePermissions() {
        final SingularityNetworkRegistry registry = new SingularityNetworkRegistry("test_registry");
        final int networkID = registry.createNetwork(1, "Private", 0x123456, SecurityLevel.PRIVATE);

        assertTrue(registry.setPlayerPermissions(networkID, 1, 2, EnumSet.of(BUILD, EXTRACT)));
        assertTrue(registry.hasPermission(networkID, 2, BUILD));
        assertTrue(registry.hasPermission(networkID, 2, EXTRACT));
        assertFalse(registry.hasPermission(networkID, 2, INJECT));

        assertTrue(registry.setPlayerPermissions(networkID, 1, 2, EnumSet.noneOf(SecurityPermissions.class)));
        assertFalse(registry.canUseNetwork(networkID, 2));
    }

    /** SECURITY 可下放，持有者能管理其他非 owner 玩家。 */
    @Test
    public void securityHolderCanManageOthers() {
        final SingularityNetworkRegistry registry = new SingularityNetworkRegistry("test_registry");
        final int networkID = registry.createNetwork(1, "Private", 0x123456, SecurityLevel.PRIVATE);

        assertTrue(registry.setPlayerPermissions(networkID, 1, 2, EnumSet.of(SECURITY)));
        assertTrue(registry.setPlayerPermissions(networkID, 2, 3, EnumSet.of(BUILD, SECURITY)));
        assertTrue(registry.hasPermission(networkID, 3, BUILD));
        assertTrue(registry.hasPermission(networkID, 3, SECURITY));
    }

    /** owner 的内建权限不可被任何授权表写入覆盖。 */
    @Test
    public void cannotModifyOwnerPermissions() {
        final SingularityNetworkRegistry registry = new SingularityNetworkRegistry("test_registry");
        final int networkID = registry.createNetwork(1, "Private", 0x123456, SecurityLevel.PRIVATE);

        assertFalse(registry.setPlayerPermissions(networkID, 1, 1, EnumSet.noneOf(SecurityPermissions.class)));
        assertEquals(EnumSet.allOf(SecurityPermissions.class), registry.getPlayerPermissions(networkID, 1));
    }

    /** PUBLIC 网络由 Registry 层统一短路为所有玩家可见、可用、全权限。 */
    @Test
    public void publicNetworkEveryoneCanUse() {
        final SingularityNetworkRegistry registry = new SingularityNetworkRegistry("test_registry");
        final int networkID = registry.createNetwork(1, "Public", 0x123456, SecurityLevel.PUBLIC);

        assertTrue(registry.canViewNetwork(networkID, 99));
        assertTrue(registry.canUseNetwork(networkID, 99));
        assertEquals(EnumSet.allOf(SecurityPermissions.class), registry.getPlayerPermissions(networkID, 99));
    }

    /** PRIVATE 网络未授权玩家不可见也不可用。 */
    @Test
    public void privateNetworkRequiresGrant() {
        final SingularityNetworkRegistry registry = new SingularityNetworkRegistry("test_registry");
        final int networkID = registry.createNetwork(1, "Private", 0x123456, SecurityLevel.PRIVATE);

        assertFalse(registry.canViewNetwork(networkID, 99));
        assertFalse(registry.canUseNetwork(networkID, 99));
        assertFalse(registry.hasPermission(networkID, 99, BUILD));
    }

    /** 可见/可用/BUILD 是三层不同语义，拥有一项非 BUILD 权限不代表能建造。 */
    @Test
    public void canViewVsCanUseVsBuild() {
        final SingularityNetworkRegistry registry = new SingularityNetworkRegistry("test_registry");
        final int networkID = registry.createNetwork(1, "Private", 0x123456, SecurityLevel.PRIVATE);

        assertTrue(registry.setPlayerPermissions(networkID, 1, 2, EnumSet.of(EXTRACT)));

        assertTrue(registry.canViewNetwork(networkID, 2));
        assertTrue(registry.canUseNetwork(networkID, 2));
        assertFalse(registry.hasPermission(networkID, 2, BUILD));
        assertTrue(registry.hasPermission(networkID, 2, EXTRACT));
    }

    /** 旧 admins/members/encrypted 存档按保守策略迁移为权限表和 PRIVATE。 */
    @Test
    public void legacyAdminMemberMigratesToPermissions() {
        final NBTTagCompound saved = new NBTTagCompound();
        saved.setInteger("nextID", 2);

        final NBTTagCompound network = new NBTTagCompound();
        network.setInteger("id", 1);
        network.setInteger("owner", 1);
        network.setString("name", "Legacy");
        network.setInteger("color", 0x123456);
        network.setInteger("security", 1);
        appendLegacyPlayer(network, "admins", 2);
        appendLegacyPlayer(network, "members", 3);

        final NBTTagList networks = new NBTTagList();
        networks.appendTag(network);
        saved.setTag("networks", networks);

        final SingularityNetworkRegistry loaded = new SingularityNetworkRegistry("test_registry");
        loaded.readFromNBT(saved);

        assertEquals(SecurityLevel.PRIVATE, loaded.getNetwork(1).security);
        assertEquals(EnumSet.allOf(SecurityPermissions.class), loaded.getPlayerPermissions(1, 2));
        assertEquals(EnumSet.of(BUILD, CRAFT, INJECT, EXTRACT), loaded.getPlayerPermissions(1, 3));
    }

    private static void appendLegacyPlayer(final NBTTagCompound network, final String key, final int playerID) {
        final NBTTagList list = network.hasKey(key) ? network.getTagList(key, 10) : new NBTTagList();
        final NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("id", playerID);
        list.appendTag(tag);
        network.setTag(key, list);
    }
}

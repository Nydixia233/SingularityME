package com.github.singularityme.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.github.singularityme.core.SingularityNetworkRegistry.JoinNetworkResult;

/** 验证玩家自助加入奇点网络的权限语义。 */
public class SingularityNetworkRegistryJoinTest {

    /** 公开网络允许访客自助加入，并把访客提升为 MEMBER。 */
    @Test
    public void publicGuestJoinBecomesMember() {
        final SingularityNetworkRegistry registry = new SingularityNetworkRegistry("test_registry");
        final int networkID = registry.createNetwork(1, "Public", 0x123456, SecurityLevel.PUBLIC, null);

        assertEquals(JoinNetworkResult.JOINED, registry.joinNetwork(networkID, 2, ""));

        assertEquals(AccessLevel.MEMBER, registry.getAccessLevel(networkID, 2));
    }

    /** 加密网络密码正确时加入成功，密码错误时权限不变。 */
    @Test
    public void encryptedJoinRequiresMatchingPasswordHash() {
        final SingularityNetworkRegistry registry = new SingularityNetworkRegistry("test_registry");
        final String passwordHash = SingularityNetworkRegistry.sha256Hex("secret");
        final int networkID = registry.createNetwork(1, "Encrypted", 0x123456, SecurityLevel.ENCRYPTED, passwordHash);

        assertEquals(JoinNetworkResult.BAD_PASSWORD, registry.joinNetwork(networkID, 2, "bad"));
        assertEquals(AccessLevel.NONE, registry.getAccessLevel(networkID, 2));

        assertEquals(JoinNetworkResult.JOINED, registry.joinNetwork(networkID, 2, passwordHash));
        assertEquals(AccessLevel.MEMBER, registry.getAccessLevel(networkID, 2));
    }

    /** 加密网络未传密码时返回需要密码，不改变访客权限。 */
    @Test
    public void encryptedJoinWithoutPasswordReportsRequired() {
        final SingularityNetworkRegistry registry = new SingularityNetworkRegistry("test_registry");
        final int networkID = registry.createNetwork(
            1,
            "Encrypted",
            0x123456,
            SecurityLevel.ENCRYPTED,
            SingularityNetworkRegistry.sha256Hex("secret"));

        assertEquals(JoinNetworkResult.PASSWORD_REQUIRED, registry.joinNetwork(networkID, 2, ""));
        assertEquals(AccessLevel.NONE, registry.getAccessLevel(networkID, 2));
    }

    /** 私有网络不允许访客自助加入。 */
    @Test
    public void privateGuestJoinFails() {
        final SingularityNetworkRegistry registry = new SingularityNetworkRegistry("test_registry");
        final int networkID = registry.createNetwork(1, "Private", 0x123456, SecurityLevel.PRIVATE, null);

        assertEquals(JoinNetworkResult.PRIVATE_NETWORK, registry.joinNetwork(networkID, 2, ""));

        assertEquals(AccessLevel.NONE, registry.getAccessLevel(networkID, 2));
    }

    /** 被拉黑玩家不能通过公开或加密入口重新加入。 */
    @Test
    public void blockedPlayerJoinFails() {
        final SingularityNetworkRegistry registry = new SingularityNetworkRegistry("test_registry");
        final int networkID = registry.createNetwork(1, "Public", 0x123456, SecurityLevel.PUBLIC, null);
        registry.setMemberRole(networkID, 1, 2, AccessLevel.BLOCKED);

        assertEquals(JoinNetworkResult.BLOCKED, registry.joinNetwork(networkID, 2, ""));

        assertEquals(AccessLevel.BLOCKED, registry.getAccessLevel(networkID, 2));
    }

    /** 已是成员、管理员或拥有者时重复加入应幂等成功，不改变角色。 */
    @Test
    public void existingAccessJoinIsIdempotent() {
        final SingularityNetworkRegistry registry = new SingularityNetworkRegistry("test_registry");
        final int networkID = registry.createNetwork(1, "Public", 0x123456, SecurityLevel.PUBLIC, null);
        registry.setMemberRole(networkID, 1, 2, AccessLevel.MEMBER);
        registry.setMemberRole(networkID, 1, 3, AccessLevel.ADMIN);

        assertEquals(JoinNetworkResult.ALREADY_MEMBER, registry.joinNetwork(networkID, 1, ""));
        assertEquals(JoinNetworkResult.ALREADY_MEMBER, registry.joinNetwork(networkID, 2, ""));
        assertEquals(JoinNetworkResult.ALREADY_MEMBER, registry.joinNetwork(networkID, 3, ""));

        assertEquals(AccessLevel.OWNER, registry.getAccessLevel(networkID, 1));
        assertEquals(AccessLevel.MEMBER, registry.getAccessLevel(networkID, 2));
        assertEquals(AccessLevel.ADMIN, registry.getAccessLevel(networkID, 3));
    }

    /** 不存在的网络返回明确错误码。 */
    @Test
    public void missingNetworkJoinFails() {
        final SingularityNetworkRegistry registry = new SingularityNetworkRegistry("test_registry");

        assertEquals(JoinNetworkResult.NETWORK_NOT_FOUND, registry.joinNetwork(99, 2, ""));
    }
}

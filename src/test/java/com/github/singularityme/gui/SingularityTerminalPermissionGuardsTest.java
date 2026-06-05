package com.github.singularityme.gui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import com.github.singularityme.tile.ISingularityNetworkDevice;

import appeng.api.config.SecurityPermissions;

/** 验证奇点终端自动合成入口必须按 CRAFT 权限放行。 */
public class SingularityTerminalPermissionGuardsTest {

    /** 奇点网络目标必须拥有 CRAFT 权限才允许进入 AE2 合成下单流程。 */
    @Test
    public void singularityTargetRequiresCraftPermission() {
        final FakeNetworkDevice target = new FakeNetworkDevice(7);

        assertFalse(SingularityTerminalPermissionGuards.canRequestCrafting(target, (networkID, permission) -> false));
        assertTrue(
            SingularityTerminalPermissionGuards.canRequestCrafting(
                target,
                (networkID, permission) -> networkID == 7 && permission == SecurityPermissions.CRAFT));
    }

    /** 非奇点目标继续交给 AE2 原生权限系统处理，不触发奇点权限查询。 */
    @Test
    public void nonSingularityTargetDoesNotUseCraftPermissionLookup() {
        final AtomicInteger calls = new AtomicInteger();

        assertTrue(
            SingularityTerminalPermissionGuards.canRequestCrafting(
                new Object(),
                (networkID, permission) -> {
                    calls.incrementAndGet();
                    return false;
                }));
        assertTrue(calls.get() == 0);
    }

    private static final class FakeNetworkDevice implements ISingularityNetworkDevice {

        private final int networkID;

        private FakeNetworkDevice(final int networkID) {
            this.networkID = networkID;
        }

        @Override
        public int getNetworkID() {
            return networkID;
        }

        @Override
        public void setNetworkID(final int newNetworkID) {}

        @Override
        public int getGridOwnerPlayerID() {
            return 1;
        }
    }
}

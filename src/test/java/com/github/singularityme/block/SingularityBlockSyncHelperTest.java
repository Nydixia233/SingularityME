package com.github.singularityme.block;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 无权限破坏被服务端拒绝时，必须只给发起玩家回滚客户端方块状态。 */
public class SingularityBlockSyncHelperTest {

    @Test
    public void resyncDeniedBreakOnlyTargetsServerSidePacketCapablePlayer() {
        assertTrue(SingularityBlockSyncHelper.shouldResyncDeniedBreak(true, true));
        assertFalse(SingularityBlockSyncHelper.shouldResyncDeniedBreak(false, true));
        assertFalse(SingularityBlockSyncHelper.shouldResyncDeniedBreak(true, false));
        assertFalse(SingularityBlockSyncHelper.shouldResyncDeniedBreak(false, false));
    }
}

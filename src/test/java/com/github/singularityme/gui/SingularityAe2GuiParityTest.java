package com.github.singularityme.gui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** 验证奇点设备保留 AE2 原版标签页坐标，只新增网络标签页。 */
public class SingularityAe2GuiParityTest {

    @Test
    public void drivePriorityTabUsesAe2OriginalYOffset() {
        assertEquals(66, GuiSingularityDrive.PRIORITY_TAB_Y_OFFSET);
    }

    @Test
    public void storageBusPriorityTabUsesAe2OriginalYOffset() {
        assertEquals(66, GuiSingularityStorageBus.PRIORITY_TAB_Y_OFFSET);
    }
}

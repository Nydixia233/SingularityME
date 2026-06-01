package com.github.singularityme.client.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.github.singularityme.client.ui.NetworkUiKit.Palette;

/** 验证网络 UI 设备类型展示辅助方法。 */
public class NetworkUiKitTest {

    @Test
    public void assignsKnownDeviceColors() {
        assertEquals(Palette.SECURITY_ENCRYPTED, NetworkUiKit.deviceTypeColor("TileSingularityPowerCore"));
        assertEquals(Palette.ACCESS_MEMBER, NetworkUiKit.deviceTypeColor("TileSingularityDrive"));
        assertEquals(Palette.ACCESS_ADMIN, NetworkUiKit.deviceTypeColor("TileSingularityNetworkTerminal"));
        assertEquals(Palette.BTN_NORMAL, NetworkUiKit.deviceTypeColor("TileSingularityStorageBus"));
        assertEquals(Palette.TEXT_MUTED, NetworkUiKit.deviceTypeColor("UnmappedDevice"));
    }

    /** 提亮颜色时保留 alpha，并按比例靠近白色。 */
    @Test
    public void lightensColorTowardWhite() {
        assertEquals(0xFF7F7F7F, NetworkUiKit.lighten(0xFF000000, 0.5f));
        assertEquals(0xFFFFFFFF, NetworkUiKit.lighten(0xFFFFFFFF, 0.5f));
    }

    /** 压暗系数应限制在 0~1，避免越界调用产生反直觉颜色。 */
    @Test
    public void clampsDarkenFactor() {
        assertEquals(0xFFFFFFFF, NetworkUiKit.darken(0xFFFFFFFF, 2.0f));
        assertEquals(0xFF000000, NetworkUiKit.darken(0xFFFFFFFF, -1.0f));
    }
}

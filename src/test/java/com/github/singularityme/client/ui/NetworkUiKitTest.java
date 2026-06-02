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

    /** 选中列表行只使用低饱和强调色，避免整行接近原色高亮。 */
    @Test
    public void selectedRowColorDarkensAccent() {
        assertEquals(0xFF172E48, NetworkUiKit.selectedRowColor(0xFF4A90E2));
        assertEquals(0xFF172E48, NetworkUiKit.selectedRowColor(0x004A90E2));
    }

    /** 颜色展示统一为 6 位大写 RGB，不泄露 alpha。 */
    @Test
    public void formatsRgbHexUppercase() {
        assertEquals("4A90E2", NetworkUiKit.rgbHex(0xFF4A90E2));
        assertEquals("00000A", NetworkUiKit.rgbHex(0x0000000A));
    }

    /** 默认网络徽章使用明确语义文本，避免单字母 D 在中文界面中像残留缩写。 */
    @Test
    public void defaultBadgeTextIsSemantic() {
        assertEquals(NetworkUiKit.tr("gui.singularityme.network_terminal.badge.default"),
            NetworkUiKit.defaultBadgeText());
    }

    /** 导航按钮使用稳定宽度，避免激活背景只绘制成小方块。 */
    @Test
    public void computesStableNavButtonWidth() {
        assertEquals(88, NetworkUiKit.navButtonWidth(760, 8));
        assertEquals(58, NetworkUiKit.navButtonWidth(520, 8));
    }
}

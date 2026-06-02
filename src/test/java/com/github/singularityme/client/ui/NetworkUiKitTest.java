package com.github.singularityme.client.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
        assertEquals(110, NetworkUiKit.navButtonWidth(594, 5));
        assertEquals(96, NetworkUiKit.navButtonWidth(520, 5));
    }

    @Test
    public void capsTerminalPanelSize() {
        assertEquals(1120, NetworkUiKit.terminalPanelWidth(2560, 1));
        assertEquals(700, NetworkUiKit.terminalPanelHeight(1440, 1));
        assertEquals(594, NetworkUiKit.terminalPanelWidth(2048, 2));
        assertEquals(359, NetworkUiKit.terminalPanelHeight(1088, 2));
        assertEquals(396, NetworkUiKit.terminalPanelWidth(2048, 3));
        assertEquals(239, NetworkUiKit.terminalPanelHeight(1088, 3));
        assertEquals(480, NetworkUiKit.terminalPanelWidth(800, 1));
        assertEquals(317, NetworkUiKit.terminalPanelHeight(480, 1));
    }

    /** 网络终端使用固定坐标骨架，避免顶层 Flow 相对布局在游戏内溢出或拉伸。 */
    @Test
    public void computesFixedTerminalLayout() {
        NetworkUiKit.TerminalLayout layout = NetworkUiKit.terminalLayout(594, 359);

        assertEquals(8, layout.navX);
        assertEquals(8, layout.navY);
        assertEquals(578, layout.navW);
        assertEquals(30, layout.navH);
        assertEquals(20, layout.railX);
        assertEquals(82, layout.railY);
        assertEquals(136, layout.railW);
        assertEquals(267, layout.railH);
        assertEquals(183, layout.railListH);
        assertEquals(168, layout.contentX);
        assertEquals(82, layout.contentY);
        assertEquals(406, layout.contentW);
        assertEquals(227, layout.contentH);
        assertEquals(168, layout.bottomX);
        assertEquals(317, layout.bottomY);
        assertEquals(406, layout.bottomW);
        assertEquals(32, layout.bottomH);
        assertTrue(layout.contentY + layout.contentH < layout.bottomY);
        assertTrue(layout.railY + layout.railH <= 349);
    }

    /** 高 GUI 缩放下不使用固定 GUI 坐标最小宽高硬撑面板，避免 guiScale=3/4 下整体变胖。 */
    @Test
    public void keepsTerminalLayoutBoundedAtHighGuiScale() {
        NetworkUiKit.TerminalLayout layout = NetworkUiKit.terminalLayout(396, 239);

        assertEquals(96, layout.railW);
        assertEquals(128, layout.contentX);
        assertEquals(248, layout.contentW);
        assertEquals(107, layout.contentH);
        assertEquals(63, layout.railListH);
        assertTrue(layout.contentY + layout.contentH < layout.bottomY);
        assertTrue(layout.railListH >= 56);
    }

    /** 主页信息在宽面板中使用两列紧凑布局，对齐 companion 预览稿的信息密度。 */
    @Test
    public void computesHomeInfoColumnWidth() {
        assertEquals(202, NetworkUiKit.homeInfoColumnWidth(406));
        assertEquals(360, NetworkUiKit.homeInfoColumnWidth(360));
    }

    @Test
    public void detectsHomeInfoColumnMode() {
        assertTrue(NetworkUiKit.homeInfoUsesTwoColumns(406));
        assertFalse(NetworkUiKit.homeInfoUsesTwoColumns(360));
    }

    @Test
    public void computesOverviewMetricWidth() {
        assertEquals(98, NetworkUiKit.metricCardWidth(406, 4));
        assertEquals(188, NetworkUiKit.metricCardWidth(380, 2));
    }

    @Test
    public void formatsCompactEnergy() {
        assertEquals("9.22P AE", NetworkUiKit.formatCompactEnergy(9223372036854777D));
        assertEquals("120k AE", NetworkUiKit.formatCompactEnergy(120000D));
    }

    @Test
    public void computesStableBadgeWidth() {
        assertEquals(25, NetworkUiKit.badgeWidth("*"));
        assertEquals(42, NetworkUiKit.badgeWidth("默认"));
        assertEquals(67, NetworkUiKit.badgeWidth("Default"));
    }

    /** 表单与主页标签使用固定宽度，避免值列和输入框起点抖动。 */
    @Test
    public void exposesStableLabelWidths() {
        assertEquals(82, Palette.INFO_LABEL_W);
        assertEquals(76, Palette.FORM_LABEL_W);
    }

    /** 左侧网络栏和基础按钮使用更紧凑的高度，避免在截图区域形成厚重色块。 */
    @Test
    public void usesCompactControlHeights() {
        assertEquals(26, Palette.ROW_H);
        assertEquals(16, Palette.TEXT_ROW_H);
        assertEquals(16, Palette.RAIL_HEADER_H);
        assertEquals(24, Palette.RAIL_FILTER_H);
        assertEquals(22, Palette.RAIL_ROW_H);
        assertEquals(24, Palette.RAIL_ACTION_H);
        assertEquals(16, Palette.BADGE_H);
        assertEquals(16, Palette.ID_PILL_H);
    }

    /** 高 GUI 缩放下仍保留两栏布局的最小可用空间，并让左侧操作按钮留在 rail 内部。 */
    @Test
    public void exposesTerminalMinimumBounds() {
        assertEquals(308, NetworkUiKit.terminalMinimumWidth());
        assertEquals(232, NetworkUiKit.terminalMinimumHeight());
        assertEquals(84, NetworkUiKit.terminalRailChromeHeight());
        assertEquals(88, NetworkUiKit.railActionWidth(96));
        assertEquals(128, NetworkUiKit.railActionWidth(136));
    }

    /** 终端列表高度优先占满内容区，只为过滤、元信息和选中栏预留稳定空间。 */
    @Test
    public void computesTerminalListHeights() {
        assertEquals(240, NetworkUiKit.selectionListHeight(356));
        assertEquals(308, NetworkUiKit.memberListHeight(356));
        assertEquals(120, NetworkUiKit.selectionListHeight(180));
        assertEquals(132, NetworkUiKit.memberListHeight(180));
    }

    /** 色板按钮视觉尺寸固定，选中态不得通过改变控件大小造成布局跳动。 */
    @Test
    public void exposesStableSwatchMetrics() {
        assertEquals(26, Palette.SWATCH_BUTTON_SIZE);
        assertEquals(22, Palette.SWATCH_INNER_SIZE);
    }
}

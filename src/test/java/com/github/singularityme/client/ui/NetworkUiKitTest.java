package com.github.singularityme.client.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.github.singularityme.client.ui.NetworkUiKit.Palette;
import com.github.singularityme.core.AccessLevel;
import com.github.singularityme.core.SecurityLevel;
import com.github.singularityme.network.packet.NetworkActionResult;
import com.github.singularityme.network.packet.PacketNetworkStatus;
import com.github.singularityme.network.packet.PacketNetworkTabData.NetworkEntry;

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
        assertEquals(0xFF122438, NetworkUiKit.selectedRowColor(0xFF4A90E2));
        assertEquals(0xFF122438, NetworkUiKit.selectedRowColor(0x004A90E2));
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

    /** 当前设备网络徽章必须使用明确语义文本，避免 "*" 在设备选择页看起来像异常符号。 */
    @Test
    public void currentBadgeTextIsSemantic() {
        assertEquals(NetworkUiKit.tr("gui.singularityme.network_tab.badge.current"),
            NetworkUiKit.currentBadgeText());
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
        assertEquals(594, NetworkUiKit.terminalPanelWidth(2048, 3));
        assertEquals(359, NetworkUiKit.terminalPanelHeight(1088, 3));
        assertEquals(594, NetworkUiKit.terminalPanelWidth(2048, 4));
        assertEquals(359, NetworkUiKit.terminalPanelHeight(1088, 4));
        assertEquals(480, NetworkUiKit.terminalPanelWidth(800, 1));
        assertEquals(317, NetworkUiKit.terminalPanelHeight(480, 1));
    }

    /** 终端内部控件以 guiScale=2 为参考；更高缩放需要缩小 GUI 坐标尺寸，保持物理像素观感一致。 */
    @Test
    public void computesTerminalVisualScale() {
        assertEquals(1.0f, NetworkUiKit.terminalVisualScale(1), 0.001f);
        assertEquals(1.0f, NetworkUiKit.terminalVisualScale(2), 0.001f);
        assertEquals(2.0f / 3.0f, NetworkUiKit.terminalVisualScale(3), 0.001f);
        assertEquals(0.5f, NetworkUiKit.terminalVisualScale(4), 0.001f);
        assertEquals(20, NetworkUiKit.terminalScaledPx(30, 3));
        assertEquals(15, NetworkUiKit.terminalScaledPx(30, 4));
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
        assertEquals(179, layout.railListH);
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
        NetworkUiKit.TerminalLayout layout = NetworkUiKit.terminalLayout(
            NetworkUiKit.terminalPanelWidth(2048, 3),
            NetworkUiKit.terminalPanelHeight(1088, 3),
            3);

        assertEquals(8, layout.navX);
        assertEquals(30, layout.navH);
        assertEquals(136, layout.railW);
        assertEquals(168, layout.contentX);
        assertEquals(406, layout.contentW);
        assertEquals(227, layout.contentH);
        assertEquals(179, layout.railListH);
        assertTrue(layout.contentY + layout.contentH < layout.bottomY);
        assertTrue(layout.railListH >= 72);
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

    /** 内容视口存在内边距，主页两列宽度必须按实际可用宽度计算，避免右侧被裁切。 */
    @Test
    public void computesContentInnerWidthForHomeRows() {
        assertEquals(4, Palette.CONTENT_VIEWPORT_PAD);
        assertEquals(398, NetworkUiKit.terminalContentInnerWidth(406));
        assertEquals(198, NetworkUiKit.homeInfoColumnWidth(NetworkUiKit.terminalContentInnerWidth(406)));
    }

    /** 切换网络时优先复用已收到的状态快照，避免内容区进入空白 loading 中间帧。 */
    @Test
    public void reusesCachedStatusForSelectedNetwork() {
        final Map<Integer, PacketNetworkStatus> cache = new LinkedHashMap<>();
        final PacketNetworkStatus status = new PacketNetworkStatus(13, 120D, 240D, Collections.emptyList());
        cache.put(status.networkID, status);

        assertSame(status, NetworkUiKit.cachedStatusForNetwork(cache, 13));
        assertNull(NetworkUiKit.cachedStatusForNetwork(cache, 14));
        assertNull(NetworkUiKit.cachedStatusForNetwork(cache, 0));
    }

    /** 状态包未返回时用稳定占位值填充主页指标，避免切换后指标行忽然消失再出现。 */
    @Test
    public void usesPendingStatusPlaceholderWhileLoading() {
        final PacketNetworkStatus status = new PacketNetworkStatus(13, 120D, 240D, Collections.emptyList());

        assertEquals("-", NetworkUiKit.statusValueOrPending(null, "120 AE"));
        assertEquals("120 AE", NetworkUiKit.statusValueOrPending(status, "120 AE"));
    }

    /** 主页顶部能量显示为现有/容量（百分比），状态未返回时保持占位。 */
    @Test
    public void formatsHomeEnergyOverview() {
        assertEquals("-", NetworkUiKit.formatHomeEnergyOverview(null));
        assertEquals("120 AE / 240 AE (50%)", NetworkUiKit.formatHomeEnergyOverview(
            new PacketNetworkStatus(13, 120D, 240D, Collections.emptyList())));
        assertEquals("900.00T AE / 900.00T AE (100%)", NetworkUiKit.formatHomeEnergyOverview(
            new PacketNetworkStatus(13, 900_000_000_000_000D, 900_000_000_000_000D, Collections.emptyList())));
    }

    /** 主页顶部在线率显示为在线/总数（百分比）。 */
    @Test
    public void formatsHomeOnlineOverview() {
        assertEquals("-", NetworkUiKit.formatHomeOnlineOverview(null));
        assertEquals("0 / 0 (0%)", NetworkUiKit.formatHomeOnlineOverview(
            new PacketNetworkStatus(13, 0D, 0D, Collections.emptyList())));
        assertEquals("1 / 2 (50%)", NetworkUiKit.formatHomeOnlineOverview(new PacketNetworkStatus(
            13,
            0D,
            0D,
            java.util.Arrays.asList(
                new PacketNetworkStatus.DeviceInfo("TileSingularityDrive", 1, 2, 3, 0, true),
                new PacketNetworkStatus.DeviceInfo("TileSingularityDrive", 4, 5, 6, 0, false)))));
    }

    /** 主页设备统计按状态包中的首次出现顺序聚合，便于稳定渲染两列网格。 */
    @Test
    public void countsHomeDeviceTypesInStableOrder() {
        final PacketNetworkStatus status = new PacketNetworkStatus(
            13,
            0D,
            0D,
            java.util.Arrays.asList(
                new PacketNetworkStatus.DeviceInfo("TileSingularityDrive", 1, 2, 3, 0, true),
                new PacketNetworkStatus.DeviceInfo("TileSingularityPowerCore", 4, 5, 6, 0, true),
                new PacketNetworkStatus.DeviceInfo("TileSingularityDrive", 7, 8, 9, 0, false)));

        final Map<String, Integer> counts = NetworkUiKit.countDeviceTypes(status);

        assertEquals(Integer.valueOf(2), counts.get("TileSingularityDrive"));
        assertEquals(Integer.valueOf(1), counts.get("TileSingularityPowerCore"));
        assertEquals("TileSingularityDrive", counts.keySet().iterator().next());
        assertTrue(NetworkUiKit.countDeviceTypes(null).isEmpty());
    }

    /** 主页设备统计的值列统一使用 xN，和统计页文案保持一致。 */
    @Test
    public void formatsHomeDeviceCountBadge() {
        assertEquals("x4", NetworkUiKit.formatCountBadge(4));
        assertEquals("x0", NetworkUiKit.formatCountBadge(0));
        assertEquals("x0", NetworkUiKit.formatCountBadge(-1));
    }

    /** 终端表面色保持参考稿的深蓝灰层级，避免内容区退回纯黑或高对比闪烁。 */
    @Test
    public void exposesReferenceSurfaceColors() {
        assertEquals(0xF0141923, Palette.BG_PANEL);
        assertEquals(0xD80D1219, Palette.BG_LIST);
        assertEquals(0xFF192331, Palette.BG_ROW_SOFT);
        assertEquals(0xB005070B, Palette.BG_OVERLAY);
    }

    @Test
    public void formatsCompactEnergy() {
        assertEquals("9.22P AE", NetworkUiKit.formatCompactEnergy(9223372036854777D));
        assertEquals("120k AE", NetworkUiKit.formatCompactEnergy(120000D));
    }

    @Test
    public void computesStableBadgeWidth() {
        assertEquals(24, NetworkUiKit.badgeWidth("*"));
        assertEquals(32, NetworkUiKit.badgeWidth("默认"));
        assertEquals(57, NetworkUiKit.badgeWidth("Default"));
        assertEquals(32, NetworkUiKit.idPillWidth(1));
        assertEquals(36, NetworkUiKit.idPillWidth(123));
    }

    /** 表单与主页标签使用固定宽度，避免值列和输入框起点抖动。 */
    @Test
    public void exposesStableLabelWidths() {
        assertEquals(64, Palette.INFO_LABEL_W);
        assertEquals(76, Palette.FORM_LABEL_W);
    }

    @Test
    public void treatsTwoArgumentPaddingAsHorizontalThenVertical() {
        final Flow row = Flow.row().padding(4, 0);

        assertEquals(4, row.getArea().getPadding().getLeft());
        assertEquals(4, row.getArea().getPadding().getRight());
        assertEquals(0, row.getArea().getPadding().getTop());
        assertEquals(0, row.getArea().getPadding().getBottom());
    }

    /** 左侧列表的圆点、编号胶囊和行距使用紧凑尺寸，匹配游戏内小列观感。 */
    @Test
    public void exposesCompactNetworkListMetrics() {
        assertEquals(6, Palette.STATUS_DOT_SIZE);
        assertEquals(4, Palette.LIST_ROW_PADDING_H);
        assertEquals(4, Palette.NETWORK_ROW_INSET);
        assertEquals(4, Palette.LIST_CONTENT_INSET);
        assertEquals(4, Palette.BADGE_PADDING_H);
        assertPaletteFieldMissing("BADGE_MARGIN_H");
        assertEquals(32, Palette.ID_PILL_MIN_W);
        assertEquals(2, Palette.LIST_ROW_GAP);
    }

    /** 带自身背景的表单胶囊也必须让圆点、文字和分段按钮离左右边界 4px。 */
    @Test
    public void keepsFormControlContentInsideItsOwnBackground() {
        final Flow colorField = NetworkUiKit.colorReadonly(0x4A90E2);
        assertEquals(4, colorField.getArea().getPadding().getLeft());
        assertEquals(4, colorField.getArea().getPadding().getRight());
        assertEquals(0, colorField.getArea().getPadding().getTop());
        assertEquals(0, colorField.getArea().getPadding().getBottom());

        final Flow securityField = NetworkUiKit.securitySegmentRow(SecurityLevel.PRIVATE, ignored -> {});
        assertEquals(4, securityField.getArea().getPadding().getLeft());
        assertEquals(4, securityField.getArea().getPadding().getRight());
        assertEquals(0, securityField.getArea().getPadding().getTop());
        assertEquals(0, securityField.getArea().getPadding().getBottom());
    }

    /** 主页信息胶囊使用自身背景时，内部标签和值也必须离左右边界 4px。 */
    @Test
    public void keepsInfoRowsInsideTheirOwnBackground() {
        final Flow fixed = NetworkUiKit.infoRowFixed("标签", "值");
        assertEquals(4, fixed.getArea().getPadding().getLeft());
        assertEquals(4, fixed.getArea().getPadding().getRight());
        assertEquals(0, fixed.getArea().getPadding().getTop());
        assertEquals(0, fixed.getArea().getPadding().getBottom());

        final Flow compact = NetworkUiKit.infoRowCompact("标签", "值");
        assertEquals(4, compact.getArea().getPadding().getLeft());
        assertEquals(4, compact.getArea().getPadding().getRight());
        assertEquals(0, compact.getArea().getPadding().getTop());
        assertEquals(0, compact.getArea().getPadding().getBottom());

        final Flow selection = NetworkUiKit.selectionBar("选中", 0x4A90E2);
        assertEquals(4, selection.getArea().getPadding().getLeft());
        assertEquals(4, selection.getArea().getPadding().getRight());
        assertEquals(0, selection.getArea().getPadding().getTop());
        assertEquals(0, selection.getArea().getPadding().getBottom());
    }

    private static void assertPaletteFieldMissing(final String name) {
        try {
            Palette.class.getField(name);
            fail("Palette should not expose " + name + ".");
        } catch (NoSuchFieldException expected) {
            // expected
        }
    }

    /** 左侧网络栏、连接列表和成员列表使用更紧凑的高度，避免形成厚重色块。 */
    @Test
    public void usesCompactControlHeights() {
        assertEquals(26, Palette.ROW_H);
        assertEquals(22, Palette.LIST_ROW_H);
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
        assertEquals(236, NetworkUiKit.terminalMinimumHeight());
        assertEquals(88, NetworkUiKit.terminalRailChromeHeight());
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

    /** 嵌套列表必须使用内容视口扣除 padding 后的高度，避免外层内容区出现 8px 假滚动。 */
    @Test
    public void computesContentInnerHeightForNestedLists() {
        assertEquals(219, NetworkUiKit.terminalContentInnerHeight(227));
        assertEquals(348, NetworkUiKit.terminalContentInnerHeight(356));
        assertEquals(219, NetworkUiKit.connectionListHeight(227));
    }

    /** 页面切换复用同一个 ListWidget 时必须清除旧滚动位置，避免滚动偏移泄漏到其他页面。 */
    @Test
    public void resetsListScrollOffset() {
        final ListWidget<?, ?> list = new ListWidget<>();
        list.onInit();
        list.size(100, 100);
        list.getScrollData().setScrollSize(200);
        list.getScrollData().scrollTo(list.getScrollArea(), 40);

        NetworkUiKit.resetListScroll(list);

        assertEquals(0, list.getScrollData().getScroll());
    }

    /** 色板按钮视觉尺寸固定，选中态不得通过改变控件大小造成布局跳动。 */
    @Test
    public void exposesStableSwatchMetrics() {
        assertEquals(26, Palette.SWATCH_BUTTON_SIZE);
        assertEquals(22, Palette.SWATCH_INNER_SIZE);
    }

    /** 色板行必须把按钮完整放在父行点击区域内，否则游戏里会出现看得到但点不到。 */
    @Test
    public void keepsColorSwatchesInsideClickableRowBounds() {
        final Flow row = NetworkUiKit.colorSwatchRow(new int[] { 0x4A90E2 }, 0x4A90E2, ignored -> {});

        assertEquals(NetworkUiKit.formInputOffset(), row.getArea().getPadding().getLeft());
        assertEquals(0, row.getArea().getPadding().getRight());
        assertEquals(0, row.getArea().getPadding().getTop());
        assertEquals(0, row.getArea().getPadding().getBottom());
    }

    /** 设置/创建页色板第一颗色块必须与上方表单输入框左边缘对齐。 */
    @Test
    public void alignsColorSwatchesWithFormInputStart() {
        assertEquals(84, NetworkUiKit.formInputOffset());

        final Flow row = NetworkUiKit.colorSwatchRow(new int[] { 0x4A90E2 }, 0x4A90E2, ignored -> {});

        assertEquals(NetworkUiKit.formInputOffset(), row.getArea().getPadding().getLeft());
    }

    /** 色块视觉必须挂在 ButtonWidget 本体上，避免内部 Flow 抢占 MUI2 的 hover/click 命中链。 */
    @Test
    public void rendersColorSwatchesOnClickableButtonItself() {
        final Flow row = NetworkUiKit.colorSwatchRow(new int[] { 0x4A90E2 }, 0x4A90E2, ignored -> {});
        final IWidget child = row.getChildren().get(0);

        assertTrue(child instanceof ButtonWidget);
        final ButtonWidget<?> button = (ButtonWidget<?>) child;
        assertTrue(IDrawable.isVisible(button.getBackground()));
        assertTrue(IDrawable.isVisible(button.getOverlay()));
        assertTrue(button.getChildren().isEmpty());
    }

    /** 公开访客网络应显示为可自助加入，并且仍可访问以保持旧 UI 兼容。 */
    @Test
    public void detectsPublicGuestSelfJoin() {
        final NetworkEntry entry = entry(SecurityLevel.PUBLIC, AccessLevel.NONE);

        assertTrue(NetworkUiKit.canAccess(entry));
        assertTrue(NetworkUiKit.isPublicJoinAvailable(entry));
        assertTrue(NetworkUiKit.canSelfJoin(entry));
        assertFalse(NetworkUiKit.isEncryptedJoinRequired(entry));
        assertFalse(NetworkUiKit.isMemberAccess(entry));
    }

    /** 加密访客网络不应直接访问，应进入密码加入流程。 */
    @Test
    public void detectsEncryptedGuestPasswordJoin() {
        final NetworkEntry entry = entry(SecurityLevel.ENCRYPTED, AccessLevel.NONE);

        assertFalse(NetworkUiKit.canAccess(entry));
        assertFalse(NetworkUiKit.isPublicJoinAvailable(entry));
        assertTrue(NetworkUiKit.isEncryptedJoinRequired(entry));
        assertTrue(NetworkUiKit.canSelfJoin(entry));
        assertFalse(NetworkUiKit.isMemberAccess(entry));
    }

    /** 私有访客、拉黑玩家和已有成员在两种 GUI 中应得到一致权限判断。 */
    @Test
    public void classifiesPrivateBlockedAndMemberAccess() {
        final NetworkEntry privateGuest = entry(SecurityLevel.PRIVATE, AccessLevel.NONE);
        final NetworkEntry blocked = entry(SecurityLevel.PUBLIC, AccessLevel.BLOCKED);
        final NetworkEntry member = entry(SecurityLevel.PRIVATE, AccessLevel.MEMBER);

        assertFalse(NetworkUiKit.canAccess(privateGuest));
        assertFalse(NetworkUiKit.canSelfJoin(privateGuest));

        assertFalse(NetworkUiKit.canAccess(blocked));
        assertTrue(NetworkUiKit.isBlocked(blocked));
        assertFalse(NetworkUiKit.canSelfJoin(blocked));

        assertTrue(NetworkUiKit.canAccess(member));
        assertTrue(NetworkUiKit.isMemberAccess(member));
        assertFalse(NetworkUiKit.canSelfJoin(member));
    }

    /** 操作结果颜色只按成功/失败语义映射，保证终端和设备选择界面反馈一致。 */
    @Test
    public void mapsActionResultColorsBySuccessFlag() {
        assertEquals(Palette.SECURITY_PUBLIC, NetworkUiKit.actionResultColor(NetworkActionResult.JOINED));
        assertEquals(Palette.SECURITY_PUBLIC, NetworkUiKit.actionResultColor(NetworkActionResult.DEVICE_ASSIGNED));
        assertEquals(Palette.BTN_DANGER_NORMAL, NetworkUiKit.actionResultColor(NetworkActionResult.BAD_PASSWORD));
        assertEquals(Palette.BTN_DANGER_NORMAL, NetworkUiKit.actionResultColor(NetworkActionResult.NO_ACCESS));
        assertEquals(Palette.TEXT_MUTED, NetworkUiKit.actionResultColor(null));
    }

    /** 设备分配主动作应随目标网络状态变化，减少“点了会发生什么”的猜测。 */
    @Test
    public void describesDeviceAssignmentPrimaryActions() {
        final NetworkEntry current = entry(7, SecurityLevel.PRIVATE, AccessLevel.MEMBER);
        final NetworkEntry unassigned = entry(0, SecurityLevel.PUBLIC, AccessLevel.NONE);
        final NetworkEntry publicGuest = entry(8, SecurityLevel.PUBLIC, AccessLevel.NONE);
        final NetworkEntry encryptedGuest = entry(8, SecurityLevel.ENCRYPTED, AccessLevel.NONE);
        final NetworkEntry privateGuest = entry(8, SecurityLevel.PRIVATE, AccessLevel.NONE);

        assertFalse(NetworkUiKit.canAssignDeviceTo(current, 7));
        assertEquals(NetworkUiKit.tr("gui.singularityme.network_tab.action.current"),
            NetworkUiKit.deviceAssignmentActionText(current, 7));

        assertTrue(NetworkUiKit.canAssignDeviceTo(unassigned, 7));
        assertEquals(NetworkUiKit.tr("gui.singularityme.network_tab.action.unassign"),
            NetworkUiKit.deviceAssignmentActionText(unassigned, 7));

        assertTrue(NetworkUiKit.canAssignDeviceTo(publicGuest, 7));
        assertEquals(NetworkUiKit.tr("gui.singularityme.network_tab.action.join_assign"),
            NetworkUiKit.deviceAssignmentActionText(publicGuest, 7));

        assertTrue(NetworkUiKit.canAssignDeviceTo(encryptedGuest, 7));
        assertEquals(NetworkUiKit.tr("gui.singularityme.network_tab.action.password_assign"),
            NetworkUiKit.deviceAssignmentActionText(encryptedGuest, 7));

        assertFalse(NetworkUiKit.canAssignDeviceTo(privateGuest, 7));
        assertEquals(NetworkUiKit.tr("gui.singularityme.network_tab.action.unavailable"),
            NetworkUiKit.deviceAssignmentActionText(privateGuest, 7));
    }

    /** 设备分配提示文字必须解释可用/不可用原因，并用稳定颜色表达状态。 */
    @Test
    public void explainsDeviceAssignmentState() {
        final NetworkEntry current = entry(7, SecurityLevel.PRIVATE, AccessLevel.MEMBER);
        final NetworkEntry publicGuest = entry(8, SecurityLevel.PUBLIC, AccessLevel.NONE);
        final NetworkEntry encryptedGuest = entry(8, SecurityLevel.ENCRYPTED, AccessLevel.NONE);
        final NetworkEntry privateGuest = entry(8, SecurityLevel.PRIVATE, AccessLevel.NONE);
        final NetworkEntry blocked = entry(8, SecurityLevel.PUBLIC, AccessLevel.BLOCKED);

        assertEquals(NetworkUiKit.tr("gui.singularityme.network_tab.hint.current"),
            NetworkUiKit.deviceAssignmentHint(current, 7));
        assertEquals(Palette.TEXT_MUTED, NetworkUiKit.deviceAssignmentHintColor(current, 7));

        assertEquals(NetworkUiKit.tr("gui.singularityme.network_tab.hint.public_join"),
            NetworkUiKit.deviceAssignmentHint(publicGuest, 7));
        assertEquals(Palette.SECURITY_PUBLIC, NetworkUiKit.deviceAssignmentHintColor(publicGuest, 7));

        assertEquals(NetworkUiKit.tr("gui.singularityme.network_tab.hint.password_join"),
            NetworkUiKit.deviceAssignmentHint(encryptedGuest, 7));
        assertEquals(Palette.SECURITY_ENCRYPTED, NetworkUiKit.deviceAssignmentHintColor(encryptedGuest, 7));

        assertEquals(NetworkUiKit.tr("gui.singularityme.network_action.private_network"),
            NetworkUiKit.deviceAssignmentHint(privateGuest, 7));
        assertEquals(Palette.BTN_DANGER_NORMAL, NetworkUiKit.deviceAssignmentHintColor(privateGuest, 7));

        assertEquals(NetworkUiKit.tr("gui.singularityme.network_action.blocked"),
            NetworkUiKit.deviceAssignmentHint(blocked, 7));
        assertEquals(Palette.BTN_DANGER_NORMAL, NetworkUiKit.deviceAssignmentHintColor(blocked, 7));
    }

    private static NetworkEntry entry(final SecurityLevel security, final AccessLevel access) {
        return entry(7, security, access);
    }

    private static NetworkEntry entry(final int id, final SecurityLevel security, final AccessLevel access) {
        return new NetworkEntry(
            id,
            1,
            access == AccessLevel.OWNER,
            "Alpha",
            0x4A90E2,
            security.ordinal(),
            access.ordinal(),
            security == SecurityLevel.ENCRYPTED);
    }
}

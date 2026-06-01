package com.github.singularityme.client.ui;

import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.drawable.Circle;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.github.singularityme.core.AccessLevel;
import com.github.singularityme.core.SecurityLevel;
import com.github.singularityme.network.packet.PacketNetworkTabData.NetworkEntry;

/**
 * 网络 UI 公共工具类，供 {@link NetworkTabUI} 和 {@link NetworkTerminalUI} 共用。
 *
 * <p>
 * 包含：颜色/尺寸常量（{@link Palette}）、MUI2 样式工厂、颜色计算、
 * 安全/访问级别展示、权限判断、i18n 工具。
 * </p>
 */
public final class NetworkUiKit {

    private NetworkUiKit() {}

    // ---- 颜色 / 尺寸常量 ----

    /** 颜色与尺寸常量集中管理，避免魔法数散落各处。 */
    public static final class Palette {

        private Palette() {}

        // 背景色
        public static final int BG_PANEL = 0xEE141923;
        public static final int BG_LIST = 0xFF0D1219;
        public static final int BG_ROW = 0xFF151D27;
        public static final int BG_INPUT = 0xFF0D1117;
        public static final int BG_OVERLAY = 0xAA05070B;
        public static final int BG_ID_PILL = 0xFF0B1016;

        // 边框色
        public static final int BORDER_PANEL = 0xFF536B7F;
        public static final int BORDER_LIST = 0xFF2C3846;
        public static final int BORDER_ROW = 0xFF263240;
        public static final int BORDER_INPUT_NORMAL = 0xFF354451;
        public static final int BORDER_INPUT_FOCUS = 0xFF6AA1D2;
        public static final int BORDER_SWATCH = 0xFF0A0D10;

        // 文字色
        public static final int TEXT_PRIMARY = 0xFFEAF2F8;
        public static final int TEXT_SECONDARY = 0xFFD7E0EA;
        public static final int TEXT_MUTED = 0xFF9FB0BF;
        public static final int TEXT_EMPTY = 0xFF8996A3;
        public static final int TEXT_LABEL = 0xFFAFC0CC;
        public static final int TEXT_BADGE = 0xFFFFFFFF;
        public static final int TEXT_INPUT = 0xFFEAF2F8;
        public static final int TEXT_INPUT_PLACEHOLDER = 0xFF748392;
        public static final int TEXT_INPUT_DISABLED = 0xFF5C6770;
        public static final int TEXT_INPUT_BUTTON_DISABLED = 0xFF8B99A6;

        // 按钮色
        public static final int BTN_NORMAL = 0xFF2F6F95;
        public static final int BTN_ACTIVE = 0xFF255878;
        public static final int BTN_DISABLED = 0xFF33414E;
        public static final int BTN_FOCUS_BORDER = 0xFF9ED0FF;
        public static final int BTN_DANGER_NORMAL = 0xFF8C3D3D;
        public static final int BTN_DANGER_ACTIVE = 0xFF6F3030;
        public static final int BTN_DANGER_DISABLED = 0xFF4A3B3B;

        // 安全级别色
        public static final int SECURITY_PUBLIC = 0xFF2F8C52;
        public static final int SECURITY_ENCRYPTED = 0xFFC48A32;
        public static final int SECURITY_PRIVATE = 0xFF7055A8;

        // 访问级别色
        public static final int ACCESS_OWNER = 0xFF3D78C2;
        public static final int ACCESS_ADMIN = 0xFF5E7FC7;
        public static final int ACCESS_MEMBER = 0xFF4D8F8A;
        public static final int ACCESS_BLOCKED = 0xFF9A3C3C;
        public static final int ACCESS_NONE = 0xFF5D6875;

        // 特殊标记色
        public static final int BADGE_CURRENT = 0xFF2F8C52;
        public static final int BADGE_DEFAULT = 0xFF446EAA;
        public static final int BADGE_INACTIVE = 0xFF5D6875;

        // 未分配网络的默认色
        public static final int COLOR_UNASSIGNED = 0xFF777777;

        // 尺寸
        public static final int ROW_H = 36;
        /** 文本行固定高度。无固定高度的 Row 内含 TextWidget + 垂直 padding 会导致 MUI2 循环求解失败，统一用此高度兜底。 */
        public static final int TEXT_ROW_H = 20;
        public static final int BADGE_H = 22;
        public static final int BADGE_MIN_W = 24;
        public static final int BADGE_PADDING_H = 5;
        public static final int BORDER_RADIUS_PANEL = 6;
        public static final int BORDER_RADIUS_ROW = 4;
        public static final int BORDER_RADIUS_BADGE = 3;
        public static final int BORDER_RADIUS_SWATCH = 2;
        public static final int ID_PILL_W = 48;
        public static final int ID_PILL_H = 22;
    }

    // ---- 颜色计算 ----

    /**
     * 将颜色按比例压暗（乘以 factor）。
     *
     * @param color  ARGB 颜色值
     * @param factor 压暗系数（0.0~1.0）
     * @return 压暗后的 ARGB 颜色值（alpha 固定为 0xFF）
     */
    public static int darken(final int color, final float factor) {
        final float clamped = Math.max(0.0f, Math.min(1.0f, factor));
        final int r = (int) (((color >> 16) & 0xFF) * clamped);
        final int g = (int) (((color >> 8) & 0xFF) * clamped);
        final int b = (int) ((color & 0xFF) * clamped);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /**
     * 将颜色按比例提亮到白色。
     *
     * @param color  ARGB 颜色值
     * @param factor 提亮系数（0.0~1.0）
     * @return 提亮后的 ARGB 颜色值（alpha 固定为 0xFF）
     */
    public static int lighten(final int color, final float factor) {
        final float clamped = Math.max(0.0f, Math.min(1.0f, factor));
        final int r = (int) (((color >> 16) & 0xFF) + (255 - ((color >> 16) & 0xFF)) * clamped);
        final int g = (int) (((color >> 8) & 0xFF) + (255 - ((color >> 8) & 0xFF)) * clamped);
        final int b = (int) ((color & 0xFF) + (255 - (color & 0xFF)) * clamped);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    // ---- MUI2 样式工厂 ----

    /** 集中生成 MUI2 drawable；每次返回新实例，避免可变 drawable 状态串扰。 */
    public static final class Styles {

        private Styles() {}

        /** 主面板背景。 */
        public static IDrawable panelBg() {
            return new Rectangle()
                .cornerRadius(Palette.BORDER_RADIUS_PANEL)
                .verticalGradient(lighten(Palette.BG_PANEL, 0.08f), Palette.BG_PANEL);
        }

        /** 次级卡片背景。 */
        public static IDrawable cardBg() {
            return new Rectangle()
                .cornerRadius(Palette.BORDER_RADIUS_ROW)
                .verticalGradient(lighten(Palette.BG_ROW, 0.06f), Palette.BG_ROW);
        }

        /** 列表容器背景。 */
        public static IDrawable listBg() {
            return new Rectangle()
                .cornerRadius(Palette.BORDER_RADIUS_ROW)
                .color(Palette.BG_LIST);
        }

        /** 列表行或按钮背景。 */
        public static IDrawable rowBg(final int color) {
            return new Rectangle()
                .cornerRadius(Palette.BORDER_RADIUS_ROW)
                .verticalGradient(lighten(color, 0.08f), color);
        }

        /** 输入框背景。 */
        public static IDrawable inputBg() {
            return new Rectangle()
                .cornerRadius(Palette.BORDER_RADIUS_ROW)
                .color(Palette.BG_INPUT);
        }

        /** 顶部导航背景。 */
        public static IDrawable headerGradient(final int color) {
            return new Rectangle()
                .cornerRadius(Palette.BORDER_RADIUS_ROW)
                .verticalGradient(lighten(color, 0.16f), color);
        }

        /** 颜色块背景。 */
        public static IDrawable swatch(final int color) {
            return new Rectangle()
                .cornerRadius(Palette.BORDER_RADIUS_SWATCH)
                .color(0xFF000000 | color);
        }

        /** 状态圆点。 */
        public static IDrawable statusDot(final int color) {
            return new Circle()
                .color(color)
                .segments(12);
        }
    }

    /** 创建自适应文本行，避免 TextWidget 在固定高度内触发垂直 padding 溢出。 */
    public static Flow textRow() {
        return Flow.row()
            .coverChildrenHeight()
            .margin(2, 0)
            .crossAxisAlignment(Alignment.CrossAxis.CENTER);
    }

    /** 创建固定高度行，仅保留水平 padding。 */
    public static Flow fixedRow(final int height) {
        return Flow.row()
            .height(height)
            .padding(0, 12)
            .crossAxisAlignment(Alignment.CrossAxis.CENTER);
    }

    /**
     * 从 NetworkEntry 取完整 ARGB 颜色值。
     * networkID=0（未分配）时返回灰色。
     *
     * @param entry 网络条目
     * @return ARGB 颜色值
     */
    public static int entryColor(final NetworkEntry entry) {
        return entry.networkID == 0 ? Palette.COLOR_UNASSIGNED : 0xFF000000 | entry.color;
    }

    // ---- 设备类型 ----

    /** 返回设备类型的本地化显示名；未知类型保留原 simpleName。 */
    public static String deviceTypeLabel(final String simpleName) {
        final String suffix = deviceTypeSuffix(simpleName);
        return suffix == null ? (simpleName == null ? "" : simpleName)
            : tr("gui.singularityme.network_terminal.device_type." + suffix);
    }

    /** 返回设备类型在状态页中的强调色。 */
    public static int deviceTypeColor(final String simpleName) {
        final String suffix = deviceTypeSuffix(simpleName);
        if (suffix == null) return Palette.TEXT_MUTED;
        return switch (suffix) {
            case "power_core" -> Palette.SECURITY_ENCRYPTED;
            case "drive" -> Palette.ACCESS_MEMBER;
            case "terminal", "crafting_terminal", "pattern_terminal", "network_terminal" -> Palette.ACCESS_ADMIN;
            case "storage_bus", "import_bus", "export_bus", "interface" -> Palette.BTN_NORMAL;
            case "crafting_core" -> Palette.SECURITY_PRIVATE;
            case "probe" -> Palette.ACCESS_NONE;
            default -> Palette.TEXT_MUTED;
        };
    }

    private static String deviceTypeSuffix(final String simpleName) {
        if (simpleName == null) return null;
        return switch (simpleName) {
            case "TileSingularityStorageBus" -> "storage_bus";
            case "TileSingularityImportBus" -> "import_bus";
            case "TileSingularityExportBus" -> "export_bus";
            case "TileSingularityInterface" -> "interface";
            case "TileSingularityTerminal" -> "terminal";
            case "TileSingularityCraftingTerminal" -> "crafting_terminal";
            case "TileSingularityPatternTerminal" -> "pattern_terminal";
            case "TileSingularityNetworkTerminal" -> "network_terminal";
            case "TileSingularityDrive" -> "drive";
            case "TileSingularityPowerCore" -> "power_core";
            case "TileSingularityCraftingCore" -> "crafting_core";
            case "TileSingularityProbe" -> "probe";
            default -> null;
        };
    }

    // ---- 安全级别 ----

    /** 返回安全级别的完整显示名（用于按钮文字、readOnly 字段）。 */
    public static String securityName(final SecurityLevel security) {
        return switch (security) {
            case PUBLIC -> tr("gui.singularityme.network_tab.security.public");
            case ENCRYPTED -> tr("gui.singularityme.network_tab.security.encrypted");
            case PRIVATE -> tr("gui.singularityme.network_tab.security.private");
        };
    }

    /** 返回安全级别的完整显示名（从 NetworkEntry 读取）。 */
    public static String securityName(final NetworkEntry entry) {
        return securityName(SecurityLevel.fromOrdinal(entry.securityOrdinal));
    }

    /** 返回安全级别的简短标签（用于行内 badge）。 */
    public static String securityShort(final NetworkEntry entry) {
        return switch (SecurityLevel.fromOrdinal(entry.securityOrdinal)) {
            case PUBLIC -> tr("gui.singularityme.network_tab.security.public_short");
            case ENCRYPTED -> tr("gui.singularityme.network_tab.security.encrypted_short");
            case PRIVATE -> tr("gui.singularityme.network_tab.security.private_short");
        };
    }

    /** 返回安全级别对应的 badge 背景色（从 SecurityLevel 枚举读取）。 */
    public static int securityColor(final SecurityLevel security) {
        return switch (security) {
            case PUBLIC -> Palette.SECURITY_PUBLIC;
            case ENCRYPTED -> Palette.SECURITY_ENCRYPTED;
            case PRIVATE -> Palette.SECURITY_PRIVATE;
        };
    }

    /** 返回安全级别对应的 badge 背景色（从 NetworkEntry 读取）。 */
    public static int securityColor(final NetworkEntry entry) {
        return securityColor(SecurityLevel.fromOrdinal(entry.securityOrdinal));
    }

    // ---- 访问级别 ----

    /** 返回访问级别的完整显示名（用于 Info 面板）。 */
    public static String accessName(final NetworkEntry entry) {
        return roleName(AccessLevel.fromOrdinal(entry.accessLevelOrdinal));
    }

    /** 返回访问级别的完整显示名（从 AccessLevel 枚举读取，用于成员行 badge）。 */
    public static String roleName(final AccessLevel role) {
        return switch (role) {
            case OWNER -> tr("gui.singularityme.network_terminal.access.owner");
            case ADMIN -> tr("gui.singularityme.network_terminal.access.admin");
            case MEMBER -> tr("gui.singularityme.network_terminal.access.member");
            case BLOCKED -> tr("gui.singularityme.network_terminal.access.blocked");
            case NONE -> tr("gui.singularityme.network_terminal.access.none");
        };
    }

    /** 返回访问级别的简短标记（用于行内 badge，如 "(O)"）。 */
    public static String accessMark(final NetworkEntry entry) {
        return switch (AccessLevel.fromOrdinal(entry.accessLevelOrdinal)) {
            case OWNER -> tr("gui.singularityme.network_tab.access.owner_mark");
            case ADMIN -> tr("gui.singularityme.network_tab.access.admin_mark");
            case MEMBER -> tr("gui.singularityme.network_tab.access.member_mark");
            case BLOCKED -> tr("gui.singularityme.network_tab.access.blocked_mark");
            case NONE -> tr("gui.singularityme.network_tab.access.none_mark");
        };
    }

    /** 返回访问级别的简短标签（用于行内 badge，如 "O"）。 */
    public static String accessShort(final NetworkEntry entry) {
        return switch (AccessLevel.fromOrdinal(entry.accessLevelOrdinal)) {
            case OWNER -> tr("gui.singularityme.network_tab.access.owner_short");
            case ADMIN -> tr("gui.singularityme.network_tab.access.admin_short");
            case MEMBER -> tr("gui.singularityme.network_tab.access.member_short");
            case BLOCKED -> tr("gui.singularityme.network_tab.access.blocked_short");
            case NONE -> tr("gui.singularityme.network_tab.access.none_short");
        };
    }

    /** 返回访问级别对应的 badge 背景色（从 NetworkEntry 读取）。 */
    public static int accessColor(final NetworkEntry entry) {
        return accessColor(AccessLevel.fromOrdinal(entry.accessLevelOrdinal));
    }

    /** 返回访问级别对应的 badge 背景色（从 AccessLevel 枚举读取）。 */
    public static int accessColor(final AccessLevel role) {
        return switch (role) {
            case OWNER -> Palette.ACCESS_OWNER;
            case ADMIN -> Palette.ACCESS_ADMIN;
            case MEMBER -> Palette.ACCESS_MEMBER;
            case BLOCKED -> Palette.ACCESS_BLOCKED;
            case NONE -> Palette.ACCESS_NONE;
        };
    }

    // ---- 权限判断 ----

    /**
     * 判断玩家是否可访问该网络（非 BLOCKED 且 access != NONE，或 PUBLIC 网络）。
     *
     * @param entry 网络条目
     * @return 是否可访问
     */
    public static boolean canAccess(final NetworkEntry entry) {
        final AccessLevel access = AccessLevel.fromOrdinal(entry.accessLevelOrdinal);
        if (access == AccessLevel.BLOCKED) return false;
        if (access != AccessLevel.NONE) return true;
        return SecurityLevel.fromOrdinal(entry.securityOrdinal) == SecurityLevel.PUBLIC;
    }

    /**
     * 判断是否需要输入密码才能加入（ENCRYPTED 且 access==NONE）。
     *
     * @param entry 网络条目
     * @return 是否需要密码加入
     */
    public static boolean isEncryptedJoinRequired(final NetworkEntry entry) {
        return entry.networkID != 0 && SecurityLevel.fromOrdinal(entry.securityOrdinal) == SecurityLevel.ENCRYPTED
            && AccessLevel.fromOrdinal(entry.accessLevelOrdinal) == AccessLevel.NONE;
    }

    /**
     * 判断玩家是否被该网络封禁。
     *
     * @param entry 网络条目
     * @return 是否被封禁
     */
    public static boolean isBlocked(final NetworkEntry entry) {
        return AccessLevel.fromOrdinal(entry.accessLevelOrdinal) == AccessLevel.BLOCKED;
    }

    // ---- i18n 工具 ----

    /** 翻译 lang key。 */
    public static String tr(final String key) {
        return StatCollector.translateToLocal(key);
    }

    /** 翻译带参数的 lang key。 */
    public static String trf(final String key, final Object... args) {
        return StatCollector.translateToLocalFormatted(key, args);
    }

    // ---- 密码掩码工具（无 GUI 框架依赖）----

    /** 密码掩码字符 */
    public static final char MASK_CHAR = '\u25CF';

    /**
     * 将真实密码转换为等长掩码字符串。
     *
     * @param realValue 真实密码
     * @return 等长 ● 字符串
     */
    public static String maskPassword(final String realValue) {
        if (realValue == null || realValue.isEmpty()) return "";
        final int len = realValue.codePointCount(0, realValue.length());
        final StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) sb.append(MASK_CHAR);
        return sb.toString();
    }
}

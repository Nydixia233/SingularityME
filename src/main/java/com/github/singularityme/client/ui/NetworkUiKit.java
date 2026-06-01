package com.github.singularityme.client.ui;

import net.minecraft.util.StatCollector;

import com.github.singularityme.core.AccessLevel;
import com.github.singularityme.core.SecurityLevel;
import com.github.singularityme.network.packet.PacketNetworkTabData.NetworkEntry;

/**
 * 网络 UI 公共工具类，供 {@link NetworkTabUI} 和 {@link NetworkTerminalUI} 共用。
 *
 * <p>
 * 包含：颜色/尺寸常量（{@link Palette}）、颜色计算、安全/访问级别展示、
 * 权限判断、i18n 工具。零 GUI 框架依赖，纯逻辑层。
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
        final int r = Math.max(0, Math.min(255, (int) (((color >> 16) & 0xFF) * factor)));
        final int g = Math.max(0, Math.min(255, (int) (((color >> 8) & 0xFF) * factor)));
        final int b = Math.max(0, Math.min(255, (int) ((color & 0xFF) * factor)));
        return 0xFF000000 | r << 16 | g << 8 | b;
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

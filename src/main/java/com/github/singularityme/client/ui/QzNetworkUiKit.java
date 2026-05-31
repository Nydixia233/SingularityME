package com.github.singularityme.client.ui;

import net.minecraft.util.StatCollector;

import org.lwjgl.input.Keyboard;

import com.github.singularityme.core.AccessLevel;
import com.github.singularityme.core.SecurityLevel;
import com.github.singularityme.network.packet.PacketNetworkTabData.NetworkEntry;

import club.heiqi.uilib.ui.control.DocumentTextInputControl;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.style.cascade.UiStyleDeclaration;
import club.heiqi.uilib.ui.style.cascade.UiStyleSheet;
import club.heiqi.uilib.ui.style.props.UiAlignItems;
import club.heiqi.uilib.ui.style.props.UiBorderStyle;
import club.heiqi.uilib.ui.style.props.UiBoxSizing;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiFontWeight;
import club.heiqi.uilib.ui.style.props.UiJustifyContent;
import club.heiqi.uilib.ui.style.values.UiBoxShadow;
import club.heiqi.uilib.ui.style.values.UiStyleLength;
import club.heiqi.uilib.ui.style.values.UiTransform;

/**
 * 网络 UI 公共工具类，供 {@link QzNetworkTabScreens} 和 {@link QzNetworkTerminalScreens} 共用。
 *
 * <p>
 * 包含：颜色/尺寸常量（{@link Palette}）、DOM 构建工具、NetworkEntry 展示逻辑、i18n 工具。
 * </p>
 */
public final class QzNetworkUiKit {

    private QzNetworkUiKit() {}

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

    // ---- DOM 构建工具 ----

    /**
     * 创建颜色色块元素。
     *
     * @param document 所属文档
     * @param color    ARGB 颜色值
     * @param size     宽高（正方形）
     * @return 色块元素
     */
    public static ElementNode colorSwatch(final UiDocument document, final int color, final int size) {
        final ElementNode swatch = document.div();
        swatch.style()
            .setWidth(UiStyleLength.px(size))
            .setHeight(UiStyleLength.px(size))
            .setFlexShrink(0.0F)
            .setBackgroundColor(color)
            .setBorderWidth(UiStyleLength.px(1))
            .setBorderStyle(UiBorderStyle.SOLID)
            .setBorderColor(Palette.BORDER_SWATCH)
            .setBorderRadius(UiStyleLength.px(Palette.BORDER_RADIUS_SWATCH));
        return swatch;
    }

    /**
     * 创建文字徽章元素。
     *
     * @param document 所属文档
     * @param text     徽章文字
     * @param color    背景色（ARGB）
     * @return 徽章元素
     */
    public static ElementNode badge(final UiDocument document, final String text, final int color) {
        final ElementNode badge = document.div();
        badge.appendText(text);
        badge.style()
            .setMinWidth(UiStyleLength.px(Palette.BADGE_MIN_W))
            .setHeight(UiStyleLength.px(Palette.BADGE_H))
            .setPaddingLeft(UiStyleLength.px(Palette.BADGE_PADDING_H))
            .setPaddingRight(UiStyleLength.px(Palette.BADGE_PADDING_H))
            .setBackgroundColor(color)
            .setTextColor(Palette.TEXT_BADGE)
            .setFontWeight(UiFontWeight.BOLD)
            .setBorderRadius(UiStyleLength.px(Palette.BORDER_RADIUS_BADGE))
            .setDisplay(UiDisplay.FLEX)
            .setAlignItems(UiAlignItems.CENTER)
            .setJustifyContent(UiJustifyContent.CENTER)
            .setFlexShrink(0.0F);
        return badge;
    }

    /**
     * 创建网络 ID 胶囊元素（显示 "#N" 或 "-"）。
     *
     * @param document  所属文档
     * @param networkID 网络 ID，0 时显示 "-"
     * @return 胶囊元素
     */
    public static ElementNode idPill(final UiDocument document, final int networkID) {
        final ElementNode pill = document.div();
        pill.appendText(networkID == 0 ? "-" : "#" + networkID);
        pill.style()
            .setWidth(UiStyleLength.px(Palette.ID_PILL_W))
            .setHeight(UiStyleLength.px(Palette.ID_PILL_H))
            .setDisplay(UiDisplay.FLEX)
            .setAlignItems(UiAlignItems.CENTER)
            .setJustifyContent(UiJustifyContent.CENTER)
            .setBoxSizing(UiBoxSizing.BORDER_BOX)
            .setBackgroundColor(Palette.BG_ID_PILL)
            .setBorderRadius(UiStyleLength.px(Palette.BORDER_RADIUS_BADGE))
            .setTextColor(Palette.TEXT_MUTED);
        return pill;
    }

    // ---- 共享样式表 ----

    /**
     * 为文档安装网络 UI 组件级样式表，提供行悬浮高亮、色块悬浮放大等交互反馈。
     *
     * <p>
     * 样式表规则只补充内联样式未覆盖的属性（box-shadow、transform），
     * 不会覆盖 Java 代码中通过 {@code .style()} 设置的动态颜色和布局。
     * </p>
     *
     * @param document 目标文档
     */
    public static void installComponentStyleSheet(final UiDocument document) {
        document.addStyleSheet(
            UiStyleSheet.create()
                // 网络行悬浮：内阴影高亮
                .addRule(
                    ".net-row:hover",
                    new UiStyleDeclaration().setBoxShadow(UiBoxShadow.inset(0, 0, 0, 1, 0x18FFFFFF)))
                // 成员行悬浮：内阴影高亮
                .addRule(
                    ".member-row:hover",
                    new UiStyleDeclaration().setBoxShadow(UiBoxShadow.inset(0, 0, 0, 1, 0x18FFFFFF)))
                // 导航按钮悬浮：内阴影浅色叠加（不覆盖内联 bg）
                .addRule(
                    ".nav-btn:hover",
                    new UiStyleDeclaration().setBoxShadow(UiBoxShadow.inset(0, 0, 0, 999, 0x0DFFFFFF)))
                // 可点击色块悬浮：放大
                .addRule(
                    ".swatch-btn:hover",
                    new UiStyleDeclaration().setTransform(UiTransform.of(0.0F, 0.0F, 1.22F, 1.22F, 0.0F))));
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
        return switch (AccessLevel.fromOrdinal(entry.accessLevelOrdinal)) {
            case OWNER -> tr("gui.singularityme.network_terminal.access.owner");
            case ADMIN -> tr("gui.singularityme.network_terminal.access.admin");
            case MEMBER -> tr("gui.singularityme.network_terminal.access.member");
            case BLOCKED -> tr("gui.singularityme.network_terminal.access.blocked");
            case NONE -> tr("gui.singularityme.network_terminal.access.none");
        };
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

    // ---- 键盘工具 ----

    /**
     * 判断按键事件是否为提交键（Enter / 小键盘 Enter）。
     *
     * @param keyCode 键码
     * @param action  按键动作
     * @return 是否为提交键
     */
    public static boolean isSubmitKey(final int keyCode, final UiKeyEvent.Action action) {
        return action == UiKeyEvent.Action.PRESSED
            && (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER);
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

    // ---- 密码掩码输入 ----

    /**
     * 密码掩码输入包装器。
     *
     * <p>
     * 内部持有真实密码字符串，对外显示等长 {@code ●}。
     * 通过 {@link #getRealValue()} 获取真实密码用于提交。
     * 调用 {@link #clear()} 清空密码。
     * </p>
     *
     * <p>
     * 用法：
     * 
     * <pre>
     * 
     * MaskedInput masked = MaskedInput.wrap(input);
     * // 提交时：
     * String password = masked.getRealValue();
     * </pre>
     * </p>
     */
    public static final class MaskedInput {

        private static final char MASK_CHAR = '\u25CF';
        private static final String MASK_CHAR_STR = String.valueOf(MASK_CHAR);

        private final DocumentTextInputControl control;
        private String realValue = "";
        private boolean updating = false;

        private MaskedInput(final DocumentTextInputControl control) {
            this.control = control;
            control.setChangeHandler(event -> {
                if (updating) return;
                final String newText = event.getText() == null ? "" : event.getText();
                final int oldMaskLength = maskLength();
                if (newText.length() < oldMaskLength && isMaskPrefix(newText, newText.length())) {
                    realValue = truncateToCodePoints(realValue, newText.length());
                } else if (isMaskPrefix(newText, oldMaskLength)) {
                    realValue = realValue + newText.substring(oldMaskLength);
                } else {
                    realValue = newText;
                }
                syncDisplay();
            });
        }

        /**
         * 把一个已有的 {@link DocumentTextInputControl} 包装成密码输入框。
         *
         * @param control 要包装的输入控件
         * @return 密码掩码包装器
         */
        public static MaskedInput wrap(final DocumentTextInputControl control) {
            return new MaskedInput(control);
        }

        /** 返回用户实际输入的密码（未掩码）。 */
        public String getRealValue() {
            return realValue;
        }

        /** 清空密码（同时清空控件显示）。 */
        public void clear() {
            realValue = "";
            syncDisplay();
        }

        /** 返回密码是否为空。 */
        public boolean isEmpty() {
            return realValue.isEmpty();
        }

        private void syncDisplay() {
            updating = true;
            try {
                control.setText(repeat(MASK_CHAR_STR, maskLength()));
            } finally {
                updating = false;
            }
        }

        private int maskLength() {
            return realValue.codePointCount(0, realValue.length());
        }

        private static boolean isMaskPrefix(final String text, final int length) {
            if (text == null || length < 0 || text.length() < length) return false;
            for (int i = 0; i < length; i++) {
                if (text.charAt(i) != MASK_CHAR) return false;
            }
            return true;
        }

        private static String truncateToCodePoints(final String value, final int count) {
            if (value == null || value.isEmpty() || count <= 0) return "";
            final int available = value.codePointCount(0, value.length());
            if (count >= available) return value;
            return value.substring(0, value.offsetByCodePoints(0, count));
        }

        private static String repeat(final String s, final int count) {
            if (count <= 0) return "";
            final StringBuilder sb = new StringBuilder(count);
            for (int i = 0; i < count; i++) sb.append(s);
            return sb.toString();
        }
    }
}

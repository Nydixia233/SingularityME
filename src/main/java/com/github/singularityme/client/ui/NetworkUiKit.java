package com.github.singularityme.client.ui;

import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.Circle;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.github.singularityme.core.AccessLevel;
import com.github.singularityme.core.SecurityLevel;
import com.github.singularityme.network.packet.PacketNetworkTabData.NetworkEntry;

import java.util.function.Consumer;
import java.util.function.IntConsumer;

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

    /**
     * 列表选中态使用低饱和强调色，避免整行高亮过亮。
     *
     * @param color ARGB 或 RGB 颜色值
     * @return 适合整行背景的 ARGB 颜色
     */
    public static int selectedRowColor(final int color) {
        return darken(0xFF000000 | color, 0.32f);
    }

    /**
     * 将颜色格式化为 6 位大写 RGB 文本。
     *
     * @param color ARGB 或 RGB 颜色值
     * @return 6 位 RGB 十六进制字符串
     */
    public static String rgbHex(final int color) {
        return String.format("%06X", color & 0xFFFFFF);
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

    // ---- 徽章 / ID 胶囊组件 ----

    /** 构建彩色圆角徽章。 */
    @SuppressWarnings("unchecked")
    public static Flow badge(final String text, final int bgColor) {
        return Flow.row()
            .coverChildrenHeight()
            .padding(3, Palette.BADGE_PADDING_H)
            .background(new Rectangle().cornerRadius(Palette.BORDER_RADIUS_BADGE).color(bgColor))
            .crossAxisAlignment(Alignment.CrossAxis.CENTER)
            .child(new TextWidget(IKey.str(text)).color(Palette.TEXT_BADGE));
    }

    /** 构建 ID 胶囊（如 #1、#42）。 */
    @SuppressWarnings("unchecked")
    public static Flow idPill(final int networkID) {
        return Flow.row()
            .width(Palette.ID_PILL_W).height(Palette.ID_PILL_H)
            .mainAxisAlignment(Alignment.MainAxis.CENTER)
            .crossAxisAlignment(Alignment.CrossAxis.CENTER)
            .background(new Rectangle().cornerRadius(Palette.BORDER_RADIUS_BADGE).color(Palette.BG_ID_PILL))
            .child(new TextWidget(IKey.str("#" + networkID)).color(Palette.TEXT_MUTED));
    }

    /** 构建安全级别徽章。 */
    public static Flow securityBadge(final NetworkEntry entry) {
        final int color = 0xFF000000 | securityColor(entry);
        return badge(securityShort(entry), color);
    }

    /** 构建访问级别徽章。 */
    public static Flow accessBadge(final NetworkEntry entry) {
        final int color = 0xFF000000 | accessColor(entry);
        return badge(accessShort(entry), color);
    }

    /** 构建当前网络标记 "*"。 */
    public static Flow currentBadge() {
        return badge("*", Palette.BADGE_CURRENT);
    }

    /** 构建默认网络标记 "D"。 */
    public static Flow defaultBadge() {
        return badge(defaultBadgeText(), Palette.BADGE_DEFAULT);
    }

    /** 返回默认网络徽章文本。 */
    public static String defaultBadgeText() {
        return tr("gui.singularityme.network_terminal.badge.default");
    }

    // ---- 布局辅助组件（对齐 HTML 参考样式）----

    /** 构建小型状态圆点组件，用于替代语义不明的方块。 */
    @SuppressWarnings("unchecked")
    public static Flow statusDotWidget(final int color) {
        return Flow.row()
            .width(10).height(10)
            .background(Styles.statusDot(0xFF000000 | color));
    }

    /**
     * 构建选中摘要栏（对应 HTML .selection-summary）。
     * 以选中网络的标识色作为左侧 accent，浅色背景铺底。
     */
    @SuppressWarnings("unchecked")
    public static Flow selectionBar(final String text, final int accentColor) {
        final Flow bar = Flow.row()
            .childPadding(8).widthRel(1f).height(Palette.ROW_H).padding(0, 10)
            .crossAxisAlignment(Alignment.CrossAxis.CENTER)
            .background(Styles.listBg());
        // 左侧色条作为视觉 accent
        final Flow accent = Flow.row()
            .width(3).heightRel(0.6f)
            .background(new Rectangle().cornerRadius(2).color(0xFF000000 | accentColor));
        bar.child(accent);
        bar.child(new TextWidget(IKey.str(text)).color(Palette.TEXT_SECONDARY));
        return bar;
    }

    /**
     * 构建固定高度信息行（对应 HTML .info-row，36px 高度）。
     * 与 {@code fixedRow} 不同，本方法附加背景色和边框色营造卡片行效果。
     */
    @SuppressWarnings("unchecked")
    public static Flow infoRowFixed(final String label, final String value) {
        return infoRowFixed(label, value, Palette.TEXT_PRIMARY);
    }

    /** 构建固定高度信息行，可自定义值颜色。 */
    @SuppressWarnings("unchecked")
    public static Flow infoRowFixed(final String label, final String value, final int valueColor) {
        final Flow row = Flow.row()
            .childPadding(8).widthRel(1f).height(Palette.ROW_H).padding(0, 10)
            .crossAxisAlignment(Alignment.CrossAxis.CENTER)
            .background(Styles.rowBg(Palette.BG_ROW));
        final TextWidget labelWidget = new TextWidget(IKey.str(label + ":")).color(Palette.TEXT_LABEL);
        labelWidget.width(92);
        row.child(labelWidget);
        final TextWidget val = new TextWidget(IKey.str(value)).color(valueColor);
        val.expanded();
        row.child(val);
        return row;
    }

    /**
     * 构建表单项行（对应 HTML .field-row），标签固定宽度右对齐。
     */
    @SuppressWarnings("unchecked")
    public static Flow formRow(final String label, final IWidget input) {
        final Flow row = Flow.row()
            .childPadding(8).widthRel(1f).height(Palette.ROW_H).padding(0, 12)
            .crossAxisAlignment(Alignment.CrossAxis.CENTER);
        final TextWidget labelWidget = new TextWidget(IKey.str(label)).color(Palette.TEXT_LABEL);
        labelWidget.width(76);
        row.child(labelWidget);
        row.child(input);
        return row;
    }

    /** 构建颜色只读字段，显示状态圆点和 #RRGGBB。 */
    @SuppressWarnings("unchecked")
    public static Flow colorReadonly(final int color) {
        return Flow.row()
            .childPadding(6).height(Palette.ROW_H).expanded()
            .padding(0, 8)
            .crossAxisAlignment(Alignment.CrossAxis.CENTER)
            .background(Styles.inputBg())
            .child(statusDotWidget(color))
            .child(new TextWidget(IKey.str("#" + rgbHex(color))).color(0xFF000000 | color));
    }

    /** 构建安全级别分段控件。 */
    @SuppressWarnings("unchecked")
    public static Flow securitySegmentRow(final SecurityLevel selected,
        final Consumer<SecurityLevel> onSelect) {
        final Flow row = Flow.row()
            .childPadding(4).height(Palette.ROW_H).expanded()
            .padding(3, 4)
            .crossAxisAlignment(Alignment.CrossAxis.CENTER)
            .background(Styles.inputBg());
        for (final SecurityLevel level : SecurityLevel.values()) {
            row.child(securitySegment(selected, level, () -> onSelect.accept(level)));
        }
        return row;
    }

    /** 构建单个安全级别分段按钮。 */
    public static ButtonWidget<?> securitySegment(final SecurityLevel selected, final SecurityLevel option,
        final Runnable action) {
        final boolean active = selected == option;
        final ButtonWidget<?> button = new ButtonWidget<>()
            .overlay(IKey.str(securityChoiceName(option)))
            .width(62).height(Palette.ID_PILL_H)
            .background(active ? Styles.rowBg(securityColor(option)) : IDrawable.NONE)
            .disableHoverBackground()
            .onMousePressed(mb -> {
                action.run();
                return true;
            });
        return button;
    }

    /** 返回表单分段控件中的安全级别名称。 */
    public static String securityChoiceName(final SecurityLevel security) {
        return switch (security) {
            case PUBLIC -> tr("gui.singularityme.network_terminal.security.public");
            case ENCRYPTED -> tr("gui.singularityme.network_terminal.security.encrypted");
            case PRIVATE -> tr("gui.singularityme.network_terminal.security.private");
        };
    }

    /**
     * 构建色块行（对应 HTML .color-palette）。
     * 每个色块是一个小型 ButtonWidget，选中时放大以示区别。
     */
    @SuppressWarnings("unchecked")
    public static Flow colorSwatchRow(final int[] presets, final int selectedColor,
        final IntConsumer onSelect) {
        final Flow row = Flow.row()
            .childPadding(6).widthRel(1f).height(Palette.ROW_H).padding(0, 12)
            .crossAxisAlignment(Alignment.CrossAxis.CENTER);
        for (final int color : presets) {
            final boolean selected = (selectedColor & 0xFFFFFF) == (color & 0xFFFFFF);
            final int swatchSize = selected ? 26 : 22;
            row.child(new ButtonWidget<>()
                .width(swatchSize).height(swatchSize)
                .background(selected ? Styles.rowBg(lighten(color, 0.18f)) : Styles.swatch(color))
                .disableHoverBackground()
                .onMousePressed(mb -> {
                    onSelect.accept(color & 0xFFFFFF);
                    return true;
                }));
        }
        return row;
    }
}

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.style.props.*;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

/**
 * Qz 布局预览 V2 —— 匹配游戏 QzNetworkTerminalScreens 实际 UI 结构。
 *
 * <p>改进：
 * <ul>
 *   <li>使用游戏实际调色板（QzNetworkUiKit.Palette）</li>
 *   <li>单层 scrollBox（FlexLayoutHelper 已修复 flexGrow 判断）</li>
 *   <li>包含色块、徽章、ID 胶囊、安全/访问指示器</li>
 *   <li>10 行网络数据测试滚动行为</li>
 * </ul>
 *
 * <p>用法: javac -cp qz_uilib.jar LayoutPreview.java LayoutVisualizer.java
 *          java  -cp qz_uilib.jar;log4j-api.jar;. LayoutPreview</p>
 */
public class LayoutPreview {

    // ============ 游戏实际调色板 (QzNetworkUiKit.Palette) ============
    // 背景
    static final int BG_PANEL     = 0xEE141923;
    static final int BG_LIST      = 0xFF0D1219;
    static final int BG_ROW       = 0xFF151D27;
    static final int BG_INPUT     = 0xFF0D1117;
    static final int BG_OVERLAY   = 0xAA05070B;
    static final int BG_ID_PILL   = 0xFF0B1016;
    // 边框
    static final int BORDER_PANEL     = 0xFF536B7F;
    static final int BORDER_LIST      = 0xFF2C3846;
    static final int BORDER_ROW       = 0xFF263240;
    static final int BORDER_INPUT     = 0xFF354451;
    static final int BORDER_SWATCH    = 0xFF0A0D10;
    // 文字
    static final int TEXT_PRIMARY     = 0xFFEAF2F8;
    static final int TEXT_SECONDARY   = 0xFFD7E0EA;
    static final int TEXT_MUTED       = 0xFF9FB0BF;
    static final int TEXT_EMPTY       = 0xFF8996A3;
    static final int TEXT_BADGE       = 0xFFFFFFFF;
    // 按钮
    static final int BTN_NORMAL       = 0xFF2F6F95;
    static final int BTN_ACTIVE       = 0xFF255878;
    static final int BTN_DANGER       = 0xFF8C3D3D;
    // 安全级别
    static final int SEC_PUBLIC       = 0xFF2F8C52;
    static final int SEC_ENCRYPTED    = 0xFFC48A32;
    static final int SEC_PRIVATE      = 0xFF7055A8;
    // 访问级别
    static final int ACCESS_OWNER     = 0xFF3D78C2;
    static final int ACCESS_ADMIN     = 0xFF5E7FC7;
    static final int ACCESS_MEMBER    = 0xFF4D8F8A;
    static final int ACCESS_BLOCKED   = 0xFF9A3C3C;
    static final int ACCESS_NONE      = 0xFF5D6875;
    // 徽章
    static final int BADGE_DEFAULT    = 0xFF446EAA;
    static final int BADGE_CURRENT    = 0xFF2F8C52;

    // 尺寸常量
    static final int ROW_H = 36, RADIUS_PANEL = 6, RADIUS_ROW = 4, RADIUS_BADGE = 3, RADIUS_SWATCH = 2;

    // 网络数据（匹配 HTML 原型 MOCK_NETWORKS）
    static final int NET_COUNT = 5;
    static final int[] NET_IDS    = {1, 2, 3, 4, 5};
    static final int[] NET_COLORS = {0xFF4A90E2, 0xFFE2A84A, 0xFF4AE24A, 0xFF7B68EE, 0xFFE24A4A};
    static final String[] NET_NAMES = {"主基地网络", "采矿前哨", "公共交易站", "实验室网络", "被封锁网络"};
    static final String[] NET_SEC_LABELS = {"私", "密", "公", "私", "密"};
    static final int[]    NET_SEC_COLORS = {SEC_PRIVATE, SEC_ENCRYPTED, SEC_PUBLIC, SEC_PRIVATE, SEC_ENCRYPTED};
    static final String[] NET_ACC_LABELS = {"O", "-", "M", "A", "B"};
    static final int[]    NET_ACC_COLORS = {ACCESS_OWNER, ACCESS_NONE, ACCESS_MEMBER, ACCESS_ADMIN, ACCESS_BLOCKED};

    public static void main(String[] args) {
        UiDocument doc = UiDocument.create();
        buildTerminalUI(doc);

        int[][] sizes = {{427, 240}, {854, 480}, {1280, 720}, {2560, 1440}};
        LayoutVisualizer.visualize(doc.getRootElement(), sizes,
                "docs/html-reference/qz-layout-terminal.html");
        System.out.println("Wrote: docs/html-reference/qz-layout-terminal.html");
        System.out.println("Done. Open in browser — use tabs to switch viewport sizes.");
    }

    // ============ 构建 UI 树 ============

    static void buildTerminalUI(UiDocument doc) {
        ElementNode root = doc.getRootElement();
        s(root).setDisplay(UiDisplay.FLEX).setAlignItems(UiAlignItems.CENTER)
                .setJustifyContent(UiJustifyContent.CENTER)
                .setWidth(pct(1)).setHeight(pct(1))
                .setOverflowX(UiOverflow.HIDDEN).setOverflowY(UiOverflow.HIDDEN)
                .setBackgroundColor(BG_OVERLAY);

        // ---- frame ----
        ElementNode frame = d(doc);
        s(frame).setBoxSizing(UiBoxSizing.BORDER_BOX)
                .setWidth(pct(0.92F)).setMaxWidth(px(760))
                .setHeight(pct(0.92F)).setMaxHeight(px(520))
                .setDisplay(UiDisplay.FLEX).setFlexDirection(UiFlexDirection.COLUMN)
                .setOverflowX(UiOverflow.HIDDEN).setOverflowY(UiOverflow.HIDDEN)
                .setBackgroundColor(BG_PANEL)
                .setBorderWidth(px(1)).setBorderStyle(UiBorderStyle.SOLID)
                .setBorderColor(BORDER_PANEL).setBorderRadius(px(RADIUS_PANEL));
        root.append(frame);

        // ---- nav bar ----
        ElementNode nav = d(doc);
        s(nav).setDisplay(UiDisplay.FLEX).setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.CENTER).setFlexShrink(0)
                .setBackgroundColor(BG_LIST)
                .setBorderWidth(px(1)).setBorderStyle(UiBorderStyle.SOLID)
                .setBorderColor(BORDER_LIST).setBorderRadius(px(RADIUS_ROW))
                .setBoxSizing(UiBoxSizing.BORDER_BOX)
                .setMargin(px(8)).setPadding(px(4)).setGap(px(4));
        frame.append(nav);
        String[] tabIcons = {"🧭 ", "🏷️ ", "⚙️ ", "📄 ", "✨ "};
        String[] tabs = {"网络选择", "成员管理", "网络设置", "网络信息", "创建网络"};
        for (int i = 0; i < tabs.length; i++) {
            ElementNode btn = d(doc); btn.appendText(tabIcons[i] + tabs[i]);
            s(btn).setHeight(px(30))
                    .setPadding(px(5)).setPaddingLeft(px(10)).setPaddingRight(px(10))
                    .setBorderRadius(px(RADIUS_ROW))
                    .setTextColor(i == 0 ? TEXT_PRIMARY : TEXT_MUTED)
                    .setFontWeight(UiFontWeight.BOLD);
            if (i == 0) s(btn).setBackgroundColor(BTN_ACTIVE);
            nav.append(btn);
        }

        // ---- content ----
        ElementNode content = d(doc);
        s(content).setFlexGrow(1).setFlexShrink(1).setFlexBasis(px(0))
                .setMinHeight(px(0))
                .setDisplay(UiDisplay.FLEX).setFlexDirection(UiFlexDirection.COLUMN)
                .setOverflowX(UiOverflow.HIDDEN).setOverflowY(UiOverflow.HIDDEN)
                .setBoxSizing(UiBoxSizing.BORDER_BOX);
        frame.append(content);

        // ---- networkBar ----
        ElementNode bar = d(doc);
        s(bar).setDisplay(UiDisplay.FLEX).setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.CENTER).setFlexShrink(0)
                .setBoxSizing(UiBoxSizing.BORDER_BOX)
                .setPadding(px(6)).setPaddingLeft(px(12)).setPaddingRight(px(12))
                .setGap(px(8));
        content.append(bar);
        // 标题
        ElementNode title = d(doc); title.appendText("网络选择");
        s(title).setFlexGrow(1).setMinWidth(px(0))
                .setTextColor(TEXT_PRIMARY).setFontWeight(UiFontWeight.BOLD)
                .setWhiteSpace(UiWhiteSpace.NOWRAP)
                .setOverflowX(UiOverflow.HIDDEN).setOverflowY(UiOverflow.HIDDEN)
                .setTextOverflow(UiTextOverflow.ELLIPSIS);
        bar.append(title);
        // 色块
        ElementNode swatch = colorSwatch(doc, NET_COLORS[0], 10);
        bar.append(swatch);
        // 名称
        ElementNode nameLabel = d(doc); nameLabel.appendText(NET_NAMES[0]);
        s(nameLabel).setTextColor(NET_COLORS[0])
                .setWhiteSpace(UiWhiteSpace.NOWRAP)
                .setOverflowX(UiOverflow.HIDDEN).setOverflowY(UiOverflow.HIDDEN)
                .setTextOverflow(UiTextOverflow.ELLIPSIS);
        bar.append(nameLabel);
        // ID 胶囊
        bar.append(idPill(doc, 1));
        // 默认徽章
        bar.append(badge(doc, "D", BADGE_DEFAULT));

        // ---- listMeta ----
        ElementNode meta = d(doc);
        s(meta).setDisplay(UiDisplay.FLEX).setFlexDirection(UiFlexDirection.ROW)
                .setJustifyContent(UiJustifyContent.SPACE_BETWEEN)
                .setAlignItems(UiAlignItems.CENTER).setFlexShrink(0)
                .setBoxSizing(UiBoxSizing.BORDER_BOX)
                .setPadding(px(4)).setPaddingLeft(px(12)).setPaddingRight(px(12));
        content.append(meta);
        ElementNode sortLabel = d(doc); sortLabel.appendText("按 名称 排序");
        s(sortLabel).setTextColor(TEXT_MUTED);
        meta.append(sortLabel);
        ElementNode countLabel = d(doc); countLabel.appendText("共 " + NET_COUNT + " 个网络");
        s(countLabel).setTextColor(TEXT_MUTED);
        meta.append(countLabel);

        // ---- scrollBox (单层，依赖 FlexLayoutHelper flexGrow 修复) ----
        ElementNode scrollBox = d(doc);
        s(scrollBox).setFlexGrow(1).setFlexShrink(1).setFlexBasis(px(0))
                .setMinHeight(px(120))
                .setHeight(UiStyleLength.auto())
                .setOverflowX(UiOverflow.HIDDEN).setOverflowY(UiOverflow.SCROLL)
                .setScrollbarWidth(UiScrollbarWidth.THIN)
                .setBoxSizing(UiBoxSizing.BORDER_BOX)
                .setPaddingLeft(px(12)).setPaddingRight(px(18))
                .setPaddingTop(px(6)).setPaddingBottom(px(6));
        content.append(scrollBox);

        // 网络列表
        ElementNode netList = d(doc);
        s(netList).setDisplay(UiDisplay.FLEX).setFlexDirection(UiFlexDirection.COLUMN)
                .setBackgroundColor(BG_LIST)
                .setBorderWidth(px(1)).setBorderStyle(UiBorderStyle.SOLID)
                .setBorderColor(BORDER_LIST).setBorderRadius(px(RADIUS_ROW));
        scrollBox.append(netList);

        for (int i = 0; i < NET_COUNT; i++) {
            boolean selected = (i == 0);
            int color = NET_COLORS[i];
            ElementNode row = d(doc);
            s(row).setBoxSizing(UiBoxSizing.BORDER_BOX)
                    .setDisplay(UiDisplay.FLEX).setAlignItems(UiAlignItems.CENTER)
                    .setHeight(px(ROW_H)).setPadding(px(6)).setGap(px(8))
                    .setBackgroundColor(selected ? darken(color, 0.32F) : BG_ROW)
                    .setBorderWidth(px(1)).setBorderStyle(UiBorderStyle.SOLID)
                    .setBorderColor(selected ? color : BORDER_ROW);
            // 色块 14px
            row.append(colorSwatch(doc, color, 14));
            // ID 胶囊
            row.append(idPill(doc, NET_IDS[i]));
            // 名称
            ElementNode nm = d(doc); nm.appendText(NET_NAMES[i]);
            s(nm).setFlexGrow(1).setMinWidth(px(0))
                    .setTextColor(selected ? TEXT_BADGE : TEXT_SECONDARY)
                    .setWhiteSpace(UiWhiteSpace.NOWRAP)
                    .setOverflowX(UiOverflow.HIDDEN).setOverflowY(UiOverflow.HIDDEN)
                    .setTextOverflow(UiTextOverflow.ELLIPSIS);
            row.append(nm);
            // 安全徽章
            if (NET_SEC_LABELS[i] != null && !NET_SEC_LABELS[i].isEmpty()) {
                row.append(badge(doc, NET_SEC_LABELS[i], NET_SEC_COLORS[i]));
            }
            // 访问徽章
            row.append(badge(doc, NET_ACC_LABELS[i], NET_ACC_COLORS[i]));
            // 默认标记 (只有网络1有)
            if (i == 0) row.append(badge(doc, "D", BADGE_DEFAULT));
            netList.append(row);
        }

        // ---- selectionSummary ----
        ElementNode summary = d(doc);
        s(summary).setDisplay(UiDisplay.FLEX).setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.CENTER).setFlexShrink(0)
                .setBoxSizing(UiBoxSizing.BORDER_BOX)
                .setMargin(px(6)).setMarginLeft(px(12)).setMarginRight(px(12))
                .setPadding(px(8)).setGap(px(8))
                .setBackgroundColor(BG_LIST)
                .setBorderWidth(px(1)).setBorderStyle(UiBorderStyle.SOLID)
                .setBorderColor(NET_COLORS[0])
                .setBorderRadius(px(RADIUS_ROW));
        content.append(summary);
        ElementNode selText = d(doc);
        selText.appendText("已选中：#1 " + NET_NAMES[0] + " 私有 (O)");
        s(selText).setFlexGrow(1).setMinWidth(px(0))
                .setTextColor(TEXT_SECONDARY)
                .setWhiteSpace(UiWhiteSpace.NOWRAP)
                .setOverflowX(UiOverflow.HIDDEN).setOverflowY(UiOverflow.HIDDEN)
                .setTextOverflow(UiTextOverflow.ELLIPSIS);
        summary.append(selText);

        // ---- actions ----
        ElementNode actions = d(doc);
        s(actions).setDisplay(UiDisplay.FLEX).setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.CENTER).setFlexShrink(0)
                .setFlexWrap(UiFlexWrap.WRAP)
                .setBoxSizing(UiBoxSizing.BORDER_BOX)
                .setPaddingLeft(px(12)).setPaddingRight(px(12)).setPaddingBottom(px(10))
                .setGap(px(8));
        content.append(actions);
        // 按钮（匹配 HTML 原型："清除默认"）
        ElementNode btn = d(doc); btn.appendText("清除默认");
        s(btn).setHeight(px(ROW_H)).setPaddingLeft(px(14)).setPaddingRight(px(14))
                .setBackgroundColor(BTN_NORMAL).setTextColor(TEXT_BADGE)
                .setFontWeight(UiFontWeight.BOLD).setBorderRadius(px(RADIUS_ROW));
        actions.append(btn);
    }

    // ============ 组件工厂 ============

    static ElementNode colorSwatch(UiDocument doc, int color, int size) {
        ElementNode sw = d(doc);
        s(sw).setWidth(px(size)).setHeight(px(size))
                .setFlexShrink(0)
                .setBackgroundColor(color)
                .setBorderWidth(px(1)).setBorderStyle(UiBorderStyle.SOLID)
                .setBorderColor(BORDER_SWATCH)
                .setBorderRadius(px(RADIUS_SWATCH));
        return sw;
    }

    static ElementNode badge(UiDocument doc, String text, int bgColor) {
        ElementNode b = d(doc); b.appendText(text);
        s(b).setHeight(px(22)).setMinWidth(px(24)).setFlexShrink(0)
                .setDisplay(UiDisplay.FLEX)
                .setAlignItems(UiAlignItems.CENTER)
                .setJustifyContent(UiJustifyContent.CENTER)
                .setPaddingLeft(px(5)).setPaddingRight(px(5))
                .setBackgroundColor(bgColor).setTextColor(TEXT_BADGE)
                .setFontWeight(UiFontWeight.BOLD)
                .setBorderRadius(px(RADIUS_BADGE));
        return b;
    }

    static ElementNode idPill(UiDocument doc, int id) {
        ElementNode p = d(doc);
        p.appendText("#" + id);
        s(p).setWidth(px(48)).setHeight(px(22)).setFlexShrink(0)
                .setDisplay(UiDisplay.FLEX)
                .setAlignItems(UiAlignItems.CENTER)
                .setJustifyContent(UiJustifyContent.CENTER)
                .setBackgroundColor(BG_ID_PILL).setTextColor(TEXT_MUTED)
                .setBorderRadius(px(RADIUS_BADGE));
        return p;
    }

    /** 压暗颜色 (模拟游戏 darken 函数) */
    static int darken(int color, float factor) {
        int a = (color >> 24) & 0xFF;
        int r = (int) (((color >> 16) & 0xFF) * (1f - factor));
        int g = (int) (((color >> 8) & 0xFF) * (1f - factor));
        int b = (int) ((color & 0xFF) * (1f - factor));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // ============ 快捷方法 ============

    static ElementNode d(UiDocument doc) { return doc.div(); }
    static club.heiqi.uilib.ui.style.cascade.UiStyleDeclaration s(ElementNode e) { return e.style(); }
    static UiStyleLength px(int v) { return UiStyleLength.px(v); }
    static UiStyleLength pct(float v) { return UiStyleLength.percent(v); }
}

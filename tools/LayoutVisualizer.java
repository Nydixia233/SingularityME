import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.layout.DocumentLayoutBox;
import club.heiqi.uilib.ui.layout.DocumentLayoutEdges;
import club.heiqi.uilib.ui.layout.DocumentLayoutEngine;
import club.heiqi.uilib.ui.text.TextMeasureService;

/**
 * Qz-UILib 布局可视化工具 V2 —— 借鉴游戏实际渲染，提供盒模型分层、文本内容、类型着色、
 * 溢出警告、多视口切换等功能。
 *
 * <p>独立工具，只需 Qz-UILib JAR + log4j-api JAR 即可运行。</p>
 */
public class LayoutVisualizer {

    private static final TextMeasureService FIXED_MEASURE = new TextMeasureService() {
        @Override public int getEpoch() { return 0; }
        @Override public int getLineHeight() { return 10; }
        @Override public int getStringWidth(String text) {
            return text == null ? 0 : Math.max(1, text.length() * 6);
        }
        @Override public String trimStringToWidth(String text, int targetWidth) {
            if (text == null) return "";
            int chars = Math.max(1, targetWidth / 6);
            return text.length() <= chars ? text : text.substring(0, chars);
        }
        @Override public List<String> listFormattedStringToWidth(String text, int wrapWidth) {
            if (text == null || text.isEmpty()) return Collections.emptyList();
            List<String> lines = new ArrayList<>();
            int charsPerLine = Math.max(1, wrapWidth / 6);
            for (int i = 0; i < text.length(); i += charsPerLine) {
                lines.add(text.substring(i, Math.min(i + charsPerLine, text.length())));
            }
            return lines;
        }
    };

    /**
     * 布局可视化入口（多视口版本）。生成包含全部视口的单页 HTML，支持标签切换。
     */
    public static void visualize(ElementNode root, int[][] viewportSizes, String outputPath) {
        DocumentLayoutBox[] boxes = new DocumentLayoutBox[viewportSizes.length];
        for (int i = 0; i < viewportSizes.length; i++) {
            boxes[i] = DocumentLayoutEngine.layout(root, viewportSizes[i][0], viewportSizes[i][1], FIXED_MEASURE);
        }
        try (PrintWriter w = new PrintWriter(new FileWriter(outputPath))) {
            writeHtml(w, viewportSizes, boxes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** 单视口便捷方法 */
    public static void visualize(ElementNode root, int vpW, int vpH, String outputPath) {
        visualize(root, new int[][]{{vpW, vpH}}, outputPath);
    }

    // ==================== HTML 输出 ====================

    private static void writeHtml(PrintWriter w, int[][] sizes, DocumentLayoutBox[] boxes) {
        w.println("<!DOCTYPE html><html lang=\"zh\"><head><meta charset=\"UTF-8\">");
        w.println("<title>Qz Layout Visualizer</title>");
        writeCss(w);
        w.println("</head><body>");
        writeLegend(w);
        if (sizes.length > 1) writeTabs(w, sizes);
        for (int i = 0; i < sizes.length; i++) {
            int vw = sizes[i][0], vh = sizes[i][1];
            w.printf("<div class=\"%s\" id=\"vp%d\">%n", i == 0 ? "qz-viewport active" : "qz-viewport", i);
            w.printf("<div class=\"qz-canvas\" style=\"width:%dpx;height:%dpx;\">%n", vw, vh);
            writeBox(w, boxes[i]);
            w.println("</div>");
            w.printf("<div class=\"qz-vp-label\">%d × %d</div>%n", vw, vh);
            w.println("</div>");
        }
        w.println("<div class=\"qz-infobar\">Qz Layout Visualizer v2 | 悬浮=详情 | 黄=margin 蓝=border 绿=padding 红点=content | ⚠=溢出 | ↕=滚动 ⇲=flex</div>");
        if (sizes.length > 1) writeJs(w);
        w.println("</body></html>");
    }

    // ==================== CSS ====================

    private static void writeCss(PrintWriter w) {
        w.println("<style>");
        w.println("*{margin:0;padding:0;box-sizing:border-box;}");
        w.println("body{background:#0A0D10;font:11px 'Segoe UI',system-ui,sans-serif;color:#AFC0CC;}");

        // 视口
        w.println(".qz-viewport{display:none;position:relative;margin:16px auto 8px;width:fit-content;}");
        w.println(".qz-viewport.active{display:block;}");
        w.println(".qz-canvas{position:relative;background:#111;overflow:hidden;}");
        w.println(".qz-vp-label{position:absolute;top:2px;left:50%;transform:translateX(-50%);");
        w.println("  background:rgba(0,0,0,0.8);color:#888;font-size:10px;padding:1px 6px;border-radius:3px;pointer-events:none;}");

        // 盒模型
        w.println(".qz-box{position:absolute;font-size:0;line-height:1;}");
        w.println(".qz-box:hover{z-index:999!important;}");
        w.println(".qz-box:hover>.qz-info{display:block!important;}");
        w.println(".qz-box:hover>.qz-overflow-warn{display:none!important;}");

        w.println(".qz-margin{position:absolute;background:rgba(255,200,0,0.06);outline:1px dashed rgba(255,200,0,0.18);pointer-events:none;}");
        w.println(".qz-border-layer{position:absolute;background:rgba(80,160,255,0.05);outline:1px solid rgba(80,160,255,0.2);pointer-events:none;}");
        w.println(".qz-padding{position:absolute;background:rgba(80,255,80,0.04);outline:1px dashed rgba(80,255,80,0.12);pointer-events:none;}");
        w.println(".qz-content{position:absolute;outline:1px dotted rgba(255,80,80,0.3);pointer-events:none;}");

        // 文本
        w.println(".qz-text{position:absolute;font-size:10px;line-height:1.3;color:rgba(200,215,230,0.65);");
        w.println("  white-space:nowrap;overflow:hidden;text-overflow:ellipsis;pointer-events:none;}");

        // 标签
        w.println(".qz-label{position:absolute;top:0;left:1px;white-space:nowrap;");
        w.println("  background:rgba(0,0,0,0.82);padding:0 5px;border-radius:2px;font-size:9px;z-index:2;max-width:95%;overflow:hidden;text-overflow:ellipsis;}");

        // 浮层
        w.println(".qz-info{display:none;position:absolute;bottom:100%;left:0;min-width:180px;max-width:340px;");
        w.println("  background:rgba(10,14,18,0.96);border:1px solid rgba(255,255,255,0.18);border-radius:5px;");
        w.println("  padding:7px 9px;font-size:10px;color:#ccc;z-index:1000;pointer-events:none;line-height:1.55;}");

        // 溢出警告
        w.println(".qz-overflow-warn{display:none;position:absolute;bottom:0;right:2px;color:rgba(255,70,70,0.8);font-size:14px;font-weight:bold;pointer-events:none;z-index:1;}");
        w.println(".qz-overflowing>.qz-overflow-warn{display:block;}");

        // 类型着色
        w.println(".qz-scroll>.qz-border-layer{outline:2px dashed rgba(255,70,70,0.5)!important;}");
        w.println(".qz-scroll>.qz-label{color:#f88!important;}");
        w.println(".qz-btn>.qz-label{color:#8cf!important;}");
        w.println(".qz-flex>.qz-label::before{content:'↔ ';color:#6af;}");

        // 图例
        w.println(".qz-legend{position:fixed;top:8px;right:8px;background:rgba(18,24,30,0.93);");
        w.println("  border:1px solid rgba(255,255,255,0.1);border-radius:6px;padding:8px 10px;font-size:10px;z-index:10000;line-height:1.65;}");
        w.println(".qz-legend b{color:#ddd;font-size:11px;}");
        w.println(".qz-legend i{display:inline-block;width:10px;height:10px;margin-right:4px;vertical-align:middle;border-radius:1px;}");
        w.println(".qz-legend .lmg{background:rgba(255,200,0,0.35);outline:1px dashed rgba(255,200,0,0.5);}");
        w.println(".qz-legend .lbd{background:rgba(80,160,255,0.3);outline:1px solid rgba(80,160,255,0.55);}");
        w.println(".qz-legend .lpd{background:rgba(80,255,80,0.25);outline:1px dashed rgba(80,255,80,0.35);}");
        w.println(".qz-legend .lct{outline:1px dotted rgba(255,80,80,0.45);}");
        w.println(".qz-legend .lsc{outline:2px dashed rgba(255,70,70,0.55);}");

        // 标签页
        w.println(".qz-tabs{display:flex;gap:4px;justify-content:center;margin:14px 0 0;}");
        w.println(".qz-tab-btn{padding:4px 16px;border:1px solid rgba(255,255,255,0.12);border-radius:4px;");
        w.println("  background:rgba(255,255,255,0.03);color:#999;font-size:11px;cursor:pointer;font-family:inherit;}");
        w.println(".qz-tab-btn:hover{background:rgba(255,255,255,0.07);color:#ccc;}");
        w.println(".qz-tab-btn.active{background:rgba(80,160,255,0.18);border-color:rgba(80,160,255,0.35);color:#acf;}");

        // 底部信息
        w.println(".qz-infobar{position:fixed;bottom:4px;right:8px;color:#555;font-size:9px;pointer-events:none;}");
        w.println("</style>");
    }

    // ==================== 图例 ====================

    private static void writeLegend(PrintWriter w) {
        w.println("<div class=\"qz-legend\">");
        w.println("<b>📐 盒模型图例</b><br>");
        w.println("<i class=\"lmg\"></i> 外边距 margin<br>");
        w.println("<i class=\"lbd\"></i> 边框 border<br>");
        w.println("<i class=\"lpd\"></i> 内边距 padding<br>");
        w.println("<i class=\"lct\"></i> 内容区 content<br>");
        w.println("<i class=\"lsc\"></i> 滚动容器<br>");
        w.println("<span style=\"color:#f88;\">⚠</span> 内容溢出容器<br>");
        w.println("<span style=\"color:#8cf;\">btn</span> 按钮元素<br>");
        w.println("<span style=\"color:#6af;\">↔</span> Flex 容器<br>");
        w.println("<span style=\"color:#f88;\">↕</span> 滚动容器<br>");
        w.println("</div>");
    }

    // ==================== 标签页 ====================

    private static void writeTabs(PrintWriter w, int[][] sizes) {
        w.println("<div class=\"qz-tabs\">");
        for (int i = 0; i < sizes.length; i++) {
            w.printf("<button class=\"%s\" onclick=\"switchTab(%d)\">%d × %d</button>%n",
                    i == 0 ? "qz-tab-btn active" : "qz-tab-btn", i, sizes[i][0], sizes[i][1]);
        }
        w.println("</div>");
    }

    // ==================== 盒子递归 ====================

    private static void writeBox(PrintWriter w, DocumentLayoutBox box) {
        int l = box.getLeft(), t = box.getTop();
        int wPx = Math.max(1, box.getWidth()), hPx = Math.max(1, box.getHeight());

        ElementNode el = box.getElement();
        String tag = el != null ? el.getTagName() : "?";

        // 跳过 Qz 内部匿名 flex 包装元素
        if (tag.startsWith("qz-")) {
            List<DocumentLayoutBox> children = box.getChildren();
            if (children != null) {
                for (DocumentLayoutBox child : children) writeBox(w, child);
            }
            return;
        }

        String text = collectText(el);

        DocumentLayoutEdges margin  = box.getMargin();
        DocumentLayoutEdges border  = box.getBorder();
        DocumentLayoutEdges padding = box.getPadding();

        boolean isScroll = hasOverflowScroll(box);
        boolean isFlex   = isFlexContainer(box);
        boolean isBtn    = isButtonLike(box, text);
        String cls = (isScroll ? " qz-scroll" : "") + (isFlex ? " qz-flex" : "") + (isBtn ? " qz-btn" : "");
        boolean overflowing = isScroll && hasOverflowingContent(box);

        w.printf("<div class=\"qz-box%s%s\" style=\"left:%dpx;top:%dpx;width:%dpx;height:%dpx\">%n",
                cls, overflowing ? " qz-overflowing" : "", l, t, wPx, hPx);

        // 外边距
        int mw = wPx + margin.getHorizontal(), mh = hPx + margin.getVertical();
        if (hasEdge(margin)) {
            w.printf("<div class=\"qz-margin\" style=\"left:%dpx;top:%dpx;width:%dpx;height:%dpx\"></div>%n",
                    -margin.getLeft(), -margin.getTop(), Math.max(1, mw), Math.max(1, mh));
        }

        // 边框层
        w.printf("<div class=\"qz-border-layer\" style=\"left:0;top:0;width:%dpx;height:%dpx\"></div>%n", wPx, hPx);

        // 内边距
        if (hasEdge(padding)) {
            w.printf("<div class=\"qz-padding\" style=\"left:%dpx;top:%dpx;width:%dpx;height:%dpx\"></div>%n",
                    border.getLeft(), border.getTop(),
                    Math.max(1, wPx - border.getHorizontal()), Math.max(1, hPx - border.getVertical()));
        }

        // 内容区
        int cx = box.getContentLeft() - l, cy = box.getContentTop() - t;
        int cw = Math.max(1, box.getContentWidth()), ch = Math.max(1, box.getContentHeight());
        w.printf("<div class=\"qz-content\" style=\"left:%dpx;top:%dpx;width:%dpx;height:%dpx\"></div>%n", cx, cy, cw, ch);

        // 文本
        if (!text.isEmpty()) {
            int tw = Math.min(cw - 2, wPx - cx - 2);
            if (tw > 12) {
                w.printf("<div class=\"qz-text\" style=\"left:%dpx;top:%dpx;width:%dpx;height:%dpx\">%s</div>%n",
                        cx + 1, cy + 1, tw, Math.min(ch, 15), escHtml(text));
            }
        }

        // 标签
        String extra = "";
        if (isScroll) extra += " ↕"; else if (isFlex) extra += " ⇲";
        if (overflowing) extra += " ⚠";
        w.printf("<div class=\"qz-label\">&lt;%s&gt;%s %d×%d</div>%n", escHtml(tag), extra, wPx, hPx);

        if (overflowing) w.println("<div class=\"qz-overflow-warn\">⚠</div>");

        // 浮层
        writeInfoPopup(w, box, tag, text, isScroll, isFlex, isBtn, overflowing);

        // 子元素
        List<DocumentLayoutBox> children = box.getChildren();
        if (children != null) {
            for (DocumentLayoutBox child : children) {
                writeBox(w, child);
            }
        }

        w.println("</div>");
    }

    // ==================== 浮层信息 ====================

    private static void writeInfoPopup(PrintWriter w, DocumentLayoutBox box, String tag, String text,
                                        boolean isScroll, boolean isFlex, boolean isBtn, boolean overflow) {
        DocumentLayoutEdges m = box.getMargin();
        DocumentLayoutEdges b = box.getBorder();
        DocumentLayoutEdges p = box.getPadding();

        StringBuilder sb = new StringBuilder();
        sb.append("&lt;").append(escHtml(tag)).append("&gt;  ")
          .append(box.getWidth()).append("×").append(box.getHeight()).append(" (border-box)");
        if (!text.isEmpty()) {
            String t = text.length() > 40 ? text.substring(0, 37) + "…" : text;
            sb.append("<br>📝 \"").append(escHtml(t)).append("\"");
        }
        sb.append("<br>───");
        sb.append("<br>📍 left=").append(box.getLeft()).append(" top=").append(box.getTop());
        sb.append("<br>📐 margin:  ").append(edgeStr(m));
        sb.append("<br>🟦 border:  ").append(edgeStr(b));
        sb.append("<br>🟩 padding: ").append(edgeStr(p));
        sb.append("<br>📦 content: ").append(box.getContentWidth()).append("×").append(box.getContentHeight());
        if (isScroll) sb.append("<br>🔄 overflow-y: scroll");
        if (isFlex) sb.append("<br>📏 display: flex");
        if (isBtn) sb.append("<br>🔘 button-like");
        if (overflow) sb.append("<br>⚠ 内容溢出!");

        w.printf("<div class=\"qz-info\">%s</div>%n", sb.toString());
    }

    // ==================== 工具方法 ====================

    private static String edgeStr(DocumentLayoutEdges e) {
        return "上" + e.getTop() + " 右" + e.getRight() + " 下" + e.getBottom() + " 左" + e.getLeft();
    }

    private static boolean hasEdge(DocumentLayoutEdges e) {
        return e.getTop() > 0 || e.getRight() > 0 || e.getBottom() > 0 || e.getLeft() > 0;
    }

    private static String escHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String collectText(ElementNode el) {
        if (el == null) return "";
        StringBuilder sb = new StringBuilder();
        for (club.heiqi.uilib.ui.dom.DocumentNode child : el.getChildren()) {
            if (child instanceof TextNode) {
                String t = ((TextNode) child).getText();
                if (t != null) sb.append(t);
            }
        }
        return sb.toString().trim();
    }

    private static boolean hasOverflowScroll(DocumentLayoutBox box) {
        if (box.getElement() == null) return false;
        try {
            Object ov = box.getElement().style().getOverflowY();
            return ov != null && ov.toString().contains("SCROLL");
        } catch (Exception e) { return false; }
    }

    private static boolean isFlexContainer(DocumentLayoutBox box) {
        if (box.getElement() == null) return false;
        try {
            Object d = box.getElement().style().getDisplay();
            return d != null && d.toString().contains("FLEX");
        } catch (Exception e) { return false; }
    }

    private static boolean isButtonLike(DocumentLayoutBox box, String text) {
        if (text.isEmpty() || text.length() > 8) return false;
        int h = box.getHeight();
        if (h < 26 || h > 42) return false;
        // 排除单字徽章和 ID 胶囊 (#N 格式)
        if (text.length() <= 2 && (text.startsWith("#") || text.length() == 1)) return false;
        try {
            int bg = box.getElement().style().getBackgroundColor();
            return bg != 0 && (bg & 0xFF000000) != 0;
        } catch (Exception e) { return false; }
    }

    private static boolean hasOverflowingContent(DocumentLayoutBox box) {
        List<DocumentLayoutBox> children = box.getChildren();
        if (children == null || children.isEmpty()) return false;
        int contentTop = box.getContentTop();
        int contentBottom = contentTop + box.getContentHeight();
        for (DocumentLayoutBox child : children) {
            if (child.getBottom() > contentBottom + 2) return true;
        }
        return false;
    }

    // ==================== JS ====================

    private static void writeJs(PrintWriter w) {
        w.println("<script>");
        w.println("function switchTab(i){");
        w.println("document.querySelectorAll('.qz-viewport').forEach(v=>v.classList.remove('active'));");
        w.println("document.querySelectorAll('.qz-tab-btn').forEach(b=>b.classList.remove('active'));");
        w.println("document.getElementById('vp'+i).classList.add('active');");
        w.println("document.querySelectorAll('.qz-tab-btn')[i].classList.add('active');");
        w.println("}");
        w.println("</script>");
    }
}

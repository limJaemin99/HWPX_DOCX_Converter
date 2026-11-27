package io.github.limjaemin99.hwpxdocx.internal.h2d;

import io.github.limjaemin99.hwpxdocx.internal.ConvertStats;
import io.github.limjaemin99.hwpxdocx.internal.hwpx.HwpxHeader;
import org.w3c.dom.Element;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static io.github.limjaemin99.hwpxdocx.internal.Xml.attr;
import static io.github.limjaemin99.hwpxdocx.internal.hwpx.HwpxHeader.NS_HC;
import static io.github.limjaemin99.hwpxdocx.internal.hwpx.HwpxHeader.NS_HH;
import static io.github.limjaemin99.hwpxdocx.internal.hwpx.HwpxHeader.NS_HP;
import static io.github.limjaemin99.hwpxdocx.internal.hwpx.HwpxHeader.child;
import static io.github.limjaemin99.hwpxdocx.internal.hwpx.HwpxHeader.intAttr;

/** HWPX 글자 모양·문단 모양 → DOCX w:rPr·w:pPr 안쪽 XML. */
final class DocxProps {

    // 한컴 전용(HFT) 글꼴은 Word에서 쓸 수 없어 같은 모양의 TTF 이름으로 바꿈
    private static final Map<String, String> FONT_MAP = new HashMap<String, String>();

    static {
        FONT_MAP.put("한양신명조", "HY신명조");
        FONT_MAP.put("한양견명조", "HY견명조");
        FONT_MAP.put("한양중고딕", "HY중고딕");
        FONT_MAP.put("한양견고딕", "HY견고딕");
        FONT_MAP.put("한양그래픽", "HY그래픽");
        FONT_MAP.put("한양궁서", "HY궁서");
        FONT_MAP.put("한컴바탕", "함초롬바탕");
        FONT_MAP.put("한컴돋움", "함초롬돋움");
    }

    private DocxProps() {
    }

    /** HWPUNIT(1/7200 인치) → twip(1/1440 인치). */
    static int twip(int hwpunit) {
        return Math.round(hwpunit / 5.0f);
    }

    static String font(String face) {
        String f = FONT_MAP.get(face);
        return f != null ? f : face;
    }

    static String runProps(HwpxHeader h, String charPrId, ConvertStats stats) {
        Element c = h.charPr(charPrId);
        if (c == null) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        Element fr = child(c, NS_HH, "fontRef");
        if (fr != null) {
            String ea = font(h.fontFace("HANGUL", fr.getAttribute("hangul")));
            String la = font(h.fontFace("LATIN", fr.getAttribute("latin")));
            if (!ea.isEmpty() || !la.isEmpty()) {
                b.append("<w:rFonts");
                if (!la.isEmpty()) {
                    b.append(" w:ascii=\"").append(attr(la)).append("\" w:hAnsi=\"").append(attr(la))
                            .append("\" w:cs=\"").append(attr(la)).append('"');
                }
                if (!ea.isEmpty()) {
                    b.append(" w:eastAsia=\"").append(attr(ea)).append('"');
                }
                b.append("/>");
            }
        }
        if (child(c, NS_HH, "bold") != null) {
            b.append("<w:b/><w:bCs/>");
        }
        if (child(c, NS_HH, "italic") != null) {
            b.append("<w:i/><w:iCs/>");
        }
        Element so = child(c, NS_HH, "strikeout");
        String soShape = so == null ? "NONE" : so.getAttribute("shape");
        if (!soShape.isEmpty() && !"NONE".equals(soShape) && !"3D".equals(soShape)) {
            b.append(soShape.startsWith("DOUBLE") ? "<w:dstrike/>" : "<w:strike/>");
        }
        Element outline = child(c, NS_HH, "outline");
        if (outline != null && !"NONE".equals(outline.getAttribute("type")) && !outline.getAttribute("type").isEmpty()) {
            b.append("<w:outline/>");
        }
        Element shadow = child(c, NS_HH, "shadow");
        if (shadow != null && !"NONE".equals(shadow.getAttribute("type")) && !shadow.getAttribute("type").isEmpty()) {
            b.append("<w:shadow/>");
        }
        if (child(c, NS_HH, "emboss") != null) {
            b.append("<w:emboss/>");
        }
        if (child(c, NS_HH, "engrave") != null) {
            b.append("<w:imprint/>");
        }
        String color = hex(c.getAttribute("textColor"));
        if (color != null && !"000000".equals(color)) {
            b.append("<w:color w:val=\"").append(color).append("\"/>");
        }
        int height = intAttr(c, "height", 1000);
        Element sp = child(c, NS_HH, "spacing");
        int spacing = intAttr(sp, "hangul", 0);
        if (spacing != 0) {
            // 한컴 자간은 글자 크기에 대한 %, Word는 twip
            b.append("<w:spacing w:val=\"").append(Math.round(height * spacing / 500.0f)).append("\"/>");
        }
        Element ratio = child(c, NS_HH, "ratio");
        int w = intAttr(ratio, "hangul", 100);
        if (w != 100) {
            b.append("<w:w w:val=\"").append(Math.max(1, Math.min(600, w))).append("\"/>");
        }
        Element offset = child(c, NS_HH, "offset");
        int off = intAttr(offset, "hangul", 0);
        if (off != 0) {
            // 한컴 위치는 글자 크기에 대한 %(아래가 +), Word는 반 pt(위가 +)
            b.append("<w:position w:val=\"").append(Math.round(-height * off / 5000.0f)).append("\"/>");
        }
        int sz = Math.max(1, Math.round(height / 50.0f));
        b.append("<w:sz w:val=\"").append(sz).append("\"/><w:szCs w:val=\"").append(sz).append("\"/>");
        Element ul = child(c, NS_HH, "underline");
        String ulType = ul == null ? "NONE" : ul.getAttribute("type");
        if ("BOTTOM".equals(ulType)) {
            b.append("<w:u w:val=\"").append(underline(ul.getAttribute("shape"))).append('"');
            String uc = hex(ul.getAttribute("color"));
            if (uc != null) {
                b.append(" w:color=\"").append(uc).append('"');
            }
            b.append("/>");
        } else if (!ulType.isEmpty() && !"NONE".equals(ulType)) {
            stats.warn("밑줄 위치 " + ulType + "는 Word에 없어 뺌");
        }
        String shade = hex(c.getAttribute("shadeColor"));
        if (shade != null && !"FFFFFF".equals(shade)) {
            b.append("<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"").append(shade).append("\"/>");
        }
        if (child(c, NS_HH, "supscript") != null) {
            b.append("<w:vertAlign w:val=\"superscript\"/>");
        } else if (child(c, NS_HH, "subscript") != null) {
            b.append("<w:vertAlign w:val=\"subscript\"/>");
        }
        String sym = c.getAttribute("symMark");
        if (!sym.isEmpty() && !"NONE".equals(sym)) {
            b.append("<w:em w:val=\"dot\"/>");
        }
        return b.toString();
    }

    private static String underline(String shape) {
        switch (shape) {
            case "DASH":
                return "dash";
            case "DOT":
                return "dotted";
            case "DASH_DOT":
                return "dotDash";
            case "DASH_DOT_DOT":
                return "dotDotDash";
            case "LONG_DASH":
                return "dashLong";
            case "DOUBLE_SLIM":
            case "DOUBLE":
                return "double";
            case "WAVE":
                return "wave";
            case "DOUBLE_WAVE":
                return "wavyDouble";
            case "SLIM_THICK":
            case "THICK_SLIM":
            case "SLIM_THICK_SLIM":
                return "thick";
            default:
                return "single";
        }
    }

    /** "#RRGGBB" → "RRGGBB". 색이 아니면(none 등) null. */
    static String hex(String v) {
        if (v == null || v.length() != 7 || v.charAt(0) != '#') {
            return null;
        }
        for (int i = 1; i < 7; i++) {
            if (Character.digit(v.charAt(i), 16) < 0) {
                return null;
            }
        }
        return v.substring(1).toUpperCase(Locale.ROOT);
    }

    /**
     * @param maxHeight 문단 안 가장 큰 글자 크기(1/100 pt). 비율 줄 간격의 기준
     * @param hasObject 문단에 표·그림이 있으면 줄 높이를 고정하지 않음 (잘림 방지)
     */
    static String paraProps(HwpxHeader h, String paraPrId, int maxHeight, boolean hasObject, ConvertStats stats) {
        Element p = h.paraPr(paraPrId);
        if (p == null) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        Element br = child(p, NS_HH, "breakSetting");
        if (br != null) {
            if ("1".equals(br.getAttribute("keepWithNext"))) {
                b.append("<w:keepNext/>");
            }
            if ("1".equals(br.getAttribute("keepLines"))) {
                b.append("<w:keepLines/>");
            }
            if ("1".equals(br.getAttribute("pageBreakBefore"))) {
                b.append("<w:pageBreakBefore/>");
            }
            b.append("<w:widowControl w:val=\"").append("1".equals(br.getAttribute("widowOrphan")) ? 1 : 0)
                    .append("\"/>");
        }
        Element heading = child(p, NS_HH, "heading");
        String headingType = heading == null ? "NONE" : heading.getAttribute("type");
        if (!headingType.isEmpty() && !"NONE".equals(headingType)) {
            stats.warn("문단 번호(" + headingType + ")는 아직 옮기지 않음");
        }
        Element auto = child(p, NS_HH, "autoSpacing");
        b.append("<w:autoSpaceDE w:val=\"").append(auto != null && "1".equals(auto.getAttribute("eAsianEng")) ? 1 : 0)
                .append("\"/><w:autoSpaceDN w:val=\"")
                .append(auto != null && "1".equals(auto.getAttribute("eAsianNum")) ? 1 : 0).append("\"/>");

        // 한컴 2016 이후 문서는 margin·lineSpacing을 hp:switch로 두 벌 적음.
        // case(HwpUnitChar 지원)는 실제 값, default는 옛 프로그램용으로 두 배 값
        Element margin = child(p, NS_HH, "margin");
        Element lineSpacing = child(p, NS_HH, "lineSpacing");
        int div = 1;
        Element sw = child(p, NS_HP, "switch");
        if (sw != null) {
            Element cs = child(sw, NS_HP, "case");
            Element df = child(sw, NS_HP, "default");
            if (cs != null) {
                margin = child(cs, NS_HH, "margin");
                lineSpacing = child(cs, NS_HH, "lineSpacing");
            } else if (df != null) {
                margin = child(df, NS_HH, "margin");
                lineSpacing = child(df, NS_HH, "lineSpacing");
                div = 2;
            }
        }
        int intent = marginValue(margin, "intent", div, stats);
        int left = marginValue(margin, "left", div, stats);
        int right = marginValue(margin, "right", div, stats);
        int prev = marginValue(margin, "prev", div, stats);
        int next = marginValue(margin, "next", div, stats);

        b.append("<w:spacing w:before=\"").append(twip(prev)).append("\" w:after=\"").append(twip(next)).append('"');
        if (lineSpacing != null) {
            String type = lineSpacing.getAttribute("type");
            int v = intAttr(lineSpacing, "value", 160);
            int line;
            String rule;
            if ("FIXED".equals(type)) {
                line = twip(v / div);
                rule = "exact";
            } else if ("BETWEEN_LINES".equals(type)) {
                line = Math.round(maxHeight / 5.0f) + twip(v / div);
                rule = "exact";
            } else if ("AT_LEAST".equals(type)) {
                line = twip(v / div);
                rule = "atLeast";
            } else {
                // 한컴 비율 줄 간격은 글자 크기 × 비율. Word 배수(글꼴 줄 높이 기준)와 달라 높이로 바꿔 고정함.
                // 글꼴별 보정은 따로 함
                line = Math.round(maxHeight * v / 500.0f);
                rule = hasObject ? "atLeast" : "exact";
            }
            b.append(" w:line=\"").append(Math.max(1, line)).append("\" w:lineRule=\"").append(rule).append('"');
        }
        b.append("/>");

        if (intent < 0) {
            b.append("<w:ind w:left=\"").append(twip(left - intent)).append("\" w:right=\"").append(twip(right))
                    .append("\" w:hanging=\"").append(twip(-intent)).append("\"/>");
        } else {
            b.append("<w:ind w:left=\"").append(twip(left)).append("\" w:right=\"").append(twip(right))
                    .append("\" w:firstLine=\"").append(twip(intent)).append("\"/>");
        }
        b.append("<w:jc w:val=\"").append(align(child(p, NS_HH, "align"), stats)).append("\"/>");
        return b.toString();
    }

    /**
     * 구역 설정 → w:sectPr 안의 pgSz·pgMar·pgNumType·cols (스키마 순서대로).
     * 한컴 위·아래 여백은 용지 끝에서 머리말·꼬리말까지, 본문은 그 안쪽에서 시작한다.
     * Word는 본문까지의 거리와 머리말 위치를 따로 적어서 더해 줌.
     */
    static String pageSetup(Element secPr, Element colPr) {
        Element page = child(secPr, NS_HP, "pagePr");
        Element m = child(page, NS_HP, "margin");
        // 값이 없으면 한컴 새 문서 기본값 (A4, 위 20 mm, 아래·머리말·꼬리말 15 mm, 좌우 30 mm)
        int w = intAttr(page, "width", 59528);
        int h = intAttr(page, "height", 84186);
        boolean landscape = page != null && "NARROWLY".equals(page.getAttribute("landscape"));
        if (landscape && w < h) {
            int t = w;
            w = h;
            h = t;
        }
        int top = intAttr(m, "top", 5668);
        int bottom = intAttr(m, "bottom", 4252);
        int header = intAttr(m, "header", 4252);
        int footer = intAttr(m, "footer", 4252);
        StringBuilder b = new StringBuilder();
        b.append("<w:pgSz w:w=\"").append(twip(w)).append("\" w:h=\"").append(twip(h)).append('"');
        if (landscape) {
            b.append(" w:orient=\"landscape\"");
        }
        b.append("/><w:pgMar w:top=\"").append(twip(top + header))
                .append("\" w:right=\"").append(twip(intAttr(m, "right", 8504)))
                .append("\" w:bottom=\"").append(twip(bottom + footer))
                .append("\" w:left=\"").append(twip(intAttr(m, "left", 8504)))
                .append("\" w:header=\"").append(twip(top))
                .append("\" w:footer=\"").append(twip(bottom))
                .append("\" w:gutter=\"").append(twip(intAttr(m, "gutter", 0))).append("\"/>");
        int start = intAttr(child(secPr, NS_HP, "startNum"), "page", 0);
        if (start > 0) {
            b.append("<w:pgNumType w:start=\"").append(start).append("\"/>");
        }
        int cols = intAttr(colPr, "colCount", 1);
        int gap = intAttr(colPr, "sameGap", 0);
        if (cols > 1) {
            b.append("<w:cols w:num=\"").append(cols).append("\" w:space=\"").append(twip(gap)).append("\"/>");
        } else {
            b.append("<w:cols w:space=\"").append(twip(gap)).append("\"/>");
        }
        return b.toString();
    }

    private static final String[][] SIDES = {
            {"top", "topBorder"}, {"left", "leftBorder"}, {"bottom", "bottomBorder"}, {"right", "rightBorder"}};

    /** 테두리·배경의 네 변 → {@code <w:tag>…</w:tag>} (tag는 tcBorders·tblBorders). 테두리 정의가 없으면 빈 문자열. */
    static String borders(Element borderFill, String tag) {
        if (borderFill == null) {
            return "";
        }
        StringBuilder b = new StringBuilder("<w:").append(tag).append('>');
        for (String[] s : SIDES) {
            Element e = child(borderFill, NS_HH, s[1]);
            String type = e == null ? "NONE" : e.getAttribute("type");
            if (type.isEmpty() || "NONE".equals(type)) {
                b.append("<w:").append(s[0]).append(" w:val=\"nil\"/>");
                continue;
            }
            String color = hex(e.getAttribute("color"));
            b.append("<w:").append(s[0]).append(" w:val=\"").append(borderStyle(type)).append("\" w:sz=\"")
                    .append(borderSize(e.getAttribute("width"))).append("\" w:space=\"0\" w:color=\"")
                    .append(color == null ? "000000" : color).append("\"/>");
        }
        return b.append("</w:").append(tag).append('>').toString();
    }

    /** 단색 채우기 → w:shd. 흰색·채우기 없음은 빈 문자열. */
    static String shading(Element borderFill) {
        Element fill = child(borderFill, NS_HC, "fillBrush");
        Element win = child(fill, NS_HC, "winBrush");
        String face = win == null ? null : hex(win.getAttribute("faceColor"));
        if (face == null || "FFFFFF".equals(face)) {
            return "";
        }
        return "<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"" + face + "\"/>";
    }

    // 한컴 선 굵기는 "0.12 mm" 같은 문자열, Word는 1/8 pt (2~96)
    static int borderSize(String width) {
        String num = width == null ? "" : width.replaceAll("[^0-9.]", "");
        double mm;
        try {
            mm = num.isEmpty() ? 0.12 : Double.parseDouble(num);
        } catch (NumberFormatException e) {
            mm = 0.12;
        }
        long sz = Math.round(mm / 25.4 * 72 * 8);
        return (int) Math.max(2, Math.min(96, sz));
    }

    private static String borderStyle(String type) {
        switch (type) {
            case "DASH":
            case "LONG_DASH":
                return "dashed";
            case "DOT":
            case "CIRCLE":
                return "dotted";
            case "DASH_DOT":
                return "dotDash";
            case "DASH_DOT_DOT":
                return "dotDotDash";
            case "DOUBLE_SLIM":
                return "double";
            case "SLIM_THICK":
                return "thinThickSmallGap";
            case "THICK_SLIM":
                return "thickThinSmallGap";
            case "SLIM_THICK_SLIM":
                return "thinThickThinSmallGap";
            case "WAVE":
                return "wave";
            case "DOUBLE_WAVE":
                return "doubleWave";
            default:
                return "single";
        }
    }

    private static int marginValue(Element margin, String local, int div, ConvertStats stats) {
        Element e = child(margin, NS_HC, local);
        if (e == null) {
            return 0;
        }
        String unit = e.getAttribute("unit");
        if (!unit.isEmpty() && !"HWPUNIT".equals(unit)) {
            stats.warn("문단 여백 단위 " + unit + "는 아직 옮기지 않음");
            return 0;
        }
        return intAttr(e, "value", 0) / div;
    }

    /** 문단 모양의 가로 정렬 → w:jc 값. */
    static String jc(HwpxHeader h, String paraPrId) {
        Element a = child(h.paraPr(paraPrId), NS_HH, "align");
        return jcValue(a == null ? "JUSTIFY" : a.getAttribute("horizontal"));
    }

    private static String align(Element a, ConvertStats stats) {
        String v = a == null ? "JUSTIFY" : a.getAttribute("horizontal");
        if ("DISTRIBUTE_SPACE".equals(v)) {
            // 한컴 "나눔"은 글자 사이가 아닌 낱말 사이를 벌림. Word에 같은 것이 없어 배분으로
            stats.warn("정렬 '나눔'은 Word '배분'으로 바꿈");
        }
        return jcValue(v);
    }

    private static String jcValue(String v) {
        switch (v) {
            case "LEFT":
                return "left";
            case "RIGHT":
                return "right";
            case "CENTER":
                return "center";
            case "DISTRIBUTE":
                return "distribute";
            case "DISTRIBUTE_SPACE":
                return "distribute";
            default:
                return "both";
        }
    }
}

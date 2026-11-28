package io.github.limjaemin99.hwpxdocx.internal.d2h;

import io.github.limjaemin99.hwpxdocx.internal.Xml;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 만들 HWPX의 header.xml 참조표. 글꼴·글자 모양·문단 모양은 같은 것을 한 번만 넣고 ID를 돌려준다.
 * 처음 등록한 글자·문단 모양(ID 0)이 스타일 "바탕글"이 된다.
 */
final class HwpxRefs {

    private static final String[] LANGS = {"HANGUL", "LATIN", "HANJA", "JAPANESE", "OTHER", "SYMBOL", "USER"};

    private static final Map<String, String> HIGHLIGHT = new HashMap<String, String>();

    static {
        HIGHLIGHT.put("yellow", "FFFF00");
        HIGHLIGHT.put("green", "00FF00");
        HIGHLIGHT.put("cyan", "00FFFF");
        HIGHLIGHT.put("magenta", "FF00FF");
        HIGHLIGHT.put("blue", "0000FF");
        HIGHLIGHT.put("red", "FF0000");
        HIGHLIGHT.put("darkBlue", "000080");
        HIGHLIGHT.put("darkCyan", "008080");
        HIGHLIGHT.put("darkGreen", "008000");
        HIGHLIGHT.put("darkMagenta", "800080");
        HIGHLIGHT.put("darkRed", "800000");
        HIGHLIGHT.put("darkYellow", "808000");
        HIGHLIGHT.put("darkGray", "808080");
        HIGHLIGHT.put("lightGray", "C0C0C0");
        HIGHLIGHT.put("black", "000000");
    }

    private final List<String> fonts = new ArrayList<String>();
    private final List<String> chars = new ArrayList<String>();
    private final Map<String, Integer> charIds = new HashMap<String, Integer>();
    private final List<String> paras = new ArrayList<String>();
    private final Map<String, Integer> paraIds = new HashMap<String, Integer>();
    private final List<String> borders = new ArrayList<String>();
    private final Map<String, Integer> borderIds = new HashMap<String, Integer>();
    // 되살린 원본 문단이 가리키는 탭 정의. ID 0은 기본 탭이라 1부터
    private final List<String> tabs = new ArrayList<String>();
    private final Map<String, Integer> tabIds = new HashMap<String, Integer>();

    static final String NO_LINE = "NONE";

    HwpxRefs() {
        // ID 1: 테두리·배경 없음 (글자 모양·문단 테두리가 가리킴)
        borderFill(NO_LINE, NO_LINE, NO_LINE, NO_LINE, null);
    }

    /**
     * 테두리·배경 ID (1부터).
     *
     * @param top    선 하나: {@link #NO_LINE} 또는 {@link #line} 결과
     * @param fill   채우기 색 RRGGBB, 없으면 null
     */
    int borderFill(String top, String left, String bottom, String right, String fill) {
        StringBuilder b = new StringBuilder(" threeD=\"0\" shadow=\"0\" centerLine=\"NONE\" breakCellSeparateLine=\"0\">"
                + "<hh:slash type=\"NONE\" Crooked=\"0\" isCounter=\"0\"/>"
                + "<hh:backSlash type=\"NONE\" Crooked=\"0\" isCounter=\"0\"/>");
        side(b, "leftBorder", left);
        side(b, "rightBorder", right);
        side(b, "topBorder", top);
        side(b, "bottomBorder", bottom);
        b.append("<hh:diagonal type=\"SOLID\" width=\"0.1 mm\" color=\"#000000\"/>");
        if (fill != null) {
            b.append("<hc:fillBrush><hc:winBrush faceColor=\"#").append(fill)
                    .append("\" hatchColor=\"#000000\" alpha=\"0\"/></hc:fillBrush>");
        }
        Integer id = borderIds.get(b.toString());
        if (id == null) {
            borders.add(b.toString());
            id = borders.size();
            borderIds.put(b.toString(), id);
        }
        return id;
    }

    private static void side(StringBuilder b, String tag, String line) {
        if (NO_LINE.equals(line)) {
            b.append("<hh:").append(tag).append(" type=\"NONE\" width=\"0.1 mm\" color=\"#000000\"/>");
        } else {
            b.append("<hh:").append(tag).append(' ').append(line).append("/>");
        }
    }

    // 한컴이 쓰는 선 굵기 (mm)
    private static final double[] LINE_MM = {0.1, 0.12, 0.15, 0.2, 0.25, 0.3, 0.4, 0.5, 0.6, 0.7, 1.0, 1.5, 2.0, 3.0, 4.0, 5.0};

    /**
     * Word 선(w:val·w:sz·w:color) → 한컴 선 속성. 선이 없으면 {@link #NO_LINE}.
     * Word 굵기는 1/8 pt라 mm로 바꾼 뒤 한컴이 쓰는 굵기 중 가장 가까운 값으로 맞춤.
     */
    static String line(String val, String sz, String color) {
        if (val == null || "nil".equals(val) || "none".equals(val)) {
            return NO_LINE;
        }
        double mm = (sz == null ? 4 : parse(sz, 4)) / 8.0 * 25.4 / 72;
        double best = LINE_MM[0];
        for (double c : LINE_MM) {
            if (Math.abs(c - mm) < Math.abs(best - mm)) {
                best = c;
            }
        }
        String type;
        switch (val) {
            case "dashed":
            case "dashSmallGap":
                type = "DASH";
                break;
            case "dotted":
                type = "DOT";
                break;
            case "dotDash":
                type = "DASH_DOT";
                break;
            case "dotDotDash":
                type = "DASH_DOT_DOT";
                break;
            case "double":
                type = "DOUBLE_SLIM";
                break;
            case "thinThickSmallGap":
            case "thinThickMediumGap":
            case "thinThickLargeGap":
                type = "SLIM_THICK";
                break;
            case "thickThinSmallGap":
            case "thickThinMediumGap":
            case "thickThinLargeGap":
                type = "THICK_SLIM";
                break;
            case "thinThickThinSmallGap":
            case "thinThickThinMediumGap":
            case "thinThickThinLargeGap":
                type = "SLIM_THICK_SLIM";
                break;
            case "wave":
                type = "WAVE";
                break;
            case "doubleWave":
                type = "DOUBLE_WAVE";
                break;
            default:
                type = "SOLID";
        }
        String w = best == Math.floor(best) ? String.valueOf((int) best) : String.valueOf(best);
        return "type=\"" + type + "\" width=\"" + w + " mm\" color=\"#" + color(color, "000000") + "\"";
    }

    private static double parse(String s, double def) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    int font(String face) {
        int i = fonts.indexOf(face);
        if (i < 0) {
            fonts.add(face);
            i = fonts.size() - 1;
        }
        return i;
    }

    /** 글자 모양 ID. {@code r}은 상속을 다 푼 글자 속성. */
    int charId(Props r, String eastAsiaFont, String latinFont) {
        int height = height(r);
        int fe = font(eastAsiaFont);
        int fl = font(latinFont);
        StringBuilder b = new StringBuilder();
        b.append(" height=\"").append(height).append("\" textColor=\"#").append(color(r.get("color"), "000000"))
                .append("\" shadeColor=\"").append(shade(r)).append('"')
                .append(" useFontSpace=\"0\" useKerning=\"0\" symMark=\"NONE\" borderFillIDRef=\"1\">")
                .append("<hh:fontRef hangul=\"").append(fe).append("\" latin=\"").append(fl).append("\" hanja=\"").append(fe)
                .append("\" japanese=\"").append(fe).append("\" other=\"").append(fl).append("\" symbol=\"").append(fl)
                .append("\" user=\"").append(fl).append("\"/>")
                .append(seven("ratio", clamp(r.num("w", 100), 50, 200)))
                // Word 자간은 twip, 한컴은 글자 크기에 대한 %
                .append(seven("spacing", clamp(Math.round(r.num("spacing", 0) * 500f / height), -50, 50)))
                .append(seven("relSz", 100))
                // Word 위치는 반 pt(위가 +), 한컴은 글자 크기에 대한 %(아래가 +)
                .append(seven("offset", clamp(Math.round(-r.num("position", 0) * 5000f / height), -100, 100)));
        if (r.on("b")) {
            b.append("<hh:bold/>");
        }
        if (r.on("i")) {
            b.append("<hh:italic/>");
        }
        String u = r.get("u", "none");
        boolean underline = !"none".equals(u) && !"0".equals(u);
        b.append("<hh:underline type=\"").append(underline ? "BOTTOM" : "NONE").append("\" shape=\"")
                .append(underline ? underlineShape(u) : "SOLID").append("\" color=\"#")
                .append(color(r.get("uColor"), color(r.get("color"), "000000"))).append("\"/>");
        String strike = r.on("dstrike") ? "DOUBLE_SLIM" : r.on("strike") ? "SOLID" : "NONE";
        b.append("<hh:strikeout shape=\"").append(strike).append("\" color=\"#000000\"/>");
        b.append("<hh:outline type=\"").append(r.on("outline") ? "SOLID" : "NONE").append("\"/>");
        b.append("<hh:shadow type=\"").append(r.on("shadow") ? "DROP" : "NONE")
                .append("\" color=\"#B2B2B2\" offsetX=\"10\" offsetY=\"10\"/>");
        if (r.on("emboss")) {
            b.append("<hh:emboss/>");
        }
        if (r.on("imprint")) {
            b.append("<hh:engrave/>");
        }
        String va = r.get("vertAlign", "");
        if ("superscript".equals(va)) {
            b.append("<hh:supscript/>");
        } else if ("subscript".equals(va)) {
            b.append("<hh:subscript/>");
        }
        return id(b.toString(), chars, charIds);
    }

    static int height(Props r) {
        // w:sz는 반 pt, 한컴 height는 1/100 pt
        return Math.max(100, r.num("sz", 20) * 50);
    }

    /**
     * 문단 모양 ID.
     *
     * @param lineFactor Word 한 줄 높이 ÷ 글자 크기 (글꼴마다 다름)
     */
    int paraId(Props p, double lineFactor) {
        return paraId(p, lineFactor, NO_HEADING);
    }

    static final String NO_HEADING = "<hh:heading type=\"NONE\" idRef=\"0\" level=\"0\"/>";

    /** @param heading 문단 번호 hh:heading 요소 ({@link HwpxNumbering}) */
    int paraId(Props p, double lineFactor, String heading) {
        String align;
        switch (p.get("jc", "left")) {
            case "both":
                align = "JUSTIFY";
                break;
            case "center":
                align = "CENTER";
                break;
            case "right":
            case "end":
                align = "RIGHT";
                break;
            case "distribute":
                align = "DISTRIBUTE";
                break;
            default:
                align = "LEFT";
        }
        // 단위: Word twip × 5 = HWPUNIT
        int left = p.num("left", 0);
        int intent;
        if (p.get("hanging") != null) {
            intent = -p.num("hanging", 0);
            left -= p.num("hanging", 0);
        } else {
            intent = p.num("firstLine", 0);
        }
        int right = p.num("right", 0);
        int before = p.num("before", 0);
        int after = p.num("after", 0);
        String rule = p.get("lineRule", "auto");
        int line = p.num("line", 240);
        String lsType;
        int lsValue;
        if ("exact".equals(rule)) {
            lsType = "FIXED";
            lsValue = line * 5;
        } else if ("atLeast".equals(rule)) {
            lsType = "AT_LEAST";
            lsValue = line * 5;
        } else {
            // Word 배수는 글꼴의 한 줄 높이 기준, 한컴 비율은 글자 크기 기준
            lsType = "PERCENT";
            lsValue = (int) Math.round(line / 240.0 * lineFactor * 100);
        }
        StringBuilder b = new StringBuilder();
        b.append(" tabPrIDRef=\"0\" condense=\"0\" fontLineHeight=\"0\" snapToGrid=\"")
                .append(p.on("snapToGrid", true) ? 1 : 0).append("\" suppressLineNumbers=\"0\" checked=\"0\">")
                .append("<hh:align horizontal=\"").append(align).append("\" vertical=\"BASELINE\"/>")
                .append(heading)
                .append("<hh:breakSetting breakLatinWord=\"KEEP_WORD\" breakNonLatinWord=\"")
                .append(p.on("wordWrap", true) ? "KEEP_WORD" : "BREAK_WORD")
                .append("\" widowOrphan=\"").append(p.on("widowControl") ? 1 : 0)
                .append("\" keepWithNext=\"").append(p.on("keepNext") ? 1 : 0)
                .append("\" keepLines=\"").append(p.on("keepLines") ? 1 : 0)
                .append("\" pageBreakBefore=\"").append(p.on("pageBreakBefore") ? 1 : 0)
                .append("\" lineWrap=\"BREAK\"/>")
                .append("<hh:autoSpacing eAsianEng=\"").append(p.on("autoSpaceDE", true) ? 1 : 0)
                .append("\" eAsianNum=\"").append(p.on("autoSpaceDN", true) ? 1 : 0).append("\"/>")
                // 한컴 2016 이후 형식: case는 실제 값, default는 옛 프로그램용 두 배 값 (비율 줄 간격은 그대로)
                .append("<hp:switch><hp:case hp:required-namespace=\"http://www.hancom.co.kr/hwpml/2016/HwpUnitChar\">")
                .append(margin(intent, left, right, before, after, lsType, lsValue, 1))
                .append("</hp:case><hp:default>")
                .append(margin(intent, left, right, before, after, lsType, lsValue, 2))
                .append("</hp:default></hp:switch>")
                .append("<hh:border borderFillIDRef=\"1\" offsetLeft=\"0\" offsetRight=\"0\" offsetTop=\"0\"")
                .append(" offsetBottom=\"0\" connect=\"0\" ignoreMargin=\"0\"/>");
        return id(b.toString(), paras, paraIds);
    }

    private static String margin(int intent, int left, int right, int before, int after, String lsType, int lsValue,
                                 int mul) {
        int k = 5 * mul;
        return "<hh:margin>"
                + "<hc:intent value=\"" + intent * k + "\" unit=\"HWPUNIT\"/>"
                + "<hc:left value=\"" + left * k + "\" unit=\"HWPUNIT\"/>"
                + "<hc:right value=\"" + right * k + "\" unit=\"HWPUNIT\"/>"
                + "<hc:prev value=\"" + before * k + "\" unit=\"HWPUNIT\"/>"
                + "<hc:next value=\"" + after * k + "\" unit=\"HWPUNIT\"/>"
                + "</hh:margin>"
                + "<hh:lineSpacing type=\"" + lsType + "\" value=\"" + ("PERCENT".equals(lsType) ? lsValue : lsValue * mul)
                + "\" unit=\"HWPUNIT\"/>";
    }

    // 원본 header.xml에서 옮겨 붙이는 정의. body는 id 속성 뒤의 속성들과 '>' 뒤 자식 XML

    int importChar(String body) {
        return id(body, chars, charIds);
    }

    int importPara(String body) {
        return id(body, paras, paraIds);
    }

    int importBorder(String body) {
        Integer id = borderIds.get(body);
        if (id == null) {
            borders.add(body);
            id = borders.size();
            borderIds.put(body, id);
        }
        return id;
    }

    int importTab(String body) {
        return id(body, tabs, tabIds) + 1;
    }

    private static int id(String key, List<String> list, Map<String, Integer> ids) {
        Integer id = ids.get(key);
        if (id == null) {
            id = list.size();
            list.add(key);
            ids.put(key, id);
        }
        return id;
    }

    private static String seven(String tag, int v) {
        return "<hh:" + tag + " hangul=\"" + v + "\" latin=\"" + v + "\" hanja=\"" + v + "\" japanese=\"" + v
                + "\" other=\"" + v + "\" symbol=\"" + v + "\" user=\"" + v + "\"/>";
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** Word 색(RRGGBB·auto) → RRGGBB. 색이 아니면 {@code def}. */
    static String color(String v, String def) {
        if (v == null || v.length() != 6) {
            return def;
        }
        for (int i = 0; i < 6; i++) {
            if (Character.digit(v.charAt(i), 16) < 0) {
                return def;
            }
        }
        return v.toUpperCase(Locale.ROOT);
    }

    // 형광펜과 글자 배경은 한컴에서 모두 글자 음영
    private static String shade(Props r) {
        String hl = HIGHLIGHT.get(r.get("highlight", ""));
        if (hl != null) {
            return "#" + hl;
        }
        String fill = color(r.get("shd"), null);
        return fill == null ? "none" : "#" + fill;
    }

    private static String underlineShape(String u) {
        switch (u) {
            case "dotted":
            case "dottedHeavy":
                return "DOT";
            case "dash":
            case "dashedHeavy":
                return "DASH";
            case "dashLong":
            case "dashLongHeavy":
                return "LONG_DASH";
            case "dotDash":
            case "dashDotHeavy":
                return "DASH_DOT";
            case "dotDotDash":
            case "dashDotDotHeavy":
                return "DASH_DOT_DOT";
            case "double":
                return "DOUBLE_SLIM";
            case "wave":
            case "wavyHeavy":
                return "WAVE";
            case "wavyDouble":
                return "DOUBLE_WAVE";
            default:
                return "SOLID";
        }
    }

    /** header.xml의 refList 안쪽. */
    String refList() {
        return refList("");
    }

    /** @param numbering hh:numberings·hh:bullets (tabProperties와 paraProperties 사이에 들어감) */
    String refList(String numbering) {
        StringBuilder b = new StringBuilder();
        if (fonts.isEmpty()) {
            font("함초롬바탕");
        }
        b.append("<hh:fontfaces itemCnt=\"").append(LANGS.length).append("\">");
        for (String lang : LANGS) {
            b.append("<hh:fontface lang=\"").append(lang).append("\" fontCnt=\"").append(fonts.size()).append("\">");
            for (int i = 0; i < fonts.size(); i++) {
                b.append("<hh:font id=\"").append(i).append("\" face=\"").append(Xml.attr(fonts.get(i)))
                        .append("\" type=\"TTF\" isEmbedded=\"0\"/>");
            }
            b.append("</hh:fontface>");
        }
        b.append("</hh:fontfaces>");
        b.append("<hh:borderFills itemCnt=\"").append(borders.size()).append("\">");
        for (int i = 0; i < borders.size(); i++) {
            b.append("<hh:borderFill id=\"").append(i + 1).append('"').append(borders.get(i)).append("</hh:borderFill>");
        }
        b.append("</hh:borderFills>");
        b.append("<hh:charProperties itemCnt=\"").append(chars.size()).append("\">");
        for (int i = 0; i < chars.size(); i++) {
            b.append("<hh:charPr id=\"").append(i).append('"').append(chars.get(i)).append("</hh:charPr>");
        }
        b.append("</hh:charProperties>");
        b.append("<hh:tabProperties itemCnt=\"").append(tabs.size() + 1)
                .append("\"><hh:tabPr id=\"0\" autoTabLeft=\"0\" autoTabRight=\"0\"/>");
        for (int i = 0; i < tabs.size(); i++) {
            b.append("<hh:tabPr id=\"").append(i + 1).append('"').append(tabs.get(i)).append("</hh:tabPr>");
        }
        b.append("</hh:tabProperties>");
        b.append(numbering);
        b.append("<hh:paraProperties itemCnt=\"").append(paras.size()).append("\">");
        for (int i = 0; i < paras.size(); i++) {
            b.append("<hh:paraPr id=\"").append(i).append('"').append(paras.get(i)).append("</hh:paraPr>");
        }
        b.append("</hh:paraProperties>");
        b.append("<hh:styles itemCnt=\"1\"><hh:style id=\"0\" type=\"PARA\" name=\"바탕글\" engName=\"Normal\""
                + " paraPrIDRef=\"0\" charPrIDRef=\"0\" nextStyleIDRef=\"0\" langID=\"1042\" lockForm=\"0\"/>"
                + "</hh:styles>");
        return b.toString();
    }
}

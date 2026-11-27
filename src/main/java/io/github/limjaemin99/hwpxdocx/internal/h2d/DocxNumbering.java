package io.github.limjaemin99.hwpxdocx.internal.h2d;

import io.github.limjaemin99.hwpxdocx.internal.ConvertStats;
import io.github.limjaemin99.hwpxdocx.internal.docx.DocxSkeleton;
import io.github.limjaemin99.hwpxdocx.internal.hwpx.HwpxHeader;
import org.w3c.dom.Element;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static io.github.limjaemin99.hwpxdocx.internal.Xml.attr;
import static io.github.limjaemin99.hwpxdocx.internal.hwpx.HwpxHeader.NS_HH;
import static io.github.limjaemin99.hwpxdocx.internal.hwpx.HwpxHeader.children;
import static io.github.limjaemin99.hwpxdocx.internal.hwpx.HwpxHeader.intAttr;

/**
 * 한컴 문단 번호(hh:numbering)·글머리표(hh:bullet) → word/numbering.xml.
 * 문단에서 쓴 것만 abstractNum·num 한 쌍씩 만든다. 같은 번호 모양을 쓰는 문단은 같은 num이라 번호가 이어진다.
 */
final class DocxNumbering {

    /** Word 수준은 0~8. 한컴은 10수준까지 있어 10수준은 9수준으로 붙인다. */
    private static final int MAX_LEVEL = 8;

    private static final Map<String, String> FORMATS = new HashMap<String, String>();

    static {
        FORMATS.put("DIGIT", "decimal");
        FORMATS.put("CIRCLED_DIGIT", "decimalEnclosedCircle");
        FORMATS.put("ROMAN_CAPITAL", "upperRoman");
        FORMATS.put("ROMAN_SMALL", "lowerRoman");
        FORMATS.put("LATIN_CAPITAL", "upperLetter");
        FORMATS.put("LATIN_SMALL", "lowerLetter");
        FORMATS.put("HANGUL_SYLLABLE", "ganada");
        FORMATS.put("HANGUL_JAMO", "chosung");
        FORMATS.put("IDEOGRAPH", "ideographTraditional");
    }

    private final HwpxHeader header;
    private final ConvertStats stats;
    // "N:id" 또는 "B:id" → numId. 순서대로 abstractNum을 쓴다
    private final Map<String, Integer> numIds = new LinkedHashMap<String, Integer>();
    private final Map<Integer, String> abstractNums = new LinkedHashMap<Integer, String>();

    DocxNumbering(HwpxHeader header, ConvertStats stats) {
        this.header = header;
        this.stats = stats;
    }

    /**
     * 문단 모양의 hh:heading → w:numPr. 번호가 없으면 빈 문자열.
     *
     * @param outlineId 구역 설정의 개요 번호 모양(outlineShapeIDRef). OUTLINE은 heading의 idRef 대신 이것을 씀
     */
    String numPr(Element heading, String outlineId) {
        if (heading == null) {
            return "";
        }
        String type = heading.getAttribute("type");
        int level = Math.min(Math.max(intAttr(heading, "level", 0), 0), MAX_LEVEL);
        Integer numId;
        if ("OUTLINE".equals(type)) {
            numId = number(outlineId);
        } else if ("NUMBER".equals(type)) {
            numId = number(heading.getAttribute("idRef"));
        } else if ("BULLET".equals(type)) {
            numId = bullet(heading.getAttribute("idRef"));
        } else {
            return "";
        }
        if (numId == null) {
            stats.warn("문단 번호 " + type + " " + heading.getAttribute("idRef") + "를 머리 정보에서 못 찾음");
            return "";
        }
        return "<w:numPr><w:ilvl w:val=\"" + level + "\"/><w:numId w:val=\"" + numId + "\"/></w:numPr>";
    }

    private Integer number(String id) {
        String key = "N:" + id;
        Integer numId = numIds.get(key);
        if (numId != null) {
            return numId;
        }
        Element n = header.numbering(id);
        if (n == null) {
            return null;
        }
        Map<Integer, Element> heads = heads(n);
        StringBuilder b = new StringBuilder();
        for (int lvl = 0; lvl <= MAX_LEVEL; lvl++) {
            Element h = heads.get(lvl);
            if (h == null) {
                b.append(level(lvl, 1, "decimal", "", "left"));
                continue;
            }
            String fmt = FORMATS.get(h.getAttribute("numFormat"));
            if (fmt == null) {
                stats.warn("번호 모양 " + h.getAttribute("numFormat") + "는 숫자로 바꿈");
                fmt = "decimal";
            }
            b.append(level(lvl, intAttr(h, "start", intAttr(n, "start", 1)), fmt, levelText(h.getTextContent()),
                    jc(h.getAttribute("align"))));
        }
        return add(key, b.toString());
    }

    private Integer bullet(String id) {
        String key = "B:" + id;
        Integer numId = numIds.get(key);
        if (numId != null) {
            return numId;
        }
        Element bl = header.bullet(id);
        if (bl == null) {
            return null;
        }
        if ("1".equals(bl.getAttribute("useImage"))) {
            stats.warn("그림 글머리표는 글자 글머리표로 바꿈");
        }
        String ch = bl.getAttribute("char");
        // 사용자 영역 글자(기호 글꼴)는 다른 글꼴에서 빈 칸으로 보임
        if (ch.isEmpty() || (ch.charAt(0) >= '' && ch.charAt(0) <= '')) {
            stats.warn("글머리표 글자 U+" + (ch.isEmpty() ? "" : Integer.toHexString(ch.charAt(0)).toUpperCase())
                    + "는 ●로 바꿈");
            ch = "●";
        }
        Map<Integer, Element> heads = heads(bl);
        StringBuilder b = new StringBuilder();
        for (int lvl = 0; lvl <= MAX_LEVEL; lvl++) {
            Element h = heads.get(lvl);
            b.append(level(lvl, 1, "bullet", ch, h == null ? "left" : jc(h.getAttribute("align"))));
        }
        return add(key, b.toString());
    }

    /** hh:paraHead의 level은 1부터. */
    private static Map<Integer, Element> heads(Element parent) {
        Map<Integer, Element> m = new HashMap<Integer, Element>();
        for (Element h : children(parent, NS_HH, "paraHead")) {
            int lvl = Math.min(intAttr(h, "level", 1) - 1, MAX_LEVEL);
            if (lvl >= 0 && !m.containsKey(lvl)) {
                m.put(lvl, h);
            }
        }
        return m;
    }

    private Integer add(String key, String levels) {
        int numId = numIds.size() + 1;
        numIds.put(key, numId);
        abstractNums.put(numId, levels);
        return numId;
    }

    /** 한컴 "^1." (^n = n수준 번호) → Word "%1.". ^10은 %9로. */
    static String levelText(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '^' && i + 1 < s.length() && Character.isDigit(s.charAt(i + 1))) {
                int j = i + 1;
                while (j < s.length() && Character.isDigit(s.charAt(j))) {
                    j++;
                }
                int n = Math.min(Integer.parseInt(s.substring(i + 1, Math.min(j, i + 3))), MAX_LEVEL + 1);
                b.append('%').append(Math.max(n, 1));
                i = j - 1;
            } else if (c == '%') {
                b.append("%%");
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }

    private static String jc(String align) {
        if ("CENTER".equals(align)) {
            return "center";
        }
        if ("RIGHT".equals(align)) {
            return "right";
        }
        return "left";
    }

    // 들여쓰기는 문단 쪽 w:ind가 정함 (한컴 문단 여백이 번호 자리까지 포함). 번호 뒤는 빈칸 하나
    private static String level(int lvl, int start, String fmt, String text, String jc) {
        return "<w:lvl w:ilvl=\"" + lvl + "\"><w:start w:val=\"" + start + "\"/><w:numFmt w:val=\"" + fmt + "\"/>"
                + "<w:suff w:val=\"space\"/><w:lvlText w:val=\"" + attr(text) + "\"/><w:lvlJc w:val=\"" + jc
                + "\"/></w:lvl>";
    }

    boolean isEmpty() {
        return numIds.isEmpty();
    }

    String xml() {
        StringBuilder b = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<w:numbering xmlns:w=\"").append(DocxSkeleton.NS_W).append("\">");
        for (Map.Entry<Integer, String> e : abstractNums.entrySet()) {
            b.append("<w:abstractNum w:abstractNumId=\"").append(e.getKey())
                    .append("\"><w:multiLevelType w:val=\"multilevel\"/>").append(e.getValue())
                    .append("</w:abstractNum>");
        }
        for (Integer id : abstractNums.keySet()) {
            b.append("<w:num w:numId=\"").append(id).append("\"><w:abstractNumId w:val=\"").append(id)
                    .append("\"/></w:num>");
        }
        return b.append("</w:numbering>").toString();
    }
}

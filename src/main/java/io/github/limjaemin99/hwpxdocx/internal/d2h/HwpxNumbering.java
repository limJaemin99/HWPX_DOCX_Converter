package io.github.limjaemin99.hwpxdocx.internal.d2h;

import io.github.limjaemin99.hwpxdocx.internal.ConvertStats;
import io.github.limjaemin99.hwpxdocx.internal.Xml;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.github.limjaemin99.hwpxdocx.internal.d2h.Props.NS_W;
import static io.github.limjaemin99.hwpxdocx.internal.d2h.Props.attr;
import static io.github.limjaemin99.hwpxdocx.internal.d2h.Props.child;

/**
 * Word numbering.xml → 한컴 문단 번호(hh:numbering)·글머리표(hh:bullet).
 * 문단이 쓴 것만 만든다. 번호는 abstractNum 하나에 hh:numbering 하나라 같은 abstractNum을 쓰는 문단은 번호가 이어진다
 * (Word도 같은 abstractNum이면 num이 달라도 이어짐). lvlOverride가 있는 num만 따로 만든다.
 * 한컴 글머리표는 수준마다 글자를 달리 둘 수 없어 글자마다 hh:bullet 하나를 만든다.
 */
final class HwpxNumbering {

    /** Word 수준 0~8. 한컴은 1~10수준이라 10수준은 9수준 모양을 그대로 씀. */
    private static final int WORD_LEVELS = 9;
    private static final int HWPX_LEVELS = 10;

    private static final Map<String, String> FORMATS = new HashMap<String, String>();
    // 기호 글꼴(Symbol·Wingdings)의 사용자 영역 글자 → 일반 글자. 다른 글꼴에서는 빈 칸으로 보임
    private static final Map<Character, String> SYMBOL_BULLETS = new HashMap<Character, String>();

    static {
        FORMATS.put("decimal", "DIGIT");
        FORMATS.put("decimalEnclosedCircle", "CIRCLED_DIGIT");
        FORMATS.put("decimalEnclosedCircleChinese", "CIRCLED_DIGIT");
        FORMATS.put("upperRoman", "ROMAN_CAPITAL");
        FORMATS.put("lowerRoman", "ROMAN_SMALL");
        FORMATS.put("upperLetter", "LATIN_CAPITAL");
        FORMATS.put("lowerLetter", "LATIN_SMALL");
        FORMATS.put("ganada", "HANGUL_SYLLABLE");
        FORMATS.put("chosung", "HANGUL_JAMO");
        FORMATS.put("koreanDigital", "HANGUL_PHONETIC");
        FORMATS.put("ideographDigital", "IDEOGRAPH");
        FORMATS.put("koreanDigital2", "IDEOGRAPH");
        FORMATS.put("chineseCounting", "IDEOGRAPH");
        FORMATS.put("japaneseCounting", "IDEOGRAPH");
        // 천간(甲, 乙, 丙)은 한컴 번호 모양에 없어 한자 숫자로 바꿈
        FORMATS.put("ideographTraditional", "IDEOGRAPH");
        FORMATS.put("none", "DIGIT");

        SYMBOL_BULLETS.put('', "•");
        SYMBOL_BULLETS.put('', "▪");
        SYMBOL_BULLETS.put('', "●");
        SYMBOL_BULLETS.put('', "■");
        SYMBOL_BULLETS.put('', "❑");
        SYMBOL_BULLETS.put('', "❖");
        SYMBOL_BULLETS.put('', "➢");
        SYMBOL_BULLETS.put('', "✓");
    }

    /** 한 문단에 적용할 번호: 문단 모양의 hh:heading과 그 수준의 Word 문단 속성(들여쓰기 등). */
    static final class Level {
        final String heading;
        final Element pPr;

        Level(String heading, Element pPr) {
            this.heading = heading;
            this.pPr = pPr;
        }
    }

    private final ConvertStats stats;
    private final Map<String, Element> abstractNums = new HashMap<String, Element>();
    private final Map<String, Element> styleLinks = new HashMap<String, Element>();
    private final Map<String, Element> nums = new HashMap<String, Element>();

    private final List<String> numberings = new ArrayList<String>();
    private final Map<String, Integer> numberingIds = new HashMap<String, Integer>();
    private final List<String> bullets = new ArrayList<String>();
    private final Map<String, Integer> bulletIds = new HashMap<String, Integer>();

    HwpxNumbering(DocxPackage pkg, ConvertStats stats) throws IOException {
        this.stats = stats;
        String part = pkg.related("numbering");
        if (part == null) {
            return;
        }
        Document d = pkg.parse(part);
        NodeList list = d.getElementsByTagNameNS(NS_W, "abstractNum");
        for (int i = 0; i < list.getLength(); i++) {
            Element a = (Element) list.item(i);
            abstractNums.put(a.getAttributeNS(NS_W, "abstractNumId"), a);
            String link = attr(child(a, "styleLink"), "val");
            if (link != null) {
                styleLinks.put(link, a);
            }
        }
        list = d.getElementsByTagNameNS(NS_W, "num");
        for (int i = 0; i < list.getLength(); i++) {
            Element n = (Element) list.item(i);
            nums.put(n.getAttributeNS(NS_W, "numId"), n);
        }
    }

    /** 번호가 없으면(numId 0, 정의 없음) null. */
    Level level(String numId, int ilvl) {
        if (numId == null || "0".equals(numId)) {
            return null;
        }
        Element num = nums.get(numId);
        Element abs = num == null ? null : resolve(attr(child(num, "abstractNumId"), "val"));
        if (abs == null) {
            stats.warn("자동 번호 " + numId + "를 번호 정의에서 못 찾음");
            return null;
        }
        int lvl = Math.max(0, Math.min(ilvl, WORD_LEVELS - 1));
        Element[] levels = levels(abs, num);
        Element w = levels[lvl];
        if (w == null) {
            return null;
        }
        String fmt = attr(child(w, "numFmt"), "val");
        if ("bullet".equals(fmt)) {
            int id = bullet(w);
            return new Level("<hh:heading type=\"BULLET\" idRef=\"" + id + "\" level=\"" + lvl + "\"/>", child(w, "pPr"));
        }
        boolean override = num != null && child(num, "lvlOverride") != null;
        String key = override ? "num:" + numId : "abs:" + abs.getAttributeNS(NS_W, "abstractNumId");
        Integer id = numberingIds.get(key);
        if (id == null) {
            numberings.add(numbering(levels));
            id = numberings.size();
            numberingIds.put(key, id);
        }
        return new Level("<hh:heading type=\"NUMBER\" idRef=\"" + id + "\" level=\"" + lvl + "\"/>", child(w, "pPr"));
    }

    // numStyleLink만 있는 abstractNum은 같은 이름의 styleLink를 가진 abstractNum이 실제 수준을 가짐
    private Element resolve(String abstractNumId) {
        Element a = abstractNums.get(abstractNumId);
        String link = attr(child(a, "numStyleLink"), "val");
        if (link != null && styleLinks.containsKey(link)) {
            return styleLinks.get(link);
        }
        return a;
    }

    /** 수준별 w:lvl. lvlOverride 안의 w:lvl이 통째로 바꾸고, startOverride는 시작 번호만 바꿈. */
    private static Element[] levels(Element abs, Element num) {
        Element[] out = new Element[WORD_LEVELS];
        for (Node n = abs.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (isW(n, "lvl")) {
                int i = ilvl((Element) n);
                if (i >= 0 && i < WORD_LEVELS) {
                    out[i] = (Element) n;
                }
            }
        }
        if (num == null) {
            return out;
        }
        for (Node n = num.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (!isW(n, "lvlOverride")) {
                continue;
            }
            int i = ilvl((Element) n);
            if (i < 0 || i >= WORD_LEVELS) {
                continue;
            }
            Element lvl = child((Element) n, "lvl");
            if (lvl != null) {
                out[i] = lvl;
            }
            String start = attr(child((Element) n, "startOverride"), "val");
            if (start != null && out[i] != null) {
                Element copy = (Element) out[i].cloneNode(true);
                Element s = child(copy, "start");
                if (s == null) {
                    s = copy.getOwnerDocument().createElementNS(NS_W, "w:start");
                    copy.insertBefore(s, copy.getFirstChild());
                }
                s.setAttributeNS(NS_W, "w:val", start);
                out[i] = copy;
            }
        }
        return out;
    }

    private static boolean isW(Node n, String local) {
        return n instanceof Element && local.equals(n.getLocalName()) && NS_W.equals(n.getNamespaceURI());
    }

    private static int ilvl(Element e) {
        try {
            return Integer.parseInt(e.getAttributeNS(NS_W, "ilvl"));
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private String numbering(Element[] levels) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < HWPX_LEVELS; i++) {
            Element w = levels[Math.min(i, WORD_LEVELS - 1)];
            String fmt = w == null ? "decimal" : attr(child(w, "numFmt"), "val");
            String hwpFmt = FORMATS.get(fmt == null ? "decimal" : fmt);
            if (hwpFmt == null) {
                if (!"bullet".equals(fmt)) {
                    stats.warn("번호 모양 " + fmt + "는 숫자로 바꿈");
                }
                hwpFmt = "DIGIT";
            }
            String text = w == null ? "^" + (i + 1) + "." : levelText(attr(child(w, "lvlText"), "val"));
            int start = w == null ? 1 : parseInt(attr(child(w, "start"), "val"), 1);
            b.append("<hh:paraHead start=\"").append(start).append("\" level=\"").append(i + 1)
                    .append("\" align=\"").append(align(w)).append('"')
                    .append(headAttrs(w, hwpFmt)).append('>').append(Xml.text(text)).append("</hh:paraHead>");
        }
        return b.toString();
    }

    private int bullet(Element w) {
        String ch = attr(child(w, "lvlText"), "val");
        if (child(w, "lvlPicBulletId") != null) {
            stats.warn("그림 글머리표는 글자 글머리표로 바꿈");
            ch = "●";
        } else if (ch == null || ch.isEmpty()) {
            ch = "●";
        } else {
            char c = ch.charAt(0);
            if (c >= '' && c <= '') {
                String mapped = SYMBOL_BULLETS.get(c);
                if (mapped == null) {
                    stats.warn("글머리표 글자 U+" + Integer.toHexString(c).toUpperCase() + "는 ●로 바꿈");
                    mapped = "●";
                }
                ch = mapped;
            } else {
                ch = ch.substring(0, Character.charCount(ch.codePointAt(0)));
            }
        }
        Integer id = bulletIds.get(ch);
        if (id == null) {
            bullets.add("<hh:bullet id=\"" + (bullets.size() + 1) + "\" char=\"" + Xml.attr(ch) + "\" useImage=\"0\">"
                    + "<hh:paraHead level=\"0\" align=\"" + align(w) + "\"" + headAttrs(w, "DIGIT") + "/></hh:bullet>");
            id = bullets.size();
            bulletIds.put(ch, id);
        }
        return id;
    }

    // 들여쓰기는 문단 모양 여백이 정하므로 한컴 자동 내어쓰기(autoIndent)는 끔. 번호 뒤 간격은 글자 크기의 50%
    private static String headAttrs(Element w, String fmt) {
        boolean nothing = w != null && "nothing".equals(attr(child(w, "suff"), "val"));
        return " useInstWidth=\"1\" autoIndent=\"0\" widthAdjust=\"0\" textOffsetType=\"PERCENT\" textOffset=\""
                + (nothing ? 0 : 50) + "\" numFormat=\"" + fmt + "\" charPrIDRef=\"4294967295\" checkable=\"0\"";
    }

    private static String align(Element w) {
        String jc = w == null ? null : attr(child(w, "lvlJc"), "val");
        if ("center".equals(jc)) {
            return "CENTER";
        }
        if ("right".equals(jc) || "end".equals(jc)) {
            return "RIGHT";
        }
        return "LEFT";
    }

    /** Word "%1." (%n = n수준 번호) → 한컴 "^1.". */
    static String levelText(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '%' && i + 1 < s.length() && s.charAt(i + 1) >= '1' && s.charAt(i + 1) <= '9') {
                b.append('^').append(s.charAt(i + 1));
                i++;
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }

    private static int parseInt(String s, int def) {
        if (s == null) {
            return def;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** header.xml refList 안 hh:numberings·hh:bullets. 쓴 것이 없으면 빈 문자열. */
    String refXml() {
        StringBuilder b = new StringBuilder();
        if (!numberings.isEmpty()) {
            b.append("<hh:numberings itemCnt=\"").append(numberings.size()).append("\">");
            for (int i = 0; i < numberings.size(); i++) {
                b.append("<hh:numbering id=\"").append(i + 1).append("\" start=\"0\">").append(numberings.get(i))
                        .append("</hh:numbering>");
            }
            b.append("</hh:numberings>");
        }
        if (!bullets.isEmpty()) {
            b.append("<hh:bullets itemCnt=\"").append(bullets.size()).append("\">");
            for (String s : bullets) {
                b.append(s);
            }
            b.append("</hh:bullets>");
        }
        return b.toString();
    }
}

package io.github.limjaemin99.hwpxdocx.internal.d2h;

import io.github.limjaemin99.hwpxdocx.ConvertException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.github.limjaemin99.hwpxdocx.internal.d2h.Props.NS_W;
import static io.github.limjaemin99.hwpxdocx.internal.d2h.Props.attr;
import static io.github.limjaemin99.hwpxdocx.internal.d2h.Props.child;

/** styles.xml(문서 기본값·스타일)과 테마 글꼴. 스타일 상속을 풀어 실제 값을 돌려준다. */
final class DocxStyles {

    private static final String NS_A = "http://schemas.openxmlformats.org/drawingml/2006/main";

    // 영문 글꼴 이름 → 한글판 Word·한컴에서 쓰는 이름
    private static final Map<String, String> FONT_NAME = new HashMap<String, String>();

    static {
        FONT_NAME.put("Malgun Gothic", "맑은 고딕");
        FONT_NAME.put("Batang", "바탕");
        FONT_NAME.put("BatangChe", "바탕체");
        FONT_NAME.put("Gulim", "굴림");
        FONT_NAME.put("GulimChe", "굴림체");
        FONT_NAME.put("Dotum", "돋움");
        FONT_NAME.put("DotumChe", "돋움체");
        FONT_NAME.put("Gungsuh", "궁서");
        FONT_NAME.put("HCR Batang", "함초롬바탕");
        FONT_NAME.put("HCR Dotum", "함초롬돋움");
        FONT_NAME.put("HYSMyeongJo-Medium", "HY신명조");
        FONT_NAME.put("HYGothic-Medium", "HY중고딕");
        FONT_NAME.put("HYGothic-Extra", "HY견고딕");
        FONT_NAME.put("HYMyeongJo-Extra", "HY견명조");
    }

    private Element defaultRPr;
    private Element defaultPPr;
    private final Map<String, Element> styles = new HashMap<String, Element>();
    private String defaultParagraphStyle;
    private final Map<String, String> theme = new HashMap<String, String>();

    DocxStyles(DocxPackage pkg) throws ConvertException {
        String stylesPart = pkg.related("styles");
        if (stylesPart != null) {
            Document d = pkg.parse(stylesPart);
            Element dd = first(d, "docDefaults");
            defaultRPr = child(child(dd, "rPrDefault"), "rPr");
            defaultPPr = child(child(dd, "pPrDefault"), "pPr");
            NodeList list = d.getElementsByTagNameNS(NS_W, "style");
            for (int i = 0; i < list.getLength(); i++) {
                Element s = (Element) list.item(i);
                String id = s.getAttributeNS(NS_W, "styleId");
                styles.put(id, s);
                String def = s.getAttributeNS(NS_W, "default");
                if ("paragraph".equals(s.getAttributeNS(NS_W, "type")) && ("1".equals(def) || "true".equals(def))) {
                    defaultParagraphStyle = id;
                }
            }
        }
        String themePart = pkg.related("theme");
        if (themePart != null) {
            readTheme(pkg.parse(themePart));
        }
    }

    private static Element first(Document d, String local) {
        NodeList l = d.getElementsByTagNameNS(NS_W, local);
        return l.getLength() == 0 ? null : (Element) l.item(0);
    }

    private void readTheme(Document d) {
        for (String kind : new String[]{"major", "minor"}) {
            NodeList l = d.getElementsByTagNameNS(NS_A, kind + "Font");
            if (l.getLength() == 0) {
                continue;
            }
            Element f = (Element) l.item(0);
            String latin = typeface(f, "latin");
            String ea = typeface(f, "ea");
            String hang = null;
            for (Node n = f.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (n instanceof Element && "font".equals(n.getLocalName()) && "Hang".equals(((Element) n).getAttribute("script"))) {
                    hang = ((Element) n).getAttribute("typeface");
                }
            }
            theme.put(kind + "HAnsi", latin);
            theme.put(kind + "Ascii", latin);
            theme.put(kind + "Bidi", latin);
            // 한국어 문서의 동아시아 글꼴은 ea가 비어 있고 Hang 스크립트 글꼴로 정해지는 경우가 많음
            theme.put(kind + "EastAsia", hang != null && !hang.isEmpty() ? hang : ea != null && !ea.isEmpty() ? ea : latin);
        }
    }

    private static String typeface(Element parent, String local) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element && local.equals(n.getLocalName()) && NS_A.equals(n.getNamespaceURI())) {
                return ((Element) n).getAttribute("typeface");
            }
        }
        return null;
    }

    /** 스타일 상속 사슬 (가장 위 조상부터). 고리가 있으면 끊는다. */
    private List<Element> chain(String id) {
        List<Element> out = new ArrayList<Element>();
        Set<String> seen = new HashSet<String>();
        while (id != null && styles.containsKey(id) && seen.add(id)) {
            Element s = styles.get(id);
            out.add(0, s);
            id = attr(child(s, "basedOn"), "val");
        }
        return out;
    }

    /** 문단 스타일까지 적용한 {문단 속성, 글자 속성}. */
    Props[] paragraphBase(String styleId) {
        Props p = new Props().applyPara(defaultPPr);
        Props r = new Props().applyRun(defaultRPr);
        for (Element s : chain(styleId != null && styles.containsKey(styleId) ? styleId : defaultParagraphStyle)) {
            p.applyPara(child(s, "pPr"));
            r.applyRun(child(s, "rPr"));
        }
        return new Props[]{p, r};
    }

    /** 글자 스타일(w:rStyle) 사슬을 덮어씀. */
    void applyRunStyle(Props r, String styleId) {
        for (Element s : chain(styleId)) {
            r.applyRun(child(s, "rPr"));
        }
    }

    /** 동아시아(한글) 글꼴. 없으면 바탕. */
    String eastAsiaFont(Props r) {
        String name = firstNonEmpty(theme.get(r.get("eastAsiaTheme", "")), r.get("eastAsia"),
                theme.get(r.get("asciiTheme", "")), r.get("ascii"), "바탕");
        return localName(name);
    }

    /** 영문 글꼴. 없으면 Times New Roman. */
    String latinFont(Props r) {
        String name = firstNonEmpty(theme.get(r.get("asciiTheme", "")), r.get("ascii"),
                theme.get(r.get("hAnsiTheme", "")), r.get("hAnsi"), "Times New Roman");
        return localName(name);
    }

    static String localName(String name) {
        String k = FONT_NAME.get(name);
        return k != null ? k : name;
    }

    private static String firstNonEmpty(String... v) {
        for (String s : v) {
            if (s != null && !s.isEmpty()) {
                return s;
            }
        }
        return "";
    }
}

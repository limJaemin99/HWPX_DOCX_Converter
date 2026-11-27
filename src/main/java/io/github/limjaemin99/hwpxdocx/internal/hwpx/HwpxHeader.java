package io.github.limjaemin99.hwpxdocx.internal.hwpx;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** header.xml의 참조표: 글꼴, 글자 모양, 문단 모양, 테두리·배경, 스타일. ID 문자열로 찾는다. */
public final class HwpxHeader {

    public static final String NS_HH = HwpxSkeleton.NS_HEAD;
    public static final String NS_HP = HwpxSkeleton.NS_PARA;
    public static final String NS_HC = HwpxSkeleton.NS_CORE;

    private final Map<String, Map<String, String>> fonts = new HashMap<String, Map<String, String>>();
    private final Map<String, Element> charPr;
    private final Map<String, Element> paraPr;
    private final Map<String, Element> borderFill;
    private final Map<String, Element> style;
    private final Map<String, Element> numbering;
    private final Map<String, Element> bullet;

    public HwpxHeader(Document doc) {
        NodeList faces = doc.getElementsByTagNameNS(NS_HH, "fontface");
        for (int i = 0; i < faces.getLength(); i++) {
            Element ff = (Element) faces.item(i);
            Map<String, String> byId = new HashMap<String, String>();
            for (Element f : children(ff, NS_HH, "font")) {
                byId.put(f.getAttribute("id"), f.getAttribute("face"));
            }
            fonts.put(ff.getAttribute("lang"), byId);
        }
        charPr = byId(doc, "charPr");
        paraPr = byId(doc, "paraPr");
        borderFill = byId(doc, "borderFill");
        style = byId(doc, "style");
        numbering = byId(doc, "numbering");
        bullet = byId(doc, "bullet");
    }

    private static Map<String, Element> byId(Document doc, String local) {
        Map<String, Element> m = new HashMap<String, Element>();
        NodeList list = doc.getElementsByTagNameNS(NS_HH, local);
        for (int i = 0; i < list.getLength(); i++) {
            Element e = (Element) list.item(i);
            m.put(e.getAttribute("id"), e);
        }
        return m;
    }

    /** {@code lang}은 HANGUL·LATIN 등. 없으면 빈 문자열. */
    public String fontFace(String lang, String id) {
        Map<String, String> m = fonts.get(lang);
        String f = m == null ? null : m.get(id);
        return f == null ? "" : f;
    }

    public Element charPr(String id) {
        return charPr.get(id);
    }

    public Element paraPr(String id) {
        return paraPr.get(id);
    }

    public Element numbering(String id) {
        return numbering.get(id);
    }

    public Element bullet(String id) {
        return bullet.get(id);
    }

    public Element borderFill(String id) {
        return borderFill.get(id);
    }

    public Element style(String id) {
        return style.get(id);
    }

    /** 글자 크기(1/100 pt). 모르면 10 pt. */
    public int charHeight(String charPrId) {
        Element c = charPr.get(charPrId);
        return c == null ? 1000 : intAttr(c, "height", 1000);
    }

    public static Element child(Element parent, String ns, String local) {
        if (parent == null) {
            return null;
        }
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element && local.equals(n.getLocalName()) && ns.equals(n.getNamespaceURI())) {
                return (Element) n;
            }
        }
        return null;
    }

    public static List<Element> children(Element parent, String ns, String local) {
        List<Element> list = new ArrayList<Element>();
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element && local.equals(n.getLocalName()) && ns.equals(n.getNamespaceURI())) {
                list.add((Element) n);
            }
        }
        return list;
    }

    /**
     * 정수 속성. 한컴은 음수를 부호 없는 32비트 값(예: 4294966296)으로 적기도 해서 되돌림.
     * 없거나 숫자가 아니거나 int 범위 밖이면 {@code def}.
     */
    public static int intAttr(Element e, String name, int def) {
        if (e == null || !e.hasAttribute(name)) {
            return def;
        }
        try {
            long v = Long.parseLong(e.getAttribute(name).trim());
            if (v >= (1L << 31) && v < (1L << 32)) {
                v -= 1L << 32;
            }
            return v < Integer.MIN_VALUE || v > Integer.MAX_VALUE ? def : (int) v;
        } catch (NumberFormatException ex) {
            return def;
        }
    }
}

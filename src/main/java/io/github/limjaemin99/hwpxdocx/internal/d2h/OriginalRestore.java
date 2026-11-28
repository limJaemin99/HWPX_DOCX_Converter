package io.github.limjaemin99.hwpxdocx.internal.d2h;

import io.github.limjaemin99.hwpxdocx.ConvertException;
import io.github.limjaemin99.hwpxdocx.internal.ConvertStats;
import io.github.limjaemin99.hwpxdocx.internal.SafeXml;
import io.github.limjaemin99.hwpxdocx.internal.Xml;
import io.github.limjaemin99.hwpxdocx.internal.hwpx.HwpxHeader;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * HWPX → DOCX 때 보관한 원본 문단(사용자 정의 XML 파트)을 되살린다.
 * 원본 조각이 가리키는 글자·문단 모양, 테두리, 탭, 글꼴은 보관한 원본 header.xml에서 새 header.xml 쪽에 옮겨 붙이고 번호를 다시 매긴다.
 */
final class OriginalRestore {

    static final String NS = "urn:io.github.limjaemin99:hwpx-docx:original:1";
    static final String BOOKMARK_PREFIX = "_hwpx_";

    private static final String NS_HP = HwpxHeader.NS_HP;
    private static final String NS_HH = HwpxHeader.NS_HH;
    // 쪽 설정은 DOCX 구역 설정에서 다시 만들므로 되살린 문단에서는 뺌
    private static final Set<String> SECTION_CTRLS = new HashSet<String>(Arrays.asList(
            "colPr", "header", "footer", "pageNum", "pageHiding", "pageNumCtrl", "newNum"));
    private static final String[][] FONT_LANGS = {{"hangul", "HANGUL"}, {"latin", "LATIN"}, {"hanja", "HANJA"},
            {"japanese", "JAPANESE"}, {"other", "OTHER"}, {"symbol", "SYMBOL"}, {"user", "USER"}};

    /** 옮길 수 없는 참조. 그 문단은 변환 결과를 그대로 씀. */
    private static final class Unsupported extends Exception {
        private static final long serialVersionUID = 1L;

        Unsupported(String what) {
            super(what, null, false, false);
        }
    }

    private final Map<Integer, Element> items = new HashMap<Integer, Element>();
    private final Map<String, Element> charPrs = new HashMap<String, Element>();
    private final Map<String, Element> paraPrs = new HashMap<String, Element>();
    private final Map<String, Element> borderFills = new HashMap<String, Element>();
    private final Map<String, Element> tabPrs = new HashMap<String, Element>();
    private final Map<String, Map<String, String>> fonts = new HashMap<String, Map<String, String>>();
    private final Map<String, Integer> charMap = new HashMap<String, Integer>();
    private final Map<String, Integer> paraMap = new HashMap<String, Integer>();
    private final Map<String, Integer> borderMap = new HashMap<String, Integer>();
    private final Map<String, Integer> tabMap = new HashMap<String, Integer>();
    private boolean hasHeader;

    /** 본문에 딸린 사용자 정의 XML 파트 가운데 보관 파트. 없으면 null. */
    static OriginalRestore read(DocxPackage pkg) throws ConvertException {
        for (DocxPackage.Rel r : pkg.rels(pkg.mainPart).values()) {
            if (r.external || !"customXml".equals(r.kind()) || !pkg.has(r.target)) {
                continue;
            }
            Element root = pkg.parse(r.target).getDocumentElement();
            if (NS.equals(root.getNamespaceURI()) && "original".equals(root.getLocalName())) {
                return new OriginalRestore(root);
            }
        }
        return null;
    }

    private OriginalRestore(Element root) throws ConvertException {
        for (Node n = root.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (!(n instanceof Element) || !NS.equals(n.getNamespaceURI())) {
                continue;
            }
            Element e = (Element) n;
            if ("p".equals(e.getLocalName())) {
                try {
                    items.put(Integer.parseInt(e.getAttribute("n")), e);
                } catch (NumberFormatException ignored) {
                    // 번호가 없는 항목은 찾을 수 없으므로 버림
                }
            } else if ("header".equals(e.getLocalName()) && !e.getTextContent().trim().isEmpty()) {
                index(parse(e.getTextContent(), "보관한 원본 header.xml"));
                hasHeader = true;
            }
        }
    }

    private void index(Element head) {
        put(head, "charPr", charPrs);
        put(head, "paraPr", paraPrs);
        put(head, "borderFill", borderFills);
        put(head, "tabPr", tabPrs);
        NodeList faces = head.getElementsByTagNameNS(NS_HH, "fontface");
        for (int i = 0; i < faces.getLength(); i++) {
            Element ff = (Element) faces.item(i);
            Map<String, String> byId = new HashMap<String, String>();
            NodeList fl = ff.getElementsByTagNameNS(NS_HH, "font");
            for (int j = 0; j < fl.getLength(); j++) {
                Element f = (Element) fl.item(j);
                byId.put(f.getAttribute("id"), f.getAttribute("face"));
            }
            fonts.put(ff.getAttribute("lang"), byId);
        }
    }

    private static void put(Element head, String local, Map<String, Element> into) {
        NodeList l = head.getElementsByTagNameNS(NS_HH, local);
        for (int i = 0; i < l.getLength(); i++) {
            Element e = (Element) l.item(i);
            into.put(e.getAttribute("id"), e);
        }
    }

    /** "_hwpx_N" → N. 보관 책갈피가 아니면 0. */
    static int number(String bookmarkName) {
        if (bookmarkName == null || !bookmarkName.startsWith(BOOKMARK_PREFIX)) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(bookmarkName.substring(BOOKMARK_PREFIX.length())));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 되살린 hp:p XML. 글이 바뀌었거나 옮길 수 없는 참조가 있으면 경고를 남기고 null.
     *
     * @param docxText 책갈피 안 Word 문단들의 글자 (w:t만 이어 붙임)
     */
    String paragraph(int n, String docxText, HwpxRefs refs, ConvertStats stats) throws ConvertException {
        Element item = items.get(n);
        if (item == null) {
            return null;
        }
        if (!docxText.equals(item.getAttribute("text"))) {
            stats.warn("Word에서 글이 바뀐 문단은 보관한 원본 대신 변환 결과를 씀");
            return null;
        }
        if (!hasHeader) {
            stats.warn("보관한 원본에 header.xml이 없어 문단을 되살리지 않음");
            return null;
        }
        Element p = first(parse("<x " + item.getAttribute("ns") + ">" + item.getTextContent() + "</x>", "보관한 원본 문단"));
        if (p == null || !NS_HP.equals(p.getNamespaceURI()) || !"p".equals(p.getLocalName())) {
            return null;
        }
        strip(p);
        try {
            remap(p, refs);
        } catch (Unsupported u) {
            stats.warn("보관한 원본 문단을 되살리지 않고 변환 결과를 씀: 옮길 수 없는 " + u.getMessage());
            return null;
        }
        StringBuilder b = new StringBuilder();
        write(p, b, true);
        return b.toString();
    }

    private static Element parse(String xml, String name) throws ConvertException {
        Document d = SafeXml.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), name);
        return d.getDocumentElement();
    }

    private static Element first(Element parent) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element) {
                return (Element) n;
            }
        }
        return null;
    }

    /** 줄 배치(한글이 다시 계산함)와 구역 설정 컨트롤을 뺌. */
    private static void strip(Element p) {
        NodeList segs = p.getElementsByTagNameNS(NS_HP, "linesegarray");
        for (int i = segs.getLength() - 1; i >= 0; i--) {
            segs.item(i).getParentNode().removeChild(segs.item(i));
        }
        for (Element run : children(p, "run")) {
            for (Element sec : children(run, "secPr")) {
                run.removeChild(sec);
            }
            for (Element ctrl : children(run, "ctrl")) {
                for (Node c = ctrl.getFirstChild(); c != null; ) {
                    Node next = c.getNextSibling();
                    if (c instanceof Element && SECTION_CTRLS.contains(c.getLocalName())) {
                        ctrl.removeChild(c);
                    }
                    c = next;
                }
                if (first(ctrl) == null) {
                    run.removeChild(ctrl);
                }
            }
        }
    }

    private static List<Element> children(Element parent, String local) {
        List<Element> l = new ArrayList<Element>();
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element && NS_HP.equals(n.getNamespaceURI()) && local.equals(n.getLocalName())) {
                l.add((Element) n);
            }
        }
        return l;
    }

    /** 문단 조각 안 모양 번호를 새 header 번호로. fieldEnd의 beginIDRef는 조각 안 참조라 그대로 둠. */
    private void remap(Element e, HwpxRefs refs) throws Unsupported {
        NamedNodeMap attrs = e.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Attr a = (Attr) attrs.item(i);
            String name = a.getLocalName() == null ? a.getName() : a.getLocalName();
            if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(a.getNamespaceURI())) {
                continue;
            }
            if ("charPrIDRef".equals(name)) {
                a.setValue(String.valueOf(charId(a.getValue(), refs)));
            } else if ("paraPrIDRef".equals(name)) {
                a.setValue(String.valueOf(paraId(a.getValue(), refs)));
            } else if ("styleIDRef".equals(name)) {
                // 새 header에는 바탕글 하나뿐. 모양은 글자·문단 모양 번호가 따로 가리킴
                a.setValue("0");
            } else if ("borderFillIDRef".equals(name)) {
                a.setValue(String.valueOf(borderId(a.getValue(), refs)));
            } else if (name.endsWith("IDRef") && !"beginIDRef".equals(name) && !noLink(name, a.getValue())) {
                throw new Unsupported("참조(" + name + ")");
            }
        }
        for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element) {
                remap((Element) n, refs);
            }
        }
    }

    // 글상자 글 이어 흐르기 연결. 0이면 연결 없음
    private static boolean noLink(String name, String value) {
        return ("linkListIDRef".equals(name) || "linkListNextIDRef".equals(name)) && "0".equals(value);
    }

    private int charId(String id, HwpxRefs refs) throws Unsupported {
        Integer done = charMap.get(id);
        if (done != null) {
            return done;
        }
        Element c = copy(charPrs, id, "글자 모양");
        for (Node n = c.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element && NS_HH.equals(n.getNamespaceURI()) && "fontRef".equals(n.getLocalName())) {
                Element f = (Element) n;
                for (String[] lang : FONT_LANGS) {
                    if (!f.hasAttribute(lang[0])) {
                        continue;
                    }
                    Map<String, String> byId = fonts.get(lang[1]);
                    String face = byId == null ? null : byId.get(f.getAttribute(lang[0]));
                    if (face == null) {
                        throw new Unsupported("글꼴 참조");
                    }
                    f.setAttribute(lang[0], String.valueOf(refs.font(face)));
                }
            }
        }
        remapDef(c, refs);
        int v = refs.importChar(body(c));
        charMap.put(id, v);
        return v;
    }

    private int paraId(String id, HwpxRefs refs) throws Unsupported {
        Integer done = paraMap.get(id);
        if (done != null) {
            return done;
        }
        Element c = copy(paraPrs, id, "문단 모양");
        NodeList heads = c.getElementsByTagNameNS(NS_HH, "heading");
        for (int i = 0; i < heads.getLength(); i++) {
            String type = ((Element) heads.item(i)).getAttribute("type");
            if (!type.isEmpty() && !"NONE".equals(type)) {
                throw new Unsupported("문단 번호");
            }
        }
        remapDef(c, refs);
        int v = refs.importPara(body(c));
        paraMap.put(id, v);
        return v;
    }

    private int borderId(String id, HwpxRefs refs) throws Unsupported {
        Integer done = borderMap.get(id);
        if (done != null) {
            return done;
        }
        Element c = copy(borderFills, id, "테두리");
        remapDef(c, refs);
        int v = refs.importBorder(body(c));
        borderMap.put(id, v);
        return v;
    }

    private int tabId(String id, HwpxRefs refs) throws Unsupported {
        if ("0".equals(id)) {
            return 0;
        }
        Integer done = tabMap.get(id);
        if (done != null) {
            return done;
        }
        Element c = copy(tabPrs, id, "탭");
        remapDef(c, refs);
        int v = refs.importTab(body(c));
        tabMap.put(id, v);
        return v;
    }

    private static Element copy(Map<String, Element> defs, String id, String what) throws Unsupported {
        Element def = defs.get(id);
        if (def == null) {
            throw new Unsupported(what + " 참조");
        }
        return (Element) def.cloneNode(true);
    }

    /** 정의 안 참조. 그림 채우기(binaryItemIDRef)처럼 바이너리를 가리키는 것은 데이터가 없어 옮길 수 없음. */
    private void remapDef(Element e, HwpxRefs refs) throws Unsupported {
        NamedNodeMap attrs = e.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Attr a = (Attr) attrs.item(i);
            String name = a.getLocalName() == null ? a.getName() : a.getLocalName();
            if ("borderFillIDRef".equals(name)) {
                a.setValue(String.valueOf(borderId(a.getValue(), refs)));
            } else if ("tabPrIDRef".equals(name)) {
                a.setValue(String.valueOf(tabId(a.getValue(), refs)));
            } else if (name.endsWith("IDRef")) {
                throw new Unsupported("참조(" + name + ")");
            }
        }
        for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element) {
                remapDef((Element) n, refs);
            }
        }
    }

    /** HwpxRefs 목록 항목 꼴: id 뒤의 속성들 + '>' + 자식 XML. 쓰인 이름공간은 이 요소에 선언함. */
    private static String body(Element def) {
        StringBuilder b = new StringBuilder();
        declare(def, b);
        attributes(def, b, true);
        b.append('>');
        for (Node n = def.getFirstChild(); n != null; n = n.getNextSibling()) {
            writeNode(n, b);
        }
        return b.toString();
    }

    private static void write(Element e, StringBuilder b, boolean top) {
        b.append('<').append(e.getNodeName());
        if (top) {
            declare(e, b);
        }
        attributes(e, b, false);
        if (e.getFirstChild() == null) {
            b.append("/>");
            return;
        }
        b.append('>');
        for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling()) {
            writeNode(n, b);
        }
        b.append("</").append(e.getNodeName()).append('>');
    }

    private static void writeNode(Node n, StringBuilder b) {
        if (n instanceof Element) {
            write((Element) n, b, false);
        } else if (n.getNodeType() == Node.TEXT_NODE || n.getNodeType() == Node.CDATA_SECTION_NODE) {
            b.append(Xml.text(n.getNodeValue()));
        }
    }

    private static void attributes(Element e, StringBuilder b, boolean skipId) {
        NamedNodeMap attrs = e.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Attr a = (Attr) attrs.item(i);
            if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(a.getNamespaceURI()) || (skipId && "id".equals(a.getName()))) {
                continue;
            }
            b.append(' ').append(a.getName()).append("=\"").append(Xml.attr(a.getValue())).append('"');
        }
    }

    /** 조각이 따로 서도록 하위 요소·속성이 쓰는 접두사를 모두 선언. */
    private static void declare(Element e, StringBuilder b) {
        Map<String, String> used = new LinkedHashMap<String, String>();
        collect(e, used);
        for (Map.Entry<String, String> u : used.entrySet()) {
            b.append(' ').append(u.getKey().isEmpty() ? "xmlns" : "xmlns:" + u.getKey()).append("=\"")
                    .append(Xml.attr(u.getValue())).append('"');
        }
    }

    private static void collect(Element e, Map<String, String> used) {
        if (e.getNamespaceURI() != null && !used.containsKey(prefix(e))) {
            used.put(prefix(e), e.getNamespaceURI());
        }
        NamedNodeMap attrs = e.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Attr a = (Attr) attrs.item(i);
            String ns = a.getNamespaceURI();
            if (ns != null && a.getPrefix() != null && !XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(ns)
                    && !XMLConstants.XML_NS_URI.equals(ns) && !used.containsKey(a.getPrefix())) {
                used.put(a.getPrefix(), ns);
            }
        }
        for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element) {
                collect((Element) n, used);
            }
        }
    }

    private static String prefix(Element e) {
        return e.getPrefix() == null ? "" : e.getPrefix();
    }
}

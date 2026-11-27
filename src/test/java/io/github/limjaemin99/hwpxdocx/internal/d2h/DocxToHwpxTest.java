package io.github.limjaemin99.hwpxdocx.internal.d2h;

import io.github.limjaemin99.hwpxdocx.ConvertException;
import io.github.limjaemin99.hwpxdocx.ConvertResult;
import io.github.limjaemin99.hwpxdocx.HwpxDocxConverter;
import io.github.limjaemin99.hwpxdocx.internal.SafeXml;
import io.github.limjaemin99.hwpxdocx.internal.ZipReader;
import io.github.limjaemin99.hwpxdocx.internal.docx.DocxSkeleton;
import io.github.limjaemin99.hwpxdocx.internal.hwpx.HwpxHeader;
import io.github.limjaemin99.hwpxdocx.internal.hwpx.HwpxSkeleton;
import io.github.limjaemin99.hwpxdocx.testkit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.github.limjaemin99.hwpxdocx.internal.d2h.TestDocx.p;
import static io.github.limjaemin99.hwpxdocx.internal.d2h.TestDocx.r;
import static io.github.limjaemin99.hwpxdocx.internal.d2h.TestDocx.t;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertContains;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertEquals;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertThrows;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertTrue;

class DocxToHwpxTest {

    private static final String HP = HwpxHeader.NS_HP;
    private static final String HH = HwpxHeader.NS_HH;
    private static final String HC = HwpxHeader.NS_HC;

    private ConvertResult result;
    private Document header;
    private Document section;

    private void convert(byte[] docx) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        result = HwpxDocxConverter.docxToHwpx(new ByteArrayInputStream(docx), out, null);
        Map<String, byte[]> m = ZipReader.unzip(new ByteArrayInputStream(out.toByteArray()));
        assertEquals("mimetype", m.keySet().iterator().next());
        header = SafeXml.parse(new ByteArrayInputStream(m.get("Contents/header.xml")), "header");
        section = SafeXml.parse(new ByteArrayInputStream(m.get("Contents/section0.xml")), "section");
    }

    private List<Element> paras() {
        List<Element> list = new ArrayList<Element>();
        for (Node n = section.getDocumentElement().getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element && "p".equals(n.getLocalName())) {
                list.add((Element) n);
            }
        }
        return list;
    }

    private static String text(Element p) {
        StringBuilder b = new StringBuilder();
        NodeList ts = p.getElementsByTagNameNS(HP, "t");
        for (int i = 0; i < ts.getLength(); i++) {
            for (Node n = ts.item(i).getFirstChild(); n != null; n = n.getNextSibling()) {
                if (n.getNodeType() == Node.TEXT_NODE) {
                    b.append(n.getNodeValue());
                } else if ("tab".equals(n.getLocalName())) {
                    b.append("<TAB>");
                } else if ("lineBreak".equals(n.getLocalName())) {
                    b.append("<BR>");
                }
            }
        }
        return b.toString();
    }

    private Element byId(String local, String id) {
        NodeList l = header.getElementsByTagNameNS(HH, local);
        for (int i = 0; i < l.getLength(); i++) {
            Element e = (Element) l.item(i);
            if (id.equals(e.getAttribute("id"))) {
                return e;
            }
        }
        throw new AssertionError(local + " " + id + " 없음");
    }

    /** 문단 i의 첫 글자 런(구역 설정 런 제외)의 글자 모양. */
    private Element charPrOf(int i) {
        for (Element run : kids(paras().get(i), HP, "run")) {
            if (kids(run, HP, "secPr").isEmpty()) {
                return byId("charPr", run.getAttribute("charPrIDRef"));
            }
        }
        throw new AssertionError("런 없음");
    }

    private Element paraPrOf(int i) {
        return byId("paraPr", paras().get(i).getAttribute("paraPrIDRef"));
    }

    private static List<Element> kids(Element parent, String ns, String local) {
        List<Element> list = new ArrayList<Element>();
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element && local.equals(n.getLocalName()) && ns.equals(n.getNamespaceURI())) {
                list.add((Element) n);
            }
        }
        return list;
    }

    private static Element kid(Element parent, String ns, String local) {
        List<Element> l = kids(parent, ns, local);
        return l.isEmpty() ? null : l.get(0);
    }

    private String face(String id) {
        for (Element ff : kids((Element) header.getElementsByTagNameNS(HH, "fontfaces").item(0), HH, "fontface")) {
            if ("HANGUL".equals(ff.getAttribute("lang"))) {
                for (Element f : kids(ff, HH, "font")) {
                    if (id.equals(f.getAttribute("id"))) {
                        return f.getAttribute("face");
                    }
                }
            }
        }
        throw new AssertionError("글꼴 " + id + " 없음");
    }

    @Test
    void emptySkeleton() throws Exception {
        convert(DocxSkeleton.toBytes());
        assertEquals(1, paras().size());
        assertEquals(1, result.getParagraphCount());
        assertEquals(0, result.getSkippedCount());
        assertEquals(1, section.getElementsByTagNameNS(HP, "secPr").getLength());
        Element c0 = byId("charPr", "0");
        assertEquals("1000", c0.getAttribute("height"));
        assertEquals("함초롬바탕", face(kid(c0, HH, "fontRef").getAttribute("hangul")));
        Element style = (Element) header.getElementsByTagNameNS(HH, "style").item(0);
        assertEquals("바탕글", style.getAttribute("name"));
    }

    @Test
    void textTabsBreaksAndPageBreak() throws Exception {
        convert(new TestDocx().body(
                p("", r("", t("제1조 ①대한민국은\t민주공화국이다.") + "<w:br/>" + t("②대한민국의 주권은") + "<w:tab/>"
                        + t("국민에게 있고") + "<w:noBreakHyphen/>" + "<w:sym w:font=\"Symbol\" w:char=\"2460\"/>")),
                p("", r("", t("제2장")), r("", "<w:br w:type=\"page\"/>"), r("", t("제10조 모든 국민은")))
        ).toBytes());
        List<Element> ps = paras();
        assertEquals(3, ps.size());
        assertEquals("제1조 ①대한민국은<TAB>민주공화국이다.<BR>②대한민국의 주권은<TAB>국민에게 있고-①", text(ps.get(0)));
        assertEquals("제2장", text(ps.get(1)));
        assertEquals("0", ps.get(1).getAttribute("pageBreak"));
        assertEquals("제10조 모든 국민은", text(ps.get(2)));
        assertEquals("1", ps.get(2).getAttribute("pageBreak"));
        assertEquals(2, result.getParagraphCount());
    }

    @Test
    void charShape() throws Exception {
        convert(new TestDocx().body(p("", r("<w:b/><w:i/><w:strike/><w:color w:val=\"FF0000\"/><w:w w:val=\"90\"/>"
                        + "<w:spacing w:val=\"-16\"/><w:sz w:val=\"32\"/><w:highlight w:val=\"yellow\"/>"
                        + "<w:u w:val=\"dotted\" w:color=\"0000FF\"/><w:vertAlign w:val=\"superscript\"/>",
                t("대한민국은 민주공화국이다.")))).toBytes());
        Element c = charPrOf(0);
        assertEquals("1600", c.getAttribute("height"));
        assertEquals("#FF0000", c.getAttribute("textColor"));
        assertEquals("#FFFF00", c.getAttribute("shadeColor"));
        assertEquals("90", kid(c, HH, "ratio").getAttribute("hangul"));
        assertEquals("-5", kid(c, HH, "spacing").getAttribute("hangul"));
        assertTrue(kid(c, HH, "bold") != null);
        assertTrue(kid(c, HH, "italic") != null);
        assertTrue(kid(c, HH, "supscript") != null);
        assertEquals("BOTTOM", kid(c, HH, "underline").getAttribute("type"));
        assertEquals("DOT", kid(c, HH, "underline").getAttribute("shape"));
        assertEquals("#0000FF", kid(c, HH, "underline").getAttribute("color"));
        assertEquals("SOLID", kid(c, HH, "strikeout").getAttribute("shape"));
    }

    @Test
    void styleInheritance() throws Exception {
        convert(new TestDocx().styles(
                "<w:style w:type=\"paragraph\" w:styleId=\"Heading\"><w:name w:val=\"heading\"/>"
                        + "<w:basedOn w:val=\"Normal\"/><w:pPr><w:jc w:val=\"center\"/></w:pPr>"
                        + "<w:rPr><w:b/><w:sz w:val=\"28\"/></w:rPr></w:style>",
                "<w:style w:type=\"paragraph\" w:styleId=\"Sub\"><w:basedOn w:val=\"Heading\"/>"
                        + "<w:rPr><w:b w:val=\"0\"/></w:rPr></w:style>",
                "<w:style w:type=\"character\" w:styleId=\"Em\"><w:rPr><w:i/></w:rPr></w:style>")
                .body(p("<w:pStyle w:val=\"Heading\"/>", r("", t("제1장 총강"))),
                        p("<w:pStyle w:val=\"Sub\"/>", r("<w:rStyle w:val=\"Em\"/><w:color w:val=\"00FF00\"/>", t("제1조"))))
                .toBytes());
        Element c0 = charPrOf(0);
        assertEquals("1400", c0.getAttribute("height"));
        assertTrue(kid(c0, HH, "bold") != null);
        assertEquals("CENTER", kid(paraPrOf(0), HH, "align").getAttribute("horizontal"));
        Element c1 = charPrOf(1);
        assertEquals("1400", c1.getAttribute("height"));
        assertEquals(null, kid(c1, HH, "bold"));
        assertTrue(kid(c1, HH, "italic") != null);
        assertEquals("#00FF00", c1.getAttribute("textColor"));
        assertEquals("CENTER", kid(paraPrOf(1), HH, "align").getAttribute("horizontal"));
    }

    @Test
    void themeFonts() throws Exception {
        convert(new TestDocx().theme("Calibri", "맑은 고딕").body(p("", r(
                "<w:rFonts w:asciiTheme=\"minorHAnsi\" w:hAnsiTheme=\"minorHAnsi\" w:eastAsiaTheme=\"minorEastAsia\"/>",
                t("대한민국")))).toBytes());
        Element ref = kid(charPrOf(0), HH, "fontRef");
        assertEquals("맑은 고딕", face(ref.getAttribute("hangul")));
        assertEquals("Calibri", face(ref.getAttribute("latin")));
    }

    @Test
    void englishFontNamesBecomeKorean() throws Exception {
        convert(new TestDocx().body(p("", r("<w:rFonts w:ascii=\"Arial\" w:eastAsia=\"Batang\"/>", t("가"))))
                .toBytes());
        assertEquals("바탕", face(kid(charPrOf(0), HH, "fontRef").getAttribute("hangul")));
    }

    private static Element caseMargin(Element paraPr, String local) {
        Element sw = kid(paraPr, HP, "switch");
        return kid(kid(kid(sw, HP, "case"), HH, "margin"), HC, local);
    }

    private static Element defaultMargin(Element paraPr, String local) {
        Element sw = kid(paraPr, HP, "switch");
        return kid(kid(kid(sw, HP, "default"), HH, "margin"), HC, local);
    }

    private static Element lineSpacing(Element paraPr, String branch) {
        return kid(kid(kid(paraPr, HP, "switch"), HP, branch), HH, "lineSpacing");
    }

    @Test
    void paraShape() throws Exception {
        convert(new TestDocx().body(
                p("<w:jc w:val=\"center\"/><w:ind w:left=\"720\" w:hanging=\"360\"/>"
                                + "<w:spacing w:before=\"240\" w:after=\"120\" w:line=\"360\" w:lineRule=\"auto\"/>"
                                + "<w:keepNext/>",
                        r("", t("제4조 대한민국은 통일을 지향한다."))),
                p("<w:ind w:firstLine=\"200\"/><w:spacing w:line=\"400\" w:lineRule=\"exact\"/>",
                        r("", t("제9조 국가는 전통문화의 계승ㆍ발전에 노력하여야 한다."))))
                .toBytes());
        Element p0 = paraPrOf(0);
        assertEquals("CENTER", kid(p0, HH, "align").getAttribute("horizontal"));
        assertEquals("1800", caseMargin(p0, "left").getAttribute("value"));
        assertEquals("-1800", caseMargin(p0, "intent").getAttribute("value"));
        assertEquals("1200", caseMargin(p0, "prev").getAttribute("value"));
        assertEquals("600", caseMargin(p0, "next").getAttribute("value"));
        assertEquals("3600", defaultMargin(p0, "left").getAttribute("value"));
        // 함초롬바탕 1.5줄: 1.5 × 1.69 = 253.5 %
        assertEquals("PERCENT", lineSpacing(p0, "case").getAttribute("type"));
        assertEquals("254", lineSpacing(p0, "case").getAttribute("value"));
        assertEquals("254", lineSpacing(p0, "default").getAttribute("value"));
        assertEquals("1", kid(p0, HH, "breakSetting").getAttribute("keepWithNext"));

        Element p1 = paraPrOf(1);
        // 골격의 Normal 스타일이 양쪽 정렬이라 상속됨
        assertEquals("JUSTIFY", kid(p1, HH, "align").getAttribute("horizontal"));
        assertEquals("1000", caseMargin(p1, "intent").getAttribute("value"));
        assertEquals("FIXED", lineSpacing(p1, "case").getAttribute("type"));
        assertEquals("2000", lineSpacing(p1, "case").getAttribute("value"));
        assertEquals("4000", lineSpacing(p1, "default").getAttribute("value"));
    }

    @Test
    void sameShapesShareIds() throws Exception {
        convert(new TestDocx().body(
                p("", r("<w:b/>", t("제14조"))), p("", r("<w:b/>", t("제15조"))), p("", r("", t("제17조")))).toBytes());
        assertEquals(paras().get(0).getAttribute("paraPrIDRef"), paras().get(1).getAttribute("paraPrIDRef"));
        assertEquals(charPrOf(0).getAttribute("id"), charPrOf(1).getAttribute("id"));
        assertTrue(!charPrOf(0).getAttribute("id").equals(charPrOf(2).getAttribute("id")));
        Element props = (Element) header.getElementsByTagNameNS(HH, "charProperties").item(0);
        assertEquals(String.valueOf(kids(props, HH, "charPr").size()), props.getAttribute("itemCnt"));
    }

    @Test
    void pageFieldsBecomeAutoNumbers() throws Exception {
        convert(new TestDocx().body(p("",
                r("", t("쪽 ")),
                "<w:fldSimple w:instr=\" PAGE \"><w:r><w:t>7</w:t></w:r></w:fldSimple>",
                r("", t(" / ")),
                r("", "<w:fldChar w:fldCharType=\"begin\"/>"), r("", "<w:instrText> NUMPAGES \\* MERGEFORMAT </w:instrText>"),
                r("", "<w:fldChar w:fldCharType=\"separate\"/>"), r("", t("3")), r("", "<w:fldChar w:fldCharType=\"end\"/>"),
                r("", "<w:fldChar w:fldCharType=\"begin\"/>"), r("", "<w:instrText> DATE </w:instrText>"),
                r("", "<w:fldChar w:fldCharType=\"separate\"/>"), r("", t("2026")), r("", "<w:fldChar w:fldCharType=\"end\"/>")
        )).toBytes());
        NodeList nums = section.getElementsByTagNameNS(HP, "autoNum");
        assertEquals(2, nums.getLength());
        assertEquals("PAGE", ((Element) nums.item(0)).getAttribute("numType"));
        assertEquals("TOTAL_PAGE", ((Element) nums.item(1)).getAttribute("numType"));
        // 쪽 번호 필드의 표시 글자는 버리고, 다른 필드는 표시 글자를 남김
        assertEquals("쪽  / 2026", text(paras().get(0)));
    }

    @Test
    void hiddenDeletedAndUnsupported() throws Exception {
        convert(new TestDocx().body(
                p("<w:numPr><w:ilvl w:val=\"0\"/><w:numId w:val=\"1\"/></w:numPr>",
                        r("<w:vanish/>", t("숨김")), r("", t("보임")), "<w:del><w:r><w:delText>지움</w:delText></w:r></w:del>"),
                "<w:tbl><w:tr><w:tc><w:p/></w:tc></w:tr></w:tbl>").toBytes());
        assertEquals("보임", text(paras().get(0)));
        assertEquals(Integer.valueOf(1), result.getSkipped().get("w:tbl"));
        assertContains(result.getWarnings().toString(), "자동 번호");
        assertContains(result.getWarnings().toString(), "변경 추적");
    }

    @Test
    void rejectsNonDocx() throws Exception {
        assertThrows(ConvertException.class, () -> convert(HwpxSkeleton.toBytes()));
    }

    @Test
    void hwpxToDocxToHwpxKeepsText() throws Exception {
        String[] lines = {"제1조 ①대한민국은 민주공화국이다.", "②대한민국의 주권은 국민에게 있고, 모든 권력은 국민으로부터 나온다."};
        StringBuilder sec = new StringBuilder();
        for (String s : lines) {
            sec.append("<hp:p id=\"0\" paraPrIDRef=\"0\" styleIDRef=\"0\" pageBreak=\"0\" columnBreak=\"0\" merged=\"0\">")
                    .append("<hp:run charPrIDRef=\"0\"><hp:t>").append(s).append("</hp:t></hp:run></hp:p>");
        }
        Map<String, byte[]> h = HwpxSkeleton.entries();
        String section0 = new String(h.get("Contents/section0.xml"), "UTF-8");
        h.put("Contents/section0.xml", section0.replace("</hs:sec>", sec + "</hs:sec>").getBytes("UTF-8"));
        ByteArrayOutputStream hwpx = new ByteArrayOutputStream();
        HwpxSkeleton.write(h, hwpx);
        ByteArrayOutputStream docx = new ByteArrayOutputStream();
        HwpxDocxConverter.hwpxToDocx(new ByteArrayInputStream(hwpx.toByteArray()), docx, null);
        convert(docx.toByteArray());
        List<String> texts = new ArrayList<String>();
        for (Element p : paras()) {
            texts.add(text(p));
        }
        assertEquals("[, " + lines[0] + ", " + lines[1] + "]", texts.toString());
    }
}

package io.github.limjaemin99.hwpxdocx.internal.d2h;

import io.github.limjaemin99.hwpxdocx.testkit.TestConvert;
import io.github.limjaemin99.hwpxdocx.ConvertResult;
import io.github.limjaemin99.hwpxdocx.HwpxDocxConverter;
import io.github.limjaemin99.hwpxdocx.internal.SafeXml;
import io.github.limjaemin99.hwpxdocx.internal.ZipReader;
import io.github.limjaemin99.hwpxdocx.internal.hwpx.HwpxHeader;
import io.github.limjaemin99.hwpxdocx.testkit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.github.limjaemin99.hwpxdocx.internal.d2h.TestDocx.p;
import static io.github.limjaemin99.hwpxdocx.internal.d2h.TestDocx.r;
import static io.github.limjaemin99.hwpxdocx.internal.d2h.TestDocx.t;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertContains;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertEquals;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertTrue;

class DocxNumberingTest {

    private static final String HH = HwpxHeader.NS_HH;
    private static final String HP = HwpxHeader.NS_HP;

    private ConvertResult result;
    private Map<String, byte[]> hwpx;
    private byte[] hwpxBytes;

    private void convert(TestDocx d) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        result = HwpxDocxConverter.docxToHwpx(new ByteArrayInputStream(d.toBytes()), out, null);
        hwpxBytes = out.toByteArray();
        hwpx = ZipReader.unzip(new ByteArrayInputStream(hwpxBytes));
    }

    private Document xml(String name) throws Exception {
        return SafeXml.parse(new ByteArrayInputStream(hwpx.get(name)), name);
    }

    private String text(String name) {
        return new String(hwpx.get(name), StandardCharsets.UTF_8);
    }

    private static String lvl(int ilvl, String fmt, String text, String pPr, String rPr) {
        return "<w:lvl w:ilvl=\"" + ilvl + "\"><w:start w:val=\"1\"/><w:numFmt w:val=\"" + fmt + "\"/>"
                + "<w:lvlText w:val=\"" + text + "\"/><w:lvlJc w:val=\"left\"/>"
                + (pPr.isEmpty() ? "" : "<w:pPr>" + pPr + "</w:pPr>") + (rPr.isEmpty() ? "" : "<w:rPr>" + rPr + "</w:rPr>")
                + "</w:lvl>";
    }

    private static String numPr(String numId, int ilvl) {
        return "<w:numPr><w:ilvl w:val=\"" + ilvl + "\"/><w:numId w:val=\"" + numId + "\"/></w:numPr>";
    }

    /** abstractNum 1: 1. / 가) / ①, abstractNum 2: 기호 글꼴 글머리표 / o. num 1·4 → 1, num 2 → 2, num 3 → 1(5부터). */
    private static String numberingXml() {
        return "<w:abstractNum w:abstractNumId=\"1\"><w:multiLevelType w:val=\"multilevel\"/>"
                + lvl(0, "decimal", "%1.", "<w:ind w:left=\"720\" w:hanging=\"360\"/>", "")
                + lvl(1, "ganada", "%2)", "<w:ind w:left=\"1080\" w:hanging=\"360\"/>", "")
                + lvl(2, "decimalEnclosedCircle", "%3", "", "")
                + "</w:abstractNum>"
                + "<w:abstractNum w:abstractNumId=\"2\"><w:multiLevelType w:val=\"hybridMultilevel\"/>"
                + lvl(0, "bullet", "", "<w:ind w:left=\"800\" w:hanging=\"400\"/>",
                "<w:rFonts w:ascii=\"Symbol\" w:hAnsi=\"Symbol\" w:hint=\"default\"/>")
                + lvl(1, "bullet", "o", "", "<w:rFonts w:ascii=\"Courier New\" w:hAnsi=\"Courier New\"/>")
                + "</w:abstractNum>"
                + "<w:num w:numId=\"1\"><w:abstractNumId w:val=\"1\"/></w:num>"
                + "<w:num w:numId=\"2\"><w:abstractNumId w:val=\"2\"/></w:num>"
                + "<w:num w:numId=\"3\"><w:abstractNumId w:val=\"1\"/>"
                + "<w:lvlOverride w:ilvl=\"0\"><w:startOverride w:val=\"5\"/></w:lvlOverride></w:num>"
                + "<w:num w:numId=\"4\"><w:abstractNumId w:val=\"1\"/></w:num>";
    }

    private static TestDocx doc() {
        return new TestDocx().numbering(numberingXml()).body(
                p(numPr("1", 0), r("", t("총강"))),
                p(numPr("1", 1), r("", t("대한민국은 민주공화국이다."))),
                p(numPr("1", 2), r("", t("대한민국의 주권은 국민에게 있다."))),
                p(numPr("2", 0), r("", t("모든 국민은 인간으로서의 존엄과 가치를 가진다."))),
                p(numPr("2", 1), r("", t("모든 국민은 법 앞에 평등하다."))),
                p(numPr("3", 0), r("", t("국민의 권리와 의무"))),
                p(numPr("4", 0), r("", t("국회"))),
                p("", r("", t("번호 없음"))));
    }

    /** 본문 문단 순서대로 문단 모양의 hh:heading. */
    private List<Element> headings() throws Exception {
        HwpxHeader header = new HwpxHeader(xml("Contents/header.xml"));
        NodeList ps = xml("Contents/section0.xml").getElementsByTagNameNS(HP, "p");
        List<Element> out = new ArrayList<Element>();
        for (int i = 0; i < ps.getLength(); i++) {
            Element para = header.paraPr(((Element) ps.item(i)).getAttribute("paraPrIDRef"));
            out.add((Element) para.getElementsByTagNameNS(HH, "heading").item(0));
        }
        return out;
    }

    private static String h(Element heading) {
        return heading.getAttribute("type") + ":" + heading.getAttribute("idRef") + ":" + heading.getAttribute("level");
    }

    @Test
    public void numPrToHeading() throws Exception {
        convert(doc());
        List<Element> hs = headings();
        assertEquals("NUMBER:1:0", h(hs.get(0)));
        assertEquals("NUMBER:1:1", h(hs.get(1)));
        assertEquals("NUMBER:1:2", h(hs.get(2)));
        assertEquals("BULLET:1:0", h(hs.get(3)));
        assertEquals("BULLET:2:1", h(hs.get(4)));
        // startOverride가 있는 num 3만 따로, 없는 num 4는 num 1과 같은 번호라 이어짐
        assertEquals("NUMBER:2:0", h(hs.get(5)));
        assertEquals("NUMBER:1:0", h(hs.get(6)));
        assertEquals("NONE:0:0", h(hs.get(7)));
        for (String w : result.getWarnings()) {
            assertTrue(!w.contains("번호"));
        }
    }

    @Test
    public void numberingsAndBullets() throws Exception {
        convert(doc());
        HwpxHeader header = new HwpxHeader(xml("Contents/header.xml"));
        Element n1 = header.numbering("1");
        NodeList heads = n1.getElementsByTagNameNS(HH, "paraHead");
        assertEquals(10, heads.getLength());
        Element l1 = (Element) heads.item(0);
        Element l2 = (Element) heads.item(1);
        Element l3 = (Element) heads.item(2);
        assertEquals("DIGIT", l1.getAttribute("numFormat"));
        assertEquals("^1.", l1.getTextContent());
        assertEquals("HANGUL_SYLLABLE", l2.getAttribute("numFormat"));
        assertEquals("^2)", l2.getTextContent());
        assertEquals("CIRCLED_DIGIT", l3.getAttribute("numFormat"));
        assertEquals("1", l1.getAttribute("level"));
        Element n2first = (Element) header.numbering("2").getElementsByTagNameNS(HH, "paraHead").item(0);
        assertEquals("5", n2first.getAttribute("start"));
        assertEquals("1", l1.getAttribute("start"));
        // 기호 글꼴 U+F0B7은 일반 글자 •로
        assertEquals("•", header.bullet("1").getAttribute("char"));
        assertEquals("o", header.bullet("2").getAttribute("char"));
        // refList 순서: tabProperties → numberings → bullets → paraProperties
        String hx = text("Contents/header.xml");
        assertTrue(hx.indexOf("</hh:tabProperties>") < hx.indexOf("<hh:numberings"));
        assertTrue(hx.indexOf("</hh:numberings>") < hx.indexOf("<hh:bullets"));
        assertTrue(hx.indexOf("</hh:bullets>") < hx.indexOf("<hh:paraProperties"));
    }

    @Test
    public void levelIndentAppliesUnderDirectFormatting() throws Exception {
        TestDocx d = new TestDocx().numbering(numberingXml()).body(
                p(numPr("1", 0), r("", t("총강"))),
                p(numPr("1", 0) + "<w:ind w:left=\"1440\" w:hanging=\"720\"/>", r("", t("국회"))));
        convert(d);
        HwpxHeader header = new HwpxHeader(xml("Contents/header.xml"));
        NodeList ps = xml("Contents/section0.xml").getElementsByTagNameNS(HP, "p");
        Element a = header.paraPr(((Element) ps.item(0)).getAttribute("paraPrIDRef"));
        Element b = header.paraPr(((Element) ps.item(1)).getAttribute("paraPrIDRef"));
        // 번호 수준 ind: 왼쪽 720 - 내어쓰기 360 = 360 twip = 1800 HWPUNIT
        assertEquals("1800", caseValue(a, "left"));
        assertEquals("-1800", caseValue(a, "intent"));
        // 문단 직접 ind가 번호 수준 ind보다 우선
        assertEquals("3600", caseValue(b, "left"));
        assertEquals("-3600", caseValue(b, "intent"));
    }

    private static String caseValue(Element paraPr, String local) {
        Element c = (Element) paraPr.getElementsByTagNameNS(HP, "case").item(0);
        return ((Element) c.getElementsByTagNameNS(HwpxHeader.NS_HC, local).item(0)).getAttribute("value");
    }

    @Test
    public void numberingFromStyleAndNumIdZero() throws Exception {
        TestDocx d = new TestDocx().numbering(numberingXml())
                .styles("<w:style w:type=\"paragraph\" w:styleId=\"Jo\"><w:name w:val=\"조\"/><w:pPr>" + numPr("1", 0)
                        + "</w:pPr></w:style>")
                .body(p("<w:pStyle w:val=\"Jo\"/>", r("", t("제1조"))),
                        p("<w:pStyle w:val=\"Jo\"/><w:numPr><w:numId w:val=\"0\"/></w:numPr>", r("", t("제2조"))));
        convert(d);
        List<Element> hs = headings();
        assertEquals("NUMBER:1:0", h(hs.get(0)));
        assertEquals("NONE:0:0", h(hs.get(1)));
    }

    @Test
    public void numStyleLink() throws Exception {
        String xml = "<w:abstractNum w:abstractNumId=\"7\"><w:styleLink w:val=\"Law\"/>"
                + lvl(0, "upperRoman", "%1.", "", "") + "</w:abstractNum>"
                + "<w:abstractNum w:abstractNumId=\"8\"><w:numStyleLink w:val=\"Law\"/></w:abstractNum>"
                + "<w:num w:numId=\"9\"><w:abstractNumId w:val=\"8\"/></w:num>";
        convert(new TestDocx().numbering(xml).body(p(numPr("9", 0), r("", t("총강")))));
        HwpxHeader header = new HwpxHeader(xml("Contents/header.xml"));
        Element first = (Element) header.numbering("1").getElementsByTagNameNS(HH, "paraHead").item(0);
        assertEquals("ROMAN_CAPITAL", first.getAttribute("numFormat"));
    }

    @Test
    public void unknownFormatAndMissingNum() throws Exception {
        String xml = "<w:abstractNum w:abstractNumId=\"1\">" + lvl(0, "ordinal", "%1", "", "") + "</w:abstractNum>"
                + "<w:num w:numId=\"1\"><w:abstractNumId w:val=\"1\"/></w:num>";
        convert(new TestDocx().numbering(xml).body(p(numPr("1", 0), r("", t("총강"))),
                p(numPr("5", 0), r("", t("국회")))));
        List<Element> hs = headings();
        assertEquals("NUMBER:1:0", h(hs.get(0)));
        assertEquals("NONE:0:0", h(hs.get(1)));
        assertTrue(result.getWarnings().contains("번호 모양 ordinal는 숫자로 바꿈"));
        assertTrue(result.getWarnings().contains("자동 번호 5를 번호 정의에서 못 찾음"));
    }

    @Test
    public void noNumberingWithoutNumPr() throws Exception {
        convert(new TestDocx().body(p("", r("", t("번호 없음")))));
        assertTrue(!text("Contents/header.xml").contains("hh:numberings"));
        assertTrue(!text("Contents/header.xml").contains("hh:bullets"));
    }

    /** DOCX → HWPX → DOCX에서 번호 모양이 살아 있는지. */
    @Test
    public void roundTrip() throws Exception {
        convert(doc());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        TestConvert.hwpxToDocx(new ByteArrayInputStream(hwpxBytes), out, null);
        Map<String, byte[]> docx = ZipReader.unzip(new ByteArrayInputStream(out.toByteArray()));
        String n = new String(docx.get("word/numbering.xml"), StandardCharsets.UTF_8);
        assertContains(n, "<w:numFmt w:val=\"ganada\"/>");
        assertContains(n, "<w:lvlText w:val=\"%2)\"/>");
        assertContains(n, "<w:lvlText w:val=\"•\"/>");
        assertContains(n, "<w:start w:val=\"5\"/>");
    }
}

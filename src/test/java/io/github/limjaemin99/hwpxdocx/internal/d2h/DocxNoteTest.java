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
import java.util.Map;

import static io.github.limjaemin99.hwpxdocx.internal.d2h.TestDocx.p;
import static io.github.limjaemin99.hwpxdocx.internal.d2h.TestDocx.r;
import static io.github.limjaemin99.hwpxdocx.internal.d2h.TestDocx.t;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertContains;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertEquals;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertTrue;

class DocxNoteTest {

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

    private String section() {
        return new String(hwpx.get("Contents/section0.xml"), StandardCharsets.UTF_8);
    }

    private Document sectionXml() throws Exception {
        return SafeXml.parse(new ByteArrayInputStream(hwpx.get("Contents/section0.xml")), "section0");
    }

    private static String note(String tag, String id, String text) {
        String one = tag.substring(0, tag.length() - 1);
        return "<w:" + one + " w:id=\"" + id + "\"><w:p>"
                + r("<w:vertAlign w:val=\"superscript\"/>", "<w:" + one + "Ref/>") + r("", t(" " + text))
                + "</w:p></w:" + one + ">";
    }

    private static String ref(String tag, String id) {
        String one = tag.substring(0, tag.length() - 1);
        return r("<w:vertAlign w:val=\"superscript\"/>", "<w:" + one + "Reference w:id=\"" + id + "\"/>");
    }

    private static TestDocx footnoteDoc() {
        return new TestDocx().notes("footnotes", note("footnotes", "1", "헌법 제1조 제1항"))
                .body(p("", r("", t("대한민국은 민주공화국이다.")), ref("footnotes", "1"),
                        r("", t(" 대한민국의 주권은 국민에게 있다."))));
    }

    private static Element first(Document d, String local) {
        NodeList l = d.getElementsByTagNameNS(HP, local);
        return l.getLength() == 0 ? null : (Element) l.item(0);
    }

    @Test
    void footnoteBecomesControl() throws Exception {
        convert(footnoteDoc());
        Document d = sectionXml();
        Element fn = first(d, "footNote");
        assertTrue(fn != null);
        assertEquals("1", fn.getAttribute("number"));
        Element num = (Element) fn.getElementsByTagNameNS(HP, "autoNum").item(0);
        assertEquals("FOOTNOTE", num.getAttribute("numType"));
        assertContains(fn.getTextContent(), "헌법 제1조 제1항");
        // 구분선 특수 각주는 옮기지 않음, 각주 자리는 본문 글 사이
        String s = section();
        assertEquals(1, d.getElementsByTagNameNS(HP, "footNote").getLength());
        assertTrue(s.indexOf("민주공화국이다.") < s.indexOf("<hp:footNote "));
        assertTrue(s.indexOf("</hp:footNote>") < s.indexOf("주권은"));
        assertEquals(0, result.getSkippedCount());
        // 각주 문단은 본문 문단 수에 들어가지만 구역 나눔에는 영향 없음
        assertTrue(hwpx.get("Contents/section1.xml") == null);
    }

    @Test
    void defaultNotePrFollowsWord() throws Exception {
        convert(footnoteDoc());
        Document d = sectionXml();
        Element foot = first(d, "footNotePr");
        Element end = first(d, "endNotePr");
        assertEquals("DIGIT", ((Element) foot.getElementsByTagNameNS(HP, "autoNumFormat").item(0)).getAttribute("type"));
        assertEquals("", ((Element) foot.getElementsByTagNameNS(HP, "autoNumFormat").item(0)).getAttribute("suffixChar"));
        // Word 미주 기본 번호는 i, ii
        assertEquals("ROMAN_SMALL", ((Element) end.getElementsByTagNameNS(HP, "autoNumFormat").item(0)).getAttribute("type"));
        assertEquals("END_OF_DOCUMENT", ((Element) end.getElementsByTagNameNS(HP, "placement").item(0)).getAttribute("place"));
    }

    @Test
    void endnoteAndNotePr() throws Exception {
        String sectPr = "<w:sectPr><w:footnotePr><w:pos w:val=\"beneathText\"/><w:numFmt w:val=\"ganada\"/>"
                + "<w:numStart w:val=\"3\"/><w:numRestart w:val=\"eachPage\"/></w:footnotePr>"
                + "<w:endnotePr><w:numFmt w:val=\"upperRoman\"/><w:numRestart w:val=\"eachSect\"/></w:endnotePr>"
                + "<w:pgSz w:w=\"11906\" w:h=\"16838\"/></w:sectPr>";
        TestDocx d = new TestDocx().notes("endnotes", note("endnotes", "2", "헌법 제40조"))
                .bodyWithSection(sectPr, p("", r("", t("국회")), ref("endnotes", "2")));
        d.put("word/settings.xml", "<w:settings " + TestDocx.W_NS + "><w:endnotePr><w:pos w:val=\"sectEnd\"/>"
                + "<w:endnote w:id=\"-1\"/><w:endnote w:id=\"0\"/></w:endnotePr></w:settings>");
        convert(d);
        Document x = sectionXml();
        Element en = first(x, "endNote");
        assertTrue(en != null);
        assertEquals("ENDNOTE", ((Element) en.getElementsByTagNameNS(HP, "autoNum").item(0)).getAttribute("numType"));
        Element foot = first(x, "footNotePr");
        Element end = first(x, "endNotePr");
        assertEquals("HANGUL_SYLLABLE", ((Element) foot.getElementsByTagNameNS(HP, "autoNumFormat").item(0)).getAttribute("type"));
        Element fnum = (Element) foot.getElementsByTagNameNS(HP, "numbering").item(0);
        assertEquals("ON_PAGE", fnum.getAttribute("type"));
        assertEquals("3", fnum.getAttribute("newNum"));
        assertEquals("1", ((Element) foot.getElementsByTagNameNS(HP, "placement").item(0)).getAttribute("beneathText"));
        assertEquals("ROMAN_CAPITAL", ((Element) end.getElementsByTagNameNS(HP, "autoNumFormat").item(0)).getAttribute("type"));
        assertEquals("ON_SECTION", ((Element) end.getElementsByTagNameNS(HP, "numbering").item(0)).getAttribute("type"));
        assertEquals("END_OF_SECTION", ((Element) end.getElementsByTagNameNS(HP, "placement").item(0)).getAttribute("place"));
    }

    @Test
    void pictureInFootnoteUsesNoteRelationships() throws Exception {
        byte[] png = new byte[]{(byte) 0x89, 'P', 'N', 'G'};
        String fn = "<w:footnote w:id=\"1\"><w:p>" + r("", "<w:footnoteRef/>")
                + r("", TestDocx.inline("rId1", 127000, 127000)) + "</w:p></w:footnote>";
        TestDocx d = new TestDocx().notes("footnotes", fn).noteImage("footnotes", "rId1", "media/n1.png", png)
                .body(p("", r("", t("국민")), ref("footnotes", "1")));
        convert(d);
        assertTrue(hwpx.containsKey("BinData/image1.png"));
        assertTrue(section().indexOf("<hp:pic") > section().indexOf("<hp:footNote "));
        assertEquals(1, result.getPictureCount());
    }

    @Test
    void missingNoteWarns() throws Exception {
        convert(new TestDocx().notes("footnotes", note("footnotes", "1", "주석"))
                .body(p("", r("", t("국민")), ref("footnotes", "9"))));
        assertTrue(result.getWarnings().contains("각주 9를 각주 파트에서 못 찾음"));
        assertTrue(!section().contains("<hp:footNote "));
    }

    @Test
    void noteReferenceInHeaderIsSkipped() throws Exception {
        TestDocx d = new TestDocx().notes("footnotes", note("footnotes", "1", "주석"))
                .headerFooter("rId20", "header1.xml", "hdr", p("", r("", t("대한민국헌법")), ref("footnotes", "1")));
        d.bodyWithSection("<w:sectPr><w:headerReference w:type=\"default\" r:id=\"rId20\"/>"
                + "<w:pgSz w:w=\"11906\" w:h=\"16838\"/></w:sectPr>", p("", r("", t("국민"))));
        convert(d);
        assertTrue(!section().contains("<hp:footNote "));
        assertTrue(result.getWarnings().contains("본문 밖(머리말·각주 안)의 각주 참조는 뺌"));
    }

    @Test
    void docxToHwpxToDocxKeepsFootnote() throws Exception {
        convert(footnoteDoc());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        TestConvert.hwpxToDocx(new ByteArrayInputStream(hwpxBytes), out, null);
        Map<String, byte[]> docx = ZipReader.unzip(new ByteArrayInputStream(out.toByteArray()));
        String fn = new String(docx.get("word/footnotes.xml"), StandardCharsets.UTF_8);
        assertContains(fn, "<w:footnoteRef/>");
        assertContains(fn, "헌법 제1조 제1항");
        assertContains(new String(docx.get("word/document.xml"), StandardCharsets.UTF_8), "<w:footnoteReference w:id=\"1\"/>");
        assertContains(new String(docx.get("word/settings.xml"), StandardCharsets.UTF_8), "<w:footnote w:id=\"-1\"/>");
    }
}

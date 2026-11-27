package io.github.limjaemin99.hwpxdocx.internal.d2h;

import io.github.limjaemin99.hwpxdocx.ConvertResult;
import io.github.limjaemin99.hwpxdocx.HwpxDocxConverter;
import io.github.limjaemin99.hwpxdocx.internal.SafeXml;
import io.github.limjaemin99.hwpxdocx.internal.ZipReader;
import io.github.limjaemin99.hwpxdocx.internal.hwpx.HwpxHeader;
import io.github.limjaemin99.hwpxdocx.testkit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

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

class DocxBookmarkTest {

    private static final String HP = HwpxHeader.NS_HP;

    private ConvertResult result;
    private byte[] hwpxBytes;
    private Map<String, byte[]> hwpx;

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

    private static String start(String id, String name) {
        return "<w:bookmarkStart w:id=\"" + id + "\" w:name=\"" + name + "\"/>";
    }

    private static String end(String id) {
        return "<w:bookmarkEnd w:id=\"" + id + "\"/>";
    }

    @Test
    void rangeBookmarkBecomesField() throws Exception {
        convert(new TestDocx().body(
                p("", start("0", "총강"), r("", t("대한민국은 민주공화국이다."))),
                p("", r("", t("대한민국의 주권은 국민에게 있다.")), end("0"))));
        Document d = sectionXml();
        Element begin = (Element) d.getElementsByTagNameNS(HP, "fieldBegin").item(0);
        Element stop = (Element) d.getElementsByTagNameNS(HP, "fieldEnd").item(0);
        assertEquals("BOOKMARK", begin.getAttribute("type"));
        assertEquals("총강", begin.getAttribute("name"));
        assertEquals(begin.getAttribute("id"), stop.getAttribute("beginIDRef"));
        assertContains(section(), "<hp:integerParam name=\"Prop\">2</hp:integerParam>");
        String s = section();
        assertTrue(s.indexOf("<hp:fieldBegin") < s.indexOf("민주공화국"));
        assertTrue(s.indexOf("주권은") < s.indexOf("<hp:fieldEnd"));
        assertEquals(0, result.getSkippedCount());
    }

    @Test
    void emptyBookmarkIsPoint() throws Exception {
        convert(new TestDocx().body(p("", r("", t("국민")), start("1", "국민"), end("1"))));
        assertContains(section(), "<hp:ctrl><hp:bookmark name=\"국민\"/></hp:ctrl>");
        assertTrue(!section().contains("fieldBegin"));
    }

    @Test
    void goBackIgnored() throws Exception {
        convert(new TestDocx().body(p("", start("2", "_GoBack"), r("", t("국민")), end("2"))));
        assertTrue(!section().contains("bookmark") && !section().contains("fieldBegin"));
    }

    @Test
    void hyperlinkKeepsTextAndWarns() throws Exception {
        convert(new TestDocx().body(p("", "<w:hyperlink w:anchor=\"총강\">" + r("", t("총강으로")) + "</w:hyperlink>")));
        assertContains(section(), "총강으로");
        assertTrue(result.getWarnings().contains("링크 주소는 아직 옮기지 않음 (글만 옮김)"));
    }

    @Test
    void hwpxToDocxToHwpxKeepsBookmark() throws Exception {
        convert(new TestDocx().body(p("", start("0", "총강"), r("", t("대한민국은 민주공화국이다.")), end("0"))));
        ByteArrayOutputStream docx = new ByteArrayOutputStream();
        HwpxDocxConverter.hwpxToDocx(new ByteArrayInputStream(hwpxBytes), docx, null);
        Map<String, byte[]> m = ZipReader.unzip(new ByteArrayInputStream(docx.toByteArray()));
        String doc = new String(m.get("word/document.xml"), StandardCharsets.UTF_8);
        assertContains(doc, "w:name=\"총강\"");
        assertContains(doc, "<w:bookmarkEnd");
    }
}

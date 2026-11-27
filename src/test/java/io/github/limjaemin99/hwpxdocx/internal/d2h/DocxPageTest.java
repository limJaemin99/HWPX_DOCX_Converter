package io.github.limjaemin99.hwpxdocx.internal.d2h;

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
import java.util.Map;

import static io.github.limjaemin99.hwpxdocx.internal.d2h.TestDocx.p;
import static io.github.limjaemin99.hwpxdocx.internal.d2h.TestDocx.r;
import static io.github.limjaemin99.hwpxdocx.internal.d2h.TestDocx.t;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertContains;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertEquals;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertTrue;

class DocxPageTest {

    private static final String HP = HwpxHeader.NS_HP;

    private ConvertResult result;
    private Document section;

    private void convert(byte[] docx) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        result = HwpxDocxConverter.docxToHwpx(new ByteArrayInputStream(docx), out, null);
        Map<String, byte[]> m = ZipReader.unzip(new ByteArrayInputStream(out.toByteArray()));
        section = SafeXml.parse(new ByteArrayInputStream(m.get("Contents/section0.xml")), "section");
    }

    private Element first(String local) {
        NodeList l = section.getElementsByTagNameNS(HP, local);
        return l.getLength() == 0 ? null : (Element) l.item(0);
    }

    private Element ctrl(String local, String applyPageType) {
        NodeList l = section.getElementsByTagNameNS(HP, local);
        for (int i = 0; i < l.getLength(); i++) {
            Element e = (Element) l.item(i);
            if (applyPageType.equals(e.getAttribute("applyPageType"))) {
                return e;
            }
        }
        return null;
    }

    private static String text(Element e) {
        StringBuilder b = new StringBuilder();
        NodeList ts = e.getElementsByTagNameNS(HP, "t");
        for (int i = 0; i < ts.getLength(); i++) {
            b.append(ts.item(i).getTextContent());
        }
        return b.toString();
    }

    private static String sect(String inner) {
        return "<w:sectPr>" + inner + "</w:sectPr>";
    }

    private static final String A4 = "<w:pgSz w:w=\"11906\" w:h=\"16838\"/>"
            + "<w:pgMar w:top=\"1701\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\" w:header=\"851\" w:footer=\"992\" w:gutter=\"0\"/>";

    @Test
    void pageSizeAndMargins() throws Exception {
        convert(new TestDocx().body(p("", r("", t("대한민국헌법")))).toBytes());
        Element page = first("pagePr");
        assertEquals("WIDELY", page.getAttribute("landscape"));
        assertEquals("59530", page.getAttribute("width"));
        assertEquals("84190", page.getAttribute("height"));
        Element m = first("margin");
        // 한컴 위 여백 = Word 머리말 거리, 머리말 높이 = Word 위 여백 − 머리말 거리
        assertEquals("4255", m.getAttribute("top"));
        assertEquals("4250", m.getAttribute("header"));
        assertEquals("4960", m.getAttribute("bottom"));
        assertEquals("2240", m.getAttribute("footer"));
        assertEquals("7200", m.getAttribute("left"));
        assertEquals(1, section.getElementsByTagNameNS(HP, "secPr").getLength());
    }

    @Test
    void landscapeColumnsAndStartPage() throws Exception {
        convert(new TestDocx().bodyWithSection(sect("<w:pgSz w:w=\"16838\" w:h=\"11906\" w:orient=\"landscape\"/>"
                        + "<w:pgMar w:top=\"1440\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\" w:header=\"720\" w:footer=\"720\"/>"
                        + "<w:pgNumType w:start=\"3\"/><w:cols w:num=\"2\" w:space=\"425\"/>"),
                p("", r("", t("제1조")))).toBytes());
        Element page = first("pagePr");
        assertEquals("NARROWLY", page.getAttribute("landscape"));
        assertEquals("84190", page.getAttribute("width"));
        assertEquals("2", first("colPr").getAttribute("colCount"));
        assertEquals("2125", first("colPr").getAttribute("sameGap"));
        assertEquals("3", first("startNum").getAttribute("page"));
    }

    @Test
    void headerAndFooterWithPageNumber() throws Exception {
        convert(new TestDocx()
                .headerFooter("rId20", "header1.xml", "hdr", p("<w:jc w:val=\"right\"/>", r("", t("대한민국헌법 [헌법 제10호]"))))
                .headerFooter("rId21", "footer1.xml", "ftr", p("<w:jc w:val=\"center\"/>", r("", t("- ")),
                        "<w:fldSimple w:instr=\" PAGE \"><w:r><w:t>1</w:t></w:r></w:fldSimple>", r("", t(" -"))))
                .bodyWithSection(sect("<w:headerReference w:type=\"default\" r:id=\"rId20\"/>"
                        + "<w:footerReference w:type=\"default\" r:id=\"rId21\"/>" + A4), p("", r("", t("제1조"))))
                .toBytes());
        Element h = ctrl("header", "BOTH");
        assertTrue(h != null);
        assertEquals("대한민국헌법 [헌법 제10호]", text(h));
        Element f = ctrl("footer", "BOTH");
        assertEquals("- -", text(f).replace("  ", " "));
        assertEquals("PAGE", ((Element) f.getElementsByTagNameNS(HP, "autoNum").item(0)).getAttribute("numType"));
        // 머리말 글은 본문 글로 세지 않게 머리말 안에만 있음
        assertEquals(0, result.getSkippedCount());
    }

    @Test
    void evenAndOddHeaders() throws Exception {
        convert(new TestDocx().evenAndOddHeaders()
                .headerFooter("rId20", "header1.xml", "hdr", p("", r("", t("홀수"))))
                .headerFooter("rId21", "header2.xml", "hdr", p("", r("", t("짝수"))))
                .bodyWithSection(sect("<w:headerReference w:type=\"default\" r:id=\"rId20\"/>"
                        + "<w:headerReference w:type=\"even\" r:id=\"rId21\"/>" + A4), p("", r("", t("제1조"))))
                .toBytes());
        assertEquals("홀수", text(ctrl("header", "ODD")));
        assertEquals("짝수", text(ctrl("header", "EVEN")));
        assertEquals(null, ctrl("header", "BOTH"));
    }

    @Test
    void evenHeaderIgnoredWithoutSetting() throws Exception {
        convert(new TestDocx()
                .headerFooter("rId20", "header1.xml", "hdr", p("", r("", t("모든 쪽"))))
                .headerFooter("rId21", "header2.xml", "hdr", p("", r("", t("짝수"))))
                .bodyWithSection(sect("<w:headerReference w:type=\"default\" r:id=\"rId20\"/>"
                        + "<w:headerReference w:type=\"even\" r:id=\"rId21\"/>" + A4), p("", r("", t("제1조"))))
                .toBytes());
        assertEquals("모든 쪽", text(ctrl("header", "BOTH")));
        assertEquals(null, ctrl("header", "EVEN"));
    }

    @Test
    void titlePageHidesFirstHeader() throws Exception {
        convert(new TestDocx()
                .headerFooter("rId20", "header1.xml", "hdr", p("", r("", t("대한민국헌법"))))
                .headerFooter("rId22", "header3.xml", "hdr", p("", r("", t("표지"))))
                .bodyWithSection(sect("<w:headerReference w:type=\"default\" r:id=\"rId20\"/>"
                        + "<w:headerReference w:type=\"first\" r:id=\"rId22\"/>" + A4 + "<w:titlePg/>"), p("", r("", t("제1조"))))
                .toBytes());
        Element vis = first("visibility");
        assertEquals("1", vis.getAttribute("hideFirstHeader"));
        assertEquals("1", vis.getAttribute("hideFirstFooter"));
        assertContains(result.getWarnings().toString(), "첫 쪽");
        assertEquals("대한민국헌법", text(ctrl("header", "BOTH")));
    }

    @Test
    void hwpxHeaderSurvivesRoundTrip() throws Exception {
        byte[] docx = new TestDocx()
                .headerFooter("rId20", "header1.xml", "hdr", p("", r("", t("대한민국헌법"))))
                .bodyWithSection(sect("<w:headerReference w:type=\"default\" r:id=\"rId20\"/>" + A4), p("", r("", t("제1조"))))
                .toBytes();
        ByteArrayOutputStream hwpx = new ByteArrayOutputStream();
        HwpxDocxConverter.docxToHwpx(new ByteArrayInputStream(docx), hwpx, null);
        ByteArrayOutputStream back = new ByteArrayOutputStream();
        ConvertResult r = HwpxDocxConverter.hwpxToDocx(new ByteArrayInputStream(hwpx.toByteArray()), back, null);
        assertEquals(0, r.getSkippedCount());
        Map<String, byte[]> m = ZipReader.unzip(new ByteArrayInputStream(back.toByteArray()));
        assertContains(new String(m.get("word/header1.xml"), "UTF-8"), "대한민국헌법");
        // 여백도 되돌아옴: 위 1701, 머리말 851
        String doc = new String(m.get("word/document.xml"), "UTF-8");
        assertContains(doc, "w:top=\"1701\"");
        assertContains(doc, "w:header=\"851\"");
    }
}

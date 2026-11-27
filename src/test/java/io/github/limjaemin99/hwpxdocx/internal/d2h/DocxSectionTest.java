package io.github.limjaemin99.hwpxdocx.internal.d2h;

import io.github.limjaemin99.hwpxdocx.ConvertResult;
import io.github.limjaemin99.hwpxdocx.HwpxDocxConverter;
import io.github.limjaemin99.hwpxdocx.internal.SafeXml;
import io.github.limjaemin99.hwpxdocx.internal.ZipReader;
import io.github.limjaemin99.hwpxdocx.internal.docx.DocxSkeleton;
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

class DocxSectionTest {

    private static final String HP = HwpxHeader.NS_HP;

    private static final String PORTRAIT = "<w:pgSz w:w=\"11906\" w:h=\"16838\"/>"
            + "<w:pgMar w:top=\"1701\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\" w:header=\"851\" w:footer=\"992\" w:gutter=\"0\"/>";
    private static final String LANDSCAPE = "<w:pgSz w:w=\"16838\" w:h=\"11906\" w:orient=\"landscape\"/>"
            + "<w:pgMar w:top=\"1440\" w:right=\"1701\" w:bottom=\"1440\" w:left=\"1701\" w:header=\"851\" w:footer=\"992\" w:gutter=\"0\"/>";

    private ConvertResult result;
    private Map<String, byte[]> hwpx;

    private void convert(byte[] docx) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        result = HwpxDocxConverter.docxToHwpx(new ByteArrayInputStream(docx), out, null);
        hwpx = ZipReader.unzip(new ByteArrayInputStream(out.toByteArray()));
    }

    private Document section(int i) throws Exception {
        byte[] b = hwpx.get("Contents/section" + i + ".xml");
        assertTrue(b != null);
        return SafeXml.parse(new ByteArrayInputStream(b), "section" + i);
    }

    private static Element first(Document d, String local) {
        NodeList l = d.getElementsByTagNameNS(HP, local);
        return l.getLength() == 0 ? null : (Element) l.item(0);
    }

    private static String text(Element e) {
        StringBuilder b = new StringBuilder();
        NodeList ts = e.getElementsByTagNameNS(HP, "t");
        for (int i = 0; i < ts.getLength(); i++) {
            b.append(ts.item(i).getTextContent());
        }
        return b.toString();
    }

    private static String bodyText(Document d) {
        // 머리말 안 글은 빼고 본문 글만
        StringBuilder b = new StringBuilder();
        NodeList ts = d.getElementsByTagNameNS(HP, "t");
        for (int i = 0; i < ts.getLength(); i++) {
            boolean inHeader = false;
            for (org.w3c.dom.Node n = ts.item(i).getParentNode(); n != null; n = n.getParentNode()) {
                if ("header".equals(n.getLocalName()) || "footer".equals(n.getLocalName())) {
                    inHeader = true;
                    break;
                }
            }
            if (!inHeader) {
                b.append(ts.item(i).getTextContent());
            }
        }
        return b.toString();
    }

    private static String sect(String inner) {
        return "<w:sectPr>" + inner + "</w:sectPr>";
    }

    private static byte[] portraitThenLandscape() throws Exception {
        return new TestDocx()
                .headerFooter("rId20", "header1.xml", "hdr", p("", r("", t("대한민국헌법"))))
                .bodyWithSection(sect(LANDSCAPE + "<w:pgNumType w:start=\"1\"/>"),
                        p("", r("", t("제1조 ①대한민국은 민주공화국이다."))),
                        p(sect("<w:headerReference w:type=\"default\" r:id=\"rId20\"/>" + PORTRAIT),
                                r("", t("②대한민국의 주권은 국민에게 있고, 모든 권력은 국민으로부터 나온다."))),
                        p("", r("", t("제2조 ①대한민국의 국민이 되는 요건은 법률로 정한다."))))
                .toBytes();
    }

    @Test
    void splitsIntoSectionFiles() throws Exception {
        convert(portraitThenLandscape());
        assertEquals(null, hwpx.get("Contents/section2.xml"));
        Document s0 = section(0);
        Document s1 = section(1);
        assertEquals(1, s0.getElementsByTagNameNS(HP, "secPr").getLength());
        assertEquals(1, s1.getElementsByTagNameNS(HP, "secPr").getLength());
        // 구역 끝 문단(sectPr을 가진 문단)까지가 앞 구역
        assertEquals("제1조 ①대한민국은 민주공화국이다.②대한민국의 주권은 국민에게 있고, 모든 권력은 국민으로부터 나온다.",
                bodyText(s0));
        assertEquals("제2조 ①대한민국의 국민이 되는 요건은 법률로 정한다.", bodyText(s1));

        String hpf = new String(hwpx.get("Contents/content.hpf"), StandardCharsets.UTF_8);
        assertContains(hpf, "<opf:item id=\"section1\" href=\"Contents/section1.xml\"");
        assertContains(hpf, "<opf:itemref idref=\"section1\" linear=\"yes\"/>");
        assertContains(new String(hwpx.get("Contents/header.xml"), StandardCharsets.UTF_8), "secCnt=\"2\"");
        assertEquals(0, result.getSkippedCount());
    }

    @Test
    void eachSectionKeepsItsOwnPage() throws Exception {
        convert(portraitThenLandscape());
        Element p0 = first(section(0), "pagePr");
        Element p1 = first(section(1), "pagePr");
        assertEquals("WIDELY", p0.getAttribute("landscape"));
        assertEquals("59530", p0.getAttribute("width"));
        assertEquals("NARROWLY", p1.getAttribute("landscape"));
        assertEquals("84190", p1.getAttribute("width"));
        assertEquals("8505", ((Element) p1.getElementsByTagNameNS(HP, "margin").item(0)).getAttribute("left"));
        assertEquals("0", first(section(0), "startNum").getAttribute("page"));
        assertEquals("1", first(section(1), "startNum").getAttribute("page"));
    }

    @Test
    void inheritedHeaderIsNotRepeated() throws Exception {
        convert(portraitThenLandscape());
        // 둘째 구역은 머리말을 적지 않음: 한컴도 앞 구역 머리말을 이어 쓰므로 다시 두지 않음
        assertEquals("대한민국헌법", text(first(section(0), "header")));
        assertEquals(null, first(section(1), "header"));
    }

    @Test
    void inheritedEvenHeaderKeepsDefaultOnOddPages() throws Exception {
        convert(new TestDocx().evenAndOddHeaders()
                .headerFooter("rId20", "header1.xml", "hdr", p("", r("", t("홀수"))))
                .headerFooter("rId21", "header2.xml", "hdr", p("", r("", t("짝수"))))
                .headerFooter("rId22", "header3.xml", "hdr", p("", r("", t("제2장"))))
                .bodyWithSection(sect("<w:headerReference w:type=\"default\" r:id=\"rId22\"/>" + PORTRAIT),
                        p(sect("<w:headerReference w:type=\"default\" r:id=\"rId20\"/>"
                                + "<w:headerReference w:type=\"even\" r:id=\"rId21\"/>" + PORTRAIT), r("", t("제1조"))),
                        p("", r("", t("제10조"))))
                .toBytes());
        Element h = first(section(1), "header");
        assertEquals("제2장", text(h));
        assertEquals("ODD", h.getAttribute("applyPageType"));
    }

    @Test
    void ownHeaderReplacesInherited() throws Exception {
        convert(new TestDocx()
                .headerFooter("rId20", "header1.xml", "hdr", p("", r("", t("제1장 총강"))))
                .headerFooter("rId21", "header2.xml", "hdr", p("", r("", t("제2장 국민의 권리와 의무"))))
                .bodyWithSection(sect("<w:headerReference w:type=\"default\" r:id=\"rId21\"/>" + PORTRAIT),
                        p(sect("<w:headerReference w:type=\"default\" r:id=\"rId20\"/>" + PORTRAIT), r("", t("제1조"))),
                        p("", r("", t("제10조"))))
                .toBytes());
        assertEquals("제1장 총강", text(first(section(0), "header")));
        assertEquals("제2장 국민의 권리와 의무", text(first(section(1), "header")));
    }

    @Test
    void continuousBreakWarns() throws Exception {
        convert(new TestDocx()
                .bodyWithSection(sect("<w:type w:val=\"continuous\"/>" + PORTRAIT + "<w:cols w:num=\"2\" w:space=\"425\"/>"),
                        p(sect(PORTRAIT), r("", t("제1조"))),
                        p("", r("", t("제2조"))))
                .toBytes());
        assertEquals("2", first(section(1), "colPr").getAttribute("colCount"));
        assertContains(result.getWarnings().toString(), "같은 쪽에서 이어지는 구역 나누기");
    }

    @Test
    void singleSectionUnchanged() throws Exception {
        convert(new TestDocx().bodyWithSection(sect(PORTRAIT), p("", r("", t("제1조")))).toBytes());
        assertEquals(null, hwpx.get("Contents/section1.xml"));
        assertContains(new String(hwpx.get("Contents/header.xml"), StandardCharsets.UTF_8), "secCnt=\"1\"");
        assertEquals(0, result.getWarnings().size());
    }

    @Test
    void sectionBreakInTableCellIgnored() throws Exception {
        // 표 칸 안 문단의 sectPr은 Word도 구역으로 보지 않음
        String tbl = "<w:tbl><w:tblGrid><w:gridCol w:w=\"4000\"/></w:tblGrid><w:tr><w:tc>"
                + p(sect(LANDSCAPE), r("", t("제1조"))) + "</w:tc></w:tr></w:tbl>";
        convert(new TestDocx().bodyWithSection(sect(PORTRAIT), tbl, p("", r("", t("제2조")))).toBytes());
        assertEquals(null, hwpx.get("Contents/section1.xml"));
    }

    @Test
    void sectionsSurviveRoundTrip() throws Exception {
        ByteArrayOutputStream h = new ByteArrayOutputStream();
        HwpxDocxConverter.docxToHwpx(new ByteArrayInputStream(portraitThenLandscape()), h, null);
        ByteArrayOutputStream back = new ByteArrayOutputStream();
        ConvertResult r = HwpxDocxConverter.hwpxToDocx(new ByteArrayInputStream(h.toByteArray()), back, null);
        assertEquals(0, r.getSkippedCount());
        Map<String, byte[]> m = ZipReader.unzip(new ByteArrayInputStream(back.toByteArray()));
        Document doc = SafeXml.parse(new ByteArrayInputStream(m.get("word/document.xml")), "document");
        NodeList sects = doc.getElementsByTagNameNS(DocxSkeleton.NS_W, "sectPr");
        assertEquals(2, sects.getLength());
        Element pgSz = (Element) ((Element) sects.item(1)).getElementsByTagNameNS(DocxSkeleton.NS_W, "pgSz").item(0);
        assertEquals("landscape", pgSz.getAttributeNS(DocxSkeleton.NS_W, "orient"));
    }
}

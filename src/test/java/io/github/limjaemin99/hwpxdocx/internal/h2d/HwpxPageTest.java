package io.github.limjaemin99.hwpxdocx.internal.h2d;

import io.github.limjaemin99.hwpxdocx.testkit.TestConvert;
import io.github.limjaemin99.hwpxdocx.ConvertResult;
import io.github.limjaemin99.hwpxdocx.HwpxDocxConverter;
import io.github.limjaemin99.hwpxdocx.internal.SafeXml;
import io.github.limjaemin99.hwpxdocx.internal.docx.DocxSkeleton;
import io.github.limjaemin99.hwpxdocx.testkit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static io.github.limjaemin99.hwpxdocx.internal.h2d.TestHwpx.para;
import static io.github.limjaemin99.hwpxdocx.internal.h2d.TestHwpx.pic;
import static io.github.limjaemin99.hwpxdocx.internal.h2d.TestHwpx.run;
import static io.github.limjaemin99.hwpxdocx.internal.h2d.TestHwpx.t;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertContains;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertEquals;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertTrue;

class HwpxPageTest {

    private static final String W = DocxSkeleton.NS_W;
    private static final String R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final String PR = "http://schemas.openxmlformats.org/package/2006/relationships";
    private static final String CT = "http://schemas.openxmlformats.org/package/2006/content-types";

    private ConvertResult result;
    private Map<String, byte[]> out;

    private void convert(byte[] hwpx) throws Exception {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        result = TestConvert.hwpxToDocx(new ByteArrayInputStream(hwpx), o, null);
        out = new LinkedHashMap<String, byte[]>();
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(o.toByteArray()))) {
            ZipEntry e;
            byte[] buf = new byte[4096];
            while ((e = z.getNextEntry()) != null) {
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                int n;
                while ((n = z.read(buf)) > 0) {
                    b.write(buf, 0, n);
                }
                out.put(e.getName(), b.toByteArray());
            }
        }
    }

    private Document xml(String name) throws Exception {
        byte[] b = out.get(name);
        if (b == null) {
            throw new AssertionError("파트 없음: " + name + " / " + out.keySet());
        }
        return SafeXml.parse(new ByteArrayInputStream(b), name);
    }

    private Element sectPr() throws Exception {
        NodeList l = xml("word/document.xml").getElementsByTagNameNS(W, "sectPr");
        return (Element) l.item(l.getLength() - 1);
    }

    private static Element first(Element parent, String local) {
        NodeList l = parent.getElementsByTagNameNS(W, local);
        return l.getLength() == 0 ? null : (Element) l.item(0);
    }

    private static String w(Element e, String attr) {
        return e.getAttributeNS(W, attr);
    }

    /** sectPr의 머리말·꼬리말 참조 → 파트 경로. */
    private String part(String what, String type) throws Exception {
        NodeList refs = sectPr().getElementsByTagNameNS(W, what + "Reference");
        String rid = null;
        for (int i = 0; i < refs.getLength(); i++) {
            Element r = (Element) refs.item(i);
            if (type.equals(w(r, "type"))) {
                rid = r.getAttributeNS(R, "id");
            }
        }
        if (rid == null) {
            return null;
        }
        NodeList rels = xml("word/_rels/document.xml.rels").getElementsByTagNameNS(PR, "Relationship");
        for (int i = 0; i < rels.getLength(); i++) {
            Element r = (Element) rels.item(i);
            if (rid.equals(r.getAttribute("Id"))) {
                return "word/" + r.getAttribute("Target");
            }
        }
        throw new AssertionError("관계 없음: " + rid);
    }

    private String text(String part) throws Exception {
        StringBuilder b = new StringBuilder();
        NodeList ts = xml(part).getElementsByTagNameNS(W, "t");
        for (int i = 0; i < ts.getLength(); i++) {
            b.append(ts.item(i).getTextContent());
        }
        return b.toString();
    }

    private static String ctrl(String inner) {
        return "<hp:ctrl>" + inner + "</hp:ctrl>";
    }

    private static String headerCtrl(String tag, String applyPageType, String body) {
        return ctrl("<hp:" + tag + " id=\"1\" applyPageType=\"" + applyPageType + "\">"
                + "<hp:subList id=\"\" textDirection=\"HORIZONTAL\" lineWrap=\"BREAK\" vertAlign=\"TOP\">" + body
                + "</hp:subList></hp:" + tag + ">");
    }

    private static TestHwpx withSection(String from, String to) {
        TestHwpx h = new TestHwpx();
        String sec = h.get("Contents/section0.xml");
        if (!sec.contains(from)) {
            throw new IllegalStateException("골격에 없음: " + from);
        }
        return h.put("Contents/section0.xml", sec.replace(from, to));
    }

    @Test
    void pageSizeAndMarginsFromSkeleton() throws Exception {
        convert(new TestHwpx().toBytes());
        Element s = sectPr();
        assertEquals("11906", w(first(s, "pgSz"), "w"));
        assertEquals("16837", w(first(s, "pgSz"), "h"));
        Element m = first(s, "pgMar");
        // 위 20 mm + 머리말 15 mm = 35 mm, 아래 15 + 15 = 30 mm
        assertEquals("1984", w(m, "top"));
        assertEquals("1701", w(m, "bottom"));
        assertEquals("1701", w(m, "left"));
        assertEquals("1134", w(m, "header"));
        assertEquals("850", w(m, "footer"));
        assertEquals(0, s.getElementsByTagNameNS(W, "headerReference").getLength());
    }

    @Test
    void landscapeSwapsSize() throws Exception {
        convert(withSection("landscape=\"WIDELY\"", "landscape=\"NARROWLY\"").toBytes());
        Element sz = first(sectPr(), "pgSz");
        assertEquals("16837", w(sz, "w"));
        assertEquals("11906", w(sz, "h"));
        assertEquals("landscape", w(sz, "orient"));
    }

    @Test
    void headerFooterAndPageField() throws Exception {
        convert(new TestHwpx().paragraphs(para("0",
                run("0", headerCtrl("header", "BOTH", para("0", run("0", t("대한민국헌법 [헌법 제10호]")))))
                        + run("0", headerCtrl("footer", "BOTH", para("0",
                        run("0", t("쪽 ") + ctrl("<hp:autoNum num=\"1\" numType=\"PAGE\"/>")))))),
                para("0", run("0", t("제1조 ①대한민국은 민주공화국이다."))))
                .toBytes());
        String hdr = part("header", "default");
        assertEquals("대한민국헌법 [헌법 제10호]", text(hdr));
        String ftr = part("footer", "default");
        NodeList fld = xml(ftr).getElementsByTagNameNS(W, "fldSimple");
        assertEquals(1, fld.getLength());
        assertEquals(" PAGE ", w((Element) fld.item(0), "instr"));
        assertEquals(0, result.getSkippedCount());

        NodeList overrides = xml("[Content_Types].xml").getElementsByTagNameNS(CT, "Override");
        boolean headerType = false;
        for (int i = 0; i < overrides.getLength(); i++) {
            Element o = (Element) overrides.item(i);
            headerType |= ("/" + hdr).equals(o.getAttribute("PartName"))
                    && o.getAttribute("ContentType").endsWith("header+xml");
        }
        assertTrue(headerType);
        // 본문에는 머리말 글이 들어가지 않음
        assertTrue(!new String(out.get("word/document.xml"), "UTF-8").contains("헌법 제10호"));
    }

    @Test
    void pageNumberGoesToFooter() throws Exception {
        convert(new TestHwpx().paragraphs(para("0", run("0",
                ctrl("<hp:pageNum pos=\"BOTTOM_CENTER\" formatType=\"DIGIT\" sideChar=\"-\"/>")))).toBytes());
        String ftr = part("footer", "default");
        assertEquals("- 1 -", text(ftr));
        Element p = (Element) xml(ftr).getElementsByTagNameNS(W, "p").item(0);
        assertEquals("center", w(first(p, "jc"), "val"));
        assertEquals(null, part("header", "default"));
    }

    @Test
    void evenPageHeaderTurnsOnEvenAndOdd() throws Exception {
        convert(new TestHwpx().paragraphs(para("0",
                run("0", headerCtrl("header", "ODD", para("0", run("0", t("홀수")))))
                        + run("0", headerCtrl("header", "EVEN", para("0", run("0", t("짝수")))))))
                .toBytes());
        assertEquals("홀수", text(part("header", "default")));
        assertEquals("짝수", text(part("header", "even")));
        assertContains(new String(out.get("word/settings.xml"), "UTF-8"), "<w:evenAndOddHeaders/>");
    }

    @Test
    void oddOnlyHeaderLeavesEvenPagesEmpty() throws Exception {
        convert(new TestHwpx().paragraphs(para("0",
                run("0", headerCtrl("header", "ODD", para("0", run("0", t("홀수")))))))
                .toBytes());
        assertEquals("", text(part("header", "even")));
        assertContains(new String(out.get("word/settings.xml"), "UTF-8"), "<w:evenAndOddHeaders/>");
    }

    @Test
    void hideFirstHeaderUsesTitlePage() throws Exception {
        convert(withSection("hideFirstHeader=\"0\"", "hideFirstHeader=\"1\"").paragraphs(para("0",
                run("0", headerCtrl("header", "BOTH", para("0", run("0", t("대한민국헌법"))))))).toBytes());
        assertTrue(first(sectPr(), "titlePg") != null);
        assertEquals("", text(part("header", "first")));
        assertEquals("대한민국헌법", text(part("header", "default")));
    }

    @Test
    void columnsAndStartPage() throws Exception {
        TestHwpx h = withSection("colCount=\"1\"", "colCount=\"2\"");
        h.put("Contents/section0.xml", h.get("Contents/section0.xml").replace("sameGap=\"0\"", "sameGap=\"2268\"")
                .replace("<hp:startNum pageStartsOn=\"BOTH\" page=\"0\"", "<hp:startNum pageStartsOn=\"BOTH\" page=\"5\""));
        convert(h.toBytes());
        Element cols = first(sectPr(), "cols");
        assertEquals("2", w(cols, "num"));
        assertEquals("454", w(cols, "space"));
        assertEquals("5", w(first(sectPr(), "pgNumType"), "start"));
    }

    @Test
    void pictureInHeaderUsesHeaderRelationships() throws Exception {
        convert(new TestHwpx().binData("image1", "BinData/image1.png", "image/png", HwpxPictureTest.png())
                .paragraphs(para("0", run("0", headerCtrl("header", "BOTH",
                        para("0", run("0", pic("image1", 7200, 3600, true) + t("대한민국헌법")))))),
                        para("0", run("0", pic("image1", 3600, 3600, true))))
                .toBytes());
        assertEquals(0, result.getSkippedCount());
        assertEquals(2, result.getPictureCount());
        String hdr = part("header", "default");
        String rid = ((Element) xml(hdr).getElementsByTagNameNS("http://schemas.openxmlformats.org/drawingml/2006/main", "blip")
                .item(0)).getAttributeNS(R, "embed");
        String relsName = "word/_rels/" + hdr.substring("word/".length()) + ".rels";
        NodeList rels = xml(relsName).getElementsByTagNameNS(PR, "Relationship");
        assertEquals(1, rels.getLength());
        assertEquals(rid, ((Element) rels.item(0)).getAttribute("Id"));
        assertEquals("media/image1.png", ((Element) rels.item(0)).getAttribute("Target"));
        // 본문과 머리말이 같은 그림을 쓰면 파일은 하나
        int media = 0;
        for (String name : out.keySet()) {
            media += name.startsWith("word/media/") ? 1 : 0;
        }
        assertEquals(1, media);
    }

    /** 둘째 구역(section1.xml)을 더함: 가로 방향, 첫 문단에 {@code firstRuns}. spine 순서는 section0 → section1. */
    private static TestHwpx withSecondSection(TestHwpx h, String firstRuns, String... more) {
        String sec0 = h.get("Contents/section0.xml");
        String sec1 = new TestHwpx().get("Contents/section0.xml")
                .replace("landscape=\"WIDELY\"", "landscape=\"NARROWLY\"")
                .replace("<hp:run charPrIDRef=\"0\"><hp:t/></hp:run>", firstRuns);
        int end = sec1.lastIndexOf("</hs:sec>");
        sec1 = sec1.substring(0, end) + String.join("", more) + sec1.substring(end);
        h.put("Contents/section0.xml", sec0);
        h.put("Contents/section1.xml", sec1);
        return h.put("Contents/content.hpf", h.get("Contents/content.hpf")
                .replace("<opf:item id=\"settings\"",
                        "<opf:item id=\"section1\" href=\"Contents/section1.xml\" media-type=\"application/xml\"/>"
                                + "<opf:item id=\"settings\"")
                .replace("<opf:itemref idref=\"section0\" linear=\"yes\"/>",
                        "<opf:itemref idref=\"section0\" linear=\"yes\"/><opf:itemref idref=\"section1\" linear=\"yes\"/>"));
    }

    @Test
    void eachSectionKeepsItsOwnPageSetupAndHeader() throws Exception {
        TestHwpx h = new TestHwpx().paragraphs(
                para("0", run("0", headerCtrl("header", "BOTH", para("0", run("0", t("제1장 총강")))))),
                para("0", run("0", t("제1조 ①대한민국은 민주공화국이다."))));
        withSecondSection(h,
                run("0", headerCtrl("header", "BOTH", para("0", run("0", t("제2장 국민의 권리와 의무")))))
                        + run("0", ctrl("<hp:newNum num=\"5\" numType=\"PAGE\"/>")),
                para("0", run("0", t("제10조 모든 국민은 인간으로서의 존엄과 가치를 가진다."))));
        convert(h.toBytes());
        Document doc = xml("word/document.xml");
        NodeList sects = doc.getElementsByTagNameNS(W, "sectPr");
        assertEquals(2, sects.getLength());
        Element firstSect = (Element) sects.item(0);
        assertEquals("pPr", firstSect.getParentNode().getLocalName());
        assertEquals("", w(first(firstSect, "pgSz"), "orient"));
        Element last = sectPr();
        assertEquals("landscape", w(first(last, "pgSz"), "orient"));
        assertEquals("5", w(first(last, "pgNumType"), "start"));
        assertEquals("제2장 국민의 권리와 의무", text(part("header", "default")));
        // 첫 구역 머리말은 첫 구역 설정에 붙음
        String firstHeaderRid = ((Element) firstSect.getElementsByTagNameNS(W, "headerReference").item(0)).getAttributeNS(R, "id");
        assertTrue(!firstHeaderRid.isEmpty());
        assertEquals(0, result.getSkippedCount());
        assertTrue(!result.getWarnings().toString().contains("구역"));
        // 첫 구역 마지막 문단(구역 설정이 붙은 문단)은 첫 구역 본문
        Element pWithSect = (Element) firstSect.getParentNode().getParentNode();
        StringBuilder t = new StringBuilder();
        NodeList ts = pWithSect.getElementsByTagNameNS(W, "t");
        for (int i = 0; i < ts.getLength(); i++) {
            t.append(ts.item(i).getTextContent());
        }
        assertEquals("제1조 ①대한민국은 민주공화국이다.", t.toString());
    }

    @Test
    void sectionWithoutHeaderInheritsInWord() throws Exception {
        TestHwpx h = new TestHwpx().paragraphs(
                para("0", run("0", headerCtrl("header", "BOTH", para("0", run("0", t("대한민국헌법")))))));
        withSecondSection(h, "<hp:run charPrIDRef=\"0\"><hp:t>제2장</hp:t></hp:run>");
        convert(h.toBytes());
        assertEquals(0, sectPr().getElementsByTagNameNS(W, "headerReference").getLength());
        NodeList sects = xml("word/document.xml").getElementsByTagNameNS(W, "sectPr");
        assertEquals(1, ((Element) sects.item(0)).getElementsByTagNameNS(W, "headerReference").getLength());
    }

    @Test
    void endSectionAddsParagraphWhenNeeded() {
        StringBuilder empty = new StringBuilder();
        HwpxToDocx.endSection(empty, "<w:sectPr/>");
        assertEquals("<w:p><w:pPr><w:sectPr/></w:pPr></w:p>", empty.toString());
        StringBuilder sec = new StringBuilder("<w:p><w:pPr><w:jc w:val=\"both\"/></w:pPr><w:r/></w:p>"
                + "<w:tbl><w:tr><w:tc><w:p><w:pPr></w:pPr></w:p></w:tc></w:tr></w:tbl><w:p><w:pPr><w:rPr/></w:pPr></w:p>");
        HwpxToDocx.endSection(sec, "<w:sectPr/>");
        assertTrue(sec.toString().endsWith("<w:p><w:pPr><w:rPr/><w:sectPr/></w:pPr></w:p>"));
    }
}

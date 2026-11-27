package io.github.limjaemin99.hwpxdocx.internal.h2d;

import io.github.limjaemin99.hwpxdocx.ConvertException;
import io.github.limjaemin99.hwpxdocx.ConvertOptions;
import io.github.limjaemin99.hwpxdocx.ConvertResult;
import io.github.limjaemin99.hwpxdocx.HwpxDocxConverter;
import io.github.limjaemin99.hwpxdocx.internal.SafeXml;
import io.github.limjaemin99.hwpxdocx.internal.docx.DocxSkeleton;
import io.github.limjaemin99.hwpxdocx.internal.hwpx.HwpxSkeleton;
import io.github.limjaemin99.hwpxdocx.testkit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static io.github.limjaemin99.hwpxdocx.internal.h2d.TestHwpx.charPr;
import static io.github.limjaemin99.hwpxdocx.internal.h2d.TestHwpx.margin;
import static io.github.limjaemin99.hwpxdocx.internal.h2d.TestHwpx.para;
import static io.github.limjaemin99.hwpxdocx.internal.h2d.TestHwpx.paraPr;
import static io.github.limjaemin99.hwpxdocx.internal.h2d.TestHwpx.run;
import static io.github.limjaemin99.hwpxdocx.internal.h2d.TestHwpx.t;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertEquals;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertThrows;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertTrue;

class HwpxToDocxTest {

    private static final String W = DocxSkeleton.NS_W;

    private static final String BASE_CHAR = charPr("0", 1000, "", "");
    private static final String BASE_PARA = paraPr("0", "<hh:align horizontal=\"JUSTIFY\" vertical=\"BASELINE\"/>"
            + margin(0, 0, 0, 0, 0) + "<hh:lineSpacing type=\"PERCENT\" value=\"160\" unit=\"HWPUNIT\"/>");

    private ConvertResult result;
    private Document doc;

    private Document convert(byte[] hwpx) throws IOException {
        return convert(hwpx, ConvertOptions.defaults());
    }

    private Document convert(byte[] hwpx, ConvertOptions opt) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        result = HwpxDocxConverter.hwpxToDocx(new ByteArrayInputStream(hwpx), out, opt);
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(out.toByteArray()))) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                if ("word/document.xml".equals(e.getName())) {
                    doc = SafeXml.parse(z, "word/document.xml");
                    return doc;
                }
            }
        }
        throw new AssertionError("document.xml 없음");
    }

    private List<Element> paragraphs() {
        List<Element> list = new ArrayList<Element>();
        NodeList ps = doc.getElementsByTagNameNS(W, "p");
        for (int i = 0; i < ps.getLength(); i++) {
            list.add((Element) ps.item(i));
        }
        return list;
    }

    private static String text(Element p) {
        StringBuilder b = new StringBuilder();
        NodeList ts = p.getElementsByTagNameNS(W, "t");
        for (int i = 0; i < ts.getLength(); i++) {
            b.append(ts.item(i).getTextContent());
        }
        return b.toString();
    }

    private static Element first(Element parent, String local) {
        NodeList l = parent.getElementsByTagNameNS(W, local);
        return l.getLength() == 0 ? null : (Element) l.item(0);
    }

    private static String val(Element parent, String local, String attr) {
        Element e = first(parent, local);
        if (e == null) {
            throw new AssertionError("w:" + local + " 없음");
        }
        return e.getAttributeNS(W, attr);
    }

    /** 첫 문단은 골격의 구역 설정 문단(빈 문단). 붙인 문단은 1부터. */
    private Element p(int i) {
        return paragraphs().get(i);
    }

    private static Element runProps(Element p) {
        Element r = first(p, "r");
        return first(r, "rPr");
    }

    @Test
    void emptySkeletonBecomesOneEmptyParagraph() throws Exception {
        convert(HwpxSkeleton.toBytes());
        assertEquals(1, paragraphs().size());
        assertEquals("", text(p(0)));
        assertEquals(1, result.getParagraphCount());
        assertEquals(0, result.getSkippedCount());
        assertEquals(1, doc.getElementsByTagNameNS(W, "sectPr").getLength());
    }

    @Test
    void textTabLineBreakAndSpaces() throws Exception {
        convert(new TestHwpx().paragraphs(
                para("0", run("0", t("제1조 ①대한민국은<hp:tab/>민주공화국이다.<hp:lineBreak/>②대한민국의"
                        + "<hp:nbSpace/>주권은<hp:fwSpace/>국민에게 있다"))),
                para("0", run("0", t("제3조")), run("0", t(" 대한민국의 영토는 한반도와 그 부속도서로 한다.")))
        ).toBytes());
        assertEquals(3, paragraphs().size());
        assertEquals("제1조 ①대한민국은민주공화국이다.②대한민국의 주권은　국민에게 있다", text(p(1)));
        assertEquals(1, p(1).getElementsByTagNameNS(W, "tab").getLength());
        assertEquals(1, p(1).getElementsByTagNameNS(W, "br").getLength());
        assertEquals("제3조 대한민국의 영토는 한반도와 그 부속도서로 한다.", text(p(2)));
        assertEquals(3, result.getParagraphCount());
    }

    @Test
    void escapesMarkup() throws Exception {
        convert(new TestHwpx().paragraphs(para("0", run("0", t("a &lt; b &amp; c &gt; d")))).toBytes());
        assertEquals("a < b & c > d", text(p(1)));
    }

    @Test
    void xmlEscapeDropsCharactersXmlCannotHold() {
        assertEquals("a&lt;b&amp;c&gt;de", Xml.text("a<b&c>d\u0001e"));
        assertEquals("&quot;x&quot;", Xml.attr("\"x\""));
        assertEquals("가\uD83D\uDE00", Xml.text("가\uD83D\uDE00"));
        assertEquals("ab", Xml.text("a\uD800b"));
        assertEquals("a\tb\n", Xml.text("a\tb\n\uFFFF"));
    }

    @Test
    void charShape() throws Exception {
        String styled = charPr("1", 1600, "textColor=\"#FF0000\" shadeColor=\"#FFFF00\"",
                "<hh:ratio hangul=\"90\" latin=\"90\" hanja=\"90\" japanese=\"90\" other=\"90\" symbol=\"90\" user=\"90\"/>"
                        + "<hh:spacing hangul=\"-5\" latin=\"-5\" hanja=\"-5\" japanese=\"-5\" other=\"-5\" symbol=\"-5\" user=\"-5\"/>"
                        + "<hh:bold/><hh:italic/>"
                        + "<hh:underline type=\"BOTTOM\" shape=\"DOT\" color=\"#0000FF\"/>"
                        + "<hh:strikeout shape=\"SOLID\" color=\"#000000\"/>"
                        + "<hh:supscript/>");
        convert(new TestHwpx().charPrs(BASE_CHAR, styled)
                .paragraphs(para("0", run("1", t("대한민국은 민주공화국이다.")))).toBytes());
        Element rPr = runProps(p(1));
        assertEquals("32", val(rPr, "sz", "val"));
        assertEquals("FF0000", val(rPr, "color", "val"));
        assertEquals("FFFF00", val(rPr, "shd", "fill"));
        assertEquals("90", val(rPr, "w", "val"));
        assertEquals("-16", val(rPr, "spacing", "val"));
        assertEquals("dotted", val(rPr, "u", "val"));
        assertEquals("0000FF", val(rPr, "u", "color"));
        assertEquals("superscript", val(rPr, "vertAlign", "val"));
        assertTrue(first(rPr, "b") != null);
        assertTrue(first(rPr, "i") != null);
        assertTrue(first(rPr, "strike") != null);
        // 기본 모양에는 덧붙는 속성이 없음
        convert(new TestHwpx().paragraphs(para("0", run("0", t("가")))).toBytes());
        Element plain = runProps(p(1));
        assertEquals("20", val(plain, "sz", "val"));
        assertEquals(null, first(plain, "b"));
        assertEquals(null, first(plain, "color"));
        assertEquals(null, first(plain, "u"));
    }

    @Test
    void hftFontMapsToTtf() throws Exception {
        String c = charPr("1", 1000, "", "").replace("<hh:fontRef hangul=\"0\"", "<hh:fontRef hangul=\"1\"");
        convert(new TestHwpx().hangulFonts("한양신명조").charPrs(BASE_CHAR, c)
                .paragraphs(para("0", run("1", t("가")))).toBytes());
        assertEquals("HY신명조", val(runProps(p(1)), "rFonts", "eastAsia"));
        assertEquals("함초롬바탕", val(runProps(p(1)), "rFonts", "ascii"));
    }

    @Test
    void paraShape() throws Exception {
        String centered = paraPr("1", "<hh:align horizontal=\"CENTER\" vertical=\"BASELINE\"/>"
                + "<hh:breakSetting breakLatinWord=\"KEEP_WORD\" breakNonLatinWord=\"KEEP_WORD\" widowOrphan=\"1\""
                + " keepWithNext=\"1\" keepLines=\"0\" pageBreakBefore=\"0\" lineWrap=\"BREAK\"/>"
                + margin(-2000, 4000, 1000, 1000, 2000)
                + "<hh:lineSpacing type=\"PERCENT\" value=\"160\" unit=\"HWPUNIT\"/>");
        String fixed = paraPr("2", margin(3000, 0, 0, 0, 0)
                + "<hh:lineSpacing type=\"FIXED\" value=\"2000\" unit=\"HWPUNIT\"/>");
        convert(new TestHwpx().paraPrs(BASE_PARA, centered, fixed).paragraphs(
                para("1", run("0", t("제4조 대한민국은 통일을 지향한다."))),
                para("2", run("0", t("제9조 국가는 전통문화의 계승ㆍ발전에 노력하여야 한다.")))).toBytes());
        Element p1 = first(p(1), "pPr");
        assertEquals("center", val(p1, "jc", "val"));
        assertEquals("200", val(p1, "spacing", "before"));
        assertEquals("400", val(p1, "spacing", "after"));
        assertEquals("320", val(p1, "spacing", "line"));
        assertEquals("exact", val(p1, "spacing", "lineRule"));
        assertEquals("1200", val(p1, "ind", "left"));
        assertEquals("200", val(p1, "ind", "right"));
        assertEquals("400", val(p1, "ind", "hanging"));
        assertEquals("1", val(p1, "widowControl", "val"));
        assertTrue(first(p1, "keepNext") != null);

        Element p2 = first(p(2), "pPr");
        assertEquals("both", val(p2, "jc", "val"));
        assertEquals("600", val(p2, "ind", "firstLine"));
        assertEquals("400", val(p2, "spacing", "line"));
        assertEquals("exact", val(p2, "spacing", "lineRule"));
    }

    @Test
    void percentLineSpacingUsesLargestCharInParagraph() throws Exception {
        convert(new TestHwpx().charPrs(BASE_CHAR, charPr("1", 2000, "", ""))
                .paragraphs(para("0", run("0", t("가")), run("1", t("나")))).toBytes());
        // 20 pt × 160% = 32 pt = 640 twip
        assertEquals("640", val(first(p(1), "pPr"), "spacing", "line"));
        // 문단 기호는 마지막 런의 모양
        Element markRPr = first(first(p(1), "pPr"), "rPr");
        assertEquals("40", val(markRPr, "sz", "val"));
    }

    @Test
    void switchCaseIsRealValueAndDefaultIsDoubled() throws Exception {
        String sw = paraPr("1", "<hp:switch><hp:case hp:required-namespace=\"http://www.hancom.co.kr/hwpml/2016/HwpUnitChar\">"
                + margin(0, 1000, 0, 0, 0) + "<hh:lineSpacing type=\"PERCENT\" value=\"160\" unit=\"HWPUNIT\"/>"
                + "</hp:case><hp:default>" + margin(0, 2000, 0, 0, 0)
                + "<hh:lineSpacing type=\"PERCENT\" value=\"160\" unit=\"HWPUNIT\"/></hp:default></hp:switch>");
        String onlyDefault = paraPr("2", "<hp:switch><hp:default>" + margin(0, 2000, 0, 0, 0)
                + "<hh:lineSpacing type=\"FIXED\" value=\"4000\" unit=\"HWPUNIT\"/></hp:default></hp:switch>");
        convert(new TestHwpx().paraPrs(BASE_PARA, sw, onlyDefault)
                .paragraphs(para("1", run("0", t("가"))), para("2", run("0", t("나")))).toBytes());
        assertEquals("200", val(first(p(1), "pPr"), "ind", "left"));
        assertEquals("200", val(first(p(2), "pPr"), "ind", "left"));
        assertEquals("400", val(first(p(2), "pPr"), "spacing", "line"));
    }

    @Test
    void pageBreakAttribute() throws Exception {
        convert(new TestHwpx().paragraphs(para("0", run("0", t("제2장")))
                .replace("pageBreak=\"0\"", "pageBreak=\"1\"")).toBytes());
        assertTrue(first(first(p(1), "pPr"), "pageBreakBefore") != null);
    }

    @Test
    void unsupportedElementsAreCounted() throws Exception {
        byte[] hwpx = new TestHwpx().paragraphs(
                para("0", run("0", "<hp:tbl/>"), run("0", t("표 뒤 글"))),
                para("0", run("0", "<hp:tbl/><hp:pic/>"))).toBytes();
        convert(hwpx);
        assertEquals(Integer.valueOf(2), result.getSkipped().get("hp:tbl"));
        assertEquals(Integer.valueOf(1), result.getSkipped().get("hp:pic"));
        assertEquals("표 뒤 글", text(p(1)));
        assertThrows(ConvertException.class, () -> convert(hwpx, ConvertOptions.builder().failOnUnsupported(true).build()));
    }

    @Test
    void sectionsFollowSpineOrder() throws Exception {
        TestHwpx h = new TestHwpx().paragraphs(para("0", run("0", t("둘째"))));
        String sec1 = h.get("Contents/section0.xml").replace("둘째", "첫째");
        h.put("Contents/section1.xml", sec1);
        h.put("Contents/content.hpf", h.get("Contents/content.hpf")
                .replace("<opf:item id=\"section0\"",
                        "<opf:item id=\"section1\" href=\"Contents/section1.xml\" media-type=\"application/xml\"/>"
                                + "<opf:item id=\"section0\"")
                .replace("<opf:itemref idref=\"section0\" linear=\"yes\"/>",
                        "<opf:itemref idref=\"section1\" linear=\"yes\"/><opf:itemref idref=\"section0\" linear=\"yes\"/>"));
        convert(h.toBytes());
        List<String> texts = new ArrayList<String>();
        for (Element p : paragraphs()) {
            if (!text(p).isEmpty()) {
                texts.add(text(p));
            }
        }
        assertEquals("[첫째, 둘째]", texts.toString());
    }

    @Test
    void rejectsNonHwpxPackages() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(out)) {
            z.putNextEntry(new ZipEntry("word/document.xml"));
            z.write("<a/>".getBytes(StandardCharsets.UTF_8));
            z.closeEntry();
        }
        assertThrows(ConvertException.class, () -> convert(out.toByteArray()));
        byte[] wrongMime = new TestHwpx().put("mimetype", "application/zip").toBytes();
        assertThrows(ConvertException.class, () -> convert(wrongMime));
    }

    @Test
    void outputPartsAreValidXml() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        HwpxDocxConverter.hwpxToDocx(new ByteArrayInputStream(new TestHwpx().paragraphs(
                para("0", run("0", t("제10조 모든 국민은 인간으로서의 존엄과 가치를 가진다.")))).toBytes()), out, null);
        int parts = 0;
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(out.toByteArray()))) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                byte[] b = readAll(z);
                Node root = SafeXml.parse(new ByteArrayInputStream(b), e.getName()).getDocumentElement();
                assertTrue(root != null);
                parts++;
            }
        }
        assertEquals(DocxSkeleton.entries().size(), parts);
    }

    private static byte[] readAll(ZipInputStream z) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = z.read(buf)) > 0) {
            b.write(buf, 0, n);
        }
        return b.toByteArray();
    }
}

package io.github.limjaemin99.hwpxdocx.internal.h2d;

import io.github.limjaemin99.hwpxdocx.testkit.TestConvert;
import io.github.limjaemin99.hwpxdocx.ConvertResult;
import io.github.limjaemin99.hwpxdocx.HwpxDocxConverter;
import io.github.limjaemin99.hwpxdocx.internal.SafeXml;
import io.github.limjaemin99.hwpxdocx.internal.ZipReader;
import io.github.limjaemin99.hwpxdocx.testkit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static io.github.limjaemin99.hwpxdocx.internal.h2d.TestHwpx.para;
import static io.github.limjaemin99.hwpxdocx.internal.h2d.TestHwpx.pic;
import static io.github.limjaemin99.hwpxdocx.internal.h2d.TestHwpx.run;
import static io.github.limjaemin99.hwpxdocx.internal.h2d.TestHwpx.t;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertContains;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertEquals;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertTrue;

class HwpxNoteTest {

    private ConvertResult result;
    private Map<String, byte[]> out;

    private void convert(TestHwpx h) throws Exception {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        result = TestConvert.hwpxToDocx(new ByteArrayInputStream(h.toBytes()), o, null);
        out = ZipReader.unzip(new ByteArrayInputStream(o.toByteArray()));
        for (Map.Entry<String, byte[]> e : out.entrySet()) {
            if (e.getKey().endsWith(".xml") || e.getKey().endsWith(".rels")) {
                SafeXml.parse(new ByteArrayInputStream(e.getValue()), e.getKey());
            }
        }
    }

    private String text(String name) {
        byte[] b = out.get(name);
        if (b == null) {
            throw new AssertionError("파트 없음: " + name + " / " + out.keySet());
        }
        return new String(b, StandardCharsets.UTF_8);
    }

    private static String autoNum(String numType, String suffix, int supscript) {
        return "<hp:ctrl><hp:autoNum num=\"1\" numType=\"" + numType + "\"><hp:autoNumFormat type=\"DIGIT\" userChar=\"\""
                + " prefixChar=\"\" suffixChar=\"" + suffix + "\" supscript=\"" + supscript + "\"/></hp:autoNum></hp:ctrl>";
    }

    private static String note(String tag, String... paras) {
        return "<hp:ctrl><hp:" + tag + " number=\"1\" suffixChar=\"41\" instId=\"1\">"
                + "<hp:subList id=\"\" textDirection=\"HORIZONTAL\" lineWrap=\"BREAK\" vertAlign=\"TOP\">"
                + String.join("", paras) + "</hp:subList></hp:" + tag + "></hp:ctrl>";
    }

    private static String notePr(String tag, String fmt, String numbering, int newNum, String lineType, String place,
                                 int beneath) {
        return "<hp:" + tag + "><hp:autoNumFormat type=\"" + fmt + "\" userChar=\"\" prefixChar=\"\" suffixChar=\")\""
                + " supscript=\"0\"/><hp:noteLine length=\"-1\" type=\"" + lineType + "\" width=\"0.12 mm\" color=\"#000000\"/>"
                + "<hp:noteSpacing betweenNotes=\"283\" belowLine=\"567\" aboveLine=\"850\"/>"
                + "<hp:numbering type=\"" + numbering + "\" newNum=\"" + newNum + "\"/>"
                + "<hp:placement place=\"" + place + "\" beneathText=\"" + beneath + "\"/></hp:" + tag + ">";
    }

    private static TestHwpx withNotePr(TestHwpx h, String prs) {
        String sec = h.get("Contents/section0.xml");
        // 골격의 기본 각주·미주 모양을 바꿔 넣음
        return h.put("Contents/section0.xml", sec.replaceFirst("(?s)<hp:footNotePr>.*</hp:endNotePr>",
                java.util.regex.Matcher.quoteReplacement(prs)));
    }

    private static TestHwpx footnoteDoc() {
        return new TestHwpx().paragraphs(para("0", run("0", t("대한민국은 민주공화국이다.")
                + note("footNote", para("0", run("0", autoNum("FOOTNOTE", ")", 0) + t(" 헌법 제1조 제1항"))))
                + t(" 대한민국의 주권은 국민에게 있다."))));
    }

    @Test
    void footnoteBecomesFootnotePart() throws Exception {
        convert(footnoteDoc());
        String doc = text("word/document.xml");
        assertContains(doc, "<w:vertAlign w:val=\"superscript\"/></w:rPr><w:footnoteReference w:id=\"1\"/>");
        // 각주 앞뒤 본문은 그대로 이어짐
        assertTrue(doc.indexOf("민주공화국이다.") < doc.indexOf("footnoteReference"));
        assertTrue(doc.indexOf("footnoteReference") < doc.indexOf("주권은"));
        String fn = text("word/footnotes.xml");
        assertContains(fn, "<w:footnote w:type=\"separator\" w:id=\"-1\">");
        assertContains(fn, "<w:r><w:separator/></w:r>");
        assertContains(fn, "<w:footnote w:type=\"continuationSeparator\" w:id=\"0\">");
        assertContains(fn, "<w:footnote w:id=\"1\">");
        assertContains(fn, "<w:footnoteRef/>");
        assertContains(fn, ">)</w:t>");
        assertContains(fn, "헌법 제1조 제1항");
        assertTrue(!fn.contains("footnoteRef/></w:r><w:r><w:rPr><w:vertAlign"));
        assertContains(text("[Content_Types].xml"), "/word/footnotes.xml");
        assertContains(text("word/_rels/document.xml.rels"), "relationships/footnotes\" Target=\"footnotes.xml\"");
        assertContains(text("word/settings.xml"), "<w:footnotePr><w:footnote w:id=\"-1\"/><w:footnote w:id=\"0\"/></w:footnotePr>");
        assertTrue(!out.containsKey("word/endnotes.xml"));
        assertEquals(0, result.getSkippedCount());
    }

    @Test
    void superscriptNumberInNote() throws Exception {
        convert(new TestHwpx().paragraphs(para("0", run("0", t("국민")
                + note("footNote", para("0", run("0", autoNum("FOOTNOTE", "", 1) + t("주석"))))))));
        assertContains(text("word/footnotes.xml"), "<w:vertAlign w:val=\"superscript\"/></w:rPr><w:footnoteRef/>");
    }

    @Test
    void endnoteAndSectionNumbering() throws Exception {
        TestHwpx h = new TestHwpx().paragraphs(para("0", run("0", t("국회")
                + note("endNote", para("0", run("0", autoNum("ENDNOTE", ")", 0) + t("헌법 제40조")))))));
        withNotePr(h, notePr("footNotePr", "DIGIT", "ON_PAGE", 1, "SOLID", "EACH_COLUMN", 1)
                + notePr("endNotePr", "ROMAN_SMALL", "ON_SECTION", 3, "NONE", "END_OF_SECTION", 0));
        convert(h);
        assertContains(text("word/document.xml"), "<w:endnoteReference w:id=\"1\"/>");
        String en = text("word/endnotes.xml");
        assertContains(en, "<w:endnoteRef/>");
        // 구분선 없음: 구분선 특수 미주는 빈 문단
        assertTrue(!en.contains("<w:separator/>"));
        String doc = text("word/document.xml");
        assertContains(doc, "<w:footnotePr><w:pos w:val=\"beneathText\"/><w:numFmt w:val=\"decimal\"/>"
                + "<w:numStart w:val=\"1\"/><w:numRestart w:val=\"eachPage\"/></w:footnotePr>"
                + "<w:endnotePr><w:numFmt w:val=\"lowerRoman\"/><w:numStart w:val=\"3\"/>"
                + "<w:numRestart w:val=\"eachSect\"/></w:endnotePr>");
        // sectPr 순서: 머리말 참조 뒤, 용지 크기 앞
        assertTrue(doc.indexOf("</w:endnotePr>") < doc.indexOf("<w:pgSz"));
        assertContains(text("word/settings.xml"), "<w:endnotePr><w:pos w:val=\"sectEnd\"/><w:endnote w:id=\"-1\"/>");
        assertTrue(text("word/settings.xml").indexOf("</w:endnotePr>") < text("word/settings.xml").indexOf("<w:compat>"));
    }

    @Test
    void noteWithoutNumberGetsReference() throws Exception {
        convert(new TestHwpx().paragraphs(para("0", run("0", t("국민")
                + note("footNote", para("0", run("0", t("번호 없는 각주"))))))));
        String fn = text("word/footnotes.xml");
        assertTrue(fn.indexOf("<w:footnoteRef/>") < fn.indexOf("번호 없는 각주"));
    }

    @Test
    void pictureInNoteUsesNoteRelationships() throws Exception {
        convert(new TestHwpx().binData("image1", "BinData/image1.png", "image/png", HwpxPictureTest.png())
                .paragraphs(para("0", run("0", t("국민") + note("footNote",
                        para("0", run("0", autoNum("FOOTNOTE", ")", 0) + pic("image1", 3600, 3600, true))))))));
        assertContains(text("word/_rels/footnotes.xml.rels"), "Target=\"media/image1.png\"");
        assertTrue(!text("word/_rels/document.xml.rels").contains("media/image1.png"));
    }

    @Test
    void noteInHeaderIsSkipped() throws Exception {
        convert(new TestHwpx().paragraphs(para("0", run("0", "<hp:ctrl><hp:header id=\"1\" applyPageType=\"BOTH\">"
                + "<hp:subList id=\"\" textDirection=\"HORIZONTAL\" lineWrap=\"BREAK\" vertAlign=\"TOP\">"
                + para("0", run("0", t("대한민국헌법") + note("footNote", para("0", run("0", t("주석"))))))
                + "</hp:subList></hp:header></hp:ctrl>"))));
        assertTrue(!out.containsKey("word/footnotes.xml"));
        assertTrue(result.getWarnings().contains("머리말·꼬리말·각주 안의 각주는 Word에 둘 수 없어 뺌"));
    }

    @Test
    void noNotesNoParts() throws Exception {
        convert(new TestHwpx().paragraphs(para("0", run("0", t("각주 없음")))));
        assertTrue(!out.containsKey("word/footnotes.xml"));
        assertTrue(!text("word/settings.xml").contains("footnotePr"));
    }

    @Test
    void superscriptKeepsSchemaOrder() {
        assertEquals("<w:rPr><w:b/><w:vertAlign w:val=\"superscript\"/><w:em w:val=\"dot\"/></w:rPr>",
                HwpxToDocx.superscript("<w:rPr><w:b/><w:vertAlign w:val=\"subscript\"/><w:em w:val=\"dot\"/></w:rPr>"));
    }
}

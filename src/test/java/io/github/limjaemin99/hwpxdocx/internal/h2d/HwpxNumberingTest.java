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
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static io.github.limjaemin99.hwpxdocx.internal.h2d.TestHwpx.para;
import static io.github.limjaemin99.hwpxdocx.internal.h2d.TestHwpx.paraPr;
import static io.github.limjaemin99.hwpxdocx.internal.h2d.TestHwpx.run;
import static io.github.limjaemin99.hwpxdocx.internal.h2d.TestHwpx.t;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertContains;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertEquals;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertTrue;

public class HwpxNumberingTest {

    private static final String W = DocxSkeleton.NS_W;

    private Map<String, byte[]> out;
    private ConvertResult result;

    private void convert(TestHwpx h) throws Exception {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        result = TestConvert.hwpxToDocx(new ByteArrayInputStream(h.toBytes()), o, null);
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

    private String text(String name) {
        return new String(out.get(name), StandardCharsets.UTF_8);
    }

    private static String head(int level, String fmt, int start, String text) {
        return "<hh:paraHead start=\"" + start + "\" level=\"" + level + "\" align=\"LEFT\" useInstWidth=\"1\""
                + " autoIndent=\"1\" widthAdjust=\"0\" textOffsetType=\"PERCENT\" textOffset=\"50\""
                + " numFormat=\"" + fmt + "\" charPrIDRef=\"4294967295\" checkable=\"0\">" + text + "</hh:paraHead>";
    }

    private static String heading(String type, String idRef, int level) {
        return "<hh:heading type=\"" + type + "\" idRef=\"" + idRef + "\" level=\"" + level + "\"/>";
    }

    /** 번호 2개(개요 1, 문단 번호 2)와 글머리표 1개, 문단 모양 0~4. */
    private static TestHwpx doc() {
        TestHwpx h = new TestHwpx();
        String refs = "<hh:numberings itemCnt=\"2\">"
                + "<hh:numbering id=\"1\" start=\"0\">" + head(1, "DIGIT", 1, "^1.") + head(2, "HANGUL_SYLLABLE", 1, "^2.")
                + head(3, "DIGIT", 1, "^3)") + "</hh:numbering>"
                + "<hh:numbering id=\"2\" start=\"0\">" + head(1, "CIRCLED_DIGIT", 3, "^1") + "</hh:numbering>"
                + "</hh:numberings>"
                + "<hh:bullets itemCnt=\"1\"><hh:bullet id=\"1\" char=\"□\" useImage=\"0\">"
                + "<hh:paraHead level=\"0\" align=\"LEFT\" useInstWidth=\"0\" autoIndent=\"1\" widthAdjust=\"0\""
                + " textOffsetType=\"PERCENT\" textOffset=\"50\" numFormat=\"DIGIT\" charPrIDRef=\"4294967295\""
                + " checkable=\"0\"/></hh:bullet></hh:bullets>";
        h.put("Contents/header.xml", h.get("Contents/header.xml").replace("<hh:paraProperties", refs + "<hh:paraProperties"));
        // 한글 저장 문서는 개요 번호 모양 1을 가리킴 (골격은 0)
        h.put("Contents/section0.xml", h.get("Contents/section0.xml").replace("outlineShapeIDRef=\"0\"", "outlineShapeIDRef=\"1\""));
        h.paraPrs(paraPr("0", heading("NONE", "0", 0)),
                paraPr("1", heading("OUTLINE", "0", 0)),
                paraPr("2", heading("OUTLINE", "0", 1)),
                paraPr("3", heading("NUMBER", "2", 0)),
                paraPr("4", heading("BULLET", "1", 0)));
        return h.paragraphs(
                para("1", run("0", t("총강"))),
                para("2", run("0", t("대한민국은 민주공화국이다."))),
                para("3", run("0", t("국민의 권리와 의무"))),
                para("4", run("0", t("모든 국민은 인간으로서의 존엄과 가치를 가진다."))),
                para("0", run("0", t("번호 없음"))));
    }

    private static Element numPr(Document d, int paragraph) {
        Element p = (Element) d.getElementsByTagNameNS(W, "p").item(paragraph);
        NodeList l = p.getElementsByTagNameNS(W, "numPr");
        return l.getLength() == 0 ? null : (Element) l.item(0);
    }

    private static String val(Element parent, String local) {
        return ((Element) parent.getElementsByTagNameNS(W, local).item(0)).getAttributeNS(W, "val");
    }

    @Test
    public void headingToNumPr() throws Exception {
        convert(doc());
        Document d = xml("word/document.xml");
        // 0번 문단은 구역 설정이 든 빈 문단
        Element outline1 = numPr(d, 1);
        Element outline2 = numPr(d, 2);
        Element number = numPr(d, 3);
        Element bullet = numPr(d, 4);
        assertEquals("0", val(outline1, "ilvl"));
        assertEquals("1", val(outline2, "ilvl"));
        assertEquals(val(outline1, "numId"), val(outline2, "numId"));
        assertTrue(!val(number, "numId").equals(val(outline1, "numId")));
        assertTrue(!val(bullet, "numId").equals(val(number, "numId")));
        assertTrue(numPr(d, 5) == null);
        for (String w : result.getWarnings()) {
            assertTrue(!w.contains("문단 번호"));
        }
    }

    @Test
    public void numberingPart() throws Exception {
        convert(doc());
        String n = text("word/numbering.xml");
        assertContains(n, "<w:numFmt w:val=\"ganada\"/>");
        assertContains(n, "<w:lvlText w:val=\"%2.\"/>");
        assertContains(n, "<w:lvlText w:val=\"%3)\"/>");
        assertContains(n, "<w:numFmt w:val=\"decimalEnclosedCircle\"/>");
        assertContains(n, "<w:start w:val=\"3\"/>");
        assertContains(n, "<w:lvlText w:val=\"□\"/>");
        assertContains(text("[Content_Types].xml"), "/word/numbering.xml");
        assertContains(text("word/_rels/document.xml.rels"), "numbering.xml");
        xml("word/numbering.xml");
    }

    @Test
    public void noNumberingPartWithoutHeading() throws Exception {
        TestHwpx h = new TestHwpx().paragraphs(para("0", run("0", t("번호 없음"))));
        convert(h);
        assertTrue(!out.containsKey("word/numbering.xml"));
    }

    @Test
    public void ideographIsChineseDigit() throws Exception {
        TestHwpx h = new TestHwpx();
        h.put("Contents/header.xml", h.get("Contents/header.xml").replace("<hh:paraProperties",
                "<hh:numberings itemCnt=\"1\"><hh:numbering id=\"1\" start=\"0\">" + head(1, "IDEOGRAPH", 1, "^1.")
                        + "</hh:numbering></hh:numberings><hh:paraProperties"));
        h.paraPrs(paraPr("0", heading("NONE", "0", 0)), paraPr("1", heading("NUMBER", "1", 0)));
        convert(h.paragraphs(para("1", run("0", t("총강")))));
        // 한컴 IDEOGRAPH는 一, 二, 三 (Word ideographTraditional은 甲, 乙, 丙)
        assertContains(text("word/numbering.xml"), "<w:numFmt w:val=\"ideographDigital\"/>");
    }

    @Test
    public void levelText() {
        assertEquals("%1.%2.", DocxNumbering.levelText("^1.^2."));
        assertEquals("(%9)", DocxNumbering.levelText("(^10)"));
        assertEquals("100%%", DocxNumbering.levelText("100%"));
    }
}

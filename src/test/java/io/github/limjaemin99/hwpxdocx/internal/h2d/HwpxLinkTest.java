package io.github.limjaemin99.hwpxdocx.internal.h2d;

import io.github.limjaemin99.hwpxdocx.ConvertResult;
import io.github.limjaemin99.hwpxdocx.HwpxDocxConverter;
import io.github.limjaemin99.hwpxdocx.internal.SafeXml;
import io.github.limjaemin99.hwpxdocx.internal.ZipReader;
import io.github.limjaemin99.hwpxdocx.internal.docx.DocxSkeleton;
import io.github.limjaemin99.hwpxdocx.testkit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static io.github.limjaemin99.hwpxdocx.internal.h2d.TestHwpx.para;
import static io.github.limjaemin99.hwpxdocx.internal.h2d.TestHwpx.run;
import static io.github.limjaemin99.hwpxdocx.internal.h2d.TestHwpx.t;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertContains;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertEquals;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertTrue;

class HwpxLinkTest {

    private static final String W = DocxSkeleton.NS_W;

    private ConvertResult result;
    private Map<String, byte[]> out;

    private void convert(TestHwpx h) throws Exception {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        result = HwpxDocxConverter.hwpxToDocx(new ByteArrayInputStream(h.toBytes()), o, null);
        out = ZipReader.unzip(new ByteArrayInputStream(o.toByteArray()));
        for (Map.Entry<String, byte[]> e : out.entrySet()) {
            if (e.getKey().endsWith(".xml") || e.getKey().endsWith(".rels")) {
                SafeXml.parse(new ByteArrayInputStream(e.getValue()), e.getKey());
            }
        }
    }

    private String text(String name) {
        return new String(out.get(name), StandardCharsets.UTF_8);
    }

    private Document doc() throws Exception {
        return SafeXml.parse(new ByteArrayInputStream(out.get("word/document.xml")), "document");
    }

    private static String bookmarkBegin(String id, String name) {
        return "<hp:ctrl><hp:fieldBegin id=\"" + id + "\" type=\"BOOKMARK\" name=\"" + name + "\" editable=\"0\" dirty=\"0\""
                + " zorder=\"-1\" fieldid=\"" + id + "\"><hp:parameters cnt=\"1\"><hp:integerParam name=\"Prop\">2"
                + "</hp:integerParam></hp:parameters></hp:fieldBegin></hp:ctrl>";
    }

    private static String linkBegin(String id, String command, String path) {
        return "<hp:ctrl><hp:fieldBegin id=\"" + id + "\" type=\"HYPERLINK\" name=\"\" editable=\"0\" dirty=\"0\""
                + " zorder=\"-1\" fieldid=\"" + id + "\"><hp:parameters cnt=\"2\">"
                + "<hp:integerParam name=\"Prop\">0</hp:integerParam>"
                + "<hp:stringParam name=\"Command\">" + command + "</hp:stringParam>"
                + (path == null ? "" : "<hp:stringParam name=\"Path\">" + path + "</hp:stringParam>")
                + "</hp:parameters></hp:fieldBegin></hp:ctrl>";
    }

    private static String end(String id) {
        return "<hp:ctrl><hp:fieldEnd beginIDRef=\"" + id + "\" fieldid=\"" + id + "\"/></hp:ctrl>";
    }

    private static Element el(Document d, String local, int i) {
        return (Element) d.getElementsByTagNameNS(W, local).item(i);
    }

    @Test
    void bookmarkFieldAcrossParagraphs() throws Exception {
        convert(new TestHwpx().paragraphs(
                para("0", run("0", bookmarkBegin("11", "제1장 총강") + t("대한민국은 민주공화국이다."))),
                para("0", run("0", t("대한민국의 주권은 국민에게 있다.") + end("11")))));
        Document d = doc();
        Element start = el(d, "bookmarkStart", 0);
        Element stop = el(d, "bookmarkEnd", 0);
        // 빈칸은 Word 책갈피 이름에 쓸 수 없어 밑줄로
        assertEquals("제1장_총강", start.getAttributeNS(W, "name"));
        assertEquals(start.getAttributeNS(W, "id"), stop.getAttributeNS(W, "id"));
        NodeList ps = d.getElementsByTagNameNS(W, "p");
        assertTrue(start.getParentNode() != stop.getParentNode());
        assertTrue(ps.getLength() >= 2);
        assertEquals(0, result.getSkippedCount());
    }

    @Test
    void pointBookmark() throws Exception {
        convert(new TestHwpx().paragraphs(para("0", run("0", t("국민") + "<hp:ctrl><hp:bookmark name=\"국민\"/></hp:ctrl>"))));
        assertContains(text("word/document.xml"), "<w:bookmarkStart w:id=\"0\" w:name=\"국민\"/><w:bookmarkEnd w:id=\"0\"/>");
    }

    @Test
    void externalHyperlink() throws Exception {
        convert(new TestHwpx().paragraphs(para("0", run("0", t("법령은 ")
                + linkBegin("21", "https\\://www.law.go.kr/?a=1&amp;b=2;1;0;0;", "https://www.law.go.kr/?a=1&amp;b=2")
                + t("국가법령정보센터") + end("21") + t("에서 봄")))));
        Document d = doc();
        Element link = el(d, "hyperlink", 0);
        assertEquals("국가법령정보센터", link.getTextContent());
        String rid = link.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id");
        String rels = text("word/_rels/document.xml.rels");
        assertContains(rels, "Id=\"" + rid + "\"");
        assertContains(rels, "Target=\"https://www.law.go.kr/?a=1&amp;b=2\" TargetMode=\"External\"");
        assertEquals(0, result.getSkippedCount());
    }

    @Test
    void commandOnlyHyperlinkIsUnescaped() {
        Element f = field(linkBegin("1", "http\\://example.org/x;1;0;0;", null));
        assertEquals("http://example.org/x", HwpxToDocx.linkTarget(f));
    }

    private static Element field(String ctrl) {
        try {
            String xml = "<r xmlns:hp=\"http://www.hancom.co.kr/hwpml/2011/paragraph\">" + ctrl + "</r>";
            Document d = SafeXml.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), "t");
            return (Element) d.getElementsByTagNameNS("http://www.hancom.co.kr/hwpml/2011/paragraph", "fieldBegin").item(0);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void hyperlinkAcrossParagraphsIsSplit() throws Exception {
        convert(new TestHwpx().paragraphs(
                para("0", run("0", linkBegin("31", "", "https://example.org/") + t("첫 문단"))),
                para("0", run("0", t("둘째 문단") + end("31") + t(" 링크 밖")))));
        Document d = doc();
        assertEquals(2, d.getElementsByTagNameNS(W, "hyperlink").getLength());
        assertEquals("첫 문단", el(d, "hyperlink", 0).getTextContent());
        assertEquals("둘째 문단", el(d, "hyperlink", 1).getTextContent());
        // 같은 주소는 관계 하나
        String rels = text("word/_rels/document.xml.rels");
        assertEquals(rels.indexOf("https://example.org/"), rels.lastIndexOf("https://example.org/"));
    }

    @Test
    void internalLinkUsesAnchor() throws Exception {
        convert(new TestHwpx().paragraphs(para("0", run("0", linkBegin("41", "", "#제1장 총강") + t("총강") + end("41")))));
        assertEquals("제1장_총강", el(doc(), "hyperlink", 0).getAttributeNS(W, "anchor"));
    }

    @Test
    void hyperlinkInHeaderUsesHeaderRelationships() throws Exception {
        convert(new TestHwpx().paragraphs(para("0", run("0", "<hp:ctrl><hp:header id=\"1\" applyPageType=\"BOTH\">"
                + "<hp:subList id=\"\" textDirection=\"HORIZONTAL\" lineWrap=\"BREAK\" vertAlign=\"TOP\">"
                + para("0", run("0", linkBegin("51", "", "https://example.org/h") + t("대한민국헌법") + end("51")))
                + "</hp:subList></hp:header></hp:ctrl>"))));
        assertContains(text("word/_rels/header1.xml.rels"), "TargetMode=\"External\"");
        assertTrue(!text("word/_rels/document.xml.rels").contains("example.org/h"));
    }

    @Test
    void otherFieldsStaySkipped() throws Exception {
        convert(new TestHwpx().paragraphs(para("0", run("0",
                "<hp:ctrl><hp:fieldBegin id=\"61\" type=\"CLICK_HERE\" name=\"\"/></hp:ctrl>" + t("여기") + end("61")))));
        assertEquals(2, result.getSkippedCount());
        assertTrue(!text("word/document.xml").contains("hyperlink"));
    }

    @Test
    void bookmarkNameLimit() {
        assertEquals(40, HwpxToDocx.bookmarkName("가나다라마바사아자차카타파하가나다라마바사아자차카타파하가나다라마바사아자차카타파하").length());
        assertEquals("_", HwpxToDocx.bookmarkName("  "));
    }
}

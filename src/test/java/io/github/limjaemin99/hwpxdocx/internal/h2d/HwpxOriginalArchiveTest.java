package io.github.limjaemin99.hwpxdocx.internal.h2d;

import io.github.limjaemin99.hwpxdocx.ConvertOptions;
import io.github.limjaemin99.hwpxdocx.ConvertResult;
import io.github.limjaemin99.hwpxdocx.HwpxDocxConverter;
import io.github.limjaemin99.hwpxdocx.internal.SafeXml;
import io.github.limjaemin99.hwpxdocx.internal.docx.DocxSkeleton;
import io.github.limjaemin99.hwpxdocx.testkit.Test;
import io.github.limjaemin99.hwpxdocx.testkit.TestConvert;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static io.github.limjaemin99.hwpxdocx.internal.h2d.TestHwpx.para;
import static io.github.limjaemin99.hwpxdocx.internal.h2d.TestHwpx.run;
import static io.github.limjaemin99.hwpxdocx.internal.h2d.TestHwpx.t;
import static io.github.limjaemin99.hwpxdocx.internal.h2d.TestHwpx.tbl;
import static io.github.limjaemin99.hwpxdocx.internal.h2d.TestHwpx.tc;
import static io.github.limjaemin99.hwpxdocx.internal.h2d.TestHwpx.tr;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertEquals;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertTrue;

class HwpxOriginalArchiveTest {

    private static final String W = DocxSkeleton.NS_W;

    private ConvertResult result;
    private byte[] docx;
    private final Map<String, byte[]> entries = new HashMap<String, byte[]>();

    private void convert(byte[] hwpx) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        result = TestConvert.hwpxToDocx(new ByteArrayInputStream(hwpx), out, ConvertOptions.defaults());
        docx = out.toByteArray();
        entries.clear();
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(docx))) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = z.read(buf)) > 0) {
                    b.write(buf, 0, n);
                }
                entries.put(e.getName(), b.toByteArray());
            }
        }
    }

    private String part(String name) {
        byte[] b = entries.get(name);
        return b == null ? null : new String(b, StandardCharsets.UTF_8);
    }

    private Document parse(String name) throws IOException {
        return SafeXml.parse(new ByteArrayInputStream(entries.get(name)), name);
    }

    private static Element bookmark(Document doc, String name) {
        NodeList l = doc.getElementsByTagNameNS(W, "bookmarkStart");
        for (int i = 0; i < l.getLength(); i++) {
            Element e = (Element) l.item(i);
            if (name.equals(e.getAttributeNS(W, "name"))) {
                return e;
            }
        }
        return null;
    }

    private static Element bookmarkEnd(Document doc, String id) {
        NodeList l = doc.getElementsByTagNameNS(W, "bookmarkEnd");
        for (int i = 0; i < l.getLength(); i++) {
            Element e = (Element) l.item(i);
            if (id.equals(e.getAttributeNS(W, "id"))) {
                return e;
            }
        }
        return null;
    }

    @Test
    void paragraphWithSkippedElementIsKept() throws Exception {
        convert(new TestHwpx().paragraphs(
                para("0", run("0", t("제1조 ①대한민국은 민주공화국이다."))),
                para("0", run("0", "<hp:rect id=\"7\"/>"), run("0", t("제3조 대한민국의 영토는 한반도와 그 부속도서로 한다.")))).toBytes());

        Document doc = parse("word/document.xml");
        Element start = bookmark(doc, "_hwpx_1");
        assertTrue(start != null);
        Element end = bookmarkEnd(doc, start.getAttributeNS(W, "id"));
        assertTrue(end != null);
        // 둘 다 도형이 있던 문단의 Word 문단 안
        Node para = start.getParentNode();
        assertEquals("p", para.getLocalName());
        assertEquals(para, end.getParentNode());
        assertTrue(para.getTextContent().contains("제3조"));
        assertTrue(bookmark(doc, "_hwpx_2") == null);

        Document item = parse("customXml/item1.xml");
        Element root = item.getDocumentElement();
        assertEquals(OriginalArchive.NS, root.getNamespaceURI());
        NodeList ps = item.getElementsByTagNameNS(OriginalArchive.NS, "p");
        assertEquals(1, ps.getLength());
        Element p = (Element) ps.item(0);
        assertEquals("1", p.getAttribute("n"));
        assertEquals("제3조 대한민국의 영토는 한반도와 그 부속도서로 한다.", p.getAttribute("text"));
        String fragment = p.getTextContent();
        assertTrue(fragment.startsWith("<hp:p") && fragment.contains("<hp:rect id=\"7\""));
        // 저장해 둔 이름공간 선언으로 조각을 따로 읽을 수 있음
        Document frag = SafeXml.parse(new ByteArrayInputStream(("<x " + p.getAttribute("ns") + ">" + fragment + "</x>")
                .getBytes(StandardCharsets.UTF_8)), "fragment");
        assertEquals(1, frag.getElementsByTagNameNS("http://www.hancom.co.kr/hwpml/2011/paragraph", "rect").getLength());
        String header = item.getElementsByTagNameNS(OriginalArchive.NS, "header").item(0).getTextContent();
        assertTrue(header.startsWith("<hh:head") && header.contains("charProperties"));

        assertTrue(part("customXml/itemProps1.xml").contains("ds:itemID=\"{"));
        assertTrue(part("customXml/_rels/item1.xml.rels").contains("Target=\"itemProps1.xml\""));
        assertTrue(part("[Content_Types].xml").contains("PartName=\"/customXml/itemProps1.xml\""));
        assertTrue(part("word/_rels/document.xml.rels").contains("relationships/customXml\" Target=\"../customXml/item1.xml\""));
        assertTrue(result.getWarnings().contains("건너뛴 요소가 든 문단 1개의 원본을 DOCX 안에 보관함"));
    }

    @Test
    void nothingKeptWithoutSkippedElements() throws Exception {
        convert(new TestHwpx().paragraphs(para("0", run("0", t("제1조 ①대한민국은 민주공화국이다.")))).toBytes());
        assertTrue(part("customXml/item1.xml") == null);
        assertTrue(!part("word/document.xml").contains("_hwpx_"));
        assertTrue(!part("[Content_Types].xml").contains("customXml"));
    }

    @Test
    void paragraphStartingWithTableBookmarksAtBodyLevel() throws Exception {
        String table = tbl(1, 1, "borderFillIDRef=\"1\"", tr(tc(0, 0, 1, 1, 10000, "1", "제24조")));
        convert(new TestHwpx().paragraphs(para("0", run("0", table), run("0", "<hp:ellipse/>"), run("0", t("선거권")))).toBytes());
        Document doc = parse("word/document.xml");
        Element start = bookmark(doc, "_hwpx_1");
        assertEquals("body", start.getParentNode().getLocalName());
        Node next = start.getNextSibling();
        assertEquals("tbl", next.getLocalName());
        Element end = bookmarkEnd(doc, start.getAttributeNS(W, "id"));
        assertTrue(end.getParentNode().getTextContent().contains("선거권"));
        Element p = (Element) parse("customXml/item1.xml").getElementsByTagNameNS(OriginalArchive.NS, "p").item(0);
        assertEquals("제24조선거권", p.getAttribute("text"));
    }

    /** 다시 HWPX로 바꿀 때 보관 표시 책갈피는 한컴 책갈피로 옮기지 않음. */
    @Test
    void keptBookmarkIsNotCarriedBackToHwpx() throws Exception {
        convert(new TestHwpx().paragraphs(para("0", run("0", "<hp:rect/>"), run("0", t("제3조")))).toBytes());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        HwpxDocxConverter.docxToHwpx(new ByteArrayInputStream(docx), out, null);
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(out.toByteArray()))) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                if (e.getName().startsWith("Contents/section")) {
                    ByteArrayOutputStream b = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = z.read(buf)) > 0) {
                        b.write(buf, 0, n);
                    }
                    String xml = new String(b.toByteArray(), StandardCharsets.UTF_8);
                    assertTrue(!xml.contains("_hwpx_"));
                    assertTrue(xml.contains("제3조"));
                }
            }
        }
    }
}

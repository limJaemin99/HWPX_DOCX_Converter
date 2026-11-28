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
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static io.github.limjaemin99.hwpxdocx.internal.h2d.TestHwpx.charPr;
import static io.github.limjaemin99.hwpxdocx.internal.h2d.TestHwpx.para;
import static io.github.limjaemin99.hwpxdocx.internal.h2d.TestHwpx.pic;
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

    private static Map<String, byte[]> unzip(byte[] zip) throws IOException {
        Map<String, byte[]> m = new LinkedHashMap<String, byte[]>();
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = z.read(buf)) > 0) {
                    b.write(buf, 0, n);
                }
                m.put(e.getName(), b.toByteArray());
            }
        }
        return m;
    }

    private static byte[] zip(Map<String, byte[]> m) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> e : m.entrySet()) {
                z.putNextEntry(new ZipEntry(e.getKey()));
                z.write(e.getValue());
                z.closeEntry();
            }
        }
        return out.toByteArray();
    }

    private ConvertResult back;

    /** DOCX → HWPX 결과의 header.xml과 section0.xml. */
    private Map<String, String> toHwpx(byte[] docx) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        back = HwpxDocxConverter.docxToHwpx(new ByteArrayInputStream(docx), out, null);
        Map<String, String> m = new HashMap<String, String>();
        for (Map.Entry<String, byte[]> e : unzip(out.toByteArray()).entrySet()) {
            if (e.getKey().startsWith("Contents/")) {
                m.put(e.getKey(), new String(e.getValue(), StandardCharsets.UTF_8));
            }
        }
        return m;
    }

    private static byte[] shapeDocument() throws IOException {
        return new TestHwpx()
                .charPrs(charPr("0", 1000, "", ""), charPr("1", 1500, "textColor=\"#C00000\"", "<hh:bold/>"))
                .paragraphs(
                        para("0", run("0", t("제1조 ①대한민국은 민주공화국이다."))),
                        para("0", run("1", "<hp:rect id=\"7\"/>"), run("1", t("제3조 대한민국의 영토는 한반도와 그 부속도서로 한다."))))
                .toBytes();
    }

    @Test
    void roundTripRestoresKeptParagraph() throws Exception {
        convert(shapeDocument());
        Map<String, String> hwpx = toHwpx(docx);
        String sec = hwpx.get("Contents/section0.xml");
        assertTrue(sec.contains("<hp:rect id=\"7\""));
        assertTrue(!sec.contains("linesegarray"));
        assertTrue(back.getWarnings().contains("보관한 원본 문단 1개를 되살림"));
        // 되살린 문단의 글자 모양 번호가 새 header에 옮겨 붙인 원본 모양(15pt, 빨강, 굵게)을 가리킴
        Document h = SafeXml.parse(new ByteArrayInputStream(hwpx.get("Contents/header.xml").getBytes(StandardCharsets.UTF_8)), "header");
        Document s = SafeXml.parse(new ByteArrayInputStream(sec.getBytes(StandardCharsets.UTF_8)), "section");
        Element rect = (Element) s.getElementsByTagNameNS(HP, "rect").item(0);
        String charId = ((Element) rect.getParentNode()).getAttribute("charPrIDRef");
        Element cp = byId(h, "charPr", charId);
        assertEquals("1500", cp.getAttribute("height"));
        assertEquals("#C00000", cp.getAttribute("textColor"));
        assertEquals(1, cp.getElementsByTagNameNS(HH, "bold").getLength());
        // 글자 모양 수와 itemCnt가 맞음
        Element list = (Element) h.getElementsByTagNameNS(HH, "charProperties").item(0);
        assertEquals(String.valueOf(list.getElementsByTagNameNS(HH, "charPr").getLength()), list.getAttribute("itemCnt"));
        // 다른 문단은 그대로 변환됨
        assertTrue(sec.contains("제1조 ①대한민국은 민주공화국이다."));
        assertEquals(1, s.getElementsByTagNameNS(HP, "rect").getLength());
    }

    @Test
    void editedParagraphIsNotRestored() throws Exception {
        convert(shapeDocument());
        Map<String, byte[]> m = unzip(docx);
        String doc = new String(m.get("word/document.xml"), StandardCharsets.UTF_8);
        m.put("word/document.xml", doc.replace("부속도서로 한다.", "부속도서로 한다. (고침)").getBytes(StandardCharsets.UTF_8));
        String sec = toHwpx(zip(m)).get("Contents/section0.xml");
        assertTrue(!sec.contains("hp:rect"));
        assertTrue(sec.contains("(고침)"));
        assertTrue(back.getWarnings().contains("Word에서 글이 바뀐 문단은 보관한 원본 대신 변환 결과를 씀"));
    }

    @Test
    void otherParagraphEditedStillRestores() throws Exception {
        convert(shapeDocument());
        Map<String, byte[]> m = unzip(docx);
        String doc = new String(m.get("word/document.xml"), StandardCharsets.UTF_8);
        m.put("word/document.xml", doc.replace("민주공화국이다.", "민주공화국이다. (고침)").getBytes(StandardCharsets.UTF_8));
        String sec = toHwpx(zip(m)).get("Contents/section0.xml");
        assertTrue(sec.contains("<hp:rect id=\"7\""));
        assertTrue(sec.contains("(고침)"));
    }

    @Test
    void numberedParagraphFallsBackToConversion() throws Exception {
        TestHwpx t = new TestHwpx();
        String header = t.get("Contents/header.xml");
        // 바탕 문단 모양에 문단 번호를 걸어 되살릴 수 없는 경우를 만듦
        int at = header.indexOf("<hh:heading ");
        assertTrue(at > 0);
        int end = header.indexOf("/>", at);
        t.put("Contents/header.xml", header.substring(0, at) + "<hh:heading type=\"NUMBER\" idRef=\"1\" level=\"0\""
                + header.substring(end));
        convert(t.paragraphs(para("0", run("0", "<hp:rect/>"), run("0", t("제3조")))).toBytes());
        String sec = toHwpx(docx).get("Contents/section0.xml");
        assertTrue(!sec.contains("hp:rect"));
        assertTrue(sec.contains("제3조"));
        assertTrue(back.getWarnings().contains("보관한 원본 문단을 되살리지 않고 변환 결과를 씀: 옮길 수 없는 문단 번호"));
    }

    private static final String HP = "http://www.hancom.co.kr/hwpml/2011/paragraph";
    private static final String HH = "http://www.hancom.co.kr/hwpml/2011/head";

    private static Element byId(Document d, String local, String id) {
        NodeList l = d.getElementsByTagNameNS(HH, local);
        for (int i = 0; i < l.getLength(); i++) {
            if (id.equals(((Element) l.item(i)).getAttribute("id"))) {
                return (Element) l.item(i);
            }
        }
        throw new AssertionError(local + " " + id + " 없음");
    }

    /** 그림이 든 도형 문단: 보관할 때 그림 파일도 담고, 되살릴 때 새 BinData로 넣음. */
    @Test
    void keptPictureDataComesBack() throws Exception {
        byte[] png = HwpxPictureTest.png();
        convert(new TestHwpx().binData("image1", "BinData/image1.png", "image/png", png)
                .paragraphs(para("0", run("0", pic("image1", 7200, 3600, true)), run("0", "<hp:rect/>"), run("0", t("제9조"))))
                .toBytes());
        Element bin = (Element) parse("customXml/item1.xml").getElementsByTagNameNS(OriginalArchive.NS, "bin").item(0);
        assertEquals("image1", bin.getAttribute("id"));
        assertEquals("BinData/image1.png", bin.getAttribute("href"));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        back = HwpxDocxConverter.docxToHwpx(new ByteArrayInputStream(docx), out, null);
        Map<String, byte[]> m = unzip(out.toByteArray());
        String sec = new String(m.get("Contents/section0.xml"), StandardCharsets.UTF_8);
        assertTrue(sec.contains("<hp:rect"));
        Matcher id = Pattern.compile("binaryItemIDRef=\"([^\"]+)\"").matcher(sec);
        assertTrue(id.find());
        String hpf = new String(m.get("Contents/content.hpf"), StandardCharsets.UTF_8);
        Matcher href = Pattern.compile("id=\"" + id.group(1) + "\" href=\"([^\"]+)\"").matcher(hpf);
        assertTrue(href.find());
        assertTrue(Arrays.equals(png, m.get(href.group(1))));
        assertTrue(back.getWarnings().contains("보관한 원본 문단 1개를 되살림"));
    }
}

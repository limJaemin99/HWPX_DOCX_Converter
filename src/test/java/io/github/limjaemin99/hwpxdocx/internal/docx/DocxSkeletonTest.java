package io.github.limjaemin99.hwpxdocx.internal.docx;

import io.github.limjaemin99.hwpxdocx.internal.SafeXml;
import io.github.limjaemin99.hwpxdocx.testkit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertEquals;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertTrue;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.fail;

class DocxSkeletonTest {

    private static final List<String> EXPECTED = Arrays.asList(
            "[Content_Types].xml", "_rels/.rels", "word/document.xml", "word/_rels/document.xml.rels",
            "word/styles.xml", "word/settings.xml", "docProps/core.xml", "docProps/app.xml");

    private static Map<String, byte[]> unzip(byte[] zip) throws Exception {
        Map<String, byte[]> m = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e;
            byte[] buf = new byte[8192];
            while ((e = in.getNextEntry()) != null) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
                m.put(e.getName(), out.toByteArray());
            }
        }
        return m;
    }

    private static Document xml(Map<String, byte[]> m, String name) throws Exception {
        return SafeXml.parse(new ByteArrayInputStream(m.get(name)), name);
    }

    private static void check(boolean ok, String message) {
        if (!ok) {
            fail(message);
        }
    }

    @Test
    void entriesInOrder() throws Exception {
        assertEquals(EXPECTED, new ArrayList<>(unzip(DocxSkeleton.toBytes()).keySet()));
    }

    @Test
    void allXmlParsesSafely() throws Exception {
        Map<String, byte[]> m = unzip(DocxSkeleton.toBytes());
        for (String name : EXPECTED) {
            assertTrue(xml(m, name).getDocumentElement() != null);
        }
    }

    @Test
    void everyPartHasContentType() throws Exception {
        Map<String, byte[]> m = unzip(DocxSkeleton.toBytes());
        Document ct = xml(m, "[Content_Types].xml");
        Set<String> defaults = new HashSet<>();
        NodeList ds = ct.getElementsByTagNameNS(DocxSkeleton.NS_CT, "Default");
        for (int i = 0; i < ds.getLength(); i++) {
            defaults.add(((Element) ds.item(i)).getAttribute("Extension"));
        }
        Set<String> overrides = new HashSet<>();
        NodeList os = ct.getElementsByTagNameNS(DocxSkeleton.NS_CT, "Override");
        for (int i = 0; i < os.getLength(); i++) {
            String part = ((Element) os.item(i)).getAttribute("PartName");
            check(m.containsKey(part.substring(1)), "없는 파트: " + part);
            overrides.add(part.substring(1));
        }
        for (String name : EXPECTED) {
            if (!name.equals("[Content_Types].xml")) {
                String ext = name.substring(name.lastIndexOf('.') + 1);
                check(overrides.contains(name) || defaults.contains(ext), "형식 없음: " + name);
            }
        }
        assertTrue(overrides.contains("word/document.xml"));
    }

    @Test
    void relationshipTargetsExist() throws Exception {
        Map<String, byte[]> m = unzip(DocxSkeleton.toBytes());
        checkTargets(m, "_rels/.rels", "");
        checkTargets(m, "word/_rels/document.xml.rels", "word/");
    }

    private static void checkTargets(Map<String, byte[]> m, String rels, String base) throws Exception {
        NodeList rs = xml(m, rels).getElementsByTagNameNS(DocxSkeleton.NS_PKG_REL, "Relationship");
        assertTrue(rs.getLength() > 0);
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < rs.getLength(); i++) {
            Element r = (Element) rs.item(i);
            check(ids.add(r.getAttribute("Id")), "Id 중복: " + r.getAttribute("Id"));
            String target = base + r.getAttribute("Target");
            check(m.containsKey(target), rels +" → 없는 파트: " + target);
        }
    }

    @Test
    void bodyHasOneEmptyParagraphAndSection() throws Exception {
        Document doc = xml(unzip(DocxSkeleton.toBytes()), "word/document.xml");
        assertEquals(1, doc.getElementsByTagNameNS(DocxSkeleton.NS_W, "p").getLength());
        assertEquals(0, doc.getElementsByTagNameNS(DocxSkeleton.NS_W, "t").getLength());
        assertEquals(1, doc.getElementsByTagNameNS(DocxSkeleton.NS_W, "sectPr").getLength());
        Element pgSz = (Element) doc.getElementsByTagNameNS(DocxSkeleton.NS_W, "pgSz").item(0);
        assertEquals("11906", pgSz.getAttributeNS(DocxSkeleton.NS_W, "w"));
        assertEquals("16838", pgSz.getAttributeNS(DocxSkeleton.NS_W, "h"));
    }

    @Test
    void defaultParagraphStyleDefined() throws Exception {
        Document styles = xml(unzip(DocxSkeleton.toBytes()), "word/styles.xml");
        assertEquals(1, styles.getElementsByTagNameNS(DocxSkeleton.NS_W, "docDefaults").getLength());
        NodeList ss = styles.getElementsByTagNameNS(DocxSkeleton.NS_W, "style");
        int defaultParagraph = 0;
        for (int i = 0; i < ss.getLength(); i++) {
            Element s = (Element) ss.item(i);
            if ("paragraph".equals(s.getAttributeNS(DocxSkeleton.NS_W, "type"))
                    && "1".equals(s.getAttributeNS(DocxSkeleton.NS_W, "default"))) {
                defaultParagraph++;
            }
        }
        assertEquals(1, defaultParagraph);
    }

    @Test
    void noAuthorInformation() throws Exception {
        Map<String, byte[]> m = unzip(DocxSkeleton.toBytes());
        Document core = xml(m, "docProps/core.xml");
        assertEquals("", core.getElementsByTagNameNS(DocxSkeleton.NS_DC, "creator").item(0).getTextContent());
        assertEquals("", core.getElementsByTagNameNS(DocxSkeleton.NS_CP, "lastModifiedBy").item(0)
                .getTextContent());
        Document app = xml(m, "docProps/app.xml");
        assertEquals("", app.getElementsByTagNameNS(DocxSkeleton.NS_EP, "Company").item(0).getTextContent());
        assertEquals(0, app.getElementsByTagNameNS(DocxSkeleton.NS_EP, "Manager").getLength());
    }

    @Test
    void sameBytesEveryTime() throws Exception {
        assertTrue(Arrays.equals(DocxSkeleton.toBytes(), DocxSkeleton.toBytes()));
    }
}

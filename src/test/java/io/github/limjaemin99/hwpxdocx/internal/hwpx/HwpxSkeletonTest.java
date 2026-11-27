package io.github.limjaemin99.hwpxdocx.internal.hwpx;

import io.github.limjaemin99.hwpxdocx.internal.SafeXml;
import io.github.limjaemin99.hwpxdocx.testkit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertEquals;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertTrue;

class HwpxSkeletonTest {

    private static final List<String> EXPECTED = Arrays.asList(
            "mimetype", "version.xml", "META-INF/container.xml", "META-INF/manifest.xml",
            "Contents/content.hpf", "Contents/header.xml", "Contents/section0.xml", "settings.xml");

    private static Map<String, byte[]> unzip(byte[] zip, List<ZipEntry> entries) throws Exception {
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
                entries.add(e);
                m.put(e.getName(), out.toByteArray());
            }
        }
        return m;
    }

    private static Document xml(Map<String, byte[]> m, String name) throws Exception {
        return SafeXml.parse(new ByteArrayInputStream(m.get(name)), name);
    }

    @Test
    void entriesInOrder() throws Exception {
        List<ZipEntry> entries = new ArrayList<>();
        Map<String, byte[]> m = unzip(HwpxSkeleton.toBytes(), entries);
        assertEquals(EXPECTED, new ArrayList<>(m.keySet()));
    }

    @Test
    void mimetypeFirstAndStored() throws Exception {
        byte[] zip = HwpxSkeleton.toBytes();
        // 로컬 헤더 30바이트 뒤에 이름 "mimetype", 그 바로 뒤에 비압축 내용이 와야 함
        String head = new String(zip, 30, 8 + HwpxSkeleton.MIMETYPE.length(), StandardCharsets.US_ASCII);
        assertEquals("mimetype" + HwpxSkeleton.MIMETYPE, head);
        List<ZipEntry> entries = new ArrayList<>();
        unzip(zip, entries);
        assertEquals(ZipEntry.STORED, entries.get(0).getMethod());
    }

    @Test
    void allXmlParsesSafely() throws Exception {
        Map<String, byte[]> m = unzip(HwpxSkeleton.toBytes(), new ArrayList<ZipEntry>());
        for (String name : EXPECTED) {
            if (!"mimetype".equals(name)) {
                assertTrue(xml(m, name).getDocumentElement() != null);
            }
        }
    }

    @Test
    void sectionHasOneEmptyParagraph() throws Exception {
        Map<String, byte[]> m = unzip(HwpxSkeleton.toBytes(), new ArrayList<ZipEntry>());
        Document sec = xml(m, "Contents/section0.xml");
        NodeList ps = sec.getElementsByTagNameNS(HwpxSkeleton.NS_PARA, "p");
        assertEquals(1, ps.getLength());
        assertEquals(1, sec.getElementsByTagNameNS(HwpxSkeleton.NS_PARA, "secPr").getLength());
        NodeList ts = sec.getElementsByTagNameNS(HwpxSkeleton.NS_PARA, "t");
        assertEquals(1, ts.getLength());
        assertEquals("", ts.item(0).getTextContent());
    }

    @Test
    void headerReferencesResolve() throws Exception {
        Map<String, byte[]> m = unzip(HwpxSkeleton.toBytes(), new ArrayList<ZipEntry>());
        Document head = xml(m, "Contents/header.xml");
        Element style = (Element) head.getElementsByTagNameNS(HwpxSkeleton.NS_HEAD, "style").item(0);
        Element charPr = (Element) head.getElementsByTagNameNS(HwpxSkeleton.NS_HEAD, "charPr").item(0);
        Element paraPr = (Element) head.getElementsByTagNameNS(HwpxSkeleton.NS_HEAD, "paraPr").item(0);
        assertEquals(charPr.getAttribute("id"), style.getAttribute("charPrIDRef"));
        assertEquals(paraPr.getAttribute("id"), style.getAttribute("paraPrIDRef"));
        assertEquals(7, head.getElementsByTagNameNS(HwpxSkeleton.NS_HEAD, "fontface").getLength());
        Element borderFill = (Element) head.getElementsByTagNameNS(HwpxSkeleton.NS_HEAD, "borderFill").item(0);
        assertEquals(borderFill.getAttribute("id"), charPr.getAttribute("borderFillIDRef"));
    }

    @Test
    void noAuthorInformation() throws Exception {
        Map<String, byte[]> m = unzip(HwpxSkeleton.toBytes(), new ArrayList<ZipEntry>());
        Document hpf = xml(m, "Contents/content.hpf");
        NodeList metas = hpf.getElementsByTagNameNS("http://www.idpf.org/2007/opf/", "meta");
        assertTrue(metas.getLength() > 0);
        for (int i = 0; i < metas.getLength(); i++) {
            assertEquals("", metas.item(i).getTextContent());
        }
    }

    @Test
    void sameBytesEveryTime() throws Exception {
        assertTrue(Arrays.equals(HwpxSkeleton.toBytes(), HwpxSkeleton.toBytes()));
    }
}

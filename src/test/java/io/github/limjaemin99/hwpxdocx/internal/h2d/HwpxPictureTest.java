package io.github.limjaemin99.hwpxdocx.internal.h2d;

import io.github.limjaemin99.hwpxdocx.testkit.TestConvert;
import io.github.limjaemin99.hwpxdocx.ConvertResult;
import io.github.limjaemin99.hwpxdocx.HwpxDocxConverter;
import io.github.limjaemin99.hwpxdocx.internal.SafeXml;
import io.github.limjaemin99.hwpxdocx.testkit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
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

class HwpxPictureTest {

    private static final String WP = "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing";
    private static final String A = "http://schemas.openxmlformats.org/drawingml/2006/main";
    private static final String PR = "http://schemas.openxmlformats.org/package/2006/relationships";
    private static final String CT = "http://schemas.openxmlformats.org/package/2006/content-types";
    private static final String R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";

    private ConvertResult result;
    private Map<String, byte[]> out;

    static byte[] png() throws Exception {
        BufferedImage img = new BufferedImage(4, 2, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, 0xFF0000);
        img.setRGB(3, 1, 0x0000FF);
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        ImageIO.write(img, "png", b);
        return b.toByteArray();
    }

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
        return SafeXml.parse(new ByteArrayInputStream(out.get(name)), name);
    }

    private static Element first(Document d, String ns, String local) {
        NodeList l = d.getElementsByTagNameNS(ns, local);
        return l.getLength() == 0 ? null : (Element) l.item(0);
    }

    @Test
    void inlinePictureCopiesFileAndRelation() throws Exception {
        byte[] data = png();
        convert(new TestHwpx().binData("image1", "BinData/image1.png", "image/png", data)
                .paragraphs(para("0", run("0", t("제1조 ①대한민국은 민주공화국이다.")), run("0", pic("image1", 7200, 3600, true))))
                .toBytes());
        assertEquals(1, result.getPictureCount());
        assertEquals(0, result.getSkippedCount());
        assertTrue(Arrays.equals(data, out.get("word/media/image1.png")));

        Document doc = xml("word/document.xml");
        Element inline = first(doc, WP, "inline");
        assertTrue(inline != null);
        Element extent = first(doc, WP, "extent");
        assertEquals("914400", extent.getAttribute("cx"));
        assertEquals("457200", extent.getAttribute("cy"));
        String rid = first(doc, A, "blip").getAttributeNS(R, "embed");

        NodeList rels = xml("word/_rels/document.xml.rels").getElementsByTagNameNS(PR, "Relationship");
        boolean found = false;
        for (int i = 0; i < rels.getLength(); i++) {
            Element r = (Element) rels.item(i);
            if (r.getAttribute("Id").equals(rid)) {
                assertEquals("media/image1.png", r.getAttribute("Target"));
                assertTrue(r.getAttribute("Type").endsWith("/image"));
                found = true;
            }
        }
        assertTrue(found);

        NodeList defaults = xml("[Content_Types].xml").getElementsByTagNameNS(CT, "Default");
        boolean png = false;
        for (int i = 0; i < defaults.getLength(); i++) {
            Element d = (Element) defaults.item(i);
            png |= "png".equals(d.getAttribute("Extension")) && "image/png".equals(d.getAttribute("ContentType"));
        }
        assertTrue(png);
    }

    @Test
    void floatingPictureIsAnchored() throws Exception {
        convert(new TestHwpx().binData("image1", "BinData/image1.png", "image/png", png())
                .paragraphs(para("0", run("0", pic("image1", 7200, 3600, false)))).toBytes());
        Document doc = xml("word/document.xml");
        Element anchor = first(doc, WP, "anchor");
        assertTrue(anchor != null);
        assertEquals(null, first(doc, WP, "inline"));
        assertTrue(first(doc, WP, "wrapTopAndBottom") != null);
        Element h = first(doc, WP, "positionH");
        assertEquals("margin", h.getAttribute("relativeFrom"));
        assertEquals("center", first(doc, WP, "align").getTextContent());
        Element v = first(doc, WP, "positionV");
        assertEquals("paragraph", v.getAttribute("relativeFrom"));
        assertEquals("127000", first(doc, WP, "posOffset").getTextContent());
    }

    @Test
    void samePictureTwiceIsStoredOnceWithUniqueIds() throws Exception {
        convert(new TestHwpx().binData("image1", "BinData/image1.png", "image/png", png())
                .paragraphs(para("0", run("0", pic("image1", 7200, 3600, true) + pic("image1", 3600, 3600, true))))
                .toBytes());
        assertEquals(2, result.getPictureCount());
        int media = 0;
        for (String name : out.keySet()) {
            media += name.startsWith("word/media/") ? 1 : 0;
        }
        assertEquals(1, media);
        NodeList docPr = xml("word/document.xml").getElementsByTagNameNS(WP, "docPr");
        assertEquals(2, docPr.getLength());
        assertTrue(!((Element) docPr.item(0)).getAttribute("id").equals(((Element) docPr.item(1)).getAttribute("id")));
    }

    @Test
    void missingOrUnsupportedPictureIsSkipped() throws Exception {
        convert(new TestHwpx().binData("ole1", "BinData/ole1.ole", "application/octet-stream", new byte[]{1, 2})
                .paragraphs(para("0", run("0", pic("ole1", 7200, 3600, true) + pic("none", 7200, 3600, true))))
                .toBytes());
        assertEquals(0, result.getPictureCount());
        assertEquals(Integer.valueOf(2), result.getSkipped().get("hp:pic"));
        // 그림 두 개의 경고 + 원본 문단 보관 경고
        assertEquals(3, result.getWarnings().size());
        assertTrue(result.getWarnings().get(2).startsWith("건너뛴 요소가 든 문단 1개의 원본"));
    }

    @Test
    void watermarkEffectBecomesWashout() throws Exception {
        String wm = pic("image1", 7200, 3600, true).replace("bright=\"0\" contrast=\"0\"", "bright=\"70\" contrast=\"-50\"");
        convert(new TestHwpx().binData("image1", "BinData/image1.png", "image/png", png())
                .paragraphs(para("0", run("0", wm))).toBytes());
        Document doc = xml("word/document.xml");
        Element lum = first(doc, A, "lum");
        assertTrue(lum != null);
        assertEquals("70000", lum.getAttribute("bright"));
        assertEquals("-70000", lum.getAttribute("contrast"));
        assertEquals(0, result.getWarnings().size());
        // 다시 HWPX로: 워터마크 값이 돌아옴
        ByteArrayOutputStream back = new ByteArrayOutputStream();
        HwpxDocxConverter.docxToHwpx(new ByteArrayInputStream(zipOut()), back, null);
        String sec = new String(io.github.limjaemin99.hwpxdocx.internal.ZipReader.unzip(
                new ByteArrayInputStream(back.toByteArray())).get("Contents/section0.xml"), java.nio.charset.StandardCharsets.UTF_8);
        assertContains(sec, "bright=\"70\" contrast=\"-50\"");
    }

    @Test
    void otherBrightnessKeepsColorsWithWarning() throws Exception {
        String p = pic("image1", 7200, 3600, true).replace("bright=\"0\"", "bright=\"20\"");
        convert(new TestHwpx().binData("image1", "BinData/image1.png", "image/png", png())
                .paragraphs(para("0", run("0", p))).toBytes());
        assertEquals(null, first(xml("word/document.xml"), A, "lum"));
        assertContains(result.getWarnings().toString(), "워터마크 효과 값만");
    }

    private byte[] zipOut() throws Exception {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(o)) {
            for (Map.Entry<String, byte[]> e : out.entrySet()) {
                z.putNextEntry(new java.util.zip.ZipEntry(e.getKey()));
                z.write(e.getValue());
                z.closeEntry();
            }
        }
        return o.toByteArray();
    }
}

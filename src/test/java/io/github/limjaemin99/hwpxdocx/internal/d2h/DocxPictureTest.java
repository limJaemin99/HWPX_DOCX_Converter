package io.github.limjaemin99.hwpxdocx.internal.d2h;

import io.github.limjaemin99.hwpxdocx.ConvertResult;
import io.github.limjaemin99.hwpxdocx.HwpxDocxConverter;
import io.github.limjaemin99.hwpxdocx.internal.SafeXml;
import io.github.limjaemin99.hwpxdocx.internal.ZipReader;
import io.github.limjaemin99.hwpxdocx.internal.hwpx.HwpxHeader;
import io.github.limjaemin99.hwpxdocx.testkit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Map;

import static io.github.limjaemin99.hwpxdocx.internal.d2h.TestDocx.anchor;
import static io.github.limjaemin99.hwpxdocx.internal.d2h.TestDocx.inline;
import static io.github.limjaemin99.hwpxdocx.internal.d2h.TestDocx.p;
import static io.github.limjaemin99.hwpxdocx.internal.d2h.TestDocx.r;
import static io.github.limjaemin99.hwpxdocx.internal.d2h.TestDocx.t;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertEquals;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertTrue;

class DocxPictureTest {

    private static final String HP = HwpxHeader.NS_HP;
    private static final String HC = HwpxHeader.NS_HC;
    private static final String OPF = "http://www.idpf.org/2007/opf/";

    private ConvertResult result;
    private Map<String, byte[]> out;
    private Document section;

    private static byte[] png() throws Exception {
        BufferedImage img = new BufferedImage(4, 2, BufferedImage.TYPE_INT_RGB);
        img.setRGB(1, 1, 0x00FF00);
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        ImageIO.write(img, "png", b);
        return b.toByteArray();
    }

    private void convert(byte[] docx) throws Exception {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        result = HwpxDocxConverter.docxToHwpx(new ByteArrayInputStream(docx), o, null);
        out = ZipReader.unzip(new ByteArrayInputStream(o.toByteArray()));
        section = SafeXml.parse(new ByteArrayInputStream(out.get("Contents/section0.xml")), "section");
    }

    private Element pic(int i) {
        return (Element) section.getElementsByTagNameNS(HP, "pic").item(i);
    }

    private static Element kid(Element parent, String ns, String local) {
        NodeList l = parent.getElementsByTagNameNS(ns, local);
        return l.getLength() == 0 ? null : (Element) l.item(0);
    }

    @Test
    void inlinePicture() throws Exception {
        byte[] data = png();
        convert(new TestDocx().image("rId10", "media/image1.png", data)
                .body(p("", r("", t("제1조 ①대한민국은 민주공화국이다.")), r("", inline("rId10", 914400, 457200)))).toBytes());
        assertEquals(1, result.getPictureCount());
        assertEquals(0, result.getSkippedCount());
        assertTrue(Arrays.equals(data, out.get("BinData/image1.png")));

        Document hpf = SafeXml.parse(new ByteArrayInputStream(out.get("Contents/content.hpf")), "hpf");
        NodeList items = hpf.getElementsByTagNameNS(OPF, "item");
        boolean found = false;
        for (int i = 0; i < items.getLength(); i++) {
            Element it = (Element) items.item(i);
            if ("image1".equals(it.getAttribute("id"))) {
                assertEquals("BinData/image1.png", it.getAttribute("href"));
                assertEquals("image/png", it.getAttribute("media-type"));
                found = true;
            }
        }
        assertTrue(found);

        Element pic = pic(0);
        assertEquals("run", pic.getParentNode().getLocalName());
        assertEquals("image1", kid(pic, HC, "img").getAttribute("binaryItemIDRef"));
        assertEquals("7200", kid(pic, HP, "sz").getAttribute("width"));
        assertEquals("3600", kid(pic, HP, "sz").getAttribute("height"));
        assertEquals("1", kid(pic, HP, "pos").getAttribute("treatAsChar"));
    }

    @Test
    void anchoredPicture() throws Exception {
        convert(new TestDocx().image("rId10", "media/image1.png", png())
                .body(p("", r("", anchor("rId10", 914400, 457200)))).toBytes());
        Element pic = pic(0);
        assertEquals("SQUARE", pic.getAttribute("textWrap"));
        Element pos = kid(pic, HP, "pos");
        assertEquals("0", pos.getAttribute("treatAsChar"));
        assertEquals("PAGE", pos.getAttribute("horzRelTo"));
        assertEquals("CENTER", pos.getAttribute("horzAlign"));
        assertEquals("PARA", pos.getAttribute("vertRelTo"));
        assertEquals("1000", pos.getAttribute("vertOffset"));
    }

    @Test
    void samePictureStoredOnce() throws Exception {
        convert(new TestDocx().image("rId10", "media/image1.png", png())
                .body(p("", r("", inline("rId10", 914400, 457200) + inline("rId10", 457200, 457200)))).toBytes());
        assertEquals(2, result.getPictureCount());
        int bins = 0;
        for (String name : out.keySet()) {
            bins += name.startsWith("BinData/") ? 1 : 0;
        }
        assertEquals(1, bins);
        assertTrue(!pic(0).getAttribute("id").equals(pic(1).getAttribute("id")));
    }

    @Test
    void missingAndUnsupportedPictures() throws Exception {
        convert(new TestDocx().image("rId10", "media/x.svg", new byte[]{1})
                .body(p("", r("", inline("rId10", 914400, 457200) + inline("rId99", 914400, 457200)
                        + "<w:drawing><wp:inline xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\"/></w:drawing>")))
                .toBytes());
        assertEquals(0, result.getPictureCount());
        assertEquals(Integer.valueOf(3), result.getSkipped().get("w:drawing"));
        assertEquals(3, result.getWarnings().size());
    }

    @Test
    void docxToHwpxToDocxKeepsPicture() throws Exception {
        byte[] data = png();
        convert(new TestDocx().image("rId10", "media/image1.png", data)
                .body(p("", r("", inline("rId10", 914400, 457200)))).toBytes());
        ByteArrayOutputStream hwpx = new ByteArrayOutputStream();
        io.github.limjaemin99.hwpxdocx.internal.hwpx.HwpxSkeleton.write(out, hwpx);
        ByteArrayOutputStream docx = new ByteArrayOutputStream();
        ConvertResult back = HwpxDocxConverter.hwpxToDocx(new ByteArrayInputStream(hwpx.toByteArray()), docx, null);
        assertEquals(1, back.getPictureCount());
        Map<String, byte[]> d = ZipReader.unzip(new ByteArrayInputStream(docx.toByteArray()));
        assertTrue(Arrays.equals(data, d.get("word/media/image1.png")));
    }
}

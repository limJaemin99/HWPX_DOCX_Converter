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
import java.util.Map;

import static io.github.limjaemin99.hwpxdocx.internal.d2h.TestDocx.p;
import static io.github.limjaemin99.hwpxdocx.internal.d2h.TestDocx.r;
import static io.github.limjaemin99.hwpxdocx.internal.d2h.TestDocx.t;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertContains;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertEquals;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertTrue;

class DocxVmlPictureTest {

    private static final String HP = HwpxHeader.NS_HP;
    private static final String HC = HwpxHeader.NS_HC;

    private static final String VML_NS = " xmlns:v=\"urn:schemas-microsoft-com:vml\""
            + " xmlns:o=\"urn:schemas-microsoft-com:office:office\""
            + " xmlns:w10=\"urn:schemas-microsoft-com:office:word\""
            + " xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"";

    private ConvertResult result;
    private Map<String, byte[]> out;
    private Document section;

    private static byte[] png() throws Exception {
        BufferedImage img = new BufferedImage(4, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        ImageIO.write(img, "png", b);
        return b.toByteArray();
    }

    private static String pict(String style, String imagedataAttrs, String extra) {
        return "<w:pict" + VML_NS + "><v:shape id=\"x\" type=\"#_x0000_t75\" style=\"" + style + "\">"
                + "<v:imagedata r:id=\"rId30\"" + imagedataAttrs + "/>" + extra + "</v:shape></w:pict>";
    }

    private void convert(byte[] docx) throws Exception {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        result = HwpxDocxConverter.docxToHwpx(new ByteArrayInputStream(docx), o, null);
        out = ZipReader.unzip(new ByteArrayInputStream(o.toByteArray()));
        section = SafeXml.parse(new ByteArrayInputStream(out.get("Contents/section0.xml")), "section");
    }

    private Element first(String ns, String local) {
        NodeList l = section.getElementsByTagNameNS(ns, local);
        return l.getLength() == 0 ? null : (Element) l.item(0);
    }

    @Test
    void inlineVmlPicture() throws Exception {
        convert(new TestDocx().image("rId30", "media/image1.png", png())
                .body(p("", r("", t("제1조")), r("", pict("width:72pt;height:36pt", "", ""))))
                .toBytes());
        Element pic = first(HP, "pic");
        assertTrue(pic != null);
        assertEquals("TOP_AND_BOTTOM", pic.getAttribute("textWrap"));
        Element sz = (Element) pic.getElementsByTagNameNS(HP, "sz").item(0);
        // 1pt = 100 HWPUNIT
        assertEquals("7200", sz.getAttribute("width"));
        assertEquals("3600", sz.getAttribute("height"));
        assertEquals("1", first(HP, "pos").getAttribute("treatAsChar"));
        assertEquals("image1", first(HC, "img").getAttribute("binaryItemIDRef"));
        assertTrue(out.containsKey("BinData/image1.png"));
        assertEquals(0, result.getSkippedCount());
        assertEquals(1, result.getPictureCount());
    }

    @Test
    void watermarkInHeaderGoesBehindText() throws Exception {
        String style = "position:absolute;left:0;text-align:left;margin-left:0;margin-top:0;width:425pt;height:368.35pt;"
                + "z-index:-251584512;mso-position-horizontal:center;mso-position-horizontal-relative:margin;"
                + "mso-position-vertical:center;mso-position-vertical-relative:margin";
        TestDocx d = new TestDocx().image("rId30", "media/image1.png", png())
                .headerFooter("rId20", "header1.xml", "hdr",
                        p("", r("", pict(style, " gain=\"19661f\" blacklevel=\"22938f\"", ""))));
        d.put("word/_rels/header1.xml.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId30\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\""
                + " Target=\"media/image1.png\"/></Relationships>");
        convert(d.bodyWithSection("<w:sectPr><w:headerReference w:type=\"default\" r:id=\"rId20\"/></w:sectPr>",
                p("", r("", t("제1조")))).toBytes());
        Element header = first(HP, "header");
        Element pic = (Element) header.getElementsByTagNameNS(HP, "pic").item(0);
        assertTrue(pic != null);
        assertEquals("BEHIND_TEXT", pic.getAttribute("textWrap"));
        Element pos = (Element) pic.getElementsByTagNameNS(HP, "pos").item(0);
        assertEquals("0", pos.getAttribute("treatAsChar"));
        assertEquals("PAGE", pos.getAttribute("horzRelTo"));
        assertEquals("PAGE", pos.getAttribute("vertRelTo"));
        assertEquals("CENTER", pos.getAttribute("horzAlign"));
        assertEquals("CENTER", pos.getAttribute("vertAlign"));
        assertEquals("42500", ((Element) pic.getElementsByTagNameNS(HP, "sz").item(0)).getAttribute("width"));
        assertEquals("36835", ((Element) pic.getElementsByTagNameNS(HP, "sz").item(0)).getAttribute("height"));
        // Word '희미하게' → 한글 '워터마크 효과'가 저장하는 값
        Element img = (Element) pic.getElementsByTagNameNS(HC, "img").item(0);
        assertEquals("70", img.getAttribute("bright"));
        assertEquals("-50", img.getAttribute("contrast"));
        assertEquals("REAL_PIC", img.getAttribute("effect"));
        assertEquals(0, result.getWarnings().size());
        assertEquals(0, result.getSkippedCount());
    }

    @Test
    void otherBrightnessBecomesWatermarkWithWarning() throws Exception {
        convert(new TestDocx().image("rId30", "media/image1.png", png())
                .body(p("", r("", pict("width:72pt;height:36pt", " gain=\"2\" blacklevel=\"-6554f\"", ""))))
                .toBytes());
        Element img = first(HC, "img");
        assertEquals("70", img.getAttribute("bright"));
        assertContains(result.getWarnings().toString(), "워터마크 효과로 바꿈");
    }

    @Test
    void pictureWithoutEffectKeepsColors() throws Exception {
        convert(new TestDocx().image("rId30", "media/image1.png", png())
                .body(p("", r("", pict("width:72pt;height:36pt", "", "")))).toBytes());
        assertEquals("0", first(HC, "img").getAttribute("bright"));
        assertEquals("0", first(HC, "img").getAttribute("contrast"));
    }

    @Test
    void absoluteOffsetAndSquareWrap() throws Exception {
        convert(new TestDocx().image("rId30", "media/image1.png", png())
                .body(p("", r("", pict("position:absolute;margin-left:1in;margin-top:-10pt;width:1in;height:1in;"
                        + "mso-position-horizontal-relative:page;mso-position-vertical-relative:text;z-index:3", "",
                        "<w10:wrap type=\"square\"/>"))))
                .toBytes());
        Element pic = first(HP, "pic");
        assertEquals("SQUARE", pic.getAttribute("textWrap"));
        Element pos = first(HP, "pos");
        assertEquals("PAPER", pos.getAttribute("horzRelTo"));
        assertEquals("PARA", pos.getAttribute("vertRelTo"));
        assertEquals("7200", pos.getAttribute("horzOffset"));
        // 음수는 부호 없는 32비트 값
        assertEquals(String.valueOf(0x100000000L - 1000), pos.getAttribute("vertOffset"));
    }

    @Test
    void vmlWithoutPictureIsSkipped() throws Exception {
        convert(new TestDocx().body(p("", r("", "<w:pict" + VML_NS + "><v:line from=\"0,0\" to=\"10pt,0\"/></w:pict>")))
                .toBytes());
        assertEquals(1, result.getSkippedCount());
        assertEquals(0, result.getPictureCount());
    }
}

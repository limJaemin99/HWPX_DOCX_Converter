package io.github.limjaemin99.hwpxdocx.internal.hwpx;

import io.github.limjaemin99.hwpxdocx.ConvertException;
import io.github.limjaemin99.hwpxdocx.internal.SafeXml;
import io.github.limjaemin99.hwpxdocx.testkit.Test;
import org.w3c.dom.Element;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertContains;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertEquals;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertThrows;

class HwpxPackageTest {

    @Test
    void readsSkeleton() throws Exception {
        HwpxPackage pkg = HwpxPackage.read(new ByteArrayInputStream(HwpxSkeleton.toBytes()));
        assertEquals("[Contents/section0.xml]", pkg.sections().toString());
        assertEquals("hh:head", pkg.parse("Contents/header.xml").getDocumentElement().getTagName());
    }

    private static byte[] zipOf(int entries, int bytesEach) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(out)) {
            for (int i = 0; i < entries; i++) {
                z.putNextEntry(new ZipEntry("f" + i));
                z.write(new byte[bytesEach]);
                z.closeEntry();
            }
        }
        return out.toByteArray();
    }

    @Test
    void limitsEntryCountAndSize() throws Exception {
        ConvertException many = assertThrows(ConvertException.class,
                () -> HwpxPackage.unzip(new ByteArrayInputStream(zipOf(5, 1)), 4, 1000));
        assertContains(many.getMessage(), "너무 많음");
        // 0으로 채운 1 MB는 몇 KB로 압축되지만 풀면 한도를 넘음
        ConvertException big = assertThrows(ConvertException.class,
                () -> HwpxPackage.unzip(new ByteArrayInputStream(zipOf(1, 1 << 20)), 10, 1 << 19));
        assertContains(big.getMessage(), "너무 큼");
    }

    @Test
    void notAZip() {
        assertThrows(ConvertException.class, () -> HwpxPackage.read(
                new ByteArrayInputStream("not a zip".getBytes(StandardCharsets.US_ASCII))));
    }

    @Test
    void unsignedIntegersBecomeNegative() throws Exception {
        Element e = SafeXml.parse(new ByteArrayInputStream(
                "<a v=\"4294966296\" w=\"-3\" x=\"z\"/>".getBytes(StandardCharsets.UTF_8)), "a").getDocumentElement();
        assertEquals(-1000, HwpxHeader.intAttr(e, "v", 0));
        assertEquals(-3, HwpxHeader.intAttr(e, "w", 0));
        assertEquals(7, HwpxHeader.intAttr(e, "x", 7));
        assertEquals(7, HwpxHeader.intAttr(e, "none", 7));
    }
}

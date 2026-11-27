package io.github.limjaemin99.hwpxdocx.internal.d2h;

import io.github.limjaemin99.hwpxdocx.ConvertException;
import io.github.limjaemin99.hwpxdocx.HwpxDocxConverter;
import io.github.limjaemin99.hwpxdocx.testkit.Malformed;
import io.github.limjaemin99.hwpxdocx.testkit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertContains;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertThrows;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertTrue;

class DocxMalformedTest {

    private static void convert(byte[] docx) throws Exception {
        HwpxDocxConverter.docxToHwpx(new ByteArrayInputStream(docx), new ByteArrayOutputStream(), null);
    }

    @Test
    void richDocumentConverts() throws Exception {
        convert(DocxSamples.rich());
    }

    @Test
    void hugeGridSpanFailsWithConvertException() throws Exception {
        byte[] docx = new TestDocx().body(DocxSamples.table("", "99999999")).toBytes();
        ConvertException e = assertThrows(ConvertException.class, () -> convert(docx));
        assertContains(e.getMessage(), "표 크기");
    }

    @Test
    void hugeGridBeforeFailsWithConvertException() throws Exception {
        byte[] docx = new TestDocx().body(DocxSamples.table("<w:trPr><w:gridBefore w:val=\"2147483647\"/></w:trPr>", "2")).toBytes();
        assertThrows(ConvertException.class, () -> convert(docx));
    }

    @Test
    void negativeGridBeforeIgnored() throws Exception {
        convert(new TestDocx().body(DocxSamples.table("<w:trPr><w:gridBefore w:val=\"-3\"/></w:trPr>", "2")).toBytes());
    }

    @Test
    void brokenDocxNeverThrowsOtherErrors() throws Exception {
        int ok = Malformed.run(DocxSamples.rich(), false, 1L, 400);
        assertTrue(ok > 200);
    }
}

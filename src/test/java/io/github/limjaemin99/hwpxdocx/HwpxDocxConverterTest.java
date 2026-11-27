package io.github.limjaemin99.hwpxdocx;

import io.github.limjaemin99.hwpxdocx.testkit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertThrows;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertTrue;

class HwpxDocxConverterTest {

    private static final byte[] NOT_A_PACKAGE = "not a zip".getBytes(StandardCharsets.US_ASCII);

    @Test
    void rejectsNullStreams() {
        assertThrows(NullPointerException.class,
                () -> HwpxDocxConverter.hwpxToDocx(null, new ByteArrayOutputStream(), null));
        assertThrows(NullPointerException.class,
                () -> HwpxDocxConverter.docxToHwpx(new ByteArrayInputStream(NOT_A_PACKAGE), null, null));
    }

    @Test
    void invalidInputFailsWithConvertException() {
        assertThrows(ConvertException.class, () -> HwpxDocxConverter.hwpxToDocx(
                new ByteArrayInputStream(NOT_A_PACKAGE), new ByteArrayOutputStream(), null));
        assertThrows(ConvertException.class, () -> HwpxDocxConverter.docxToHwpx(
                new ByteArrayInputStream(NOT_A_PACKAGE), new ByteArrayOutputStream(), ConvertOptions.defaults()));
    }

    @Test
    void convertExceptionIsIOException() {
        assertTrue(IOException.class.isAssignableFrom(ConvertException.class));
    }
}

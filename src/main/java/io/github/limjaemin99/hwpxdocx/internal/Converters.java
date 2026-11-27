package io.github.limjaemin99.hwpxdocx.internal;

import io.github.limjaemin99.hwpxdocx.internal.d2h.DocxToHwpx;
import io.github.limjaemin99.hwpxdocx.internal.h2d.HwpxToDocx;

public final class Converters {

    private Converters() {
    }

    public static Converter hwpxToDocx() {
        return new HwpxToDocx();
    }

    public static Converter docxToHwpx() {
        return new DocxToHwpx();
    }
}
package io.github.limjaemin99.hwpxdocx.internal;

import io.github.limjaemin99.hwpxdocx.ConvertException;
import io.github.limjaemin99.hwpxdocx.internal.h2d.HwpxToDocx;

public final class Converters {

    private Converters() {
    }

    public static Converter hwpxToDocx() {
        return new HwpxToDocx();
    }

    public static Converter docxToHwpx() {
        return notImplemented("DOCX → HWPX");
    }

    private static Converter notImplemented(final String name) {
        return (in, out, options, stats) -> {
            throw new ConvertException(name + " 변환은 아직 구현되지 않았습니다");
        };
    }
}

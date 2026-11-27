package io.github.limjaemin99.hwpxdocx.internal;

import io.github.limjaemin99.hwpxdocx.ConvertOptions;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** 한 방향의 변환. 구현은 상태를 갖지 않고, 센 값은 {@link ConvertStats}에 남긴다. */
public interface Converter {

    void convert(InputStream in, OutputStream out, ConvertOptions options, ConvertStats stats)
            throws IOException;
}

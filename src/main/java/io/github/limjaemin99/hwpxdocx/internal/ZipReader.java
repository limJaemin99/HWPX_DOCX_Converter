package io.github.limjaemin99.hwpxdocx.internal;

import io.github.limjaemin99.hwpxdocx.ConvertException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

/** HWPX·DOCX 패키지(ZIP) 읽기. 항목 내용을 메모리에 올린다. */
public final class ZipReader {

    // 압축 폭탄 방어. 법령 문서는 수 MB 수준이라 넉넉히 잡음
    public static final int MAX_ENTRIES = 10000;
    public static final long MAX_TOTAL_BYTES = 1L << 30;

    private ZipReader() {
    }

    public static Map<String, byte[]> unzip(InputStream in) throws IOException {
        return unzip(in, MAX_ENTRIES, MAX_TOTAL_BYTES);
    }

    /** 항목 이름 → 내용 (ZIP 순서). 폴더 항목은 뺀다. */
    public static Map<String, byte[]> unzip(InputStream in, int maxEntries, long maxTotal) throws IOException {
        Map<String, byte[]> m = new LinkedHashMap<String, byte[]>();
        long total = 0;
        try (ZipInputStream zip = new ZipInputStream(in, StandardCharsets.UTF_8)) {
            ZipEntry e;
            byte[] buf = new byte[8192];
            while ((e = zip.getNextEntry()) != null) {
                if (m.size() >= maxEntries) {
                    throw new ConvertException("패키지 항목이 너무 많음 (" + maxEntries + "개 초과)");
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                int n;
                while ((n = zip.read(buf)) > 0) {
                    total += n;
                    if (total > maxTotal) {
                        throw new ConvertException("패키지 압축을 푼 크기가 너무 큼 (" + maxTotal + "바이트 초과)");
                    }
                    out.write(buf, 0, n);
                }
                if (!e.isDirectory()) {
                    m.put(e.getName(), out.toByteArray());
                }
            }
        } catch (ZipException e) {
            throw new ConvertException("ZIP 패키지를 읽지 못함: " + e.getMessage(), e);
        }
        if (m.isEmpty()) {
            throw new ConvertException("ZIP 패키지가 아니거나 비어 있음");
        }
        return m;
    }
}
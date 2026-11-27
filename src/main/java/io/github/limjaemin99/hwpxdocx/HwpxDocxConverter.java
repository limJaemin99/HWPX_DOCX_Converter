package io.github.limjaemin99.hwpxdocx;

import io.github.limjaemin99.hwpxdocx.internal.ConvertStats;
import io.github.limjaemin99.hwpxdocx.internal.Converter;
import io.github.limjaemin99.hwpxdocx.internal.Converters;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * HWPX와 DOCX 문서를 서로 바꾼다.
 *
 * <p>입력·출력 스트림은 닫지 않는다. 정적 상태가 없어 여러 스레드에서 동시에 불러도 된다.
 *
 * <pre>{@code
 * try (InputStream in = Files.newInputStream(src);
 *      OutputStream out = Files.newOutputStream(dst)) {
 *     ConvertResult r = HwpxDocxConverter.hwpxToDocx(in, out, null);
 * }
 * }</pre>
 */
public final class HwpxDocxConverter {

    private HwpxDocxConverter() {
    }

    /**
     * HWPX를 DOCX로 바꾼다.
     *
     * @param in      HWPX 입력
     * @param out     DOCX 출력
     * @param options 변환 설정. {@code null}이면 {@link ConvertOptions#defaults()}
     * @return 옮긴 요소 수, 건너뛴 요소, 경고
     * @throws ConvertException 입력이 HWPX가 아니거나 변환할 수 없을 때
     * @throws IOException      스트림을 읽거나 쓰지 못했을 때
     */
    public static ConvertResult hwpxToDocx(InputStream in, OutputStream out, ConvertOptions options)
            throws IOException {
        return run(Converters.hwpxToDocx(), in, out, options);
    }

    /**
     * DOCX를 HWPX로 바꾼다.
     *
     * @param in      DOCX 입력
     * @param out     HWPX 출력
     * @param options 변환 설정. {@code null}이면 {@link ConvertOptions#defaults()}
     * @return 옮긴 요소 수, 건너뛴 요소, 경고
     * @throws ConvertException 입력이 DOCX가 아니거나 변환할 수 없을 때
     * @throws IOException      스트림을 읽거나 쓰지 못했을 때
     */
    public static ConvertResult docxToHwpx(InputStream in, OutputStream out, ConvertOptions options)
            throws IOException {
        return run(Converters.docxToHwpx(), in, out, options);
    }

    private static ConvertResult run(Converter converter, InputStream in, OutputStream out,
                                     ConvertOptions options) throws IOException {
        if (in == null) {
            throw new NullPointerException("in");
        }
        if (out == null) {
            throw new NullPointerException("out");
        }
        ConvertOptions opt = options != null ? options : ConvertOptions.defaults();
        ConvertStats stats = new ConvertStats(opt);
        converter.convert(in, out, opt, stats);
        return new ConvertResult(stats);
    }
}

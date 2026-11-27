package io.github.limjaemin99.hwpxdocx.testkit;

import io.github.limjaemin99.hwpxdocx.ConvertOptions;
import io.github.limjaemin99.hwpxdocx.ConvertResult;
import io.github.limjaemin99.hwpxdocx.HwpxDocxConverter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 시험에서 부르는 HWPX → DOCX 변환. 환경 변수 {@value #OUT_ENV}가 있으면 성공한 결과를 그 폴더에도 저장한다.
 * CI가 이 파일들을 Open XML SDK로 규격 검사한다.
 */
public final class TestConvert {

    public static final String OUT_ENV = "HWPXDOCX_TEST_DOCX_OUT";

    private static final AtomicInteger SEQ = new AtomicInteger();

    private TestConvert() {
    }

    public static ConvertResult hwpxToDocx(InputStream in, OutputStream out, ConvertOptions opt) throws IOException {
        String dir = System.getenv(OUT_ENV);
        if (dir == null || dir.isEmpty()) {
            return HwpxDocxConverter.hwpxToDocx(in, out, opt);
        }
        ByteArrayOutputStream copy = new ByteArrayOutputStream();
        ConvertResult r = HwpxDocxConverter.hwpxToDocx(in, copy, opt);
        byte[] docx = copy.toByteArray();
        out.write(docx);
        Path d = Paths.get(dir);
        Files.createDirectories(d);
        Files.write(d.resolve(String.format("%04d-%s.docx", SEQ.incrementAndGet(), caller())), docx);
        return r;
    }

    // 파일 이름으로 어느 시험이 만든 결과인지 알 수 있게 함. 시험 클래스 안 도우미 메서드를 건너뛰려고 가장 바깥 프레임을 씀
    private static String caller() {
        String name = "unknown";
        for (StackTraceElement e : new Throwable().getStackTrace()) {
            String c = e.getClassName();
            if (c.endsWith("Test")) {
                name = c.substring(c.lastIndexOf('.') + 1) + "." + e.getMethodName();
            }
        }
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}

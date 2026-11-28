package io.github.limjaemin99.hwpxdocx;

import io.github.limjaemin99.hwpxdocx.testkit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertEquals;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertTrue;

/** 공개 시험 문서(한글에서 저장한 HWPX)를 HWPX → DOCX → HWPX로 왕복. */
public class RoundTripTest {

    private ConvertResult there;
    private ConvertResult back;

    private Map<String, String> roundTrip(String name) throws IOException {
        byte[] hwpx;
        try (InputStream in = RoundTripTest.class.getResourceAsStream("/docs/hwpx/" + name)) {
            assertTrue(in != null);
            hwpx = read(in);
        }
        ByteArrayOutputStream docx = new ByteArrayOutputStream();
        there = HwpxDocxConverter.hwpxToDocx(new ByteArrayInputStream(hwpx), docx, null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        back = HwpxDocxConverter.docxToHwpx(new ByteArrayInputStream(docx.toByteArray()), out, null);
        Map<String, String> m = new LinkedHashMap<String, String>();
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(out.toByteArray()))) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                if (e.getName().startsWith("Contents/section")) {
                    m.put(e.getName(), new String(read(z), StandardCharsets.UTF_8));
                }
            }
        }
        return m;
    }

    private static byte[] read(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            b.write(buf, 0, n);
        }
        return b.toByteArray();
    }

    private static int count(String s, String what) {
        int n = 0;
        for (int i = s.indexOf(what); i >= 0; i = s.indexOf(what, i + 1)) {
            n++;
        }
        return n;
    }

    /** DOCX로 옮기지 못하는 수식이 든 문단은 보관했다가 그대로 되살림. */
    @Test
    public void skippedEquationComesBack() throws Exception {
        Map<String, String> sections = roundTrip("kept-equation.hwpx");
        assertEquals(Integer.valueOf(1), there.getSkipped().get("hp:equation"));
        String sec = sections.get("Contents/section0.xml");
        assertEquals(1, count(sec, "<hp:equation"));
        assertTrue(sec.contains("a+b=c"));
        assertTrue(back.getWarnings().contains("보관한 원본 문단 1개를 되살림"));
    }

    /** 구역 중간에서 머리말이 바뀌는 문서: Word에서는 구역이 나뉘지만 돌아오면 구역 하나에 머리말 둘. */
    @Test
    public void headerChangeStaysInOneSection() throws Exception {
        Map<String, String> sections = roundTrip("header-change.hwpx");
        assertEquals(1, sections.size());
        String sec = sections.get("Contents/section0.xml");
        assertEquals(2, count(sec, "<hp:header "));
        assertTrue(sec.contains("제2장 국민의 권리와 의무"));
    }
}

package io.github.limjaemin99.hwpxdocx.regress;

import io.github.limjaemin99.hwpxdocx.internal.docx.DocxSkeleton;
import io.github.limjaemin99.hwpxdocx.internal.hwpx.HwpxSkeleton;
import io.github.limjaemin99.hwpxdocx.testkit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertContains;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertEquals;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertFalse;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertTrue;

class RegressionTest {

    private static byte[] zip(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                z.putNextEntry(new ZipEntry(e.getKey()));
                z.write(e.getValue());
                z.closeEntry();
            }
        }
        return out.toByteArray();
    }

    private static byte[] replace(Map<String, byte[]> entries, String part, String from, String to)
            throws IOException {
        String xml = new String(entries.get(part), StandardCharsets.UTF_8);
        assertTrue(xml.contains(from));
        entries.put(part, xml.replace(from, to).getBytes(StandardCharsets.UTF_8));
        return zip(entries);
    }

    private static byte[] hwpxWithText() throws IOException {
        return replace(HwpxSkeleton.entries(), "Contents/section0.xml", "<hp:t/>",
                "<hp:t>대한 민국<hp:tab/>헌법</hp:t>");
    }

    private static byte[] docxWithText() throws IOException {
        Map<String, byte[]> m = DocxSkeleton.entries();
        m.put("word/footnotes.xml", ("<w:footnotes xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
                + "<w:footnote><w:p><w:r><w:t>각주</w:t></w:r></w:p></w:footnote></w:footnotes>")
                .getBytes(StandardCharsets.UTF_8));
        return replace(m, "word/document.xml", "<w:p/>",
                "<w:p><w:r><w:t xml:space=\"preserve\">제1조 대한</w:t></w:r><w:r><w:instrText>PAGE</w:instrText>"
                        + "</w:r></w:p>");
    }

    @Test
    void countsTextWithoutSpaces() throws Exception {
        assertEquals(0, TextCounter.count(HwpxSkeleton.toBytes(), true));
        assertEquals(0, TextCounter.count(DocxSkeleton.toBytes(), false));
        assertEquals(6, TextCounter.count(hwpxWithText(), true));
        // instrText(필드 명령)는 본문이 아니라 세지 않음, 각주는 셈
        assertEquals(7, TextCounter.count(docxWithText(), false));
        assertEquals(3, TextCounter.nonSpace("가　나 \t다\n"));
    }

    @Test
    void tsvRoundTrip() {
        Row r = new Row("a/b.hwpx", true, 10, 9, 3, 1, 0, 2, "무시됨");
        Row back = Row.parseTsv(r.toTsv());
        assertEquals(r.toTsv(), back.toTsv());
        assertEquals(null, back.message);
    }

    @Test
    void rejectsBrokenBaselineLine() {
        for (String bad : Arrays.asList("x\tok\t1", "x\tmaybe\t1\t1\t1\t1\t1\t1", "x\tok\t1\t1\t1\t1\t1\tz")) {
            try {
                Row.parseTsv(bad);
                throw new AssertionError("받아들이면 안 됨: " + bad);
            } catch (IllegalArgumentException expected) {
                // 통과
            }
        }
    }

    private static Row row(boolean ok, int src, int out, int para, int skipped) {
        return new Row("d.hwpx", ok, src, out, para, 0, 0, skipped, null);
    }

    @Test
    void detectsWorse() {
        Row base = row(true, 100, 98, 10, 1);
        assertTrue(Row.worse(base, base).isEmpty());
        assertContains(Row.worse(base, row(true, 100, 97, 10, 1)).toString(), "결과 글자 98 → 97");
        assertContains(Row.worse(base, row(true, 100, 98, 9, 1)).toString(), "문단 10 → 9");
        assertContains(Row.worse(base, row(true, 100, 98, 10, 2)).toString(), "건너뜀 1 → 2");
        assertContains(Row.worse(base, Row.failed("d.hwpx", 100, "x")).toString(), "변환 실패");
        assertContains(Row.worse(base, row(true, 120, 120, 12, 0)).toString(), "기준값 갱신 필요");
        assertContains(Row.worse(null, base).toString(), "기준값 없음");
    }

    @Test
    void betterIsNotWorse() {
        Row base = row(true, 100, 90, 10, 3);
        Row now = row(true, 100, 99, 11, 0);
        assertTrue(Row.worse(base, now).isEmpty());
        assertEquals(3, Row.changed(base, now).size());
        Row failedBase = Row.failed("d.hwpx", 100, "x");
        assertTrue(Row.worse(failedBase, failedBase).isEmpty());
        assertTrue(Row.worse(failedBase, now).isEmpty());
        assertEquals(Arrays.asList("변환 성공"), Row.changed(failedBase, now));
    }

    private static Path tempDocs() throws IOException {
        Path dir = Files.createTempDirectory("regress");
        Files.createDirectories(dir.resolve("docs/sub"));
        Files.write(dir.resolve("docs/a.hwpx"), hwpxWithText());
        Files.write(dir.resolve("docs/sub/b.docx"), docxWithText());
        Files.write(dir.resolve("docs/README.md"), "x".getBytes(StandardCharsets.UTF_8));
        return dir;
    }

    private static void delete(Path dir) throws IOException {
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path p : (Iterable<Path>) s.sorted(Comparator.reverseOrder())::iterator) {
                Files.delete(p);
            }
        }
    }

    private static int run(PrintStream out, String... args) {
        return Regression.run(args, out, out);
    }

    @Test
    void updateThenCompare() throws Exception {
        Path dir = tempDocs();
        try {
            ByteArrayOutputStream log = new ByteArrayOutputStream();
            PrintStream out = new PrintStream(log, true, "UTF-8");
            String docs = dir.resolve("docs").toString();
            String base = dir.resolve("base").toString();

            // 기준값이 없으면 문서마다 "기준값 없음"으로 실패
            assertEquals(Regression.WORSE, run(out, "--docs", docs, "--baseline", base));

            assertEquals(Regression.OK, run(out, "--docs", docs, "--baseline", base, "--update"));
            List<String> lines = Files.readAllLines(dir.resolve("base").resolve(Regression.BASELINE_FILE),
                    StandardCharsets.UTF_8);
            assertEquals(3, lines.size());
            assertEquals(Row.HEADER, lines.get(0));
            assertTrue(lines.get(1).startsWith("a.hwpx\t"));
            assertTrue(lines.get(2).startsWith("sub/b.docx\t"));

            assertEquals(Regression.OK, run(out, "--docs", docs, "--baseline", base));

            // 문서가 사라지면 실패
            Files.delete(dir.resolve("docs/a.hwpx"));
            log.reset();
            assertEquals(Regression.WORSE, run(out, "--docs", docs, "--baseline", base));
            assertContains(log.toString("UTF-8"), "a.hwpx: 기준값에는 있는데 문서가 없음");
        } finally {
            delete(dir);
        }
    }

    @Test
    void writesConvertedFilesOnlyWhenConversionSucceeds() throws Exception {
        Path dir = tempDocs();
        try {
            PrintStream out = new PrintStream(new ByteArrayOutputStream(), true, "UTF-8");
            run(out, "--docs", dir.resolve("docs").toString(), "--baseline", dir.resolve("base").toString(),
                    "--out", dir.resolve("out").toString(), "--update");
            Map<String, Row> base = Regression.readBaseline(dir.resolve("base").resolve(Regression.BASELINE_FILE));
            for (Row r : base.values()) {
                Path converted = dir.resolve("out").resolve(r.name + (r.name.endsWith(".hwpx") ? ".docx" : ".hwpx"));
                assertEquals(r.ok, Files.exists(converted));
            }
        } finally {
            delete(dir);
        }
    }

    @Test
    void usageErrors() throws Exception {
        PrintStream out = new PrintStream(new ByteArrayOutputStream(), true, "UTF-8");
        assertEquals(Regression.USAGE, run(out));
        assertEquals(Regression.USAGE, run(out, "--docs", "x"));
        assertEquals(Regression.USAGE, run(out, "--docs", "없는-폴더-zz", "--baseline", "b"));
        assertEquals(Regression.USAGE, run(out, "--bogus"));
        assertFalse(Files.exists(java.nio.file.Paths.get("b")));
    }
}

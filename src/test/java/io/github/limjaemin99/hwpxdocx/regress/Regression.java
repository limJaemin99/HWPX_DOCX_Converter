package io.github.limjaemin99.hwpxdocx.regress;

import io.github.limjaemin99.hwpxdocx.ConvertResult;
import io.github.limjaemin99.hwpxdocx.HwpxDocxConverter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 회귀 시험: 폴더 안 HWPX·DOCX를 모두 반대 형식으로 바꾸고 글자 수·요소 수를 기준값과 비교한다.
 * 기준값보다 나빠진 문서가 있으면 종료 코드 1.
 *
 * <pre>
 * Regression --docs &lt;문서 폴더&gt; --baseline &lt;기준값 폴더&gt; [--out &lt;결과 폴더&gt;] [--update]
 * </pre>
 * 기준값은 {@code <기준값 폴더>/regression.tsv}. {@code --update}는 지금 수치로 기준값을 다시 쓴다.
 * 저장소 밖 문서와 기준값도 폴더만 바꿔 주면 같은 방법으로 돈다.
 */
public final class Regression {

    static final String BASELINE_FILE = "regression.tsv";

    static final int OK = 0;
    static final int WORSE = 1;
    static final int USAGE = 2;

    private Regression() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        Path docs = null;
        Path baseline = null;
        Path outDir = null;
        boolean update = false;
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if ("--update".equals(a)) {
                update = true;
            } else if (i + 1 < args.length && "--docs".equals(a)) {
                docs = Paths.get(args[++i]);
            } else if (i + 1 < args.length && "--baseline".equals(a)) {
                baseline = Paths.get(args[++i]);
            } else if (i + 1 < args.length && "--out".equals(a)) {
                outDir = Paths.get(args[++i]);
            } else {
                err.println("알 수 없는 인자: " + a);
                docs = null;
                break;
            }
        }
        if (docs == null || baseline == null) {
            err.println("사용법: Regression --docs <문서 폴더> --baseline <기준값 폴더> [--out <결과 폴더>] [--update]");
            return USAGE;
        }
        if (!Files.isDirectory(docs)) {
            err.println("문서 폴더가 없습니다: " + docs);
            return USAGE;
        }
        try {
            return run(docs, baseline.resolve(BASELINE_FILE), outDir, update, out);
        } catch (IOException | IllegalArgumentException e) {
            err.println("회귀 시험 오류: " + e.getMessage());
            return USAGE;
        }
    }

    static int run(Path docs, Path baselineFile, Path outDir, boolean update, PrintStream out) throws IOException {
        List<Row> rows = new ArrayList<Row>();
        for (Path p : findDocuments(docs)) {
            rows.add(measure(docs, p, outDir));
        }
        if (update) {
            writeBaseline(baselineFile, rows);
            printTable(out, rows, Collections.<String, Row>emptyMap(), false);
            out.println("기준값을 다시 씀: " + baselineFile + " (문서 " + rows.size() + "개)");
            return OK;
        }
        Map<String, Row> base = readBaseline(baselineFile);
        int worse = printTable(out, rows, base, true);
        for (String name : base.keySet()) {
            if (!containsName(rows, name)) {
                out.println("  " + name + ": 기준값에는 있는데 문서가 없음");
                worse++;
            }
        }
        out.println("회귀 시험: 문서 " + rows.size() + "개, 나빠짐 " + worse + "개");
        return worse == 0 ? OK : WORSE;
    }

    private static boolean containsName(List<Row> rows, String name) {
        for (Row r : rows) {
            if (r.name.equals(name)) {
                return true;
            }
        }
        return false;
    }

    static List<Path> findDocuments(Path docs) throws IOException {
        try (Stream<Path> files = Files.walk(docs)) {
            return files.filter(Files::isRegularFile)
                    .filter(p -> isHwpx(p) || isDocx(p))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    private static boolean isHwpx(Path p) {
        return p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".hwpx");
    }

    private static boolean isDocx(Path p) {
        return p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".docx");
    }

    static Row measure(Path docs, Path file, Path outDir) throws IOException {
        String name = docs.relativize(file).toString().replace('\\', '/');
        boolean hwpx = isHwpx(file);
        byte[] in = Files.readAllBytes(file);
        int sourceChars;
        try {
            sourceChars = TextCounter.count(in, hwpx);
        } catch (IOException | RuntimeException e) {
            return Row.failed(name, -1, "원본을 읽지 못함: " + e.getMessage());
        }
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        ConvertResult r;
        int outputChars;
        try {
            r = hwpx
                    ? HwpxDocxConverter.hwpxToDocx(new ByteArrayInputStream(in), buf, null)
                    : HwpxDocxConverter.docxToHwpx(new ByteArrayInputStream(in), buf, null);
            outputChars = TextCounter.count(buf.toByteArray(), !hwpx);
        } catch (IOException | RuntimeException e) {
            return Row.failed(name, sourceChars, e.getMessage());
        }
        if (outDir != null) {
            Path target = outDir.resolve(name + (hwpx ? ".docx" : ".hwpx"));
            Files.createDirectories(target.getParent());
            Files.write(target, buf.toByteArray());
        }
        return new Row(name, true, sourceChars, outputChars, r.getParagraphCount(), r.getTableCount(),
                r.getPictureCount(), r.getSkippedCount(), null);
    }

    static Map<String, Row> readBaseline(Path file) throws IOException {
        Map<String, Row> m = new TreeMap<String, Row>();
        if (!Files.exists(file)) {
            return m;
        }
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) {
                continue;
            }
            Row r = Row.parseTsv(line);
            if (m.put(r.name, r) != null) {
                throw new IllegalArgumentException("기준값에 같은 문서가 두 번 있음: " + r.name);
            }
        }
        return m;
    }

    static void writeBaseline(Path file, List<Row> rows) throws IOException {
        List<String> lines = new ArrayList<String>();
        lines.add(Row.HEADER);
        TreeSet<String> seen = new TreeSet<String>();
        for (Row r : rows) {
            if (seen.add(r.name)) {
                lines.add(r.toTsv());
            }
        }
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        // 줄 끝을 \n으로 고정해 Windows·Linux에서 같은 파일이 나오게 함
        Files.write(file, (String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8));
    }

    /** 나빠진 문서 수를 돌려준다. */
    private static int printTable(PrintStream out, List<Row> rows, Map<String, Row> base, boolean compare) {
        out.println(String.format("%-36s %-4s %8s %8s %6s %4s %4s %6s  %s",
                "문서", "상태", "원본글자", "결과글자", "문단", "표", "그림", "건너뜀", compare ? "기준 대비" : ""));
        int worse = 0;
        for (Row r : rows) {
            String note = "";
            if (compare) {
                Row b = base.get(r.name);
                List<String> w = Row.worse(b, r);
                if (!w.isEmpty()) {
                    worse++;
                    note = "나빠짐: " + String.join(", ", w);
                } else {
                    List<String> c = Row.changed(b, r);
                    note = c.isEmpty() ? "같음" : "좋아짐: " + String.join(", ", c);
                }
            }
            if (!r.ok && r.message != null) {
                note = note.isEmpty() ? r.message : note + " / " + r.message;
            }
            out.println(String.format("%-36s %-4s %8d %8d %6d %4d %4d %6d  %s",
                    r.name, r.ok ? "ok" : "fail", r.sourceChars, r.outputChars, r.paragraphs, r.tables,
                    r.pictures, r.skipped, note));
        }
        return worse;
    }
}

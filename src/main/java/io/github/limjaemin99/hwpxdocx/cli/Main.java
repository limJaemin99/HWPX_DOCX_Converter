package io.github.limjaemin99.hwpxdocx.cli;

import io.github.limjaemin99.hwpxdocx.ConvertException;
import io.github.limjaemin99.hwpxdocx.ConvertResult;
import io.github.limjaemin99.hwpxdocx.HwpxDocxConverter;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;

/** 명령줄 도구: {@code java -jar hwpx-docx-converter-<버전>.jar 입력.hwpx 출력.docx} */
public final class Main {

    static final int OK = 0;
    static final int FAILED = 1;
    static final int USAGE = 2;

    private static final String NAME = "hwpx-docx-converter";

    private Main() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 1 && ("-h".equals(args[0]) || "--help".equals(args[0]))) {
            out.println(usage());
            return OK;
        }
        if (args.length == 1 && "--version".equals(args[0])) {
            out.println(NAME + " " + version());
            return OK;
        }
        if (args.length != 2) {
            err.println(usage());
            return USAGE;
        }

        String inExt = extension(args[0]);
        String outExt = extension(args[1]);
        boolean toDocx;
        if ("hwpx".equals(inExt) && "docx".equals(outExt)) {
            toDocx = true;
        } else if ("docx".equals(inExt) && "hwpx".equals(outExt)) {
            toDocx = false;
        } else {
            err.println("입력·출력 확장자는 .hwpx → .docx 또는 .docx → .hwpx 여야 합니다");
            return USAGE;
        }

        Path inPath = Paths.get(args[0]);
        Path outPath = Paths.get(args[1]).toAbsolutePath();
        if (!Files.isRegularFile(inPath)) {
            err.println("입력 파일이 없습니다: " + inPath);
            return USAGE;
        }

        // 실패했을 때 반쯤 쓴 출력이 남지 않게 임시 파일에 쓰고 끝나면 옮김
        Path tmp = null;
        try {
            tmp = Files.createTempFile(outPath.getParent(), outPath.getFileName().toString(), ".tmp");
            ConvertResult result;
            try (InputStream in = new BufferedInputStream(Files.newInputStream(inPath));
                 OutputStream os = new BufferedOutputStream(Files.newOutputStream(tmp))) {
                result = toDocx
                        ? HwpxDocxConverter.hwpxToDocx(in, os, null)
                        : HwpxDocxConverter.docxToHwpx(in, os, null);
            }
            Files.move(tmp, outPath, StandardCopyOption.REPLACE_EXISTING);
            tmp = null;
            report(out, outPath, result);
            return OK;
        } catch (ConvertException e) {
            err.println("변환 실패: " + e.getMessage());
            return FAILED;
        } catch (IOException e) {
            err.println("파일 오류: " + e);
            return FAILED;
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                    // 지우지 못한 임시 파일은 결과에 영향이 없음
                }
            }
        }
    }

    private static void report(PrintStream out, Path outPath, ConvertResult r) {
        out.println("변환 완료: " + outPath);
        out.println("  " + r);
        for (Map.Entry<String, Integer> e : r.getSkipped().entrySet()) {
            out.println("  건너뜀 " + e.getKey() + " " + e.getValue());
        }
        for (String w : r.getWarnings()) {
            out.println("  경고 " + w);
        }
    }

    private static String extension(String path) {
        int dot = path.lastIndexOf('.');
        return dot < 0 ? "" : path.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String version() {
        String v = Main.class.getPackage().getImplementationVersion();
        return v != null ? v : "(개발 빌드)";
    }

    private static String usage() {
        return "사용법: java -jar " + NAME + "-<버전>.jar <입력> <출력>\n"
                + "  확장자로 방향을 정함: .hwpx → .docx, .docx → .hwpx\n"
                + "  -h, --help   도움말\n"
                + "  --version    버전\n"
                + "종료 코드: 0 성공, 1 변환 실패, 2 사용법 오류";
    }
}

package io.github.limjaemin99.hwpxdocx.tools;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 개발 PC의 TTF·TTC 글꼴에서 Word 한 줄 높이 비율 표(font-line-height.tsv)를 만든다. 라이브러리에는 들어가지 않는다.
 * <pre>java -cp build/test-classes io.github.limjaemin99.hwpxdocx.tools.FontLineHeights 결과.tsv [글꼴 폴더...]</pre>
 * 글꼴 폴더를 주지 않으면 Windows 글꼴 폴더 두 곳을 읽는다.
 */
public final class FontLineHeights {

    /** 문서에 흔히 쓰는 글꼴. 영문 이름으로 찾고, 찾은 글꼴의 모든 언어 이름을 표에 넣는다. */
    static final List<String> FAMILIES = Arrays.asList(
            "Malgun Gothic", "Batang", "BatangChe", "Gulim", "GulimChe", "Dotum", "DotumChe", "Gungsuh", "GungsuhChe",
            "HCR Batang", "HCR Dotum", "NanumGothic", "NanumMyeongjo", "NanumBarunGothic",
            "HYSinMyeongJo-Medium", "HYGothic-Medium", "HYGothic-Extra", "HYMyeongJo-Extra", "HYGraphic-Medium",
            "HYHeadLine-Medium", "Times New Roman", "Arial", "Calibri", "Cambria", "Courier New", "Verdana", "Tahoma",
            "Segoe UI", "Georgia");

    // OS/2 ulCodePageRange1 비트 17~21: 일본어, 중국어 간체, 한국어 완성형, 중국어 번체, 한국어 조합형
    private static final int CJK_CODE_PAGES = 0x003E0000;

    // Word는 동아시아 글꼴의 한 줄을 (winAscent + winDescent)의 1.3배로 잡음 (Word에서 잰 값과 맞춘 비율)
    static final double EAST_ASIAN_FACTOR = 1.3;

    private FontLineHeights() {
    }

    /** 글꼴 하나의 이름들과 줄 높이 비율. */
    static final class Face {
        final Set<String> families = new HashSet<String>();
        boolean regular;
        double factor;
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("사용법: FontLineHeights <결과.tsv> [글꼴 폴더...]");
            System.exit(2);
        }
        List<File> dirs = new ArrayList<File>();
        for (int i = 1; i < args.length; i++) {
            dirs.add(new File(args[i]));
        }
        if (dirs.isEmpty()) {
            dirs.add(new File(System.getenv("WINDIR") == null ? "C:\\Windows" : System.getenv("WINDIR"), "Fonts"));
            String local = System.getenv("LOCALAPPDATA");
            if (local != null) {
                dirs.add(new File(local, "Microsoft\\Windows\\Fonts"));
            }
        }
        List<Face> faces = new ArrayList<Face>();
        for (File dir : dirs) {
            File[] files = dir.listFiles();
            if (files == null) {
                continue;
            }
            Arrays.sort(files);
            for (File f : files) {
                String n = f.getName().toLowerCase(Locale.ROOT);
                if (n.endsWith(".ttf") || n.endsWith(".ttc")) {
                    try {
                        faces.addAll(read(Files.readAllBytes(f.toPath())));
                    } catch (RuntimeException e) {
                        System.err.println("건너뜀: " + f.getName() + " (" + e + ")");
                    }
                }
            }
        }
        Map<String, Double> table = table(faces);
        try (PrintWriter w = new PrintWriter(new OutputStreamWriter(Files.newOutputStream(new File(args[0]).toPath()),
                StandardCharsets.UTF_8))) {
            w.print("# 글꼴 이름\tWord 한 줄 높이 ÷ 글자 크기. tools/FontLineHeights로 만듦\n");
            for (Map.Entry<String, Double> e : table.entrySet()) {
                w.print(e.getKey() + "\t" + String.format(Locale.ROOT, "%.3f", e.getValue()) + "\n");
            }
        }
        System.out.println(table.size() + "개 이름을 씀: " + args[0]);
    }

    /** 목록의 글꼴만, 같은 이름이면 Regular(없으면 처음 것)를 씀. */
    static Map<String, Double> table(List<Face> faces) {
        Map<String, Double> out = new TreeMap<String, Double>();
        Map<String, Boolean> fromRegular = new TreeMap<String, Boolean>();
        for (Face f : faces) {
            boolean wanted = false;
            for (String name : f.families) {
                wanted |= FAMILIES.contains(name);
            }
            if (!wanted) {
                continue;
            }
            for (String name : f.families) {
                Boolean prev = fromRegular.get(name);
                if (prev == null || (!prev && f.regular)) {
                    out.put(name, f.factor);
                    fromRegular.put(name, f.regular);
                }
            }
        }
        return out;
    }

    /** TTF 하나 또는 TTC 묶음. */
    static List<Face> read(byte[] data) {
        ByteBuffer b = ByteBuffer.wrap(data);
        List<Face> out = new ArrayList<Face>();
        if (b.getInt(0) == 0x74746366) { // 'ttcf'
            int n = b.getInt(8);
            for (int i = 0; i < n; i++) {
                out.add(face(b, b.getInt(12 + 4 * i)));
            }
        } else {
            out.add(face(b, 0));
        }
        return out;
    }

    static Face face(ByteBuffer b, int off) {
        int tables = b.getShort(off + 4) & 0xFFFF;
        int head = -1;
        int hhea = -1;
        int os2 = -1;
        int name = -1;
        for (int i = 0; i < tables; i++) {
            int rec = off + 12 + 16 * i;
            String tag = new String(new byte[]{b.get(rec), b.get(rec + 1), b.get(rec + 2), b.get(rec + 3)},
                    StandardCharsets.ISO_8859_1);
            int at = b.getInt(rec + 8);
            if ("head".equals(tag)) {
                head = at;
            } else if ("hhea".equals(tag)) {
                hhea = at;
            } else if ("OS/2".equals(tag)) {
                os2 = at;
            } else if ("name".equals(tag)) {
                name = at;
            }
        }
        if (head < 0 || hhea < 0 || os2 < 0 || name < 0) {
            throw new IllegalArgumentException("head·hhea·OS/2·name 표가 없음");
        }
        int upm = b.getShort(head + 18) & 0xFFFF;
        int ascender = b.getShort(hhea + 4);
        int descender = b.getShort(hhea + 6);
        int lineGap = b.getShort(hhea + 8);
        int version = b.getShort(os2) & 0xFFFF;
        int winAscent = b.getShort(os2 + 74) & 0xFFFF;
        int winDescent = b.getShort(os2 + 76) & 0xFFFF;
        int codePages = version >= 1 ? b.getInt(os2 + 78) : 0;
        Face f = new Face();
        f.factor = factor(upm, ascender, descender, lineGap, winAscent, winDescent, codePages);
        names(b, name, f);
        return f;
    }

    static double factor(int upm, int ascender, int descender, int lineGap, int winAscent, int winDescent, int codePages) {
        if ((codePages & CJK_CODE_PAGES) != 0) {
            return (winAscent + winDescent) * EAST_ASIAN_FACTOR / upm;
        }
        return (double) (ascender - descender + lineGap) / upm;
    }

    // Windows 플랫폼(3)의 글꼴 가족 이름(1)은 언어마다, 하위 이름(2)은 영어로 Regular인지 봄
    private static void names(ByteBuffer b, int at, Face f) {
        int count = b.getShort(at + 2) & 0xFFFF;
        int strings = at + (b.getShort(at + 4) & 0xFFFF);
        for (int i = 0; i < count; i++) {
            int rec = at + 6 + 12 * i;
            int platform = b.getShort(rec) & 0xFFFF;
            int lang = b.getShort(rec + 4) & 0xFFFF;
            int id = b.getShort(rec + 6) & 0xFFFF;
            int len = b.getShort(rec + 8) & 0xFFFF;
            int off = b.getShort(rec + 10) & 0xFFFF;
            if (platform != 3 || (id != 1 && id != 2)) {
                continue;
            }
            byte[] raw = new byte[len];
            for (int k = 0; k < len; k++) {
                raw[k] = b.get(strings + off + k);
            }
            String s = new String(raw, StandardCharsets.UTF_16BE);
            if (id == 1) {
                f.families.add(s);
            } else if (lang == 0x409 && ("Regular".equals(s) || "Normal".equals(s))) {
                f.regular = true;
            }
        }
    }
}

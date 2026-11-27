package io.github.limjaemin99.hwpxdocx.regress;

import java.util.ArrayList;
import java.util.List;

/** 문서 하나의 회귀 수치. 기준값 파일의 한 줄. */
final class Row {

    static final String HEADER = "# 문서\t상태\t원본글자\t결과글자\t문단\t표\t그림\t건너뜀";

    final String name;
    final boolean ok;
    final int sourceChars;
    final int outputChars;
    final int paragraphs;
    final int tables;
    final int pictures;
    final int skipped;
    /** 실패 이유. 기준값 파일에는 쓰지 않는다 (문구가 바뀌어도 기준값이 흔들리지 않게). */
    final String message;

    Row(String name, boolean ok, int sourceChars, int outputChars, int paragraphs, int tables, int pictures,
        int skipped, String message) {
        this.name = name;
        this.ok = ok;
        this.sourceChars = sourceChars;
        this.outputChars = outputChars;
        this.paragraphs = paragraphs;
        this.tables = tables;
        this.pictures = pictures;
        this.skipped = skipped;
        this.message = message;
    }

    static Row failed(String name, int sourceChars, String message) {
        return new Row(name, false, sourceChars, 0, 0, 0, 0, 0, message);
    }

    String toTsv() {
        return name + "\t" + (ok ? "ok" : "fail") + "\t" + sourceChars + "\t" + outputChars + "\t" + paragraphs
                + "\t" + tables + "\t" + pictures + "\t" + skipped;
    }

    static Row parseTsv(String line) {
        String[] f = line.split("\t", -1);
        if (f.length != 8 || !("ok".equals(f[1]) || "fail".equals(f[1]))) {
            throw new IllegalArgumentException("기준값 줄 형식이 아님: " + line);
        }
        return new Row(f[0], "ok".equals(f[1]), num(f[2], line), num(f[3], line), num(f[4], line),
                num(f[5], line), num(f[6], line), num(f[7], line), null);
    }

    private static int num(String s, String line) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("기준값 줄 형식이 아님: " + line, e);
        }
    }

    /** 기준값보다 나빠진 점. 비어 있으면 통과. */
    static List<String> worse(Row base, Row now) {
        List<String> w = new ArrayList<String>();
        if (base == null) {
            w.add("기준값 없음");
            return w;
        }
        if (now == null) {
            w.add("문서 없음");
            return w;
        }
        if (base.sourceChars != now.sourceChars) {
            // 문서 자체가 바뀌면 다른 수치도 비교할 수 없음
            w.add("원본 글자 수 " + base.sourceChars + " → " + now.sourceChars + " (문서가 바뀜, 기준값 갱신 필요)");
            return w;
        }
        if (base.ok && !now.ok) {
            w.add("변환 실패");
            return w;
        }
        if (!now.ok || !base.ok) {
            // 실패했던 기준값의 수치(0)와는 비교하지 않음
            return w;
        }
        less(w, "결과 글자", base.outputChars, now.outputChars);
        less(w, "문단", base.paragraphs, now.paragraphs);
        less(w, "표", base.tables, now.tables);
        less(w, "그림", base.pictures, now.pictures);
        if (now.skipped > base.skipped) {
            w.add("건너뜀 " + base.skipped + " → " + now.skipped);
        }
        return w;
    }

    private static void less(List<String> w, String label, int base, int now) {
        if (now < base) {
            w.add(label + " " + base + " → " + now);
        }
    }

    /** 기준값과 다르지만 나빠지지는 않은 점 (좋아졌거나 늘어난 값). */
    static List<String> changed(Row base, Row now) {
        List<String> c = new ArrayList<String>();
        if (base == null || now == null || base.sourceChars != now.sourceChars) {
            return c;
        }
        if (!base.ok && now.ok) {
            c.add("변환 성공");
            return c;
        }
        more(c, "결과 글자", base.outputChars, now.outputChars);
        more(c, "문단", base.paragraphs, now.paragraphs);
        more(c, "표", base.tables, now.tables);
        more(c, "그림", base.pictures, now.pictures);
        if (now.skipped < base.skipped) {
            c.add("건너뜀 " + base.skipped + " → " + now.skipped);
        }
        return c;
    }

    private static void more(List<String> c, String label, int base, int now) {
        if (now > base) {
            c.add(label + " " + base + " → " + now);
        }
    }
}

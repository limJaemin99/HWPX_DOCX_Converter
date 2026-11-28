package io.github.limjaemin99.hwpxdocx.internal.d2h;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * jar 안 글꼴 줄 높이 표(font-line-height.tsv, 개발 PC 글꼴로 미리 계산). 처음 쓸 때 한 번 읽고 바꾸지 않는다.
 * 실행하는 서버에 글꼴 파일이 없어도 된다.
 */
final class FontLineTable {

    private static final String RESOURCE = "/io/github/limjaemin99/hwpxdocx/font-line-height.tsv";

    private FontLineTable() {
    }

    private static final class Holder {
        static final Map<String, Double> TABLE = load(FontLineTable.class.getResourceAsStream(RESOURCE));
    }

    /** Word 한 줄 높이 ÷ 글자 크기. 표에 없는 글꼴이면 null. */
    static Double factor(String face) {
        return Holder.TABLE.get(face);
    }

    /** "글꼴 이름\t비율" 줄. #으로 시작하는 줄과 읽지 못하는 줄은 건너뜀. {@code in}이 null이면 빈 표. */
    static Map<String, Double> load(InputStream in) {
        Map<String, Double> m = new HashMap<String, Double>();
        if (in == null) {
            return Collections.emptyMap();
        }
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                int tab = line.indexOf('\t');
                if (line.startsWith("#") || tab <= 0) {
                    continue;
                }
                try {
                    int end = line.indexOf('	', tab + 1);
                    double v = Double.parseDouble(line.substring(tab + 1, end < 0 ? line.length() : end).trim());
                    if (v > 0.5 && v < 3) {
                        m.put(line.substring(0, tab), v);
                    }
                } catch (NumberFormatException ignored) {
                    // 숫자가 아닌 줄은 건너뜀
                }
            }
        } catch (IOException e) {
            // jar 안 자원을 못 읽으면 평균값으로 변환을 이어감
        }
        return Collections.unmodifiableMap(m);
    }
}

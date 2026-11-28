package io.github.limjaemin99.hwpxdocx.internal;

import io.github.limjaemin99.hwpxdocx.ConvertException;
import io.github.limjaemin99.hwpxdocx.ConvertOptions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 변환 한 번 동안 세는 값. 변환마다 새로 만들고 스레드 간에 같이 쓰지 않는다. */
public final class ConvertStats {

    private final boolean failOnUnsupported;
    private int paragraphCount;
    private int tableCount;
    private int pictureCount;
    private final Map<String, Integer> skipped = new LinkedHashMap<String, Integer>();
    private final List<String> warnings = new ArrayList<String>();

    public ConvertStats(ConvertOptions options) {
        this.failOnUnsupported = options.isFailOnUnsupported();
    }

    public void paragraph() {
        paragraphCount++;
    }

    public void table() {
        tableCount++;
    }

    public void picture() {
        pictureCount++;
    }

    public void skip(String element) throws ConvertException {
        if (failOnUnsupported) {
            throw new ConvertException("옮길 수 없는 요소: " + element);
        }
        Integer n = skipped.get(element);
        skipped.put(element, n == null ? 1 : n + 1);
    }

    /** 같은 문구는 한 번만 남긴다 (모양 하나가 문단 여럿에 쓰여도 경고가 쌓이지 않게). */
    public void warn(String message) {
        if (!warnings.contains(message)) {
            warnings.add(message);
        }
    }

    public int getParagraphCount() {
        return paragraphCount;
    }

    public int getTableCount() {
        return tableCount;
    }

    public int getPictureCount() {
        return pictureCount;
    }

    /** 건너뛴 요소 수의 합. */
    public int getSkipTotal() {
        int n = 0;
        for (Integer v : skipped.values()) {
            n += v;
        }
        return n;
    }

    public Map<String, Integer> getSkipped() {
        return skipped;
    }

    public List<String> getWarnings() {
        return warnings;
    }
}

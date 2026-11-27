package io.github.limjaemin99.hwpxdocx;

import io.github.limjaemin99.hwpxdocx.internal.ConvertStats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 변환 결과. 옮긴 요소 수, 옮기지 못하고 건너뛴 요소, 경고를 담는다.
 */
public final class ConvertResult {

    private final int paragraphCount;
    private final int tableCount;
    private final int pictureCount;
    private final Map<String, Integer> skipped;
    private final List<String> warnings;

    ConvertResult(ConvertStats stats) {
        this.paragraphCount = stats.getParagraphCount();
        this.tableCount = stats.getTableCount();
        this.pictureCount = stats.getPictureCount();
        this.skipped = Collections.unmodifiableMap(new LinkedHashMap<String, Integer>(stats.getSkipped()));
        this.warnings = Collections.unmodifiableList(new ArrayList<String>(stats.getWarnings()));
    }

    /** 옮긴 문단 수. */
    public int getParagraphCount() {
        return paragraphCount;
    }

    /** 옮긴 표 수. */
    public int getTableCount() {
        return tableCount;
    }

    /** 옮긴 그림 수. */
    public int getPictureCount() {
        return pictureCount;
    }

    /**
     * 옮기지 못하고 건너뛴 요소. 요소 이름(예: {@code hp:rect})과 개수를 처음 나온 순서대로 담는다.
     * 바꿀 수 없는 Map이다.
     */
    public Map<String, Integer> getSkipped() {
        return skipped;
    }

    /** 건너뛴 요소 개수의 합. */
    public int getSkippedCount() {
        int n = 0;
        for (int c : skipped.values()) {
            n += c;
        }
        return n;
    }

    /** 옮기긴 했지만 원본과 달라진 점. 바꿀 수 없는 List다. */
    public List<String> getWarnings() {
        return warnings;
    }

    @Override
    public String toString() {
        return "문단 " + paragraphCount + ", 표 " + tableCount + ", 그림 " + pictureCount
                + ", 건너뜀 " + getSkippedCount() + ", 경고 " + warnings.size();
    }
}

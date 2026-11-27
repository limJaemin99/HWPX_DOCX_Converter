package io.github.limjaemin99.hwpxdocx.internal;

import io.github.limjaemin99.hwpxdocx.ConvertException;

/** 깨진 입력이 메모리를 다 쓰지 않게 막는 상한. 실제 문서는 이 근처에도 가지 않는다. */
public final class Limits {

    public static final int MAX_TABLE_COLS = 10000;
    public static final long MAX_TABLE_CELLS = 4000000L;

    private Limits() {
    }

    /** 표 격자(행 × 열)가 상한을 넘으면 던진다. */
    public static void checkTable(long rows, long cols) throws ConvertException {
        if (cols > MAX_TABLE_COLS || rows * cols > MAX_TABLE_CELLS) {
            throw new ConvertException("표 크기가 비정상임 (행 " + rows + ", 열 " + cols + ")");
        }
    }
}

package io.github.limjaemin99.hwpxdocx;

import io.github.limjaemin99.hwpxdocx.internal.ConvertStats;
import io.github.limjaemin99.hwpxdocx.testkit.Test;

import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertEquals;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertThrows;

class ConvertResultTest {

    @Test
    void copiesCountsSkippedAndWarnings() throws Exception {
        ConvertStats s = new ConvertStats(ConvertOptions.defaults());
        s.paragraph();
        s.paragraph();
        s.table();
        s.picture();
        s.skip("hp:rect");
        s.skip("hp:ellipse");
        s.skip("hp:rect");
        s.warn("글꼴 대체");

        ConvertResult r = new ConvertResult(s);

        assertEquals(2, r.getParagraphCount());
        assertEquals(1, r.getTableCount());
        assertEquals(1, r.getPictureCount());
        assertEquals(3, r.getSkippedCount());
        assertEquals("[hp:rect, hp:ellipse]", r.getSkipped().keySet().toString());
        assertEquals(2, (int) r.getSkipped().get("hp:rect"));
        assertEquals(1, r.getWarnings().size());
        assertEquals("문단 2, 표 1, 그림 1, 건너뜀 3, 경고 1", r.toString());
    }

    @Test
    void resultIsSnapshotAndReadOnly() throws Exception {
        ConvertStats s = new ConvertStats(ConvertOptions.defaults());
        s.skip("a");
        final ConvertResult r = new ConvertResult(s);
        s.skip("a");
        s.warn("나중 경고");

        assertEquals(1, r.getSkippedCount());
        assertEquals(0, r.getWarnings().size());
        assertThrows(UnsupportedOperationException.class, () -> r.getSkipped().put("b", 1));
        assertThrows(UnsupportedOperationException.class, () -> r.getWarnings().add("x"));
    }
}

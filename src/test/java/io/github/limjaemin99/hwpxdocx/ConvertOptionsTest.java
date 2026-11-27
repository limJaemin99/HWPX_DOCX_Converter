package io.github.limjaemin99.hwpxdocx;

import io.github.limjaemin99.hwpxdocx.internal.ConvertStats;
import io.github.limjaemin99.hwpxdocx.testkit.Test;

import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertContains;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertFalse;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertThrows;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertTrue;

class ConvertOptionsTest {

    @Test
    void defaultsSkipUnsupported() {
        assertFalse(ConvertOptions.defaults().isFailOnUnsupported());
    }

    @Test
    void builderSetsFailOnUnsupported() {
        assertTrue(ConvertOptions.builder().failOnUnsupported(true).build().isFailOnUnsupported());
    }

    @Test
    void failOnUnsupportedStopsAtFirstSkip() {
        final ConvertStats s = new ConvertStats(ConvertOptions.builder().failOnUnsupported(true).build());
        ConvertException e = assertThrows(ConvertException.class, () -> s.skip("hp:rect"));
        assertContains(e.getMessage(), "hp:rect");
    }
}

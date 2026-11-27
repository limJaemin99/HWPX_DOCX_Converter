package io.github.limjaemin99.hwpxdocx.internal.h2d;

import io.github.limjaemin99.hwpxdocx.ConvertException;
import io.github.limjaemin99.hwpxdocx.ConvertResult;
import io.github.limjaemin99.hwpxdocx.HwpxDocxConverter;
import io.github.limjaemin99.hwpxdocx.internal.d2h.DocxSamples;
import io.github.limjaemin99.hwpxdocx.testkit.Malformed;
import io.github.limjaemin99.hwpxdocx.testkit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static io.github.limjaemin99.hwpxdocx.internal.h2d.TestHwpx.borderFill;
import static io.github.limjaemin99.hwpxdocx.internal.h2d.TestHwpx.para;
import static io.github.limjaemin99.hwpxdocx.internal.h2d.TestHwpx.run;
import static io.github.limjaemin99.hwpxdocx.internal.h2d.TestHwpx.t;
import static io.github.limjaemin99.hwpxdocx.internal.h2d.TestHwpx.tbl;
import static io.github.limjaemin99.hwpxdocx.internal.h2d.TestHwpx.tc;
import static io.github.limjaemin99.hwpxdocx.internal.h2d.TestHwpx.tr;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertContains;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertEquals;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertThrows;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertTrue;

class HwpxMalformedTest {

    /** 대한민국헌법 조문 표. {@code rowSpan}은 "제1장" 칸의 세로 합치기 값. */
    private static byte[] tableDoc(String rowSpan, String firstCellAddr) throws Exception {
        String b = "2";
        String table = tbl(3, 2, "borderFillIDRef=\"" + b + "\"",
                tr(tc(0, 0, 1, 1, 6000, b, "구분"), tc(0, 1, 1, 1, 20000, b, "내용")),
                tr(tc(1, 0, 2, 1, 6000, b, "제1장"), tc(1, 1, 1, 1, 20000, b, "대한민국은 민주공화국이다.")),
                tr(tc(2, 1, 1, 1, 20000, b, "대한민국의 주권은 국민에게 있다.")));
        table = table.replace("<hp:cellSpan colSpan=\"1\" rowSpan=\"2\"/>",
                "<hp:cellSpan colSpan=\"1\" rowSpan=\"" + rowSpan + "\"/>");
        table = table.replace("<hp:cellAddr colAddr=\"0\" rowAddr=\"0\"/>", firstCellAddr);
        return new TestHwpx()
                .borderFills(borderFill("1", "NONE", "0.1 mm", null), borderFill("2", "SOLID", "0.12 mm", "#E0E0E0"))
                .paragraphs(para("0", run("0", t("대한민국헌법"))), para("0", run("0", table)),
                        para("0", run("0", t("표 뒤 문단"))))
                .toBytes();
    }

    private static final String ADDR = "<hp:cellAddr colAddr=\"0\" rowAddr=\"0\"/>";

    private static ConvertResult convert(byte[] hwpx) throws Exception {
        return HwpxDocxConverter.hwpxToDocx(new ByteArrayInputStream(hwpx), new ByteArrayOutputStream(), null);
    }

    @Test
    void outOfIntRangeSpanFallsBackToDefault() throws Exception {
        // int로 자르면 2147483647이 되어 격자를 그만큼 잡았음
        ConvertResult r = convert(tableDoc("-2147483649", ADDR));
        assertEquals(1, r.getTableCount());
    }

    @Test
    void unsignedNegativeStillReadAsNegative() throws Exception {
        // 한컴의 부호 없는 -1 표기(4294967295)는 1보다 작아 1로 둠
        ConvertResult r = convert(tableDoc("4294967295", ADDR));
        assertEquals(1, r.getTableCount());
    }

    @Test
    void hugeCellAddressFailsWithConvertException() throws Exception {
        byte[] hwpx = tableDoc("2", "<hp:cellAddr colAddr=\"60000\" rowAddr=\"60000\"/>");
        ConvertException e = assertThrows(ConvertException.class, () -> convert(hwpx));
        assertContains(e.getMessage(), "표 크기");
    }

    @Test
    void hugeRowSpanFailsWithConvertException() throws Exception {
        byte[] hwpx = tableDoc("2147483647", ADDR);
        assertThrows(ConvertException.class, () -> convert(hwpx));
    }

    @Test
    void brokenHwpxNeverThrowsOtherErrors() throws Exception {
        int ok = Malformed.run(tableDoc("2", ADDR), true, 1L, 300);
        assertTrue(ok > 150);
    }

    @Test
    void brokenConvertedHwpxNeverThrowsOtherErrors() throws Exception {
        // 번호·각주·구역 설정이 든 HWPX는 DOCX 시험 문서를 바꿔서 얻음
        ByteArrayOutputStream hwpx = new ByteArrayOutputStream();
        HwpxDocxConverter.docxToHwpx(new ByteArrayInputStream(DocxSamples.rich()), hwpx, null);
        int ok = Malformed.run(hwpx.toByteArray(), true, 2L, 300);
        assertTrue(ok > 150);
    }
}

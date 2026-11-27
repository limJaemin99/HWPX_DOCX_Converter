package io.github.limjaemin99.hwpxdocx.internal.d2h;

import static io.github.limjaemin99.hwpxdocx.internal.d2h.TestDocx.p;
import static io.github.limjaemin99.hwpxdocx.internal.d2h.TestDocx.r;
import static io.github.limjaemin99.hwpxdocx.internal.d2h.TestDocx.t;

/** 다른 패키지 시험에서도 쓰는 DOCX 시험 문서. 문장은 대한민국헌법에서. */
public final class DocxSamples {

    private DocxSamples() {
    }

    private static String tc(String tcPr, String text) {
        return "<w:tc><w:tcPr><w:tcW w:w=\"1200\" w:type=\"dxa\"/>" + tcPr + "</w:tcPr>" + p("", r("", t(text))) + "</w:tc>";
    }

    static String table(String firstRowPr, String headSpan) {
        return "<w:tbl><w:tblPr><w:tblBorders><w:top w:val=\"single\" w:sz=\"6\" w:space=\"0\" w:color=\"000000\"/>"
                + "</w:tblBorders></w:tblPr>"
                + "<w:tblGrid><w:gridCol w:w=\"1200\"/><w:gridCol w:w=\"4000\"/><w:gridCol w:w=\"1600\"/></w:tblGrid>"
                + "<w:tr>" + firstRowPr + tc("", "구분") + tc("<w:gridSpan w:val=\"" + headSpan + "\"/>", "내용") + "</w:tr>"
                + "<w:tr>" + tc("<w:vMerge w:val=\"restart\"/>", "제1장") + tc("", "대한민국은 민주공화국이다.")
                + tc("", "") + "</w:tr>"
                + "<w:tr>" + tc("<w:vMerge/>", "") + tc("", "대한민국의 주권은 국민에게 있다.") + tc("", "") + "</w:tr>"
                + "</w:tbl>";
    }

    private static final String NUMBERING = "<w:abstractNum w:abstractNumId=\"0\">"
            + "<w:lvl w:ilvl=\"0\"><w:start w:val=\"1\"/><w:numFmt w:val=\"decimal\"/><w:lvlText w:val=\"%1.\"/>"
            + "<w:pPr><w:ind w:left=\"720\" w:hanging=\"360\"/></w:pPr></w:lvl>"
            + "<w:lvl w:ilvl=\"1\"><w:start w:val=\"1\"/><w:numFmt w:val=\"ganada\"/><w:lvlText w:val=\"%2.\"/></w:lvl>"
            + "</w:abstractNum><w:num w:numId=\"1\"><w:abstractNumId w:val=\"0\"/></w:num>";

    private static String numPr(int level) {
        return "<w:numPr><w:ilvl w:val=\"" + level + "\"/><w:numId w:val=\"1\"/></w:numPr>";
    }

    /** 표·번호·각주·구역 설정이 다 든 대한민국헌법 문서. */
    public static byte[] rich() throws Exception {
        return new TestDocx()
                .numbering(NUMBERING)
                .notes("footnotes", "<w:footnote w:id=\"1\"><w:p><w:r><w:footnoteRef/></w:r>"
                        + "<w:r><w:t>1948. 7. 17. 제정</w:t></w:r></w:p></w:footnote>")
                .bodyWithSection("<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/>"
                                + "<w:pgMar w:top=\"1440\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\" w:header=\"851\""
                                + " w:footer=\"992\" w:gutter=\"0\"/><w:cols w:num=\"2\" w:space=\"720\"/>"
                                + "<w:pgNumType w:start=\"3\"/></w:sectPr>",
                        p("<w:jc w:val=\"center\"/>", r("<w:b/><w:sz w:val=\"32\"/>", t("대한민국헌법")),
                                r("", "<w:footnoteReference w:id=\"1\"/>")),
                        p(numPr(0), r("", t("총강"))),
                        p(numPr(1), r("<w:i/>", t("대한민국은 민주공화국이다."))),
                        table("", "2"),
                        p("<w:spacing w:line=\"360\" w:lineRule=\"auto\"/><w:ind w:firstLine=\"400\"/>",
                                r("<w:rFonts w:ascii=\"Arial\" w:eastAsia=\"맑은 고딕\"/>", t("모든 국민은 인간으로서의 존엄과 가치를 가진다."))))
                .toBytes();
    }
}

package io.github.limjaemin99.hwpxdocx.internal.h2d;

import io.github.limjaemin99.hwpxdocx.testkit.TestConvert;
import io.github.limjaemin99.hwpxdocx.ConvertResult;
import io.github.limjaemin99.hwpxdocx.HwpxDocxConverter;
import io.github.limjaemin99.hwpxdocx.internal.SafeXml;
import io.github.limjaemin99.hwpxdocx.internal.docx.DocxSkeleton;
import io.github.limjaemin99.hwpxdocx.testkit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static io.github.limjaemin99.hwpxdocx.internal.h2d.TestHwpx.borderFill;
import static io.github.limjaemin99.hwpxdocx.internal.h2d.TestHwpx.para;
import static io.github.limjaemin99.hwpxdocx.internal.h2d.TestHwpx.run;
import static io.github.limjaemin99.hwpxdocx.internal.h2d.TestHwpx.t;
import static io.github.limjaemin99.hwpxdocx.internal.h2d.TestHwpx.tbl;
import static io.github.limjaemin99.hwpxdocx.internal.h2d.TestHwpx.tc;
import static io.github.limjaemin99.hwpxdocx.internal.h2d.TestHwpx.tr;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertEquals;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertTrue;

class HwpxTableTest {

    private static final String W = DocxSkeleton.NS_W;

    private ConvertResult result;

    private Element body(byte[] hwpx) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        result = TestConvert.hwpxToDocx(new ByteArrayInputStream(hwpx), out, null);
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(out.toByteArray()))) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                if ("word/document.xml".equals(e.getName())) {
                    Document d = SafeXml.parse(z, e.getName());
                    return (Element) d.getElementsByTagNameNS(W, "body").item(0);
                }
            }
        }
        throw new AssertionError("document.xml 없음");
    }

    private static List<Element> kids(Element parent, String local) {
        List<Element> list = new ArrayList<Element>();
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element && local.equals(n.getLocalName())) {
                list.add((Element) n);
            }
        }
        return list;
    }

    private static Element kid(Element parent, String local) {
        List<Element> l = kids(parent, local);
        return l.isEmpty() ? null : l.get(0);
    }

    private static String wAttr(Element e, String name) {
        return e == null ? null : e.getAttributeNS(W, name);
    }

    private static String text(Element e) {
        StringBuilder b = new StringBuilder();
        NodeList ts = e.getElementsByTagNameNS(W, "t");
        for (int i = 0; i < ts.getLength(); i++) {
            b.append(ts.item(i).getTextContent());
        }
        return b.toString();
    }

    /** 대한민국헌법 조문 표: 머리 행의 "내용"을 가로로, 장 칸과 비고 칸을 세로로 합침. */
    private static byte[] mergedTable(String tableParaAttrs) throws Exception {
        String b = "2";
        String table = tbl(5, 4, "borderFillIDRef=\"" + b + "\" repeatHeader=\"1\"",
                tr(tc(0, 0, 1, 1, 6000, b, "구분"), tc(0, 1, 1, 1, 6000, b, "조문"), tc(0, 2, 1, 2, 28000, b, "내용")),
                tr(tc(1, 0, 2, 1, 6000, b, "제1장"), tc(1, 1, 1, 1, 6000, b, "제1조"),
                        tc(1, 2, 1, 1, 20000, b, "대한민국은 민주공화국이다."), tc(1, 3, 2, 1, 8000, b, "")),
                tr(tc(2, 1, 1, 1, 6000, b, "제3조"), tc(2, 2, 1, 1, 20000, b, "대한민국의 영토는 한반도와 그 부속도서로 한다.")),
                tr(tc(3, 0, 2, 1, 6000, b, "제2장"), tc(3, 1, 1, 1, 6000, b, "제14조"),
                        tc(3, 2, 1, 1, 20000, b, "모든 국민은 거주ㆍ이전의 자유를 가진다."), tc(3, 3, 2, 1, 8000, b, "")),
                tr(tc(4, 1, 1, 1, 6000, b, "제15조"), tc(4, 2, 1, 1, 20000, b, "모든 국민은 직업선택의 자유를 가진다.")));
        return new TestHwpx()
                .borderFills(borderFill("1", "NONE", "0.1 mm", null), borderFill("2", "SOLID", "0.12 mm", "#E0E0E0"))
                .paragraphs(para("0", run("0", t("대한민국헌법"))),
                        para("0", run("0", table)).replace("pageBreak=\"0\"", tableParaAttrs),
                        para("0", run("0", t("표 뒤 문단"))))
                .toBytes();
    }

    @Test
    void mergedCellsKeepGrid() throws Exception {
        Element body = body(mergedTable("pageBreak=\"0\""));
        assertEquals(1, result.getTableCount());
        assertEquals(0, result.getSkippedCount());
        List<Element> tables = kids(body, "tbl");
        assertEquals(1, tables.size());
        Element t = tables.get(0);

        List<Element> grid = kids(kid(t, "tblGrid"), "gridCol");
        assertEquals(4, grid.size());
        assertEquals("1200", wAttr(grid.get(0), "w"));
        assertEquals("4000", wAttr(grid.get(2), "w"));
        assertEquals("1600", wAttr(grid.get(3), "w"));

        List<Element> rows = kids(t, "tr");
        assertEquals(5, rows.size());
        // 모든 행의 칸 너비 합(격자 칸 수)이 4
        for (Element r : rows) {
            int span = 0;
            for (Element c : kids(r, "tc")) {
                Element gs = kid(kid(c, "tcPr"), "gridSpan");
                span += gs == null ? 1 : Integer.parseInt(wAttr(gs, "val"));
            }
            assertEquals(4, span);
        }
        List<Element> head = kids(rows.get(0), "tc");
        assertEquals(3, head.size());
        assertEquals("2", wAttr(kid(kid(head.get(2), "tcPr"), "gridSpan"), "val"));
        assertTrue(kid(kid(rows.get(0), "trPr"), "tblHeader") != null);
        assertEquals(null, kid(kid(rows.get(1), "trPr"), "tblHeader"));

        List<Element> r1 = kids(rows.get(1), "tc");
        List<Element> r2 = kids(rows.get(2), "tc");
        assertEquals("restart", wAttr(kid(kid(r1.get(0), "tcPr"), "vMerge"), "val"));
        assertEquals("", wAttr(kid(kid(r2.get(0), "tcPr"), "vMerge"), "val"));
        assertEquals("", text(r2.get(0)));
        assertEquals("제1장", text(r1.get(0)));
        assertEquals("대한민국의 영토는 한반도와 그 부속도서로 한다.", text(r2.get(2)));
        assertEquals("", wAttr(kid(kid(r2.get(3), "tcPr"), "vMerge"), "val"));
    }

    @Test
    void bordersShadingAndMargins() throws Exception {
        Element t = kids(body(mergedTable("pageBreak=\"0\"")), "tbl").get(0);
        Element tcPr = kid(kids(kids(t, "tr").get(1), "tc").get(1), "tcPr");
        Element top = kid(kid(tcPr, "tcBorders"), "top");
        assertEquals("single", wAttr(top, "val"));
        assertEquals("3", wAttr(top, "sz"));
        assertEquals("E0E0E0", wAttr(kid(tcPr, "shd"), "fill"));
        assertEquals("center", wAttr(kid(tcPr, "vAlign"), "val"));
        Element tblPr = kid(t, "tblPr");
        assertEquals("102", wAttr(kid(kid(tblPr, "tblCellMar"), "left"), "w"));
        assertEquals("fixed", wAttr(kid(tblPr, "tblLayout"), "type"));
        assertEquals("8000", wAttr(kid(tblPr, "tblW"), "w"));
    }

    @Test
    void tableSplitsParagraph() throws Exception {
        Element body = body(mergedTable("pageBreak=\"0\""));
        List<String> order = new ArrayList<String>();
        for (Node n = body.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element && !"sectPr".equals(n.getLocalName())) {
                order.add(n.getLocalName() + ":" + text((Element) n).length());
            }
        }
        // 골격 빈 문단, 제목, 표, 표를 담았던 문단의 남은(빈) 조각, 표 뒤 문단
        assertEquals("p", order.get(0).split(":")[0]);
        assertEquals("p:6", order.get(1));
        assertEquals("tbl", order.get(2).split(":")[0]);
        assertEquals("p:0", order.get(3));
        assertEquals("p:6", order.get(4));
    }

    @Test
    void pageBreakBeforeTableGoesToEmptyParagraph() throws Exception {
        Element body = body(mergedTable("pageBreak=\"1\""));
        List<Element> all = new ArrayList<Element>();
        for (Node n = body.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element) {
                all.add((Element) n);
            }
        }
        Element beforeTable = all.get(2);
        assertEquals("p", beforeTable.getLocalName());
        assertTrue(kid(kid(beforeTable, "pPr"), "pageBreakBefore") != null);
        assertEquals("tbl", all.get(3).getLocalName());
    }

    @Test
    void nestedTableCellEndsWithParagraph() throws Exception {
        String inner = tbl(1, 1, "borderFillIDRef=\"1\"", tr(tc(0, 0, 1, 1, 10000, "1", "제3조")));
        String outerCell = tc(0, 0, 1, 1, 20000, "1", "x").replace(t("x"), inner);
        byte[] hwpx = new TestHwpx().paragraphs(
                para("0", run("0", tbl(1, 1, "borderFillIDRef=\"1\"", tr(outerCell))))).toBytes();
        Element outer = kids(body(hwpx), "tbl").get(0);
        Element cell = kids(kids(outer, "tr").get(0), "tc").get(0);
        List<String> names = new ArrayList<String>();
        for (Node n = cell.getFirstChild(); n != null; n = n.getNextSibling()) {
            names.add(n.getLocalName());
        }
        assertEquals("[tcPr, tbl, p]", names.toString());
        assertEquals(2, result.getTableCount());
    }

    @Test
    void spannedOnlyColumnsShareWidth() throws Exception {
        String b = "1";
        // 둘째 행만 열을 나눠 주는 표: 첫 행은 두 열을 합친 칸 하나
        byte[] hwpx = new TestHwpx().paragraphs(para("0", run("0", tbl(2, 2, "borderFillIDRef=\"1\"",
                tr(tc(0, 0, 1, 2, 30000, b, "제1장 총강")),
                tr(tc(1, 0, 1, 1, 10000, b, "제1조"), tc(1, 1, 1, 1, 20000, b, "대한민국은 민주공화국이다.")))))).toBytes();
        List<Element> grid = kids(kid(kids(body(hwpx), "tbl").get(0), "tblGrid"), "gridCol");
        assertEquals("2000", wAttr(grid.get(0), "w"));
        assertEquals("4000", wAttr(grid.get(1), "w"));

        byte[] onlySpan = new TestHwpx().paragraphs(para("0", run("0", tbl(1, 2, "borderFillIDRef=\"1\"",
                tr(tc(0, 0, 1, 2, 30000, b, "제1장 총강")))))).toBytes();
        List<Element> g2 = kids(kid(kids(body(onlySpan), "tbl").get(0), "tblGrid"), "gridCol");
        assertEquals("3000", wAttr(g2.get(0), "w"));
        assertEquals("3000", wAttr(g2.get(1), "w"));
    }

    @Test
    void borderSizeClamps() {
        assertEquals(3, DocxProps.borderSize("0.12 mm"));
        assertEquals(2, DocxProps.borderSize("0.01 mm"));
        assertEquals(96, DocxProps.borderSize("50 mm"));
        assertEquals(3, DocxProps.borderSize("abc"));
    }
}

package io.github.limjaemin99.hwpxdocx.internal.d2h;

import io.github.limjaemin99.hwpxdocx.ConvertResult;
import io.github.limjaemin99.hwpxdocx.HwpxDocxConverter;
import io.github.limjaemin99.hwpxdocx.internal.SafeXml;
import io.github.limjaemin99.hwpxdocx.internal.ZipReader;
import io.github.limjaemin99.hwpxdocx.internal.hwpx.HwpxHeader;
import io.github.limjaemin99.hwpxdocx.testkit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.github.limjaemin99.hwpxdocx.internal.d2h.TestDocx.p;
import static io.github.limjaemin99.hwpxdocx.internal.d2h.TestDocx.r;
import static io.github.limjaemin99.hwpxdocx.internal.d2h.TestDocx.t;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertEquals;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertTrue;

class DocxTableTest {

    private static final String HP = HwpxHeader.NS_HP;
    private static final String HH = HwpxHeader.NS_HH;
    private static final String HC = HwpxHeader.NS_HC;

    private ConvertResult result;
    private Document header;
    private Document section;

    private void convert(byte[] docx) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        result = HwpxDocxConverter.docxToHwpx(new ByteArrayInputStream(docx), out, null);
        Map<String, byte[]> m = ZipReader.unzip(new ByteArrayInputStream(out.toByteArray()));
        header = SafeXml.parse(new ByteArrayInputStream(m.get("Contents/header.xml")), "header");
        section = SafeXml.parse(new ByteArrayInputStream(m.get("Contents/section0.xml")), "section");
    }

    private static List<Element> kids(Element parent, String ns, String local) {
        List<Element> list = new ArrayList<Element>();
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element && local.equals(n.getLocalName()) && ns.equals(n.getNamespaceURI())) {
                list.add((Element) n);
            }
        }
        return list;
    }

    private static Element kid(Element parent, String ns, String local) {
        List<Element> l = kids(parent, ns, local);
        return l.isEmpty() ? null : l.get(0);
    }

    private Element borderFill(String id) {
        NodeList l = header.getElementsByTagNameNS(HH, "borderFill");
        for (int i = 0; i < l.getLength(); i++) {
            if (id.equals(((Element) l.item(i)).getAttribute("id"))) {
                return (Element) l.item(i);
            }
        }
        throw new AssertionError("borderFill " + id + " 없음");
    }

    private static String text(Element e) {
        StringBuilder b = new StringBuilder();
        NodeList ts = e.getElementsByTagNameNS(HP, "t");
        for (int i = 0; i < ts.getLength(); i++) {
            b.append(ts.item(i).getTextContent());
        }
        return b.toString();
    }

    private static String tc(String tcPr, String text) {
        return "<w:tc><w:tcPr><w:tcW w:w=\"1200\" w:type=\"dxa\"/>" + tcPr + "</w:tcPr>" + p("", r("", t(text))) + "</w:tc>";
    }

    /** 대한민국헌법 조문 표: 머리 행 "내용"을 가로로, 장 칸·비고 칸을 세로로 합침. 안쪽 가로선은 없음. */
    private static String constitutionTable() {
        String head = "<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"E0E0E0\"/>";
        String restart = "<w:vMerge w:val=\"restart\"/>";
        String cont = "<w:vMerge/>";
        return "<w:tbl><w:tblPr><w:jc w:val=\"center\"/><w:tblBorders>"
                + "<w:top w:val=\"single\" w:sz=\"6\" w:space=\"0\" w:color=\"000000\"/>"
                + "<w:left w:val=\"single\" w:sz=\"6\" w:space=\"0\" w:color=\"000000\"/>"
                + "<w:bottom w:val=\"double\" w:sz=\"6\" w:space=\"0\" w:color=\"000000\"/>"
                + "<w:right w:val=\"single\" w:sz=\"6\" w:space=\"0\" w:color=\"000000\"/>"
                + "<w:insideV w:val=\"dotted\" w:sz=\"4\" w:space=\"0\" w:color=\"0000FF\"/>"
                + "</w:tblBorders></w:tblPr>"
                + "<w:tblGrid><w:gridCol w:w=\"1200\"/><w:gridCol w:w=\"1200\"/><w:gridCol w:w=\"4000\"/><w:gridCol w:w=\"1600\"/></w:tblGrid>"
                + "<w:tr><w:trPr><w:tblHeader/></w:trPr>" + tc(head, "구분") + tc(head, "조문")
                + tc(head + "<w:gridSpan w:val=\"2\"/>", "내용") + "</w:tr>"
                + "<w:tr>" + tc(restart, "제1장") + tc("", "제1조") + tc("", "대한민국은 민주공화국이다.") + tc(restart, "") + "</w:tr>"
                + "<w:tr>" + tc(cont, "") + tc("", "제3조") + tc("", "대한민국의 영토는 한반도와 그 부속도서로 한다.") + tc(cont, "") + "</w:tr>"
                + "<w:tr><w:trPr><w:trHeight w:val=\"600\"/></w:trPr>" + tc(restart, "제2장") + tc("", "제14조")
                + tc("", "모든 국민은 거주ㆍ이전의 자유를 가진다.") + tc(restart, "") + "</w:tr>"
                + "<w:tr>" + tc(cont, "") + tc("", "제15조") + tc("", "모든 국민은 직업선택의 자유를 가진다.") + tc(cont, "") + "</w:tr>"
                + "</w:tbl>";
    }

    private Element table() {
        return (Element) section.getElementsByTagNameNS(HP, "tbl").item(0);
    }

    private Element cell(int row, int col) {
        for (Element tr : kids(table(), HP, "tr")) {
            for (Element tc : kids(tr, HP, "tc")) {
                Element a = kid(tc, HP, "cellAddr");
                if (a.getAttribute("rowAddr").equals(String.valueOf(row)) && a.getAttribute("colAddr").equals(String.valueOf(col))) {
                    return tc;
                }
            }
        }
        throw new AssertionError("칸 (" + row + "," + col + ") 없음");
    }

    @Test
    void mergedCells() throws Exception {
        convert(new TestDocx().body(constitutionTable(), p("", r("", t("표 뒤 문단")))).toBytes());
        assertEquals(1, result.getTableCount());
        assertEquals(0, result.getSkippedCount());
        Element tbl = table();
        assertEquals("5", tbl.getAttribute("rowCnt"));
        assertEquals("4", tbl.getAttribute("colCnt"));
        assertEquals("1", tbl.getAttribute("repeatHeader"));
        assertEquals(15, tbl.getElementsByTagNameNS(HP, "tc").getLength());
        assertEquals("2", kid(cell(0, 2), HP, "cellSpan").getAttribute("colSpan"));
        assertEquals("2", kid(cell(1, 0), HP, "cellSpan").getAttribute("rowSpan"));
        assertEquals("2", kid(cell(3, 3), HP, "cellSpan").getAttribute("rowSpan"));
        assertEquals("제1장", text(cell(1, 0)));
        assertEquals("대한민국의 영토는 한반도와 그 부속도서로 한다.", text(cell(2, 2)));
        assertEquals("1", cell(0, 0).getAttribute("header"));
        assertEquals("0", cell(1, 1).getAttribute("header"));
        // 너비: twip × 5, 합친 칸은 열 너비 합
        assertEquals("6000", kid(cell(0, 0), HP, "cellSz").getAttribute("width"));
        assertEquals("28000", kid(cell(0, 2), HP, "cellSz").getAttribute("width"));
        assertEquals("40000", kid(tbl, HP, "sz").getAttribute("width"));
        // 높이가 정해진 행은 twip × 5, 아니면 최소값. 세로로 합친 칸은 행 높이 합
        assertEquals("3000", kid(cell(3, 1), HP, "cellSz").getAttribute("height"));
        assertEquals("4000", kid(cell(3, 0), HP, "cellSz").getAttribute("height"));
    }

    @Test
    void bordersByEdgeAndFill() throws Exception {
        convert(new TestDocx().body(constitutionTable()).toBytes());
        Element head = borderFill(cell(0, 0).getAttribute("borderFillIDRef"));
        assertEquals("SOLID", kid(head, HH, "topBorder").getAttribute("type"));
        assertEquals("0.25 mm", kid(head, HH, "topBorder").getAttribute("width"));
        assertEquals("SOLID", kid(head, HH, "leftBorder").getAttribute("type"));
        assertEquals("DOT", kid(head, HH, "rightBorder").getAttribute("type"));
        assertEquals("#0000FF", kid(head, HH, "rightBorder").getAttribute("color"));
        // 안쪽 가로선(insideH)은 정하지 않아 없음
        assertEquals("NONE", kid(head, HH, "bottomBorder").getAttribute("type"));
        assertEquals("#E0E0E0", kid(kid(head, HC, "fillBrush"), HC, "winBrush").getAttribute("faceColor"));
        Element last = borderFill(cell(4, 1).getAttribute("borderFillIDRef"));
        assertEquals("DOUBLE_SLIM", kid(last, HH, "bottomBorder").getAttribute("type"));
        assertEquals(null, kid(last, HC, "fillBrush"));
        // 같은 테두리·배경은 한 번만
        assertEquals(cell(1, 1).getAttribute("borderFillIDRef"), cell(2, 1).getAttribute("borderFillIDRef"));
    }

    @Test
    void tableParagraphFollowsTableAlignment() throws Exception {
        convert(new TestDocx().body(constitutionTable()).toBytes());
        Element p = (Element) table().getParentNode().getParentNode();
        assertEquals("p", p.getLocalName());
        String id = p.getAttribute("paraPrIDRef");
        NodeList prs = header.getElementsByTagNameNS(HH, "paraPr");
        for (int i = 0; i < prs.getLength(); i++) {
            Element pr = (Element) prs.item(i);
            if (id.equals(pr.getAttribute("id"))) {
                assertEquals("CENTER", kid(pr, HH, "align").getAttribute("horizontal"));
            }
        }
        // 문서가 표로 시작하면 구역 설정은 표를 담은 문단에 들어감
        assertEquals(1, p.getElementsByTagNameNS(HP, "secPr").getLength());
    }

    @Test
    void nestedTableAndCellMargins() throws Exception {
        String inner = "<w:tbl><w:tblGrid><w:gridCol w:w=\"1000\"/></w:tblGrid><w:tr>"
                + "<w:tc>" + p("", r("", t("제3조"))) + "</w:tc></w:tr></w:tbl>";
        String outer = "<w:tbl><w:tblPr><w:tblCellMar><w:left w:w=\"200\" w:type=\"dxa\"/></w:tblCellMar></w:tblPr>"
                + "<w:tblGrid><w:gridCol w:w=\"3000\"/></w:tblGrid><w:tr><w:tc><w:tcPr>"
                + "<w:tcMar><w:top w:w=\"100\" w:type=\"dxa\"/></w:tcMar><w:vAlign w:val=\"center\"/></w:tcPr>"
                + inner + p("") + "</w:tc></w:tr></w:tbl>";
        convert(new TestDocx().body(outer).toBytes());
        assertEquals(2, result.getTableCount());
        Element outerTbl = table();
        assertEquals("1000", kid(outerTbl, HP, "inMargin").getAttribute("left"));
        assertEquals("540", kid(outerTbl, HP, "inMargin").getAttribute("right"));
        Element c = cell(0, 0);
        assertEquals("1", c.getAttribute("hasMargin"));
        assertEquals("500", kid(c, HP, "cellMargin").getAttribute("top"));
        assertEquals("1000", kid(c, HP, "cellMargin").getAttribute("left"));
        assertEquals("CENTER", kid(c, HP, "subList").getAttribute("vertAlign"));
        assertEquals(1, kid(c, HP, "subList").getElementsByTagNameNS(HP, "tbl").getLength());
        assertTrue(text(c).contains("제3조"));
    }
}

package io.github.limjaemin99.hwpxdocx.internal.h2d;

import io.github.limjaemin99.hwpxdocx.ConvertOptions;
import io.github.limjaemin99.hwpxdocx.internal.ConvertStats;
import io.github.limjaemin99.hwpxdocx.internal.Converter;
import io.github.limjaemin99.hwpxdocx.internal.docx.DocxSkeleton;
import io.github.limjaemin99.hwpxdocx.internal.hwpx.HwpxHeader;
import io.github.limjaemin99.hwpxdocx.internal.hwpx.HwpxPackage;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.github.limjaemin99.hwpxdocx.internal.hwpx.HwpxHeader.NS_HP;
import static io.github.limjaemin99.hwpxdocx.internal.hwpx.HwpxHeader.child;
import static io.github.limjaemin99.hwpxdocx.internal.hwpx.HwpxHeader.children;

/** HWPX → DOCX. 지금은 본문 문단(글자·문단 모양)과 표를 옮긴다. 변환 한 번마다 새로 만든다. */
public final class HwpxToDocx implements Converter {

    @Override
    public void convert(InputStream in, OutputStream out, ConvertOptions options, ConvertStats stats)
            throws IOException {
        HwpxPackage pkg = HwpxPackage.read(in);
        Run run = new Run(new HwpxHeader(pkg.parse("Contents/header.xml")), stats);
        StringBuilder body = new StringBuilder();
        for (String section : pkg.sections()) {
            Element root = pkg.parse(section).getDocumentElement();
            for (Element p : children(root, NS_HP, "p")) {
                run.paragraph(p, body);
            }
        }
        if (body.length() == 0) {
            body.append("<w:p/>");
        }
        Map<String, byte[]> entries = DocxSkeleton.entries();
        entries.put("word/document.xml", DocxSkeleton.documentXml(body.toString()).getBytes(StandardCharsets.UTF_8));
        DocxSkeleton.write(entries, out);
    }

    /** 변환 한 번 동안의 상태. 같은 모양 ID의 속성 XML은 한 번만 만든다. */
    private static final class Run {

        private final HwpxHeader header;
        private final ConvertStats stats;
        private final Map<String, String> runProps = new HashMap<String, String>();
        private final Map<String, String> paraProps = new HashMap<String, String>();

        Run(HwpxHeader header, ConvertStats stats) {
            this.header = header;
            this.stats = stats;
        }

        /**
         * 문단 하나. Word는 문단 안에 표를 둘 수 없어서, 표가 나오면 문단을 끊고 표를 문단 사이에 둔다.
         * 마지막 조각은 비어 있어도 남긴다 (표 칸은 문단으로 끝나야 하고, 표가 이어 붙지 않게 함).
         */
        void paragraph(Element p, StringBuilder out) throws IOException {
            stats.paragraph();
            int maxHeight = 0;
            boolean hasObject = false;
            String lastCharPr = null;
            for (Element r : children(p, NS_HP, "run")) {
                String id = r.getAttribute("charPrIDRef");
                maxHeight = Math.max(maxHeight, header.charHeight(id));
                lastCharPr = id;
                hasObject |= child(r, NS_HP, "tbl") != null || child(r, NS_HP, "pic") != null;
            }
            if (maxHeight == 0) {
                maxHeight = 1000;
            }
            String paraPrId = p.getAttribute("paraPrIDRef");
            StringBuilder pPr = new StringBuilder(paraProps(paraPrId, maxHeight, hasObject));
            if (lastCharPr != null) {
                // 문단 기호의 글자 모양: 빈 문단의 줄 높이가 여기서 정해짐
                pPr.append("<w:rPr>").append(runProps(lastCharPr)).append("</w:rPr>");
            }
            Pieces pieces = new Pieces(out, pPr.toString(), "1".equals(p.getAttribute("pageBreak")),
                    DocxProps.jc(header, paraPrId));
            for (Node n = p.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (!(n instanceof Element)) {
                    continue;
                }
                Element e = (Element) n;
                if (isHp(e, "run")) {
                    run(e, pieces);
                } else if (!isHp(e, "linesegarray")) {
                    // linesegarray는 한컴이 계산해 둔 줄 배치라 Word에서는 다시 계산됨
                    skip(e);
                }
            }
            pieces.flush(true);
        }

        /** 한 문단에서 나온 w:p 조각들. 표가 끼면 그 앞까지를 문단 하나로 내보낸다. */
        private static final class Pieces {
            final StringBuilder out;
            final String pPr;
            final String jc;
            boolean pageBreak;
            final StringBuilder cur = new StringBuilder();

            Pieces(StringBuilder out, String pPr, boolean pageBreak, String jc) {
                this.out = out;
                this.pPr = pPr;
                this.pageBreak = pageBreak;
                this.jc = jc;
            }

            void flush(boolean last) {
                if (cur.length() == 0 && !last) {
                    return;
                }
                out.append("<w:p><w:pPr>");
                if (pageBreak) {
                    out.append("<w:pageBreakBefore/>");
                    pageBreak = false;
                }
                out.append(pPr).append("</w:pPr>").append(cur).append("</w:p>");
                cur.setLength(0);
            }
        }

        private void run(Element r, Pieces pieces) throws IOException {
            String rPr = "<w:rPr>" + runProps(r.getAttribute("charPrIDRef")) + "</w:rPr>";
            for (Node n = r.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (!(n instanceof Element)) {
                    continue;
                }
                Element e = (Element) n;
                if (isHp(e, "t")) {
                    text(e, rPr, pieces.cur);
                } else if (isHp(e, "tbl")) {
                    if (pieces.pageBreak && pieces.cur.length() == 0) {
                        // 표에는 쪽 나눔을 걸 수 없어 쪽 나눔만 담은 빈 문단을 표 앞에 둠
                        pieces.flush(true);
                    } else {
                        pieces.flush(false);
                    }
                    table(e, pieces.jc, pieces.out);
                } else if (isHp(e, "secPr")) {
                    // 구역 설정은 쪽 설정 변환에서 다룸
                    continue;
                } else if (isHp(e, "ctrl")) {
                    for (Element c : childElements(e)) {
                        if (!isHp(c, "colPr")) {
                            skip(c);
                        }
                    }
                } else {
                    skip(e);
                }
            }
        }

        private void text(Element t, String rPr, StringBuilder out) throws IOException {
            StringBuilder pending = new StringBuilder();
            for (Node n = t.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (n.getNodeType() == Node.TEXT_NODE || n.getNodeType() == Node.CDATA_SECTION_NODE) {
                    pending.append(n.getNodeValue());
                    continue;
                }
                if (!(n instanceof Element)) {
                    continue;
                }
                Element e = (Element) n;
                String name = e.getLocalName();
                if (!NS_HP.equals(e.getNamespaceURI())) {
                    skip(e);
                } else if ("nbSpace".equals(name)) {
                    pending.append(' ');
                } else if ("fwSpace".equals(name)) {
                    pending.append('　');
                } else if ("hyphen".equals(name)) {
                    pending.append('-');
                } else if ("tab".equals(name)) {
                    flush(pending, rPr, out);
                    out.append("<w:r>").append(rPr).append("<w:tab/></w:r>");
                } else if ("lineBreak".equals(name)) {
                    flush(pending, rPr, out);
                    out.append("<w:r>").append(rPr).append("<w:br/></w:r>");
                } else if ("markpenBegin".equals(name) || "markpenEnd".equals(name)) {
                    stats.warn("형광펜은 아직 옮기지 않음");
                } else {
                    skip(e);
                }
            }
            flush(pending, rPr, out);
        }

        private static void flush(StringBuilder pending, String rPr, StringBuilder out) {
            if (pending.length() == 0) {
                return;
            }
            out.append("<w:r>").append(rPr).append("<w:t xml:space=\"preserve\">")
                    .append(Xml.text(pending.toString())).append("</w:t></w:r>");
            pending.setLength(0);
        }

        // ---------- 표 ----------

        /** 표 칸 하나. HWPX는 다른 칸에 합쳐져 가려진 칸을 적지 않는다. */
        private static final class Cell {
            final Element tc;
            final int row;
            final int col;
            final int rowSpan;
            final int colSpan;
            final int width;
            final int height;

            Cell(Element tc) {
                this.tc = tc;
                Element addr = child(tc, NS_HP, "cellAddr");
                Element span = child(tc, NS_HP, "cellSpan");
                Element sz = child(tc, NS_HP, "cellSz");
                row = HwpxHeader.intAttr(addr, "rowAddr", 0);
                col = HwpxHeader.intAttr(addr, "colAddr", 0);
                rowSpan = Math.max(1, HwpxHeader.intAttr(span, "rowSpan", 1));
                colSpan = Math.max(1, HwpxHeader.intAttr(span, "colSpan", 1));
                width = HwpxHeader.intAttr(sz, "width", 0);
                height = HwpxHeader.intAttr(sz, "height", 0);
            }
        }

        private void table(Element tbl, String paraJc, StringBuilder out) throws IOException {
            stats.table();
            List<Cell> cells = new ArrayList<Cell>();
            int rows = HwpxHeader.intAttr(tbl, "rowCnt", 0);
            int cols = HwpxHeader.intAttr(tbl, "colCnt", 0);
            for (Element tr : children(tbl, NS_HP, "tr")) {
                for (Element tc : children(tr, NS_HP, "tc")) {
                    Cell c = new Cell(tc);
                    if (c.row < 0 || c.col < 0) {
                        stats.warn("표 칸 주소가 음수라 뺌");
                        continue;
                    }
                    cells.add(c);
                    rows = Math.max(rows, c.row + c.rowSpan);
                    cols = Math.max(cols, c.col + c.colSpan);
                }
            }
            if (rows == 0 || cols == 0) {
                stats.warn("칸이 없는 표를 뺌");
                return;
            }
            Cell[][] at = new Cell[rows][cols];
            for (Cell c : cells) {
                if (at[c.row][c.col] != null) {
                    stats.warn("같은 위치에 칸이 둘 있는 표: 뒤의 칸을 뺌");
                    continue;
                }
                at[c.row][c.col] = c;
            }
            int[] widths = columnWidths(cells, cols);

            int total = 0;
            for (int w : widths) {
                total += w;
            }
            out.append("<w:tbl><w:tblPr><w:tblW w:w=\"").append(DocxProps.twip(total)).append("\" w:type=\"dxa\"/>");
            out.append("<w:jc w:val=\"").append(tableJc(tbl, paraJc)).append("\"/>");
            out.append(DocxProps.borders(header.borderFill(tbl.getAttribute("borderFillIDRef")), "tblBorders"));
            out.append("<w:tblLayout w:type=\"fixed\"/>");
            Element in = child(tbl, NS_HP, "inMargin");
            if (in != null) {
                out.append("<w:tblCellMar>").append(margins(in)).append("</w:tblCellMar>");
            }
            out.append("<w:tblLook w:val=\"0000\"/></w:tblPr><w:tblGrid>");
            for (int w : widths) {
                out.append("<w:gridCol w:w=\"").append(DocxProps.twip(w)).append("\"/>");
            }
            out.append("</w:tblGrid>");

            // 세로로 합쳐진 칸이 덮는 자리: 아래 행에 vMerge 이어짐 칸을 넣어야 Word 격자가 맞음
            Cell[][] covering = new Cell[rows][cols];
            for (Cell c : cells) {
                for (int r = c.row + 1; r < Math.min(rows, c.row + c.rowSpan); r++) {
                    covering[r][c.col] = c;
                }
            }
            boolean repeatHeader = "1".equals(tbl.getAttribute("repeatHeader"));
            for (int r = 0; r < rows; r++) {
                int height = 0;
                boolean headerRow = repeatHeader;
                boolean any = false;
                for (int c = 0; c < cols; c++) {
                    Cell cell = at[r][c];
                    if (cell != null) {
                        any = true;
                        headerRow &= "1".equals(cell.tc.getAttribute("header"));
                        if (cell.rowSpan == 1) {
                            height = Math.max(height, cell.height);
                        }
                    }
                }
                out.append("<w:tr><w:trPr>");
                if (headerRow && any) {
                    out.append("<w:tblHeader/>");
                }
                if (height > 0) {
                    out.append("<w:trHeight w:val=\"").append(DocxProps.twip(height)).append("\" w:hRule=\"atLeast\"/>");
                }
                out.append("</w:trPr>");
                int c = 0;
                while (c < cols) {
                    Cell cell = at[r][c];
                    if (cell != null) {
                        cell(cell, cell.rowSpan > 1, false, span(widths, c, cell.colSpan), out);
                        c += cell.colSpan;
                    } else if (covering[r][c] != null) {
                        Cell top = covering[r][c];
                        cell(top, false, true, span(widths, c, top.colSpan), out);
                        c += top.colSpan;
                    } else {
                        // 원본에 빠진 칸: 격자가 어긋나지 않게 빈 칸을 채움
                        out.append("<w:tc><w:tcPr><w:tcW w:w=\"").append(DocxProps.twip(widths[c]))
                                .append("\" w:type=\"dxa\"/></w:tcPr><w:p/></w:tc>");
                        c++;
                    }
                }
                out.append("</w:tr>");
            }
            out.append("</w:tbl>");
        }

        private void cell(Cell cell, boolean vStart, boolean vContinue, int width, StringBuilder out)
                throws IOException {
            Element tc = cell.tc;
            Element bf = header.borderFill(tc.getAttribute("borderFillIDRef"));
            Element sub = child(tc, NS_HP, "subList");
            out.append("<w:tc><w:tcPr><w:tcW w:w=\"").append(DocxProps.twip(width)).append("\" w:type=\"dxa\"/>");
            if (cell.colSpan > 1) {
                out.append("<w:gridSpan w:val=\"").append(cell.colSpan).append("\"/>");
            }
            if (vStart) {
                out.append("<w:vMerge w:val=\"restart\"/>");
            } else if (vContinue) {
                out.append("<w:vMerge/>");
            }
            out.append(DocxProps.borders(bf, "tcBorders")).append(DocxProps.shading(bf));
            Element cm = child(tc, NS_HP, "cellMargin");
            if ("1".equals(tc.getAttribute("hasMargin")) && cm != null) {
                out.append("<w:tcMar>").append(margins(cm)).append("</w:tcMar>");
            }
            String va = sub == null ? "" : sub.getAttribute("vertAlign");
            out.append("<w:vAlign w:val=\"")
                    .append("CENTER".equals(va) ? "center" : "BOTTOM".equals(va) ? "bottom" : "top")
                    .append("\"/></w:tcPr>");
            if (vContinue || sub == null) {
                out.append("<w:p/>");
            } else {
                int before = out.length();
                for (Element p : children(sub, NS_HP, "p")) {
                    paragraph(p, out);
                }
                if (out.length() == before) {
                    out.append("<w:p/>");
                }
            }
            out.append("</w:tc>");
        }

        private static String margins(Element m) {
            return "<w:top w:w=\"" + DocxProps.twip(HwpxHeader.intAttr(m, "top", 0)) + "\" w:type=\"dxa\"/>"
                    + "<w:left w:w=\"" + DocxProps.twip(HwpxHeader.intAttr(m, "left", 0)) + "\" w:type=\"dxa\"/>"
                    + "<w:bottom w:w=\"" + DocxProps.twip(HwpxHeader.intAttr(m, "bottom", 0)) + "\" w:type=\"dxa\"/>"
                    + "<w:right w:w=\"" + DocxProps.twip(HwpxHeader.intAttr(m, "right", 0)) + "\" w:type=\"dxa\"/>";
        }

        private static String tableJc(Element tbl, String paraJc) {
            Element pos = child(tbl, NS_HP, "pos");
            if (pos == null || "1".equals(pos.getAttribute("treatAsChar"))) {
                // 글자처럼 취급하는 표는 문단 정렬을 따름
                return "center".equals(paraJc) || "right".equals(paraJc) ? paraJc : "left";
            }
            String h = pos.getAttribute("horzAlign");
            return "CENTER".equals(h) ? "center" : "RIGHT".equals(h) ? "right" : "left";
        }

        private static int span(int[] widths, int from, int n) {
            int s = 0;
            for (int i = from; i < Math.min(widths.length, from + n); i++) {
                s += widths[i];
            }
            return s;
        }

        /** 열 너비(HWPUNIT). 한 칸짜리 칸에서 먼저 정하고, 합친 칸은 남은 너비를 모르는 열에 나눠 줌. */
        static int[] columnWidths(List<Cell> cells, int cols) {
            int[] w = new int[cols];
            for (Cell c : cells) {
                if (c.colSpan == 1 && c.col < cols && w[c.col] == 0 && c.width > 0) {
                    w[c.col] = c.width;
                }
            }
            boolean changed = true;
            while (changed) {
                changed = false;
                for (Cell c : cells) {
                    if (c.colSpan <= 1 || c.width <= 0) {
                        continue;
                    }
                    int known = 0;
                    int unknown = 0;
                    for (int i = c.col; i < Math.min(cols, c.col + c.colSpan); i++) {
                        if (w[i] == 0) {
                            unknown++;
                        } else {
                            known += w[i];
                        }
                    }
                    if (unknown > 0) {
                        int each = Math.max(1, (c.width - known) / unknown);
                        for (int i = c.col; i < Math.min(cols, c.col + c.colSpan); i++) {
                            if (w[i] == 0) {
                                w[i] = each;
                            }
                        }
                        changed = true;
                    }
                }
            }
            for (int i = 0; i < cols; i++) {
                if (w[i] == 0) {
                    w[i] = 5000;
                }
            }
            return w;
        }

        // ---------- 공통 ----------

        private String runProps(String id) {
            String v = runProps.get(id);
            if (v == null) {
                v = DocxProps.runProps(header, id, stats);
                runProps.put(id, v);
            }
            return v;
        }

        private String paraProps(String id, int maxHeight, boolean hasObject) {
            String key = id + "/" + maxHeight + "/" + hasObject;
            String v = paraProps.get(key);
            if (v == null) {
                v = DocxProps.paraProps(header, id, maxHeight, hasObject, stats);
                paraProps.put(key, v);
            }
            return v;
        }

        private void skip(Element e) throws IOException {
            String prefix = e.getPrefix();
            stats.skip((prefix == null ? "" : prefix + ":") + e.getLocalName());
        }

        private static boolean isHp(Element e, String local) {
            return local.equals(e.getLocalName()) && NS_HP.equals(e.getNamespaceURI());
        }

        private static List<Element> childElements(Element parent) {
            List<Element> list = new ArrayList<Element>();
            for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (n instanceof Element) {
                    list.add((Element) n);
                }
            }
            return list;
        }
    }
}

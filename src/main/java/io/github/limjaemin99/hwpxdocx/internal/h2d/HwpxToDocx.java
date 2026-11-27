package io.github.limjaemin99.hwpxdocx.internal.h2d;

import io.github.limjaemin99.hwpxdocx.internal.Xml;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.github.limjaemin99.hwpxdocx.internal.hwpx.HwpxHeader.NS_HP;
import static io.github.limjaemin99.hwpxdocx.internal.hwpx.HwpxHeader.child;
import static io.github.limjaemin99.hwpxdocx.internal.hwpx.HwpxHeader.children;

/** HWPX → DOCX. 지금은 본문 문단(글자·문단 모양), 표, 그림, 쪽 설정, 머리말·꼬리말을 옮긴다. 변환 한 번마다 새로 만든다. */
public final class HwpxToDocx implements Converter {

    @Override
    public void convert(InputStream in, OutputStream out, ConvertOptions options, ConvertStats stats)
            throws IOException {
        HwpxPackage pkg = HwpxPackage.read(in);
        DocxParts parts = new DocxParts();
        Run run = new Run(pkg, new HwpxHeader(pkg.parse("Contents/header.xml")), parts, stats);
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
        if (pkg.sections().size() > 1 || run.secPrCount > 1) {
            stats.warn("구역이 여러 개인 문서는 첫 구역의 쪽 설정·머리말을 씀");
        }
        DocxSkeleton.write(parts.entries(DocxSkeleton.documentXml(body.toString(), run.sectPr())), out);
    }

    /** 변환 한 번 동안의 상태. 같은 모양 ID의 속성 XML은 한 번만 만든다. */
    private static final class Run {

        private final HwpxPackage pkg;
        private final HwpxHeader header;
        private final DocxParts parts;
        private final ConvertStats stats;
        private final Map<String, String> runProps = new HashMap<String, String>();
        private final Map<String, String> paraProps = new HashMap<String, String>();
        private int drawingId = 1;

        // 쪽 설정·머리말·꼬리말: 지금은 문서 전체에 첫 구역 것을 씀
        private Element secPr;
        private Element colPr;
        int secPrCount;
        private Element pageNum;
        private final Map<String, String> headers = new LinkedHashMap<String, String>();
        private final Map<String, String> footers = new LinkedHashMap<String, String>();
        // 머리말·꼬리말 파트 안 관계(그림): 종류(default·even) → {Id, 관계 이름, Target}
        private final Map<String, List<String[]>> headerRels = new HashMap<String, List<String[]>>();
        private final Map<String, List<String[]>> footerRels = new HashMap<String, List<String[]>>();
        private List<String[]> partRels;
        private boolean oddOnlyHeader;
        private boolean oddOnlyFooter;
        private int headerFooterDepth;

        Run(HwpxPackage pkg, HwpxHeader header, DocxParts parts, ConvertStats stats) {
            this.pkg = pkg;
            this.header = header;
            this.parts = parts;
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
                } else if (isHp(e, "pic")) {
                    picture(e, rPr, pieces.cur);
                } else if (isHp(e, "secPr")) {
                    secPrCount++;
                    if (secPr == null) {
                        secPr = e;
                    }
                } else if (isHp(e, "ctrl")) {
                    for (Element c : childElements(e)) {
                        control(c, rPr, pieces.cur);
                    }
                } else {
                    skip(e);
                }
            }
        }

        private void control(Element c, String rPr, StringBuilder out) throws IOException {
            if (isHp(c, "colPr")) {
                if (colPr == null) {
                    colPr = c;
                }
            } else if (isHp(c, "header") || isHp(c, "footer")) {
                headerFooter(c);
            } else if (isHp(c, "pageNum")) {
                if (pageNum == null) {
                    pageNum = c;
                }
            } else if (isHp(c, "autoNum") && "PAGE".equals(c.getAttribute("numType"))) {
                out.append(field("PAGE", rPr));
            } else if (isHp(c, "autoNum") && "TOTAL_PAGE".equals(c.getAttribute("numType"))) {
                out.append(field("NUMPAGES", rPr));
            } else {
                skip(c);
            }
        }

        private static String field(String instr, String rPr) {
            return "<w:fldSimple w:instr=\" " + instr + " \"><w:r>" + rPr + "<w:t>1</w:t></w:r></w:fldSimple>";
        }

        /** 한컴은 머리말을 쪽마다 바꿀 수 있지만 Word 구역에는 종류별로 하나라서 처음 것만 씀. */
        private void headerFooter(Element c) throws IOException {
            boolean header = isHp(c, "header");
            String type = c.getAttribute("applyPageType");
            String kind = "EVEN".equals(type) ? "even" : "default";
            Map<String, String> target = header ? headers : footers;
            if (target.containsKey(kind)) {
                stats.warn("중간에 바뀌는 머리말·꼬리말은 처음 것만 옮김");
                return;
            }
            if ("ODD".equals(type)) {
                if (header) {
                    oddOnlyHeader = true;
                } else {
                    oddOnlyFooter = true;
                }
            }
            StringBuilder b = new StringBuilder();
            Element sub = child(c, NS_HP, "subList");
            List<String[]> rels = new ArrayList<String[]>();
            List<String[]> savedRels = partRels;
            partRels = rels;
            headerFooterDepth++;
            try {
                if (sub != null) {
                    for (Element p : children(sub, NS_HP, "p")) {
                        paragraph(p, b);
                    }
                }
            } finally {
                headerFooterDepth--;
                partRels = savedRels;
            }
            target.put(kind, b.toString());
            (header ? headerRels : footerRels).put(kind, rels);
        }

        /** 마지막 w:sectPr. 머리말·꼬리말 파트도 여기서 만든다. */
        String sectPr() {
            Element vis = child(secPr, NS_HP, "visibility");
            boolean hideFirstHeader = "1".equals(vis == null ? "" : vis.getAttribute("hideFirstHeader"));
            boolean hideFirstFooter = "1".equals(vis == null ? "" : vis.getAttribute("hideFirstFooter"));
            boolean hideFirstPageNum = "1".equals(vis == null ? "" : vis.getAttribute("hideFirstPageNum"));

            Map<String, String> hdr = new LinkedHashMap<String, String>(headers);
            Map<String, String> ftr = new LinkedHashMap<String, String>(footers);
            String firstHdr = hideFirstHeader ? "" : hdr.get("default");
            String firstFtr = hideFirstFooter ? "" : ftr.get("default");
            String num = pageNumParagraph();
            if (num != null) {
                Map<String, String> target = pageNum.getAttribute("pos").startsWith("TOP") ? hdr : ftr;
                String old = target.get("default");
                target.put("default", (old == null ? "" : old) + num);
                if (target.containsKey("even")) {
                    target.put("even", target.get("even") + num);
                }
                if (!hideFirstPageNum) {
                    if (target == hdr && !hideFirstHeader) {
                        firstHdr = target.get("default");
                    } else if (target == ftr && !hideFirstFooter) {
                        firstFtr = target.get("default");
                    }
                }
            }
            boolean titlePg = hideFirstHeader && hdr.containsKey("default")
                    || hideFirstFooter && ftr.containsKey("default")
                    || hideFirstPageNum && num != null;
            // 홀수 쪽에만 있는 머리말은 짝수 쪽을 비워 둬야 Word에서도 홀수 쪽에만 나옴
            if (oddOnlyHeader && !hdr.containsKey("even")) {
                hdr.put("even", "");
            }
            if (oddOnlyFooter && !ftr.containsKey("even")) {
                ftr.put("even", "");
            }
            if (hdr.containsKey("even") || ftr.containsKey("even")) {
                parts.evenAndOddHeaders();
            }

            // 첫 쪽 파트는 기본 파트 내용을 쓰거나 비어 있으므로 기본 파트의 관계를 같이 씀 (안 쓰는 관계는 무해)
            StringBuilder b = new StringBuilder("<w:sectPr>");
            for (Map.Entry<String, String> e : hdr.entrySet()) {
                b.append(reference("header", e.getKey(), parts.headerFooter("hdr", e.getValue(), relsOf(headerRels, e.getKey()))));
            }
            if (titlePg && hdr.containsKey("default")) {
                b.append(reference("header", "first",
                        parts.headerFooter("hdr", firstHdr == null ? "" : firstHdr, relsOf(headerRels, "default"))));
            }
            for (Map.Entry<String, String> e : ftr.entrySet()) {
                b.append(reference("footer", e.getKey(), parts.headerFooter("ftr", e.getValue(), relsOf(footerRels, e.getKey()))));
            }
            if (titlePg && ftr.containsKey("default")) {
                b.append(reference("footer", "first",
                        parts.headerFooter("ftr", firstFtr == null ? "" : firstFtr, relsOf(footerRels, "default"))));
            }
            b.append(DocxProps.pageSetup(secPr, colPr));
            if (titlePg) {
                b.append("<w:titlePg/>");
            }
            return b.append("<w:docGrid w:linePitch=\"360\"/></w:sectPr>").toString();
        }

        private static List<String[]> relsOf(Map<String, List<String[]>> m, String kind) {
            List<String[]> l = m.get(kind);
            return l == null ? new ArrayList<String[]>() : l;
        }

        private static String reference(String what, String type, String rid) {
            return "<w:" + what + "Reference w:type=\"" + type + "\" r:id=\"" + rid + "\"/>";
        }

        /** 한컴 쪽 번호(머리말과 따로 그려짐) → 머리말·꼬리말 끝에 붙일 문단. 없으면 null. */
        private String pageNumParagraph() {
            if (pageNum == null || "NONE".equals(pageNum.getAttribute("pos"))) {
                return null;
            }
            String pos = pageNum.getAttribute("pos");
            String jc = pos.contains("LEFT") ? "left" : pos.contains("RIGHT") ? "right" : "center";
            if (pos.startsWith("INSIDE") || pos.startsWith("OUTSIDE")) {
                stats.warn("쪽 번호 위치 " + pos + "는 가운데로 바꿈");
            }
            String format;
            switch (pageNum.getAttribute("formatType")) {
                case "ROMAN_SMALL":
                    format = " \\* roman";
                    break;
                case "ROMAN_CAPITAL":
                    format = " \\* ROMAN";
                    break;
                case "LATIN_SMALL":
                    format = " \\* alphabetic";
                    break;
                case "LATIN_CAPITAL":
                    format = " \\* ALPHABETIC";
                    break;
                case "DIGIT":
                case "":
                    format = "";
                    break;
                default:
                    stats.warn("쪽 번호 모양 " + pageNum.getAttribute("formatType") + "는 숫자로 바꿈");
                    format = "";
            }
            String side = pageNum.getAttribute("sideChar");
            StringBuilder b = new StringBuilder("<w:p><w:pPr><w:jc w:val=\"").append(jc).append("\"/></w:pPr>");
            if (!side.isEmpty()) {
                b.append("<w:r><w:t xml:space=\"preserve\">").append(Xml.text(side)).append(" </w:t></w:r>");
            }
            b.append(field("PAGE" + format, ""));
            if (!side.isEmpty()) {
                b.append("<w:r><w:t xml:space=\"preserve\"> ").append(Xml.text(side)).append("</w:t></w:r>");
            }
            return b.append("</w:p>").toString();
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

        // ---------- 그림 ----------

        private static final long EMU_PER_HWPUNIT = 127; // 914400 EMU/인치 ÷ 7200 HWPUNIT/인치

        private void picture(Element pic, String rPr, StringBuilder out) throws IOException {

            Element img = child(pic, HwpxHeader.NS_HC, "img");
            String ref = img == null ? "" : img.getAttribute("binaryItemIDRef");
            String[] item = pkg.item(ref);
            byte[] data = item == null ? null : pkg.get(item[0]);
            if (data == null) {
                stats.warn("그림 파일을 찾지 못해 뺌: " + ref);
                skip(pic);
                return;
            }
            String href = item[0];
            String ext = href.lastIndexOf('.') < 0 ? "" : href.substring(href.lastIndexOf('.') + 1);
            if (!DocxParts.isImageExtension(ext)) {
                stats.warn("Word가 읽지 못하는 그림 형식이라 뺌: " + href);
                skip(pic);
                return;
            }
            Element sz = child(pic, NS_HP, "sz");
            Element cur = child(pic, NS_HP, "curSz");
            long cx = EMU_PER_HWPUNIT * Math.max(1, positive(HwpxHeader.intAttr(sz, "width", 0), HwpxHeader.intAttr(cur, "width", 0)));
            long cy = EMU_PER_HWPUNIT * Math.max(1, positive(HwpxHeader.intAttr(sz, "height", 0), HwpxHeader.intAttr(cur, "height", 0)));
            String rid;
            if (headerFooterDepth > 0) {
                // 머리말·꼬리말 파트의 관계는 그 파트의 관계 파일에 따로 적음
                String media = parts.media(href, data, ext);
                rid = null;
                for (String[] r : partRels) {
                    if (r[2].equals(media)) {
                        rid = r[0];
                    }
                }
                if (rid == null) {
                    rid = "rId" + (partRels.size() + 1);
                    partRels.add(new String[]{rid, "image", media});
                }
            } else {
                rid = parts.image(href, data, ext);
            }
            stats.picture();
            int id = drawingId++;

            Element pos = child(pic, NS_HP, "pos");
            Element om = child(pic, NS_HP, "outMargin");
            String dist = " distT=\"" + emu(om, "top") + "\" distB=\"" + emu(om, "bottom") + "\" distL=\"" + emu(om, "left")
                    + "\" distR=\"" + emu(om, "right") + "\"";
            String graphic = "<wp:docPr id=\"" + id + "\" name=\"그림 " + id + "\"/>"
                    + "<wp:cNvGraphicFramePr><a:graphicFrameLocks noChangeAspect=\"1\"/></wp:cNvGraphicFramePr>"
                    + "<a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">"
                    + "<pic:pic><pic:nvPicPr><pic:cNvPr id=\"0\" name=\"" + Xml.attr(href) + "\"/><pic:cNvPicPr/></pic:nvPicPr>"
                    + "<pic:blipFill><a:blip r:embed=\"" + rid + "\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>"
                    + "<pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"" + cx + "\" cy=\"" + cy + "\"/></a:xfrm>"
                    + "<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr></pic:pic>"
                    + "</a:graphicData></a:graphic>";
            out.append("<w:r>").append(rPr).append("<w:drawing>");
            if (pos == null || "1".equals(pos.getAttribute("treatAsChar"))) {
                out.append("<wp:inline").append(dist).append("><wp:extent cx=\"").append(cx).append("\" cy=\"").append(cy)
                        .append("\"/>").append(graphic).append("</wp:inline>");
            } else {
                String wrap = pic.getAttribute("textWrap");
                boolean behind = "BEHIND_TEXT".equals(wrap);
                out.append("<wp:anchor").append(dist).append(" simplePos=\"0\" relativeHeight=\"").append(id)
                        .append("\" behindDoc=\"").append(behind ? 1 : 0).append("\" locked=\"0\" layoutInCell=\"1\"")
                        .append(" allowOverlap=\"").append("1".equals(pos.getAttribute("allowOverlap")) ? 1 : 0).append("\">")
                        .append("<wp:simplePos x=\"0\" y=\"0\"/>")
                        .append(position("positionH", horzFrom(pos.getAttribute("horzRelTo")), pos.getAttribute("horzAlign"),
                                HwpxHeader.intAttr(pos, "horzOffset", 0)))
                        .append(position("positionV", vertFrom(pos.getAttribute("vertRelTo")), pos.getAttribute("vertAlign"),
                                HwpxHeader.intAttr(pos, "vertOffset", 0)))
                        .append("<wp:extent cx=\"").append(cx).append("\" cy=\"").append(cy).append("\"/>")
                        .append("<wp:effectExtent l=\"0\" t=\"0\" r=\"0\" b=\"0\"/>")
                        .append(wrap(wrap))
                        .append(graphic).append("</wp:anchor>");
            }
            out.append("</w:drawing></w:r>");
        }

        private static int positive(int a, int b) {
            return a > 0 ? a : b;
        }

        private static long emu(Element margin, String side) {
            return Math.max(0, EMU_PER_HWPUNIT * HwpxHeader.intAttr(margin, side, 0));
        }

        // 한컴 PAPER는 용지 끝, PAGE는 쪽 여백 안쪽 기준
        private static String horzFrom(String v) {
            return "PAPER".equals(v) ? "page" : "PAGE".equals(v) ? "margin" : "column";
        }

        private static String vertFrom(String v) {
            return "PAPER".equals(v) ? "page" : "PAGE".equals(v) ? "margin" : "paragraph";
        }

        private static String position(String tag, String from, String align, int offset) {
            String a = "CENTER".equals(align) ? "center"
                    : "RIGHT".equals(align) ? "right"
                    : "BOTTOM".equals(align) ? "bottom"
                    : "INSIDE".equals(align) ? "inside"
                    : "OUTSIDE".equals(align) ? "outside" : null;
            String inner = a != null ? "<wp:align>" + a + "</wp:align>"
                    : "<wp:posOffset>" + EMU_PER_HWPUNIT * offset + "</wp:posOffset>";
            return "<wp:" + tag + " relativeFrom=\"" + from + "\">" + inner + "</wp:" + tag + ">";
        }

        private static String wrap(String textWrap) {
            switch (textWrap) {
                case "SQUARE":
                    return "<wp:wrapSquare wrapText=\"bothSides\"/>";
                case "BEHIND_TEXT":
                case "IN_FRONT_OF_TEXT":
                    return "<wp:wrapNone/>";
                default:
                    return "<wp:wrapTopAndBottom/>";
            }
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

package io.github.limjaemin99.hwpxdocx.internal.d2h;

import io.github.limjaemin99.hwpxdocx.ConvertOptions;
import io.github.limjaemin99.hwpxdocx.internal.ConvertStats;
import io.github.limjaemin99.hwpxdocx.internal.Converter;
import io.github.limjaemin99.hwpxdocx.internal.Xml;
import io.github.limjaemin99.hwpxdocx.internal.hwpx.HwpxSkeleton;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static io.github.limjaemin99.hwpxdocx.internal.d2h.Props.NS_W;
import static io.github.limjaemin99.hwpxdocx.internal.d2h.Props.attr;
import static io.github.limjaemin99.hwpxdocx.internal.d2h.Props.child;

/** DOCX → HWPX. 지금은 본문 문단(글자·문단 모양), 표, 그림, 구역별 쪽 설정, 머리말·꼬리말을 옮긴다. 변환 한 번마다 새로 만든다. */
public final class DocxToHwpx implements Converter {

    // Word 한 줄 높이 ÷ 글자 크기 (Word에서 잰 값). 배수 줄 간격을 한컴 비율로 바꿀 때 씀. 없는 글꼴은 평균값
    private static final Map<String, Double> LINE_FACTOR = new HashMap<String, Double>();
    private static final double DEFAULT_LINE_FACTOR = 1.3;

    static {
        LINE_FACTOR.put("맑은 고딕", 1.73);
        LINE_FACTOR.put("함초롬바탕", 1.69);
        LINE_FACTOR.put("함초롬돋움", 1.69);
        LINE_FACTOR.put("나눔고딕", 1.5);
        LINE_FACTOR.put("Times New Roman", 1.15);
        LINE_FACTOR.put("Arial", 1.15);
        LINE_FACTOR.put("Calibri", 1.22);
    }

    @Override
    public void convert(InputStream in, OutputStream out, ConvertOptions options, ConvertStats stats)
            throws IOException {
        DocxPackage pkg = DocxPackage.read(in);
        Run run = new Run(pkg, new DocxStyles(pkg), stats);
        NodeList bodies = pkg.parse(pkg.mainPart).getElementsByTagNameNS(NS_W, "body");
        List<String> paragraphs = new ArrayList<String>();
        Element lastSectPr = null;
        if (bodies.getLength() > 0) {
            Element body = (Element) bodies.item(0);
            run.blocks(body, paragraphs);
            for (Node n = body.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (n instanceof Element && "sectPr".equals(n.getLocalName()) && NS_W.equals(n.getNamespaceURI())) {
                    lastSectPr = (Element) n;
                }
            }
        }
        // Word는 구역 끝 문단의 pPr에 그 구역 설정을 두고, 마지막 구역 설정은 body 끝에 둠
        List<Element> sectPrs = new ArrayList<Element>(run.sectionEnds);
        sectPrs.add(lastSectPr);
        List<Integer> cuts = new ArrayList<Integer>(run.sectionCuts);
        cuts.add(paragraphs.size());
        List<String> sections = new ArrayList<String>();
        Map<String, String> inherited = new HashMap<String, String>();
        int from = 0;
        for (int i = 0; i < sectPrs.size(); i++) {
            List<String> paras = new ArrayList<String>(paragraphs.subList(from, cuts.get(i)));
            from = cuts.get(i);
            if (paras.isEmpty()) {
                paras.add("<hp:p id=\"0\" paraPrIDRef=\"0\" styleIDRef=\"0\" pageBreak=\"0\" columnBreak=\"0\" merged=\"0\">"
                        + "<hp:run charPrIDRef=\"0\"/></hp:p>");
            }
            // 구역 설정과 머리말·꼬리말은 구역 첫 문단의 맨 앞 런에 둠
            String first = paras.get(0);
            int open = first.indexOf('>') + 1;
            paras.set(0, first.substring(0, open) + run.section(sectPrs.get(i), i, inherited) + first.substring(open));
            StringBuilder section = new StringBuilder();
            for (String p : paras) {
                section.append(p);
            }
            sections.add(HwpxSkeleton.sectionXml(section.toString()));
        }
        HwpxSkeleton.write(HwpxSkeleton.entries(HwpxSkeleton.headerXml(run.refs.refList(), sections.size()),
                sections, run.binItems, run.binData), out);
    }

    /** 문단 안의 한 조각: 글, 탭, 줄바꿈, 쪽 나눔, 쪽 번호. */
    private static final class Item {
        static final int TEXT = 0;
        static final int TAB = 1;
        static final int BREAK = 2;
        static final int PAGE_BREAK = 3;
        static final int AUTO_NUM = 4;
        static final int OBJECT = 5;

        final Props props;
        final int kind;
        final String text;

        Item(Props props, int kind, String text) {
            this.props = props;
            this.kind = kind;
            this.text = text;
        }
    }

    /** 필드(w:fldChar begin … separate … end) 읽는 상태. */
    private static final class Field {
        static final int NONE = 0;
        static final int INSTR = 1;
        static final int RESULT = 2;
        static final int SKIP_RESULT = 3;

        int state = NONE;
        final StringBuilder instr = new StringBuilder();
    }

    private static final String NS_WP = "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing";
    private static final String NS_A = "http://schemas.openxmlformats.org/drawingml/2006/main";
    private static final String NS_R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final String NS_MC = "http://schemas.openxmlformats.org/markup-compatibility/2006";

    private static final Map<String, String> IMAGE_TYPES = new HashMap<String, String>();

    static {
        IMAGE_TYPES.put("png", "image/png");
        IMAGE_TYPES.put("jpg", "image/jpeg");
        IMAGE_TYPES.put("jpeg", "image/jpeg");
        IMAGE_TYPES.put("gif", "image/gif");
        IMAGE_TYPES.put("bmp", "image/bmp");
        IMAGE_TYPES.put("tif", "image/tiff");
        IMAGE_TYPES.put("tiff", "image/tiff");
        IMAGE_TYPES.put("emf", "image/x-emf");
        IMAGE_TYPES.put("wmf", "image/x-wmf");
    }

    private static final class Run {
        final DocxPackage pkg;
        final DocxStyles styles;
        final ConvertStats stats;
        // 지금 읽는 파트(본문·머리말)와 그 관계. 그림은 이 관계로 찾음
        String part;
        Map<String, DocxPackage.Rel> rels;
        final List<String[]> binItems = new ArrayList<String[]>();
        final Map<String, byte[]> binData = new LinkedHashMap<String, byte[]>();
        private final Map<String, String> binIdByTarget = new HashMap<String, String>();
        final HwpxRefs refs = new HwpxRefs();
        // 표·그림 등 개체 ID. 문서 안에서 겹치지 않으면 됨
        int nextObjectId = 1000;
        // 본문 문단 pPr 안의 구역 설정과, 그 구역이 끝나는 위치(본문 HWPX 문단 수)
        final List<Element> sectionEnds = new ArrayList<Element>();
        final List<Integer> sectionCuts = new ArrayList<Integer>();
        private int tableDepth;

        Run(DocxPackage pkg, DocxStyles styles, ConvertStats stats) throws IOException {
            this.pkg = pkg;
            this.styles = styles;
            this.stats = stats;
            this.part = pkg.mainPart;
            this.rels = pkg.rels(part);
            // 바탕글(ID 0)은 기본 문단 스타일의 모양
            Props[] base = styles.paragraphBase(null);
            charId(base[1]);
            refs.paraId(base[0], lineFactor(base[1]));
        }

        int charId(Props r) {
            return refs.charId(r, styles.eastAsiaFont(r), styles.latinFont(r));
        }

        double lineFactor(Props r) {
            Double ea = LINE_FACTOR.get(styles.eastAsiaFont(r));
            Double la = LINE_FACTOR.get(styles.latinFont(r));
            return Math.max(ea == null ? DEFAULT_LINE_FACTOR : ea, la == null ? DEFAULT_LINE_FACTOR : la);
        }

        void blocks(Element parent, List<String> out) throws IOException {
            for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (!(n instanceof Element)) {
                    continue;
                }
                Element e = (Element) n;
                String name = e.getLocalName();
                if (!NS_W.equals(e.getNamespaceURI())) {
                    skip(e);
                } else if ("p".equals(name)) {
                    paragraph(e, out);
                } else if ("tbl".equals(name)) {
                    table(e, out);
                } else if ("sdt".equals(name)) {
                    Element content = child(e, "sdtContent");
                    if (content != null) {
                        blocks(content, out);
                    }
                } else if ("customXml".equals(name)) {
                    blocks(e, out);
                } else if (!"sectPr".equals(name) && !"tcPr".equals(name) && !"bookmarkStart".equals(name)
                        && !"bookmarkEnd".equals(name) && !"proofErr".equals(name) && !"permStart".equals(name)
                        && !"permEnd".equals(name)) {
                    // 구역 설정(sectPr)은 쪽 설정 변환에서 다룸
                    skip(e);
                }
            }
        }

        // ---------- 표 ----------

        /** Word 표 칸. 세로로 합친 칸은 처음 칸 하나로 모으고 rowSpan을 늘린다. */
        private static final class Cell {
            final Element tc;
            final int row;
            final int col;
            final int span;
            int rowSpan = 1;

            Cell(Element tc, int row, int col, int span) {
                this.tc = tc;
                this.row = row;
                this.col = col;
                this.span = span;
            }
        }

        /** 표는 한컴에서 문단 안 글자처럼 취급하는 개체라 표 하나를 문단 하나에 담는다. */
        void table(Element tbl, List<String> out) throws IOException {
            stats.table();
            Element tblPr = child(tbl, "tblPr");
            String styleId = attr(child(tblPr, "tblStyle"), "val");
            Map<String, Element> tableBorders = styles.tableBorders(styleId);
            DocxStyles.putChildren(tableBorders, child(tblPr, "tblBorders"));
            Map<String, Integer> margins = styles.tableCellMargins(styleId);
            DocxStyles.putMargins(margins, child(tblPr, "tblCellMar"));

            List<Integer> grid = new ArrayList<Integer>();
            Element tblGrid = child(tbl, "tblGrid");
            if (tblGrid != null) {
                for (Node n = tblGrid.getFirstChild(); n != null; n = n.getNextSibling()) {
                    if (n instanceof Element && "gridCol".equals(n.getLocalName())) {
                        grid.add(Math.max(0, parseInt(attr((Element) n, "w"), 0)));
                    }
                }
            }

            List<Cell> cells = new ArrayList<Cell>();
            List<Integer> rowHeights = new ArrayList<Integer>();
            List<Boolean> headerRows = new ArrayList<Boolean>();
            Map<Integer, Cell> openV = new HashMap<Integer, Cell>();
            int row = 0;
            for (Node n = tbl.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (!(n instanceof Element) || !"tr".equals(n.getLocalName())) {
                    continue;
                }
                Element tr = (Element) n;
                Element trPr = child(tr, "trPr");
                rowHeights.add(parseInt(attr(child(trPr, "trHeight"), "val"), 0));
                Element th = child(trPr, "tblHeader");
                headerRows.add(th != null && !"0".equals(attr(th, "val")) && !"false".equals(attr(th, "val")));
                int col = parseInt(attr(child(trPr, "gridBefore"), "val"), 0);
                for (Node m = tr.getFirstChild(); m != null; m = m.getNextSibling()) {
                    if (!(m instanceof Element) || !"tc".equals(m.getLocalName())) {
                        continue;
                    }
                    Element tc = (Element) m;
                    Element tcPr = child(tc, "tcPr");
                    int span = Math.max(1, parseInt(attr(child(tcPr, "gridSpan"), "val"), 1));
                    Element vMerge = child(tcPr, "vMerge");
                    String vm = vMerge == null ? null : attr(vMerge, "val");
                    if (vMerge != null && !"restart".equals(vm)) {
                        Cell top = openV.get(col);
                        if (top != null) {
                            top.rowSpan++;
                        }
                        col += span;
                        continue;
                    }
                    Cell c = new Cell(tc, row, col, span);
                    cells.add(c);
                    if ("restart".equals(vm)) {
                        openV.put(col, c);
                    } else {
                        openV.remove(col);
                    }
                    col += span;
                }
                row++;
            }
            int rows = row;
            int cols = grid.size();
            for (Cell c : cells) {
                cols = Math.max(cols, c.col + c.span);
            }
            while (grid.size() < cols) {
                grid.add(1000);
            }
            if (rows == 0 || cols == 0) {
                stats.warn("칸이 없는 표를 뺌");
                return;
            }

            boolean anyHeader = false;
            int height = 0;
            for (int r = 0; r < rows; r++) {
                anyHeader |= headerRows.get(r);
                height += rowHeight(rowHeights.get(r));
            }
            int width = 0;
            for (int w : grid) {
                width += w * 5;
            }
            StringBuilder b = new StringBuilder();
            b.append("<hp:tbl id=\"").append(nextObjectId++).append("\" zOrder=\"0\" numberingType=\"TABLE\"")
                    .append(" textWrap=\"TOP_AND_BOTTOM\" textFlow=\"BOTH_SIDES\" lock=\"0\" dropcapstyle=\"None\"")
                    .append(" pageBreak=\"CELL\" repeatHeader=\"").append(anyHeader ? 1 : 0)
                    .append("\" rowCnt=\"").append(rows).append("\" colCnt=\"").append(cols)
                    .append("\" cellSpacing=\"0\" borderFillIDRef=\"1\" noAdjust=\"0\">")
                    .append("<hp:sz width=\"").append(width).append("\" widthRelTo=\"ABSOLUTE\" height=\"").append(height)
                    .append("\" heightRelTo=\"ABSOLUTE\" protect=\"0\"/>")
                    .append("<hp:pos treatAsChar=\"1\" affectLSpacing=\"0\" flowWithText=\"1\" allowOverlap=\"0\"")
                    .append(" holdAnchorAndSO=\"0\" vertRelTo=\"PARA\" horzRelTo=\"COLUMN\" vertAlign=\"TOP\" horzAlign=\"LEFT\"")
                    .append(" vertOffset=\"0\" horzOffset=\"0\"/>")
                    .append("<hp:outMargin left=\"0\" right=\"0\" top=\"0\" bottom=\"0\"/>")
                    .append("<hp:inMargin").append(marginAttrs(margins)).append("/>");
            for (int r = 0; r < rows; r++) {
                b.append("<hp:tr>");
                for (Cell c : cells) {
                    if (c.row == r) {
                        cell(c, rows, cols, grid, rowHeights, headerRows.get(r), tableBorders, margins, b);
                    }
                }
                b.append("</hp:tr>");
            }
            b.append("</hp:tbl>");

            // 표를 담는 문단: 표 정렬·들여쓰기를 문단 정렬·여백으로
            Props wp = new Props();
            String jc = attr(child(tblPr, "jc"), "val");
            wp.v.put("jc", "center".equals(jc) ? "center" : "right".equals(jc) || "end".equals(jc) ? "right" : "left");
            Element ind = child(tblPr, "tblInd");
            if (ind != null && (attr(ind, "type") == null || "dxa".equals(attr(ind, "type")))) {
                wp.v.put("left", String.valueOf(parseInt(attr(ind, "w"), 0)));
            }
            int paraId = refs.paraId(wp, 1.0);
            out.add("<hp:p id=\"0\" paraPrIDRef=\"" + paraId + "\" styleIDRef=\"0\" pageBreak=\"0\" columnBreak=\"0\""
                    + " merged=\"0\"><hp:run charPrIDRef=\"0\">" + b + "<hp:t/></hp:run></hp:p>");
        }

        // 한컴 칸 높이는 최소 높이로 쓰임. 높이를 적지 않은 행은 한 줄 정도로 둠
        private static int rowHeight(int twip) {
            return Math.max(twip * 5, 1000);
        }

        private void cell(Cell c, int rows, int cols, List<Integer> grid, List<Integer> rowHeights, boolean header,
                          Map<String, Element> tableBorders, Map<String, Integer> tableMargins, StringBuilder b)
                throws IOException {
            Element tcPr = child(c.tc, "tcPr");
            Map<String, Element> own = new HashMap<String, Element>();
            DocxStyles.putChildren(own, child(tcPr, "tcBorders"));
            // 칸 테두리: 표 가장자리면 표의 바깥 선, 안쪽이면 insideH·insideV, 칸에 직접 정한 선이 우선
            String top = border(own, tableBorders, "top", c.row == 0 ? "top" : "insideH");
            String bottom = border(own, tableBorders, "bottom", c.row + c.rowSpan >= rows ? "bottom" : "insideH");
            String left = border(own, tableBorders, "left", c.col == 0 ? "left" : "insideV");
            String right = border(own, tableBorders, "right", c.col + c.span >= cols ? "right" : "insideV");
            String fill = HwpxRefs.color(attr(child(tcPr, "shd"), "fill"), null);
            int bf = refs.borderFill(top, left, bottom, right, fill);

            String va = attr(child(tcPr, "vAlign"), "val");
            Map<String, Integer> margins = new HashMap<String, Integer>(tableMargins);
            Element ownMar = child(tcPr, "tcMar");
            DocxStyles.putMargins(margins, ownMar);

            List<String> inner = new ArrayList<String>();
            tableDepth++;
            try {
                blocks(c.tc, inner);
            } finally {
                tableDepth--;
            }
            if (inner.isEmpty()) {
                inner.add("<hp:p id=\"0\" paraPrIDRef=\"0\" styleIDRef=\"0\" pageBreak=\"0\" columnBreak=\"0\" merged=\"0\">"
                        + "<hp:run charPrIDRef=\"0\"/></hp:p>");
            }
            int width = 0;
            for (int i = c.col; i < c.col + c.span; i++) {
                width += grid.get(i) * 5;
            }
            int height = 0;
            for (int r = c.row; r < Math.min(rows, c.row + c.rowSpan); r++) {
                height += rowHeight(rowHeights.get(r));
            }
            b.append("<hp:tc name=\"\" header=\"").append(header ? 1 : 0).append("\" hasMargin=\"")
                    .append(ownMar != null ? 1 : 0).append("\" protect=\"0\" editable=\"0\" dirty=\"0\" borderFillIDRef=\"")
                    .append(bf).append("\"><hp:subList id=\"\" textDirection=\"HORIZONTAL\" lineWrap=\"BREAK\" vertAlign=\"")
                    .append("center".equals(va) ? "CENTER" : "bottom".equals(va) ? "BOTTOM" : "TOP")
                    .append("\" linkListIDRef=\"0\" linkListNextIDRef=\"0\" textWidth=\"0\" textHeight=\"0\"")
                    .append(" hasTextRef=\"0\" hasNumRef=\"0\">");
            for (String p : inner) {
                b.append(p);
            }
            b.append("</hp:subList><hp:cellAddr colAddr=\"").append(c.col).append("\" rowAddr=\"").append(c.row)
                    .append("\"/><hp:cellSpan colSpan=\"").append(c.span).append("\" rowSpan=\"").append(c.rowSpan)
                    .append("\"/><hp:cellSz width=\"").append(width).append("\" height=\"").append(height)
                    .append("\"/><hp:cellMargin").append(marginAttrs(margins)).append("/></hp:tc>");
        }

        private static String border(Map<String, Element> own, Map<String, Element> table, String side, String tableKey) {
            Element e = own.containsKey(side) ? own.get(side) : table.get(tableKey);
            if (e == null) {
                return HwpxRefs.NO_LINE;
            }
            return HwpxRefs.line(attr(e, "val"), attr(e, "sz"), attr(e, "color"));
        }

        private static String marginAttrs(Map<String, Integer> m) {
            return " left=\"" + m.get("left") * 5 + "\" right=\"" + m.get("right") * 5 + "\" top=\"" + m.get("top") * 5
                    + "\" bottom=\"" + m.get("bottom") * 5 + "\"";
        }

        private static int parseInt(String s, int def) {
            if (s == null) {
                return def;
            }
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException e) {
                return def;
            }
        }

        void paragraph(Element p, List<String> out) throws IOException {
            stats.paragraph();
            Element pPr = child(p, "pPr");
            Props[] base = styles.paragraphBase(attr(child(pPr, "pStyle"), "val"));
            Props pp = base[0].applyPara(pPr);
            Props rp = base[1];
            String numId = pp.get("numId");
            if (numId != null && !"0".equals(numId)) {
                stats.warn("자동 번호·글머리표는 아직 옮기지 않음 (번호 글자가 빠짐)");
            }
            Props mark = rp.copy().applyRun(child(pPr, "rPr"));

            List<Item> items = new ArrayList<Item>();
            collect(p, rp, items, new Field());

            Props biggest = mark;
            for (Item it : items) {
                if (it.kind == Item.TEXT && HwpxRefs.height(it.props) > HwpxRefs.height(biggest)) {
                    biggest = it.props;
                }
            }
            int paraId = refs.paraId(pp, lineFactor(biggest));
            int markId = charId(mark);

            // w:br type=page는 한컴에 없어서 문단을 나누고 다음 문단에 쪽 나눔을 검
            List<List<Item>> pieces = new ArrayList<List<Item>>();
            List<Item> cur = new ArrayList<Item>();
            for (Item it : items) {
                if (it.kind == Item.PAGE_BREAK) {
                    pieces.add(cur);
                    cur = new ArrayList<Item>();
                } else {
                    cur.add(it);
                }
            }
            pieces.add(cur);
            for (int i = 0; i < pieces.size(); i++) {
                out.add("<hp:p id=\"0\" paraPrIDRef=\"" + paraId + "\" styleIDRef=\"0\" pageBreak=\"" + (i > 0 ? 1 : 0)
                        + "\" columnBreak=\"0\" merged=\"0\">" + runs(pieces.get(i), markId) + "</hp:p>");
            }
            Element ownSect = child(pPr, "sectPr");
            if (ownSect != null && tableDepth == 0 && pkg.mainPart.equals(part)) {
                sectionEnds.add(ownSect);
                sectionCuts.add(out.size());
            }
        }

        private String runs(List<Item> items, int markId) {
            StringBuilder b = new StringBuilder();
            int curId = -1;
            StringBuilder t = null;
            for (Item it : items) {
                int id = charId(it.props);
                if (id != curId) {
                    closeRun(b, t);
                    b.append("<hp:run charPrIDRef=\"").append(id).append("\">");
                    curId = id;
                    t = new StringBuilder();
                }
                switch (it.kind) {
                    case Item.TEXT:
                        t.append(Xml.text(it.text));
                        break;
                    case Item.TAB:
                        t.append("<hp:tab width=\"4000\" leader=\"0\" type=\"1\"/>");
                        break;
                    case Item.BREAK:
                        t.append("<hp:lineBreak/>");
                        break;
                    case Item.OBJECT:
                        flushText(b, t);
                        b.append(it.text);
                        break;
                    default:
                        // 쪽 번호는 hp:t 밖의 컨트롤
                        flushText(b, t);
                        b.append("<hp:ctrl><hp:autoNum num=\"1\" numType=\"").append(it.text).append("\">")
                                .append("<hp:autoNumFormat type=\"DIGIT\" userChar=\"\" prefixChar=\"\" suffixChar=\"\"")
                                .append(" supscript=\"0\"/></hp:autoNum></hp:ctrl>");
                }
            }
            closeRun(b, t);
            if (b.length() == 0) {
                // 빈 문단: 문단 기호의 글자 모양으로 줄 높이가 정해짐
                b.append("<hp:run charPrIDRef=\"").append(markId).append("\"/>");
            }
            return b.toString();
        }

        private static void flushText(StringBuilder b, StringBuilder t) {
            if (t != null && t.length() > 0) {
                b.append("<hp:t>").append(t).append("</hp:t>");
                t.setLength(0);
            }
        }

        private static void closeRun(StringBuilder b, StringBuilder t) {
            if (t != null) {
                flushText(b, t);
                b.append("</hp:run>");
            }
        }

        private void collect(Element parent, Props rp, List<Item> items, Field field) throws IOException {
            for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (!(n instanceof Element)) {
                    continue;
                }
                Element e = (Element) n;
                String name = e.getLocalName();
                if (!NS_W.equals(e.getNamespaceURI())) {
                    skip(e);
                    continue;
                }
                switch (name) {
                    case "r":
                        run(e, rp, items, field);
                        break;
                    case "fldSimple":
                        String kind = fieldKind(attr(e, "instr"));
                        if (kind != null) {
                            items.add(new Item(rp.copy(), Item.AUTO_NUM, kind));
                        } else {
                            collect(e, rp, items, field);
                        }
                        break;
                    case "hyperlink":
                    case "smartTag":
                    case "ins":
                    case "moveTo":
                    case "customXml":
                        collect(e, rp, items, field);
                        break;
                    case "sdt":
                        Element content = child(e, "sdtContent");
                        if (content != null) {
                            collect(content, rp, items, field);
                        }
                        break;
                    case "del":
                    case "moveFrom":
                        stats.warn("변경 추적으로 지운 글은 옮기지 않음");
                        break;
                    case "pPr":
                    case "bookmarkStart":
                    case "bookmarkEnd":
                    case "proofErr":
                    case "commentRangeStart":
                    case "commentRangeEnd":
                    case "permStart":
                    case "permEnd":
                        break;
                    default:
                        skip(e);
                }
            }
        }

        private void run(Element r, Props paraRun, List<Item> items, Field field) throws IOException {
            Props rd = paraRun.copy();
            Element rPr = child(r, "rPr");
            String rStyle = attr(child(rPr, "rStyle"), "val");
            if (rStyle != null) {
                styles.applyRunStyle(rd, rStyle);
            }
            rd.applyRun(rPr);
            if (rd.on("vanish")) {
                return;
            }
            for (Node n = r.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (!(n instanceof Element)) {
                    continue;
                }
                Element c = (Element) n;
                String name = c.getLocalName();
                if (NS_MC.equals(c.getNamespaceURI()) && "AlternateContent".equals(name)) {
                    // Word 2010 이후 형식의 대체 내용: 그림이 들어 있으면 그것을 씀
                    NodeList drawings = c.getElementsByTagNameNS(NS_W, "drawing");
                    if (drawings.getLength() > 0) {
                        picture((Element) drawings.item(0), rd, items);
                    } else {
                        skip(c);
                    }
                    continue;
                }
                if (!NS_W.equals(c.getNamespaceURI())) {
                    skip(c);
                    continue;
                }
                if ("fldChar".equals(name)) {
                    String type = attr(c, "fldCharType");
                    if ("begin".equals(type)) {
                        field.state = Field.INSTR;
                        field.instr.setLength(0);
                    } else if ("separate".equals(type)) {
                        String kind = fieldKind(field.instr.toString());
                        if (kind != null) {
                            items.add(new Item(rd, Item.AUTO_NUM, kind));
                            field.state = Field.SKIP_RESULT;
                        } else {
                            field.state = Field.RESULT;
                        }
                    } else if ("end".equals(type)) {
                        field.state = Field.NONE;
                    }
                    continue;
                }
                if (field.state == Field.INSTR) {
                    if ("instrText".equals(name)) {
                        field.instr.append(c.getTextContent());
                    }
                    continue;
                }
                if (field.state == Field.SKIP_RESULT) {
                    continue;
                }
                switch (name) {
                    case "t":
                        text(c.getTextContent(), rd, items);
                        break;
                    case "tab":
                        items.add(new Item(rd, Item.TAB, null));
                        break;
                    case "br":
                        String type = attr(c, "type");
                        if ("page".equals(type)) {
                            items.add(new Item(rd, Item.PAGE_BREAK, null));
                        } else {
                            if ("column".equals(type)) {
                                stats.warn("단 나누기는 줄바꿈으로 바꿈");
                            }
                            items.add(new Item(rd, Item.BREAK, null));
                        }
                        break;
                    case "cr":
                        items.add(new Item(rd, Item.BREAK, null));
                        break;
                    case "noBreakHyphen":
                        items.add(new Item(rd, Item.TEXT, "-"));
                        break;
                    case "sym":
                        String code = attr(c, "char");
                        if (code != null) {
                            try {
                                items.add(new Item(rd, Item.TEXT, new String(Character.toChars(Integer.parseInt(code, 16)))));
                            } catch (IllegalArgumentException ex) {
                                skip(c);
                            }
                        }
                        break;
                    case "drawing":
                        picture(c, rd, items);
                        break;
                    case "rPr":
                    case "lastRenderedPageBreak":
                    case "softHyphen":
                    case "proofErr":
                        break;
                    default:
                        skip(c);
                }
            }
        }

        // ---------- 쪽 설정·머리말·꼬리말 ----------

        /**
         * w:sectPr 하나 → 구역 설정 런 + 머리말·꼬리말 런.
         * 머리말·꼬리말을 적지 않은 구역은 Word·한컴 모두 앞 구역 것을 이어 쓰므로 구역이 직접 적은 것만 둔다.
         * {@code inherited}는 지금까지 이어진 종류별 파트로, 홀수·짝수 나눔을 정할 때만 씀.
         * Word 위 여백은 용지 끝에서 본문까지, 머리말 거리는 용지 끝에서 머리말까지라
         * 한컴 위 여백 = 머리말 거리, 머리말 높이 = 위 여백 − 머리말 거리로 바꾼다 (아래도 같음).
         */
        String section(Element sectPr, int index, Map<String, String> inherited) throws IOException {
            if (sectPr == null) {
                return HwpxSkeleton.secPrRun("0");
            }
            if (index > 0) {
                // 한컴 구역은 늘 새 쪽에서 시작함
                String type = attr(child(sectPr, "type"), "val");
                if ("continuous".equals(type) || "nextColumn".equals(type)) {
                    stats.warn("같은 쪽에서 이어지는 구역 나누기는 새 쪽에서 시작하는 구역으로 바꿈");
                } else if ("oddPage".equals(type) || "evenPage".equals(type)) {
                    stats.warn("홀수·짝수 쪽에서 시작하는 구역 나누기는 다음 쪽에서 시작하는 구역으로 바꿈");
                }
            }
            Element pgSz = child(sectPr, "pgSz");
            Element pgMar = child(sectPr, "pgMar");
            int w = parseInt(attr(pgSz, "w"), 11906);
            int h = parseInt(attr(pgSz, "h"), 16838);
            boolean landscape = "landscape".equals(attr(pgSz, "orient"));
            // Word는 위·아래 여백에 음수를 쓸 수 있음 (머리말이 길어도 본문 위치 고정)
            int top = Math.abs(parseInt(attr(pgMar, "top"), 1440));
            int bottom = Math.abs(parseInt(attr(pgMar, "bottom"), 1440));
            int header = parseInt(attr(pgMar, "header"), 851);
            int footer = parseInt(attr(pgMar, "footer"), 992);
            String pagePr = "<hp:pagePr landscape=\"" + (landscape ? "NARROWLY" : "WIDELY") + "\" width=\"" + w * 5
                    + "\" height=\"" + h * 5 + "\" gutterType=\"LEFT_ONLY\"><hp:margin header=\""
                    + Math.max(0, top - header) * 5 + "\" footer=\"" + Math.max(0, bottom - footer) * 5
                    + "\" gutter=\"" + parseInt(attr(pgMar, "gutter"), 0) * 5
                    + "\" left=\"" + parseInt(attr(pgMar, "left"), 1440) * 5
                    + "\" right=\"" + parseInt(attr(pgMar, "right"), 1440) * 5
                    + "\" top=\"" + header * 5 + "\" bottom=\"" + footer * 5 + "\"/></hp:pagePr>";
            Element cols = child(sectPr, "cols");
            int colCount = parseInt(attr(cols, "num"), 1);
            int colGap = parseInt(attr(cols, "space"), colCount > 1 ? 720 : 0) * 5;
            int start = parseInt(attr(child(sectPr, "pgNumType"), "start"), 0);
            Element titlePgEl = child(sectPr, "titlePg");
            boolean titlePg = titlePgEl != null && !"0".equals(attr(titlePgEl, "val")) && !"false".equals(attr(titlePgEl, "val"));

            boolean evenOdd = evenAndOddHeaders();
            StringBuilder ctrls = new StringBuilder();
            for (String what : new String[]{"header", "footer"}) {
                Map<String, String> parts = new HashMap<String, String>();
                for (Node n = sectPr.getFirstChild(); n != null; n = n.getNextSibling()) {
                    if (n instanceof Element && (what + "Reference").equals(n.getLocalName())) {
                        Element ref = (Element) n;
                        String type = attr(ref, "type") == null ? "default" : attr(ref, "type");
                        DocxPackage.Rel rel = rels.get(ref.getAttributeNS(NS_R, "id"));
                        if (rel != null && !rel.external && pkg.has(rel.target)) {
                            parts.put(type, rel.target);
                        }
                    }
                }
                // 짝수 쪽 머리말이 앞 구역에서 이어지면 이 구역의 기본 머리말은 홀수 쪽에만 걸어야 함
                boolean even = parts.containsKey("even") || inherited.containsKey(what + ":even");
                for (Map.Entry<String, String> e : parts.entrySet()) {
                    inherited.put(what + ":" + e.getKey(), e.getValue());
                }
                if (titlePg && parts.containsKey("first") && hasText(parts.get("first"))) {
                    // 한컴은 첫 쪽 머리말을 따로 둘 수 없고 감추기만 됨
                    stats.warn("첫 쪽만 다른 머리말·꼬리말은 첫 쪽 감추기로 바꿈");
                }
                if (parts.containsKey("default")) {
                    String apply = evenOdd && even ? "ODD" : "BOTH";
                    ctrls.append(headerFooter(what, apply, parts.get("default")));
                }
                if (evenOdd && parts.containsKey("even")) {
                    ctrls.append(headerFooter(what, "EVEN", parts.get("even")));
                }
            }
            String run = HwpxSkeleton.secPrRun("0", pagePr, start, titlePg, titlePg, colCount, colGap);
            if (ctrls.length() > 0) {
                run += "<hp:run charPrIDRef=\"0\">" + ctrls + "</hp:run>";
            }
            return run;
        }

        private boolean evenAndOddHeaders() throws IOException {
            String settings = pkg.related("settings");
            if (settings == null) {
                return false;
            }
            NodeList l = pkg.parse(settings).getElementsByTagNameNS(NS_W, "evenAndOddHeaders");
            if (l.getLength() == 0) {
                return false;
            }
            String v = attr((Element) l.item(0), "val");
            return !"0".equals(v) && !"false".equals(v);
        }

        private boolean hasText(String part) throws IOException {
            NodeList ts = pkg.parse(part).getElementsByTagNameNS(NS_W, "t");
            for (int i = 0; i < ts.getLength(); i++) {
                if (!ts.item(i).getTextContent().trim().isEmpty()) {
                    return true;
                }
            }
            return false;
        }

        /** 머리말·꼬리말 파트 하나 → hp:header·hp:footer 컨트롤. 그 파트의 관계로 그림을 찾는다. */
        private String headerFooter(String what, String applyPageType, String partName) throws IOException {
            String savedPart = part;
            Map<String, DocxPackage.Rel> savedRels = rels;
            List<String> paras = new ArrayList<String>();
            try {
                part = partName;
                rels = pkg.rels(partName);
                blocks(pkg.parse(partName).getDocumentElement(), paras);
            } finally {
                part = savedPart;
                rels = savedRels;
            }
            StringBuilder b = new StringBuilder("<hp:ctrl><hp:").append(what).append(" id=\"").append(nextObjectId++)
                    .append("\" applyPageType=\"").append(applyPageType).append("\">")
                    .append("<hp:subList id=\"\" textDirection=\"HORIZONTAL\" lineWrap=\"BREAK\" vertAlign=\"")
                    .append("header".equals(what) ? "TOP" : "BOTTOM")
                    .append("\" linkListIDRef=\"0\" linkListNextIDRef=\"0\" textWidth=\"0\" textHeight=\"0\"")
                    .append(" hasTextRef=\"0\" hasNumRef=\"0\">");
            if (paras.isEmpty()) {
                b.append("<hp:p id=\"0\" paraPrIDRef=\"0\" styleIDRef=\"0\" pageBreak=\"0\" columnBreak=\"0\" merged=\"0\">")
                        .append("<hp:run charPrIDRef=\"0\"/></hp:p>");
            }
            for (String p : paras) {
                b.append(p);
            }
            return b.append("</hp:subList></hp:").append(what).append("></hp:ctrl>").toString();
        }

        // ---------- 그림 ----------

        private void picture(Element drawing, Props rd, List<Item> items) throws IOException {
            Element inline = childNs(drawing, NS_WP, "inline");
            Element anchor = childNs(drawing, NS_WP, "anchor");
            Element node = inline != null ? inline : anchor;
            NodeList blips = node == null ? null : node.getElementsByTagNameNS(NS_A, "blip");
            if (blips == null || blips.getLength() == 0) {
                stats.warn("그림이 아닌 그리기 개체(도형·차트 등)는 아직 옮기지 않음");
                skip(drawing);
                return;
            }
            String rid = ((Element) blips.item(0)).getAttributeNS(NS_R, "embed");
            DocxPackage.Rel rel = rels.get(rid);
            byte[] data = rel == null || rel.external ? null : pkg.get(rel.target);
            if (data == null) {
                stats.warn("그림 파일을 찾지 못하거나 외부 연결 그림이라 뺌");
                skip(drawing);
                return;
            }
            String ext = rel.target.lastIndexOf('.') < 0 ? "" : rel.target.substring(rel.target.lastIndexOf('.') + 1)
                    .toLowerCase(Locale.ROOT);
            String media = IMAGE_TYPES.get(ext);
            if (media == null) {
                stats.warn("한컴이 읽지 못하는 그림 형식이라 뺌: " + rel.target);
                skip(drawing);
                return;
            }
            String binId = binIdByTarget.get(rel.target);
            if (binId == null) {
                binId = "image" + (binItems.size() + 1);
                String href = "BinData/" + binId + "." + ext;
                binItems.add(new String[]{binId, href, media});
                binData.put(href, data);
                binIdByTarget.put(rel.target, binId);
            }
            stats.picture();
            Element extent = childNs(node, NS_WP, "extent");
            // EMU ÷ 127 = HWPUNIT
            int w = (int) Math.max(1, parseLong(extent == null ? null : extent.getAttribute("cx")) / 127);
            int h = (int) Math.max(1, parseLong(extent == null ? null : extent.getAttribute("cy")) / 127);
            int id = nextObjectId++;
            String pos;
            String wrap;
            if (inline != null) {
                pos = "<hp:pos treatAsChar=\"1\" affectLSpacing=\"0\" flowWithText=\"1\" allowOverlap=\"0\" holdAnchorAndSO=\"0\""
                        + " vertRelTo=\"PARA\" horzRelTo=\"COLUMN\" vertAlign=\"TOP\" horzAlign=\"LEFT\" vertOffset=\"0\" horzOffset=\"0\"/>";
                wrap = "TOP_AND_BOTTOM";
            } else {
                Element ph = childNs(anchor, NS_WP, "positionH");
                Element pv = childNs(anchor, NS_WP, "positionV");
                String hFrom = ph == null ? "column" : ph.getAttribute("relativeFrom");
                String vFrom = pv == null ? "paragraph" : pv.getAttribute("relativeFrom");
                // Word page는 용지 끝(한컴 PAPER), margin은 여백 안쪽(한컴 PAGE)
                String hRel = "page".equals(hFrom) ? "PAPER" : "margin".equals(hFrom) ? "PAGE" : "COLUMN";
                String vRel = "page".equals(vFrom) ? "PAPER" : "margin".equals(vFrom) ? "PAGE" : "PARA";
                pos = "<hp:pos treatAsChar=\"0\" affectLSpacing=\"0\" flowWithText=\"" + ("PARA".equals(vRel) ? 1 : 0)
                        + "\" allowOverlap=\"" + ("1".equals(anchor.getAttribute("allowOverlap")) ? 1 : 0)
                        + "\" holdAnchorAndSO=\"0\" vertRelTo=\"" + vRel + "\" horzRelTo=\"" + hRel
                        + "\" vertAlign=\"" + align(pv, "TOP") + "\" horzAlign=\"" + align(ph, "LEFT")
                        + "\" vertOffset=\"" + offset(pv) + "\" horzOffset=\"" + offset(ph) + "\"/>";
                if (childNs(anchor, NS_WP, "wrapNone") != null) {
                    wrap = "1".equals(anchor.getAttribute("behindDoc")) ? "BEHIND_TEXT" : "IN_FRONT_OF_TEXT";
                } else if (childNs(anchor, NS_WP, "wrapSquare") != null || childNs(anchor, NS_WP, "wrapTight") != null
                        || childNs(anchor, NS_WP, "wrapThrough") != null) {
                    wrap = "SQUARE";
                } else {
                    wrap = "TOP_AND_BOTTOM";
                }
            }
            String xml = "<hp:pic id=\"" + id + "\" zOrder=\"" + id + "\" numberingType=\"PICTURE\" textWrap=\"" + wrap
                    + "\" textFlow=\"BOTH_SIDES\" lock=\"0\" dropcapstyle=\"None\" href=\"\" groupLevel=\"0\" instid=\"" + id
                    + "\" reverse=\"0\">"
                    + "<hp:offset x=\"0\" y=\"0\"/><hp:orgSz width=\"" + w + "\" height=\"" + h + "\"/>"
                    + "<hp:curSz width=\"" + w + "\" height=\"" + h + "\"/><hp:flip horizontal=\"0\" vertical=\"0\"/>"
                    + "<hp:rotationInfo angle=\"0\" centerX=\"" + w / 2 + "\" centerY=\"" + h / 2 + "\" rotateimage=\"1\"/>"
                    + "<hp:renderingInfo><hc:transMatrix e1=\"1\" e2=\"0\" e3=\"0\" e4=\"0\" e5=\"1\" e6=\"0\"/>"
                    + "<hc:scaMatrix e1=\"1\" e2=\"0\" e3=\"0\" e4=\"0\" e5=\"1\" e6=\"0\"/>"
                    + "<hc:rotMatrix e1=\"1\" e2=\"0\" e3=\"0\" e4=\"0\" e5=\"1\" e6=\"0\"/></hp:renderingInfo>"
                    + "<hc:img binaryItemIDRef=\"" + binId + "\" bright=\"0\" contrast=\"0\" effect=\"REAL_PIC\" alpha=\"0\"/>"
                    + "<hp:imgRect><hc:pt0 x=\"0\" y=\"0\"/><hc:pt1 x=\"" + w + "\" y=\"0\"/><hc:pt2 x=\"" + w + "\" y=\"" + h
                    + "\"/><hc:pt3 x=\"0\" y=\"" + h + "\"/></hp:imgRect>"
                    + "<hp:imgClip left=\"0\" right=\"" + w + "\" top=\"0\" bottom=\"" + h + "\"/>"
                    + "<hp:inMargin left=\"0\" right=\"0\" top=\"0\" bottom=\"0\"/>"
                    + "<hp:imgDim dimwidth=\"" + w + "\" dimheight=\"" + h + "\"/><hp:effects/>"
                    + "<hp:sz width=\"" + w + "\" widthRelTo=\"ABSOLUTE\" height=\"" + h + "\" heightRelTo=\"ABSOLUTE\" protect=\"0\"/>"
                    + pos + "<hp:outMargin left=\"0\" right=\"0\" top=\"0\" bottom=\"0\"/></hp:pic>";
            items.add(new Item(rd, Item.OBJECT, xml));
        }

        private static Element childNs(Element parent, String ns, String local) {
            if (parent == null) {
                return null;
            }
            for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (n instanceof Element && local.equals(n.getLocalName()) && ns.equals(n.getNamespaceURI())) {
                    return (Element) n;
                }
            }
            return null;
        }

        private static String align(Element position, String def) {
            Element a = position == null ? null : childNs(position, NS_WP, "align");
            if (a == null) {
                return def;
            }
            switch (a.getTextContent().trim()) {
                case "center":
                    return "CENTER";
                case "right":
                    return "RIGHT";
                case "bottom":
                    return "BOTTOM";
                case "inside":
                    return "INSIDE";
                case "outside":
                    return "OUTSIDE";
                default:
                    return def;
            }
        }

        private static long offset(Element position) {
            Element o = position == null ? null : childNs(position, NS_WP, "posOffset");
            // 음수는 한컴처럼 부호 없는 32비트 값으로 적음
            return o == null ? 0 : (parseLong(o.getTextContent()) / 127) & 0xFFFFFFFFL;
        }

        private static long parseLong(String s) {
            if (s == null) {
                return 0;
            }
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        private static void text(String s, Props rd, List<Item> items) {
            int from = 0;
            for (int i = 0; i < s.length(); i++) {
                if (s.charAt(i) == '\t') {
                    if (i > from) {
                        items.add(new Item(rd, Item.TEXT, s.substring(from, i)));
                    }
                    items.add(new Item(rd, Item.TAB, null));
                    from = i + 1;
                }
            }
            if (from < s.length()) {
                items.add(new Item(rd, Item.TEXT, s.substring(from)));
            }
        }

        /** 쪽 번호 필드면 한컴 자동 번호 종류(PAGE·TOTAL_PAGE), 아니면 null. */
        private static String fieldKind(String instr) {
            if (instr == null) {
                return null;
            }
            String s = instr.trim();
            int sp = s.indexOf(' ');
            String kind = (sp < 0 ? s : s.substring(0, sp)).toUpperCase(Locale.ROOT);
            return "PAGE".equals(kind) ? "PAGE" : "NUMPAGES".equals(kind) ? "TOTAL_PAGE" : null;
        }

        private void skip(Element e) throws IOException {
            String prefix = e.getPrefix();
            stats.skip((prefix == null ? "" : prefix + ":") + e.getLocalName());
        }
    }
}

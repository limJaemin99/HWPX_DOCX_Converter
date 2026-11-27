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
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static io.github.limjaemin99.hwpxdocx.internal.d2h.Props.NS_W;
import static io.github.limjaemin99.hwpxdocx.internal.d2h.Props.attr;
import static io.github.limjaemin99.hwpxdocx.internal.d2h.Props.child;

/** DOCX → HWPX. 지금은 본문 문단의 글자·문단 모양까지 옮긴다. 변환 한 번마다 새로 만든다. */
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
        Run run = new Run(new DocxStyles(pkg), stats);
        NodeList bodies = pkg.parse(pkg.mainPart).getElementsByTagNameNS(NS_W, "body");
        List<String> paragraphs = new ArrayList<String>();
        if (bodies.getLength() > 0) {
            run.blocks((Element) bodies.item(0), paragraphs);
        }
        if (paragraphs.isEmpty()) {
            paragraphs.add("<hp:p id=\"0\" paraPrIDRef=\"0\" styleIDRef=\"0\" pageBreak=\"0\" columnBreak=\"0\" merged=\"0\">"
                    + "<hp:run charPrIDRef=\"0\"/></hp:p>");
        }
        // 구역 설정은 첫 문단의 맨 앞 런에 둠
        String first = paragraphs.get(0);
        int open = first.indexOf('>') + 1;
        paragraphs.set(0, first.substring(0, open) + HwpxSkeleton.secPrRun("0") + first.substring(open));
        StringBuilder section = new StringBuilder();
        for (String p : paragraphs) {
            section.append(p);
        }
        HwpxSkeleton.write(HwpxSkeleton.entries(HwpxSkeleton.headerXml(run.refs.refList()),
                HwpxSkeleton.sectionXml(section.toString())), out);
    }

    /** 문단 안의 한 조각: 글, 탭, 줄바꿈, 쪽 나눔, 쪽 번호. */
    private static final class Item {
        static final int TEXT = 0;
        static final int TAB = 1;
        static final int BREAK = 2;
        static final int PAGE_BREAK = 3;
        static final int AUTO_NUM = 4;

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

    private static final class Run {
        final DocxStyles styles;
        final ConvertStats stats;
        final HwpxRefs refs = new HwpxRefs();

        Run(DocxStyles styles, ConvertStats stats) {
            this.styles = styles;
            this.stats = stats;
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
                } else if ("sdt".equals(name)) {
                    Element content = child(e, "sdtContent");
                    if (content != null) {
                        blocks(content, out);
                    }
                } else if ("customXml".equals(name)) {
                    blocks(e, out);
                } else if (!"sectPr".equals(name) && !"bookmarkStart".equals(name) && !"bookmarkEnd".equals(name)
                        && !"proofErr".equals(name) && !"permStart".equals(name) && !"permEnd".equals(name)) {
                    // 구역 설정(sectPr)은 쪽 설정 변환에서 다룸
                    skip(e);
                }
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
            if (child(pPr, "sectPr") != null) {
                stats.warn("구역이 여러 개인 문서는 아직 구역을 나누지 않음");
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

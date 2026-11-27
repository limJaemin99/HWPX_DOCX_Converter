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
import static io.github.limjaemin99.hwpxdocx.internal.hwpx.HwpxHeader.children;

/** HWPX → DOCX. 지금은 본문 문단의 글자·문단 모양까지 옮긴다. 변환 한 번마다 새로 만든다. */
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

        void paragraph(Element p, StringBuilder out) throws IOException {
            stats.paragraph();
            List<Element> runs = children(p, NS_HP, "run");
            int maxHeight = 0;
            boolean hasObject = false;
            String lastCharPr = null;
            for (Element r : runs) {
                String id = r.getAttribute("charPrIDRef");
                maxHeight = Math.max(maxHeight, header.charHeight(id));
                lastCharPr = id;
                hasObject |= HwpxHeader.child(r, NS_HP, "tbl") != null || HwpxHeader.child(r, NS_HP, "pic") != null;
            }
            if (maxHeight == 0) {
                maxHeight = 1000;
            }

            StringBuilder content = new StringBuilder();
            for (Node n = p.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (!(n instanceof Element)) {
                    continue;
                }
                Element e = (Element) n;
                if (isHp(e, "run")) {
                    run(e, content);
                } else if (!isHp(e, "linesegarray")) {
                    // linesegarray는 한컴이 계산해 둔 줄 배치라 Word에서는 다시 계산됨
                    skip(e);
                }
            }

            out.append("<w:p><w:pPr>");
            if ("1".equals(p.getAttribute("pageBreak"))) {
                out.append("<w:pageBreakBefore/>");
            }
            out.append(paraProps(p.getAttribute("paraPrIDRef"), maxHeight, hasObject));
            if (lastCharPr != null) {
                // 문단 기호의 글자 모양: 빈 문단의 줄 높이가 여기서 정해짐
                out.append("<w:rPr>").append(runProps(lastCharPr)).append("</w:rPr>");
            }
            out.append("</w:pPr>").append(content).append("</w:p>");
        }

        private void run(Element r, StringBuilder out) throws IOException {
            String rPr = "<w:rPr>" + runProps(r.getAttribute("charPrIDRef")) + "</w:rPr>";
            for (Node n = r.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (!(n instanceof Element)) {
                    continue;
                }
                Element e = (Element) n;
                if (isHp(e, "t")) {
                    text(e, rPr, out);
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

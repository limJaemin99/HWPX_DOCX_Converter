package io.github.limjaemin99.hwpxdocx.internal.d2h;

import io.github.limjaemin99.hwpxdocx.internal.docx.DocxSkeleton;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 시험용 DOCX: 빈 골격에 본문·스타일·테마를 바꿔 넣는다. 문장은 대한민국헌법에서. */
final class TestDocx {

    static final String W_NS = "xmlns:w=\"" + DocxSkeleton.NS_W + "\"";

    private final Map<String, byte[]> entries = DocxSkeleton.entries();
    private final List<String[]> rels = new ArrayList<String[]>();

    String get(String name) {
        return new String(entries.get(name), StandardCharsets.UTF_8);
    }

    TestDocx put(String name, String xml) {
        entries.put(name, xml.getBytes(StandardCharsets.UTF_8));
        return this;
    }

    TestDocx body(String... blocks) {
        return put("word/document.xml", DocxSkeleton.documentXml(String.join("", blocks)));
    }

    /** styles.xml의 문서 기본값 뒤에 스타일을 더함. */
    TestDocx styles(String... style) {
        String xml = get("word/styles.xml");
        int end = xml.lastIndexOf("</w:styles>");
        return put("word/styles.xml", xml.substring(0, end) + String.join("", style) + xml.substring(end));
    }

    /** 부 글꼴(minor)의 영문·한글 글꼴을 정한 테마를 넣고 본문 관계에 이음. */
    TestDocx theme(String latin, String hangul) {
        rels.add(new String[]{"rId9", "theme", "theme/theme1.xml"});
        put("word/_rels/document.xml.rels", DocxSkeleton.documentRels(rels));
        String fonts = "<a:latin typeface=\"" + latin + "\"/><a:ea typeface=\"\"/><a:cs typeface=\"\"/>"
                + "<a:font script=\"Hang\" typeface=\"" + hangul + "\"/>";
        return put("word/theme/theme1.xml", "<a:theme xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\""
                + " name=\"t\"><a:themeElements><a:fontScheme name=\"f\"><a:majorFont>" + fonts + "</a:majorFont>"
                + "<a:minorFont>" + fonts + "</a:minorFont></a:fontScheme></a:themeElements></a:theme>");
    }

    static String p(String pPr, String... runs) {
        return "<w:p>" + (pPr.isEmpty() ? "" : "<w:pPr>" + pPr + "</w:pPr>") + String.join("", runs) + "</w:p>";
    }

    static String r(String rPr, String inner) {
        return "<w:r>" + (rPr.isEmpty() ? "" : "<w:rPr>" + rPr + "</w:rPr>") + inner + "</w:r>";
    }

    static String t(String s) {
        return "<w:t xml:space=\"preserve\">" + s + "</w:t>";
    }

    /** 마지막 구역 설정(w:sectPr 요소 전체)을 정해 본문을 씀. */
    TestDocx bodyWithSection(String sectPr, String... blocks) {
        return put("word/document.xml", DocxSkeleton.documentXml(String.join("", blocks), sectPr));
    }

    /** 머리말(hdr)·꼬리말(ftr) 파트를 넣고 본문 관계에 이음. */
    TestDocx headerFooter(String rid, String name, String tag, String body) {
        rels.add(new String[]{rid, "hdr".equals(tag) ? "header" : "footer", name});
        put("word/" + name, DocxSkeleton.headerFooterXml(tag, body));
        return put("word/_rels/document.xml.rels", DocxSkeleton.documentRels(rels));
    }

    /** word/numbering.xml을 넣고 본문 관계에 이음. {@code inner}는 w:numbering 안쪽. */
    TestDocx numbering(String inner) {
        rels.add(new String[]{"rId8", "numbering", "numbering.xml"});
        put("word/numbering.xml", "<w:numbering " + W_NS + ">" + inner + "</w:numbering>");
        return put("word/_rels/document.xml.rels", DocxSkeleton.documentRels(rels));
    }

    /** word/footnotes.xml·endnotes.xml({@code tag}는 footnotes·endnotes)을 넣고 본문 관계에 이음. 구분선 특수 각주를 앞에 둠. */
    TestDocx notes(String tag, String inner) {
        String one = tag.substring(0, tag.length() - 1);
        rels.add(new String[]{"footnotes".equals(tag) ? "rId7" : "rId6", tag, tag + ".xml"});
        String sep = "<w:" + one + " w:type=\"separator\" w:id=\"-1\"><w:p><w:r><w:separator/></w:r></w:p></w:" + one + ">"
                + "<w:" + one + " w:type=\"continuationSeparator\" w:id=\"0\"><w:p><w:r><w:continuationSeparator/></w:r></w:p></w:"
                + one + ">";
        put("word/" + tag + ".xml", "<w:" + tag + " " + W_NS + " xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\""
                + " xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\">"
                + sep + inner + "</w:" + tag + ">");
        return put("word/_rels/document.xml.rels", DocxSkeleton.documentRels(rels));
    }

    /** 각주 파트 안 관계(그림)를 넣음. */
    TestDocx noteImage(String tag, String rid, String target, byte[] data) {
        entries.put("word/" + target, data);
        return put("word/_rels/" + tag + ".xml.rels", DocxSkeleton.relationshipsXml(
                java.util.Collections.singletonList(new String[]{rid, "image", target})));
    }

    TestDocx evenAndOddHeaders() {
        return put("word/settings.xml", DocxSkeleton.settingsXml(true));
    }

    /** word/media에 그림을 넣고 본문 관계에 이음. */    TestDocx image(String rid, String target, byte[] data) {
        rels.add(new String[]{rid, "image", target});
        entries.put("word/" + target, data);
        return put("word/_rels/document.xml.rels", DocxSkeleton.documentRels(rels));
    }

    private static final String PIC = "<a:graphic xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\">"
            + "<a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">"
            + "<pic:pic xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\"><pic:nvPicPr>"
            + "<pic:cNvPr id=\"0\" name=\"x\"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill>"
            + "<a:blip r:embed=\"%s\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>"
            + "<pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"%d\" cy=\"%d\"/></a:xfrm>"
            + "<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic>";

    static String inline(String rid, long cx, long cy) {
        return "<w:drawing><wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\"><wp:extent cx=\"" + cx + "\" cy=\"" + cy
                + "\"/><wp:docPr id=\"1\" name=\"그림 1\"/>" + String.format(PIC, rid, cx, cy) + "</wp:inline></w:drawing>";
    }

    /** 여백 기준 가로 가운데, 문단 기준 아래로 127000 EMU(= 1000 HWPUNIT), 어울림 배치. */
    static String anchor(String rid, long cx, long cy) {
        return "<w:drawing><wp:anchor distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\" simplePos=\"0\" relativeHeight=\"1\""
                + " behindDoc=\"0\" locked=\"0\" layoutInCell=\"1\" allowOverlap=\"1\"><wp:simplePos x=\"0\" y=\"0\"/>"
                + "<wp:positionH relativeFrom=\"margin\"><wp:align>center</wp:align></wp:positionH>"
                + "<wp:positionV relativeFrom=\"paragraph\"><wp:posOffset>127000</wp:posOffset></wp:positionV>"
                + "<wp:extent cx=\"" + cx + "\" cy=\"" + cy + "\"/><wp:effectExtent l=\"0\" t=\"0\" r=\"0\" b=\"0\"/>"
                + "<wp:wrapSquare wrapText=\"bothSides\"/><wp:docPr id=\"2\" name=\"그림 2\"/>"
                + String.format(PIC, rid, cx, cy) + "</wp:anchor></w:drawing>";
    }

    byte[] toBytes() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DocxSkeleton.write(entries, out);
        return out.toByteArray();
    }
}

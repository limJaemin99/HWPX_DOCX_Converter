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

    byte[] toBytes() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DocxSkeleton.write(entries, out);
        return out.toByteArray();
    }
}

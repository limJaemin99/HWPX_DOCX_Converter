package io.github.limjaemin99.hwpxdocx.internal.docx;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 빈 문단 하나가 있는 최소 DOCX 패키지 (ECMA-376, ISO/IEC 29500).
 * 기존 문서를 복사하지 않고 코드로 만든다. 작성자·회사 등 저장 정보는 비워 둔다.
 */
public final class DocxSkeleton {

    public static final String NS_W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    static final String NS_R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    static final String NS_PKG_REL = "http://schemas.openxmlformats.org/package/2006/relationships";
    static final String NS_CT = "http://schemas.openxmlformats.org/package/2006/content-types";
    static final String NS_CP = "http://schemas.openxmlformats.org/package/2006/metadata/core-properties";
    static final String NS_DC = "http://purl.org/dc/elements/1.1/";
    static final String NS_EP = "http://schemas.openxmlformats.org/officeDocument/2006/extended-properties";

    // 그림(w:drawing)에 쓰는 네임스페이스. 본문 루트에 선언해 둠
    static final String NS_DRAWING =
            " xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\""
            + " xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\""
            + " xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\"";

    private static final String REL_TYPE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/";
    private static final String CT_WML = "application/vnd.openxmlformats-officedocument.wordprocessingml.";

    // ZIP 항목 시각을 고정해 같은 입력이면 같은 바이트가 나오게 함 (1980-01-01은 ZIP 최소값)
    private static final long FIXED_TIME = 315532800000L + 12L * 3600 * 1000;

    private static final String XML_DECL = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>";

    private DocxSkeleton() {
    }

    /** 패키지 안 경로 → 내용. 순서가 ZIP 항목 순서다. */
    public static Map<String, byte[]> entries() {
        Map<String, byte[]> m = new LinkedHashMap<>();
        m.put("[Content_Types].xml", utf8(contentTypesXml()));
        m.put("_rels/.rels", utf8(packageRels()));
        m.put("word/document.xml", utf8(documentXml()));
        m.put("word/_rels/document.xml.rels", utf8(documentRels()));
        m.put("word/styles.xml", utf8(stylesXml()));
        m.put("word/settings.xml", utf8(settingsXml()));
        m.put("docProps/core.xml", utf8(coreXml()));
        m.put("docProps/app.xml", utf8(appXml()));
        return m;
    }

    public static byte[] toBytes() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out);
        return out.toByteArray();
    }

    /** {@code out}은 닫지 않는다. */
    public static void write(OutputStream out) throws IOException {
        write(entries(), out);
    }

    /** 패키지 항목을 순서대로 ZIP으로 쓴다. {@code out}은 닫지 않는다. */
    public static void write(Map<String, byte[]> entries, OutputStream out) throws IOException {
        ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8);
        for (Map.Entry<String, byte[]> e : entries.entrySet()) {
            ZipEntry z = new ZipEntry(e.getKey());
            z.setTime(FIXED_TIME);
            z.setMethod(ZipEntry.DEFLATED);
            zip.putNextEntry(z);
            zip.write(e.getValue());
            zip.closeEntry();
        }
        zip.finish();
    }

    private static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    static String contentTypesXml() {
        return contentTypesXml(Collections.<String, String>emptyMap(), Collections.<String, String>emptyMap());
    }

    /**
     * @param defaults  더할 확장자 → 콘텐츠 형식 (그림 파일 등)
     * @param overrides 더할 파트 경로("/word/header1.xml") → 콘텐츠 형식
     */
    public static String contentTypesXml(Map<String, String> defaults, Map<String, String> overrides) {
        StringBuilder b = new StringBuilder(XML_DECL).append("<Types xmlns=\"").append(NS_CT).append("\">")
                .append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
                .append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>");
        for (Map.Entry<String, String> e : defaults.entrySet()) {
            b.append("<Default Extension=\"").append(e.getKey()).append("\" ContentType=\"").append(e.getValue())
                    .append("\"/>");
        }
        b.append(override("/word/document.xml", CT_WML + "document.main+xml"))
                .append(override("/word/styles.xml", CT_WML + "styles+xml"))
                .append(override("/word/settings.xml", CT_WML + "settings+xml"))
                .append(override("/docProps/core.xml", "application/vnd.openxmlformats-package.core-properties+xml"))
                .append(override("/docProps/app.xml", "application/vnd.openxmlformats-officedocument.extended-properties+xml"));
        for (Map.Entry<String, String> e : overrides.entrySet()) {
            b.append(override(e.getKey(), e.getValue()));
        }
        return b.append("</Types>").toString();
    }

    private static String override(String part, String type) {
        return "<Override PartName=\"" + part + "\" ContentType=\"" + type + "\"/>";
    }

    static String packageRels() {
        return XML_DECL + "<Relationships xmlns=\"" + NS_PKG_REL + "\">"
                + rel("rId1", REL_TYPE + "officeDocument", "word/document.xml")
                + rel("rId2", "http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties",
                        "docProps/core.xml")
                + rel("rId3", REL_TYPE + "extended-properties", "docProps/app.xml")
                + "</Relationships>";
    }

    static String documentRels() {
        return documentRels(Collections.<String[]>emptyList());
    }

    /** 골격이 쓰는 관계 ID 다음 번호. 더하는 관계는 rId3부터. */
    public static final int FIRST_FREE_REL = 3;

    /** @param extra {Id, 관계 이름(image·header 등), Target} 목록. Target은 word/ 기준 상대 경로 */
    public static String documentRels(List<String[]> extra) {
        StringBuilder b = new StringBuilder(XML_DECL).append("<Relationships xmlns=\"").append(NS_PKG_REL).append("\">")
                .append(rel("rId1", REL_TYPE + "styles", "styles.xml"))
                .append(rel("rId2", REL_TYPE + "settings", "settings.xml"));
        for (String[] r : extra) {
            b.append(rel(r[0], REL_TYPE + r[1], r[2]));
        }
        return b.append("</Relationships>").toString();
    }

    /** 관계 파일 XML. @param rels {Id, 관계 이름(image 등), Target} 목록 */
    public static String relationshipsXml(List<String[]> rels) {
        StringBuilder b = new StringBuilder(XML_DECL).append("<Relationships xmlns=\"").append(NS_PKG_REL).append("\">");
        for (String[] r : rels) {
            b.append(rel(r[0], REL_TYPE + r[1], r[2]));
        }
        return b.append("</Relationships>").toString();
    }

    private static String rel(String id, String type, String target) {
        return "<Relationship Id=\"" + id + "\" Type=\"" + type + "\" Target=\"" + target + "\"/>";
    }

    static String documentXml() {
        return documentXml("<w:p/>");
    }

    // A4 세로(twip = 1/20 pt). 여백은 한국어판 Word 새 문서 기본값
    static final String DEFAULT_SECT_PR = "<w:sectPr>"
            + "<w:pgSz w:w=\"11906\" w:h=\"16838\"/>"
            + "<w:pgMar w:top=\"1701\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\""
            + " w:header=\"851\" w:footer=\"992\" w:gutter=\"0\"/>"
            + "<w:cols w:space=\"425\"/>"
            + "<w:docGrid w:linePitch=\"360\"/>"
            + "</w:sectPr>";

    /** {@code body}(w:p·w:tbl 나열) 뒤에 기본 구역 설정을 붙인 document.xml. */
    public static String documentXml(String body) {
        return documentXml(body, DEFAULT_SECT_PR);
    }

    /** {@code body} 뒤에 마지막 구역 설정 {@code sectPr}(w:sectPr 요소 전체)을 붙인 document.xml. */
    public static String documentXml(String body, String sectPr) {
        return XML_DECL + "<w:document xmlns:w=\"" + NS_W + "\" xmlns:r=\"" + NS_R + "\"" + NS_DRAWING + ">"
                + "<w:body>" + body + sectPr + "</w:body></w:document>";
    }

    /** 머리말(tag = hdr)·꼬리말(tag = ftr) 파트. {@code body}가 비면 빈 문단 하나. */
    public static String headerFooterXml(String tag, String body) {
        return XML_DECL + "<w:" + tag + " xmlns:w=\"" + NS_W + "\" xmlns:r=\"" + NS_R + "\"" + NS_DRAWING + ">"
                + (body.isEmpty() ? "<w:p/>" : body) + "</w:" + tag + ">";
    }

    static String stylesXml() {
        return XML_DECL + "<w:styles xmlns:w=\"" + NS_W + "\">"
                + "<w:docDefaults>"
                + "<w:rPrDefault><w:rPr>"
                + "<w:rFonts w:ascii=\"함초롬바탕\" w:eastAsia=\"함초롬바탕\" w:hAnsi=\"함초롬바탕\" w:cs=\"함초롬바탕\"/>"
                + "<w:kern w:val=\"2\"/>"
                + "<w:sz w:val=\"20\"/><w:szCs w:val=\"20\"/>"
                + "<w:lang w:val=\"en-US\" w:eastAsia=\"ko-KR\" w:bidi=\"ar-SA\"/>"
                + "</w:rPr></w:rPrDefault>"
                + "<w:pPrDefault><w:pPr>"
                + "<w:spacing w:after=\"0\" w:line=\"240\" w:lineRule=\"auto\"/>"
                + "</w:pPr></w:pPrDefault>"
                + "</w:docDefaults>"
                + "<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\">"
                + "<w:name w:val=\"Normal\"/><w:qFormat/>"
                + "<w:pPr><w:widowControl w:val=\"0\"/><w:wordWrap w:val=\"0\"/><w:jc w:val=\"both\"/></w:pPr>"
                + "</w:style>"
                + "<w:style w:type=\"character\" w:default=\"1\" w:styleId=\"DefaultParagraphFont\">"
                + "<w:name w:val=\"Default Paragraph Font\"/><w:uiPriority w:val=\"1\"/><w:semiHidden/>"
                + "<w:unhideWhenUsed/>"
                + "</w:style>"
                + "<w:style w:type=\"table\" w:default=\"1\" w:styleId=\"TableNormal\">"
                + "<w:name w:val=\"Normal Table\"/><w:uiPriority w:val=\"99\"/><w:semiHidden/><w:unhideWhenUsed/>"
                + "<w:tblPr><w:tblInd w:w=\"0\" w:type=\"dxa\"/><w:tblCellMar>"
                + "<w:top w:w=\"0\" w:type=\"dxa\"/><w:left w:w=\"108\" w:type=\"dxa\"/>"
                + "<w:bottom w:w=\"0\" w:type=\"dxa\"/><w:right w:w=\"108\" w:type=\"dxa\"/>"
                + "</w:tblCellMar></w:tblPr>"
                + "</w:style>"
                + "<w:style w:type=\"numbering\" w:default=\"1\" w:styleId=\"NoList\">"
                + "<w:name w:val=\"No List\"/><w:uiPriority w:val=\"99\"/><w:semiHidden/><w:unhideWhenUsed/>"
                + "</w:style>"
                + "</w:styles>";
    }

    static String settingsXml() {
        return settingsXml(false);
    }

    /** @param evenAndOddHeaders 홀수·짝수 쪽 머리말을 따로 쓸 때 */
    public static String settingsXml(boolean evenAndOddHeaders) {
        return XML_DECL + "<w:settings xmlns:w=\"" + NS_W + "\">"
                + "<w:zoom w:percent=\"100\"/>"
                + "<w:defaultTabStop w:val=\"800\"/>"
                // 스키마 순서상 defaultTabStop 뒤, characterSpacingControl 앞
                + (evenAndOddHeaders ? "<w:evenAndOddHeaders/>" : "")
                + "<w:characterSpacingControl w:val=\"compressPunctuation\"/>"
                + "<w:compat>"
                // 15 = Word 2013 이후 배치 방식. 낮추면 Word가 호환 모드로 열어 줄 배치가 달라짐
                + "<w:compatSetting w:name=\"compatibilityMode\" w:uri=\"http://schemas.microsoft.com/office/word\""
                + " w:val=\"15\"/>"
                + "</w:compat>"
                + "</w:settings>";
    }

    static String coreXml() {
        return XML_DECL + "<cp:coreProperties xmlns:cp=\"" + NS_CP + "\" xmlns:dc=\"" + NS_DC + "\""
                + " xmlns:dcterms=\"http://purl.org/dc/terms/\""
                + " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">"
                + "<dc:title></dc:title><dc:creator></dc:creator><cp:lastModifiedBy></cp:lastModifiedBy>"
                + "</cp:coreProperties>";
    }

    static String appXml() {
        return XML_DECL + "<Properties xmlns=\"" + NS_EP + "\""
                + " xmlns:vt=\"http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes\">"
                + "<DocSecurity>0</DocSecurity><Company></Company>"
                + "</Properties>";
    }
}

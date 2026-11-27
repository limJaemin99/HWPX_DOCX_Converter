package io.github.limjaemin99.hwpxdocx.internal.hwpx;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 빈 문단 하나가 있는 최소 HWPX 패키지 (OWPML, KS X 6101).
 * 기존 문서를 복사하지 않고 코드로 만든다. 작성자 등 저장 정보는 비워 둔다.
 */
public final class HwpxSkeleton {

    public static final String MIMETYPE = "application/hwp+zip";

    static final String NS_HEAD = "http://www.hancom.co.kr/hwpml/2011/head";
    static final String NS_PARA = "http://www.hancom.co.kr/hwpml/2011/paragraph";
    static final String NS_SECTION = "http://www.hancom.co.kr/hwpml/2011/section";
    static final String NS_CORE = "http://www.hancom.co.kr/hwpml/2011/core";

    // ZIP 항목 시각을 고정해 같은 입력이면 같은 바이트가 나오게 함 (1980-01-01은 ZIP 최소값)
    private static final long FIXED_TIME = 315532800000L + 12L * 3600 * 1000;

    private static final String XML_DECL = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>";

    private static final String NS_ALL =
            " xmlns:ha=\"http://www.hancom.co.kr/hwpml/2011/app\""
            + " xmlns:hp=\"" + NS_PARA + "\""
            + " xmlns:hs=\"" + NS_SECTION + "\""
            + " xmlns:hc=\"" + NS_CORE + "\""
            + " xmlns:hh=\"" + NS_HEAD + "\""
            + " xmlns:hpf=\"http://www.hancom.co.kr/schema/2011/hpf\""
            + " xmlns:opf=\"http://www.idpf.org/2007/opf/\"";

    private HwpxSkeleton() {
    }

    /** 패키지 안 경로 → 내용. 순서가 ZIP 항목 순서다 (mimetype이 맨 앞). */
    public static Map<String, byte[]> entries() {
        Map<String, byte[]> m = new LinkedHashMap<>();
        m.put("mimetype", MIMETYPE.getBytes(StandardCharsets.US_ASCII));
        m.put("version.xml", utf8(versionXml()));
        m.put("META-INF/container.xml", utf8(containerXml()));
        m.put("META-INF/manifest.xml", utf8(manifestXml()));
        m.put("Contents/content.hpf", utf8(contentHpf()));
        m.put("Contents/header.xml", utf8(headerXml()));
        m.put("Contents/section0.xml", utf8(sectionXml()));
        m.put("settings.xml", utf8(settingsXml()));
        return m;
    }

    /** 머리 정보·구역 하나를 바꿔 넣은 패키지 항목. */
    public static Map<String, byte[]> entries(String headerXml, String sectionXml) {
        Map<String, byte[]> m = entries();
        m.put("Contents/header.xml", utf8(headerXml));
        m.put("Contents/section0.xml", utf8(sectionXml));
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

    /** 패키지 항목을 순서대로 ZIP으로 쓴다 (mimetype은 맨 앞이어야 함). {@code out}은 닫지 않는다. */
    public static void write(Map<String, byte[]> entries, OutputStream out) throws IOException {
        ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8);
        for (Map.Entry<String, byte[]> e : entries.entrySet()) {
            byte[] data = e.getValue();
            ZipEntry z = new ZipEntry(e.getKey());
            z.setTime(FIXED_TIME);
            if ("mimetype".equals(e.getKey())) {
                // 한컴은 mimetype이 첫 항목·비압축이어야 HWPX로 알아봄
                CRC32 crc = new CRC32();
                crc.update(data);
                z.setMethod(ZipEntry.STORED);
                z.setSize(data.length);
                z.setCompressedSize(data.length);
                z.setCrc(crc.getValue());
            } else {
                z.setMethod(ZipEntry.DEFLATED);
            }
            zip.putNextEntry(z);
            zip.write(data);
            zip.closeEntry();
        }
        zip.finish();
    }

    private static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    static String versionXml() {
        // tagetApplication은 규격 표기 그대로 (철자 오류가 아님)
        return XML_DECL + "<hv:HCFVersion xmlns:hv=\"http://www.hancom.co.kr/hwpml/2011/version\""
                + " tagetApplication=\"WORDPROCESSOR\" major=\"5\" minor=\"1\" micro=\"0\" buildNumber=\"1\""
                + " os=\"1\" xmlVersion=\"1.4\" application=\"\" appVersion=\"\"/>";
    }

    static String containerXml() {
        return XML_DECL + "<ocf:container xmlns:ocf=\"urn:oasis:names:tc:opendocument:xmlns:container\""
                + " xmlns:hpf=\"http://www.hancom.co.kr/schema/2011/hpf\"><ocf:rootfiles>"
                + "<ocf:rootfile full-path=\"Contents/content.hpf\" media-type=\"application/hwpml-package+xml\"/>"
                + "</ocf:rootfiles></ocf:container>";
    }

    static String manifestXml() {
        return XML_DECL + "<odf:manifest xmlns:odf=\"urn:oasis:names:tc:opendocument:xmlns:manifest:1.0\"/>";
    }

    static String contentHpf() {
        return XML_DECL + "<opf:package" + NS_ALL + " version=\"\" unique-identifier=\"\" id=\"\">"
                + "<opf:metadata><opf:title/><opf:language>ko</opf:language>"
                + "<opf:meta name=\"creator\" content=\"text\"/>"
                + "<opf:meta name=\"subject\" content=\"text\"/>"
                + "<opf:meta name=\"description\" content=\"text\"/>"
                + "<opf:meta name=\"lastsaveby\" content=\"text\"/>"
                + "<opf:meta name=\"keyword\" content=\"text\"/>"
                + "</opf:metadata>"
                + "<opf:manifest>"
                + "<opf:item id=\"header\" href=\"Contents/header.xml\" media-type=\"application/xml\"/>"
                + "<opf:item id=\"section0\" href=\"Contents/section0.xml\" media-type=\"application/xml\"/>"
                + "<opf:item id=\"settings\" href=\"settings.xml\" media-type=\"application/xml\"/>"
                + "</opf:manifest>"
                + "<opf:spine>"
                + "<opf:itemref idref=\"header\" linear=\"yes\"/>"
                + "<opf:itemref idref=\"section0\" linear=\"yes\"/>"
                + "</opf:spine></opf:package>";
    }

    private static final String[] FONT_LANGS = {"HANGUL", "LATIN", "HANJA", "JAPANESE", "OTHER", "SYMBOL", "USER"};

    static String headerXml() {
        return headerXml(defaultRefList());
    }

    /** {@code refList}(hh:fontfaces ~ hh:styles)를 담은 header.xml. */
    public static String headerXml(String refList) {
        return XML_DECL + "<hh:head" + NS_ALL + " version=\"1.4\" secCnt=\"1\">"
                + "<hh:beginNum page=\"1\" footnote=\"1\" endnote=\"1\" pic=\"1\" tbl=\"1\" equation=\"1\"/>"
                + "<hh:refList>" + refList + "</hh:refList>"
                + "<hh:compatibleDocument targetProgram=\"HWP201X\"><hh:layoutCompatibility/></hh:compatibleDocument>"
                + "<hh:docOption><hh:linkinfo path=\"\" pageInherit=\"0\" footnoteInherit=\"0\"/></hh:docOption>"
                + "<hh:trackchageConfig flags=\"56\"/>"
                + "</hh:head>";
    }

    private static String defaultRefList() {
        StringBuilder b = new StringBuilder();

        b.append("<hh:fontfaces itemCnt=\"").append(FONT_LANGS.length).append("\">");
        for (String lang : FONT_LANGS) {
            b.append("<hh:fontface lang=\"").append(lang).append("\" fontCnt=\"1\">")
                    .append("<hh:font id=\"0\" face=\"함초롬바탕\" type=\"TTF\" isEmbedded=\"0\"/>")
                    .append("</hh:fontface>");
        }
        b.append("</hh:fontfaces>");

        b.append("<hh:borderFills itemCnt=\"1\"><hh:borderFill id=\"1\" threeD=\"0\" shadow=\"0\""
                + " centerLine=\"NONE\" breakCellSeparateLine=\"0\">"
                + "<hh:slash type=\"NONE\" Crooked=\"0\" isCounter=\"0\"/>"
                + "<hh:backSlash type=\"NONE\" Crooked=\"0\" isCounter=\"0\"/>");
        for (String side : new String[]{"leftBorder", "rightBorder", "topBorder", "bottomBorder", "diagonal"}) {
            b.append("<hh:").append(side).append(" type=\"NONE\" width=\"0.1 mm\" color=\"#000000\"/>");
        }
        b.append("</hh:borderFill></hh:borderFills>");

        b.append("<hh:charProperties itemCnt=\"1\"><hh:charPr id=\"0\" height=\"1000\" textColor=\"#000000\""
                + " shadeColor=\"none\" useFontSpace=\"0\" useKerning=\"0\" symMark=\"NONE\" borderFillIDRef=\"1\">"
                + langAttrs("fontRef", "0")
                + langAttrs("ratio", "100")
                + langAttrs("spacing", "0")
                + langAttrs("relSz", "100")
                + langAttrs("offset", "0")
                + "<hh:underline type=\"NONE\" shape=\"SOLID\" color=\"#000000\"/>"
                + "<hh:strikeout shape=\"NONE\" color=\"#000000\"/>"
                + "<hh:outline type=\"NONE\"/>"
                + "<hh:shadow type=\"NONE\" color=\"#B2B2B2\" offsetX=\"10\" offsetY=\"10\"/>"
                + "</hh:charPr></hh:charProperties>");

        b.append("<hh:tabProperties itemCnt=\"1\"><hh:tabPr id=\"0\" autoTabLeft=\"0\" autoTabRight=\"0\"/>"
                + "</hh:tabProperties>");

        b.append("<hh:paraProperties itemCnt=\"1\"><hh:paraPr id=\"0\" tabPrIDRef=\"0\" condense=\"0\""
                + " fontLineHeight=\"0\" snapToGrid=\"1\" suppressLineNumbers=\"0\" checked=\"0\">"
                + "<hh:align horizontal=\"JUSTIFY\" vertical=\"BASELINE\"/>"
                + "<hh:heading type=\"NONE\" idRef=\"0\" level=\"0\"/>"
                + "<hh:breakSetting breakLatinWord=\"KEEP_WORD\" breakNonLatinWord=\"KEEP_WORD\""
                + " widowOrphan=\"0\" keepWithNext=\"0\" keepLines=\"0\" pageBreakBefore=\"0\" lineWrap=\"BREAK\"/>"
                + "<hh:autoSpacing eAsianEng=\"0\" eAsianNum=\"0\"/>"
                + "<hh:margin>"
                + "<hc:intent value=\"0\" unit=\"HWPUNIT\"/>"
                + "<hc:left value=\"0\" unit=\"HWPUNIT\"/>"
                + "<hc:right value=\"0\" unit=\"HWPUNIT\"/>"
                + "<hc:prev value=\"0\" unit=\"HWPUNIT\"/>"
                + "<hc:next value=\"0\" unit=\"HWPUNIT\"/>"
                + "</hh:margin>"
                + "<hh:lineSpacing type=\"PERCENT\" value=\"160\" unit=\"HWPUNIT\"/>"
                + "<hh:border borderFillIDRef=\"1\" offsetLeft=\"0\" offsetRight=\"0\" offsetTop=\"0\""
                + " offsetBottom=\"0\" connect=\"0\" ignoreMargin=\"0\"/>"
                + "</hh:paraPr></hh:paraProperties>");

        b.append("<hh:styles itemCnt=\"1\"><hh:style id=\"0\" type=\"PARA\" name=\"바탕글\" engName=\"Normal\""
                + " paraPrIDRef=\"0\" charPrIDRef=\"0\" nextStyleIDRef=\"0\" langID=\"1042\" lockForm=\"0\"/>"
                + "</hh:styles>");

        return b.toString();
    }

    private static String langAttrs(String name, String v) {
        return "<hh:" + name + " hangul=\"" + v + "\" latin=\"" + v + "\" hanja=\"" + v + "\" japanese=\"" + v
                + "\" other=\"" + v + "\" symbol=\"" + v + "\" user=\"" + v + "\"/>";
    }

    static String sectionXml() {
        return sectionXml("<hp:p id=\"0\" paraPrIDRef=\"0\" styleIDRef=\"0\" pageBreak=\"0\" columnBreak=\"0\" merged=\"0\">"
                + secPrRun("0")
                + "<hp:run charPrIDRef=\"0\"><hp:t/></hp:run>"
                + "<hp:linesegarray><hp:lineseg textpos=\"0\" vertpos=\"0\" vertsize=\"1000\" textheight=\"1000\""
                + " baseline=\"850\" spacing=\"600\" horzpos=\"0\" horzsize=\"42520\" flags=\"393216\"/>"
                + "</hp:linesegarray>"
                + "</hp:p>");
    }

    /** 문단(hp:p 나열)을 담은 구역 XML. 첫 문단의 첫 런에 {@link #secPrRun} 이 있어야 한다. */
    public static String sectionXml(String paragraphs) {
        return XML_DECL + "<hs:sec" + NS_ALL + ">" + paragraphs + "</hs:sec>";
    }

    /** 구역 설정(A4 세로, 한컴 기본 여백)과 단 정의를 담은 런. 구역 첫 문단 맨 앞에 둔다. */
    public static String secPrRun(String charPrId) {
        return secPrRun(charPrId, PAGE_A4);
    }

    // A4 세로, 한컴 기본 여백 (HWPUNIT = 1/7200 인치)
    static final String PAGE_A4 = "<hp:pagePr landscape=\"WIDELY\" width=\"59528\" height=\"84186\" gutterType=\"LEFT_ONLY\">"
            + "<hp:margin header=\"4252\" footer=\"4252\" gutter=\"0\" left=\"8504\" right=\"8504\""
            + " top=\"5668\" bottom=\"4252\"/>"
            + "</hp:pagePr>";

    /** @param pagePr hp:pagePr 요소 전체 */
    public static String secPrRun(String charPrId, String pagePr) {
        return "<hp:run charPrIDRef=\"" + charPrId + "\">"
                + "<hp:secPr id=\"\" textDirection=\"HORIZONTAL\" spaceColumns=\"1134\" tabStop=\"8000\""
                + " tabStopVal=\"4000\" tabStopUnit=\"HWPUNIT\" outlineShapeIDRef=\"0\" memoShapeIDRef=\"0\""
                + " textVerticalWidthHead=\"0\" masterPageCnt=\"0\">"
                + "<hp:grid lineGrid=\"0\" charGrid=\"0\" wonggojiFormat=\"0\"/>"
                + "<hp:startNum pageStartsOn=\"BOTH\" page=\"0\" pic=\"0\" tbl=\"0\" equation=\"0\"/>"
                + "<hp:visibility hideFirstHeader=\"0\" hideFirstFooter=\"0\" hideFirstMasterPage=\"0\""
                + " border=\"SHOW_ALL\" fill=\"SHOW_ALL\" hideFirstPageNum=\"0\" hideFirstEmptyLine=\"0\""
                + " showLineNumber=\"0\"/>"
                + "<hp:lineNumberShape restartType=\"0\" countBy=\"0\" distance=\"0\" startNumber=\"0\"/>"
                + pagePr
                + "</hp:secPr>"
                + "<hp:ctrl><hp:colPr id=\"\" type=\"NEWSPAPER\" layout=\"LEFT\" colCount=\"1\" sameSz=\"1\""
                + " sameGap=\"0\"/></hp:ctrl>"
                + "</hp:run>";
    }

    static String settingsXml() {
        return XML_DECL + "<ha:HWPApplicationSetting xmlns:ha=\"http://www.hancom.co.kr/hwpml/2011/app\""
                + " xmlns:config=\"urn:oasis:names:tc:opendocument:xmlns:config:1.0\">"
                + "<ha:CaretPosition listIDRef=\"0\" paraIDRef=\"0\" pos=\"0\"/>"
                + "</ha:HWPApplicationSetting>";
    }
}

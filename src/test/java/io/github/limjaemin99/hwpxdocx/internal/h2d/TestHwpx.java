package io.github.limjaemin99.hwpxdocx.internal.h2d;

import io.github.limjaemin99.hwpxdocx.internal.hwpx.HwpxSkeleton;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** 시험용 HWPX: 빈 골격에 글자 모양·문단 모양·글꼴·문단을 바꿔 넣는다. 문장은 대한민국헌법에서. */
final class TestHwpx {

    static final String HH = "xmlns:hh=\"http://www.hancom.co.kr/hwpml/2011/head\"";

    private final Map<String, byte[]> entries = HwpxSkeleton.entries();

    String get(String name) {
        return new String(entries.get(name), StandardCharsets.UTF_8);
    }

    TestHwpx put(String name, String xml) {
        entries.put(name, xml.getBytes(StandardCharsets.UTF_8));
        return this;
    }

    private TestHwpx replaceBlock(String name, String tag, String inner, int count) {
        String xml = get(name);
        Matcher m = Pattern.compile("<hh:" + tag + " itemCnt=\"\\d+\">.*?</hh:" + tag + ">", Pattern.DOTALL).matcher(xml);
        if (!m.find()) {
            throw new IllegalStateException(tag + " 없음");
        }
        String rep = "<hh:" + tag + " itemCnt=\"" + count + "\">" + inner + "</hh:" + tag + ">";
        return put(name, xml.substring(0, m.start()) + rep + xml.substring(m.end()));
    }

    /** charPr 목록 전체를 바꿈. id 0은 골격의 바탕글이 쓰므로 반드시 넣는다. */
    TestHwpx charPrs(String... charPr) {
        return replaceBlock("Contents/header.xml", "charProperties", String.join("", charPr), charPr.length);
    }

    TestHwpx borderFills(String... borderFill) {
        return replaceBlock("Contents/header.xml", "borderFills", String.join("", borderFill), borderFill.length);
    }

    /** 네 변 같은 선(굵기 "0.12 mm" 등)과 채우기 색(null이면 없음). */
    static String borderFill(String id, String type, String width, String fill) {
        StringBuilder b = new StringBuilder("<hh:borderFill id=\"" + id + "\" threeD=\"0\" shadow=\"0\""
                + " centerLine=\"NONE\" breakCellSeparateLine=\"0\">"
                + "<hh:slash type=\"NONE\" Crooked=\"0\" isCounter=\"0\"/>"
                + "<hh:backSlash type=\"NONE\" Crooked=\"0\" isCounter=\"0\"/>");
        for (String side : new String[]{"leftBorder", "rightBorder", "topBorder", "bottomBorder"}) {
            b.append("<hh:").append(side).append(" type=\"").append(type).append("\" width=\"").append(width)
                    .append("\" color=\"#000000\"/>");
        }
        b.append("<hh:diagonal type=\"SOLID\" width=\"0.1 mm\" color=\"#000000\"/>");
        if (fill != null) {
            b.append("<hc:fillBrush><hc:winBrush faceColor=\"").append(fill)
                    .append("\" hatchColor=\"#999999\" alpha=\"0\"/></hc:fillBrush>");
        }
        return b.append("</hh:borderFill>").toString();
    }

    static String tbl(int rows, int cols, String attrs, String... trs) {
        return "<hp:tbl id=\"1\" zOrder=\"0\" numberingType=\"TABLE\" textWrap=\"TOP_AND_BOTTOM\" textFlow=\"BOTH_SIDES\""
                + " lock=\"0\" dropcapstyle=\"None\" pageBreak=\"CELL\" rowCnt=\"" + rows + "\" colCnt=\"" + cols + "\""
                + " cellSpacing=\"0\" noAdjust=\"0\" " + attrs + ">"
                + "<hp:sz width=\"40000\" widthRelTo=\"ABSOLUTE\" height=\"5000\" heightRelTo=\"ABSOLUTE\" protect=\"0\"/>"
                + "<hp:pos treatAsChar=\"1\" affectLSpacing=\"0\" flowWithText=\"1\" allowOverlap=\"0\" holdAnchorAndSO=\"0\""
                + " vertRelTo=\"PARA\" horzRelTo=\"COLUMN\" vertAlign=\"TOP\" horzAlign=\"LEFT\" vertOffset=\"0\" horzOffset=\"0\"/>"
                + "<hp:outMargin left=\"283\" right=\"283\" top=\"283\" bottom=\"283\"/>"
                + "<hp:inMargin left=\"510\" right=\"510\" top=\"141\" bottom=\"141\"/>"
                + String.join("", trs) + "</hp:tbl>";
    }

    static String tr(String... tcs) {
        return "<hp:tr>" + String.join("", tcs) + "</hp:tr>";
    }

    static String tc(int row, int col, int rowSpan, int colSpan, int width, String borderFill, String text) {
        return "<hp:tc name=\"\" header=\"" + (row == 0 ? 1 : 0) + "\" hasMargin=\"0\" protect=\"0\" editable=\"0\""
                + " dirty=\"0\" borderFillIDRef=\"" + borderFill + "\">"
                + "<hp:subList id=\"\" textDirection=\"HORIZONTAL\" lineWrap=\"BREAK\" vertAlign=\"CENTER\">"
                + para("0", run("0", t(text))) + "</hp:subList>"
                + "<hp:cellAddr colAddr=\"" + col + "\" rowAddr=\"" + row + "\"/>"
                + "<hp:cellSpan colSpan=\"" + colSpan + "\" rowSpan=\"" + rowSpan + "\"/>"
                + "<hp:cellSz width=\"" + width + "\" height=\"" + (1000 * rowSpan) + "\"/>"
                + "<hp:cellMargin left=\"510\" right=\"510\" top=\"141\" bottom=\"141\"/></hp:tc>";
    }

    TestHwpx paraPrs(String... paraPr) {
        return replaceBlock("Contents/header.xml", "paraProperties", String.join("", paraPr), paraPr.length);
    }

    /** 한글 글꼴 목록에 글꼴을 더함 (id는 1부터). */
    TestHwpx hangulFonts(String... faces) {
        StringBuilder b = new StringBuilder("<hh:fontface lang=\"HANGUL\" fontCnt=\"" + (faces.length + 1) + "\">")
                .append("<hh:font id=\"0\" face=\"함초롬바탕\" type=\"TTF\" isEmbedded=\"0\"/>");
        for (int i = 0; i < faces.length; i++) {
            b.append("<hh:font id=\"").append(i + 1).append("\" face=\"").append(faces[i])
                    .append("\" type=\"HFT\" isEmbedded=\"0\"/>");
        }
        b.append("</hh:fontface>");
        String xml = get("Contents/header.xml");
        xml = xml.replaceFirst("<hh:fontface lang=\"HANGUL\".*?</hh:fontface>", Matcher.quoteReplacement(b.toString()));
        return put("Contents/header.xml", xml);
    }

    /** 첫 문단(구역 설정이 든 빈 문단) 뒤에 문단을 붙임. */
    TestHwpx paragraphs(String... p) {
        String xml = get("Contents/section0.xml");
        int end = xml.lastIndexOf("</hs:sec>");
        return put("Contents/section0.xml", xml.substring(0, end) + String.join("", p) + xml.substring(end));
    }

    static String para(String paraPr, String... runs) {
        return "<hp:p id=\"0\" paraPrIDRef=\"" + paraPr + "\" styleIDRef=\"0\" pageBreak=\"0\" columnBreak=\"0\""
                + " merged=\"0\">" + String.join("", runs) + "</hp:p>";
    }

    static String run(String charPr, String inner) {
        return "<hp:run charPrIDRef=\"" + charPr + "\">" + inner + "</hp:run>";
    }

    static String t(String s) {
        return "<hp:t>" + s + "</hp:t>";
    }

    static String charPr(String id, int height, String attrs, String inner) {
        String colors = (attrs.contains("textColor=") ? "" : " textColor=\"#000000\"")
                + (attrs.contains("shadeColor=") ? "" : " shadeColor=\"none\"");
        return "<hh:charPr id=\"" + id + "\" height=\"" + height + "\"" + colors
                + " useFontSpace=\"0\" useKerning=\"0\" symMark=\"NONE\" borderFillIDRef=\"1\" " + attrs + ">"
                + "<hh:fontRef hangul=\"0\" latin=\"0\" hanja=\"0\" japanese=\"0\" other=\"0\" symbol=\"0\" user=\"0\"/>"
                + inner + "</hh:charPr>";
    }

    static String paraPr(String id, String inner) {
        return "<hh:paraPr id=\"" + id + "\" tabPrIDRef=\"0\" condense=\"0\" fontLineHeight=\"0\" snapToGrid=\"1\""
                + " suppressLineNumbers=\"0\" checked=\"0\">" + inner + "</hh:paraPr>";
    }

    static String margin(int intent, int left, int right, int prev, int next) {
        return "<hh:margin>"
                + "<hc:intent value=\"" + intent + "\" unit=\"HWPUNIT\"/>"
                + "<hc:left value=\"" + left + "\" unit=\"HWPUNIT\"/>"
                + "<hc:right value=\"" + right + "\" unit=\"HWPUNIT\"/>"
                + "<hc:prev value=\"" + prev + "\" unit=\"HWPUNIT\"/>"
                + "<hc:next value=\"" + next + "\" unit=\"HWPUNIT\"/>"
                + "</hh:margin>";
    }

    byte[] toBytes() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                z.putNextEntry(new ZipEntry(e.getKey()));
                z.write(e.getValue());
                z.closeEntry();
            }
        }
        return out.toByteArray();
    }
}

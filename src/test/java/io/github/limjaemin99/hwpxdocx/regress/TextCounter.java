package io.github.limjaemin99.hwpxdocx.regress;

import io.github.limjaemin99.hwpxdocx.internal.SafeXml;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 문서 패키지 안 본문 글자 수. 공백은 세지 않는다 (탭·줄바꿈을 옮기는 방식이 형식마다 달라서).
 * 머리말·꼬리말·각주도 센다. HWPX는 이것들이 구역 XML 안에 있어서, DOCX도 같은 범위로 맞춤.
 */
final class TextCounter {

    private static final String NS_HP = "http://www.hancom.co.kr/hwpml/2011/paragraph";
    private static final String NS_W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";

    private static final Pattern HWPX_PARTS = Pattern.compile("Contents/section\\d+\\.xml");
    private static final Pattern DOCX_PARTS =
            Pattern.compile("word/(document|header\\d*|footer\\d*|footnotes|endnotes)\\.xml");

    private TextCounter() {
    }

    static int count(byte[] pkg, boolean hwpx) throws IOException {
        Pattern parts = hwpx ? HWPX_PARTS : DOCX_PARTS;
        String ns = hwpx ? NS_HP : NS_W;
        int n = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(pkg))) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                if (parts.matcher(e.getName()).matches()) {
                    n += countPart(readAll(zip), e.getName(), ns);
                }
            }
        }
        return n;
    }

    private static int countPart(byte[] xml, String name, String ns) throws IOException {
        XMLStreamReader r = SafeXml.newStreamReader(new ByteArrayInputStream(xml), name);
        int n = 0;
        int inText = 0;
        try {
            while (r.hasNext()) {
                switch (r.next()) {
                    case XMLStreamConstants.START_ELEMENT:
                        if (isText(r, ns)) {
                            inText++;
                        }
                        break;
                    case XMLStreamConstants.END_ELEMENT:
                        if (isText(r, ns)) {
                            inText--;
                        }
                        break;
                    case XMLStreamConstants.CHARACTERS:
                    case XMLStreamConstants.CDATA:
                        if (inText > 0) {
                            n += nonSpace(r.getText());
                        }
                        break;
                    default:
                        break;
                }
            }
        } catch (XMLStreamException e) {
            throw new IOException(name + ": " + e.getMessage(), e);
        } finally {
            try {
                r.close();
            } catch (XMLStreamException ignored) {
                // 다 읽은 뒤라 결과에 영향 없음
            }
        }
        return n;
    }

    private static boolean isText(XMLStreamReader r, String ns) {
        return "t".equals(r.getLocalName()) && ns.equals(r.getNamespaceURI());
    }

    static int nonSpace(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            if (!Character.isWhitespace(cp) && !Character.isSpaceChar(cp)) {
                n++;
            }
            i += Character.charCount(cp);
        }
        return n;
    }

    static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int k;
        while ((k = in.read(buf)) > 0) {
            out.write(buf, 0, k);
        }
        return out.toByteArray();
    }
}

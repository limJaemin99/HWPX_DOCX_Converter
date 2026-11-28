package io.github.limjaemin99.hwpxdocx.internal.h2d;

import io.github.limjaemin99.hwpxdocx.ConvertException;
import io.github.limjaemin99.hwpxdocx.internal.SafeXml;
import io.github.limjaemin99.hwpxdocx.internal.Xml;
import io.github.limjaemin99.hwpxdocx.internal.hwpx.HwpxPackage;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import javax.xml.XMLConstants;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 변환에서 건너뛴 요소가 든 본문 문단의 원본 hp:p를 모아 DOCX 사용자 정의 XML 파트로 만든다.
 * 원본 조각은 XML 글자로 넣는다. Word가 파트 안 XML을 해석하거나 이름공간을 바꾸지 않게 하려는 것.
 */
final class OriginalArchive {

    static final String NS = "urn:io.github.limjaemin99:hwpx-docx:original:1";
    /** Word에서 '_'로 시작하는 책갈피는 숨은 책갈피라 사용자에게 보이지 않음. */
    static final String BOOKMARK_PREFIX = "_hwpx_";

    private static final Pattern W_T = Pattern.compile("<w:t(?:\\s[^>]*)?>([^<]*)</w:t>");
    private static final Pattern XML_DECL = Pattern.compile("^\\s*<\\?xml[^>]*\\?>\\s*");

    private final StringBuilder items = new StringBuilder();
    private final StringBuilder bins = new StringBuilder();
    private final Set<String> binIds = new HashSet<String>();
    private int count;

    boolean isEmpty() {
        return count == 0;
    }

    int size() {
        return count;
    }

    /**
     * 원본 문단 하나를 담고 책갈피 번호를 돌려준다.
     *
     * @param docxText 이 문단에서 나온 Word 문단들의 글자 (되살릴 때 고쳐졌는지 비교)
     */
    int add(Element p, String docxText, HwpxPackage pkg) throws ConvertException {
        keepBinData(p, pkg);
        int n = ++count;
        items.append("<p n=\"").append(n).append("\" ns=\"").append(Xml.attr(namespaces(p))).append("\" text=\"")
                .append(Xml.attr(docxText)).append("\">").append(Xml.text(serialize(p))).append("</p>");
        return n;
    }

    /** 조각이 가리키는 그림 등 BinData 파일. 되살릴 때 새 HWPX에 다시 넣음. 파일이 없는 참조는 되살릴 때 걸러짐. */
    private void keepBinData(Element p, HwpxPackage pkg) {
        org.w3c.dom.NodeList all = p.getElementsByTagName("*");
        for (int i = -1; i < all.getLength(); i++) {
            Element e = i < 0 ? p : (Element) all.item(i);
            String id = e.getAttribute("binaryItemIDRef");
            if (id.isEmpty() || !binIds.add(id)) {
                continue;
            }
            String[] item = pkg.item(id);
            byte[] data = item == null ? null : pkg.get(item[0]);
            if (data != null) {
                bins.append("<bin id=\"").append(Xml.attr(id)).append("\" href=\"").append(Xml.attr(item[0]))
                        .append("\" type=\"").append(Xml.attr(item[1])).append("\">")
                        .append(Base64.getEncoder().encodeToString(data)).append("</bin>");
            }
        }
    }

    /** 사용자 정의 XML 파트 본문. 원본 header.xml도 함께 둠 (되살린 조각이 가리키는 모양 정의를 옮겨 붙일 때 씀). */
    String itemXml(byte[] headerXml) {
        String header = headerXml == null ? "" : XML_DECL.matcher(new String(headerXml, StandardCharsets.UTF_8)).replaceFirst("");
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><original xmlns=\"" + NS
                + "\" version=\"1\"><header>" + Xml.text(header) + "</header>" + items + bins + "</original>";
    }

    /** itemID는 내용에서 만든 이름 기반 UUID라 같은 입력이면 같은 결과가 나옴. */
    static String itemPropsXml(String itemXml) {
        String id = UUID.nameUUIDFromBytes(itemXml.getBytes(StandardCharsets.UTF_8)).toString().toUpperCase(Locale.ROOT);
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?><ds:datastoreItem ds:itemID=\"{" + id
                + "}\" xmlns:ds=\"http://schemas.openxmlformats.org/officeDocument/2006/customXml\">"
                + "<ds:schemaRefs><ds:schemaRef ds:uri=\"" + NS + "\"/></ds:schemaRefs></ds:datastoreItem>";
    }

    /** Word 문단 XML 조각의 글자. w:t 안 글자만 이어 붙임. */
    static String docxText(CharSequence xml) {
        StringBuilder b = new StringBuilder();
        Matcher m = W_T.matcher(xml);
        while (m.find()) {
            b.append(unescape(m.group(1)));
        }
        return b.toString();
    }

    private static String unescape(String s) {
        if (s.indexOf('&') < 0) {
            return s;
        }
        return s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
                .replace("&amp;", "&");
    }

    /** 조각을 따로 읽을 수 있게 조상까지 포함한 이름공간 선언을 모음. */
    private static String namespaces(Element p) {
        Map<String, String> decl = new TreeMap<String, String>();
        for (Node n = p; n instanceof Element; n = n.getParentNode()) {
            NamedNodeMap attrs = n.getAttributes();
            for (int i = 0; i < attrs.getLength(); i++) {
                Attr a = (Attr) attrs.item(i);
                if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(a.getNamespaceURI()) && !decl.containsKey(a.getName())) {
                    decl.put(a.getName(), a.getValue());
                }
            }
        }
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, String> e : decl.entrySet()) {
            b.append(b.length() == 0 ? "" : " ").append(e.getKey()).append("=\"").append(Xml.attr(e.getValue())).append('"');
        }
        return b.toString();
    }

    private static String serialize(Element p) throws ConvertException {
        try {
            Transformer t = SafeXml.newTransformer();
            t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            t.setOutputProperty(OutputKeys.INDENT, "no");
            StringWriter w = new StringWriter();
            t.transform(new DOMSource(p), new StreamResult(w));
            return w.toString();
        } catch (TransformerException e) {
            throw new ConvertException("원본 문단을 보관할 수 없음 (" + e.getMessage() + ")", e);
        }
    }
}

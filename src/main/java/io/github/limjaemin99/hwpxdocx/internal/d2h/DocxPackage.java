package io.github.limjaemin99.hwpxdocx.internal.d2h;

import io.github.limjaemin99.hwpxdocx.ConvertException;
import io.github.limjaemin99.hwpxdocx.internal.SafeXml;
import io.github.limjaemin99.hwpxdocx.internal.ZipReader;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/** 읽어 들인 DOCX 패키지. 관계(rels)로 본문 파트와 그 딸린 파트를 찾는다. */
final class DocxPackage {

    static final String NS_PKG_REL = "http://schemas.openxmlformats.org/package/2006/relationships";
    private static final String OFFICE_DOCUMENT =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument";
    // Strict 형식(ISO/IEC 29500 Strict)은 관계 이름공간이 다름
    private static final String OFFICE_DOCUMENT_STRICT = "http://purl.oclc.org/ooxml/officeDocument/relationships/officeDocument";

    private final Map<String, byte[]> entries;
    final String mainPart;

    private DocxPackage(Map<String, byte[]> entries, String mainPart) {
        this.entries = entries;
        this.mainPart = mainPart;
    }

    static DocxPackage read(InputStream in) throws IOException {
        Map<String, byte[]> m = ZipReader.unzip(in);
        if (!m.containsKey("[Content_Types].xml")) {
            throw new ConvertException("DOCX가 아님: [Content_Types].xml이 없음");
        }
        String main = null;
        if (m.containsKey("_rels/.rels")) {
            for (Rel r : rels(m, "", "_rels/.rels").values()) {
                if (OFFICE_DOCUMENT.equals(r.type) || OFFICE_DOCUMENT_STRICT.equals(r.type)) {
                    main = r.target;
                }
            }
        }
        if (main == null && m.containsKey("word/document.xml")) {
            main = "word/document.xml";
        }
        if (main == null || !m.containsKey(main)) {
            throw new ConvertException("DOCX가 아님: 본문 파트(word/document.xml)가 없음");
        }
        return new DocxPackage(m, main);
    }

    static final class Rel {
        final String type;
        final String target;
        final boolean external;

        Rel(String type, String target, boolean external) {
            this.type = type;
            this.target = target;
            this.external = external;
        }

        /** 관계 이름 (…/relationships/image → image). */
        String kind() {
            return type.substring(type.lastIndexOf('/') + 1);
        }
    }

    /** {@code part}에 딸린 관계: Id → 대상(패키지 안 경로로 풀어 둠). 없으면 빈 지도. */
    Map<String, Rel> rels(String part) throws ConvertException {
        int slash = part.lastIndexOf('/');
        String dir = slash < 0 ? "" : part.substring(0, slash + 1);
        String relsPath = dir + "_rels/" + part.substring(slash + 1) + ".rels";
        if (!entries.containsKey(relsPath)) {
            return new HashMap<String, Rel>();
        }
        return rels(entries, dir, relsPath);
    }

    private static Map<String, Rel> rels(Map<String, byte[]> m, String dir, String relsPath) throws ConvertException {
        Document d = SafeXml.parse(new ByteArrayInputStream(m.get(relsPath)), relsPath);
        Map<String, Rel> out = new HashMap<String, Rel>();
        NodeList list = d.getElementsByTagNameNS(NS_PKG_REL, "Relationship");
        for (int i = 0; i < list.getLength(); i++) {
            Element r = (Element) list.item(i);
            boolean external = "External".equals(r.getAttribute("TargetMode"));
            String target = r.getAttribute("Target");
            out.put(r.getAttribute("Id"), new Rel(r.getAttribute("Type"),
                    external ? target : resolve(dir, target), external));
        }
        return out;
    }

    /** rels 기준 폴더 + 상대 경로 → 패키지 안 경로. "/"로 시작하면 패키지 뿌리 기준. */
    static String resolve(String dir, String target) {
        String path = target.startsWith("/") ? target.substring(1) : dir + target;
        Deque<String> parts = new ArrayDeque<String>();
        for (String s : path.split("/")) {
            if (s.isEmpty() || ".".equals(s)) {
                continue;
            }
            if ("..".equals(s)) {
                if (!parts.isEmpty()) {
                    parts.removeLast();
                }
            } else {
                parts.addLast(s);
            }
        }
        return String.join("/", parts);
    }

    boolean has(String name) {
        return entries.containsKey(name);
    }

    byte[] get(String name) {
        return entries.get(name);
    }

    Document parse(String name) throws ConvertException {
        byte[] b = entries.get(name);
        if (b == null) {
            throw new ConvertException("DOCX에 " + name + "이 없음");
        }
        return SafeXml.parse(new ByteArrayInputStream(b), name);
    }

    /** 본문에 딸린 관계 가운데 {@code kind}(styles·theme 등)인 첫 파트. 없으면 null. */
    String related(String kind) throws ConvertException {
        for (Rel r : rels(mainPart).values()) {
            if (!r.external && kind.equals(r.kind()) && entries.containsKey(r.target)) {
                return r.target;
            }
        }
        return null;
    }
}

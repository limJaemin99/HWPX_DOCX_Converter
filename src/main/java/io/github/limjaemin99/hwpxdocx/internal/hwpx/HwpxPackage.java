package io.github.limjaemin99.hwpxdocx.internal.hwpx;

import io.github.limjaemin99.hwpxdocx.ConvertException;
import io.github.limjaemin99.hwpxdocx.internal.SafeXml;
import io.github.limjaemin99.hwpxdocx.internal.ZipReader;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 읽어 들인 HWPX 패키지. 항목 내용을 메모리에 들고, 구역 XML은 본문 순서대로 돌려준다. */
public final class HwpxPackage {

    static final String NS_OPF = "http://www.idpf.org/2007/opf/";


    private static final Pattern SECTION = Pattern.compile("Contents/section(\\d+)\\.xml");

    private final Map<String, byte[]> entries;
    private final List<String> sections;
    private final Map<String, String[]> items;

    private HwpxPackage(Map<String, byte[]> entries, List<String> sections, Map<String, String[]> items) {
        this.entries = entries;
        this.sections = sections;
        this.items = items;
    }

    public static HwpxPackage read(InputStream in) throws IOException {
        Map<String, byte[]> m = ZipReader.unzip(in);
        byte[] mime = m.get("mimetype");
        if (mime != null && !HwpxSkeleton.MIMETYPE.equals(new String(mime, StandardCharsets.US_ASCII).trim())) {
            throw new ConvertException("HWPX가 아님: mimetype이 " + new String(mime, StandardCharsets.US_ASCII));
        }
        if (!m.containsKey("Contents/header.xml") || !m.containsKey("Contents/content.hpf")) {
            throw new ConvertException("HWPX가 아님: Contents/header.xml 또는 content.hpf가 없음");
        }
        List<String> sections = spineSections(m);
        if (sections.isEmpty()) {
            throw new ConvertException("HWPX에 구역(Contents/section*.xml)이 없음");
        }
        return new HwpxPackage(m, sections, manifest(m));
    }

    static Map<String, byte[]> unzip(InputStream in, int maxEntries, long maxTotal) throws IOException {
        return ZipReader.unzip(in, maxEntries, maxTotal);
    }

    // content.hpf spine 순서. spine에 구역이 없으면 파일 번호 순
    private static List<String> spineSections(Map<String, byte[]> m) throws ConvertException {
        Document hpf = SafeXml.parse(new ByteArrayInputStream(m.get("Contents/content.hpf")), "Contents/content.hpf");
        Map<String, String> hrefById = new HashMap<String, String>();
        NodeList items = hpf.getElementsByTagNameNS(NS_OPF, "item");
        for (int i = 0; i < items.getLength(); i++) {
            Element it = (Element) items.item(i);
            hrefById.put(it.getAttribute("id"), it.getAttribute("href"));
        }
        List<String> list = new ArrayList<String>();
        NodeList refs = hpf.getElementsByTagNameNS(NS_OPF, "itemref");
        for (int i = 0; i < refs.getLength(); i++) {
            String href = hrefById.get(((Element) refs.item(i)).getAttribute("idref"));
            if (href != null && SECTION.matcher(href).matches() && m.containsKey(href) && !list.contains(href)) {
                list.add(href);
            }
        }
        if (list.isEmpty()) {
            List<Integer> nums = new ArrayList<Integer>();
            for (String name : m.keySet()) {
                Matcher mt = SECTION.matcher(name);
                if (mt.matches()) {
                    nums.add(Integer.valueOf(mt.group(1)));
                }
            }
            Collections.sort(nums);
            for (Integer n : nums) {
                list.add("Contents/section" + n + ".xml");
            }
        }
        return list;
    }

    // content.hpf manifest: id → {href, media-type}
    private static Map<String, String[]> manifest(Map<String, byte[]> m) throws ConvertException {
        Document hpf = SafeXml.parse(new ByteArrayInputStream(m.get("Contents/content.hpf")), "Contents/content.hpf");
        Map<String, String[]> items = new HashMap<String, String[]>();
        NodeList list = hpf.getElementsByTagNameNS(NS_OPF, "item");
        for (int i = 0; i < list.getLength(); i++) {
            Element it = (Element) list.item(i);
            items.put(it.getAttribute("id"), new String[]{it.getAttribute("href"), it.getAttribute("media-type")});
        }
        return items;
    }

    /** manifest 항목 {href, media-type}. 없으면 null. */
    public String[] item(String id) {
        String[] v = items.get(id);
        return v == null ? null : v.clone();
    }

    public List<String> sections() {
        return Collections.unmodifiableList(sections);
    }

    public byte[] get(String name) {
        return entries.get(name);
    }

    public Document parse(String name) throws ConvertException {
        byte[] b = entries.get(name);
        if (b == null) {
            throw new ConvertException("HWPX에 " + name + "이 없음");
        }
        return SafeXml.parse(new ByteArrayInputStream(b), name);
    }
}

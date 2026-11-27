package io.github.limjaemin99.hwpxdocx.internal.h2d;

import io.github.limjaemin99.hwpxdocx.internal.docx.DocxSkeleton;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** 골격 위에 더하는 DOCX 파트(그림 파일 등)와 그 관계·콘텐츠 형식을 모은다. */
final class DocxParts {

    private static final Map<String, String> IMAGE_TYPES = new HashMap<String, String>();

    static {
        IMAGE_TYPES.put("png", "image/png");
        IMAGE_TYPES.put("jpg", "image/jpeg");
        IMAGE_TYPES.put("jpeg", "image/jpeg");
        IMAGE_TYPES.put("gif", "image/gif");
        IMAGE_TYPES.put("bmp", "image/bmp");
        IMAGE_TYPES.put("tif", "image/tiff");
        IMAGE_TYPES.put("tiff", "image/tiff");
        IMAGE_TYPES.put("emf", "image/x-emf");
        IMAGE_TYPES.put("wmf", "image/x-wmf");
    }

    private final Map<String, byte[]> parts = new LinkedHashMap<String, byte[]>();
    private final List<String[]> rels = new ArrayList<String[]>();
    private final Map<String, String> defaults = new TreeMap<String, String>();
    private final Map<String, String> overrides = new LinkedHashMap<String, String>();
    private final Map<String, String> imageRelBySource = new HashMap<String, String>();
    private int nextRel = DocxSkeleton.FIRST_FREE_REL;
    private int nextImage = 1;

    /** Word가 읽는 그림 형식의 확장자인지. */
    static boolean isImageExtension(String ext) {
        return IMAGE_TYPES.containsKey(ext.toLowerCase(Locale.ROOT));
    }

    /**
     * 그림 파일을 word/media에 넣고 관계 ID를 돌려준다. 같은 원본은 한 번만 넣는다.
     *
     * @param source 원본 안에서 그림을 가리키는 이름 (같은 그림을 여러 번 쓸 때 묶는 열쇠)
     */
    String image(String source, byte[] data, String ext) {
        String rid = imageRelBySource.get(source);
        if (rid != null) {
            return rid;
        }
        rid = "rId" + nextRel++;
        rels.add(new String[]{rid, "image", media(source, data, ext)});
        imageRelBySource.put(source, rid);
        return rid;
    }

    private final Map<String, String> mediaBySource = new HashMap<String, String>();

    /** 그림 파일을 word/media에 한 번만 넣고 word/ 기준 경로(media/imageN.확장자)를 돌려준다. */
    String media(String source, byte[] data, String ext) {
        String path = mediaBySource.get(source);
        if (path == null) {
            String e = ext.toLowerCase(Locale.ROOT);
            path = "media/image" + nextImage++ + "." + e;
            parts.put("word/" + path, data);
            defaults.put(e, IMAGE_TYPES.get(e));
            mediaBySource.put(source, path);
        }
        return path;
    }

    private boolean evenAndOddHeaders;
    private int nextHeader = 1;
    private int nextFooter = 1;

    /**
     * 머리말(hdr)·꼬리말(ftr) 파트를 더하고 본문 쪽 관계 ID를 돌려준다.
     *
     * @param partRels 이 파트 안에서 쓰는 관계 (그림 등). 비어 있지 않으면 파트 옆에 관계 파일을 만든다
     */
    String headerFooter(String tag, String body, List<String[]> partRels) {
        boolean header = "hdr".equals(tag);
        String name = (header ? "header" + nextHeader++ : "footer" + nextFooter++) + ".xml";
        parts.put("word/" + name, utf8(DocxSkeleton.headerFooterXml(tag, body)));
        if (!partRels.isEmpty()) {
            parts.put("word/_rels/" + name + ".rels", utf8(DocxSkeleton.relationshipsXml(partRels)));
        }
        overrides.put("/word/" + name, "application/vnd.openxmlformats-officedocument.wordprocessingml."
                + (header ? "header" : "footer") + "+xml");
        String rid = "rId" + nextRel++;
        rels.add(new String[]{rid, header ? "header" : "footer", name});
        return rid;
    }

    void evenAndOddHeaders() {
        evenAndOddHeaders = true;
    }

    Map<String, byte[]> entries(String documentXml) {
        Map<String, byte[]> m = DocxSkeleton.entries();
        m.put("[Content_Types].xml", utf8(DocxSkeleton.contentTypesXml(defaults, overrides)));
        m.put("word/settings.xml", utf8(DocxSkeleton.settingsXml(evenAndOddHeaders)));
        m.put("word/document.xml", utf8(documentXml));
        m.put("word/_rels/document.xml.rels", utf8(DocxSkeleton.documentRels(rels)));
        m.putAll(parts);
        return m;
    }

    private static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}

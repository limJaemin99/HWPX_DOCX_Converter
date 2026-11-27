package io.github.limjaemin99.hwpxdocx.internal.d2h;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.HashMap;
import java.util.Map;

/**
 * Word 글자·문단 속성을 상속 순서대로 덮어쓰며 모은다 (문서 기본값 → 스타일 → 직접 서식).
 * 속성 이름은 w: 요소 이름을 그대로 쓴다. 값은 w:val 등 문자열, 켜고 끄는 속성은 "1"·"0".
 */
final class Props {

    static final String NS_W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";

    private static final String[] RUN_TOGGLES = {"b", "i", "strike", "dstrike", "vanish", "caps", "smallCaps",
            "outline", "shadow", "emboss", "imprint"};
    private static final String[] RUN_VALUES = {"sz", "color", "highlight", "w", "spacing", "position", "vertAlign"};
    private static final String[] PARA_TOGGLES = {"keepNext", "keepLines", "pageBreakBefore", "widowControl",
            "wordWrap", "autoSpaceDE", "autoSpaceDN", "snapToGrid"};

    final Map<String, String> v = new HashMap<String, String>();

    Props copy() {
        Props p = new Props();
        p.v.putAll(v);
        return p;
    }

    String get(String key) {
        return v.get(key);
    }

    String get(String key, String def) {
        String s = v.get(key);
        return s == null ? def : s;
    }

    int num(String key, int def) {
        String s = v.get(key);
        if (s == null) {
            return def;
        }
        try {
            return (int) Math.round(Double.parseDouble(s));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    boolean on(String key) {
        return "1".equals(v.get(key));
    }

    /** 켜고 끄는 속성. 없으면 {@code def}. */
    boolean on(String key, boolean def) {
        String s = v.get(key);
        return s == null ? def : "1".equals(s);
    }

    static Element child(Element parent, String local) {
        if (parent == null) {
            return null;
        }
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element && local.equals(n.getLocalName()) && NS_W.equals(n.getNamespaceURI())) {
                return (Element) n;
            }
        }
        return null;
    }

    static String attr(Element e, String name) {
        if (e == null || !e.hasAttributeNS(NS_W, name)) {
            return null;
        }
        return e.getAttributeNS(NS_W, name);
    }

    // w:val이 없으면 켬. 0·false·off면 끔
    private static String toggle(Element e) {
        String val = attr(e, "val");
        return val == null || !("0".equals(val) || "false".equals(val) || "off".equals(val)) ? "1" : "0";
    }

    /** w:rPr를 덮어씀. */
    Props applyRun(Element rPr) {
        if (rPr == null) {
            return this;
        }
        Element f = child(rPr, "rFonts");
        if (f != null) {
            for (String k : new String[]{"ascii", "hAnsi", "eastAsia"}) {
                String name = attr(f, k);
                String theme = attr(f, k + "Theme");
                // 테마 글꼴이 이름 지정보다 우선
                if (theme != null) {
                    v.put(k + "Theme", theme);
                    v.remove(k);
                } else if (name != null) {
                    v.put(k, name);
                    v.remove(k + "Theme");
                }
            }
        }
        for (String k : RUN_TOGGLES) {
            Element e = child(rPr, k);
            if (e != null) {
                v.put(k, toggle(e));
            }
        }
        for (String k : RUN_VALUES) {
            String val = attr(child(rPr, k), "val");
            if (val != null) {
                v.put(k, val);
            }
        }
        Element u = child(rPr, "u");
        if (u != null) {
            v.put("u", attr(u, "val") == null ? "single" : attr(u, "val"));
            if (attr(u, "color") != null) {
                v.put("uColor", attr(u, "color"));
            }
        }
        Element shd = child(rPr, "shd");
        if (shd != null && attr(shd, "fill") != null) {
            v.put("shd", attr(shd, "fill"));
        }
        return this;
    }

    /** w:pPr를 덮어씀. */
    Props applyPara(Element pPr) {
        if (pPr == null) {
            return this;
        }
        String jc = attr(child(pPr, "jc"), "val");
        if (jc != null) {
            v.put("jc", jc);
        }
        Element ind = child(pPr, "ind");
        if (ind != null) {
            putFirst(ind, "left", "left", "start");
            putFirst(ind, "right", "right", "end");
            if (attr(ind, "hanging") != null) {
                v.put("hanging", attr(ind, "hanging"));
                v.remove("firstLine");
            } else if (attr(ind, "firstLine") != null) {
                v.put("firstLine", attr(ind, "firstLine"));
                v.remove("hanging");
            }
        }
        Element sp = child(pPr, "spacing");
        if (sp != null) {
            for (String k : new String[]{"before", "after", "line", "lineRule"}) {
                if (attr(sp, k) != null) {
                    v.put(k, attr(sp, k));
                }
            }
        }
        for (String k : PARA_TOGGLES) {
            Element e = child(pPr, k);
            if (e != null) {
                v.put(k, toggle(e));
            }
        }
        Element numPr = child(pPr, "numPr");
        if (numPr != null) {
            String numId = attr(child(numPr, "numId"), "val");
            if (numId != null) {
                v.put("numId", numId);
            }
        }
        return this;
    }

    private void putFirst(Element e, String key, String... names) {
        for (String n : names) {
            if (attr(e, n) != null) {
                v.put(key, attr(e, n));
                return;
            }
        }
    }
}

package io.github.limjaemin99.hwpxdocx.internal;

/** 문자열로 XML을 만들 때 쓰는 이스케이프. XML 1.0에 넣을 수 없는 제어 문자는 버린다. */
public final class Xml {

    private Xml() {
    }

    public static String text(String s) {
        return escape(s, false);
    }

    public static String attr(String s) {
        return escape(s, true);
    }

    private static String escape(String s, boolean attr) {
        StringBuilder b = null;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            String rep = null;
            if (c == '&') {
                rep = "&amp;";
            } else if (c == '<') {
                rep = "&lt;";
            } else if (c == '>') {
                rep = "&gt;";
            } else if (attr && c == '"') {
                rep = "&quot;";
            } else if (!allowed(s, i)) {
                rep = "";
            }
            if (rep != null) {
                if (b == null) {
                    b = new StringBuilder(s.length() + 16).append(s, 0, i);
                }
                b.append(rep);
            } else if (b != null) {
                b.append(c);
            }
        }
        return b == null ? s : b.toString();
    }

    private static boolean allowed(String s, int i) {
        char c = s.charAt(i);
        if (c == '\t' || c == '\n' || c == '\r') {
            return true;
        }
        if (c < 0x20 || c == 0xFFFE || c == 0xFFFF) {
            return false;
        }
        if (Character.isHighSurrogate(c)) {
            return i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1));
        }
        if (Character.isLowSurrogate(c)) {
            return i > 0 && Character.isHighSurrogate(s.charAt(i - 1));
        }
        return true;
    }
}

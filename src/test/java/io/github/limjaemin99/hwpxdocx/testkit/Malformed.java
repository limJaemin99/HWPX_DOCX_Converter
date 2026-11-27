package io.github.limjaemin99.hwpxdocx.testkit;

import io.github.limjaemin99.hwpxdocx.ConvertException;
import io.github.limjaemin99.hwpxdocx.HwpxDocxConverter;
import io.github.limjaemin99.hwpxdocx.internal.ZipReader;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 깨진 입력 시험: 속성값을 바꾸거나 XML을 자르거나 파트를 빼서 변환해 본다.
 * 정상 변환 또는 {@link ConvertException}만 허용한다. 씨앗이 정해져 있어 매번 같은 입력이 나온다.
 */
public final class Malformed {

    private static final String[] VALUES = {
            "", "-1", "abc", "99999999999", "1e9", "0", "NaN", "-2147483649", "2147483647", "#", "1.5", " 3 ", "65536"};
    private static final Pattern ATTR = Pattern.compile("(\\s[\\w:]+=\")([^\"]*)(\")");

    private Malformed() {
    }

    /** @return 정상 변환된 횟수 */
    public static int run(byte[] seedDocument, boolean hwpx, long seed, int iterations) throws IOException {
        Map<String, byte[]> base = ZipReader.unzip(new ByteArrayInputStream(seedDocument));
        List<String> xmls = new ArrayList<String>();
        for (String n : base.keySet()) {
            if (n.endsWith(".xml") || n.endsWith(".rels") || n.endsWith(".hpf")) {
                xmls.add(n);
            }
        }
        Random rnd = new Random(seed);
        int ok = 0;
        for (int i = 0; i < iterations; i++) {
            Map<String, byte[]> pkg = new LinkedHashMap<String, byte[]>(base);
            String what = mutate(pkg, xmls, rnd);
            byte[] in = zip(pkg);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                if (hwpx) {
                    TestConvert.hwpxToDocx(new ByteArrayInputStream(in), out, null);
                } else {
                    HwpxDocxConverter.docxToHwpx(new ByteArrayInputStream(in), out, null);
                }
                ok++;
            } catch (ConvertException expected) {
                // 깨진 입력을 알리는 정상 경로
            } catch (Throwable t) {
                AssertionError e = new AssertionError("씨앗 " + seed + " " + i + "번째 (" + what + "): " + t);
                e.initCause(t);
                throw e;
            }
        }
        return ok;
    }

    private static String mutate(Map<String, byte[]> pkg, List<String> xmls, Random rnd) {
        int kind = rnd.nextInt(20);
        if (kind == 0) {
            List<String> names = new ArrayList<String>(pkg.keySet());
            String n = names.get(rnd.nextInt(names.size()));
            pkg.remove(n);
            return "파트 뺌 " + n;
        }
        String n = xmls.get(rnd.nextInt(xmls.size()));
        byte[] b = pkg.get(n);
        if (kind == 1) {
            int cut = rnd.nextInt(Math.max(1, b.length));
            pkg.put(n, Arrays.copyOf(b, cut));
            return n + " " + cut + "바이트에서 자름";
        }
        String xml = new String(b, StandardCharsets.UTF_8);
        List<int[]> spots = new ArrayList<int[]>();
        Matcher m = ATTR.matcher(xml);
        while (m.find()) {
            spots.add(new int[]{m.start(2), m.end(2)});
        }
        if (spots.isEmpty()) {
            return n + " 그대로";
        }
        StringBuilder what = new StringBuilder(n);
        int times = 1 + rnd.nextInt(3);
        int[][] picked = new int[times][];
        String[] values = new String[times];
        for (int t = 0; t < times; t++) {
            picked[t] = spots.get(rnd.nextInt(spots.size()));
            values[t] = VALUES[rnd.nextInt(VALUES.length)];
        }
        // 뒤에서부터 바꿔야 앞의 위치가 그대로임
        Integer[] order = new Integer[times];
        for (int t = 0; t < times; t++) {
            order[t] = t;
        }
        Arrays.sort(order, (x, y) -> picked[y][0] - picked[x][0]);
        StringBuilder s = new StringBuilder(xml);
        int last = Integer.MAX_VALUE;
        for (int t : order) {
            if (picked[t][0] >= last) {
                continue;
            }
            what.append(" [").append(xml, picked[t][0], picked[t][1]).append(" → ").append(values[t]).append(']');
            s.replace(picked[t][0], picked[t][1], values[t]);
            last = picked[t][0];
        }
        pkg.put(n, s.toString().getBytes(StandardCharsets.UTF_8));
        return what.toString();
    }

    private static byte[] zip(Map<String, byte[]> m) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(o, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, byte[]> e : m.entrySet()) {
                z.putNextEntry(new ZipEntry(e.getKey()));
                z.write(e.getValue());
                z.closeEntry();
            }
        }
        return o.toByteArray();
    }
}

package io.github.limjaemin99.hwpxdocx.tools;

import io.github.limjaemin99.hwpxdocx.testkit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertEquals;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertTrue;

public class FontLineHeightsTest {

    /** head·hhea·OS/2·name 표만 있는 TTF. 이름은 {언어 ID, 이름 ID, 글} 목록. */
    static byte[] ttf(int upm, int asc, int desc, int gap, int winAsc, int winDesc, int codePages, String[][] names) {
        byte[] head = new byte[54];
        ByteBuffer.wrap(head).putShort(18, (short) upm);
        byte[] hhea = new byte[36];
        ByteBuffer.wrap(hhea).putShort(4, (short) asc).putShort(6, (short) desc).putShort(8, (short) gap);
        byte[] os2 = new byte[86];
        ByteBuffer.wrap(os2).putShort(0, (short) 1).putShort(74, (short) winAsc).putShort(76, (short) winDesc)
                .putInt(78, codePages);
        List<byte[]> strings = new ArrayList<byte[]>();
        int total = 0;
        for (String[] n : names) {
            byte[] s = n[2].getBytes(StandardCharsets.UTF_16BE);
            strings.add(s);
            total += s.length;
        }
        ByteBuffer name = ByteBuffer.allocate(6 + 12 * names.length + total);
        name.putShort((short) 0).putShort((short) names.length).putShort((short) (6 + 12 * names.length));
        int off = 0;
        for (int i = 0; i < names.length; i++) {
            name.putShort((short) 3).putShort((short) 1).putShort((short) Integer.parseInt(names[i][0], 16))
                    .putShort((short) Integer.parseInt(names[i][1])).putShort((short) strings.get(i).length)
                    .putShort((short) off);
            off += strings.get(i).length;
        }
        for (byte[] s : strings) {
            name.put(s);
        }
        byte[][] tables = {head, hhea, os2, name.array()};
        String[] tags = {"head", "hhea", "OS/2", "name"};
        int size = 12 + 16 * 4;
        for (byte[] t : tables) {
            size += t.length;
        }
        ByteBuffer b = ByteBuffer.allocate(size);
        b.putInt(0x00010000).putShort((short) 4).putShort((short) 0).putShort((short) 0).putShort((short) 0);
        int at = 12 + 16 * 4;
        for (int i = 0; i < 4; i++) {
            b.put(tags[i].getBytes(StandardCharsets.ISO_8859_1)).putInt(0).putInt(at).putInt(tables[i].length);
            at += tables[i].length;
        }
        for (byte[] t : tables) {
            b.put(t);
        }
        return b.array();
    }

    @Test
    public void latinUsesHheaWithLineGap() {
        // Arial과 같은 값
        byte[] f = ttf(2048, 1854, -434, 67, 1854, 434, 0x400001FF, new String[][]{{"409", "1", "Arial"}, {"409", "2", "Regular"}});
        FontLineHeights.Face face = FontLineHeights.read(f).get(0);
        assertEquals("1.150", String.format(java.util.Locale.ROOT, "%.3f", face.factor));
        assertTrue(face.regular);
        assertTrue(face.families.contains("Arial"));
    }

    @Test
    public void eastAsianUsesWinMetricsTimes13() {
        // 맑은 고딕과 같은 값, 한국어·영어 이름
        byte[] f = ttf(2048, 2229, -495, 0, 2229, 495, 0x00080001,
                new String[][]{{"409", "1", "Malgun Gothic"}, {"412", "1", "맑은 고딕"}, {"409", "2", "Regular"}});
        FontLineHeights.Face face = FontLineHeights.read(f).get(0);
        assertEquals("1.729", String.format(java.util.Locale.ROOT, "%.3f", face.factor));
        assertTrue(face.families.contains("맑은 고딕"));
    }

    @Test
    public void ttcAndRegularPreferred() {
        byte[] bold = ttf(1024, 879, -145, 152, 900, 145, 0x00080000, new String[][]{{"409", "1", "Batang"}, {"409", "2", "Bold"}});
        byte[] reg = ttf(1024, 879, -145, 152, 879, 145, 0x00080000, new String[][]{{"409", "1", "Batang"}, {"409", "2", "Regular"}});
        ByteBuffer c = ByteBuffer.allocate(20 + bold.length + reg.length);
        c.putInt(0x74746366).putInt(0x00010000).putInt(2).putInt(20).putInt(20 + bold.length);
        // TTC 안 글꼴의 표 위치는 파일 처음 기준이라 옮겨 적음
        c.put(shift(bold, 20)).put(shift(reg, 20 + bold.length));
        List<FontLineHeights.Face> faces = FontLineHeights.read(c.array());
        assertEquals(2, faces.size());
        Map<String, Double> t = FontLineHeights.table(faces);
        assertEquals("1.300", String.format(java.util.Locale.ROOT, "%.3f", t.get("Batang")));
    }

    private static byte[] shift(byte[] font, int by) {
        ByteBuffer b = ByteBuffer.wrap(font.clone());
        int n = b.getShort(4);
        for (int i = 0; i < n; i++) {
            int rec = 12 + 16 * i + 8;
            b.putInt(rec, b.getInt(rec) + by);
        }
        return b.array();
    }

    @Test
    public void unlistedFontsLeftOut() {
        byte[] f = ttf(1000, 800, -200, 0, 800, 200, 0, new String[][]{{"409", "1", "Some Font"}, {"409", "2", "Regular"}});
        assertTrue(FontLineHeights.table(FontLineHeights.read(f)).isEmpty());
    }

    @Test
    public void measuredValueOverridesTtf() {
        assertEquals("맑은 고딕	1.730	Word 실측(30줄 평균)", FontLineHeights.line("맑은 고딕", 1.7293));
        assertEquals("Batang	1.3025	Word 실측(30줄 평균)", FontLineHeights.line("Batang", 1.3));
        assertEquals("Arial	1.150	TTF 계산", FontLineHeights.line("Arial", 1.1499));
    }

    /** jar 안 표가 Word에서 30줄 평균으로 잰 비율과 같고, 값마다 출처가 적혀 있는지. */
    @Test
    public void bundledTableMatchesWordMeasurements() throws Exception {
        Map<String, String[]> t = new HashMap<String, String[]>();
        try (InputStream in = FontLineHeightsTest.class.getResourceAsStream("/io/github/limjaemin99/hwpxdocx/font-line-height.tsv")) {
            assertTrue(in != null);
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                if (!line.startsWith("#") && line.indexOf('	') > 0) {
                    String[] c = line.split("	");
                    assertEquals(3, c.length);
                    t.put(c[0], c);
                }
            }
        }
        for (Map.Entry<String, Double> m : FontLineHeights.MEASURED.entrySet()) {
            String[] c = t.get(m.getKey());
            if (c == null || Double.parseDouble(c[1]) != m.getValue() || !FontLineHeights.SOURCE_MEASURED.equals(c[2])) {
                throw new AssertionError(m.getKey() + " 표 " + (c == null ? null : String.join("/", c)) + " / 실측 " + m.getValue());
            }
        }
        // 잴 때 대체 글꼴로 그려진 서양 글꼴은 TTF 계산값
        String[][] ttf = {{"Times New Roman", "1.150"}, {"Arial", "1.150"}, {"Calibri", "1.221"}};
        for (String[] f : ttf) {
            assertEquals(f[1], t.get(f[0])[1]);
            assertEquals(FontLineHeights.SOURCE_TTF, t.get(f[0])[2]);
        }
    }
}

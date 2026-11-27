package io.github.limjaemin99.hwpxdocx.cli;

import io.github.limjaemin99.hwpxdocx.testkit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertContains;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertEquals;

class MainTest {

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    private int run(String... args) {
        return Main.run(args, new PrintStream(out, true), new PrintStream(err, true));
    }

    @Test
    void helpPrintsUsage() {
        assertEquals(Main.OK, run("--help"));
        assertContains(out.toString(), "사용법");
    }

    @Test
    void versionPrintsName() {
        assertEquals(Main.OK, run("--version"));
        assertContains(out.toString(), "hwpx-docx-converter");
    }

    @Test
    void wrongArgumentCountIsUsageError() {
        assertEquals(Main.USAGE, run());
        assertEquals(Main.USAGE, run("a.hwpx"));
        assertContains(err.toString(), "사용법");
    }

    @Test
    void unsupportedExtensionPairIsUsageError() {
        assertEquals(Main.USAGE, run("a.txt", "b.docx"));
        assertEquals(Main.USAGE, run("a.hwpx", "b.hwpx"));
        assertEquals(Main.USAGE, run("a.docx", "b.docx"));
    }

    @Test
    void missingInputIsUsageError() throws IOException {
        Path dir = Files.createTempDirectory("hdc-cli");
        try {
            assertEquals(Main.USAGE, run(dir.resolve("none.hwpx").toString(), dir.resolve("out.docx").toString()));
        } finally {
            deleteTree(dir);
        }
    }

    @Test
    void failedConversionLeavesNoOutputOrTempFile() throws IOException {
        Path dir = Files.createTempDirectory("hdc-cli");
        try {
            Path in = dir.resolve("in.hwpx");
            Files.write(in, "not a zip".getBytes(StandardCharsets.US_ASCII));

            assertEquals(Main.FAILED, run(in.toString(), dir.resolve("out.docx").toString()));
            assertContains(err.toString(), "변환 실패");
            assertEquals("[in.hwpx]", list(dir).toString());
        } finally {
            deleteTree(dir);
        }
    }

    private static List<String> list(Path dir) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            return s.map(p -> p.getFileName().toString()).sorted().collect(Collectors.toList());
        }
    }

    private static void deleteTree(Path dir) throws IOException {
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path p : s.sorted((a, b) -> b.compareTo(a)).collect(Collectors.toList())) {
                Files.deleteIfExists(p);
            }
        }
    }
}

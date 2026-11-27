package io.github.limjaemin99.hwpxdocx.testkit;

import java.util.Objects;

public final class Assert {

    private Assert() {
    }

    @FunctionalInterface
    public interface Block {
        void run() throws Throwable;
    }

    public static void fail(String message) {
        throw new AssertionError(message);
    }

    public static void assertTrue(boolean condition) {
        if (!condition) {
            fail("참이어야 함");
        }
    }

    public static void assertFalse(boolean condition) {
        if (condition) {
            fail("거짓이어야 함");
        }
    }

    public static void assertEquals(long expected, long actual) {
        if (expected != actual) {
            fail("기대 " + expected + ", 실제 " + actual);
        }
    }

    public static void assertEquals(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            fail("기대 <" + expected + ">, 실제 <" + actual + ">");
        }
    }

    public static void assertContains(String text, String part) {
        if (text == null || !text.contains(part)) {
            fail("<" + part + "> 가 들어 있어야 함: <" + text + ">");
        }
    }

    public static <T extends Throwable> T assertThrows(Class<T> type, Block block) {
        try {
            block.run();
        } catch (Throwable t) {
            if (type.isInstance(t)) {
                return type.cast(t);
            }
            AssertionError e = new AssertionError(type.getName() + " 이어야 하는데 " + t.getClass().getName());
            e.initCause(t);
            throw e;
        }
        throw new AssertionError(type.getName() + " 이 나와야 하는데 아무것도 나오지 않음");
    }
}

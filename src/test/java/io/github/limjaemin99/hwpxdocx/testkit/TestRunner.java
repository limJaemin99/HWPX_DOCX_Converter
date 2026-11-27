package io.github.limjaemin99.hwpxdocx.testkit;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * JUnit 없이 시험을 돌린다. 외부 라이브러리를 쓰지 않는 프로젝트라 build 스크립트가 JDK만으로 돌 수 있게 함.
 *
 * <p>인자로 받은 폴더에서 이름이 {@code Test}로 끝나는 클래스를 찾아 {@link Test} 메서드를 이름 순으로 실행한다.
 * 실패가 있거나 시험이 하나도 없으면 종료 코드 1.
 */
public final class TestRunner {

    private TestRunner() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("사용법: TestRunner <시험 클래스 폴더>");
            System.exit(2);
        }
        int run = 0;
        List<String> failed = new ArrayList<String>();
        for (String name : findClasses(Paths.get(args[0]))) {
            Class<?> type;
            try {
                type = Class.forName(name);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("클래스를 읽지 못함: " + name, e);
            }
            for (Method m : testMethods(type)) {
                run++;
                String id = type.getSimpleName() + "." + m.getName();
                Throwable error = invoke(type, m);
                if (error == null) {
                    System.out.println("  ok    " + id);
                } else {
                    failed.add(id);
                    System.out.println("  FAIL  " + id + " - " + error);
                    printTrace(error);
                }
            }
        }
        System.out.println("시험 " + run + "개, 실패 " + failed.size() + "개 (Java "
                + System.getProperty("java.version") + ")");
        if (run == 0 || !failed.isEmpty()) {
            System.exit(1);
        }
    }

    private static List<String> findClasses(final Path root) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            return files
                    .map(p -> root.relativize(p).toString())
                    .filter(s -> s.endsWith("Test.class") && s.indexOf('$') < 0)
                    .map(s -> s.substring(0, s.length() - ".class".length()).replace(File.separatorChar, '.'))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    private static List<Method> testMethods(Class<?> type) {
        List<Method> list = new ArrayList<Method>();
        for (Method m : type.getDeclaredMethods()) {
            if (m.isAnnotationPresent(Test.class)) {
                list.add(m);
            }
        }
        Collections.sort(list, Comparator.comparing(Method::getName));
        return list;
    }

    private static Throwable invoke(Class<?> type, Method m) {
        try {
            Constructor<?> ctor = type.getDeclaredConstructor();
            ctor.setAccessible(true);
            Object instance = ctor.newInstance();
            m.setAccessible(true);
            m.invoke(instance);
            return null;
        } catch (InvocationTargetException e) {
            return e.getCause();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return e;
        }
    }

    private static void printTrace(Throwable t) {
        StackTraceElement[] trace = t.getStackTrace();
        for (int i = 0; i < trace.length && i < 8; i++) {
            if (trace[i].getClassName().startsWith("java.lang.reflect.")
                    || trace[i].getClassName().startsWith("jdk.internal.")
                    || trace[i].getClassName().startsWith("sun.reflect.")) {
                break;
            }
            System.out.println("          at " + trace[i]);
        }
        if (t.getCause() != null) {
            System.out.println("        원인: " + t.getCause());
        }
    }
}

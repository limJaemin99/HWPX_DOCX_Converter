package io.github.limjaemin99.hwpxdocx;

import io.github.limjaemin99.hwpxdocx.internal.d2h.DocxSamples;
import io.github.limjaemin99.hwpxdocx.testkit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertTrue;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.fail;

class ConcurrentConvertTest {

    private static final int THREADS = 8;
    private static final int ROUNDS = 5;

    private static byte[] toHwpx(byte[] docx) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        HwpxDocxConverter.docxToHwpx(new ByteArrayInputStream(docx), out, null);
        return out.toByteArray();
    }

    private static byte[] toDocx(byte[] hwpx) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        HwpxDocxConverter.hwpxToDocx(new ByteArrayInputStream(hwpx), out, null);
        return out.toByteArray();
    }

    @Test
    void sameOutputWhenCalledFromManyThreads() throws Exception {
        final byte[] docx = DocxSamples.rich();
        final byte[] hwpx = toHwpx(docx);
        final byte[] docxAgain = toDocx(hwpx);
        assertTrue(hwpx.length > 0 && docxAgain.length > 0);

        // 겹쳐 돌도록 모든 스레드를 한꺼번에 출발시킴
        final CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<String>> results = new ArrayList<Future<String>>();
            for (int i = 0; i < THREADS; i++) {
                final int id = i;
                results.add(pool.submit(new Callable<String>() {
                    @Override
                    public String call() throws Exception {
                        start.await();
                        for (int round = 0; round < ROUNDS; round++) {
                            // 스레드마다 방향 순서를 달리해 두 변환이 섞여 돌게 함
                            boolean hwpxFirst = (id + round) % 2 == 0;
                            if (hwpxFirst && !Arrays.equals(hwpx, toHwpx(docx))) {
                                return "thread " + id + " round " + round + ": DOCX -> HWPX differs";
                            }
                            if (!Arrays.equals(docxAgain, toDocx(hwpx))) {
                                return "thread " + id + " round " + round + ": HWPX -> DOCX differs";
                            }
                            if (!hwpxFirst && !Arrays.equals(hwpx, toHwpx(docx))) {
                                return "thread " + id + " round " + round + ": DOCX -> HWPX differs";
                            }
                        }
                        return null;
                    }
                }));
            }
            start.countDown();
            for (Future<String> f : results) {
                String problem = f.get(120, TimeUnit.SECONDS);
                if (problem != null) {
                    fail(problem);
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }
}

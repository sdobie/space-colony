package spacecolony.debug;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CrashHandlerTest {
    @Test void uncaught_isLoggedRecordedAndReportedOnce() throws Exception {
        ExceptionLog log = new ExceptionLog(20);
        List<Throwable> reported = new CopyOnWriteArrayList<>();
        CountDownLatch release = new CountDownLatch(1);
        // Reporter that "holds the dialog open" until released.
        CrashHandler.Reporter r = t -> { reported.add(t); release.await(5, TimeUnit.SECONDS); };
        CrashHandler h = new CrashHandler(log, () -> false /* debug off */, r);

        Thread a = new Thread(() -> { throw new IllegalStateException("boom-1"); });
        a.setUncaughtExceptionHandler(h);
        a.start();
        waitUntil(() -> reported.size() == 1);
        h.uncaughtException(Thread.currentThread(), new RuntimeException("boom-2")); // while dialog open
        release.countDown();
        a.join();

        assertEquals(2, log.snapshot().size());
        assertEquals(2, log.total());
        assertEquals(1, reported.size(), "second exception must not open another dialog");
    }

    @Test void debugOn_neverReports() {
        ExceptionLog log = new ExceptionLog(20);
        List<Throwable> reported = new CopyOnWriteArrayList<>();
        CrashHandler h = new CrashHandler(log, () -> true, reported::add);
        h.uncaughtException(Thread.currentThread(), new RuntimeException("x"));
        assertEquals(1, log.snapshot().size());
        assertTrue(reported.isEmpty());
    }

    @Test void afterDialogCloses_nextExceptionReportsAgain() {
        ExceptionLog log = new ExceptionLog(20);
        List<Throwable> reported = new CopyOnWriteArrayList<>();
        CrashHandler h = new CrashHandler(log, () -> false, reported::add);
        h.report(new RuntimeException("a"));
        h.report(new RuntimeException("b"));
        assertEquals(2, reported.size());
    }

    @Test void exceptionLog_isBoundedButCountsAll() {
        ExceptionLog log = new ExceptionLog(3);
        for (int i = 0; i < 5; i++) log.add(Thread.currentThread(), new RuntimeException("e" + i));
        assertEquals(3, log.snapshot().size());
        assertEquals(5, log.total());
        assertEquals("e4", log.snapshot().get(2).error().getMessage());
    }

    private static void waitUntil(BooleanSupplier cond) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!cond.getAsBoolean()) {
            if (System.nanoTime() > deadline) fail("timed out");
            Thread.sleep(10);
        }
    }
}

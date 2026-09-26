package spacecolony.debug;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RingBufferHandlerTest {
    @Test void keepsNewestUpToCapacity() {
        RingBufferHandler h = new RingBufferHandler(3);
        for (int i = 0; i < 5; i++) h.publish(new LogRecord(Level.INFO, "m" + i));
        assertEquals(List.of("m2", "m3", "m4"), h.snapshot().stream().map(LogRecord::getMessage).toList());
    }

    @Test void concurrentPublish_losesNothingBelowCapacity() throws Exception {
        RingBufferHandler h = new RingBufferHandler(10_000);
        ExecutorService ex = Executors.newFixedThreadPool(4);
        for (int t = 0; t < 4; t++) ex.submit(() -> { for (int i = 0; i < 1000; i++) h.publish(new LogRecord(Level.INFO, "x")); });
        ex.shutdown();
        assertTrue(ex.awaitTermination(10, TimeUnit.SECONDS));
        assertEquals(4000, h.snapshot().size());
    }

    @Test void version_increasesOnPublishAndClear() {
        RingBufferHandler h = new RingBufferHandler(2);
        long v0 = h.version();
        h.publish(new LogRecord(Level.INFO, "a"));
        long v1 = h.version();
        assertTrue(v1 > v0);
        h.clear();
        assertTrue(h.version() > v1);
        assertTrue(h.snapshot().isEmpty());
    }
}

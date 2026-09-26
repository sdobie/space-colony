package spacecolony.debug;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RingBufferHandlerTest {
    @Test
    void keepsOnlyTheNewestRecords_oldestFirst() {
        RingBufferHandler h = new RingBufferHandler(3);
        for (int i = 0; i < 5; i++) h.publish(new LogRecord(Level.INFO, "m" + i));
        List<LogRecord> snap = h.snapshot();
        assertEquals(List.of("m2", "m3", "m4"), snap.stream().map(LogRecord::getMessage).toList());
        assertEquals(5, h.sequence());
    }

    @Test
    void respectsHandlerLevel() {
        RingBufferHandler h = new RingBufferHandler(10);
        h.setLevel(Level.WARNING);
        h.publish(new LogRecord(Level.INFO, "quiet"));
        h.publish(new LogRecord(Level.SEVERE, "loud"));
        assertEquals(1, h.snapshot().size());
        assertEquals("loud", h.snapshot().get(0).getMessage());
    }

    @Test
    void clearEmptiesAndBumpsSequence() {
        RingBufferHandler h = new RingBufferHandler(10);
        h.publish(new LogRecord(Level.INFO, "x"));
        long before = h.sequence();
        h.clear();
        assertTrue(h.snapshot().isEmpty());
        assertTrue(h.sequence() > before);
    }

    @Test
    void recentExceptions_returnsOnlyThrownRecordsNewestLast() {
        RingBufferHandler h = new RingBufferHandler(10);
        for (int i = 0; i < 4; i++) {
            LogRecord r = new LogRecord(Level.SEVERE, "boom" + i);
            r.setThrown(new IllegalStateException("e" + i));
            h.publish(r);
            h.publish(new LogRecord(Level.INFO, "noise" + i));
        }
        List<LogRecord> ex = DebugLogging.recentExceptions(h, 2);
        assertEquals(List.of("boom2", "boom3"), ex.stream().map(LogRecord::getMessage).toList());
    }

    @Test
    void parseLevel_acceptsSpecNames() {
        assertEquals(Level.FINE, DebugLogging.parseLevel("debug"));
        assertEquals(Level.INFO, DebugLogging.parseLevel("INFO"));
        assertEquals(Level.WARNING, DebugLogging.parseLevel("WARN"));
        assertEquals(Level.SEVERE, DebugLogging.parseLevel("SEVERE"));
        assertThrows(IllegalArgumentException.class, () -> DebugLogging.parseLevel("LOUD"));
    }

    @Test
    void install_routesSpacecolonyLoggersIntoTheBuffer() {
        RingBufferHandler buf = DebugLogging.install(Level.INFO);
        long before = buf.sequence();
        java.util.logging.Logger.getLogger("spacecolony.test.x").info("hello buffer");
        assertTrue(buf.sequence() > before);
        assertEquals("hello buffer", buf.snapshot().get(buf.snapshot().size() - 1).getMessage());
    }
}

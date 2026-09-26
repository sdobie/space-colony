package spacecolony.debug;

import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * java.util.logging wiring for debug mode (spec §8.1). Everything under the
 * {@code spacecolony} logger also lands in one process-wide {@link RingBufferHandler}.
 */
public final class DebugLogging {
    public static final String ROOT = "spacecolony";
    public static final int BUFFER_CAPACITY = 2000;

    private static final Logger ROOT_LOGGER = Logger.getLogger(ROOT);
    private static final RingBufferHandler BUFFER = new RingBufferHandler(BUFFER_CAPACITY);
    private static boolean installed;

    private DebugLogging() {}

    /** Attach the ring buffer to the {@code spacecolony} logger (idempotent) and set its level. */
    public static synchronized RingBufferHandler install(Level level) {
        if (!installed) {
            BUFFER.setLevel(Level.ALL);
            ROOT_LOGGER.addHandler(BUFFER);
            installed = true;
        }
        setLevel(level);
        return BUFFER;
    }

    public static RingBufferHandler buffer() { return BUFFER; }

    public static void setLevel(Level level) { ROOT_LOGGER.setLevel(level); }

    public static Level level() {
        Level l = ROOT_LOGGER.getLevel();
        return l == null ? Level.INFO : l;
    }

    /**
     * Parse a {@code --log-level} value. Accepts the spec's DEBUG|INFO|WARN plus any
     * java.util.logging level name.
     */
    public static Level parseLevel(String s) {
        String v = s.trim().toUpperCase(Locale.ROOT);
        return switch (v) {
            case "DEBUG" -> Level.FINE;
            case "TRACE" -> Level.FINEST;
            case "WARN"  -> Level.WARNING;
            case "ERROR" -> Level.SEVERE;
            default      -> Level.parse(v);
        };
    }

    /**
     * Route uncaught exceptions on any thread (the EDT included) into the log at SEVERE
     * so the overlay and log viewer can surface them (spec §8.2).
     */
    public static void installUncaughtHandler() {
        Thread.UncaughtExceptionHandler prior = Thread.getDefaultUncaughtExceptionHandler();
        Logger log = Logger.getLogger(ROOT + ".uncaught");
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            log.log(Level.SEVERE, "Uncaught exception on thread " + t.getName(), e);
            if (prior != null) prior.uncaughtException(t, e);
            else e.printStackTrace();
        });
    }

    /** The most recent {@code max} buffered records that carry a Throwable, newest last. */
    public static List<LogRecord> recentExceptions(RingBufferHandler buffer, int max) {
        List<LogRecord> all = buffer.snapshot();
        List<LogRecord> out = all.stream().filter(r -> r.getThrown() != null).toList();
        return out.subList(Math.max(0, out.size() - max), out.size());
    }
}

package spacecolony.debug;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Locale;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Installs the {@code spacecolony} logger tree (spec §8.1): an in-memory ring for the log
 * viewer (always) plus a rotating daily file (best effort; never stops the game starting).
 */
public final class DebugLogging {
    public static final String ROOT = "spacecolony";
    public static final int RING_CAPACITY = 2000;
    private static final Logger LOG = Logger.getLogger("spacecolony.debug");

    private DebugLogging() {}

    /** Handles to what {@link #install} attached, so it can be flushed, re-levelled or removed. */
    public record Installed(Logger root, RingBufferHandler ring, FileHandler file) {
        public void flush() { if (file != null) file.flush(); }
        public void setLevel(Level l) { root.setLevel(l); }
        public Level level() { return root.getLevel(); }
        public void uninstall() {
            root.removeHandler(ring);
            if (file != null) { root.removeHandler(file); file.close(); }
            if (current == this) current = null;
        }
    }

    private static volatile Installed current;

    /** The installation made by the app at startup; null in tests that don't install one. */
    public static Installed current() { return current; }

    /** "DEBUG"→FINE, "WARN"→WARNING, any JUL level name (case-insensitive); null when unrecognised. */
    public static Level parseLevel(String s) {
        if (s == null) return null;
        String u = s.trim().toUpperCase(Locale.ROOT);
        return switch (u) {
            case "DEBUG" -> Level.FINE;
            case "WARN"  -> Level.WARNING;
            default -> {
                try { yield Level.parse(u); }
                catch (IllegalArgumentException e) { yield null; }
            }
        };
    }

    public static Path defaultLogDir() {
        return Paths.get(System.getProperty("user.home"), ".space-colony", "logs");
    }

    public static Installed install(Level level, Path logDir) {
        Logger root = Logger.getLogger(ROOT);
        root.setUseParentHandlers(false);
        root.setLevel(level);
        RingBufferHandler ring = new RingBufferHandler(RING_CAPACITY);
        root.addHandler(ring);
        FileHandler file = null;
        try {
            Files.createDirectories(logDir);
            String pattern = logDir.resolve("space-colony-" + LocalDate.now() + ".%g.log").toString();
            file = new FileHandler(pattern, 1_000_000, 5, true);
            file.setFormatter(new SimpleFormatter());
            file.setLevel(Level.ALL);
            root.addHandler(file);
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "File logging disabled: " + e.getMessage(), e);
        }
        Installed inst = new Installed(root, ring, file);
        current = inst;
        return inst;
    }
}

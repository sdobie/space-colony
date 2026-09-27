package spacecolony.options;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Properties;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import spacecolony.engine.Speed;

/**
 * Reads and writes {@link Options} as a properties file (Plan 6 §4.2). Loading never throws:
 * each key falls back to its default on its own, so options can't stop the game starting.
 */
public final class OptionsStore {
    /** System property that overrides the options file (tests and play-test drivers). */
    public static final String FILE_PROPERTY = "spacecolony.optionsFile";
    private static final Logger LOG = Logger.getLogger("spacecolony.options");

    static final String START_SPEED = "gameplay.startSpeed";
    static final String AUTOSAVE_MINUTES = "gameplay.autosaveMinutes";
    static final String CONFIRM_QUIT = "gameplay.confirmQuit";
    static final String SHOW_SPLASH = "display.showSplash";
    static final String START_MAXIMIZED = "display.startMaximized";
    static final String UI_SCALE = "display.uiScalePercent";
    static final String START_IN_DEBUG = "developer.startInDebug";
    static final String LOG_LEVEL = "developer.logLevel";
    static final String TUTORIAL_COMPLETED = "progress.tutorialCompleted";

    private static final List<Boolean> BOOLS = List.of(false, true);

    private final Path file;

    public OptionsStore(Path file) { this.file = file; }

    /** {@code ~/.space-colony/options.properties}, or the file named by {@value #FILE_PROPERTY}. */
    public static OptionsStore defaultFile() {
        String override = System.getProperty(FILE_PROPERTY);
        if (override != null && !override.isBlank()) return new OptionsStore(Paths.get(override));
        return new OptionsStore(Paths.get(System.getProperty("user.home"), ".space-colony", "options.properties"));
    }

    public Path file() { return file; }

    public Options load() {
        Properties p = new Properties();
        if (Files.isRegularFile(file)) {
            try (Reader r = Files.newBufferedReader(file)) {
                p.load(r);
            } catch (IOException | IllegalArgumentException e) {
                LOG.log(Level.WARNING, "Could not read " + file + "; using default options", e);
                return Options.DEFAULTS;
            }
        }
        Options d = Options.DEFAULTS;
        return new Options(
            pick(p, START_SPEED, Options.START_SPEEDS, Speed::valueOf, d.startSpeed()),
            pick(p, AUTOSAVE_MINUTES, Options.AUTOSAVE_MINUTES, Integer::valueOf, d.autosaveMinutes()),
            pick(p, CONFIRM_QUIT, BOOLS, OptionsStore::parseBool, d.confirmQuit()),
            pick(p, SHOW_SPLASH, BOOLS, OptionsStore::parseBool, d.showSplash()),
            pick(p, START_MAXIMIZED, BOOLS, OptionsStore::parseBool, d.startMaximized()),
            pick(p, UI_SCALE, Options.UI_SCALES, Integer::valueOf, d.uiScalePercent()),
            pick(p, START_IN_DEBUG, BOOLS, OptionsStore::parseBool, d.startInDebug()),
            pick(p, LOG_LEVEL, Options.LOG_LEVELS, Level::parse, d.logLevel()),
            pick(p, TUTORIAL_COMPLETED, BOOLS, OptionsStore::parseBool, d.tutorialCompleted()));
    }

    /** Writes atomically (a temp file moved into place), creating the directory if needed. */
    public void save(Options o) throws IOException {
        Properties p = new Properties();
        p.setProperty(START_SPEED, o.startSpeed().name());
        p.setProperty(AUTOSAVE_MINUTES, Integer.toString(o.autosaveMinutes()));
        p.setProperty(CONFIRM_QUIT, Boolean.toString(o.confirmQuit()));
        p.setProperty(SHOW_SPLASH, Boolean.toString(o.showSplash()));
        p.setProperty(START_MAXIMIZED, Boolean.toString(o.startMaximized()));
        p.setProperty(UI_SCALE, Integer.toString(o.uiScalePercent()));
        p.setProperty(START_IN_DEBUG, Boolean.toString(o.startInDebug()));
        p.setProperty(LOG_LEVEL, o.logLevel().getName());
        p.setProperty(TUTORIAL_COMPLETED, Boolean.toString(o.tutorialCompleted()));
        Path dir = file.toAbsolutePath().getParent();
        if (dir != null) Files.createDirectories(dir);
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (Writer w = Files.newBufferedWriter(tmp)) {
            p.store(w, "Space Colony options");
        }
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static <T> T pick(Properties p, String key, List<T> allowed, Function<String, T> parse, T fallback) {
        String raw = p.getProperty(key);
        if (raw == null) return fallback;
        try {
            T v = parse.apply(raw.trim());
            if (allowed.contains(v)) return v;
        } catch (RuntimeException ignored) {
            // falls through to the warning below
        }
        LOG.warning(() -> "Ignoring option " + key + "=" + raw + "; using " + fallback);
        return fallback;
    }

    private static Boolean parseBool(String s) {
        if (s.equalsIgnoreCase("true")) return true;
        if (s.equalsIgnoreCase("false")) return false;
        throw new IllegalArgumentException(s);
    }
}

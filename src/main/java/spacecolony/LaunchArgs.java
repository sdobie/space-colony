package spacecolony;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Level;
import spacecolony.debug.DebugLogging;

/**
 * Command-line flags for {@link SpaceColonyApp} (Plan 6 §3.1).
 *
 * @param seed      {@code --seed N}: start that game directly, skipping splash and title; null when absent
 * @param logLevel  {@code --log-level=}: null when absent (the options file decides)
 * @param badLevel  the raw {@code --log-level=} argument when it didn't parse, else null
 * @param skipIntro {@code --skip-intro}: no splash, straight to the title
 */
public record LaunchArgs(Long seed, boolean debug, Level logLevel, String badLevel, boolean skipIntro) {
    public static LaunchArgs parse(String[] args) {
        Long seed = null;
        boolean debug = false, skipIntro = false;
        Level level = null;
        String badLevel = null;
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.equals("--seed") && i + 1 < args.length) seed = Long.parseLong(args[++i]);
            else if (a.equals("--debug")) debug = true;
            else if (a.equals("--skip-intro")) skipIntro = true;
            else if (a.startsWith("--log-level=")) {
                Level l = DebugLogging.parseLevel(a.substring("--log-level=".length()));
                if (l != null) level = l; else badLevel = a;
            }
        }
        return new LaunchArgs(seed, debug, level, badLevel, skipIntro);
    }

    /** {@code --seed} goes straight into a game. */
    public boolean skipTitle() { return seed != null; }

    /** The build version from {@code spacecolony/version.properties}; "dev" when unavailable. */
    public static String version() {
        try (InputStream in = LaunchArgs.class.getResourceAsStream("/spacecolony/version.properties")) {
            if (in == null) return "dev";
            Properties p = new Properties();
            p.load(in);
            String v = p.getProperty("version", "dev");
            return v.isBlank() || v.contains("$") ? "dev" : v;
        } catch (IOException e) {
            return "dev";
        }
    }
}

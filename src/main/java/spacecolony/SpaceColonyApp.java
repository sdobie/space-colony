package spacecolony;

import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import spacecolony.debug.CrashHandler;
import spacecolony.debug.DebugLogging;
import spacecolony.debug.ExceptionLog;
import spacecolony.options.Options;
import spacecolony.options.OptionsStore;
import spacecolony.save.SaveSlots;
import spacecolony.ui.startup.AppController;

/**
 * Swing entry point: {@code ./gradlew play} shows the splash, then the title screen.
 * {@code --seed N} skips both and starts that seed directly; {@code --skip-intro} skips only the
 * splash. {@code --debug} (or {@code ./gradlew play -Pdebug}) starts games in debug mode, and
 * {@code --log-level=DEBUG|INFO|WARN} sets logging verbosity. Both override the options file
 * ({@code ~/.space-colony/options.properties}).
 */
public class SpaceColonyApp {
    public static void main(String[] argv) {
        LaunchArgs args = LaunchArgs.parse(argv);
        OptionsStore store = OptionsStore.defaultFile();
        Options options = store.load();
        // Swing reads the scale once, when the toolkit starts, so this must precede any AWT use.
        if (options.uiScalePercent() != 0 && System.getProperty("sun.java2d.uiScale") == null) {
            System.setProperty("sun.java2d.uiScale", Double.toString(options.uiScalePercent() / 100.0));
        }
        Level level = args.logLevel() != null ? args.logLevel() : options.logLevel();

        // Logging and the crash handler go in before any UI so a crash before Ctrl+D is captured.
        System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tF %1$tT %4$s %3$s: %5$s%6$s%n");
        DebugLogging.install(level, DebugLogging.defaultLogDir());
        if (args.badLevel() != null) {
            Logger.getLogger("spacecolony").warning("Ignoring " + args.badLevel() + "; using " + level);
        }
        ExceptionLog exceptions = new ExceptionLog(20);

        SwingUtilities.invokeLater(() -> {
            AppController app = new AppController(options, store, SaveSlots.defaultDir(), exceptions, args);
            CrashHandler.install(new CrashHandler(exceptions, app::debugOn, app::report));
            app.start();
        });
    }
}

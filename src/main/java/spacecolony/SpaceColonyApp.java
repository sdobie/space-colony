package spacecolony;

import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import spacecolony.debug.CrashHandler;
import spacecolony.debug.DebugLogging;
import spacecolony.debug.ExceptionLog;
import spacecolony.engine.Engine;
import spacecolony.ui.SpaceColonyFrame;
import spacecolony.world.WorldGenerator;

/**
 * Swing entry point. Use `./gradlew play --args="--seed N"` to launch. Add {@code --debug}
 * (or use {@code ./gradlew play -Pdebug}) to start in debug mode and {@code --log-level=DEBUG|INFO|WARN} to set logging verbosity.
 */
public class SpaceColonyApp {
    public static void main(String[] args) {
        long seed = 42L;
        boolean debug = false;
        Level level = Level.INFO;
        String badLevel = null;
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.equals("--seed") && i + 1 < args.length) seed = Long.parseLong(args[++i]);
            else if (a.equals("--debug")) debug = true;
            else if (a.startsWith("--log-level=")) {
                Level l = DebugLogging.parseLevel(a.substring("--log-level=".length()));
                if (l != null) level = l; else badLevel = a;
            }
        }
        // Logging and the crash handler go in before any UI so a crash before Ctrl+D is captured.
        System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tF %1$tT %4$s %3$s: %5$s%6$s%n");
        DebugLogging.install(level, DebugLogging.defaultLogDir());
        if (badLevel != null) Logger.getLogger("spacecolony").warning("Ignoring " + badLevel + "; using INFO");
        ExceptionLog exceptions = new ExceptionLog(20);

        final long finalSeed = seed;
        final boolean finalDebug = debug;
        SwingUtilities.invokeLater(() -> {
            Engine engine = new Engine(WorldGenerator.generate(finalSeed));
            SpaceColonyFrame frame = new SpaceColonyFrame(engine, exceptions);
            CrashHandler.install(new CrashHandler(exceptions, engine::debugEnabled, frame::showCrashDialog));
            engine.setDebugEnabled(finalDebug);
            frame.setVisible(true);
        });
    }
}

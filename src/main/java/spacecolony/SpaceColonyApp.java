package spacecolony;

import java.util.logging.Level;
import javax.swing.SwingUtilities;
import spacecolony.debug.DebugLogging;
import spacecolony.engine.Engine;
import spacecolony.ui.SpaceColonyFrame;
import spacecolony.world.WorldGenerator;

/**
 * Swing entry point. Use `./gradlew play --args="--seed N"` to launch. Add {@code --debug}
 * to start in debug mode and {@code --log-level=DEBUG|INFO|WARN} to set logging verbosity.
 */
public class SpaceColonyApp {
    public static void main(String[] args) {
        long seed = 42L;
        boolean debug = false;
        Level logLevel = Level.INFO;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--seed") && i + 1 < args.length) seed = Long.parseLong(args[i + 1]);
            if (args[i].equals("--debug")) debug = true;
            if (args[i].startsWith("--log-level=")) logLevel = DebugLogging.parseLevel(args[i].substring("--log-level=".length()));
        }
        DebugLogging.install(logLevel);
        DebugLogging.installUncaughtHandler();
        final long finalSeed = seed;
        final boolean finalDebug = debug;
        SwingUtilities.invokeLater(() -> {
            Engine engine = new Engine(WorldGenerator.generate(finalSeed));
            SpaceColonyFrame frame = new SpaceColonyFrame(engine);
            if (finalDebug) engine.setDebugEnabled(true);
            frame.setVisible(true);
        });
    }
}

package spacecolony;

import javax.swing.SwingUtilities;
import spacecolony.engine.Engine;
import spacecolony.ui.SpaceColonyFrame;
import spacecolony.world.WorldGenerator;

/** Swing entry point. Use `./gradlew play --args="--seed N"` to launch. */
public class SpaceColonyApp {
    public static void main(String[] args) {
        long seed = 42L;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--seed") && i + 1 < args.length) seed = Long.parseLong(args[i + 1]);
        }
        final long finalSeed = seed;
        SwingUtilities.invokeLater(() -> {
            Engine engine = new Engine(WorldGenerator.generate(finalSeed));
            SpaceColonyFrame frame = new SpaceColonyFrame(engine);
            frame.setVisible(true);
        });
    }
}

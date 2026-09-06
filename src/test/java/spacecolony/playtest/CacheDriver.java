package spacecolony.playtest;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import spacecolony.engine.Engine;
import spacecolony.engine.Selection;
import spacecolony.save.SaveFile;
import spacecolony.sim.World;
import spacecolony.ui.SpaceColonyFrame;
import spacecolony.world.WorldGenerator;

/**
 * Focused check on the WorldReplaced flat-map cache invalidation. Requires a desktop
 * session — run via {@code ./gradlew cacheCheck}.
 *
 * <p>The invariant: body ids are stable
 * across worlds but surfaceSeed is not, so a cached render from the previous world must
 * not survive a load. Selects Earth (populating the cache), loads a different-seed world,
 * re-selects Earth, and compares the rendered pixels.
 */
public class CacheDriver {
    static Engine engine;
    static SpaceColonyFrame frame;
    static Path out;

    public static void main(String[] args) throws Exception {
        out = Path.of(args.length > 0 ? args[0] : "build/playtest");
        Files.createDirectories(out);

        // A save from a different seed => different surfaceSeed under the same body ids.
        Path other = out.resolve("seed999.json");
        SaveFile.save(WorldGenerator.generate(999L), other);

        SwingUtilities.invokeAndWait(() -> {
            engine = new Engine(WorldGenerator.generate(1L));
            frame = new SpaceColonyFrame(engine);
            frame.setVisible(true);
        });
        Thread.sleep(1200);

        SwingUtilities.invokeAndWait(() -> engine.setSelection(Selection.body("earth")));
        Thread.sleep(1500);                       // sphere generation is ~270ms per body
        BufferedImage before = shot("cache-01-earth-seed1");
        long seedBefore = engine.world().seed;

        // Load through the real API the File menu uses, on the EDT like the menu does.
        World loaded = SaveFile.load(other);
        SwingUtilities.invokeAndWait(() -> engine.reset(loaded));
        Thread.sleep(300);
        SwingUtilities.invokeAndWait(() -> engine.setSelection(Selection.body("earth")));
        Thread.sleep(1500);
        BufferedImage after = shot("cache-02-earth-seed999");
        long seedAfter = engine.world().seed;

        long diff = differingPixels(before, after);
        double pct = 100.0 * diff / (before.getWidth() * (double) before.getHeight());
        System.out.printf("seed %d -> %d%n", seedBefore, seedAfter);
        System.out.printf("differing pixels: %d (%.2f%% of frame)%n", diff, pct);
        boolean ok = seedBefore != seedAfter && diff > 0;
        System.out.println((ok ? "PASS" : "FAIL")
            + "  flat-map cache invalidated on WorldReplaced (terrain re-rendered for new surfaceSeed)");
        System.exit(ok ? 0 : 1);
    }

    static long differingPixels(BufferedImage a, BufferedImage b) {
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) return -1;
        long n = 0;
        for (int y = 0; y < a.getHeight(); y++)
            for (int x = 0; x < a.getWidth(); x++)
                if (a.getRGB(x, y) != b.getRGB(x, y)) n++;
        return n;
    }

    static BufferedImage shot(String name) throws Exception {
        BufferedImage[] img = new BufferedImage[1];
        SwingUtilities.invokeAndWait(() -> {
            JRootPane pane = frame.getRootPane();
            BufferedImage i = new BufferedImage(pane.getWidth(), pane.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = i.createGraphics();
            pane.paint(g);
            g.dispose();
            img[0] = i;
        });
        ImageIO.write(img[0], "png", out.resolve(name + ".png").toFile());
        return img[0];
    }
}

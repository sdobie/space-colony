package spacecolony.playtest;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import spacecolony.engine.Engine;
import spacecolony.engine.Selection;
import spacecolony.render.BuildingGlyphs;
import spacecolony.render.ResourceGlyphs;
import spacecolony.sim.BuildingType;
import spacecolony.sim.Resource;
import spacecolony.sim.Ship;
import spacecolony.sim.ShipClass;
import spacecolony.sim.Simulator;
import spacecolony.ui.SpaceColonyFrame;
import spacecolony.ui.dialogs.BuildBuildingDialog;
import spacecolony.world.WorldGenerator;

/**
 * Screenshots of the resource and building icons: the full set, Earth Hub's detail panel and
 * left column, a hauler's cargo and the build dialog. Run via {@code ./gradlew iconsShot};
 * screenshots land in build/playtest/icons.
 */
public class IconsDriver {
    public static void main(String[] args) throws Exception {
        PlayTestDriver.out = Path.of(args.length > 0 ? args[0] : "build/playtest/icons");
        Files.createDirectories(PlayTestDriver.out);
        ImageIO.write(sheet(), "png", PlayTestDriver.out.resolve("icons-00-set.png").toFile());

        SpaceColonyFrame[] frame = new SpaceColonyFrame[1];
        Engine[] engine = new Engine[1];
        SwingUtilities.invokeAndWait(() -> {
            var w = WorldGenerator.generate(42L);
            w.randomEventsEnabled = false;
            Simulator sim = new Simulator();
            for (int i = 0; i < 3; i++) sim.advance(w);
            Ship mule = new Ship("h1", "Mule-1", ShipClass.HAULER, "site-earth-hub");
            mule.cargo.putAll(Map.of(Resource.WATER, 40.0, Resource.METAL, 25.0, Resource.FUEL, 30.0));
            w.ships.add(mule);
            engine[0] = new Engine(w);
            engine[0].setSelection(Selection.site("site-earth-hub"));
            frame[0] = new SpaceColonyFrame(engine[0]);
            frame[0].setSize(1400, 900);
            frame[0].setVisible(true);
        });
        Thread.sleep(800);
        SwingUtilities.invokeAndWait(() -> {
            var ledger = find(frame[0].getRootPane(), spacecolony.ui.ResourceLedgerPanel.class);
            ledger.toggle(Resource.FOOD);
        });
        Thread.sleep(300);
        PlayTestDriver.shot(frame[0].getRootPane(), "icons-01-colony");
        SwingUtilities.invokeAndWait(() -> engine[0].setSelection(Selection.ship("h1")));
        Thread.sleep(300);
        PlayTestDriver.shot(frame[0].getRootPane(), "icons-02-ship-cargo");

        JDialog[] dialog = new JDialog[1];
        SwingUtilities.invokeAndWait(() -> {
            dialog[0] = BuildBuildingDialog.create(frame[0], engine[0], "site-earth-hub");
            dialog[0].setModal(false);
            dialog[0].setVisible(true);
        });
        Thread.sleep(500);
        PlayTestDriver.shot(dialog[0].getRootPane(), "icons-03-build-dialog");
        SwingUtilities.invokeAndWait(() -> { dialog[0].dispose(); frame[0].dispose(); });
        System.exit(0);
    }

    private static <T> T find(java.awt.Container c, Class<T> type) {
        for (var child : c.getComponents()) {
            if (type.isInstance(child)) return type.cast(child);
            if (child instanceof java.awt.Container k) {
                T t = find(k, type);
                if (t != null) return t;
            }
        }
        return null;
    }

    /** Every glyph at 14, 20, 32 and 64 px on the panel background. */
    static BufferedImage sheet() {
        int[] sizes = { 14, 20, 32, 64 };
        int cols = Math.max(Resource.values().length + 1, BuildingType.values().length);
        int rowH = 0;
        for (int s : sizes) rowH += s + 10;
        BufferedImage img = new BufferedImage(cols * 72 + 10, 2 * rowH + 30, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(13, 20, 33));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        int y = 10;
        for (int s : sizes) {
            int x = 10;
            for (Resource r : Resource.values()) { ResourceGlyphs.draw(g, r, x, y, s); x += 72; }
            ResourceGlyphs.drawPopulation(g, x, y, s);
            y += s + 10;
        }
        y += 20;
        for (int s : sizes) {
            int x = 10;
            for (BuildingType t : BuildingType.values()) { BuildingGlyphs.draw(g, t, x, y, s); x += 72; }
            y += s + 10;
        }
        g.dispose();
        return img;
    }
}

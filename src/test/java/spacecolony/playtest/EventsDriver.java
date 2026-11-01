package spacecolony.playtest;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import javax.imageio.ImageIO;
import javax.swing.JToolTip;
import javax.swing.SwingUtilities;
import spacecolony.engine.Engine;
import spacecolony.engine.Selection;
import spacecolony.sim.Building;
import spacecolony.sim.BuildingType;
import spacecolony.sim.Event;
import spacecolony.sim.EventKind;
import spacecolony.sim.MeteorSize;
import spacecolony.sim.Resource;
import spacecolony.sim.Simulator;
import spacecolony.sim.Site;
import spacecolony.sim.World;
import spacecolony.sim.phases.EventPhase;
import spacecolony.ui.SpaceColonyFrame;
import spacecolony.world.WorldGenerator;

/**
 * Screenshots of Plan 11's event consequences: the event strip after one of each event, Earth
 * Hub's damaged rows, offline power plant and impact dust, and a meteor's tooltip. Run via
 * {@code ./gradlew eventsShot}; screenshots land in build/playtest/events.
 */
public class EventsDriver {
    public static void main(String[] args) throws Exception {
        PlayTestDriver.out = Path.of(args.length > 0 ? args[0] : "build/playtest/events");
        Files.createDirectories(PlayTestDriver.out);

        World w = WorldGenerator.generate(42L);
        w.randomEventsEnabled = false;
        Site mars = new Site("site-mars-base", "Mars Base", "mars", 0.1, -1.9, 100);
        mars.population = 60;
        for (BuildingType t : new BuildingType[] { BuildingType.HABITAT, BuildingType.POWER_PLANT,
                BuildingType.MINE, BuildingType.REFINERY, BuildingType.FARM })
            mars.addBuilding(new Building(t, 1));
        mars.stockpile.put(Resource.ICE, 190.0);
        mars.stockpile.put(Resource.FOOD, 80.0);
        mars.stockpile.put(Resource.WATER, 60.0);
        w.findBody("mars").sites.add(mars);
        Site hub = w.findSite("site-earth-hub");
        Simulator sim = new Simulator();
        for (int i = 0; i < 40; i++) sim.advance(w);

        EventPhase.applyForced(w, mars, EventKind.EQUIPMENT_FAILURE, null, new Random(2));
        sim.advance(w);
        EventPhase.applyForced(w, mars, EventKind.METEOR_STRIKE, MeteorSize.MINOR, new Random(4));
        sim.advance(w);
        EventPhase.applyForced(w, mars, EventKind.DISEASE_OUTBREAK, null, new Random(1));
        for (int i = 0; i < 3; i++) sim.advance(w);
        EventPhase.applyForced(w, hub, EventKind.METEOR_STRIKE, MeteorSize.MAJOR, new Random(3));
        sim.advance(w);
        EventPhase.applyForced(w, w.findBody("earth"), EventKind.SOLAR_FLARE, new Random(1));
        sim.advance(w);
        Event meteor = w.recentEvents.stream().filter(e -> e.message().startsWith("Major")).findFirst().orElseThrow();
        for (Event e : w.recentEvents) System.out.println(e.severity() + "  " + e.message());

        SpaceColonyFrame[] frame = new SpaceColonyFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            Engine engine = new Engine(w);
            engine.setSelection(Selection.site(hub.id));
            frame[0] = new SpaceColonyFrame(engine);
            frame[0].setSize(1400, 900);
            frame[0].setVisible(true);
        });
        Thread.sleep(800);
        PlayTestDriver.shot(frame[0].getRootPane(), "events-01-earth-hub");

        BufferedImage[] tip = new BufferedImage[1];
        SwingUtilities.invokeAndWait(() -> {
            JToolTip t = new JToolTip();
            t.setTipText(tooltipOf(meteor));
            var d = t.getPreferredSize();
            t.setSize(d);
            tip[0] = new BufferedImage(d.width, d.height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = tip[0].createGraphics();
            t.paint(g);
            g.dispose();
        });
        ImageIO.write(tip[0], "png", PlayTestDriver.out.resolve("events-02-meteor-tooltip.png").toFile());

        SwingUtilities.invokeAndWait(() -> frame[0].dispose());
        System.exit(0);
    }

    private static String tooltipOf(Event e) {
        try {
            var m = spacecolony.ui.EventStripPanel.class.getDeclaredMethod("tooltip", Event.class, boolean.class);
            m.setAccessible(true);
            return (String) m.invoke(null, e, true);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}

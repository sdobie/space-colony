package spacecolony.playtest;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.SwingUtilities;
import spacecolony.engine.Engine;
import spacecolony.engine.Selection;
import spacecolony.sim.Building;
import spacecolony.sim.BuildingType;
import spacecolony.sim.Resource;
import spacecolony.sim.ShipClass;
import spacecolony.sim.commands.BuildShipCommand;
import spacecolony.ui.SpaceColonyFrame;
import spacecolony.world.WorldGenerator;

/**
 * Plan 10 screenshots: a colonizer on the slipway (ship card, map badges, shipyard and lab
 * progress bars), the ship's own panel, and a fuel plant brewing synfuel. Run via
 * {@code ./gradlew shipsAndFuelShot}; screenshots land in build/playtest/plan10.
 */
public class ShipsAndFuelDriver {
    public static void main(String[] args) throws Exception {
        PlayTestDriver.out = Path.of(args.length > 0 ? args[0] : "build/playtest/plan10");
        Files.createDirectories(PlayTestDriver.out);
        SpaceColonyFrame[] frame = new SpaceColonyFrame[1];
        Engine[] eng = new Engine[1];
        SwingUtilities.invokeAndWait(() -> {
            Engine engine = new Engine(WorldGenerator.generate(42L));
            eng[0] = engine;
            var w = engine.world();
            w.randomEventsEnabled = false;
            var hub = w.findSite("site-earth-hub");
            hub.addBuilding(new Building(BuildingType.RESEARCH_LAB, 1));
            hub.addBuilding(new Building(BuildingType.FARM, 1));
            hub.addBuilding(new Building(BuildingType.FUEL_PLANT, 1));
            hub.stockpile.put(Resource.HYDROGEN, 120.0);
            w.tech.activeId = "ion-drives";
            engine.enqueue(new BuildShipCommand("c1", "Ark-1", ShipClass.COLONIZER, hub.id));
            for (int i = 0; i < 14; i++) engine.tick();
            w.tech.accumulatedPoints = 120;
            engine.setSelection(Selection.site(hub.id));
            frame[0] = new SpaceColonyFrame(engine);
            frame[0].setSize(1400, 900);
            frame[0].setVisible(true);
        });
        Thread.sleep(1200);
        PlayTestDriver.shot(frame[0].getRootPane(), "plan10-01-hub-progress");
        SwingUtilities.invokeAndWait(() -> eng[0].setSelection(Selection.ship("c1")));
        Thread.sleep(600);
        PlayTestDriver.shot(frame[0].getRootPane(), "plan10-02-ship-building");
        SwingUtilities.invokeAndWait(() -> frame[0].dispose());
        System.exit(0);
    }
}

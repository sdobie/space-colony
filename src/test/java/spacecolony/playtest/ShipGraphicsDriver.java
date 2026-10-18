package spacecolony.playtest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import javax.swing.SwingUtilities;
import spacecolony.engine.Engine;
import spacecolony.engine.Selection;
import spacecolony.sim.Resource;
import spacecolony.sim.Ship;
import spacecolony.sim.ShipClass;
import spacecolony.sim.ShipState;
import spacecolony.sim.Transit;
import spacecolony.ui.SpaceColonyFrame;
import spacecolony.world.WorldGenerator;

/**
 * Screenshots of every ship class on the system map and in the left column: two docked at
 * Earth Hub, two in flight and an explorer waiting in orbit. The ships are placed straight into
 * the world rather than built and flown, so the shot shows each pose at once. Run via
 * {@code ./gradlew shipGraphicsShot}; screenshots land in build/playtest/ships.
 */
public class ShipGraphicsDriver {
    public static void main(String[] args) throws Exception {
        PlayTestDriver.out = Path.of(args.length > 0 ? args[0] : "build/playtest/ships");
        Files.createDirectories(PlayTestDriver.out);
        SpaceColonyFrame[] frame = new SpaceColonyFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            Engine engine = new Engine(WorldGenerator.generate(42L));
            var w = engine.world();
            w.tick = 40;
            String hub = "site-earth-hub";
            w.ships.add(new Ship("h1", "Mule-1", ShipClass.HAULER, hub));
            Ship tanker = new Ship("t1", "Gulp-1", ShipClass.TANKER, hub);
            tanker.state = ShipState.LOADING;
            tanker.transit = new Transit(hub, hub, 0, Transit.PENDING_ARRIVAL_TICK, Map.of());
            w.ships.add(tanker);
            Ship col = new Ship("c1", "Ark-1", ShipClass.COLONIZER, hub);
            col.state = ShipState.IN_TRANSIT;
            col.currentSiteId = null;
            col.transit = new Transit(hub, null, "mars", 10, 90, Map.of(Resource.WATER, 40.0));
            w.ships.add(col);
            Ship scout = new Ship("x1", "Scout-1", ShipClass.EXPLORER, hub);
            scout.state = ShipState.IN_TRANSIT;
            scout.currentSiteId = null;
            scout.transit = new Transit(hub, null, "jovian", 0, 120, Map.of());
            w.ships.add(scout);
            Ship scout2 = new Ship("x2", "Scout-2", ShipClass.EXPLORER, hub);
            scout2.currentSiteId = null;
            scout2.orbitingBodyId = "venus";
            w.ships.add(scout2);
            engine.setSelection(Selection.ship("c1"));
            frame[0] = new SpaceColonyFrame(engine);
            frame[0].setSize(1400, 900);
            frame[0].setVisible(true);
        });
        Thread.sleep(1000);
        PlayTestDriver.shot(frame[0].getRootPane(), "ships-01-map");
        SwingUtilities.invokeAndWait(() -> frame[0].dispose());
        System.exit(0);
    }
}

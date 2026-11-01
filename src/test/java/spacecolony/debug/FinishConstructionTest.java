package spacecolony.debug;

import org.junit.jupiter.api.Test;
import spacecolony.sim.Building;
import spacecolony.sim.BuildingType;
import spacecolony.sim.Simulator;
import spacecolony.sim.Site;
import spacecolony.sim.World;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class FinishConstructionTest {
    @Test
    void everythingUnderWayFinishesNextTick() {
        World w = WorldGenerator.generate(1L);
        Site hub = w.findSite("site-earth-hub");
        Building shipyard = hub.addBuilding(new Building(BuildingType.SHIPYARD, 0));
        shipyard.daysLeft = 10;
        hub.buildings.get(1).daysLeft = 3;
        DebugActions.finishConstruction(w, hub);
        new Simulator().advance(w);
        assertTrue(hub.buildings.stream().noneMatch(Building::isUnderConstruction));
        assertEquals(1, shipyard.level);
        assertEquals(2, hub.buildings.get(1).level);
    }

    @Test
    void shipsUnderConstructionFinishNextTick() {
        World w = WorldGenerator.generate(1L);
        Site hub = w.findSite("site-earth-hub");
        Simulator sim = new Simulator();
        sim.enqueue(new spacecolony.sim.commands.BuildShipCommand("c1", "Ark", spacecolony.sim.ShipClass.COLONIZER, hub.id));
        sim.advance(w);
        DebugActions.finishConstruction(w, hub);
        sim.advance(w);
        assertEquals(spacecolony.sim.ShipState.IDLE, w.findShip("c1").state);
    }

    @Test
    void surveyAll_surveysEveryBodyOnce() {
        World w = WorldGenerator.generate(1L);
        DebugActions.surveyAll(w);
        assertEquals(w.bodies.size(), w.surveyedBodies.size());
        long events = w.recentEvents.stream().filter(e -> e.kind() == spacecolony.sim.EventKind.BODY_SURVEYED).count();
        assertEquals(w.bodies.size() - 1, events); // Earth was surveyed at start
    }
}

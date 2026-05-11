package spacecolony.sim;

import org.junit.jupiter.api.Test;
import spacecolony.sim.commands.*;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class CommandTest {
    @Test
    void buildBuildingCommand_addsBuildingToSite() {
        World w = WorldGenerator.generate(1L);
        Site earth = w.findSite("site-earth-hub");
        int before = earth.buildings.size();
        Simulator sim = new Simulator();
        sim.enqueue(new BuildBuildingCommand("site-earth-hub", BuildingType.RESEARCH_LAB));
        sim.advance(w);
        assertEquals(before + 1, earth.buildings.size());
        assertEquals(BuildingType.RESEARCH_LAB,
            earth.buildings.get(earth.buildings.size() - 1).type);
    }

    @Test
    void buildBuildingCommand_unknownSite_emitsRejectionEvent() {
        World w = WorldGenerator.generate(1L);
        Simulator sim = new Simulator();
        sim.enqueue(new BuildBuildingCommand("nonexistent", BuildingType.MINE));
        sim.advance(w);
        assertTrue(w.recentEvents.stream()
            .anyMatch(e -> e.kind() == EventKind.COMMAND_REJECTED));
    }

    @Test
    void buildShipCommand_createsIdleShip() {
        World w = WorldGenerator.generate(1L);
        Simulator sim = new Simulator();
        sim.enqueue(new BuildShipCommand("ship-1", "Hauler 1", ShipClass.HAULER, "site-earth-hub"));
        sim.advance(w);
        Ship s = w.findShip("ship-1");
        assertNotNull(s);
        assertEquals(ShipState.IDLE, s.state);
        assertEquals("site-earth-hub", s.currentSiteId);
    }

    @Test
    void retireShipCommand_removesShip() {
        World w = WorldGenerator.generate(1L);
        w.ships.add(new Ship("doomed", "Doomed", ShipClass.HAULER, "site-earth-hub"));
        Simulator sim = new Simulator();
        sim.enqueue(new RetireShipCommand("doomed"));
        sim.advance(w);
        assertNull(w.findShip("doomed"));
    }

    @Test
    void queueResearchCommand_setsActiveTech() {
        World w = WorldGenerator.generate(1L);
        Simulator sim = new Simulator();
        sim.enqueue(new QueueResearchCommand("basic-mining"));
        sim.advance(w);
        assertEquals("basic-mining", w.tech.activeId);
    }

    @Test
    void buildSiteCommand_addsSiteAndConsumesColonizer() {
        World w = WorldGenerator.generate(1L);
        // Place a colonizer at the Earth hub.
        Ship col = new Ship("col-1", "Pioneer", ShipClass.COLONIZER, "site-earth-hub");
        w.ships.add(col);
        int sitesBefore = w.findBody("earth").sites.size();
        Simulator sim = new Simulator();
        sim.enqueue(new BuildSiteCommand("site-earth-2", "Earth Outpost", "earth", 0.1, 0.2, "col-1"));
        sim.advance(w);
        assertEquals(sitesBefore + 1, w.findBody("earth").sites.size());
        assertNotNull(w.findSite("site-earth-2"));
        assertNull(w.findShip("col-1"), "Colonizer should be consumed");
    }

    @Test
    void buildSiteCommand_duplicateSiteId_isRejected() {
        World w = WorldGenerator.generate(1L);
        Ship col = new Ship("col-1", "Pioneer", ShipClass.COLONIZER, "site-earth-hub");
        w.ships.add(col);
        Simulator sim = new Simulator();
        sim.enqueue(new BuildSiteCommand("site-earth-hub", "Duplicate", "earth", 0.1, 0.2, "col-1"));
        sim.advance(w);
        assertTrue(w.recentEvents.stream()
            .anyMatch(e -> e.kind() == EventKind.COMMAND_REJECTED));
        assertNotNull(w.findShip("col-1"), "Rejected dispatch must not consume the colonizer");
    }

    @Test
    void dispatchShipCommand_unknownDest_isRejected() {
        World w = WorldGenerator.generate(1L);
        Ship h = new Ship("h-1", "H1", ShipClass.HAULER, "site-earth-hub");
        h.fuel = 1000.0;
        w.ships.add(h);
        Simulator sim = new Simulator();
        sim.enqueue(new DispatchShipCommand("h-1", "site-doesnt-exist", java.util.Map.of()));
        sim.advance(w);
        assertEquals(ShipState.IDLE, w.findShip("h-1").state);
        assertTrue(w.recentEvents.stream()
            .anyMatch(e -> e.kind() == EventKind.COMMAND_REJECTED));
    }
}

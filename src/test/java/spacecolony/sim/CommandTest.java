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
}

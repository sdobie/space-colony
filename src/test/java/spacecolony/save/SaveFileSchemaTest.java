package spacecolony.save;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import spacecolony.sim.Body;
import spacecolony.sim.Building;
import spacecolony.sim.BuildingType;
import spacecolony.sim.Resource;
import spacecolony.sim.Ship;
import spacecolony.sim.ShipClass;
import spacecolony.sim.ShipState;
import spacecolony.sim.Site;
import spacecolony.sim.Transit;
import spacecolony.sim.World;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

/** Plan 6 §6.4: schema v2 adds three fields and still reads v1. */
class SaveFileSchemaTest {
    static String v1Sample() throws Exception {
        try (InputStream in = SaveFileSchemaTest.class.getResourceAsStream("/saves/v1-sample.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test void v2_roundTripsTheNewFields() throws Exception {
        World w = WorldGenerator.generate(4L);
        w.randomEventsEnabled = false;
        Ship orbiting = new Ship("c1", "Ark", ShipClass.COLONIZER, null);
        orbiting.orbitingBodyId = "mars";
        orbiting.cargo.put(Resource.FOOD, 40.0);
        w.ships.add(orbiting);
        Ship loading = new Ship("c2", "Ark 2", ShipClass.COLONIZER, "site-earth-hub");
        loading.state = ShipState.LOADING;
        loading.transit = new Transit("site-earth-hub", null, "belt-a", 0L,
            Transit.PENDING_ARRIVAL_TICK, Transit.snapshot(Map.of(Resource.WATER, 20.0)));
        w.ships.add(loading);

        String json = SaveFile.toJson(w);
        World back = SaveFile.fromJson(json);
        assertEquals(json, SaveFile.toJson(back));
        assertFalse(back.randomEventsEnabled);
        assertEquals("mars", back.findShip("c1").orbitingBodyId);
        assertNull(back.findShip("c1").currentSiteId);
        assertNull(back.findShip("c2").transit.destSiteId());
        assertEquals("belt-a", back.findShip("c2").transit.destBodyId());
    }

    @Test void v1_loadsWithDefaults() throws Exception {
        String v1 = v1Sample();
        assertTrue(v1.contains("\"schemaVersion\": 1"));
        World w = SaveFile.fromJson(v1);
        assertTrue(w.randomEventsEnabled);
        assertFalse(w.ships.isEmpty());
        for (Ship s : w.ships) {
            assertNull(s.orbitingBodyId);
            if (s.transit != null) {
                assertNull(s.transit.destBodyId());
                assertNotNull(s.transit.destSiteId());
            }
        }
        assertTrue(w.ships.stream().anyMatch(s -> s.state == ShipState.IN_TRANSIT));
        assertTrue(SaveFile.toJson(w).contains("\"schemaVersion\": " + SaveFile.SCHEMA_VERSION));
    }

    @Test void newerSchema_rejected() throws Exception {
        String future = v1Sample().replace("\"schemaVersion\": 1", "\"schemaVersion\": " + (SaveFile.SCHEMA_VERSION + 1));
        IncompatibleSaveException ex = assertThrows(IncompatibleSaveException.class, () -> SaveFile.fromJson(future));
        assertEquals(SaveFile.SCHEMA_VERSION + 1, ex.fileSchemaVersion);
        assertEquals(SaveFile.SCHEMA_VERSION, ex.currentSchemaVersion);
    }

    @Test void v3_roundTripsConstruction() throws Exception {
        World w = WorldGenerator.generate(4L);
        Site hub = w.findSite("site-earth-hub");
        Building fresh = hub.addBuilding(new Building(BuildingType.FARM, 0));
        fresh.daysLeft = 2;
        hub.buildings.get(1).daysLeft = 3; // an upgrade under way
        String json = SaveFile.toJson(w);
        World back = SaveFile.fromJson(json);
        assertEquals(json, SaveFile.toJson(back));
        Site hub2 = back.findSite("site-earth-hub");
        assertEquals(6, hub2.buildings.get(5).id);
        assertEquals(0, hub2.buildings.get(5).level);
        assertEquals(2, hub2.buildings.get(5).daysLeft);
        assertEquals(3, hub2.buildings.get(1).daysLeft);
    }

    @Test void olderSaves_numberBuildingsInOrder() throws Exception {
        World w = SaveFile.fromJson(v1Sample());
        for (Body b : w.bodies) for (Site s : b.sites)
            for (int i = 0; i < s.buildings.size(); i++) {
                assertEquals(i + 1, s.buildings.get(i).id);
                assertEquals(0, s.buildings.get(i).daysLeft);
            }
    }
}

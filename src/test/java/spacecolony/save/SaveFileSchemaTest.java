package spacecolony.save;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import spacecolony.sim.Resource;
import spacecolony.sim.Ship;
import spacecolony.sim.ShipClass;
import spacecolony.sim.ShipState;
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
        String v3 = v1Sample().replace("\"schemaVersion\": 1", "\"schemaVersion\": 3");
        IncompatibleSaveException ex = assertThrows(IncompatibleSaveException.class, () -> SaveFile.fromJson(v3));
        assertEquals(3, ex.fileSchemaVersion);
        assertEquals(SaveFile.SCHEMA_VERSION, ex.currentSchemaVersion);
    }
}

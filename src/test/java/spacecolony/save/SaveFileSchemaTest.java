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

    /** Plan 9 §3.7: v4 saves surveys and orbit departures. */
    @Test void v4_roundTripsSurveysAndOrbitDepartures() throws Exception {
        World w = WorldGenerator.generate(4L);
        w.survey("mars", "Scout-1");
        Ship x = new Ship("x1", "Scout-1", ShipClass.EXPLORER, null);
        x.state = ShipState.IN_TRANSIT;
        x.setTank(Resource.HYDROGEN, 42.5);
        x.transit = new Transit(null, null, "belt-a", 3L, 9L, Transit.snapshot(x.cargo), "mars");
        w.ships.add(x);

        String json = SaveFile.toJson(w);
        assertTrue(json.contains("\"surveyedBodies\""));
        World back = SaveFile.fromJson(json);
        assertEquals(json, SaveFile.toJson(back));
        assertEquals(java.util.List.of("earth", "mars"), java.util.List.copyOf(back.surveyedBodies));
        Ship bx = back.findShip("x1");
        assertEquals(ShipClass.EXPLORER, bx.shipClass);
        assertNull(bx.transit.originSiteId());
        assertEquals("mars", bx.transit.originBodyId());
        assertEquals(42.5, bx.fuelValue());
    }

    @Test void v3_loadsWithSettledBodiesSurveyed() throws Exception {
        World w = WorldGenerator.generate(4L);
        w.findBody("belt-b").sites.add(new Site("site-b", "B Camp", "belt-b", 0.0, 0.0, 100));
        w.survey("mars", "Scout-1"); // dropped: v3 files carry no surveys
        String v3 = SaveFile.toJson(w)
            .replace("\"schemaVersion\": " + SaveFile.SCHEMA_VERSION, "\"schemaVersion\": 3")
            .replaceAll("\"surveyedBodies\": \\[[^\\]]*\\],\\s*", "");
        assertFalse(v3.contains("surveyedBodies"));
        World back = SaveFile.fromJson(v3);
        assertEquals(java.util.List.of("belt-b", "earth"), java.util.List.copyOf(back.surveyedBodies));
    }

    /** Plan 11 §6: v5 saves event damage causes, solar outages and impact dust. */
    @Test void v5_roundTripsEventState() throws Exception {
        World w = WorldGenerator.generate(4L);
        Site hub = w.findSite("site-earth-hub");
        hub.buildings.get(1).damage(spacecolony.sim.EventKind.METEOR_STRIKE, 77L);
        hub.buildings.get(3).offlineDays = 2;
        hub.dustDays = 14;
        hub.dustFactor = 0.75;
        String json = SaveFile.toJson(w);
        World back = SaveFile.fromJson(json);
        assertEquals(json, SaveFile.toJson(back));
        Site h = back.findSite("site-earth-hub");
        assertFalse(h.buildings.get(1).enabled);
        assertEquals(spacecolony.sim.EventKind.METEOR_STRIKE, h.buildings.get(1).damagedBy);
        assertEquals(77L, h.buildings.get(1).damagedTick);
        assertEquals(2, h.buildings.get(3).offlineDays);
        assertEquals(14, h.dustDays);
        assertEquals(0.75, h.dustFactor);
    }

    @Test void olderSaves_disabledPowerPlantLoadsAsOneDayOffline() throws Exception {
        World w = WorldGenerator.generate(4L);
        Site hub = w.findSite("site-earth-hub");
        Building plant = hub.buildings.get(3);
        assertEquals(BuildingType.POWER_PLANT, plant.type);
        plant.enabled = false;
        hub.buildings.get(1).enabled = false;
        String v4 = SaveFile.toJson(w).replace("\"schemaVersion\": " + SaveFile.SCHEMA_VERSION, "\"schemaVersion\": 4");
        assertTrue(v4.contains("\"schemaVersion\": 4"));
        Site h = SaveFile.fromJson(v4).findSite("site-earth-hub");
        assertTrue(h.buildings.get(3).enabled);
        assertEquals(1, h.buildings.get(3).offlineDays);
        assertFalse(h.buildings.get(1).enabled, "other damage stays");
        assertNull(h.buildings.get(1).damagedBy);
        assertEquals(0, h.dustDays);
        assertEquals(1.0, h.dustFactor);
    }

    /** Plan 10 §5: v6 saves a ship under construction and a two-fuel tank. */
    @Test void v6_roundTripsShipBuildsAndTanks() throws Exception {
        World w = WorldGenerator.generate(5L);
        Ship h = new Ship("h1", "Mule", ShipClass.HAULER, "site-earth-hub");
        h.state = ShipState.BUILDING;
        h.buildDaysLeft = 12;
        h.yardBuildingId = 5;
        h.setTank(Resource.HYDROGEN, 7.0);
        h.setTank(Resource.SYNFUEL, 9.0);
        w.ships.add(h);
        String json = SaveFile.toJson(w);
        World back = SaveFile.fromJson(json);
        assertEquals(json, SaveFile.toJson(back));
        Ship b = back.findShip("h1");
        assertEquals(ShipState.BUILDING, b.state);
        assertEquals(12, b.buildDaysLeft);
        assertEquals(5, b.yardBuildingId);
        assertEquals(9.0, b.tank.get(Resource.SYNFUEL));
    }

    /** Plan 10 §5: a v4 save's FUEL and ship "fuel" load as hydrogen. */
    @Test void v4_fuelLoadsAsHydrogen() throws Exception {
        World w = WorldGenerator.generate(5L);
        w.ships.add(new Ship("h1", "Mule", ShipClass.HAULER, "site-earth-hub"));
        String v4 = SaveFile.toJson(w)
            .replace("\"schemaVersion\": " + SaveFile.SCHEMA_VERSION, "\"schemaVersion\": 4")
            .replace("\"HYDROGEN\":", "\"FUEL\":")
            .replaceAll("\"SYNFUEL\": [0-9.E-]+,\\s*", "")
            .replaceAll("\"tank\": \\{[^}]*\\}", "\"fuel\": 30.0");
        assertTrue(v4.contains("\"fuel\": 30.0"), v4);
        World back = SaveFile.fromJson(v4);
        assertEquals(400.0, back.findSite("site-earth-hub").stockpile.get(Resource.HYDROGEN));
        assertEquals(30.0, back.findShip("h1").tank.get(Resource.HYDROGEN));
        assertEquals(ShipState.IDLE, back.findShip("h1").state);
    }
}

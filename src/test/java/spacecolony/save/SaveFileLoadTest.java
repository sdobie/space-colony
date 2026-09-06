package spacecolony.save;

import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import spacecolony.sim.Resource;
import spacecolony.sim.Ship;
import spacecolony.sim.ShipClass;
import spacecolony.sim.ShipState;
import spacecolony.sim.Simulator;
import spacecolony.sim.Site;
import spacecolony.sim.World;
import spacecolony.sim.commands.DispatchShipCommand;
import spacecolony.sim.commands.QueueResearchCommand;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class SaveFileLoadTest {

    @Test
    void roundTrip_freshWorld_preservesAllState(@TempDir Path tmp) throws Exception {
        World w = WorldGenerator.generate(7L);
        Path f = tmp.resolve("a.json");
        SaveFile.save(w, f);
        World loaded = SaveFile.load(f);
        assertEquals(w.tick, loaded.tick);
        assertEquals(w.seed, loaded.seed);
        assertEquals(w.credits, loaded.credits);
        assertEquals(w.bodies.size(), loaded.bodies.size());
        assertNotNull(loaded.findSite("site-earth-hub"));
    }

    @Test
    void roundTrip_afterSimulation_isDeterministic(@TempDir Path tmp) throws Exception {
        World w = WorldGenerator.generate(1L);
        Simulator sim = new Simulator();
        sim.enqueue(new QueueResearchCommand("basic-mining"));
        for (int i = 0; i < 500; i++) sim.advance(w);
        Path f = tmp.resolve("a.json");
        SaveFile.save(w, f);
        World loaded = SaveFile.load(f);

        // Advance both 100 more ticks; resulting state should match.
        Simulator s1 = new Simulator(), s2 = new Simulator();
        for (int i = 0; i < 100; i++) { s1.advance(w); s2.advance(loaded); }

        assertEquals(w.tick, loaded.tick);
        assertEquals(w.credits, loaded.credits);
        assertEquals(w.tech.researched, loaded.tech.researched);
        assertEquals(w.goals.achieved, loaded.goals.achieved);
        Site sa = w.findSite("site-earth-hub");
        Site sb = loaded.findSite("site-earth-hub");
        assertEquals(sa.population, sb.population);
        assertEquals(sa.morale, sb.morale, 1e-6);
        for (Resource r : Resource.values()) {
            if (!r.isStockpileable()) continue;
            assertEquals(sa.stockpile.get(r), sb.stockpile.get(r), 1e-3, "stockpile " + r);
        }
    }

    @Test
    void roundTrip_shipInTransit_preservesArrivalTick(@TempDir Path tmp) throws Exception {
        World w = WorldGenerator.generate(1L);
        // Plant a Mars site and a hauler with fuel; dispatch and let it depart.
        Site mars = new Site("site-mars-1", "Mars 1", "mars", 0.0, 0.0, 100);
        w.findBody("mars").sites.add(mars);
        Ship h = new Ship("h1", "H1", ShipClass.HAULER, "site-earth-hub");
        h.fuel = 1_000_000.0;
        w.ships.add(h);
        w.findSite("site-earth-hub").stockpile.put(Resource.METAL, 200.0);
        Simulator sim = new Simulator();
        sim.enqueue(new DispatchShipCommand("h1", "site-mars-1", Map.of(Resource.METAL, 50.0)));
        for (int i = 0; i < 200 && h.state != ShipState.IN_TRANSIT; i++) sim.advance(w);
        assertEquals(ShipState.IN_TRANSIT, h.state);

        Path f = tmp.resolve("transit.json");
        SaveFile.save(w, f);
        World loaded = SaveFile.load(f);
        Ship lh = loaded.findShip("h1");
        assertEquals(ShipState.IN_TRANSIT, lh.state);
        assertEquals(h.transit.arrivalTick(), lh.transit.arrivalTick());
        assertEquals(h.transit.destSiteId(), lh.transit.destSiteId());
    }

    @Test
    void incompatibleVersion_throws(@TempDir Path tmp) throws Exception {
        Path f = tmp.resolve("bad.json");
        Files.writeString(f, "{\"schemaVersion\": 99, \"seed\": 1, \"tick\": 0}");
        IncompatibleSaveException ex = assertThrows(IncompatibleSaveException.class,
            () -> SaveFile.load(f));
        assertEquals(99, ex.fileSchemaVersion);
        assertEquals(SaveFile.SCHEMA_VERSION, ex.currentSchemaVersion);
    }

    @Test
    void malformedJson_throws(@TempDir Path tmp) throws Exception {
        Path f = tmp.resolve("bad.json");
        Files.writeString(f, "{this is not json");
        assertThrows(JsonParseException.class, () -> SaveFile.load(f));
    }

    @Test
    void missingFile_throws(@TempDir Path tmp) {
        Path f = tmp.resolve("nope.json");
        assertThrows(NoSuchFileException.class, () -> SaveFile.load(f));
    }
}

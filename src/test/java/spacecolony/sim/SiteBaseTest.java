package spacecolony.sim;

import org.junit.jupiter.api.Test;
import spacecolony.sim.commands.BuildSiteCommand;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class SiteBaseTest {
    @Test
    void earthHub_siteBase_is200() {
        World w = WorldGenerator.generate(1L);
        Site earthHub = w.findSite("site-earth-hub");
        assertEquals(200, earthHub.siteBase);
        assertEquals(200, earthHub.populationCap, "initial cap equals siteBase before HABITAT boost is computed");
    }

    @Test
    void colonizerPlantedSite_siteBase_is100() {
        World w = WorldGenerator.generate(2L);
        // Inject a colonizer at Mars manually so we can fire BuildSiteCommand without
        // running a multi-tick transit.
        Ship colonizer = new Ship("colo-1", "Colo", ShipClass.COLONIZER, "site-mars-stub");
        Site marsStub = new Site("site-mars-stub", "Mars Stub", "mars", 0.1, 0.1, 100);
        w.findBody("mars").sites.add(marsStub);
        w.ships.add(colonizer);

        Simulator sim = new Simulator();
        sim.enqueue(new BuildSiteCommand("site-mars-new", "Mars New", "mars", 0.2, 0.2, "colo-1"));
        sim.advance(w);

        Site planted = w.findSite("site-mars-new");
        assertNotNull(planted);
        assertEquals(100, planted.siteBase);
        // CommandPhase plants every colonizer site with an L1 HABITAT, and ProductionPhase
        // recomputes the cap in this same tick: siteBase 100 + habitat boost 100 = 200.
        assertEquals(200, planted.populationCap);
    }
}

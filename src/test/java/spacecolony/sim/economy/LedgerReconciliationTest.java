package spacecolony.sim.economy;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import spacecolony.sim.Body;
import spacecolony.sim.EconomyScenario;
import spacecolony.sim.Resource;
import spacecolony.sim.SimPhase;
import spacecolony.sim.Site;
import spacecolony.sim.World;
import spacecolony.sim.economy.FlowSource.Shipping;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The ledger adds up: over production and departures (phases 4-5), every colony's stock
 * changes by exactly the sum of its day report's lines.
 */
class LedgerReconciliationTest {
    @Test
    void stockChangeEqualsReportNet() {
        for (long seed : new long[] {7777L, 42L}) {
            EconomyScenario sc = new EconomyScenario(seed);
            World w = sc.world;
            Map<String, Map<Resource, Double>> before = new HashMap<>();
            sc.sim.setPhaseObserver((tick, phase, nanos) -> {
                if (phase == SimPhase.ARRIVALS) {
                    before.clear();
                    for (Body b : w.bodies) for (Site s : b.sites) before.put(s.id, new EnumMap<>(s.stockpile));
                }
            });
            int shippingLines = 0;
            for (int i = 0; i < 500; i++) {
                sc.step();
                for (Body b : w.bodies) for (Site s : b.sites) {
                    Map<Resource, Double> was = before.get(s.id);
                    if (was == null) continue;
                    for (Resource r : Resource.values()) {
                        if (!r.isStockpileable()) continue;
                        double change = s.stockpile.get(r) - was.get(r);
                        assertEquals(change, s.lastDay.net(r), 1e-9,
                            "seed " + seed + " tick " + w.tick + " " + s.id + " " + r);
                    }
                    for (FlowLine l : s.lastDay.lines()) if (l.source() instanceof Shipping) shippingLines++;
                }
            }
            assertTrue(shippingLines > 0, "the scenario should ship something");
        }
    }
}

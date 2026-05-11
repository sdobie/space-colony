package spacecolony.sim;

import org.junit.jupiter.api.Test;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class EventsTest {

    @Test
    void givenEnoughTicks_someRandomEventFires() {
        World w = WorldGenerator.generate(1L);
        Simulator sim = new Simulator();
        for (int i = 0; i < 5000; i++) sim.advance(w);
        long random = w.recentEvents.stream()
            .filter(e -> e.kind() == EventKind.METEOR_STRIKE
                      || e.kind() == EventKind.SOLAR_FLARE
                      || e.kind() == EventKind.EQUIPMENT_FAILURE
                      || e.kind() == EventKind.DISEASE_OUTBREAK)
            .count();
        assertTrue(random > 0, "Expected at least one random event in 5000 ticks");
    }

    @Test
    void sameSeed_sameEventTimeline() {
        World a = WorldGenerator.generate(99L);
        World b = WorldGenerator.generate(99L);
        Simulator s1 = new Simulator();
        Simulator s2 = new Simulator();
        for (int i = 0; i < 2000; i++) { s1.advance(a); s2.advance(b); }
        // Compare event kinds and ticks.
        var aList = a.recentEvents.stream()
            .map(e -> e.tick() + ":" + e.kind()).toList();
        var bList = b.recentEvents.stream()
            .map(e -> e.tick() + ":" + e.kind()).toList();
        assertFalse(aList.isEmpty(), "Test seed should produce at least one event in 2000 ticks");
        assertEquals(aList, bList);
    }
}

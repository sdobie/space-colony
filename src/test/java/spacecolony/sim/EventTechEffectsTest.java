package spacecolony.sim;

import java.util.Random;
import org.junit.jupiter.api.Test;
import spacecolony.sim.phases.EventPhase;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class EventTechEffectsTest {

    @Test
    void medicine_halvesDiseasePopulationLoss() {
        int lossBase = applyDisease(false);
        int lossTech = applyDisease(true);
        // With medicine, loss should be ~half of base. Rounding via Math.max(1, ...) can
        // wobble by 1; allow a tight slack.
        assertEquals(lossBase / 2, lossTech, 1,
            "medicine should halve disease pop loss (base=" + lossBase + ", tech=" + lossTech + ")");
    }

    @Test
    void medicine_halvesDiseaseMoraleHit() {
        double moraleHitBase = moraleHitFromDisease(false);
        double moraleHitTech = moraleHitFromDisease(true);
        assertEquals(0.50, moraleHitTech / moraleHitBase, 1e-6);
    }

    private static int applyDisease(boolean medicine) {
        World w = WorldGenerator.generate(42L);
        if (medicine) w.tech.researched.add("medicine");
        Site s = w.findSite("site-earth-hub");
        s.population = 1000; // large so /10 dominates the rounding
        int before = s.population;
        EventPhase.applyForTest(w, w.findBody("earth"), EventKind.DISEASE_OUTBREAK, new Random(7L));
        return before - s.population;
    }

    private static double moraleHitFromDisease(boolean medicine) {
        World w = WorldGenerator.generate(42L);
        if (medicine) w.tech.researched.add("medicine");
        Site s = w.findSite("site-earth-hub");
        s.population = 1000;
        s.morale = 1.0;
        EventPhase.applyForTest(w, w.findBody("earth"), EventKind.DISEASE_OUTBREAK, new Random(7L));
        return 1.0 - s.morale;
    }
}

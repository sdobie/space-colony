package spacecolony.sim;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TechEffectsDeltaTest {

    @Test
    void unresearchedTech_reportsTodayVersusWithIt() {
        TechState t = new TechState();
        t.researched.add("ion-drives");
        List<TechEffects.Delta> d = TechEffects.deltas(t, "fusion-drives");
        assertEquals(1, d.size());
        assertEquals("Ship fuel cost", d.get(0).label());
        assertEquals(0.80, d.get(0).before(), 1e-9);
        assertEquals(0.56, d.get(0).after(), 1e-9);
    }

    @Test
    void researchedTech_reportsItsContribution() {
        TechState t = new TechState();
        t.researched.add("life-support-i");
        t.researched.add("life-support-ii");
        List<TechEffects.Delta> d = TechEffects.deltas(t, "life-support-ii");
        assertEquals(1, d.size());
        assertEquals("Morale ceiling", d.get(0).label());
        assertEquals(1.20, d.get(0).before(), 1e-9);
        assertEquals(1.56, d.get(0).after(), 1e-9);
    }

    @Test
    void multiEffectTech_listsEveryChangedNumber() {
        List<String> labels = TechEffects.deltas(new TechState(), "hydroponics")
            .stream().map(TechEffects.Delta::label).toList();
        assertEquals(List.of("Farm food output", "Farm water use"), labels);
    }

    @Test
    void flagTech_isMarkedAsFlag() {
        List<TechEffects.Delta> d = TechEffects.deltas(new TechState(), "atm-mining");
        assertEquals(1, d.size());
        assertTrue(d.get(0).flag());
        assertEquals(0.0, d.get(0).before());
        assertEquals(1.0, d.get(0).after());
    }

    @Test
    void everyCatalogTech_hasAtLeastOneDelta() {
        for (Tech tech : TechCatalog.all())
            assertFalse(TechEffects.deltas(new TechState(), tech.id()).isEmpty(), tech.id());
    }

    @Test
    void deltas_doNotMutateInput() {
        TechState t = new TechState();
        t.researched.add("basic-mining");
        TechEffects.deltas(t, "auto-mining");
        TechEffects.deltas(t, "basic-mining");
        assertEquals(java.util.Set.of("basic-mining"), t.researched);
    }
}

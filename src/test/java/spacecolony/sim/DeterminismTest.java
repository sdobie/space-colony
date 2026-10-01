package spacecolony.sim;

import org.junit.jupiter.api.Test;
import spacecolony.world.WorldGenerator;
import spacecolony.sim.commands.*;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class DeterminismTest {
    @Test
    void advance_incrementsTick() {
        World w = WorldGenerator.generate(1L);
        Simulator sim = new Simulator();
        long before = w.tick;
        sim.advance(w);
        assertEquals(before + 1, w.tick);
    }

    @Test
    void advance100_endsAtTick100() {
        World w = WorldGenerator.generate(1L);
        Simulator sim = new Simulator();
        for (int i = 0; i < 100; i++) sim.advance(w);
        assertEquals(100, w.tick);
    }

    @Test
    void fullEndToEnd_determinism() {
        World a = spacecolony.world.WorldGenerator.generate(7777L);
        World b = spacecolony.world.WorldGenerator.generate(7777L);
        Simulator s1 = new Simulator();
        Simulator s2 = new Simulator();
        // Same command stream into both sims at same ticks.
        s1.enqueue(new BuildBuildingCommand("site-earth-hub", BuildingType.RESEARCH_LAB));
        s2.enqueue(new BuildBuildingCommand("site-earth-hub", BuildingType.RESEARCH_LAB));
        s1.enqueue(new QueueResearchCommand("basic-mining"));
        s2.enqueue(new QueueResearchCommand("basic-mining"));
        for (int i = 0; i < 1000; i++) { s1.advance(a); s2.advance(b); }
        // Final state should match.
        assertEquals(a.tick, b.tick);
        assertEquals(a.credits, b.credits);
        assertEquals(a.tech.researched, b.tech.researched);
        assertEquals(a.goals.achieved, b.goals.achieved);
        Site sa = a.findSite("site-earth-hub");
        Site sb = b.findSite("site-earth-hub");
        assertEquals(sa.population, sb.population);
        assertEquals(sa.morale, sb.morale, 1e-9);
        for (Resource r : Resource.values()) {
            if (!r.isStockpileable()) continue;
            assertEquals(sa.stockpile.get(r), sb.stockpile.get(r), 1e-6, "stockpile " + r);
        }
    }

    @Test
    void techWiring_stillDeterministic() {
        World a = spacecolony.world.WorldGenerator.generate(7777L);
        World b = spacecolony.world.WorldGenerator.generate(7777L);
        Simulator s1 = new Simulator();
        Simulator s2 = new Simulator();
        s1.enqueue(new spacecolony.sim.commands.QueueResearchCommand("basic-mining"));
        s2.enqueue(new spacecolony.sim.commands.QueueResearchCommand("basic-mining"));
        for (int i = 0; i < 2000; i++) { s1.advance(a); s2.advance(b); }
        assertEquals(a.tech.researched, b.tech.researched);
        Site sa = a.findSite("site-earth-hub");
        Site sb = b.findSite("site-earth-hub");
        assertEquals(sa.population, sb.population);
        for (Resource r : Resource.values()) {
            if (!r.isStockpileable()) continue;
            assertEquals(sa.stockpile.get(r), sb.stockpile.get(r), 1e-6, "stockpile " + r);
        }
    }

    /** Plan 8: construction survives a save/load mid-way and replays identically. */
    @Test
    void construction_saveLoadReplay() throws Exception {
        World a = WorldGenerator.generate(7777L);
        World b = WorldGenerator.generate(7777L);
        Simulator s1 = new Simulator();
        Simulator s2 = new Simulator();
        for (int t = 0; t < 300; t++) {
            if (t == 150) b = spacecolony.save.SaveFile.fromJson(spacecolony.save.SaveFile.toJson(b));
            for (Command c : script(a, t)) s1.enqueue(c);
            for (Command c : script(b, t)) s2.enqueue(c);
            s1.advance(a);
            s2.advance(b);
        }
        assertEquals(spacecolony.save.SaveFile.toJson(a), spacecolony.save.SaveFile.toJson(b));
        assertTrue(a.findSite("site-earth-hub").buildings.stream().anyMatch(x -> x.type == BuildingType.FARM && x.level == 2));
    }

    /** Build, upgrade, repair after a forced meteor, demolish; stays mid-construction across tick 150. */
    private static java.util.List<Command> script(World w, int t) {
        String hub = "site-earth-hub";
        Site s = w.findSite(hub);
        java.util.List<Command> out = new java.util.ArrayList<>();
        switch (t) {
            case 0 -> out.add(new BuildBuildingCommand(hub, BuildingType.RESEARCH_LAB));
            case 148 -> out.add(new UpgradeBuildingCommand(hub, 2)); // the farm; finishes after the save
            case 200 -> {
                spacecolony.sim.phases.EventPhase.applyForced(w, w.findBody(s.bodyId), EventKind.METEOR_STRIKE,
                    DeterministicRng.forStep(w.seed, w.tick, spacecolony.sim.phases.EventPhase.DEBUG_STEP_ID));
                for (Building b : s.buildings)
                    if (!b.enabled && b.type != BuildingType.POWER_PLANT) out.add(new RepairBuildingCommand(hub, b.id));
            }
            case 250 -> out.add(new DemolishBuildingCommand(hub, 5)); // the shipyard
            default -> {}
        }
        return out;
    }
}

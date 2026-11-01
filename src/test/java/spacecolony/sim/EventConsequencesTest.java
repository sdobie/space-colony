package spacecolony.sim;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import spacecolony.sim.commands.RepairBuildingCommand;
import spacecolony.sim.phases.EventPhase;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

/** Plan 11: where events strike, what they cost, and what they say. */
class EventConsequencesTest {
    private static final String HUB = "site-earth-hub";

    private static World world() { return WorldGenerator.generate(42L); }

    private static Event last(World w) { return w.recentEvents.peekLast(); }

    private static int damaged(Site s) {
        int n = 0;
        for (Building b : s.buildings) if (!b.enabled) n++;
        return n;
    }

    private static Site emptyOutpost(World w, String bodyId) {
        Site s = new Site("site-empty", "Empty", bodyId, 0, 0, 100);
        w.findBody(bodyId).sites.add(s);
        return s;
    }

    // ===== Rolls =====

    @Test void bodyEventsOnlyStrikeColonies() {
        World w = world();
        Simulator sim = new Simulator();
        for (int i = 0; i < 6000; i++) sim.advance(w);
        int body = 0;
        for (Event e : w.recentEvents) {
            if (e.kind() == EventKind.SOLAR_FLARE || !EventPhase.randomKinds().contains(e.kind())) continue;
            body++;
            assertEquals(HUB, e.siteId(), e.message());
            assertEquals("earth", e.bodyId());
        }
        assertTrue(body > 5, "Earth Hub should see events in 6000 days, saw " + body);
    }

    @Test void colonyEventRateIsAboutOnePer250Days() {
        int events = 0;
        long days = 0;
        for (long seed = 1; seed <= 6; seed++) {
            World w = WorldGenerator.generate(seed);
            Site hub = w.findSite(HUB);
            for (int i = 0; i < 20000; i++) {
                // Keep the hub able to suffer every kind, and undo the damage so nothing compounds.
                hub.population = 100;
                for (Building b : hub.buildings) b.repair();
                int before = w.recentEvents.size();
                w.tick++;
                EventPhase.run(w);
                for (Event e : w.recentEvents.stream().skip(before).toList())
                    if (e.kind() != EventKind.SOLAR_FLARE) events++;
                while (w.recentEvents.size() > 100) w.recentEvents.removeFirst();
                days++;
            }
        }
        double rate = events / (double) days;
        assertEquals(EventPhase.COLONY_EVENT_RATE, rate, 0.0008, "events " + events + " in " + days + " days");
    }

    @Test void emptyOutpostGetsNothingThatCannotApply() {
        World w = world();
        w.findBody("earth").sites.clear();
        Site empty = emptyOutpost(w, "mars");
        for (int i = 0; i < 20000; i++) { w.tick++; EventPhase.run(w); }
        assertTrue(w.recentEvents.isEmpty(), "nothing to hit, so nothing fires: " + w.recentEvents.peekLast());
        assertEquals(0, empty.population);
    }

    @Test void noDiseaseWithoutPeopleAndNoFailureWithoutBuildings() {
        World w = world();
        w.findBody("earth").sites.clear();
        Site s = emptyOutpost(w, "mars");
        s.stockpile.put(Resource.ICE, 100.0);  // only a meteor can do anything here
        for (int i = 0; i < 30000; i++) { w.tick++; EventPhase.run(w); s.stockpile.put(Resource.ICE, 100.0); }
        assertFalse(w.recentEvents.isEmpty());
        for (Event e : w.recentEvents) assertEquals(EventKind.METEOR_STRIKE, e.kind(), e.message());
    }

    @Test void noFlareWithoutAPowerPlant() {
        World w = world();
        Site hub = w.findSite(HUB);
        hub.buildings.removeIf(b -> b.type == BuildingType.POWER_PLANT);
        for (int i = 0; i < 20000; i++) {
            w.tick++;
            EventPhase.run(w);
            while (w.recentEvents.size() > 100) w.recentEvents.removeFirst();
            assertNotEquals(EventKind.SOLAR_FLARE, w.recentEvents.isEmpty() ? null : last(w).kind());
        }
    }

    @Test void meteorSizeRollFollowsChances() {
        Random r = new Random(5);
        Map<MeteorSize, Integer> n = new EnumMap<>(MeteorSize.class);
        for (int i = 0; i < 100_000; i++) n.merge(MeteorSize.roll(r.nextDouble()), 1, Integer::sum);
        assertEquals(0.60, n.get(MeteorSize.MINOR) / 1e5, 0.01);
        assertEquals(0.30, n.get(MeteorSize.MAJOR) / 1e5, 0.01);
        assertEquals(0.10, n.get(MeteorSize.CATASTROPHIC) / 1e5, 0.01);
    }

    // ===== Meteor strikes =====

    private static World strike(MeteorSize size) {
        World w = world();
        EventPhase.applyForced(w, w.findSite(HUB), EventKind.METEOR_STRIKE, size, new Random(3));
        return w;
    }

    @Test void minorMeteor_oneBuildingAndATenthOfOneStock() {
        World before = world();
        World w = strike(MeteorSize.MINOR);
        Site hub = w.findSite(HUB);
        assertEquals(1, damaged(hub));
        assertEquals(100, hub.population);
        List<Resource> hit = lost(before.findSite(HUB), hub);
        assertEquals(1, hit.size());
        Resource r = hit.get(0);
        double had = before.findSite(HUB).stockpile.get(r);
        assertEquals(Math.floor(had * 0.1), had - hub.stockpile.get(r), 1e-9);
        assertEquals(0, hub.dustDays);
        assertEquals(EventSeverity.WARNING, last(w).severity());
        assertTrue(last(w).message().startsWith("Meteor strike at Earth Hub: "), last(w).message());
    }

    @Test void majorMeteor_twoBuildingsTwoStocksDeathsAndDust() {
        World before = world();
        World w = strike(MeteorSize.MAJOR);
        Site hub = w.findSite(HUB);
        assertEquals(2, damaged(hub));
        assertEquals(2, lost(before.findSite(HUB), hub).size());
        assertEquals(98, hub.population);
        assertEquals(20, hub.dustDays);
        assertEquals(0.75, hub.dustFactor, 1e-9);
        assertEquals(0.9, hub.morale, 1e-9);
        assertEquals(EventSeverity.ERROR, last(w).severity());
        String m = last(w).message();
        assertTrue(m.startsWith("Major meteor strike at Earth Hub: "), m);
        assertTrue(m.contains("2 colonists died"), m);
        assertTrue(m.contains("impact dust: farms at 75% for 20 days"), m);
    }

    @Test void catastrophicMeteor_threeBuildingsThreeStocksAndLongDust() {
        World before = world();
        World w = strike(MeteorSize.CATASTROPHIC);
        Site hub = w.findSite(HUB);
        assertEquals(3, damaged(hub));
        assertEquals(3, lost(before.findSite(HUB), hub).size());
        assertEquals(95, hub.population);
        assertEquals(60, hub.dustDays);
        assertEquals(0.5, hub.dustFactor, 1e-9);
        for (Building b : hub.buildings) if (!b.enabled) {
            assertEquals(EventKind.METEOR_STRIKE, b.damagedBy);
            assertEquals(w.tick, b.damagedTick);
        }
    }

    @Test void meteorNeverHitsDamagedOrUnbuiltBuildings() {
        World w = world();
        Site hub = w.findSite(HUB);
        hub.buildings.get(0).damage(EventKind.EQUIPMENT_FAILURE, 0);
        Building scaffold = hub.addBuilding(new Building(BuildingType.REFINERY, 0));
        scaffold.daysLeft = 4;
        EventPhase.applyForced(w, hub, EventKind.METEOR_STRIKE, MeteorSize.CATASTROPHIC, new Random(9));
        assertEquals(EventKind.EQUIPMENT_FAILURE, hub.buildings.get(0).damagedBy, "already damaged: untouched");
        assertTrue(scaffold.enabled, "under construction: too small a target");
        assertEquals(4, damaged(hub), "the old damage plus three new");
    }

    @Test void meteorDamagesAllWhenFewerBuildingsThanItsSize() {
        World w = world();
        Site hub = w.findSite(HUB);
        hub.buildings.subList(1, hub.buildings.size()).clear();
        EventPhase.applyForced(w, hub, EventKind.METEOR_STRIKE, MeteorSize.CATASTROPHIC, new Random(9));
        assertEquals(1, damaged(hub));
    }

    @Test void meteorWithNothingToHitSaysNoDamage() {
        World w = world();
        Site s = emptyOutpost(w, "mars");
        EventPhase.applyForced(w, s, EventKind.METEOR_STRIKE, MeteorSize.MAJOR, new Random(1));
        assertEquals("Major meteor strike at Empty: no damage", last(w).message());
    }

    @Test void noDustOnAirlessBodies() {
        World w = world();
        Site s = emptyOutpost(w, "belt-a");
        s.population = 10;
        s.addBuilding(new Building(BuildingType.FARM, 1));
        EventPhase.applyForced(w, s, EventKind.METEOR_STRIKE, MeteorSize.CATASTROPHIC, new Random(1));
        assertEquals(0, s.dustDays);
        assertFalse(last(w).message().contains("dust"), last(w).message());
    }

    @Test void dustCutsFarmFoodForItsDaysThenClears() {
        World w = world();
        w.randomEventsEnabled = false;
        Simulator sim = new Simulator();
        Site hub = w.findSite(HUB);
        sim.advance(w);
        double clear = farmFood(hub);
        EventPhase.applyForced(w, hub, EventKind.METEOR_STRIKE, MeteorSize.MAJOR, new Random(3));
        for (Building b : hub.buildings) b.repair();  // isolate the dust from the damage
        for (int d = 0; d < 20; d++) {
            sim.advance(w);
            assertEquals(clear * 0.75, farmFood(hub), 1e-9, "day " + d);
            assertEquals(spacecolony.sim.economy.Limit.DUST, farmOutcome(hub).limit());
        }
        assertEquals(0, hub.dustDays);
        assertEquals(1.0, hub.dustFactor, 1e-9);
        sim.advance(w);
        assertEquals(clear, farmFood(hub), 1e-9);
    }

    @Test void aSecondStrikeKeepsTheWorseDust() {
        World w = world();
        Site hub = w.findSite(HUB);
        hub.dustDays = 50;
        hub.dustFactor = 0.5;
        EventPhase.applyForced(w, hub, EventKind.METEOR_STRIKE, MeteorSize.MAJOR, new Random(3));
        assertEquals(50, hub.dustDays);
        assertEquals(0.5, hub.dustFactor, 1e-9);
        hub.dustDays = 5;
        EventPhase.applyForced(w, hub, EventKind.METEOR_STRIKE, MeteorSize.MAJOR, new Random(4));
        assertEquals(20, hub.dustDays);
        assertEquals(0.5, hub.dustFactor, 1e-9);
    }

    @Test void destroyedStockIsInTheLedger() {
        World w = world();
        w.randomEventsEnabled = false;
        new Simulator().advance(w);
        Site hub = w.findSite(HUB);
        double foodBefore = hub.lastDay.net(Resource.FOOD);
        Map<Resource, Double> before = new EnumMap<>(hub.stockpile);
        EventPhase.applyForced(w, hub, EventKind.METEOR_STRIKE, MeteorSize.MINOR, new Random(3));
        Resource r = lost(before, hub.stockpile).get(0);
        boolean line = hub.lastDay.linesFor(r).stream()
            .anyMatch(l -> l.source() instanceof spacecolony.sim.economy.FlowSource.EventLoss);
        assertTrue(line, "the ledger names the meteor loss");
        if (r != Resource.FOOD) assertEquals(foodBefore, hub.lastDay.net(Resource.FOOD), 1e-12);
    }

    // ===== Equipment failure, disease =====

    @Test void equipmentFailureAlwaysBreaksExactlyOne() {
        for (long seed = 0; seed < 200; seed++) {
            World w = world();
            Site hub = w.findSite(HUB);
            EventPhase.applyForced(w, hub, EventKind.EQUIPMENT_FAILURE, null, new Random(seed));
            assertEquals(1, damaged(hub), "seed " + seed);
            Building b = hub.buildings.stream().filter(x -> !x.enabled).findFirst().orElseThrow();
            assertEquals(EventKind.EQUIPMENT_FAILURE, b.damagedBy);
            assertTrue(last(w).message().matches("Equipment failure at Earth Hub: .+ L1 broke down \\(repair .+\\)"),
                last(w).message());
        }
    }

    @Test void diseaseKeepsItsNumbersAndSaysThem() {
        World w = world();
        Site hub = w.findSite(HUB);
        hub.population = 1000;
        EventPhase.applyForced(w, hub, EventKind.DISEASE_OUTBREAK, null, new Random(1));
        assertEquals(900, hub.population);
        assertEquals(0.8, hub.morale, 1e-9);
        assertEquals("Disease outbreak at Earth Hub: 100 colonists died; morale −20%", last(w).message());
        assertEquals(EventSeverity.ERROR, last(w).severity());
        assertEquals(HUB, last(w).siteId());
    }

    // ===== Solar flare =====

    @Test void flareDaysScaleWithSunDistance() {
        World w = world();
        assertEquals(6, EventPhase.flareDays(w, w.findBody("mercury")));
        assertEquals(3, EventPhase.flareDays(w, w.findBody("earth")));
        assertEquals(2, EventPhase.flareDays(w, w.findBody("mars")));
        assertEquals(1, EventPhase.flareDays(w, w.findBody("jovian")));
    }

    @Test void flareTakesPowerPlantsOfflineForTheirDaysThenTheyReturn() {
        World w = world();
        w.randomEventsEnabled = false;
        Simulator sim = new Simulator();
        Site hub = w.findSite(HUB);
        Building plant = hub.buildings.stream().filter(b -> b.type == BuildingType.POWER_PLANT).findFirst().orElseThrow();
        EventPhase.applyForced(w, w.findBody("earth"), EventKind.SOLAR_FLARE, new Random(1));
        assertEquals("Solar flare: power plants offline at Earth Hub for 3 days", last(w).message());
        assertTrue(plant.enabled, "offline is not damage");
        for (int d = 0; d < 3; d++) {
            sim.advance(w);
            assertEquals(0.0, hub.lastDay.powerFactor, 1e-9, "day " + d);
        }
        assertEquals(0, plant.offlineDays);
        sim.advance(w);
        assertEquals(1.0, hub.lastDay.powerFactor, 1e-9);
    }

    @Test void aSecondFlareKeepsTheLongerOutage() {
        World w = world();
        Building plant = w.findSite(HUB).buildings.stream()
            .filter(b -> b.type == BuildingType.POWER_PLANT).findFirst().orElseThrow();
        plant.offlineDays = 5;
        EventPhase.applyForced(w, w.findBody("earth"), EventKind.SOLAR_FLARE, new Random(1));
        assertEquals(5, plant.offlineDays);
    }

    @Test void damagedPowerPlantStaysOffUntilRepaired() {
        World w = world();
        w.randomEventsEnabled = false;
        Simulator sim = new Simulator();
        Site hub = w.findSite(HUB);
        Building plant = hub.buildings.stream().filter(b -> b.type == BuildingType.POWER_PLANT).findFirst().orElseThrow();
        plant.damage(EventKind.METEOR_STRIKE, 0);
        for (int d = 0; d < 5; d++) sim.advance(w);
        assertFalse(plant.enabled);
        double metal = hub.stockpile.get(Resource.METAL);
        sim.enqueue(new RepairBuildingCommand(HUB, plant.id));
        sim.advance(w);
        assertTrue(plant.enabled);
        assertNull(plant.damagedBy);
        assertTrue(hub.stockpile.get(Resource.METAL) < metal, "repair was paid for");
    }

    // ===== Messages =====

    @Test void effectTextJoinsWithAnd() {
        assertEquals("A", EventEffects.joinAnd(List.of("A")));
        assertEquals("A and B", EventEffects.joinAnd(List.of("A", "B")));
        assertEquals("A, B and C", EventEffects.joinAnd(List.of("A", "B", "C")));
    }

    @Test void effectTextSingulars() {
        World w = world();
        Site hub = w.findSite(HUB);
        EventEffects fx = new EventEffects();
        fx.died(1);
        fx.offline(hub, 1);
        assertEquals("Solar flare: 1 colonist died; power plants offline at Earth Hub for 1 day",
            fx.message("Solar flare", null, null));
        EventEffects none = new EventEffects();
        assertEquals("Meteor strike on Mars: no damage", none.message("Meteor strike", null, w.findBody("mars")));
    }

    @Test void multiBuildingMessageNamesThemWithoutCosts() {
        World w = strike(MeteorSize.MAJOR);
        String first = last(w).message().split(EventEffects.SEPARATOR)[0];
        assertTrue(first.matches("Major meteor strike at Earth Hub: \\w[\\w ]* L1 and \\w[\\w ]* L1 damaged"), first);
    }

    // ===== helpers =====

    private static List<Resource> lost(Site before, Site after) { return lost(before.stockpile, after.stockpile); }

    private static List<Resource> lost(Map<Resource, Double> before, Map<Resource, Double> after) {
        List<Resource> out = new ArrayList<>();
        for (Resource r : Resource.values())
            if (after.getOrDefault(r, 0.0) < before.getOrDefault(r, 0.0) - 1e-9) out.add(r);
        return out;
    }

    private static int farmIndex(Site s) {
        for (int i = 0; i < s.buildings.size(); i++) if (s.buildings.get(i).type == BuildingType.FARM) return i;
        throw new AssertionError("no farm");
    }

    private static double farmFood(Site s) {
        int i = farmIndex(s);
        return s.lastDay.linesOf(i).stream().filter(l -> l.resource() == Resource.FOOD)
            .mapToDouble(spacecolony.sim.economy.FlowLine::amount).sum();
    }

    private static spacecolony.sim.economy.BuildingOutcome farmOutcome(Site s) {
        return s.lastDay.outcome(farmIndex(s));
    }
}

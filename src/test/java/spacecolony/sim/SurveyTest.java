package spacecolony.sim;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import spacecolony.sim.commands.BuildShipCommand;
import spacecolony.sim.commands.BuildSiteCommand;
import spacecolony.sim.commands.DispatchShipCommand;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

/** Plan 9 §3.2: which bodies are surveyed, and when. */
class SurveyTest {
    static final String HUB = "site-earth-hub";

    private static List<Event> surveys(World w) {
        return w.recentEvents.stream().filter(e -> e.kind() == EventKind.BODY_SURVEYED).toList();
    }

    @Test void newWorld_onlyEarthSurveyed_noEvent() {
        World w = WorldGenerator.generate(42L);
        assertEquals(List.of("earth"), List.copyOf(w.surveyedBodies));
        assertTrue(w.isSurveyed("earth"));
        assertFalse(w.isSurveyed("mars"));
        assertTrue(surveys(w).isEmpty());
    }

    @Test void survey_emitsOnceWithBestSpots() {
        World w = WorldGenerator.generate(42L);
        assertTrue(w.survey("mars", "Scout-1"));
        assertFalse(w.survey("mars", "Scout-1"));
        assertFalse(w.survey("pluto", "Scout-1"));
        List<Event> ev = surveys(w);
        assertEquals(1, ev.size());
        assertEquals("mars", ev.get(0).bodyId());
        assertEquals(EventSeverity.INFO, ev.get(0).severity());
        assertTrue(ev.get(0).message().startsWith("Scout-1 surveyed Mars. Best spots: ORE .8"), ev.get(0).message());
    }

    @Test void explorer_arrivingInOrbit_surveysTheBody() {
        World w = ExplorerTransitTest.withExplorer();
        Simulator sim = new Simulator();
        sim.enqueue(DispatchShipCommand.toBody("x1", "mars", Map.of()));
        for (int i = 0; i < 60 && w.findShip("x1").orbitingBodyId == null; i++) sim.advance(w);
        assertEquals("mars", w.findShip("x1").orbitingBodyId);
        assertTrue(w.isSurveyed("mars"));
        assertEquals(1, surveys(w).size());
        assertTrue(w.recentEvents.stream().anyMatch(e -> e.message().equals("Explorer Scout-1 is orbiting Mars")));
    }

    @Test void explorer_arrivingAtASite_surveysItsBody() {
        World w = ExplorerTransitTest.withExplorer();
        Site belt = new Site("site-belt", "Belt Camp", "belt-a", 0.1, 0.2, 100);
        w.findBody("belt-a").sites.add(belt);
        Simulator sim = new Simulator();
        sim.enqueue(new DispatchShipCommand("x1", "site-belt", Map.of()));
        for (int i = 0; i < 60 && !w.isSurveyed("belt-a"); i++) sim.advance(w);
        assertTrue(w.isSurveyed("belt-a"));
    }

    @Test void colonizer_inOrbit_doesNotSurvey_butFoundingDoes() {
        World w = WorldGenerator.generate(1L);
        w.findSite(HUB).stockpile.put(Resource.FUEL, 1_000.0);
        Simulator sim = new Simulator();
        sim.enqueue(new BuildShipCommand("c1", "Ark", ShipClass.COLONIZER, HUB));
        sim.advance(w);
        sim.enqueue(DispatchShipCommand.toBody("c1", "mars", Map.of()));
        for (int i = 0; i < 60 && w.findShip("c1").orbitingBodyId == null; i++) sim.advance(w);
        assertEquals("mars", w.findShip("c1").orbitingBodyId);
        assertFalse(w.isSurveyed("mars"));
        sim.enqueue(new BuildSiteCommand("site-mars", "Pavonis", "mars", 0.1, 0.2, "c1"));
        sim.advance(w);
        assertNotNull(w.findSite("site-mars"));
        assertTrue(w.isSurveyed("mars"));
    }
}

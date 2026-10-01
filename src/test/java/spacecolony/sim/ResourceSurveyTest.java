package spacecolony.sim;

import java.util.List;
import org.junit.jupiter.api.Test;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

/** Plan 9 §3.3: a body's survey is its average and best yield per ground resource. */
class ResourceSurveyTest {
    private static final World W = WorldGenerator.generate(42L);

    @Test void earth_listsRockyResourcesBestFirst() {
        List<ResourceSurvey.Entry> e = ResourceSurvey.of(W.findBody("earth"));
        assertEquals(List.of(Resource.ORE, Resource.SILICATE, Resource.BIOMASS, Resource.ICE),
            e.stream().map(ResourceSurvey.Entry::resource).toList());
        for (int i = 1; i < e.size(); i++) assertTrue(e.get(i - 1).best() >= e.get(i).best());
    }

    @Test void entries_matchBruteForceGrid() {
        Body earth = W.findBody("earth");
        for (ResourceSurvey.Entry e : ResourceSurvey.of(earth)) {
            double sum = 0, best = 0;
            for (int i = 0; i < 8; i++) for (int j = 0; j < 16; j++) {
                double y = earth.resourceYields.sample(e.resource(), ResourceSurvey.gridLat(i), ResourceSurvey.gridLon(j));
                sum += y;
                best = Math.max(best, y);
            }
            assertEquals(sum / 128, e.average(), 1e-12);
            assertEquals(best, e.best(), 1e-12);
            assertEquals(best, earth.resourceYields.sample(e.resource(), e.bestLat(), e.bestLon()), 1e-12);
        }
    }

    @Test void gasGiant_onlyFuel_iceBody_iceFirst() {
        assertEquals(List.of(Resource.FUEL), ResourceSurvey.of(W.findBody("jovian")).stream()
            .map(ResourceSurvey.Entry::resource).toList());
        assertEquals(Resource.ICE, ResourceSurvey.of(W.findBody("europa")).get(0).resource());
    }

    @Test void fuel_onlyOnGasGiants() {
        for (Body b : W.bodies) {
            boolean fuel = ResourceSurvey.of(b).stream().anyMatch(e -> e.resource() == Resource.FUEL);
            assertEquals(b.type == BodyType.GAS_GIANT, fuel, b.id);
        }
    }

    @Test void nothingBelowTheNoneThreshold() {
        for (Body b : W.bodies)
            for (ResourceSurvey.Entry e : ResourceSurvey.of(b))
                assertTrue(e.best() >= ResourceSurvey.NONE_BELOW, b.id + " " + e.resource());
    }

    @Test void ratingThresholds() {
        assertEquals(ResourceSurvey.Rating.RICH, ResourceSurvey.rating(0.6));
        assertEquals(ResourceSurvey.Rating.GOOD, ResourceSurvey.rating(0.59));
        assertEquals(ResourceSurvey.Rating.GOOD, ResourceSurvey.rating(0.4));
        assertEquals(ResourceSurvey.Rating.FAIR, ResourceSurvey.rating(0.2));
        assertEquals(ResourceSurvey.Rating.POOR, ResourceSurvey.rating(0.19));
    }

    @Test void bodyWithoutYields_isEmpty() {
        Body bare = new Body("x", "X", BodyType.ROCKY, new Orbit(1, 1, 0, null), 1, 1, 0L, null);
        assertTrue(ResourceSurvey.of(bare).isEmpty());
        assertTrue(ResourceSurvey.at(bare, 0, 0).isEmpty());
    }

    @Test void at_givesTheSpotsYields() {
        Body earth = W.findBody("earth");
        for (ResourceSurvey.Entry e : ResourceSurvey.at(earth, 0.3, -1.2))
            assertEquals(earth.resourceYields.sample(e.resource(), 0.3, -1.2), e.best(), 1e-12);
    }

    @Test void fmt_dropsLeadingZero() {
        assertEquals(".83", ResourceSurvey.fmt(0.834));
        assertEquals("1.00", ResourceSurvey.fmt(1.0));
    }
}

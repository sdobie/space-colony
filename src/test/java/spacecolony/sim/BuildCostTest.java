package spacecolony.sim;

import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BuildCostTest {
    private static Map<Resource, Double> stock(double metal, double comps) {
        Map<Resource, Double> m = new EnumMap<>(Resource.class);
        m.put(Resource.METAL, metal);
        m.put(Resource.COMPONENTS, comps);
        return m;
    }

    @Test void affordableAtExactlyTheCost() {
        BuildCost c = BuildCost.of(15, 5, 3);
        assertTrue(c.affordable(stock(15, 5)));
        assertFalse(c.affordable(stock(14.5, 5)));
    }

    @Test void shortfallListsOnlyWhatIsMissing() {
        Map<Resource, Double> s = BuildCost.of(15, 5, 3).shortfall(stock(10, 9));
        assertEquals(Map.of(Resource.METAL, 5.0), s);
    }

    @Test void scaledRoundsUpPerResource() {
        BuildCost c = BuildCost.of(15, 5, 3).scaled(1.5);
        assertEquals(23.0, c.resources().get(Resource.METAL));
        assertEquals(8.0, c.resources().get(Resource.COMPONENTS));
        assertEquals(3, c.days());
        assertEquals(15.0, BuildCost.of(15, 5, 3).scaled(1.0).resources().get(Resource.METAL));
    }

    @Test void describe() {
        assertEquals("15 METAL, 5 COMPONENTS · 3 days", BuildCost.of(15, 5, 3).describe());
        assertEquals("15 METAL, 5 COMPONENTS · 1 day", BuildCost.of(15, 5, 1).describe());
        assertEquals("4 METAL, 2 COMPONENTS", BuildCost.of(4, 2, 0).describe());
    }
}

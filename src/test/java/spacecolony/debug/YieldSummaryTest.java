package spacecolony.debug;

import java.util.List;
import org.junit.jupiter.api.Test;
import spacecolony.sim.Body;
import spacecolony.sim.Resource;
import spacecolony.sim.World;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class YieldSummaryTest {
    @Test void topThree_sortedDescending_andCached() {
        World w = WorldGenerator.generate(1L);
        YieldSummary ys = new YieldSummary();
        Body mars = w.findBody("mars");
        List<YieldSummary.Entry> top = ys.top(mars, 3);
        assertEquals(3, top.size());
        assertTrue(top.get(0).mean() >= top.get(1).mean() && top.get(1).mean() >= top.get(2).mean());
        assertSame(top, ys.top(mars, 3));
        ys.clear();
        assertNotSame(top, ys.top(mars, 3));
    }

    @Test void format_usesThreeLetterNamesAndDropsLeadingZero() {
        String s = YieldSummary.format(List.of(
            new YieldSummary.Entry(Resource.ORE, 0.62), new YieldSummary.Entry(Resource.ICE, 0.41)));
        assertEquals("ORE .62 · ICE .41", s);
    }
}

package spacecolony.ui.dialogs;

import java.util.List;
import org.junit.jupiter.api.Test;
import spacecolony.sim.Body;
import spacecolony.sim.Resource;
import spacecolony.sim.World;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

/** Plan 9 §6: the place-site dialog shows the clicked spot's yields once the body is surveyed. */
class PlaceSiteDialogTest {
    @Test void unsurveyed_saysUnknown() {
        World w = WorldGenerator.generate(42L);
        List<String[]> rows = PlaceSiteDialog.yieldRows(w, w.findBody("mars"), 0.2, 1.0);
        assertEquals(1, rows.size());
        assertEquals(PlaceSiteDialog.UNKNOWN_YIELDS, rows.get(0)[1]);
    }

    @Test void surveyed_listsTheSpotsYields() {
        World w = WorldGenerator.generate(42L);
        w.survey("mars", "Test");
        Body mars = w.findBody("mars");
        List<String[]> rows = PlaceSiteDialog.yieldRows(w, mars, 0.2, 1.0);
        assertEquals("ORE:", rows.get(0)[0]);
        double ore = mars.resourceYields.sample(Resource.ORE, 0.2, 1.0);
        assertTrue(rows.get(0)[1].endsWith(String.format("%.2f", ore).substring(1)), rows.get(0)[1]);
        assertTrue(rows.stream().noneMatch(r -> r[0].equals("FUEL:") && r[1].endsWith(".00")));
    }
}

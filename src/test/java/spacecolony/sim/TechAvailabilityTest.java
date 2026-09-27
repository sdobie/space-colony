package spacecolony.sim;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TechAvailabilityTest {
    @Test
    void rootTechs_areTierZero_andAvailable() {
        TechState s = new TechState();
        Tech ion = TechCatalog.get("ion-drives");
        assertEquals(0, TechAvailability.tier(ion));
        assertTrue(TechAvailability.prereqsMet(s, ion));
    }

    @Test
    void antimatter_isTierTwo() {
        assertEquals(1, TechAvailability.tier(TechCatalog.get("fusion-drives")));
        assertEquals(2, TechAvailability.tier(TechCatalog.get("antimatter")));
    }

    @Test
    void missingPrereqs_listsUnresearched() {
        TechState s = new TechState();
        Tech fusion = TechCatalog.get("fusion-drives");
        assertFalse(TechAvailability.prereqsMet(s, fusion));
        assertEquals(List.of("ion-drives"), TechAvailability.missingPrereqs(s, fusion));
        s.researched.add("ion-drives");
        assertTrue(TechAvailability.prereqsMet(s, fusion));
        assertEquals(List.of(), TechAvailability.missingPrereqs(s, fusion));
    }
}

package spacecolony.sim;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PopCapBreakdownTest {
    private static Site site(int base, int... habitatLevels) {
        Site s = new Site("s", "S", "earth", 0, 0, base);
        for (int l : habitatLevels) s.buildings.add(new Building(BuildingType.HABITAT, l));
        return s;
    }
    private static TechState tech(String... ids) {
        TechState t = new TechState();
        for (String id : ids) t.researched.add(id);
        return t;
    }

    @Test void l1_noTech()        { assertEquals(300,  PopCapBreakdown.of(site(200, 1), tech()).cap()); }
    @Test void l1l1_noTech()      { assertEquals(400,  PopCapBreakdown.of(site(200, 1, 1), tech()).cap()); }
    @Test void l1_mgmtI()         { assertEquals(360,  PopCapBreakdown.of(site(200, 1), tech("colony-mgmt-i")).cap()); }
    @Test void l1_bothMgmt()      { assertEquals(468,  PopCapBreakdown.of(site(200, 1), tech("colony-mgmt-i", "colony-mgmt-ii")).cap()); }
    @Test void l1l2l1_bothMgmt()  { assertEquals(936,  PopCapBreakdown.of(site(200, 1, 2, 1), tech("colony-mgmt-i", "colony-mgmt-ii")).cap()); }
    @Test void colonizer_l1l2l1() { assertEquals(780,  PopCapBreakdown.of(site(100, 1, 2, 1), tech("colony-mgmt-i", "colony-mgmt-ii")).cap()); }

    @Test
    void components_areExposed() {
        Site s = site(200, 2);
        s.buildings.add(new Building(BuildingType.HABITAT, 1));
        s.buildings.get(1).enabled = false;
        PopCapBreakdown b = PopCapBreakdown.of(s, tech("colony-mgmt-i"));
        assertEquals(200, b.siteBase());
        assertEquals(200, b.habitatBoost());   // disabled L1 excluded
        assertEquals(1.2, b.techMultiplier(), 1e-12);
    }
}

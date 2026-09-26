package spacecolony.debug;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import spacecolony.sim.Building;
import spacecolony.sim.BuildingType;
import spacecolony.sim.Resource;
import spacecolony.sim.Ship;
import spacecolony.sim.ShipState;
import spacecolony.sim.Site;
import spacecolony.sim.World;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class ObjectInspectorTest {
    @SuppressWarnings("unused")
    static class Secretive {
        private int hidden = 7;
        private final String fixed = "f";
        Secretive self = this;
        List<Object> items = new ArrayList<>(List.of(1, "two"));
        int[] numbers = { 3, 4 };
    }

    @Test
    void includesPrivateFieldsAndMarksFinalsReadOnly() {
        ObjectInspector.Node root = ObjectInspector.inspect("s", new Secretive());
        ObjectInspector.Node hidden = root.child("hidden");
        assertNotNull(hidden);
        assertEquals(7, hidden.value());
        assertTrue(hidden.isEditable());
        ObjectInspector.Node fixed = root.child("fixed");
        assertEquals("f", fixed.value());
        assertFalse(fixed.isEditable());
    }

    @Test
    void cyclesAreCutNotFollowed() {
        ObjectInspector.Node root = ObjectInspector.inspect("s", new Secretive());
        ObjectInspector.Node self = root.child("self");
        assertTrue(self.toString().contains("(cycle)"));
        assertTrue(self.children().isEmpty());
    }

    @Test
    void expandsListsAndArraysAndEditsElements() {
        Secretive s = new Secretive();
        ObjectInspector.Node root = ObjectInspector.inspect("s", s);
        ObjectInspector.Node items = root.child("items");
        assertEquals(2, items.children().size());
        ObjectInspector.set(items.child("[0]"), "42");
        assertEquals(42, s.items.get(0));
        ObjectInspector.set(root.child("numbers").child("[1]"), "9");
        assertEquals(9, s.numbers[1]);
    }

    @Test
    void editsPrimitiveFieldOnSite() {
        World w = WorldGenerator.generate(1L);
        Site site = w.findSite("site-earth-hub");
        ObjectInspector.Node root = ObjectInspector.inspect("site", site);
        ObjectInspector.set(root.child("population"), "1234");
        assertEquals(1234, site.population);
        ObjectInspector.set(root.child("morale"), "0.5");
        assertEquals(0.5, site.morale, 1e-12);
        ObjectInspector.set(root.child("name"), "Renamed Hub");
        assertEquals("Renamed Hub", site.name);
    }

    @Test
    void editsEnumMapValuesAndEnumFields() {
        World w = WorldGenerator.generate(1L);
        Site site = w.findSite("site-earth-hub");
        ObjectInspector.Node stock = ObjectInspector.inspect("site", site).child("stockpile");
        ObjectInspector.set(stock.child(Resource.FOOD.name()), "777");
        assertEquals(777.0, site.stockpile.get(Resource.FOOD), 1e-12);

        Ship ship = new Ship("s1", "Test", spacecolony.sim.ShipClass.values()[0], "site-earth-hub");
        ObjectInspector.set(ObjectInspector.inspect("ship", ship).child("state"), "LOADING");
        assertEquals(ShipState.LOADING, ship.state);

        Building b = new Building(BuildingType.MINE, 1);
        ObjectInspector.set(ObjectInspector.inspect("b", b).child("enabled"), "false");
        assertFalse(b.enabled);
    }

    @Test
    void rejectsBadInputAndReadOnlyNodes() {
        World w = WorldGenerator.generate(1L);
        Site site = w.findSite("site-earth-hub");
        ObjectInspector.Node root = ObjectInspector.inspect("site", site);
        assertThrows(IllegalArgumentException.class, () -> ObjectInspector.set(root.child("population"), "lots"));
        assertThrows(IllegalArgumentException.class,
            () -> ObjectInspector.set(ObjectInspector.inspect("b", new Building(BuildingType.MINE, 1)).child("enabled"), "yes"));
        assertThrows(IllegalStateException.class, () -> ObjectInspector.set(root.child("id"), "x"));
        assertThrows(IllegalStateException.class, () -> ObjectInspector.set(root, "x"));
    }

    @Test
    void wholeWorldReflectsWithoutBlowingUp() {
        World w = WorldGenerator.generate(1L);
        ObjectInspector.Node root = ObjectInspector.inspect("world", w);
        assertNotNull(root.child("bodies"));
        assertEquals(w.bodies.size(), root.child("bodies").children().size());
        assertTrue(root.child("tick").isEditable());
        assertFalse(root.child("seed").isEditable());
        ObjectInspector.Node yields = root.child("bodies").child("[0]").child("resourceYields");
        assertTrue(yields.children().isEmpty());
        assertTrue(yields.toString().endsWith("<sampler>"));
    }

    @Test
    void nullRootIsALeaf() {
        ObjectInspector.Node root = ObjectInspector.inspect("gone", null);
        assertTrue(root.children().isEmpty());
        assertTrue(root.toString().endsWith("= null"));
    }
}

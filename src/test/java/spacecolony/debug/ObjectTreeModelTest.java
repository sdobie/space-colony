package spacecolony.debug;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import spacecolony.sim.Building;
import spacecolony.sim.BuildingType;
import spacecolony.sim.Ship;
import spacecolony.sim.ShipClass;
import spacecolony.sim.ShipState;
import spacecolony.sim.Site;
import spacecolony.sim.World;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class ObjectTreeModelTest {
    static final class Loop { Loop self; int n = 3; }

    @SuppressWarnings("unused")
    static final class Big {
        private int hidden = 7;
        List<Integer> many = new ArrayList<>();
        Big() { for (int i = 0; i < ObjectTreeModel.MAX_CHILDREN + 5; i++) many.add(i); }
    }

    @Test void site_exposesFields_withEditability() {
        Site s = new Site("s", "S", "earth", 0, 0, 200);
        ObjectTreeModel m = new ObjectTreeModel(s, "site");
        ObjectTreeModel.Node pop = m.child(m.root(), "population");
        ObjectTreeModel.Node base = m.child(m.root(), "siteBase");
        assertTrue(pop.isEditableLeaf());
        assertFalse(base.isEditableLeaf(), "final field");
        assertEquals("200", base.valueText());
    }

    @Test void privateFieldsAreIncluded() {
        ObjectTreeModel m = new ObjectTreeModel(new Big(), "big");
        ObjectTreeModel.Node hidden = m.child(m.root(), "hidden");
        assertEquals(7, hidden.value());
        assertTrue(hidden.isEditableLeaf());
    }

    @Test void map_childrenAreKeys() {
        Site s = new Site("s", "S", "earth", 0, 0, 200);
        ObjectTreeModel m = new ObjectTreeModel(s, "site");
        ObjectTreeModel.Node stock = m.child(m.root(), "stockpile");
        assertNotNull(m.child(stock, "[FOOD]"));
    }

    @Test void cycle_rendersAsBackReference() {
        Loop l = new Loop(); l.self = l;
        ObjectTreeModel m = new ObjectTreeModel(l, "loop");
        ObjectTreeModel.Node self = m.child(m.root(), "self");
        assertTrue(self.label().startsWith("self ↺"), self.label());
        assertEquals(0, m.getChildCount(self));
    }

    @Test void setValue_parsesAndWrites() throws Exception {
        Site s = new Site("s", "S", "earth", 0, 0, 200);
        ObjectTreeModel m = new ObjectTreeModel(s, "site");
        m.setValue(m.child(m.root(), "population"), "321");
        assertEquals(321, s.population);
        m.setValue(m.child(m.root(), "morale"), "0.5");
        assertEquals(0.5, s.morale);
        assertThrows(IllegalArgumentException.class, () -> m.setValue(m.child(m.root(), "siteBase"), "1"));
        assertThrows(NumberFormatException.class, () -> m.setValue(m.child(m.root(), "population"), "lots"));
    }

    @Test void enumField_listsConstantsAndEdits() {
        Building b = new Building(BuildingType.MINE, 1);
        ObjectTreeModel m = new ObjectTreeModel(b, "b");
        ObjectTreeModel.Node type = m.child(m.root(), "type");
        assertFalse(type.isEditableLeaf(), "final");

        Ship ship = new Ship("s1", "S1", ShipClass.HAULER, "site-earth-hub");
        ObjectTreeModel sm = new ObjectTreeModel(ship, "ship");
        ObjectTreeModel.Node state = sm.child(sm.root(), "state");
        assertTrue(state.isEditableLeaf());
        assertArrayEquals(ShipState.values(), state.enumConstants());
        sm.setValue(state, "LOADING");
        assertEquals(ShipState.LOADING, ship.state);
    }

    @Test void recordsAndSamplers() {
        World w = WorldGenerator.generate(1L);
        ObjectTreeModel m = new ObjectTreeModel(w, "world");
        ObjectTreeModel.Node earth = m.child(m.child(m.root(), "bodies"), "[2]");
        assertNotNull(earth);
        ObjectTreeModel.Node yields = m.child(earth, "resourceYields");
        assertEquals("<sampler>", yields.valueText());
        assertEquals(0, m.getChildCount(yields));
        ObjectTreeModel.Node orbit = m.child(earth, "orbit");
        assertNotNull(m.child(orbit, "period"), "record components are children");
    }

    @Test void largeCollections_areCappedWithMoreLeaf() {
        ObjectTreeModel m = new ObjectTreeModel(new Big(), "big");
        ObjectTreeModel.Node many = m.child(m.root(), "many");
        assertEquals(ObjectTreeModel.MAX_CHILDREN + 1, m.getChildCount(many));
        assertEquals("… 5 more", ((ObjectTreeModel.Node) m.getChild(many, ObjectTreeModel.MAX_CHILDREN)).name());
    }

    @Test void refresh_rereadsLiveValues_andFindKeepsPaths() {
        Site s = new Site("s", "S", "earth", 0, 0, 200);
        ObjectTreeModel m = new ObjectTreeModel(s, "site");
        ObjectTreeModel.Node pop = m.child(m.root(), "population");
        s.population = 55;
        assertEquals(0, pop.value(), "snapshot until refresh");
        m.refresh();
        ObjectTreeModel.Node again = m.find(pop.path());
        assertEquals(55, again.value());
        assertEquals("population", again.pathString());
        assertNotNull(m.treePath(again));
    }
}

package spacecolony.ui;

import java.awt.Component;
import java.util.List;
import java.awt.Container;
import javax.swing.JButton;
import org.junit.jupiter.api.Test;
import spacecolony.engine.Engine;
import spacecolony.engine.Selection;
import spacecolony.sim.Body;
import spacecolony.sim.Resource;
import spacecolony.sim.ResourceSurvey;
import spacecolony.sim.Ship;
import spacecolony.sim.ShipClass;
import spacecolony.sim.World;
import spacecolony.testutil.Edt;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

/** Plan 9 §4 and §5: what the detail panel and body view show for surveyed and unsurveyed bodies. */
class SurveyPanelsTest {

    private static JButton button(Container c, String text) {
        for (Component k : c.getComponents()) {
            if (k instanceof JButton b && text.equals(b.getText())) return b;
            if (k instanceof Container sub) {
                JButton found = button(sub, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Test void surveyedBody_listsResourcesBestFirst_unsurveyedSaysUnknown() throws Exception {
        Edt.run(() -> {
            Engine engine = new Engine(WorldGenerator.generate(42L));
            DetailPanel p = new DetailPanel(engine);
            p.setSize(330, 800);
            engine.setSelection(Selection.body("earth"));
            String text = PanelSmokeTest.allText(p);
            assertTrue(text.contains("Resources (surveyed)"), text);
            int ore = text.indexOf("ORE  Rich  avg .51  best .85");
            int sil = text.indexOf("SILICATE  Rich");
            int bio = text.indexOf("BIOMASS  Fair");
            int ice = text.indexOf("ICE  Poor");
            assertTrue(ore >= 0 && ore < sil && sil < bio && bio < ice, text);

            engine.setSelection(Selection.body("mars"));
            text = PanelSmokeTest.allText(p);
            assertTrue(text.contains(DetailPanel.UNSURVEYED_TEXT), text);
            assertFalse(text.contains("Resources (surveyed)"), text);
        });
    }

    @Test void colony_showsItsGround() throws Exception {
        Edt.run(() -> {
            Engine engine = new Engine(WorldGenerator.generate(42L));
            DetailPanel p = new DetailPanel(engine);
            p.setSize(330, 900);
            engine.setSelection(Selection.site("site-earth-hub"));
            String text = PanelSmokeTest.allText(p);
            assertTrue(text.contains("Ground: ORE .19 · SIL "), text);
            assertFalse(text.contains("FUE "), text);
        });
    }

    @Test void explorer_showsItsTank_andCanBeDispatchedFromOrbit() throws Exception {
        Edt.run(() -> {
            World w = WorldGenerator.generate(1L);
            Ship x = new Ship("x1", "Scout-1", ShipClass.EXPLORER, null);
            x.orbitingBodyId = "mars";
            x.setTank(Resource.HYDROGEN, 64);
            w.ships.add(x);
            Ship c = new Ship("c1", "Ark", ShipClass.COLONIZER, null);
            c.orbitingBodyId = "mars";
            w.ships.add(c);
            Engine engine = new Engine(w);
            DetailPanel p = new DetailPanel(engine);
            p.setSize(330, 600);
            engine.setSelection(Selection.ship("x1"));
            assertTrue(PanelSmokeTest.allText(p).contains("Tank: 64 / 100 (64 hydrogen)"));
            assertNotNull(button(p, "Dispatch..."));
            assertNull(button(p, DetailPanel.FOUND_COLONY_LABEL));
            engine.setSelection(Selection.ship("c1"));
            assertNull(button(p, "Dispatch..."));
            assertNotNull(button(p, DetailPanel.FOUND_COLONY_LABEL));
        });
    }

    @Test void degrees_formatsHemispheres() {
        assertEquals("23°N 104°W", DetailPanel.degrees(Math.toRadians(23.2), Math.toRadians(-104.4)));
        assertEquals("10°S 5°E", DetailPanel.degrees(Math.toRadians(-10), Math.toRadians(5)));
    }

    @Test void bodyView_pickerDisabledUntilSurveyed_thenOverlayAndReadout() throws Exception {
        Edt.run(() -> {
            Engine engine = new Engine(WorldGenerator.generate(42L));
            BodyViewPanel view = new BodyViewPanel(engine);
            view.setSize(700, 700);
            view.doLayout();
            for (Component k : view.getComponents()) k.doLayout();
            engine.setSelection(Selection.body("mars"));
            Body mars = engine.world().findBody("mars");
            assertFalse(view.overlayPicker().isEnabled());
            assertEquals(BodyViewPanel.UNSURVEYED, view.overlayPicker().getSelectedItem());
            assertEquals(BodyViewPanel.UNSURVEYED_TIP, view.overlayPicker().getToolTipText());

            engine.applyDebugEdit("survey mars", w -> w.survey("mars", "Test"));
            assertTrue(view.overlayPicker().isEnabled());
            assertEquals(BodyViewPanel.SURFACE, view.overlayPicker().getItemAt(0));
            assertEquals(ResourceSurvey.of(mars).size() + 1, view.overlayPicker().getItemCount());
            assertNull(view.overlayFor(mars));

            view.overlayPicker().setSelectedItem("ORE");
            assertEquals(Resource.ORE, view.overlayFor(mars));
            // Picking a resource turns its best spot toward the camera.
            ResourceSurvey.Entry oreBest = ResourceSurvey.of(mars).stream()
                .filter(e -> e.resource() == Resource.ORE).findFirst().orElseThrow();
            double[] star = view.screenPointOf(oreBest.bestLat(), oreBest.bestLon());
            assertNotNull(star, "best ORE spot on the near side");
            Component globe = view.getComponent(1);
            assertEquals(globe.getWidth() / 2.0, star[0], 2.0, "best spot centred left to right");
            // A tick doesn't reset the choice.
            engine.tick();
            assertEquals("ORE", view.overlayPicker().getSelectedItem());

            Component sphere = view.getComponent(1);
            int cx = sphere.getWidth() / 2, cy = sphere.getHeight() / 2;
            String here = view.readoutText(mars, cx, cy);
            assertNotNull(here);
            assertTrue(here.startsWith("Here: ORE ."), here);
            assertTrue(here.contains(" (Rich)") || here.contains(" (Good)") || here.contains(" (Fair)")
                || here.contains(" (Poor)"), here);
            view.overlayPicker().setSelectedItem("SILICATE");
            assertTrue(view.readoutText(mars, cx, cy).startsWith("Here: SIL ."), "shown resource comes first");
            List<String> legend = BodyViewPanel.legendLines(mars, Resource.ORE,
                ResourceSurvey.of(mars).stream().filter(e -> e.resource() == Resource.ORE).findFirst().orElseThrow(), true);
            assertEquals("ORE in the ground", legend.get(0));
            assertTrue(legend.stream().anyMatch(l -> l.contains("Best spot 83%")), legend.toString());
            assertNull(view.readoutText(mars, 1, 1), "off the globe");
            view.overlayPicker().setSelectedItem(BodyViewPanel.SURFACE);
            assertNull(view.readoutText(mars, cx, cy), "no readout without an overlay");
        });
    }
}

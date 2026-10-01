package spacecolony.playtest;

import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.awt.event.MouseEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import spacecolony.engine.EngineEvent;
import spacecolony.engine.Engine;
import spacecolony.engine.Selection;
import spacecolony.sim.Body;
import spacecolony.sim.Resource;
import spacecolony.sim.ResourceSurvey;
import spacecolony.sim.Ship;
import spacecolony.sim.ShipClass;
import spacecolony.sim.Site;
import spacecolony.sim.commands.BuildShipCommand;
import spacecolony.sim.commands.DispatchShipCommand;
import spacecolony.ui.BodyViewPanel;
import spacecolony.ui.SpaceColonyFrame;
import spacecolony.ui.dialogs.PlaceSiteDialog;
import spacecolony.world.WorldGenerator;

/**
 * Plan 9 play-test: builds an explorer at Earth Hub, surveys Mars, shows the Resources section
 * and the ORE overlay, then founds a Mars colony on Mars's best ORE spot through the real
 * place-site dialog. Run via {@code ./gradlew surveyPlayTest}; screenshots land in
 * build/playtest/survey.
 */
public class SurveyDriver {
    static Engine engine;
    static SpaceColonyFrame frame;

    public static void main(String[] args) throws Exception {
        PlayTestDriver.out = Path.of(args.length > 0 ? args[0] : "build/playtest/survey");
        Files.createDirectories(PlayTestDriver.out);
        SwingUtilities.invokeAndWait(() -> {
            engine = new Engine(WorldGenerator.generate(42L));
            frame = new SpaceColonyFrame(engine);
            frame.setSize(1400, 900);
            frame.setVisible(true);
        });
        Thread.sleep(1000);

        // Mars before the survey.
        onEdt(() -> engine.setSelection(Selection.body("mars")));
        Thread.sleep(300);
        PlayTestDriver.shot(frame.getRootPane(), "survey-01-mars-unsurveyed");
        PlayTestDriver.check(!engine.world().isSurveyed("mars"), "Mars starts unsurveyed", "");

        // Build an explorer and send it to Mars.
        onEdt(() -> {
            engine.enqueue(new BuildShipCommand("x1", "Scout-1", ShipClass.EXPLORER, "site-earth-hub"));
            engine.tick();
            engine.enqueue(DispatchShipCommand.toBody("x1", "mars", Map.of()));
            for (int i = 0; i < 120 && !engine.world().isSurveyed("mars"); i++) engine.tick();
        });
        Ship x = engine.world().findShip("x1");
        PlayTestDriver.check(engine.world().isSurveyed("mars"), "Explorer surveyed Mars",
            "day " + engine.world().tick + ", tank " + Math.round(x.fuel));
        onEdt(() -> engine.setSelection(Selection.body("mars")));
        Thread.sleep(300);
        PlayTestDriver.shot(frame.getRootPane(), "survey-02-mars-surveyed");

        // Body view with the ORE overlay and the readout.
        onEdt(() -> engine.setView(EngineEvent.ViewChanged.View.BODY_VIEW));
        Thread.sleep(300);
        BodyViewPanel view = find(frame.getContentPane(), BodyViewPanel.class);
        onEdt(() -> {
            @SuppressWarnings("unchecked")
            JComboBox<String> picker = find(view, JComboBox.class);
            picker.setSelectedItem("ORE");
            Component sphere = view.getComponent(1);
            sphere.dispatchEvent(new MouseEvent(sphere, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0,
                sphere.getWidth() / 2 + 40, sphere.getHeight() / 2 - 30, 0, false));
        });
        Thread.sleep(2500); // the tinted map takes a moment the first time
        PlayTestDriver.shot(frame.getRootPane(), "survey-03-mars-ore-overlay");

        // A colonizer in orbit, then the place-site dialog on the best ORE spot.
        Body mars = engine.world().findBody("mars");
        ResourceSurvey.Entry ore = ResourceSurvey.of(mars).stream()
            .filter(e -> e.resource() == Resource.ORE).findFirst().orElseThrow();
        onEdt(() -> engine.applyDebugEdit("colonizer at Mars", w -> {
            Ship c = new Ship("c1", "Ark", ShipClass.COLONIZER, null);
            c.orbitingBodyId = "mars";
            c.cargo.put(Resource.FOOD, 40.0);
            w.ships.add(c);
        }));
        SwingUtilities.invokeLater(() -> PlaceSiteDialog.show(frame, engine, "mars", ore.bestLat(), ore.bestLon()));
        JDialog dialog = null;
        for (int i = 0; i < 100 && dialog == null; i++) {
            Thread.sleep(50);
            for (Window w : Window.getWindows()) if (w instanceof JDialog d && d.isShowing()) dialog = d;
        }
        PlayTestDriver.check(dialog != null, "Place-site dialog opened", "");
        if (dialog != null) {
            JDialog d = dialog;
            Thread.sleep(300);
            PlayTestDriver.shot(d.getRootPane(), "survey-04-place-site");
            onEdt(() -> {
                AbstractButton ok = findText(d.getContentPane(), "OK");
                if (ok != null) ok.doClick();
            });
            onEdt(() -> engine.tick());
        }
        Site colony = mars.sites.isEmpty() ? null : mars.sites.get(0);
        PlayTestDriver.check(colony != null && Math.abs(colony.lat - ore.bestLat()) < 1e-9,
            "Colony founded on Mars's best ORE spot",
            colony == null ? "no colony" : String.format("ORE here %.2f", mars.resourceYields.sample(Resource.ORE, colony.lat, colony.lon)));
        if (colony != null) {
            onEdt(() -> {
                engine.setView(EngineEvent.ViewChanged.View.SYSTEM_MAP);
                engine.setSelection(Selection.site(colony.id));
            });
            Thread.sleep(300);
            PlayTestDriver.shot(frame.getRootPane(), "survey-05-mars-colony-ground");
        }

        boolean failed = PlayTestDriver.report.stream().anyMatch(s -> s.startsWith("FAIL"));
        System.out.println(failed ? "SURVEY PLAY-TEST FAILED" : "SURVEY PLAY-TEST PASSED");
        System.exit(failed ? 1 : 0);
    }

    interface Edt { void run() throws Exception; }

    static void onEdt(Edt r) throws Exception {
        Exception[] err = new Exception[1];
        SwingUtilities.invokeAndWait(() -> { try { r.run(); } catch (Exception e) { err[0] = e; } });
        if (err[0] != null) throw err[0];
    }

    @SuppressWarnings("unchecked")
    static <T> T find(Container c, Class<T> type) {
        for (Component k : c.getComponents()) {
            if (type.isInstance(k)) return (T) k;
            if (k instanceof Container sub) {
                T t = find(sub, type);
                if (t != null) return t;
            }
        }
        return null;
    }

    static AbstractButton findText(Container c, String text) {
        for (Component k : c.getComponents()) {
            if (k instanceof AbstractButton b && text.equals(b.getText())) return b;
            if (k instanceof Container sub) {
                AbstractButton t = findText(sub, text);
                if (t != null) return t;
            }
        }
        return null;
    }
}

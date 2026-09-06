package spacecolony.playtest;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JRootPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import spacecolony.engine.Engine;
import spacecolony.sim.Building;
import spacecolony.sim.BuildingType;
import spacecolony.sim.Resource;
import spacecolony.sim.Ship;
import spacecolony.sim.ShipClass;
import spacecolony.sim.ShipState;
import spacecolony.sim.Site;
import spacecolony.sim.World;
import spacecolony.ui.SpaceColonyFrame;
import spacecolony.ui.TechModal;
import spacecolony.ui.dialogs.BuildBuildingDialog;
import spacecolony.ui.dialogs.DispatchShipDialog;
import spacecolony.world.WorldGenerator;

/**
 * Drives the real Space Colony Swing app through Plan 4's Task 19 Step 4 checklist.
 *
 * <p>Requires a desktop session — run via {@code ./gradlew playTest}. Exits non-zero if any
 * step fails, and writes annotated screenshots to the output directory (default build/playtest).
 * Uses the actual frame, menu actions and dialogs; screenshots are painted from the
 * component tree so no macOS screen-recording permission is needed.
 */
public class PlayTestDriver {

    static SpaceColonyFrame frame;
    static Engine engine;
    static Path out;
    static final List<String> report = new ArrayList<>();
    static final BlockingQueue<Consumer<Window>> responders = new LinkedBlockingQueue<>();
    static final Set<Window> handled = Collections.synchronizedSet(new java.util.HashSet<>());

    public static void main(String[] args) throws Exception {
        out = Path.of(args.length > 0 ? args[0] : "build/playtest");
        Files.createDirectories(out);
        Path saveFile = out.resolve("playtest.json");

        startResponderThread();

        SwingUtilities.invokeAndWait(() -> {
            engine = new Engine(WorldGenerator.generate(1L));
            frame = new SpaceColonyFrame(engine);
            frame.setVisible(true);
        });
        Thread.sleep(1200);
        shot(frame.getRootPane(), "01-launch");

        step1_menuPresent();
        step2_habitatCap();
        step3_miningTech();
        step4_saveRoundTrip(saveFile);
        step5_schemaMismatch(saveFile);
        step6_malformedJson(saveFile);
        step7_midTransit();

        System.out.println("\n================ PLAY-TEST REPORT ================");
        for (String r : report) System.out.println(r);
        System.out.println("==================================================");
        System.exit(report.stream().anyMatch(s -> s.startsWith("FAIL")) ? 1 : 0);
    }

    // ---------- checklist steps ----------

    static void step1_menuPresent() throws Exception {
        JMenuBar mb = frame.getJMenuBar();
        List<String> items = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            JMenu file = mb.getMenu(0);
            for (int i = 0; i < file.getMenuComponentCount(); i++) {
                Component c = file.getMenuComponent(i);
                items.add(c instanceof JMenuItem mi ? mi.getText() : "<separator>");
            }
        });
        boolean ok = mb.getMenuCount() == 1 && "File".equals(mb.getMenu(0).getText())
            && items.equals(List.of("New Game", "Save…", "Load…", "<separator>", "Quit"));
        check(ok, "1. File menu present", "menu=" + mb.getMenu(0).getText() + " items=" + items);
    }

    static void step2_habitatCap() throws Exception {
        tick(1);
        int capBefore = site("site-earth-hub").populationCap;

        // Drive the real Build dialog: pick HABITAT, press OK.
        expect(dlg -> {
            JComboBox<?> combo = find(dlg, JComboBox.class, c -> true);
            combo.setSelectedItem(BuildingType.HABITAT);
            shotQuiet(((JDialog) dlg).getRootPane(), "02-build-dialog");
            clickButton(dlg, "OK");
        });
        runModal(() -> BuildBuildingDialog.show(frame, engine, "site-earth-hub"));

        tick(2);
        int capAfter = site("site-earth-hub").populationCap;
        int habitats = 0;
        for (Building b : site("site-earth-hub").buildings)
            if (b.type == BuildingType.HABITAT) habitats++;
        shot(frame.getRootPane(), "03-after-habitat");
        check(capBefore == 300 && capAfter == 400 && habitats == 2,
            "2. HABITAT raises populationCap",
            "cap " + capBefore + " -> " + capAfter + " (habitats=" + habitats + ")");
    }

    static void step3_miningTech() throws Exception {
        // A lab is needed for research to accumulate points; build it via the real dialog.
        expect(dlg -> {
            find(dlg, JComboBox.class, c -> true).setSelectedItem(BuildingType.RESEARCH_LAB);
            clickButton(dlg, "OK");
        });
        runModal(() -> BuildBuildingDialog.show(frame, engine, "site-earth-hub"));
        tick(2);

        double before = oreDeltaOneTick();

        // Drive the real Tech modal: click the "Basic Mining" row.
        expect(dlg -> {
            JLabel row = find(dlg, JLabel.class, l -> l.getText() != null && l.getText().startsWith("Basic Mining"));
            shotQuiet(((JDialog) dlg).getRootPane(), "04-tech-modal");
            for (java.awt.event.MouseListener ml : row.getMouseListeners()) {
                ml.mousePressed(new java.awt.event.MouseEvent(row, java.awt.event.MouseEvent.MOUSE_PRESSED,
                    System.currentTimeMillis(), 0, 1, 1, 1, false));
            }
        });
        runModal(() -> TechModal.show(frame, engine));

        int ticks = 0;
        while (!world().tech.researched.contains("basic-mining") && ticks < 4000) { tick(1); ticks++; }
        boolean researched = world().tech.researched.contains("basic-mining");
        double after = oreDeltaOneTick();
        double ratio = before > 0 ? after / before : Double.NaN;
        shot(frame.getRootPane(), "05-after-research");
        check(researched && Math.abs(ratio - 1.10) < 0.01,
            "3. basic-mining raises ore rate ~10%",
            String.format("researched=%s after %d ticks, ore/tick %.4f -> %.4f (x%.4f)",
                researched, ticks, before, after, ratio));
    }

    static void step4_saveRoundTrip(Path saveFile) throws Exception {
        long tickBefore = world().tick;
        long creditsBefore = world().credits;
        int popBefore = site("site-earth-hub").population;

        expect(dlg -> {
            JFileChooser ch = find(dlg, JFileChooser.class, c -> true);
            ch.setSelectedFile(saveFile.toFile());
            ch.approveSelection();
        });
        runModal(() -> menuItem("Save…").doClick());
        waitFor(() -> Files.exists(saveFile), 10000);
        boolean saved = Files.exists(saveFile) && Files.size(saveFile) > 0;

        // File -> New: confirm, then supply a seed.
        expect(dlg -> clickButton(dlg, "OK"));
        expect(dlg -> {
            JTextField f = find(dlg, JTextField.class, c -> true);
            f.setText("999");
            clickButton(dlg, "OK");
        });
        runModal(() -> menuItem("New Game").doClick());
        long tickAfterNew = world().tick;
        shot(frame.getRootPane(), "06-after-new");

        // File -> Load: pick the save back.
        expect(dlg -> {
            JFileChooser ch = find(dlg, JFileChooser.class, c -> true);
            ch.setSelectedFile(saveFile.toFile());
            ch.approveSelection();
        });
        runModal(() -> menuItem("Load…").doClick());
        waitFor(() -> world().tick == tickBefore, 10000);
        shot(frame.getRootPane(), "07-after-load");

        boolean restored = world().tick == tickBefore
            && world().credits == creditsBefore
            && site("site-earth-hub") != null
            && site("site-earth-hub").population == popBefore;
        check(saved && tickAfterNew == 0 && restored,
            "4. Save -> New -> Load round-trips",
            "saved=" + saved + " newTick=" + tickAfterNew
                + " restored tick=" + world().tick + "/" + tickBefore
                + " credits=" + world().credits + "/" + creditsBefore
                + " pop=" + site("site-earth-hub").population + "/" + popBefore);
    }

    static void step5_schemaMismatch(Path saveFile) throws Exception {
        String good = Files.readString(saveFile);
        Path bad = saveFile.resolveSibling("schema99.json");
        Files.writeString(bad, good.replaceFirst("\"schemaVersion\": 1", "\"schemaVersion\": 99"));
        long tickBefore = world().tick;

        String[] msg = new String[1];
        expect(dlg -> {
            JFileChooser ch = find(dlg, JFileChooser.class, c -> true);
            ch.setSelectedFile(bad.toFile());
            ch.approveSelection();
        });
        expect(dlg -> {
            msg[0] = dialogText(dlg);
            shotQuiet(((JDialog) dlg).getRootPane(), "08-schema-warning");
            clickButton(dlg, "OK");
        });
        runModal(() -> menuItem("Load…").doClick());
        Thread.sleep(600);

        boolean warned = msg[0] != null && msg[0].contains("schema v99");
        check(warned && world().tick == tickBefore,
            "5. Schema-mismatch warning, world unchanged",
            "dialog=\"" + msg[0] + "\" tick=" + world().tick + "/" + tickBefore);
    }

    static void step6_malformedJson(Path saveFile) throws Exception {
        Path bad = saveFile.resolveSibling("malformed.json");
        Files.writeString(bad, "{not json");
        long tickBefore = world().tick;

        String[] msg = new String[1];
        expect(dlg -> {
            JFileChooser ch = find(dlg, JFileChooser.class, c -> true);
            ch.setSelectedFile(bad.toFile());
            ch.approveSelection();
        });
        expect(dlg -> {
            msg[0] = dialogText(dlg);
            shotQuiet(((JDialog) dlg).getRootPane(), "09-malformed-error");
            clickButton(dlg, "OK");
        });
        runModal(() -> menuItem("Load…").doClick());
        Thread.sleep(600);

        boolean errored = msg[0] != null && msg[0].toLowerCase().contains("not valid json");
        check(errored && world().tick == tickBefore,
            "6. Malformed-JSON error, world unchanged",
            "dialog=\"" + msg[0] + "\" tick=" + world().tick + "/" + tickBefore);
    }

    static void step7_midTransit() throws Exception {
        // Seed a destination site and a fuelled hauler (a colonizer run would take
        // thousands of ticks); the dispatch itself goes through the real dialog.
        SwingUtilities.invokeAndWait(() -> {
            World w = engine.world();
            Site mars = new Site("site-mars-1", "Mars 1", "mars", 0.0, 0.0, 100);
            w.findBody("mars").sites.add(mars);
            Ship h = new Ship("h1", "H1", ShipClass.HAULER, "site-earth-hub");
            h.fuel = 1_000_000.0;
            w.ships.add(h);
            w.findSite("site-earth-hub").stockpile.put(Resource.METAL, 200.0);
        });

        expect(dlg -> {
            JComboBox<?> dest = find(dlg, JComboBox.class, c -> true);
            dest.setSelectedItem("site-mars-1");
            List<JTextField> fields = findAll(dlg, JTextField.class);
            // Field order follows stockpileable Resource.values(); locate METAL by its label.
            setFieldAfterLabel(dlg, "METAL:", "50");
            if (fields.isEmpty()) throw new IllegalStateException("no manifest fields");
            clickButton(dlg, "OK");
        });
        runModal(() -> DispatchShipDialog.show(frame, engine, "h1"));

        int n = 0;
        while (ship("h1").state != ShipState.IN_TRANSIT && n < 400) { tick(1); n++; }
        boolean departed = ship("h1").state == ShipState.IN_TRANSIT;
        long arrival = departed ? ship("h1").transit.arrivalTick() : -1;
        String dest = departed ? ship("h1").transit.destSiteId() : null;
        shot(frame.getRootPane(), "10-in-transit");

        Path f = out.resolve("transit.json");
        expect(dlg -> {
            JFileChooser ch = find(dlg, JFileChooser.class, c -> true);
            ch.setSelectedFile(f.toFile());
            ch.approveSelection();
        });
        runModal(() -> menuItem("Save…").doClick());
        waitFor(() -> Files.exists(f), 10000);

        expect(dlg -> clickButton(dlg, "OK"));
        expect(dlg -> { find(dlg, JTextField.class, c -> true).setText("5"); clickButton(dlg, "OK"); });
        runModal(() -> menuItem("New Game").doClick());

        expect(dlg -> {
            JFileChooser ch = find(dlg, JFileChooser.class, c -> true);
            ch.setSelectedFile(f.toFile());
            ch.approveSelection();
        });
        runModal(() -> menuItem("Load…").doClick());
        waitFor(() -> world().findShip("h1") != null, 10000);
        shot(frame.getRootPane(), "11-transit-reloaded");

        Ship lh = world().findShip("h1");
        boolean ok = departed && lh != null && lh.state == ShipState.IN_TRANSIT
            && lh.transit.arrivalTick() == arrival && lh.transit.destSiteId().equals(dest);
        check(ok, "7. Mid-transit ship survives save/load",
            "departed=" + departed + " arrival=" + arrival
                + " reloaded=" + (lh == null ? "null" : lh.state + " arrival=" + lh.transit.arrivalTick()
                    + " dest=" + lh.transit.destSiteId()));
    }

    // ---------- modal dialog plumbing ----------

    static void startResponderThread() {
        Thread t = new Thread(() -> {
            while (true) {
                try {
                    Window dlg = null;
                    for (Window w : Window.getWindows()) {
                        if (w instanceof JDialog && w.isVisible() && !handled.contains(w)) { dlg = w; break; }
                    }
                    if (dlg != null && !responders.isEmpty()) {
                        Consumer<Window> r = responders.take();
                        handled.add(dlg);
                        final Window target = dlg;
                        SwingUtilities.invokeLater(() -> {
                            try { r.accept(target); }
                            catch (RuntimeException e) {
                                System.out.println("  [responder error] " + e);
                                target.setVisible(false);
                            }
                        });
                    }
                    Thread.sleep(40);
                } catch (InterruptedException e) { return; }
            }
        });
        t.setDaemon(true);
        t.start();
    }

    static void expect(Consumer<Window> responder) { responders.add(responder); }

    /** Runs a modal-opening action on the EDT; queued responders drive the dialogs. */
    static void runModal(Runnable openOnEdt) throws Exception {
        SwingUtilities.invokeAndWait(openOnEdt);
        Thread.sleep(250);
    }

    // ---------- helpers ----------

    static World world() { return engine.world(); }
    static Site site(String id) { return world().findSite(id); }
    static Ship ship(String id) { return world().findShip(id); }

    static void tick(int n) throws Exception {
        for (int i = 0; i < n; i++) SwingUtilities.invokeAndWait(() -> engine.tick());
    }

    static double oreDeltaOneTick() throws Exception {
        SwingUtilities.invokeAndWait(() -> site("site-earth-hub").stockpile.put(Resource.ORE, 0.0));
        tick(1);
        return site("site-earth-hub").stockpile.get(Resource.ORE);
    }

    static JMenuItem menuItem(String text) {
        JMenu file = frame.getJMenuBar().getMenu(0);
        for (int i = 0; i < file.getMenuComponentCount(); i++) {
            if (file.getMenuComponent(i) instanceof JMenuItem mi && text.equals(mi.getText())) return mi;
        }
        throw new IllegalStateException("no menu item " + text);
    }

    @SuppressWarnings("unchecked")
    static <T> T find(Container root, Class<T> type, Predicate<T> p) {
        for (Component c : root.getComponents()) {
            if (type.isInstance(c) && p.test((T) c)) return (T) c;
            if (c instanceof Container inner) {
                T r = find(inner, type, p);
                if (r != null) return r;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    static <T> List<T> findAll(Container root, Class<T> type) {
        List<T> outList = new ArrayList<>();
        for (Component c : root.getComponents()) {
            if (type.isInstance(c)) outList.add((T) c);
            if (c instanceof Container inner) outList.addAll(findAll(inner, type));
        }
        return outList;
    }

    /** Finds the JTextField sitting immediately after the given label in its parent. */
    static void setFieldAfterLabel(Container root, String labelText, String value) {
        JLabel lbl = find(root, JLabel.class, l -> labelText.equals(l.getText()));
        if (lbl == null) throw new IllegalStateException("no label " + labelText);
        Container parent = lbl.getParent();
        Component[] kids = parent.getComponents();
        for (int i = 0; i < kids.length - 1; i++) {
            if (kids[i] == lbl && kids[i + 1] instanceof JTextField tf) { tf.setText(value); return; }
        }
        throw new IllegalStateException("no field after " + labelText);
    }

    static void clickButton(Container root, String text) {
        AbstractButton b = find(root, AbstractButton.class, x -> text.equals(x.getText()));
        if (b == null) throw new IllegalStateException("no button '" + text + "' in " + dialogText(root));
        b.doClick();
    }

    static String dialogText(Container root) {
        StringBuilder sb = new StringBuilder();
        for (JLabel l : findAll(root, JLabel.class)) {
            if (l.getText() != null && !l.getText().isBlank()) sb.append(l.getText()).append(" ");
        }
        return sb.toString().trim();
    }

    static void shot(JRootPane pane, String name) throws Exception {
        BufferedImage[] img = new BufferedImage[1];
        SwingUtilities.invokeAndWait(() -> img[0] = paint(pane));
        ImageIO.write(img[0], "png", out.resolve(name + ".png").toFile());
    }

    /** Screenshot from inside an EDT responder (already on the EDT). */
    static void shotQuiet(JRootPane pane, String name) {
        try { ImageIO.write(paint(pane), "png", out.resolve(name + ".png").toFile()); }
        catch (Exception e) { System.out.println("  [shot failed] " + name + ": " + e); }
    }

    static BufferedImage paint(JRootPane pane) {
        int w = Math.max(pane.getWidth(), 50), h = Math.max(pane.getHeight(), 50);
        BufferedImage i = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = i.createGraphics();
        pane.paint(g);
        g.dispose();
        return i;
    }

    static void waitFor(ThrowingSupplier cond, long ms) throws Exception {
        long deadline = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < deadline) {
            if (cond.get()) return;
            Thread.sleep(50);
        }
    }

    interface ThrowingSupplier { boolean get() throws Exception; }

    static void check(boolean ok, String label, String detail) {
        report.add((ok ? "PASS  " : "FAIL  ") + label + "  [" + detail + "]");
        System.out.println((ok ? "PASS  " : "FAIL  ") + label + "  [" + detail + "]");
    }
}

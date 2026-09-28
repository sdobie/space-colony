package spacecolony.playtest;

import java.awt.Component;
import java.awt.Container;
import java.awt.Point;
import java.awt.Robot;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.function.Predicate;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import spacecolony.SpaceColonyApp;
import spacecolony.engine.Engine;
import spacecolony.options.OptionsStore;
import spacecolony.save.SaveSlots;
import spacecolony.sim.Resource;
import spacecolony.sim.ShipClass;
import spacecolony.tutorial.TutorialScript;
import spacecolony.ui.SpaceColonyFrame;
import spacecolony.ui.startup.SplashWindow;
import spacecolony.ui.startup.TitleScreen;
import spacecolony.ui.tutorial.TutorialTargets;

/**
 * Drives the real app from launch (Plan 6 design §7.4): splash, title, Options, the whole
 * tutorial with real mouse clicks on the highlighted controls, Keep playing, Save As, Main
 * Menu. Run via {@code ./gradlew startupPlayTest}, which points options and saves at
 * build/playtest/startup. Screenshots land in build/playtest.
 */
public class StartupDriver {
    static Path out;
    static Robot robot;
    static final List<String> failures = new ArrayList<>();
    static final Set<Window> seenDialogs = new HashSet<>();
    static int shots;

    public static void main(String[] args) throws Exception {
        out = Path.of(args.length > 0 ? args[0] : "build/playtest").toAbsolutePath();
        Files.createDirectories(out);
        Path optionsFile = Path.of(System.getProperty(OptionsStore.FILE_PROPERTY));
        Path savesDir = Path.of(System.getProperty(SaveSlots.DIR_PROPERTY));
        robot = new Robot();
        robot.setAutoDelay(30);

        // Step 1: splash, then title.
        SpaceColonyApp.main(new String[0]);
        SplashWindow splash = waitForWindow(SplashWindow.class, 3000);
        check("splash shows", splash != null);
        if (splash != null) shot(splash, "startup-01-splash");
        TitleScreen title = waitForWindow(TitleScreen.class, 6000);
        check("title shows after the splash", title != null);
        if (title == null) finish();
        Thread.sleep(400);
        onEdt(() -> {
            check("Continue disabled on first run", !title.button(TitleScreen.Action.CONTINUE).isEnabled());
            check("new-player banner visible", title.bannerVisible());
        });
        shot(title, "startup-02-title");

        // Step 2: Options → autosave 5 minutes → OK writes the file.
        click(title.button(TitleScreen.Action.OPTIONS));
        JDialog options = waitForDialog("Options");
        check("Options dialog opens", options != null);
        if (options != null) {
            shot(options, "startup-03-options");
            onEdt(() -> {
                JComboBox<?> autosave = find(options, JComboBox.class,
                    c -> c.getItemCount() == 5 && Integer.valueOf(30).equals(c.getItemAt(4)));
                autosave.setSelectedItem(5);
            });
            click(button(options, "OK"));
            Thread.sleep(300);
        }
        Properties p = new Properties();
        if (Files.exists(optionsFile)) try (var r = Files.newBufferedReader(optionsFile)) { p.load(r); }
        check("options file has gameplay.autosaveMinutes=5", "5".equals(p.getProperty("gameplay.autosaveMinutes")));

        // Step 3: the tutorial, clicking what it highlights.
        click(title.button(TitleScreen.Action.TUTORIAL));
        SpaceColonyFrame frame = waitForWindow(SpaceColonyFrame.class, 4000);
        check("tutorial opens a game window", frame != null);
        if (frame == null) finish();
        Engine engine = frame.engine();
        Thread.sleep(600);
        check("window title says Tutorial", frame.getTitle().endsWith("Tutorial"));
        tutorialShot(frame, 1, "welcome");
        click(button(frame.getLayeredPane(), "Next step"));

        expectStep(frame, 2);
        tutorialShot(frame, 2, "start-clock");
        click(target(frame, TutorialScript.T_X1));

        expectStep(frame, 3);
        tutorialShot(frame, 3, "select-hub");
        click(label(frame, l -> l.getText().contains("Earth Hub")));

        expectStep(frame, 4);
        tutorialShot(frame, 4, "read-dock");
        click(button(frame.getLayeredPane(), "Next step"));

        expectStep(frame, 5);
        tutorialShot(frame, 5, "build-lab");
        click(target(frame, TutorialScript.T_BUILD_BUILDING));
        JDialog build = waitForDialog("");
        if (build != null) {
            onEdt(() -> find(build, javax.swing.JList.class, c -> spacecolony.ui.dialogs.BuildBuildingDialog.LIST.equals(c.getName()))
                .setSelectedValue(spacecolony.sim.BuildingCatalog.get(spacecolony.sim.BuildingType.RESEARCH_LAB), true));
            click(button(build, spacecolony.ui.dialogs.BuildBuildingDialog.OK_LABEL));
        }

        expectStep(frame, 6);
        tutorialShot(frame, 6, "research");
        click(target(frame, TutorialScript.T_TECH));
        JDialog tech = waitForDialog("Tech Tree");
        if (tech != null) {
            click(find(tech, JLabel.class, l -> l.getText() != null && l.getText().startsWith("Basic Mining")));
            // Queueing a tech closes the modal.
            waitFor(() -> !tech.isShowing(), 2000);
            check("queueing research closes the Tech modal", !tech.isShowing());
        }

        expectStep(frame, 7);
        tutorialShot(frame, 7, "build-colonizer");
        click(target(frame, TutorialScript.T_BUILD_SHIP));
        JDialog ship = waitForDialog("Build ship");
        if (ship != null) {
            onEdt(() -> {
                List<JTextField> fields = findAll(ship, JTextField.class);
                fields.get(0).setText("c1");
                fields.get(1).setText("Ark");
                find(ship, JComboBox.class, c -> true).setSelectedItem(ShipClass.COLONIZER);
            });
            click(button(ship, "OK"));
        }

        expectStep(frame, 8);
        waitFor(() -> label(frame, l -> l.getText().contains("Ark")) != null, 3000);
        click(label(frame, l -> l.getText().contains("Ark")));
        tutorialShot(frame, 8, "dispatch");
        click(target(frame, TutorialScript.T_DISPATCH));
        JDialog dispatch = waitForDialog("Dispatch");
        if (dispatch != null) {
            onEdt(() -> {
                find(dispatch, JComboBox.class, c -> true).setSelectedItem("Mars (unsettled)");
                setFieldAfterLabel(dispatch, Resource.FOOD + ":", "40");
                setFieldAfterLabel(dispatch, Resource.WATER + ":", "40");
            });
            click(button(dispatch, "OK"));
        }

        // Mars is only days away in this seed, so at 1× the colonizer can arrive before the
        // driver sees step 9; the script then moves straight on, which is what a player gets too.
        waitFor(() -> stepNumber(frame) >= 9, 6000);
        check("tutorial moves past dispatch", stepNumber(frame) >= 9);
        if (stepNumber(frame) == 9) {
            tutorialShot(frame, 9, "fast-forward");
            click(target(frame, TutorialScript.T_X16));
        }
        // Shorten the flight: silent ticks until the colonizer is in orbit.
        onEdt(() -> {
            for (int i = 0; i < 2000 && engine.world().findShip("c1").orbitingBodyId == null; i++) engine.advanceSilently(1);
        });
        check("colonizer reaches Mars orbit", "mars".equals(engine.world().findShip("c1").orbitingBodyId));

        expectStep(frame, 10);
        waitFor(() -> target(frame, TutorialScript.T_FOUND_COLONY) != null, 3000);
        tutorialShot(frame, 10, "found-colony");
        click(target(frame, TutorialScript.T_FOUND_COLONY));
        waitFor(() -> target(frame, TutorialScript.T_SPHERE) != null, 3000);
        click(target(frame, TutorialScript.T_SPHERE));
        JDialog place = waitForDialog("Place site");
        if (place != null) click(button(place, "OK"));

        expectStep(frame, 11);
        tutorialShot(frame, 11, "goals");
        click(target(frame, TutorialScript.T_GOALS));
        JDialog goals = waitForDialog("Goals");
        if (goals != null) click(button(goals, "Close"));
        click(button(frame.getLayeredPane(), "Next step"));

        expectStep(frame, 12);
        tutorialShot(frame, 12, "finish");
        check("a colony stands on Mars",
            !engine.world().findBody("mars").sites.isEmpty());

        // Step 4: Keep playing, then Save As works.
        click(button(frame.getLayeredPane(), "Keep playing"));
        Thread.sleep(300);
        check("Keep playing leaves tutorial mode", !frame.getTitle().endsWith("Tutorial"));
        check("tutorial marked complete in options", readOptions(optionsFile).contains("progress.tutorialCompleted=true"));
        SwingUtilities.invokeLater(() -> menuItem(frame, "Save As…").doClick());
        JDialog saveAs = waitForDialog("Save As");
        if (saveAs != null) {
            onEdt(() -> find(saveAs, JTextField.class, c -> true).setText("first-colony"));
            click(button(saveAs, "Save"));
        }
        waitFor(() -> Files.exists(savesDir.resolve("first-colony.json")), 10_000);
        check("Save As writes first-colony.json", Files.exists(savesDir.resolve("first-colony.json")));
        shot(frame, "startup-04-after-tutorial");

        // Step 5: Main Menu → title with Continue naming the game.
        SwingUtilities.invokeLater(() -> menuItem(frame, "Main Menu").doClick());
        JDialog confirm = waitForDialog("Main Menu");
        if (confirm != null) click(button(confirm, "OK"));
        waitFor(() -> title.isShowing() && !frame.isDisplayable(), 5000);
        check("Main Menu closes the game and shows the title", title.isShowing() && !frame.isDisplayable());
        waitFor(() -> title.button(TitleScreen.Action.CONTINUE).isEnabled(), 5000);
        String[] cont = new String[1];
        onEdt(() -> cont[0] = continueText(title));
        check("Continue names the saved game", cont[0].contains("first-colony"));
        check("no new-player banner once the tutorial is done", !title.bannerVisible());
        shot(title, "startup-05-title-continue");
        finish();
    }

    // ---- tutorial helpers ----

    static void expectStep(SpaceColonyFrame f, int n) throws Exception {
        waitFor(() -> ("Step " + n + " of 12").equals(stepHeader(f)), 4000);
        check("tutorial reaches step " + n, ("Step " + n + " of 12").equals(stepHeader(f)));
    }

    static int stepNumber(SpaceColonyFrame f) {
        String[] parts = stepHeader(f).split(" ");
        return parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
    }

    static String stepHeader(SpaceColonyFrame f) {
        JLabel l = find(f.getLayeredPane(), JLabel.class, x -> x.getText() != null && x.getText().startsWith("Step "));
        return l == null ? "" : l.getText();
    }

    static void tutorialShot(Window w, int n, String id) throws Exception {
        Thread.sleep(250);
        shot(w, String.format("tutorial-%02d-%s", n, id));
    }

    static Component target(SpaceColonyFrame f, String name) {
        return TutorialTargets.find(f.getContentPane(), name);
    }

    static String continueText(TitleScreen t) {
        AbstractButton b = t.button(TitleScreen.Action.CONTINUE);
        StringBuilder sb = new StringBuilder(String.valueOf(b.getText()));
        if (b.getToolTipText() != null) sb.append(' ').append(b.getToolTipText());
        for (JLabel l : findAll(b, JLabel.class)) sb.append(' ').append(l.getText());
        return sb.toString();
    }

    static String readOptions(Path f) throws Exception {
        return Files.exists(f) ? Files.readString(f) : "";
    }

    // ---- generic helpers ----

    /** A real mouse click at the component's centre. */
    static void click(Component c) throws Exception {
        if (c == null) { check("click target present", false); return; }
        Point[] p = new Point[1];
        onEdt(() -> {
            Window w = SwingUtilities.getWindowAncestor(c);
            if (w != null) w.toFront();
            Point s = c.getLocationOnScreen();
            p[0] = new Point(s.x + c.getWidth() / 2, s.y + c.getHeight() / 2);
        });
        robot.mouseMove(p[0].x, p[0].y);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        // No waitForIdle: the title's starfield and the highlight pulse keep the EDT busy.
        Thread.sleep(300);
    }

    static <W extends Window> W waitForWindow(Class<W> type, long millis) throws Exception {
        long end = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < end) {
            for (Window w : Window.getWindows()) if (type.isInstance(w) && w.isShowing()) return type.cast(w);
            Thread.sleep(40);
        }
        return null;
    }

    /** The next not-yet-seen showing dialog whose title starts with {@code prefix}. */
    static JDialog waitForDialog(String prefix) throws Exception {
        long end = System.currentTimeMillis() + 4000;
        while (System.currentTimeMillis() < end) {
            for (Window w : Window.getWindows()) {
                if (w instanceof JDialog d && d.isShowing() && !seenDialogs.contains(d) && d.getTitle().startsWith(prefix)) {
                    seenDialogs.add(d);
                    Thread.sleep(300);
                    return d;
                }
            }
            Thread.sleep(40);
        }
        check("dialog '" + prefix + "' opens", false);
        return null;
    }

    static AbstractButton button(Container root, String text) {
        return find(root, AbstractButton.class, b -> text.equals(b.getText()) && b.isShowing());
    }

    static JLabel label(JFrame f, Predicate<JLabel> p) {
        return find(f.getContentPane(), JLabel.class, l -> l.getText() != null && l.isShowing() && p.test(l));
    }

    static JMenuItem menuItem(JFrame f, String text) {
        JMenu file = f.getJMenuBar().getMenu(0);
        for (int i = 0; i < file.getMenuComponentCount(); i++)
            if (file.getMenuComponent(i) instanceof JMenuItem mi && text.equals(mi.getText())) return mi;
        throw new IllegalStateException("no menu item " + text);
    }

    static void setFieldAfterLabel(Container root, String labelText, String value) {
        JLabel lbl = find(root, JLabel.class, l -> labelText.equals(l.getText()));
        Component[] kids = lbl.getParent().getComponents();
        for (int i = 0; i < kids.length - 1; i++)
            if (kids[i] == lbl && kids[i + 1] instanceof JTextField tf) { tf.setText(value); return; }
        throw new IllegalStateException("no field after " + labelText);
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
        List<T> list = new ArrayList<>();
        for (Component c : root.getComponents()) {
            if (type.isInstance(c)) list.add((T) c);
            if (c instanceof Container inner) list.addAll(findAll(inner, type));
        }
        return list;
    }

    interface Body { void run() throws Exception; }

    static void onEdt(Body b) throws Exception {
        Exception[] err = new Exception[1];
        SwingUtilities.invokeAndWait(() -> { try { b.run(); } catch (Exception e) { err[0] = e; } });
        if (err[0] != null) throw err[0];
    }

    interface Cond { boolean get() throws Exception; }

    static void waitFor(Cond c, long millis) throws Exception {
        long end = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < end) {
            boolean[] ok = new boolean[1];
            onEdt(() -> ok[0] = c.get());
            if (ok[0]) return;
            Thread.sleep(50);
        }
    }

    /** Screenshot of what is actually on screen, via Robot. */
    static void shot(Window w, String name) throws Exception {
        java.awt.Rectangle[] r = new java.awt.Rectangle[1];
        onEdt(() -> r[0] = w.getBounds());
        BufferedImage img = robot.createScreenCapture(r[0]);
        ImageIO.write(img, "png", out.resolve(name + ".png").toFile());
    }

    static final long T0 = System.currentTimeMillis();

    static void check(String what, boolean ok) {
        System.out.printf("[%5.1fs] ", (System.currentTimeMillis() - T0) / 1000.0);
        System.out.println((ok ? "PASS  " : "FAIL  ") + what);
        if (!ok) failures.add(what);
    }

    static void finish() {
        System.out.println(failures.isEmpty() ? "ALL PASS" : "FAILURES: " + failures);
        System.exit(failures.isEmpty() ? 0 : 1);
    }
}

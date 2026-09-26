package spacecolony.playtest;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Robot;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import spacecolony.debug.CrashHandler;
import spacecolony.debug.DebugActions;
import spacecolony.debug.DebugLogging;
import spacecolony.debug.DebugOverlayPanel;
import spacecolony.debug.ExceptionLog;
import spacecolony.debug.TriggerEventDialog;
import spacecolony.engine.Engine;
import spacecolony.engine.Selection;
import spacecolony.sim.EventKind;
import spacecolony.ui.EventStripPanel;
import spacecolony.ui.SpaceColonyFrame;
import spacecolony.world.WorldGenerator;

/**
 * Drives debug mode through the real window (Plan 5 design §7.5 debug steps). Requires a
 * desktop session — run via {@code ./gradlew debugPlayTest}. Screenshots land in
 * build/playtest. {@code user.home} is pointed into the output directory so logs and
 * world dumps don't touch the real home.
 */
public class DebugModeDriver {
    static Engine engine;
    static SpaceColonyFrame frame;
    static Path out;
    static Robot robot;
    static final List<String> failures = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        out = Path.of(args.length > 0 ? args[0] : "build/playtest").toAbsolutePath();
        Files.createDirectories(out);
        Path home = out.resolve("home");
        System.setProperty("user.home", home.toString());
        System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tF %1$tT %4$s %3$s: %5$s%6$s%n");
        DebugLogging.install(Level.INFO, DebugLogging.defaultLogDir());
        ExceptionLog exceptions = new ExceptionLog(20);

        SwingUtilities.invokeAndWait(() -> {
            engine = new Engine(WorldGenerator.generate(1L));
            frame = new SpaceColonyFrame(engine, exceptions);
            CrashHandler.install(new CrashHandler(exceptions, engine::debugEnabled, frame::showCrashDialog));
            frame.setVisible(true);
            frame.toFront();
        });
        robot = new Robot();
        Thread.sleep(1200);
        check("debug off at launch", !engine.debugEnabled() && overlay() == null);

        press(KeyEvent.VK_D, true);
        check("Ctrl+D turns debug on", engine.debugEnabled());
        check("overlay mounted", overlay() != null && overlay().isShowing());
        check("Debug menu mounted", frame.getJMenuBar().getMenu(frame.getJMenuBar().getMenuCount() - 1).getText().equals("Debug"));

        press(KeyEvent.VK_F10, false);
        check("F10 steps one tick", engine.world().tick == 1);
        clickNow("Step");
        check("Step button steps one tick", engine.world().tick == 2);

        SwingUtilities.invokeAndWait(() -> engine.advanceSilently(500));
        check("Run 500 lands on tick 502", engine.world().tick == 502);

        SwingUtilities.invokeAndWait(() -> TriggerEventDialog.trigger(engine, "earth", EventKind.METEOR_STRIKE));
        Thread.sleep(200);
        check("forced meteor strike is the strip's first row", firstStripRow().contains("Meteor strike on Earth"));

        clickNow("Determinism check");
        waitFor(() -> overlayStatus().matches(".*Determinism (OK|MISMATCH).*"), 20_000);
        check("determinism status says OK", overlayStatus().contains("Determinism OK"));
        if (!overlayStatus().contains("Determinism OK")) System.out.println("  status: " + overlayStatus());

        clickNow("Dump world");
        waitFor(() -> overlayStatus().contains("Dumped to"), 5000);
        Path dumpDir = DebugActions.debugDir();
        check("world dump written", Files.isDirectory(dumpDir) && Files.list(dumpDir).findAny().isPresent());
        check("log file written", Files.list(DebugLogging.defaultLogDir()).anyMatch(p -> p.toString().endsWith(".log")));

        SwingUtilities.invokeAndWait(() -> engine.setSelection(Selection.site("site-earth-hub")));
        Thread.sleep(400);
        shot(frame, "debug-01-overlay");

        clickNow("Inspector");
        JDialog inspector = waitForDialog("Inspect Site site-earth-hub");
        check("inspector opens for the selection", inspector != null);
        if (inspector != null) {
            Thread.sleep(300);
            shot(inspector, "debug-02-inspector");
            SwingUtilities.invokeAndWait(inspector::dispose);
        }

        clickNow("Log viewer");
        JDialog logs = waitForDialog("Log viewer");
        check("log viewer opens", logs != null);
        if (logs != null) {
            Thread.sleep(800);
            shot(logs, "debug-03-log-viewer");
            SwingUtilities.invokeAndWait(logs::dispose);
        }

        // Debug on: an uncaught EDT exception lands in the overlay banner, no dialog.
        SwingUtilities.invokeLater(() -> { throw new IllegalStateException("debug driver test exception"); });
        Thread.sleep(900);
        check("exception recorded", exceptions.total() == 1);
        check("no crash dialog in debug mode", waitForDialog("Space Colony", 500) == null);
        shot(frame, "debug-04-exception-banner");

        focusFrame();
        press(KeyEvent.VK_D, true);
        check("Ctrl+D turns debug off", !engine.debugEnabled() && overlay() == null);

        // Debug off: the same exception shows the polite dialog once.
        SwingUtilities.invokeLater(() -> { throw new IllegalStateException("second test exception"); });
        JDialog crash = waitForDialog("Space Colony");
        check("crash dialog shown with debug off", crash != null);
        if (crash != null) {
            Thread.sleep(300);
            shot(crash, "debug-05-crash-dialog");
            SwingUtilities.invokeAndWait(() -> clickIn(crash, "Continue"));
            Thread.sleep(300);
            check("Continue keeps the game running", frame.isShowing());
        }

        System.out.println(failures.isEmpty() ? "ALL PASS" : "FAILURES: " + failures);
        System.exit(failures.isEmpty() ? 0 : 1);
    }

    static DebugOverlayPanel overlay() { return find(frame.getContentPane(), DebugOverlayPanel.class); }

    static String overlayStatus() throws Exception {
        String[] s = new String[1];
        SwingUtilities.invokeAndWait(() -> {
            DebugOverlayPanel o = overlay();
            JLabel l = o == null ? null : find(o, JLabel.class);
            s[0] = l == null ? "" : l.getText();
        });
        return s[0];
    }

    static String firstStripRow() throws Exception {
        String[] s = new String[1];
        SwingUtilities.invokeAndWait(() -> {
            EventStripPanel strip = find(frame.getContentPane(), EventStripPanel.class);
            JLabel l = strip == null ? null : find(strip, JLabel.class);
            s[0] = l == null ? "" : l.getText();
        });
        return s[0];
    }

    static void press(int key, boolean ctrl) throws Exception {
        focusFrame();
        if (ctrl) robot.keyPress(KeyEvent.VK_CONTROL);
        robot.keyPress(key);
        robot.keyRelease(key);
        if (ctrl) robot.keyRelease(KeyEvent.VK_CONTROL);
        robot.waitForIdle();
        Thread.sleep(300);
    }

    /** Without a window manager, focus can drop after dialogs close. */
    static void focusFrame() throws Exception {
        SwingUtilities.invokeAndWait(() -> { frame.toFront(); frame.requestFocus(); });
        Thread.sleep(200);
    }

    static void check(String what, boolean ok) {
        System.out.println((ok ? "PASS  " : "FAIL  ") + what + (ok ? "" : "   [tick=" + engine.world().tick + "]"));
        if (!ok) failures.add(what);
    }

    static void clickNow(String text) throws Exception {
        SwingUtilities.invokeAndWait(() -> clickIn(frame, text));
    }

    static void clickIn(Container root, String text) {
        AbstractButton b = findButton(root, text);
        if (b == null) throw new IllegalStateException("No button " + text);
        b.doClick();
    }

    static AbstractButton findButton(Container c, String text) {
        for (Component k : c.getComponents()) {
            if (k instanceof AbstractButton b && text.equals(b.getText())) return b;
            if (k instanceof Container kc) {
                AbstractButton hit = findButton(kc, text);
                if (hit != null) return hit;
            }
        }
        return null;
    }

    static <T> T find(Container c, Class<T> type) {
        for (Component k : c.getComponents()) {
            if (type.isInstance(k)) return type.cast(k);
            if (k instanceof Container kc) {
                T hit = find(kc, type);
                if (hit != null) return hit;
            }
        }
        return null;
    }

    static void waitFor(BooleanSupplierX cond, long millis) throws Exception {
        long deadline = System.currentTimeMillis() + millis;
        while (!cond.get() && System.currentTimeMillis() < deadline) Thread.sleep(100);
    }

    @FunctionalInterface interface BooleanSupplierX { boolean get() throws Exception; }

    static JDialog waitForDialog(String title) throws Exception { return waitForDialog(title, 4000); }

    static JDialog waitForDialog(String title, long millis) throws Exception {
        long deadline = System.currentTimeMillis() + millis;
        do {
            for (Window w : Window.getWindows()) {
                if (w instanceof JDialog d && d.isShowing() && title.equals(d.getTitle())) return d;
            }
            Thread.sleep(100);
        } while (System.currentTimeMillis() < deadline);
        return null;
    }

    static void shot(Window w, String name) throws Exception {
        BufferedImage[] img = new BufferedImage[1];
        SwingUtilities.invokeAndWait(() -> {
            var pane = SwingUtilities.getRootPane(w);
            BufferedImage i = new BufferedImage(pane.getWidth(), pane.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = i.createGraphics();
            pane.paint(g);
            g.dispose();
            img[0] = i;
        });
        ImageIO.write(img[0], "png", out.resolve(name + ".png").toFile());
    }
}

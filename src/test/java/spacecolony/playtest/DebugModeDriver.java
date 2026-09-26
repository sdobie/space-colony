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
import javax.swing.SwingUtilities;
import spacecolony.debug.DebugLogging;
import spacecolony.engine.Engine;
import spacecolony.engine.Selection;
import spacecolony.sim.EventKind;
import spacecolony.ui.SpaceColonyFrame;
import spacecolony.world.WorldGenerator;

/**
 * Drives debug mode through the real window (Plan 5 debug-mode checklist). Requires a
 * desktop session — run via {@code ./gradlew debugPlayTest}. Screenshots land in
 * build/playtest.
 */
public class DebugModeDriver {
    static Engine engine;
    static SpaceColonyFrame frame;
    static Path out;
    static final List<String> failures = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        out = Path.of(args.length > 0 ? args[0] : "build/playtest");
        Files.createDirectories(out);
        DebugLogging.install(Level.INFO);
        DebugLogging.installUncaughtHandler();

        SwingUtilities.invokeAndWait(() -> {
            engine = new Engine(WorldGenerator.generate(1L));
            frame = new SpaceColonyFrame(engine);
            frame.setVisible(true);
            frame.toFront();
        });
        Thread.sleep(1200);
        check("debug off at launch", !engine.debugEnabled());

        // Real key press through the window's input map.
        Robot robot = new Robot();
        robot.keyPress(KeyEvent.VK_CONTROL);
        robot.keyPress(KeyEvent.VK_D);
        robot.keyRelease(KeyEvent.VK_D);
        robot.keyRelease(KeyEvent.VK_CONTROL);
        robot.waitForIdle();
        Thread.sleep(300);
        check("Ctrl+D turns debug on", engine.debugEnabled());
        check("overlay visible", frame.debugUi().overlay().isShowing());

        clickButton("Step 1 tick");
        check("Step 1 tick advances to tick 1", engine.world().tick == 1);

        SwingUtilities.invokeAndWait(() -> frame.debugUi().controller().runTicks(500));
        check("Run 500 ticks lands on tick 501", engine.world().tick == 501);

        SwingUtilities.invokeAndWait(() -> {
            engine.setSelection(Selection.site("site-earth-hub"));
            frame.debugUi().controller().triggerEvent("earth", EventKind.EQUIPMENT_FAILURE);
        });
        check("forced event is newest", engine.world().recentEvents.peekLast().kind() == EventKind.EQUIPMENT_FAILURE);
        Thread.sleep(600);
        shot(frame, "debug-01-overlay");

        // Modal inspector blocks its caller, so open it from a later EDT turn and capture it.
        SwingUtilities.invokeLater(() -> clickNow(frame, "Inspect selection…"));
        JDialog inspector = waitForDialog("Inspect site site-earth-hub");
        check("inspector opens for selection", inspector != null);
        if (inspector != null) {
            Thread.sleep(300);
            shot(inspector, "debug-02-inspector");
            SwingUtilities.invokeAndWait(inspector::dispose);
        }

        SwingUtilities.invokeAndWait(() -> clickNow(frame, "Log viewer…"));
        JDialog logs = waitForDialog("Log viewer");
        check("log viewer opens", logs != null);
        if (logs != null) {
            Thread.sleep(800);
            shot(logs, "debug-03-log-viewer");
            SwingUtilities.invokeAndWait(logs::dispose);
        }

        // An uncaught EDT exception should surface in the overlay.
        SwingUtilities.invokeLater(() -> { throw new IllegalStateException("debug driver test exception"); });
        Thread.sleep(900);
        shot(frame, "debug-04-exception-banner");

        // Closing the dialogs can leave focus nowhere without a window manager.
        SwingUtilities.invokeAndWait(() -> { frame.toFront(); frame.requestFocus(); });
        Thread.sleep(300);
        robot.keyPress(KeyEvent.VK_CONTROL);
        robot.keyPress(KeyEvent.VK_D);
        robot.keyRelease(KeyEvent.VK_D);
        robot.keyRelease(KeyEvent.VK_CONTROL);
        robot.waitForIdle();
        Thread.sleep(300);
        check("Ctrl+D turns debug off", !engine.debugEnabled() && !frame.debugUi().overlay().isShowing());

        System.out.println(failures.isEmpty() ? "ALL PASS" : "FAILURES: " + failures);
        System.exit(failures.isEmpty() ? 0 : 1);
    }

    static void check(String what, boolean ok) {
        System.out.println((ok ? "PASS  " : "FAIL  ") + what);
        if (!ok) failures.add(what);
    }

    static void clickButton(String text) throws Exception {
        SwingUtilities.invokeAndWait(() -> clickNow(frame, text));
    }

    static void clickNow(Container root, String text) {
        AbstractButton b = find(root, text);
        if (b == null) throw new IllegalStateException("No button " + text);
        b.doClick();
    }

    static AbstractButton find(Container c, String text) {
        for (Component k : c.getComponents()) {
            if (k instanceof AbstractButton b && text.equals(b.getText())) return b;
            if (k instanceof Container kc) {
                AbstractButton hit = find(kc, text);
                if (hit != null) return hit;
            }
        }
        return null;
    }

    static JDialog waitForDialog(String title) throws Exception {
        for (int i = 0; i < 40; i++) {
            for (Window w : Window.getWindows()) {
                if (w instanceof JDialog d && d.isShowing() && title.equals(d.getTitle())) return d;
            }
            Thread.sleep(100);
        }
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

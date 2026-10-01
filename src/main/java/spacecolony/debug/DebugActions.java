package spacecolony.debug;

import java.awt.Cursor;
import java.awt.event.ActionEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import spacecolony.engine.EdtGuard;
import spacecolony.engine.Engine;
import spacecolony.engine.EngineEvent;
import spacecolony.engine.Selection;
import spacecolony.engine.Speed;
import spacecolony.save.SaveFile;
import spacecolony.sim.Building;
import spacecolony.sim.Site;
import spacecolony.sim.World;

/** One Swing Action per debug control, shared by the overlay buttons and the Debug menu. */
public final class DebugActions {
    private static final Logger LOG = Logger.getLogger("spacecolony.debug");
    static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    public static final int DETERMINISM_TICKS = 1000;

    private final DebugController debug;
    private final Engine engine;
    public final Action step;
    public final Action runN;
    public final Action triggerEvent;
    public final Action finishConstruction;
    public final Action surveyAll;
    public final Action dumpWorld;
    public final Action determinism;
    public final Action inspector;
    public final Action logViewer;
    public final Action throwTest;

    DebugActions(DebugController debug) {
        this.debug = debug;
        this.engine = debug.engine();
        step = action("Step", this::doStep);
        runN = action("Run N…", this::doRunN);
        triggerEvent = action("Trigger event…", () -> TriggerEventDialog.show(debug.frame(), engine));
        finishConstruction = action("Finish construction", this::doFinishConstruction);
        surveyAll = action("Survey all bodies", () ->
            engine.applyDebugEdit("survey all bodies", DebugActions::surveyAll));
        dumpWorld = action("Dump world", this::doDump);
        determinism = action("Determinism check", this::doDeterminism);
        inspector = action("Inspector", this::doInspect);
        logViewer = action("Log viewer", () -> LogViewerDialog.show(debug.frame(), engine, Level.ALL));
        throwTest = action("Throw test exception", () -> {
            throw new IllegalStateException("Debug test exception");
        });
        engine.addListener(e -> { if (e instanceof EngineEvent.SpeedChanged) syncEnabled(); });
        syncEnabled();
    }

    /** Directory for world dumps: {@code ~/.space-colony/debug/}. */
    public static Path debugDir() {
        return Paths.get(System.getProperty("user.home"), ".space-colony", "debug");
    }

    private void syncEnabled() {
        boolean paused = engine.speed().isPaused();
        step.setEnabled(paused);
        triggerEvent.setEnabled(paused);
        finishConstruction.setEnabled(paused);
        surveyAll.setEnabled(paused);
    }

    /** Surveys every body not yet surveyed, one event each. */
    static void surveyAll(spacecolony.sim.World w) {
        for (spacecolony.sim.Body b : w.bodies) w.survey(b.id, "Debug");
    }

    /** Selected colony's builds and upgrades all finish on the next tick. */
    private void doFinishConstruction() {
        Selection sel = engine.selection();
        if (sel.kind() != Selection.Kind.SITE || engine.world().findSite(sel.id()) == null) {
            JOptionPane.showMessageDialog(debug.frame(), "Select a colony first.", "Finish construction",
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        engine.applyDebugEdit("finish construction at " + sel.id(),
            w -> finishConstruction(w.findSite(sel.id())));
    }

    /** Sets every build and upgrade under way at {@code s} to one day left. */
    static void finishConstruction(Site s) {
        for (Building b : s.buildings) if (b.daysLeft > 1) b.daysLeft = 1;
    }

    private void doStep() {
        engine.step();
    }

    private void doRunN() {
        Speed prior = engine.speed();
        engine.setSpeed(Speed.PAUSED);
        try {
            String s = JOptionPane.showInputDialog(debug.frame(),
                "Ticks to run (1–" + Engine.MAX_SILENT_TICKS + "):", "1000");
            if (s == null) return;
            int n;
            try {
                n = Integer.parseInt(s.trim());
            } catch (NumberFormatException e) {
                n = -1;
            }
            if (n < 1 || n > Engine.MAX_SILENT_TICKS) {
                JOptionPane.showMessageDialog(debug.frame(), "Enter a whole number from 1 to "
                    + Engine.MAX_SILENT_TICKS + ".", "Run N", JOptionPane.WARNING_MESSAGE);
                return;
            }
            runSilently(n);
        } finally {
            engine.setSpeed(prior);
        }
    }

    /** Run {@code n} ticks with a wait cursor and log the elapsed time. */
    void runSilently(int n) {
        debug.frame().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        long t0 = System.nanoTime();
        try {
            engine.advanceSilently(n);
        } finally {
            debug.frame().setCursor(Cursor.getDefaultCursor());
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        LOG.info("Ran " + n + " ticks in " + ms + " ms");
        debug.overlay().setStatus("Ran " + n + " ticks in " + ms + " ms");
    }

    private void doDump() {
        World w = engine.world();
        // Serialise on the EDT (the world is EDT-owned); only the file write goes to the worker.
        String json = SaveFile.toJson(w);
        Path file = debugDir().resolve("world-" + LocalDateTime.now().format(STAMP) + "-t" + w.tick + ".json");
        new SwingWorker<Path, Void>() {
            @Override protected Path doInBackground() throws Exception {
                Files.createDirectories(file.getParent());
                Files.writeString(file, json);
                return file;
            }
            @Override protected void done() {
                try {
                    get();
                    LOG.info("Dumped world to " + file);
                    debug.overlay().setStatus("Dumped to " + file);
                } catch (InterruptedException | ExecutionException e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    LOG.log(Level.SEVERE, "World dump failed", cause);
                    debug.overlay().setStatus("Dump failed: " + cause.getMessage());
                }
            }
        }.execute();
    }

    private void doDeterminism() {
        String snapshot = SaveFile.toJson(engine.world());
        debug.overlay().setStatus("Determinism check running…");
        new SwingWorker<DeterminismCheck.Result, Void>() {
            @Override protected DeterminismCheck.Result doInBackground() throws Exception {
                return DeterminismCheck.run(snapshot, DETERMINISM_TICKS);
            }
            @Override protected void done() {
                try {
                    DeterminismCheck.Result r = get();
                    String msg = r.match()
                        ? "Determinism OK (" + r.ticks() + " ticks, " + r.millis() + " ms)"
                        : "Determinism MISMATCH: " + r.firstDiff();
                    LOG.log(r.match() ? Level.INFO : Level.SEVERE, msg);
                    debug.overlay().setStatus(msg);
                } catch (InterruptedException | ExecutionException e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    CrashHandler.reportIfInstalled(cause);
                    debug.overlay().setStatus("Determinism check failed: " + cause);
                }
            }
        }.execute();
    }

    private void doInspect() {
        Selection sel = engine.selection();
        Object target = switch (sel.kind()) {
            case BODY -> engine.world().findBody(sel.id());
            case SITE -> engine.world().findSite(sel.id());
            case SHIP -> engine.world().findShip(sel.id());
            case NONE -> null;
        };
        if (target == null) {
            ObjectInspectorDialog.inspect(debug.frame(), engine, engine.world(), "World");
        } else {
            String kind = sel.kind().name().charAt(0) + sel.kind().name().substring(1).toLowerCase();
            ObjectInspectorDialog.inspect(debug.frame(), engine, target, kind + " " + sel.id());
        }
    }

    private static Action action(String name, Runnable body) {
        return new AbstractAction(name) {
            @Override public void actionPerformed(ActionEvent e) {
                EdtGuard.assertEdt();
                body.run();
            }
        };
    }
}

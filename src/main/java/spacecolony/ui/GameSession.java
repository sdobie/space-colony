package spacecolony.ui;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingWorker;
import spacecolony.debug.CrashHandler;
import spacecolony.engine.EdtGuard;
import spacecolony.engine.Engine;
import spacecolony.engine.Speed;
import spacecolony.save.SaveFile;
import spacecolony.save.SaveSlots;
import spacecolony.sim.World;

/**
 * The current save slot and the ways a game ends: Quit (File → Quit, the window close box, the
 * crash dialog) and, since Plan 6, Main Menu. Both autosave beside the current slot, or to
 * {@code _autosave.json} for a game never saved to a slot, except in a tutorial. Also runs the
 * timed autosave.
 */
public final class GameSession {
    /** A tutorial game can't be saved and doesn't autosave (Plan 6 §5.7). */
    public enum Mode { NORMAL, TUTORIAL }

    /** The dialogs and status line the session needs, swappable so tests don't open windows. */
    public interface Ui {
        /** Asks before ending the game; {@code toMenu} is false for Quit. */
        boolean confirmQuit(Mode mode, boolean toMenu);
        boolean quitAnyway(String autosaveError);
        /** Transient status text, e.g. "Saved “colony”" or "Autosaved". */
        void toast(String text);
    }

    private static final Logger LOG = Logger.getLogger("spacecolony.save");

    private final Engine engine;
    private final SaveSlots slots;
    private final Ui ui;
    private final IntConsumer exit;
    private final Runnable mainMenu;
    private final BooleanSupplier confirmQuit;
    private final List<Consumer<Mode>> modeListeners = new ArrayList<>();
    private String currentSlot;
    private Mode mode = Mode.NORMAL;
    /** World and tick of the last autosave (or of the game's start), so an idle game isn't rewritten. */
    private World autosavedWorld;
    private long autosavedTick;

    public GameSession(Engine engine, SaveSlots slots, Ui ui, IntConsumer exit) {
        this(engine, slots, ui, exit, null, () -> true);
    }

    /**
     * @param mainMenu    what Main Menu does after the autosave; null when there's no main menu
     * @param confirmQuit whether Quit and Main Menu ask first (the options' "Confirm before quitting")
     */
    public GameSession(Engine engine, SaveSlots slots, Ui ui, IntConsumer exit,
                       Runnable mainMenu, BooleanSupplier confirmQuit) {
        this.engine = engine;
        this.slots = slots;
        this.ui = ui;
        this.exit = exit;
        this.mainMenu = mainMenu;
        this.confirmQuit = confirmQuit;
        markAutosaved();
    }

    /** Slot the current game was last saved to or loaded from; null for an unnamed game. */
    public String currentSlot() { return currentSlot; }
    public SaveSlots slots() { return slots; }
    public Mode mode() { return mode; }
    public boolean canLeaveToMenu() { return mainMenu != null; }

    public void setMode(Mode m) {
        if (m == mode) return;
        mode = m;
        for (Consumer<Mode> l : new ArrayList<>(modeListeners)) l.accept(m);
    }

    public void addModeListener(Consumer<Mode> l) { modeListeners.add(l); }

    public void onSavedAs(String name) { currentSlot = name; }
    public void onNewGame() { currentSlot = null; markAutosaved(); }

    /** {@code <n>.json} or {@code <n>.autosave.json} in the slots dir → {@code n}; anything else → null. */
    public void onLoaded(Path file) { currentSlot = slots.slotOf(file); markAutosaved(); }

    public void savedToast(String label) { ui.toast("Saved “" + label + "”"); }

    /** File → Quit, the close box, and the crash dialog's Quit. */
    public void quit() { end(false, () -> exit.accept(0)); }

    /** File → Main Menu. Does nothing when there is no main menu. */
    public void leave() {
        if (mainMenu != null) end(true, mainMenu);
    }

    /**
     * Pauses, confirms, autosaves (NORMAL games only), then runs {@code then}. The autosave runs
     * synchronously on the EDT: the JVM may be about to exit and a worker would race it.
     * Cancelling leaves the game paused.
     */
    private void end(boolean toMenu, Runnable then) {
        EdtGuard.assertEdt();
        engine.setSpeed(Speed.PAUSED);
        if (confirmQuit.getAsBoolean() && !ui.confirmQuit(mode, toMenu)) return;
        if (mode == Mode.NORMAL) {
            Path target = slots.autosavePath(currentSlot);
            try {
                SaveFile.save(engine.world(), target);
                LOG.info("Autosaved to " + target);
            } catch (IOException | RuntimeException e) {
                LOG.log(Level.SEVERE, "Autosave failed", e);
                if (!ui.quitAnyway(e.getMessage())) return;
            }
        }
        then.run();
    }

    /**
     * Timed autosave (Plan 6 §4.4): snapshots on the EDT and writes on a worker, so the write
     * never races a tick. Skips tutorials and games whose tick hasn't moved since the last one.
     * A failure is logged and toasted, never a modal.
     */
    public void autosaveInBackground() {
        EdtGuard.assertEdt();
        if (mode != Mode.NORMAL) return;
        World w = engine.world();
        if (w == autosavedWorld && w.tick == autosavedTick) return;
        final String json = SaveFile.toJson(w);
        final Path target = slots.autosavePath(currentSlot);
        final long tick = w.tick;
        new SwingWorker<Void, Void>() {
            IOException ioErr;
            @Override protected Void doInBackground() {
                try { SaveFile.writeJson(json, target); }
                catch (IOException e) { ioErr = e; }
                return null;
            }
            @Override protected void done() {
                try {
                    get();
                } catch (InterruptedException | ExecutionException ex) {
                    CrashHandler.reportIfInstalled(ex.getCause() == null ? ex : ex.getCause());
                    return;
                }
                if (ioErr != null) {
                    LOG.log(Level.WARNING, "Timed autosave to " + target + " failed", ioErr);
                    ui.toast("Autosave failed");
                } else {
                    LOG.info("Autosaved to " + target);
                    ui.toast("Autosaved");
                }
            }
        }.execute();
        // Don't queue a second write of the same tick while this one is in flight.
        autosavedWorld = w;
        autosavedTick = tick;
    }

    private void markAutosaved() {
        autosavedWorld = engine.world();
        autosavedTick = autosavedWorld.tick;
    }
}

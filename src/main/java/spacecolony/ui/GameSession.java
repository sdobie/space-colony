package spacecolony.ui;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.IntConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import spacecolony.engine.EdtGuard;
import spacecolony.engine.Engine;
import spacecolony.engine.Speed;
import spacecolony.save.SaveFile;
import spacecolony.save.SaveSlots;

/**
 * The current save slot and the one quit path (File → Quit, the window close box, and later the
 * crash dialog). Quitting autosaves beside the current slot, or to {@code _autosave.json} for a
 * game that has never been saved to a slot.
 */
public final class GameSession {
    /** The dialogs quit needs, swappable so tests don't open windows. */
    public interface Ui {
        boolean confirmQuit();
        boolean quitAnyway(String autosaveError);
        void savedToast(String label);
    }

    private static final Logger LOG = Logger.getLogger("spacecolony.save");

    private final Engine engine;
    private final SaveSlots slots;
    private final Ui ui;
    private final IntConsumer exit;
    private String currentSlot;

    public GameSession(Engine engine, SaveSlots slots, Ui ui, IntConsumer exit) {
        this.engine = engine;
        this.slots = slots;
        this.ui = ui;
        this.exit = exit;
    }

    /** Slot the current game was last saved to or loaded from; null for an unnamed game. */
    public String currentSlot() { return currentSlot; }
    public SaveSlots slots() { return slots; }

    public void onSavedAs(String name) { currentSlot = name; }
    public void onNewGame() { currentSlot = null; }

    /** {@code <n>.json} or {@code <n>.autosave.json} in the slots dir → {@code n}; anything else → null. */
    public void onLoaded(Path file) { currentSlot = slots.slotOf(file); }

    public void savedToast(String label) { ui.savedToast(label); }

    /**
     * Pauses, confirms, autosaves, then exits. The autosave runs synchronously on the EDT: the
     * JVM is about to exit and a worker would race it. Cancelling leaves the game paused.
     */
    public void quit() {
        EdtGuard.assertEdt();
        engine.setSpeed(Speed.PAUSED);
        if (!ui.confirmQuit()) return;
        Path target = slots.autosavePath(currentSlot);
        try {
            SaveFile.save(engine.world(), target);
            LOG.info("Autosaved to " + target);
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.SEVERE, "Autosave failed", e);
            if (!ui.quitAnyway(e.getMessage())) return;
        }
        exit.accept(0);
    }
}

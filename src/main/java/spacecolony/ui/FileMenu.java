package spacecolony.ui;

import java.awt.event.ActionEvent;
import java.io.IOException;
import javax.swing.AbstractAction;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import spacecolony.engine.EdtGuard;
import spacecolony.engine.Engine;
import spacecolony.engine.Speed;
import spacecolony.save.IncompatibleSaveException;
import spacecolony.save.JsonParseException;
import spacecolony.save.SaveSlots;
import spacecolony.sim.World;
import spacecolony.ui.dialogs.SaveSlotDialog;
import spacecolony.world.WorldGenerator;

/**
 * File menu (New / Save / Load / Quit) mounted on the main frame. Save and Load go through the
 * named-slot picker; quitting (menu or window close) autosaves beside the current slot first.
 */
public final class FileMenu extends JMenuBar {
    private final JFrame owner;
    private final Engine engine;
    private final SaveSlots slots;
    private final Runnable exit;
    /** Slot the current game was last saved to or loaded from; null for a new, unsaved game. */
    private String currentSlot;
    /** True while a quit's autosave is in flight, so a second close click doesn't start another. */
    private boolean quitting;

    public FileMenu(JFrame owner, Engine engine) {
        this(owner, engine, SaveSlots.atDefaultLocation(), () -> System.exit(0));
    }

    FileMenu(JFrame owner, Engine engine, SaveSlots slots, Runnable exit) {
        this.owner = owner;
        this.engine = engine;
        this.slots = slots;
        this.exit = exit;
        JMenu file = new JMenu("File");
        file.add(new JMenuItem(new NewAction()));
        file.add(new JMenuItem(new SaveAction()));
        file.add(new JMenuItem(new LoadAction()));
        file.addSeparator();
        file.add(new JMenuItem(new QuitAction()));
        add(file);
    }

    /** Slot the current game belongs to; null until it is saved to or loaded from a named slot. */
    String currentSlot() { return currentSlot; }

    /** Swaps in {@code w} as the running game, remembering which slot (if any) it came from. */
    void startGame(World w, String slot) {
        EdtGuard.assertEdt();
        engine.reset(w);
        currentSlot = slot;
    }

    /**
     * Autosaves, then exits. File → Quit asks first; closing the window does not, matching the
     * old close button. If the autosave fails the player can still quit or go back to the game.
     */
    public void quit(boolean confirm) {
        EdtGuard.assertEdt();
        if (quitting) return;
        Speed prior = engine.speed();
        engine.setSpeed(Speed.PAUSED);
        if (confirm) {
            int r = JOptionPane.showConfirmDialog(owner, "Quit Space Colony?",
                "Quit", JOptionPane.OK_CANCEL_OPTION);
            if (r != JOptionPane.OK_OPTION) { engine.setSpeed(prior); return; }
        }
        quitting = true;
        final World world = engine.world();
        final String slot = currentSlot;
        new SwingWorker<Void, Void>() {
            Exception err;
            @Override protected Void doInBackground() {
                try { slots.autosave(world, slot); }
                catch (IOException | RuntimeException ex) { err = ex; }
                return null;
            }
            @Override protected void done() {
                EdtGuard.assertEdt();
                if (err != null) {
                    int r = JOptionPane.showConfirmDialog(owner,
                        "Autosave failed: " + err.getMessage() + "\nQuit anyway?",
                        "Autosave Error", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                    if (r != JOptionPane.YES_OPTION) {
                        quitting = false;
                        engine.setSpeed(prior);
                        return;
                    }
                }
                exit.run();
            }
        }.execute();
    }

    private final class NewAction extends AbstractAction {
        NewAction() { super("New Game"); }
        @Override public void actionPerformed(ActionEvent e) {
            EdtGuard.assertEdt();
            Speed prior = engine.speed();
            engine.setSpeed(Speed.PAUSED);
            int choice = JOptionPane.showConfirmDialog(owner,
                "Discard the current game?", "New Game", JOptionPane.OK_CANCEL_OPTION);
            if (choice != JOptionPane.OK_OPTION) { engine.setSpeed(prior); return; }
            String seedStr = JOptionPane.showInputDialog(owner,
                "Seed:", Long.toString(System.currentTimeMillis()));
            if (seedStr == null) { engine.setSpeed(prior); return; }
            try {
                long seed = Long.parseLong(seedStr.trim());
                startGame(WorldGenerator.generate(seed), null);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(owner, "Not a valid number: " + seedStr,
                    "New Game", JOptionPane.ERROR_MESSAGE);
            }
            // Leave the game paused after reset; player presses 1x to start.
        }
    }

    private final class SaveAction extends AbstractAction {
        SaveAction() { super("Save…"); }
        @Override public void actionPerformed(ActionEvent e) {
            EdtGuard.assertEdt();
            Speed prior = engine.speed();
            engine.setSpeed(Speed.PAUSED);
            String name = SaveSlotDialog.showSave(owner, slots, currentSlot);
            if (name == null) { engine.setSpeed(prior); return; }
            final World world = engine.world();
            new SwingWorker<Void, Void>() {
                IOException ioErr;
                @Override protected Void doInBackground() {
                    try { slots.save(world, name); }
                    catch (IOException ex) { ioErr = ex; }
                    return null;
                }
                @Override protected void done() {
                    EdtGuard.assertEdt();
                    if (ioErr != null) {
                        JOptionPane.showMessageDialog(owner,
                            "Could not save: " + ioErr.getMessage(),
                            "Save Error", JOptionPane.ERROR_MESSAGE);
                    } else {
                        currentSlot = name;
                    }
                    engine.setSpeed(prior);
                }
            }.execute();
        }
    }

    private final class LoadAction extends AbstractAction {
        LoadAction() { super("Load…"); }
        @Override public void actionPerformed(ActionEvent e) {
            EdtGuard.assertEdt();
            Speed prior = engine.speed();
            engine.setSpeed(Speed.PAUSED);
            SaveSlots.Slot slot = SaveSlotDialog.showLoad(owner, slots);
            if (slot == null) { engine.setSpeed(prior); return; }
            new SwingWorker<World, Void>() {
                Exception err;
                @Override protected World doInBackground() {
                    try { return slots.load(slot); }
                    catch (Exception ex) { err = ex; return null; }
                }
                @Override protected void done() {
                    EdtGuard.assertEdt();
                    if (err instanceof IncompatibleSaveException inc) {
                        JOptionPane.showMessageDialog(owner,
                            "This save was written with schema v" + inc.fileSchemaVersion
                                + "; the current game uses v" + inc.currentSchemaVersion
                                + ". Cannot load this save.",
                            "Incompatible Save", JOptionPane.WARNING_MESSAGE);
                    } else if (err instanceof JsonParseException jpe) {
                        JOptionPane.showMessageDialog(owner,
                            "Save file is not valid JSON: " + jpe.getMessage(),
                            "Load Error", JOptionPane.ERROR_MESSAGE);
                    } else if (err != null) {
                        JOptionPane.showMessageDialog(owner,
                            "Could not load: " + err.getMessage(),
                            "Load Error", JOptionPane.ERROR_MESSAGE);
                    } else {
                        try { startGame(get(), slot.owningSlot()); }
                        catch (Exception ignore) { /* covered by err branch above */ }
                    }
                    engine.setSpeed(prior);
                }
            }.execute();
        }
    }

    private final class QuitAction extends AbstractAction {
        QuitAction() { super("Quit"); }
        @Override public void actionPerformed(ActionEvent e) {
            quit(true);
        }
    }
}

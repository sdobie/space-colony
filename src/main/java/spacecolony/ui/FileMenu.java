package spacecolony.ui;

import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Path;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import spacecolony.engine.EdtGuard;
import spacecolony.engine.Engine;
import spacecolony.engine.Speed;
import spacecolony.save.IncompatibleSaveException;
import spacecolony.save.JsonParseException;
import spacecolony.save.SaveFile;
import spacecolony.save.SlotInfo;
import spacecolony.sim.World;
import spacecolony.world.WorldGenerator;

/**
 * File menu mounted on the main frame: New Game, Save, Save As…, Load…, Load from file…, Quit.
 * Save/Save As/Load go through named slots ({@link SaveSlotDialog}); Load from file… keeps the
 * Plan 4 file chooser for hand-edited or out-of-directory saves. Quit goes through
 * {@link GameSession#quit}, which autosaves.
 */
public final class FileMenu extends JMenuBar {
    private final JFrame owner;
    private final Engine engine;
    private final GameSession session;

    public FileMenu(JFrame owner, Engine engine, GameSession session) {
        this.owner = owner;
        this.engine = engine;
        this.session = session;
        JMenu file = new JMenu("File");
        file.add(new JMenuItem(new NewAction()));
        file.add(new JMenuItem(withKey(new SaveAction(), KeyEvent.VK_S, 0)));
        file.add(new JMenuItem(withKey(new SaveAsAction(), KeyEvent.VK_S, InputEvent.SHIFT_DOWN_MASK)));
        file.add(new JMenuItem(withKey(new LoadAction(), KeyEvent.VK_O, 0)));
        file.add(new JMenuItem(new LoadFromFileAction()));
        file.addSeparator();
        file.add(new JMenuItem(withKey(new QuitAction(), KeyEvent.VK_Q, 0)));
        add(file);
    }

    /** Menu shortcut: Ctrl on Windows/Linux, Cmd on macOS (plain Ctrl when headless, e.g. in tests). */
    private static Action withKey(Action a, int key, int extraMods) {
        int menuMask = GraphicsEnvironment.isHeadless()
            ? InputEvent.CTRL_DOWN_MASK
            : Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        a.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(key, menuMask | extraMods));
        return a;
    }

    private JFileChooser chooser(String dialogTitle, boolean save) {
        JFileChooser ch = new JFileChooser(session.slots().dir().toFile());
        ch.setDialogTitle(dialogTitle);
        ch.setFileFilter(new FileNameExtensionFilter("Space Colony saves (*.json)", "json"));
        if (save) ch.setDialogType(JFileChooser.SAVE_DIALOG);
        return ch;
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
                engine.reset(WorldGenerator.generate(seed));
                session.onNewGame();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(owner, "Not a valid number: " + seedStr,
                    "New Game", JOptionPane.ERROR_MESSAGE);
            }
            // Leave the game paused after reset; player presses 1x to start.
        }
    }

    /** Saves to the current slot, or behaves as Save As… for an unnamed game. */
    private final class SaveAction extends AbstractAction {
        SaveAction() { super("Save"); }
        @Override public void actionPerformed(ActionEvent e) {
            EdtGuard.assertEdt();
            String slot = session.currentSlot();
            if (slot == null) { saveAs(); return; }
            Speed prior = engine.speed();
            engine.setSpeed(Speed.PAUSED);
            saveTo(slot, prior);
        }
    }

    private final class SaveAsAction extends AbstractAction {
        SaveAsAction() { super("Save As…"); }
        @Override public void actionPerformed(ActionEvent e) {
            EdtGuard.assertEdt();
            saveAs();
        }
    }

    private void saveAs() {
        Speed prior = engine.speed();
        engine.setSpeed(Speed.PAUSED);
        String name = SaveSlotDialog.saveAs(owner, session.slots());
        if (name == null) { engine.setSpeed(prior); return; }
        saveTo(name, prior);
    }

    /** Writes the slot in a worker; on success the game belongs to that slot and the top bar says so. */
    private void saveTo(String name, Speed prior) {
        final Path target = session.slots().slotPath(name);
        final World world = engine.world();
        new SwingWorker<Void, Void>() {
            IOException ioErr;
            @Override protected Void doInBackground() {
                try { SaveFile.save(world, target); }
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
                    session.onSavedAs(name);
                    session.savedToast(name);
                }
                engine.setSpeed(prior);
            }
        }.execute();
    }

    private final class LoadAction extends AbstractAction {
        LoadAction() { super("Load…"); }
        @Override public void actionPerformed(ActionEvent e) {
            EdtGuard.assertEdt();
            Speed prior = engine.speed();
            engine.setSpeed(Speed.PAUSED);
            SlotInfo slot = SaveSlotDialog.load(owner, session.slots());
            if (slot == null) { engine.setSpeed(prior); return; }
            loadFrom(slot.path(), prior);
        }
    }

    private final class LoadFromFileAction extends AbstractAction {
        LoadFromFileAction() { super("Load from file…"); }
        @Override public void actionPerformed(ActionEvent e) {
            EdtGuard.assertEdt();
            Speed prior = engine.speed();
            engine.setSpeed(Speed.PAUSED);
            JFileChooser ch = chooser("Load Game", false);
            int r = ch.showOpenDialog(owner);
            if (r != JFileChooser.APPROVE_OPTION) { engine.setSpeed(prior); return; }
            Path file = ch.getSelectedFile().toPath();
            loadFrom(file, prior);
        }
    }

    /** Loads in a worker with Plan 4's error dialogs; on success swaps the world in. */
    private void loadFrom(Path file, Speed prior) {
        new SwingWorker<World, Void>() {
            Exception err;
            @Override protected World doInBackground() {
                try { return SaveFile.load(file); }
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
                    try {
                        engine.reset(get());
                        session.onLoaded(file);
                    } catch (Exception ignore) { /* covered by err branch above */ }
                }
                engine.setSpeed(prior);
            }
        }.execute();
    }

    private final class QuitAction extends AbstractAction {
        QuitAction() { super("Quit"); }
        @Override public void actionPerformed(ActionEvent e) {
            EdtGuard.assertEdt();
            session.quit();
        }
    }
}

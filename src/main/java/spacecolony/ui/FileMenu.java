package spacecolony.ui;

import java.awt.event.ActionEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.swing.AbstractAction;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import spacecolony.engine.EdtGuard;
import spacecolony.engine.Engine;
import spacecolony.engine.Speed;
import spacecolony.save.IncompatibleSaveException;
import spacecolony.save.JsonParseException;
import spacecolony.save.SaveFile;
import spacecolony.sim.World;
import spacecolony.world.WorldGenerator;

/** File menu (New / Save / Load / Quit) mounted on the main frame. */
public final class FileMenu extends JMenuBar {
    private static final Path SAVES_DIR =
        Paths.get(System.getProperty("user.home"), ".space-colony", "saves");

    private final JFrame owner;
    private final Engine engine;

    public FileMenu(JFrame owner, Engine engine) {
        this.owner = owner;
        this.engine = engine;
        JMenu file = new JMenu("File");
        file.add(new JMenuItem(new NewAction()));
        file.add(new JMenuItem(new SaveAction()));
        file.add(new JMenuItem(new LoadAction()));
        file.addSeparator();
        file.add(new JMenuItem(new QuitAction()));
        add(file);
    }

    private JFileChooser chooser(String dialogTitle, boolean save) {
        JFileChooser ch = new JFileChooser(SAVES_DIR.toFile());
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
            JFileChooser ch = chooser("Save Game", true);
            int r = ch.showSaveDialog(owner);
            if (r != JFileChooser.APPROVE_OPTION) { engine.setSpeed(prior); return; }
            Path file = ch.getSelectedFile().toPath();
            if (!file.getFileName().toString().endsWith(".json")) {
                file = file.resolveSibling(file.getFileName().toString() + ".json");
            }
            final Path target = file;
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
            JFileChooser ch = chooser("Load Game", false);
            int r = ch.showOpenDialog(owner);
            if (r != JFileChooser.APPROVE_OPTION) { engine.setSpeed(prior); return; }
            Path file = ch.getSelectedFile().toPath();
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
                        try { engine.reset(get()); }
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
            EdtGuard.assertEdt();
            int r = JOptionPane.showConfirmDialog(owner, "Quit Space Colony?",
                "Quit", JOptionPane.OK_CANCEL_OPTION);
            if (r == JOptionPane.OK_OPTION) System.exit(0);
        }
    }
}

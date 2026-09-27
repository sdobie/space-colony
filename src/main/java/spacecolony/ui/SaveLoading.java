package spacecolony.ui;

import java.awt.Component;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import spacecolony.debug.CrashHandler;
import spacecolony.engine.EdtGuard;
import spacecolony.save.IncompatibleSaveException;
import spacecolony.save.JsonParseException;
import spacecolony.save.SaveFile;
import spacecolony.sim.World;

/**
 * Loads a save in a worker with Plan 4's error dialogs. Shared by the File menu and the title
 * screen so both report bad saves the same way.
 */
public final class SaveLoading {
    private static final Logger LOG = Logger.getLogger("spacecolony.save");

    private SaveLoading() {}

    /**
     * Reads {@code file} off the EDT. On success calls {@code onLoaded} with the world; on a
     * failure shows the matching dialog over {@code owner}. {@code onFinally} runs on the EDT
     * either way, after the other callback.
     */
    public static void load(Component owner, Path file, Consumer<World> onLoaded, Runnable onFinally) {
        new SwingWorker<World, Void>() {
            Exception err;
            @Override protected World doInBackground() {
                try { return SaveFile.load(file); }
                catch (Exception ex) { err = ex; return null; }
            }
            @Override protected void done() {
                EdtGuard.assertEdt();
                try {
                    if (err != null) LOG.log(Level.WARNING, "Load from " + file + " failed", err);
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
                            onLoaded.accept(get());
                            LOG.info("Loaded " + file);
                        } catch (InterruptedException | ExecutionException ex) {
                            CrashHandler.reportIfInstalled(ex.getCause() == null ? ex : ex.getCause());
                        }
                    }
                } finally {
                    onFinally.run();
                }
            }
        }.execute();
    }
}

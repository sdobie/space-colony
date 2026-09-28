package spacecolony.ui.startup;

import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import spacecolony.LaunchArgs;
import spacecolony.debug.DebugLogging;
import spacecolony.debug.ExceptionLog;
import spacecolony.engine.EdtGuard;
import spacecolony.engine.Engine;
import spacecolony.engine.Speed;
import spacecolony.options.Options;
import spacecolony.options.OptionsStore;
import spacecolony.save.SaveSlots;
import spacecolony.save.SlotInfo;
import spacecolony.sim.World;
import spacecolony.tutorial.TutorialScenario;
import spacecolony.ui.GameSession;
import spacecolony.ui.SaveLoading;
import spacecolony.ui.SaveSlotDialog;
import spacecolony.ui.SpaceColonyFrame;
import spacecolony.ui.dialogs.NewGameDialog;
import spacecolony.ui.dialogs.OptionsDialog;
import spacecolony.ui.tutorial.TutorialController;
import spacecolony.world.WorldGenerator;

/**
 * Owns the app's lifecycle (Plan 6 §3.5): splash → title → game → title. Each game gets a fresh
 * {@link Engine} and {@link SpaceColonyFrame}, so nothing from one game survives into the next.
 * Also routes the crash handler to whichever window is showing.
 */
public final class AppController implements TitleScreen.Listener {
    static final int SPLASH_MILLIS = 1500;
    private static final Logger LOG = Logger.getLogger("spacecolony.app");

    private final OptionsStore store;
    private final SaveSlots slots;
    private final ExceptionLog exceptions;
    private final LaunchArgs args;
    private final String version = LaunchArgs.version();
    private Options options;
    private List<SlotInfo> slotList = List.of();
    private TitleScreen title;
    private volatile SpaceColonyFrame frame;
    private TutorialController tutorial;

    public AppController(Options options, OptionsStore store, SaveSlots slots,
                         ExceptionLog exceptions, LaunchArgs args) {
        this.options = options;
        this.store = store;
        this.slots = slots;
        this.exceptions = exceptions;
        this.args = args;
    }

    /** Splash (unless skipped) then title, or straight into a game for {@code --seed}. */
    public void start() {
        EdtGuard.assertEdt();
        if (args.skipTitle()) {
            startGame(WorldGenerator.generate(args.seed()), null, GameSession.Mode.NORMAL);
            return;
        }
        boolean splash = options.showSplash() && !args.skipIntro() && !GraphicsEnvironment.isHeadless();
        if (!splash) {
            listSlots(list -> showTitle());
            return;
        }
        SplashWindow w = new SplashWindow(version);
        boolean[] listed = {false}, waited = {false}, done = {false};
        Runnable finish = () -> {
            if (done[0] || !listed[0]) return;
            done[0] = true;
            w.dispose();
            showTitle();
        };
        w.onSkip(finish);
        w.setStatus("Looking for saved colonies…", 0.4);
        w.setVisible(true);
        Timer minimum = new Timer(SPLASH_MILLIS, e -> { waited[0] = true; if (listed[0]) finish.run(); });
        minimum.setRepeats(false);
        minimum.start();
        listSlots(list -> {
            listed[0] = true;
            w.setStatus("Ready", 1.0);
            if (waited[0]) finish.run();
        });
    }

    // ---- title screen ----

    @Override public void onTitleAction(TitleScreen.Action a) {
        switch (a) {
            case CONTINUE -> {
                SlotInfo s = TitleScreen.continueTarget(slotList);
                if (s != null) load(s.path());
            }
            case NEW_GAME -> {
                Long seed = NewGameDialog.show(title);
                if (seed != null) startGame(WorldGenerator.generate(seed), null, GameSession.Mode.NORMAL);
            }
            case TUTORIAL -> startGame(TutorialScenario.world(), null, GameSession.Mode.TUTORIAL);
            case LOAD -> {
                SlotInfo s = SaveSlotDialog.load(title, slots);
                if (s != null) load(s.path());
            }
            case OPTIONS -> {
                Options updated = OptionsDialog.show(title, options);
                if (updated != null) {
                    saveOptions(updated);
                    refreshTitle();
                }
            }
            case QUIT -> System.exit(0);
        }
    }

    private void load(Path file) {
        SaveLoading.load(title, file, world -> startGame(world, slots.slotOf(file), GameSession.Mode.NORMAL), () -> {});
    }

    private void showTitle() {
        if (title == null) title = new TitleScreen(version, this);
        refreshTitle();
        if (options.startMaximized()) title.setExtendedState(Frame.MAXIMIZED_BOTH);
        title.setVisible(true);
        title.toFront();
    }

    private void refreshTitle() {
        title.update(options, slotList, args.debug() || options.startInDebug(), Instant.now());
    }

    /** Lists the saves off the EDT; a failure is logged and treated as "no saves". */
    private void listSlots(Consumer<List<SlotInfo>> then) {
        new SwingWorker<List<SlotInfo>, Void>() {
            @Override protected List<SlotInfo> doInBackground() throws IOException { return slots.list(); }
            @Override protected void done() {
                try {
                    slotList = get();
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Could not list saves in " + slots.dir(), e);
                    slotList = List.of();
                }
                then.accept(slotList);
            }
        }.execute();
    }

    // ---- games ----

    /** Builds a frame for {@code world} and shows it in place of the title. */
    void startGame(World world, String slotOrNull, GameSession.Mode mode) {
        EdtGuard.assertEdt();
        Engine engine = new Engine(world);
        SpaceColonyFrame.Hooks hooks = new SpaceColonyFrame.Hooks(System::exit, this::returnToTitle,
            () -> options, this::saveOptions);
        frame = new SpaceColonyFrame(engine, exceptions, hooks);
        frame.session().setMode(mode);
        if (slotOrNull != null) frame.session().onSavedAs(slotOrNull);
        engine.setDebugEnabled(args.debug() || options.startInDebug());
        // The tutorial starts paused: its second step is pressing 1×.
        engine.setSpeed(mode == GameSession.Mode.TUTORIAL ? Speed.PAUSED : options.startSpeed());
        if (options.startMaximized()) frame.setExtendedState(Frame.MAXIMIZED_BOTH);
        if (title != null) title.setVisible(false);
        frame.setVisible(true);
        if (mode == GameSession.Mode.TUTORIAL) {
            tutorial = new TutorialController(frame, engine,
                () -> saveOptions(options.withTutorialCompleted(true)));
        }
        LOG.info(() -> "Started " + mode + " game: seed=" + world.seed + " tick=" + world.tick
            + (slotOrNull == null ? "" : " slot=" + slotOrNull));
    }

    /** Main Menu: tear the game down, re-list saves so Continue points at it, show the title. */
    void returnToTitle() {
        EdtGuard.assertEdt();
        if (tutorial != null) { tutorial.dispose(); tutorial = null; }
        if (frame != null) { frame.dispose(); frame = null; }
        listSlots(list -> showTitle());
    }

    SpaceColonyFrame frame() { return frame; }
    TitleScreen title() { return title; }
    TutorialController tutorial() { return tutorial; }

    private void saveOptions(Options updated) {
        options = updated;
        try {
            store.save(updated);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Could not save options to " + store.file(), e);
            JOptionPane.showMessageDialog(frame != null ? frame : title,
                "Could not save options: " + e.getMessage(), "Options", JOptionPane.WARNING_MESSAGE);
        }
        DebugLogging.Installed logging = DebugLogging.current();
        if (logging != null) logging.setLevel(updated.logLevel());
    }

    // ---- crash routing ----

    /** For CrashHandler: the game's debug flag while a game shows, else false. */
    public boolean debugOn() {
        SpaceColonyFrame f = frame;
        return f != null && f.engine().debugEnabled();
    }

    /** For CrashHandler: the game's crash dialog, or a plain one over the title. */
    public void report(Throwable t) throws InterruptedException, InvocationTargetException {
        SpaceColonyFrame f = frame;
        if (f != null) { f.showCrashDialog(t); return; }
        Runnable show = () -> {
            String msg = "Something went wrong: " + t.getClass().getSimpleName()
                + (t.getMessage() == null ? "" : ": " + t.getMessage())
                + ".\nDetails were written to the log.";
            Object[] choices = { "Continue", "Quit" };
            int r = JOptionPane.showOptionDialog(title, msg, "Space Colony", JOptionPane.DEFAULT_OPTION,
                JOptionPane.ERROR_MESSAGE, null, choices, choices[0]);
            if (r == 1) System.exit(0);
        };
        if (SwingUtilities.isEventDispatchThread()) show.run();
        else SwingUtilities.invokeAndWait(show);
    }
}

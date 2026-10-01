package spacecolony.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.lang.reflect.InvocationTargetException;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import spacecolony.debug.DebugController;
import spacecolony.debug.DebugLogging;
import spacecolony.debug.ExceptionLog;
import spacecolony.engine.Engine;
import spacecolony.engine.GameLoop;
import spacecolony.options.Options;
import spacecolony.save.SaveSlots;
import spacecolony.ui.dialogs.OptionsDialog;

/** Top-level Swing window. Owns the engine + game loop and wires the 5-region layout. */
public class SpaceColonyFrame extends JFrame {
    private final Engine engine;
    private final GameLoop gameLoop;
    private final DebugController debugController;
    private final GameSession session;
    private final Hooks hooks;
    private final TopBar topBar;
    private final AutosaveTimer autosave;
    private final MainViewPanel mainView;
    private static final String TITLE = "Space Colony";

    /**
     * What the frame needs from whoever built it (Plan 6 §3.5): how to exit, how to return to the
     * main menu (null for no Main Menu item), and the options, read and saved.
     */
    public record Hooks(IntConsumer exit, Runnable mainMenu, Supplier<Options> options,
                        Consumer<Options> saveOptions) {
        /** What the existing constructors use: System::exit, no Main Menu, in-memory default options. */
        public static Hooks standalone() {
            Options[] held = { Options.DEFAULTS };
            return new Hooks(System::exit, null, () -> held[0], o -> held[0] = o);
        }
    }

    public SpaceColonyFrame(Engine engine) {
        this(engine, new ExceptionLog(20));
    }

    public SpaceColonyFrame(Engine engine, ExceptionLog exceptions) {
        this(engine, exceptions, Hooks.standalone());
    }

    public SpaceColonyFrame(Engine engine, ExceptionLog exceptions, Hooks hooks) {
        super(TITLE);
        this.engine = engine;
        this.hooks = hooks;
        this.gameLoop = new GameLoop(engine);

        // The close box goes through GameSession.quit so it confirms and autosaves like File → Quit.
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setPreferredSize(new Dimension(1280, 800));
        setLayout(new BorderLayout());
        getContentPane().setBackground(UiColors.BACKGROUND);

        this.topBar = new TopBar(engine);
        this.session = new GameSession(engine, SaveSlots.defaultDir(), new GameSession.Ui() {
            @Override public boolean confirmQuit(GameSession.Mode mode, boolean toMenu) {
                String msg = mode == GameSession.Mode.TUTORIAL ? "Leave the tutorial?"
                    : toMenu ? "Return to the main menu? Your game will be autosaved."
                    : "Quit Space Colony?";
                return JOptionPane.showConfirmDialog(SpaceColonyFrame.this, msg,
                    toMenu ? "Main Menu" : "Quit", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION;
            }
            @Override public boolean quitAnyway(String autosaveError) {
                return JOptionPane.showConfirmDialog(SpaceColonyFrame.this,
                    "Autosave failed: " + autosaveError + ". Quit anyway?", "Autosave Error",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
            }
            @Override public void toast(String text) { topBar.toast(text); }
        }, hooks.exit(), hooks.mainMenu(), () -> hooks.options().get().confirmQuit());
        session.addModeListener(m -> setTitle(m == GameSession.Mode.TUTORIAL ? TITLE + " — Tutorial" : TITLE));
        this.autosave = new AutosaveTimer(session);
        autosave.setMinutes(hooks.options().get().autosaveMinutes());
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { session.quit(); }
        });

        FileMenu menuBar = new FileMenu(this, engine, session, this::openOptions);
        setJMenuBar(menuBar);

        add(topBar, BorderLayout.NORTH);
        ColonyListPanel colonyList = new ColonyListPanel(engine);
        colonyList.setPreferredSize(new Dimension(220, 0));
        add(colonyList, BorderLayout.WEST);
        this.mainView = new MainViewPanel(engine);
        add(mainView, BorderLayout.CENTER);
        DetailPanel detail = new DetailPanel(engine);
        detail.setPreferredSize(new Dimension(330, 0));
        add(detail, BorderLayout.EAST);
        // The debug overlay mounts at index 0 of this stack, above the event strip.
        JPanel southStack = new JPanel();
        southStack.setLayout(new BoxLayout(southStack, BoxLayout.Y_AXIS));
        southStack.add(new EventStripPanel(engine));
        add(southStack, BorderLayout.SOUTH);

        this.debugController = new DebugController(this, menuBar, southStack, engine, exceptions, mainView::repaint);
        mainView.systemMap().setDebug(debugController);

        pack();
        // On a screen smaller than 1280x800 (less the taskbar or dock), shrink to fit so the right column isn't off-screen.
        java.awt.Rectangle usable = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setSize(Math.min(getWidth(), usable.width), Math.min(getHeight(), usable.height));
        setLocationRelativeTo(null);
    }

    private static JPanel placeholder(String text, Dimension preferred) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(UiColors.PANEL_BACKGROUND);
        p.setBorder(BorderFactory.createLineBorder(UiColors.PANEL_BORDER));
        p.setPreferredSize(preferred);
        JLabel l = new JLabel(text, SwingConstants.CENTER);
        l.setForeground(UiColors.FOREGROUND_DIM);
        p.add(l, BorderLayout.CENTER);
        return p;
    }

    /** File → Options…: edit, save through the hooks, and apply what takes effect at once. */
    private void openOptions() {
        Options updated = OptionsDialog.show(this, hooks.options().get());
        if (updated == null) return;
        hooks.saveOptions().accept(updated);
        autosave.setMinutes(updated.autosaveMinutes());
        DebugLogging.Installed logging = DebugLogging.current();
        if (logging != null) logging.setLevel(updated.logLevel());
    }

    /** Stops the game's timers and closes the window (Main Menu and tests). */
    @Override public void dispose() {
        autosave.stop();
        gameLoop.dispose();
        super.dispose();
    }

    public TopBar topBar() { return topBar; }
    /** The centre region (system map or body view); the tutorial card sits in its lower left. */
    public java.awt.Component centerView() { return mainView; }
    AutosaveTimer autosaveTimer() { return autosave; }
    public Engine engine() { return engine; }
    public GameLoop gameLoop() { return gameLoop; }
    public DebugController debugController() { return debugController; }

    /**
     * Crash reporter for {@link spacecolony.debug.CrashHandler} when debug mode is off: one
     * modal "Something went wrong" dialog with Continue and Quit. Blocks the caller until
     * it closes; safe from any thread.
     */
    public void showCrashDialog(Throwable t) throws InterruptedException, InvocationTargetException {
        Runnable show = () -> {
            String msg = "Something went wrong: " + t.getClass().getSimpleName()
                + (t.getMessage() == null ? "" : ": " + t.getMessage())
                + ".\nDetails were written to the log.";
            Object[] options = { "Continue", "Quit" };
            int r = JOptionPane.showOptionDialog(this, msg, "Space Colony", JOptionPane.DEFAULT_OPTION,
                JOptionPane.ERROR_MESSAGE, null, options, options[0]);
            // Quit takes the normal quit path, so the game autosaves first.
            if (r == 1) session.quit();
        };
        if (SwingUtilities.isEventDispatchThread()) show.run();
        else SwingUtilities.invokeAndWait(show);
    }
    public GameSession session() { return session; }
}

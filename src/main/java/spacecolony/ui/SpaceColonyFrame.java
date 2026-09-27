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
import spacecolony.debug.DebugController;
import spacecolony.debug.ExceptionLog;
import spacecolony.engine.Engine;
import spacecolony.engine.GameLoop;
import spacecolony.save.SaveSlots;

/** Top-level Swing window. Owns the engine + game loop and wires the 5-region layout. */
public class SpaceColonyFrame extends JFrame {
    private final Engine engine;
    private final GameLoop gameLoop;
    private final DebugController debugController;
    private final GameSession session;

    public SpaceColonyFrame(Engine engine) {
        this(engine, new ExceptionLog(20));
    }

    public SpaceColonyFrame(Engine engine, ExceptionLog exceptions) {
        super("Space Colony");
        this.engine = engine;
        this.gameLoop = new GameLoop(engine);

        // The close box goes through GameSession.quit so it confirms and autosaves like File → Quit.
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setPreferredSize(new Dimension(1280, 800));
        setLayout(new BorderLayout());
        getContentPane().setBackground(UiColors.BACKGROUND);

        TopBar topBar = new TopBar(engine);
        this.session = new GameSession(engine, SaveSlots.defaultDir(), new GameSession.Ui() {
            @Override public boolean confirmQuit() {
                return JOptionPane.showConfirmDialog(SpaceColonyFrame.this, "Quit Space Colony?",
                    "Quit", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION;
            }
            @Override public boolean quitAnyway(String autosaveError) {
                return JOptionPane.showConfirmDialog(SpaceColonyFrame.this,
                    "Autosave failed: " + autosaveError + ". Quit anyway?", "Autosave Error",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
            }
            @Override public void savedToast(String label) { topBar.toast("Saved “" + label + "”"); }
        }, System::exit);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { session.quit(); }
        });

        FileMenu menuBar = new FileMenu(this, engine, session);
        setJMenuBar(menuBar);

        add(topBar, BorderLayout.NORTH);
        ColonyListPanel colonyList = new ColonyListPanel(engine);
        colonyList.setPreferredSize(new Dimension(220, 0));
        add(colonyList, BorderLayout.WEST);
        MainViewPanel mainView = new MainViewPanel(engine);
        add(mainView, BorderLayout.CENTER);
        DetailPanel detail = new DetailPanel(engine);
        detail.setPreferredSize(new Dimension(280, 0));
        add(detail, BorderLayout.EAST);
        // The debug overlay mounts at index 0 of this stack, above the event strip.
        JPanel southStack = new JPanel();
        southStack.setLayout(new BoxLayout(southStack, BoxLayout.Y_AXIS));
        southStack.add(new EventStripPanel(engine));
        add(southStack, BorderLayout.SOUTH);

        this.debugController = new DebugController(this, menuBar, southStack, engine, exceptions, mainView::repaint);
        mainView.systemMap().setDebug(debugController);

        pack();
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

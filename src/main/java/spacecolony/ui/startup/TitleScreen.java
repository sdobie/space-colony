package spacecolony.ui.startup;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import spacecolony.options.Options;
import spacecolony.save.SlotInfo;
import spacecolony.ui.UiColors;

/**
 * The title screen (Plan 6 §3.4): Continue, New Game…, Tutorial, Load Game…, Options… and Quit
 * over an animated starfield. The window only reports choices; {@link AppController} acts.
 */
public final class TitleScreen extends JFrame {
    public enum Action { CONTINUE, NEW_GAME, TUTORIAL, LOAD, OPTIONS, QUIT }

    public interface Listener { void onTitleAction(Action a); }

    static final String BANNER = "New to Space Colony? The tutorial takes about 10 minutes.";

    private final Map<Action, JButton> buttons = new EnumMap<>(Action.class);
    private final JLabel banner = new JLabel(BANNER, SwingConstants.CENTER);
    private final JLabel footer = new JLabel(" ", SwingConstants.CENTER);
    private final String version;
    private final Timer animation;
    private final long started = System.nanoTime();
    private Action defaultAction = Action.NEW_GAME;

    public TitleScreen(String version, Listener listener) {
        super("Space Colony");
        this.version = version;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { listener.onTitleAction(Action.QUIT); }
            @Override public void windowOpened(WindowEvent e) { focusDefault(); }
        });

        Backdrop backdrop = new Backdrop();
        backdrop.setLayout(new BorderLayout());
        setContentPane(backdrop);

        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        column.add(Box.createVerticalGlue());
        column.add(centered(label("SPACE COLONY", Font.BOLD, 52, UiColors.FOREGROUND)));
        column.add(centered(label("A solar-system logistics game", Font.PLAIN, 16, UiColors.FOREGROUND_DIM)));
        column.add(Box.createVerticalStrut(28));
        banner.setForeground(UiColors.WARNING);
        banner.setFont(banner.getFont().deriveFont(Font.PLAIN, 14f));
        column.add(centered(banner));
        column.add(Box.createVerticalStrut(12));
        String[] labels = {"Continue", "New Game…", "Tutorial", "Load Game…", "Options…", "Quit"};
        for (Action a : Action.values()) {
            JButton b = new MenuButton(labels[a.ordinal()]);
            b.setName("title." + a.name().toLowerCase());
            b.addActionListener(e -> listener.onTitleAction(a));
            buttons.put(a, b);
            column.add(centered(b));
            column.add(Box.createVerticalStrut(8));
        }
        column.add(Box.createVerticalGlue());
        backdrop.add(column, BorderLayout.CENTER);
        footer.setForeground(UiColors.FOREGROUND_DIM);
        footer.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));
        backdrop.add(footer, BorderLayout.SOUTH);

        bindKeys(listener);
        animation = new Timer(33, e -> backdrop.repaint());
        addWindowListener(new WindowAdapter() {
            @Override public void windowOpened(WindowEvent e) { animation.start(); }
            @Override public void windowClosed(WindowEvent e) { animation.stop(); }
        });
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentShown(java.awt.event.ComponentEvent e) { animation.start(); }
            @Override public void componentHidden(java.awt.event.ComponentEvent e) { animation.stop(); }
        });
        setPreferredSize(new Dimension(1280, 800));
        pack();
        setLocationRelativeTo(null);
    }

    /** Refreshes Continue, the new-player banner, the default button and the footer. */
    public void update(Options options, List<SlotInfo> slots, boolean debugNext, Instant now) {
        SlotInfo target = continueTarget(slots);
        JButton cont = buttons.get(Action.CONTINUE);
        cont.setEnabled(target != null);
        cont.setText(target == null
            ? "<html><center>Continue<br><font size=-1>No saved games yet</font></center></html>"
            : "<html><center>Continue<br><font size=-1>" + continueLabel(target, now) + "</font></center></html>");
        banner.setVisible(showNewPlayerBanner(options, slots));
        defaultAction = defaultAction(options, slots);
        for (var e : buttons.entrySet()) ((MenuButton) e.getValue()).setAccent(e.getKey() == defaultAction);
        getRootPane().setDefaultButton(buttons.get(defaultAction));
        footer.setText("v" + version + (debugNext ? "  ·  Debug mode on" : ""));
        focusDefault();
    }

    // Public for the startup play-test driver.
    public JButton button(Action a) { return buttons.get(a); }
    public boolean bannerVisible() { return banner.isVisible(); }

    private void focusDefault() { buttons.get(defaultAction).requestFocusInWindow(); }

    // ---- pure rules, unit-tested ----

    /** The newest readable save (the list is newest first), or null. */
    static SlotInfo continueTarget(List<SlotInfo> slots) {
        for (SlotInfo s : slots) if (s.status() == SlotInfo.Status.OK) return s;
        return null;
    }

    static boolean showNewPlayerBanner(Options o, List<SlotInfo> slots) {
        return !o.tutorialCompleted() && slots.isEmpty();
    }

    /** What Enter does: the tutorial for a first run, else Continue when possible, else New Game. */
    static Action defaultAction(Options o, List<SlotInfo> slots) {
        if (showNewPlayerBanner(o, slots)) return Action.TUTORIAL;
        return continueTarget(slots) != null ? Action.CONTINUE : Action.NEW_GAME;
    }

    /** "colony · Y3 D121 · 2 hours ago". */
    static String continueLabel(SlotInfo s, Instant now) {
        String name = s.name().equals(spacecolony.save.SaveSlots.UNNAMED_AUTOSAVE) ? "Unnamed game" : s.name();
        return name + " · Y" + (s.tick() / 365) + " D" + (s.tick() % 365 + 1) + " · " + ago(s.modified(), now);
    }

    static String ago(Instant then, Instant now) {
        long min = Math.max(0, Duration.between(then, now).toMinutes());
        if (min < 1) return "just now";
        if (min < 60) return min + (min == 1 ? " minute ago" : " minutes ago");
        long h = min / 60;
        if (h < 24) return h + (h == 1 ? " hour ago" : " hours ago");
        long d = h / 24;
        return d + (d == 1 ? " day ago" : " days ago");
    }

    // ---- layout helpers ----

    private void bindKeys(Listener listener) {
        JComponent root = getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "quit");
        root.getActionMap().put("quit", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { listener.onTitleAction(Action.QUIT); }
        });
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "next");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "prev");
        root.getActionMap().put("next", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { moveFocus(1); }
        });
        root.getActionMap().put("prev", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { moveFocus(-1); }
        });
    }

    private void moveFocus(int dir) {
        List<JButton> order = buttons.values().stream().filter(Component::isEnabled).toList();
        int i = order.indexOf(getFocusOwner());
        int next = i < 0 ? 0 : Math.floorMod(i + dir, order.size());
        order.get(next).requestFocusInWindow();
    }

    private static JLabel label(String text, int style, int size, Color color) {
        JLabel l = new JLabel(text, SwingConstants.CENTER);
        l.setFont(new Font(Font.SANS_SERIF, style, size));
        l.setForeground(color);
        return l;
    }

    private static JComponent centered(JComponent c) {
        c.setAlignmentX(Component.CENTER_ALIGNMENT);
        return c;
    }

    /** Backdrop that paints the animated starfield. */
    private final class Backdrop extends JPanel {
        private final Starfield stars = new Starfield(7L);
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            stars.paint(g2, getWidth(), getHeight(), (System.nanoTime() - started) / 1e9);
            g2.dispose();
        }
    }

    /** A 280 px menu button in the game palette; the default action gets the accent outline. */
    private static final class MenuButton extends JButton {
        private boolean accent;
        MenuButton(String text) {
            super(text);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setForeground(UiColors.FOREGROUND);
            setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            Dimension d = new Dimension(280, 52);
            setPreferredSize(d);
            setMaximumSize(d);
            setMinimumSize(d);
        }
        void setAccent(boolean on) { accent = on; repaint(); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean hot = getModel().isRollover() || isFocusOwner();
            g2.setColor(hot ? UiColors.SELECTION : new Color(13, 20, 33, 220));
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            g2.setColor(accent ? UiColors.INFO : UiColors.PANEL_BORDER);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            g2.dispose();
            setForeground(isEnabled() ? UiColors.FOREGROUND : UiColors.FOREGROUND_DIM);
            super.paintComponent(g);
        }
    }
}

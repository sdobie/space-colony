package spacecolony.ui.tutorial;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.text.html.HTMLEditorKit;
import spacecolony.tutorial.TutorialStep;
import spacecolony.ui.UiColors;

/** The tutorial's step card (Plan 6 §5.6). */
final class CoachPanel extends JPanel {
    static final int WIDTH = 340;

    interface Listener {
        void next();
        void skip();
        void exit();
        void keepPlaying();
        void mainMenu();
    }

    private final JLabel header = new JLabel();
    private final JLabel title = new JLabel();
    private final JEditorPane body = new JEditorPane();
    private final JLabel hint = new JLabel();
    private final JPanel details = new JPanel();
    private final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
    private final JButton collapse = new JButton("▾");
    private final Listener listener;
    private boolean collapsed;

    CoachPanel(Listener listener) {
        super(new BorderLayout(0, 6));
        this.listener = listener;
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));

        header.setForeground(UiColors.FOREGROUND_DIM);
        header.setFont(header.getFont().deriveFont(Font.PLAIN, 11f));
        title.setForeground(UiColors.FOREGROUND);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        collapse.setFocusable(false);
        collapse.setBorderPainted(false);
        collapse.setContentAreaFilled(false);
        collapse.setForeground(UiColors.FOREGROUND_DIM);
        collapse.addActionListener(e -> setCollapsed(!collapsed));
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.add(header);
        titles.add(title);
        top.add(titles, BorderLayout.CENTER);
        top.add(collapse, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        body.setEditable(false);
        body.setOpaque(false);
        body.setFocusable(false);
        HTMLEditorKit kit = new HTMLEditorKit();
        body.setEditorKit(kit);
        kit.getStyleSheet().addRule("body { color: rgb(220,226,240); font-family: sans-serif; font-size: 12pt; }");
        kit.getStyleSheet().addRule("p { margin-top: 0; margin-bottom: 6px; }");
        kit.getStyleSheet().addRule("b { color: rgb(120,180,240); }");
        hint.setForeground(UiColors.WARNING);
        buttons.setOpaque(false);
        details.setOpaque(false);
        details.setLayout(new BorderLayout(0, 6));
        details.add(body, BorderLayout.CENTER);
        JPanel south = new JPanel(new BorderLayout(0, 6));
        south.setOpaque(false);
        south.add(hint, BorderLayout.NORTH);
        south.add(buttons, BorderLayout.SOUTH);
        details.add(south, BorderLayout.SOUTH);
        add(details, BorderLayout.CENTER);
    }

    /** Shows {@code step} (0-based {@code index} of {@code size}); the hint shows when no target is visible. */
    void show(TutorialStep step, int index, int size, boolean targetVisible) {
        header.setText("Step " + (index + 1) + " of " + size);
        title.setText(step.title());
        body.setText("<html><body>" + step.html() + "</body></html>");
        boolean showHint = !targetVisible && step.hint() != null;
        hint.setText(showHint ? step.hint() : "");
        hint.setVisible(showHint);
        buttons.removeAll();
        if (index == size - 1) {
            buttons.add(button("Main menu", listener::mainMenu));
            buttons.add(button("Keep playing", listener::keepPlaying));
        } else {
            buttons.add(button("Exit tutorial", listener::exit));
            // Next step on every step: it continues a reading step and skips an action step.
            buttons.add(button("Next step", step.manual() ? listener::next : listener::skip));
        }
        revalidate();
        repaint();
    }

    void setHintVisible(boolean visible, String text) {
        hint.setText(visible && text != null ? text : "");
        hint.setVisible(visible && text != null);
    }

    void setCollapsed(boolean c) {
        collapsed = c;
        details.setVisible(!c);
        collapse.setText(c ? "▸" : "▾");
        revalidate();
        repaint();
    }

    /** Height this card wants at its fixed width. */
    int preferredHeight() {
        body.setSize(WIDTH - 28, Short.MAX_VALUE);
        setSize(WIDTH, 10);
        return getPreferredSize().height;
    }

    @Override public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        return new Dimension(WIDTH, d.height);
    }

    private static JButton button(String label, Runnable r) {
        JButton b = new JButton(label);
        b.setFocusable(false);
        b.addActionListener(e -> r.run());
        return b;
    }

    java.util.List<String> buttonLabels() {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (var c : buttons.getComponents()) if (c instanceof JButton b) out.add(b.getText());
        return out;
    }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(UiColors.PANEL_BACKGROUND);
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
        g2.setColor(UiColors.INFO);
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
        g2.dispose();
    }
}

package spacecolony.ui.startup;

import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;
import javax.swing.JWindow;
import spacecolony.ui.UiColors;

/** The startup splash (Plan 6 §3.3): title, version, and a status line with progress. */
public final class SplashWindow extends JWindow {
    static final int W = 640, H = 360;

    private final Content content;

    SplashWindow(String version) {
        this.content = new Content(version);
        setContentPane(content);
        setSize(W, H);
        setLocationRelativeTo(null);
    }

    void setStatus(String text, double fraction) { content.setStatus(text, fraction); }

    /** Runs {@code r} on a click or key press. */
    void onSkip(Runnable r) {
        content.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { r.run(); }
        });
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) { r.run(); }
        });
    }

    /** The painted content, separate so tests can paint it without a window. */
    static final class Content extends JComponent {
        private final Starfield stars = new Starfield(7L);
        private final String version;
        private String status = "Starting…";
        private double fraction;

        Content(String version) {
            this.version = version;
            setPreferredSize(new Dimension(W, H));
            setOpaque(true);
        }

        void setStatus(String text, double fraction) {
            this.status = text;
            this.fraction = Math.max(0, Math.min(1, fraction));
            repaint();
        }

        String status() { return status; }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            int w = getWidth(), h = getHeight();
            stars.paint(g, w, h, 0);
            g.setColor(UiColors.FOREGROUND);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 44));
            String title = "SPACE COLONY";
            int tw = g.getFontMetrics().stringWidth(title);
            g.drawString(title, w - tw - 40, h / 2 - 10);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
            g.setColor(UiColors.FOREGROUND_DIM);
            String v = "v" + version;
            g.drawString(v, w - g.getFontMetrics().stringWidth(v) - 40, h / 2 + 14);
            g.drawString(status, w - 240, h - 46);
            int bx = w - 240, by = h - 36;
            g.setColor(UiColors.PANEL_BORDER);
            g.fillRect(bx, by, 200, 4);
            g.setColor(UiColors.INFO);
            g.fillRect(bx, by, (int) (200 * fraction), 4);
            g.setColor(UiColors.PANEL_BORDER);
            g.setStroke(new BasicStroke(1));
            g.drawRect(0, 0, w - 1, h - 1);
            g.dispose();
        }
    }
}

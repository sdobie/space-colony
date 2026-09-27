package spacecolony.ui.tutorial;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import spacecolony.ui.UiColors;

/**
 * A pulsing outline around the current step's target (Plan 6 §5.6). Sits over the whole frame
 * on the layered pane but never takes mouse events: {@link #contains} is always false.
 */
final class HighlightLayer extends JComponent {
    private Component target;
    private final Timer pulse = new Timer(50, e -> repaint());
    private final long started = System.nanoTime();

    HighlightLayer() {
        setOpaque(false);
    }

    /** Null hides the outline. */
    void setTarget(Component c) {
        target = c;
        if (c == null) pulse.stop(); else if (!pulse.isRunning()) pulse.start();
        repaint();
    }

    Component target() { return target; }

    void stop() { pulse.stop(); }

    @Override public boolean contains(int x, int y) { return false; }

    @Override protected void paintComponent(Graphics g) {
        Component c = target;
        if (c == null || !c.isShowing()) return;
        Rectangle r = SwingUtilities.convertRectangle(c.getParent(), c.getBounds(), this);
        r.grow(4, 4);
        double phase = ((System.nanoTime() - started) / 1e9) * Math.PI;     // one pulse per 2 s
        int alpha = 90 + (int) (165 * (0.5 + 0.5 * Math.sin(phase)));
        Color base = UiColors.INFO;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setStroke(new BasicStroke(2f));
        g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha));
        g2.drawRoundRect(r.x, r.y, r.width, r.height, 10, 10);
        g2.dispose();
    }
}

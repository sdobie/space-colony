package spacecolony.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JComponent;

/** A thin rounded progress bar the width of its row (Plan 10 §6.1: labs, shipyards, construction). */
public final class ProgressBar extends JComponent {
    static final Color TRACK = new Color(255, 255, 255, 28);
    private final double fraction;
    private final Color color;

    public ProgressBar(double fraction, Color color) {
        this.fraction = Math.max(0.0, Math.min(1.0, fraction));
        this.color = color;
        setOpaque(false);
        setAlignmentX(LEFT_ALIGNMENT);
    }

    public double fraction() { return fraction; }

    @Override public Dimension getPreferredSize() { return new Dimension(40, 7); }
    @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, 7); }

    @Override protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int x = 8, w = Math.max(0, getWidth() - x - 2);
        g.setColor(TRACK);
        g.fill(new RoundRectangle2D.Double(x, 2, w, 4, 4, 4));
        if (fraction > 0) {
            g.setColor(color);
            g.fill(new RoundRectangle2D.Double(x, 2, Math.max(4, w * fraction), 4, 4, 4));
        }
        g.dispose();
    }
}

package spacecolony.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;

/**
 * A small round badge with a progress ring (Plan 10 §6.1): a flask for research under way, a
 * hull for a ship being built. Drawn on the system map next to the colony's body.
 */
public final class ProgressBadge {
    private ProgressBadge() {}

    public enum Kind { RESEARCH, SHIP }

    /** Diameter in pixels. */
    public static final int SIZE = 14;

    public static Color color(Kind k) { return k == Kind.RESEARCH ? UiColors.INFO : UiColors.WARNING; }

    /** Paints a badge centred on (x, y), its ring filled to {@code fraction}. */
    public static void paint(Graphics2D g, Kind kind, double x, double y, double fraction) {
        double r = SIZE / 2.0;
        g.setColor(new Color(10, 14, 24, 220));
        g.fill(new Ellipse2D.Double(x - r, y - r, SIZE, SIZE));
        java.awt.Stroke prior = g.getStroke();
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        g.setColor(ProgressBar.TRACK);
        g.draw(new Ellipse2D.Double(x - r + 1, y - r + 1, SIZE - 2, SIZE - 2));
        Color c = color(kind);
        double f = Math.max(0.0, Math.min(1.0, fraction));
        if (f > 0) {
            g.setColor(c);
            g.draw(new Arc2D.Double(x - r + 1, y - r + 1, SIZE - 2, SIZE - 2, 90, -360 * f, Arc2D.OPEN));
        }
        g.setStroke(new BasicStroke(1f));
        g.setColor(c);
        Path2D glyph = new Path2D.Double();
        if (kind == Kind.RESEARCH) {
            // A flask: narrow neck widening to a flat base.
            glyph.moveTo(x - 1, y - 3.5);
            glyph.lineTo(x + 1, y - 3.5);
            glyph.lineTo(x + 1, y - 1);
            glyph.lineTo(x + 3, y + 3);
            glyph.lineTo(x - 3, y + 3);
            glyph.lineTo(x - 1, y - 1);
            glyph.closePath();
        } else {
            // A hull: a dart pointing up.
            glyph.moveTo(x, y - 4);
            glyph.lineTo(x + 2.5, y + 3);
            glyph.lineTo(x, y + 1.5);
            glyph.lineTo(x - 2.5, y + 3);
            glyph.closePath();
        }
        g.fill(glyph);
        g.setStroke(prior);
    }
}

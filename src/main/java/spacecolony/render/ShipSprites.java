package spacecolony.render;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import spacecolony.sim.ShipClass;

/**
 * Top-down ship drawings, one look per {@link ShipClass}, shared by the system map and the
 * left-column ship cards. Each ship is drawn in a unit frame {@link #LENGTH} long with its nose
 * along +x, then scaled and turned to the requested heading.
 */
public final class ShipSprites {
    /** Length of a ship in sprite units, nose to stern (stern at -12, nose at +12). */
    public static final double LENGTH = 24.0;

    private static final Color OUTLINE = new Color(8, 10, 18);
    private static final Color STEEL = new Color(150, 158, 172);
    private static final Color STEEL_DARK = new Color(92, 100, 116);
    private static final Color CANOPY = new Color(140, 220, 255);

    private ShipSprites() {}

    /** The class's signature colour: its cargo, tanks, habitat ring or hull. */
    public static Color accent(ShipClass cls) {
        return switch (cls) {
            case HAULER    -> new Color(232, 146, 62);
            case TANKER    -> new Color(96, 204, 168);
            case COLONIZER -> new Color(150, 196, 255);
            case EXPLORER  -> new Color(190, 140, 255);
        };
    }

    /**
     * Draws a ship centred on (x, y).
     *
     * @param heading direction of the nose in radians, 0 = screen right, PI/2 = screen down
     * @param length on-screen length of the ship in pixels
     * @param thrusting adds an engine flame behind the stern
     */
    public static void draw(Graphics2D g, ShipClass cls, double x, double y, double heading,
                            double length, boolean thrusting) {
        Graphics2D h = (Graphics2D) g.create();
        h.translate(x, y);
        h.rotate(heading);
        double s = length / LENGTH;
        h.scale(s, s);
        h.setStroke(new BasicStroke((float) Math.max(0.6, 0.9 / s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        if (thrusting) flame(h);
        switch (cls) {
            case HAULER    -> hauler(h);
            case TANKER    -> tanker(h);
            case COLONIZER -> colonizer(h);
            case EXPLORER  -> explorer(h);
        }
        h.dispose();
    }

    private static void flame(Graphics2D h) {
        Path2D f = new Path2D.Double();
        f.moveTo(-11, -2.6);
        f.quadTo(-17, -1.8, -21, 0);
        f.quadTo(-17, 1.8, -11, 2.6);
        f.closePath();
        h.setPaint(new GradientPaint(-11, 0, new Color(255, 240, 170, 240), -21, 0, new Color(255, 110, 40, 0)));
        h.fill(f);
        h.setPaint(new GradientPaint(-11, 0, new Color(255, 255, 255, 230), -15, 0, new Color(255, 220, 120, 0)));
        h.fill(new Ellipse2D.Double(-15, -1.2, 5, 2.4));
    }

    /** Boxy freighter: a steel spine carrying rows of cargo containers, cab at the nose. */
    private static void hauler(Graphics2D h) {
        Color cargo = accent(ShipClass.HAULER);
        part(h, new Rectangle2D.Double(-13, -3.5, 3, 7), STEEL_DARK);          // engine block
        part(h, new RoundRectangle2D.Double(-11, -1.8, 20, 3.6, 1.5, 1.5), STEEL);
        for (int i = 0; i < 3; i++) {
            Color c = i % 2 == 0 ? cargo : cargo.darker();
            part(h, new Rectangle2D.Double(-9 + i * 5.6, -6.5, 4.8, 4.6), c);
            part(h, new Rectangle2D.Double(-9 + i * 5.6, 1.9, 4.8, 4.6), c);
        }
        Path2D cab = new Path2D.Double();
        cab.moveTo(8, -3.4); cab.lineTo(11, -3.4); cab.lineTo(13, 0); cab.lineTo(11, 3.4); cab.lineTo(8, 3.4);
        cab.closePath();
        part(h, cab, STEEL);
        h.setColor(CANOPY);
        h.fill(new Rectangle2D.Double(10.2, -1.6, 1.6, 3.2));
    }

    /** Two long fuel tanks side by side on a short frame. */
    private static void tanker(Graphics2D h) {
        Color tank = accent(ShipClass.TANKER);
        part(h, new Rectangle2D.Double(-13, -3, 3, 6), STEEL_DARK);
        for (double cy : new double[] { -3.6, 3.6 }) {
            part(h, new RoundRectangle2D.Double(-11, cy - 3, 18, 6, 6, 6), tank);
            h.setColor(new Color(255, 255, 255, 110));
            h.fill(new RoundRectangle2D.Double(-9, cy - 2, 14, 1.2, 1.2, 1.2));
            h.setColor(tank.darker().darker());
            h.draw(new Line2D.Double(-4, cy - 3, -4, cy + 3));
            h.draw(new Line2D.Double(1, cy - 3, 1, cy + 3));
        }
        Path2D cab = new Path2D.Double();
        cab.moveTo(6, -2.6); cab.lineTo(10, -2.6); cab.lineTo(13, 0); cab.lineTo(10, 2.6); cab.lineTo(6, 2.6);
        cab.closePath();
        part(h, cab, STEEL);
        h.setColor(CANOPY);
        h.fill(new Ellipse2D.Double(9, -1.2, 2.4, 2.4));
    }

    /** A habitat ring on spokes around a long spine, command module at the nose. */
    private static void colonizer(Graphics2D h) {
        Color ring = accent(ShipClass.COLONIZER);
        Stroke base = h.getStroke();
        part(h, new Rectangle2D.Double(-13, -2.8, 3, 5.6), STEEL_DARK);
        part(h, new Rectangle2D.Double(-11, -1.4, 20, 2.8), STEEL);
        // Solar wings behind the ring.
        part(h, new Rectangle2D.Double(-10, -11, 3, 7), new Color(60, 90, 160));
        part(h, new Rectangle2D.Double(-10, 4, 3, 7), new Color(60, 90, 160));
        h.setColor(STEEL);
        h.draw(new Line2D.Double(-1, -7.5, -1, 7.5));
        h.draw(new Line2D.Double(-6.3, -5.3, 4.3, 5.3));
        h.draw(new Line2D.Double(-6.3, 5.3, 4.3, -5.3));
        Shape torus = new Ellipse2D.Double(-8.5, -8.5, 15, 15);
        h.setStroke(new BasicStroke(4.2f));
        h.setColor(OUTLINE);
        h.draw(torus);
        h.setStroke(new BasicStroke(2.8f));
        h.setColor(ring);
        h.draw(torus);
        h.setStroke(base);
        part(h, new Ellipse2D.Double(-3, -2, 4, 4), ring.brighter());
        Path2D nose = new Path2D.Double();
        nose.moveTo(8, -2.6); nose.lineTo(11, -2); nose.lineTo(13, 0); nose.lineTo(11, 2); nose.lineTo(8, 2.6);
        nose.closePath();
        part(h, nose, Color.WHITE);
        h.setColor(CANOPY);
        h.fill(new Ellipse2D.Double(10, -1, 2, 2));
    }

    /** Slim swept dart with a sensor dish and a forward antenna. */
    private static void explorer(Graphics2D h) {
        Color hull = accent(ShipClass.EXPLORER);
        h.setColor(STEEL);
        h.draw(new Line2D.Double(10, 0, 15, 0));
        Path2D dart = new Path2D.Double();
        dart.moveTo(12, 0);
        dart.lineTo(-2, -3);
        dart.lineTo(-11, -9);
        dart.lineTo(-8, -2);
        dart.lineTo(-11, 0);
        dart.lineTo(-8, 2);
        dart.lineTo(-11, 9);
        dart.lineTo(-2, 3);
        dart.closePath();
        part(h, dart, hull);
        h.setColor(hull.darker());
        h.draw(new Line2D.Double(-8, 0, 8, 0));
        part(h, new Ellipse2D.Double(-6, -2.6, 5.2, 5.2), STEEL);
        h.setColor(STEEL_DARK);
        h.fill(new Ellipse2D.Double(-4.6, -1.2, 2.4, 2.4));
        h.setColor(CANOPY);
        h.fill(new Ellipse2D.Double(5, -1.1, 3.4, 2.2));
    }

    /** Fills a shape and outlines it dark so the ship reads against space and planets. */
    private static void part(Graphics2D h, Shape s, Color fill) {
        h.setColor(fill);
        h.fill(s);
        h.setColor(OUTLINE);
        h.draw(s);
    }
}

package spacecolony.render;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.EnumMap;
import java.util.Map;
import spacecolony.sim.Resource;

/**
 * Small resource drawings in the ship-sprite style (dark outline, gradient fill, one highlight),
 * used wherever a resource is listed. Each glyph is drawn in a {@link #FRAME} x {@link #FRAME}
 * unit square and scaled to the requested size.
 *
 * <p>Adding a resource: a new {@link Resource} constant needs nothing here to show up. Until a
 * glyph is registered in {@link #GLYPHS} it draws as a lettered badge in a colour taken from its
 * name. A new fuel can register {@code tank(colour)}, a canister banded in the fuel's colour, and
 * give the same colour to {@link #ACCENTS}.
 */
public final class ResourceGlyphs {
    /** Side of the unit square every glyph is drawn in. */
    public static final double FRAME = 16.0;

    /** One drawing, in the 16 x 16 unit frame; the stroke is already set for outlines. */
    @FunctionalInterface
    public interface Glyph {
        void draw(Graphics2D g);
    }

    static final Color OUTLINE = new Color(8, 10, 18);

    private static final Map<Resource, Glyph> GLYPHS = new EnumMap<>(Resource.class);
    private static final Map<Resource, Color> ACCENTS = new EnumMap<>(Resource.class);

    static {
        add(Resource.ORE,        ResourceGlyphs::ore,        new Color(204, 128, 70));
        add(Resource.METAL,      ResourceGlyphs::metal,      new Color(176, 192, 214));
        add(Resource.SILICATE,   ResourceGlyphs::silicate,   new Color(190, 170, 240));
        add(Resource.ICE,        ResourceGlyphs::ice,        new Color(176, 226, 248));
        add(Resource.WATER,      ResourceGlyphs::water,      new Color(84, 156, 240));
        add(Resource.FOOD,       ResourceGlyphs::food,       new Color(236, 196, 92));
        add(Resource.BIOMASS,    ResourceGlyphs::biomass,    new Color(112, 196, 100));
        add(Resource.HYDROGEN,   ResourceGlyphs::fuel,       new Color(255, 140, 52));
        add(Resource.SYNFUEL,    ResourceGlyphs::synfuel,    new Color(150, 210, 70));
        add(Resource.COMPONENTS, ResourceGlyphs::components, new Color(84, 196, 150));
        add(Resource.ENERGY,     ResourceGlyphs::energy,     new Color(255, 214, 80));
    }

    private static void add(Resource r, Glyph glyph, Color accent) {
        GLYPHS.put(r, glyph);
        ACCENTS.put(r, accent);
    }

    private ResourceGlyphs() {}

    /** True when the resource has its own drawing rather than the lettered fallback badge. */
    public static boolean hasGlyph(Resource r) { return GLYPHS.containsKey(r); }

    /** The resource's signature colour; a stable colour from its name when none is registered. */
    public static Color accent(Resource r) {
        Color c = ACCENTS.get(r);
        return c != null ? c : nameColour(r.name());
    }

    /** Draws a resource's glyph in a {@code size} x {@code size} box with its top-left at (x, y). */
    public static void draw(Graphics2D g, Resource r, double x, double y, double size) {
        Glyph glyph = GLYPHS.get(r);
        inFrame(g, x, y, size, glyph != null ? glyph : badge(accent(r), r.name().substring(0, 1)));
    }

    /** A person's head and shoulders, for population figures beside the resource glyphs. */
    public static void drawPopulation(Graphics2D g, double x, double y, double size) {
        inFrame(g, x, y, size, h -> {
            Path2D body = new Path2D.Double();
            body.moveTo(2.5, 15);
            body.curveTo(2.5, 10.5, 5, 9, 8, 9);
            body.curveTo(11, 9, 13.5, 10.5, 13.5, 15);
            body.closePath();
            fillOutlined(h, body, grad(0, 9, new Color(150, 196, 255), 0, 15, new Color(70, 110, 180)));
            fillOutlined(h, new Ellipse2D.Double(5, 1.5, 6, 6),
                grad(0, 1.5, new Color(220, 236, 255), 0, 7.5, new Color(140, 176, 230)));
        });
    }

    /** Runs a glyph in the unit frame scaled to a box, with smoothing and a size-aware outline. */
    static void inFrame(Graphics2D g, double x, double y, double size, Glyph glyph) {
        Graphics2D h = (Graphics2D) g.create();
        h.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        h.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        h.translate(x, y);
        double s = size / FRAME;
        h.scale(s, s);
        // About 0.8 px whatever the size, so small icons keep an edge and big ones stay crisp.
        h.setStroke(new BasicStroke((float) Math.max(0.5, 0.8 / s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        glyph.draw(h);
        h.dispose();
    }

    // ---- reusable pieces -------------------------------------------------------------------

    /** A rounded fuel canister with a band in {@code band}: the template for new fuel types. */
    public static Glyph tank(Color band) {
        return h -> {
            Shape body = new RoundRectangle2D.Double(3.5, 3.5, 9, 11.5, 4, 4);
            fillOutlined(h, new RoundRectangle2D.Double(6, 1, 4, 3.2, 1.2, 1.2), new Color(120, 128, 142));
            fillOutlined(h, body, grad(3.5, 0, new Color(214, 220, 230), 12.5, 0, new Color(110, 118, 134)));
            Graphics2D c = (Graphics2D) h.create();
            c.clip(body);
            c.setPaint(grad(3.5, 0, band.brighter(), 12.5, 0, band.darker()));
            c.fill(new Rectangle2D.Double(3.5, 7, 9, 4.5));
            c.dispose();
            h.setColor(new Color(255, 255, 255, 150));
            h.fill(new RoundRectangle2D.Double(4.8, 4.5, 1.4, 9.5, 1.4, 1.4));
            h.setColor(OUTLINE);
            h.draw(body);
        };
    }

    /** A hexagonal badge with a letter: the fallback for a resource without a drawing. */
    static Glyph badge(Color colour, String letter) {
        return h -> {
            Path2D hex = new Path2D.Double();
            for (int i = 0; i < 6; i++) {
                double a = Math.PI / 6 + i * Math.PI / 3;
                double px = 8 + 7 * Math.cos(a), py = 8 + 7 * Math.sin(a);
                if (i == 0) hex.moveTo(px, py); else hex.lineTo(px, py);
            }
            hex.closePath();
            fillOutlined(h, hex, grad(0, 1, colour.brighter(), 0, 15, colour.darker()));
            h.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 9));
            FontMetrics fm = h.getFontMetrics();
            h.setColor(OUTLINE);
            h.drawString(letter, (float) (8 - fm.stringWidth(letter) / 2.0), 11.3f);
        };
    }

    /** Stable, fairly bright colour for a name, so an unregistered resource keeps its look. */
    static Color nameColour(String name) {
        float hue = (name.hashCode() & 0xFFFF) / 65536f;
        return Color.getHSBColor(hue, 0.55f, 0.92f);
    }

    static GradientPaint grad(double x1, double y1, Color c1, double x2, double y2, Color c2) {
        return new GradientPaint((float) x1, (float) y1, c1, (float) x2, (float) y2, c2);
    }

    static void fillOutlined(Graphics2D h, Shape s, java.awt.Paint paint) {
        h.setPaint(paint);
        h.fill(s);
        h.setColor(OUTLINE);
        h.draw(s);
    }

    static Path2D poly(double... xy) {
        Path2D p = new Path2D.Double();
        p.moveTo(xy[0], xy[1]);
        for (int i = 2; i < xy.length; i += 2) p.lineTo(xy[i], xy[i + 1]);
        p.closePath();
        return p;
    }

    // ---- glyphs ------------------------------------------------------------------------------

    /** A rough grey-brown rock with copper flecks. */
    private static void ore(Graphics2D h) {
        Path2D rock = poly(1.8, 11, 3.4, 5.6, 7, 2.6, 11.6, 3.6, 14.4, 7.8, 13.4, 12.6, 8.4, 14.4, 3.4, 13.8);
        fillOutlined(h, rock, grad(3, 3, new Color(140, 120, 104), 13, 14, new Color(66, 56, 50)));
        h.setColor(new Color(176, 156, 138));
        h.fill(poly(3.6, 6, 7, 3.2, 9.2, 6, 5.4, 8.4));
        Color fleck = accent(Resource.ORE);
        h.setColor(fleck);
        h.fill(new Ellipse2D.Double(8.4, 7.6, 2.8, 2.4));
        h.fill(new Ellipse2D.Double(5, 10.2, 2, 1.8));
        h.fill(new Ellipse2D.Double(10.6, 10.8, 1.8, 1.6));
        h.setColor(new Color(255, 214, 160));
        h.fill(new Ellipse2D.Double(9, 8, 1, 0.9));
    }

    /** A steel ingot seen from above the front. */
    private static void metal(Graphics2D h) {
        Path2D front = poly(3, 7.5, 13, 7.5, 15, 12.8, 1, 12.8);
        Path2D top = poly(4.6, 4, 11.4, 4, 13, 7.5, 3, 7.5);
        fillOutlined(h, front, grad(0, 7.5, new Color(150, 160, 178), 0, 12.8, new Color(86, 94, 110)));
        fillOutlined(h, top, grad(0, 4, new Color(236, 242, 250), 0, 7.5, accent(Resource.METAL)));
        h.setColor(new Color(255, 255, 255, 200));
        h.draw(new Line2D.Double(5.4, 5, 10.4, 5));
    }

    /** A cluster of pale violet quartz shards. */
    private static void silicate(Graphics2D h) {
        Color c = accent(Resource.SILICATE);
        Color dark = new Color(104, 84, 168);
        fillOutlined(h, poly(1.8, 6.4, 4.6, 8.2, 6, 14.6, 2.8, 14.6, 1.6, 9.4),
            grad(1.6, 0, c, 6, 0, dark));
        fillOutlined(h, poly(14.2, 7, 14.6, 10.4, 13.4, 14.6, 10, 14.6, 10.8, 9.6),
            grad(10, 0, c, 14.6, 0, dark));
        Path2D mid = poly(8, 1.2, 10.8, 5, 10.2, 14.6, 5.8, 14.6, 5.2, 5);
        fillOutlined(h, mid, grad(5.2, 0, new Color(236, 228, 255), 10.8, 0, dark));
        h.setColor(new Color(255, 255, 255, 170));
        h.fill(poly(8, 2.4, 8, 13.6, 6.6, 13.6, 6.2, 5.4));
    }

    /** A faceted block of ice. */
    private static void ice(Graphics2D h) {
        Path2D top = poly(8, 1.8, 14.4, 5, 8, 8.2, 1.6, 5);
        Path2D left = poly(1.6, 5, 8, 8.2, 8, 14.8, 1.6, 11.6);
        Path2D right = poly(8, 8.2, 14.4, 5, 14.4, 11.6, 8, 14.8);
        fillOutlined(h, left, new Color(150, 206, 236));
        fillOutlined(h, right, new Color(96, 168, 214));
        fillOutlined(h, top, grad(0, 1.8, Color.WHITE, 0, 8.2, accent(Resource.ICE)));
        h.setColor(new Color(255, 255, 255, 190));
        h.draw(new Line2D.Double(3, 6.6, 3, 10.4));
        h.draw(new Line2D.Double(10.6, 11.6, 12.6, 10.6));
    }

    /** A water drop. */
    private static void water(Graphics2D h) {
        Path2D drop = new Path2D.Double();
        drop.moveTo(8, 1.2);
        drop.curveTo(9.8, 4.6, 13.6, 7.6, 13.6, 10.4);
        drop.curveTo(13.6, 13.4, 11.2, 15, 8, 15);
        drop.curveTo(4.8, 15, 2.4, 13.4, 2.4, 10.4);
        drop.curveTo(2.4, 7.6, 6.2, 4.6, 8, 1.2);
        drop.closePath();
        fillOutlined(h, drop, grad(0, 2, new Color(150, 206, 255), 0, 15, new Color(34, 92, 196)));
        h.setColor(new Color(255, 255, 255, 200));
        h.fill(new Ellipse2D.Double(4.6, 8.4, 1.8, 3.6));
    }

    /** An ear of grain. */
    private static void food(Graphics2D h) {
        Color c = accent(Resource.FOOD);
        Color dark = new Color(150, 104, 30);
        h.setColor(OUTLINE);
        h.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        h.draw(new Line2D.Double(8, 15, 8, 5));
        h.setColor(new Color(170, 150, 70));
        h.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        h.draw(new Line2D.Double(8, 15, 8, 5));
        h.setStroke(new BasicStroke(0.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 3; i++) {
            double y = 4.6 + i * 2.9;
            grain(h, 5.6, y + 1.2, -0.6, c, dark);
            grain(h, 10.4, y + 1.2, 0.6, c, dark);
        }
        grain(h, 8, 3.2, 0, c, dark);
    }

    private static void grain(Graphics2D h, double cx, double cy, double turn, Color c, Color dark) {
        Shape kernel = AffineTransform.getRotateInstance(turn, cx, cy)
            .createTransformedShape(new Ellipse2D.Double(cx - 1.7, cy - 2.4, 3.4, 4.8));
        fillOutlined(h, kernel, grad(cx - 1.7, 0, c.brighter(), cx + 1.7, 0, dark));
    }

    /** A green leaf with its midrib. */
    private static void biomass(Graphics2D h) {
        Path2D leaf = new Path2D.Double();
        leaf.moveTo(2.4, 13.6);
        leaf.curveTo(2, 6.4, 7.6, 2.2, 14.2, 1.8);
        leaf.curveTo(14, 8.6, 9.8, 13.8, 2.4, 13.6);
        leaf.closePath();
        fillOutlined(h, leaf, grad(3, 3, new Color(176, 236, 128), 13, 13, new Color(46, 132, 62)));
        h.setColor(new Color(220, 250, 190, 210));
        h.draw(new Line2D.Double(3.2, 12.8, 11.6, 4.6));
        h.draw(new Line2D.Double(6.6, 9.4, 6.4, 6.4));
        h.draw(new Line2D.Double(8.8, 7.2, 11.4, 7.6));
        h.setColor(OUTLINE);
        h.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        h.draw(new Line2D.Double(2.4, 13.6, 1.4, 15));
    }

    /** A flame with a hot yellow core. */
    private static void fuel(Graphics2D h) {
        Path2D outer = new Path2D.Double();
        outer.moveTo(8.4, 1);
        outer.curveTo(10.6, 4, 13.8, 6.8, 13.8, 10.4);
        outer.curveTo(13.8, 13.4, 11.2, 15.2, 8, 15.2);
        outer.curveTo(4.8, 15.2, 2.2, 13.4, 2.2, 10.4);
        outer.curveTo(2.2, 8, 3.6, 6.4, 5, 5);
        outer.curveTo(5.2, 6.8, 6, 7.8, 7, 8.2);
        outer.curveTo(6.2, 5.6, 6.8, 3, 8.4, 1);
        outer.closePath();
        fillOutlined(h, outer, grad(0, 1, new Color(255, 196, 80), 0, 15, new Color(222, 62, 34)));
        Path2D inner = new Path2D.Double();
        inner.moveTo(8, 7.4);
        inner.curveTo(9.6, 9.2, 11, 10.6, 11, 12.2);
        inner.curveTo(11, 13.8, 9.6, 14.6, 8, 14.6);
        inner.curveTo(6.4, 14.6, 5, 13.8, 5, 12.2);
        inner.curveTo(5, 10.6, 6.8, 9.6, 8, 7.4);
        inner.closePath();
        h.setPaint(grad(0, 7.4, new Color(255, 240, 160), 0, 14.6, new Color(255, 255, 230)));
        h.fill(inner);
    }

    /** A green jerrycan with a small flame on its side: fuel brewed from biomass. */
    private static void synfuel(Graphics2D h) {
        fillOutlined(h, new RoundRectangle2D.Double(8.6, 1, 3.6, 2.6, 1, 1), new Color(120, 128, 142));
        Path2D can = new Path2D.Double();
        can.moveTo(3, 4.6);
        can.lineTo(10.4, 3.2);
        can.lineTo(13, 4.6);
        can.lineTo(13, 15);
        can.lineTo(3, 15);
        can.closePath();
        fillOutlined(h, can, grad(3, 0, new Color(176, 226, 96), 13, 0, new Color(70, 128, 40)));
        h.setColor(new Color(40, 78, 24, 170));
        h.draw(new Line2D.Double(4.4, 6.4, 11.6, 13.4));
        h.draw(new Line2D.Double(11.6, 6.4, 4.4, 13.4));
        Path2D flame = new Path2D.Double();
        flame.moveTo(8, 7.4);
        flame.curveTo(9.6, 9, 10.2, 10.2, 10.2, 11.4);
        flame.curveTo(10.2, 12.8, 9.2, 13.6, 8, 13.6);
        flame.curveTo(6.8, 13.6, 5.8, 12.8, 5.8, 11.4);
        flame.curveTo(5.8, 10.2, 6.8, 9, 8, 7.4);
        flame.closePath();
        fillOutlined(h, flame, grad(0, 7.4, new Color(255, 230, 120), 0, 13.6, new Color(240, 110, 40)));
    }

    /** A microchip: dark board, silver pins, gold die. */
    private static void components(Graphics2D h) {
        h.setColor(new Color(196, 204, 216));
        for (int i = 0; i < 3; i++) {
            double p = 5.2 + i * 2.4;
            h.fill(new Rectangle2D.Double(p, 1.2, 1.2, 2.6));
            h.fill(new Rectangle2D.Double(p, 12.2, 1.2, 2.6));
            h.fill(new Rectangle2D.Double(1.2, p, 2.6, 1.2));
            h.fill(new Rectangle2D.Double(12.2, p, 2.6, 1.2));
        }
        Shape board = new RoundRectangle2D.Double(3.4, 3.4, 9.2, 9.2, 2, 2);
        fillOutlined(h, board, grad(3.4, 3.4, new Color(60, 140, 108), 12.6, 12.6, new Color(22, 66, 52)));
        Shape die = new Rectangle2D.Double(5.8, 5.8, 4.4, 4.4);
        fillOutlined(h, die, grad(5.8, 5.8, new Color(255, 230, 150), 10.2, 10.2, new Color(184, 136, 50)));
        h.setColor(accent(Resource.COMPONENTS));
        h.fill(new Ellipse2D.Double(4.2, 4.2, 1.2, 1.2));
    }

    /** A lightning bolt. */
    private static void energy(Graphics2D h) {
        Path2D bolt = poly(10.4, 0.8, 2.8, 9.2, 7.2, 9.2, 5.4, 15.2, 13.2, 6.4, 8.8, 6.4, 11.2, 0.8);
        fillOutlined(h, bolt, grad(0, 1, new Color(255, 248, 170), 0, 15, new Color(236, 170, 30)));
        h.setColor(new Color(255, 255, 255, 200));
        h.draw(new Line2D.Double(9.4, 2.2, 5, 8.2));
    }
}

package spacecolony.render;

import static spacecolony.render.ResourceGlyphs.OUTLINE;
import static spacecolony.render.ResourceGlyphs.fillOutlined;
import static spacecolony.render.ResourceGlyphs.grad;
import static spacecolony.render.ResourceGlyphs.poly;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.EnumMap;
import java.util.Map;
import spacecolony.render.ResourceGlyphs.Glyph;
import spacecolony.sim.BuildingType;
import spacecolony.sim.ShipClass;

/**
 * Building drawings on a dark rounded tile, in the same 16-unit frame and style as
 * {@link ResourceGlyphs}. A {@link BuildingType} without a registered drawing gets its initial
 * on the tile, so a new type shows up before it has art.
 */
public final class BuildingGlyphs {
    private static final Color TILE_TOP = new Color(34, 50, 76);
    private static final Color TILE_BOTTOM = new Color(14, 21, 35);
    private static final Color TILE_EDGE = new Color(64, 84, 112);
    private static final Color GROUND = new Color(88, 96, 112);
    private static final Color STEEL = new Color(150, 158, 172);
    private static final Color STEEL_DARK = new Color(92, 100, 116);
    private static final Color LIT = new Color(255, 222, 130);

    private static final Map<BuildingType, Glyph> GLYPHS = new EnumMap<>(BuildingType.class);

    static {
        GLYPHS.put(BuildingType.HABITAT, BuildingGlyphs::habitat);
        GLYPHS.put(BuildingType.FARM, BuildingGlyphs::farm);
        GLYPHS.put(BuildingType.MINE, BuildingGlyphs::mine);
        GLYPHS.put(BuildingType.REFINERY, BuildingGlyphs::refinery);
        GLYPHS.put(BuildingType.FURNACE, BuildingGlyphs::furnace);
        GLYPHS.put(BuildingType.FACTORY, BuildingGlyphs::factory);
        GLYPHS.put(BuildingType.POWER_PLANT, BuildingGlyphs::powerPlant);
        GLYPHS.put(BuildingType.SHIPYARD, BuildingGlyphs::shipyard);
        GLYPHS.put(BuildingType.RESEARCH_LAB, BuildingGlyphs::researchLab);
        GLYPHS.put(BuildingType.FUEL_PLANT, BuildingGlyphs::fuelPlant);
    }

    private BuildingGlyphs() {}

    public static boolean hasGlyph(BuildingType t) { return GLYPHS.containsKey(t); }

    /** Draws a building's tile in a {@code size} x {@code size} box with its top-left at (x, y). */
    public static void draw(Graphics2D g, BuildingType t, double x, double y, double size) {
        Glyph glyph = GLYPHS.get(t);
        ResourceGlyphs.inFrame(g, x, y, size, h -> {
            tile(h);
            if (glyph != null) glyph.draw(h);
            else initial(h, t.name().substring(0, 1));
        });
        // The shipyard's hull is a real ship sprite, drawn in pixels so its outline stays thin.
        if (t == BuildingType.SHIPYARD && glyph != null) {
            double s = size / ResourceGlyphs.FRAME;
            ShipSprites.draw(g, ShipClass.HAULER, x + 8 * s, y + 8.6 * s, -Math.PI / 2, 8.4 * s, false);
        }
    }

    private static void tile(Graphics2D h) {
        Shape tile = new RoundRectangle2D.Double(0.4, 0.4, 15.2, 15.2, 4.5, 4.5);
        h.setPaint(grad(0, 0, TILE_TOP, 0, 16, TILE_BOTTOM));
        h.fill(tile);
        h.setColor(TILE_EDGE);
        h.draw(tile);
    }

    private static void initial(Graphics2D h, String letter) {
        h.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        FontMetrics fm = h.getFontMetrics();
        h.setColor(STEEL);
        h.drawString(letter, (float) (8 - fm.stringWidth(letter) / 2.0), 11.6f);
    }

    private static void ground(Graphics2D h, Color c) {
        fillOutlined(h, new Rectangle2D.Double(1.6, 12.6, 12.8, 1.8), c);
    }

    /** A pressurised dome with lit windows and an antenna. */
    private static void habitat(Graphics2D h) {
        ground(h, GROUND);
        h.setColor(STEEL);
        h.draw(new Line2D.Double(8, 7.2, 8, 3.2));
        h.setColor(new Color(255, 120, 100));
        h.fill(new Ellipse2D.Double(7.3, 2.4, 1.4, 1.4));
        Shape dome = new Arc2D.Double(2.2, 6.8, 11.6, 11.6, 0, 180, Arc2D.CHORD);
        fillOutlined(h, dome, grad(0, 6.8, new Color(206, 220, 240), 0, 12.6, new Color(104, 124, 156)));
        h.setColor(LIT);
        for (int i = 0; i < 3; i++) h.fill(new RoundRectangle2D.Double(4.4 + i * 2.6, 9.8, 1.6, 1.6, 0.6, 0.6));
        h.setColor(new Color(255, 255, 255, 170));
        h.draw(new Arc2D.Double(3.6, 8.2, 8.8, 8.8, 110, 50, Arc2D.OPEN));
    }

    /** A glass greenhouse tunnel with rows of crops. */
    private static void farm(Graphics2D h) {
        ground(h, new Color(110, 88, 64));
        Path2D tunnel = new Path2D.Double();
        tunnel.moveTo(1.8, 12.6);
        tunnel.lineTo(1.8, 8.4);
        tunnel.curveTo(1.8, 4.4, 14.2, 4.4, 14.2, 8.4);
        tunnel.lineTo(14.2, 12.6);
        tunnel.closePath();
        h.setPaint(grad(0, 5, new Color(170, 230, 240, 120), 0, 12.6, new Color(80, 150, 170, 90)));
        h.fill(tunnel);
        Color leaf = new Color(112, 196, 100);
        for (int i = 0; i < 4; i++) {
            double cx = 3.6 + i * 2.9;
            fillOutlined(h, new Ellipse2D.Double(cx - 1.2, 9.2, 2.4, 3.2), leaf);
        }
        h.setColor(new Color(220, 245, 255, 200));
        for (int i = 1; i < 4; i++) h.draw(new Line2D.Double(1.8 + i * 3.1, 5.7, 1.8 + i * 3.1, 12.6));
        h.setColor(OUTLINE);
        h.draw(tunnel);
        h.setColor(new Color(255, 255, 255, 190));
        h.draw(new Arc2D.Double(3, 5.4, 6, 4, 100, 60, Arc2D.OPEN));
    }

    /** A headframe over a shaft, with an ore pile. */
    private static void mine(Graphics2D h) {
        ground(h, new Color(110, 88, 64));
        h.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        h.setColor(OUTLINE);
        Path2D frame = new Path2D.Double();
        frame.moveTo(3, 12.6); frame.lineTo(7, 3.6); frame.lineTo(11, 12.6);
        frame.moveTo(4.6, 9); frame.lineTo(9.4, 9);
        h.draw(frame);
        h.setStroke(new BasicStroke(0.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        h.setColor(STEEL);
        h.draw(frame);
        fillOutlined(h, new Ellipse2D.Double(5.4, 2.4, 3.2, 3.2), STEEL_DARK);
        h.setColor(STEEL);
        h.fill(new Ellipse2D.Double(6.5, 3.5, 1, 1));
        h.setColor(new Color(220, 226, 240));
        h.draw(new Line2D.Double(7, 5.6, 7, 11.2));
        Path2D pile = poly(9.8, 12.6, 11.4, 9.6, 13, 9, 14.4, 11, 14.4, 12.6);
        fillOutlined(h, pile, grad(10, 9, new Color(140, 120, 104), 14, 12.6, new Color(70, 58, 52)));
        h.setColor(ResourceGlyphs.accent(spacecolony.sim.Resource.ORE));
        h.fill(new Ellipse2D.Double(11.6, 10.4, 1.2, 1.1));
    }

    /** A tall tank and a squat one joined by a pipe. */
    private static void refinery(Graphics2D h) {
        ground(h, GROUND);
        h.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        h.setColor(OUTLINE);
        Path2D pipe = new Path2D.Double();
        pipe.moveTo(6.5, 5.6); pipe.lineTo(11.6, 5.6); pipe.lineTo(11.6, 7.6);
        h.draw(pipe);
        h.setStroke(new BasicStroke(0.8f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        h.setColor(new Color(200, 160, 90));
        h.draw(pipe);
        h.setStroke(new BasicStroke(0.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Shape tall = new RoundRectangle2D.Double(2.4, 2.8, 4.8, 9.8, 4.2, 3);
        fillOutlined(h, tall, grad(2.4, 0, new Color(226, 232, 240), 7.2, 0, new Color(120, 130, 146)));
        Shape squat = new RoundRectangle2D.Double(8.6, 7.4, 6, 5.2, 3, 3);
        fillOutlined(h, squat, grad(8.6, 0, new Color(226, 232, 240), 14.6, 0, new Color(120, 130, 146)));
        h.setColor(ResourceGlyphs.accent(spacecolony.sim.Resource.WATER));
        h.fill(new Rectangle2D.Double(2.9, 8, 3.9, 1.4));
        h.setColor(ResourceGlyphs.accent(spacecolony.sim.Resource.METAL));
        h.fill(new Rectangle2D.Double(9, 9.6, 5.2, 1.2));
    }

    /** A brick kiln with a glowing mouth and a smoking chimney. */
    private static void furnace(Graphics2D h) {
        ground(h, GROUND);
        h.setColor(new Color(170, 176, 190, 150));
        h.fill(new Ellipse2D.Double(11.4, 0.8, 2.6, 2.2));
        h.fill(new Ellipse2D.Double(12.8, 0.2, 2, 1.8));
        fillOutlined(h, new Rectangle2D.Double(10.4, 2.6, 2.4, 5), new Color(120, 66, 52));
        Path2D kiln = poly(2.6, 12.6, 3.6, 6.4, 12.6, 6.4, 13.6, 12.6);
        fillOutlined(h, kiln, grad(0, 6.4, new Color(186, 98, 70), 0, 12.6, new Color(110, 54, 42)));
        h.setColor(new Color(70, 30, 24, 160));
        h.draw(new Line2D.Double(3.3, 8.4, 12.9, 8.4));
        h.draw(new Line2D.Double(3, 10.4, 13.2, 10.4));
        h.draw(new Line2D.Double(6, 6.4, 6, 8.4));
        h.draw(new Line2D.Double(10, 6.4, 10, 8.4));
        Shape mouth = new Arc2D.Double(5.4, 8.8, 5.2, 7.6, 0, 180, Arc2D.CHORD);
        fillOutlined(h, mouth, grad(0, 8.8, new Color(255, 236, 140), 0, 12.6, new Color(240, 96, 30)));
    }

    /** A fat green fermenting tank fed by a pipe, with a fuel-orange gauge (Plan 10). */
    private static void fuelPlant(Graphics2D h) {
        ground(h, GROUND);
        fillOutlined(h, new Rectangle2D.Double(1.8, 8.4, 4.4, 1.6), STEEL_DARK);
        fillOutlined(h, new Rectangle2D.Double(1.8, 8.4, 1.6, 4.2), STEEL_DARK);
        Shape tank = new RoundRectangle2D.Double(5.6, 3, 8, 9.6, 4, 3.4);
        fillOutlined(h, tank, grad(5.6, 0, new Color(170, 222, 96), 13.6, 0, new Color(64, 118, 40)));
        h.setColor(new Color(40, 78, 24, 160));
        h.draw(new Line2D.Double(6, 6.4, 13.2, 6.4));
        h.draw(new Line2D.Double(6, 9.4, 13.2, 9.4));
        fillOutlined(h, new Ellipse2D.Double(8.2, 6.9, 3, 3), new Color(255, 160, 60));
    }

    /** A sawtooth-roofed works with a row of lit windows. */
    private static void factory(Graphics2D h) {
        ground(h, GROUND);
        Path2D shell = poly(1.8, 12.6, 1.8, 5.4, 6, 7.6, 6, 5, 10.2, 7.2, 10.2, 4.6, 14.2, 6.8, 14.2, 12.6);
        fillOutlined(h, shell, grad(0, 4.6, new Color(150, 162, 182), 0, 12.6, new Color(80, 90, 108)));
        h.setColor(new Color(140, 200, 255));
        h.draw(new Line2D.Double(2.6, 6.2, 5.4, 7.6));
        h.draw(new Line2D.Double(6.8, 5.8, 9.6, 7.2));
        h.draw(new Line2D.Double(11, 5.4, 13.6, 6.8));
        h.setColor(ResourceGlyphs.accent(spacecolony.sim.Resource.COMPONENTS));
        for (int i = 0; i < 4; i++) h.fill(new Rectangle2D.Double(3 + i * 2.7, 9, 1.6, 1.4));
        fillOutlined(h, new Rectangle2D.Double(7, 10.8, 2, 1.8), new Color(40, 48, 62));
    }

    /** A tilted solar panel on a mast. */
    private static void powerPlant(Graphics2D h) {
        ground(h, GROUND);
        h.setColor(STEEL);
        h.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        h.draw(new Line2D.Double(8.4, 8.6, 8.4, 12.6));
        h.draw(new Line2D.Double(6.6, 12.6, 10.2, 12.6));
        h.setStroke(new BasicStroke(0.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D panel = poly(1.6, 4.4, 11.6, 2, 14.6, 8.4, 4.4, 10.8);
        fillOutlined(h, panel, grad(2, 2, new Color(96, 150, 236), 14, 10, new Color(30, 56, 130)));
        h.setColor(new Color(170, 204, 255, 200));
        for (int i = 1; i < 4; i++) {
            double t = i / 4.0;
            h.draw(new Line2D.Double(1.6 + 10 * t, 4.4 - 2.4 * t, 4.4 + 10.2 * t, 10.8 - 2.4 * t));
        }
        h.draw(new Line2D.Double(3, 7.6, 13.1, 5.2));
        h.setColor(ResourceGlyphs.accent(spacecolony.sim.Resource.ENERGY));
        h.fill(new Ellipse2D.Double(12.6, 0.8, 2.4, 2.4));
    }

    /** A gantry over a pad; the ship in it is added by {@link #draw} as a real sprite. */
    private static void shipyard(Graphics2D h) {
        ground(h, GROUND);
        h.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        h.setColor(OUTLINE);
        Path2D gantry = new Path2D.Double();
        gantry.moveTo(2.6, 12.6); gantry.lineTo(2.6, 2.6); gantry.lineTo(13.4, 2.6); gantry.lineTo(13.4, 12.6);
        h.draw(gantry);
        h.setStroke(new BasicStroke(0.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        h.setColor(new Color(232, 186, 70));
        h.draw(gantry);
        for (int i = 0; i < 3; i++) {
            double y = 4.4 + i * 2.8;
            h.draw(new Line2D.Double(2.6, y, 3.8, y + 1.4));
            h.draw(new Line2D.Double(13.4, y, 12.2, y + 1.4));
        }
        h.setColor(STEEL);
        h.draw(new Line2D.Double(8, 2.6, 8, 4));
    }

    /** A flask of bubbling violet liquid. */
    private static void researchLab(Graphics2D h) {
        Path2D flask = poly(6.4, 1.6, 9.6, 1.6, 9.6, 6, 13.8, 13.4, 13.4, 14.4, 2.6, 14.4, 2.2, 13.4, 6.4, 6);
        h.setColor(new Color(200, 220, 240, 70));
        h.fill(flask);
        Graphics2D c = (Graphics2D) h.create();
        c.clip(flask);
        c.setPaint(grad(0, 9, new Color(196, 150, 255), 0, 14.4, new Color(110, 60, 200)));
        c.fill(new Rectangle2D.Double(0, 9.2, 16, 6));
        c.dispose();
        h.setColor(new Color(240, 230, 255));
        h.fill(new Ellipse2D.Double(6.4, 10.6, 1.6, 1.6));
        h.fill(new Ellipse2D.Double(9, 11.8, 1.1, 1.1));
        h.fill(new Ellipse2D.Double(8, 7.6, 1, 1));
        h.setColor(OUTLINE);
        h.draw(flask);
        fillOutlined(h, new RoundRectangle2D.Double(5.6, 1, 4.8, 1.4, 0.8, 0.8), new Color(200, 210, 226));
        h.setColor(new Color(255, 255, 255, 170));
        h.draw(new Line2D.Double(7.2, 2.8, 7.2, 6.2));
    }
}

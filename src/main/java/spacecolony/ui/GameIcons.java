package spacecolony.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import javax.swing.Icon;
import spacecolony.render.BuildingGlyphs;
import spacecolony.render.ResourceGlyphs;
import spacecolony.sim.BuildingType;
import spacecolony.sim.Resource;

/**
 * Swing {@link Icon}s for the resource and building glyphs. They are drawn as vectors on every
 * paint, so they stay sharp at any size and on high-DPI screens.
 */
public final class GameIcons {
    /** Next to text in lists, ledgers and forms. */
    public static final int SMALL = 14;
    /** Building rows in the detail panel and the build dialog's list. */
    public static final int MEDIUM = 20;
    /** The build dialog's card title. */
    public static final int LARGE = 32;

    private GameIcons() {}

    public static Icon resource(Resource r, int size) {
        return new Glyph(size, size, (g, x, y) -> ResourceGlyphs.draw(g, r, x, y, size), "resource:" + r);
    }

    public static Icon building(BuildingType t, int size) {
        return new Glyph(size, size, (g, x, y) -> BuildingGlyphs.draw(g, t, x, y, size), "building:" + t);
    }

    public static Icon population(int size) {
        return new Glyph(size, size, (g, x, y) -> ResourceGlyphs.drawPopulation(g, x, y, size), "population");
    }

    /**
     * A disclosure triangle (▸ closed, ▾ open) followed by another icon, for the ledger's
     * expandable rows.
     */
    public static Icon disclosure(boolean open, Icon then, Color colour) {
        int arrow = 10;
        return new Glyph(arrow + then.getIconWidth(), Math.max(arrow, then.getIconHeight()), (g, x, y) -> {
            int h = Math.max(arrow, then.getIconHeight());
            double cx = x + 4, cy = y + h / 2.0;
            Path2D t = new Path2D.Double();
            if (open) { t.moveTo(cx - 3.2, cy - 1.8); t.lineTo(cx + 3.2, cy - 1.8); t.lineTo(cx, cy + 2.2); }
            else      { t.moveTo(cx - 1.8, cy - 3.2); t.lineTo(cx + 2.2, cy); t.lineTo(cx - 1.8, cy + 3.2); }
            t.closePath();
            g.setColor(colour);
            g.fill(t);
            then.paintIcon(null, g, x + arrow, y + (h - then.getIconHeight()) / 2);
        }, (open ? "open:" : "closed:") + then);
    }

    private interface Painter {
        void paint(Graphics2D g, int x, int y);
    }

    private record Glyph(int width, int height, Painter painter, String label) implements Icon {
        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            painter.paint(g2, x, y);
            g2.dispose();
        }
        @Override public int getIconWidth() { return width; }
        @Override public int getIconHeight() { return height; }
        @Override public String toString() { return label; }
    }
}

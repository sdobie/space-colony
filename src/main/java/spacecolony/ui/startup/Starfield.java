package spacecolony.ui.startup;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.util.Random;
import spacecolony.ui.UiColors;

/**
 * Backdrop shared by the splash and title screens: a seeded starfield, the sun low on the left,
 * and a few planets drifting along faint orbit arcs. Star positions are fractions of the size,
 * so the field survives resizes.
 */
final class Starfield {
    private static final int STARS = 300;
    /** Orbit radii as fractions of the height, planet phase, angular speed (rad/s) and size. */
    private static final double[][] PLANETS = {
        {0.55, 0.30, 0.050, 5},
        {0.85, -0.10, 0.028, 7},
        {1.20, 0.18, 0.016, 10},
    };
    private static final Color[] PLANET_COLORS = {
        new Color(180, 130, 100), new Color(120, 170, 220), new Color(220, 180, 130),
    };

    private final float[] sx = new float[STARS], sy = new float[STARS];
    private final int[] size = new int[STARS];
    private final Color[] shade = new Color[STARS];

    Starfield(long seed) {
        Random r = new Random(seed);
        Color[] levels = { new Color(90, 100, 130), new Color(160, 170, 200), new Color(235, 240, 255) };
        for (int i = 0; i < STARS; i++) {
            sx[i] = r.nextFloat();
            sy[i] = r.nextFloat();
            size[i] = r.nextInt(10) == 0 ? 2 : 1;
            shade[i] = levels[r.nextInt(3) == 0 ? 2 : r.nextInt(2)];
        }
    }

    /** Paints the backdrop into {@code w × h}; {@code t} is seconds of animation (0 = still). */
    void paint(Graphics2D g, int w, int h, double t) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(UiColors.STARFIELD_BG);
        g.fillRect(0, 0, w, h);
        for (int i = 0; i < STARS; i++) {
            g.setColor(shade[i]);
            g.fillRect((int) (sx[i] * w), (int) (sy[i] * h), size[i], size[i]);
        }

        double sunX = -0.10 * w, sunY = 0.75 * h, sunR = 0.35 * h;
        g.setColor(UiColors.ORBIT_LINE);
        for (double[] p : PLANETS) {
            double r = p[0] * h;
            g.draw(new Arc2D.Double(sunX - r, sunY - r, 2 * r, 2 * r, -60, 150, Arc2D.OPEN));
        }
        for (int i = 0; i < PLANETS.length; i++) {
            double[] p = PLANETS[i];
            double r = p[0] * h;
            double a = p[1] + t * p[2];
            double px = sunX + r * Math.cos(a), py = sunY - r * Math.sin(a);
            g.setColor(PLANET_COLORS[i]);
            g.fill(new Ellipse2D.Double(px - p[3] / 2, py - p[3] / 2, p[3], p[3]));
        }

        g.setPaint(new RadialGradientPaint(new Point2D.Double(sunX, sunY), (float) (sunR * 1.6),
            new float[] {0f, 0.55f, 0.62f, 1f},
            new Color[] {new Color(255, 236, 170), UiColors.SUN, new Color(255, 200, 90, 70), new Color(255, 200, 90, 0)}));
        g.fill(new Ellipse2D.Double(sunX - sunR * 1.6, sunY - sunR * 1.6, sunR * 3.2, sunR * 3.2));
    }
}

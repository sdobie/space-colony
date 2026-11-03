package spacecolony.render;

import java.awt.Color;
import java.awt.image.BufferedImage;
import spacecolony.sim.Resource;
import spacecolony.sim.ResourceSurvey;
import spacecolony.sim.ResourceYieldSampler;

/**
 * Colours a body's flat surface map by where one resource is in the ground, so a player can see
 * where a mine or farm would do well. The terrain is turned to dark grey, then each spot is
 * painted in the resource's own colour at one of four strengths, the same bands the survey
 * uses ({@link ResourceSurvey#rating}): Poor ground is left grey, Fair is a light wash, Good is
 * stronger and Rich is solid. A thin dark line traces each band edge so the patches read as
 * shapes rather than a blur.
 *
 * <p>Uses {@link SphereRenderer}'s equirectangular mapping: pixel column x is longitude
 * {@code (x + 0.5) · 2π / W} and row y is latitude {@code π/2 − (y + 0.5) · π / H}.
 */
public final class YieldOverlay {
    /** Yield where each band starts: Fair, Good, Rich (Poor is below the first). */
    public static final double[] BAND_START = { 0.2, 0.4, 0.6 };
    /** How much of the resource colour covers the grey terrain in each band, Poor to Rich. */
    private static final double[] COVER = { 0.0, 0.3, 0.62, 1.0 };
    /** Yield is sampled every STEP pixels and reused for the block; 1 keeps band edges smooth. */
    private static final int STEP = 1;

    private YieldOverlay() {}

    public static double lonAt(int x, int width) { return (x + 0.5) * 2 * Math.PI / width; }
    public static double latAt(int y, int height) { return Math.PI / 2 - (y + 0.5) * Math.PI / height; }

    /** The overlay colour for {@code r}: one hue per resource so ORE never looks like ICE. */
    public static Color colorOf(Resource r) {
        return switch (r) {
            case ORE -> new Color(0xFF, 0x7A, 0x2E);      // rust orange
            case SILICATE -> new Color(0xFF, 0xE0, 0x4D); // sand yellow
            case ICE -> new Color(0x5C, 0xE1, 0xFF);      // ice blue
            case BIOMASS -> new Color(0x6B, 0xE0, 0x4A);  // leaf green
            case HYDROGEN -> new Color(0xE0, 0x6C, 0xFF);     // violet
            default -> new Color(0xF0, 0xF0, 0xF0);
        };
    }

    /** 0 Poor, 1 Fair, 2 Good, 3 Rich, matching {@link ResourceSurvey#rating}. */
    public static int band(double y) {
        int b = 0;
        while (b < BAND_START.length && y >= BAND_START[b]) b++;
        return b;
    }

    /** The swatch colour for band {@code b} over plain dark terrain, for the legend. */
    public static Color swatch(Resource r, int b) {
        return new Color(blend(0x606060, b, r));
    }

    /** A copy of {@code flat} coloured by {@code r}'s yield. */
    public static BufferedImage tint(BufferedImage flat, ResourceYieldSampler s, Resource r) {
        int w = flat.getWidth(), h = flat.getHeight();
        int bw = (w + STEP - 1) / STEP, bh = (h + STEP - 1) / STEP;
        int[][] bands = new int[bh][bw];
        for (int j = 0; j < bh; j++) {
            double lat = latAt(Math.min(h - 1, j * STEP + STEP / 2), h);
            for (int i = 0; i < bw; i++) {
                double y = s == null ? 0.0 : s.sample(r, lat, lonAt(Math.min(w - 1, i * STEP + STEP / 2), w));
                bands[j][i] = band(y);
            }
        }
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int j = 0; j < bh; j++) {
            for (int i = 0; i < bw; i++) {
                int b = bands[j][i];
                // A block on the rich side of a band edge gets the outline (longitude wraps).
                boolean edge = b > 0 && (bands[j][(i + 1) % bw] < b || bands[j][(i + bw - 1) % bw] < b
                    || (j > 0 && bands[j - 1][i] < b) || (j < bh - 1 && bands[j + 1][i] < b));
                for (int py = j * STEP; py < Math.min(h, (j + 1) * STEP); py++)
                    for (int px = i * STEP; px < Math.min(w, (i + 1) * STEP); px++) {
                        int c = blend(flat.getRGB(px, py), b, r);
                        out.setRGB(px, py, edge ? darken(c) : c);
                    }
            }
        }
        return out;
    }

    /** {@code rgb} turned to dark grey and covered by {@code r}'s colour as much as band {@code b} says. */
    static int blend(int rgb, int b, Resource r) {
        Color hue = colorOf(r);
        int[] ramp = { hue.getRed(), hue.getGreen(), hue.getBlue() };
        int r0 = (rgb >> 16) & 0xFF, g0 = (rgb >> 8) & 0xFF, b0 = rgb & 0xFF;
        double grey = 0.4 * (0.3 * r0 + 0.59 * g0 + 0.11 * b0);
        double cover = COVER[Math.max(0, Math.min(COVER.length - 1, b))];
        int[] out = new int[3];
        for (int i = 0; i < 3; i++) out[i] = (int) Math.round(grey + (ramp[i] - grey) * cover);
        return (out[0] << 16) | (out[1] << 8) | out[2];
    }

    private static int darken(int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        return ((r * 2 / 5) << 16) | ((g * 2 / 5) << 8) | (b * 2 / 5);
    }
}

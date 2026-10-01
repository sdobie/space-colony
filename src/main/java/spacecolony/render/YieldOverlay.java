package spacecolony.render;

import java.awt.image.BufferedImage;
import spacecolony.sim.Resource;
import spacecolony.sim.ResourceYieldSampler;

/**
 * Colours a body's flat surface map by one resource's yield (Plan 9 §5), so rich ground shows on
 * the globe as a heat map over faint grey terrain. Uses {@link SphereRenderer}'s equirectangular mapping: pixel column x is longitude
 * {@code (x + 0.5) · 2π / W} and row y is latitude {@code π/2 − (y + 0.5) · π / H}.
 */
public final class YieldOverlay {
    /** Ramp stops at yield 0, 0.5 and 1 (indigo, teal, yellow): readable on red, white or blue worlds. */
    private static final int[][] RAMP = { { 0x3B, 0x2F, 0x80 }, { 0x21, 0x91, 0x8C }, { 0xFD, 0xE7, 0x25 } };
    /** How much of the ramp colour covers the surface; the rest is the darkened grey terrain. */
    private static final double COVER = 0.75;
    /** Yield is sampled every STEP pixels (the noise is smooth) and reused for the block. */
    private static final int STEP = 2;

    private YieldOverlay() {}

    public static double lonAt(int x, int width) { return (x + 0.5) * 2 * Math.PI / width; }
    public static double latAt(int y, int height) { return Math.PI / 2 - (y + 0.5) * Math.PI / height; }

    /** A copy of {@code flat} coloured by {@code r}'s yield. */
    public static BufferedImage tint(BufferedImage flat, ResourceYieldSampler s, Resource r) {
        int w = flat.getWidth(), h = flat.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int by = 0; by < h; by += STEP) {
            double lat = latAt(Math.min(h - 1, by + STEP / 2), h);
            for (int bx = 0; bx < w; bx += STEP) {
                double y = s == null ? 0.0 : s.sample(r, lat, lonAt(Math.min(w - 1, bx + STEP / 2), w));
                for (int py = by; py < Math.min(h, by + STEP); py++)
                    for (int px = bx; px < Math.min(w, bx + STEP); px++)
                        out.setRGB(px, py, blend(flat.getRGB(px, py), y));
            }
        }
        return out;
    }

    /**
     * {@code rgb} turned to dark grey (so the body's own colour can't mask the ramp) and covered
     * {@link #COVER} by the ramp colour for yield {@code y}.
     */
    static int blend(int rgb, double y) {
        double t = Math.min(1.0, Math.max(0.0, y));
        int[] lo = t < 0.5 ? RAMP[0] : RAMP[1];
        int[] hi = t < 0.5 ? RAMP[1] : RAMP[2];
        double f = t < 0.5 ? t * 2 : (t - 0.5) * 2;
        int r0 = (rgb >> 16) & 0xFF, g0 = (rgb >> 8) & 0xFF, b0 = rgb & 0xFF;
        double grey = 0.45 * (0.3 * r0 + 0.59 * g0 + 0.11 * b0);
        int[] out = new int[3];
        for (int i = 0; i < 3; i++) {
            double ramp = lo[i] + (hi[i] - lo[i]) * f;
            out[i] = (int) Math.round(grey + (ramp - grey) * COVER);
        }
        return (out[0] << 16) | (out[1] << 8) | out[2];
    }
}

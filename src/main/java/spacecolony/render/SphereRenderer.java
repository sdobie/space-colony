package spacecolony.render;

import java.awt.Color;
import java.awt.image.BufferedImage;
import spacecolony.world.SimplexNoise;

/**
 * Renders an equirectangular map onto a 3D sphere with atmospheric glow,
 * Lambertian shading, and a starfield background.
 */
public class SphereRenderer {

    // Atmosphere color (bluish glow)
    private static final int ATMO_R = 100, ATMO_G = 150, ATMO_B = 255;

    /**
     * Render the flat map onto a sphere.
     * @param flatMap  the equirectangular map image
     * @param size     output image will be size x size
     * @param rotationDeg  longitude rotation in degrees (0-360)
     * @return rendered sphere image
     */
    public static BufferedImage render(BufferedImage flatMap, int size, double rotationDeg) {
        return render(flatMap, size, rotationDeg, 0, System.nanoTime());
    }

    public static BufferedImage render(BufferedImage flatMap, int size, double rotationDeg, long starSeed) {
        return render(flatMap, size, rotationDeg, 0, starSeed);
    }

    public static BufferedImage render(BufferedImage flatMap, int size, double rotationDeg, double tiltDeg, long starSeed) {
        return render(flatMap, size, rotationDeg, tiltDeg, 1.0, starSeed);
    }

    private static final SimplexNoise detailNoise = new SimplexNoise(12345);

    /** Default Earth-like atmosphere. */
    private static final Color DEFAULT_ATMOSPHERE = new Color(100, 150, 255);

    public static BufferedImage render(BufferedImage flatMap, int size, double rotationDeg, double tiltDeg, double zoom, long starSeed) {
        return render(flatMap, size, rotationDeg, tiltDeg, zoom, starSeed, DEFAULT_ATMOSPHERE);
    }

    /**
     * Render with explicit atmosphere color. Pass {@code null} for airless bodies — the
     * limb-darkening pass still runs internally but the colored glow is omitted, leaving
     * the sphere surrounded by black space.
     */
    public static BufferedImage render(BufferedImage flatMap, int size, double rotationDeg, double tiltDeg, double zoom, long starSeed, Color atmosphereColor) {
        int mapW = flatMap.getWidth();
        int mapH = flatMap.getHeight();

        BufferedImage output = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);

        double radius = size * 0.45 * zoom;
        double cx = size / 2.0;
        double cy = size / 2.0;
        double rotRad = Math.toRadians(rotationDeg);
        double tiltRad = Math.toRadians(clamp(tiltDeg, -80, 80));
        double cosTilt = Math.cos(tiltRad);
        double sinTilt = Math.sin(tiltRad);

        // Light direction for sphere shading (from upper-left)
        double lightX = -0.5;
        double lightY = -0.6;
        double lightZ = 0.6;
        double lightLen = Math.sqrt(lightX * lightX + lightY * lightY + lightZ * lightZ);
        lightX /= lightLen; lightY /= lightLen; lightZ /= lightLen;

        // Generate starfield
        java.util.Random starRng = new java.util.Random(starSeed);

        for (int py = 0; py < size; py++) {
            for (int px = 0; px < size; px++) {
                double dx = px - cx;
                double dy = py - cy;
                double dist = Math.sqrt(dx * dx + dy * dy);

                if (dist <= radius) {
                    // On the sphere: compute 3D normal
                    double nx = dx / radius;
                    double ny = dy / radius;
                    double nz = Math.sqrt(Math.max(0, 1.0 - nx * nx - ny * ny));

                    // Apply tilt rotation (rotate around X axis)
                    double ny2 = ny * cosTilt - nz * sinTilt;
                    double nz2 = ny * sinTilt + nz * cosTilt;

                    // Sphere to lat/lon
                    double lat = Math.asin(clamp(-ny2, -1, 1));
                    double lon = Math.atan2(nx, nz2) + rotRad;

                    // Normalize lon to [0, 2*PI]
                    lon = ((lon % (2 * Math.PI)) + 2 * Math.PI) % (2 * Math.PI);

                    // Sample the flat map with bilinear interpolation
                    double mapX = lon / (2 * Math.PI) * mapW;
                    double mapY = (0.5 - lat / Math.PI) * mapH;

                    int color = sampleBilinear(flatMap, mapX, mapY, mapW, mapH);

                    // Add procedural detail that appears as we zoom in
                    if (zoom > 1.05) {
                        double detailStrength = Math.min(1.0, (zoom - 1.0) / 1.5) * 0.25;

                        // Multiple octaves of high-frequency detail
                        double detail = 0;
                        double freq = 15.0 * zoom;  // frequency scales with zoom
                        double amp = 1.0;
                        double totalAmp = 0;
                        for (int oct = 0; oct < 4; oct++) {
                            detail += amp * detailNoise.eval(nx * freq + 100, ny2 * freq + 200, nz2 * freq + 300);
                            totalAmp += amp;
                            freq *= 2.0;
                            amp *= 0.5;
                        }
                        detail /= totalAmp;

                        int cr = (color >> 16) & 0xFF;
                        int cg = (color >> 8) & 0xFF;
                        int cb = color & 0xFF;

                        // Only apply detail to non-ocean pixels
                        boolean isLand = cg > cb || cr > 80;
                        if (isLand) {
                            double mod = 1.0 + detail * detailStrength;
                            cr = clampInt((int)(cr * mod), 0, 255);
                            cg = clampInt((int)(cg * mod), 0, 255);
                            cb = clampInt((int)(cb * mod), 0, 255);
                            color = (cr << 16) | (cg << 8) | cb;
                        }
                    }

                    // Lambertian shading on sphere surface
                    double diffuse = nx * lightX + ny * lightY + nz * lightZ;
                    diffuse = Math.max(0.08, diffuse); // ambient minimum

                    // Slight specular highlight
                    double spec = Math.pow(Math.max(0, diffuse), 20) * 0.15;

                    int r = (color >> 16) & 0xFF;
                    int g = (color >> 8) & 0xFF;
                    int b = color & 0xFF;

                    r = clampInt((int) (r * (0.3 + 0.7 * diffuse) + 255 * spec), 0, 255);
                    g = clampInt((int) (g * (0.3 + 0.7 * diffuse) + 255 * spec), 0, 255);
                    b = clampInt((int) (b * (0.3 + 0.7 * diffuse) + 255 * spec), 0, 255);

                    // Atmospheric limb darkening/brightening at edges — only blend when atmosphere present.
                    if (atmosphereColor != null) {
                        double edgeFactor = 1.0 - nz; // 0 at center, 1 at edge
                        double atmoBlend = Math.pow(edgeFactor, 3) * 0.5;
                        int ar = atmosphereColor.getRed();
                        int ag = atmosphereColor.getGreen();
                        int ab = atmosphereColor.getBlue();
                        r = clampInt((int) (r * (1 - atmoBlend) + ar * atmoBlend), 0, 255);
                        g = clampInt((int) (g * (1 - atmoBlend) + ag * atmoBlend), 0, 255);
                        b = clampInt((int) (b * (1 - atmoBlend) + ab * atmoBlend), 0, 255);
                    }

                    output.setRGB(px, py, (r << 16) | (g << 8) | b);

                } else if (atmosphereColor != null && dist < radius + radius * 0.04) {
                    // Atmospheric glow around the sphere — airless bodies skip this entirely.
                    double glowDist = (dist - radius) / (radius * 0.04);
                    double glowIntensity = Math.pow(1.0 - glowDist, 2) * 0.6;
                    int r = clampInt((int) (atmosphereColor.getRed()   * glowIntensity), 0, 255);
                    int g = clampInt((int) (atmosphereColor.getGreen() * glowIntensity), 0, 255);
                    int b = clampInt((int) (atmosphereColor.getBlue()  * glowIntensity), 0, 255);
                    output.setRGB(px, py, (r << 16) | (g << 8) | b);
                }
                // else: stays black (space)
            }
        }

        // Scatter some stars in the background
        int numStars = size * size / 400;
        for (int i = 0; i < numStars; i++) {
            int sx = starRng.nextInt(size);
            int sy = starRng.nextInt(size);
            double sdx = sx - cx;
            double sdy = sy - cy;
            double sdist = Math.sqrt(sdx * sdx + sdy * sdy);
            if (sdist > radius + radius * 0.05) {
                int brightness = 100 + starRng.nextInt(156);
                // Slight color variation
                int sr = clampInt(brightness + starRng.nextInt(30) - 15, 0, 255);
                int sg = clampInt(brightness + starRng.nextInt(30) - 15, 0, 255);
                int sb = clampInt(brightness + starRng.nextInt(40) - 10, 0, 255);
                output.setRGB(sx, sy, (sr << 16) | (sg << 8) | sb);
            }
        }

        return output;
    }

    /**
     * Bilinear interpolation sampling of the flat map.
     */
    private static int sampleBilinear(BufferedImage img, double x, double y, int w, int h) {
        int x0 = ((int) Math.floor(x)) % w;
        if (x0 < 0) x0 += w;
        int x1 = (x0 + 1) % w;
        int y0 = clampInt((int) Math.floor(y), 0, h - 1);
        int y1 = clampInt(y0 + 1, 0, h - 1);

        double fx = x - Math.floor(x);
        double fy = y - Math.floor(y);

        int c00 = img.getRGB(x0, y0);
        int c10 = img.getRGB(x1, y0);
        int c01 = img.getRGB(x0, y1);
        int c11 = img.getRGB(x1, y1);

        return lerpRGB(
            lerpRGB(c00, c10, fx),
            lerpRGB(c01, c11, fx),
            fy
        );
    }

    private static int lerpRGB(int c1, int c2, double t) {
        int r = (int) (((c1 >> 16) & 0xFF) * (1 - t) + ((c2 >> 16) & 0xFF) * t);
        int g = (int) (((c1 >> 8) & 0xFF) * (1 - t) + ((c2 >> 8) & 0xFF) * t);
        int b = (int) ((c1 & 0xFF) * (1 - t) + (c2 & 0xFF) * t);
        return (clampInt(r, 0, 255) << 16) | (clampInt(g, 0, 255) << 8) | clampInt(b, 0, 255);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static int clampInt(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    /**
     * Convert a screen pixel on a rendered sphere back to (lat, lon). The inverse of the
     * forward projection in {@link #render}: returns {@code null} when the pixel falls
     * outside the rendered disc.
     *
     * @param px screen x in {@code [0, size)}
     * @param py screen y in {@code [0, size)}
     * @param size the size argument passed to render()
     * @param rotationDeg the rotationDeg passed to render()
     * @param tiltDeg the tiltDeg passed to render() (clamped to [-80, 80] internally)
     * @param zoom the zoom passed to render()
     * @return {@code {lat, lon}} in radians (lat in [-π/2, π/2], lon in (-π, π]), or
     *         {@code null} if the pixel is outside the sphere disc
     */
    public static double[] unproject(int px, int py, int size, double rotationDeg, double tiltDeg, double zoom) {
        double radius = size * 0.45 * zoom;
        double cx = size / 2.0;
        double cy = size / 2.0;
        double nx = (px - cx) / radius;
        double nyScreen = (py - cy) / radius;
        double d2 = nx * nx + nyScreen * nyScreen;
        if (d2 > 1.0) return null; // outside the disc
        double nz = Math.sqrt(1.0 - d2); // camera-facing point on unit sphere
        // Forward projection rotates (ny, nz) about X by tilt: ny2 = ny*cosT - nz*sinT;
        // so this is the identity transform on the screen-space (nx, nyScreen, nz) frame.
        // We pass nyScreen as ny since the forward formula reads ny directly from screen y.
        double tiltClamped = Math.max(-80.0, Math.min(80.0, tiltDeg));
        double tiltRad = Math.toRadians(tiltClamped);
        double cosT = Math.cos(tiltRad), sinT = Math.sin(tiltRad);
        double ny2 = nyScreen * cosT - nz * sinT;
        double nz2 = nyScreen * sinT + nz * cosT;
        double rotRad = Math.toRadians(rotationDeg);
        double lat = Math.asin(clamp(-ny2, -1.0, 1.0));
        double lon = Math.atan2(nx, nz2) + rotRad;
        // Normalize lon to (-π, π].
        lon = ((lon + Math.PI) % (2 * Math.PI) + 2 * Math.PI) % (2 * Math.PI) - Math.PI;
        return new double[] { lat, lon };
    }
}

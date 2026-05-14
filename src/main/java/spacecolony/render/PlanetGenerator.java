package spacecolony.render;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Random;
import spacecolony.world.SimplexNoise;

/**
 * Generates realistic planet maps using 3D simplex noise sampled on a sphere,
 * with domain warping, hillshading for 3D relief, and bimodal elevation.
 */
public class PlanetGenerator {

    private final int width;
    private final int height;

    private BodyAppearance currentAppearance = BodyAppearances.defaultFor(spacecolony.sim.BodyType.ROCKY);

    // Water colors
    private static final Color DEEP_OCEAN        = new Color(15, 35, 90);
    private static final Color OCEAN             = new Color(25, 55, 130);
    private static final Color SHALLOW_WATER     = new Color(45, 85, 155);
    private static final Color SHORE_WATER       = new Color(65, 110, 170);

    // Land colors — olive/tan palette matching realistic planet renders
    private static final Color BEACH             = new Color(190, 175, 130);
    private static final Color SUBTROPICAL_DESERT = new Color(175, 150, 95);
    private static final Color DRY_GRASSLAND     = new Color(150, 140, 75);
    private static final Color GRASSLAND         = new Color(140, 155, 65);
    private static final Color TEMPERATE_FOREST  = new Color(75, 115, 50);
    private static final Color TROPICAL_FOREST   = new Color(50, 100, 40);
    private static final Color BOREAL_FOREST     = new Color(55, 80, 45);
    private static final Color TUNDRA            = new Color(160, 170, 150);
    private static final Color ALPINE_ROCK       = new Color(120, 110, 95);   // bare rock above treeline
    private static final Color ALPINE_SCREE      = new Color(145, 135, 120);  // lighter rocky debris
    private static final Color SNOW              = new Color(235, 242, 248);
    private static final Color ICE               = new Color(210, 225, 240);

    public PlanetGenerator() {
        this(1024, 512);
    }

    public PlanetGenerator(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }

    public BufferedImage generate() {
        return generate(new Random().nextLong());
    }

    public BufferedImage generate(long seed) {
        // Independent noise generators for each role
        SimplexNoise continentNoise = new SimplexNoise(seed);
        SimplexNoise warpNoise1     = new SimplexNoise(seed + 1000);
        SimplexNoise warpNoise2     = new SimplexNoise(seed + 2000);
        SimplexNoise warpNoise3     = new SimplexNoise(seed + 3000);
        SimplexNoise detailNoise    = new SimplexNoise(seed + 4000);
        SimplexNoise moistNoise     = new SimplexNoise(seed + 6000);
        SimplexNoise tempNoise      = new SimplexNoise(seed + 7000);
        SimplexNoise mountainNoise  = new SimplexNoise(seed + 8000);
        SimplexNoise roughNoise     = new SimplexNoise(seed + 9000);

        double[][] elevation = new double[width][height];
        double[][] roughness = new double[width][height]; // high-freq detail for hillshading only
        double[][] moisture = new double[width][height];
        double[][] temperature = new double[width][height];

        for (int py = 0; py < height; py++) {
            double lat = Math.PI * (0.5 - (double) py / height);
            double cosLat = Math.cos(lat);
            double sinLat = Math.sin(lat);
            double absLat = Math.abs(sinLat);

            for (int px = 0; px < width; px++) {
                double lon = 2.0 * Math.PI * px / width;

                // 3D point on the unit sphere
                double sx = cosLat * Math.cos(lon);
                double sy = cosLat * Math.sin(lon);
                double sz = sinLat;

                // === CONTINENTALNESS: very low frequency, smooth ===
                double cont = continentNoise.fractal(sx, sy, sz, 2, 0.4, 2.0);

                // === DOMAIN WARPING ===
                double warpFreq = 0.8;
                double warpAmp = 0.45;
                double wx = warpNoise1.fractal(sx * warpFreq + 5.2, sy * warpFreq + 1.3, sz * warpFreq + 3.7, 2, 0.4, 2.0) * warpAmp;
                double wy = warpNoise2.fractal(sx * warpFreq + 9.1, sy * warpFreq + 4.8, sz * warpFreq + 7.2, 2, 0.4, 2.0) * warpAmp;
                double wz = warpNoise3.fractal(sx * warpFreq + 2.6, sy * warpFreq + 8.4, sz * warpFreq + 1.9, 2, 0.4, 2.0) * warpAmp;

                double wsx = sx + wx, wsy = sy + wy, wsz = sz + wz;

                // Level 2: coastline irregularity
                double w2Amp = 0.12;
                double w2x = warpNoise1.fractal(wsx * 2 + 20.1, wsy * 2 + 15.3, wsz * 2 + 10.7, 2, 0.4, 2.0) * w2Amp;
                double w2y = warpNoise2.fractal(wsx * 2 + 25.4, wsy * 2 + 18.9, wsz * 2 + 12.1, 2, 0.4, 2.0) * w2Amp;
                double w2z = warpNoise3.fractal(wsx * 2 + 30.8, wsy * 2 + 22.5, wsz * 2 + 14.3, 2, 0.4, 2.0) * w2Amp;

                double wwsx = wsx + w2x, wwsy = wsy + w2y, wwsz = wsz + w2z;

                // === TERRAIN DETAIL ===
                double terrain = detailNoise.fractal(wwsx * 3.5, wwsy * 3.5, wwsz * 3.5, 4, 0.4, 2.0);

                // === MOUNTAIN REGIONS ===
                double mt = mountainNoise.fractal(wsx * 1.8 + 50, wsy * 1.8 + 50, wsz * 1.8 + 50, 2, 0.4, 2.0);
                mt = smoothstep(Math.max(0, mt + 0.25));

                double mountainElev = mt * 0.9;

                // === COMBINE ===
                double e = cont * 0.55 + terrain * 0.10 + mountainElev * 0.35;
                elevation[px][py] = e;

                // === ROUGHNESS: high-frequency detail for jagged hillshading ===
                double rough = roughNoise.fractal(sx * 8.0, sy * 8.0, sz * 8.0, 6, 0.55, 2.2);
                roughness[px][py] = rough;

                // === MOISTURE: broad regions using unwarped coords ===
                moisture[px][py] = moistNoise.fractal(sx * 1.2 + 40, sy * 1.2 + 40, sz * 1.2 + 40, 2, 0.4, 2.0);

                // === TEMPERATURE: latitude-based with significant noise to break horizontal bands ===
                double baseTemp = 1.0 - absLat;
                double tNoise = tempNoise.fractal(sx * 2, sy * 2, sz * 2, 3, 0.5, 2.0) * 0.25;
                temperature[px][py] = baseTemp * 0.75 + tNoise + 0.12;
            }
        }

        normalize(elevation);
        normalize(moisture);

        // Hypsometric curve
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                elevation[x][y] = hypsometric(elevation[x][y]);
            }
        }

        // Elevation and latitude influence on biomes
        for (int py = 0; py < height; py++) {
            double lat = Math.PI * (0.5 - (double) py / height);
            double absLat = Math.abs(Math.sin(lat));
            for (int px = 0; px < width; px++) {
                double e = elevation[px][py];
                if (e > SEA_LEVEL) {
                    double landH = (e - SEA_LEVEL) / (1.0 - SEA_LEVEL);
                    // Higher land is colder
                    temperature[px][py] -= landH * 0.4;
                    // Higher land is drier (rain shadow effect)
                    if (landH > 0.3) {
                        moisture[px][py] -= (landH - 0.3) * 0.5;
                    }
                }
                if (absLat > 0.75) {
                    double polar = (absLat - 0.75) / 0.25;
                    moisture[px][py] *= (1.0 - polar * 0.5);
                }
            }
        }
        normalize(moisture);

        // === COMPUTE HILLSHADING ===
        // Light direction: upper-left (northwest), angled down at 45 degrees
        double lightAzimuth = Math.toRadians(315); // NW
        double lightAltitude = Math.toRadians(35);
        double lx = Math.cos(lightAltitude) * Math.cos(lightAzimuth);
        double ly = Math.cos(lightAltitude) * Math.sin(lightAzimuth);
        double lz = Math.sin(lightAltitude);

        // Build combined elevation for hillshading: base elevation + roughness
        normalize(roughness);
        double[][] shadingElev = new double[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                double landH = elevation[x][y] >= SEA_LEVEL ?
                    (elevation[x][y] - SEA_LEVEL) / (1.0 - SEA_LEVEL) : 0;
                // More roughness on higher terrain, less on flat lowlands and ocean
                double roughWeight = 0.08 * smoothstep(landH * 2.0);
                shadingElev[x][y] = elevation[x][y] + roughness[x][y] * roughWeight;
            }
        }

        double[][] hillshade = new double[width][height];
        double zFactor = 16.0; // exaggeration factor for relief

        for (int py = 0; py < height; py++) {
            for (int px = 0; px < width; px++) {
                // Compute gradient using central differences, wrapping horizontally
                int xl = (px - 1 + width) % width;
                int xr = (px + 1) % width;
                int yt = Math.max(0, py - 1);
                int yb = Math.min(height - 1, py + 1);

                double dzdx = (shadingElev[xr][py] - shadingElev[xl][py]) * zFactor;
                double dzdy = (shadingElev[px][yt] - shadingElev[px][yb]) * zFactor;

                // Surface normal
                double nx = -dzdx;
                double ny = -dzdy;
                double nz = 1.0;
                double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
                nx /= len; ny /= len; nz /= len;

                // Lambertian shading
                double shade = nx * lx + ny * ly + nz * lz;
                shade = Math.max(0, shade);
                hillshade[px][py] = shade;
            }
        }

        // Normalize hillshade to [0, 1]
        double hsMin = Double.MAX_VALUE, hsMax = -Double.MAX_VALUE;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (hillshade[x][y] < hsMin) hsMin = hillshade[x][y];
                if (hillshade[x][y] > hsMax) hsMax = hillshade[x][y];
            }
        }
        double hsRange = hsMax - hsMin;
        if (hsRange > 0) {
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    hillshade[x][y] = (hillshade[x][y] - hsMin) / hsRange;
                }
            }
        }

        // === COMPUTE RIVERS via flow accumulation ===
        // Only Earth-like bodies (BodyAppearance.computeRivers == true) get rivers.
        // For others, riverIntensity stays all-zeros so the per-pixel river overlay below is a no-op.
        double[][] riverIntensity = new double[width][height];
        if (currentAppearance.computeRivers()) {
            // Flow direction: each land cell flows to its lowest neighbor
            // Flow accumulation: count how many cells drain through each cell
            double[][] flow = new double[width][height];

            // Initialize: every land cell starts with 1 unit of water
            // Higher elevations get slightly more (rainfall on mountains)
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    if (elevation[x][y] >= SEA_LEVEL) {
                        double landH = (elevation[x][y] - SEA_LEVEL) / (1.0 - SEA_LEVEL);
                        flow[x][y] = 1.0 + landH * 0.5;
                    }
                }
            }

            // Sort land cells by elevation (high to low) for downhill flow
            int landCount = 0;
            for (int x = 0; x < width; x++)
                for (int y = 0; y < height; y++)
                    if (elevation[x][y] >= SEA_LEVEL) landCount++;

            int[] sortedX = new int[landCount];
            int[] sortedY = new int[landCount];
            int idx = 0;
            for (int x = 0; x < width; x++)
                for (int y = 0; y < height; y++)
                    if (elevation[x][y] >= SEA_LEVEL) {
                        sortedX[idx] = x; sortedY[idx] = y; idx++;
                    }

            // Simple insertion sort by descending elevation (good enough for this size)
            // Using a more efficient approach: bucket into elevation bands
            // Actually, let's just use Arrays.sort with indices
            Integer[] indices = new Integer[landCount];
            for (int i = 0; i < landCount; i++) indices[i] = i;
            java.util.Arrays.sort(indices, (a, b) ->
                Double.compare(elevation[sortedX[b]][sortedY[b]], elevation[sortedX[a]][sortedY[a]]));

            // Flow water downhill
            int[] dx8 = {-1, 0, 1, -1, 1, -1, 0, 1};
            int[] dy8 = {-1, -1, -1, 0, 0, 1, 1, 1};

            for (int i = 0; i < landCount; i++) {
                int ci = indices[i];
                int cx = sortedX[ci];
                int cy = sortedY[ci];
                double myElev = elevation[cx][cy];

                // Find lowest neighbor
                double lowestElev = myElev;
                int lowestX = -1, lowestY = -1;
                for (int d = 0; d < 8; d++) {
                    int nx = (cx + dx8[d] + width) % width;
                    int ny = cy + dy8[d];
                    if (ny < 0 || ny >= height) continue;
                    if (elevation[nx][ny] < lowestElev) {
                        lowestElev = elevation[nx][ny];
                        lowestX = nx; lowestY = ny;
                    }
                }

                // Flow to lowest neighbor
                if (lowestX >= 0) {
                    flow[lowestX][lowestY] += flow[cx][cy];
                }
            }

            // Normalize flow with log scale for visualization
            double maxFlow = 0;
            for (int x = 0; x < width; x++)
                for (int y = 0; y < height; y++)
                    if (flow[x][y] > maxFlow) maxFlow = flow[x][y];

            double riverThreshold = 30; // minimum flow to show as river
            if (maxFlow > 0) {
                double logMax = Math.log(maxFlow);
                double logThresh = Math.log(riverThreshold);
                for (int x = 0; x < width; x++) {
                    for (int y = 0; y < height; y++) {
                        if (flow[x][y] > riverThreshold && elevation[x][y] >= SEA_LEVEL) {
                            double logFlow = Math.log(flow[x][y]);
                            riverIntensity[x][y] = Math.min(1.0, (logFlow - logThresh) / (logMax - logThresh));
                        }
                    }
                }
            }
        }

        // River color
        Color RIVER_COLOR = new Color(40, 75, 145);

        // === RENDER ===
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int py = 0; py < height; py++) {
            double lat = Math.PI * (0.5 - (double) py / height);
            double absLat = Math.abs(Math.sin(lat));

            for (int px = 0; px < width; px++) {
                double e = elevation[px][py];
                double m = moisture[px][py];
                double t = temperature[px][py];
                double hs = hillshade[px][py];

                Color color = getBiomeColor(e, m, t, absLat, roughness[px][py]);
                // Apply hillshading: land gets full relief, water gets almost none
                double shadeFactor;
                if (e >= SEA_LEVEL) {
                    shadeFactor = 0.15 + hs * 1.45;
                } else {
                    // Very subtle shading on water
                    shadeFactor = 0.85 + hs * 0.2;
                }
                color = applyShading(color, shadeFactor);

                // Draw rivers
                double river = riverIntensity[px][py];
                if (river > 0) {
                    double riverAlpha = smoothstep(river) * 0.95;
                    color = lerpColor(color, RIVER_COLOR, riverAlpha);
                }

                image.setRGB(px, py, color.getRGB());
            }
        }

        return image;
    }

    /**
     * Generate a body-typed equirectangular map. Uses {@code appearance}'s palettes and
     * pipeline flags instead of the Earth-default biome logic.
     */
    public BufferedImage generate(long seed, BodyAppearance appearance) {
        this.currentAppearance = appearance;
        return generate(seed);
    }

    /**
     * Apply a brightness multiplier to a color, clamping to [0, 255].
     */
    private static Color applyShading(Color c, double factor) {
        int r = Math.min(255, Math.max(0, (int) (c.getRed() * factor)));
        int g = Math.min(255, Math.max(0, (int) (c.getGreen() * factor)));
        int b = Math.min(255, Math.max(0, (int) (c.getBlue() * factor)));
        return new Color(r, g, b);
    }

    /**
     * Remaps normalized elevation [0,1] through a curve that produces
     * a bimodal distribution: flat ocean floors and distinct continental shelves.
     */
    private double hypsometric(double e) {
        // Piecewise linear spline approximating Earth's hypsometric curve
        // Input [0,1] -> Output [0,1] with the "sea level" crossing around 0.5
        if (e < 0.30) {
            // Deep ocean floor: mostly flat
            return lerp(0.0, 0.20, e / 0.30);
        } else if (e < 0.42) {
            // Ocean to shelf transition
            return lerp(0.20, 0.38, (e - 0.30) / 0.12);
        } else if (e < 0.50) {
            // Continental shelf / coastline: steep transition
            return lerp(0.38, 0.52, (e - 0.42) / 0.08);
        } else if (e < 0.65) {
            // Lowlands: relatively flat
            return lerp(0.52, 0.62, (e - 0.50) / 0.15);
        } else if (e < 0.80) {
            // Highlands
            return lerp(0.62, 0.78, (e - 0.65) / 0.15);
        } else {
            // Mountains
            return lerp(0.78, 1.0, (e - 0.80) / 0.20);
        }
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * Math.max(0, Math.min(1, t));
    }

    private static double smoothstep(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    private static final double SEA_LEVEL = 0.50;

    private Color getBiomeColor(double elevation, double moisture, double temperature, double absLat, double rough) {
        Color[] ocean = currentAppearance.oceanPalette();
        Color[] land = currentAppearance.landPalette();

        // Gas giant: latitude-banded color using the real lat parameter the caller provides.
        if (currentAppearance.latitudeBanded()) {
            double normalizedLat = absLat / (Math.PI / 2); // [0, 1]
            double idx = normalizedLat * (land.length - 1);
            // Small noise-driven wobble so the bands aren't perfectly straight.
            idx += (rough - 0.5) * 1.5;
            int i = (int) Math.floor(idx);
            return land[Math.max(0, Math.min(land.length - 1, i))];
        }

        // Below sea level: ocean palette (depth-banded) or fall back to land[0] for airless bodies.
        if (elevation < SEA_LEVEL) {
            if (ocean != null) {
                double depth = (SEA_LEVEL - elevation) / SEA_LEVEL;
                if (depth > 0.55) return ocean[0];
                if (depth > 0.30) return lerpColor(ocean[1], ocean[0], (depth - 0.30) / 0.25);
                if (depth > 0.10) return lerpColor(ocean[2], ocean[1], (depth - 0.10) / 0.20);
                return lerpColor(ocean[3], ocean[2], depth / 0.10);
            }
            return land[0];
        }

        // Above sea level: linear bucket lookup into the land palette.
        double base = (ocean != null) ? SEA_LEVEL : 0.0;
        double t = (elevation - base) / Math.max(1e-9, 1.0 - base);
        int idx = (int) Math.floor(t * (land.length - 1));
        return land[Math.max(0, Math.min(land.length - 1, idx))];
    }

    private Color getWaterColor(double depth) {
        if (depth > 0.55) return DEEP_OCEAN;
        if (depth > 0.30) return lerpColor(OCEAN, DEEP_OCEAN, (depth - 0.30) / 0.25);
        if (depth > 0.10) return lerpColor(SHALLOW_WATER, OCEAN, (depth - 0.10) / 0.20);
        return lerpColor(SHORE_WATER, SHALLOW_WATER, depth / 0.10);
    }

    private Color getLandBiome(double moisture, double temperature) {
        // Smooth blending between biomes using interpolation
        // Temperature zones blend over a range instead of hard cutoffs
        double coldWeight    = 1.0 - smoothstep((temperature - 0.10) / 0.15);  // peaks below 0.10
        double coolWeight    = bellCurve(temperature, 0.25, 0.15);              // peaks at 0.25
        double warmWeight    = bellCurve(temperature, 0.45, 0.15);              // peaks at 0.45
        double hotWeight     = smoothstep((temperature - 0.45) / 0.15);         // peaks above 0.60

        // Normalize weights
        double totalW = coldWeight + coolWeight + warmWeight + hotWeight;
        if (totalW > 0) {
            coldWeight /= totalW; coolWeight /= totalW;
            warmWeight /= totalW; hotWeight /= totalW;
        }

        // Get biome color for each temperature zone with smooth moisture blending
        Color coldBiome = blendMoisture(moisture, TUNDRA, 0.30, BOREAL_FOREST, 0.55);
        Color coolBiome = blendMoisture3(moisture, DRY_GRASSLAND, 0.25, GRASSLAND, 0.45, BOREAL_FOREST, 0.65);
        Color warmBiome = blendMoisture3(moisture, DRY_GRASSLAND, 0.25, GRASSLAND, 0.45, TEMPERATE_FOREST, 0.65);
        Color hotBiome  = blendMoisture4(moisture, SUBTROPICAL_DESERT, 0.20, DRY_GRASSLAND, 0.35,
                                          GRASSLAND, 0.50, TROPICAL_FOREST, 0.65);

        // Blend temperature zones together
        int r = (int)(coldBiome.getRed() * coldWeight + coolBiome.getRed() * coolWeight +
                      warmBiome.getRed() * warmWeight + hotBiome.getRed() * hotWeight);
        int g = (int)(coldBiome.getGreen() * coldWeight + coolBiome.getGreen() * coolWeight +
                      warmBiome.getGreen() * warmWeight + hotBiome.getGreen() * hotWeight);
        int b = (int)(coldBiome.getBlue() * coldWeight + coolBiome.getBlue() * coolWeight +
                      warmBiome.getBlue() * warmWeight + hotBiome.getBlue() * hotWeight);
        return new Color(clamp255(r), clamp255(g), clamp255(b));
    }

    /** Bell curve weight centered at 'center' with half-width 'width'. */
    private static double bellCurve(double x, double center, double width) {
        double d = (x - center) / width;
        return Math.max(0, 1.0 - d * d);
    }

    /** Blend between two biomes along a moisture gradient. */
    private static Color blendMoisture(double m, Color dry, double threshold, Color wet, double wetThreshold) {
        double t = smoothstep((m - threshold) / (wetThreshold - threshold));
        return lerpColor(dry, wet, t);
    }

    /** Blend between three biomes along a moisture gradient. */
    private static Color blendMoisture3(double m, Color c1, double t1, Color c2, double t2, Color c3, double t3) {
        if (m < t2) {
            double t = smoothstep((m - t1) / (t2 - t1));
            return lerpColor(c1, c2, t);
        } else {
            double t = smoothstep((m - t2) / (t3 - t2));
            return lerpColor(c2, c3, t);
        }
    }

    /** Blend between four biomes along a moisture gradient. */
    private static Color blendMoisture4(double m, Color c1, double t1, Color c2, double t2,
                                         Color c3, double t3, Color c4, double t4) {
        if (m < t2) {
            double t = smoothstep((m - t1) / (t2 - t1));
            return lerpColor(c1, c2, t);
        } else if (m < t3) {
            double t = smoothstep((m - t2) / (t3 - t2));
            return lerpColor(c2, c3, t);
        } else {
            double t = smoothstep((m - t3) / (t4 - t3));
            return lerpColor(c3, c4, t);
        }
    }

    private static int clamp255(int v) { return Math.max(0, Math.min(255, v)); }

    private void normalize(double[][] data) {
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (data[x][y] < min) min = data[x][y];
                if (data[x][y] > max) max = data[x][y];
            }
        }
        double range = max - min;
        if (range == 0) return;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                data[x][y] = (data[x][y] - min) / range;
            }
        }
    }

    private static Color lerpColor(Color a, Color b, double t) {
        t = Math.max(0, Math.min(1, t));
        return new Color(
            (int) (a.getRed() + (b.getRed() - a.getRed()) * t),
            (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
            (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t)
        );
    }
}

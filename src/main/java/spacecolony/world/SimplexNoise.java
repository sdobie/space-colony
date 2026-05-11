package spacecolony.world;

import java.util.Random;

/**
 * 3D Simplex noise for sphere-surface sampling.
 * Based on Stefan Gustavson's simplex noise implementation.
 */
public class SimplexNoise {

    private final int[] perm = new int[512];
    private final int[] permMod12 = new int[512];

    private static final double F3 = 1.0 / 3.0;
    private static final double G3 = 1.0 / 6.0;

    private static final int[][] GRAD3 = {
        {1,1,0},{-1,1,0},{1,-1,0},{-1,-1,0},
        {1,0,1},{-1,0,1},{1,0,-1},{-1,0,-1},
        {0,1,1},{0,-1,1},{0,1,-1},{0,-1,-1}
    };

    public SimplexNoise(long seed) {
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) p[i] = i;

        Random rng = new Random(seed);
        for (int i = 255; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int tmp = p[i];
            p[i] = p[j];
            p[j] = tmp;
        }
        for (int i = 0; i < 512; i++) {
            perm[i] = p[i & 255];
            permMod12[i] = perm[i] % 12;
        }
    }

    /**
     * 3D simplex noise. Returns values roughly in [-1, 1].
     */
    public double eval(double x, double y, double z) {
        double s = (x + y + z) * F3;
        int i = fastFloor(x + s);
        int j = fastFloor(y + s);
        int k = fastFloor(z + s);
        double t = (i + j + k) * G3;

        double X0 = i - t;
        double Y0 = j - t;
        double Z0 = k - t;
        double x0 = x - X0;
        double y0 = y - Y0;
        double z0 = z - Z0;

        int i1, j1, k1, i2, j2, k2;
        if (x0 >= y0) {
            if (y0 >= z0) { i1=1; j1=0; k1=0; i2=1; j2=1; k2=0; }
            else if (x0 >= z0) { i1=1; j1=0; k1=0; i2=1; j2=0; k2=1; }
            else { i1=0; j1=0; k1=1; i2=1; j2=0; k2=1; }
        } else {
            if (y0 < z0) { i1=0; j1=0; k1=1; i2=0; j2=1; k2=1; }
            else if (x0 < z0) { i1=0; j1=1; k1=0; i2=0; j2=1; k2=1; }
            else { i1=0; j1=1; k1=0; i2=1; j2=1; k2=0; }
        }

        double x1 = x0 - i1 + G3;
        double y1 = y0 - j1 + G3;
        double z1 = z0 - k1 + G3;
        double x2 = x0 - i2 + 2.0 * G3;
        double y2 = y0 - j2 + 2.0 * G3;
        double z2 = z0 - k2 + 2.0 * G3;
        double x3 = x0 - 1.0 + 3.0 * G3;
        double y3 = y0 - 1.0 + 3.0 * G3;
        double z3 = z0 - 1.0 + 3.0 * G3;

        int ii = i & 255;
        int jj = j & 255;
        int kk = k & 255;

        double n0 = contribution(x0, y0, z0, ii, jj, kk);
        double n1 = contribution(x1, y1, z1, ii + i1, jj + j1, kk + k1);
        double n2 = contribution(x2, y2, z2, ii + i2, jj + j2, kk + k2);
        double n3 = contribution(x3, y3, z3, ii + 1, jj + 1, kk + 1);

        return 32.0 * (n0 + n1 + n2 + n3);
    }

    private double contribution(double x, double y, double z, int ii, int jj, int kk) {
        double t = 0.6 - x * x - y * y - z * z;
        if (t < 0) return 0;
        t *= t;
        int gi = permMod12[(ii + perm[(jj + perm[kk & 255]) & 255]) & 255];
        return t * t * dot(GRAD3[gi], x, y, z);
    }

    /**
     * Fractal Brownian Motion with multiple octaves of 3D noise.
     */
    public double fractal(double x, double y, double z, int octaves, double persistence, double lacunarity) {
        double total = 0;
        double amplitude = 1;
        double frequency = 1;
        double maxValue = 0;

        for (int i = 0; i < octaves; i++) {
            total += eval(x * frequency, y * frequency, z * frequency) * amplitude;
            maxValue += amplitude;
            amplitude *= persistence;
            frequency *= lacunarity;
        }

        return total / maxValue;
    }

    private static double dot(int[] g, double x, double y, double z) {
        return g[0] * x + g[1] * y + g[2] * z;
    }

    private static int fastFloor(double x) {
        int xi = (int) x;
        return x < xi ? xi - 1 : xi;
    }
}

package io.github.pokemeetup.utils;

import java.util.Random;

public class PerlinNoise {
    private static final int PERMUTATION_SIZE = 256;
    private static final int PERMUTATION_MASK = PERMUTATION_SIZE - 1;
    private final int[] permutation;
    public PerlinNoise(int seed) {
        Random random = new Random(seed);
        this.permutation = new int[PERMUTATION_SIZE * 2];

        // Initialize the permutation array
        int[] p = new int[PERMUTATION_SIZE];
        for (int i = 0; i < PERMUTATION_SIZE; i++) {
            p[i] = i;
        }

        // Fisher-Yates shuffle
        for (int i = PERMUTATION_SIZE - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int temp = p[i];
            p[i] = p[j];
            p[j] = temp;
        }

        // Duplicate the permutation array
        for (int i = 0; i < PERMUTATION_SIZE * 2; i++) {
            permutation[i] = p[i & PERMUTATION_MASK];
        }
    }

    // In PerlinNoise class
    public double noise(double x, double y) {
        // Increase scale to spread out the noise values
        return noise(x, y, 4, 0.5, 0.02); // Decrease scale from 0.1 to 0.02
    }


    public double noise(double x, double y, int octaves, double persistence, double scale) {
        double total = 0;
        double frequency = 1;
        double amplitude = 1;
        double maxValue = 0;

        for (int i = 0; i < octaves; i++) {
            total += generateNoise(x * frequency * scale, y * frequency * scale) * amplitude;
            maxValue += amplitude;
            amplitude *= persistence;
            frequency *= 2;
        }

        return total / maxValue;
    }
private double generateNoise(double x, double y) {
        int X = fastFloor(x) & PERMUTATION_MASK;
        int Y = fastFloor(y) & PERMUTATION_MASK;

        x -= fastFloor(x);
        y -= fastFloor(y);

        double u = fade(x);
        double v = fade(y);

        int aa = permutation[permutation[X] + Y];
        int ab = permutation[permutation[X] + Y + 1];
        int ba = permutation[permutation[X + 1] + Y];
        int bb = permutation[permutation[X + 1] + Y + 1];

        double gradAA = grad(aa, x, y);
        double gradBA = grad(ba, x - 1, y);
        double gradAB = grad(ab, x, y - 1);
        double gradBB = grad(bb, x - 1, y - 1);

        double lerpX1 = lerp(u, gradAA, gradBA);
        double lerpX2 = lerp(u, gradAB, gradBB);

    // The result is already between -1 and 1
        return lerp(v, lerpX1, lerpX2);
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    private static double grad(int hash, double x, double y) {
        int h = hash & 7;
        double u = h < 4 ? x : y;
        double v = h < 4 ? y : x;
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }

    private static int fastFloor(double x) {
        int xi = (int) x;
        return x < xi ? xi - 1 : xi;
    }
}

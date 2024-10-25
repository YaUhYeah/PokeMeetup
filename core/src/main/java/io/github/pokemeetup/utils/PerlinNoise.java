package io.github.pokemeetup.utils;

import java.util.Random;

public class PerlinNoise {
    private static final int PERMUTATION_SIZE = 256;
    private static final int PERMUTATION_MASK = PERMUTATION_SIZE - 1;
    private final int[] permutation;

    // Default values for terrain generation
    private static final int DEFAULT_OCTAVES = 4;
    private static final double DEFAULT_PERSISTENCE = 0.5;
    private static final double DEFAULT_SCALE = 0.1;

    /**
     * Creates a new PerlinNoise generator with the specified seed.
     * @param seed The seed for random number generation
     */
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


    public double noise(double x, double y) {
        // Add octaves for more natural-looking terrain
        return noise(x, y, 4, 0.5, 1.0);
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

        // Normalize the result to ensure it's between 0 and 1
        return (total / maxValue + 1.0) / 2.0;
    }

    private double generateNoise(double x, double y) {
        int X = fastFloor(x) & PERMUTATION_MASK;
        int Y = fastFloor(y) & PERMUTATION_MASK;

        x -= fastFloor(x);
        y -= fastFloor(y);

        double u = fade(x);
        double v = fade(y);

        int A = permutation[X] + Y;
        int B = permutation[X + 1] + Y;

        // Add more variation to the output
        double result = lerp(v,
            lerp(u,
                grad(permutation[A], x, y),
                grad(permutation[B], x - 1, y)
            ),
            lerp(u,
                grad(permutation[A + 1], x, y - 1),
                grad(permutation[B + 1], x - 1, y - 1)
            )
        );

        // Ensure result is normalized between -1 and 1
        return Math.max(-1, Math.min(1, result));
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

    public double[][] generateTerrainMap(int width, int height, double scale) {
        double[][] map = new double[width][height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                map[x][y] = noise(x * scale, y * scale);
            }
        }
        return map;
    }
}

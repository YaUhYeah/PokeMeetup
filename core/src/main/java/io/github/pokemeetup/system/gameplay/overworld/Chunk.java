package io.github.pokemeetup.system.gameplay.overworld;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import io.github.pokemeetup.system.gameplay.overworld.biomes.Biome;
import io.github.pokemeetup.utils.PerlinNoise;

import java.util.List;
import java.util.Map;
import java.util.Random;

public class Chunk {
    public static final int CHUNK_SIZE = 16;
    // Also make the tile type constants public static so they can be accessed from other classes:
    public static final int WATER = 0;
    public static final int GRASS = 1;
    public static final int SAND = 2;
    public static final int ROCK = 3;
    public static final int SNOW = 4;
    public static final int HAUNTED_GRASS = 5;
    public static final int SNOW_TALL_GRASS = 6;
    public static final int HAUNTED_TALL_GRASS = 7;
    public static final int HAUNTED_SHROOM = 8;
    public static final int HAUNTED_SHROOMS = 9;
    public static final int TALL_GRASS = 10;
    private static final PerlinNoise noiseGenerator;

    static {
        noiseGenerator = new PerlinNoise(42);
    }

    private int[][] tileData;
    private Biome biome;
    private Map<Integer, TextureRegion> tileTextures;
    private long worldSeed;
    private int chunkX, chunkY;// Add this method to the Chunk class

    public Chunk(int chunkX, int chunkY, Biome biome, Map<Integer, TextureRegion> tileTextures, long worldSeed) {
        this.chunkX = chunkX;
        this.chunkY = chunkY;
        this.biome = biome;
        this.tileTextures = tileTextures;
        this.tileData = new int[CHUNK_SIZE][CHUNK_SIZE];
        this.worldSeed = worldSeed;
        generateChunkData();
    }

    public Biome getBiome() {
        return biome;
    }

    public int getTileType(int localX, int localY) {
        if (localX < 0 || localX >= CHUNK_SIZE || localY < 0 || localY >= CHUNK_SIZE) {
            return -1; // Invalid tile position
        }
        return tileData[localX][localY];
    }

    public int[][] getTileData() {
        return tileData;
    }

    public Map<Integer, TextureRegion> getTileTextures() {
        return tileTextures;
    }

    public int getChunkX() {
        return chunkX;
    }

    public int getChunkY() {
        return chunkY;
    }

    private void generateChunkData() {
        double noiseScale = 0.1;
        Random random = new Random(worldSeed + chunkX * 4967L + chunkY * 3251L);

        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int y = 0; y < CHUNK_SIZE; y++) {
                // Calculate world coordinates
                int worldX = chunkX * CHUNK_SIZE + x;
                int worldY = chunkY * CHUNK_SIZE + y;

                // Get noise for tile variation
                double noise = noiseGenerator.noise(worldX * noiseScale, worldY * noiseScale);

                // Add variation to make terrain less uniform
                double variationNoise = noiseGenerator.noise(
                    (worldX + 500) * noiseScale * 2,
                    (worldY + 500) * noiseScale * 2
                ) * 0.3;

                noise += variationNoise;

                // Get tile type based on biome and noise
                int tileType = determineTileType(noise, biome, random);
                tileData[x][y] = tileType;
            }
        }
    }

    private int determineTileType(double noiseValue, Biome biome, Random random) {
        Map<Integer, Integer> tileDistribution = biome.getTileDistribution();

        if (tileDistribution.isEmpty()) {
            List<Integer> allowedTiles = biome.getAllowedTileTypes();
            return allowedTiles.isEmpty() ?
                GRASS : allowedTiles.get(random.nextInt(allowedTiles.size()));
        }

        // Calculate total weight
        int totalWeight = tileDistribution.values().stream()
            .mapToInt(Integer::intValue)
            .sum();

        // Add some noise-based variation to the random selection
        int randomValue = random.nextInt(totalWeight);
        randomValue = (int) (randomValue * (1 + noiseValue * 0.2)); // Vary by ±20% based on noise
        randomValue = Math.min(Math.max(0, randomValue), totalWeight - 1);

        // Select tile type based on weights
        int cumulativeWeight = 0;
        for (Map.Entry<Integer, Integer> entry : tileDistribution.entrySet()) {
            cumulativeWeight += entry.getValue();
            if (randomValue < cumulativeWeight) {
                return entry.getKey();
            }
        }

        // Default to first tile type if none selected
        return tileDistribution.keySet().iterator().next();
    }


    private void printChunkDebugInfo() {
        int[] tileCounts = new int[4];
        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int y = 0; y < CHUNK_SIZE; y++) {
                tileCounts[tileData[x][y]]++;
            }
        }
        System.out.println("Chunk tile distribution - Water: " + tileCounts[0] +
            ", Grass: " + tileCounts[1] +
            ", Sand: " + tileCounts[2] +
            ", Rock: " + tileCounts[3]);
    }

    public void render(SpriteBatch batch, int tileSize) {
        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int y = 0; y < CHUNK_SIZE; y++) {
                int worldX = (chunkX * CHUNK_SIZE + x) * tileSize;
                int worldY = (chunkY * CHUNK_SIZE + y) * tileSize;

                int tileType = tileData[x][y];
                TextureRegion tileTexture = tileTextures.get(tileType);

                if (tileTexture == null) {
                    System.err.println("Missing texture for tile type: " + tileType);
                    continue;
                }

                batch.draw(tileTexture, worldX, worldY, tileSize, tileSize);
            }
        }
    }

    public boolean isPassable(int localX, int localY) {
        if (localX < 0 || localX >= CHUNK_SIZE || localY < 0 || localY >= CHUNK_SIZE) {
            return false;
        }
        int tileType = tileData[localX][localY];
        return tileType == GRASS || tileType == SAND || tileType == HAUNTED_GRASS || tileType == TALL_GRASS || tileType == SNOW || tileType == SNOW_TALL_GRASS || tileType == HAUNTED_SHROOM || tileType == HAUNTED_SHROOMS;
    }
}

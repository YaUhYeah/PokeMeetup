package io.github.pokemeetup.system.gameplay.overworld;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.github.pokemeetup.managers.BiomeManager;
import io.github.pokemeetup.managers.BiomeTransitionResult;
import io.github.pokemeetup.system.gameplay.overworld.biomes.Biome;
import io.github.pokemeetup.system.gameplay.overworld.mechanics.MountainGenerator;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.textures.TextureManager;
import io.github.pokemeetup.utils.textures.TileType;

import java.util.*;

import static io.github.pokemeetup.system.gameplay.overworld.World.TILE_SIZE;

public class Chunk {
    public static final int CHUNK_SIZE = 16;
    public static final int WATER = 0;
    public static final int GRASS = 1;
    public static final int SAND = 2;
    public static final int ROCK = 3;
    public static final int SNOW = 4;


    private final BiomeManager biomeManager;
    private int[][] tileData;
    private Biome biome;
    private long worldSeed;
    private int chunkX, chunkY;

    public Chunk(int chunkX, int chunkY, Biome biome, long worldSeed, BiomeManager biomeManager) {
        this.chunkX = chunkX;
        this.chunkY = chunkY;
        this.biome = biome;
        this.tileData = new int[CHUNK_SIZE][CHUNK_SIZE];
        this.worldSeed = worldSeed;
        this.biomeManager = biomeManager;
        generateChunkData();
    }

    public Biome getBiome() {
        return biome;
    }

    public int getTileType(int localX, int localY) {
        if (localX < 0 || localX >= CHUNK_SIZE || localY < 0 || localY >= CHUNK_SIZE) {
            return -1;
        }
        return tileData[localX][localY];
    }

    public int[][] getTileData() {
        return tileData;
    }


    public int getChunkX() {
        return chunkX;
    }

    public int getChunkY() {
        return chunkY;
    }


        private void generateChunkData() {
            Random random = new Random();
            for (int x = 0; x < CHUNK_SIZE; x++) {
                for (int y = 0; y < CHUNK_SIZE; y++) {
                    BiomeTransitionResult biomeTransition = biomeManager.getBiomeAt(
                        (chunkX * CHUNK_SIZE + x) * TILE_SIZE,
                        (chunkY * CHUNK_SIZE + y) * TILE_SIZE
                    );
                    tileData[x][y] = determineTileTypeWithTransition(biomeTransition, random);
                }
            }

            if (shouldGenerateMountains()) {
                MountainGenerator mountainGen = new MountainGenerator(worldSeed + chunkX * 31L + chunkY * 17L);
                mountainGen.generateMountains(this);
            }
        }

        private boolean shouldGenerateMountains() {
            Random random = new Random();
            // Add your logic for when mountains should generate
            // Could be based on biome, noise, or other factors
            return random.nextFloat() < 0.2f; // 20% chance per chunk
        }

    private int determineTileTypeWithTransition(BiomeTransitionResult transition, Random random) {
        Biome primaryBiome = transition.getPrimaryBiome();
        Biome secondaryBiome = transition.getSecondaryBiome();
        float factor = transition.getTransitionFactor();

        // Get tile distributions from both biomes
        Map<Integer, Integer> primaryDist = primaryBiome.getTileDistribution();
        Map<Integer, Integer> secondaryDist = secondaryBiome != null ?
            secondaryBiome.getTileDistribution() : new HashMap<>();

        if (primaryDist == null || primaryDist.isEmpty()) {
            GameLogger.error("Primary biome has no tile distribution");
            return GRASS; // Default fallback
        }
        Map<Integer, Integer> blendedDist = new HashMap<>();
        Set<Integer> allTileTypes = new HashSet<>();
        allTileTypes.addAll(primaryDist.keySet());
        allTileTypes.addAll(secondaryDist.keySet());

        int totalWeight = 0;
        for (int tileType : allTileTypes) {
            int primaryWeight = primaryDist.getOrDefault(tileType, 0);
            int secondaryWeight = secondaryDist.getOrDefault(tileType, 0);

            int blendedWeight = (int) (
                primaryWeight * (1 - factor) +
                    secondaryWeight * factor
            );

            if (blendedWeight > 0) {
                blendedDist.put(tileType, blendedWeight);
                totalWeight += blendedWeight;
            }
        }
        if (totalWeight <= 0) {
            GameLogger.error("No valid tile weights calculated");
            return GRASS;
        }

        // Select tile type based on weights
        int selection = random.nextInt(totalWeight);
        int currentTotal = 0;

        for (Map.Entry<Integer, Integer> entry : blendedDist.entrySet()) {
            currentTotal += entry.getValue();
            if (selection < currentTotal) {
                return entry.getKey();
            }
        }

        // Fallback in case something goes wrong
        GameLogger.error("Failed to select tile type, using default");
        return GRASS;
    }


    public void render(SpriteBatch batch, int tileSize) {

        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int y = 0; y < CHUNK_SIZE; y++) {
                int worldX = (chunkX * CHUNK_SIZE + x) * tileSize;
                int worldY = (chunkY * CHUNK_SIZE + y) * tileSize;

                int tileType = tileData[x][y];
                TextureRegion tileTexture;

                if (TileType.isMountainTile(tileType)) {
                    tileTexture = TextureManager.getTileTexture(tileType);
                } else {
                    tileTexture = TextureManager.getTileTexture(tileType);
                }

                if (tileTexture != null) {
                    batch.draw(tileTexture, worldX, worldY, tileSize, tileSize);
                }
            }
        }
    }


    public boolean isPassable(int localX, int localY) {
        // Ensure local coordinates are within bounds
        localX = (localX + CHUNK_SIZE) % CHUNK_SIZE;
        localY = (localY + CHUNK_SIZE) % CHUNK_SIZE;

        int tileType = tileData[localX][localY];
        return TileType.isPassableTile(tileType);
    }
}

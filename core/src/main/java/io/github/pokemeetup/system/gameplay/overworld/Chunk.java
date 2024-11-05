    package io.github.pokemeetup.system.gameplay.overworld;

    import com.badlogic.gdx.graphics.g2d.SpriteBatch;
    import com.badlogic.gdx.graphics.g2d.TextureRegion;
    import io.github.pokemeetup.managers.BiomeManager;
    import io.github.pokemeetup.managers.BiomeTransitionResult;
    import io.github.pokemeetup.system.gameplay.overworld.biomes.Biome;
    import io.github.pokemeetup.utils.GameLogger;
    import io.github.pokemeetup.utils.PerlinNoise;
    import io.github.pokemeetup.utils.TextureManager;
    import io.github.pokemeetup.utils.TileType;

    import java.util.*;

    import static io.github.pokemeetup.system.gameplay.overworld.World.TILE_SIZE;

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

        private final BiomeManager biomeManager;
        private BiomeTransitionResult biomeTransition;
        private int[][] tileData;
        private Biome biome;
        private long worldSeed;
        private int chunkX, chunkY;// Add this method to the Chunk class

        public Chunk(int chunkX, int chunkY, Biome biome, long worldSeed, BiomeManager biomeManager) {
            this.chunkX = chunkX;
            this.chunkY = chunkY;
            this.biome = biome;
            this.tileData = new int[CHUNK_SIZE][CHUNK_SIZE];
            this.worldSeed = worldSeed;
            this.biomeManager = biomeManager;
            generateChunkData(worldSeed);
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


        public int getChunkX() {
            return chunkX;
        }

        public int getChunkY() {
            return chunkY;
        }


        private void generateChunkData(long worldSeed) {
            double noiseScale = 0.1;
            PerlinNoise noiseGenerator = new PerlinNoise((int) (worldSeed + chunkX * 4967L + chunkY * 3251L));
            Random random = new Random(worldSeed + chunkX * 4967L + chunkY * 3251L);

            for (int x = 0; x < CHUNK_SIZE; x++) {
                for (int y = 0; y < CHUNK_SIZE; y++) {
                    try {
                        // Calculate world coordinates
                        float worldX = (chunkX * CHUNK_SIZE + x) * TILE_SIZE;
                        float worldY = (chunkY * CHUNK_SIZE + y) * TILE_SIZE;

                        // Get biome transition data for this tile using biomeManager
                        BiomeTransitionResult biomeTransition = biomeManager.getBiomeAt(worldX, worldY);

                        // Generate terrain using noise
                        double noise = noiseGenerator.noise(worldX * noiseScale, worldY * noiseScale);

                        // Determine tile type based on noise and biome transition
                        int tileType = determineTileTypeWithTransition(biomeTransition, random);

                        // Safety check the tile type
                        TextureRegion texture = TextureManager.getTileTexture(tileType);
                        if (texture == null) {
                            GameLogger.error("Invalid tile type generated: " + tileType +
                                " at position " + worldX + "," + worldY);
                            tileType = TileType.GRASS; // Fallback to grass
                        }

                        tileData[x][y] = tileType;
                    } catch (Exception e) {
                        tileData[x][y] = TileType.GRASS; // Fallback to grass on error
                    }
                }
            }

            // Optional: Print debug info
            // printChunkDebugInfo();
        }

        private int determineTileTypeWithTransition(BiomeTransitionResult transition, Random random) {
            Biome primaryBiome = transition.getPrimaryBiome();
            Biome secondaryBiome = transition.getSecondaryBiome();
            float factor = transition.getTransitionFactor();

            // Get tile distributions from both biomes
            Map<Integer, Integer> primaryDist = primaryBiome.getTileDistribution();
            Map<Integer, Integer> secondaryDist = secondaryBiome != null ?
                secondaryBiome.getTileDistribution() : new HashMap<>();

            // Validate distributions
            if (primaryDist == null || primaryDist.isEmpty()) {
                GameLogger.error("Primary biome has no tile distribution");
                return GRASS; // Default fallback
            }

            // Blend distributions based on transition factor
            Map<Integer, Integer> blendedDist = new HashMap<>();
            Set<Integer> allTileTypes = new HashSet<>();
            allTileTypes.addAll(primaryDist.keySet());
            allTileTypes.addAll(secondaryDist.keySet());

            // Calculate total weight while building blended distribution
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

            // Safety check - if no valid weights, return default tile
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
            int[] tileCounts = new int[11]; // Updated size to accommodate tile types 0-10
            for (int x = 0; x < CHUNK_SIZE; x++) {
                for (int y = 0; y < CHUNK_SIZE; y++) {
                    int tileType = tileData[x][y];
                    if (tileType >= 0 && tileType < tileCounts.length) {
                        tileCounts[tileType]++;
                    } else {
                        GameLogger.error("Invalid tile type " + tileType + " at (" + x + ", " + y + ")");
                    }
                }
            }
    //                GameLogger.info("Chunk tile distribution - " +
    //                    "Water: " + tileCounts[WATER] + ", " +
    //                    "Grass: " + tileCounts[GRASS] + ", " +
    //                    "Sand: " + tileCounts[SAND] + ", " +
    //                    "Rock: " + tileCounts[ROCK] + ", " +
    //                    "Snow: " + tileCounts[SNOW] + ", " +
    //                    "Haunted Grass: " + tileCounts[HAUNTED_GRASS] + ", " +
    //                    "Snow Tall Grass: " + tileCounts[SNOW_TALL_GRASS] + ", " +
    //                    "Haunted Tall Grass: " + tileCounts[HAUNTED_TALL_GRASS] + ", " +
    //                    "Haunted Shroom: " + tileCounts[HAUNTED_SHROOM] + ", " +
    //                    "Haunted Shrooms: " + tileCounts[HAUNTED_SHROOMS] + ", " +
    //                    "Tall Grass: " + tileCounts[TALL_GRASS]

        }


        public void render(SpriteBatch batch, int tileSize) {
            for (int x = 0; x < CHUNK_SIZE; x++) {
                for (int y = 0; y < CHUNK_SIZE; y++) {
                    int worldX = (chunkX * CHUNK_SIZE + x) * tileSize;
                    int worldY = (chunkY * CHUNK_SIZE + y) * tileSize;

                    int tileType = tileData[x][y];
                    TextureRegion tileTexture = TextureManager.getTileTexture(tileType);

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
            return tileType == TileType.GRASS ||
                tileType == TileType.SAND ||
                tileType == TileType.HAUNTED_GRASS ||
                tileType == TileType.TALL_GRASS ||
                tileType == TileType.SNOW ||
                tileType == TileType.SNOW_TALL_GRASS ||
                tileType == TileType.HAUNTED_SHROOM ||
                tileType == TileType.HAUNTED_SHROOMS || tileType == TileType.HAUNTED_TALL_GRASS ||
                tileType == TileType.FOREST_GRASS ||
                tileType == TileType.FOREST_TALL_GRASS || tileType == TileType.DESERT_SAND || tileType == TileType.RAIN_FOREST_TALL_GRASS || tileType == TileType.RAIN_FOREST_GRASS;
        }
    }

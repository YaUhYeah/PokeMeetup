    package io.github.pokemeetup.system.gameplay.overworld;

    import io.github.pokemeetup.managers.BiomeManager;
    import io.github.pokemeetup.managers.BiomeTransitionResult;
    import io.github.pokemeetup.system.gameplay.overworld.biomes.Biome;
    import io.github.pokemeetup.utils.GameLogger;
    import io.github.pokemeetup.utils.textures.TileType;

    import java.util.*;

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
            this.biome = biome; // Ensure biome is assigned here
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

        public void setTileData(int[][] tileData) {
            this.tileData = tileData;
        }

        @SuppressWarnings("DefaultLocale")
        private void generateChunkData() {
            Random random = new Random();

            for (int x = 0; x < CHUNK_SIZE; x++) {
                for (int y = 0; y < CHUNK_SIZE; y++) {
                    // Convert local coordinates to world coordinates properly
                    float worldX = (chunkX * CHUNK_SIZE + x) * World.TILE_SIZE;
                    float worldY = (chunkY * CHUNK_SIZE + y) * World.TILE_SIZE;

                    BiomeTransitionResult biomeTransition = biomeManager.getBiomeAt(worldX, worldY);

                    // Debug output for first tile of each chunk
                    if (x == 0 && y == 0) {
                        GameLogger.info(String.format(
                            "Generating chunk(%d,%d) at world(%f,%f) - Primary: %s Secondary: %s",
                            chunkX, chunkY, worldX, worldY,
                            biomeTransition.getPrimaryBiome().getName(),
                            biomeTransition.getSecondaryBiome() != null ?
                                biomeTransition.getSecondaryBiome().getName() : "none"
                        ));
                        // Set the chunk's biome to the primary biome
                        this.biome = biomeTransition.getPrimaryBiome();
                    }

                    int tileType = determineTileTypeWithTransition(biomeTransition, random);
                    tileData[x][y] = tileType;
                }
            }
        }

        private int determineTileTypeWithTransition(BiomeTransitionResult transition, Random random) {
            Biome primaryBiome = transition.getPrimaryBiome();
            Biome secondaryBiome = transition.getSecondaryBiome();
            float factor = transition.getTransitionFactor();

            // Validate tile distributions
            Map<Integer, Integer> primaryDist = primaryBiome.getTileDistribution();
            if (primaryDist == null || primaryDist.isEmpty()) {
                GameLogger.error("Invalid tile distribution for biome: " + primaryBiome.getName());
                return 1; // Default to grass
            }

            // If no transition or very small factor, use primary biome directly
            if (secondaryBiome == null || factor < 0.1f) {
                return selectTileFromDistribution(primaryDist, random);
            }

            // Create blended distribution
            Map<Integer, Integer> blendedDist = new HashMap<>();
            Set<Integer> allTileTypes = new HashSet<>();
            allTileTypes.addAll(primaryDist.keySet());
            allTileTypes.addAll(secondaryBiome.getTileDistribution().keySet());

            // Calculate total weight for normalization
            int totalWeight = 0;
            for (int tileType : allTileTypes) {
                float primaryWeight = primaryDist.getOrDefault(tileType, 0) * (1 - factor);
                float secondaryWeight = secondaryBiome.getTileDistribution().getOrDefault(tileType, 0) * factor;

                int blendedWeight = Math.round(primaryWeight + secondaryWeight);
                if (blendedWeight > 0) {
                    blendedDist.put(tileType, blendedWeight);
                    totalWeight += blendedWeight;
                }
            }

            // Normalize weights
            if (totalWeight != 100) {
                for (Map.Entry<Integer, Integer> entry : blendedDist.entrySet()) {
                    entry.setValue(Math.round(entry.getValue() * 100f / totalWeight));
                }
            }

            return selectTileFromDistribution(blendedDist, random);
        }

        private int selectTileFromDistribution(Map<Integer, Integer> distribution, Random random) {
            int roll = random.nextInt(100);
            int currentTotal = 0;

            for (Map.Entry<Integer, Integer> entry : distribution.entrySet()) {
                currentTotal += entry.getValue();
                if (roll < currentTotal) {
                    int selectedTile = entry.getKey();
                    // Validate the selected tile type
                    if (!TileType.getTileTypeNames().containsKey(selectedTile)) {
                        GameLogger.error("Invalid tile type selected: " + selectedTile);
                        return 1; // Default to grass
                    }
                    return selectedTile;
                }
            }

            // Fallback to most common tile
            return distribution.keySet().iterator().next();
        }

        public boolean isPassable(int localX, int localY) {
            // Ensure local coordinates are within bounds
            localX = (localX + CHUNK_SIZE) % CHUNK_SIZE;
            localY = (localY + CHUNK_SIZE) % CHUNK_SIZE;

            int tileType = tileData[localX][localY];
            return TileType.isPassableTile(tileType);
        }
    }

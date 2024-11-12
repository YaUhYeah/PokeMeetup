package io.github.pokemeetup.system.gameplay.overworld.mechanics;

import io.github.pokemeetup.system.gameplay.overworld.Chunk;
import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;
import io.github.pokemeetup.utils.PerlinNoise;
import io.github.pokemeetup.utils.TileType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
public class MountainGenerator {
    private static final float BASE_NOISE_SCALE = 0.03f;
    private static final float DETAIL_NOISE_SCALE = 0.06f;
    private static final int MIN_MOUNTAIN_SIZE = 4;
    private static final int MAX_MOUNTAIN_SIZE = 12;

    // Biome-specific mountain spawn chances
    private static final float BIG_MOUNTAINS_CHANCE = 0.85f;
    private static final float SNOW_BIOME_CHANCE = 0.25f;
    private static final float DEFAULT_MOUNTAIN_CHANCE = 0.12f;

    private final PerlinNoise heightNoise;
    private final PerlinNoise detailNoise;
    private final Random random;

    public MountainGenerator(long seed) {
        this.heightNoise = new PerlinNoise((int) seed);
        this.detailNoise = new PerlinNoise((int) (seed + 123));
        this.random = new Random(seed);
    }

    public void generateMountains(Chunk chunk) {
        float mountainChance = getMountainChanceForBiome(chunk.getBiome().getType());

        double worldX = chunk.getChunkX() * Chunk.CHUNK_SIZE * BASE_NOISE_SCALE;
        double worldY = chunk.getChunkY() * Chunk.CHUNK_SIZE * BASE_NOISE_SCALE;

        double noiseValue = (heightNoise.noise(worldX, worldY) + 1) / 2;
        if (noiseValue * random.nextFloat() < mountainChance) {
            generateMountainFormation(chunk);
        }
    }

    private float getMountainChanceForBiome(BiomeType biomeType) {
        if (biomeType == BiomeType.BIG_MOUNTAINS) {
            return BIG_MOUNTAINS_CHANCE;
        } else if (biomeType == BiomeType.SNOW) {
            return SNOW_BIOME_CHANCE;
        } else if (biomeType == BiomeType.DESERT) {
            return DEFAULT_MOUNTAIN_CHANCE * 0.5f;
        } else if (biomeType == BiomeType.RAIN_FOREST) {
            return DEFAULT_MOUNTAIN_CHANCE * 0.3f;
        } else {
            return DEFAULT_MOUNTAIN_CHANCE;
        }
    }

    private void generateMountainFormation(Chunk chunk) {
        int[][] heightMap = new int[Chunk.CHUNK_SIZE][Chunk.CHUNK_SIZE];
        BiomeType biomeType = chunk.getBiome().getType();

        // Generate base mountain shape
        for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
            for (int y = 0; y < Chunk.CHUNK_SIZE; y++) {
                double wx = (chunk.getChunkX() * Chunk.CHUNK_SIZE + x) * BASE_NOISE_SCALE;
                double wy = (chunk.getChunkY() * Chunk.CHUNK_SIZE + y) * BASE_NOISE_SCALE;

                double height = heightNoise.noise(wx, wy);
                height += detailNoise.noise(wx * 2, wy * 2) * 0.5;
                height = adjustHeightForBiome(height, biomeType);

                if (height > 0.3) {
                    heightMap[x][y] = (int)((height - 0.3) * 8);
                } else {
                    heightMap[x][y] = -1;
                }
            }
        }

        smoothHeightMap(heightMap);
        validateMountainFormations(heightMap);
        applyMountainTiles(chunk, heightMap, biomeType);
    }

    private void validateMountainFormations(int[][] heightMap) {
        List<Point> toRemove = new ArrayList<>();
        boolean[][] visited = new boolean[Chunk.CHUNK_SIZE][Chunk.CHUNK_SIZE];

        // Find and validate mountain formations
        for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
            for (int y = 0; y < Chunk.CHUNK_SIZE; y++) {
                if (heightMap[x][y] >= 0 && !visited[x][y]) {
                    List<Point> formation = new ArrayList<>();
                    floodFill(heightMap, x, y, visited, formation);

                    // If formation is too small, mark for removal
                    if (formation.size() < MIN_MOUNTAIN_SIZE) {
                        toRemove.addAll(formation);
                    }
                }
            }
        }

        // Remove invalid formations
        for (Point p : toRemove) {
            heightMap[p.x][p.y] = -1;
        }

        // Optional: Ensure gradual elevation changes
        smoothElevations(heightMap);
    }
    // Simple Point class for tracking coordinates
    private static class Point {
        final int x, y;

        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    private void floodFill(int[][] heightMap, int x, int y, boolean[][] visited, List<Point> formation) {
        // Check bounds and validity
        if (x < 0 || x >= Chunk.CHUNK_SIZE || y < 0 || y >= Chunk.CHUNK_SIZE ||
            visited[x][y] || heightMap[x][y] < 0) {
            return;
        }

        // Mark as visited and add to formation
        visited[x][y] = true;
        formation.add(new Point(x, y));

        // Check all adjacent tiles
        floodFill(heightMap, x + 1, y, visited, formation);  // Right
        floodFill(heightMap, x - 1, y, visited, formation);  // Left
        floodFill(heightMap, x, y + 1, visited, formation);  // Up
        floodFill(heightMap, x, y - 1, visited, formation);  // Down
    }

    private void smoothElevations(int[][] heightMap) {
        int[][] tempMap = new int[Chunk.CHUNK_SIZE][Chunk.CHUNK_SIZE];

        // Copy original heightMap
        for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
            for (int y = 0; y < Chunk.CHUNK_SIZE; y++) {
                tempMap[x][y] = heightMap[x][y];
            }
        }

        // Smooth out harsh elevation changes
        for (int x = 1; x < Chunk.CHUNK_SIZE - 1; x++) {
            for (int y = 1; y < Chunk.CHUNK_SIZE - 1; y++) {
                if (heightMap[x][y] >= 0) {
                    // Check for harsh elevation differences with neighbors
                    int maxDiff = 2; // Maximum allowed elevation difference
                    boolean needsSmoothing = false;

                    // Check adjacent tiles
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = -1; dy <= 1; dy++) {
                            if (dx == 0 && dy == 0) continue;

                            int nx = x + dx;
                            int ny = y + dy;

                            if (heightMap[nx][ny] >= 0) {
                                int diff = Math.abs(heightMap[x][y] - heightMap[nx][ny]);
                                if (diff > maxDiff) {
                                    needsSmoothing = true;
                                    break;
                                }
                            }
                        }
                        if (needsSmoothing) break;
                    }

                    // Smooth out elevation if needed
                    if (needsSmoothing) {
                        int sum = heightMap[x][y];
                        int count = 1;

                        // Average with valid neighbors
                        for (int dx = -1; dx <= 1; dx++) {
                            for (int dy = -1; dy <= 1; dy++) {
                                if (dx == 0 && dy == 0) continue;

                                int nx = x + dx;
                                int ny = y + dy;

                                if (heightMap[nx][ny] >= 0) {
                                    sum += heightMap[nx][ny];
                                    count++;
                                }
                            }
                        }
                        tempMap[x][y] = Math.round((float) sum / count);
                    }
                }
            }
        }

        // Copy back smoothed heights
        for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
            for (int y = 0; y < Chunk.CHUNK_SIZE; y++) {
                heightMap[x][y] = tempMap[x][y];
            }
        }
    }

    private double adjustHeightForBiome(double height, BiomeType biomeType) {
        if (biomeType == BiomeType.BIG_MOUNTAINS) {
            return height * 1.5;
        } else if (biomeType == BiomeType.SNOW) {
            return height * 1.2;
        } else if (biomeType == BiomeType.DESERT) {
            return height * 0.8;
        } else if (biomeType == BiomeType.HAUNTED) {
            return height * 0.9;
        }
        return height;
    }

    private void applyMountainTiles(Chunk chunk, int[][] heightMap, BiomeType biomeType) {
        int[][] tileData = chunk.getTileData();

        for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
            for (int y = 0; y < Chunk.CHUNK_SIZE; y++) {
                if (heightMap[x][y] >= 0) {
                    int mountainTile = determineMountainTile(x, y, heightMap, biomeType);
                    tileData[x][y] = mountainTile;
                }
            }
        }

        if (shouldAddPath(biomeType)) {
            addMountainPath(chunk, heightMap);
        }
    }

    private boolean shouldAddPath(BiomeType biomeType) {
        float pathProbability;
        if (biomeType == BiomeType.BIG_MOUNTAINS) {
            pathProbability = 0.8f;
        } else if (biomeType == BiomeType.SNOW) {
            pathProbability = 0.4f;
        } else if (biomeType == BiomeType.DESERT) {
            pathProbability = 0.2f;
        } else {
            pathProbability = 0.3f;
        }
        return random.nextFloat() < pathProbability;
    }

    private int determineMountainTile(int x, int y, int[][] heightMap, BiomeType biomeType) {
        int baseTile = getBaseMountainTile(x, y, heightMap);

        if (biomeType == BiomeType.SNOW) {
            return adjustForSnowBiome(baseTile);
        } else if (biomeType == BiomeType.DESERT) {
            return adjustForDesertBiome(baseTile);
        } else if (biomeType == BiomeType.HAUNTED) {
            return adjustForHauntedBiome(baseTile);
        }
        return baseTile;
    }

    private int getBaseMountainTile(int x, int y, int[][] heightMap) {
        int currentHeight = heightMap[x][y];

        int leftHeight = x > 0 ? heightMap[x-1][y] : -1;
        int rightHeight = x < heightMap.length-1 ? heightMap[x+1][y] : -1;
        int topHeight = y < heightMap[0].length-1 ? heightMap[x][y+1] : -1;
        int bottomHeight = y > 0 ? heightMap[x][y-1] : -1;

        if (currentHeight == 0) {
            return TileType.MOUNTAIN_BASE;
        }
        if (bottomHeight < currentHeight) {
            return TileType.MOUNTAIN_WALL;
        }
        if (leftHeight < currentHeight) {
            return TileType.MOUNTAIN_EDGE_LEFT;
        }
        if (rightHeight < currentHeight) {
            return TileType.MOUNTAIN_EDGE_RIGHT;
        }
        if (topHeight < currentHeight) {
            return TileType.MOUNTAIN_EDGE_TOP;
        }

        return TileType.MOUNTAIN_PATH;
    }

    private int adjustForSnowBiome(int baseTile) {
        if (baseTile == TileType.MOUNTAIN_BASE) {
            return TileType.MOUNTAIN_SNOW_BASE;
        }
        return baseTile;
    }

    private int adjustForDesertBiome(int baseTile) {
        return baseTile; // Add desert-specific adjustments if needed
    }

    private int adjustForHauntedBiome(int baseTile) {
        return baseTile; // Add haunted-specific adjustments if needed
    }

    private void smoothHeightMap(int[][] heightMap) {
        int[][] smoothed = new int[Chunk.CHUNK_SIZE][Chunk.CHUNK_SIZE];

        for (int x = 1; x < Chunk.CHUNK_SIZE - 1; x++) {
            for (int y = 1; y < Chunk.CHUNK_SIZE - 1; y++) {
                if (heightMap[x][y] == -1) continue;

                int neighbors = 0;
                int totalHeight = 0;

                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        if (heightMap[x + dx][y + dy] != -1) {
                            neighbors++;
                            totalHeight += heightMap[x + dx][y + dy];
                        }
                    }
                }

                if (neighbors < 4) {
                    smoothed[x][y] = -1;
                } else if (neighbors > 0) {
                    smoothed[x][y] = Math.round((float)totalHeight / neighbors);
                }
            }
        }

        for (int x = 1; x < Chunk.CHUNK_SIZE - 1; x++) {
            for (int y = 1; y < Chunk.CHUNK_SIZE - 1; y++) {
                heightMap[x][y] = smoothed[x][y];
            }
        }
    }

    private void addMountainPath(Chunk chunk, int[][] heightMap) {
        // Find suitable entrance point
        int entranceX = random.nextInt(Chunk.CHUNK_SIZE - 4) + 2;

        // Create path upward
        for (int y = 0; y < Chunk.CHUNK_SIZE; y++) {
            if (heightMap[entranceX][y] >= 0) {
                chunk.getTileData()[entranceX][y] = TileType.MOUNTAIN_PATH;
                if (y == 0) {
                    // Add stairs at entrance
                    chunk.getTileData()[entranceX][y] = TileType.MOUNTAIN_STAIRS;
                    if (entranceX > 0) {
                        chunk.getTileData()[entranceX - 1][y] = TileType.MOUNTAIN_STAIRS_LEFT;
                    }
                    if (entranceX < Chunk.CHUNK_SIZE - 1) {
                        chunk.getTileData()[entranceX + 1][y] = TileType.MOUNTAIN_STAIRS_RIGHT;
                    }
                }
            }
        }
    }
}

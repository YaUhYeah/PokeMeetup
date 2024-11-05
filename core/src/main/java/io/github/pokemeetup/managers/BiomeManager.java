package io.github.pokemeetup.managers;

import io.github.pokemeetup.system.gameplay.overworld.WorldObject;
import io.github.pokemeetup.system.gameplay.overworld.biomes.Biome;
import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.PerlinNoise;
import io.github.pokemeetup.utils.TextureManager;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class BiomeManager {
    private static final float BIOME_SCALE = 0.005f;       // Base biome variation
    private static final float WARP_SCALE = 0.008f;        // Terrain warping
    private static final float DETAIL_SCALE = 0.025f;      // Local variations
    private static final float TRANSITION_WIDTH = 0.15f;   // Width of transition zones
    private Map<BiomeType, Biome> biomes;
    private static final int BIOME_OCTAVES = 4;
    private static final double BIOME_PERSISTENCE = 0.5;
    private PerlinNoise temperatureNoise;
    private PerlinNoise moistureNoise;
    final PerlinNoise detailNoise;
    private final PerlinNoise warpNoise;
    private long baseSeed;

    private final double temperatureBias;
    private final double moistureBias;

    public BiomeManager(long baseSeed) {
        this.baseSeed = baseSeed;
        this.biomes = new HashMap<>();
        this.temperatureNoise = new PerlinNoise((int) (baseSeed));
        this.moistureNoise = new PerlinNoise((int) ((baseSeed >> 32)));
        this.detailNoise = new PerlinNoise((int) ((baseSeed + 1) & 0xFFFFFFFFL));
        this.warpNoise = new PerlinNoise((int) ((baseSeed + 2) & 0xFFFFFFFFL));
        Random seedRandom = new Random(baseSeed);
        this.temperatureBias = seedRandom.nextDouble() * 0.2 - 0.1; // [-0.1, 0.1]
        this.moistureBias = seedRandom.nextDouble() * 0.2 - 0.1;    // [-0.1, 0.1]
        loadBiomesFromJson();
    }

    public enum TransitionDirection {
        NORTH(0, 1),
        SOUTH(0, -1),
        EAST(1, 0),
        WEST(-1, 0);

        public final int dx, dy;

        TransitionDirection(int dx, int dy) {
            this.dx = dx;
            this.dy = dy;
        }
    }

    // Custom clamp method replacing MathUtils.clamp
    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    // Add this utility method for smooth interpolation
    private float smoothstep(float edge0, float edge1, float x) {
        x = clamp((x - edge0) / (edge1 - edge0), 0.0f, 1.0f);
        return x * x * (3 - 2 * x);
    }

    // Add this utility method to calculate directional influence
    private float getDirectionalInfluence(float x, float y, int dx, int dy) {
        float dirX = dx * x;
        float dirY = dy * y;
        return (dirX + dirY) * 0.5f;
    }

    // Add this method to get biome with transitions
    public BiomeTransitionResult getBiomeAt(float worldX, float worldY) {
        // Apply terrain warping for more natural biome shapes
        double warpX = warpNoise.noise(
            worldX * WARP_SCALE,
            worldY * WARP_SCALE
        ) * 20.0;

        double warpY = warpNoise.noise(
            worldY * WARP_SCALE,
            worldX * WARP_SCALE
        ) * 20.0;

        // Sample coordinates with warping
        double sampleX = worldX + warpX;
        double sampleY = worldY + warpY;

        // Generate base temperature and moisture with octaves for better variation
        double temperature = generateOctaveNoise(temperatureNoise, sampleX, sampleY, BIOME_SCALE) + temperatureBias;
        double moisture = generateOctaveNoise(moistureNoise, sampleX, sampleY, BIOME_SCALE) + moistureBias;

        // Add detail variation
        double detail = detailNoise.noise(sampleX * DETAIL_SCALE, sampleY * DETAIL_SCALE) * 0.15;
        temperature += detail;
        moisture += detail;

        // Determine biomes and transition
        BiomeType primaryType = determineBiomeType(temperature, moisture);
        BiomeType secondaryType = getTransitionBiome(temperature, moisture, primaryType);
        float transitionFactor = calculateTransitionFactor(temperature, moisture);

        return new BiomeTransitionResult(
            getBiome(primaryType),
            getBiome(secondaryType),
            transitionFactor
        );
    }

    private double generateOctaveNoise(PerlinNoise noise, double x, double y, double scale) {
        double value = 0;
        double amplitude = 1.0;
        double frequency = 1.0;
        double maxValue = 0;

        for (int i = 0; i < BIOME_OCTAVES; i++) {
            value += amplitude * noise.noise(x * scale * frequency, y * scale * frequency);
            maxValue += amplitude;
            amplitude *= BIOME_PERSISTENCE;
            frequency *= 2.0;
        }

        return value / maxValue;
    }

    public BiomeTransitionResult getBiomeWithDirection(float worldX, float worldY, TransitionDirection direction) {
        // Calculate base coordinates with directional influence
        float dirInfluence = getDirectionalInfluence(worldX, worldY, direction.dx, direction.dy);

        // Get warped coordinates
        double warpX = warpNoise.noise(worldX * WARP_SCALE, worldY * WARP_SCALE) * 20.0;
        double warpY = warpNoise.noise(worldY * WARP_SCALE, worldX * WARP_SCALE) * 20.0;

        // Apply coordinate warping
        double sampleX = worldX + warpX + (direction.dx * dirInfluence);
        double sampleY = worldY + warpY + (direction.dy * dirInfluence);

        // Get biome parameters with detail noise
        double detail = detailNoise.noise(sampleX * DETAIL_SCALE, sampleY * DETAIL_SCALE) * 0.3;
        double temperature = temperatureNoise.noise(sampleX * BIOME_SCALE, sampleY * BIOME_SCALE) + (detail * 0.2);
        double moisture = moistureNoise.noise(sampleX * BIOME_SCALE, sampleY * BIOME_SCALE) + (detail * 0.2);

        // Get primary and transition biomes
        BiomeType primaryType = determineBiomeType(temperature, moisture);
        BiomeType secondaryType = getTransitionBiome(temperature, moisture, primaryType);

        // Calculate transition factor with directional influence
        float transitionBase = calculateTransitionFactor(temperature, moisture);
        float dirFactor = clamp((float) smoothstep(0.3f, 0.7f, Math.abs(dirInfluence)), 0f, 1f);
        float finalTransition = transitionBase * dirFactor;

        return new BiomeTransitionResult(
            getBiome(primaryType),
            getBiome(secondaryType),
            finalTransition
        );
    }

    private BiomeType determineBiomeType(double temperature, double moisture) {
        // Normalize values to [0,1] range
        temperature = (temperature + 1.0) / 2.0;
        moisture = (moisture + 1.0) / 2.0;

        // Clear biome boundaries for more distinct regions
        if (temperature < 0.2) {
            return BiomeType.SNOW;
        }
        if (temperature < 0.4) {
            if (moisture > 0.6) {
                return BiomeType.HAUNTED;
            }
        }
        if (temperature < 0.7) {
            if (moisture > 0.5) {
                return BiomeType.FOREST;
            }
        }
        if (moisture < 0.3 && temperature > 0.7) {
            return BiomeType.DESERT;
        }

        return BiomeType.PLAINS;
    }

    // Determine secondary biome for smooth transitions
    private BiomeType getTransitionBiome(double temperature, double moisture, BiomeType primary) {
        temperature = (temperature + 1.0) / 2.0;
        moisture = (moisture + 1.0) / 2.0;

        // Enhanced transition logic
        switch (primary) {
            case SNOW:
                return moisture > 0.5 ? BiomeType.HAUNTED : BiomeType.PLAINS;
            case HAUNTED:
                return temperature < 0.3 ? BiomeType.SNOW : BiomeType.FOREST;
            case FOREST:
                return moisture < 0.4 ? BiomeType.PLAINS : BiomeType.HAUNTED;
            case DESERT:
                return temperature < 0.6 ? BiomeType.PLAINS : BiomeType.FOREST;
            case PLAINS:
            default:
                if (temperature < 0.3) return BiomeType.SNOW;
                if (moisture > 0.6) return BiomeType.FOREST;
                if (temperature > 0.7) return BiomeType.DESERT;
                return BiomeType.PLAINS;
        }
    }

    private float calculateTransitionFactor(double temperature, double moisture) {
        // Normalize values
        temperature = (temperature + 1.0) / 2.0;
        moisture = (moisture + 1.0) / 2.0;

        // Calculate distances to biome boundaries
        double snowDist = Math.abs(temperature - 0.2);
        double forestDist = Math.abs(moisture - 0.5);
        double hauntedDist = Math.min(
            Math.abs(temperature - 0.4),
            Math.abs(moisture - 0.6)
        );
        double desertDist = Math.min(
            Math.abs(temperature - 0.7),
            Math.abs(moisture - 0.3)
        );

        // Find closest boundary
        double minDist = Math.min(
            Math.min(snowDist, forestDist),
            Math.min(hauntedDist, desertDist)
        );

        // Calculate transition factor
        float factor = (float) (1.0 - (minDist / TRANSITION_WIDTH));
        return clamp(factor, 0f, 1f);
    }

    private void validateBiomes() {
        for (Map.Entry<BiomeType, Biome> entry : biomes.entrySet()) {
            BiomeType type = entry.getKey();
            Biome biome = entry.getValue();

            // Check tileDistribution
            if (biome.getTileDistribution().isEmpty()) {
                GameLogger.error("Biome " + biome.getName() + " of type " + type + " has an empty tileDistribution!");
            }

            // Check tileTextures via TextureManager
            // Assuming TextureManager is adjusted to not use LibGDX
            for (int tileType : biome.getTileDistribution().keySet()) {
                if (TextureManager.getTileTexture(tileType) == null) {
                    GameLogger.error("Biome " + biome.getName() + " requires texture for tile type " + tileType + ", but it's missing!");
                }
            }
        }
    }

    private void loadBiomesFromJson() {
        try {
            Gson gson = new Gson();
            String jsonContent = new String(Files.readAllBytes(Paths.get("Data/biomes.json")));
            List<BiomeData> biomeDataList = gson.fromJson(jsonContent, new TypeToken<List<BiomeData>>(){}.getType());

            for (BiomeData data : biomeDataList) {
                BiomeType biomeType = BiomeType.valueOf(data.type.toUpperCase());
                Biome biome = new Biome(data.name, biomeType);

                // Set allowedTileTypes
                if (data.allowedTileTypes != null) {
                    biome.setAllowedTileTypes(data.allowedTileTypes);
                } else {
                    GameLogger.error("Biome " + data.name + " has no allowedTileTypes defined!");
                }

                // Assign tileDistribution
                if (data.tileDistribution != null) {
                    for (Map.Entry<String, Double> entry : data.tileDistribution.entrySet()) {
                        try {
                            int tileType = Integer.parseInt(entry.getKey());
                            int weight = entry.getValue().intValue(); // Convert Double to int
                            biome.getTileDistribution().put(tileType, weight);
                            GameLogger.info("Biome " + data.name + " added tile type " + tileType + " with weight " + weight);
                        } catch (NumberFormatException e) {
                            GameLogger.error("Invalid tile type in tileDistribution: " + entry.getKey());
                        }
                    }
                }

                // Assign spawnChances
                if (data.spawnChances != null) {
                    Map<Integer, Float> tileSpawnChances = new HashMap<>();
                    for (Map.Entry<String, Double> entry : data.spawnChances.entrySet()) {
                        try {
                            int tileType = Integer.parseInt(entry.getKey());
                            float chance = entry.getValue().floatValue(); // Convert Double to float
                            tileSpawnChances.put(tileType, chance);
                            GameLogger.info("Biome " + data.name + " added spawn chance " + chance + " for tile type " + tileType);
                        } catch (NumberFormatException e) {
                            GameLogger.error("Invalid tile type in spawnChances: " + entry.getKey());
                        }
                    }
                    biome.setTileSpawnChances(tileSpawnChances);
                }

                // Load transitionTilesConfig
                if (data.transitionTiles != null) {
                    Map<BiomeType, Map<Integer, Integer>> transitionTilesConfig = new HashMap<>();
                    for (Map.Entry<String, HashMap<String, Number>> transitionEntry : data.transitionTiles.entrySet()) {
                        BiomeType neighborBiomeType = BiomeType.valueOf(transitionEntry.getKey().toUpperCase());
                        HashMap<String, Number> tileMappings = transitionEntry.getValue();
                        Map<Integer, Integer> tileMappingsInt = new HashMap<>();
                        for (Map.Entry<String, Number> tileEntry : tileMappings.entrySet()) {
                            int fromTileType = Integer.parseInt(tileEntry.getKey());
                            int toTileType = tileEntry.getValue().intValue(); // Convert Number to int
                            tileMappingsInt.put(fromTileType, toTileType);
                        }
                        transitionTilesConfig.put(neighborBiomeType, tileMappingsInt);
                    }
                    biome.setTransitionTilesConfig(transitionTilesConfig);
                } else {
                    GameLogger.info("Biome " + data.name + " has no transitionTiles defined.");
                }

                // Assign spawnableObjects
                if (data.spawnableObjects != null) {
                    List<WorldObject.ObjectType> spawnableObjects = new ArrayList<>();
                    for (String objName : data.spawnableObjects) {
                        try {
                            WorldObject.ObjectType objType = WorldObject.ObjectType.valueOf(objName.toUpperCase());
                            spawnableObjects.add(objType);
                        } catch (IllegalArgumentException e) {
                            GameLogger.error("Invalid spawnable object type in biome " + data.name + ": " + objName);
                        }
                    }
                    biome.setSpawnableObjects(spawnableObjects);
                } else {
                    GameLogger.info("Biome " + data.name + " has no spawnableObjects defined.");
                }

                // Texture loading is not required on the server
                // biome.loadTileTextures();
                // biome.loadTransitionTiles();

                biomes.put(biomeType, biome);
            }
        } catch (IOException e) {
            GameLogger.error("Error loading biomes: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            GameLogger.error("Error parsing biomes JSON: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public Biome getBiome(BiomeType type) {
        return biomes.get(type);
    }

    private static class BiomeData {
        public String name;
        public String type;
        public List<Integer> allowedTileTypes;
        public HashMap<String, Double> tileDistribution; // Use HashMap<String, Double>
        public List<String> spawnableObjects;
        public HashMap<String, Double> spawnChances;     // Use HashMap<String, Double>
        public HashMap<String, HashMap<String, Number>> transitionTiles; // Use Number instead of Integer
    }
}

package io.github.pokemeetup.managers;

import com.badlogic.gdx.Gdx;
import io.github.pokemeetup.FileSystemDelegate;
import io.github.pokemeetup.system.gameplay.overworld.WorldObject;
import io.github.pokemeetup.system.gameplay.overworld.biomes.Biome;
import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.PerlinNoise;
import io.github.pokemeetup.utils.TextureManager;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.github.pokemeetup.utils.storage.GameFileSystem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class BiomeManager {
    private static final float BIOME_SCALE = 0.008f;       // Increased for smaller biomes
    private static final float WARP_SCALE = 0.002f;        // Adjusted for better variation
    private static final float DETAIL_SCALE = 0.01f;       // Increased detail variation
    private static final float MOUNTAIN_THRESHOLD = 0.85f;  // More mountains
    private static final float TRANSITION_WIDTH = 0.2f;     // Wider transitions
    private static final double BIOME_PERSISTENCE = 0.45f;  // More varied patterns

    private static final int BIOME_OCTAVES = 4;            // Simpler patterns
    private static final String[] BIOME_FILE_PATHS = {
        "server/data/biomes.json",
        "data/biomes.json",
        "assets/data/biomes.json",
        "config/biomes.json"
    };
    private static final float MOUNTAIN_BIAS_SCALE = 0.002f; // New scale for mountain bias
    private static final float PLAINS_DOMINANCE = 0.6f;    // Plains biome dominance factor
    private static final float MOUNTAIN_SCALE = 0.001f;  // Scale for mountain noise
    private static boolean DEBUG_ENABLED = false;
    final PerlinNoise detailNoise;
    private final PerlinNoise mountainNoise;  // Make sure this is declared
    private final PerlinNoise warpNoise;
    private final double temperatureBias;
    private final double moistureBias;
    private final PerlinNoise mountainBiasNoise;
    private final int DEBUG_SAMPLE_RATE = 1000; // Log every 1000th tile
    private Map<BiomeType, Biome> biomes;
    private PerlinNoise temperatureNoise;
    private PerlinNoise moistureNoise;
    private long baseSeed;
    private int debugCounter = 0;

    public BiomeManager(long baseSeed) {
        this.baseSeed = baseSeed;
        this.biomes = new HashMap<>();
        this.mountainBiasNoise = new PerlinNoise((int) (baseSeed + 3));
        this.temperatureNoise = new PerlinNoise((int) (baseSeed));
        this.moistureNoise = new PerlinNoise((int) ((baseSeed >> 32)));
        this.detailNoise = new PerlinNoise((int) ((baseSeed + 1) & 0xFFFFFFFFL));
        this.warpNoise = new PerlinNoise((int) ((baseSeed + 2) & 0xFFFFFFFFL));
        this.mountainNoise = new PerlinNoise((int) (baseSeed + 2));
        Random seedRandom = new Random(baseSeed);
        this.temperatureBias = seedRandom.nextDouble() * 0.2 - 0.1; // [-0.1, 0.1]
        this.moistureBias = seedRandom.nextDouble() * 0.2 - 0.1;    // [-0.1, 0.1]
        loadBiomesFromJson();
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

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

    public BiomeTransitionResult getBiomeAt(float worldX, float worldY) {
        // Basic noise without warping first to check distribution
        double rawTemperature = temperatureNoise.noise(worldX * BIOME_SCALE, worldY * BIOME_SCALE);
        double rawMoisture = moistureNoise.noise(worldX * BIOME_SCALE, worldY * BIOME_SCALE);
        // Generate mountain value first
        double mountainBase = mountainNoise.noise(worldX * MOUNTAIN_SCALE, worldY * MOUNTAIN_SCALE);
        double mountainDetail = mountainNoise.noise(worldX * (MOUNTAIN_SCALE * 2), worldY * (MOUNTAIN_SCALE * 2)) * 0.5;

        double mountainValue = (mountainBase + mountainDetail + 1.0) / 2.0;  // Normalize to 0-1
        // Apply warping
        double warpX = warpNoise.noise(worldX * WARP_SCALE, worldY * WARP_SCALE) * 30;
        double warpY = warpNoise.noise((worldX + 31.5f) * WARP_SCALE, (worldY + 31.5f) * WARP_SCALE) * 30;

        // Get warped values
        double temperature = temperatureNoise.noise((worldX + warpX) * BIOME_SCALE, (worldY + warpY) * BIOME_SCALE);
        double moisture = moistureNoise.noise((worldX + warpX * 0.8) * BIOME_SCALE, (worldY + warpY * 0.8) * BIOME_SCALE);

        // Force wider distribution by applying exponential scaling
        temperature = Math.pow((temperature + 1.0) / 2.0, 0.8) + temperatureBias;
        moisture = Math.pow((moisture + 1.0) / 2.0, 0.8) + moistureBias;

        // Add variation
        double detailVar = detailNoise.noise(worldX * DETAIL_SCALE, worldY * DETAIL_SCALE) * 0.15;
        temperature += detailVar;
        moisture += detailVar;

        // Ensure full range utilization
        temperature = Math.max(0.1, Math.min(0.9, temperature));
        moisture = Math.max(0.1, Math.min(0.9, moisture));

        // Debug logging
        if (DEBUG_ENABLED && debugCounter++ % DEBUG_SAMPLE_RATE == 0) {
            GameLogger.info(String.format("Biome Debug - Pos(%f,%f) Raw(T:%.2f,M:%.2f) Final(T:%.2f,M:%.2f)",
                worldX, worldY, rawTemperature, rawMoisture, temperature, moisture));
        }

        BiomeType primaryType = determineBiomeType(temperature, moisture, mountainValue);

        // Log biome distribution
        if (DEBUG_ENABLED && debugCounter % DEBUG_SAMPLE_RATE == 0) {
            GameLogger.info("Selected Biome: " + primaryType);
        }

        BiomeType secondaryType = getTransitionBiome(temperature, moisture, primaryType);
        float transitionFactor = calculateTransitionFactor(temperature, moisture);

        return new BiomeTransitionResult(getBiome(primaryType), getBiome(secondaryType), transitionFactor);
    }

    private float calculateTransitionFactor(double temperature, double moisture) {
        // Add detail variation to transition calculation
        double detailVar = detailNoise.noise(temperature * 10, moisture * 10) * 0.15;

        double tempFactor = Math.abs(temperature - 0.5) * 2;
        double moistFactor = Math.abs(moisture - 0.5) * 2;

        double transitionStrength = Math.max(tempFactor, moistFactor) + Math.abs(detailVar);
        return (float) Math.max(0, Math.min(1, transitionStrength / TRANSITION_WIDTH));
    }



    private BiomeType determineBiomeType(double temperature, double moisture, double mountainValue) {
        // Normalize temperature and moisture to 0-1 range for easier thresholding
        temperature = (temperature + 1.0) / 2.0;
        moisture = (moisture + 1.0) / 2.0;

        // Mountain check first
        if (mountainValue > MOUNTAIN_THRESHOLD) {
            if (temperature < 0.3) {
                return BiomeType.SNOW;
            }
            return BiomeType.BIG_MOUNTAINS;
        }

        // Temperature-based biome selection with more granular thresholds
        if (temperature < 0.2) {
            // Cold biomes
            return BiomeType.SNOW;
        } else if (temperature < 0.4) {
            // Cool biomes
            return moisture < 0.5 ? BiomeType.PLAINS : BiomeType.FOREST;
        } else if (temperature < 0.6) {
            // Moderate biomes
            if (moisture < 0.3) {
                return BiomeType.PLAINS;
            } else if (moisture < 0.6) {
                return BiomeType.FOREST;
            } else {
                return BiomeType.HAUNTED;
            }
        } else if (temperature < 0.8) {
            // Warm biomes
            if (moisture < 0.2) {
                return BiomeType.DESERT;
            } else if (moisture < 0.5) {
                return BiomeType.PLAINS;
            } else if (moisture < 0.7) {
                return BiomeType.FOREST;
            } else {
                return BiomeType.RAIN_FOREST;
            }
        } else {
            // Hot biomes
            if (moisture < 0.3) {
                return BiomeType.DESERT;
            } else if (moisture < 0.6) {
                return BiomeType.HAUNTED;
            } else {
                return BiomeType.RAIN_FOREST;
            }
        }
    }

    // Enhanced transition biome selection
    private BiomeType getTransitionBiome(double temperature, double moisture, BiomeType primary) {
        temperature = (temperature + 1.0) / 2.0;
        moisture = (moisture + 1.0) / 2.0;

        switch (primary) {
            case SNOW:
                return moisture > 0.5 ? BiomeType.FOREST : BiomeType.PLAINS;
            case RAIN_FOREST:
                return temperature < 0.7 ? BiomeType.FOREST : BiomeType.HAUNTED;
            case HAUNTED:
                return moisture < 0.5 ? BiomeType.FOREST : BiomeType.RAIN_FOREST;
            case FOREST:
                return temperature > 0.7 ? BiomeType.RAIN_FOREST :
                    (moisture < 0.4 ? BiomeType.PLAINS : BiomeType.HAUNTED);
            case DESERT:
                return moisture > 0.3 ? BiomeType.PLAINS :
                    (temperature < 0.7 ? BiomeType.PLAINS : BiomeType.HAUNTED);
            case BIG_MOUNTAINS:
                return temperature < 0.3 ? BiomeType.SNOW :
                    (moisture > 0.6 ? BiomeType.FOREST : BiomeType.PLAINS);
            case PLAINS:
            default:
                if (temperature < 0.3) return BiomeType.SNOW;
                if (moisture > 0.7) return BiomeType.FOREST;
                if (temperature > 0.8 && moisture < 0.3) return BiomeType.DESERT;
                return BiomeType.PLAINS;
        }
    }

    public Biome getBiome(BiomeType type) {
        Biome biome = biomes.get(type);
        if (biome == null) {
            GameLogger.error("Missing biome type: " + type + ", falling back to PLAINS");
            return biomes.get(BiomeType.PLAINS);
        }
        return biome;
    }




    private void loadBiomesFromJson() {
        String jsonContent = null;
        FileSystemDelegate delegate = GameFileSystem.getInstance().getDelegate();
        try {
            jsonContent = delegate.readString("Data/biomes.json");
            GameLogger.info("Successfully loaded biomes via delegate");
        } catch (IOException e) {
            GameLogger.error("Could not load biomes.json via delegate: " + e.getMessage());
        }

        if (jsonContent == null) {
            GameLogger.error("Could not load biomes from any location, using defaults");
            initializeDefaultBiomes();
            return;
        }
        boolean isServer = Gdx.files == null; // Check if we're running on server

        if (isServer) {
            for (String path : BIOME_FILE_PATHS) {
                try {
                    jsonContent = new String(Files.readAllBytes(Paths.get(path)));
                    GameLogger.info("Server: Successfully loaded biomes from " + path);
                    break;
                } catch (IOException e) {
                    GameLogger.info("Server: Could not load biomes from " + path);
                }
            }
        } else {
            for (String path : BIOME_FILE_PATHS) {
                try {
                    com.badlogic.gdx.files.FileHandle fileHandle = Gdx.files.internal(path);
                    if (fileHandle.exists()) {
                        jsonContent = fileHandle.readString();
                        GameLogger.info("Client: Successfully loaded biomes from " + path);
                        break;
                    }
                } catch (Exception e) {
                    // Continue trying other paths
                    GameLogger.info("Client: Could not load biomes from " + path);
                }
            }
        }

        if (jsonContent == null) {
            GameLogger.error("Could not load biomes from any location, using defaults");
            initializeDefaultBiomes();
            return;
        }
        try {
            Gson gson = new Gson();
            List<BiomeData> biomeDataList = gson.fromJson(jsonContent,
                new TypeToken<List<BiomeData>>() {
                }.getType());

            for (BiomeData data : biomeDataList) {
                try {
                    BiomeType biomeType = BiomeType.valueOf(data.type.toUpperCase());
                    Biome biome = new Biome(data.name, biomeType);

                    // Process basic biome data
                    if (data.allowedTileTypes != null) {
                        biome.setAllowedTileTypes(data.allowedTileTypes);
                    }

                    // Process tile distribution
                    if (data.tileDistribution != null) {
                        data.tileDistribution.forEach((key, value) -> {
                            try {
                                int tileType = Integer.parseInt(key);
                                biome.getTileDistribution().put(tileType, value.intValue());
                            } catch (NumberFormatException e) {
                                GameLogger.error("Invalid tile type: " + key + " for biome " + data.name);
                            }
                        });
                    }

                    // Process spawnable objects and their chances
                    if (data.spawnableObjects != null) {
                        List<WorldObject.ObjectType> spawnableObjects = new ArrayList<>();
                        for (String objType : data.spawnableObjects) {
                            try {
                                WorldObject.ObjectType objectType = WorldObject.ObjectType.valueOf(objType);
                                spawnableObjects.add(objectType);

                                // Set spawn chance if available
                                if (data.spawnChances != null && data.spawnChances.containsKey(objType)) {
                                    float chance = data.spawnChances.get(objType).floatValue();
                                    biome.setObjectSpawnChance(objectType, chance);
                                }
                            } catch (IllegalArgumentException e) {
                                GameLogger.error("Invalid object type: " + objType + " for biome " + data.name);
                            }
                        }
                        biome.setSpawnableObjects(spawnableObjects);
                    }

                    biomes.put(biomeType, biome);
                    GameLogger.info("Successfully loaded biome: " + data.name + " with " +
                        (data.spawnableObjects != null ? data.spawnableObjects.size() : 0) +
                        " spawnable objects");

                } catch (Exception e) {
                    GameLogger.error("Error processing biome " + data.name + ": " + e.getMessage());
                }
            }

        } catch (Exception e) {
            GameLogger.error("Error parsing biomes JSON, using defaults: " + e.getMessage());
            initializeDefaultBiomes();
        }
    }

    // Add server-specific biome validation
    private void validateBiomes() {
        if (biomes.isEmpty()) {
            GameLogger.error("No biomes loaded! Initializing defaults...");
            initializeDefaultBiomes();
            return;
        }

        // Ensure all required biome types exist
        for (BiomeType type : BiomeType.values()) {
            if (!biomes.containsKey(type)) {
                GameLogger.error("Missing required biome type: " + type + ". Creating default.");
                Biome defaultBiome = createDefaultBiome(type);
                biomes.put(type, defaultBiome);
            }
        }

        // Validate each biome's data
        for (Map.Entry<BiomeType, Biome> entry : biomes.entrySet()) {
            Biome biome = entry.getValue();

            if (biome.getTileDistribution().isEmpty()) {
                GameLogger.error("Biome " + biome.getName() + " has no tile distribution. Adding defaults.");
                biome.getTileDistribution().put(1, 70);
                biome.getTileDistribution().put(2, 20);
                biome.getTileDistribution().put(3, 10);
            }

            if (biome.getAllowedTileTypes().isEmpty()) {
                GameLogger.error("Biome " + biome.getName() + " has no allowed tile types. Adding defaults.");
                biome.setAllowedTileTypes(Arrays.asList(1, 2, 3));
            }
        }
    }

    private Biome createDefaultBiome(BiomeType type) {
        Biome biome = new Biome(type.name(), type);
        biome.setAllowedTileTypes(Arrays.asList(1, 2, 3));

        // Set different default distributions based on biome type
        switch (type) {
            case DESERT:
                biome.getTileDistribution().put(1, 85);
                biome.getTileDistribution().put(2, 10);
                biome.getTileDistribution().put(3, 5);
                break;
            case FOREST:
                biome.getTileDistribution().put(1, 60);
                biome.getTileDistribution().put(2, 30);
                biome.getTileDistribution().put(3, 10);
                break;
            case SNOW:
                biome.getTileDistribution().put(1, 75);
                biome.getTileDistribution().put(2, 20);
                biome.getTileDistribution().put(3, 5);
                break;
            case HAUNTED:
                biome.getTileDistribution().put(1, 65);
                biome.getTileDistribution().put(2, 25);
                biome.getTileDistribution().put(3, 10);
                break;
            default:
                biome.getTileDistribution().put(1, 70);
                biome.getTileDistribution().put(2, 20);
                biome.getTileDistribution().put(3, 10);
                break;
        }
        return biome;
    }

    private void initializeDefaultBiomes() {
        // Ensure we have basic biomes even if loading fails
        if (!biomes.containsKey(BiomeType.PLAINS)) {
            Biome plains = new Biome("Plains", BiomeType.PLAINS);
            plains.setAllowedTileTypes(Arrays.asList(1, 2, 3));
            plains.getTileDistribution().put(1, 70);
            plains.getTileDistribution().put(2, 20);
            plains.getTileDistribution().put(3, 10);
            biomes.put(BiomeType.PLAINS, plains);
        }

        if (!biomes.containsKey(BiomeType.FOREST)) {
            Biome forest = new Biome("Forest", BiomeType.FOREST);
            forest.setAllowedTileTypes(Arrays.asList(1, 2, 3));
            forest.getTileDistribution().put(1, 60);
            forest.getTileDistribution().put(2, 30);
            forest.getTileDistribution().put(3, 10);
            biomes.put(BiomeType.FOREST, forest);
        }

        // Add other default biomes
        BiomeType[] requiredTypes = {BiomeType.SNOW, BiomeType.DESERT, BiomeType.HAUNTED};
        for (BiomeType type : requiredTypes) {
            if (!biomes.containsKey(type)) {
                Biome biome = new Biome(type.name(), type);
                biome.setAllowedTileTypes(Arrays.asList(1, 2, 3));
                biome.getTileDistribution().put(1, 80);
                biome.getTileDistribution().put(2, 15);
                biome.getTileDistribution().put(3, 5);
                biomes.put(type, biome);
            }
        }

        GameLogger.info("Default biomes initialized");
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

    private static class BiomeData {
        String name;
        String type;
        List<Integer> allowedTileTypes;
        Map<String, Double> tileDistribution;
        List<String> spawnableObjects;
        Map<String, Double> spawnChances;
        Map<String, Map<String, Number>> transitionTiles;
    }
}

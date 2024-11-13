package io.github.pokemeetup.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.pokemeetup.FileSystemDelegate;
import io.github.pokemeetup.audio.AudioManager;
import io.github.pokemeetup.system.gameplay.overworld.WorldObject;
import io.github.pokemeetup.system.gameplay.overworld.biomes.Biome;
import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.PerlinNoise;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.github.pokemeetup.utils.storage.GameFileSystem;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class BiomeManager {
    private static final String[] BIOME_FILE_PATHS = {
        "server/data/biomes.json",
        "data/biomes.json",
        "assets/data/biomes.json",
        "config/biomes.json"
    };
    private static final float MOUNTAIN_BIAS_SCALE = 0.002f; // New scale for mountain bias
    private static final float PLAINS_DOMINANCE = 0.6f;    // Plains biome dominance factor
    private static final float MOUNTAIN_SCALE = 0.001f;  // Scale for mountain noise
    private static final double BLEND_RADIUS = 8.0; // Blocks to blend over
    private static final int NOISE_OCTAVES = 4;
    private static final double EDGE_ROUGHNESS = 0.4;
    private static final Map<String, Integer> TILE_TYPE_CACHE = new HashMap<>();
    private static float BIOME_SCALE = 0.008f;       // Increased for smaller biomes
    private static float WARP_SCALE = 0.002f;        // Adjusted for better variation
    private static float DETAIL_SCALE = 0.01f;       // Increased detail variation
    private static float MOUNTAIN_THRESHOLD = 0.85f;  // More mountains
    private static float TRANSITION_WIDTH = 0.2f;     // Wider transitions
    private static double BIOME_PERSISTENCE = 0.45f;  // More varied patterns
    private static int BIOME_OCTAVES = 4;            // Simpler patterns
    private static boolean DEBUG_ENABLED = false;
    final PerlinNoise detailNoise;
    private final PerlinNoise mountainNoise;  // Make sure this is declared
    private final PerlinNoise warpNoise;
    private final double temperatureBias;
    private final double moistureBias;
    private final PerlinNoise mountainBiasNoise;
    private final int DEBUG_SAMPLE_RATE = 1000; // Log every 1000th tile
    private final Map<BiomeType, BiomeEnvironmentEffect> biomeEnvironmentEffects = new EnumMap<>(BiomeType.class);
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

    public BiomeTransitionResult calculateBiomeTransition(float worldX, float worldY) {
        // Generate base noise values with multiple octaves for natural variation
        double temperature = generateOctaveNoise(temperatureNoise, worldX, worldY, BIOME_SCALE);
        double moisture = generateOctaveNoise(moistureNoise, worldX, worldY, BIOME_SCALE);

        // Add warping for more natural boundaries
        double warpX = warpNoise.noise(worldX * WARP_SCALE, worldY * WARP_SCALE) * 30;
        double warpY = warpNoise.noise((worldX + 31.5f) * WARP_SCALE, (worldY + 31.5f) * WARP_SCALE) * 30;

        // Apply warping to coordinates
        double warpedX = worldX + warpX;
        double warpedY = worldY + warpY;

        // Calculate mountain influence with ridged noise
        double mountainBase = Math.abs(mountainNoise.noise(worldX * MOUNTAIN_SCALE, worldY * MOUNTAIN_SCALE));
        double mountainDetail = Math.abs(mountainNoise.noise(worldX * (MOUNTAIN_SCALE * 2), worldY * (MOUNTAIN_SCALE * 2))) * 0.5;
        double mountainValue = (mountainBase + mountainDetail) * 1.5; // Amplify mountain effect

        // Add edge variation using detail noise
        double edgeNoise = detailNoise.noise(worldX * DETAIL_SCALE, worldY * DETAIL_SCALE) * EDGE_ROUGHNESS;

        // Calculate biome weights for nearby points
        Map<BiomeType, Double> biomeWeights = new HashMap<>();
        for (double dx = -BLEND_RADIUS; dx <= BLEND_RADIUS; dx += 2.0) {
            for (double dy = -BLEND_RADIUS; dy <= BLEND_RADIUS; dy += 2.0) {
                // Calculate distance-based weight
                double distance = Math.sqrt(dx * dx + dy * dy) / BLEND_RADIUS;
                if (distance > 1.0) continue;

                double weight = 1.0 - smoothstep(0.0, 1.0, distance);

                // Sample biome at this point
                double sampleX = warpedX + dx;
                double sampleY = warpedY + dy;

                // Get temperature and moisture for this sample point
                double sampleTemp = temperature + detailNoise.noise(sampleX * DETAIL_SCALE, sampleY * DETAIL_SCALE) * 0.15;
                double sampleMoist = moisture + detailNoise.noise((sampleX + 31.5) * DETAIL_SCALE, (sampleY + 31.5) * DETAIL_SCALE) * 0.15;

                // Determine biome at this point
                BiomeType biomeType = determineBiomeType(sampleTemp, sampleMoist, mountainValue + edgeNoise);

                // Add weighted contribution
                biomeWeights.merge(biomeType, weight, Double::sum);
            }
        }

        // Normalize weights and find primary/secondary biomes
        BiomeType primaryType = null;
        BiomeType secondaryType = null;
        double primaryWeight = 0.0;
        double secondaryWeight = 0.0;

        double totalWeight = biomeWeights.values().stream().mapToDouble(Double::doubleValue).sum();
        for (Map.Entry<BiomeType, Double> entry : biomeWeights.entrySet()) {
            double normalizedWeight = entry.getValue() / totalWeight;
            if (normalizedWeight > primaryWeight) {
                secondaryType = primaryType;
                secondaryWeight = primaryWeight;
                primaryType = entry.getKey();
                primaryWeight = normalizedWeight;
            } else if (normalizedWeight > secondaryWeight) {
                secondaryType = entry.getKey();
                secondaryWeight = normalizedWeight;
            }
        }

        // Calculate transition factor
        float transitionFactor = (float) (secondaryWeight / (primaryWeight + secondaryWeight));

        // Add noise to transition factor for more natural blending
        transitionFactor += (float) (detailNoise.noise(worldX * DETAIL_SCALE * 2, worldY * DETAIL_SCALE * 2) * 0.1);
        transitionFactor = Math.max(0.0f, Math.min(1.0f, transitionFactor));

        return new BiomeTransitionResult(
            getBiome(primaryType),
            getBiome(secondaryType),
            transitionFactor,
            (float) mountainValue,
            (float) temperature,
            (float) moisture
        );
    }

    private double smoothstep(double edge0, double edge1, double x) {
        x = Math.max(0, Math.min(1, (x - edge0) / (edge1 - edge0)));
        return x * x * x * (x * (x * 6 - 15) + 10);
    }

    private double generateOctaveNoise(PerlinNoise noise, double x, double y, double scale) {
        double value = 0;
        double amplitude = 1.0;
        double frequency = 1.0;
        double maxValue = 0;

        for (int i = 0; i < NOISE_OCTAVES; i++) {
            value += amplitude * noise.noise(
                x * scale * frequency + i * 31.5,
                y * scale * frequency + i * 31.5
            );
            maxValue += amplitude;
            amplitude *= 0.5;
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
        double normalizedTemp = (temperature + 1.0) / 2.0;
        double normalizedMoisture = (moisture + 1.0) / 2.0;

        BiomeType secondaryType = getTransitionBiome(temperature, moisture, primaryType);
        float transitionFactor = calculateTransitionFactor(temperature, moisture);
        return new BiomeTransitionResult(
            getBiome(primaryType),                  // Primary biome
            getBiome(secondaryType),                // Secondary biome
            transitionFactor,                       // Transition blend factor
            (float) mountainValue,                   // Mountain influence
            (float) normalizedTemp,                  // Normalized temperature
            (float) normalizedMoisture              // Normalized moisture
        );
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
        // Normalize temperature and moisture to 0-1 range
        temperature = (temperature + 1.0) / 2.0;
        moisture = (moisture + 1.0) / 2.0;

        // Mountain checks first
        if (mountainValue > MOUNTAIN_THRESHOLD) {
            if (temperature > 0.8) {
                return BiomeType.VOLCANO;
            }
            if (temperature < 0.3) {
                return BiomeType.SNOW;
            }
            return BiomeType.BIG_MOUNTAINS;
        }

        // Very Cold (0.0 - 0.2)
        if (temperature < 0.2) {
            return BiomeType.SNOW;
        }

        // Cold (0.2 - 0.4)
        if (temperature < 0.4) {
            if (moisture < 0.3) {
                return BiomeType.PLAINS;
            } else if (moisture < 0.6) {
                return BiomeType.FOREST;
            } else {
                return BiomeType.SWAMP;
            }
        }

        // Moderate (0.4 - 0.6)
        if (temperature < 0.6) {
            if (moisture < 0.2) {
                return BiomeType.PLAINS;
            } else if (moisture < 0.4) {
                return BiomeType.FOREST;
            } else if (moisture < 0.6) {
                return BiomeType.CHERRY_BLOSSOM;
            } else if (moisture < 0.8) {
                return BiomeType.SWAMP;
            } else {
                return BiomeType.HAUNTED;
            }
        }

        // Warm (0.6 - 0.8)
        if (temperature < 0.8) {
            if (moisture < 0.2) {
                return BiomeType.DESERT;
            } else if (moisture < 0.4) {
                return BiomeType.PLAINS;
            } else if (moisture < 0.6) {
                return BiomeType.SAFARI;
            } else if (moisture < 0.8) {
                return BiomeType.FOREST;
            } else {
                return BiomeType.SWAMP;
            }
        }

        // Hot (0.8 - 1.0)
        if (moisture < 0.2) {
            return BiomeType.DESERT;
        } else if (moisture < 0.4) {
            return BiomeType.SAFARI;
        } else if (moisture < 0.6) {
            return BiomeType.VOLCANO;
        } else if (moisture < 0.8) {
            return BiomeType.HAUNTED;
        } else {
            return BiomeType.SWAMP;
        }
    }

    // Update transition logic
    private BiomeType getTransitionBiome(double temperature, double moisture, BiomeType primary) {
        temperature = (temperature + 1.0) / 2.0;
        moisture = (moisture + 1.0) / 2.0;

        switch (primary) {
            case SNOW:
                return moisture > 0.5 ? BiomeType.FOREST : BiomeType.PLAINS;

            case CHERRY_BLOSSOM:
                return temperature < 0.5 ? BiomeType.FOREST : BiomeType.PLAINS;

            case HAUNTED:
                if (moisture < 0.4) return BiomeType.FOREST;
                return temperature > 0.7 ? BiomeType.SWAMP : BiomeType.CHERRY_BLOSSOM;

            case FOREST:
                if (temperature < 0.4) return BiomeType.SNOW;
                if (moisture > 0.7) return BiomeType.SWAMP;
                return BiomeType.PLAINS;

            case DESERT:
                if (moisture > 0.3) return BiomeType.SAFARI;
                return temperature < 0.7 ? BiomeType.PLAINS : BiomeType.VOLCANO;

            case SAFARI:
                if (moisture < 0.3) return BiomeType.DESERT;
                if (moisture > 0.7) return BiomeType.FOREST;
                return BiomeType.PLAINS;

            case SWAMP:
                if (moisture < 0.5) return BiomeType.FOREST;
                return temperature > 0.6 ? BiomeType.HAUNTED : BiomeType.FOREST;

            case VOLCANO:
                if (temperature < 0.7) return BiomeType.DESERT;
                return moisture > 0.5 ? BiomeType.HAUNTED : BiomeType.SAFARI;

            case BIG_MOUNTAINS:
                if (temperature < 0.3) return BiomeType.SNOW;
                if (temperature > 0.8) return BiomeType.VOLCANO;
                return moisture > 0.6 ? BiomeType.FOREST : BiomeType.PLAINS;

            case PLAINS:
            default:
                if (temperature < 0.3) return BiomeType.SNOW;
                if (moisture > 0.7) return BiomeType.FOREST;
                if (temperature > 0.8 && moisture < 0.3) return BiomeType.DESERT;
                return BiomeType.FOREST;
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

    private void updateGlobalSettings(JsonBiomeConfig.GlobalSettings settings) {
        if (settings != null) {
            TRANSITION_WIDTH = settings.transitionWidth;
            MOUNTAIN_THRESHOLD = settings.mountainThreshold;
            BIOME_SCALE = settings.biomeScale;
            DETAIL_SCALE = settings.detailScale;
            WARP_SCALE = settings.warpScale;
            BIOME_OCTAVES = settings.biomeOctaves;
            BIOME_PERSISTENCE = settings.biomePersistence;

            // Process environment effects if they exist
            if (settings.environmentEffects != null) {
                loadEnvironmentEffects(settings.environmentEffects);
            }
        }
    }

    private List<Integer> convertTileTypes(List<String> tileTypeNames) {
        List<Integer> tileTypes = new ArrayList<>();

        for (String tileName : tileTypeNames) {
            // First check the cache
            Integer tileType = TILE_TYPE_CACHE.get(tileName);

            if (tileType == null) {
                // If not in cache, convert and cache it
                tileType = getTileTypeFromString(tileName);
                if (tileType != -1) {
                    TILE_TYPE_CACHE.put(tileName, tileType);
                }
            }

            if (tileType != -1) {
                tileTypes.add(tileType);
            } else {
                GameLogger.error("Invalid tile type name: " + tileName);
            }
        }

        if (tileTypes.isEmpty()) {
            GameLogger.error("No valid tile types converted from names");
        }

        return tileTypes;
    }

    private void loadEnvironmentEffects(Map<String, JsonBiomeConfig.EnvironmentEffectData> effectsData) {
        if (effectsData == null) {
            GameLogger.info("No environment effects data provided");
            return;
        }

        for (Map.Entry<String, JsonBiomeConfig.EnvironmentEffectData> entry : effectsData.entrySet()) {
            try {
                BiomeType biomeType = BiomeType.valueOf(entry.getKey().toUpperCase());
                JsonBiomeConfig.EnvironmentEffectData effectData = entry.getValue();

                if (effectData == null) {
                    GameLogger.info("Null effect data for biome: " + biomeType);
                    continue;
                }

                // Validate effect data
                if (effectData.particleEffect == null || effectData.ambientSound == null) {
                    GameLogger.info("Missing required effect data for biome: " + biomeType);
                    continue;
                }

                // Create and validate the effect
                BiomeEnvironmentEffect effect = validateAndCreateEffect(biomeType, effectData);
                if (effect != null) {
                    biomeEnvironmentEffects.put(biomeType, effect);
                    GameLogger.info("Loaded environment effect for " + biomeType +
                        " - Particle: " + effectData.particleEffect +
                        " Sound: " + effectData.ambientSound +
                        " Chance: " + effectData.weatherChance);
                }

            } catch (IllegalArgumentException e) {
                GameLogger.error("Invalid biome type for environment effect: " + entry.getKey());
            } catch (Exception e) {
                GameLogger.error("Error loading environment effect for " + entry.getKey() +
                    ": " + e.getMessage());
            }
        }

        // Log summary
        GameLogger.info("Loaded " + biomeEnvironmentEffects.size() + " environment effects");
    }

    private BiomeEnvironmentEffect validateAndCreateEffect(
        BiomeType biomeType,
        JsonBiomeConfig.EnvironmentEffectData data) {

        // Validate particle effect name
        if (!isValidParticleEffect(data.particleEffect)) {
            GameLogger.info("Invalid particle effect name for " + biomeType + ": " + data.particleEffect);
            return null;
        }

        // Validate ambient sound name
        if (!isValidAmbientSound(data.ambientSound)) {
            GameLogger.info("Invalid ambient sound name for " + biomeType + ": " + data.ambientSound);
            return null;
        }

        // Validate weather chance
        float weatherChance = Math.max(0.0f, Math.min(1.0f, data.weatherChance));
        if (weatherChance != data.weatherChance) {
            GameLogger.error("Weather chance for " + biomeType + " clamped to valid range [0,1]");
        }

        return new BiomeEnvironmentEffect(
            data.particleEffect,
            data.ambientSound,
            weatherChance
        );
    }

    private boolean isValidParticleEffect(String effectName) {
        // Add your particle effect validation logic here
        // For example, check against a list of valid effect names
        return effectName != null && !effectName.isEmpty() && (
            effectName.equals("SNOW_FALL") ||
                effectName.equals("RAIN_FALL") ||
                effectName.equals("SAND_STORM") ||
                effectName.equals("FOG") ||
                effectName.equals("FIREFLY") ||
                effectName.equals("LEAVES") ||
                effectName.equals("THUNDER")
        );
    }

    public void applyEffect(AudioManager audioManager, float intensity) {
//        if (ambientSound != null) {
//            audioManager.updateAmbientSound(ambientSound, intensity);
//        }
    }

    private boolean isValidAmbientSound(String soundName) {
        return soundName != null && !soundName.isEmpty() && (
            soundName.equals("WINTER_WIND") ||
                soundName.equals("RAIN_AMBIENT") ||
                soundName.equals("DESERT_WIND") ||
                soundName.equals("SPOOKY_AMBIENT") ||
                soundName.equals("SWAMP_AMBIENT") ||
                soundName.equals("FOREST_AMBIENT") ||
                soundName.equals("THUNDER_AMBIENT")
        );
    }

    private String loadJsonContent() throws IOException {
        String jsonContent = null;
        FileSystemDelegate delegate = GameFileSystem.getInstance().getDelegate();

        try {
            jsonContent = delegate.readString("Data/biomes.json");
            GameLogger.info("Successfully loaded biomes via delegate");
            return jsonContent;
        } catch (IOException e) {
            GameLogger.error("Could not load biomes via delegate, trying alternate paths");
        }

        // Try alternate paths if delegate fails
        boolean isServer = Gdx.files == null;
        if (isServer) {
            for (String path : BIOME_FILE_PATHS) {
                try {
                    jsonContent = new String(Files.readAllBytes(Paths.get(path)));
                    GameLogger.info("Server: Successfully loaded biomes from " + path);
                    return jsonContent;
                } catch (IOException e) {
                    GameLogger.error("Server: Could not load biomes from " + path);
                }
            }
        } else {
            for (String path : BIOME_FILE_PATHS) {
                try {
                    FileHandle fileHandle = Gdx.files.internal(path);
                    if (fileHandle.exists()) {
                        jsonContent = fileHandle.readString();
                        GameLogger.info("Client: Successfully loaded biomes from " + path);
                        return jsonContent;
                    }
                } catch (Exception e) {
                    GameLogger.error("Client: Could not load biomes from " + path);
                }
            }
        }

        if (jsonContent == null) {
            throw new IOException("Could not load biomes.json from any location");
        }

        return jsonContent;
    }

    private void loadEnvironmentEffectsForBiome(Biome biome,
                                                Map<String, JsonBiomeConfig.EnvironmentEffectData> effects) {

        JsonBiomeConfig.EnvironmentEffectData effectData =
            effects.get(biome.getType().name());

        if (effectData != null) {
            BiomeEnvironmentEffect effect = new BiomeEnvironmentEffect(
                effectData.particleEffect,
                effectData.ambientSound,
                effectData.weatherChance
            );
            biome.setEnvironmentEffect(effect);
        }
    }


    private void processBiomeData(BiomeData data) {
        try {
            BiomeType biomeType = BiomeType.valueOf(data.type);
            Biome biome = new Biome(data.name, biomeType);

            // Process allowed tile types
            if (data.allowedTileTypes != null && !data.allowedTileTypes.isEmpty()) {
                List<Integer> tileTypes = new ArrayList<>();
                for (String tileType : data.allowedTileTypes) {
                    int tileId = getTileTypeFromString(tileType);
                    if (tileId != -1) {
                        tileTypes.add(tileId);
                        GameLogger.info("Added tile type: " + tileType + " -> " + tileId + " for biome " + data.name);
                    } else {
                        GameLogger.error("Failed to convert tile type: " + tileType + " for biome " + data.name);
                    }
                }
                if (!tileTypes.isEmpty()) {
                    biome.setAllowedTileTypes(tileTypes);
                } else {
                    GameLogger.error("No valid tile types found for biome " + data.name);
                }
            } else {
                GameLogger.error("No allowed tile types specified for biome " + data.name);
            }

            // Process tile distribution with better error handling
            if (data.tileDistribution != null) {
                for (Map.Entry<String, Double> entry : data.tileDistribution.entrySet()) {
                    int tileId = getTileTypeFromString(entry.getKey());
                    if (tileId != -1) {
                        biome.getTileDistribution().put(tileId, entry.getValue().intValue());
                        GameLogger.info("Added tile distribution: " + entry.getKey() + " -> " + tileId +
                            " (" + entry.getValue() + "%) for biome " + data.name);
                    }
                }
            }

            // Validate the biome data
            if (biome.getAllowedTileTypes().isEmpty()) {
                GameLogger.error("No valid tile types loaded for biome " + data.name + ", using defaults");
                biome.setAllowedTileTypes(Arrays.asList(1, 2, 3));
            }

            biomes.put(biomeType, biome);
            GameLogger.info("Successfully loaded biome: " + data.name + " with " +
                biome.getAllowedTileTypes().size() + " tile types");

        } catch (IllegalArgumentException e) {
            GameLogger.error("Invalid biome type: " + data.type + " for biome " + data.name);
        } catch (Exception e) {
            GameLogger.error("Error processing biome " + data.name + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void loadBiomesFromJson() {
        try {
            String jsonContent = loadJsonContent();
            Gson gson = new GsonBuilder()
                .setLenient()
                .create();

            JsonObject root = gson.fromJson(jsonContent, JsonObject.class);
            JsonArray biomesArray = root.getAsJsonArray("biomes");

            Type biomeListType = new TypeToken<List<BiomeData>>() {
            }.getType();
            List<BiomeData> biomeDataList = gson.fromJson(biomesArray, biomeListType);

            if (root.has("globalSettings")) {
                JsonBiomeConfig.GlobalSettings settings = gson.fromJson(
                    root.get("globalSettings"),
                    JsonBiomeConfig.GlobalSettings.class
                );
                updateGlobalSettings(settings);
            }

            for (BiomeData biomeData : biomeDataList) {
                processBiomeData(biomeData);
            }

            validateBiomes();

            GameLogger.info("Successfully loaded " + biomes.size() + " biomes");

        } catch (Exception e) {
            GameLogger.error("Failed to load biomes.json: " + e.getMessage());
            e.printStackTrace();
            initializeDefaultBiomes();
        }
    }

    private int getTileTypeFromString(String tileType) {
        // Handle numeric tile types (for backwards compatibility)
        try {
            if (tileType.matches("\\d+")) {
                return Integer.parseInt(tileType);
            }
        } catch (NumberFormatException ignored) {
        }

        String[] parts = tileType.toLowerCase().split("_");
        if (parts.length < 2) {
            GameLogger.error("Invalid tile type format: " + tileType);
            return -1;
        }

        Map<String, Integer> baseCategories = new HashMap<String, Integer>() {{
            // Basic terrain (0-99)
            put("grass", 10);
            put("ground", 20);
            put("water", 30);
            put("wall", 430);                 // Base wall type
            put("wall_stone", 431);           // Stone wall variant
            put("wall_wood", 432);            // Wood wall variant
            put("wall_decorated", 433);       // Decorated wall variant
            put("platform", 420);
            put("building", 440);
            put("house", 450);

            // Vegetation (100-199)
            put("flower", 100);
            put("flowers", 100);  // Alternative naming
            put("flower_tall", 101);  // Added for forest
            put("vegetation_small", 102);  // Added for forest
            put("tree", 110);
            put("tree_large", 111);  // Added for forest
            put("tree_small", 112);
            put("bush", 120);
            put("vegetation", 130);
            put("vegetation_dense", 131);  // Added for safari
            put("roots", 140);

            // Rocks and Formations (200-299)
            put("rock", 200);
            put("rock_small", 201);
            put("rock_formation", 202);
            put("rock_border", 203);
            put("cliff", 210);
            put("cave", 220);
            put("formation", 230);

            // Water Features (300-399)
            put("waterfall", 300);
            put("pool", 310);
            put("ice", 320);

            // Structures (400-499)
            put("bridge", 400);
            put("fence", 410);
            put("platform_wood", 421);
            put("platform_stone", 422);
            put("window", 460);

            // Special Features (500-599)
            put("mushroom", 500);
            put("cactus", 510);
            put("planter", 520);
            put("bench", 530);
            put("lava", 540);
            put("fountain", 550);  // Added for safari

            // Decorative (600-699)
            put("decoration", 600);
            put("ground_decorated", 601);  // Added for forest ground variations
            put("ground_detail", 602);
            put("awning", 610);
            put("tallgrass", 620);
        }};

// Update biome offsets to include all biomes
        Map<String, Integer> biomeOffsets = new HashMap<String, Integer>() {{
            put("plains", 1000);
            put("desert", 2000);
            put("forest", 3000);    // Changed to match your enum order
            put("snow", 4000);
            put("haunted", 5000);
            put("mountain", 6000);
            put("cherry", 7000);
            put("safari", 8000);
            put("swamp", 9000);
            put("volcano", 10000);    // Basic terrain (0-99)
            put("grass", 10);
            put("ground", 20);
            put("water", 30);

            // Vegetation (100-199)
            put("tree", 110);
            put("tree_dark", 111);  // Added for haunted
            put("tree_dead", 112);  // Added for haunted
            put("vegetation", 130);

            // Structures (400-499)
            put("platform", 420);
            put("wall", 430);
            put("wall_stone", 431); // Added for haunted

            // Decorative (600-699)
            put("decoration", 600);

            // Special Features (500-599)
            put("mushroom", 500);   // Keep original for compatibility
        }};
        String biomePrefix = parts[0];
        String baseType = parts[1];

        // Handle special compound types (e.g., "lava_pool", "house_wood")
        if (parts.length > 2) {
            if (parts[1].equals("tree")) {
                // Handle special tree types
                if (parts[2].equals("green") || parts[2].equals("pink") ||
                    parts[2].equals("snowy") || parts[2].equals("dead") ||
                    parts[2].equals("mangrove")) {
                    baseType = "tree";  // All tree variants use the base tree type
                }
            } else if (parts[1].equals("lava") && parts[2].equals("pool")) {
                baseType = "lava";
            } else if (parts[1].equals("ground") && parts.length > 2) {
                // Handle ground variants (e.g., ground_decorated, ground_detail)
                baseType = "ground";
            } else if (parts[1].equals("house") && parts[2].equals("wood")) {
                baseType = "house";
            } else if (parts[1].equals("wall") && parts[2].equals("stone")) {
                baseType = "wall_stone";
            } else if (parts[1].equals("wall")) {
                if (parts[2].equals("wood")) {
                    baseType = "wall_wood";
                } else if (parts[2].equals("decorated")) {
                    baseType = "wall_decorated";
                }
            }
        }

        Integer baseValue = baseCategories.get(baseType);
        Integer biomeValue = biomeOffsets.get(biomePrefix);

        if (baseValue == null || biomeValue == null) {
            GameLogger.error("Invalid base category (" + baseType + ") or biome prefix (" +
                biomePrefix + ") for tile: " + tileType);
            return -1;
        }

        return biomeValue + baseValue;
    }

    // Helper method to get tile type strings
    public List<String> getDefaultTileTypes(BiomeType biomeType) {
        String prefix = biomeType.name().toLowerCase();
        switch (biomeType) {
            case PLAINS:
                return Arrays.asList(
                    prefix + "_grass",
                    prefix + "_ground",
                    prefix + "_water",
                    prefix + "_tree",
                    prefix + "_rock",
                    prefix + "_bridge",
                    prefix + "_fence",
                    prefix + "_decoration"
                );
            case DESERT:
                return Arrays.asList(
                    prefix + "_ground",
                    prefix + "_vegetation",
                    prefix + "_rock",
                    prefix + "_cactus",
                    prefix + "_rock_small",
                    prefix + "_platform",
                    prefix + "_building",
                    prefix + "_decoration"
                );
            // Add cases for other biomes...
            default:
                return Arrays.asList(
                    prefix + "_ground",
                    prefix + "_grass",
                    prefix + "_rock",
                    prefix + "_tree",
                    prefix + "_decoration"
                );
        }
    }

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

    // Helper class for environment effects
    public static class BiomeEnvironmentEffect {
        private final String particleEffect;
        private final String ambientSound;
        private final float weatherChance;

        public BiomeEnvironmentEffect(String particleEffect, String ambientSound, float weatherChance) {
            this.particleEffect = particleEffect;
            this.ambientSound = ambientSound;
            this.weatherChance = weatherChance;
        }

        public String getParticleEffect() {
            return particleEffect;
        }

        public String getAmbientSound() {
            return ambientSound;
        }

        public float getWeatherChance() {
            return weatherChance;
        }
    }

    private static class JsonBiomeConfig {
        List<BiomeData> biomes;
        GlobalSettings globalSettings;

        static class GlobalSettings {
            float transitionWidth;
            float mountainThreshold;
            float biomeScale;
            float detailScale;
            float warpScale;
            int biomeOctaves;
            float biomePersistence;
            Map<String, EnvironmentEffectData> environmentEffects;
        }

        static class EnvironmentEffectData {
            String particleEffect;
            String ambientSound;
            float weatherChance;
        }
    }

    private static class BiomeData {
        String name;
        String type;
        double weight;
        List<String> allowedTileTypes;
        Map<String, Double> tileDistribution;
        List<String> spawnableObjects;
        Map<String, Float> spawnChances;
        Map<String, Map<String, String>> transitionMapping;
        JsonBiomeConfig.GlobalSettings globalSettings; // For biome-specific settings
    }
}

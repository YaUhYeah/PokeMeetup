    package io.github.pokemeetup.managers;

    import com.badlogic.gdx.Gdx;
    import com.badlogic.gdx.files.FileHandle;
    import com.badlogic.gdx.math.Vector2;
    import com.badlogic.gdx.utils.Json;
    import com.google.gson.*;
    import com.google.gson.stream.JsonReader;
    import com.google.gson.stream.JsonWriter;
    import io.github.pokemeetup.FileSystemDelegate;
    import io.github.pokemeetup.system.gameplay.overworld.Chunk;
    import io.github.pokemeetup.system.gameplay.overworld.WorldObject;
    import io.github.pokemeetup.system.gameplay.overworld.biomes.Biome;
    import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;
    import io.github.pokemeetup.utils.GameLogger;
    import io.github.pokemeetup.utils.PerlinNoise;

    import com.google.gson.reflect.TypeToken;
    import io.github.pokemeetup.utils.TileValidator;
    import io.github.pokemeetup.utils.storage.GameFileSystem;

    import java.io.IOException;
    import java.io.Serializable;
    import java.lang.reflect.Type;
    import java.nio.file.Files;
    import java.nio.file.Paths;
    import java.util.*;

    public class BiomeManager {
        private static final float BIOME_SCALE = 0.0015f;   // Adjusted from 0.002f
        private static final float DETAIL_SCALE = 0.005f;   // Adjusted from 0.004f
        private static final float WARP_SCALE = 0.0007f;    // Adjusted from 0.0005f

        private static final float MOUNTAIN_THRESHOLD = 0.6f; // Lowered from 0.8f to 0.6fprivate static final int BIOME_OCTAVES = 6;           // Increased from 5 to 6
        private static final double BIOME_PERSISTENCE = 0.5; // Adjusted from 0.45 to 0.5


        private static final float TRANSITION_WIDTH = 0.2f;   // Wider transitions

        private static final float MOUNTAIN_SCALE = 0.0005f;  // Larger mountain features
        private static final int BIOME_OCTAVES = 6;           // Increase for more detail


        private static final String[] BIOME_FILE_PATHS = {
            "server/data/biomes.json",
            "data/biomes.json",
            "assets/data/biomes.json",
            "config/biomes.json"
        };
        private static boolean DEBUG_ENABLED = true;
        final PerlinNoise detailNoise;
        private final PerlinNoise mountainNoise;  // Make sure this is declared
        private final PerlinNoise warpNoise;
        private final PerlinNoise mountainBiasNoise;
        private final int DEBUG_SAMPLE_RATE = 1000; // Log every 1000th tile
        private double temperatureBias;
        private double moistureBias;
        private Map<BiomeType, Biome> biomes;
        private PerlinNoise temperatureNoise;
        private PerlinNoise moistureNoise;
        private long baseSeed;
        private int debugCounter = 0;
        private double temperatureSum = 0;
        private double moistureSum = 0;
        private int sampleCount = 0;

        public BiomeManager(long baseSeed) {
            this.baseSeed = baseSeed;
            this.biomes = new HashMap<>();
            this.mountainBiasNoise = new PerlinNoise((int) (baseSeed + 3));// Adjust seed calculations
            this.temperatureNoise = new PerlinNoise((int) (baseSeed & 0xFFFFFFFFL));
            this.moistureNoise = new PerlinNoise((int) ((baseSeed >> 32) & 0xFFFFFFFFL));

            this.detailNoise = new PerlinNoise((int) ((baseSeed + 1) & 0xFFFFFFFFL));
            this.warpNoise = new PerlinNoise((int) ((baseSeed + 2) & 0xFFFFFFFFL));
            this.mountainNoise = new PerlinNoise((int) (baseSeed + 2));
            this.temperatureBias = 0.0;
            this.moistureBias = 0.0;
            loadBiomesFromJson();
        }

        public void saveChunkBiomeData(Vector2 chunkPos, Chunk chunk, String worldName, boolean isMultiplayer) {
            if (isMultiplayer) {
                return;
            }

            try {
                String baseDir = isMultiplayer ?
                    "worlds/" + worldName + "/biomes/" :
                    "worlds/singleplayer/" + worldName + "/biomes/";

                FileHandle saveDir = Gdx.files.local(baseDir);
                if (!saveDir.exists()) {
                    saveDir.mkdirs();
                }

                BiomeData biomeData = new BiomeData();
                biomeData.chunkX = (int) chunkPos.x;
                biomeData.chunkY = (int) chunkPos.y;
                biomeData.primaryBiomeType = chunk.getBiome().getType();
                biomeData.lastModified = System.currentTimeMillis();

                // Create new mutable collections
                HashMap<Integer, Integer> distribution = new HashMap<>(chunk.getBiome().getTileDistribution());
                biomeData.setTileDistribution(distribution);

                ArrayList<Integer> allowedTypes = new ArrayList<>(chunk.getBiome().getAllowedTileTypes());
                biomeData.setAllowedTileTypes(allowedTypes);

                String filename = String.format("biome_%d_%d.json", (int) chunkPos.x, (int) chunkPos.y);
                FileHandle biomeFile = saveDir.child(filename);

                // Use Gson for serialization
                Gson gson = new GsonBuilder()
                    .registerTypeAdapter(BiomeData.class, new BiomeDataTypeAdapter())
                    .setPrettyPrinting()
                    .create();

                String jsonContent = gson.toJson(biomeData);
                biomeFile.writeString(jsonContent, false);

                GameLogger.info("Saved biome data for chunk at: " + chunkPos);

            } catch (Exception e) {
                GameLogger.error("Failed to save biome data: " + e.getMessage());
            }
        }

        public float getNoise(float x, float y) {
            try {
                // Create octave-based noise
                float noise = 0f;
                float amplitude = 1.0f;
                float frequency = 1.0f;
                float maxValue = 0f;

                // Use our detail noise for additional variation
                for (int i = 0; i < BIOME_OCTAVES; i++) {
                    noise += (float) (amplitude * detailNoise.noise(
                        x * frequency,
                        y * frequency
                    ));
                    maxValue += amplitude;
                    amplitude *= BIOME_PERSISTENCE;
                    frequency *= 2.0;
                }

                // Normalize to -1 to 1 range
                noise = noise / maxValue;

                // Add some warping
                float warpX = (float) warpNoise.noise(x * WARP_SCALE, y * WARP_SCALE);
                float warpY = (float) warpNoise.noise((x + 31.5f) * WARP_SCALE, (y + 31.5f) * WARP_SCALE);

                // Apply warp
                noise += (warpX + warpY) * 0.15f;

                // Normalize to 0-1 range
                return (noise + 1f) * 0.5f;

            } catch (Exception e) {
                GameLogger.error("Error generating noise: " + e.getMessage());
                return 0.5f; // Safe fallback value
            }
        }

        // Add helper method for biome-specific noise
        public float getBiomeNoise(float x, float y, BiomeType biomeType) {
            float baseNoise = getNoise(x, y);

            // Modify noise based on biome type
            switch (biomeType) {
                case DESERT:
                    // More uniform, with occasional dunes
                    return smoothstep(0.3f, 0.7f, baseNoise);

                case FOREST:
                    // More varied terrain
                    return baseNoise * 1.2f - 0.1f;

                case SNOW:
                    // Smoother transitions
                    return smoothstep(0.2f, 0.8f, baseNoise);

                case HAUNTED:
                    // More extreme variations
                    return (float) Math.pow(baseNoise, 1.5);

                default:
                    return baseNoise;
            }
        }

        // Add this method to generate elevation noise
        public float getElevationNoise(float x, float y) {
            float mountainInfluence = (float) mountainNoise.noise(
                x * MOUNTAIN_SCALE,
                y * MOUNTAIN_SCALE
            );

            float baseNoise = getNoise(x, y);

            // Blend mountain and base noise
            return mountainInfluence > MOUNTAIN_THRESHOLD ?
                mountainInfluence :
                baseNoise;
        }

        public BiomeType loadChunkBiomeData(Vector2 chunkPos, String worldName, boolean isMultiplayer) {
            if (isMultiplayer) {
                return null;
            }
            try {
                String baseDir = isMultiplayer ?
                    "worlds/" + worldName + "/biomes/" :
                    "worlds/singleplayer/" + worldName + "/biomes/";

                String filename = String.format("biome_%d_%d.json", (int) chunkPos.x, (int) chunkPos.y);
                FileHandle biomeFile = Gdx.files.local(baseDir + filename);

                if (!biomeFile.exists()) {
                    return null;
                }

                // Use Gson instead of libgdx Json
                Gson gson = new GsonBuilder()
                    .registerTypeAdapter(BiomeData.class, new BiomeDataTypeAdapter())
                    .create();

                BiomeData biomeData = gson.fromJson(biomeFile.readString(), BiomeData.class);

                if (biomeData != null && biomeData.primaryBiomeType != null) {
                    GameLogger.info(String.format(
                        "Loaded biome data for chunk (%d,%d): %s",
                        biomeData.chunkX, biomeData.chunkY, biomeData.primaryBiomeType
                    ));
                    return biomeData.primaryBiomeType;
                }

            } catch (Exception e) {
                GameLogger.error("Failed to load biome data: " + e.getMessage());
            }
            return null;
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

        private double generateMountainValue(float worldX, float worldY) {
            double mountainBase = mountainNoise.noise(worldX * MOUNTAIN_SCALE, worldY * MOUNTAIN_SCALE);
            double mountainDetail = mountainNoise.noise(worldX * (MOUNTAIN_SCALE * 2), worldY * (MOUNTAIN_SCALE * 2)) * 0.3;
            return (mountainBase + mountainDetail + 1.0) / 2.0;
        }


        private float calculateTransitionFactor(double temperature, double moisture) {
            double detailVar = detailNoise.noise(temperature * 10, moisture * 10) * 0.1;
            double tempFactor = Math.abs(temperature - 0.5) * 2;
            double moistFactor = Math.abs(moisture - 0.5) * 2;
            double transitionStrength = Math.max(tempFactor, moistFactor) + Math.abs(detailVar);

            // Smoother transition calculation
            return smoothstep(0, TRANSITION_WIDTH, (float) transitionStrength);
        }

        private BiomeType determineBiomeType(double temperature, double moisture, double mountainValue) {
            if (mountainValue > MOUNTAIN_THRESHOLD) {
                return temperature < 0.5 ? BiomeType.SNOW : BiomeType.BIG_MOUNTAINS;
            }

            if (temperature < 0.2) { // Lowered from 0.3 to 0.2
                return moisture > 0.4 ? BiomeType.SNOW : BiomeType.DESERT;
            } else if (temperature < 0.5) { // Adjusted to 0.5
                if (moisture < 0.3) {
                    return BiomeType.DESERT;
                } else if (moisture < 0.6) {
                    return BiomeType.PLAINS;
                } else {
                    return BiomeType.FOREST;
                }
            } else if (temperature < 0.75) { // Added an extra range
                if (moisture < 0.3) {
                    return BiomeType.DESERT;
                } else if (moisture < 0.6) {
                    return BiomeType.PLAINS;
                } else if (moisture < 0.8) {
                    return BiomeType.FOREST;
                } else {
                    return BiomeType.RAIN_FOREST;
                }
            } else {
                if (moisture < 0.3) {
                    return BiomeType.DESERT;
                } else if (moisture < 0.6) {
                    return BiomeType.PLAINS;
                } else if (moisture < 0.8) {
                    return BiomeType.FOREST;
                } else {
                    return BiomeType.HAUNTED;
                }
            }
        }


        private BiomeType getTransitionBiome(double temperature, double moisture, BiomeType primary) {
            switch (primary) {
                case RAIN_FOREST:
                    if (moisture < 0.7) return BiomeType.FOREST;
                    if (temperature < 0.7) return BiomeType.FOREST;
                    return BiomeType.HAUNTED;

                case HAUNTED:
                    if (moisture < 0.7) return BiomeType.FOREST;
                    return BiomeType.RAIN_FOREST;

                case FOREST:
                    if (temperature > 0.7 && moisture > 0.7) return BiomeType.RAIN_FOREST;
                    if (moisture < 0.5) return BiomeType.PLAINS;
                    return BiomeType.RAIN_FOREST;

                case DESERT:
                    return BiomeType.PLAINS;

                case PLAINS:
                    if (temperature < 0.3) return BiomeType.SNOW;
                    if (moisture > 0.6) return BiomeType.FOREST;
                    return BiomeType.PLAINS;

                case SNOW:
                    return BiomeType.PLAINS;

                default:
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

        // In loadBiomesFromJson():


        private void loadBiomesFromJson() {
            try {
                String jsonContent = GameFileSystem.getInstance().getDelegate().readString("Data/biomes.json");
                if (jsonContent == null) {
                    initializeDefaultBiomes();
                    return;
                }

                JsonParser parser = new JsonParser();
                JsonArray biomesArray = parser.parse(jsonContent).getAsJsonArray();

                for (JsonElement element : biomesArray) {
                    JsonObject biomeObject = element.getAsJsonObject();
                    BiomeData biomeData = parseBiomeData(biomeObject);

                    if (validateBiomeData(biomeData)) {
                        BiomeType type = BiomeType.valueOf(biomeData.getType().toUpperCase());
                        Biome biome = new Biome(biomeData.getName(), type);
                        biome.setAllowedTileTypes(biomeData.getAllowedTileTypes());
                        biome.setTileDistribution(biomeData.getTileDistribution());
                        biomes.put(type, biome);
                        GameLogger.info("Loaded biome: " + biomeData.getName());
                    } else {
                        GameLogger.error("Invalid biome data for: " + biomeData.getName());
                    }
                }

            } catch (Exception e) {
                GameLogger.error("Failed to load biomes: " + e.getMessage());
                initializeDefaultBiomes();
            }
        }

        private BiomeData parseBiomeData(JsonObject json) {
            BiomeData data = new BiomeData();
            data.setName(json.get("name").getAsString());
            data.setType(json.get("type").getAsString());

            // Parse allowed tile types
            JsonArray allowedTypes = json.getAsJsonArray("allowedTileTypes");
            List<Integer> tileTypes = new ArrayList<>();
            for (JsonElement type : allowedTypes) {
                tileTypes.add(type.getAsInt());
            }
            data.setAllowedTileTypes(tileTypes);

            // Parse tile distribution with validation
            JsonObject distObject = json.getAsJsonObject("tileDistribution");
            Map<Integer, Integer> distribution = new HashMap<>();
            double total = 0;
            for (Map.Entry<String, JsonElement> entry : distObject.entrySet()) {
                int tileType = Integer.parseInt(entry.getKey());
                double weight = entry.getValue().getAsDouble();
                distribution.put(tileType, (int)Math.round(weight));
                total += weight;
            }

            if (Math.abs(total - 100.0) > 0.01) {
                GameLogger.error("Tile distribution does not sum to 100% for biome: " + data.getName());
                distribution = getDefaultDistribution(BiomeType.valueOf(data.getType()));
            }

            data.setTileDistribution(distribution);
            return data;
        }

        private boolean validateBiomeData(BiomeData data) {
            if (data.getName() == null || data.getType() == null) return false;
            if (data.getAllowedTileTypes() == null || data.getAllowedTileTypes().isEmpty()) return false;

            Map<Integer, Integer> dist = data.getTileDistribution();
            if (dist == null || dist.isEmpty()) return false;

            double total = dist.values().stream()
                .mapToDouble(Integer::intValue)
                .sum();

            return Math.abs(total - 100.0) < 0.01;
        }

        public BiomeTransitionResult getBiomeAt(float worldX, float worldY) {
            double warpX = warpNoise.noise(worldX * WARP_SCALE, worldY * WARP_SCALE) * 10;
            double warpY = warpNoise.noise((worldX + 31.5f) * WARP_SCALE, (worldY + 31.5f) * WARP_SCALE) * 10;

            double temperatureBase = generateOctaveNoise(temperatureNoise,
                (worldX + warpX) * BIOME_SCALE,
                (worldY + warpY) * BIOME_SCALE,
                1.0);

            double moistureBase = generateOctaveNoise(moistureNoise,
                (worldX + warpX * 0.8) * BIOME_SCALE,
                (worldY + warpY * 0.8) * BIOME_SCALE,
                1.0);

            double detailVar = detailNoise.noise(worldX * DETAIL_SCALE, worldY * DETAIL_SCALE) * 0.1;

            double temperature = (temperatureBase + 1.0) / 2.0 + temperatureBias + (detailVar * 0.3);
            double moisture = (moistureBase + 1.0) / 2.0 + moistureBias + (detailVar * 0.3);

            temperature = Math.max(0.0, Math.min(1.0, temperature));
            moisture = Math.max(0.0, Math.min(1.0, moisture));

            double mountainValue = generateMountainValue(worldX, worldY);

            BiomeType primaryType = determineBiomeType(temperature, moisture, mountainValue);
            BiomeType secondaryType = getTransitionBiome(temperature, moisture, primaryType);
            float transitionFactor = calculateTransitionFactor(temperature, moisture);

            Biome primaryBiome = getBiome(primaryType);
            Biome secondaryBiome = getBiome(secondaryType);

            if (!validateBiome(primaryBiome) || !validateBiome(secondaryBiome)) {
                GameLogger.error("Invalid biome detected, falling back to Plains");
                return new BiomeTransitionResult(getBiome(BiomeType.PLAINS), null, 0);
            }

            return new BiomeTransitionResult(primaryBiome, secondaryBiome, transitionFactor);
        }

        private boolean validateBiome(Biome biome) {
            if (biome == null) return false;

            Map<Integer, Integer> dist = biome.getTileDistribution();
            if (dist == null || dist.isEmpty()) return false;

            double total = dist.values().stream()
                .mapToDouble(Integer::intValue)
                .sum();

            return Math.abs(total - 100.0) < 0.01;
        }

        private List<Integer> getDefaultAllowedTypes(BiomeType type) {
            switch (type) {
                case DESERT:
                    return Arrays.asList(1, 2, 16); // grass, dirt, sand
                case SNOW:
                    return Arrays.asList(1, 2, 3, 4); // grass, dirt, stone, snow
                case HAUNTED:
                    return Arrays.asList(1, 2, 3, 8); // grass, dirt, stone, dark grass
                default:
                    return Arrays.asList(1, 2, 3); // grass, dirt, stone
            }
        }

        private Map<Integer, Integer> getDefaultDistribution(BiomeType type) {
            Map<Integer, Integer> dist = new HashMap<>();
            switch (type) {
                case DESERT:
                    dist.put(16, 70); // sand
                    dist.put(2, 20);  // dirt
                    dist.put(1, 10);  // grass
                    break;
                case SNOW:
                    dist.put(4, 70);  // snow
                    dist.put(1, 20);  // grass
                    dist.put(3, 10);  // stone
                    break;
                case HAUNTED:
                    dist.put(8, 70);  // dark grass
                    dist.put(2, 20);  // dirt
                    dist.put(3, 10);  // stone
                    break;
                default:
                    dist.put(1, 70);  // grass
                    dist.put(2, 20);  // dirt
                    dist.put(3, 10);  // stone
            }
            return dist;
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

        private static class MapDeserializer implements JsonDeserializer<Map<?, ?>> {
            @Override
            public Map<?, ?> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
                return new HashMap<>(context.deserialize(json, HashMap.class));
            }
        }

        public static class BiomeData implements Serializable {
            private String name;
            private String type;
            private ArrayList<Integer> allowedTileTypes;
            private HashMap<Integer, Integer> tileDistribution;
            private ArrayList<String> spawnableObjects;
            private int chunkX;
            private int chunkY;
            private BiomeType primaryBiomeType;
            private long lastModified;
            private HashMap<String, Double> spawnChances;

            // Required no-arg constructor
            public BiomeData() {
                this.tileDistribution = new HashMap<>();
                this.allowedTileTypes = new ArrayList<>();
                this.spawnableObjects = new ArrayList<>();
                this.spawnChances = new HashMap<>();
            }

            // Copy constructor
            public BiomeData(BiomeData other) {
                this();
                if (other != null) {
                    this.name = other.name;
                    this.type = other.type;
                    this.allowedTileTypes.addAll(other.allowedTileTypes);
                    this.tileDistribution.putAll(other.tileDistribution);
                    this.spawnableObjects.addAll(other.spawnableObjects);
                    this.chunkX = other.chunkX;
                    this.chunkY = other.chunkY;
                    this.primaryBiomeType = other.primaryBiomeType;
                    this.lastModified = other.lastModified;
                    this.spawnChances.putAll(other.spawnChances);
                }
            }

            // Getters and Setters
            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }
            private boolean validateTileDistribution(Map<Integer, Integer> distribution) {
                if (distribution == null || distribution.isEmpty()) return false;

                double total = distribution.values().stream()
                    .mapToDouble(Number::doubleValue)
                    .sum();

                return Math.abs(total - 100.0) < 0.01; // Allow small floating point variance
            }
            public String getType() {
                return type;
            }

            public void setType(String type) {
                this.type = type;
            }

            public ArrayList<Integer> getAllowedTileTypes() {
                return allowedTileTypes;
            }

            public void setAllowedTileTypes(List<Integer> types) {
                if (types != null) {
                    this.allowedTileTypes.clear();
                    this.allowedTileTypes.addAll(types);
                }
            }

            public HashMap<Integer, Integer> getTileDistribution() {
                return tileDistribution;
            }

            public void setTileDistribution(Map<Integer, Integer> distribution) {
                if (distribution != null) {
                    this.tileDistribution.clear();
                    this.tileDistribution.putAll(distribution);
                }
            }

            public BiomeType getPrimaryBiomeType() {
                return primaryBiomeType;
            }

            public void setPrimaryBiomeType(BiomeType type) {
                this.primaryBiomeType = type;
            }

            public void validate() {
                if (tileDistribution == null) tileDistribution = new HashMap<>();
                if (allowedTileTypes == null) allowedTileTypes = new ArrayList<>();
                if (spawnableObjects == null) spawnableObjects = new ArrayList<>();
                if (spawnChances == null) spawnChances = new HashMap<>();
            }
        }

        // Custom TypeAdapter for BiomeData
        private static class BiomeDataTypeAdapter extends TypeAdapter<BiomeData> {
            @Override
            public void write(JsonWriter out, BiomeData value) throws IOException {
                out.beginObject();

                // Write basic properties
                if (value.getName() != null) out.name("name").value(value.getName());
                if (value.getType() != null) out.name("type").value(value.getType());

                // Write allowed types
                if (value.getAllowedTileTypes() != null && !value.getAllowedTileTypes().isEmpty()) {
                    out.name("allowedTileTypes");
                    out.beginArray();
                    for (Integer type : value.getAllowedTileTypes()) {
                        if (type != null) out.value(type);
                    }
                    out.endArray();
                }

                // Write distribution
                if (value.getTileDistribution() != null && !value.getTileDistribution().isEmpty()) {
                    out.name("tileDistribution");
                    out.beginObject();
                    for (Map.Entry<Integer, Integer> entry : value.getTileDistribution().entrySet()) {
                        if (entry.getKey() != null && entry.getValue() != null) {
                            out.name(entry.getKey().toString()).value(entry.getValue());
                        }
                    }
                    out.endObject();
                }

                // Write optional properties
                if (value.getPrimaryBiomeType() != null) {
                    out.name("primaryBiomeType").value(value.getPrimaryBiomeType().name());
                }

                out.endObject();
            }

            @Override
            public BiomeData read(JsonReader in) throws IOException {
                BiomeData data = new BiomeData();
                in.beginObject();

                while (in.hasNext()) {
                    String fieldName = in.nextName();
                    try {
                        switch (fieldName) {
                            case "name":
                                data.setName(in.nextString());
                                break;
                            case "type":
                                data.setType(in.nextString());
                                break;
                            case "allowedTileTypes":
                                in.beginArray();
                                while (in.hasNext()) {
                                    try {
                                        data.getAllowedTileTypes().add(in.nextInt());
                                    } catch (Exception e) {
                                        in.skipValue();
                                        GameLogger.error("Skipped invalid allowed tile type");
                                    }
                                }
                                in.endArray();
                                break;
                            case "tileDistribution":
                                in.beginObject();
                                while (in.hasNext()) {
                                    try {
                                        String key = in.nextName();
                                        int value = in.nextInt();
                                        data.getTileDistribution().put(Integer.parseInt(key), value);
                                    } catch (Exception e) {
                                        in.skipValue();
                                        GameLogger.error("Skipped invalid tile distribution entry");
                                    }
                                }
                                in.endObject();
                                break;
                            case "primaryBiomeType":
                                try {
                                    data.setPrimaryBiomeType(BiomeType.valueOf(in.nextString()));
                                } catch (Exception e) {
                                    in.skipValue();
                                }
                                break;
                            default:
                                in.skipValue();
                                break;
                        }
                    } catch (Exception e) {
                        GameLogger.error("Error parsing field " + fieldName + ": " + e.getMessage());
                        in.skipValue();
                    }
                }

                in.endObject();
                data.validate();
                return data;
            }
        }
    }

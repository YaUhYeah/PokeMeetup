package io.github.pokemeetup.system.gameplay.overworld.biomes;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.github.pokemeetup.system.gameplay.overworld.WorldObject;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.textures.TextureManager;

import java.util.*;

public class Biome {
    private final BiomeType type;
    private final Map<Integer, Float> tileSpawnChances;
    private final float temperature;
    private final Map<WorldObject.ObjectType, Float> objectSpawnChances;
    private HashMap<Integer, Integer> tileDistribution;
    private String name;
    private List<Integer> allowedTileTypes;
    private List<WorldObject.ObjectType> spawnableObjects;

    public Biome(String name, BiomeType type) {
        this.name = name;
        this.type = type;
        this.temperature = 0;
        this.allowedTileTypes = new ArrayList<>();
        this.spawnableObjects = new ArrayList<>();
        this.tileSpawnChances = new HashMap<>();
        this.objectSpawnChances = new HashMap<>();
        this.tileDistribution = new HashMap<>();
    }

    public void setTileDistribution(Map<Integer, Integer> distribution) {
        if (distribution == null || distribution.isEmpty()) {
            throw new IllegalArgumentException("Tile distribution cannot be null or empty");
        }

        // If allowed types is empty, initialize it from the distribution
        if (allowedTileTypes.isEmpty()) {
            allowedTileTypes = new ArrayList<>(distribution.keySet());
            GameLogger.info(String.format("Biome %s: Automatically setting allowed types to %s",
                name, allowedTileTypes));
        }

        // Add any missing tile types to allowed types
        for (Integer tileType : distribution.keySet()) {
            if (!allowedTileTypes.contains(tileType)) {
                allowedTileTypes.add(tileType);
                GameLogger.info(String.format("Biome %s: Added tile type %d to allowed types",
                    name, tileType));
            }
        }

        // Verify textures exist for all tiles
        boolean allTexturesValid = true;
        for (Integer tileType : distribution.keySet()) {
            if (TextureManager.getTileTexture(tileType) == null) {
                GameLogger.error(String.format("Biome %s: Missing texture for tile type %d",
                    name, tileType));
                allTexturesValid = false;
            }
        }

        if (!allTexturesValid) {
            GameLogger.error(String.format("Biome %s: Using fallback tile distribution due to missing textures",
                name));
            useFallbackDistribution();
            return;
        }

        // Calculate total weight and normalize
        double totalWeight = distribution.values().stream()
            .mapToDouble(Integer::doubleValue)
            .sum();

        // Normalize weights if needed
        if (Math.abs(totalWeight - 100.0) > 0.001) {
            Map<Integer, Integer> normalizedDist = new HashMap<>();

            for (Map.Entry<Integer, Integer> entry : distribution.entrySet()) {
                double normalizedValue = (entry.getValue() / totalWeight) * 100.0;
                normalizedDist.put(entry.getKey(), (int) Math.round(normalizedValue));
            }

            // Adjust for rounding errors
            int finalTotal = normalizedDist.values().stream()
                .mapToInt(Integer::intValue)
                .sum();

            if (finalTotal != 100) {
                int diff = 100 - finalTotal;
                int highestKey = normalizedDist.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(distribution.keySet().iterator().next());

                normalizedDist.put(highestKey, normalizedDist.get(highestKey) + diff);
            }

            distribution = normalizedDist;
        }

        // Store the normalized distribution
        this.tileDistribution = new HashMap<>(distribution);

        // Log the final distribution
        GameLogger.info(String.format("Biome %s final tile distribution:", name));
        distribution.forEach((type, weight) -> {
            String textureName = TextureManager.getTextureNameForBiome(type, this.type);
            GameLogger.info(String.format("  Tile %d (%s): %d%%", type, textureName, weight));
        });
    }

    public void validateTileDistribution() {
        if (tileDistribution == null || tileDistribution.isEmpty()) {
            GameLogger.error(String.format("Biome %s has invalid tile distribution", name));
            return;
        }

        int total = tileDistribution.values().stream().mapToInt(Integer::intValue).sum();
        GameLogger.info(String.format("Biome %s distribution total: %d%%, tiles: %s", name, total, tileDistribution.keySet()));

        // Verify each tile has a valid texture
        tileDistribution.keySet().forEach(tileType -> {
            TextureRegion texture = TextureManager.getTileTexture(tileType);
            if (texture == null) {
                GameLogger.error(String.format("Biome %s: Missing texture for tile type %d", name, tileType));
            }
        });
    }

    public void setObjectSpawnChance(WorldObject.ObjectType objectType, float chance) {
        objectSpawnChances.put(objectType, chance);
    }

    public float getObjectSpawnChance(WorldObject.ObjectType objectType) {
        return objectSpawnChances.getOrDefault(objectType, 0.0f);
    }

    public boolean shouldSpawnObject(WorldObject.ObjectType objectType, Random random) {
        float chance = getObjectSpawnChance(objectType);
        return random.nextFloat() < chance;
    }

    public BiomeType getType() {
        return type;
    }

    public HashMap<Integer, Integer> getTileDistribution() {
        return tileDistribution;
    }
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }


    public float getSpawnChanceForTileType(int tileType) {
        return tileSpawnChances.getOrDefault(tileType, 0f);
    }

    public float getTemperature() {
        return temperature;
    }

    public List<Integer> getAllowedTileTypes() {
        return allowedTileTypes;
    }

    public void setAllowedTileTypes(List<Integer> allowedTileTypes) {
        this.allowedTileTypes = allowedTileTypes;
    }

    public List<WorldObject.ObjectType> getSpawnableObjects() {
        return spawnableObjects;
    }

    public void setSpawnableObjects(List<WorldObject.ObjectType> spawnableObjects) {
        this.spawnableObjects = spawnableObjects;
    }

    private void useFallbackDistribution() {
        // Provide safe default distribution
        Map<Integer, Integer> fallback = new HashMap<>();
        fallback.put(1, 70);  // grass
        fallback.put(2, 20);  // dirt
        fallback.put(3, 10);  // stone

        this.allowedTileTypes = new ArrayList<>(fallback.keySet());
        this.tileDistribution = new HashMap<>(fallback);

        GameLogger.info(String.format("Biome %s using fallback distribution: %s",
            name, fallback));
    }

    // Add this method to validate the entire biome state
    public void validate() {
        if (allowedTileTypes == null) {
            allowedTileTypes = new ArrayList<>();
        }

        if (tileDistribution == null || tileDistribution.isEmpty()) {
            useFallbackDistribution();
        }

        if (spawnableObjects == null) {
            spawnableObjects = new ArrayList<>();
        }

        validateTileDistribution();
    }
}

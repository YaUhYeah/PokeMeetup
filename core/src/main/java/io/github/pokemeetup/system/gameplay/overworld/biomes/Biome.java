package io.github.pokemeetup.system.gameplay.overworld.biomes;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.github.pokemeetup.system.gameplay.overworld.WorldObject;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.TextureManager;
import io.github.pokemeetup.utils.TileType;

import java.util.*;

public class Biome {
    private Map<Integer, Integer> tileDistribution; // Tile type to weight
    private Map<Integer, Float> tileSpawnChances;   // Tile type to spawn chance
    private final BiomeType type;
    private final Map<Integer, TextureRegion> tileTextures;
    private final Map<BiomeType, Map<Integer, TextureRegion>> transitionTiles;
    private Set<Integer> spawnableTileTypes;      // Tile types where objects can spawn
    private String name;
    private float temperature;
    private Map<BiomeType, Map<Integer, Integer>> transitionTilesConfig; // Add this field
    private float moisture;
    private List<Integer> allowedTileTypes;
    private List<WorldObject.ObjectType> spawnableObjects;

    public Biome(String name, BiomeType type) {
        this.name = name;
        this.type = type;
        this.tileTextures = new HashMap<>();
        this.transitionTiles = new HashMap<>();
        this.transitionTilesConfig = new HashMap<>();
        this.allowedTileTypes = new ArrayList<>();
        this.spawnableObjects = new ArrayList<>();
        this.tileSpawnChances = new HashMap<>();
        this.spawnableTileTypes = new HashSet<>();
        this.tileDistribution = new HashMap<>();
    }

    public void loadTileTextures() {
        // Load main tile textures for this biome
        for (Integer tileType : allowedTileTypes) {
            String tileName = TileType.getTileTypeNames().get(tileType);
            TextureRegion texture = TextureManager.tiles.findRegion(tileName);
            if (texture != null) {
                tileTextures.put(tileType, texture);
                GameLogger.info("Loaded texture for tile type " + tileName + " in biome " + type);
            } else {
                // Handle missing texture
                GameLogger.error("Missing texture for tile type " + tileName + " in biome " + type);
            }
        }
    }

    public void loadTransitionTiles() {
        // Load transition tiles for transitions to other biomes
        for (Map.Entry<BiomeType, Map<Integer, Integer>> transitionEntry : transitionTilesConfig.entrySet()) {
            BiomeType neighborBiomeType = transitionEntry.getKey();
            Map<Integer, Integer> tileMapping = transitionEntry.getValue();
            Map<Integer, TextureRegion> neighborTransitionTiles = new HashMap<>();

            for (Map.Entry<Integer, Integer> tileEntry : tileMapping.entrySet()) {
                int fromTileType = tileEntry.getKey();
                int toTileType = tileEntry.getValue();

                String tileName = TileType.getTileTypeNames().get(toTileType);
                TextureRegion texture = TextureManager.tiles.findRegion(tileName);
                if (texture != null) {
                    neighborTransitionTiles.put(fromTileType, texture);
                    GameLogger.info("Loaded transition texture for tile type " + tileName + " from " + this.type + " to " + neighborBiomeType);
                } else {
                    GameLogger.error("Missing transition texture for tile type " + tileName + " from " + this.type + " to " + neighborBiomeType);
                }
            }

            if (!neighborTransitionTiles.isEmpty()) {
                transitionTiles.put(neighborBiomeType, neighborTransitionTiles);
            }
        }
    }

    public Map<Integer, TextureRegion> getTileTextures() {
        return tileTextures;
    }

    public Map<BiomeType, Map<Integer, TextureRegion>> getTransitionTiles() {
        return transitionTiles;
    }

    public Map<BiomeType, Map<Integer, Integer>> getTransitionTilesConfig() {
        return transitionTilesConfig;
    }

    public void setTileSpawnChances(Map<Integer, Float> tileSpawnChances) {
        this.tileSpawnChances = tileSpawnChances;
    }

    public void setSpawnableTileTypes(Set<Integer> spawnableTileTypes) {
        this.spawnableTileTypes = spawnableTileTypes;
    }

    public void setTransitionTilesConfig(Map<BiomeType, Map<Integer, Integer>> transitionTilesConfig) {
        this.transitionTilesConfig = transitionTilesConfig;
    }

    public TextureRegion getTileTexture(int tileType) {
        return tileTextures.get(tileType);
    }

    public TextureRegion getTransitionTile(BiomeType neighborBiomeType, int tileType) {
        Map<Integer, TextureRegion> transitions = transitionTiles.get(neighborBiomeType);
        if (transitions != null) {
            return transitions.get(tileType);
        }
        return null;
    }

    public BiomeType getType() {
        return type;
    }

    public Map<Integer, Integer> getTileDistribution() {
        return tileDistribution;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setSpawnChanceForTileType(int tileType, float chance) {
        tileSpawnChances.put(tileType, chance);
        spawnableTileTypes.add(tileType);
    }

    public float getSpawnChanceForTileType(int tileType) {
        return tileSpawnChances.getOrDefault(tileType, 0f);
    }

    public Map<Integer, Float> getTileSpawnChances() {
        return tileSpawnChances;
    }

    public Set<Integer> getSpawnableTileTypes() {
        return spawnableTileTypes;
    }

    public float getTemperature() {
        return temperature;
    }

    public void setTemperature(float temperature) {
        this.temperature = temperature;
    }

    public float getMoisture() {
        return moisture;
    }

    public void setMoisture(float moisture) {
        this.moisture = moisture;
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
}

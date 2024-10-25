package io.github.pokemeetup.system.gameplay.overworld.biomes;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.github.pokemeetup.system.gameplay.overworld.WorldObject;

import java.util.*;

public class Biome {
    private String name;
    private float temperature;
    private Map<Integer, Integer> tileDistribution; // Add this field
    private float moisture;
    private BiomeType type;
    private List<Integer> allowedTileTypes;
    private List<WorldObject.ObjectType> spawnableObjects;
    private Map<Integer, TextureRegion> tileTextures;

    // Constructor, getters, setters, etc.
    private Map<Integer, Float> tileSpawnChances; // Map of tile type to spawn chance
    private Set<Integer> spawnableTileTypes;      // Tile types where objects can spawn
    public Biome(String name, BiomeType type) {
        this.name = name;
        this.type = type;
        this.allowedTileTypes = new ArrayList<>();
        this.tileTextures = new HashMap<>();
        this.spawnableObjects = new ArrayList<>();
        this.tileSpawnChances = new HashMap<>();
        this.spawnableTileTypes = new HashSet<>();
        this.tileDistribution = new HashMap<>();
    }

    public BiomeType getType() {
        return type;
    }   public Map<Integer, Integer> getTileDistribution() {
        return tileDistribution;
    }

    public String getName() {
        return name;
    }
    public void setSpawnChanceForTileType(int tileType, float chance) {
        tileSpawnChances.put(tileType, chance);
        spawnableTileTypes.add(tileType);
    }

    // Method to get spawn chance for a tile type
    public float getSpawnChanceForTileType(int tileType) {
        return tileSpawnChances.getOrDefault(tileType, 0f);
    }
    public void setName(String name) {
        this.name = name;
    }
    public Map<Integer, Float> getTileSpawnChances() {
        return tileSpawnChances;
    }

    public Set<Integer> getSpawnableTileTypes() {
        return spawnableTileTypes;
    }

    // Methods to set spawn chance for a tile type

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

    public Map<Integer, TextureRegion> getTileTextures() {
        return tileTextures;
    }

    public void setTileTextures(Map<Integer, TextureRegion> tileTextures) {
        this.tileTextures = tileTextures;
    }
}

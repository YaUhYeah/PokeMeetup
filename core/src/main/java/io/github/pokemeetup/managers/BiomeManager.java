package io.github.pokemeetup.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import io.github.pokemeetup.system.gameplay.overworld.WorldObject;
import io.github.pokemeetup.system.gameplay.overworld.biomes.Biome;
import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;

import java.util.*;

public class BiomeManager {
    private Map<BiomeType, Biome> biomes;
    private TextureAtlas atlas;

    public BiomeManager(TextureAtlas atlas) {
        this.atlas = atlas;
        biomes = new HashMap<>();
        loadBiomesFromJson();
    }

    private void loadBiomesFromJson() {
        Json json = new Json();
        Array<BiomeData> biomeDataList = json.fromJson(Array.class, BiomeData.class, Gdx.files.internal("Data/biomes.json"));

        for (BiomeData data : biomeDataList) {
            BiomeType biomeType = BiomeType.valueOf(data.type);
            Biome biome = new Biome(data.name, biomeType);

            // Load allowed tile types
            biome.getAllowedTileTypes().addAll(data.allowedTileTypes);

            // Load tile textures
            for (Map.Entry<String, String> entry : data.tileTextures.entrySet()) {
                int tileType = Integer.parseInt(entry.getKey());
                TextureRegion texture = atlas.findRegion(entry.getValue());
                biome.getTileTextures().put(tileType, texture);
            }

            // Load spawnable objects
            for (String objTypeName : data.spawnableObjects) {
                WorldObject.ObjectType objType = WorldObject.ObjectType.valueOf(objTypeName);
                biome.getSpawnableObjects().add(objType);
            }

            // Load spawn chances
            if (data.spawnChances != null) {
                for (Map.Entry<String, Double> entry : data.spawnChances.entrySet()) {
                    int tileType = Integer.parseInt(entry.getKey());
                    float chance = entry.getValue().floatValue();
                    biome.setSpawnChanceForTileType(tileType, chance);
                }
            }
            // Load tile distribution
            if (data.tileDistribution != null) {
                for (Map.Entry<String, Integer> entry : data.tileDistribution.entrySet()) {
                    int tileType = Integer.parseInt(entry.getKey());
                    int weight = entry.getValue();
                    biome.getTileDistribution().put(tileType, weight);
                }
            }
            biomes.put(biomeType, biome);
        }
    }

    public Biome getBiome(BiomeType type) {
        return biomes.get(type);
    }

    // Inner class to map JSON data
    private static class BiomeData {
        public String name;
        public String type;
        public List<Integer> allowedTileTypes;
        public HashMap<String, String> tileTextures;
        public List<String> spawnableObjects;
        public HashMap<String, Double> spawnChances;
        public HashMap<String, Integer> tileDistribution; // Add this field
    }

}

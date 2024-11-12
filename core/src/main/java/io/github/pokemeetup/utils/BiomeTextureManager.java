package io.github.pokemeetup.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;

import java.util.HashMap;
import java.util.Map;

public class BiomeTextureManager {
    private static final String TILESET_PATH = "tilesets/";
    private static final Map<BiomeType, TextureAtlas> biomeAtlases = new HashMap<>();
    private static final Map<String, TextureRegion> tileCache = new HashMap<>();

    public static void initialize() {
        // Load tileset for each biome
        for (BiomeType biome : BiomeType.values()) {
            loadBiomeTileset(biome);
        }
    }

    private static void loadBiomeTileset(BiomeType biome) {
        String filename = biome.name().toLowerCase() + "_tileset.atlas";
        try {
            TextureAtlas atlas = new TextureAtlas(Gdx.files.internal(TILESET_PATH + filename));
            biomeAtlases.put(biome, atlas);
            GameLogger.info("Loaded tileset for biome: " + biome);
        } catch (Exception e) {
            GameLogger.error("Failed to load tileset for " + biome + ": " + e.getMessage());
        }
    }

    public static TextureRegion getTile(BiomeType biome, String tileName) {
        String cacheKey = biome + "_" + tileName;
        
        // Check cache first
        if (tileCache.containsKey(cacheKey)) {
            return tileCache.get(cacheKey);
        }

        // Get from atlas
        TextureAtlas atlas = biomeAtlases.get(biome);
        if (atlas != null) {
            TextureRegion region = atlas.findRegion(tileName);
            if (region != null) {
                tileCache.put(cacheKey, region);
                return region;
            }
        }

        GameLogger.error("Failed to find tile '" + tileName + "' for biome " + biome);
        return null;
    }

    public static void dispose() {
        for (TextureAtlas atlas : biomeAtlases.values()) {
            atlas.dispose();
        }
        biomeAtlases.clear();
        tileCache.clear();
    }
}

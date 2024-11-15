package io.github.pokemeetup.utils;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.github.pokemeetup.system.gameplay.overworld.biomes.Biome;
import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;
import io.github.pokemeetup.utils.textures.TextureManager;

import java.util.Map;

public class TileValidator {
    public static void validateTileMappings(Map<BiomeType, Biome> biomes) {
        for (Biome biome : biomes.values()) {
            for (Integer tileType : biome.getAllowedTileTypes()) {
                // Verify texture exists
                TextureRegion texture = TextureManager.getTileTexture(tileType);
                if (texture == null) {
                    continue;
                }

                // Verify tile distribution
                if (!biome.getTileDistribution().containsKey(tileType)) {
                    GameLogger.error("Tile type " + tileType + " allowed but missing distribution for " +
                        biome.getType());
                }
            }

            // Check distribution total
            double total = biome.getTileDistribution().values().stream()
                .mapToDouble(Number::doubleValue)
                .sum();
            if (Math.abs(total - 100.0) > 0.01) {
                GameLogger.error("Distribution total for " + biome.getType() +
                    " equals " + total + " (should be 100)");
            }
        }
    }
}

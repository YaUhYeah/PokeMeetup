// BiomeRenderer.java
package io.github.pokemeetup.managers;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.github.pokemeetup.system.gameplay.overworld.Chunk;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.biomes.Biome;
import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.TextureManager;

import java.util.Map;

public class BiomeRenderer {

    public enum Direction {
        NORTH, SOUTH, EAST, WEST
    }

    // Adjust the rendering logic to use the centralized TextureManager
    public void renderChunk(SpriteBatch batch, Chunk chunk) {
        for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
            for (int y = 0; y < Chunk.CHUNK_SIZE; y++) {
                float worldX = (chunk.getChunkX() * Chunk.CHUNK_SIZE + x) * World.TILE_SIZE;
                float worldY = (chunk.getChunkY() * Chunk.CHUNK_SIZE + y) * World.TILE_SIZE;

                Biome currentBiome = chunk.getBiome();
                int tileType = chunk.getTileType(x, y);

                // Fetch the texture from TextureManager instead of the biome
                TextureRegion tileTexture = TextureManager.getTileTexture(tileType);

                if (tileTexture != null) {
                    batch.draw(tileTexture, worldX, worldY, World.TILE_SIZE, World.TILE_SIZE);
                } else {
                    GameLogger.error("Missing texture for tile at (" + x + ", " + y + ") with tileType " + tileType);
                }
            }
        }
    }

    private BiomeType getNeighborBiomeType(int x, int y, Map<Direction, Biome> neighbors) {
        // Determine if the current tile is at the edge of the chunk
        if (x == 0 && neighbors.containsKey(Direction.WEST)) {
            return neighbors.get(Direction.WEST).getType();
        } else if (x == Chunk.CHUNK_SIZE - 1 && neighbors.containsKey(Direction.EAST)) {
            return neighbors.get(Direction.EAST).getType();
        } else if (y == 0 && neighbors.containsKey(Direction.SOUTH)) {
            return neighbors.get(Direction.SOUTH).getType();
        } else if (y == Chunk.CHUNK_SIZE - 1 && neighbors.containsKey(Direction.NORTH)) {
            return neighbors.get(Direction.NORTH).getType();
        }
        return null;
    }

}

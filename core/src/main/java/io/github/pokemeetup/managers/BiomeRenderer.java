// BiomeRenderer.java
package io.github.pokemeetup.managers;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.github.pokemeetup.system.gameplay.overworld.Chunk;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.textures.TextureManager;

public class BiomeRenderer {

    public enum Direction {
        NORTH, SOUTH, EAST, WEST
    }

    public void renderChunk(SpriteBatch batch, Chunk chunk) {
        for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
            for (int y = 0; y < Chunk.CHUNK_SIZE; y++) {
                float worldX = (chunk.getChunkX() * Chunk.CHUNK_SIZE + x) * World.TILE_SIZE;
                float worldY = (chunk.getChunkY() * Chunk.CHUNK_SIZE + y) * World.TILE_SIZE;

                int tileType = chunk.getTileType(x, y);
                TextureRegion tileTexture = TextureManager.getTileTexture(tileType);

                if (tileTexture != null) {
                    batch.draw(tileTexture, worldX, worldY, World.TILE_SIZE, World.TILE_SIZE);
                } else {
//                    GameLogger.error("Missing texture for tile at (" + x + ", " + y + ") with tileType " + tileType);
                }
            }
        }
    }
}

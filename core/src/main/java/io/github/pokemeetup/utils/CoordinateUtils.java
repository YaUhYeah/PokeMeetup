// CoordinateUtils.java

package io.github.pokemeetup.utils;

import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.Chunk;

public class CoordinateUtils {

    // Convert world coordinates to chunk coordinates
    public static Vector2 worldToChunk(float worldX, float worldY) {
        int chunkX = (int) Math.floor((worldX - World.ORIGIN_X) / (Chunk.CHUNK_SIZE * World.TILE_SIZE));
        int chunkY = (int) Math.floor((worldY - World.ORIGIN_Y) / (Chunk.CHUNK_SIZE * World.TILE_SIZE));
        return new Vector2(chunkX, chunkY);
    }

    // Convert chunk coordinates to world coordinates (origin of chunk)
    public static Vector2 chunkToWorld(int chunkX, int chunkY) {
        float worldX = chunkX * Chunk.CHUNK_SIZE * World.TILE_SIZE + World.ORIGIN_X;
        float worldY = chunkY * Chunk.CHUNK_SIZE * World.TILE_SIZE + World.ORIGIN_Y;
        return new Vector2(worldX, worldY);
    }

    // Convert world coordinates to tile coordinates
    public static Vector2 worldToTile(float worldX, float worldY) {
        int tileX = (int) Math.floor((worldX - World.ORIGIN_X) / World.TILE_SIZE);
        int tileY = (int) Math.floor((worldY - World.ORIGIN_Y) / World.TILE_SIZE);
        return new Vector2(tileX, tileY);
    }

    // Convert tile coordinates to world coordinates
    public static Vector2 tileToWorld(int tileX, int tileY) {
        float worldX = tileX * World.TILE_SIZE + World.ORIGIN_X;
        float worldY = tileY * World.TILE_SIZE + World.ORIGIN_Y;
        return new Vector2(worldX, worldY);
    }
}

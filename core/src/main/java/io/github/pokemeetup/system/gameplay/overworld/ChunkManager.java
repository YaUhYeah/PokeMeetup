package io.github.pokemeetup.system.gameplay.overworld;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import java.util.*;

import static io.github.pokemeetup.system.gameplay.overworld.World.TILE_SIZE;

public class ChunkManager {
    public static final int CHUNK_SIZE = 16;
    public static final float CHUNK_PIXEL_SIZE = CHUNK_SIZE * TILE_SIZE;
    private static final int LOAD_RADIUS = 3;
    public static final float VISIBILITY_BUFFER = 2f * CHUNK_PIXEL_SIZE;

    public static boolean isChunkVisible(Vector2 chunkPos, Rectangle viewBounds) {
        // Convert chunk position to world coordinates
        float chunkWorldX = chunkPos.x * CHUNK_PIXEL_SIZE;
        float chunkWorldY = chunkPos.y * CHUNK_PIXEL_SIZE;

        // Create chunk bounds with buffer for smoother loading
        Rectangle chunkBounds = new Rectangle(
            chunkWorldX - VISIBILITY_BUFFER,
            chunkWorldY - VISIBILITY_BUFFER,
            CHUNK_PIXEL_SIZE + (VISIBILITY_BUFFER * 2),
            CHUNK_PIXEL_SIZE + (VISIBILITY_BUFFER * 2)
        );

        return viewBounds.overlaps(chunkBounds);
    }

    public static Rectangle calculateViewBounds(float centerX, float centerY, float viewportWidth, float viewportHeight) {
        return new Rectangle(
            centerX - (viewportWidth / 2),
            centerY - (viewportHeight / 2),
            viewportWidth + VISIBILITY_BUFFER * 2,
            viewportHeight + VISIBILITY_BUFFER * 2
        );
    }

    public static Vector2 getChunkPosition(float worldX, float worldY) {
        int chunkX = Math.floorDiv((int)worldX, CHUNK_SIZE);
        int chunkY = Math.floorDiv((int)worldY, CHUNK_SIZE);
        return new Vector2(chunkX, chunkY);
    }

    public static Set<Vector2> getChunksToLoad(Vector2 playerPosition, Rectangle viewBounds) {
        Set<Vector2> chunksToLoad = new HashSet<>();
        Vector2 playerChunkPos = getChunkPosition(playerPosition.x, playerPosition.y);

        // Load chunks in a square around player
        for (int dx = -LOAD_RADIUS; dx <= LOAD_RADIUS; dx++) {
            for (int dy = -LOAD_RADIUS; dy <= LOAD_RADIUS; dy++) {
                Vector2 chunkPos = new Vector2(
                    playerChunkPos.x + dx,
                    playerChunkPos.y + dy
                );

                // Only add chunks that are visible or within load radius
                float distanceSquared = playerChunkPos.dst2(chunkPos);
                if (distanceSquared <= LOAD_RADIUS * LOAD_RADIUS || isChunkVisible(chunkPos, viewBounds)) {
                    chunksToLoad.add(chunkPos);
                }
            }
        }

        return chunksToLoad;
    }
}

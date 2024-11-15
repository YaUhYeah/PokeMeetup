    // BiomeRenderer.java
    package io.github.pokemeetup.managers;

    import com.badlogic.gdx.graphics.g2d.SpriteBatch;
    import com.badlogic.gdx.graphics.g2d.TextureRegion;
    import io.github.pokemeetup.system.gameplay.overworld.Chunk;
    import io.github.pokemeetup.system.gameplay.overworld.World;
    import io.github.pokemeetup.system.gameplay.overworld.biomes.Biome;
    import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;
    import io.github.pokemeetup.utils.GameLogger;
    import io.github.pokemeetup.utils.textures.TextureManager;

    import java.util.Map;

    public class BiomeRenderer {

        public enum Direction {
            NORTH, SOUTH, EAST, WEST
        }

        private static final boolean DEBUG_RENDERING = false;

        public void renderChunk(SpriteBatch batch, Chunk chunk) {
            int chunkX = chunk.getChunkX();
            int chunkY = chunk.getChunkY();

            for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
                for (int y = 0; y < Chunk.CHUNK_SIZE; y++) {
                    float worldX = (chunkX * Chunk.CHUNK_SIZE + x) * World.TILE_SIZE;
                    float worldY = (chunkY * Chunk.CHUNK_SIZE + y) * World.TILE_SIZE;

                    int tileType = chunk.getTileType(x, y);
                    TextureRegion tileTexture = TextureManager.getTileTexture(tileType);

                    if (tileTexture != null) {
                        batch.draw(tileTexture, worldX, worldY, World.TILE_SIZE, World.TILE_SIZE);

                        if (DEBUG_RENDERING && x == 0 && y == 0) {
                            GameLogger.info(String.format(
                                "Rendering tile %d at world pos (%.0f,%.0f) in chunk (%d,%d)",
                                tileType, worldX, worldY, chunkX, chunkY
                            ));
                        }
                    } else {
                        GameLogger.error(String.format(
                            "Missing texture for tile %d at (%d,%d) in chunk (%d,%d)",
                            tileType, x, y, chunkX, chunkY
                        ));
                    }
                }
            }
        }
    }

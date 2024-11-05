package io.github.pokemeetup.blocks;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.system.gameplay.overworld.Chunk;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.data.WorldData;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.storage.BlockSaveData;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BlockManager {
    private final Map<Vector2, PlaceableBlock> placedBlocks = new ConcurrentHashMap<>();
    private final TextureAtlas atlas;
    public boolean placeBlock(PlaceableBlock.BlockType type, int tileX, int tileY, World world) {
        Vector2 pos = new Vector2(tileX, tileY);

        // Additional validation
        if (tileX < 0 || tileY < 0 || tileX >= World.WORLD_SIZE || tileY >= World.WORLD_SIZE) {
            return false;
        }

        // Check if tile is occupied by blocks or entities
        if (placedBlocks.containsKey(pos) || !world.isPassable(tileX, tileY)) {
            return false;
        }

        // Get block texture
        TextureRegion texture = atlas.findRegion(type.id);
        if (texture == null) {
            GameLogger.error("Missing texture for block: " + type.id);
            return false;
        }

        PlaceableBlock block = new PlaceableBlock(type, pos, texture);
        placedBlocks.put(pos, block);

        // Notify network if in multiplayer
        if (world.getGameClient() != null && !world.getGameClient().isSinglePlayer()) {
            NetworkProtocol.WorldObjectUpdate update = new NetworkProtocol.WorldObjectUpdate();
            update.objectId = block.getId();
            update.type = NetworkProtocol.NetworkObjectUpdateType.ADD;
            update.x = tileX;
            update.y = tileY;
            world.getGameClient().sendWorldObjectUpdate(update);
        }

        GameLogger.info("Placed " + type.id + " at " + tileX + "," + tileY);
        return true;
    }
    public BlockManager(TextureAtlas atlas) {
        this.atlas = atlas;
    }

    // ... existing code ...

    public void saveBlocks(WorldData worldData) {
        BlockSaveData saveData = new BlockSaveData();

        for (Map.Entry<Vector2, PlaceableBlock> entry : placedBlocks.entrySet()) {
            Vector2 pos = entry.getKey();
            PlaceableBlock block = entry.getValue();

            // Calculate chunk key
            int chunkX = (int) Math.floor(pos.x / Chunk.CHUNK_SIZE);
            int chunkY = (int) Math.floor(pos.y / Chunk.CHUNK_SIZE);
            String chunkKey = chunkX + "," + chunkY;

            BlockSaveData.BlockData blockData = new BlockSaveData.BlockData(
                block.getId(),
                (int) pos.x,
                (int) pos.y
            );

            // Save extra data for interactive blocks
            if (block.isInteractable()) {
                // Example: save crafting table contents
                if (block.getId().equals("crafting_table")) {
                    blockData.extraData.put("expanded", true);
                }
            }

            saveData.addBlock(chunkKey, blockData);
        }

        worldData.setBlockData(saveData);
    }

    public void loadBlocks(WorldData worldData) {
        placedBlocks.clear();

        BlockSaveData saveData = worldData.getBlockData();
        if (saveData == null) return;

        for (Map.Entry<String, List<BlockSaveData.BlockData>> entry :
            saveData.getPlacedBlocks().entrySet()) {
            for (BlockSaveData.BlockData blockData : entry.getValue()) {
                Vector2 pos = new Vector2(blockData.x, blockData.y);

                PlaceableBlock.BlockType type = PlaceableBlock.BlockType.valueOf(
                    blockData.type.toUpperCase()
                );

                TextureRegion texture = atlas.findRegion(type.id);
                if (texture == null) continue;

                PlaceableBlock block = new PlaceableBlock(type, pos, texture);

                // Restore extra data
                if (blockData.extraData != null && block.isInteractable()) {
                    // Example: restore crafting table state
                    if (block.getId().equals("crafting_table") &&
                        Boolean.TRUE.equals(blockData.extraData.get("expanded"))) {
                        // Set expanded crafting state
                    }
                }

                placedBlocks.put(pos, block);
            }
        }

        GameLogger.info("Loaded " + placedBlocks.size() + " blocks from save data");
    }


    public void removeBlock(int tileX, int tileY) {
        Vector2 pos = new Vector2(tileX, tileY);
        PlaceableBlock removed = placedBlocks.remove(pos);
        if (removed != null) {
            GameLogger.info("Removed " + removed.getId() + " at " + tileX + "," + tileY);
        }
    }

    public PlaceableBlock getBlockAt(int tileX, int tileY) {
        return placedBlocks.get(new Vector2(tileX, tileY));
    }

    public void render(SpriteBatch batch) {
        for (PlaceableBlock block : placedBlocks.values()) {
            batch.draw(block.getTexture(),
                block.getPosition().x * World.TILE_SIZE,
                block.getPosition().y * World.TILE_SIZE,
                World.TILE_SIZE, World.TILE_SIZE);
        }
    }
}

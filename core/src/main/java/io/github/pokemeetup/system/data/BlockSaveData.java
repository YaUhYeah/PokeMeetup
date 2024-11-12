package io.github.pokemeetup.system.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BlockSaveData {
    private Map<String, List<BlockData>> placedBlocks = new HashMap<>();

    public BlockSaveData() {
        this.placedBlocks = new HashMap<>();
    }

    public Map<String, List<BlockData>> getPlacedBlocks() {
        if (placedBlocks == null) {
            placedBlocks = new HashMap<>();
        }
        return placedBlocks;
    }

    public void setPlacedBlocks(Map<String, List<BlockData>> blocks) {
        this.placedBlocks = blocks != null ? blocks : new HashMap<>();
    }

    public void addBlock(String chunkKey, BlockData block) {
        placedBlocks.computeIfAbsent(chunkKey, k -> new ArrayList<>()).add(block);
    }

    public List<BlockData> getBlocksInChunk(String chunkKey) {
        return placedBlocks.getOrDefault(chunkKey, new ArrayList<>());
    }

    public static class BlockData {
        public String type;
        public int x;
        public int y;
        public Map<String, Object> extraData;

        public BlockData() {
        }

        public BlockData(String type, int x, int y) {
            this.type = type;
            this.x = x;
            this.y = y;
            this.extraData = new HashMap<>();
        }
    }
}

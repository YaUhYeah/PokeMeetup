package io.github.pokemeetup.blocks;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.utils.GameLogger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PlaceableBlock {
    private String id;
    private TextureRegion texture;
    private Vector2 position;
    private boolean isInteractable;
    private BlockInteraction interaction;
    private boolean isBreakable = true;
    private float hardness = 1.0f; // Time in seconds to break the block

    public enum BlockType {
        CRAFTING_TABLE("crafting_table", true, true, 2.0f),
        CHEST("chest", true, true, 2.0f),
        DIRT("dirt", false, true, 0.5f),
        STONE("stone", false, true, 3.0f),
        WOOD("wood", false, true, 1.5f),
        BEDROCK("bedrock", false, false, -1f);

        public final String id;
        private final boolean interactive;
        private final boolean breakable;
        private final float hardness;

        public String getId() {
            return id;
        }

        public boolean isInteractive() {
            return interactive;
        }

        public boolean isBreakable() {
            return breakable;
        }

        public float getHardness() {
            return hardness;
        }

        BlockType(String id, boolean interactive, boolean breakable, float hardness) {
            this.id = id;
            this.interactive = interactive;
            this.breakable = breakable;
            this.hardness = hardness;
        }
    }

    public interface BlockInteraction {
        void onInteract(Player player);
    }

    public PlaceableBlock(BlockType type, Vector2 position, TextureRegion texture) {
        this.id = type.id;
        this.position = position;
        this.texture = texture;
        this.isInteractable = type.interactive;
        this.isBreakable = type.breakable;
        this.hardness = type.hardness;

        // Set up block-specific interactions
        if (type == BlockType.CRAFTING_TABLE) {
            this.interaction = player -> {
                // Open 3x3 crafting grid
                player.openCraftingTable();
            };
        } else if (type == BlockType.CHEST) {
            this.interaction = player -> {
                // Open chest inventory
                player.openChestInventory(this);
            };
        }
    }

    public Vector2 getPosition() { return position; }
    public TextureRegion getTexture() { return texture; }
    public boolean isInteractable() { return isInteractable; }
    public String getId() { return id; }
    public boolean isBreakable() { return isBreakable; }
    public float getHardness() { return hardness; }

    public void interact(Player player) {
        if (isInteractable && interaction != null) {
            interaction.onInteract(player);
        }
    }
}


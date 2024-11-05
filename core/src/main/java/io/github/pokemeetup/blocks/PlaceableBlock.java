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

    public enum BlockType {
        CRAFTING_TABLE("crafting_table", true),
        CHEST("chest", true);

        public final String id;
        private final boolean interactive;

        public String getId() {
            return id;
        }

        public boolean isInteractive() {
            return interactive;
        }

        BlockType(String id, boolean interactive) {
            this.id = id;
            this.interactive = interactive;
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

        // Set up block-specific interactions
        if (type == BlockType.CRAFTING_TABLE) {
            this.interaction = player -> {
                // Open 3x3 crafting grid
//                player.openExpandedCrafting();
            };
        }
    }

    public Vector2 getPosition() { return position; }
    public TextureRegion getTexture() { return texture; }
    public boolean isInteractable() { return isInteractable; }
    public String getId() { return id; }

    public void interact(Player player) {
        if (isInteractable && interaction != null) {
            interaction.onInteract(player);
        }
    }
}


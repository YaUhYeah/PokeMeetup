package io.github.pokemeetup.screens.otherui;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.blocks.PlaceableBlock;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.gameplay.inventory.Inventory;
import io.github.pokemeetup.system.gameplay.inventory.Item;
import io.github.pokemeetup.utils.GameLogger;

import java.util.HashMap;
import java.util.Map;

public class CraftingUI {
    private final Player player;
    private final TextureAtlas atlas;
    private final Inventory craftingGrid;
    private boolean visible;
    private Vector2 position;
    
    private static final Map<String, PlaceableBlock.BlockType> RECIPES = new HashMap<>();
    static {
        // Define basic crafting recipes
        RECIPES.put("wood,wood,wood,wood", PlaceableBlock.BlockType.CRAFTING_TABLE);
        RECIPES.put("stone,stone,stone,stone", PlaceableBlock.BlockType.CHEST);
    }

    public CraftingUI(Player player, TextureAtlas atlas) {
        this.player = player;
        this.atlas = atlas;
        this.craftingGrid = new Inventory(9); // 3x3 grid
        this.position = new Vector2(100, 100); // Default position
        this.visible = false;
    }

    public void show() {
        visible = true;
    }

    public void hide() {
        visible = false;
        // Return items to player inventory
        for (Item item : craftingGrid.getAllItems()) {
            if (item != null) {
                player.getInventory().addItem(item);
            }
        }
        craftingGrid.clear();
    }

    public void update() {
        if (!visible) return;

        // Check for valid recipe
        String recipe = getCurrentRecipe();
        PlaceableBlock.BlockType result = RECIPES.get(recipe);
        
        if (result != null) {
            // Create crafted item
            // TODO: Create item from block type
            GameLogger.info("Crafted: " + result.getId());
            craftingGrid.clear();
        }
    }

    private String getCurrentRecipe() {
        StringBuilder recipe = new StringBuilder();
        for (Item item : craftingGrid.getAllItems()) {
            if (item != null) {
                recipe.append(item.getId()).append(",");
            }
        }
        return recipe.toString().replaceAll(",$", "");
    }

    public void render(SpriteBatch batch) {
        if (!visible) return;

        // Render crafting grid background
        // TODO: Draw 3x3 grid background

        // Render items in grid
        for (int i = 0; i < craftingGrid.getSize(); i++) {
            Item item = craftingGrid.getItem(i);
            if (item != null) {
                float x = position.x + (i % 3) * 32;
                float y = position.y + (i / 3) * 32;
                // TODO: Draw item texture
            }
        }
    }

    public boolean isVisible() {
        return visible;
    }

    public void addItem(Item item, int slot) {
        if (slot >= 0 && slot < craftingGrid.getSize()) {
            craftingGrid.setItem(slot, item);
        }
    }

    public Item removeItem(int slot) {
        return craftingGrid.removeItem(slot);
    }
}

package io.github.pokemeetup.screens.otherui;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.blocks.PlaceableBlock;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.gameplay.inventory.Inventory;
import io.github.pokemeetup.system.gameplay.inventory.Item;
import io.github.pokemeetup.utils.GameLogger;

public class ChestUI {
    private final Player player;
    private final TextureAtlas atlas;
    private final Inventory chestInventory;
    private boolean visible;
    private Vector2 position;
    private PlaceableBlock chestBlock;

    public ChestUI(Player player, TextureAtlas atlas) {
        this.player = player;
        this.atlas = atlas;
        this.chestInventory = new Inventory(27); // 3x9 chest inventory
        this.position = new Vector2(100, 100); // Default position
        this.visible = false;
    }

    public void show(PlaceableBlock chest) {
        this.chestBlock = chest;
        visible = true;
        // Load chest contents from block data
        // TODO: Implement chest data persistence
    }

    public void hide() {
        visible = false;
        // Save chest contents to block data
        if (chestBlock != null) {
            // TODO: Save inventory to block data
            chestBlock = null;
        }
    }

    public void update() {
        if (!visible) return;
        // Handle item transfers between player and chest inventories
    }

    public void render(SpriteBatch batch) {
        if (!visible) return;

        // Render chest inventory background
        // TODO: Draw chest UI background

        // Render items in chest
        for (int i = 0; i < chestInventory.getSize(); i++) {
            Item item = chestInventory.getItem(i);
            if (item != null) {
                float x = position.x + (i % 9) * 32;
                float y = position.y + (i / 9) * 32;
                // TODO: Draw item texture
            }
        }

        // Render player inventory below chest
        // TODO: Draw player inventory section
    }

    public boolean isVisible() {
        return visible;
    }

    public void addItem(Item item, int slot) {
        if (slot >= 0 && slot < chestInventory.getSize()) {
            chestInventory.setItem(slot, item);
        }
    }

    public Item removeItem(int slot) {
        return chestInventory.removeItem(slot);
    }

    public void transferToPlayer(int chestSlot) {
        Item item = chestInventory.getItem(chestSlot);
        if (item != null && player.getInventory().hasSpace()) {
            chestInventory.removeItem(chestSlot);
            player.getInventory().addItem(item);
        }
    }

    public void transferToChest(int playerSlot) {
        Item item = player.getInventory().getItem(playerSlot);
        if (item != null && chestInventory.hasSpace()) {
            player.getInventory().removeItem(playerSlot);
            chestInventory.addItem(item);
        }
    }
}

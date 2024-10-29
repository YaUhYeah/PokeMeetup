package io.github.pokemeetup.system.gameplay.inventory;

import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class ItemManager {
    private static Map<String, Item> items = new HashMap<>();
    private static boolean initialized = false;

    public static void initialize(TextureAtlas atlas) {
        if (initialized) {
            System.out.println("ItemManager already initialized");
            return;
        }

        System.out.println("Initializing ItemManager with items...");

        // Debug texture loading
        TextureRegion pokeballTexture = atlas.findRegion("pokeball_item");
        TextureRegion potionTexture = atlas.findRegion("potion_item");
        TextureRegion stickTexture = atlas.findRegion("stick_item");
        TextureRegion elixirTexture = atlas.findRegion("elixir_item");

        // Log texture loading results
        System.out.println("Loaded textures - " +
            "pokeball: " + (pokeballTexture != null) + ", " +
            "potion: " + (potionTexture != null) + ", " +
            "stick: " + (stickTexture != null) + ", " +
            "elixir: " + (elixirTexture != null));

        // Define items with proper case matching saved data
        items.put("Pokeball", new Item("Pokeball", "pokeball_item", pokeballTexture));
        items.put("Potion", new Item("Potion", "potion_item", potionTexture));
        items.put("Stick", new Item("Stick", "stick_item", stickTexture));
        items.put("Elixir", new Item("Elixir", "elixir_item", elixirTexture));

        initialized = true;
        System.out.println("Initialized ItemManager with items: " + items.keySet());
    }
    public static Item getItem(String itemName) {
        if (!initialized) {
            System.err.println("ItemManager not initialized when requesting item: " + itemName);
            return null;
        }

        Item item = items.get(itemName);
        if (item == null) {
            System.err.println("Item not found: " + itemName + ". Available items: " + items.keySet());
        }
        return item;
    }

    public static Collection<String> getAllItemNames() {
        return items.keySet();
    }

    public static TextureRegion getTexture(String iconName) {
        Item item = items.get(iconName);
        return item != null ? item.getIcon() : null;
    }

    // Add method to check initialization status
    public static boolean isInitialized() {
        return initialized;

    }
}

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

        addItem("Pokeball", "pokeball_item", atlas);
        addItem("Potion", "potion_item", atlas);
        addItem("Stick", "stick_item", atlas);
        addItem("Elixir", "elixir_item", atlas);
        addItem("CraftingTable", "craftingtable_item", atlas);

        initialized = true;

        // Debug each item to ensure all textures loaded
        items.forEach((name, item) -> {
            System.out.println("Item loaded: " + name + ", Texture exists: " + (item.getIcon() != null));
        });
    }


    private static void addItem(String name, String regionName, TextureAtlas atlas) {
        TextureRegion texture = atlas.findRegion(regionName);
        if (texture == null) {
            System.err.println("Warning: Texture for item '" + name + "' is missing. Using placeholder.");
            texture = atlas.findRegion("missing_texture"); // Add "missing_texture" to your atlas
        }
        items.put(name, new Item(name, regionName, texture));
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

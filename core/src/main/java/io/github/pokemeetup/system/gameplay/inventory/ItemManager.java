package io.github.pokemeetup.system.gameplay.inventory;

import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.github.pokemeetup.system.gameplay.inventory.Item;
import io.github.pokemeetup.utils.GameLogger;

import java.util.*;

public class ItemManager {
    private static final Map<String, Item> items = new HashMap<>();
    private static boolean initialized = false;
    private static final String DEFAULT_TEXTURE = "missing_texture";

    // Define standard item IDs to ensure consistency
    public static final class ItemIDs {
        public static final String POTION = "potion";
        public static final String ELIXIR = "elixir";
        public static final String POKEBALL = "pokeball";
        public static final String STICK = "stick";
        public static final String CRAFTING_TABLE = "craftingtable";
    }

    public static void initialize(TextureAtlas atlas) {
        if (initialized) {
            GameLogger.info("ItemManager already initialized");
            return;
        }

        if (atlas == null) {
            GameLogger.error("Cannot initialize ItemManager - atlas is null");
            return;
        }

        GameLogger.info("Initializing ItemManager with atlas...");
        logAvailableRegions(atlas);

        // Define standard items with consistent IDs
        Map<String, String> standardItems = new HashMap<>();
        standardItems.put(ItemIDs.POTION, "potion_item");
        standardItems.put(ItemIDs.ELIXIR, "elixir_item");
        standardItems.put(ItemIDs.POKEBALL, "pokeball_item");
        standardItems.put(ItemIDs.STICK, "stick_item");
        standardItems.put(ItemIDs.CRAFTING_TABLE, "craftingtable_item");

        // Initialize all standard items
        for (Map.Entry<String, String> entry : standardItems.entrySet()) {
            String itemId = entry.getKey();
            String textureKey = entry.getValue();

            TextureRegion texture = getTextureWithFallbacks(atlas, textureKey, itemId);
            if (texture != null) {
                Item item = new Item(itemId, textureKey, texture);
                items.put(itemId, item);
                GameLogger.info(String.format("Initialized item: %s with texture %s", itemId, textureKey));
            }
        }

        initialized = true;
        validateItems();
        logInitializationSummary();
    }

    private static TextureRegion getTextureWithFallbacks(TextureAtlas atlas, String primaryKey, String itemId) {
        TextureRegion texture = null;
        String[] attempts = new String[]{
            primaryKey,
            itemId + "_item",
            itemId.toLowerCase() + "_item",
            itemId,
            itemId.toLowerCase(),
            DEFAULT_TEXTURE
        };

        for (String key : attempts) {
            texture = atlas.findRegion(key);
            if (texture != null) {
                GameLogger.info(String.format("Found texture for %s using key: %s", itemId, key));
                return texture;
            }
        }

        GameLogger.error(String.format("Failed to find any texture for item: %s", itemId));
        return null;
    }

    public static Item getItem(String itemId) {
        if (!initialized) {
            GameLogger.error("Attempting to get item before ItemManager initialization");
            return null;
        }

        if (itemId == null) {
            GameLogger.error("Null itemId provided to getItem");
            return null;
        }

        // Normalize item ID to match our standard format
        String normalizedId = itemId.toLowerCase().replace("_item", "");
        Item baseItem = items.get(normalizedId);

        if (baseItem == null) {
            GameLogger.error(String.format("No item found with ID: %s (normalized from: %s)",
                normalizedId, itemId));
            return null;
        }

        if (baseItem.getIcon() == null) {
            GameLogger.error(String.format("Item found but missing texture: %s", itemId));
            return null;
        }

        return baseItem.copy();
    }

    public static void validateItems() {
        GameLogger.info("Validating initialized items...");
        for (Map.Entry<String, Item> entry : items.entrySet()) {
            Item item = entry.getValue();
            if (item.getIcon() == null) {
                GameLogger.error(String.format("Item %s is missing texture", entry.getKey()));
                continue;
            }

            if (!item.getIcon().getTexture().isManaged()) {
                GameLogger.error(String.format("Item %s has invalid texture state", entry.getKey()));
            }

            GameLogger.info(String.format("Validated item: %s (texture: %dx%d)",
                entry.getKey(),
                item.getIcon().getRegionWidth(),
                item.getIcon().getRegionHeight()));
        }
    }

    public static Collection<String> getAllItemNames() {
        if (!initialized) {
            GameLogger.error("Attempting to get item names before initialization");
            return Collections.emptyList();
        }
        return new ArrayList<>(items.keySet());
    }

    public static boolean isInitialized() {
        return initialized;
    }

    // Helper methods
    private static void logAvailableRegions(TextureAtlas atlas) {
        GameLogger.info("Available regions in atlas:");
        for (TextureAtlas.AtlasRegion region : atlas.getRegions()) {
            GameLogger.info(String.format("- %s (%dx%d)",
                region.name, region.getRegionWidth(), region.getRegionHeight()));
        }
    }

    private static void logInitializationSummary() {
        GameLogger.info(String.format("ItemManager initialization complete: %d items loaded",
            items.size()));
        GameLogger.info("Loaded items: " + String.join(", ", items.keySet()));
    }
}

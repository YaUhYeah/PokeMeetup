package io.github.pokemeetup.system.gameplay.inventory;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.textures.TextureManager;

import java.util.UUID;

public class Item { public static boolean verifyTexture(String itemId) {
    String textureKey = itemId.toLowerCase() + "_item";
    TextureRegion texture = TextureManager.items.findRegion(textureKey);
    if (texture == null) {
        GameLogger.error("Missing texture for item: " + textureKey);
        return false;
    }
    return true;
}

    public static final int MAX_STACK_SIZE = 64;
    private UUID uuid; // Unique identifier for each Item instance
    private String name;
    private String iconName; // Name of the texture region
    private transient TextureRegion icon; // Marked transient to avoid serialization
    private int count = 1;
    public Item(String name) {
        this.name = name;
        this.uuid = UUID.randomUUID();

        // Get existing item to copy icon info
        Item template = ItemManager.getItem(name);
        if (template != null) {
            this.iconName = template.getIconName();
            this.icon = template.getIcon();
        } else {
            // Log error but don't crash
            GameLogger.error("Failed to find template for item: " + name);
            this.iconName = "missing";
            this.icon = TextureManager.items.findRegion("stick_item");
        }
        this.count = 1;
    }

    // Constructor
    public Item(String name, String iconName, TextureRegion icon) {
        this.name = name;
        this.iconName = iconName;
        this.icon = icon;
        this.uuid = UUID.randomUUID(); // Assign a unique ID upon creation
    }

    public Item(String name, String iconName, TextureRegion icon, int count) {
        this.name = name;
        this.iconName = iconName;
        this.icon = icon;
        this.count = count;
        this.uuid = UUID.randomUUID(); // Assign a unique ID upon creation
    }

    // Empty constructor for serialization
    public Item() {
        this.uuid = UUID.randomUUID();
    }

    // Copy constructor
    public Item(Item other) {
        this.name = other.name;
        this.count = other.count;
        this.icon = other.icon;
        this.uuid = other.uuid; // Preserve UUID
    }

    public void decrementCount(int amount) {
        if (amount < 0) {
            GameLogger.error("Attempted to decrement by negative amount: " + amount);
            return;
        }
        this.count = Math.max(0, this.count - amount);
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    // Add getters/setters for count
    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = Math.min(count, MAX_STACK_SIZE);
    }


    public boolean isEmpty() {
        return this.count <= 0;
    }

    public boolean canStackWith(Item other) {
        return other != null &&
            this.getName().equals(other.getName()) &&
            (this.count + other.count) <= MAX_STACK_SIZE;
    }

    public int addToStack(int amount) {
        int spaceLeft = MAX_STACK_SIZE - count;
        int amountToAdd = Math.min(amount, spaceLeft);
        count += amountToAdd;
        return amount - amountToAdd; // Return leftover amount
    }

    public String getName() {
        return name;
    }

    public String getIconName() {
        return iconName;
    }

    public void setIcon(TextureRegion icon) {
        if (icon == null) {
            GameLogger.error("Attempted to set null icon for item: " + name);
            return;
        }
        this.icon = icon;
        GameLogger.info("Set icon for item: " + name);
    }

    public TextureRegion getIcon() {
        if (icon == null) {
            // Try to load from TextureManager
            icon = TextureManager.items.findRegion(name.toLowerCase() + "_item");
            if (icon != null) {
                GameLogger.info("Loaded icon for " + name + " from TextureManager");
            } else {
                GameLogger.error("Could not find icon for " + name);
            }
        }
        return icon;
    }

    public Item copy() {
        return new Item(this);
    }

    @Override
    public String toString() {
        return "Item{" +
            "name='" + name + '\'' +
            ", count=" + count +
            ", uuid=" + uuid +
            '}';
    }

}

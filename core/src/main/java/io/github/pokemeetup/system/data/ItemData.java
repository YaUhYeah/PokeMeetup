package io.github.pokemeetup.system.data;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Json;
import io.github.pokemeetup.system.gameplay.inventory.Item;
import io.github.pokemeetup.system.gameplay.inventory.ItemManager;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.TextureManager;

import java.util.UUID;

public class ItemData {
    private String itemId;
    private int count;
    private UUID uuid;

    // Default constructor for Json serialization
    public ItemData() {
        this.uuid = UUID.randomUUID(); // Assign a unique ID upon creation
    }

    public ItemData(String itemId, int count, UUID uuid) {
        this.itemId = itemId;
        this.count = count;
        this.uuid = uuid;
    }

    public ItemData(String itemId, int count) {
        this.itemId = itemId;
        this.count = count;
        this.uuid = UUID.randomUUID(); // Always generate a new UUID
    }

    // Update the icon constructor to include count
    public ItemData(String itemId, String iconName, TextureRegion icon) {
        this.itemId = itemId;
        this.count = 1; // Default to 1 if not specified
        this.uuid = UUID.randomUUID();
    }

    private String normalizeItemId(String itemId) {
        // Convert to lowercase and ensure _item suffix
        String normalized = itemId.toLowerCase();
        if (!normalized.endsWith("_item")) {
            normalized += "_item";
        }
        return normalized;
    }

    // Getters and Setters
    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        // Verify texture exists
        String normalizedId = normalizeItemId(itemId);
        TextureRegion texture = TextureManager.items.findRegion(normalizedId);
        if (texture == null) {
            GameLogger.error("No texture found for item: " + normalizedId);
        }
        this.itemId = itemId;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int newCount) {
        if (newCount < 0) {
            GameLogger.error("Attempted to set negative count: " + newCount);
            this.count = 0;
            return;
        }
        this.count = newCount;
    }

    public UUID getUuid() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public boolean verifyIntegrity() {
        // Adjust the integrity check as per your game logic
        // For example, if 'itemId' must not be null or empty
        if (itemId == null || itemId.trim().isEmpty()) {
            GameLogger.error("ItemData has null or empty itemId. Treating as valid if other fields are valid.");
            return false;
        }
        if (count <= 0) {
            GameLogger.error("ItemData has non-positive count. Treating as valid if other fields are valid.");
            // Decide whether to treat this as invalid or acceptable
            // return false;
        }
        if (uuid == null) {
            GameLogger.error("ItemData has null UUID. Generating new UUID.");
            uuid = UUID.randomUUID();
        }
        // If other fields are acceptable, return true
        return true;
    }

    // Utility method to check if two items can stack
    public boolean canStackWith(ItemData other) {
        return this.itemId.equals(other.itemId);
    }

    // Method to add to stack
    public int addToStack(int additionalCount) {
        this.count += additionalCount;
        // Assuming a maximum stack size, e.g., 64
        int maxStackSize = 64;
        if (this.count > maxStackSize) {
            int leftover = this.count - maxStackSize;
            this.count = maxStackSize;
            return leftover;
        }
        return 0;
    }

    public boolean isEmpty() {
        return this.count <= 0;
    }

    /**
     * Creates a deep copy of this ItemData with a new UUID.
     *
     * @return A new ItemData instance with the same itemId and count, but a new UUID.
     */
    public ItemData copyWithUUID() {
        return new ItemData(this.itemId, this.count, UUID.randomUUID());
    }

    public ItemData copy() {
        return new ItemData(this.itemId, this.count, this.uuid);
    }

    @Override
    public String toString() {
        return "ItemData{" +
            "itemId='" + itemId + '\'' +
            ", count=" + count +
            ", uuid=" + uuid +
            '}';
    }

    public void decrementCount(int amount) {
        if (amount < 0) {
            GameLogger.error("Attempted to decrement by negative amount: " + amount);
            return;
        }
        this.count = Math.max(0, this.count - amount);
    }

    public void writeJson(Json json) {
        json.writeObjectStart();
        json.writeValue("itemId", itemId);
        json.writeValue("count", count);
        json.writeValue("uuid", uuid != null ? uuid.toString() : UUID.randomUUID().toString());
        json.writeObjectEnd();
    }

    public boolean isValid() {
        if (itemId == null || itemId.trim().isEmpty()) {
            return false;
        }
        if (count <= 0) {
            return false;
        }
        if (uuid == null) {
            uuid = UUID.randomUUID(); // Auto-generate UUID if missing
        }
        return ItemManager.getItem(itemId) != null; // Verify item exists in manager
    }

}

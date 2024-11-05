package io.github.pokemeetup.system.data;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.github.pokemeetup.system.gameplay.inventory.Item;
import io.github.pokemeetup.utils.GameLogger;

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
    }public ItemData(String itemId, int count) {
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



    // Getters and Setters
    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public int getCount() {
        return count;
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

    /**
     * Creates a deep copy of this ItemData, preserving the UUID.
     *
     * @return A new ItemData instance with the same itemId, count, and UUID.
     */
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
    public void setCount(int newCount) {
        if (newCount < 0) {
            GameLogger.error("Attempted to set negative count: " + newCount);
            this.count = 0;
            return;
        }
        this.count = newCount;
    }

    public void decrementCount(int amount) {
        if (amount < 0) {
            GameLogger.error("Attempted to decrement by negative amount: " + amount);
            return;
        }
        this.count = Math.max(0, this.count - amount);
    }

    // Add this validation method
    public boolean isValid() {
        return itemId != null && !itemId.isEmpty() &&
            count > 0 && count <= Item.MAX_STACK_SIZE &&
            uuid != null;
    }
}

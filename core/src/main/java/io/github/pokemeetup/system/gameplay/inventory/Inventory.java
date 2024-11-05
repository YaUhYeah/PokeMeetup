package io.github.pokemeetup.system.gameplay.inventory;

import io.github.pokemeetup.system.data.ItemData;
import io.github.pokemeetup.system.gameplay.inventory.crafting.CraftingSystem;
import io.github.pokemeetup.utils.GameLogger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class Inventory {
    public static final int INVENTORY_SIZE = 36; // Total slots available
    private final List<ItemData> items;
    private final Map<UUID, ItemData> itemTracker;
    private final Object inventoryLock = new Object();
    private CraftingSystem craftingSystem;
    private int selectedSlot;

    public Inventory() {
        this.craftingSystem = new CraftingSystem(this);

        this.items = new ArrayList<>(Collections.nCopies(INVENTORY_SIZE, null));
        this.itemTracker = new ConcurrentHashMap<>();
        this.selectedSlot = 0;
    }
    // Add these to the Inventory class

    /**
     * Checks if the inventory is completely empty.
     *
     * @return true if all slots are empty, false otherwise
     */
    public boolean isEmpty() {
        synchronized (inventoryLock) {
            return items.stream().allMatch(Objects::isNull);
        }
    }

    /**
     * Gets the number of empty slots in the inventory.
     *
     * @return count of empty slots
     */
    public int getEmptySlotCount() {
        synchronized (inventoryLock) {
            return (int) items.stream().filter(Objects::isNull).count();
        }
    }

    /**
     * Finds the first empty slot in the inventory.
     *
     * @return index of first empty slot, or -1 if inventory is full
     */
    public int getFirstEmptySlot() {
        synchronized (inventoryLock) {
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i) == null) {
                    return i;
                }
            }
            return -1;
        }
    }

    /**
     * Gets the total count of a specific item type in the inventory.
     *
     * @param itemId the item ID to count
     * @return total count of the specified item
     */
    public int getItemCount(String itemId) {
        synchronized (inventoryLock) {
            return items.stream()
                .filter(item -> item != null && item.getItemId().equals(itemId))
                .mapToInt(ItemData::getCount)
                .sum();
        }
    }

    public void clear() {
        synchronized (inventoryLock) {
            // Clear all slots
            for (int i = 0; i < INVENTORY_SIZE; i++) {
                items.set(i, null);
            }

            // Clear the tracker
            itemTracker.clear();

            // Reset selected slot
            selectedSlot = 0;

            // Reset crafting system if needed
            if (craftingSystem != null) {
                craftingSystem = new CraftingSystem(this);
            }

            GameLogger.info("Inventory cleared");
        }
    }

    public void removeItemAt(int index) {
        synchronized (inventoryLock) {
            if (index < 0 || index >= items.size()) {
                return;
            }

            ItemData removedItem = items.get(index);
            if (removedItem != null) {
                itemTracker.remove(removedItem.getUuid());
                items.set(index, null);
                GameLogger.info("Removed item from slot " + index + ": " + removedItem.getItemId());
            }
        }
    }

    public boolean canAddItem(ItemData item) {
        if (item == null) return false;

        synchronized (inventoryLock) {
            // First check for stacking possibilities
            for (ItemData existingItem : items) {
                if (existingItem != null && existingItem.getItemId().equals(item.getItemId())) {
                    int spaceAvailable = Item.MAX_STACK_SIZE - existingItem.getCount();
                    if (spaceAvailable >= item.getCount()) {
                        return true;
                    }
                }
            }

            // Then check for empty slots
            return items.stream().anyMatch(Objects::isNull);
        }
    }

    public boolean addItem(ItemData newItem) {
        if (newItem == null || newItem.isEmpty()) {
            GameLogger.error("Cannot add null or empty item to inventory.");
            return false;
        }

        synchronized (inventoryLock) {
            // Try to stack with existing items
            for (int i = 0; i < items.size(); i++) {
                ItemData existingItem = items.get(i);
                if (existingItem != null && existingItem.canStackWith(newItem)) {
                    int spaceAvailable = Item.MAX_STACK_SIZE - existingItem.getCount();
                    if (spaceAvailable > 0) {
                        int amountToAdd = Math.min(spaceAvailable, newItem.getCount());
                        existingItem.setCount(existingItem.getCount() + amountToAdd);
                        newItem.setCount(newItem.getCount() - amountToAdd);
                        if (newItem.getCount() <= 0) {
                            return true;
                        }
                    }
                }
            }

            // Add to first empty slot
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i) == null) {
                    if (newItem.getUuid() == null || itemTracker.containsKey(newItem.getUuid())) {
                        newItem.setUuid(UUID.randomUUID());
                    }
                    ItemData itemToAdd = newItem.copy();
                    items.set(i, itemToAdd);
                    itemTracker.put(itemToAdd.getUuid(), itemToAdd);
                    GameLogger.info("Added item " + itemToAdd.getItemId() +
                        " with UUID " + itemToAdd.getUuid() + " to slot " + i);
                    return true;
                }
            }
        }

        return false;
    }

    public int getSelectedSlot() {
        return selectedSlot;
    }

    public void setSelectedSlot(int index) {
        if (index >= 0 && index < INVENTORY_SIZE) {
            this.selectedSlot = index;
        }
    }

    public ItemData getItemAt(int index) {
        if (index >= 0 && index < items.size()) {
            return items.get(index);
        }
        return null;
    }

    public void setItemAt(int index, ItemData item) {
        synchronized (inventoryLock) {
            if (index < 0 || index >= items.size()) {
                GameLogger.error("Invalid inventory index: " + index);
                return;
            }

            ItemData oldItem = items.get(index);
            if (oldItem != null && oldItem.getUuid() != null) {
                itemTracker.remove(oldItem.getUuid());
            }

            if (item != null) {
                if (item.getUuid() == null || itemTracker.containsKey(item.getUuid())) {
                    item.setUuid(UUID.randomUUID());
                }
                items.set(index, item.copy());
                itemTracker.put(item.getUuid(), items.get(index));
            } else {
                items.set(index, null);
            }
        }
    }

    public synchronized void update() {
        synchronized (inventoryLock) {
            // Remove any invalid items
            for (int i = 0; i < items.size(); i++) {
                ItemData item = items.get(i);
                if (item != null && (item.getCount() <= 0 || item.getUuid() == null)) {
                    items.set(i, null);
                    itemTracker.remove(item.getUuid());
                }
            }

            // Validate UUID consistency
            Set<UUID> trackedUuids = new HashSet<>(itemTracker.keySet());
            for (ItemData item : items) {
                if (item != null) {
                    if (!trackedUuids.contains(item.getUuid())) {
                        itemTracker.put(item.getUuid(), item);
                    }
                    trackedUuids.remove(item.getUuid());
                }
            }

            // Remove orphaned entries from tracker
            for (UUID orphanedUuid : trackedUuids) {
                itemTracker.remove(orphanedUuid);
            }
        }
    }

    public synchronized boolean removeItem(ItemData item) {
        if (item == null) return false;

        synchronized (inventoryLock) {
            // First try to find and remove exact UUID match
            for (int i = 0; i < items.size(); i++) {
                ItemData existingItem = items.get(i);
                if (existingItem != null && existingItem.getUuid().equals(item.getUuid())) {
                    if (existingItem.getCount() >= item.getCount()) {
                        existingItem.setCount(existingItem.getCount() - item.getCount());
                        if (existingItem.getCount() <= 0) {
                            items.set(i, null);
                            itemTracker.remove(existingItem.getUuid());
                        }
                        return true;
                    }
                    return false;
                }
            }

            // If no UUID match, try to remove by item type
            int remainingToRemove = item.getCount();
            for (int i = 0; i < items.size() && remainingToRemove > 0; i++) {
                ItemData existingItem = items.get(i);
                if (existingItem != null && existingItem.getItemId().equals(item.getItemId())) {
                    int amountToRemove = Math.min(remainingToRemove, existingItem.getCount());
                    existingItem.setCount(existingItem.getCount() - amountToRemove);
                    remainingToRemove -= amountToRemove;

                    if (existingItem.getCount() <= 0) {
                        items.set(i, null);
                        itemTracker.remove(existingItem.getUuid());
                    }
                }
            }

            return remainingToRemove == 0;
        }
    }

    /**
     * Sets items in a specific range of the inventory.
     *
     * @param startIndex The starting index to place items
     * @param newItems   The items to set
     * @param replace    Whether to replace existing items (true) or only fill empty slots (false)
     */
    public void setItemsInRange(int startIndex, List<ItemData> newItems, boolean replace) {
        if (newItems == null) return;
        if (startIndex < 0 || startIndex >= INVENTORY_SIZE) {
            throw new IllegalArgumentException("Invalid start index: " + startIndex);
        }

        synchronized (inventoryLock) {
            for (int i = 0; i < newItems.size() && (startIndex + i) < INVENTORY_SIZE; i++) {
                int index = startIndex + i;
                ItemData newItem = newItems.get(i);

                if (newItem == null) {
                    if (replace) {
                        ItemData oldItem = items.get(index);
                        if (oldItem != null && oldItem.getUuid() != null) {
                            itemTracker.remove(oldItem.getUuid());
                        }
                        items.set(index, null);
                    }
                    continue;
                }

                if (!replace && items.get(index) != null) {
                    continue; // Skip if slot is occupied and not replacing
                }

                // Create deep copy and ensure UUID
                ItemData copy = newItem.copy();
                if (copy.getUuid() == null || itemTracker.containsKey(copy.getUuid())) {
                    copy.setUuid(UUID.randomUUID());
                }

                // Remove old item from tracker if it exists
                ItemData oldItem = items.get(index);
                if (oldItem != null && oldItem.getUuid() != null) {
                    itemTracker.remove(oldItem.getUuid());
                }

                // Set new item
                items.set(index, copy);
                itemTracker.put(copy.getUuid(), copy);
            }
        }
    }

    /**
     * Gets a deep copy of all inventory items.
     *
     * @return A new list containing copies of all inventory items
     */
    public List<ItemData> getAllItemsCopy() {
        synchronized (inventoryLock) {
            return items.stream()
                .map(item -> item != null ? item.copy() : null)
                .collect(Collectors.toList());
        }
    }

    // Add to WorldObject class

    /**
     * Clears all items from the inventory.
     */
    public void clearAllItems() {
        synchronized (inventoryLock) {
            items.clear();
            itemTracker.clear();
            for (int i = 0; i < INVENTORY_SIZE; i++) {
                items.add(null);
            }
            GameLogger.info("Cleared all inventory items");
        }
    }

    /**
     * Validates and repairs the inventory state, including UUID assignments and item counts.
     */

    public boolean stackItems(int sourceSlot, int targetSlot) {
        synchronized (inventoryLock) {
            ItemData sourceItem = items.get(sourceSlot);
            ItemData targetItem = items.get(targetSlot);

            if (sourceItem == null || targetItem == null ||
                !sourceItem.getItemId().equals(targetItem.getItemId())) {
                return false;
            }

            int spaceAvailable = Item.MAX_STACK_SIZE - targetItem.getCount();
            if (spaceAvailable <= 0) return false;

            int amountToMove = Math.min(spaceAvailable, sourceItem.getCount());
            targetItem.setCount(targetItem.getCount() + amountToMove);
            sourceItem.setCount(sourceItem.getCount() - amountToMove);

            if (sourceItem.getCount() <= 0) {
                items.set(sourceSlot, null);
            }

            return true;
        }
    }

    public CraftingSystem getCraftingSystem() {
        return craftingSystem;
    }

    public List<ItemData> getAllItems() {
        return new ArrayList<>(items);
    }

    /**
     * Sets all inventory items from an array.
     *
     * @param newItems Array of ItemData to set. Must be of length INVENTORY_SIZE (36).
     */
    public void setAllItems(ItemData[] newItems) {
        if (newItems == null) {
            throw new IllegalArgumentException("newItems array cannot be null");
        }
        if (newItems.length != INVENTORY_SIZE) {
            throw new IllegalArgumentException("newItems array must be of length " + INVENTORY_SIZE);
        }
        setAllItems(Arrays.asList(newItems));
    }

    /**
     * Sets all inventory items from a list.
     *
     * @param newItems List of ItemData to set. Must be of size INVENTORY_SIZE (36).
     */
    public void setAllItems(List<ItemData> newItems) {
        if (newItems == null) {
            throw new IllegalArgumentException("newItems list cannot be null");
        }

        synchronized (inventoryLock) {
            // Clear current items and tracker
            items.clear();
            itemTracker.clear();

            // Add each item in the new list
            for (int i = 0; i < INVENTORY_SIZE; i++) {
                ItemData item = (i < newItems.size()) ? newItems.get(i) : null;

                if (item != null) {
                    // Create a deep copy to avoid reference issues
                    ItemData copy = item.copy();

                    // Ensure UUID is valid and unique
                    if (copy.getUuid() == null || itemTracker.containsKey(copy.getUuid())) {
                        copy.setUuid(UUID.randomUUID());
                    }

                    items.add(copy);
                    itemTracker.put(copy.getUuid(), copy);
                } else {
                    items.add(null);
                }
            }

            GameLogger.info("Updated all inventory items. Total items: " + itemTracker.size());
        }
    }

    public void validateAndRepair() {
        synchronized (inventoryLock) {
            // Count total items for verification
            int beforeCount = getAllItems().stream()
                .filter(Objects::nonNull)
                .mapToInt(ItemData::getCount)
                .sum();

            // Stack similar items where possible
            for (int i = 0; i < items.size(); i++) {
                ItemData currentItem = items.get(i);
                if (currentItem != null) {
                    for (int j = i + 1; j < items.size(); j++) {
                        ItemData otherItem = items.get(j);
                        if (otherItem != null &&
                            currentItem.getItemId().equals(otherItem.getItemId())) {
                            stackItems(j, i);
                        }
                    }
                }
            }

            // Remove empty slots
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i) != null && items.get(i).getCount() <= 0) {
                    items.set(i, null);
                }
            }

            // Verify count hasn't changed
            int afterCount = getAllItems().stream()
                .filter(Objects::nonNull)
                .mapToInt(ItemData::getCount)
                .sum();

            GameLogger.info("Inventory validation - Before: " + beforeCount + ", After: " + afterCount);
            if (beforeCount != afterCount) {
                GameLogger.error("Item count mismatch during validation!");
            }
        }
    }

    // Additional necessary methods...
}

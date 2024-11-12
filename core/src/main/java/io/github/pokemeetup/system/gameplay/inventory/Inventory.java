package io.github.pokemeetup.system.gameplay.inventory;

import io.github.pokemeetup.system.data.ItemData;
import io.github.pokemeetup.system.gameplay.inventory.crafting.CraftingSystem;
import io.github.pokemeetup.system.gameplay.inventory.secureinventories.InventoryObserver;
import io.github.pokemeetup.system.gameplay.inventory.secureinventories.InventorySlotDataObserver;
import io.github.pokemeetup.utils.GameLogger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Inventory {
    public static final int INVENTORY_SIZE = 27;
    private final List<Slot> slots;
    private final Map<UUID, ItemData> itemTracker;
    private final Object inventoryLock = new Object();
    private CraftingSystem craftingSystem;
    private List<InventoryObserver> observers = new ArrayList<>();

    public Inventory() {
        this.slots = new ArrayList<>(INVENTORY_SIZE);
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            slots.add(new Slot());
        }
        this.itemTracker = new ConcurrentHashMap<>();
        this.craftingSystem = new CraftingSystem(this);
        validateSlots(); // Ensure all slots are properly initialized
    }

    public void setItemAt(int index, ItemData itemData) {
        synchronized (inventoryLock) {
            if (index < 0 || index >= slots.size()) {
                GameLogger.error("Invalid slot index: " + index);
                return;
            }

            Slot slot = slots.get(index);
            if (slot == null) {
                slot = new Slot();
                slots.set(index, slot);
            }

            // Remove old item from tracker
            ItemData oldItem = slot.getItemData();
            if (oldItem != null && oldItem.getUuid() != null) {
                itemTracker.remove(oldItem.getUuid());
            }

            // Set new item
            if (itemData != null) {
                // Ensure UUID
                if (itemData.getUuid() == null) {
                    itemData.setUuid(UUID.randomUUID());
                }

                // Create copy for storage
                ItemData copy = new ItemData(
                    itemData.getItemId(),
                    itemData.getCount(),
                    itemData.getUuid()
                );

                slot.setItemData(copy);
                itemTracker.put(copy.getUuid(), copy);

                GameLogger.info("Set item at slot " + index + ": " +
                    copy.getItemId() + " x" + copy.getCount() +
                    " UUID: " + copy.getUuid());
            } else {
                slot.setItemData(null);
                GameLogger.info("Cleared slot " + index);
            }
            notifyObservers(); // Add this line
        }
    }


    public ItemData getItemAt(int index) {
        synchronized (inventoryLock) {
            if (index < 0 || index >= INVENTORY_SIZE) {
                GameLogger.error("Invalid inventory slot index: " + index);
                return null;
            }

            Slot slot = slots.get(index);
            ItemData item = slot != null ? slot.getItemData() : null;
            if (item != null) {
                GameLogger.info("Got item at slot " + index + ": " +
                    item.getItemId() + " x" + item.getCount());
            }
            return item;
        }
    }

    public List<ItemData> getAllItems() {
        synchronized (inventoryLock) {
            List<ItemData> items = new ArrayList<>(INVENTORY_SIZE);

            // Log before collecting
            GameLogger.info("Getting all items...");
            int nonNullCount = 0;

            for (Slot slot : slots) {
                if (slot == null) {
                    items.add(null);
                    continue;
                }

                ItemData item = slot.getItemData();
                if (item != null) {
                    // Create defensive copy
                    ItemData copy = new ItemData(
                        item.getItemId(),
                        item.getCount(),
                        item.getUuid()
                    );
                    items.add(copy);
                    nonNullCount++;

                    GameLogger.info("Found item: " + item.getItemId() + " x" +
                        item.getCount() + " UUID: " + item.getUuid());
                } else {
                    items.add(null);
                }
            }

            GameLogger.info("Found " + nonNullCount + " items total");
            return items;
        }
    }

    public void setAllItems(List<ItemData> items) {
        if (items == null) {
            GameLogger.error("setAllItems called with null items list! Operation aborted to prevent data loss.");
            return;
        }
        synchronized (inventoryLock) {
            GameLogger.info("Setting all items - Received " +
                items.stream().filter(Objects::nonNull).count() + " items");
            // Clear existing items
//            clear();

            // Set new items
            for (int i = 0; i < INVENTORY_SIZE; i++) {
                if (i < items.size() && items.get(i) != null) {
                    ItemData item = items.get(i).copy(); // Make defensive copy
                    setItemAt(i, item);
                    GameLogger.info("Set item at " + i + ": " + item.getItemId() + " x" + item.getCount());
                }
            }

            validateAndRepair();
        }
    }

    public void moveItem(int fromIndex, int toIndex) {
        synchronized (inventoryLock) {
            try {
                // Validate indices
                if (fromIndex < 0 || fromIndex >= slots.size() ||
                    toIndex < 0 || toIndex >= slots.size()) {
                    GameLogger.error("Invalid inventory indices: from " + fromIndex + " to " + toIndex);
                    return;
                }
                if (fromIndex == toIndex) return;

                Slot fromSlot = slots.get(fromIndex);
                Slot toSlot = slots.get(toIndex);

                if (fromSlot == null || toSlot == null) {
                    GameLogger.error("Null slot encountered during move operation");
                    return;
                }

                ItemData fromItem = fromSlot.getItemData();
                ItemData toItem = toSlot.getItemData();

                // Remove old tracking
                if (fromItem != null && fromItem.getUuid() != null) {
                    itemTracker.remove(fromItem.getUuid());
                }
                if (toItem != null && toItem.getUuid() != null) {
                    itemTracker.remove(toItem.getUuid());
                }

                // Create copies with new UUIDs
                ItemData newFromItem = toItem != null ? toItem.copy() : null;
                ItemData newToItem = fromItem != null ? fromItem.copy() : null;

                if (newFromItem != null) newFromItem.setUuid(UUID.randomUUID());
                if (newToItem != null) newToItem.setUuid(UUID.randomUUID());

                // Update slots
                fromSlot.setItemData(newFromItem);
                toSlot.setItemData(newToItem);

                // Update tracker with new items
                if (newFromItem != null) {
                    safelyTrackItem(newFromItem, fromSlot);
                }
                if (newToItem != null) {
                    safelyTrackItem(newToItem, toSlot);
                }

                GameLogger.info("Moved item from index " + fromIndex + " to " + toIndex);
                validateAndRepair(); // Validate after move

            } catch (Exception e) {
                GameLogger.error("Error during item move: " + e.getMessage());
                validateAndRepair(); // Attempt recovery
                throw e; // Rethrow to inform caller
            }
        }
    }

    private void safelyTrackItem(ItemData item, Slot slot) {
        if (item != null && item.getUuid() != null && slot != null) {
            // Remove any existing tracking for this UUID first
            itemTracker.values().removeIf(trackedItem ->
                trackedItem.getUuid().equals(item.getUuid()));

            // Add new tracking
            itemTracker.put(item.getUuid(), item);
        }
    }

    public boolean addItem(ItemData itemData) {
        if (itemData == null) return false;

        synchronized (inventoryLock) {
            try {
                GameLogger.info("Attempting to add item: " + itemData.getItemId() + " x" + itemData.getCount());

                // First try to stack with existing items
                List<ItemData> currentItems = getAllItems();
                for (int i = 0; i < slots.size(); i++) {
                    Slot slot = slots.get(i);
                    if (slot == null) {
                        slots.set(i, new Slot());
                        continue;
                    }

                    ItemData existingItem = slot.getItemData();
                    if (existingItem != null &&
                        existingItem.getItemId().equals(itemData.getItemId()) &&
                        existingItem.getCount() < Item.MAX_STACK_SIZE) {

                        int spaceInStack = Item.MAX_STACK_SIZE - existingItem.getCount();
                        int amountToAdd = Math.min(spaceInStack, itemData.getCount());

                        // Create a new item with updated count to prevent reference issues
                        ItemData updatedItem = new ItemData(
                            existingItem.getItemId(),
                            existingItem.getCount() + amountToAdd,
                            existingItem.getUuid()
                        );

                        // Update slot with new item data
                        slot.setItemData(updatedItem);

                        GameLogger.info("Stacked " + amountToAdd + " items with existing stack in slot " + i +
                            " (New total: " + updatedItem.getCount() + ")");

                        itemData.setCount(itemData.getCount() - amountToAdd);
                        if (itemData.getCount() <= 0) {
                            validateAndRepair();
                            return true;
                        }
                    }
                }

                // If we still have items to add, find an empty slot
                if (itemData.getCount() > 0) {
                    for (int i = 0; i < slots.size(); i++) {
                        Slot slot = slots.get(i);
                        if (slot == null) {
                            slot = new Slot();
                            slots.set(i, slot);
                        }

                        if (slot.isEmpty()) {
                            // Create a new item instance for the new slot
                            ItemData newItem = new ItemData(
                                itemData.getItemId(),
                                itemData.getCount(),
                                UUID.randomUUID()
                            );

                            slot.setItemData(newItem);
                            GameLogger.info("Added new item stack to slot " + i +
                                " (" + newItem.getItemId() + " x" + newItem.getCount() + ")");

                            validateAndRepair();
                            return true;
                        }
                    }
                }
                notifyObservers();

                GameLogger.info("No space found for remaining items");
                return false;

            } catch (Exception e) {
                GameLogger.error("Error adding item to inventory: " + e.getMessage());
                e.printStackTrace();
                validateAndRepair();
                return false;
            }
        }
    }

    private void notifyObservers() {
        for (InventoryObserver observer : observers) {
            observer.onInventoryChanged();
        }
    }

    public void addObserver(InventoryObserver observer) {
        observers.add(observer);
    }



    public void validateAndRepair() {
        synchronized (inventoryLock) {
            try {
                GameLogger.info("Starting inventory validation");

                // Validate slots array
                if (slots.size() != INVENTORY_SIZE) {
                    GameLogger.error("Invalid slots size: " + slots.size());
                    while (slots.size() < INVENTORY_SIZE) {
                        slots.add(new Slot());
                    }
                }

                // Rebuild item tracker
                itemTracker.clear();
                int itemCount = 0;

                for (int i = 0; i < slots.size(); i++) {
                    Slot slot = slots.get(i);
                    if (slot == null) {
                        slots.set(i, new Slot());
                        continue;
                    }

                    ItemData item = slot.getItemData();
                    if (item != null) {
                        itemCount++;
                        // Ensure valid UUID
                        if (item.getUuid() == null) {
                            item.setUuid(UUID.randomUUID());
                        }
                        itemTracker.put(item.getUuid(), item);


                        // Validate stack size
                        if (item.getCount() <= 0 || item.getCount() > Item.MAX_STACK_SIZE) {
                            GameLogger.error("Invalid stack size in slot " + i + ": " + item.getCount());
                            if (item.getCount() <= 0) {
                                slot.setItemData(null);
                                itemCount--;
                            } else {
                                item.setCount(Item.MAX_STACK_SIZE);
                            }
                        }
                    }
                }

                GameLogger.info("Validation complete - Found " + itemCount + " valid items");

            } catch (Exception e) {
                GameLogger.error("Error during inventory validation: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    // Add helper methods
    public int getTotalItems() {
        return slots.stream()
            .filter(Objects::nonNull)
            .map(Slot::getItemData)
            .filter(Objects::nonNull)
            .mapToInt(ItemData::getCount)
            .sum();
    }

    public void clear() {
        synchronized (inventoryLock) {
            GameLogger.info("Clearing inventory");
            for (Slot slot : slots) {
                if (slot != null) {
                    slot.setItemData(null);
                }
            }
            itemTracker.clear();
        }
    }

    public Object getInventoryLock() {
        return inventoryLock;
    }

    public void load() {
        synchronized (inventoryLock) {
            validateSlots(); // Ensure slots are valid before loading
            validateAndRepair(); // Validate after loading
        }
    }

    public void stackItems(int sourceSlotIndex, int targetSlotIndex) {
        synchronized (inventoryLock) {
            if (sourceSlotIndex == targetSlotIndex) return;

            Slot sourceSlot = slots.get(sourceSlotIndex);
            Slot targetSlot = slots.get(targetSlotIndex);

            ItemData sourceItem = sourceSlot.getItemData();
            ItemData targetItem = targetSlot.getItemData();

            if (sourceItem == null || targetItem == null ||
                !sourceItem.getItemId().equals(targetItem.getItemId())) {
                return;
            }

            int spaceAvailable = Item.MAX_STACK_SIZE - targetItem.getCount();
            if (spaceAvailable <= 0) return;

            int amountToMove = Math.min(spaceAvailable, sourceItem.getCount());
            targetItem.setCount(targetItem.getCount() + amountToMove);
            sourceItem.setCount(sourceItem.getCount() - amountToMove);

            if (sourceItem.getCount() <= 0) {
                itemTracker.remove(sourceItem.getUuid());
                sourceSlot.setItemData(null);
            }
        }
    }

    public ItemData splitStack(int slotIndex) {
        synchronized (inventoryLock) {
            Slot slot = slots.get(slotIndex);
            ItemData originalItem = slot.getItemData();
            if (originalItem == null || originalItem.getCount() <= 1) {
                return null;
            }

            int halfCount = originalItem.getCount() / 2;
            int remainingCount = originalItem.getCount() - halfCount;

            // Create a new item for the split
            ItemData splitItem = originalItem.copy();
            splitItem.setCount(halfCount);
            splitItem.setUuid(UUID.randomUUID()); // New UUID for the split item

            // Update the original item's count
            originalItem.setCount(remainingCount);

            // Update itemTracker if necessary
            if (remainingCount <= 0) {
                slot.setItemData(null);
                itemTracker.remove(originalItem.getUuid());
            }

            // Return the split item
            return splitItem;
        }
    }

    // Place one item into a slot
    public boolean placeOneItem(int slotIndex, ItemData heldItem) {
        synchronized (inventoryLock) {
            if (heldItem == null || heldItem.getCount() <= 0) {
                return false;
            }

            Slot slot = slots.get(slotIndex);
            ItemData slotItem = slot.getItemData();

            if (slotItem == null) {
                ItemData newItem = heldItem.copy();
                newItem.setCount(1);
                newItem.setUuid(UUID.randomUUID());
                slot.setItemData(newItem);
                itemTracker.put(newItem.getUuid(), newItem);

                heldItem.setCount(heldItem.getCount() - 1);
                if (heldItem.getCount() <= 0) {
                    itemTracker.remove(heldItem.getUuid());
                }

                return true;
            } else if (slotItem.canStackWith(heldItem) && slotItem.getCount() < Item.MAX_STACK_SIZE) {
                slotItem.setCount(slotItem.getCount() + 1);
                heldItem.setCount(heldItem.getCount() - 1);
                if (heldItem.getCount() <= 0) {
                    itemTracker.remove(heldItem.getUuid());
                }

                return true;
            }

            return false;
        }
    }

    public void validateAndSync(List<ItemData> items) {
        synchronized (inventoryLock) {
            // Store current state before validation
            Map<UUID, ItemData> previousState = new HashMap<>(itemTracker);
            List<ItemData> previousItems = getAllItems();

            // Clear and rebuild inventory ensuring all slots exist
            clear();
            slots.clear();
            for (int i = 0; i < INVENTORY_SIZE; i++) {
                slots.add(new Slot());
            }

            // Carefully restore items with validation
            if (items != null) {
                for (int i = 0; i < Math.min(items.size(), INVENTORY_SIZE); i++) {
                    ItemData item = items.get(i);
                    if (item != null && item.isValid() && ItemManager.getItem(item.getItemId()) != null) {
                        // Ensure UUID consistency
                        if (item.getUuid() == null) {
                            item.setUuid(UUID.randomUUID());
                        }
                        setItemAt(i, item);
                        itemTracker.put(item.getUuid(), item);
                    }
                }
            }

            // Validate final state
            boolean stateChanged = !itemsEqual(previousItems, getAllItems());
            if (stateChanged) {
                GameLogger.error("Inventory state changed during validation!");
                logInventoryDiff(previousItems, getAllItems());
            }
        }
    }

    private boolean itemsEqual(List<ItemData> list1, List<ItemData> list2) {
        if (list1.size() != list2.size()) return false;

        for (int i = 0; i < list1.size(); i++) {
            ItemData item1 = list1.get(i);
            ItemData item2 = list2.get(i);

            if ((item1 == null) != (item2 == null)) return false;
            if (item1 != null) {
                if (!item1.getItemId().equals(item2.getItemId()) ||
                    item1.getCount() != item2.getCount()) {
                    return false;
                }
            }
        }
        return true;
    }

    private void logInventoryDiff(List<ItemData> before, List<ItemData> after) {
        GameLogger.info("=== Inventory Difference ===");
        for (int i = 0; i < Math.max(before.size(), after.size()); i++) {
            ItemData beforeItem = i < before.size() ? before.get(i) : null;
            ItemData afterItem = i < after.size() ? after.get(i) : null;

            if (!Objects.equals(beforeItem, afterItem)) {
                GameLogger.info(String.format("Slot %d: %s -> %s",
                    i,
                    beforeItem != null ? beforeItem.getItemId() + " x" + beforeItem.getCount() : "null",
                    afterItem != null ? afterItem.getItemId() + " x" + afterItem.getCount() : "null"
                ));
            }
        }
        GameLogger.info("=========================");
    }

    private int getMaxStackSize(String itemId) {
        Item baseItem = ItemManager.getItem(itemId);
        return baseItem != null ? Item.MAX_STACK_SIZE : 64; // Default max stack size
    }

    private void validateSlots() {
        synchronized (inventoryLock) {
            boolean needsRepair = false;
            for (int i = 0; i < INVENTORY_SIZE; i++) {
                if (slots.get(i) == null) {
                    slots.set(i, new Slot());
                    needsRepair = true;
                    GameLogger.error("Repaired null slot at index " + i);
                }
            }

            if (needsRepair) {
                validateAndRepair(); // Run full validation after slot repair
            }
        }
    }

    public CraftingSystem getCraftingSystem() {
        return craftingSystem;
    }

    public void removeItemAt(int index) {
        synchronized (inventoryLock) {
            if (index < 0 || index >= slots.size()) {
                return;
            }

            Slot slot = slots.get(index);
            ItemData removedItem = slot.getItemData();
            if (removedItem != null) {
                itemTracker.remove(removedItem.getUuid());
                slot.setItemData(null);
                GameLogger.info("Removed item from slot " + index + ": " + removedItem.getItemId());
            }
        }
    }

    public boolean isEmpty() {
        synchronized (inventoryLock) {
            return slots.stream().allMatch(slot -> slot.isEmpty());
        }
    }

    public void update() {
        synchronized (inventoryLock) {
            // Remove items with invalid counts or UUIDs
            for (Slot slot : slots) {
                ItemData item = slot.getItemData();
                if (item != null && (item.getCount() <= 0 || item.getUuid() == null)) {
                    itemTracker.remove(item.getUuid());
                    slot.setItemData(null);
                }
            }

            // Rebuild itemTracker
            itemTracker.clear();
            for (Slot slot : slots) {
                ItemData item = slot.getItemData();
                if (item != null && item.getUuid() != null) {
                    itemTracker.put(item.getUuid(), item);
                }
            }
        }
    }

}

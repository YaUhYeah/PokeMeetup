package io.github.pokemeetup.system.gameplay.inventory.secureinventories;

import io.github.pokemeetup.system.gameplay.inventory.Item;
import io.github.pokemeetup.system.gameplay.inventory.ItemManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class InventorySlotData {
    private int slotIndex;
    private String slotId;            // Unique identifier for this slot (optional)
    private String itemId;            // ID of item in slot, null if empty
    private int count;                // Number of items in stack
    private SlotType slotType;        // Type of slot (INVENTORY, HOTBAR, CRAFTING, CRAFTING_RESULT)
    private int position;             // Position in container (optional)
    private UUID uuid;             // Position in container (optional)

    private List<InventorySlotDataObserver> observers = new ArrayList<>();

    // Constructor with slotIndex only
    public InventorySlotData(int slotIndex) {
        this.slotIndex = slotIndex;
        this.slotId = null;
        this.slotType = null;
        this.position = 0;
        this.itemId = null;
        this.count = 0;
        this.uuid = UUID.randomUUID();
    }

    // Constructor with slotId, slotType, position, and slotIndex
    public InventorySlotData(String slotId, SlotType slotType, int position, int slotIndex) {
        this.slotId = slotId;
        this.slotType = slotType;
        this.position = position;
        this.slotIndex = slotIndex;
        this.itemId = null;
        this.count = 0;
        this.uuid = UUID.randomUUID();
    }

    // Default constructor
    public InventorySlotData() {
        this(-1); // Default slotIndex to -1
    }

    // Observer methods
    public void addObserver(InventorySlotDataObserver observer) {
        observers.add(observer);
    }

    public void removeObserver(InventorySlotDataObserver observer) {
        observers.remove(observer);
    }

    private void notifyObservers() {
        for (InventorySlotDataObserver observer : observers) {
            observer.onSlotDataChanged();
        }
    }

    // Getters and Setters
    public int getSlotIndex() {
        return slotIndex;
    }

    public void setSlotIndex(int slotIndex) {
        this.slotIndex = slotIndex;
    }

    public String getSlotId() {
        return slotId;
    }

    public void setSlotId(String slotId) {
        this.slotId = slotId;
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
        notifyObservers();
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
        notifyObservers();
    }

    public synchronized boolean transferTo(InventorySlotData target) {
        if (this.isEmpty() || target == null) return false;

        // If target is empty
        if (target.isEmpty()) {
            target.setItem(this.itemId, this.count);
            this.clear();
            return true;
        }

        // If items are the same type
        if (this.itemId.equals(target.getItemId())) {
            int totalCount = this.count + target.getCount();
            if (totalCount <= Item.MAX_STACK_SIZE) {
                target.setCount(totalCount);
                this.clear();
                return true;
            } else {
                int transfer = Item.MAX_STACK_SIZE - target.getCount();
                if (transfer > 0) {
                    target.setCount(Item.MAX_STACK_SIZE);
                    this.count -= transfer;
                    return true;
                }
            }
        }

        // Swap items if different types
        String tempId = this.itemId;
        int tempCount = this.count;
        this.itemId = target.itemId;
        this.count = target.count;
        target.itemId = tempId;
        target.count = tempCount;
        return true;
    }

    public SlotType getSlotType() {
        return slotType;
    }

    public void setSlotType(SlotType slotType) {
        this.slotType = slotType;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public void setItem(String itemId, int count) {
        this.itemId = itemId;
        this.count = count;
        notifyObservers();
    }
    public void setItem(String itemId, int count, UUID uuid) {
        this.itemId = itemId;
        this.count = count;
        this.uuid = uuid;
        notifyObservers();
    }


    public void clear() {
        this.itemId = null;
        this.count = 0;
        notifyObservers();
    }

    public boolean isEmpty() {
        return this.itemId == null || this.count <= 0;
    }

    // Deep copy for safe data handling
    public InventorySlotData copy() {
        InventorySlotData copy = new InventorySlotData(this.slotIndex);
        copy.setItem(this.itemId, this.count);
        return copy;
    }


    /**
     * Creates a copy of the Item in this slot with a specified count.
     *
     * @param count The count for the copied Item.
     * @return A new Item instance with the same properties and the specified count, or null if the slot is empty.
     */


    /**
     * Increments the count of items in the slot by a specified amount.
     *
     * @param amount The amount to increment.
     */
    public void incrementCount(int amount) {
        this.count = Math.min(this.count + amount, Item.MAX_STACK_SIZE);
    }

    /**
     * Decrements the count of items in the slot by a specified amount.
     *
     * @param amount The amount to decrement.
     * @return
     */
    public boolean decrementCount(int amount) {
        this.count = Math.max(this.count - amount, 0);
        if (this.count == 0) {
            this.itemId = null;
        }
        return false;
    }



    public Item copyItemWithCount(int count) {
        if (isEmpty() || count <= 0) return null;
        Item item = ItemManager.getItem(this.itemId);
        if (item != null && !item.isEmpty()) {
            Item copiedItem = item.copy();
            copiedItem.setCount(count);
            return copiedItem;
        }
        return null;
    }

    public Item getItem() {
        if (itemId == null) return null;

        Item baseItem = ItemManager.getItem(itemId);
        if (baseItem == null) return null;

        Item item = baseItem.copy();
        item.setCount(count);
        return item;
    }

    public Item copyItem() {
        Item item = getItem();
        return item != null ? item.copy() : null;
    }

    @Override
    public String toString() {
        return "InventorySlotData{" +
            "slotIndex=" + slotIndex +
            ", itemId='" + itemId + '\'' +
            ", count=" + count +
            '}';
    }

    public enum SlotType {
        INVENTORY,
        HOTBAR,
        CRAFTING,
        CRAFTING_RESULT
    }
}

package io.github.pokemeetup.system.gameplay.inventory.secureinventories;

import io.github.pokemeetup.system.data.ItemData;
import io.github.pokemeetup.system.gameplay.inventory.Item;
import io.github.pokemeetup.system.gameplay.inventory.ItemManager;
import io.github.pokemeetup.utils.GameLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class InventorySlotData {
    private int slotIndex;
    private SlotType slotType;        // Type of slot (INVENTORY, HOTBAR, CRAFTING, CRAFTING_RESULT)
    private int position;             // Position in container (optional)
    private String slotId;
    private ItemData itemData;
    private List<InventorySlotDataObserver> observers = new ArrayList<>();

    public InventorySlotData(String slotId, SlotType slotType, int position, int slotIndex) {
        this.slotType = slotType;
        this.position = position;
        this.slotIndex = slotIndex;
        this.slotId = slotId;
    }

    public InventorySlotData(int slotIndex) {
        this.slotIndex = slotIndex;
        this.slotType = null;
        this.position = 0;
        this.slotId = "";
        this.itemData = null;
    }


    public InventorySlotData copy() {
        InventorySlotData copy = new InventorySlotData(this.slotIndex);
        if (this.itemData != null) {
            copy.itemData = this.itemData.copy();
        }
        copy.slotType = this.slotType;
        copy.position = this.position;
        copy.slotId = this.slotId;
        return copy;
    }



    public void setItem(String itemId, int count, UUID uuid) {
        if (itemId == null || count <= 0) {
            this.itemData = null;
        } else {
            this.itemData = new ItemData(itemId, count, uuid != null ? uuid : UUID.randomUUID());
        }
        notifyObservers();
    }

    public void clear() {
        this.itemData = null;
        notifyObservers();
    }

    public ItemData getItemData() {
        if (itemData != null) {
            return new ItemData(itemData.getItemId(), itemData.getCount(), itemData.getUuid());
        }
        return null;
    }

    public boolean isEmpty() {
        return itemData == null || itemData.getCount() <= 0;
    }

    public String getItemId() {
        return itemData != null ? itemData.getItemId() : null;
    }



    public int getCount() {
        return itemData != null ? itemData.getCount() : 0;
    }

    public void setCount(int count) {
        if (itemData != null) {
            itemData.setCount(count);
            if (count <= 0) {
                itemData = null;
            }
        }
        notifyObservers();
    }

    // Observer methods
    public void addObserver(InventorySlotDataObserver observer) {
        observers.add(observer);
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

    public void setSlotType(SlotType slotType) {
        this.slotType = slotType;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public UUID getUuid() {
        return itemData != null ? itemData.getUuid() : null;
    }

    // Deep copy for safe data handling

    public Item getItem() {
        if (itemData == null) return null;

        Item baseItem = ItemManager.getItem(itemData.getItemId());
        if (baseItem == null) {
            GameLogger.error("Could not find base item for: " + itemData.getItemId());
            return null;
        }

        Item item = baseItem.copy();
        item.setCount(itemData.getCount());
        item.setUuid(itemData.getUuid());
        return item;
    }


    public enum SlotType {
        INVENTORY,
        HOTBAR,
        CRAFTING,
        CRAFTING_RESULT
    }
}

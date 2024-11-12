package io.github.pokemeetup.system.gameplay.inventory;

import io.github.pokemeetup.system.data.ItemData;

import java.util.UUID;

public class Slot {
    private final UUID slotId;
    private ItemData itemData;
    private final Object slotLock = new Object();

    public Slot() {
        this.slotId = UUID.randomUUID();
    }

    public ItemData getItemData() {
        synchronized (slotLock) {
            return itemData != null ? itemData.copy() : null;
        }
    }

    public void setItemData(ItemData newItemData) {
        synchronized (slotLock) {
            if (newItemData != null) {
                // Create defensive copy and ensure UUID
                this.itemData = newItemData.copy();
                if (this.itemData.getUuid() == null) {
                    this.itemData.setUuid(UUID.randomUUID());
                }
            } else {
                this.itemData = null;
            }
        }
    }

    public boolean isEmpty() {
        synchronized (slotLock) {
            return itemData == null || itemData.getCount() <= 0;
        }
    }

    public void clear() {
        synchronized (slotLock) {
            this.itemData = null;
        }
    }
}

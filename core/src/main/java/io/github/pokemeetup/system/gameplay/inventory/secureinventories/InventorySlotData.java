package io.github.pokemeetup.system.gameplay.inventory.secureinventories;

public class InventorySlotData {
    private int slotIndex;
    private String slotId;            // Unique identifier for this slot
    private String itemId;            // ID of item in slot, null if empty
    private int count;                // Number of items in stack
    private SlotType slotType;        // Type of slot (INVENTORY, HOTBAR, CRAFTING)
    private int position;             // Position in container

    public enum SlotType {
        INVENTORY,
        HOTBAR,
        CRAFTING,
        CRAFTING_RESULT
    }

    // Constructor with slotIndex only
    public InventorySlotData(int slotIndex) {
        this.slotIndex = slotIndex;
        this.slotId = null;
        this.slotType = null;
        this.position = 0;
        this.itemId = null;
        this.count = 0;
    }

    // Constructor with slotId, slotType, position, and slotIndex
    public InventorySlotData(String slotId, SlotType slotType, int position, int slotIndex) {
        this.slotId = slotId;
        this.slotType = slotType;
        this.position = position;
        this.slotIndex = slotIndex;
        this.itemId = null;
        this.count = 0;
    }

    // Default constructor
    public InventorySlotData() {
        this(-1); // Default slotIndex to -1
    }

    // Getters and Setters
    public int getSlotIndex() {
        return slotIndex;
    }

    public String getSlotId() {
        return slotId;
    }

    public String getItemId() {
        return itemId;
    }

    public int getCount() {
        return count;
    }

    public SlotType getSlotType() {
        return slotType;
    }

    public int getPosition() {
        return position;
    }

    public void setSlotIndex(int slotIndex) {
        this.slotIndex = slotIndex;
    }

    public void setSlotId(String slotId) {
        this.slotId = slotId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public void setSlotType(SlotType slotType) {
        this.slotType = slotType;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public void setItem(String itemId, int count) {
        this.itemId = itemId;
        this.count = count;
    }

    public void clear() {
        this.itemId = null;
        this.count = 0;
    }

    public boolean isEmpty() {
        return itemId == null || count <= 0;
    }

    // Deep copy for safe data handling
    public InventorySlotData copy() {
        InventorySlotData copy = new InventorySlotData(slotIndex);
        copy.slotId = this.slotId;
        copy.slotType = this.slotType;
        copy.position = this.position;
        copy.itemId = this.itemId;
        copy.count = this.count;
        return copy;
    }
}

package io.github.pokemeetup.system.gameplay.inventory;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Json;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class Inventory {
    public static final int HOTBAR_SIZE = 9;  // First 9 slots
    public static final int INVENTORY_ROWS = 3;
    public static final int INVENTORY_COLS = 9;
    public static final int INVENTORY_SIZE = INVENTORY_ROWS * INVENTORY_COLS;
    public static final int INVENTORY_CAPACITY = INVENTORY_SIZE * 64;  // Total 27 slots

    public static final int CRAFTING_GRID_SIZE = 4; // 2x2 for inventory crafting
    private static final String SAVE_FILE = "assets/save/inventory.json";
    private final List<Item> items;
    private final Item[][] craftingGrid;
    private float heldItemX;
    private float heldItemY;
    private String[] itemNames; // Array of item names for serialization
    private Item craftingResult;
    private int selectedHotbarSlot;
    private Item heldItem = null;  // Currently held item
    private int heldItemCount = 0; // Count of held item
    private List<Item> hotbarCache;

    public Inventory() {
        items = new ArrayList<>(INVENTORY_SIZE);
        // Initialize with null slots
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            items.add(null);
        }
        craftingGrid = new Item[2][2];
        selectedHotbarSlot = 0;
        System.out.println("Created new inventory with " + INVENTORY_SIZE + " slots");
    }    // Update getSelectedIndex to work with first 9 slots
    public int getSelectedIndex() {
        return selectedHotbarSlot;  // This should be 0-8
    }

    public void selectItem(int index) {
        if (index >= 0 && index < HOTBAR_SIZE) {  // Only allow selecting first 9 slots
            selectedHotbarSlot = index;
        }
    }

    // Update hotbar-related methods to use first 9 slots
    public void updateHotbarDisplay() {
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            if (hotbarCache != null) {
                hotbarCache.set(i, getItem(i));
            }
        }
    }

    public static Inventory loadInventory() {
        FileHandle file = Gdx.files.local(SAVE_FILE);
        if (file.exists()) {
            try {
                Json json = new Json();
                InventoryData data = json.fromJson(InventoryData.class, file.readString());
                Inventory inventory = new Inventory();

                for (int i = 0; i < data.items.length; i++) {
                    ItemData itemData = data.items[i];
                    if (itemData != null) {
                        Item item = ItemManager.getItem(itemData.name);
                        if (item != null) {
                            item.setCount(itemData.count);
                            inventory.items.set(i, item);
                        }
                    }
                }
                return inventory;
            } catch (Exception e) {
                System.err.println("Failed to load inventory: " + e.getMessage());
            }
        }
        return new Inventory();
    }

    public void updateHeldItemPosition(float mouseX, float mouseY) {
        // Offset the icon a bit from the cursor to make it more visible
        heldItemX = mouseX - 16;
        heldItemY = mouseY - 16;
    }

    public void renderHeldItem(SpriteBatch batch) {
        if (heldItem != null) {
            // Directly draw the held item's icon without calling begin/end
            batch.draw(heldItem.getIcon(), heldItemX, heldItemY, 32, 32);
        }
    }

    public Item getItemAt(int row, int col) {
        // Check that the row and column are within the bounds of the crafting grid
        if (row >= 0 && row < craftingGrid.length && col >= 0 && col < craftingGrid[row].length) {
            return craftingGrid[row][col];
        } else {
            return null; // Return null if the indices are out of bounds
        }
    }

    // Check if currently holding an item
    public boolean isHoldingItem() {
        return heldItem != null;
    }

    // Get the currently held item
    public Item getHeldItem() {
        return heldItem;
    }

    // Get count of held item
    public int getHeldItemCount() {
        return heldItemCount;
    }

    // Set an item as held
    public void setHeldItem(Item item, int count) {
        this.heldItem = item;
        this.heldItemCount = count;
    }

    // Clear the held item
    public void clearHeldItem() {
        this.heldItem = null;
        this.heldItemCount = 0;
    }

    // Decrease held item count by 1
    public void decrementHeldItem() {
        if (heldItemCount > 0) {
            heldItemCount--;
            if (heldItemCount == 0) {
                heldItem = null;
            }
        }
    }

    public void setItem(int index, Item item) {
        if (index >= 0 && index < items.size()) {
            items.set(index, item);
            if (itemNames != null) {
                itemNames[index] = item != null ? item.getName() : null;
            }
        }
    }

    public void loadFromStrings(List<String> itemStrings) {
        if (itemStrings == null) {
            System.out.println("No inventory data to load");
            return;
        }

        // Clear existing items first
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            items.set(i, null);
        }

        System.out.println("Loading inventory items: " + itemStrings);

        for (String itemString : itemStrings) {
            try {
                String[] parts = itemString.trim().split(":");
                if (parts.length == 2) {
                    String itemName = parts[0].trim();
                    int count = Integer.parseInt(parts[1].trim());

                    Item item = ItemManager.getItem(itemName);
                    if (item != null) {
                        // Create a new item instance to avoid sharing references
                        Item newItem = new Item(
                            item.getName(),
                            item.getName().toLowerCase(),
                            item.getIcon()
                        );
                        newItem.setCount(count);
                        addItem(newItem);
                    } else {
                        System.err.println("Unknown item: " + itemName);
                    }
                }
            } catch (Exception e) {
                System.err.println("Error loading item: " + itemString + " - " + e.getMessage());
            }
        }

        // Debug output final inventory state
        System.out.println("Final inventory contents:");
        for (Item item : items) {
            if (item != null) {
                System.out.println("- " + item.getName() + " x" + item.getCount());
            }
        }
    }

    public void clear() {
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            items.set(i, null);
        }
        System.out.println("Inventory cleared");
    }

    public boolean addItem(Item newItem) {
        // First try to stack with existing similar items
        for (int i = 0; i < items.size(); i++) {
            Item existingItem = items.get(i);
            if (existingItem != null && existingItem.getName().equals(newItem.getName())) {
                // Use the canStackWith and addToStack methods
                if (existingItem.canStackWith(newItem)) {
                    int leftover = existingItem.addToStack(newItem.getCount());
                    if (leftover == 0) {
                        return true; // Successfully added all items
                    }
                    newItem.setCount(leftover); // Update remaining count
                }
            }
        }

        // If we still have items to add, find an empty slot
        if (newItem.getCount() > 0) {
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i) == null) {
                    // Create new item with same properties
                    Item newSlotItem = new Item(
                        newItem.getName(),
                        newItem.getName().toLowerCase(), // Assuming iconName is lowercase name
                        newItem.getIcon()
                    );
                    newSlotItem.setCount(newItem.getCount());
                    items.set(i, newSlotItem);
                    return true;
                }
            }
        }

        return false; // Inventory is full
    }

    public void removeItem(int index) {
        if (index >= 0 && index < items.size()) {
            items.set(index, null);
            itemNames[index] = null;
        }
        // Optionally, update the UI or notify listeners
    }



    public List<String> getItemNames() {
        List<String> names = new ArrayList<>();
        for (Item item : items) {
            if (item != null) {
                names.add(item.getName());
            }
        }
        return names;
    }

    public void setItemNames(String[] itemNames) {
        if (itemNames != null && itemNames.length == INVENTORY_SIZE) {
            this.itemNames = Arrays.copyOf(itemNames, INVENTORY_SIZE);

            // Rebuild the items list based on itemNames
            items.clear();
            for (String itemName : itemNames) {
                if (itemName != null) {
                    items.add(ItemManager.getItem(itemName));
                } else {
                    items.add(null);
                }
            }
        }
    }

    public Item getSelectedItem() {
        String itemName = itemNames[selectedHotbarSlot];
        return ItemManager.getItem(itemName);
    }

    public List<Item> getItems() {
        return new ArrayList<>(items);
    }

    public Item getItem(int index) {
        if (index >= 0 && index < items.size()) {
            return items.get(index);
        } else {
            return null;
        }
    }

    // Saving the inventory to a JSON file
    public void saveInventory() {
        try {
            Json json = new Json();
            InventoryData data = new InventoryData();
            data.items = new ItemData[INVENTORY_SIZE];

            for (int i = 0; i < items.size(); i++) {
                Item item = items.get(i);
                if (item != null) {
                    ItemData itemData = new ItemData();
                    itemData.name = item.getName();
                    itemData.count = item.getCount();
                    data.items[i] = itemData;
                }
            }

            FileHandle file = Gdx.files.local(SAVE_FILE);
            file.writeString(json.toJson(data), false);
        } catch (Exception e) {
            System.err.println("Failed to save inventory: " + e.getMessage());
        }
    }

    public void setHotbarCache(List<Item> items) {
        this.hotbarCache = new ArrayList<>();
        for (Item item : items) {
            this.hotbarCache.add(item != null ? item.copy() : null);
        }
    }    public List<Item> getHotbarCache() {
        return hotbarCache;
    }

    public void restoreHotbarFromCache() {
        if (hotbarCache != null) {
            int startIndex = INVENTORY_SIZE - HOTBAR_SIZE;
            for (int i = 0; i < HOTBAR_SIZE; i++) {
                setItem(startIndex + i, hotbarCache.get(i));
            }
        }
    }



    public boolean canAddItem(Item item) {
        if (item == null) return false;

        // First check if we can stack with existing items
        for (Item existingItem : items) {
            if (existingItem != null && existingItem.canStackWith(item)) {
                if (existingItem.getCount() + item.getCount() <= Item.MAX_STACK_SIZE) {
                    return true;
                }
            }
        }

        // Then check for empty slots
        return getFirstEmptySlot() != -1;
    }

    private int getFirstEmptySlot() {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i) == null) {
                return i;
            }
        }
        return -1;
    }

    private static class InventoryData {
        public ItemData[] items;
    }

    private static class ItemData {
        public String name;
        public int count;
    }
}

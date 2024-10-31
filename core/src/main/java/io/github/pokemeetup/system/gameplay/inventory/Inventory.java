package io.github.pokemeetup.system.gameplay.inventory;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Json;
import io.github.pokemeetup.utils.GameLogger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Inventory {
    public static final int HOTBAR_SIZE = 9;  // First 9 slots
    public static final int INVENTORY_ROWS = 3;
    public static final int INVENTORY_COLS = 9;
    public static final int INVENTORY_SIZE = INVENTORY_ROWS * INVENTORY_COLS;
    public static final int INVENTORY_CAPACITY = INVENTORY_SIZE * 64;  // Total 27 slots

    public static final int CRAFTING_GRID_SIZE = 4; // 2x2 for inventory crafting
    private static final String SAVE_FILE = "assets/save/inventory.json";
    private final Item[][] craftingGrid;
    private float heldItemX;
    private float heldItemY;
    private String[] itemNames; // Array of item names for serialization
    private Item craftingResult;
    private int selectedHotbarSlot;
    private Item heldItem = null;  // Currently held item
    private int heldItemCount = 0; // Count of held item
    private List<Item> hotbarCache;
    private final List<Item> inventoryItems;  // Main inventory slots
    private final List<Item> hotbarItems;     // Hotbar slots

    public Inventory() {
        inventoryItems = new ArrayList<>(INVENTORY_SIZE);
        hotbarItems = new ArrayList<>(HOTBAR_SIZE);
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            inventoryItems.add(null);
        }
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            hotbarItems.add(null);
        }
        craftingGrid = new Item[2][2];
        selectedHotbarSlot = 0;
        GameLogger.info("Created new inventory with " + INVENTORY_SIZE + " slots");
    }    // Update getSelectedIndex to work with first 9 slots

    public int getSelectedIndex() {
        return selectedHotbarSlot;  // This should be 0-8
    }

    public void selectItem(int index) {
        if (index >= 0 && index < HOTBAR_SIZE) {  // Only allow selecting first 9 slots
            selectedHotbarSlot = index;
        }
    }

    public List<Item> getHotbarItems() {
        return hotbarItems;
    }

    public int getSelectedHotbarSlot() {
        return selectedHotbarSlot;
    }

    public void selectHotbarSlot(int index) {
        if (index >= 0 && index < HOTBAR_SIZE) {
            selectedHotbarSlot = index;
        }
    }


    public boolean addItemToHotbar(Item newItem) {
        // Try to stack with existing items in hotbar
        for (int i = 0; i < hotbarItems.size(); i++) {
            Item existingItem = hotbarItems.get(i);
            if (existingItem != null && existingItem.canStackWith(newItem)) {
                int leftover = existingItem.addToStack(newItem.getCount());
                if (leftover == 0) {
                    return true; // Successfully added all items
                }
                newItem.setCount(leftover);
            }
        }

        // Add to empty slot if any
        for (int i = 0; i < hotbarItems.size(); i++) {
            if (hotbarItems.get(i) == null) {
                hotbarItems.set(i, newItem.copy());
                return true; // Successfully added item to hotbar
            }
        }

        return false; // Hotbar is full
    }

    public boolean addItemToInventory(Item newItem) {
        // Try to stack with existing items in inventory
        for (int i = 0; i < inventoryItems.size(); i++) {
            Item existingItem = inventoryItems.get(i);
            if (existingItem != null && existingItem.canStackWith(newItem)) {
                int leftover = existingItem.addToStack(newItem.getCount());
                if (leftover == 0) {
                    return true; // Successfully added all items
                }
                newItem.setCount(leftover);
            }
        }

        // Add to empty slot if any
        for (int i = 0; i < inventoryItems.size(); i++) {
            if (inventoryItems.get(i) == null) {
                inventoryItems.set(i, newItem.copy());
                return true; // Successfully added item to inventory
            }
        }

        return false; // Inventory is full
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

                // Load inventory items
                for (int i = 0; i < data.items.length; i++) {
                    ItemData itemData = data.items[i];
                    if (itemData != null) {
                        Item item = ItemManager.getItem(itemData.name);
                        if (item != null) {
                            item.setCount(itemData.count);
                            inventory.inventoryItems.set(i, item);
                        }
                    }
                }

                // Load hotbar items
                for (int i = 0; i < data.hotBarItems.length; i++) {
                    ItemData itemData = data.hotBarItems[i];
                    if (itemData != null) {
                        Item item = ItemManager.getItem(itemData.name);
                        if (item != null) {
                            item.setCount(itemData.count);
                            inventory.hotbarItems.set(i, item);
                        }
                    }
                }

                return inventory;
            } catch (Exception e) {
                GameLogger.info("Failed to load inventory: " + e.getMessage());
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
        if (index >= 0 && index < inventoryItems.size()) {
            inventoryItems.set(index, item);
            if (itemNames != null) {
                itemNames[index] = item != null ? item.getName() : null;
            }
        }
    }

    public void loadFromStrings(List<String> itemStrings) {
        if (itemStrings == null) {
            GameLogger.info("No inventory data to load");
            return;
        }

        // Clear existing items first
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            inventoryItems.set(i, null);
        }

        GameLogger.info("Loading inventory items: " + itemStrings);

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
                        GameLogger.info("Unknown item: " + itemName);
                    }
                }
            } catch (Exception e) {
                GameLogger.info("Error loading item: " + itemString + " - " + e.getMessage());
            }
        }

        // Debug output final inventory state
        GameLogger.info("Final inventory contents:");
        for (Item item : inventoryItems) {
            if (item != null) {
                GameLogger.info("- " + item.getName() + " x" + item.getCount());
            }
        }
    }

    public void clear() {
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            inventoryItems.set(i, null);
        }
        GameLogger.info("Inventory cleared");
    }
    public boolean addItem(Item newItem) {
        // First try to add to the hotbar
        if (addItemToHotbar(newItem.copy())) {
            return true;
        }
        return addItemToInventory(newItem);
    }
    public List<String> getItemNames() {
        List<String> names = new ArrayList<>();
        for (Item item : inventoryItems) {
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
            inventoryItems.clear();
            for (String itemName : itemNames) {
                if (itemName != null) {
                    inventoryItems.add(ItemManager.getItem(itemName));
                } else {
                    inventoryItems.add(null);
                }
            }
        }
    }

    public Item getSelectedItem() {
        String itemName = itemNames[selectedHotbarSlot];
        return ItemManager.getItem(itemName);
    }

    public List<Item> getItems() {
        return new ArrayList<>(inventoryItems);
    }

    public Item getItem(int index) {
        if (index >= 0 && index < inventoryItems.size()) {
            return inventoryItems.get(index);
        } else {
            return null;
        }
    }

    private List<Item> items; // Main inventory items

    public Item getItemAtSlot(int index) {
        return items.get(index);
    }

    public void setItemAtSlot(int index, Item item) {
        items.set(index, item);
    }


    public void setHotbarItemAtSlot(int index, Item item) {
        // Ensure the index is within bounds
        while (hotbarItems.size() <= index) {
            hotbarItems.add(null); // Fill with nulls if necessary
        }
        hotbarItems.set(index, item);
    }



    // Saving the inventory to a JSON file
    public void saveInventory() {
        try {
            Json json = new Json();
            InventoryData data = new InventoryData();
            data.items = new ItemData[INVENTORY_SIZE];

            for (int i = 0; i < inventoryItems.size(); i++) {
                Item item = inventoryItems.get(i);
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
            GameLogger.info("Failed to save inventory: " + e.getMessage());
        }
    }

    public void setHotbarCache(List<Item> items) {
        this.hotbarCache = new ArrayList<>();
        for (Item item : items) {
            this.hotbarCache.add(item != null ? item.copy() : null);
        }
    }

    public List<Item> getHotbarCache() {
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
        for (Item existingItem : inventoryItems) {
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
        for (int i = 0; i < inventoryItems.size(); i++) {
            if (inventoryItems.get(i) == null) {
                return i;
            }
        }
        return -1;
    }

    private static class InventoryData {
        public ItemData[] items;
        public ItemData[] hotBarItems;
    }

    private static class ItemData {
        public String name;
        public int count;
    }
}

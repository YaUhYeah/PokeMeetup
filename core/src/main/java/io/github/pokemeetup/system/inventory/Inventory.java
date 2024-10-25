package io.github.pokemeetup.system.inventory;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class Inventory {
    public static final int INVENTORY_SIZE = 9; // Slots 1-9
    private static final String SAVE_FILE = "save/inventory.json";
    private String[] itemNames; // Array of item names for serialization
    private int selectedIndex;
    private List<Item> items = new CopyOnWriteArrayList<>();

    public Inventory() {
        items = new ArrayList<>(INVENTORY_SIZE);
        // Initialize with null slots
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            items.add(null);
        }
        selectedIndex = 0;
        System.out.println("Created new inventory with " + INVENTORY_SIZE + " slots");
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

    public void selectItem(int index) {
        if (index >= 0 && index < items.size()) {
            selectedIndex = index;
        }
    }

    public int getSelectedIndex() {
        return selectedIndex;
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
        String itemName = itemNames[selectedIndex];
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
    }    private static class InventoryData {
        public ItemData[] items;
    }

    private static class ItemData {
        public String name;
        public int count;
    }
}

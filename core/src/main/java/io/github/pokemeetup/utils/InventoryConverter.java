package io.github.pokemeetup.utils;

import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.PlayerData;
import io.github.pokemeetup.system.gameplay.inventory.Inventory;
import io.github.pokemeetup.system.gameplay.inventory.Item;
import io.github.pokemeetup.system.gameplay.inventory.ItemManager;

import java.util.ArrayList;
import java.util.List;
public class InventoryConverter {

    // Convert a list of items to a list of strings
    public static List<String> toPlayerDataFormat(List<Item> items) {
        List<String> itemStrings = new ArrayList<>();
        for (Item item : items) {
            if (item != null) {
                String itemString = item.getName() + ":" + item.getCount();
                itemStrings.add(itemString);
            }
        }
        return itemStrings;
    }

    // Apply inventory and hotbar data to the player's inventory
    public static void applyInventoryDataToPlayer(PlayerData playerData, Player player) {
        Inventory inventory = new Inventory();

        // Apply inventory items
        List<String> inventoryData = playerData.getInventoryItems();
        if (inventoryData != null) {
            for (String itemString : inventoryData) {
                addItemToInventory(itemString, inventory.getItems());
            }
        }

        // Apply hotbar items
        List<String> hotbarData = playerData.getHotbarItems();
        if (hotbarData != null) {
            for (String itemString : hotbarData) {
                addItemToInventory(itemString, inventory.getHotbarItems());
            }
        }

        player.setInventory(inventory);
    }

    private static void addItemToInventory(String itemString, List<Item> itemList) {
        if (itemString != null && !itemString.equals("null")) {
            String[] parts = itemString.split(":");
            if (parts.length == 2) {
                String itemName = parts[0];
                int count;
                try {
                    count = Integer.parseInt(parts[1]);
                } catch (NumberFormatException e) {
                    GameLogger.info("Invalid item count for item: " + itemName);
                    return;
                }
                Item item = ItemManager.getItem(itemName);
                if (item != null) {
                    Item newItem = item.copy();
                    newItem.setCount(count);
                    itemList.add(newItem);
                } else {
                    GameLogger.info("ItemManager could not find item: " + itemName);
                }
            } else {
                GameLogger.info("Invalid item string format: " + itemString);
            }
        }
    }public static void fromPlayerDataFormat(List<String> inventoryStrings, Inventory inventory) {
        if (inventoryStrings == null || inventory == null) return;

        for (String itemString : inventoryStrings) {
            String[] parts = itemString.split(":");
            if (parts.length != 2) continue;

            String itemName = parts[0];
            int count = 0;
            try {
                count = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                GameLogger.error("Invalid item count format: " + itemString);
                continue;
            }

            Item item = ItemManager.getItem(itemName);
            if (item != null) {
                Item newItem = item.copy();
                newItem.setCount(count);
                inventory.addItem(newItem);
            } else {
                GameLogger.error("Unknown item ID: " + itemName);
            }
        }
    }
}

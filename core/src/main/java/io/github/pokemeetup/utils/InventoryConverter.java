package io.github.pokemeetup.utils;

import io.github.pokemeetup.system.gameplay.inventory.Inventory;
import io.github.pokemeetup.system.gameplay.inventory.Item;
import io.github.pokemeetup.system.gameplay.inventory.ItemManager;

import java.util.ArrayList;
import java.util.List;

public class InventoryConverter {
    public static List<String> toPlayerDataFormat(Inventory inventory) {
        List<String> result = new ArrayList<>();

        // Convert inventory items to string format
        for (Item item : inventory.getItems()) {
            if (item != null && item.getCount() > 0) {
                result.add(formatItem(item));
            }
        }

        return result;
    }

    private static String formatItem(Item item) {
        return item.getName() + ":" + item.getCount();
    }

    public static void fromPlayerDataFormat(List<String> inventoryStrings, Inventory inventory) {
        if (inventoryStrings == null) return;

        for (String itemString : inventoryStrings) {
            String[] parts = itemString.split(":");
            if (parts.length != 2) continue;

            try {
                String itemName = parts[0];
                int count = Integer.parseInt(parts[1]);

                Item item = ItemManager.getItem(itemName);
                if (item != null) {
                    Item newItem = item.copy();
                    newItem.setCount(count);
                    inventory.addItem(newItem);
                }
            } catch (NumberFormatException e) {
                GameLogger.error("Invalid item count format: " + itemString);
            }
        }
    }
}

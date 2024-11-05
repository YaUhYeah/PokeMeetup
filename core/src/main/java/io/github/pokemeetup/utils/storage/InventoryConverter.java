package io.github.pokemeetup.utils.storage;

import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.data.PlayerData;
import io.github.pokemeetup.system.gameplay.inventory.Inventory;
import io.github.pokemeetup.system.gameplay.inventory.Item;
import io.github.pokemeetup.system.data.ItemData;
import io.github.pokemeetup.system.gameplay.inventory.ItemManager;
import io.github.pokemeetup.utils.GameLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Utility class for converting between Item and ItemData, and handling inventory data transformations.
 */
public class InventoryConverter {

    /**
     * Converts an Item to ItemData.
     *
     * @param item The Item to convert.
     * @return The corresponding ItemData.
     */
    public static ItemData itemToItemData(Item item) {
        if (item == null) {
            return null;
        }
        ItemData itemData = new ItemData();
        itemData.setItemId(item.getName());
        itemData.setCount(item.getCount());
        itemData.setUuid(item.getUuid() != null ? item.getUuid() : UUID.randomUUID());
        return itemData;
    }

    /**
     * Converts ItemData to an Item.
     *
     * @param itemData The ItemData to convert.
     * @return The corresponding Item.
     */
    public static Item itemDataToItem(ItemData itemData) {
        if (itemData == null) {
            return null;
        }
        Item item = ItemManager.getItem(itemData.getItemId());
        if (item == null) {
            GameLogger.error("ItemManager could not find item with ID: " + itemData.getItemId());
            return null;
        }
        item.setCount(itemData.getCount());
        item.setUuid(itemData.getUuid() != null ? itemData.getUuid() : UUID.randomUUID());
        return item;
    }

    /**
     * Converts a list of Items to a list of ItemData.
     *
     * @param items The list of Items to convert.
     * @return A new list of ItemData.
     */
    public static List<ItemData> itemsToItemDataList(List<Item> items) {
        List<ItemData> itemDataList = new ArrayList<>();
        for (Item item : items) {
            itemDataList.add(itemToItemData(item));
        }
        return itemDataList;
    }

    /**
     * Converts a list of ItemData to a list of Items.
     *
     * @param itemDataList The list of ItemData to convert.
     * @return A new list of Items.
     */
    public static List<Item> itemDataListToItems(List<ItemData> itemDataList) {
        List<Item> items = new ArrayList<>();
        for (ItemData itemData : itemDataList) {
            items.add(itemDataToItem(itemData));
        }
        return items;
    }

    /**
     * Converts and applies inventory and hotbar data from io.github.pokemeetup.system.data.PlayerData to the Player's Inventory.
     *
     * @param playerData The io.github.pokemeetup.system.data.PlayerData containing inventory information.
     * @param player     The Player instance to apply the inventory to.
     */
    public static void applyInventoryDataToPlayer(PlayerData playerData, Player player) {
        if (playerData == null || player == null) {
            GameLogger.error("io.github.pokemeetup.system.data.PlayerData or Player is null. Cannot apply inventory data.");
            return;
        }

        Inventory inventory = player.getInventory();
        if (inventory == null) {
            GameLogger.error("Player's Inventory is null. Cannot apply inventory data.");
            return;
        }

        // Convert and set main inventory items
        List<ItemData> inventoryData = playerData.getInventoryItems();
        List<ItemData> convertedInventory = duplicateItemDataList(inventoryData);
        inventory.setAllItems(convertedInventory); // Directly setting ItemData as per Inventory class

        // Convert and set hotbar items

        GameLogger.info("Applied inventory and hotbar data to player: " + playerData.getUsername());
    }

    /**
     * Extracts inventory and hotbar data from the Player's Inventory and updates io.github.pokemeetup.system.data.PlayerData.
     *
     * @param player     The Player instance whose inventory is to be extracted.
     * @param playerData The io.github.pokemeetup.system.data.PlayerData instance to update with inventory information.
     */
    public static void extractInventoryDataFromPlayer(Player player, PlayerData playerData) {
        if (player == null || playerData == null) {
            GameLogger.error("Player or io.github.pokemeetup.system.data.PlayerData is null. Cannot extract inventory data.");
            return;
        }

        Inventory inventory = player.getInventory();
        if (inventory == null) {
            GameLogger.error("Player's Inventory is null. Cannot extract inventory data.");
            return;
        }

        // Extract and convert main inventory items
        List<ItemData> inventoryItems = inventory.getAllItems();
        playerData.setInventoryItems(inventoryItems);


//        GameLogger.info("Extracted inventory and hotbar data from player: " + playerData.getUsername());
    }

    // Helper method to duplicate ItemData from Items
    public static List<ItemData> duplicateItemDataListFromItems(List<Item> items) {
        List<ItemData> duplicatedList = new ArrayList<>();
        for (Item item : items) {
            if (item != null) {
                duplicatedList.add(itemToItemData(item));
            } else {
                duplicatedList.add(null);
            }
        }
        return duplicatedList;
    }

    /**
     * Duplicates a list of ItemData by creating copies.
     * Preserves UUIDs.
     *
     * @param itemDataList The original list of ItemData.
     * @return A new list with copies of ItemData.
     */
    public static List<ItemData> duplicateItemDataList(List<ItemData> itemDataList) {
        List<ItemData> duplicatedList = new ArrayList<>();
        for (ItemData item : itemDataList) {
            if (item != null) {
                duplicatedList.add(item.copy());
            } else {
                duplicatedList.add(null);
            }
        }
        return duplicatedList;
    }

    /**
     * Creates deep copies of a list of ItemData with new UUIDs.
     * Useful for duplicating items without UUID conflicts.
     *
     * @param items The original list of ItemData.
     * @return A new list with copies of ItemData with unique UUIDs.
     */
    public static List<ItemData> duplicateItemDataWithNewUUIDs(List<ItemData> items) {
        List<ItemData> duplicatedList = new ArrayList<>();
        for (ItemData item : items) {
            if (item != null) {
                duplicatedList.add(item.copyWithUUID());
            } else {
                duplicatedList.add(null);
            }
        }
        return duplicatedList;
    }

    /**
     * Converts a list of ItemData to a list of strings in the format "ItemName:Count".
     * This method is deprecated in favor of using List<ItemData>.
     *
     * @param items The list of ItemData.
     * @return A list of strings representing the items.
     */
    @Deprecated
    public static List<String> toPlayerDataFormat(List<ItemData> items) {
        List<String> itemStrings = new ArrayList<>();
        for (ItemData item : items) {
            if (item != null) {
                itemStrings.add(item.getItemId() + ":" + item.getCount());
            } else {
                itemStrings.add(null);
            }
        }
        return itemStrings;
    }

    /**
     * Converts a list of ItemData to a list of ItemData copies.
     * Preserves UUIDs.
     *
     * @param itemDataList The list of ItemData objects.
     * @return A new list of ItemData copies.
     */
    public static List<ItemData> fromPlayerDataFormat(List<ItemData> itemDataList) {
        List<ItemData> items = new ArrayList<>();
        for (ItemData data : itemDataList) {
            if (data != null) {
                items.add(data.copy());
            } else {
                items.add(null);
            }
        }
        return items;
    }

    /**
     * Adds an Item to the Inventory, attempting to stack it first.
     * Prioritizes the hotbar slots.
     *
     * @param inventory The Inventory instance to add the item to.
     * @param newItem   The ItemData to add.
     * @return True if the item was added successfully, false otherwise.
     */
    public static boolean addItemToInventory(Inventory inventory, ItemData newItem) {
        if (inventory == null || newItem == null) {
            GameLogger.error("Inventory or newItem is null. Cannot add item.");
            return false;
        }

        synchronized (inventory) {
            // Use Inventory's addItem method which already handles hotbar prioritization
            boolean added = inventory.addItem(newItem.copyWithUUID());
            if (added) {
                GameLogger.info("Item added to inventory successfully: " + newItem.getItemId());
            } else {
                GameLogger.error("Failed to add item to inventory: " + newItem.getItemId());
            }
            return added;
        }
    }

    /**
     * Removes an Item from the Inventory based on slot index.
     *
     * @param inventory The Inventory instance.
     * @param slotIndex The slot index to remove the item from.
     * @return The removed ItemData, or null if the slot was empty or invalid.
     */
    public static ItemData removeItemFromInventory(Inventory inventory, int slotIndex) {
        if (inventory == null) {
            GameLogger.error("Inventory is null. Cannot remove item.");
            return null;
        }

        synchronized (inventory) {
            ItemData removedItem = inventory.getItemAt(slotIndex);
            inventory.setItemAt(slotIndex, null);
            if (removedItem != null) {
                GameLogger.info("Removed item from slot " + slotIndex + ": " + removedItem.getItemId());
            } else {
                GameLogger.error("No item found in slot " + slotIndex + " to remove.");
            }
            return removedItem;
        }
    }
}

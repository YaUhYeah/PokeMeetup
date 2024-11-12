package io.github.pokemeetup.utils.storage;

import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.data.PlayerData;
import io.github.pokemeetup.system.gameplay.inventory.Inventory;
import io.github.pokemeetup.system.gameplay.inventory.Item;
import io.github.pokemeetup.system.data.ItemData;
import io.github.pokemeetup.system.gameplay.inventory.ItemManager;
import io.github.pokemeetup.utils.GameLogger;

import java.util.*;

public class InventoryConverter {

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

        public static void applyInventoryDataToPlayer(PlayerData playerData, Player player) {
            if (playerData == null || player == null) {
                GameLogger.error("Cannot apply null PlayerData or Player");
                return;
            }

            try {
                Inventory inventory = player.getInventory();
                if (inventory == null) {
                    inventory = new Inventory();
                    player.setInventory(inventory);
                }

                // Clear existing inventory first
                inventory.clear();

                // Get inventory items with null checking
                List<ItemData> items = playerData.getInventoryItems();
                if (items == null) {
                    GameLogger.error("Inventory items list is null in PlayerData");
                    items = new ArrayList<>(Collections.nCopies(Inventory.INVENTORY_SIZE, null));
                }

                // Ensure list is properly sized
                while (items.size() < Inventory.INVENTORY_SIZE) {
                    items.add(null);
                }

                // Validate and apply each item
                for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
                    ItemData itemData = items.get(i);
                    if (itemData != null && itemData.isValid()) {
                        Item baseItem = ItemManager.getItem(itemData.getItemId());
                        if (baseItem != null) {
                            inventory.setItemAt(i, itemData);
                            GameLogger.info("Loaded item at slot " + i + ": " + itemData.getItemId() +
                                " x" + itemData.getCount() + " UUID: " + itemData.getUuid());
                        } else {
                            GameLogger.error("Invalid item ID in slot " + i + ": " + itemData.getItemId());
                        }
                    } else {
                        inventory.setItemAt(i, null);
                    }
                }
            } catch (Exception e) {
                GameLogger.error("Error applying inventory data: " + e.getMessage());
                e.printStackTrace();
            }
        }

        public static void extractInventoryDataFromPlayer(Player player, PlayerData playerData) {
            if (player == null || playerData == null) {
                GameLogger.error("Cannot extract inventory from null Player or PlayerData");
                return;
            }

            try {
                Inventory inventory = player.getInventory();
                if (inventory == null) {
                    GameLogger.error("Player inventory is null");
                    playerData.setInventoryItems(new ArrayList<>(Collections.nCopies(Inventory.INVENTORY_SIZE, null)));
                    return;
                }

                List<ItemData> items = new ArrayList<>(Inventory.INVENTORY_SIZE);
                for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
                    ItemData item = inventory.getItemAt(i);
                    if (item != null && item.isValid()) {
                        items.add(item.copy()); // Create deep copy
                    } else {
                        items.add(null);
                    }
                }
                playerData.setInventoryItems(items);
                GameLogger.info("Extracted " + items.stream().filter(Objects::nonNull).count() +
                    " items from inventory");
            } catch (Exception e) {
                GameLogger.error("Error extracting inventory data: " + e.getMessage());
                e.printStackTrace();
            }
        }

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
    public static boolean addItemToInventory(Inventory inventory, ItemData newItem) {
        if (inventory == null || newItem == null) {
            GameLogger.error("Inventory or newItem is null. Cannot add item.");
            return false;
        }

        synchronized (inventory) {
            boolean added = inventory.addItem(newItem.copyWithUUID());
            if (added) {
                GameLogger.info("Item added to inventory successfully: " + newItem.getItemId());
            } else {
                GameLogger.error("Failed to add item to inventory: " + newItem.getItemId());
            }
            return added;
        }
    }
}

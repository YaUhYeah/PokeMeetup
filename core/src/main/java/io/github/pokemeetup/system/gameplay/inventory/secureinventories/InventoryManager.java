package io.github.pokemeetup.system.gameplay.inventory.secureinventories;

import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.system.PlayerData;
import io.github.pokemeetup.system.gameplay.inventory.Inventory;
import io.github.pokemeetup.system.gameplay.inventory.Item;
import io.github.pokemeetup.system.gameplay.inventory.ItemManager;
import io.github.pokemeetup.system.gameplay.overworld.multiworld.WorldData;
import io.github.pokemeetup.system.gameplay.overworld.multiworld.WorldManager;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.InventoryConverter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class InventoryManager {
    private final Map<String, Inventory> playerInventories;
    private final Set<String> dirtyInventories;
    private final WorldManager worldManager;
    private final GameClient gameClient;
    private final boolean isMultiplayer;
    private final Object syncLock = new Object();

    public InventoryManager(WorldManager worldManager, GameClient gameClient) {
        this.worldManager = worldManager;
        this.gameClient = gameClient;
        this.isMultiplayer = (gameClient != null && !gameClient.isSinglePlayer());
        this.playerInventories = new ConcurrentHashMap<>();
        this.dirtyInventories = ConcurrentHashMap.newKeySet();
    }

    public void markDirty(String playerId) {
        synchronized (syncLock) {
            dirtyInventories.add(playerId);
        }
    }

    public Inventory getPlayerInventory(String playerId, PlayerData playerData) {
        return playerInventories.computeIfAbsent(playerId, id -> {
            Inventory inventory = loadInventory(playerId, playerData);
            if (inventory == null) {
                inventory = new Inventory();
            }
            return inventory;
        });
    }

    private Inventory loadInventory(String playerId, PlayerData playerData) {
        if (playerData == null) return null;

        List<String> inventoryItems = playerData.getInventoryItems();
        if (inventoryItems != null && !inventoryItems.isEmpty()) {
            Inventory inventory = new Inventory();
            InventoryConverter.fromPlayerDataFormat(inventoryItems, inventory);
            return inventory;
        }

        return null;
    }

    public void saveAll() {
        synchronized (syncLock) {
            for (String playerId : dirtyInventories) {
                savePlayerInventory(playerId);
            }
            dirtyInventories.clear();
        }
    }

    public void savePlayerInventory(String playerId) {
        synchronized (syncLock) {
            Inventory inventory = playerInventories.get(playerId);
            if (inventory == null) return;

            try {
                WorldData worldData = worldManager.getCurrentWorld();
                if (worldData != null) {
                    PlayerData playerData = worldData.getPlayerData(playerId);
                    if (playerData != null) {
                        saveInventoryToPlayerData(inventory, playerData);

                        worldManager.saveWorld(worldData);
                        GameLogger.info("Saved inventory for " + playerId);
                    }
                }

                dirtyInventories.remove(playerId);
            } catch (Exception e) {
                GameLogger.error("Failed to save inventory for " + playerId + ": " + e.getMessage());
            }
        }
    }

    public void handleNetworkUpdate(NetworkProtocol.InventoryUpdate update) {
        if (!isMultiplayer) return;

        try {
            Inventory inventory = new Inventory();
            InventoryConverter.fromPlayerDataFormat(update.itemNames, inventory);

            synchronized (syncLock) {
                playerInventories.put(update.username, inventory);
                dirtyInventories.add(update.username);
            }

            GameLogger.info("Received inventory update for " + update.username);
        } catch (Exception e) {
            GameLogger.error("Failed to handle inventory update: " + e.getMessage());
        }
    }

    public void syncToServer(String playerId) {
        if (!isMultiplayer || gameClient == null) return;

        synchronized (syncLock) {
            if (!dirtyInventories.contains(playerId)) return;

            Inventory inventory = playerInventories.get(playerId);
            if (inventory != null) {
                NetworkProtocol.InventoryUpdate update = new NetworkProtocol.InventoryUpdate();
                update.username = playerId;
                update.itemNames = (ArrayList<String>) InventoryConverter.toPlayerDataFormat(inventory);

                gameClient.sendInventoryUpdate(update.username, update.itemNames);
            }
        }
    }

    public void saveInventoryToPlayerData(Inventory inventory, PlayerData playerData) {
        if (inventory == null || playerData == null) {
            return;
        }
        try {
            List<String> inventoryStrings = InventoryConverter.toPlayerDataFormat(inventory);
            playerData.setInventoryItems(inventoryStrings);

            GameLogger.info("Saved inventory data for player: " + playerData.getUsername());
        } catch (Exception e) {
            GameLogger.error("Failed to save inventory data for player " + playerData.getUsername() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void update(float delta) {
        if (!isMultiplayer) return;

        synchronized (syncLock) {
            if (!dirtyInventories.isEmpty()) {
                for (String playerId : dirtyInventories) {
                    syncToServer(playerId);
                }
            }
        }
    }

    public boolean isDirty(String playerId) {
        synchronized (syncLock) {
            return dirtyInventories.contains(playerId);
        }
    }

    public boolean addItemToPlayer(String playerId, String itemId, int count) {
        synchronized (syncLock) {
            Inventory inventory = playerInventories.get(playerId);
            if (inventory != null) {
                Item item = ItemManager.getItem(itemId);
                if (item != null) {
                    Item newItem = item.copy();
                    newItem.setCount(count);
                    boolean added = inventory.addItem(newItem);
                    if (added) {
                        markDirty(playerId);
                    }
                    return added;
                } else {
                    GameLogger.error("Unknown item ID: " + itemId);
                }
            }
            return false;
        }
    }

    public void dispose() {
        saveAll();
        playerInventories.clear();
        dirtyInventories.clear();
    }
}

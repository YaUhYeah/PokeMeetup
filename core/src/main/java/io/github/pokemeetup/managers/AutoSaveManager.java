package io.github.pokemeetup.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.PlayerData;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.multiworld.WorldData;
import io.github.pokemeetup.system.inventory.Item;
import io.github.pokemeetup.system.inventory.ItemManager;

import java.util.ArrayList;
import java.util.List;

public class AutoSaveManager {
    private static final float SAVE_INTERVAL = 30f; // Save every 30 seconds
    private final World world;
    private final GameClient gameClient;
    private float saveTimer = 0;

    public AutoSaveManager(World world, GameClient gameClient) {
        this.world = world;
        this.gameClient = gameClient;
    }

    public void update(float delta) {
        saveTimer += delta;
        if (saveTimer >= SAVE_INTERVAL) {
            saveTimer = 0;
            performAutoSave();
        }
    }


    public void performAutoSave() {
        if (gameClient.isSinglePlayer()) {
            saveWorldData();
        } else {
            PlayerData playerData = world.getPlayerData();
            if (playerData != null) {
                // Update player data before saving
                updatePlayerData(playerData);
                gameClient.savePlayerState(playerData);
            } else {
                System.err.println("PlayerData is null. Skipping multiplayer save.");
            }
        }
    }

    private void updatePlayerData(PlayerData playerData) {
        // Get current player state from world
        Player player = world.getPlayer(); // Need to add this getter to World
        if (player != null) {
            playerData.setPosition(player.getX(), player.getY());
            playerData.setDirection(player.getDirection());
            playerData.setMoving(player.isMoving());
            playerData.setWantsToRun(player.isRunning());

            // Update inventory
            List<String> inventoryStrings = new ArrayList<>();
            for (Item item : player.getInventory().getItems()) {
                if (item != null) {
                    inventoryStrings.add(item.getName() + ":" + item.getCount());
                }
            }
            playerData.setInventory(inventoryStrings);
        }
    }
    private void saveWorldData() {
        try {
            Json json = new Json();
            json.setUsePrototypes(false); // Important for clean serialization

            FileHandle worldDir = Gdx.files.local("worlds/" + world.getName());
            if (!worldDir.exists()) {
                worldDir.mkdirs();
            }

            WorldData worldData = world.getWorldData();

            // Update current player data before saving
            PlayerData currentPlayer = world.getPlayerData();
            if (currentPlayer != null) {
                System.out.println("Saving current player state");
                updatePlayerData(currentPlayer);
                worldData.savePlayerData(currentPlayer.getUsername(), currentPlayer);
            }

            worldData.updateLastPlayed();
            FileHandle worldFile = worldDir.child("world.json");

            String jsonString = json.prettyPrint(worldData);
            System.out.println("Saving world data: " + jsonString);

            worldFile.writeString(jsonString, false);
            System.out.println("World save complete");
        } catch (Exception e) {
            System.err.println("Failed to save world: " + e.getMessage());
            e.printStackTrace();
        }
    }
    private List<String> convertInventoryToStrings(String[] inventory) {
        List<String> result = new ArrayList<>();
        if (inventory != null) {
            for (String itemName : inventory) {
                if (itemName != null) {
                    Item item = ItemManager.getItem(itemName);
                    if (item != null) {
                        result.add(itemName + ":" + item.getCount());
                    }
                }
            }
        }
        return result;
    }


}

package io.github.pokemeetup.system;

import io.github.pokemeetup.multiplayer.server.ServerStorageSystem;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.multiworld.WorldData;

public class PlayerDataManager {
    private static final float AUTO_SAVE_INTERVAL = 30f; // Save every 30 seconds
    private final Player player;
    private final World world;
    private final boolean isMultiplayer;
    private final ServerStorageSystem storage;
    private float saveTimer = 0;

    public PlayerDataManager(Player player, World world, boolean isMultiplayer, ServerStorageSystem storage) {
        this.player = player;
        this.world = world;
        this.isMultiplayer = isMultiplayer;
        this.storage = storage;
    }

    public void update(float delta) {
        if (!isMultiplayer) {
            saveTimer += delta;
            if (saveTimer >= AUTO_SAVE_INTERVAL) {
                saveTimer = 0;
                savePlayerState();
            }
        }
    }

    public void savePlayerState() {
        try {
            PlayerData currentState = new PlayerData(player.getUsername());
            currentState.updateFromPlayer(player);

            // Save inventory


            // Save to world data
            WorldData worldData = world.getWorldData();
            if (worldData != null) {
                worldData.savePlayerData(player.getUsername(), currentState);
                System.out.println("Saved player state for: " + player.getUsername() +
                    " at position: " + currentState.getX() + "," + currentState.getY());
            }

        } catch (Exception e) {
            System.err.println("Failed to save player state: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void savePlayer(String username, PlayerData data) {
        storage.savePlayerData(username, data);
    }

    public PlayerData loadPlayer(String username) {
        return storage.loadPlayerData(username);
    }

    public void loadPlayerState() {
        try {
            WorldData worldData = world.getWorldData();
            if (worldData != null) {
                PlayerData savedState = worldData.getPlayerData(player.getUsername());
                if (savedState != null) {
                    // Apply saved state
                    savedState.applyToPlayer(player);

                    System.out.println("Loaded player state for: " + player.getUsername() +
                        " at position: " + savedState.getX() + "," + savedState.getY());
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load player state: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

package io.github.pokemeetup.multiplayer.server;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import io.github.pokemeetup.system.data.PlayerData;
import io.github.pokemeetup.system.data.WorldData;
import io.github.pokemeetup.utils.GameLogger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServerStorageSystem {
    private final String baseDir;
    private final Json json;
    private final Map<String, WorldData> worldCache;
    private final Map<String, PlayerData> playerCache;

    public ServerStorageSystem() {
        // Use local storage for Android
        boolean isAndroid = (Gdx.app != null && Gdx.app.getType() == Application.ApplicationType.Android);
        this.baseDir = isAndroid ? "" : "server/data/";
        this.json = new Json();
        this.worldCache = new ConcurrentHashMap<>();
        this.playerCache = new ConcurrentHashMap<>();
        initializeDirectories();
    }

    private void initializeDirectories() {
        try {
            FileHandle worldsDir = Gdx.files.local(baseDir + "worlds");
            FileHandle playersDir = Gdx.files.local(baseDir + "players");

            if (!worldsDir.exists()) {
                worldsDir.mkdirs();
            }
            if (!playersDir.exists()) {
                playersDir.mkdirs();
            }
            GameLogger.info("Storage directories initialized");
        } catch (Exception e) {
            GameLogger.error("Failed to create storage directories: " + e.getMessage());
        }
    }

    public void saveWorld(WorldData world) {
        if (world == null || world.getName() == null) {
            return;
        }

        try {
            FileHandle file = Gdx.files.local(baseDir + "worlds/" + world.getName() + ".json");
            String jsonData = json.prettyPrint(world);
            file.writeString(jsonData, false);
            worldCache.put(world.getName(), world);
            GameLogger.info("Saved world: " + world.getName());
        } catch (Exception e) {
            GameLogger.error("Failed to save world: " + world.getName());
        }
    }

    public void savePlayerData(String username, PlayerData playerData) {
        if (username == null || playerData == null) {
            return;
        }

        try {
            FileHandle file = Gdx.files.local(baseDir + "players/" + username + ".json");
            String jsonData = json.prettyPrint(playerData);
            file.writeString(jsonData, false);
            playerCache.put(username, playerData);
            GameLogger.info("Saved player data for: " + username);
        } catch (Exception e) {
            GameLogger.error("Failed to save player data for: " + username);
        }
    }

    public WorldData loadWorld(String worldName) {
        if (worldCache.containsKey(worldName)) {
            return worldCache.get(worldName);
        }

        try {
            FileHandle file = Gdx.files.local(baseDir + "worlds/" + worldName + ".json");
            if (!file.exists()) {
                return null;
            }

            String jsonData = file.readString();
            WorldData world = json.fromJson(WorldData.class, jsonData);
            worldCache.put(worldName, world);
            return world;
        } catch (Exception e) {
            GameLogger.error("Failed to load world: " + worldName);
            return null;
        }
    }

    public PlayerData loadPlayerData(String username) {
        if (playerCache.containsKey(username)) {
            return playerCache.get(username);
        }

        try {
            FileHandle file = Gdx.files.local(baseDir + "players/" + username + ".json");
            if (!file.exists()) {
                return null;
            }

            String jsonData = file.readString();
            PlayerData playerData = json.fromJson(PlayerData.class, jsonData);
            playerCache.put(username, playerData);
            return playerData;
        } catch (Exception e) {
            GameLogger.error("Failed to load player data for: " + username);
            return null;
        }
    }

    public void deleteWorld(String worldName) {
        try {
            FileHandle file = Gdx.files.local(baseDir + "worlds/" + worldName + ".json");
            if (file.exists()) {
                file.delete();
            }
            worldCache.remove(worldName);
            GameLogger.info("Deleted world: " + worldName);
        } catch (Exception e) {
            GameLogger.error("Failed to delete world: " + worldName);
        }
    }

    public Map<String, WorldData> getAllWorlds() {
        Map<String, WorldData> worlds = new HashMap<>();
        try {
            FileHandle worldsDir = Gdx.files.local(baseDir + "worlds");
            if (worldsDir.exists()) {
                for (FileHandle file : worldsDir.list(".json")) {
                    String worldName = file.nameWithoutExtension();
                    WorldData world = loadWorld(worldName);
                    if (world != null) {
                        worlds.put(worldName, world);
                    }
                }
            }
        } catch (Exception e) {
            GameLogger.error("Failed to load worlds");
        }
        return worlds;
    }

    public void shutdown() {
        // Save all cached data
        for (WorldData world : worldCache.values()) {
            saveWorld(world);
        }
        for (PlayerData player : playerCache.values()) {
            savePlayerData(player.getUsername(), player);
        }
        GameLogger.info("Storage system shutdown complete");
    }
}

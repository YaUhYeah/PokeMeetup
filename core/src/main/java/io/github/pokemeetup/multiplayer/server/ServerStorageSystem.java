package io.github.pokemeetup.multiplayer.server;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import io.github.pokemeetup.system.data.PlayerData;
import io.github.pokemeetup.system.data.WorldData;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.storage.GameFileSystem;
import io.github.pokemeetup.utils.storage.JsonConfig;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServerStorageSystem {
    private static final String SERVER_BASE_DIR = "server/";
    private static final String SERVER_WORLD_DIR = SERVER_BASE_DIR + "worlds/";
    private static final String SERVER_PLAYER_DIR = SERVER_BASE_DIR + "players/";
    private final String baseDir;
    private final Json json;
    private final Map<String, WorldData> worldCache;
    private final Map<String, PlayerData> playerCache;
    private final GameFileSystem fs;

    public ServerStorageSystem() {
        this.baseDir = SERVER_BASE_DIR; // Use the constant for consistency;
        this.json = JsonConfig.getInstance();
        this.json.setOutputType(JsonWriter.OutputType.json);
        this.worldCache = new ConcurrentHashMap<>();
        this.playerCache = new ConcurrentHashMap<>();
        this.fs = GameFileSystem.getInstance();
        initializeDirectories();
    }

    private void initializeDirectories() {
        try {
            // Create all required directories
            fs.createDirectory(SERVER_BASE_DIR);
            fs.createDirectory(SERVER_WORLD_DIR);
            fs.createDirectory(SERVER_PLAYER_DIR);
            fs.createDirectory(SERVER_WORLD_DIR + "backups/");

            GameLogger.info("Server storage directories initialized");
        } catch (Exception e) {
            GameLogger.error("Failed to create server storage directories: " + e.getMessage());
            throw new RuntimeException("Server storage initialization failed", e);
        }
    }

    public synchronized WorldData loadWorld(String name) {
        // Check cache first
        WorldData cached = worldCache.get(name);
        if (cached != null) {
            return cached;
        }

        try {
            String worldPath = SERVER_WORLD_DIR + name + "/world.json";
            if (!fs.exists(worldPath)) {
                GameLogger.info("World file not found: " + name);
                return null;
            }

            String content = fs.readString(worldPath);
            WorldData world = json.fromJson(WorldData.class, content);

            if (world != null) {
                worldCache.put(name, world);
                GameLogger.info("Loaded world from server storage: " + name);
            }

            return world;
        } catch (Exception e) {
            GameLogger.error("Failed to load world: " + name + " - " + e.getMessage());
            return null;
        }
    }

    public synchronized void saveWorld(WorldData world) {
        if (world == null) return;

        try {
            String worldPath = SERVER_WORLD_DIR + world.getName() + "/";
            fs.createDirectory(worldPath);

            // Create backup first
            createWorldBackup(world);

            // Save using temporary file
            String tempPath = worldPath + "world.json.temp";
            String finalPath = worldPath + "world.json";

            String jsonData = json.prettyPrint(world);
            fs.writeString(tempPath, jsonData);

            // If temporary file was written successfully, move it to the actual file
            if (fs.exists(finalPath)) {
                fs.deleteFile(finalPath);
            }
            fs.moveFile(tempPath, finalPath);

            // Update cache
            worldCache.put(world.getName(), world);

            GameLogger.info("Saved world to server storage: " + world.getName());
        } catch (Exception e) {
            GameLogger.error("Failed to save world: " + world.getName() + " - " + e.getMessage());
            throw new RuntimeException("World save failed", e);
        }
    }


    public void savePlayerData(String username, PlayerData playerData) {
        if (username == null || playerData == null) {
            return;
        }

        try {
            String path = SERVER_PLAYER_DIR + username + ".json";
            String jsonData = json.prettyPrint(playerData);
            fs.writeString(path, jsonData);
            playerCache.put(username, playerData);
            GameLogger.info("Saved player data for: " + username);
        } catch (Exception e) {
            GameLogger.error("Failed to save player data for: " + username);
        }
    }

    public Map<String, WorldData> getAllWorlds() {
        String[] worldDirs = fs.list(SERVER_WORLD_DIR);
        Map<String, WorldData> worlds = new HashMap<>();

        if (worldDirs != null) {
            for (String dir : worldDirs) {
                WorldData world = loadWorld(dir);
                if (world != null) {
                    worlds.put(world.getName(), world);
                }
            }
        }

        return worlds;
    }

    public PlayerData loadPlayerData(String username) {
        if (playerCache.containsKey(username)) {
            return playerCache.get(username);
        }

        try {
            String path = SERVER_PLAYER_DIR + username + ".json";
            if (!fs.exists(path)) {
                return null;
            }

            String jsonData = fs.readString(path);
            PlayerData playerData = json.fromJson(PlayerData.class, jsonData);
            playerCache.put(username, playerData);
            return playerData;
        } catch (Exception e) {
            GameLogger.error("Failed to load player data for: " + username);
            return null;
        }
    }

    private void createWorldBackup(WorldData world) {
        try {
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String backupDir = SERVER_WORLD_DIR + world.getName() + "/backups/";
            if (!fs.exists(backupDir)) {
                fs.createDirectory(backupDir);
            }

            String backupPath = backupDir + "world_" + timestamp + ".json";

            String jsonData = json.prettyPrint(world);
            fs.writeString(backupPath, jsonData);

            GameLogger.info("Created backup of world: " + world.getName());
        } catch (Exception e) {
            GameLogger.error("Failed to create world backup: " + e.getMessage());
        }
    }



    public void deleteWorld(String name) {
        String worldPath = SERVER_WORLD_DIR + name;
        if (fs.exists(worldPath)) {
            fs.deleteDirectory(worldPath);
            worldCache.remove(name);
            GameLogger.info("Deleted world from server storage: " + name);
        }
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

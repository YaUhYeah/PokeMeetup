package io.github.pokemeetup.multiplayer.server.storage;

import com.badlogic.gdx.utils.Json;
import io.github.pokemeetup.system.PlayerData;
import io.github.pokemeetup.system.gameplay.overworld.multiworld.WorldData;
import io.github.pokemeetup.utils.GameLogger;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.ConcurrentHashMap;

public class FileStorage implements StorageSystem {
    private final Path baseDir;
    private final Path worldsDir;
    private final Path playersDir;
    private final Json json;

    // Cache to prevent constant file reads
    private final ConcurrentHashMap<String, PlayerData> playerCache;
    private final ConcurrentHashMap<String, WorldData> worldCache;

    public FileStorage(String baseDirectory) {
        this.baseDir = Paths.get(baseDirectory);
        this.worldsDir = baseDir.resolve("worlds");
        this.playersDir = baseDir.resolve("players");
        this.json = new Json();
        this.playerCache = new ConcurrentHashMap<>();
        this.worldCache = new ConcurrentHashMap<>();
    }

    @Override
    public void initialize() throws IOException {
        // Create directories if they don't exist
        Files.createDirectories(worldsDir);
        Files.createDirectories(playersDir);

        // Load existing data into cache
        loadExistingData();

        GameLogger.info("File storage initialized at: " + baseDir);
    }

    private void loadExistingData() throws IOException {
        // Load players
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(playersDir, "*.json")) {
            for (Path file : stream) {
                String username = file.getFileName().toString().replace(".json", "");
                PlayerData data = loadPlayerData(username);
                if (data != null) {
                    playerCache.put(username, data);
                }
            }
        }

        // Load worlds
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(worldsDir, "*.json")) {
            for (Path file : stream) {
                String worldName = file.getFileName().toString().replace(".json", "");
                WorldData data = loadWorldData(worldName);
                if (data != null) {
                    worldCache.put(worldName, data);
                }
            }
        }
    }

    @Override
    public void savePlayerData(String username, PlayerData data) throws IOException {
        Path file = playersDir.resolve(username + ".json");
        String jsonData = json.prettyPrint(data);
        Files.writeString(file, jsonData, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        playerCache.put(username, data);
        GameLogger.info("Saved player data for: " + username);
    }

    @Override
    public PlayerData loadPlayerData(String username) {
        // Check cache first
        PlayerData cached = playerCache.get(username);
        if (cached != null) {
            return cached;
        }

        try {
            Path file = playersDir.resolve(username + ".json");
            if (Files.exists(file)) {
                String jsonData = Files.readString(file);
                PlayerData data = json.fromJson(PlayerData.class, jsonData);
                playerCache.put(username, data);
                return data;
            }
        } catch (IOException e) {
            GameLogger.info("Error loading player data for " + username + ": " + e.getMessage());
        }
        return null;
    }

    @Override
    public void saveWorldData(String worldName, WorldData data) throws IOException {
        Path file = worldsDir.resolve(worldName + ".json");
        String jsonData = json.prettyPrint(data);
        Files.writeString(file, jsonData, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        worldCache.put(worldName, data);
        GameLogger.info("Saved world data for: " + worldName);
    }

    @Override
    public WorldData loadWorldData(String worldName) {
        // Check cache first
        WorldData cached = worldCache.get(worldName);
        if (cached != null) {
            return cached;
        }

        try {
            Path file = worldsDir.resolve(worldName + ".json");
            if (Files.exists(file)) {
                String jsonData = Files.readString(file);
                WorldData data = json.fromJson(WorldData.class, jsonData);
                worldCache.put(worldName, data);
                return data;
            }
        } catch (IOException e) {
            GameLogger.info("Error loading world data for " + worldName + ": " + e.getMessage());
        }
        return null;
    }

    @Override
    public void clearCache() {
        playerCache.clear();
        worldCache.clear();
    }

    @Override
    public void shutdown() {
        // Save any cached data
        playerCache.forEach((username, data) -> {
            try {
                savePlayerData(username, data);
            } catch (IOException e) {
                GameLogger.info("Error saving player data during shutdown: " + e.getMessage());
            }
        });

        worldCache.forEach((worldName, data) -> {
            try {
                saveWorldData(worldName, data);
            } catch (IOException e) {
                GameLogger.info("Error saving world data during shutdown: " + e.getMessage());
            }
        });

        clearCache();
    }
}

package io.github.pokemeetup.multiplayer.server.storage;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import io.github.pokemeetup.system.data.PlayerData;
import io.github.pokemeetup.system.data.WorldData;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.storage.JsonConfig;

import java.io.IOException;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FileStorage implements StorageSystem {
    private static final String MULTIPLAYER_ROOT = "multiplayer/";
    private final Path baseDir;
    private final Path worldsDir;
    private final Path playersDir;
    private final Json json;

    // Cache for multiplayer data
    private final ConcurrentHashMap<String, PlayerData> playerCache;
    private final ConcurrentHashMap<String, WorldData> worldCache;

    public FileStorage(String baseDirectory) {
        // Always use multiplayer root
        this.baseDir = Paths.get(baseDirectory, MULTIPLAYER_ROOT);
        this.worldsDir = baseDir.resolve("worlds");
        this.playersDir = baseDir.resolve("players");

        this.json = JsonConfig.getInstance();
        setupJsonSerializers(json);

        this.playerCache = new ConcurrentHashMap<>();
        this.worldCache = new ConcurrentHashMap<>();

        GameLogger.info("Initializing multiplayer storage at: " + baseDir);
    }

    private void setupJsonSerializers(Json json) {
        // Add UUID serializer
        json.setSerializer(UUID.class, new Json.Serializer<UUID>() {
            @Override
            public void write(Json json, UUID uuid, Class knownType) {
                json.writeValue(uuid != null ? uuid.toString() : null);
            }

            @Override
            public UUID read(Json json, JsonValue jsonData, Class type) {
                if (jsonData == null || jsonData.isNull()) return null;
                try {
                    return UUID.fromString(jsonData.asString());
                } catch (Exception e) {
                    GameLogger.error("Error parsing UUID: " + jsonData);
                    return null;
                }
            }
        });

        // Add any other necessary serializers for multiplayer data
    }
    private void createWorldBackup(String worldName) {
        try {
            Path worldFile = worldsDir.resolve(worldName + "/world.json");
            Path backupDir = worldsDir.resolve(worldName + "/backups");
            Files.createDirectories(backupDir);
            Path backupFile = backupDir.resolve("world_backup.json"); // fixed name
            Files.copy(worldFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
            GameLogger.info("Created backup of world: " + worldName + " at " + backupFile.toAbsolutePath());
        } catch (Exception e) {
            GameLogger.error("Failed to create backup of world: " + worldName + " - " + e.getMessage());
        }
    }

    @Override
    public void initialize() throws IOException {
        // Create directory structure
        Files.createDirectories(worldsDir);
        Files.createDirectories(playersDir);

        // Load existing multiplayer data
        loadExistingData();   worldCache.forEach((worldName, data) -> {
            createWorldBackup(worldName);
        });

        GameLogger.info("Multiplayer storage initialized");
    }

    private void loadExistingData() throws IOException {
        // Load player data
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(playersDir, "*.json")) {
            for (Path file : stream) {
                String username = file.getFileName().toString().replace(".json", "");
                PlayerData data = loadPlayerData(username);
                if (data != null) {
                    playerCache.put(username, data);
                    GameLogger.info("Loaded multiplayer player data: " + username);
                }
            }
        }

        // Load world data
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(worldsDir, "*.json")) {
            for (Path file : stream) {
                String worldName = file.getFileName().toString().replace(".json", "");
                WorldData data = loadWorldData(worldName);
                if (data != null) {
                    worldCache.put(worldName, data);
                    GameLogger.info("Loaded multiplayer world: " + worldName);
                }
            }
        }
    }

    @Override
    public void savePlayerData(String username, PlayerData data) throws IOException {
        Path file = playersDir.resolve(username + ".json");

        // Create backup if file exists
        if (Files.exists(file)) {
            Path backup = file.resolveSibling(username + ".bak");
            Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
        }

        String jsonData = json.prettyPrint(data);
        Files.writeString(file, jsonData, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        playerCache.put(username, data);

        GameLogger.info("Saved multiplayer player data: " + username);
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
                if (data != null) {
                    playerCache.put(username, data);
                }
                return data;
            }
        } catch (IOException e) {
            GameLogger.error("Error loading multiplayer player data: " + username + " - " + e.getMessage());
        }
        return null;
    }

    private boolean backupCreated = false;
    public void saveWorldData(String worldName, WorldData data) throws IOException {
        Path worldFile = worldsDir.resolve(worldName + "/world.json");
        Path backupDir = worldsDir.resolve(worldName + "/backups");

        try {
            Files.createDirectories(worldFile.getParent());
            Files.createDirectories(backupDir);
            if (Files.exists(worldFile)) {
                Path backup = backupDir.resolve("world_backup.json");
                Files.copy(worldFile, backup, StandardCopyOption.REPLACE_EXISTING);
                GameLogger.info("Created backup of world: " + worldName);
            }

            String jsonData = JsonConfig.getInstance().prettyPrint(data);
            Files.writeString(worldFile, jsonData, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            worldCache.put(worldName, data);

            GameLogger.info("Saved world: " + worldName +
                " Time: " + data.getWorldTimeInMinutes() +
                " Played: " + data.getPlayedTime() +
                " Day Length: " + data.getDayLength() +
                " to: " + worldFile.toAbsolutePath());

        } catch (Exception e) {
            GameLogger.error("Failed to save world: " + worldName + " - " + e.getMessage());
            throw e;
        }
    }


    @Override
    public WorldData loadWorldData(String worldName) {
        WorldData cached = worldCache.get(worldName);
        if (cached != null) {
            return cached;
        }

        try {
            Path worldFile = worldsDir.resolve(worldName + "/world.json");
            if (Files.exists(worldFile)) {
                String jsonData = Files.readString(worldFile);
                WorldData data = JsonConfig.getInstance().fromJson(WorldData.class, jsonData);
                if (data != null) {
                    worldCache.put(worldName, data);
                    GameLogger.info("Loaded world data: " + worldName +
                        " Time: " + data.getWorldTimeInMinutes() +
                        " Played: " + data.getPlayedTime());
                }
                return data;
            }
        } catch (IOException e) {
            GameLogger.error("Error loading world: " + worldName + " - " + e.getMessage());
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
        GameLogger.info("Shutting down multiplayer storage...");

        // Save all cached data
        playerCache.forEach((username, data) -> {
            try {
                savePlayerData(username, data);
            } catch (IOException e) {
                GameLogger.error("Error saving player data during shutdown: " + e.getMessage());
            }
        });

        worldCache.forEach((worldName, data) -> {
            try {
                saveWorldData(worldName, data);
            } catch (IOException e) {
                GameLogger.error("Error saving world data during shutdown: " + e.getMessage());
            }
        });

        clearCache();
        GameLogger.info("Multiplayer storage shutdown complete");
    }
}

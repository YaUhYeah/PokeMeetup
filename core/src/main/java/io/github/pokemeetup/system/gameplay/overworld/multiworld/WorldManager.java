package io.github.pokemeetup.system.gameplay.overworld.multiworld;

import com.badlogic.gdx.utils.Json;
import io.github.pokemeetup.CreatureCaptureGame;
import io.github.pokemeetup.multiplayer.server.ServerStorageSystem;
import io.github.pokemeetup.system.data.ItemData;
import io.github.pokemeetup.system.data.PlayerData;
import io.github.pokemeetup.system.data.PokemonData;
import io.github.pokemeetup.system.data.WorldData;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.storage.GameFileSystem;
import io.github.pokemeetup.utils.storage.JsonConfig;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WorldManager {
    private static final long AUTO_SAVE_INTERVAL = 100000; // 5 minutes
    private static final String WORLDS_BASE_DIR = "worlds/";
    private static final String SINGLE_PLAYER_DIR = WORLDS_BASE_DIR + "singleplayer/";
    private static final String MULTI_PLAYER_DIR = WORLDS_BASE_DIR + "multiplayer/";
    private static WorldManager instance;
    private final Map<String, WorldData> worlds;
    private final ServerStorageSystem storage;
    private final Object worldLock = new Object();
    private final String baseDirectory;
    private final GameFileSystem fs;
    private final boolean isMultiplayerMode;
    private boolean isInitialized = false;
    private WorldData currentWorld;
    private long lastAutoSave = 0;

    private WorldManager(ServerStorageSystem storage, boolean isMultiplayerMode) {
        this.storage = storage;
        this.isMultiplayerMode = isMultiplayerMode;
        this.worlds = new ConcurrentHashMap<>();
        this.baseDirectory = isMultiplayerMode ? MULTI_PLAYER_DIR : SINGLE_PLAYER_DIR;
        this.fs = GameFileSystem.getInstance();
        createDirectoryStructure();
    }

    public static synchronized WorldManager getInstance(ServerStorageSystem storage, boolean isMultiplayerMode) {
        if (instance == null) {
            instance = new WorldManager(storage, isMultiplayerMode);
        }
        return instance;
    }

    public synchronized void init() {
        if (isInitialized) {
            GameLogger.info("WorldManager already initialized");
            return;
        }

        synchronized (worldLock) {
            try {
                worlds.clear();

                if (isMultiplayerMode) {
                    initializeMultiplayerMode();
                } else {
                    initializeSingleplayerMode();
                }

                isInitialized = true;
                GameLogger.info("WorldManager initialized in " +
                    (isMultiplayerMode ? "multiplayer" : "singleplayer") +
                    " mode with " + worlds.size() + " worlds");

            } catch (Exception e) {
                GameLogger.error("Failed to initialize WorldManager: " + e.getMessage());
                throw new RuntimeException("WorldManager initialization failed", e);
            }
        }
    }

    private void initializeMultiplayerMode() {
        try {
            if (storage != null) {
                Map<String, WorldData> serverWorlds = storage.getAllWorlds();
                if (serverWorlds != null && !serverWorlds.isEmpty()) {
                    worlds.putAll(serverWorlds);
                    GameLogger.info("Loaded " + serverWorlds.size() + " worlds from server");
                }

                // Check if multiplayer world exists
                WorldData multiplayerWorld = worlds.get(CreatureCaptureGame.MULTIPLAYER_WORLD_NAME);
                if (multiplayerWorld == null) {
                    createDefaultMultiplayerWorld();
                } else {
                    currentWorld = multiplayerWorld; // Add this line to set currentWorld
                }
            } else {
                // Clients should not create or save world data
                GameLogger.info("Client-side multiplayer initialization - waiting for server data");
            }
        } catch (Exception e) {
            GameLogger.error("Error initializing multiplayer mode: " + e.getMessage());
            throw new RuntimeException("Multiplayer initialization failed", e);
        }
    }

    private void createDefaultMultiplayerWorld() {
        try {
            WorldData defaultWorld = createWorld(
                CreatureCaptureGame.MULTIPLAYER_WORLD_NAME,
                CreatureCaptureGame.MULTIPLAYER_WORLD_SEED,
                0.15f,
                0.05f
            );

            storage.saveWorld(defaultWorld);
            worlds.put(defaultWorld.getName(), defaultWorld);
            currentWorld = defaultWorld;

            GameLogger.info("Created and saved default multiplayer world");

        } catch (Exception e) {
            GameLogger.error("Failed to create default multiplayer world: " + e.getMessage());
            throw new RuntimeException("Failed to create multiplayer world", e);
        }
    }

    private void initializeSingleplayerMode() {
        try {
            cleanupCorruptedWorlds();
            loadSingleplayerWorlds();
        } catch (Exception e) {
            GameLogger.error("Error initializing singleplayer mode: " + e.getMessage());
            throw e;
        }
    }

    private void createDirectoryStructure() {
        try {
            fs.createDirectory(WORLDS_BASE_DIR);
            fs.createDirectory(baseDirectory);
            fs.createDirectory(baseDirectory + "backups/");
            GameLogger.info("Directory structure initialized: " + baseDirectory);
        } catch (Exception e) {
            GameLogger.error("Failed to create directory structure: " + e.getMessage());
            throw new RuntimeException("Failed to initialize directory structure", e);
        }
    }

    public WorldData createWorld(String name, long seed, float treeSpawnRate, float pokemonSpawnRate) {
        synchronized (worldLock) {
            try {
                if (name == null || name.trim().isEmpty()) {
                    throw new IllegalArgumentException("World name cannot be null or empty");
                }

                if (worlds.containsKey(name)) {
                    GameLogger.info("Cleaning up existing world: " + name);
                    deleteWorld(name);
                }

                GameLogger.info("Creating new world: " + name + " with seed: " + seed);

                WorldData.WorldConfig config = new WorldData.WorldConfig();
                config.setSeed(seed);
                config.setTreeSpawnRate(treeSpawnRate);
                config.setPokemonSpawnRate(pokemonSpawnRate);

                WorldData world = new WorldData(name);
                world.setName(name); // Set name explicitly
                world.setLastPlayed(System.currentTimeMillis());
                world.setConfig(config);

                world.setPlayersMap(new HashMap<>());

                world.setPokemonData(new PokemonData());

                if (isMultiplayerMode) {
                    storage.saveWorld(world);
                    GameLogger.info("Saved world to multiplayer storage: " + name);
                } else {
                    saveSingleplayerWorld(world);
                    GameLogger.info("Saved world to singleplayer storage: " + name);
                }

                worlds.put(name, world);
                GameLogger.info("Added world to cache: " + name);

                return world;

            } catch (Exception e) {
                GameLogger.error("Failed to create world: " + name + " - " + e.getMessage());
                throw new RuntimeException("World creation failed: " + e.getMessage(), e);
            }
        }
    }


    public void saveWorld(WorldData world) {
        synchronized(worldLock) {
            if (world == null) return;

            try {
                // Create backup before saving
                createBackup(world);

                // Validate and prepare for saving
                if (!validateWorld(world)) {
                    throw new RuntimeException("World validation failed: " + world.getName());
                }

                String worldPath = baseDirectory + world.getName();
                fs.createDirectory(worldPath);
                String worldFilePath = worldPath + "/world.json";
                String tempFilePath = worldPath + "/world.json.temp";

                // Create deep copy for saving
                WorldData worldCopy = deepCopyWorldData(world);

                Json json = JsonConfig.getInstance();
                String jsonData = json.prettyPrint(worldCopy);

                // Write to temp file first
                fs.writeString(tempFilePath, jsonData);

                // Verify the save
                try {
                    WorldData verification = json.fromJson(WorldData.class, fs.readString(tempFilePath));
                    if (!validateWorld(verification)) {
                        throw new RuntimeException("Save verification failed");
                    }
                } catch (Exception e) {
                    fs.deleteFile(tempFilePath);
                    throw new RuntimeException("Save verification failed: " + e.getMessage());
                }

                // Replace old file with new one
                if (fs.exists(worldFilePath)) {
                    fs.deleteFile(worldFilePath);
                }
                fs.moveFile(tempFilePath, worldFilePath);

                // Update cache and clear dirty flag
                worlds.put(world.getName(), worldCopy);
                world.clearDirtyFlag();

                GameLogger.info("Successfully saved world: " + world.getName());

            } catch (Exception e) {
                GameLogger.error("Failed to save world: " + world.getName() + " - " + e.getMessage());
                throw new RuntimeException("Failed to save world", e);
            }
        }
    }
    private void saveSingleplayerWorld(WorldData world) {
        try {
            String worldPath = baseDirectory + world.getName();
            fs.createDirectory(worldPath);
            String worldFilePath = worldPath + "/world.json";
            String tempFilePath = worldPath + "/world.json.temp";

            WorldData worldCopy = deepCopyWorldData(world);



            Json json = JsonConfig.getInstance();
            String jsonData = json.prettyPrint(worldCopy);
            fs.writeString(tempFilePath, jsonData);

            WorldData verification = json.fromJson(WorldData.class, fs.readString(tempFilePath));


            if (fs.exists(worldFilePath)) {
                createBackup(world);
            }

            if (fs.exists(worldFilePath)) {
                fs.deleteFile(worldFilePath);
            }
            fs.copyFile(tempFilePath, worldFilePath);
            fs.deleteFile(tempFilePath);

            // Update the cache with the saved data
            applyWorldData(verification);

            GameLogger.info("Successfully saved world: " + world.getName());

        } catch (Exception e) {
            GameLogger.error("Failed to save world: " + world.getName() + " - " + e.getMessage());
            throw new RuntimeException("Failed to save world", e);
        }
    }

    private WorldData deepCopyWorldData(WorldData original) {
        WorldData copy = new WorldData(original.getName());

        // Copy basic properties
        copy.setLastPlayed(original.getLastPlayed());
        copy.setWorldTimeInMinutes(original.getWorldTimeInMinutes());
        copy.setPlayedTime(original.getPlayedTime());
        copy.setDayLength(original.getDayLength());

        // Deep copy config
        if (original.getConfig() != null) {
            WorldData.WorldConfig configCopy = new WorldData.WorldConfig(original.getConfig().getSeed());
            configCopy.setTreeSpawnRate(original.getConfig().getTreeSpawnRate());
            configCopy.setPokemonSpawnRate(original.getConfig().getPokemonSpawnRate());
            copy.setConfig(configCopy);
        }

        // Corrected player data copying
        Map<String, PlayerData> originalPlayers = original.getPlayersMap();
        if (originalPlayers != null) {
            HashMap<String, PlayerData> playersCopy = new HashMap<>();
            for (Map.Entry<String, PlayerData> entry : originalPlayers.entrySet()) {
                playersCopy.put(entry.getKey(), entry.getValue().copy()); // Ensure deep copy
            }
            copy.setPlayersMap(playersCopy);
        }

        return copy;
    }


    public WorldData getWorld(String name) {
        synchronized (worldLock) {
            try {
                WorldData world = worlds.get(name);
                if (world == null && !isMultiplayerMode) {
                    // Try to load from file
                    world = JsonConfig.loadWorldData(name);
                    if (world != null) {
                        // Apply data and update cache
                        applyWorldData(world);
                    }
                }
                return world;
            } catch (Exception e) {
                GameLogger.error("Error loading world: " + name + " - " + e.getMessage());
                throw new RuntimeException("Failed to load world", e);
            }
        }
    }


    public void deleteWorld(String name) {
        synchronized (worldLock) {
            WorldData removed = worlds.remove(name);
            if (removed != null) {
                if (isMultiplayerMode) {
                    storage.deleteWorld(name);
                } else {
                    deleteSingleplayerWorld(name);
                }
                GameLogger.info("Deleted world: " + name);
            } else {
                GameLogger.info("Attempted to delete non-existent world: " + name);
            }
        }
    }

    private void applyWorldData(WorldData world) {
        if (world == null) return;

        try {
            // Validate and repair if needed
            world.validateAndRepair();

            // Update cached world directly
            worlds.put(world.getName(), world);

        } catch (Exception e) {
            GameLogger.error("Failed to apply world data: " + e.getMessage());
            throw new RuntimeException("World data application failed", e);
        }
    }

    public Map<String, WorldData> getWorlds() {
        return Collections.unmodifiableMap(worlds);
    }


    public WorldData getCurrentWorld() {
        return currentWorld;
    }

    private void cleanupCorruptedWorlds() {
        if (!fs.exists("worlds/singleplayer/")) return;

        String[] directories = fs.list("worlds/singleplayer/");
        for (String dirName : directories) {
            String dirPath = "worlds/singleplayer/" + dirName;
            if (!fs.isDirectory(dirPath)) continue;

            String worldFilePath = dirPath + "/world.json";
            if (fs.exists(worldFilePath)) {
                try {
                    Json json = JsonConfig.getInstance();
                    String content = fs.readString(worldFilePath);
                    WorldData world = json.fromJson(WorldData.class, content);

                    if (world == null) {
                        String backupPath = dirPath + "/world.json.corrupted";
                        fs.writeString(backupPath, content);
                        fs.deleteFile(worldFilePath);
                        world = new WorldData(dirName);
                        world.setLastPlayed(System.currentTimeMillis());
                        fs.writeString(worldFilePath, json.toJson(world));
                        GameLogger.info("Repaired corrupted world: " + dirName);
                    }
                } catch (Exception e) {
                    GameLogger.error("Failed to parse world file: " + dirName + " - " + e.getMessage());
                }
            }
        }
    }private boolean validateWorld(WorldData world) {
        if (world == null) return false;
        if (world.getName() == null || world.getName().isEmpty()) return false;

        // Validate config
        if (world.getConfig() == null) {
            world.setConfig(new WorldData.WorldConfig(System.currentTimeMillis()));
            world.setDirty(true);
        }

        // Validate players map
        if (world.getPlayersMap() == null) {
            world.setPlayersMap(new HashMap<>());
            world.setDirty(true);
        }

        // Validate each player
        for (PlayerData player : world.getPlayersMap().values()) {
            if (player != null) {
                if (player.validateAndRepairState()) {
                    world.setDirty(true);
                }
            }
        }

        return true;
    }

    private void loadSingleplayerWorlds() {
        try {
            if (!fs.exists("worlds/singleplayer/")) {
                fs.createDirectory("worlds/singleplayer/");
                GameLogger.info("Created worlds directory.");
                return;
            }

            String[] worldFolders = fs.list("worlds/singleplayer/");
            if (worldFolders == null || worldFolders.length == 0) {
                GameLogger.info("No singleplayer worlds found.");
                return;
            }

            for (String dirName : worldFolders) {
                String dirPath = "worlds/singleplayer/" + dirName;
                if (!fs.isDirectory(dirPath)) continue;

                String worldFilePath = dirPath + "/world.json";
                if (!fs.exists(worldFilePath)) {
                    GameLogger.info("Missing 'world.json' in: " + dirPath);
                    continue;
                }

                try {
                    // Load and validate the world
                    WorldData world = loadAndValidateWorld(dirName);
                    if (world != null) {
                        // Important: Add valid worlds to the worlds map
                        worlds.put(dirName, world);
                        GameLogger.info("Successfully loaded world: " + dirName);
                    }
                } catch (Exception e) {
                    GameLogger.error("Error loading world: " + dirName + " - " + e.getMessage());
                }
            }

            GameLogger.info("Loaded " + worlds.size() + " singleplayer worlds.");
        } catch (Exception e) {
            GameLogger.error("Error loading singleplayer worlds: " + e.getMessage());
        }
    }
    public WorldData loadAndValidateWorld(String name) {
        try {
            String worldPath = baseDirectory + name + "/world.json";
            if (!fs.exists(worldPath)) {
                GameLogger.error("World file not found: " + worldPath);
                return null;
            }

            String content = fs.readString(worldPath);
            WorldData world = JsonConfig.getInstance().fromJson(WorldData.class, content);
            WorldData existingWorld = worlds.get(name);

            if (world == null) {
                GameLogger.error("Failed to parse world data: " + worldPath);
                return null;
            }

            // Preserve existing data if available
            if (existingWorld != null) {
                // Keep player data
                Map<String, PlayerData> existingPlayers = existingWorld.getPlayersMap();
                if (existingPlayers != null && !existingPlayers.isEmpty()) {
                    for (Map.Entry<String, PlayerData> entry : existingPlayers.entrySet()) {
                        PlayerData existingData = entry.getValue();
                        PlayerData worldData = world.getPlayersMap().get(entry.getKey());

                        if (worldData == null || !worldData.verifyIntegrity()) {
                            world.savePlayerData(entry.getKey(), existingData);
                            GameLogger.info("Restored existing player data for: " + entry.getKey());
                        }
                    }
                }
            }

            // Perform repairs if needed
            boolean needsSave = false;

            if (world.getConfig() == null) {
                GameLogger.info("Repairing missing config for world: " + name);
                WorldData.WorldConfig config = new WorldData.WorldConfig(System.currentTimeMillis());
                config.setTreeSpawnRate(0.15f);
                config.setPokemonSpawnRate(0.05f);
                world.setConfig(config);
                needsSave = true;
            }

            // Validate each player
            HashMap<String, PlayerData> repairedPlayers = new HashMap<>();
            for (Map.Entry<String, PlayerData> entry : world.getPlayersMap().entrySet()) {
                PlayerData playerData = entry.getValue();
                if (playerData != null) {
                    if (playerData.validateAndRepairState()) {
                        needsSave = true;
                    }
                    repairedPlayers.put(entry.getKey(), playerData);

                    GameLogger.info(String.format("Validated player: %s - Pokemon: %d, Items: %d",
                        entry.getKey(),
                        playerData.getPartyPokemon().size(),
                        playerData.getInventoryItems().stream().filter(Objects::nonNull).count()
                    ));
                }
            }

            // Update with repaired data
            world.setPlayersMap(repairedPlayers);

            // Save if repairs were made
            if (needsSave) {
                saveWorld(world);
            }

            // Update cache
            worlds.put(name, world);

            GameLogger.info("Successfully loaded and validated world: " + name);
            return world;

        } catch (Exception e) {
            GameLogger.error("Error loading world: " + name + " - " + e.getMessage());
            return null;
        }
    } public void updateWorld(String name, WorldData worldData) {
        if (name == null || worldData == null) {
            throw new IllegalArgumentException("World name and data cannot be null");
        }
        worlds.put(name, worldData);
    }


    private void deleteSingleplayerWorld(String name) {
        try {
            String worldPath = "worlds/singleplayer/" + name;
            if (fs.exists(worldPath)) {
                fs.deleteDirectory(worldPath);
                GameLogger.info("Deleted singleplayer world directory: " + name);
            } else {
                GameLogger.info("Singleplayer world directory does not exist: " + name);
            }
        } catch (Exception e) {
            GameLogger.error("Failed to delete singleplayer world: " + name + " - " + e.getMessage());
            throw new RuntimeException("Singleplayer world deletion failed", e);
        }
    }

    private void createBackup(WorldData world) {
        try {
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String backupDirPath = baseDirectory + "backups/" + world.getName();
            if (!fs.exists(backupDirPath)) {
                fs.createDirectory(backupDirPath);
            }

            String worldFilePath = baseDirectory + world.getName() + "/world.json";
            if (fs.exists(worldFilePath)) {
                String backupPath = backupDirPath + "/world_" + timestamp + ".json";
                fs.copyFile(worldFilePath, backupPath);
                GameLogger.info("Created backup for world: " + world.getName());
            }
        } catch (Exception e) {
            GameLogger.error("Failed to create backup for world: " + world.getName());
        }
    }

    public void checkAutoSave() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastAutoSave >= AUTO_SAVE_INTERVAL) {
            synchronized (worldLock) {
                for (WorldData world : worlds.values()) {
                    if (world.isDirty()) {
                        saveWorld(world);
                    }
                }
            }
            lastAutoSave = currentTime;
            GameLogger.info("Auto-saved dirty worlds.");
        }
    }
}

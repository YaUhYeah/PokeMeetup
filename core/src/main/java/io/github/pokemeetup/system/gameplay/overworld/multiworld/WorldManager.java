package io.github.pokemeetup.system.gameplay.overworld.multiworld;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import io.github.pokemeetup.CreatureCaptureGame;
import io.github.pokemeetup.managers.BiomeManager;
import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.multiplayer.client.GameClientSingleton;
import io.github.pokemeetup.multiplayer.server.ServerStorageSystem;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.data.PlayerData;
import io.github.pokemeetup.system.data.PokemonData;
import io.github.pokemeetup.system.data.WorldData;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.TextureManager;
import io.github.pokemeetup.utils.storage.JsonConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WorldManager {
    private static final long AUTO_SAVE_INTERVAL = 100000; // 5 minutes
    private static final String SINGLE_PLAYER_DIR = "worlds/singleplayer/";
    private static final String MULTI_PLAYER_DIR = "worlds/multiplayer/";
    private static WorldManager instance;
    private final Map<String, WorldData> worlds;
    private final ServerStorageSystem storage;
    private final Object worldLock = new Object();
    private final String baseDirectory;
    /**
     * Periodically checks and auto-saves dirty worlds.
     * Call this method from the game's main loop.
     */
    private boolean isInitialized = false;  // Add this flag
    private boolean isMultiplayerMode;
    private WorldData currentWorld;
    private long lastAutoSave = 0;

    private WorldManager(ServerStorageSystem storage, boolean isMultiplayerMode) {
        this.storage = storage;
        this.isMultiplayerMode = isMultiplayerMode;
        this.worlds = new ConcurrentHashMap<>();
        this.baseDirectory = isMultiplayerMode ? MULTI_PLAYER_DIR : SINGLE_PLAYER_DIR;

        // Create base directory if it doesn't exist using Java NIO
        try {
            Path baseDirPath = Paths.get(baseDirectory);
            if (!Files.exists(baseDirPath)) {
                Files.createDirectories(baseDirPath);
            }
        } catch (IOException e) {
            GameLogger.error("Failed to create base directory: " + e.getMessage());
            throw new RuntimeException("Failed to initialize directory structure", e);
        }
    }

    /**
     * Initializes the singleton instance of WorldManager.
     *
     * @param storage           The ServerStorageSystem instance for multiplayer.
     * @param isMultiplayerMode Flag indicating if multiplayer mode is active.
     * @return The singleton instance of WorldManager.
     */
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
        // Load multiplayer worlds from server storage
        try {
            Map<String, WorldData> serverWorlds = storage.getAllWorlds();
            if (serverWorlds != null && !serverWorlds.isEmpty()) {
                worlds.putAll(serverWorlds);
                GameLogger.info("Loaded " + serverWorlds.size() + " multiplayer worlds");
            } else {
                // Create default multiplayer world if none exists
                createDefaultMultiplayerWorld();
            }
        } catch (Exception e) {
            GameLogger.error("Error initializing multiplayer mode: " + e.getMessage());
            throw e;
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
            GameLogger.info("Created default multiplayer world");

            // Save to server storage
            storage.saveWorld(defaultWorld);
            worlds.put(defaultWorld.getName(), defaultWorld);

        } catch (Exception e) {
            GameLogger.error("Failed to create default multiplayer world: " + e.getMessage());
            throw new RuntimeException(e);
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

    private void saveSingleplayerWorld(WorldData world) {
        try {
            if (world == null) {
                throw new IllegalArgumentException("Cannot save null world");
            }

            String worldPath = baseDirectory + world.getName();
            FileHandle worldDir = Gdx.files.local(worldPath);
            if (!worldDir.exists()) {
                worldDir.mkdirs();
            }

            // Create backup of existing file
            FileHandle worldFile = worldDir.child("world.json");
            if (worldFile.exists()) {
                FileHandle backup = worldDir.child("world.json.backup");
                worldFile.copyTo(backup);
            }

            // Save new data
            Json json = JsonConfig.getInstance();
            String jsonData = json.prettyPrint(world);
            worldFile.writeString(jsonData, false);

            GameLogger.info("Saved singleplayer world: " + world.getName());

        } catch (Exception e) {
            GameLogger.error("Failed to save singleplayer world: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }



    public WorldData createWorld(String name, long seed, float treeSpawnRate, float pokemonSpawnRate) {
        synchronized (worldLock) {
            try {
                // Basic validation
                if (name == null || name.trim().isEmpty()) {
                    throw new IllegalArgumentException("World name cannot be null or empty");
                }

                // Clean up any existing world with the same name
                if (worlds.containsKey(name)) {
                    GameLogger.info("Cleaning up existing world: " + name);
                    deleteWorld(name);
                }

                GameLogger.info("Creating new world: " + name + " with seed: " + seed);

                // Create world config
                WorldData.WorldConfig config = new WorldData.WorldConfig();
                config.setSeed(seed);
                config.setTreeSpawnRate(treeSpawnRate);
                config.setPokemonSpawnRate(pokemonSpawnRate);

                // Create new world
                WorldData world = new WorldData(name); // Use constructor that takes name
                world.setName(name); // Set name explicitly
                world.setLastPlayed(System.currentTimeMillis());
                world.setConfig(config);

                // Initialize empty player data map
                world.setPlayersMap(new HashMap<>());

                // Initialize pokemon data
                world.setPokemonData(new PokemonData());

                // Save world first
                if (isMultiplayerMode) {
                    storage.saveWorld(world);
                    GameLogger.info("Saved world to multiplayer storage: " + name);
                } else {
                    saveSingleplayerWorld(world);
                    GameLogger.info("Saved world to singleplayer storage: " + name);
                }

                // Only add to cache after successful save
                worlds.put(name, world);
                GameLogger.info("Added world to cache: " + name);

                return world;

            } catch (Exception e) {
                GameLogger.error("Failed to create world: " + name + " - " + e.getMessage());
                e.printStackTrace();
                throw new RuntimeException("World creation failed: " + e.getMessage(), e);
            }
        }
    }


    public void saveWorld(WorldData world) {
        if (world == null || !world.isDirty()) return;

        synchronized (worldLock) {
            try {
                if (isMultiplayerMode) {
                    storage.saveWorld(world);
                } else {
                    // Pass Json instance explicitly
                    saveSingleplayerWorld(world);
                }
                world.clearDirtyFlag();
                worlds.put(world.getName(), world);

                GameLogger.info("Saved world: " + world.getName());
            } catch (Exception e) {
                GameLogger.error("Failed to save world: " + world.getName() + " - " + e.getMessage());
                throw new RuntimeException("World save failed", e);
            }
        }
    }

    public WorldData getWorld(String name) {
        synchronized (worldLock) {
            WorldData world = worlds.get(name);
            if (world == null && isMultiplayerMode) {
                world = storage.loadWorld(name);
                if (world != null) {
                    worlds.put(name, world);
                }
            }
            return world;
        }
    }

    /**
     * Deletes a world by name.
     *
     * @param name The name of the world to delete.
     */
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

    /**
     * Deletes a singleplayer world from disk.
     *
     * @param name The name of the world to delete.
     */
    private void deleteSingleplayerWorld(String name) {
        try {
            FileHandle worldDir = Gdx.files.local("worlds/" + name);
            if (worldDir.exists()) {
                worldDir.deleteDirectory();
                GameLogger.info("Deleted singleplayer world directory: " + name);
            } else {
                GameLogger.info("Singleplayer world directory does not exist: " + name);
            }
        } catch (Exception e) {
            GameLogger.error("Failed to delete singleplayer world: " + name + " - " + e.getMessage());
            throw new RuntimeException("Singleplayer world deletion failed", e);
        }
    }

    public Map<String, WorldData> getWorlds() {
        return Collections.unmodifiableMap(worlds);
    }


    public WorldData getCurrentWorld() {
        return currentWorld;
    }

    private void loadMultiplayerWorlds() {
        Map<String, WorldData> serverWorlds = storage.getAllWorlds();
        if (serverWorlds != null && !serverWorlds.isEmpty()) {
            worlds.putAll(serverWorlds);
            GameLogger.info("Loaded " + serverWorlds.size() + " worlds from server");
        } else {
            GameLogger.info("No worlds found on the server.");
        }
    }

    private void validateAndRepairWorldData(WorldData world) {
        if (world.getPlayers() != null) {
            world.getPlayers().forEach((playerName, playerData) -> {
                if (playerData.getInventoryItems() != null) {
                    // Remove any null or invalid items
                    playerData.getInventoryItems().removeIf(item -> {
                        if (item == null) return true;
                        try {
                            // Ensure UUID is valid
                            if (item.getUuid() == null) {
                                item.setUuid(UUID.randomUUID());
                            }
                            return false;
                        } catch (Exception e) {
                            return true;
                        }
                    });
                } else {
                    playerData.setInventoryItems(new ArrayList<>());
                }
            });
        }
    }


    public void cleanupCorruptedWorlds() {
        FileHandle worldsDir = Gdx.files.local("worlds/");
        if (!worldsDir.exists()) return;

        for (FileHandle dir : worldsDir.list()) {
            if (!dir.isDirectory()) continue;

            FileHandle worldFile = dir.child("world.json");
            if (worldFile.exists()) {
                try {
                    // Attempt to parse the file
                    Json json = new Json();
                    String content = worldFile.readString();
                    WorldData world = json.fromJson(WorldData.class, content);

                    if (world == null) {
                        // Create backup and delete corrupted file
                        FileHandle backup = dir.child("world.json.corrupted");
                        backup.writeString(content, false);
                        worldFile.delete();

                        // Create new world file
                        world = new WorldData(dir.name());
                        world.setLastPlayed(System.currentTimeMillis());
                        worldFile.writeString(json.toJson(world), false);
                        GameLogger.info("Repaired corrupted world: " + dir.name());
                    }
                } catch (Exception e) {
                    GameLogger.error("Failed to parse world file: " + dir.name() + " - " + e.getMessage());
                }
            }
        }
    }

    private void loadSingleplayerWorlds() {
        try {
            FileHandle worldsDir = Gdx.files.local("worlds/");
            if (!worldsDir.exists()) {
                worldsDir.mkdirs();
                GameLogger.info("Created worlds directory.");
                return;
            }

            FileHandle[] worldFolders = worldsDir.list();
            if (worldFolders.length == 0) {
                GameLogger.info("No singleplayer worlds found.");
                return;
            }

            Json json = new Json();
            // Add custom serializer for UUID
            json.setSerializer(UUID.class, new Json.Serializer<UUID>() {
                @Override
                public void write(Json json, UUID uuid, Class knownType) {
                    json.writeValue(uuid != null ? uuid.toString() : null);
                }

                @Override
                public UUID read(Json json, JsonValue jsonData, Class type) {
                    try {
                        if (jsonData == null || jsonData.isNull()) return UUID.randomUUID();
                        if (jsonData.isString()) {
                            return UUID.fromString(jsonData.asString());
                        }
                        // If it's an object with a string value
                        if (jsonData.isObject() && jsonData.has("uuid")) {
                            return UUID.fromString(jsonData.getString("uuid"));
                        }
                        return UUID.randomUUID();
                    } catch (Exception e) {
                        GameLogger.error("Error parsing UUID: " + jsonData + ", generating new one");
                        return UUID.randomUUID();
                    }
                }
            });

            for (FileHandle dir : worldFolders) {
                if (!dir.isDirectory()) continue;

                FileHandle worldFile = dir.child("world.json");
                if (!worldFile.exists()) {
                    GameLogger.info("Missing 'world.json' in: " + dir.path());
                    continue;
                }

                try {
                    String content = worldFile.readString();
                    // Create backup before attempting to parse
                    FileHandle backup = dir.child("world.json.backup");
                    backup.writeString(content, false);

                    // Try to parse the world data
                    WorldData world = null;
                    try {
                        world = json.fromJson(WorldData.class, content);
                    } catch (Exception e) {
                        GameLogger.error("Failed to parse world data, creating new world: " + e.getMessage());
                        // Create new world with same name
                        world = new WorldData(dir.name());
                        world.setLastPlayed(System.currentTimeMillis());
                        // Save the new world data
                        worldFile.writeString(json.toJson(world), false);
                    }

                    if (world != null) {
                        // Validate and repair player data
                        if (world.getPlayers() != null) {
                            for (PlayerData playerData : world.getPlayers().values()) {
                                if (playerData.getInventoryItems() == null) {
                                    playerData.setInventoryItems(new ArrayList<>());
                                } else {
                                    // Remove any null or invalid items
                                    playerData.getInventoryItems().removeIf(item ->
                                        item == null || item.getUuid() == null);
                                }
                            }
                        }

                        worlds.put(world.getName(), world);
                        GameLogger.info("Loaded world: " + world.getName());
                    }
                } catch (Exception e) {
                    GameLogger.error("Error loading world from: " + worldFile.path() + " - " + e.getMessage());
                }
            }

            GameLogger.info("Loaded " + worlds.size() + " singleplayer worlds.");
        } catch (Exception e) {
            GameLogger.error("Error loading singleplayer worlds: " + e.getMessage());
            e.printStackTrace();
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

package io.github.pokemeetup.multiplayer.server;

import com.badlogic.gdx.utils.Json;
import io.github.pokemeetup.system.PlayerData;
import io.github.pokemeetup.system.gameplay.overworld.multiworld.WorldData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServerStorageSystem {
    private static final Logger logger = LoggerFactory.getLogger(ServerStorageSystem.class);
    private static final String SERVER_DATA_DIR = "server/data/";
    private static final String WORLDS_DIR = SERVER_DATA_DIR + "worlds/";
    private static final String PLAYERS_DIR = SERVER_DATA_DIR + "players/";

    private final ExecutorService saveExecutor = Executors.newSingleThreadExecutor();
    private final Json json;
    private final Map<String, WorldData> worldCache;
    private final Map<String, PlayerData> playerCache;

    public ServerStorageSystem() {
        this.json = new Json();
        this.worldCache = new ConcurrentHashMap<>();
        this.playerCache = new ConcurrentHashMap<>();
        initializeDirectories();
    }

    private void initializeDirectories() {
        try {
            Files.createDirectories(Paths.get(WORLDS_DIR));
            Files.createDirectories(Paths.get(PLAYERS_DIR));
            logger.info("Server storage directories initialized");
        } catch (IOException e) {
            logger.error("Failed to create storage directories", e);
            throw new RuntimeException("Failed to initialize storage system", e);
        }
    }

    public void saveWorld(WorldData world) {
        if (world == null || world.getName() == null) {
            return;
        }

        saveExecutor.submit(() -> {
            try {
                String filename = WORLDS_DIR + world.getName() + ".json";
                String jsonData = json.prettyPrint(world);
                Files.write(Paths.get(filename), jsonData.getBytes(),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

                worldCache.put(world.getName(), world);
                logger.info("Saved world: {}", world.getName());
            } catch (Exception e) {
//                logger.error(STR."Failed to save world: \{world.getName()}", e);
            }
        });
    }

    public void savePlayerData(String username, PlayerData playerData) {
        if (username == null || playerData == null) {
            return;
        }

        saveExecutor.submit(() -> {
            try {
                String filename = PLAYERS_DIR + username + ".json";
                String jsonData = json.prettyPrint(playerData);
                Files.write(Paths.get(filename), jsonData.getBytes(),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

                playerCache.put(username, playerData);
                logger.info("Saved player data for: {}", username);
            } catch (Exception e) {
                logger.error("Failed to save player data for: " + username, e);
            }
        });
    }

    public WorldData loadWorld(String worldName) {
        // Check cache first
        if (worldCache.containsKey(worldName)) {
            return worldCache.get(worldName);
        }

        try {
            Path worldPath = Paths.get(WORLDS_DIR + worldName + ".json");
            if (!Files.exists(worldPath)) {
                return null;
            }

            String jsonData = new String(Files.readAllBytes(worldPath));
            WorldData world = json.fromJson(WorldData.class, jsonData);
            worldCache.put(worldName, world);
            return world;
        } catch (Exception e) {
            logger.error("Failed to load world: " + worldName, e);
            return null;
        }
    }

    public PlayerData loadPlayerData(String username) {
        // Check cache first
        if (playerCache.containsKey(username)) {
            return playerCache.get(username);
        }

        try {
            Path playerPath = Paths.get(PLAYERS_DIR + username + ".json");
            if (!Files.exists(playerPath)) {
                return null;
            }

            String jsonData = new String(Files.readAllBytes(playerPath));
            PlayerData playerData = json.fromJson(PlayerData.class, jsonData);
            playerCache.put(username, playerData);
            return playerData;
        } catch (Exception e) {
            logger.error("Failed to load player data for: " + username, e);
            return null;
        }
    }

    public void deleteWorld(String worldName) {
        try {
            Path worldPath = Paths.get(WORLDS_DIR + worldName + ".json");
            Files.deleteIfExists(worldPath);
            worldCache.remove(worldName);
            logger.info("Deleted world: {}", worldName);
        } catch (Exception e) {
            logger.error("Failed to delete world: " + worldName, e);
        }
    }

    public Map<String, WorldData> getAllWorlds() {
        Map<String, WorldData> worlds = new HashMap<>();
        try {
            Files.walk(Paths.get(WORLDS_DIR))
                .filter(path -> path.toString().endsWith(".json"))
                .forEach(path -> {
                    try {
                        String worldName = path.getFileName().toString().replace(".json", "");
                        WorldData world = loadWorld(worldName);
                        if (world != null) {
                            worlds.put(worldName, world);
                        }
                    } catch (Exception e) {
                        logger.error("Failed to load world from: " + path, e);
                    }
                });
        } catch (Exception e) {
            logger.error("Failed to load worlds", e);
        }
        return worlds;
    }

    public void shutdown() {
        saveExecutor.shutdown();
        // Force save any cached data
        worldCache.values().forEach(this::saveWorld);
        playerCache.values().forEach(data -> savePlayerData(data.getUsername(), data));
    }
}


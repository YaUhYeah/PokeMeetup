package io.github.pokemeetup.system.gameplay.overworld.multiworld;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import io.github.pokemeetup.multiplayer.server.ServerStorageSystem;
import io.github.pokemeetup.multiplayer.server.entity.Entity;
import io.github.pokemeetup.system.gameplay.overworld.multiworld.WorldData;
import io.github.pokemeetup.utils.GameLogger;

import java.util.*;

public class WorldManager {
    private static final String WORLDS_DIR = "worlds/"; // Simplified path for Android
    private final Json json;
    private final Map<String, WorldData> worlds;
    private WorldData currentWorld;
    private final ServerStorageSystem storage;

    public WorldManager(ServerStorageSystem storage) {
        this.storage = storage;
        this.worlds = new HashMap<>();
        this.json = new Json();
        json.setOutputType(JsonWriter.OutputType.json);
        WorldData.setupJson(json);
    }

    private void ensureWorldDirectory(String worldName) {
        FileHandle worldDir = Gdx.files.local(WORLDS_DIR + worldName);
        if (!worldDir.exists()) {
            worldDir.mkdirs();
            GameLogger.info("Created world directory: " + worldDir.path());
        }
    }

    public WorldData createWorld(String name, long seed, float treeSpawnRate, float pokemonSpawnRate) {
        try {
            WorldData.WorldConfig config = new WorldData.WorldConfig(seed);
            config.setTreeSpawnRate(treeSpawnRate);
            config.setPokemonSpawnRate(pokemonSpawnRate);

            WorldData world = new WorldData(name, System.currentTimeMillis(), config);
            ensureWorldDirectory(name);

            worlds.put(name, world);
            saveWorld(world);
            GameLogger.info("Created new world: " + name);

            if (currentWorld == null) {
                currentWorld = world;
                GameLogger.info("Set current world to: " + name);
            }

            return world;
        } catch (Exception e) {
            GameLogger.error("Failed to create world: " + name + " - " + e.getMessage());
            throw new RuntimeException("World creation failed", e);
        }
    }

    public synchronized void saveWorld(WorldData world) {
        if (world == null) return;

        try {
            json.setUsePrototypes(false);
            String jsonString = json.prettyPrint(world);

            FileHandle worldFile = Gdx.files.local(WORLDS_DIR + world.getName() + "/world.json");
            FileHandle tempFile = Gdx.files.local(WORLDS_DIR + world.getName() + "/world.tmp");

            // Write to temp file first
            tempFile.writeString(jsonString, false);

            // Then move to final location
            if (worldFile.exists()) {
                worldFile.delete();
            }
            tempFile.moveTo(worldFile);

            GameLogger.info("Saved world: " + world.getName());
        } catch (Exception e) {
            GameLogger.error("Failed to save world: " + world.getName() + " - " + e.getMessage());
        }
    }

    public void init() {
        try {
            FileHandle worldsDir = Gdx.files.local(WORLDS_DIR);
            if (!worldsDir.exists()) {
                worldsDir.mkdirs();
                GameLogger.info("Created worlds directory");
            }

            loadWorlds();

            if (currentWorld == null && !worlds.isEmpty()) {
                currentWorld = worlds.values().iterator().next();
                GameLogger.info("Set current world to: " + currentWorld.getName());
            }
        } catch (Exception e) {
            GameLogger.error("Failed to initialize WorldManager: " + e.getMessage());
        }
    }

    private void loadWorlds() {
        worlds.clear();
        FileHandle worldsDir = Gdx.files.local(WORLDS_DIR);

        if (worldsDir.exists() && worldsDir.isDirectory()) {
            for (FileHandle dir : worldsDir.list()) {
                if (dir.isDirectory()) {
                    FileHandle worldFile = dir.child("world.json");
                    if (worldFile.exists()) {
                        try {
                            String jsonContent = worldFile.readString();
                            WorldData world = json.fromJson(WorldData.class, jsonContent);
                            worlds.put(world.getName(), world);
                            GameLogger.info("Loaded world: " + world.getName());
                        } catch (Exception e) {
                            GameLogger.error("Failed to load world from: " + worldFile.path());
                        }
                    }
                }
            }
        }
        GameLogger.info("Loaded " + worlds.size() + " worlds");
    }

    private void deleteDirectoryRecursively(FileHandle directory) {
        if (directory.exists()) {
            directory.deleteDirectory();
        }
    }

    public void deleteWorld(String name) {
        WorldData world = worlds.remove(name);
        if (world != null) {
            FileHandle worldDir = Gdx.files.local(WORLDS_DIR + name);
            deleteDirectoryRecursively(worldDir);
            GameLogger.info("Deleted world: " + name);
        }
    }

    // Storage-related methods
    public void saveWorldToStorage(WorldData world) {
        storage.saveWorld(world);
    }

    public WorldData loadWorld(String worldName) {
        return storage.loadWorld(worldName);
    }

    public Map<String, WorldData> getAllWorlds() {
        return storage.getAllWorlds();
    }

    // Getters and setters
    public Map<String, WorldData> getWorlds() {
        return new HashMap<>(worlds);
    }

    public WorldData getWorld(String name) {
        return worlds.get(name);
    }

    public WorldData getCurrentWorld() {
        return currentWorld;
    }

    public void setCurrentWorld(String worldName) {
        WorldData world = worlds.get(worldName);
        if (world != null) {
            currentWorld = world;
            GameLogger.info("Switched current world to: " + worldName);
        } else {
            GameLogger.error("World not found: " + worldName);
        }
    }

    // Entity management
    public void addEntityToCurrentWorld(Entity entity) {
        if (currentWorld != null) {
            currentWorld.addEntity(entity);
            saveWorld(currentWorld);
        } else {
            GameLogger.error("No current world set. Cannot add entity.");
        }
    }

    public void removeEntityFromCurrentWorld(UUID entityId) {
        if (currentWorld != null) {
            currentWorld.removeEntity(entityId);
            saveWorld(currentWorld);
        } else {
            GameLogger.error("No current world set. Cannot remove entity.");
        }
    }

    public Collection<Entity> getEntitiesFromCurrentWorld() {
        if (currentWorld != null) {
            return currentWorld.getEntities();
        }
        return new ArrayList<>();
    }

    public void refreshWorlds() {
        loadWorlds();
    }
}

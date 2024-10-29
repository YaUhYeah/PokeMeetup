package io.github.pokemeetup.system.gameplay.overworld.multiworld;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import io.github.pokemeetup.multiplayer.server.ServerStorageSystem;
import io.github.pokemeetup.multiplayer.server.entity.Entity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

public class WorldManager {
    private static final String WORLDS_DIR = "assets/worlds/";
    private final Json json;
    private final Map<String, WorldData> worlds;
    private WorldData currentWorld; // Active world

    public WorldManager(ServerStorageSystem storage) {
        this.storage = storage;

        this.worlds = new HashMap<>();
        this.json = new Json();
        // Configure Json serializer
        json.setOutputType(JsonWriter.OutputType.json);
        WorldData.setupJson(json);
    }
    private final ServerStorageSystem storage;

    /**
     * Ensures that the directory for the specified world exists.
     *
     * @param worldName The name of the world.
     * @throws IOException If directory creation fails.
     */
    public void ensureWorldDirectory(String worldName) throws IOException {
        Path worldDirPath = Paths.get(WORLDS_DIR, worldName);
        if (!Files.exists(worldDirPath)) {
            Files.createDirectories(worldDirPath);
            System.out.println("Created world directory: " + worldDirPath.toAbsolutePath());
        }
    }

    /**
     * Creates a new world with the specified parameters.
     *
     * @param name             The name of the new world.
     * @param seed             The seed for world generation.
     * @param treeSpawnRate    The spawn rate for trees.
     * @param pokemonSpawnRate The spawn rate for Pokémon.
     * @return The created WorldData object.
     * @throws IOException If world creation fails.
     */
    public WorldData createWorld(String name, long seed, float treeSpawnRate, float pokemonSpawnRate) throws IOException {
        // Create new world data
        WorldData.WorldConfig config = new WorldData.WorldConfig(seed);
        config.setTreeSpawnRate(treeSpawnRate);
        config.setPokemonSpawnRate(pokemonSpawnRate);

        WorldData world = new WorldData(name, System.currentTimeMillis(), config);

        // Ensure world directory exists
        ensureWorldDirectory(name);

        // Save world and update cache
        worlds.put(name, world);
        saveWorld(world);
        System.out.println("Created new world: " + name);

        // Set as current world if none is set
        if (currentWorld == null) {
            currentWorld = world;
            System.out.println("Set current world to: " + name);
        }

        return world;
    }

    /**
     * Saves the specified world data to its corresponding JSON file.
     *
     * @param world The WorldData object to save.
     */
    public synchronized void saveWorld(WorldData world) {
        if (world == null) return;

        try {
            Json json = new Json();
            json.setUsePrototypes(false);

            String jsonString = json.prettyPrint(world);
            Path worldPath = Paths.get("assets/worlds", world.getName(), "world.json");

            // Create directories if they don't exist
            Files.createDirectories(worldPath.getParent());

            // Write atomically using temp file
            Path tempFile = worldPath.resolveSibling(world.getName() + ".tmp");
            Files.writeString(tempFile, jsonString);
            Files.move(tempFile, worldPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

        } catch (IOException e) {
            throw new RuntimeException("Failed to save world: " + e.getMessage(), e);
        }
    }
    public void saveWorldToStorage(WorldData world) {
        storage.saveWorld(world);
    }

    public WorldData loadWorld(String worldName) {
        return storage.loadWorld(worldName);
    }

    public Map<String, WorldData> getAllWorlds() {
        return storage.getAllWorlds();
    }

    /**
     * Initializes the WorldManager by loading existing worlds.
     */
    public void init() {
        Path worldsDirPath = Paths.get(WORLDS_DIR);
        try {
            if (!Files.exists(worldsDirPath)) {
                Files.createDirectories(worldsDirPath);
                System.out.println("Created worlds directory: " + worldsDirPath.toAbsolutePath());
            }
            loadWorlds();

            // Set currentWorld to a default world if not set
            if (currentWorld == null && !worlds.isEmpty()) {
                currentWorld = worlds.values().iterator().next();
                System.out.println("Set current world to: " + currentWorld.getName());
            }
        } catch (IOException e) {
            System.err.println("Failed to initialize WorldManager: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Loads all worlds from the worlds directory.
     */
    private void loadWorlds() {
        worlds.clear();
        Path worldsDirPath = Paths.get(WORLDS_DIR);

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(worldsDirPath)) {
            for (Path dir : stream) {
                if (Files.isDirectory(dir)) {
                    Path worldFilePath = dir.resolve("world.json");
                    if (Files.exists(worldFilePath)) {
                        try {
                            String jsonContent = Files.readString(worldFilePath, StandardCharsets.UTF_8);
                            WorldData world = json.fromJson(WorldData.class, jsonContent);
                            worlds.put(world.getName(), world);
                            System.out.println("Loaded world: " + world.getName());
                        } catch (Exception e) {
                            System.err.println("Failed to load world from: " + worldFilePath.toAbsolutePath());
                            e.printStackTrace();
                        }
                    }
                }
            }
            System.out.println("Loaded " + worlds.size() + " worlds.");
        } catch (IOException e) {
            System.err.println("Error loading worlds: " + e.getMessage());
            e.printStackTrace();
        }
    }


    public WorldData getWorld(String name) {
        return worlds.get(name);
    }


    /**
     * Recursively deletes a directory and its contents.
     *
     * @param path The path to the directory.
     * @throws IOException If deletion fails.
     */
    private void deleteDirectoryRecursively(Path path) throws IOException {
        if (Files.notExists(path)) return;

        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException e) {
                        System.err.println("Failed to delete " + p.toAbsolutePath() + ": " + e.getMessage());
                    }
                });
        }
    }

    /**
     * Deletes the specified world.
     *
     * @param name The name of the world to delete.
     */
    public void deleteWorld(String name) {
        WorldData world = worlds.remove(name);
        if (world != null) {
            Path worldDirPath = Paths.get(WORLDS_DIR, name);
            try {
                deleteDirectoryRecursively(worldDirPath);
                System.out.println("Deleted world: " + name);
            } catch (IOException e) {
                System.err.println("Failed to delete world directory: " + worldDirPath.toAbsolutePath());
                e.printStackTrace();
            }
        }
    }

    public Map<String, WorldData> getWorlds() {
        return new HashMap<>(worlds);
    }

    public void refreshWorlds() {
        loadWorlds();
    }

    // Getter for currentWorld
    public WorldData getCurrentWorld() {
        return currentWorld;
    }

    // Setter for currentWorld
    public void setCurrentWorld(String worldName) {
        WorldData world = worlds.get(worldName);
        if (world != null) {
            currentWorld = world;
            System.out.println("Switched current world to: " + worldName);
        } else {
            System.err.println("World not found: " + worldName);
        }
    }

    // Methods to manage entities in the current world

    /**
     * Adds an entity to the current world.
     *
     * @param entity The entity to add.
     */
    public void addEntityToCurrentWorld(Entity entity) {
        if (currentWorld != null) {
            currentWorld.addEntity(entity);
            saveWorld(currentWorld); // Save after adding
        } else {
            System.err.println("No current world set. Cannot add entity.");
        }
    }

    /**
     * Removes an entity from the current world by its UUID.
     *
     * @param entityId The UUID of the entity to remove.
     */
    public void removeEntityFromCurrentWorld(UUID entityId) {
        if (currentWorld != null) {
            currentWorld.removeEntity(entityId);
            saveWorld(currentWorld); // Save after removing
        } else {
            System.err.println("No current world set. Cannot remove entity.");
        }
    }

    /**
     * Retrieves all entities from the current world.
     *
     * @return A collection of entities.
     */
    public Collection<Entity> getEntitiesFromCurrentWorld() {
        if (currentWorld != null) {
            return currentWorld.getEntities();
        }
        return new ArrayList<>();
    }
}

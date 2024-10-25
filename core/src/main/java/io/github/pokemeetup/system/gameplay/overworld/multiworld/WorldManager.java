package io.github.pokemeetup.system.gameplay.overworld.multiworld;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;

import java.util.HashMap;
import java.util.Map;

public class WorldManager {
    private static final String WORLDS_DIR = "worlds/";
    private final Json json;
    private Map<String, WorldData> worlds;

    public WorldManager() {
        this.worlds = new HashMap<>();
        this.json = new Json();
        // Configure Json serializer
        json.setOutputType(JsonWriter.OutputType.json);
    }

    public void init() {
        FileHandle worldsDir = Gdx.files.local(WORLDS_DIR);
        if (!worldsDir.exists()) {
            worldsDir.mkdirs();
        }
        loadWorlds();
    }

    public void ensureWorldDirectory(String worldName) {
        FileHandle worldDir = Gdx.files.local(WORLDS_DIR + worldName);
        if (!worldDir.exists()) {
            worldDir.mkdirs();
        }
    }

    public WorldData createWorld(String name, long seed, float treeSpawnRate, float pokemonSpawnRate) {
        // Create new world data
        WorldData.WorldConfig config = new WorldData.WorldConfig(seed);
        config.setTreeSpawnRate(treeSpawnRate);
        config.setPokemonSpawnRate(pokemonSpawnRate);

        WorldData world = new WorldData(name, System.currentTimeMillis(), config);

        // Save world and update cache
        worlds.put(name, world);
        saveWorld(world);
        System.out.println("Created new world: " + name);
        return world;
    }

    public void saveWorld(WorldData world) {
        try {
            // Ensure world directory exists
            FileHandle worldDir = Gdx.files.local(WORLDS_DIR + world.getName());
            if (!worldDir.exists()) {
                worldDir.mkdirs();
            }

            // Save world data
            FileHandle worldFile = worldDir.child("world.json");
            String jsonString = json.prettyPrint(world);
            worldFile.writeString(jsonString, false);

            // Update in-memory cache
            worlds.put(world.getName(), world);

            System.out.println("Saved world: " + world.getName());
            System.out.println("World data: " + jsonString);

        } catch (Exception e) {
            Gdx.app.error("WorldManager", "Failed to save world: " + world.getName(), e);
        }
    }

    private void loadWorlds() {
        worlds.clear();
        FileHandle worldsDir = Gdx.files.local(WORLDS_DIR);

        if (!worldsDir.exists()) {
            return;
        }

        for (FileHandle dir : worldsDir.list()) {
            if (dir.isDirectory()) {
                FileHandle worldFile = dir.child("world.json");
                if (worldFile.exists()) {
                    try {
                        String jsonContent = worldFile.readString();
                        WorldData world = json.fromJson(WorldData.class, jsonContent);
                        worlds.put(world.getName(), world);
                        System.out.println("Loaded world: " + world.getName());
                    } catch (Exception e) {
                        Gdx.app.error("WorldManager", "Failed to load world from: " + dir.name(), e);
                    }
                }
            }
        }
        System.out.println("Loaded " + worlds.size() + " worlds");
    }

    public WorldData getWorld(String name) {
        return worlds.get(name);
    }

    public void deleteWorld(String name) {
        WorldData world = worlds.remove(name);
        if (world != null) {
            FileHandle worldDir = Gdx.files.local(WORLDS_DIR + name);
            if (worldDir.exists()) {
                worldDir.deleteDirectory();
                System.out.println("Deleted world: " + name);
            }
        }
    }

    public Map<String, WorldData> getWorlds() {
        return new HashMap<>(worlds);
    }

    public void refreshWorlds() {
        loadWorlds();
    }
}

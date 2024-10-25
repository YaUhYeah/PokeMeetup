package io.github.pokemeetup.system.gameplay.overworld.multiworld;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.ObjectMap;
import io.github.pokemeetup.system.PlayerData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WorldData {
    private String name;
    private long lastPlayed;
    private WorldConfig config;
    private ObjectMap<String, PlayerData> players; // Change to ObjectMap for libGDX compatibility
    private Map<String, PluginConfig> pluginConfigs; // For plugin system

    public WorldData() {
        this.players = new ObjectMap<>();
    } // Required for Json serialization

    public WorldData(String name, long seed) {
        this.name = name;
        this.lastPlayed = System.currentTimeMillis();
        this.config = new WorldConfig(seed);
        this.players = new ObjectMap<>();
    }


    public WorldData(String name, long lastPlayed, WorldConfig config) {
        this.name = name;
        this.lastPlayed = lastPlayed;
        this.config = config;
    }

    public static void setupJson(Json json) {
        json.setSerializer(HashMap.class, new Json.Serializer<HashMap>() {
            @Override
            public void write(Json json, HashMap map, Class knownType) {
                // Write map entries directly
                json.writeObjectStart();
                for (Object entry : map.entrySet()) {
                    Map.Entry mapEntry = (Map.Entry) entry;
                    json.writeValue(mapEntry.getKey().toString(), mapEntry.getValue());
                }
                json.writeObjectEnd();
            }

            @Override
            @SuppressWarnings("unchecked")
            public HashMap read(Json json, JsonValue jsonData, Class type) {
                HashMap result = new HashMap();
                for (JsonValue child = jsonData.child; child != null; child = child.next) {
                    result.put(child.name, json.readValue(PlayerData.class, child));
                }
                return result;
            }
        });
    }

    public void savePlayerData(String username, PlayerData data) {
        System.out.println("Saving player data for: " + username);
        if (players == null) {
            players = new ObjectMap<>();
        }
        players.put(username, data);
        System.out.println("Saved position: " + data.getX() + "," + data.getY());
    }

    public PlayerData getPlayerData(String username) {
        System.out.println("Getting player data for: " + username);
        if (players != null) {
            PlayerData data = players.get(username);
            System.out.println("Found data? " + (data != null));
            return data;
        }
        System.out.println("Players map is null");
        return null;
    }

//    private void loadWorld(FileHandle file) {
//        try {
//            Json json = new Json();
//            setupJson(json);
//            String content = file.readString();
//            System.out.println("Loading world data: " + content);
//            WorldData loaded = json.fromJson(WorldData.class, content);
//            this.name = loaded.name;
//            this.lastPlayed = loaded.lastPlayed;
//            this.config = loaded.config;
//            this.players = loaded.players != null ? loaded.players : new HashMap<>();
//        } catch (Exception e) {
//            System.err.println("Failed to load world: " + e.getMessage());
//            e.printStackTrace();
//        }
//    }
// These are needed for proper serialization
public ObjectMap<String, PlayerData> getPlayers() {
    return players;
}  public void setPlayers(ObjectMap<String, PlayerData> players) {
        this.players = players;
    }
    public void addPlayer(String username, PlayerData data) {
        this.players.put(username, data);
    }

    // Required getters/setters for Json serialization
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getLastPlayed() {
        return lastPlayed;
    }

    public void setLastPlayed(long lastPlayed) {
        this.lastPlayed = lastPlayed;
    }

    public WorldConfig getConfig() {
        return config;
    }

    public void setConfig(WorldConfig config) {
        this.config = config;
    }

    public void updateLastPlayed() {
        this.lastPlayed = System.currentTimeMillis();
    }

    public long getSeed() {
        return config.getSeed();
    }

    public float getTreeSpawnRate() {
        return config.getTreeSpawnRate();
    }

    public float getPokemonSpawnRate() {
        return config.getPokemonSpawnRate();
    }

    // Methods for plugin management
    public void setPluginConfig(String pluginId, Map<String, Object> settings) {
        PluginConfig config = new PluginConfig();
        config.pluginId = pluginId;
        config.settings = settings;
        pluginConfigs.put(pluginId, config);
    }

    public PluginConfig getPluginConfig(String pluginId) {
        return pluginConfigs.get(pluginId);
    }

    // Methods for player data management
    // Debug the load/save process

    // Item stack for inventory
    public static class ItemStack {
        private String itemId;
        private int count;

        public ItemStack(String itemId, int count) {
            this.itemId = itemId;
            this.count = count;
        }
        // Add getters/setters
    }

    // Plugin configuration storage
    public static class PluginConfig {
        private String pluginId;
        private Map<String, Object> settings;

        public PluginConfig() {
            this.settings = new HashMap<>();
        }
        // Add getters/setters
    }

    public static class WorldConfig {
        private long seed;
        private float treeSpawnRate = 0.15f;
        private float pokemonSpawnRate = 0.05f;

        public WorldConfig() {
        } // Required for Json serialization

        public WorldConfig(long seed) {
            this.seed = seed;
            this.treeSpawnRate = 0.15f;
            this.pokemonSpawnRate = 0.05f;
        }

        // Required getters/setters for Json serialization
        public long getSeed() {
            return seed;
        }

        public void setSeed(long seed) {
            this.seed = seed;
        }

        public float getTreeSpawnRate() {
            return treeSpawnRate;
        }

        public void setTreeSpawnRate(float rate) {
            this.treeSpawnRate = rate;
        }

        public float getPokemonSpawnRate() {
            return pokemonSpawnRate;
        }

        public void setPokemonSpawnRate(float rate) {
            this.pokemonSpawnRate = rate;
        }
    }
}

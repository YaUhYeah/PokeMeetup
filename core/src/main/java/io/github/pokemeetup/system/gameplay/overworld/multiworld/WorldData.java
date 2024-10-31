package io.github.pokemeetup.system.gameplay.overworld.multiworld;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.ObjectMap;
import io.github.pokemeetup.multiplayer.server.entity.Entity;
import io.github.pokemeetup.system.PlayerData;
import io.github.pokemeetup.utils.GameLogger;

import java.util.*;

public class WorldData {
    private String name;
    private long lastPlayed;
    private WorldConfig config;
    private HashMap<String, PlayerData> players; // Change to ObjectMap for libGDX compatibility    private ObjectMap<String, PlayerData> players; // Change to ObjectMap for libGDX compatibility
    private HashMap<UUID, Entity> entities; // Add this field for entities
    private Map<String, PluginConfig> pluginConfigs; // For plugin system

    public WorldData() {
        this.players = new HashMap<>();
        this.entities = new HashMap<UUID, Entity>() {
        };
    } // Required for Json serialization

    public WorldData(String name, long seed) {
        this.name = name;
        this.lastPlayed = System.currentTimeMillis();
        this.config = new WorldConfig(seed);
        this.entities = new HashMap<>();
        this.players = new HashMap<>();
    }


    public WorldData(String name, long lastPlayed, WorldConfig config) {
        this.name = name;
        this.entities = new HashMap<>();
        this.lastPlayed = lastPlayed;
        this.players = new HashMap<>();
        this.config = config;
    }public static void setupJson(Json json) {
        json.setSerializer(WorldData.class, new Json.Serializer<WorldData>() {
            @Override
            public void write(Json json, WorldData worldData, Class knownType) {
                json.writeObjectStart();
                json.writeValue("name", worldData.getName());
                json.writeValue("lastPlayed", worldData.getLastPlayed());
                json.writeValue("config", worldData.getConfig());

                // Ensure that empty maps are serialized as empty objects
                if (worldData.getPlayers() == null || worldData.getPlayers().isEmpty()) {
                    json.writeValue("players", new HashMap<String, PlayerData>());
                } else {
                    json.writeValue("players", worldData.getPlayers());
                }

                if (worldData.getEntities() == null || worldData.getEntities().isEmpty()) {
                    json.writeValue("entities", new HashMap<String, Entity>());
                } else {
                    json.writeValue("entities", worldData.getEntities());
                }


                json.writeObjectEnd();
            }

            @Override
            public WorldData read(Json json, JsonValue jsonData, Class type) {
                String name = jsonData.getString("name");
                long lastPlayed = jsonData.getLong("lastPlayed");
                WorldConfig config = json.readValue(WorldConfig.class, jsonData.get("config"));

                // Read players map
                JsonValue playersValue = jsonData.get("players");
                Map<String, PlayerData> players;
                if (playersValue != null && playersValue.isObject()) {
                    players = json.readValue(HashMap.class, PlayerData.class, playersValue);
                } else {
                    players = new HashMap<>();
                }

                // Read entities map
                JsonValue entitiesValue = jsonData.get("entities");
                Map<String, Entity> entities;
                if (entitiesValue != null && entitiesValue.isObject()) {
                    entities = json.readValue(HashMap.class, Entity.class, entitiesValue);
                } else {
                    entities = new HashMap<>();
                }

                // Read pluginConfigs map
                JsonValue pluginConfigsValue = jsonData.get("pluginConfigs");
                Map<String, Object> pluginConfigs;
                if (pluginConfigsValue != null && pluginConfigsValue.isObject()) {
                    pluginConfigs = json.readValue(HashMap.class, Object.class, pluginConfigsValue);
                } else {
                    pluginConfigs = new HashMap<>();
                }

                WorldData worldData = new WorldData(name, lastPlayed, config);
                worldData.setPlayers((HashMap<String, PlayerData>) players);

                return worldData;
            }
        });

        // Register serializers for other custom classes if needed
        // e.g., WorldConfig, PlayerData, Entity
    }


    // In WorldData class
    public void savePlayerData(String username, PlayerData data) {
        if (players == null) {
            players = new HashMap<>();
        }

        if (data == null || username == null) {
            return;
        }

        // Create deep copy to avoid reference issues
        PlayerData copy = data.copy();
        players.put(username, copy);

        // Verify save
        PlayerData saved = players.get(username);
        if (saved != null) {
            GameLogger.info("Saved state verified for: " + username +
                " at " + saved.getX() + "," + saved.getY());
        }
    }

    // Improve player data retrieval
    public PlayerData getPlayerData(String username) {
        if (username == null || players == null) {
            GameLogger.info("either username or players is null");
            return null;
        }

        PlayerData data = players.get(username);
        if (data != null) {
            // Return a deep copy
            return data.copy();
        }

        return null;
    }
    // Methods for managing entities

    /**
     * Adds a new entity to the world.
     *
     * @param entity The entity to add.
     */
    public void addEntity(Entity entity) {
        if (entities == null) {
            entities = new HashMap<>();
        }
        entities.put(entity.getId(), entity);
        GameLogger.info("Added entity: ID=" + entity.getId() + ", Type=" + entity.getType());
    }

    /**
     * Removes an entity from the world.
     *
     * @param entityId The UUID of the entity to remove.
     */
    public void removeEntity(UUID entityId) {
        if (entities != null && entities.containsKey(entityId)) {
            entities.remove(entityId);
            GameLogger.info("Removed entity: ID=" + entityId);
        }
    }

    /**
     * Retrieves all entities in the world.
     *
     * @return A collection of all entities.
     */
    public Collection<Entity> getEntities() {
        if (entities != null) {
            return new ArrayList<>((Collection) entities.values());
        }
        return new ArrayList<>();
    }

    public HashMap<String, PlayerData> getPlayers() {
        return players;
    }

    public void setPlayers(HashMap<String, PlayerData> players) {
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

package io.github.pokemeetup.system.gameplay.overworld.multiworld;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.ObjectMap;
import io.github.pokemeetup.multiplayer.server.entity.CreatureEntity;
import io.github.pokemeetup.multiplayer.server.entity.Entity;
import io.github.pokemeetup.multiplayer.server.entity.EntityType;
import io.github.pokemeetup.multiplayer.server.entity.PokeballEntity;
import io.github.pokemeetup.system.PlayerData;
import io.github.pokemeetup.utils.GameLogger;

import java.util.*;

import static io.github.pokemeetup.multiplayer.server.entity.EntityType.POKEBALL;

public class WorldData {
    private String name;
    private long lastPlayed;
    private WorldConfig config;
    private ObjectMap<String, PlayerData> players; // Change to ObjectMap for libGDX compatibility    private ObjectMap<String, PlayerData> players; // Change to ObjectMap for libGDX compatibility
    private ObjectMap<UUID, Entity> entities; // Add this field for entities
    private Map<String, PluginConfig> pluginConfigs; // For plugin system

    public WorldData() {
        this.players = new ObjectMap<>();
        this.entities = new ObjectMap<>();
    } // Required for Json serialization

    public WorldData(String name, long seed) {
        this.name = name;
        this.lastPlayed = System.currentTimeMillis();
        this.config = new WorldConfig(seed);
        this.entities = new ObjectMap<>();
        this.players = new ObjectMap<>();
    }


    public WorldData(String name, long lastPlayed, WorldConfig config) {
        this.name = name;
        this.entities = new ObjectMap<>();
        this.lastPlayed = lastPlayed;
        this.players = new ObjectMap<>();
        this.config = config;
    }
    public static void setupJson(Json json) {
        // Register PlayerData serializer
        json.setSerializer(PlayerData.class, new Json.Serializer<PlayerData>() {
            @Override
            public void write(Json json, PlayerData playerData, Class knownType) {
                json.writeObjectStart();
                json.writeValue("username", playerData.getUsername());
                json.writeValue("x", playerData.getX());
                json.writeValue("y", playerData.getY());
                json.writeValue("direction", playerData.getDirection());
                json.writeValue("isMoving", playerData.isMoving());
                json.writeValue("wantsToRun", playerData.isWantsToRun());
                json.writeValue("inventoryItems", playerData.getInventoryItems());
                json.writeObjectEnd();
            }

            @Override
            public PlayerData read(Json json, JsonValue jsonData, Class type) {
                String username = jsonData.getString("username");
                PlayerData playerData = new PlayerData(username);
                playerData.setX(jsonData.getFloat("x"));
                playerData.setY(jsonData.getFloat("y"));
                playerData.setDirection(jsonData.getString("direction"));
                playerData.setMoving(jsonData.getBoolean("isMoving"));
                playerData.setWantsToRun(jsonData.getBoolean("wantsToRun"));
                playerData.setInventoryItems(json.readValue(ArrayList.class, jsonData.get("inventoryItems")));
                return playerData;
            }
        });

        // Register Entity serializer
        json.setSerializer(Entity.class, new Json.Serializer<Entity>() {
            @Override
            public void write(Json json, Entity entity, Class knownType) {
                json.writeObjectStart();
                json.writeValue("id", entity.getId().toString());
                json.writeValue("position", entity.getPosition());
                json.writeValue("velocity", entity.getVelocity());
                json.writeValue("type", entity.getType().name());
                json.writeValue("isDead", entity.isDead());
                json.writeObjectEnd();
            }

            @Override
            public Entity read(Json json, JsonValue jsonData, Class type) {
                String idStr = jsonData.getString("id");
                UUID id = UUID.fromString(idStr);
                Vector2 position = json.readValue(Vector2.class, jsonData.get("position"));
                Vector2 velocity = json.readValue(Vector2.class, jsonData.get("velocity"));
                String typeStr = jsonData.getString("type");
                EntityType entityType;
                try {
                    entityType = EntityType.valueOf(typeStr);
                } catch (IllegalArgumentException e) {
                    entityType = EntityType.ITEM; // Default type if unknown
                }
                boolean isDead = jsonData.getBoolean("isDead");

                // Instantiate concrete Entity subclasses based on EntityType
                Entity entity;
                switch (entityType) {
                    case POKEBALL:
                        entity = new PokeballEntity(position.x, position.y);
                        break;
                    default:
                        entity = new CreatureEntity(position.x, position.y);
                }
                // Set other properties as needed
                return entity;
            }
        });

        // Register other serializers as needed
    }
    // In WorldData class
    public void savePlayerData(String username, PlayerData data) {
        if (players == null) {
            players = new ObjectMap<>();
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
            entities = new ObjectMap<>();
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

    public ObjectMap<String, PlayerData> getPlayers() {
        return players;
    }

    public void setPlayers(ObjectMap<String, PlayerData> players) {
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

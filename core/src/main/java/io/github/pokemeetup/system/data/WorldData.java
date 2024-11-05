// In WorldData.java
package io.github.pokemeetup.system.data;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Json;
import io.github.pokemeetup.blocks.PlaceableBlock;
import io.github.pokemeetup.pokemon.WildPokemon;
import io.github.pokemeetup.system.gameplay.overworld.Chunk;
import io.github.pokemeetup.system.gameplay.overworld.WorldObject;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.storage.BlockSaveData;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class WorldData {// In WorldData.java

    private final Map<Vector2, PlaceableBlock> placedBlocks = new ConcurrentHashMap<>();
    private final Object saveLock = new Object();
    private final Object timeLock = new Object();
    // Update toJson() method to include new fields
    // Add this field to track Pokemon
    private final Map<UUID, WildPokemon> wildPokemonMap = new ConcurrentHashMap<>();
    private PokemonData pokemonData;  // Keep existing field
    private float dayLength = 10; // Real minutes per in-game day
    private String name;
    private HashMap<String, PlayerData> players; // Use HashMap instead of Map
    private long lastPlayed;
    private WorldConfig config;
    private boolean isDirty; // Track if data needs saving
    private BlockSaveData blockData;
    // Add new fields
    private Map<Vector2, Chunk> chunks = new HashMap<>();
    private Map<Vector2, List<WorldObject>> chunkObjects = new HashMap<>();
    private long worldSeed;
    private int spawnX, spawnY;
    private String username; // New field for username
    private double worldTimeInMinutes = 480.0; // Start at 8:00 AM by default
    private long playedTime = 0L; // New field to track total played time in milliseconds

    public WorldData(String name) {
        this();
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("World name cannot be null or empty");
        }
        this.name = name.trim();
    }

    public WorldData() {
        this.players = new HashMap<>();
        this.pokemonData = new PokemonData();
        this.lastPlayed = System.currentTimeMillis();
    }

    public WorldData(String name, long lastPlayed, WorldConfig config) {
        this();
        this.name = name;
        this.pokemonData = new PokemonData();
        this.lastPlayed = lastPlayed;
        this.config = config;
    }

//    public void addWildPokemonData(WildPokemon pokemon) {
//        if (pokemonData == null) {
//            pokemonData = new PokemonData();
//        }
//        WildPokemonData data = new WildPokemonData();
//        data.setName(pokemon.getName());
//        data.setLevel(pokemon.getLevel());
//        data.setPosition(new Vector2(pokemon.getX(), pokemon.getY()));
//        data.setDirection(pokemon.getDirection());
//        data.setMoving(pokemon.isMoving());
//        data.setSpawnTime(pokemon.getSpawnTime());
//        // Set other fields as necessary
//
//        pokemonData.addWildPokemon(pokemon.getUuid(), data);
//        setDirty(true);
//    }

    // New constructor to include username
    public WorldData(String name, long lastPlayed, WorldConfig config, String username) {
        this(name, lastPlayed, config);
        this.username = username;
    }

    public static WorldData fromJson(String jsonStr) {
        try {
            Json json = new Json();
            return json.fromJson(WorldData.class, jsonStr);
        } catch (Exception e) {
            GameLogger.error("Failed to parse WorldData from JSON: " + e.getMessage());
            return null;
        }
    }

    public void updateFromServerData(WorldData serverData) {
        // Ensure that serverData is not null to prevent NullPointerExceptions
        if (serverData == null) {
            GameLogger.error("Received null serverData in updateFromServerData");
            return;
        }

        // Synchronize on saveLock to ensure thread safety during updates
        synchronized (saveLock) {
            // Update core fields
            this.name = serverData.getName();
            this.worldSeed = serverData.getConfig().getSeed();
            this.spawnX = serverData.getSpawnX();
            this.spawnY = serverData.getSpawnY();
            this.dayLength = serverData.getDayLength();
            this.worldTimeInMinutes = serverData.getWorldTimeInMinutes();
            this.playedTime = serverData.getPlayedTime();

            // Update configuration if necessary
            if (serverData.getConfig() != null) {
                this.config = serverData.getConfig();
            }

            // Update placedBlocks
            if (serverData.getPlacedBlocks() != null) {
                this.placedBlocks.clear();
                this.placedBlocks.putAll(serverData.getPlacedBlocks());
            }

            // Update chunks
            if (serverData.getChunks() != null) {
                this.chunks.clear();
                this.chunks.putAll(serverData.getChunks());
            }

            // Update chunkObjects
            if (serverData.getChunkObjects() != null) {
                this.chunkObjects.clear();
                this.chunkObjects.putAll(serverData.getChunkObjects());
            }

            // Update players
            if (serverData.getPlayersMap() != null) {
                this.players.clear();
                this.players.putAll(serverData.getPlayersMap());
            }

            // Update pokemonData
            if (serverData.getPokemonData() != null) {
                this.pokemonData = serverData.getPokemonData();
            }

            // Update blockData if necessary
            if (serverData.getBlockData() != null) {
                this.blockData = serverData.getBlockData();
            }

            setDirty(false);

            GameLogger.info("WorldData updated from server data successfully.");
        }
    }

    public void setWorldSeed(long seed) {
        this.worldSeed = seed;
    }

    public void addChunk(Vector2 position, Chunk chunk) {
        chunks.put(position, chunk);
    }

    public void addChunkObjects(Vector2 position, List<WorldObject> objects) {
        chunkObjects.put(position, new ArrayList<>(objects));
    }

    private Map<Vector2, ChunkData> serializeChunks() {
        Map<Vector2, ChunkData> serializedChunks = new HashMap<>();
        for (Map.Entry<Vector2, Chunk> entry : chunks.entrySet()) {
            ChunkData chunkData = new ChunkData();
            chunkData.tileData = entry.getValue().getTileData();
            chunkData.primaryBiomeType = entry.getValue().getBiome().getType().name();
            serializedChunks.put(entry.getKey(), chunkData);
        }
        return serializedChunks;
    }

    private Map<Vector2, List<WorldObjectData>> serializeChunkObjects() {
        Map<Vector2, List<WorldObjectData>> serializedObjects = new HashMap<>();
        for (Map.Entry<Vector2, List<WorldObject>> entry : chunkObjects.entrySet()) {
            List<WorldObjectData> objectDataList = entry.getValue().stream()
                .map(worldObject -> {
                    WorldObjectData data = new WorldObjectData();
                    data.type = worldObject.getType().name(); // Assuming getType() returns an enum
                    data.x = worldObject.getPixelX();
                    data.y = worldObject.getPixelY();
                    data.id = worldObject.getId(); // Assuming getId() exists
                    // Map other necessary fields...
                    return data;
                })
                .collect(Collectors.toList());
            serializedObjects.put(entry.getKey(), objectDataList);
        }
        return serializedObjects;
    }

    // In WorldData.java
    public Map<String, Object> getSerializableData() {
        Map<String, Object> data = new HashMap<>();
        data.put("name", name);
        data.put("seed", config.getSeed());
        data.put("timeInMinutes", worldTimeInMinutes);
        data.put("playedTime", playedTime);
        data.put("chunks", serializeChunks());
        data.put("chunkObjects", serializeChunkObjects());
        data.put("spawnX", spawnX);
        data.put("spawnY", spawnY);
        data.put("pokemon", serializePokemon());
        return data;
    }

    private Map<String, Object> serializePokemon() {
        Map<String, Object> serializedPokemonData = new HashMap<>();
        for (Map.Entry<UUID, PokemonData.WildPokemonData> entry : this.pokemonData.getWildPokemon().entrySet()) {
            serializedPokemonData.put(entry.getKey().toString(), entry.getValue());
        }
        return serializedPokemonData;
    }

    public WorldData serializeForNetwork() {
        WorldData data = new WorldData(this.name); // Assuming `name` is a field in `World`
        data.setWorldSeed(this.config.getSeed());
        data.setWorldTimeInMinutes(this.getWorldTimeInMinutes());
        data.setPlayedTime(this.getPlayedTime());
        // Serialize other necessary fields and chunks
        return data;
    }

    // Add utility method to get chunk objects for a position
    public List<WorldObject> getChunkObjects(Vector2 position) {
        return chunkObjects.getOrDefault(position, new ArrayList<>());
    }

    public Object getTimeLock() {
        return timeLock;
    }

    public int getSpawnX() {
        return spawnX;
    }

    public void setSpawnX(int x) {
        this.spawnX = x;
    }

    public int getSpawnY() {
        return spawnY;
    }

    public void setSpawnY(int y) {
        this.spawnY = y;
    }

    public Map<Vector2, Chunk> getChunks() {
        return chunks;
    }

    public Map<Vector2, List<WorldObject>> getChunkObjects() {
        return chunkObjects;
    }

    public void updateTime(float deltaTime) {
        synchronized (timeLock) {
            // Update played time (convert delta to milliseconds)
            long deltaMillis = (long) (deltaTime * 1000);
            playedTime += deltaMillis;

            // Calculate game minutes per real second
            double gameMinutesPerSecond = (24 * 60.0) / (dayLength * 60.0);
            // Calculate time to add this frame
            double timeToAdd = deltaTime * gameMinutesPerSecond;

            // Update world time
            worldTimeInMinutes = (worldTimeInMinutes + timeToAdd) % (24 * 60);

            // Log time updates periodically
            if (playedTime % 1000 < deltaMillis) { // Log roughly every second
                GameLogger.info(String.format(
                    "Time Update - World: %.2f, Played: %d, Day Length: %.1f",
                    worldTimeInMinutes, playedTime, dayLength
                ));
            }
        }
    }

    // Make getters thread-safe
    public double getWorldTimeInMinutes() {
        synchronized (timeLock) {
            return worldTimeInMinutes;
        }
    }

    public void setWorldTimeInMinutes(double time) {
        synchronized (saveLock) {
            this.worldTimeInMinutes = time;
            setDirty(true);
        }
    }

    public long getPlayedTime() {
        synchronized (timeLock) {
            return playedTime;
        }
    }

    public void setPlayedTime(long playedTime) {
        synchronized (saveLock) {
            this.playedTime = playedTime;
            setDirty(true);
        }
    }

    public float getDayLength() {
        synchronized (timeLock) {
            return dayLength;
        }
    }

    public void setDayLength(float length) {
        synchronized (saveLock) {
            this.dayLength = length;
            setDirty(true);
        }
    }

    // Serialize time data
    public void writeTimeValues(Json json) {
        synchronized (timeLock) {
            json.writeValue("worldTimeInMinutes", Double.valueOf(worldTimeInMinutes));
            json.writeValue("playedTime", Long.valueOf(playedTime));
            json.writeValue("dayLength", Float.valueOf(dayLength));

            GameLogger.info("Serializing time values - " +
                "World Time: " + worldTimeInMinutes +
                " Played Time: " + playedTime +
                " Day Length: " + dayLength);
        }
    }

    public Map<Vector2, PlaceableBlock> getPlacedBlocks() {
        return placedBlocks;
    }
    // In PokemonData.java

    public Object getSaveLock() {
        return saveLock;
    }

    public PokemonData getPokemonData() {
        return pokemonData;
    }

    public void setPokemonData(PokemonData pokemonData) {
        this.pokemonData = pokemonData;
    }

    public BlockSaveData getBlockData() {
        return blockData;
    }

    public void setBlockData(BlockSaveData blockData) {
        this.blockData = blockData;
    }

    // Player Data Management
    public void savePlayerData(String username, PlayerData data) {
        synchronized (saveLock) {
            if (username == null || data == null) {
                GameLogger.error("Cannot save null username or data");
                return;
            }

            try {
                PlayerData copy = data.copy(); // Create deep copy
                players.put(username, copy);
                isDirty = true;
                GameLogger.info("Saved player data for: " + username);
            } catch (Exception e) {
                GameLogger.error("Failed to save player data: " + e.getMessage());
            }
        }
    }

    public PlayerData getPlayerData(String username) {
        synchronized (saveLock) {
            PlayerData data = players.get(username);
            return data != null ? data.copy() : null; // Return copy to prevent external modification
        }
    }

    // Modifiable Getter for players
    public HashMap<String, PlayerData> getPlayersMap() {
        return players;
    }

    // Setter for players
    public void setPlayersMap(HashMap<String, PlayerData> players) {
        this.players = players;
    }

    // Configuration Management
    public WorldConfig getConfig() {
        return config;
    }

    public void setConfig(WorldConfig config) {
        synchronized (saveLock) {
            this.config = config;
            isDirty = true;
        }
    }

    // Access Methods
    public String getName() {
        return name;
    }

    public void setName(String name) {
        synchronized (saveLock) {
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("World name cannot be null or empty");
            }
            this.name = name.trim();
            setDirty(true);
        }
    }

    public long getLastPlayed() {
        return lastPlayed;
    }

    public void setLastPlayed(long lastPlayed) {
        synchronized (saveLock) {
            this.lastPlayed = lastPlayed;
            isDirty = true;
        }
    }

    public Map<String, PlayerData> getPlayers() {
        return Collections.unmodifiableMap(players);
    }

    public void setPlayers(HashMap<String, PlayerData> players) {
        this.players = players;
    }

    // Add getter for Pokemon by UUID
    public WildPokemon getWildPokemon(UUID uuid) {
        return wildPokemonMap.get(uuid);
    }

    // Add method to register new Pokemon
    public void registerWildPokemon(WildPokemon pokemon) {
        if (pokemon != null && pokemon.getUuid() != null) {
            wildPokemonMap.put(pokemon.getUuid(), pokemon);
            // Also update pokemonData for persistence
            PokemonData.WildPokemonData data = new PokemonData.WildPokemonData();
            data.setName(pokemon.getName());
            data.setLevel(pokemon.getLevel());
            data.setPosition(new Vector2(pokemon.getX(), pokemon.getY()));
            data.setDirection(pokemon.getDirection());
            data.setMoving(pokemon.isMoving());
            data.setSpawnTime(pokemon.getSpawnTime());
            pokemonData.addWildPokemon(pokemon.getUuid(), data);
        }
    }

    // Add method to remove Pokemon
    public void removeWildPokemon(UUID uuid) {
        wildPokemonMap.remove(uuid);
        if (pokemonData != null) {
            pokemonData.removeWildPokemon(uuid);
        }
    }

    // Add method to get all wild Pokemon
    public Collection<WildPokemon> getAllWildPokemon() {
        return Collections.unmodifiableCollection(wildPokemonMap.values());

    }

    // Now, update the Ga
    public boolean isDirty() {
        return isDirty;
    }

    public void setDirty(boolean dirty) {
        isDirty = dirty;
    }

    public void clearDirtyFlag() {
        isDirty = false;
    }

    // New Getter and Setter for Username
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        synchronized (saveLock) {
            this.username = username;
            isDirty = true;
        }
    }

    /**
     * Converts this WorldData instance to a JSON string.
     *
     * @return JSON string representation of this WorldData
     * @throws RuntimeException if serialization fails
     */
    public void toJson(Json json) {
        synchronized (timeLock) {
            try {
                // Write all fields explicitly
                json.writeObjectStart();

                // Core fields
                json.writeValue("name", getName());
                json.writeValue("lastPlayed", getLastPlayed());

                // Time values - force correct types
                json.writeValue("worldTimeInMinutes", Double.valueOf(worldTimeInMinutes));
                json.writeValue("playedTime", Long.valueOf(playedTime));
                json.writeValue("dayLength", Float.valueOf(dayLength));

                // Write other fields
                json.writeValue("players", getPlayersMap());
                json.writeValue("pokemonData", getPokemonData());
                json.writeValue("blockData", getBlockData());
                json.writeValue("config", getConfig());

                if (getUsername() != null) {
                    json.writeValue("username", getUsername());
                }

                json.writeObjectEnd();

                GameLogger.info("Successfully serialized WorldData with time values:" +
                    "\nWorld Time: " + worldTimeInMinutes +
                    "\nPlayed Time: " + playedTime +
                    "\nDay Length: " + dayLength);

            } catch (Exception e) {
                GameLogger.error("Error serializing WorldData: " + e.getMessage());
                throw new RuntimeException("Failed to serialize WorldData", e);
            }
        }
    }

    @Override
    public String toString() {
        return "WorldData{" +
            "name='" + name + '\'' +
            ", players=" + players.size() +
            ", lastPlayed=" + lastPlayed +
            ", username='" + username + '\'' +
            '}';
    }

    // World Configuration Class
    public static class WorldConfig {
        private long seed;
        private float treeSpawnRate = 0.15f;
        private float pokemonSpawnRate = 0.05f;

        public WorldConfig() {
        }

        public WorldConfig(long seed) {
            this.seed = seed;
        }

        // Getters and Setters
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

    // Add getters/setters

    // Add serialization-friendly data classes
    public static class ChunkData implements Serializable {
        public int[][] tileData;

        public String primaryBiomeType;
        public Map<String, Float> biomeTransitions;
        // Add other chunk data
    }

    public static class WorldObjectData implements Serializable {
        public String type;
        public float x, y;
        public String id;
        // Add other object properties
    }
}

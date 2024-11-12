// In WorldData.java
package io.github.pokemeetup.system.data;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Json;
import io.github.pokemeetup.pokemon.WildPokemon;
import io.github.pokemeetup.system.gameplay.overworld.Chunk;
import io.github.pokemeetup.system.gameplay.overworld.WorldObject;
import io.github.pokemeetup.system.gameplay.overworld.multiworld.WorldManager;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.storage.JsonConfig;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WorldData {// In WorldData.java

    private final Object timeLock = new Object();
    private final Object saveLock = new Object();
    private final Map<UUID, WildPokemon> wildPokemonMap = new ConcurrentHashMap<>();
    private double worldTimeInMinutes = 480.0; // 8:00 AM default
    private long playedTime = 0L;
    private float dayLength = 10.0f;
    private PokemonData pokemonData;  // Keep existing field
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
    // Ensure that this constructor initializes all necessary fields
    public WorldData(String name) {
        this();
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("World name cannot be null or empty");
        }
        this.name = name.trim();
    }
    // The default constructor should initialize collections
    public WorldData() {
        this.players = new HashMap<>();
        this.pokemonData = new PokemonData();
        this.lastPlayed = System.currentTimeMillis();
        // Initialize other collections if needed
        this.chunks = new HashMap<>();
        this.chunkObjects = new HashMap<>();
    }
    public WorldData(String name, long lastPlayed, WorldConfig config) {
        this();
        this.name = name;
        this.pokemonData = new PokemonData();
        this.lastPlayed = lastPlayed;
        this.config = config;
    }
    // New constructor to include username
    public WorldData(String name, long lastPlayed, WorldConfig config, String username) {
        this(name, lastPlayed, config);
        this.username = username;
    }

    public static WorldData fromJson(String jsonStr) {
        try {
            Json json = JsonConfig.getInstance();
            return json.fromJson(WorldData.class, jsonStr);
        } catch (Exception e) {
            GameLogger.error("Failed to parse WorldData from JSON: " + e.getMessage());
            return null;
        }
    }

    public double getWorldTimeInMinutes() {
        synchronized (timeLock) {
            return worldTimeInMinutes;
        }
    }

    public void setWorldTimeInMinutes(double time) {
        synchronized (timeLock) {
            this.worldTimeInMinutes = time;
            GameLogger.info("Set world time to: " + time);
        }
    }

    public long getPlayedTime() {
        synchronized (timeLock) {
            return playedTime;
        }
    }

    public void setPlayedTime(long time) {
        synchronized (timeLock) {
            this.playedTime = time;
            GameLogger.info("Set played time to: " + time);
        }
    }

    public void validateAndRepair() {
        synchronized (timeLock) {
            // Validate time values
            if (worldTimeInMinutes < 0 || worldTimeInMinutes >= 24 * 60) {
                GameLogger.error("Repairing invalid world time: " + worldTimeInMinutes);
                worldTimeInMinutes = 480.0; // 8:00 AM
            }

            if (dayLength <= 0) {
                GameLogger.error("Repairing invalid day length: " + dayLength);
                dayLength = 10.0f;
            }

            if (playedTime < 0) {
                GameLogger.error("Repairing invalid played time: " + playedTime);
                playedTime = 0;
            }
        }

        // Validate players data
        if (players != null) {
            for (Map.Entry<String, PlayerData> entry : players.entrySet()) {
                PlayerData playerData = entry.getValue();
                if (playerData.getInventoryItems() == null) {
                    playerData.setInventoryItems(new ArrayList<>());
                }

                // Validate each inventory item
                for (int i = 0; i < playerData.getInventoryItems().size(); i++) {
                    ItemData item = playerData.getInventoryItems().get(i);
                    if (item != null && item.getUuid() == null) {
                        item.setUuid(UUID.randomUUID());
                        GameLogger.info("Generated new UUID for item: " + item.getItemId());
                    }
                }
            }
        }
    }

    public void validateAndRepairWorld() {
        if (this.pokemonData == null) {
            this.pokemonData = new PokemonData();
            setDirty(true);
        }

        // Validate Pokemon in player data
        if (players != null) {
            for (PlayerData player : players.values()) {
                if (player.getPartyPokemon() != null) {
                    List<PokemonData> validPokemon = new ArrayList<>();
                    for (PokemonData pokemon : player.getPartyPokemon()) {
                        if (pokemon != null ) {
                            validPokemon.add(pokemon);
                            setDirty(true);
                        }
                    }
                    if (!validPokemon.isEmpty()) {
                        player.setPartyPokemon(validPokemon);
                    }
                }
            }
        }
    }

    public void save() {
        synchronized (saveLock) {
            try {
                // Log time values before save
                GameLogger.info("Saving world data - Time: " + worldTimeInMinutes +
                    " Played Time: " + playedTime +
                    " Day Length: " + dayLength);

                // Mark as dirty to ensure save
                setDirty(true);

                // Use WorldManager to handle the actual save
                WorldManager worldManager = WorldManager.getInstance(null, true);
                worldManager.saveWorld(this);

                GameLogger.info("Successfully saved world: " + name);

            } catch (Exception e) {
                GameLogger.error("Failed to save world: " + name + " - " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public void save(boolean createBackup) {
        synchronized (saveLock) {
            try {
                validateAndRepairWorld();
                if (createBackup) {
                    // Create timestamp for backup
                    String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                    String backupName = name + "_backup_" + timestamp;

                    // Create backup WorldData
                    WorldData backup = new WorldData(backupName);
                    backup.setConfig(this.config);
                    backup.setWorldTimeInMinutes(this.worldTimeInMinutes);
                    backup.setPlayedTime(this.playedTime);
                    backup.setDayLength(this.dayLength);
                    backup.setPlayersMap(new HashMap<>(this.players));

                    // Save backup
                    WorldManager.getInstance(null, true).saveWorld(backup);
                    GameLogger.info("Created backup of world: " + name);
                }

                // Save main world
                save();

            } catch (Exception e) {
                GameLogger.error("Failed to save world with backup: " + name + " - " + e.getMessage());
                e.printStackTrace();
            }
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

    public void updateTime(float deltaTime) {
        synchronized (timeLock) {
            long deltaMillis = (long) (deltaTime * 1000);
            playedTime += deltaMillis;
            double gameMinutesPerSecond = (24 * 60.0) / (dayLength * 60.0);
            double timeToAdd = deltaTime * gameMinutesPerSecond;

            worldTimeInMinutes = (worldTimeInMinutes + timeToAdd) % (24 * 60);


        }
    }

    public float getDayLength() {
        return dayLength;
    }

    public void setDayLength(float dayLength) {
        this.dayLength = dayLength;
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
            return data != null ? data.copy() : null;
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

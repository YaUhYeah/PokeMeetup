package io.github.pokemeetup.utils.storage;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import io.github.pokemeetup.pokemon.Pokemon;
import io.github.pokemeetup.pokemon.attacks.LearnableMove;
import io.github.pokemeetup.pokemon.attacks.Move;
import io.github.pokemeetup.pokemon.data.PokemonDatabase;
import io.github.pokemeetup.system.data.*;
import io.github.pokemeetup.system.gameplay.inventory.Inventory;
import io.github.pokemeetup.system.gameplay.inventory.Item;
import io.github.pokemeetup.system.gameplay.inventory.ItemManager;
import io.github.pokemeetup.utils.GameLogger;

import java.util.*;

public class JsonConfig {
    private static final String SINGLE_PLAYER_DIR = "worlds/singleplayer/";
    private static Json instance;

    public static WorldData loadWorldData(String worldName) {
        try {
            FileHandle worldDir = Gdx.files.local(SINGLE_PLAYER_DIR + worldName);
            FileHandle worldFile = worldDir.child("world.json");

            if (!worldFile.exists()) {
                GameLogger.error("World file not found: " + worldFile.path());
                return null;
            }

            String jsonContent = worldFile.readString();
            Json json = getInstance();
            WorldData worldData = json.fromJson(WorldData.class, jsonContent);

            // Log the loaded time values
            GameLogger.info(String.format("Loaded time values - Time: %.2f, Played: %d, DayLength: %.2f",
                worldData.getWorldTimeInMinutes(),
                worldData.getPlayedTime(),
                worldData.getDayLength()));

            return worldData;

        } catch (Exception e) {
            GameLogger.error("Error loading world data: " + e.getMessage());
            return null;
        }
    }

    public static synchronized Json getInstance() {
        if (instance == null) {
            instance = new Json();
            instance.setOutputType(JsonWriter.OutputType.json);
            setupSerializers(instance);
        }
        return instance;
    }


    private static void setupSerializers(Json json) {
        // World data serializer
        json.setSerializer(WorldData.class, new Json.Serializer<WorldData>() {
            @Override
            public void write(Json json, WorldData world, Class knownType) {
                json.writeObjectStart();
                synchronized (world.getTimeLock()) {
                    json.writeValue("worldTimeInMinutes", Double.valueOf(world.getWorldTimeInMinutes()));
                    json.writeValue("playedTime", Long.valueOf(world.getPlayedTime()));
                    json.writeValue("dayLength", Float.valueOf(world.getDayLength()));
                }
                json.writeValue("name", world.getName());
                json.writeValue("lastPlayed", world.getLastPlayed());
                json.writeValue("config", world.getConfig());
                json.writeValue("players", world.getPlayersMap());
                json.writeValue("pokemonData", world.getPokemonData());
                json.writeValue("blockData", world.getBlockData());
                json.writeObjectEnd();
            }

            @Override
            public WorldData read(Json json, JsonValue jsonData, Class type) {
                try {
                    WorldData world = new WorldData();

                    // Handle time values with proper type conversion
                    JsonValue timeValue = jsonData.get("worldTimeInMinutes");
                    if (timeValue != null) {
                        if (timeValue.isDouble()) {
                            world.setWorldTimeInMinutes(timeValue.asDouble());
                        } else if (timeValue.isNumber()) {
                            world.setWorldTimeInMinutes((double) timeValue.asLong());
                        } else {
                            world.setWorldTimeInMinutes(480.0); // Default to 8:00 AM
                        }
                    }

                    JsonValue playedValue = jsonData.get("playedTime");
                    if (playedValue != null && playedValue.isNumber()) {
                        world.setPlayedTime(playedValue.asLong());
                    }

                    JsonValue dayLengthValue = jsonData.get("dayLength");
                    if (dayLengthValue != null && dayLengthValue.isNumber()) {
                        world.setDayLength(dayLengthValue.asFloat());
                    }

                    world.setName(jsonData.getString("name", ""));
                    world.setLastPlayed(jsonData.getLong("lastPlayed", System.currentTimeMillis()));

                    // Handle config with validation
                    WorldData.WorldConfig config = json.readValue(WorldData.WorldConfig.class,
                        jsonData.get("config"));
                    if (config == null) {
                        config = new WorldData.WorldConfig(System.currentTimeMillis());
                        config.setTreeSpawnRate(0.15f);
                        config.setPokemonSpawnRate(0.05f);
                    }
                    world.setConfig(config);

                    // Handle players map with validation
                    JsonValue playersObject = jsonData.get("players");
                    if (playersObject != null && playersObject.isObject()) {
                        HashMap<String, PlayerData> players = new HashMap<>();
                        for (JsonValue playerEntry = playersObject.child;
                             playerEntry != null;
                             playerEntry = playerEntry.next) {

                            String username = playerEntry.name;
                            if (username != null && !username.trim().isEmpty()) {
                                PlayerData playerData = json.readValue(PlayerData.class, playerEntry);
                                if (playerData != null) {
                                    // Validate inventory
                                    validatePlayerInventory(playerData);
                                    // Validate Pokemon data
                                    validatePlayerPokemon(playerData);
                                    players.put(username, playerData);
                                }
                            }
                        }
                        world.setPlayersMap(players);
                    }

                    // Read pokemon data with validation
                    PokemonData pokemonData = json.readValue(PokemonData.class,
                        jsonData.get("pokemonData"));
                    if (pokemonData == null) {
                        pokemonData = new PokemonData();
                    }
                    world.setPokemonData(pokemonData);

                    // Read block data with validation
                    BlockSaveData blockData = json.readValue(BlockSaveData.class,
                        jsonData.get("blockData"));
                    world.setBlockData(blockData);

                    return world;

                } catch (Exception e) {
                    GameLogger.error("Failed to deserialize WorldData: " + e.getMessage());
                    throw new RuntimeException("WorldData deserialization failed", e);
                }
            }
        });

        // PlayerData serializer with proper inventory handling
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

                // Write inventory items with validation
                json.writeArrayStart("inventoryItems");
                if (playerData.getInventoryItems() != null) {
                    for (ItemData item : playerData.getInventoryItems()) {
                        if (item != null && item.isValid()) {
                            json.writeValue(item);
                        } else {
                            json.writeValue(null);
                        }
                    }
                }
                json.writeArrayEnd();

                // Write Pokemon party with validation
                json.writeArrayStart("partyPokemon");
                if (playerData.getPartyPokemon() != null) {
                    for (PokemonData pokemon : playerData.getPartyPokemon()) {
                        json.writeValue(pokemon);
                    }
                }
                json.writeArrayEnd();

                json.writeObjectEnd();
            }

            @Override
            public PlayerData read(Json json, JsonValue jsonData, Class type) {
                try {
                    PlayerData playerData = new PlayerData();
                    playerData.setUsername(jsonData.getString("username", "Player"));
                    playerData.setX(jsonData.getFloat("x", 0f));
                    playerData.setY(jsonData.getFloat("y", 0f));
                    playerData.setDirection(jsonData.getString("direction", "down"));
                    playerData.setMoving(jsonData.getBoolean("isMoving", false));
                    playerData.setWantsToRun(jsonData.getBoolean("wantsToRun", false));

                    // Read and validate inventory

                    List<ItemData> inventory = new ArrayList<>(Inventory.INVENTORY_SIZE);
                    for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
                        inventory.add(null);
                    }

                    JsonValue inventoryArray = jsonData.get("inventoryItems");
                    if (inventoryArray != null && inventoryArray.isArray()) {
                        int index = 0;
                        for (JsonValue itemValue = inventoryArray.child;
                             itemValue != null && index < Inventory.INVENTORY_SIZE;
                             itemValue = itemValue.next, index++) {

                            ItemData item = json.readValue(ItemData.class, itemValue);
                            if (item != null && item.isValid()) {
                                if (item.getUuid() == null) {
                                    item.setUuid(UUID.randomUUID());
                                }
                                inventory.set(index, item);
                            }
                        }
                    }
                    playerData.setInventoryItems(inventory);


                    // Read and validate Pokemon party
                    List<PokemonData> party = new ArrayList<>(6);
                    for (int i = 0; i < 6; i++) {
                        party.add(null);
                    }

                    JsonValue partyArray = jsonData.get("partyPokemon");
                    if (partyArray != null && partyArray.isArray()) {
                        int index = 0;
                        for (JsonValue pokemonValue = partyArray.child;
                             pokemonValue != null && index < 6;
                             pokemonValue = pokemonValue.next, index++) {

                            PokemonData pokemon = json.readValue(PokemonData.class, pokemonValue);
                            if (pokemon != null && pokemon.getName() != null) {
                                if (pokemon.getUuid() == null) {
                                    pokemon.setUuid(UUID.randomUUID());
                                }
                                party.set(index, pokemon);
                            }
                        }
                    }
                    playerData.setPartyPokemon(party);
                    return playerData;

                } catch (Exception e) {
                    GameLogger.error("Failed to deserialize PlayerData: " + e.getMessage());
                    throw new RuntimeException("PlayerData deserialization failed", e);
                }
            }
        });
        json.setSerializer(ItemData.class, new Json.Serializer<ItemData>() {
            @Override
            public void write(Json json, ItemData itemData, Class knownType) {
                if (itemData == null) {
                    json.writeValue(null);
                    return;
                }

                try {
                    json.writeObjectStart();
                    json.writeValue("itemId", itemData.getItemId());
                    json.writeValue("count", itemData.getCount());
                    json.writeValue("uuid", itemData.getUuid() != null ?
                        itemData.getUuid().toString() : UUID.randomUUID().toString());
                    json.writeObjectEnd();
                } catch (Exception e) {
                    GameLogger.error("Error serializing ItemData: " + e.getMessage());
                    throw e;
                }
            }

            @Override
            public ItemData read(Json json, JsonValue jsonData, Class type) {
                if (jsonData == null || jsonData.isNull()) {
                    return null;
                }

                try {
                    ItemData itemData = new ItemData();

                    // Read and validate itemId
                    String itemId = jsonData.getString("itemId", null);
                    if (itemId == null || itemId.trim().isEmpty()) {
                        return null;
                    }
                    itemData.setItemId(itemId);

                    // Validate the item exists in ItemManager
                    if (!ItemManager.isInitialized() || ItemManager.getItem(itemId) == null) {
                        GameLogger.error("Invalid item ID during deserialization: " + itemId);
                        return null;
                    }

                    // Read count with validation
                    int count = jsonData.getInt("count", 0);
                    if (count <= 0 || count > Item.MAX_STACK_SIZE) {
                        GameLogger.error("Invalid item count: " + count);
                        return null;
                    }
                    itemData.setCount(count);

                    // Handle UUID
                    String uuidStr = jsonData.getString("uuid", null);
                    UUID uuid = null;
                    try {
                        uuid = uuidStr != null ? UUID.fromString(uuidStr) : UUID.randomUUID();
                    } catch (IllegalArgumentException e) {
                        uuid = UUID.randomUUID();
                        GameLogger.error("Invalid UUID format, generated new: " + uuid);
                    }
                    itemData.setUuid(uuid);

                    return itemData;

                } catch (Exception e) {
                    GameLogger.error("Error deserializing ItemData: " + e.getMessage());
                    return null;
                }
            }
        });json.setSerializer(PokemonData.class, new Json.Serializer<PokemonData>() {
            @Override
            public void write(Json json, PokemonData pokemonData, Class knownType) {
                if (pokemonData == null) {
                    json.writeValue(null);
                    return;
                }

                try {
                    json.writeObjectStart();

                    // Basic Pokemon data
                    json.writeValue("name", pokemonData.getName());
                    json.writeValue("level", pokemonData.getLevel());

                    // Initialize stats if null
                    if (pokemonData.getStats() == null) {
                        pokemonData.setStats(new PokemonData.Stats());
                        GameLogger.info("Initialized null stats for Pokemon: " + pokemonData.getName());
                    }

                    // Write current HP and stats
                    json.writeValue("currentHp", pokemonData.getBaseHp());

                    // Stats object
                    json.writeObjectStart("stats");
                    json.writeValue("hp", pokemonData.getStats().getHp());
                    json.writeValue("attack", pokemonData.getStats().getAttack());
                    json.writeValue("defense", pokemonData.getStats().getDefense());
                    json.writeValue("specialAttack", pokemonData.getStats().getSpecialAttack());
                    json.writeValue("specialDefense", pokemonData.getStats().getSpecialDefense());
                    json.writeValue("speed", pokemonData.getStats().getSpeed());
                    json.writeObjectEnd();

                    // Types
                    json.writeValue("primaryType", pokemonData.getPrimaryType() != null ?
                        pokemonData.getPrimaryType().name() : "NORMAL");
                    if (pokemonData.getSecondaryType() != null) {
                        json.writeValue("secondaryType", pokemonData.getSecondaryType().name());
                    }

                    // Write UUID
                    json.writeValue("uuid", pokemonData.getUuid() != null ?
                        pokemonData.getUuid().toString() : UUID.randomUUID().toString());

                    // Moves
                    json.writeArrayStart("moves");
                    if (pokemonData.getMoves() != null) {
                        for (PokemonData.MoveData move : pokemonData.getMoves()) {
                            if (move != null) {
                                json.writeObjectStart();
                                json.writeValue("name", move.getName());
                                json.writeValue("pp", move.getPp());
                                json.writeValue("maxPp", move.getMaxPp());
                                json.writeObjectEnd();
                            }
                        }
                    }
                    json.writeArrayEnd();

                    json.writeObjectEnd();

                } catch (Exception e) {
                    GameLogger.error("Error serializing PokemonData: " + e.getMessage());
                    e.printStackTrace();
                    // Create minimal valid representation rather than throwing
                    try {
                        json.writeObjectStart();
                        json.writeValue("name", pokemonData.getName());
                        json.writeValue("level", 1);
                        json.writeObjectStart("stats");
                        json.writeValue("hp", 1);
                        json.writeValue("attack", 1);
                        json.writeValue("defense", 1);
                        json.writeValue("specialAttack", 1);
                        json.writeValue("specialDefense", 1);
                        json.writeValue("speed", 1);
                        json.writeObjectEnd();
                        json.writeValue("primaryType", "NORMAL");
                        json.writeValue("uuid", UUID.randomUUID().toString());
                        json.writeArrayStart("moves");
                        json.writeArrayEnd();
                        json.writeObjectEnd();
                    } catch (Exception inner) {
                        GameLogger.error("Failed to write fallback PokemonData: " + inner.getMessage());
                        throw inner;
                    }
                }
            }

            @Override
            public PokemonData read(Json json, JsonValue jsonData, Class type) {
                if (jsonData == null || jsonData.isNull()) {
                    return null;
                }

                try {
                    PokemonData pokemon = new PokemonData();
                    pokemon.setName(jsonData.getString("name")); // Default to BULBASAUR if no name
                    pokemon.setLevel(jsonData.getInt("level", 1));

                    // Initialize Stats - Always create a new Stats object
                    PokemonData.Stats stats = new PokemonData.Stats();
                    JsonValue statsData = jsonData.get("stats");
                    if (statsData != null) {
                        stats.setHp(statsData.getInt("hp", 1));
                        stats.setAttack(statsData.getInt("attack", 1));
                        stats.setDefense(statsData.getInt("defense", 1));
                        stats.setSpecialAttack(statsData.getInt("specialAttack", 1));
                        stats.setSpecialDefense(statsData.getInt("specialDefense", 1));
                        stats.setSpeed(statsData.getInt("speed", 1));
                    } else {
                        // Set default values if stats are missing
                        stats.setHp(1);
                        stats.setAttack(1);
                        stats.setDefense(1);
                        stats.setSpecialAttack(1);
                        stats.setSpecialDefense(1);
                        stats.setSpeed(1);
                    }
                    pokemon.setStats(stats); // Always set stats to avoid null

                    // Set current HP with validation
                    float currentHp = jsonData.getFloat("currentHp", stats.getHp());
                    pokemon.setBaseHp((int) Math.min(currentHp, stats.getHp()));

                    // Handle Types
                    try {
                        Pokemon.PokemonType primaryType = Pokemon.PokemonType.valueOf(
                            jsonData.getString("primaryType", "NORMAL")
                        );
                        pokemon.setPrimaryType(primaryType);

                        if (jsonData.has("secondaryType")) {
                            String secondaryTypeStr = jsonData.getString("secondaryType");
                            if (secondaryTypeStr != null && !secondaryTypeStr.isEmpty()) {
                                Pokemon.PokemonType secondaryType = Pokemon.PokemonType.valueOf(secondaryTypeStr);
                                pokemon.setSecondaryType(secondaryType);
                            }
                        }
                    } catch (IllegalArgumentException e) {
                        GameLogger.error("Invalid Pokemon type, defaulting to NORMAL: " + e.getMessage());
                        pokemon.setPrimaryType(Pokemon.PokemonType.NORMAL);
                    }

                    // Handle UUID
                    try {
                        String uuidStr = jsonData.getString("uuid");
                        pokemon.setUuid(uuidStr != null ? UUID.fromString(uuidStr) : UUID.randomUUID());
                    } catch (Exception e) {
                        pokemon.setUuid(UUID.randomUUID());
                        GameLogger.error("Generated new UUID due to invalid data");
                    }

                    JsonValue movesArray = jsonData.get("moves");
                    if (movesArray != null && movesArray.isArray()) {
                        for (JsonValue moveValue = movesArray.child;
                             moveValue != null;
                             moveValue = moveValue.next) {

                            try {
                                String name = moveValue.getString("name");
                                Pokemon.PokemonType moveType = Pokemon.PokemonType.valueOf(
                                    moveValue.getString("type", "NORMAL")
                                );
                                int power = moveValue.getInt("power", 0);
                                int accuracy = moveValue.getInt("accuracy", 100);
                                int pp = moveValue.getInt("pp", 0);
                                int maxPp = moveValue.getInt("maxPp", 0);
                                boolean isSpecial = moveValue.getBoolean("isSpecial", false);
                                String description = moveValue.getString("description", "");
                                boolean canFlinch = moveValue.getBoolean("canFlinch", false);

                                // Handle move effect
                                PokemonData.MoveEffectData effect = null;
                                JsonValue effectValue = moveValue.get("effect");
                                if (effectValue != null) {
                                    effect = new PokemonData.MoveEffectData(
                                        effectValue.getString("type", ""),
                                        effectValue.getFloat("chance", 0f)
                                    );

                                    // Status effect
                                    if (effectValue.has("status")) {
                                        try {
                                            Pokemon.Status status = Pokemon.Status.valueOf(
                                                effectValue.getString("status")
                                            );
                                            effect.setStatus(status);
                                        } catch (IllegalArgumentException e) {
                                            GameLogger.error("Invalid status in move effect: " + e.getMessage());
                                        }
                                    }

                                    // Stat changes
                                    JsonValue statChanges = effectValue.get("statChanges");
                                    if (statChanges != null) {
                                        Map<String, Integer> changes = new HashMap<>();
                                        for (JsonValue stat = statChanges.child;
                                             stat != null;
                                             stat = stat.next) {
                                            changes.put(stat.name, stat.asInt());
                                        }
                                        effect.setStatChanges(changes);
                                    }
                                }

                                // Create move data with constructor
                                PokemonData.MoveData move = new PokemonData.MoveData(
                                    name,
                                    moveType,
                                    power,
                                    accuracy,
                                    pp,
                                    maxPp,
                                    isSpecial,
                                    description,
                                    effect,
                                    canFlinch
                                );

                                pokemon.getMoves().add(move);

                            } catch (Exception e) {
                                GameLogger.error("Error loading move data: " + e.getMessage());

                                // Try to create a basic move using the database as fallback
                                String moveName = moveValue.getString("name", null);
                                if (moveName != null) {
                                    Move baseMove = PokemonDatabase.getMoveByName(moveName);
                                    if (baseMove != null) {
                                        pokemon.getMoves().add(PokemonData.MoveData.fromMove(baseMove));
                                    }
                                }
                            }
                        }
                    }

                    // Ensure at least one move exists
                    if (pokemon.getMoves().isEmpty()) {
                        Move tackle = PokemonDatabase.getMoveByName("Tackle");
                        if (tackle != null) {
                            pokemon.getMoves().add(PokemonData.MoveData.fromMove(tackle));
                        }
                    }

                    return pokemon;
                } catch (Exception e) {
                    GameLogger.error("Failed to deserialize PokemonData: " + e.getMessage());
                    e.printStackTrace();
                    // Return minimal valid Pokemon rather than null
                    PokemonData fallback = new PokemonData();
                    fallback.setName("BULBASAUR");
                    fallback.setLevel(1);
                    fallback.setStats(new PokemonData.Stats());
                    fallback.setPrimaryType(Pokemon.PokemonType.NORMAL);
                    fallback.setUuid(UUID.randomUUID());
                    return fallback;
                }
            }
        });
    }
    private static void validatePlayerInventory(PlayerData playerData) {
        if (playerData.getInventoryItems() == null) {
            List<ItemData> items = new ArrayList<>(Inventory.INVENTORY_SIZE);
            for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
                items.add(null);
            }
            playerData.setInventoryItems(items);
        }
    }

    private static void validatePlayerPokemon(PlayerData playerData) {
        if (playerData.getPartyPokemon() == null) {
            List<PokemonData> pokemon = new ArrayList<>(6);
            for (int i = 0; i < 6; i++) {
                pokemon.add(null);
            }
            playerData.setPartyPokemon(pokemon);
        }
    }




    public static void saveWorldDataWithPlayer(WorldData worldData, PlayerData updatedPlayerData) {
        if (worldData != null && updatedPlayerData != null) {
            worldData.savePlayerData(updatedPlayerData.getUsername(), updatedPlayerData);
        }

        try {
            assert worldData != null;
            String worldPath = SINGLE_PLAYER_DIR + worldData.getName();
            FileHandle worldDir = Gdx.files.local(worldPath);
            worldDir.mkdirs();
            FileHandle file = worldDir.child("world.json");
            Json json = getInstance();
            String jsonText = json.toJson(worldData);
            file.writeString(jsonText, false);
            GameLogger.info("Successfully saved WorldData to " + file.path());
        } catch (Exception e) {
            GameLogger.error("Failed to save WorldData: " + e.getMessage());
        }
    }

    private static void applyInventoryData(PlayerData player, List<ItemData> items) {
        // Validate items before applying
        List<ItemData> validatedItems = new ArrayList<>(Inventory.INVENTORY_SIZE);
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            ItemData item = i < items.size() ? items.get(i) : null;
            if (item != null && item.isValid() && ItemManager.getItem(item.getItemId()) != null) {
                if (item.getUuid() == null) {
                    item.setUuid(UUID.randomUUID());
                }
                validatedItems.add(item);
            } else {
                validatedItems.add(null);
            }
        }
        player.setInventoryItems(validatedItems);
        player.validateInventory();
    }

    /**
     * Validates and normalizes inventory data during serialization/deserialization
     */
    private static List<ItemData> normalizeInventoryItems(List<ItemData> items) {
        List<ItemData> normalized = new ArrayList<>(Inventory.INVENTORY_SIZE);

        // Initialize all slots to null first
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            normalized.add(null);
        }

        // Copy valid items to the correct slots
        if (items != null) {
            for (int i = 0; i < Math.min(items.size(), Inventory.INVENTORY_SIZE); i++) {
                ItemData item = items.get(i);
                if (isValidItem(item)) {
                    // Ensure UUID exists
                    if (item.getUuid() == null) {
                        item.setUuid(UUID.randomUUID());
                    }
                    normalized.set(i, item.copy());  // Make a defensive copy
                }
            }
        }

        return normalized;
    }

    /**
     * Validates an individual item
     */
    private static boolean isValidItem(ItemData item) {
        return item != null &&
            item.getItemId() != null &&
            !item.getItemId().isEmpty() &&
            item.getCount() > 0 &&
            ItemManager.getItem(item.getItemId()) != null;
    }
}


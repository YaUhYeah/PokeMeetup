package io.github.pokemeetup.utils.storage;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import io.github.pokemeetup.pokemon.Pokemon;
import io.github.pokemeetup.pokemon.PokemonParty;
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
        // World data serializer with proper null handling
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
                WorldData world = new WorldData();
                // Safe parsing with defaults
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

                world.setPlayedTime(jsonData.getLong("playedTime", 0));
                world.setDayLength(jsonData.getFloat("dayLength", 10.0f));
                world.setName(jsonData.getString("name", ""));
                world.setLastPlayed(jsonData.getLong("lastPlayed", System.currentTimeMillis()));

                // Handle config with validation
                WorldData.WorldConfig config = json.readValue(WorldData.WorldConfig.class, jsonData.get("config"));
                if (config == null) {
                    config = new WorldData.WorldConfig(System.currentTimeMillis());
                }
                world.setConfig(config);

                // Safely handle player data map
                JsonValue playersObj = jsonData.get("players");
                if (playersObj != null && playersObj.isObject()) {
                    HashMap<String, PlayerData> players = new HashMap<>();
                    for (JsonValue entry = playersObj.child; entry != null; entry = entry.next) {
                        String username = entry.name;
                        if (username != null && !username.isEmpty()) {
                            PlayerData playerData = json.readValue(PlayerData.class, entry);
                            if (playerData != null) {
                                validatePlayerData(playerData);
                                players.put(username, playerData);
                            }
                        }
                    }
                    world.setPlayersMap(players);
                }

                return world;
            }
        });
        json.setSerializer(ItemData.class, new Json.Serializer<ItemData>() {
            @Override
            public void write(Json json, ItemData item, Class knownType) {
                if (item == null) {
                    json.writeValue(null);
                    return;
                }

                json.writeObjectStart();
                json.writeValue("itemId", item.getItemId());
                json.writeValue("count", item.getCount());
                json.writeValue("uuid", item.getUuid() != null ?
                    item.getUuid().toString() : UUID.randomUUID().toString());
                json.writeObjectEnd();
            }

            @Override
            public ItemData read(Json json, JsonValue jsonData, Class type) {
                if (jsonData == null || jsonData.isNull()) {
                    return null;
                }

                String itemId = jsonData.getString("itemId", null);
                if (itemId == null || !ItemManager.isInitialized() ||
                    ItemManager.getItem(itemId) == null) {
                    return null;
                }

                ItemData item = new ItemData();
                item.setItemId(itemId);
                item.setCount(Math.min(jsonData.getInt("count", 1), Item.MAX_STACK_SIZE));

                try {
                    String uuidStr = jsonData.getString("uuid", null);
                    item.setUuid(uuidStr != null ? UUID.fromString(uuidStr) : UUID.randomUUID());
                } catch (IllegalArgumentException e) {
                    item.setUuid(UUID.randomUUID());
                }

                return item;
            }
        });  // Pokemon party serializer with null slot handling
        json.setSerializer(PokemonData.class, new Json.Serializer<PokemonData>() {
            @Override
            public void write(Json json, PokemonData pokemon, Class knownType) {
                if (pokemon == null) {
                    json.writeValue(null);
                    return;
                }

                json.writeObjectStart();
                json.writeValue("name", pokemon.getName());
                json.writeValue("level", pokemon.getLevel());
                json.writeValue("currentHp", pokemon.getBaseHp());
                json.writeValue("stats", pokemon.getStats());
                json.writeValue("moves", pokemon.getMoves());
                json.writeValue("uuid", pokemon.getUuid() != null ?
                    pokemon.getUuid().toString() : UUID.randomUUID().toString());
                json.writeObjectEnd();
            }

            @Override
            public PokemonData read(Json json, JsonValue jsonData, Class type) {
                if (jsonData == null || jsonData.isNull()) {
                    return null;
                }

                PokemonData pokemon = new PokemonData();
                pokemon.setName(jsonData.getString("name", "BULBASAUR"));
                pokemon.setLevel(jsonData.getInt("level", 1));
                pokemon.setBaseHp(jsonData.getInt("currentHp", pokemon.getStats().getHp()));

                try {
                    String uuidStr = jsonData.getString("uuid", null);
                    pokemon.setUuid(uuidStr != null ? UUID.fromString(uuidStr) : UUID.randomUUID());
                } catch (IllegalArgumentException e) {
                    pokemon.setUuid(UUID.randomUUID());
                }

                // Load and validate moves
                JsonValue movesArray = jsonData.get("moves");
                if (movesArray != null && movesArray.isArray()) {
                    List<PokemonData.MoveData> moves = new ArrayList<>();
                    for (JsonValue moveValue = movesArray.child; moveValue != null; moveValue = moveValue.next) {
                        PokemonData.MoveData move = json.readValue(PokemonData.MoveData.class, moveValue);
                        if (move != null) {
                            moves.add(move);
                        }
                    }
                    pokemon.setMoves(moves);
                }

                return pokemon;
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

    private static void validatePlayerData(PlayerData playerData) {
        // Ensure inventory has proper size with null slots
        if (playerData.getInventoryItems() == null) {
            playerData.setInventoryItems(new ArrayList<>(Collections.nCopies(Inventory.INVENTORY_SIZE, null)));
        } else {
            List<ItemData> items = new ArrayList<>(Collections.nCopies(Inventory.INVENTORY_SIZE, null));
            for (int i = 0; i < Math.min(playerData.getInventoryItems().size(), Inventory.INVENTORY_SIZE); i++) {
                ItemData item = playerData.getInventoryItems().get(i);
                if (item != null && item.isValid()) {
                    items.set(i, item);
                }
            }
            playerData.setInventoryItems(items);
        }

        // Ensure Pokemon party has proper size with null slots
        if (playerData.getPartyPokemon() == null) {
            playerData.setPartyPokemon(new ArrayList<>(Collections.nCopies(PokemonParty.MAX_PARTY_SIZE, null)));
        } else {
            List<PokemonData> party = new ArrayList<>(Collections.nCopies(PokemonParty.MAX_PARTY_SIZE, null));
            for (int i = 0; i < Math.min(playerData.getPartyPokemon().size(), PokemonParty.MAX_PARTY_SIZE); i++) {
                PokemonData pokemon = playerData.getPartyPokemon().get(i);
                if (pokemon != null) {
                    party.set(i, pokemon);
                }
            }
            playerData.setPartyPokemon(party);
        }
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

package io.github.pokemeetup.utils.storage;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import io.github.pokemeetup.pokemon.Pokemon;
import io.github.pokemeetup.pokemon.attacks.LearnableMove;
import io.github.pokemeetup.system.data.*;
import io.github.pokemeetup.utils.GameLogger;

import java.util.*;

/**
 * JsonConfig class handles the serialization and deserialization of various data classes
 * such as PokemonData, PlayerData, WorldData, ItemData, LearnableMove, and BlockSaveData.
 * It utilizes the libGDX Json utility with custom serializers to ensure data integrity.
 */
public class JsonConfig {
    private static final String SINGLE_PLAYER_DIR = "worlds/singleplayer/";
    private static Json instance;

    /**
     * Singleton instance of Json with custom serializers.
     *
     * @return the singleton Json instance
     */
    public static synchronized Json getInstance() {
        if (instance == null) {
            instance = new Json();
            instance.setOutputType(JsonWriter.OutputType.json);
            setupSerializers(instance);
        }
        return instance;
    }

    /**
     * Sets up custom serializers for various classes.
     *
     * @param json the Json instance to configure
     */
    private static void setupSerializers(Json json) {
        // Serializer for UUID
        json.setSerializer(UUID.class, new Json.Serializer<UUID>() {
            @Override
            public void write(Json json, UUID uuid, Class knownType) {
                json.writeValue(uuid != null ? uuid.toString() : null);
            }

            @Override
            public UUID read(Json json, JsonValue jsonData, Class type) {
                if (jsonData == null || jsonData.isNull()) {
                    return null;
                }
                String uuidStr = jsonData.asString();
                if (uuidStr == null || uuidStr.isEmpty()) {
                    return null;
                }
                try {
                    return UUID.fromString(uuidStr);
                } catch (Exception e) {
                    GameLogger.error("Error parsing UUID: " + e.getMessage());
                    return null;
                }
            }
        });

        // Serializer for PokemonData.MoveData
        json.setSerializer(PokemonData.MoveData.class, new Json.Serializer<PokemonData.MoveData>() {
            @Override
            public void write(Json json, PokemonData.MoveData move, Class knownType) {
                if (move == null) {
                    json.writeValue(null);
                    return;
                }

                json.writeObjectStart();
                json.writeValue("name", move.name);
                json.writeValue("type", move.type != null ? move.type.name() : null);
                json.writeValue("power", move.power);
                json.writeValue("accuracy", move.accuracy);
                json.writeValue("pp", move.pp);
                json.writeValue("maxPp", move.maxPp);
                json.writeValue("isSpecial", move.isSpecial);
                json.writeObjectEnd();
            }

            @Override
            public PokemonData.MoveData read(Json json, JsonValue jsonData, Class type) {
                if (jsonData == null || jsonData.isNull()) {
                    return null;
                }

                PokemonData.MoveData move = new PokemonData.MoveData();
                move.name = jsonData.getString("name", "Unknown Move");
                try {
                    String typeStr = jsonData.getString("type", "NORMAL");
                    move.type = typeStr != null ? Pokemon.PokemonType.valueOf(typeStr) : Pokemon.PokemonType.NORMAL;
                } catch (IllegalArgumentException e) {
                    GameLogger.error("Invalid move type for move " + move.name + ", setting to NORMAL");
                    move.type = Pokemon.PokemonType.NORMAL;
                }
                move.power = jsonData.getInt("power", 0);
                move.accuracy = jsonData.getInt("accuracy", 100);
                move.pp = jsonData.getInt("pp", 10);
                move.maxPp = jsonData.getInt("maxPp", move.pp);
                move.isSpecial = jsonData.getBoolean("isSpecial", false);

                return move;
            }
        });

        // Serializer for LearnableMove
        json.setSerializer(LearnableMove.class, new Json.Serializer<LearnableMove>() {
            @Override
            public void write(Json json, LearnableMove move, Class knownType) {
                if (move == null) {
                    json.writeValue(null);
                    return;
                }

                json.writeObjectStart();
                json.writeValue("moveName", move.getMoveName());
                json.writeValue("levelLearned", move.getLevelLearned());
                json.writeValue("isStartingMove", move.isStartingMove());
                json.writeValue("moveType", move.getMoveType() != null ? move.getMoveType().name() : null);
                json.writeValue("power", move.getPower());
                json.writeValue("accuracy", move.getAccuracy());
                json.writeValue("pp", move.getPp());
                json.writeValue("description", move.getDescription());
                json.writeObjectEnd();
            }

            @Override
            public LearnableMove read(Json json, JsonValue jsonData, Class type) {
                if (jsonData == null || jsonData.isNull()) {
                    return null;
                }

                LearnableMove move = new LearnableMove();
                move.setMoveName(jsonData.getString("moveName", "Unknown Move"));
                move.setLevelLearned(jsonData.getInt("levelLearned", 1));
                move.setStartingMove(jsonData.getBoolean("isStartingMove", false));
                try {
                    String typeStr = jsonData.getString("moveType", "NORMAL");
                    move.setMoveType(typeStr != null ? Pokemon.PokemonType.valueOf(typeStr) : Pokemon.PokemonType.NORMAL);
                } catch (IllegalArgumentException e) {
                    GameLogger.error("Invalid moveType for LearnableMove " + move.getMoveName() + ", setting to NORMAL");
                    move.setMoveType(Pokemon.PokemonType.NORMAL);
                }
                move.setPower(jsonData.getInt("power", 0));
                move.setAccuracy(jsonData.getInt("accuracy", 100));
                move.setPp(jsonData.getInt("pp", 10));
                move.setDescription(jsonData.getString("description", "No description available"));

                return move;
            }
        });

        // Serializer for ItemData
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
                json.writeValue("uuid", item.getUuid() != null ? item.getUuid().toString() : null);
                json.writeObjectEnd();
            }

            @Override
            public ItemData read(Json json, JsonValue jsonData, Class type) {
                if (jsonData == null || jsonData.isNull()) {
                    return null;
                }

                String itemId = jsonData.getString("itemId", "UnknownItem");
                int count = jsonData.getInt("count", 1);
                UUID uuid = null;
                try {
                    String uuidStr = jsonData.getString("uuid", null);
                    if (uuidStr != null) {
                        uuid = UUID.fromString(uuidStr);
                    }
                } catch (Exception e) {
                    GameLogger.error("Invalid UUID for ItemData, generating new one");
                }

                ItemData itemData = new ItemData(itemId, count, uuid);
                if (itemData.getUuid() == null) {
                    itemData.setUuid(UUID.randomUUID());
                }
                return itemData;
            }
        });

        // Serializer for PlayerData
        json.setSerializer(PlayerData.class, new Json.Serializer<PlayerData>() {
            @Override
            public void write(Json json, PlayerData player, Class knownType) {
                if (player == null) {
                    json.writeValue(null);
                    return;
                }

                json.writeObjectStart();

                // Core Player Data
                json.writeValue("username", player.getUsername());
                json.writeValue("x", player.getX());
                json.writeValue("y", player.getY());
                json.writeValue("direction", player.getDirection());
                json.writeValue("isMoving", player.isMoving());
                json.writeValue("wantsToRun", player.isWantsToRun());

                // Inventory Items
                json.writeArrayStart("inventoryItems");
                if (player.getInventoryItems() != null) {
                    for (ItemData item : player.getInventoryItems()) {
                        json.writeValue(item);
                    }
                }
                json.writeArrayEnd();

                // Party Pokemon
                json.writeArrayStart("partyPokemon");
                if (player.getPartyPokemon() != null) {
                    for (PokemonData pokemon : player.getPartyPokemon()) {
                        json.writeValue(pokemon);
                    }
                }
                json.writeArrayEnd();

                // Stored Pokemon
                json.writeArrayStart("storedPokemon");
                if (player.getStoredPokemon() != null) {
                    for (PokemonData pokemon : player.getStoredPokemon()) {
                        json.writeValue(pokemon);
                    }
                }
                json.writeArrayEnd();

                json.writeObjectEnd();
            }

            @Override
            public PlayerData read(Json json, JsonValue jsonData, Class type) {
                if (jsonData == null || jsonData.isNull()) {
                    return null;
                }

                PlayerData player = new PlayerData();
                player.setUsername(jsonData.getString("username", "Player"));

                // Core Player Data
                player.setX(jsonData.getFloat("x", 0f));
                player.setY(jsonData.getFloat("y", 0f));
                player.setDirection(jsonData.getString("direction", "down"));
                player.setMoving(jsonData.getBoolean("isMoving", false));
                player.setWantsToRun(jsonData.getBoolean("wantsToRun", false));

                // Inventory Items
                JsonValue inventoryArray = jsonData.get("inventoryItems");
                if (inventoryArray != null && inventoryArray.isArray()) {
                    List<ItemData> inventoryItems = new ArrayList<>();
                    for (JsonValue itemValue = inventoryArray.child; itemValue != null; itemValue = itemValue.next) {
                        ItemData item = json.readValue(ItemData.class, itemValue);
                        inventoryItems.add(item);
                    }
                    player.setInventoryItems(inventoryItems);
                }

                // Party Pokemon
                JsonValue partyArray = jsonData.get("partyPokemon");
                if (partyArray != null && partyArray.isArray()) {
                    List<PokemonData> partyPokemon = new ArrayList<>();
                    for (JsonValue pokemonValue = partyArray.child; pokemonValue != null; pokemonValue = pokemonValue.next) {
                        PokemonData pokemon = json.readValue(PokemonData.class, pokemonValue);
                        partyPokemon.add(pokemon);
                    }
                    player.setPartyPokemon(partyPokemon);
                }

                // Stored Pokemon
                JsonValue storedArray = jsonData.get("storedPokemon");
                if (storedArray != null && storedArray.isArray()) {
                    List<PokemonData> storedPokemon = new ArrayList<>();
                    for (JsonValue pokemonValue = storedArray.child; pokemonValue != null; pokemonValue = pokemonValue.next) {
                        PokemonData pokemon = json.readValue(PokemonData.class, pokemonValue);
                        storedPokemon.add(pokemon);
                    }
                    player.setStoredPokemon(storedPokemon);
                }

                return player;
            }
        });

        // Serializer for WorldData
        json.setSerializer(WorldData.class, new Json.Serializer<WorldData>() {
            @Override
            public void write(Json json, WorldData world, Class knownType) {
                if (world == null) {
                    json.writeValue(null);
                    return;
                }

                try {
                    json.writeObjectStart();

                    // Write time values directly in the serializer
                    world.writeTimeValues(json); // Update internal state
                    json.writeValue("worldTimeInMinutes", world.getWorldTimeInMinutes());
                    json.writeValue("playedTime", world.getPlayedTime());
                    json.writeValue("dayLength", world.getDayLength());

                    // Write other fields
                    json.writeValue("name", world.getName());
                    json.writeValue("lastPlayed", world.getLastPlayed());

                    // Write players map
                    json.writeObjectStart("players");
                    Map<String, PlayerData> players = world.getPlayersMap();
                    if (players != null) {
                        for (Map.Entry<String, PlayerData> entry : players.entrySet()) {
                            if (entry.getKey() != null) {
                                json.writeValue(entry.getKey(), entry.getValue());
                            }
                        }
                    }
                    json.writeObjectEnd();

                    json.writeValue("pokemonData", world.getPokemonData());
                    json.writeValue("blockData", world.getBlockData());
                    json.writeValue("config", world.getConfig());

                    String username = world.getUsername();
                    if (username != null && !username.trim().isEmpty()) {
                        json.writeValue("username", username);
                    }

                    json.writeObjectEnd();

                    GameLogger.info("Successfully wrote world data with time values");

                } catch (Exception e) {
                    GameLogger.error("Error serializing WorldData: " + e.getMessage());
                    e.printStackTrace();
                    throw new RuntimeException("Failed to serialize WorldData", e);
                }
            }

            @Override
            public WorldData read(Json json, JsonValue jsonData, Class type) {
                if (jsonData == null || jsonData.isNull()) {
                    return null;
                }

                try {
                    WorldData world = new WorldData();

                    // Read time values with proper type conversion
                    JsonValue timeValue = jsonData.get("worldTimeInMinutes");
                    if (timeValue != null) {
                        if (timeValue.isDouble()) {
                            world.setWorldTimeInMinutes(timeValue.asDouble());
                        } else if (timeValue.isNumber()) {
                            world.setWorldTimeInMinutes((double)timeValue.asLong());
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

                    // Read basic fields
                    world.setName(jsonData.getString("name", ""));
                    world.setLastPlayed(jsonData.getLong("lastPlayed", System.currentTimeMillis()));

                    // Read players
                    JsonValue playersObject = jsonData.get("players");
                    if (playersObject != null && playersObject.isObject()) {
                        HashMap<String, PlayerData> players = new HashMap<>();
                        for (JsonValue playerEntry = playersObject.child; playerEntry != null; playerEntry = playerEntry.next) {
                            String username = playerEntry.name;
                            if (username != null && !username.trim().isEmpty()) {
                                PlayerData playerData = json.readValue(PlayerData.class, playerEntry);
                                players.put(username, playerData);
                            }
                        }
                        world.setPlayersMap(players);
                    }

                    // Read other data
                    JsonValue pokemonDataValue = jsonData.get("pokemonData");
                    if (pokemonDataValue != null && !pokemonDataValue.isNull()) {
                        PokemonData pokemonData = json.readValue(PokemonData.class, pokemonDataValue);
                        world.setPokemonData(pokemonData);
                    }

                    JsonValue blockDataValue = jsonData.get("blockData");
                    if (blockDataValue != null && !blockDataValue.isNull()) {
                        BlockSaveData blockData = json.readValue(BlockSaveData.class, blockDataValue);
                        world.setBlockData(blockData);
                    }

                    JsonValue configValue = jsonData.get("config");
                    if (configValue != null && !configValue.isNull()) {
                        WorldData.WorldConfig config = json.readValue(WorldData.WorldConfig.class, configValue);
                        world.setConfig(config);
                    }

                    GameLogger.info("Successfully loaded world data with time values - " +
                        "World Time: " + world.getWorldTimeInMinutes() +
                        " Played Time: " + world.getPlayedTime() +
                        " Day Length: " + world.getDayLength());

                    return world;

                } catch (Exception e) {
                    GameLogger.error("Error deserializing WorldData: " + e.getMessage());
                    e.printStackTrace();
                    throw new RuntimeException("Failed to deserialize WorldData", e);
                }
            }
        });
        json.setSerializer(BlockSaveData.class, new Json.Serializer<BlockSaveData>() {
            @Override
            public void write(Json json, BlockSaveData blockData, Class knownType) {
                if (blockData == null) {
                    json.writeValue(null);
                    return;
                }

                json.writeObjectStart();

                // Serialize placedBlocks map
                json.writeObjectStart("placedBlocks");
                if (blockData.getPlacedBlocks() != null) {
                    for (Map.Entry<String, List<BlockSaveData.BlockData>> entry : blockData.getPlacedBlocks().entrySet()) {
                        String chunkKey = entry.getKey();
                        List<BlockSaveData.BlockData> blocks = entry.getValue();

                        json.writeArrayStart(chunkKey);
                        for (BlockSaveData.BlockData block : blocks) {
                            json.writeValue(block);
                        }
                        json.writeArrayEnd();
                    }
                }
                json.writeObjectEnd();

                json.writeObjectEnd();
            }

            @Override
            public BlockSaveData read(Json json, JsonValue jsonData, Class type) {
                if (jsonData == null || jsonData.isNull()) {
                    return null;
                }

                BlockSaveData blockData = new BlockSaveData();

                // Deserialize placedBlocks map
                JsonValue placedBlocksObject = jsonData.get("placedBlocks");
                if (placedBlocksObject != null && placedBlocksObject.isObject()) {
                    for (JsonValue chunkEntry = placedBlocksObject.child; chunkEntry != null; chunkEntry = chunkEntry.next) {
                        String chunkKey = chunkEntry.name;
                        List<BlockSaveData.BlockData> blocks = new ArrayList<>();
                        if (chunkEntry.isArray()) {
                            for (JsonValue blockValue = chunkEntry.child; blockValue != null; blockValue = blockValue.next) {
                                BlockSaveData.BlockData block = json.readValue(BlockSaveData.BlockData.class, blockValue);
                                blocks.add(block);
                            }
                        }
                        blockData.getPlacedBlocks().put(chunkKey, blocks);
                    }
                }

                return blockData;
            }
        });

        // Serializer for BlockSaveData.BlockData
        json.setSerializer(BlockSaveData.BlockData.class, new Json.Serializer<BlockSaveData.BlockData>() {
            @Override
            public void write(Json json, BlockSaveData.BlockData blockData, Class knownType) {
                if (blockData == null) {
                    json.writeValue(null);
                    return;
                }

                json.writeObjectStart();
                json.writeValue("type", blockData.type);
                json.writeValue("x", blockData.x);
                json.writeValue("y", blockData.y);
                json.writeObjectStart("extraData");
                if (blockData.extraData != null) {
                    for (Map.Entry<String, Object> entry : blockData.extraData.entrySet()) {
                        json.writeValue(entry.getKey(), entry.getValue());
                    }
                }
                json.writeObjectEnd();
                json.writeObjectEnd();
            }

            @Override
            public BlockSaveData.BlockData read(Json json, JsonValue jsonData, Class type) {
                if (jsonData == null || jsonData.isNull()) {
                    return null;
                }

                BlockSaveData.BlockData block = new BlockSaveData.BlockData();
                block.type = jsonData.getString("type", "UnknownBlock");
                block.x = jsonData.getInt("x", 0);
                block.y = jsonData.getInt("y", 0);

                JsonValue extraDataValue = jsonData.get("extraData");
                if (extraDataValue != null && extraDataValue.isObject()) {
                    Map<String, Object> extraData = new HashMap<>();
                    for (JsonValue entry = extraDataValue.child; entry != null; entry = entry.next) {
                        extraData.put(entry.name, entry.asString());
                    }
                    block.extraData = extraData;
                } else {
                    block.extraData = new HashMap<>();
                }

                return block;
            }
        });

        // Serializer for PokemonData
        json.setSerializer(PokemonData.class, new Json.Serializer<PokemonData>() {
            @Override
            public void write(Json json, PokemonData pokemon, Class knownType) {
                if (pokemon == null) {
                    json.writeValue(null);
                    return;
                }

                json.writeObjectStart();

                // Write basic info
                json.writeValue("name", pokemon.getName());
                json.writeValue("uuid", pokemon.getUuid() != null ? pokemon.getUuid().toString() : null);
                json.writeValue("level", pokemon.getLevel());
                json.writeValue("nature", pokemon.getNature());

                // Write types
                if (pokemon.getPrimaryType() != null) {
                    json.writeValue("primaryType", pokemon.getPrimaryType().name());
                }
                if (pokemon.getSecondaryType() != null) {
                    json.writeValue("secondaryType", pokemon.getSecondaryType().name());
                }

                // Write base stats
                json.writeValue("baseHp", pokemon.getBaseHp());
                json.writeValue("baseAttack", pokemon.getBaseAttack());
                json.writeValue("baseDefense", pokemon.getBaseDefense());
                json.writeValue("baseSpAtk", pokemon.getBaseSpAtk());
                json.writeValue("baseSpDef", pokemon.getBaseSpDef());
                json.writeValue("baseSpeed", pokemon.getBaseSpeed());

                // Write physical dimensions
                json.writeValue("width", pokemon.getWidth());
                json.writeValue("height", pokemon.getHeight());

                // Write stats object
                json.writeValue("stats", pokemon.getStats());

                // Write moves
                if (pokemon.getMoves() != null) {
                    json.writeArrayStart("moves");
                    for (PokemonData.MoveData move : pokemon.getMoves()) {
                        json.writeValue(move);
                    }
                    json.writeArrayEnd();
                }

                // Write learnable moves
                if (pokemon.getLearnableMoves() != null) {
                    json.writeArrayStart("learnableMoves");
                    for (LearnableMove move : pokemon.getLearnableMoves()) {
                        json.writeValue(move);
                    }
                    json.writeArrayEnd();
                }

                // Write TM moves
                if (pokemon.getTmMoves() != null) {
                    json.writeArrayStart("tmMoves");
                    for (String move : pokemon.getTmMoves()) {
                        json.writeValue(move);
                    }
                    json.writeArrayEnd();
                }

                json.writeObjectEnd();
            }

            @Override
            public PokemonData read(Json json, JsonValue jsonData, Class type) {
                if (jsonData == null || jsonData.isNull()) {
                    return null;
                }

                PokemonData pokemon = new PokemonData();

                // Read basic info
                pokemon.setName(jsonData.getString("name", ""));
                try {
                    String uuidStr = jsonData.getString("uuid", null);
                    if (uuidStr != null) {
                        pokemon.setUuid(UUID.fromString(uuidStr));
                    } else {
                        pokemon.setUuid(UUID.randomUUID());
                    }
                } catch (IllegalArgumentException e) {
                    GameLogger.error("Invalid UUID for PokemonData, generating new one");
                    pokemon.setUuid(UUID.randomUUID());
                }
                pokemon.setLevel(jsonData.getInt("level", 1));
                pokemon.setNature(jsonData.getString("nature", "Hardy"));

                // Read types
                try {
                    String primaryTypeStr = jsonData.getString("primaryType", null);
                    if (primaryTypeStr != null) {
                        pokemon.setPrimaryType(Pokemon.PokemonType.valueOf(primaryTypeStr));
                    }

                    String secondaryTypeStr = jsonData.getString("secondaryType", null);
                    if (secondaryTypeStr != null) {
                        pokemon.setSecondaryType(Pokemon.PokemonType.valueOf(secondaryTypeStr));
                    }
                } catch (IllegalArgumentException e) {
                    GameLogger.error("Error parsing Pokemon type: " + e.getMessage());
                }

                // Read base stats
                pokemon.setBaseHp(jsonData.getInt("baseHp", 0));
                pokemon.setBaseAttack(jsonData.getInt("baseAttack", 0));
                pokemon.setBaseDefense(jsonData.getInt("baseDefense", 0));
                pokemon.setBaseSpAtk(jsonData.getInt("baseSpAtk", 0));
                pokemon.setBaseSpDef(jsonData.getInt("baseSpDef", 0));
                pokemon.setBaseSpeed(jsonData.getInt("baseSpeed", 0));

                // Read physical dimensions
                pokemon.setWidth(jsonData.getFloat("width", 1f));
                pokemon.setHeight(jsonData.getFloat("height", 1f));

                // Read stats object
                JsonValue statsData = jsonData.get("stats");
                if (statsData != null && !statsData.isNull()) {
                    PokemonData.Stats stats = json.readValue(PokemonData.Stats.class, statsData);
                    pokemon.setStats(stats);
                }

                // Read moves
                JsonValue movesArray = jsonData.get("moves");
                if (movesArray != null && movesArray.isArray()) {
                    List<PokemonData.MoveData> moves = new ArrayList<>();
                    for (JsonValue moveData = movesArray.child; moveData != null; moveData = moveData.next) {
                        PokemonData.MoveData move = json.readValue(PokemonData.MoveData.class, moveData);
                        moves.add(move);
                    }
                    pokemon.setMoves(moves);
                }

                // Read learnable moves
                JsonValue learnableMovesArray = jsonData.get("learnableMoves");
                if (learnableMovesArray != null && learnableMovesArray.isArray()) {
                    List<LearnableMove> learnableMoves = new ArrayList<>();
                    for (JsonValue moveData = learnableMovesArray.child; moveData != null; moveData = moveData.next) {
                        LearnableMove move = json.readValue(LearnableMove.class, moveData);
                        learnableMoves.add(move);
                    }
                    pokemon.setLearnableMoves(learnableMoves);
                }

                // Read TM moves
                JsonValue tmMovesArray = jsonData.get("tmMoves");
                if (tmMovesArray != null && tmMovesArray.isArray()) {
                    List<String> tmMoves = new ArrayList<>();
                    for (JsonValue moveData = tmMovesArray.child; moveData != null; moveData = moveData.next) {
                        tmMoves.add(moveData.asString());
                    }
                    pokemon.setTmMoves(tmMoves);
                }

                return pokemon;
            }
        });

        // Serializer for PokemonData.Stats
        json.setSerializer(PokemonData.Stats.class, new Json.Serializer<PokemonData.Stats>() {
            @Override
            public void write(Json json, PokemonData.Stats stats, Class knownType) {
                if (stats == null) {
                    json.writeValue(null);
                    return;
                }

                json.writeObjectStart();
                json.writeValue("hp", stats.hp);
                json.writeValue("attack", stats.attack);
                json.writeValue("defense", stats.defense);
                json.writeValue("specialAttack", stats.specialAttack);
                json.writeValue("specialDefense", stats.specialDefense);
                json.writeValue("speed", stats.speed);
                json.writeValue("ivs", stats.ivs);
                json.writeValue("evs", stats.evs);
                json.writeObjectEnd();
            }

            @Override
            public PokemonData.Stats read(Json json, JsonValue jsonData, Class type) {
                if (jsonData == null || jsonData.isNull()) {
                    return new PokemonData.Stats();
                }

                PokemonData.Stats stats = new PokemonData.Stats();
                stats.hp = jsonData.getInt("hp", 0);
                stats.attack = jsonData.getInt("attack", 0);
                stats.defense = jsonData.getInt("defense", 0);
                stats.specialAttack = jsonData.getInt("specialAttack", 0);
                stats.specialDefense = jsonData.getInt("specialDefense", 0);
                stats.speed = jsonData.getInt("speed", 0);
                stats.ivs = jsonData.get("ivs") != null ? json.readValue(int[].class, jsonData.get("ivs")) : new int[6];
                stats.evs = jsonData.get("evs") != null ? json.readValue(int[].class, jsonData.get("evs")) : new int[6];
                return stats;
            }
        });

        // Serializer for WorldData.WorldConfig
        json.setSerializer(WorldData.WorldConfig.class, new Json.Serializer<WorldData.WorldConfig>() {
            @Override
            public void write(Json json, WorldData.WorldConfig config, Class knownType) {
                if (config == null) {
                    json.writeValue(null);
                    return;
                }

                json.writeObjectStart();
                json.writeValue("seed", config.getSeed());
                json.writeValue("treeSpawnRate", config.getTreeSpawnRate());
                json.writeValue("pokemonSpawnRate", config.getPokemonSpawnRate());
                json.writeObjectEnd();
            }

            @Override
            public WorldData.WorldConfig read(Json json, JsonValue jsonData, Class type) {
                if (jsonData == null || jsonData.isNull()) {
                    return new WorldData.WorldConfig(System.currentTimeMillis());
                }

                WorldData.WorldConfig config = new WorldData.WorldConfig();
                config.setSeed(jsonData.getLong("seed", System.currentTimeMillis()));
                config.setTreeSpawnRate(jsonData.getFloat("treeSpawnRate", 0.15f));
                config.setPokemonSpawnRate(jsonData.getFloat("pokemonSpawnRate", 0.05f));

                return config;
            }
        });
    }

    public static void saveWorldDataWithPlayer(WorldData worldData, PlayerData updatedPlayerData) {
        // Update the player data in worldData
        if (worldData != null && updatedPlayerData != null) {
            worldData.savePlayerData(updatedPlayerData.getUsername(), updatedPlayerData);
        }

        // Serialize the updated WorldData to JSON
        try {
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

    // ... (Other methods remain unchanged)
}

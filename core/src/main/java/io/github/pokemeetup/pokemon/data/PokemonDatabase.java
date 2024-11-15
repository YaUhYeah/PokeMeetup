package io.github.pokemeetup.pokemon.data;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.google.gson.Gson;
import io.github.pokemeetup.FileSystemDelegate;
import io.github.pokemeetup.pokemon.Pokemon;
import io.github.pokemeetup.pokemon.attacks.Move;
import io.github.pokemeetup.pokemon.attacks.MoveLoader;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.storage.GameFileSystem;

import java.io.IOException;
import java.util.*;

public class PokemonDatabase {
    private static final String POKEMON_DATA_FILE = "Data/pokemon.json";  // Capital D
    private static final String MOVE_DATA_FILE = "Data/moves.json";      // Capital D
    private static final Map<String, PokemonTemplate> pokemonTemplates = new HashMap<>();
    private static final Map<String, BaseStats> pokemonStats = new HashMap<>();
    private static boolean isInitialized = false;

    // Map to store all moves loaded from moves.json
    private static Map<String, Move> allMoves = new HashMap<>();

    public static PokemonTemplate getTemplate(String name) {
        if (!isInitialized) {
            initialize();
        }
        return pokemonTemplates.get(name);
    }


        public static void initialize() {
            if (isInitialized) {
                return;
            }

            try {
                GameLogger.info("Initializing Pokemon Database...");
                FileSystemDelegate delegate = GameFileSystem.getInstance().getDelegate();

                // First load moves
                try {
                    String movesJson = delegate.readString(MOVE_DATA_FILE);
                    GameLogger.info("Loaded moves.json content (length: " + movesJson.length() + ")");

                    // Load moves using updated MoveLoader
                    allMoves.putAll(MoveLoader.loadMovesFromJson(movesJson));
                    GameLogger.info("Successfully loaded " + allMoves.size() + " moves");

                    // Log first few moves for verification
                    int count = 0;
                    for (Map.Entry<String, Move> entry : allMoves.entrySet()) {
                        if (count++ < 3) {
                            GameLogger.info("Loaded move: " + entry.getKey() + " (" +
                                entry.getValue().getType() + ", Power: " +
                                entry.getValue().getPower() + ")");
                        }
                    }
                } catch (Exception e) {
                    GameLogger.error("Failed to load moves: " + e.getMessage());
                    throw e;
                }

                // Then load Pokemon data
                try {
                    String pokemonJson = delegate.readString(POKEMON_DATA_FILE);
                    GameLogger.info("Loaded pokemon.json content (length: " + pokemonJson.length() + ")");

                    JsonReader reader = new JsonReader();
                    JsonValue root = reader.parse(pokemonJson);
                    JsonValue pokemonArray = root.get("pokemon");

                    if (pokemonArray == null) {
                        throw new RuntimeException("Invalid pokemon.json format - missing 'pokemon' array");
                    }

                    int pokemonCount = 0;
                    for (JsonValue pokemonValue = pokemonArray.child;
                         pokemonValue != null;
                         pokemonValue = pokemonValue.next) {
                        try {
                            String name = pokemonValue.getString("name");
                            if (name == null || name.isEmpty()) {
                                continue;
                            }

                            Pokemon.PokemonType primaryType = Pokemon.PokemonType.valueOf(
                                pokemonValue.getString("primaryType").toUpperCase());
                            Pokemon.PokemonType secondaryType = getSecondaryType(pokemonValue);
                            List<MoveEntry> moves = loadPokemonMoves(pokemonValue.get("moves"));

                            BaseStats stats = new BaseStats(
                                name,
                                pokemonValue.getInt("baseHp"),
                                pokemonValue.getInt("baseAttack"),
                                pokemonValue.getInt("baseDefense"),
                                pokemonValue.getInt("baseSpAtk"),
                                pokemonValue.getInt("baseSpDef"),
                                pokemonValue.getInt("baseSpeed"),
                                primaryType,
                                secondaryType,
                                moves
                            );
                            pokemonStats.put(name, stats);

                            // Create template
                            PokemonTemplate template = new PokemonTemplate();
                            template.name = name;
                            template.primaryType = primaryType;
                            template.secondaryType = secondaryType;
                            template.baseStats = stats;
                            template.moves = moves;
                            template.width = pokemonValue.getFloat("width", 1.0f);
                            template.height = pokemonValue.getFloat("height", 1.0f);

                            pokemonTemplates.put(name, template);
                            pokemonCount++;

                            // Log first few Pokemon for verification
                            if (pokemonCount <= 3) {
                                GameLogger.info("Loaded Pokemon: " + name + " (" +
                                    primaryType + (secondaryType != null ? "/" + secondaryType : "") +
                                    ") with " + moves.size() + " moves");
                            }

                        } catch (Exception e) {
                            GameLogger.error("Error loading Pokemon entry: " + e.getMessage());
                        }
                    }

                    GameLogger.info("Successfully loaded " + pokemonCount + " Pokemon");
                    isInitialized = true;

                } catch (Exception e) {
                    GameLogger.error("Failed to load Pokemon data: " + e.getMessage());
                    throw e;
                }

            } catch (Exception e) {
                GameLogger.error("Pokemon database initialization failed: " + e.getMessage());
                e.printStackTrace();
                throw new RuntimeException("Failed to initialize Pokemon database", e);
            }
        }
    private static void loadAllMoves() {
        try {
            // Assuming moves.json is located in the assets/data directory
            String movesJsonPath = Gdx.files.internal("Data/moves.json").file().getAbsolutePath();
            allMoves = MoveLoader.loadMoves(movesJsonPath);
            GameLogger.info("Loaded " + allMoves.size() + " moves from moves.json");
        } catch (IOException e) {
            GameLogger.error("Error loading moves: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static BaseStats getStats(String name) {
        if (!isInitialized) {
            initialize();
        }

        BaseStats stats = pokemonStats.get(name);
        if (stats == null) {
            GameLogger.error("No stats found for Pokemon: " + name);
            // Return default stats to prevent null pointer exceptions
            return new BaseStats(
                name,
                45,  // Default HP
                45,  // Default Attack
                45,  // Default Defense
                45,  // Default Sp. Attack
                45,  // Default Sp. Defense
                45,  // Default Speed
                Pokemon.PokemonType.NORMAL,  // Default primary type
                null,  // No secondary type
                new ArrayList<>()  // Empty moves list
            );
        }
        return stats;
    }

    private static List<MoveEntry> loadPokemonMoves(JsonValue movesArray) {
        List<MoveEntry> moves = new ArrayList<>();
        if (movesArray != null && movesArray.isArray()) {
            for (JsonValue moveValue = movesArray.child; moveValue != null; moveValue = moveValue.next) {
                try {
                    String moveName = moveValue.getString("name");
                    int level = moveValue.getInt("level");
                    moves.add(new MoveEntry(moveName, level));
                } catch (Exception e) {
                    GameLogger.error("Error loading move: " + e.getMessage());
                }
            }
        }
        return moves;
    }

    private static Pokemon.PokemonType getSecondaryType(JsonValue pokemonValue) {
        try {
            // Check if secondaryType exists and is not empty/null
            if (pokemonValue.has("secondaryType")) {
                String secondaryType = pokemonValue.getString("secondaryType", "").trim();
                if (!secondaryType.isEmpty()) {
                    return Pokemon.PokemonType.valueOf(secondaryType.toUpperCase());
                }
            }
        } catch (Exception e) {
            GameLogger.info("No secondary type for Pokemon: " + pokemonValue.getString("name", "unknown"));
        }
        return null;
    }
    public static Pokemon createPokemon(String name, int level) {
        if (!isInitialized) {
            initialize();
        }

        PokemonTemplate template = pokemonTemplates.get(name);
        if (template == null) {
            GameLogger.error("Pokemon template not found: " + name);
            return null;
        }

        try {
            Pokemon.Builder builder = new Pokemon.Builder(name, level)
                .withType(template.primaryType, template.secondaryType);

            // Calculate stats based on level and base stats
            int hp = calculateStat(template.baseStats.baseHp, level, true);
            int attack = calculateStat(template.baseStats.baseAttack, level, false);
            int defense = calculateStat(template.baseStats.baseDefense, level, false);
            int spAtk = calculateStat(template.baseStats.baseSpAtk, level, false);
            int spDef = calculateStat(template.baseStats.baseSpDef, level, false);
            int speed = calculateStat(template.baseStats.baseSpeed, level, false);

            builder.withStats(hp, attack, defense, spAtk, spDef, speed);

            // Assign moves based on level
            List<Move> startingMoves = getMovesForLevel(template.moves, level);
            builder.withMoves(startingMoves);

            return builder.build();

        } catch (Exception e) {
            GameLogger.error("Error creating Pokemon: " + e.getMessage());
            return null;
        }
    }
    public static List<Move> getMovesForLevel(List<MoveEntry> moveEntries, int level) {
        List<Move> moves = new ArrayList<>();

        try {
            // Create case-insensitive map of moves
            Map<String, Move> moveMap = new HashMap<>();
            for (Map.Entry<String, Move> entry : allMoves.entrySet()) {
                moveMap.put(entry.getKey().toLowerCase(), entry.getValue());
            }

            // Filter moves learned at or before given level
            List<MoveEntry> learnedMoves = new ArrayList<>();
            for (MoveEntry entry : moveEntries) {
                if (entry.level <= level) {
                    learnedMoves.add(entry);
                }
            }

            // Sort by level ascending
            learnedMoves.sort(Comparator.comparingInt(e -> e.level));

            // Get most recent 4 moves
            int movesToAdd = Math.min(learnedMoves.size(), 4);
            for (int i = learnedMoves.size() - movesToAdd; i < learnedMoves.size(); i++) {
                MoveEntry moveEntry = learnedMoves.get(i);
                String moveName = moveEntry.name.toLowerCase(); // Convert to lowercase for comparison

                Move move = moveMap.get(moveName);
                if (move != null) {
                    moves.add(cloneMove(move));
                    GameLogger.info("Added move: " + moveEntry.name + " (Level " + moveEntry.level + ")");
                } else {
                    // Log available moves when not found
                    GameLogger.error("Move not found: " + moveEntry.name);
                    GameLogger.error("Available moves: " + String.join(", ", moveMap.keySet()));
                }
            }

        } catch (Exception e) {
            GameLogger.error("Error loading moves: " + e.getMessage());
            e.printStackTrace();
        }

        return moves;
    }
    public static Move cloneMove(Move move) {
        // Clone the MoveEffect if present
        Move.MoveEffect clonedEffect = null;
        if (move.getEffect() != null) {
            clonedEffect = cloneMoveEffect(move.getEffect());
        }

        return new Move.Builder(move.getName(), move.getType())
            .power(move.getPower())
            .accuracy(move.getAccuracy())
            .pp(move.getPp())
            .special(move.isSpecial())
            .description(move.getDescription())
            .effect(clonedEffect)
            .build();
    }

    private static Move.MoveEffect cloneMoveEffect(Move.MoveEffect effect) {
        Move.MoveEffect clonedEffect = new Move.MoveEffect();
        clonedEffect.setEffectType(effect.getEffectType());
        clonedEffect.setChance(effect.getChance());
        clonedEffect.setAnimation(effect.getAnimation());
        clonedEffect.setSound(effect.getSound());
        clonedEffect.setStatusEffect(effect.getStatusEffect());
        clonedEffect.setDuration(effect.getDuration());
        clonedEffect.setStatModifiers(new HashMap<>(effect.getStatModifiers()));
        return clonedEffect;
    }


    public static Move getMoveByName(String moveName) {
        return allMoves.get(moveName);
    }

    private static int calculateStat(int base, int level, boolean isHp) {
        int iv = 15; // Using a default IV for simplicity
        int ev = 0;  // Starting with 0 EVs

        if (isHp) {
            return ((2 * base + iv + (ev / 4)) * level / 100) + level + 10;
        } else {
            return ((2 * base + iv + (ev / 4)) * level / 100) + 5;
        }
    }

    public static class MoveEntry {
        public final String name;
        public final int level;

        public MoveEntry(String name, int level) {
            this.name = name;
            this.level = level;
        }
    }

    public static class PokemonTemplate {
        public Pokemon.PokemonType primaryType;
        public Pokemon.PokemonType secondaryType;
        public BaseStats baseStats;
        public List<MoveEntry> moves; // List of moves with levels
        String name;
        float width;
        float height;
    }

    public static class BaseStats {
        public final String name;
        public final int baseHp;
        public final int baseAttack;
        public final int baseDefense;
        public final int baseSpAtk;
        public final int baseSpDef;
        public final int baseSpeed;
        public final Pokemon.PokemonType primaryType;
        public final Pokemon.PokemonType secondaryType; // Can be null
        public final List<MoveEntry> moves; // List of moves with levels

        public BaseStats(String name, int baseHp, int baseAttack, int baseDefense,
                         int baseSpAtk, int baseSpDef, int baseSpeed,
                         Pokemon.PokemonType primaryType, Pokemon.PokemonType secondaryType,
                         List<MoveEntry> moves) {
            this.name = name;
            this.baseHp = baseHp;
            this.baseAttack = baseAttack;
            this.baseDefense = baseDefense;
            this.baseSpAtk = baseSpAtk;
            this.baseSpDef = baseSpDef;
            this.baseSpeed = baseSpeed;
            this.primaryType = primaryType;
            this.secondaryType = secondaryType;
            this.moves = moves != null ? moves : new ArrayList<>();
        }
    }

}

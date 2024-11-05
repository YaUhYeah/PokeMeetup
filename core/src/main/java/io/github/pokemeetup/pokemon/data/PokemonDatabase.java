package io.github.pokemeetup.pokemon.data;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import io.github.pokemeetup.pokemon.Pokemon;
import io.github.pokemeetup.pokemon.attacks.Move;
import io.github.pokemeetup.utils.GameLogger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PokemonDatabase {
    private static final String POKEMON_DATA_FILE = "data/pokemon.json";
    private static final Map<String, PokemonTemplate> pokemonTemplates = new HashMap<>();
    private static final Map<String, BaseStats> pokemonStats = new HashMap<>();
    private static boolean isInitialized = false;

    public static PokemonTemplate getTemplate(String name) {
        if (!isInitialized) {
            initialize();
        }
        return pokemonTemplates.get(name);
    }

    public static void initialize() {
        if (isInitialized) return;

        try {
            FileHandle file = Gdx.files.internal(POKEMON_DATA_FILE);
            if (!file.exists()) {
                GameLogger.error("Pokemon data file not found: " + POKEMON_DATA_FILE);
                return;
            }

            JsonReader reader = new JsonReader();
            JsonValue root = reader.parse(file);
            JsonValue pokemonArray = root.get("pokemon");

            if (pokemonArray == null) {
                GameLogger.error("Invalid pokemon.json format - missing 'pokemon' array");
                return;
            }

            for (JsonValue pokemonValue = pokemonArray.child; pokemonValue != null; pokemonValue = pokemonValue.next) {
                try {
                    String name = pokemonValue.getString("name");
                    if (name == null || name.isEmpty()) {
                        GameLogger.error("Pokemon entry missing name");
                        continue;
                    }

                    Pokemon.PokemonType primaryType = Pokemon.PokemonType.valueOf(pokemonValue.getString("primaryType"));
                    Pokemon.PokemonType secondaryType = getSecondaryType(pokemonValue);

                    List<MoveTemplate> moves = loadMoves(pokemonValue.get("moves"));

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

                    // Create and populate PokemonTemplate
                    PokemonTemplate template = new PokemonTemplate();
                    template.name = name;
                    template.primaryType = primaryType;
                    template.secondaryType = secondaryType;
                    template.baseStats = stats;
                    template.moves = moves;
                    // Optionally set width and height if available
                    template.width = pokemonValue.getFloat("width", 1.0f); // Default to 1.0f if not specified
                    template.height = pokemonValue.getFloat("height", 1.0f);

                    pokemonTemplates.put(name, template);

                    GameLogger.info("Loaded Pokemon: " + name);
                } catch (Exception e) {
                    GameLogger.error("Error loading Pokemon entry: " + e.getMessage());
                }
            }

            isInitialized = true;
            GameLogger.info("Pokemon database initialized with " + pokemonStats.size() + " Pokemon");

        } catch (Exception e) {
            GameLogger.error("Error loading Pokemon database: " + e.getMessage());
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

    private static List<MoveTemplate> loadMoves(JsonValue movesArray) {
        List<MoveTemplate> moves = new ArrayList<>();
        if (movesArray != null && movesArray.isArray()) {
            for (JsonValue moveValue = movesArray.child; moveValue != null; moveValue = moveValue.next) {
                try {
                    moves.add(new MoveTemplate(
                        moveValue.getString("name"),
                        Pokemon.PokemonType.valueOf(moveValue.getString("type")),
                        moveValue.getInt("power"),
                        moveValue.getInt("accuracy"),
                        moveValue.getInt("pp"),
                        moveValue.getInt("maxPp"),
                        moveValue.getBoolean("isSpecial")
                    ));
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
                    return Pokemon.PokemonType.valueOf(secondaryType);
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

            // Add initial moves (up to 4)
            List<Move> startingMoves = new ArrayList<>();
            int moveCount = Math.min(template.moves.size(), 4);
            for (int i = 0; i < moveCount; i++) {
                MoveTemplate moveTemplate = template.moves.get(i);
                startingMoves.add(new Move(
                    moveTemplate.name,
                    moveTemplate.type,
                    moveTemplate.power,
                    moveTemplate.accuracy,
                    moveTemplate.pp,
                    moveTemplate.isSpecial,
                    ""  // Description can be added if needed
                ));
            }
            builder.withMoves(startingMoves);

            return builder.build();

        } catch (Exception e) {
            GameLogger.error("Error creating Pokemon: " + e.getMessage());
            return null;
        }
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

    public static class PokemonTemplate {
        public Pokemon.PokemonType primaryType;
        public Pokemon.PokemonType secondaryType;
        public BaseStats baseStats;
        public List<MoveTemplate> moves;
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
        public final List<MoveTemplate> moves;

        public BaseStats(String name, int baseHp, int baseAttack, int baseDefense,
                         int baseSpAtk, int baseSpDef, int baseSpeed,
                         Pokemon.PokemonType primaryType, Pokemon.PokemonType secondaryType,
                         List<MoveTemplate> moves) {
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

    public static class MoveTemplate {
        public final String name;
        public final Pokemon.PokemonType type;
        public final int power;
        public final int accuracy;
        public final int pp;
        public final int maxPp;
        public final boolean isSpecial;

        public MoveTemplate(String name, Pokemon.PokemonType type, int power, int accuracy,
                            int pp, int maxPp, boolean isSpecial) {
            this.name = name;
            this.type = type;
            this.power = power;
            this.accuracy = accuracy;
            this.pp = pp;
            this.maxPp = maxPp;
            this.isSpecial = isSpecial;
        }
    }
}

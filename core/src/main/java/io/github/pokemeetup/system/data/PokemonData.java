package io.github.pokemeetup.system.data;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.pokemon.Pokemon;
import io.github.pokemeetup.pokemon.WildPokemon;
import io.github.pokemeetup.pokemon.attacks.LearnableMove;
import io.github.pokemeetup.pokemon.attacks.Move;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.TextureManager;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

public class PokemonData {
    // Basic Info
    public String name;
    public UUID uuid = UUID.randomUUID();
    public int level;
    public String nature;

    // Helper method to update Pokemon from PokemonData
    private void updatePokemonFromData(WildPokemon pokemon, PokemonData data) {
        if (pokemon == null || data == null) return;

        try {
            // Set basic info
            pokemon.setPrimaryType(data.getPrimaryType());
            pokemon.setSecondaryType(data.getSecondaryType());

            // Set stats if available
            if (data.getStats() != null) {
                Pokemon.Stats stats = pokemon.getStats();
                stats.setHp(data.getStats().hp);
                stats.setAttack(data.getStats().attack);
                stats.setDefense(data.getStats().defense);
                stats.setSpecialAttack(data.getStats().specialAttack);
                stats.setSpecialDefense(data.getStats().specialDefense);
                stats.setSpeed(data.getStats().speed);
            }

            // Set moves if available
            if (data.getMoves() != null) {
                pokemon.getMoves().clear();
                for (PokemonData.MoveData moveData : data.getMoves()) {
                    Move move = moveData.toMove();
                    if (move != null) {
                        pokemon.getMoves().add(move);
                    }
                }
            }

        } catch (Exception e) {
            GameLogger.error("Error updating Pokemon from data: " + e.getMessage());
        }
    }

    public Pokemon.PokemonType primaryType;
    public Pokemon.PokemonType secondaryType; // Can be null

    // Stats
    public Stats stats;
    // Moves
    public List<MoveData> moves;
    // Base Stats
    private int baseHp;
    private int baseAttack;
    private int baseDefense;
    private int baseSpAtk;
    private int baseSpDef;
    private int baseSpeed;
    // Physical Dimensions
    private float width;
    private float height;
    private List<LearnableMove> learnableMoves;
    private List<String> tmMoves; // List of TM moves this Pokemon can learn

    // Move Database (Cache)
    private HashMap<String, Move> moveDatabase;
    private HashMap<UUID, WildPokemonData> wildPokemon;
    // Add getter for position
    private Vector2 position = new Vector2();

    // Constructors
    public PokemonData() {
        this.uuid = UUID.randomUUID(); // Ensure UUID is set in constructor
        this.learnableMoves = new ArrayList<>();
        this.moves = new ArrayList<>();
        this.tmMoves = new ArrayList<>();
        this.moveDatabase = new HashMap<>();
        wildPokemon = new HashMap<>();
    }

    // Static method to create PokemonData from Pokemon object
    public static PokemonData fromPokemon(Pokemon pokemon) {
        if (pokemon == null) {
            throw new IllegalArgumentException("Cannot create PokemonData from null Pokemon.");
        }

        PokemonData data = new PokemonData();
        data.setName(pokemon.getName());
        data.setLevel(pokemon.getLevel());
        data.setNature(pokemon.getNature());
        data.setUuid(pokemon.getUuid());
        data.setPrimaryType(pokemon.getPrimaryType());
        data.setSecondaryType(pokemon.getSecondaryType());

        // Apply base stats
        data.setBaseHp(pokemon.getStats().getHp());
        data.setBaseAttack(pokemon.getStats().getAttack());
        data.setBaseDefense(pokemon.getStats().getDefense());
        data.setBaseSpAtk(pokemon.getStats().getSpecialAttack());
        data.setBaseSpDef(pokemon.getStats().getSpecialDefense());
        data.setBaseSpeed(pokemon.getStats().getSpeed());

        // Add moves
        if (pokemon.getMoves() != null) {
            List<MoveData> moveDataList = pokemon.getMoves().stream().map(MoveData::fromMove).filter(Objects::nonNull).collect(Collectors.toList());
            data.setMoves(moveDataList);
        }

        // Initialize learnableMoves and tmMoves as needed
        // (Add as per your game logic)

        return data;
    }

    public Map<UUID, WildPokemonData> getWildPokemon() {
        return new HashMap<>(wildPokemon);  // Return a copy for thread safety
    }

    public void addWildPokemon(UUID id, WildPokemonData pokemon) {
        wildPokemon.put(id, pokemon);
    }

    public void removeWildPokemon(UUID id) {
        wildPokemon.remove(id);
    }

    public Vector2 getPosition() {
        return position.cpy(); // Return a copy to prevent modification
    }

    // Getters and Setters for all fields
    // (Ensure all fields have appropriate getters and setters)

    public void setPosition(float x, float y) {
        this.position.set(x, y);
    }

    // Example Getters and Setters (Implement others similarly)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getUuid() {
        if (uuid == null) {
            uuid = UUID.randomUUID(); // Ensure UUID is never null
        }
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid != null ? uuid : UUID.randomUUID();
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public String getNature() {
        return nature;
    }

    public void setNature(String nature) {
        this.nature = nature;
    }

    public Pokemon.PokemonType getPrimaryType() {
        return primaryType;
    }

    public void setPrimaryType(Pokemon.PokemonType primaryType) {
        this.primaryType = primaryType != null ? primaryType : Pokemon.PokemonType.NORMAL; // Default to NORMAL
    }

    public Pokemon.PokemonType getSecondaryType() {
        return secondaryType;
    }

    public void setSecondaryType(Pokemon.PokemonType secondaryType) {
        this.secondaryType = secondaryType;
    }

    public Stats getStats() {
        return stats;
    }

    public void setStats(Stats stats) {
        this.stats = stats != null ? stats.copy() : new Stats();
    }

    public int getBaseHp() {
        return baseHp;
    }

    public void setBaseHp(int baseHp) {
        this.baseHp = baseHp;
    }

    public int getBaseAttack() {
        return baseAttack;
    }

    public void setBaseAttack(int baseAttack) {
        this.baseAttack = baseAttack;
    }

    public int getBaseDefense() {
        return baseDefense;
    }

    public void setBaseDefense(int baseDefense) {
        this.baseDefense = baseDefense;
    }

    public int getBaseSpAtk() {
        return baseSpAtk;
    }

    public void setBaseSpAtk(int baseSpAtk) {
        this.baseSpAtk = baseSpAtk;
    }

    public int getBaseSpDef() {
        return baseSpDef;
    }

    public void setBaseSpDef(int baseSpDef) {
        this.baseSpDef = baseSpDef;
    }

    public int getBaseSpeed() {
        return baseSpeed;
    }

    public void setBaseSpeed(int baseSpeed) {
        this.baseSpeed = baseSpeed;
    }

    public float getWidth() {
        return width;
    }

    public void setWidth(float width) {
        this.width = width;
    }

    public float getHeight() {
        return height;
    }

    public void setHeight(float height) {
        this.height = height;
    }

    public List<MoveData> getMoves() {
        return moves;
    }

    public void setMoves(List<MoveData> moves) {
        this.moves = moves != null ? new ArrayList<>(moves) : new ArrayList<>();
    }

    public List<LearnableMove> getLearnableMoves() {
        return learnableMoves;
    }

    public void setLearnableMoves(List<LearnableMove> learnableMoves) {
        this.learnableMoves = learnableMoves != null ? new ArrayList<>(learnableMoves) : new ArrayList<>();
    }

    public List<String> getTmMoves() {
        return tmMoves;
    }

    public void setTmMoves(List<String> tmMoves) {
        this.tmMoves = tmMoves != null ? new ArrayList<>(tmMoves) : new ArrayList<>();
    }

    public Map<String, Move> getMoveDatabase() {
        return moveDatabase;
    }

    public void setMoveDatabase(Map<String, Move> moveDatabase) {
        this.moveDatabase = moveDatabase != null ? new HashMap<>(moveDatabase) : new HashMap<>();
    }

    public PokemonData copy() {
        PokemonData copy = new PokemonData();
        // Copy existing fields...
        copy.baseHp = this.baseHp;
        copy.baseAttack = this.baseAttack;
        copy.baseDefense = this.baseDefense;
        copy.baseSpAtk = this.baseSpAtk;
        copy.baseSpDef = this.baseSpDef;
        copy.baseSpeed = this.baseSpeed;

        if (this.tmMoves != null) {
            copy.tmMoves = new ArrayList<>(this.tmMoves);
        }

        // Copy existing fields from parent class
        copy.name = this.name;
        copy.level = this.level;
        copy.nature = this.nature;
        copy.uuid = this.uuid;
        copy.primaryType = this.primaryType;
        copy.secondaryType = this.secondaryType;
        copy.stats = this.stats != null ? this.stats.copy() : null;

        if (this.moves != null) {
            copy.moves = new ArrayList<>();
            for (MoveData move : this.moves) {
                copy.moves.add(move.copy());
            }
        }

        if (this.learnableMoves != null) {
            copy.learnableMoves = new ArrayList<>(this.learnableMoves);
        }

        return copy;
    }

    // Method to convert to Pokemon object
    public Pokemon toPokemon() {
        if (name == null || name.isEmpty()) {
            throw new IllegalStateException("Pokemon name is missing.");
        }

        if (primaryType == null) {
            // This should never happen if setters are correctly implemented
            primaryType = Pokemon.PokemonType.NORMAL;
        }

        Pokemon pokemon = new Pokemon(name, level);
        pokemon.setUuid(uuid);
        pokemon.setNature(nature);
        pokemon.setPrimaryType(primaryType);
        pokemon.setSecondaryType(secondaryType);

        // Apply base stats
        pokemon.getStats().setHp(baseHp);
        pokemon.getStats().setAttack(baseAttack);
        pokemon.getStats().setDefense(baseDefense);
        pokemon.getStats().setSpecialAttack(baseSpAtk);
        pokemon.getStats().setSpecialDefense(baseSpDef);
        pokemon.getStats().setSpeed(baseSpeed);

        // Apply moves
        for (MoveData moveData : moves) {
            Move move = moveData.toMove();
            if (move != null) {
                pokemon.getMoves().add(move);
            }
        }

        return pokemon;
    }

    // Nested Stats class
    public static class Stats {
        public int hp;
        public int attack;
        public int defense;
        public int specialAttack;
        public int specialDefense;
        public int speed;
        public int[] ivs = new int[6];
        public int[] evs = new int[6];

        public Stats() {
            // Initialize with default values or random IVs as needed
        }

        public Stats(Pokemon.Stats stats) {
            if (stats != null) {
                this.hp = stats.getHp();
                this.attack = stats.getAttack();
                this.defense = stats.getDefense();
                this.specialAttack = stats.getSpecialAttack();
                this.specialDefense = stats.getSpecialDefense();
                this.speed = stats.getSpeed();
                System.arraycopy(stats.ivs, 0, this.ivs, 0, 6);
                System.arraycopy(stats.evs, 0, this.evs, 0, 6);
            }
        }

        public Stats copy() {
            Stats copy = new Stats();
            copy.hp = this.hp;
            copy.attack = this.attack;
            copy.defense = this.defense;
            copy.specialAttack = this.specialAttack;
            copy.specialDefense = this.specialDefense;
            copy.speed = this.speed;
            copy.ivs = this.ivs.clone();
            copy.evs = this.evs.clone();
            return copy;
        }
    }

    // Nested MoveData class
    public static class MoveData {
        public String name;
        public Pokemon.PokemonType type;
        public int power;
        public int accuracy;
        public int pp;
        public int maxPp;
        public boolean isSpecial;

        public MoveData() {
        }

        public MoveData(String name, Pokemon.PokemonType type, int power, int accuracy, int pp, int maxPp, boolean isSpecial) {
            this.name = name;
            this.type = type;
            this.power = power;
            this.accuracy = accuracy;
            this.pp = pp;
            this.maxPp = maxPp;
            this.isSpecial = isSpecial;
        }

        public static MoveData fromMove(Move move) {
            if (move == null) return null;
            return new MoveData(move.getName(), move.getType(), move.getPower(), move.getAccuracy(), move.getPp(), move.getMaxPp(), move.isSpecial());
        }

        public Move toMove() {
            if (name == null || name.isEmpty()) {
                GameLogger.error("Move name is missing.");
                return null;
            }

            if (type == null) {
                GameLogger.error("Move type is null for move " + name + ", setting to NORMAL.");
                type = Pokemon.PokemonType.NORMAL;
            }

            return new Move(name, type, power, accuracy, pp, isSpecial, "Description not available");
        }

        public MoveData copy() {
            return new MoveData(name, type, power, accuracy, pp, maxPp, isSpecial);
        }
    }    // Existing PokemonData fields...

    public static class WildPokemonData implements Serializable {
        private String name;
        private int level;
        private Vector2 position;
        private String direction;
        private boolean isMoving;
        private long spawnTime;
        private Pokemon.PokemonType primaryType;
        private Pokemon.PokemonType secondaryType;
        private float currentHp;
        private Stats stats;
        private List<MoveData> moves;
        private UUID uuid;

        public WildPokemonData() {
            this.position = new Vector2();
            this.stats = new Stats();
            this.moves = new ArrayList<>();
        }

        // Create a static factory method to create from WildPokemon
        public static WildPokemonData fromWildPokemon(WildPokemon pokemon) {
            WildPokemonData data = new WildPokemonData();
            data.setName(pokemon.getName());
            data.setLevel(pokemon.getLevel());
            data.setPosition(new Vector2(pokemon.getX(), pokemon.getY()));
            data.setDirection(pokemon.getDirection());
            data.setMoving(pokemon.isMoving());
            data.setSpawnTime(pokemon.getSpawnTime());
            data.setPrimaryType(pokemon.getPrimaryType());
            data.setSecondaryType(pokemon.getSecondaryType());
            data.setCurrentHp(pokemon.getCurrentHp());
            data.setUuid(pokemon.getUuid());

            // Copy stats
            if (pokemon.getStats() != null) {
                Stats stats = new Stats();
                stats.hp = pokemon.getStats().getHp();
                stats.attack = pokemon.getStats().getAttack();
                stats.defense = pokemon.getStats().getDefense();
                stats.specialAttack = pokemon.getStats().getSpecialAttack();
                stats.specialDefense = pokemon.getStats().getSpecialDefense();
                stats.speed = pokemon.getStats().getSpeed();
                data.setStats(stats);
            }

            // Copy moves
            if (pokemon.getMoves() != null) {
                List<MoveData> moves = pokemon.getMoves().stream()
                    .map(MoveData::fromMove)
                    .collect(Collectors.toList());
                data.setMoves(moves);
            }

            return data;
        }

        // Getters and Setters
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getLevel() {
            return level;
        }

        public void setLevel(int level) {
            this.level = level;
        }

        public Vector2 getPosition() {
            return position;
        }

        public void setPosition(Vector2 position) {
            this.position = position;
        }

        public String getDirection() {
            return direction;
        }

        public void setDirection(String direction) {
            this.direction = direction;
        }

        public boolean isMoving() {
            return isMoving;
        }

        public void setMoving(boolean moving) {
            isMoving = moving;
        }

        public long getSpawnTime() {
            return spawnTime;
        }

        public void setSpawnTime(long spawnTime) {
            this.spawnTime = spawnTime;
        }

        public Pokemon.PokemonType getPrimaryType() {
            return primaryType;
        }

        public void setPrimaryType(Pokemon.PokemonType type) {
            this.primaryType = type;
        }

        public Pokemon.PokemonType getSecondaryType() {
            return secondaryType;
        }

        public void setSecondaryType(Pokemon.PokemonType type) {
            this.secondaryType = type;
        }

        public float getCurrentHp() {
            return currentHp;
        }

        public void setCurrentHp(float hp) {
            this.currentHp = hp;
        }

        public Stats getStats() {
            return stats;
        }

        public void setStats(Stats stats) {
            this.stats = stats;
        }

        public List<MoveData> getMoves() {
            return moves;
        }

        public void setMoves(List<MoveData> moves) {
            this.moves = moves;
        }

        public UUID getUuid() {
            return uuid;
        }

        public void setUuid(UUID uuid) {
            this.uuid = uuid;
        }

        // Method to convert back to WildPokemon
        public WildPokemon toWildPokemon() {
            try {
                TextureRegion sprite = TextureManager.getOverworldSprite(name);
                WildPokemon pokemon = new WildPokemon(
                    name,
                    level,
                    (int) position.x,
                    (int) position.y,
                    sprite
                );

                pokemon.setUuid(uuid);
                pokemon.setDirection(direction);
                pokemon.setMoving(isMoving);
                pokemon.setSpawnTime(spawnTime);
                pokemon.setPrimaryType(primaryType);
                pokemon.setSecondaryType(secondaryType);
                pokemon.setCurrentHp(currentHp);

                // Set stats if available
                if (stats != null) {
                    pokemon.getStats().setHp(stats.hp);
                    pokemon.getStats().setAttack(stats.attack);
                    pokemon.getStats().setDefense(stats.defense);
                    pokemon.getStats().setSpecialAttack(stats.specialAttack);
                    pokemon.getStats().setSpecialDefense(stats.specialDefense);
                    pokemon.getStats().setSpeed(stats.speed);
                }

                // Add moves if available
                if (moves != null) {
                    moves.stream()
                        .map(MoveData::toMove)
                        .filter(Objects::nonNull)
                        .forEach(pokemon.getMoves()::add);
                }

                return pokemon;
            } catch (Exception e) {
                GameLogger.error("Failed to create WildPokemon from data: " + e.getMessage());
                return null;
            }

    }}
}

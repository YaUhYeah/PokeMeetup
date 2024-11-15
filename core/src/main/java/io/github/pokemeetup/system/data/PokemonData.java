package io.github.pokemeetup.system.data;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.pokemon.Pokemon;
import io.github.pokemeetup.pokemon.attacks.LearnableMove;
import io.github.pokemeetup.pokemon.attacks.Move;
import io.github.pokemeetup.utils.GameLogger;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

public class PokemonData {
    private final Vector2 position = new Vector2();
    // Basic Info
    public String name;
    public UUID uuid = UUID.randomUUID();
    public int level;
    public String nature;
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
    private Map<UUID, WildPokemonData> wildPokemonMap = new HashMap<>();
    private int baseSpDef;
    private int baseSpeed;
    // Physical Dimensions
    private float width;
    private float height;
    private List<LearnableMove> learnableMoves;
    private List<String> tmMoves;
    private HashMap<String, Move> moveDatabase;
    private HashMap<UUID, WildPokemonData> wildPokemon;
    public PokemonData() {
        this.uuid = UUID.randomUUID();
        this.learnableMoves = new ArrayList<>();
        this.moves = new ArrayList<>();
        this.tmMoves = new ArrayList<>();
        this.moveDatabase = new HashMap<>();
        wildPokemon = new HashMap<>();
        // Initialize stats with default values
        this.stats = new Stats();
    }

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

        return data;
    }

    public Map<UUID, WildPokemonData> getWildPokemonMap() {
        return wildPokemonMap;
    }

    public void setWildPokemonMap(Map<UUID, WildPokemonData> wildPokemonMap) {
        this.wildPokemonMap = wildPokemonMap;
    }

    public boolean verifyIntegrity() {
        if (this.name == null || this.name.isEmpty()) {
            GameLogger.error("PokemonData integrity check failed: name is null or empty");
            return false;
        }

        if (this.level <= 0) {
            GameLogger.error("PokemonData integrity check failed: level is non-positive");
            return false;
        }

        return true;
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
        return position.cpy();
    }

    public void setPosition(float x, float y) {
        this.position.set(x, y);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getUuid() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
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
        // Ensure stats are never null
        if (this.stats == null) {
            this.stats = new Stats();
        }
        return stats;
    }

    public void setStats(Stats stats) {
        // Never allow null stats
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


    public PokemonData copy() {
        PokemonData copy = new PokemonData();
        copy.baseHp = this.baseHp;
        copy.baseAttack = this.baseAttack;
        copy.baseDefense = this.baseDefense;
        copy.baseSpAtk = this.baseSpAtk;
        copy.baseSpDef = this.baseSpDef;
        copy.baseSpeed = this.baseSpeed;

        if (this.tmMoves != null) {
            copy.tmMoves = new ArrayList<>(this.tmMoves);
        }

        copy.name = this.name;
        copy.level = this.level;
        copy.nature = this.nature;
        copy.uuid = this.uuid;
        copy.primaryType = this.primaryType;
        copy.secondaryType = this.secondaryType;
        // Ensure stats are never null in copies
        copy.stats = this.stats != null ? this.stats.copy() : new Stats();


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
            // Initialize with default values
            this.hp = 1;
            this.attack = 1;
            this.defense = 1;
            this.specialAttack = 1;
            this.specialDefense = 1;
            this.speed = 1;
            // Initialize IVs and EVs with zeros
            this.ivs = new int[6];
            this.evs = new int[6];
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

        public int getHp() {
            return hp;
        }

        public void setHp(int hp) {
            this.hp = hp;
        }

        public int getAttack() {
            return attack;
        }

        public void setAttack(int attack) {
            this.attack = attack;
        }

        public int getDefense() {
            return defense;
        }

        public void setDefense(int defense) {
            this.defense = defense;
        }

        public int getSpecialAttack() {
            return specialAttack;
        }

        public void setSpecialAttack(int specialAttack) {
            this.specialAttack = specialAttack;
        }

        public int getSpecialDefense() {
            return specialDefense;
        }

        public void setSpecialDefense(int specialDefense) {
            this.specialDefense = specialDefense;
        }

        public int getSpeed() {
            return speed;
        }

        public void setSpeed(int speed) {
            this.speed = speed;
        }

        public int[] getIvs() {
            return ivs;
        }

        public void setIvs(int[] ivs) {
            this.ivs = ivs;
        }

        public int[] getEvs() {
            return evs;
        }

        public void setEvs(int[] evs) {
            this.evs = evs;
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
        public String description; // Add description field
        public PokemonData.MoveEffectData effect; // Add effect field
        public boolean canFlinch;

        public MoveData() {
        }


        public MoveData(String name, Pokemon.PokemonType type, int power, int accuracy, int pp, int maxPp, boolean isSpecial, String description, PokemonData.MoveEffectData effect, boolean canFlinch) {
            this.name = name;
            this.type = type;
            this.power = power;
            this.accuracy = accuracy;
            this.pp = pp;
            this.maxPp = maxPp;
            this.isSpecial = isSpecial;
            this.description = description;
            this.effect = effect;
            this.canFlinch = canFlinch;
        }

        public static MoveData fromMove(Move move) {
            if (move == null) return null;
            MoveData moveData = new MoveData();
            moveData.name = move.getName();
            moveData.type = move.getType();
            moveData.power = move.getPower();
            moveData.accuracy = move.getAccuracy();
            moveData.pp = move.getPp();
            moveData.maxPp = move.getMaxPp();
            moveData.isSpecial = move.isSpecial();
            moveData.description = move.getDescription();
            moveData.canFlinch = move.canFlinch();


            return moveData;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Pokemon.PokemonType getType() {
            return type;
        }

        public void setType(Pokemon.PokemonType type) {
            this.type = type;
        }

        public int getPower() {
            return power;
        }

        public void setPower(int power) {
            this.power = power;
        }

        public int getAccuracy() {
            return accuracy;
        }

        public void setAccuracy(int accuracy) {
            this.accuracy = accuracy;
        }

        public int getPp() {
            return pp;
        }

        public void setPp(int pp) {
            this.pp = pp;
        }

        public int getMaxPp() {
            return maxPp;
        }

        public void setMaxPp(int maxPp) {
            this.maxPp = maxPp;
        }

        public boolean isSpecial() {
            return isSpecial;
        }

        public void setSpecial(boolean special) {
            isSpecial = special;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public boolean isCanFlinch() {
            return canFlinch;
        }

        public void setCanFlinch(boolean canFlinch) {
            this.canFlinch = canFlinch;
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

            Move.Builder builder = new Move.Builder(name, type)
                .power(power)
                .accuracy(accuracy)
                .pp(pp)
                .special(isSpecial)
                .description(description)
                .canFlinch(canFlinch);

            return builder.build();
        }


        public MoveData copy() {
            return new MoveData(name, type, power, accuracy, pp, maxPp, isSpecial, description, effect, canFlinch);
        }

        public static class MoveEffectData {
            public Pokemon.Status statusEffect;
            public Map<String, Integer> statModifiers;
            public String effectType;
            public float chance;
            public String animation;
            public String sound;
            public int duration;

            public MoveEffectData(Pokemon.Status statusEffect, Map<String, Integer> statModifiers, String effectType, float chance, String animation, String sound, int duration) {
                this.statusEffect = statusEffect;
                this.statModifiers = statModifiers;
                this.effectType = effectType;
                this.chance = chance;
                this.animation = animation;
                this.sound = sound;
                this.duration = duration;
            }

            public MoveEffectData() {
            }

            public static MoveEffectData fromMoveEffect(Move.MoveEffect moveEffect) {
                if (moveEffect == null) return null;
                MoveEffectData effectData = new MoveEffectData();
                effectData.statusEffect = moveEffect.getStatusEffect();
                effectData.statModifiers = new HashMap<>(moveEffect.getStatModifiers());
                effectData.effectType = moveEffect.getEffectType();
                effectData.chance = moveEffect.getChance();
                effectData.animation = moveEffect.getAnimation();
                effectData.sound = moveEffect.getSound();
                effectData.duration = moveEffect.getDuration();
                return effectData;
            }

            public Pokemon.Status getStatusEffect() {
                return statusEffect;
            }

            public void setStatusEffect(Pokemon.Status statusEffect) {
                this.statusEffect = statusEffect;
            }

            public Map<String, Integer> getStatModifiers() {
                return statModifiers;
            }

            public void setStatModifiers(Map<String, Integer> statModifiers) {
                this.statModifiers = statModifiers;
            }

            public String getEffectType() {
                return effectType;
            }

            public void setEffectType(String effectType) {
                this.effectType = effectType;
            }

            public float getChance() {
                return chance;
            }

            public void setChance(float chance) {
                this.chance = chance;
            }

            public String getAnimation() {
                return animation;
            }

            public void setAnimation(String animation) {
                this.animation = animation;
            }

            public String getSound() {
                return sound;
            }

            public void setSound(String sound) {
                this.sound = sound;
            }

            public int getDuration() {
                return duration;
            }

            public void setDuration(int duration) {
                this.duration = duration;
            }

            public Move.MoveEffect toMoveEffect() {
                Move.MoveEffect moveEffect = new Move.MoveEffect();
                moveEffect.setStatusEffect(statusEffect);
                moveEffect.setStatModifiers(statModifiers != null ? new HashMap<>(statModifiers) : new HashMap<>());
                moveEffect.setEffectType(effectType);
                moveEffect.setChance(chance);
                moveEffect.setAnimation(animation);
                moveEffect.setSound(sound);
                moveEffect.setDuration(duration);
                return moveEffect;
            }


            // Constructors, getters, setters
        }
    }

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
    }

    public static class MoveEffectData {
        private String type;
        private float chance;
        private Pokemon.Status status;
        private Map<String, Integer> statChanges = new HashMap<>();

        public MoveEffectData(String type, float chance) {
            this.type = type;
            this.chance = chance;
        }

        public String getType() {
            return type;
        }

        public float getChance() {
            return chance;
        }

        public Pokemon.Status getStatus() {
            return status;
        }

        public void setStatus(Pokemon.Status status) {
            this.status = status;
        }

        public Map<String, Integer> getStatChanges() {
            return statChanges;
        }

        public void setStatChanges(Map<String, Integer> changes) {
            this.statChanges = changes;
        }
    }
}







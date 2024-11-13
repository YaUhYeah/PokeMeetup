package io.github.pokemeetup.system.gameplay.overworld;

import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.pokemon.WildPokemon;
import io.github.pokemeetup.system.gameplay.overworld.biomes.Biome;
import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;
import io.github.pokemeetup.utils.GameLogger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static io.github.pokemeetup.system.gameplay.overworld.World.TILE_SIZE;

public class PokemonSpawnManager {
    public static final float POKEMON_DESPAWN_TIME = 120; // Increased from 120 to 300 seconds
    private static final float BASE_SPAWN_RATE = 0.3f;  // Base 30% chance per check
    private static final float SPAWN_CHECK_INTERVAL = 5.0f;  // Check every 5 seconds
    private static final float BASE_LEVEL_MIN = 2f;
    private static final float BASE_LEVEL_MAX = 4f;
    private static final Map<BiomeType, Map<TimeOfDay, String[]>> POKEMON_SPAWNS = new HashMap<>();
    private static final float DISTANCE_LEVEL_BONUS = 0.01f; // Level increase per distance unit
    private static final float MAX_LEVEL_BONUS = 4f; // Maximum additional levels from distance
    private static final float LEVEL_VARIANCE = 2f; // Random variance in levels
    private static final int MIN_CHUNKS_TRACKED = 9; // 3x3 around player
    private final TextureAtlas atlas;
    private final Random random;
    private final Map<Vector2, List<WildPokemon>> pokemonByChunk;  // Changed from chunkPokemo
    private final Map<UUID, WildPokemon> pokemonById;
    private final long worldSeed;
    private final Map<UUID, NetworkSyncData> syncedPokemon = new ConcurrentHashMap<>();
    private final GameClient gameClient;
    private World world;
    private float spawnTimer = 0;
    public PokemonSpawnManager(World world, TextureAtlas atlas, GameClient client) {
        if (world == null) {
            throw new IllegalArgumentException("World cannot be null when initializing PokemonSpawnManager.");
        }
        this.world = world;
        this.gameClient = client;
        this.atlas = atlas;
        this.worldSeed = world.getWorldSeed();
        this.random = new Random();
        this.pokemonByChunk = new ConcurrentHashMap<>();
        this.pokemonById = new ConcurrentHashMap<>();
        initializePokemonSpawns();

    }

    public GameClient getGameClient() {
        return gameClient;
    }

    private void checkSpawns(Vector2 playerPosition) {
        GameLogger.info("Player position (tiles): " + playerPosition.x + "," + playerPosition.y);

        // Convert player position to chunk coordinates
        Vector2 playerChunk = getChunkPosition(playerPosition.x * TILE_SIZE, playerPosition.y * TILE_SIZE);

        // Get loaded chunks around player
        Set<Vector2> loadedChunks = new HashSet<>();
        int radius = 1; // Check one chunk radius around player
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                Vector2 chunkPos = new Vector2(playerChunk.x + dx, playerChunk.y + dy);
                if (world.getChunks().containsKey(chunkPos)) {
                    loadedChunks.add(chunkPos);
                }
            }
        }

        if (loadedChunks.isEmpty()) {
            GameLogger.info("No loaded chunks found around player");
            return;
        }

        // Define spawn radius in tiles (reduced to stay within loaded chunks)
        int minSpawnDistance = 3;
        int maxSpawnDistance = 8; // Reduced from 10 to stay within chunks better

        int attempts = 10;
        while (attempts > 0) {
            attempts--;
            double angle = random.nextDouble() * 2 * Math.PI;
            int distance = minSpawnDistance + random.nextInt(maxSpawnDistance - minSpawnDistance + 1);

            int spawnTileX = (int) (playerPosition.x + Math.cos(angle) * distance);
            int spawnTileY = (int) (playerPosition.y + Math.sin(angle) * distance);
            Vector2 spawnChunk = getChunkPosition(spawnTileX * TILE_SIZE, spawnTileY * TILE_SIZE);
            if (!loadedChunks.contains(spawnChunk)) {
                GameLogger.info("Spawn position in unloaded chunk, retrying...");
                continue;
            }
            if (isValidSpawnPosition(spawnTileX, spawnTileY)) {
                if (trySpawnPokemon(spawnTileX, spawnTileY)) {
                    GameLogger.info("Successfully spawned Pokemon");
                    break;
                }
            }
        }
    }

    private boolean trySpawnPokemon(int tileX, int tileY) {
        try {
            if (!isValidSpawnPosition(tileX, tileY)) {
                return false;
            }

            Biome biome = world.getBiomeAt(tileX, tileY);
            if (biome == null) {
                GameLogger.error("Invalid biome at position: " + tileX + "," + tileY);
                return false;
            }

            // Apply spawn rate check
            if (random.nextFloat() > BASE_SPAWN_RATE) {
                return false;
            }

            // Select Pokemon for biome
            String pokemonName = selectPokemonForBiome(biome);
            if (pokemonName == null) {
                GameLogger.error("No Pokemon available for biome: " + biome.getType());
                return false;
            }

            // Get sprite
            TextureRegion overworldSprite = atlas.findRegion(pokemonName.toUpperCase() + "_overworld");
            if (overworldSprite == null) {
                GameLogger.error("Could not find sprite for Pokemon: " + pokemonName);
                return false;
            }

            // Calculate spawn position in pixels
            int pixelX = tileX * TILE_SIZE;
            int pixelY = tileY * TILE_SIZE;

            // Calculate level
            Vector2 spawnPoint = getSpawnPoint();
            float distanceFromSpawn = Vector2.dst(
                tileX,
                tileY,
                spawnPoint.x / TILE_SIZE,
                spawnPoint.y / TILE_SIZE
            );
            int level = calculatePokemonLevel(distanceFromSpawn);

            // Create Pokemon with exact pixel coordinates
            WildPokemon pokemon = new WildPokemon(
                pokemonName,
                level,
                pixelX,
                pixelY,
                overworldSprite
            );

            // Set spawn time
            pokemon.setSpawnTime((long) (System.currentTimeMillis() / 1000f));

            // Add to management collections
            pokemonById.put(pokemon.getUuid(), pokemon);

            // Calculate correct chunk position
            Vector2 chunkPos = new Vector2(
                Math.floorDiv(pixelX, TILE_SIZE * Chunk.CHUNK_SIZE),
                Math.floorDiv(pixelY, TILE_SIZE * Chunk.CHUNK_SIZE)
            );

            addPokemonToChunk(pokemon, chunkPos);
            return true;

        } catch (Exception e) {
            GameLogger.error("Error spawning Pokemon: " + e.getMessage());
            return false;
        }
    }

    public void update(float delta, Vector2 playerPosition) {
        spawnTimer += delta;
        if (spawnTimer >= SPAWN_CHECK_INTERVAL) {
            spawnTimer = 0;
            if (pokemonByChunk.size() < MIN_CHUNKS_TRACKED) {
                int radius = 1;
                Vector2 playerChunk = getChunkPosition(
                    playerPosition.x * TILE_SIZE,
                    playerPosition.y * TILE_SIZE
                );
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dy = -radius; dy <= radius; dy++) {
                        Vector2 chunkPos = new Vector2(playerChunk.x + dx, playerChunk.y + dy);
                        pokemonByChunk.computeIfAbsent(chunkPos, k -> new ArrayList<>());
                    }
                }
            }

            // Update Pokemon
            Iterator<Map.Entry<UUID, WildPokemon>> iter = pokemonById.entrySet().iterator();
            while (iter.hasNext()) {
                WildPokemon pokemon = iter.next().getValue();

                if (pokemon.isDespawning()) {
                    if (pokemon.isExpired()) {
                        removePokemon(pokemon.getUuid());
                        continue;
                    }
                }

                pokemon.update(delta, world);

            }

            checkSpawns(playerPosition);
            removeExpiredPokemon();
        }

        updatePokemonMovements(delta);
    }

    public void addPokemonToChunk(WildPokemon pokemon, Vector2 chunkPos) {
        try {
            List<WildPokemon> pokemonList = pokemonByChunk.computeIfAbsent(chunkPos, k -> new ArrayList<>());
            pokemonList.add(pokemon);
        } catch (Exception e) {
            GameLogger.error("Error adding Pokemon to chunk: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean isValidSpawnPosition(int tileX, int tileY) {
        if (!world.isPassable(tileX, tileY)) {
            GameLogger.info("Position not passable");
            return false;
        }

        float pixelX = tileX * TILE_SIZE;
        float pixelY = tileY * TILE_SIZE;
        Collection<WildPokemon> nearbyPokemon = getPokemonInRange(pixelX, pixelY, TILE_SIZE * 3);
        if (!nearbyPokemon.isEmpty()) {
            GameLogger.info("Too close to other Pokemon");
            return false;
        }
        Vector2 chunkPos = getChunkPosition(pixelX, pixelY);
        if (!world.getChunks().containsKey(chunkPos)) {
            GameLogger.info("Chunk not loaded at position: " + chunkPos);
            return false;
        }

        return true;
    }
    private Vector2 getChunkPosition(float pixelX, float pixelY) {
        int chunkX = Math.floorDiv((int)pixelX, World.CHUNK_SIZE * TILE_SIZE);
        int chunkY = Math.floorDiv((int)pixelY, World.CHUNK_SIZE * TILE_SIZE);
        return new Vector2(chunkX, chunkY);
    }

    public Vector2 getSpawnPoint() {
        return new Vector2(
            World.HALF_WORLD_SIZE * World.TILE_SIZE,
            World.HALF_WORLD_SIZE * World.TILE_SIZE
        );
    }



    private void updateNetworkedPokemon(WildPokemon pokemon, NetworkSyncData syncData, float delta) {
        // Simple linear interpolation to target position
        if (syncData.isMoving && syncData.targetPosition != null) {
            Vector2 currentPos = new Vector2(pokemon.getX(), pokemon.getY());
            Vector2 targetPos = syncData.targetPosition;

            // Calculate interpolation
            float interpolationSpeed = 5f; // Adjust as needed
            float newX = MathUtils.lerp(currentPos.x, targetPos.x, delta * interpolationSpeed);
            float newY = MathUtils.lerp(currentPos.y, targetPos.y, delta * interpolationSpeed);

            // Update position
            pokemon.setX(newX);
            pokemon.setY(newY);
            pokemon.updateBoundingBox();
        }

        // Update animation state
        pokemon.setMoving(syncData.isMoving);
        pokemon.setDirection(syncData.direction);
    }

    private void sendPokemonUpdate(WildPokemon pokemon) {
        NetworkProtocol.PokemonUpdate update = new NetworkProtocol.PokemonUpdate();
        update.uuid = pokemon.getUuid();
        update.x = pokemon.getX();
        update.y = pokemon.getY();
        update.direction = pokemon.getDirection();
        update.isMoving = pokemon.isMoving();

        gameClient.sendPokemonUpdate(update);
    }

    public void removePokemon(UUID pokemonId) {
        WildPokemon pokemon = pokemonById.remove(pokemonId);
        if (pokemon != null) {
            Vector2 chunkPos = getChunkPosition(pokemon.getX(), pokemon.getY());
            List<WildPokemon> pokemonList = pokemonByChunk.get(chunkPos);
            if (pokemonList != null) {
                pokemonList.remove(pokemon);
            }
            syncedPokemon.remove(pokemonId);

            GameLogger.info("Removed Pokemon: " + pokemon.getName());
        }
    }

    private String selectPokemonForBiome(Biome biome) {
        GameLogger.info("Selecting Pokemon for biome: " + biome.getType());

        double worldTimeInMinutes = world.getWorldData().getWorldTimeInMinutes();
        float hourOfDay = DayNightCycle.getHourOfDay(worldTimeInMinutes);
        TimeOfDay timeOfDay = (hourOfDay >= 6 && hourOfDay < 18) ? TimeOfDay.DAY : TimeOfDay.NIGHT;

        GameLogger.info("Time of day: " + timeOfDay + " (Hour: " + hourOfDay + ")");

        Map<TimeOfDay, String[]> biomeSpawns = POKEMON_SPAWNS.get(biome.getType());
        if (biomeSpawns == null) {
            GameLogger.info("No spawns defined for biome, using default");
            return getDefaultPokemon(timeOfDay);
        }

        String[] possiblePokemon = biomeSpawns.get(timeOfDay);
        if (possiblePokemon == null || possiblePokemon.length == 0) {
            GameLogger.info("No Pokemon available for time of day, using default");
            return getDefaultPokemon(timeOfDay);
        }

        String selected = possiblePokemon[random.nextInt(possiblePokemon.length)];
        GameLogger.info("Selected Pokemon: " + selected);
        return selected;
    }

    private int calculatePokemonLevel(float distanceFromSpawn) {
        // Base level range
        float baseMin = BASE_LEVEL_MIN;

        // Calculate distance bonus (capped at MAX_LEVEL_BONUS)
        float distanceBonus = Math.min(
            distanceFromSpawn * DISTANCE_LEVEL_BONUS,
            MAX_LEVEL_BONUS
        );

        // Add random variance
        float variance = (random.nextFloat() * 2 - 1) * LEVEL_VARIANCE;

        // Calculate final level
        float level = baseMin + random.nextFloat() * (BASE_LEVEL_MAX - baseMin);
        level += distanceBonus + variance;

        // Special biome adjustments
        BiomeType biomeType = world.getBiomeAt(
            (int) (distanceFromSpawn * Math.cos(random.nextFloat() * Math.PI * 2)),
            (int) (distanceFromSpawn * Math.sin(random.nextFloat() * Math.PI * 2))
        ).getType();

        // Adjust levels for special biomes
        switch (biomeType) {
            case SNOW:
                level *= 1.1f; // Snow biome Pokemon are slightly higher level
                break;
            case DESERT:
                level *= 1.15f; // Desert Pokemon are slightly higher level
                break;
            default:
                break;
        }

        // Ensure minimum level and round to integer
        return Math.max((int) level, (int) BASE_LEVEL_MIN);
    }

    private void initializePokemonSpawns() {
        // Plains biome
        Map<TimeOfDay, String[]> plainsSpawns = new HashMap<>();
        plainsSpawns.put(TimeOfDay.DAY, new String[]{
            "Pidgey", "Rattata", "Spearow", "Bulbasaur", "Squirtle",
            "Charmander", "Meowth", "Pikachu", "Eevee"
        });
        plainsSpawns.put(TimeOfDay.NIGHT, new String[]{
            "Hoothoot", "Oddish", "Meowth", "Rattata", "Zubat",
            "Gastly", "Murkrow"
        });
        POKEMON_SPAWNS.put(BiomeType.PLAINS, plainsSpawns);

        // Forest biome
        Map<TimeOfDay, String[]> forestSpawns = new HashMap<>();
        forestSpawns.put(TimeOfDay.DAY, new String[]{
            "Caterpie", "Weedle", "Oddish", "Bellsprout", "Chikorita",
            "Pineco", "Shroomish"
        });
        forestSpawns.put(TimeOfDay.NIGHT, new String[]{
            "Hoothoot", "Spinarak", "Oddish", "Venonat", "Gastly",
            "Murkrow"
        });
        POKEMON_SPAWNS.put(BiomeType.FOREST, forestSpawns);

        // Snow biome
        Map<TimeOfDay, String[]> snowSpawns = new HashMap<>();
        snowSpawns.put(TimeOfDay.DAY, new String[]{
            "Swinub", "Snover", "Snorunt", "Spheal", "Cubchoo"
        });
        snowSpawns.put(TimeOfDay.NIGHT, new String[]{
            "Sneasel", "Delibird", "Snorunt", "Glalie", "Snover",
            "Vanillite"
        });
        POKEMON_SPAWNS.put(BiomeType.SNOW, snowSpawns);

        // Desert biome
        Map<TimeOfDay, String[]> desertSpawns = new HashMap<>();
        desertSpawns.put(TimeOfDay.DAY, new String[]{
            "Trapinch", "Sandshrew", "Geodude", "Baltoy", "Cacnea",
            "Sandile", "Hippopotas"
        });
        desertSpawns.put(TimeOfDay.NIGHT, new String[]{
            "Zubat", "Voltorb", "Cacnea", "Sandile"
        });
        POKEMON_SPAWNS.put(BiomeType.DESERT, desertSpawns);

        // Haunted biome
        Map<TimeOfDay, String[]> hauntedSpawns = new HashMap<>();
        hauntedSpawns.put(TimeOfDay.DAY, new String[]{
            "Misdreavus", "Shuppet", "Duskull"
        });
        hauntedSpawns.put(TimeOfDay.NIGHT, new String[]{
            "Gastly", "Haunter", "Misdreavus", "Shuppet", "Duskull",
            "Litwick", "Phantump", "Yamask"
        });
        POKEMON_SPAWNS.put(BiomeType.HAUNTED, hauntedSpawns);

        // Rain Forest biome
        Map<TimeOfDay, String[]> rainforestSpawns = new HashMap<>();
        rainforestSpawns.put(TimeOfDay.DAY, new String[]{
            "Treecko", "Slakoth", "Aipom", "Tropius", "Pansage",
            "Tangela", "Paras"
        });


        // Big Mountains biome
        Map<TimeOfDay, String[]> mountainSpawns = new HashMap<>();
        mountainSpawns.put(TimeOfDay.DAY, new String[]{
            "Machop", "Geodude", "Aron", "Onix", "Teddiursa"
        });
        mountainSpawns.put(TimeOfDay.NIGHT, new String[]{
            "Zubat", "Machop", "Geodude", "Sableye", "Larvitar"
        });
        POKEMON_SPAWNS.put(BiomeType.BIG_MOUNTAINS, mountainSpawns);
        // Cherry Blossom biome
        Map<TimeOfDay, String[]> cherrySpawns = new HashMap<>();
        cherrySpawns.put(TimeOfDay.DAY, new String[]{
            "Cherubi", "Petilil", "Combee", "Butterfree", "Beautifly",
            "Bounsweet", "Comfey", "Ribombee", "Lilligant"
        });
        cherrySpawns.put(TimeOfDay.NIGHT, new String[]{
            "Morelull", "Cutiefly", "Spritzee", "Munna", "Swablu",
            "Illumise", "Volbeat"
        });
        POKEMON_SPAWNS.put(BiomeType.CHERRY_BLOSSOM, cherrySpawns);

        // Safari biome
        Map<TimeOfDay, String[]> safariSpawns = new HashMap<>();
        safariSpawns.put(TimeOfDay.DAY, new String[]{
            "Tauros", "Kangaskhan", "Girafarig", "Zangoose", "Bouffalant",
            "Miltank", "Zebstrika", "Pyroar", "Donphan", "Heliolisk"
        });
        safariSpawns.put(TimeOfDay.NIGHT, new String[]{
            "Luxio", "Noctowl", "Seviper", "Persian", "Liepard",
            "Zoroark", "Absol"
        });
        POKEMON_SPAWNS.put(BiomeType.SAFARI, safariSpawns);

        // Swamp biome
        Map<TimeOfDay, String[]> swampSpawns = new HashMap<>();
        swampSpawns.put(TimeOfDay.DAY, new String[]{
            "Croagunk", "Politoed", "Quagsire", "Lotad", "Yanma",
            "Carnivine", "Tympole", "Froakie", "Goomy"
        });
        swampSpawns.put(TimeOfDay.NIGHT, new String[]{
            "Wooper", "Stunky", "Gastrodon", "Skorupi", "Toxicroak",
            "Croagunk", "Marshtomp"
        });
        POKEMON_SPAWNS.put(BiomeType.SWAMP, swampSpawns);

        // Volcano biome
        Map<TimeOfDay, String[]> volcanoSpawns = new HashMap<>();
        volcanoSpawns.put(TimeOfDay.DAY, new String[]{
            "Numel", "Slugma", "Torkoal", "Heatmor", "Camerupt",
            "Magmar", "Turtonator", "Salandit"
        });
        volcanoSpawns.put(TimeOfDay.NIGHT, new String[]{
            "Slugma", "Magmar", "Houndour", "Magby", "Magcargo",
            "Torchic", "Torkoal"
        });
        POKEMON_SPAWNS.put(BiomeType.VOLCANO, volcanoSpawns);
    }

    public void setWorld(World world) {
        this.world = world;
    }

    private void removeExpiredPokemon() {
        List<UUID> toRemove = new ArrayList<>();
        for (WildPokemon pokemon : pokemonById.values()) {
            if (pokemon.isExpired()) {
                toRemove.add(pokemon.getUuid());
                Vector2 chunkPos = getChunkPosition(pokemon.getX(), pokemon.getY());
                List<WildPokemon> pokemonList = pokemonByChunk.get(chunkPos);
                if (pokemonList != null) {
                    pokemonList.remove(pokemon);
                }
            }
        }

        for (UUID id : toRemove) {
            pokemonById.remove(id);
        }
    }

//    public void handleNetworkSpawn(NetworkProtocol.WildPokemonSpawn spawnUpdate) {
//        // Create Pokemon from network data
//        WildPokemon pokemon = createPokemonFromData(spawnUpdate.data, spawnUpdate.x, spawnUpdate.y);
//        if (pokemon != null) {
//            pokemon.setUuid(spawnUpdate.uuid); // Use the network-provided UUID
//            addPokemonToChunk(pokemon, new Vector2(spawnUpdate.x, spawnUpdate.y));
//            pokemonById.put(pokemon.getUuid(), pokemon);
//            GameLogger.info("Added network-spawned Pokemon: " + pokemon.getName());
//        }
//    }

    // Add network update methods
    public void handleNetworkUpdate(NetworkProtocol.PokemonUpdate update) {
        WildPokemon pokemon = pokemonById.get(update.uuid);
        if (pokemon != null) {
            // Update Pokemon state from network data
            pokemon.setDirection(update.direction);
            pokemon.setMoving(update.isMoving);

            // Update sync data
            NetworkSyncData syncData = syncedPokemon.computeIfAbsent(
                update.uuid, k -> new NetworkSyncData());
            syncData.lastUpdateTime = System.currentTimeMillis();
            syncData.targetPosition = new Vector2(update.x, update.y);
            syncData.direction = update.direction;
            syncData.isMoving = update.isMoving;

            GameLogger.info("Received network update for Pokemon: " + pokemon.getName());
        }
    }

    public void handleNetworkDespawn(UUID pokemonId) {
        WildPokemon pokemon = pokemonById.remove(pokemonId);
        if (pokemon != null) {
            Vector2 chunkPos = getChunkPosition(pokemon.getX(), pokemon.getY());
            List<WildPokemon> pokemonList = pokemonByChunk.get(chunkPos);
            if (pokemonList != null) {
                pokemonList.remove(pokemon);
                GameLogger.info("Removed network-despawned Pokemon: " + pokemon.getName());
            }
        }
    }

    public void despawnPokemon(UUID pokemonId) {
        WildPokemon pokemon = pokemonById.get(pokemonId);
        if (pokemon != null && !pokemon.isDespawning()) {
            pokemon.startDespawnAnimation();

            // Send despawn update in multiplayer immediately
            // so other clients can show the animation too
            if (!world.getGameClient().isSinglePlayer()) {
                world.getGameClient().sendPokemonDespawn(pokemonId);
            }

            // The pokemon will be removed from collections when animation completes
            // via the normal update cycle checking isExpired()
        }
    }

    private WildPokemon createWildPokemon(Vector2 chunkPos, Biome biome) {
        int attempts = 10;
        while (attempts > 0) {
            int localX = random.nextInt(Chunk.CHUNK_SIZE);
            int localY = random.nextInt(Chunk.CHUNK_SIZE);

            int worldTileX = (int) (chunkPos.x * Chunk.CHUNK_SIZE) + localX;
            int worldTileY = (int) (chunkPos.y * Chunk.CHUNK_SIZE) + localY;

            if (world.isPassable(worldTileX, worldTileY)) {
                // Select Pokemon based on biome
                String pokemonName = selectPokemonForBiome(biome);
                if (pokemonName != null) {
                    TextureRegion overworldSprite = atlas.findRegion(pokemonName.toUpperCase() + "_overworld");
                    if (overworldSprite != null) {
                        float pixelX = worldTileX * World.TILE_SIZE;
                        float pixelY = worldTileY * World.TILE_SIZE;

                        // Snap to grid
                        float snappedX = Math.round(pixelX / World.TILE_SIZE) * World.TILE_SIZE;
                        float snappedY = Math.round(pixelY / World.TILE_SIZE) * World.TILE_SIZE;

                        return new WildPokemon(pokemonName, random.nextInt(22) + 1, (int) snappedX, (int) snappedY, overworldSprite);
                    }
                }
            }
            attempts--;
        }
        return null;
    }


    private boolean isSpecialSpawnTime(float hourOfDay) {
        // Dawn (5-7 AM) and Dusk (6-8 PM) have special spawns
        return (hourOfDay >= 5 && hourOfDay <= 7) ||
            (hourOfDay >= 18 && hourOfDay <= 20);
    }

    private String getDefaultPokemon(TimeOfDay timeOfDay) {
        return timeOfDay == TimeOfDay.DAY ? "Rattata" : "Hoothoot";
    }


    public Collection<WildPokemon> getPokemonInRange(float centerX, float centerY, float range) {
        float rangeSquared = range * range;
        int chunkRadius = (int) Math.ceil(range / (Chunk.CHUNK_SIZE * World.TILE_SIZE)) + 1;
        Vector2 centerChunk = getChunkPosition(centerX, centerY);

        List<WildPokemon> inRangePokemon = new ArrayList<>();

        // Check surrounding chunks
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dy = -chunkRadius; dy <= chunkRadius; dy++) {
                Vector2 checkChunk = new Vector2(centerChunk.x + dx, centerChunk.y + dy);
                List<WildPokemon> pokemonInChunk = pokemonByChunk.get(checkChunk);  // Fixed variable name

                if (pokemonInChunk != null) {
                    for (WildPokemon pokemon : pokemonInChunk) {
                        float dx2 = pokemon.getX() - centerX;
                        float dy2 = pokemon.getY() - centerY;
                        float distanceSquared = dx2 * dx2 + dy2 * dy2;

                        if (distanceSquared <= rangeSquared) {
                            inRangePokemon.add(pokemon);
                        }
                    }
                }
            }
        }

        return inRangePokemon;
    }


    public Collection<WildPokemon> getAllWildPokemon() {

        return pokemonById.values();
    }

    public Map<UUID, WildPokemon> getPokemonById() {
        return pokemonById;
    }

    private void updatePokemonMovements(float delta) {
        for (List<WildPokemon> pokemonList : pokemonByChunk.values()) {
            for (WildPokemon pokemon : pokemonList) {
                pokemon.update(delta);
            }
        }
    }

    private enum TimeOfDay {
        DAY,
        NIGHT
    }

    public static class NetworkSyncData {
        public long lastUpdateTime;
        public Vector2 targetPosition;
        public String direction;
        public boolean isMoving;

        public NetworkSyncData() {
            this.lastUpdateTime = System.currentTimeMillis();
        }
    }
}

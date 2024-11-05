package io.github.pokemeetup.system.gameplay.overworld;

import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.pokemon.WildPokemon;
import io.github.pokemeetup.system.data.PokemonData;
import io.github.pokemeetup.system.data.WorldData;
import io.github.pokemeetup.system.gameplay.overworld.biomes.Biome;
import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;
import io.github.pokemeetup.utils.GameLogger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static io.github.pokemeetup.system.gameplay.overworld.World.WORLD_SIZE;

public class PokemonSpawnManager {// In PokemonSpawnManager.java

    public static final float POKEMON_DESPAWN_TIME = 300f; // Increased from 120 to 300 seconds
    private static final float SPAWN_CHECK_INTERVAL = 5f; // Increase from 2f
    private static final float BASE_SPAWN_CHANCE = 0.2f; // Reduce from 0.4f to 0.2f
    private static final float DISTANCE_SCALE = 0.1f; // Reduce from 0.2f to 0.1f
    private static final float MIN_SPAWN_RATE = 0.1f; // Minimum 30% chance
    private static final float MAX_SPAWN_RATE = 0.5f; // Maximum 95% chance
    private static final float SPECIAL_TIME_MULTIPLIER = 1.5f;
    private static final int SPAWN_ATTEMPTS = 30; // Increased attempts
    private static final float BASE_LEVEL_MIN = 2f;
    private static final float BASE_LEVEL_MAX = 4f;
    private static final Map<BiomeType, Map<TimeOfDay, String[]>> POKEMON_SPAWNS = new HashMap<>();
    private static final float DISTANCE_LEVEL_BONUS = 0.1f; // Level increase per distance unit
    private static final float MAX_LEVEL_BONUS = 9f; // Maximum additional levels from distance
    private static final float LEVEL_VARIANCE = 2f; // Random variance in levels
    private static final float MIN_SPAWN_DISTANCE = 1f; // Minimum tiles from spawn
    private static final float MAX_SPAWN_DISTANCE = 2000f; // Maximum tiles from spawn
    private static final float DISTANCE_BONUS_FACTOR = 0.3f; // Bonus for distance from sp
    private static final int MAX_POKEMON_PER_CHUNK = 8;
    private static final float MIN_POKEMON_SPACING = 1.5f * World.TILE_SIZE;
    private static final int MIN_CHUNKS_TRACKED = 9; // 3x3 around player
    private final TextureAtlas atlas;
    private final Random random;
    private final Map<Vector2, List<WildPokemon>> pokemonByChunk;  // Changed from chunkPokemo
    private final Map<UUID, WildPokemon> pokemonById;
    private final long worldSeed;
    private final int totalSpawned = 0;
    private final Map<UUID, NetworkSyncData> syncedPokemon = new ConcurrentHashMap<>();
    private final GameClient gameClient;
    private World world;
    private float spawnTimer = 0;
    public PokemonSpawnManager(World world, TextureAtlas atlas) {
        if (world == null) {
            throw new IllegalArgumentException("World cannot be null when initializing PokemonSpawnManager.");
        }
        this.world = world;
        this.gameClient = world.getGameClient();
        this.atlas = atlas;
        this.worldSeed = world.getWorldSeed();
        this.random = new Random(); // Do not seed with world seed
        this.pokemonByChunk = new ConcurrentHashMap<>();
        this.pokemonById = new ConcurrentHashMap<>();
        initializePokemonSpawns();
    }

    public void initializeFromServerData(WorldData serverWorldData) {
        if (serverWorldData == null) return;

        try {
            // Clear existing Pokemon
            pokemonById.clear();
            pokemonByChunk.clear();

            // Get Pokemon data from server world data
            Map<UUID, PokemonData.WildPokemonData> serverPokemon = serverWorldData.getPokemonData().getWildPokemon();

            for (Map.Entry<UUID, PokemonData.WildPokemonData> entry : serverPokemon.entrySet()) {
                PokemonData.WildPokemonData data = entry.getValue();
                Vector2 position = data.getPosition();

                // Create Pokemon from data
                WildPokemon pokemon = createPokemonFromData(data, position.x, position.y);
                if (pokemon != null) {
                    pokemon.setUuid(entry.getKey());

                    // Add to management collections
                    pokemonById.put(pokemon.getUuid(), pokemon);
                    Vector2 chunkPos = getChunkPosition(position.x, position.y);
                    addPokemonToChunk(pokemon, chunkPos);

                    GameLogger.info("Initialized Pokemon from server data: " + pokemon.getName() +
                        " at (" + position.x + "," + position.y + ")");
                }
            }

            GameLogger.info("Pokemon initialization complete. Total Pokemon: " + pokemonById.size());

        } catch (Exception e) {
            GameLogger.error("Failed to initialize Pokemon from server data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @SuppressWarnings("DefaultLocale")
    private float getAdjustedSpawnRate(float baseRate, float hourOfDay, Vector2 playerPosition) {
        // Start with base rate
        float rate = Math.max(baseRate, MIN_SPAWN_RATE);

        // Get distance from spawn in tiles
        Vector2 spawnPoint = getSpawnPoint();
        float distanceFromSpawn = Vector2.dst(
            playerPosition.x,
            playerPosition.y,
            spawnPoint.x,
            spawnPoint.y
        );

        // Calculate distance bonus (increases logarithmically with distance)
        float distanceBonus = 0;
        if (distanceFromSpawn > MIN_SPAWN_DISTANCE) {
            // Use log scale for smoother progression
            distanceBonus = (float) (Math.log10(distanceFromSpawn / MIN_SPAWN_DISTANCE) * DISTANCE_SCALE);
        }

        // Apply distance bonus
        rate += distanceBonus;

        // Time-based adjustments
        if (isSpecialSpawnTime(hourOfDay)) {
            rate *= SPECIAL_TIME_MULTIPLIER; // 50% bonus at dawn/dusk
        } else if ((hourOfDay >= 12 && hourOfDay <= 14)) {
            rate *= 0.9f; // Slight reduction at noon
        } else if (hourOfDay >= 0 && hourOfDay <= 2) {
            rate *= 0.9f; // Slight reduction at midnight
        }

        // Biome adjustments
        BiomeType biomeType = world.getBiomeAt(
            (int) playerPosition.x,
            (int) playerPosition.y
        ).getType();

        // Boost spawns in certain biomes
        switch (biomeType) {
            case FOREST:
                rate *= 1.2f; // 20% boost in forests
                break;
            case SNOW:
            case DESERT:
                rate *= 1.1f; // 10% boost in extreme biomes
                break;
        }

        // Cap the final rate
        float finalRate = MathUtils.clamp(rate, MIN_SPAWN_RATE, MAX_SPAWN_RATE);

        // Debug logging
        GameLogger.info(String.format(
            "Spawn Calculation - Base: %.2f, Distance(%.0f): +%.2f, Final: %.2f, Biome: %s",
            baseRate, distanceFromSpawn, distanceBonus, finalRate, biomeType
        ));

        return finalRate;
    }

    @SuppressWarnings("DefaultLocale")
    private void checkSpawns(Vector2 playerPosition) {
        double worldTimeInMinutes = world.getWorldData().getWorldTimeInMinutes();
        float hourOfDay = DayNightCycle.getHourOfDay(worldTimeInMinutes);
        float spawnChance = getAdjustedSpawnRate(BASE_SPAWN_CHANCE, hourOfDay, playerPosition);

        // Debug current state
        GameLogger.info(String.format(
            "Spawn Check - Rate: %.2f, Hour: %.1f, Current Pokemon: %d",
            spawnChance, hourOfDay, pokemonById.size()
        ));

        if (random.nextFloat() >= spawnChance) {
            GameLogger.info("Spawn check failed - roll: " + random.nextFloat() + " vs chance: " + spawnChance);
            return;
        }

        Vector2 spawnPoint = getSpawnPoint();
        float distanceFromSpawn = Vector2.dst(
            playerPosition.x, playerPosition.y,
            spawnPoint.x, spawnPoint.y
        );

        // Adjust spawn radius based on distance
// Set spawn radius to a reasonable distance around the player
        float minRadius = 3f;  // Minimum distance in tiles from the player
        float maxRadius = 10f; // Maximum distance in tiles from the player

// In checkSpawns method
        int maxSpawnsThisCheck = MathUtils.random(1, 2); // Reduce from (2, 4)

        int successfulSpawns = 0;
        int attempts = 0;
        int maxAttempts = 30;

        while (successfulSpawns < maxSpawnsThisCheck && attempts < maxAttempts) {
            attempts++;
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = minRadius + (maxRadius - minRadius) * random.nextDouble();

            int spawnTileX = (int) (playerPosition.x + Math.cos(angle) * distance);
            int spawnTileY = (int) (playerPosition.y + Math.sin(angle) * distance);

            if (!isValidSpawnLocation(spawnTileX, spawnTileY)) {
                continue;
            }

            Vector2 chunkPos = getChunkPosition(spawnTileX, spawnTileY);
            List<WildPokemon> pokemonList = pokemonByChunk.computeIfAbsent(
                chunkPos, k -> new ArrayList<>()
            );

            if (pokemonList.size() >= MAX_POKEMON_PER_CHUNK) {
                continue;
            }

            if (trySpawnPokemon(spawnTileX, spawnTileY)) {
                successfulSpawns++;
                GameLogger.info(String.format(
                    "Successfully spawned Pokemon %d/%d at (%d, %d)",
                    successfulSpawns, maxSpawnsThisCheck, spawnTileX, spawnTileY
                ));
            }
        }

        GameLogger.info(String.format(
            "Spawn Check Complete - Succeeded: %d/%d (Attempts: %d)",
            successfulSpawns, maxSpawnsThisCheck, attempts
        ));
    }

    private boolean isValidSpawnLocation(int tileX, int tileY) {
        // Use the simplified passability check
        boolean passable = world.isTilePassable(tileX, tileY);
        boolean pokemonPresent = world.isPokemonAt(tileX, tileY);

        GameLogger.info("Checking spawn location at (" + tileX + ", " + tileY + "): passable=" + passable + ", pokemonPresent=" + pokemonPresent);

        return passable && !pokemonPresent;
    }

    private boolean trySpawnPokemon(int tileX, int tileY) {
        try {
            GameLogger.info("Attempting spawn at: " + tileX + "," + tileY);

            Biome biome = world.getBiomeAt(tileX, tileY);
            String pokemonName = selectPokemonForBiome(biome);

            if (pokemonName == null) {
                GameLogger.info("Failed spawn - no Pokemon selected for biome: " + biome.getType());
                return false;
            }

            TextureRegion overworldSprite = atlas.findRegion(pokemonName.toUpperCase() + "_overworld");
            if (overworldSprite == null) {
                GameLogger.info("Failed spawn - no sprite for: " + pokemonName);
                return false;
            }

            Vector2 spawnPoint = getSpawnPoint();
            float distanceFromSpawn = Vector2.dst(
                tileX, tileY,
                spawnPoint.x, spawnPoint.y
            );

            int level = calculatePokemonLevel(distanceFromSpawn);

            // Convert to pixel coordinates
            float pixelX = tileX * World.TILE_SIZE;
            float pixelY = tileY * World.TILE_SIZE;

            WildPokemon pokemon = new WildPokemon(pokemonName, level, (int) pixelX, (int) pixelY, overworldSprite);

            // Add to management collections
            pokemonById.put(pokemon.getUuid(), pokemon);

            Vector2 chunkPos = getChunkPosition(pixelX, pixelY);
            addPokemonToChunk(pokemon, chunkPos);

            GameLogger.info(String.format("Successfully spawned %s (Level %d) at (%d, %d) - UUID: %s",
                pokemonName, level, tileX, tileY, pokemon.getUuid()));

            return true;

        } catch (Exception e) {
            GameLogger.error("Error spawning Pokemon: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    public void addPokemonToChunk(WildPokemon pokemon, Vector2 chunkPos) {
        try {
            List<WildPokemon> pokemonList = pokemonByChunk.computeIfAbsent(chunkPos, k -> new ArrayList<>());
            pokemonList.add(pokemon);
//            GameLogger.info(String.format("Added Pokemon %s to chunk (%d, %d) - Total in chunk: %d",
//                pokemon.getName(), (int)chunkPos.x, (int)chunkPos.y, pokemonList.size()));
        } catch (Exception e) {
            GameLogger.error("Error adding Pokemon to chunk: " + e.getMessage());
        }
    }

    private Vector2 getChunkPosition(float pixelX, float pixelY) {
        int chunkX = Math.floorDiv((int) pixelX, Chunk.CHUNK_SIZE * World.TILE_SIZE);
        int chunkY = Math.floorDiv((int) pixelY, Chunk.CHUNK_SIZE * World.TILE_SIZE);
        return new Vector2(chunkX, chunkY);
    }

    public void update(float delta, Vector2 playerPosition) {
        spawnTimer += delta;
        if (spawnTimer >= SPAWN_CHECK_INTERVAL) {
            spawnTimer = 0;

            // Make sure we're tracking chunks
            if (pokemonByChunk.size() < MIN_CHUNKS_TRACKED) {
                GameLogger.info("Low chunk count, forcing chunk initialization");
                // Initialize nearby chunks
                int radius = 1;
                Vector2 playerChunk = getChunkPosition(
                    playerPosition.x * World.TILE_SIZE,
                    playerPosition.y * World.TILE_SIZE
                );
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dy = -radius; dy <= radius; dy++) {
                        Vector2 chunkPos = new Vector2(playerChunk.x + dx, playerChunk.y + dy);
                        pokemonByChunk.computeIfAbsent(chunkPos, k -> new ArrayList<>());
                    }
                }
            }
            for (WildPokemon pokemon : pokemonById.values()) {
                if (pokemon.isDespawning()) {
                    if (pokemon.isExpired()) {
                        removePokemon(pokemon.getUuid());
                    }
                    continue;
                }
                NetworkSyncData syncData = syncedPokemon.get(pokemon.getUuid());
                if (syncData != null) {
                    // Handle network interpolation
                    updateNetworkedPokemon(pokemon, syncData, delta);
                } else {
                    // Local Pokemon update
                    pokemon.update(delta);

                    // Send updates if we're the host/owner
                    if (!gameClient.isSinglePlayer()) {
                        sendPokemonUpdate(pokemon);
                    }
                }
            }

            checkSpawns(playerPosition);
            removeExpiredPokemon();

            // Debug current state
            GameLogger.info(String.format("Current state - Pokemon: %d, Chunks: %d",
                pokemonById.size(), pokemonByChunk.size()));
        }

        updatePokemonMovements(delta);
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
        double worldTimeInMinutes = world.getWorldData().getWorldTimeInMinutes();
        float hourOfDay = DayNightCycle.getHourOfDay(worldTimeInMinutes);
        TimeOfDay timeOfDay = (hourOfDay >= 6 && hourOfDay < 18) ? TimeOfDay.DAY : TimeOfDay.NIGHT;

        Map<TimeOfDay, String[]> biomeSpawns = POKEMON_SPAWNS.get(biome.getType());
        if (biomeSpawns == null) {
            return getDefaultPokemon(timeOfDay);
        }

        String[] possiblePokemon = biomeSpawns.get(timeOfDay);
        if (possiblePokemon == null || possiblePokemon.length == 0) {
            return getDefaultPokemon(timeOfDay);
        }

        // Increased rare spawn chance
        if (isSpecialSpawnTime(hourOfDay)) {
            if (random.nextFloat() < 0.15f) { // Increased from 0.1 to 0.15
                return getRarePokemon(biome.getType(), timeOfDay);
            }
        } else if (random.nextFloat() < 0.05f) { // 5% chance for rare spawns at any time
            return getRarePokemon(biome.getType(), timeOfDay);
        }

        return possiblePokemon[random.nextInt(possiblePokemon.length)];
    }

    private int calculatePokemonLevel(float distanceFromSpawn) {
        // Base level range
        float baseMin = BASE_LEVEL_MIN;
        float baseMax = BASE_LEVEL_MAX;

        // Calculate distance bonus (capped at MAX_LEVEL_BONUS)
        float distanceBonus = Math.min(
            distanceFromSpawn * DISTANCE_LEVEL_BONUS,
            MAX_LEVEL_BONUS
        );

        // Add random variance
        float variance = (random.nextFloat() * 2 - 1) * LEVEL_VARIANCE;

        // Calculate final level
        float level = baseMin + random.nextFloat() * (baseMax - baseMin);
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
            "Caterpie", "Weedle", "Oddish", "Bellsprout", "Chikorita"
        });
        forestSpawns.put(TimeOfDay.NIGHT, new String[]{
            "Hoothoot", "Spinarak", "Oddish", "Venonat", "Gastly"
        });
        POKEMON_SPAWNS.put(BiomeType.FOREST, forestSpawns);

        // Snow biome
        Map<TimeOfDay, String[]> snowSpawns = new HashMap<>();
        snowSpawns.put(TimeOfDay.DAY, new String[]{
            "Swinub", "Snover", "Snorunt", "Spheal", "Cubchoo"
        });
        snowSpawns.put(TimeOfDay.NIGHT, new String[]{
            "Sneasel", "Delibird", "Snorunt", "Glalie", "Snover"
        });
        POKEMON_SPAWNS.put(BiomeType.SNOW, snowSpawns);
        // Desert biome
        Map<TimeOfDay, String[]> desertSpawns = new HashMap<>();
        snowSpawns.put(TimeOfDay.DAY, new String[]{
            "Trapinch", "Sandshrew", "Geodude", "Baltoy", "Cacnea"
        });
        snowSpawns.put(TimeOfDay.NIGHT, new String[]{
            "Zubat", "Voltorb", "Cacnea"
        });
        POKEMON_SPAWNS.put(BiomeType.DESERT, desertSpawns);
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

    private void spawnPokemonAt(String name, int tileX, int tileY, TextureRegion overworldSprite) {
        try {
            // Calculate distance-based level
            Vector2 spawnPoint = getSpawnPoint();
            float distanceFromSpawn = Vector2.dst(
                tileX,
                tileY,
                spawnPoint.x,
                spawnPoint.y
            );

            int level = calculatePokemonLevel(distanceFromSpawn);

            WildPokemon pokemon = new WildPokemon(name, level, tileX, tileY, overworldSprite);
            pokemonById.put(pokemon.getUuid(), pokemon);
            addPokemonToChunk(pokemon, getChunkPosition(tileX, tileY));

            GameLogger.info("Spawned level " + level + " " + name + " at distance: " +
                String.format("%.1f", distanceFromSpawn) + " tiles from spawn");

        } catch (Exception e) {
            GameLogger.error("Failed to spawn Pokémon: " + e.getMessage());
            e.printStackTrace();
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

    public Vector2 getSpawnPoint() {
        // Assuming the spawn point is at the center of the world in tiles
        float centerX = (WORLD_SIZE / 2f);
        float centerY = (WORLD_SIZE / 2f);
        return new Vector2(centerX, centerY);
    }

    private WildPokemon createPokemonFromData(PokemonData.WildPokemonData data, float x, float y) {
        TextureRegion overworldSprite = atlas.findRegion(data.getName().toUpperCase() + "_overworld");
        if (overworldSprite != null) {
            WildPokemon pokemon = new WildPokemon(data.getName(), data.getLevel(), (int) x, (int) y, overworldSprite);
            // Apply additional data
            pokemon.setCurrentHp(data.getCurrentHp());
            pokemon.setPrimaryType(data.getPrimaryType());
            pokemon.setSecondaryType(data.getSecondaryType());
            return pokemon;
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

    private String getRarePokemon(BiomeType biomeType, TimeOfDay timeOfDay) {
        switch (biomeType) {
            case PLAINS:
                return timeOfDay == TimeOfDay.DAY ? "Scyther" : "Chansey";
            case FOREST:
                return timeOfDay == TimeOfDay.DAY ? "Pinsir" : "Gengar";
            case SNOW:
                return timeOfDay == TimeOfDay.DAY ? "Articuno" : "Glaceon";
            default:
                return timeOfDay == TimeOfDay.DAY ? "Dratini" : "Umbreon";
        }
    }

    private float getTimeBasedSpawnRate(float baseRate, float hourOfDay) {
        // Increased spawn rates during dawn and dusk
        if (isSpecialSpawnTime(hourOfDay)) {
            return baseRate * 1.5f;
        }

        // Slightly reduced spawns during peak day/night
        if (hourOfDay >= 12 && hourOfDay <= 14) { // Noon
            return baseRate * 0.8f;
        }
        if (hourOfDay >= 0 && hourOfDay <= 2) { // Midnight
            return baseRate * 0.8f;
        }

        return baseRate;
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

    public WildPokemon getNearestPokemon(float centerX, float centerY, float maxRange) {
        float shortestDistance = maxRange * maxRange;
        WildPokemon nearestPokemon = null;

        Collection<WildPokemon> inRange = getPokemonInRange(centerX, centerY, maxRange);

        for (WildPokemon pokemon : inRange) {
            float dx = pokemon.getX() - centerX;
            float dy = pokemon.getY() - centerY;
            float distanceSquared = dx * dx + dy * dy;

            if (distanceSquared < shortestDistance) {
                shortestDistance = distanceSquared;
                nearestPokemon = pokemon;
            }
        }

        return nearestPokemon;
    }

    public Collection<WildPokemon> getPokemonInArea(float minX, float minY, float maxX, float maxY) {
        // Calculate chunk boundaries
        Vector2 minChunk = getChunkPosition(minX, minY);
        Vector2 maxChunk = getChunkPosition(maxX, maxY);

        List<WildPokemon> areaPokemon = new ArrayList<>();

        for (int cx = (int) minChunk.x; cx <= maxChunk.x; cx++) {
            for (int cy = (int) minChunk.y; cy <= maxChunk.y; cy++) {
                List<WildPokemon> chunkPokemon = this.pokemonByChunk.get(new Vector2(cx, cy));

                if (chunkPokemon != null) {
                    for (WildPokemon pokemon : chunkPokemon) {
                        if (pokemon.getX() >= minX && pokemon.getX() <= maxX &&
                            pokemon.getY() >= minY && pokemon.getY() <= maxY) {
                            areaPokemon.add(pokemon);
                        }
                    }
                }
            }
        }

        return areaPokemon;
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

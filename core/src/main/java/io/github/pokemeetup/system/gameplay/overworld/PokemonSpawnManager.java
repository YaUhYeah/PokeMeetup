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
    private static final float BASE_SPAWN_RATE = 0.45f;  // Base 30% chance per check
    private static final float SPAWN_CHECK_INTERVAL = 5.0f;  // Check every 5 seconds
    private static final float BASE_LEVEL_MIN = 2f;
    private static final float BASE_LEVEL_MAX = 4f;
    private static final Map<BiomeType, Map<TimeOfDay, String[]>> POKEMON_SPAWNS = new HashMap<>();
    private static final float DISTANCE_LEVEL_BONUS = 0.01f; // Level increase per distance unit
    private static final float MAX_LEVEL_BONUS = 4f; // Maximum additional levels from distance
    private static final float LEVEL_VARIANCE = 2f; // Random variance in levels
    private static final int MIN_CHUNKS_TRACKED = 9; // 3x3 around player
    private static final float MIN_SPAWN_DISTANCE_PIXELS = 5 * World.TILE_SIZE;
    private static final float MAX_SPAWN_DISTANCE_PIXELS = 15 * World.TILE_SIZE;
    private static final int MAX_POKEMON_PER_CHUNK = 3;
    private static final float MIN_POKEMON_SPACING = World.TILE_SIZE * 2;
    private static final float MIN_SPAWN_DISTANCE = 5 * World.TILE_SIZE;
    private static final float MAX_SPAWN_DISTANCE = 15 * World.TILE_SIZE;
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

    private void checkSpawns(Vector2 playerPos) {
        GameLogger.error("=== Starting spawn check ===");

        if (random.nextFloat() > BASE_SPAWN_RATE) {
            GameLogger.error("Spawn check failed random roll");
            return;
        }

        Set<Vector2> loadedChunks = getLoadedChunksAroundPlayer(playerPos);
        if (loadedChunks.isEmpty()) {
            GameLogger.error("No loaded chunks found around player");
            return;
        }

        GameLogger.error(String.format(
            "Found %d loaded chunks around player at (%.1f,%.1f)",
            loadedChunks.size(), playerPos.x, playerPos.y
        ));

        // Try to spawn in a valid location
        int attempts = 10;
        while (attempts > 0) {
            attempts--;

            // Calculate spawn position in pixels
            float angle = random.nextFloat() * MathUtils.PI2;
            float distance = MathUtils.random(MIN_SPAWN_DISTANCE_PIXELS, MAX_SPAWN_DISTANCE_PIXELS);
            float spawnPixelX = playerPos.x*TILE_SIZE+ MathUtils.cos(angle) * distance;
            float spawnPixelY = playerPos.y *TILE_SIZE+ MathUtils.sin(angle) * distance;

            Vector2 chunkPos = getChunkPosition(spawnPixelX, spawnPixelY);

            GameLogger.error(String.format(
                "Attempt %d: Trying spawn at (%.1f,%.1f) in chunk (%.1f,%.1f)",
                10 - attempts, spawnPixelX, spawnPixelY, chunkPos.x, chunkPos.y
            ));

            if (!loadedChunks.contains(chunkPos)) {
                GameLogger.error("Chunk not loaded, skipping");
                continue;
            }

            // Check chunk capacity
            List<WildPokemon> chunkPokemon = pokemonByChunk.getOrDefault(chunkPos, new ArrayList<>());
            if (chunkPokemon.size() >= MAX_POKEMON_PER_CHUNK) {
                GameLogger.error(String.format(
                    "Chunk at (%.1f,%.1f) full with %d Pokemon",
                    chunkPos.x, chunkPos.y, chunkPokemon.size()
                ));
                continue;
            }

            // Validate spawn position
            if (isValidSpawnPosition(spawnPixelX, spawnPixelY)) {
                GameLogger.error("Valid spawn position found, spawning Pokemon");
                spawnPokemon(spawnPixelX, spawnPixelY, chunkPos);
                break;
            } else {
                GameLogger.error("Invalid spawn position, retrying");
            }
        }

        if (attempts == 0) {
            GameLogger.error("Failed to find valid spawn position after all attempts");
        }
    }


    private boolean isValidSpawnPosition(float pixelX, float pixelY) {
        // Convert to tile coordinates for passability check
        int tileX = (int)(pixelX / TILE_SIZE);
        int tileY = (int)(pixelY / TILE_SIZE);

        if (world == null) {
            GameLogger.error("World reference is null in spawn validation");
            return false;
        }

        // Check if tile is passable
        if (!world.isPassable(tileX, tileY)) {
            GameLogger.info(String.format(
                "Tile (%d,%d) is not passable", tileX, tileY
            ));
            return false;
        }

        // Check distance from other Pokemon
        Collection<WildPokemon> nearby = getPokemonInRange(pixelX, pixelY, MIN_POKEMON_SPACING);
        if (!nearby.isEmpty()) {
            GameLogger.info(String.format(
                "Found %d Pokemon too close to spawn position",
                nearby.size()
            ));
            return false;
        }

        // Check chunk loaded
        Vector2 chunkPos = getChunkPosition(pixelX, pixelY);
        if (!world.getChunks().containsKey(chunkPos)) {
            GameLogger.info(String.format(
                "Chunk at (%.1f,%.1f) not loaded",
                chunkPos.x, chunkPos.y
            ));
            return false;
        }

        GameLogger.info(String.format(
            "Position validated: Tile(%d,%d) Pixel(%.1f,%.1f)",
            tileX, tileY, pixelX, pixelY
        ));
        return true;
    }


    private int calculatePokemonLevel(float x, float y) {
        // Calculate distance from world center
        float centerX = 0;
        float centerY = 0;
        float distance = Vector2.dst(x, y, centerX, centerY);

        // Base level increases with distance
        float baseLevel = 2 + (distance / (World.TILE_SIZE * 50));

        // Add random variance
        float variance = MathUtils.random(-LEVEL_VARIANCE, LEVEL_VARIANCE);

        return MathUtils.round(MathUtils.clamp(baseLevel + variance, 1, 100));
    }


    public Collection<WildPokemon> getPokemonInRange(float centerPixelX, float centerPixelY, float rangePixels) {
        List<WildPokemon> inRange = new ArrayList<>();
        float rangeSquared = rangePixels * rangePixels;

        // Get relevant chunks
        int chunkRadius = (int)Math.ceil(rangePixels / (World.CHUNK_SIZE * World.TILE_SIZE)) + 1;
        Vector2 centerChunk = getChunkPosition(centerPixelX, centerPixelY);

        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dy = -chunkRadius; dy <= chunkRadius; dy++) {
                Vector2 checkChunk = new Vector2(centerChunk.x + dx, centerChunk.y + dy);
                List<WildPokemon> pokemonInChunk = pokemonByChunk.get(checkChunk);

                if (pokemonInChunk != null) {
                    for (WildPokemon pokemon : pokemonInChunk) {
                        float dx2 = pokemon.getX() - centerPixelX;
                        float dy2 = pokemon.getY() - centerPixelY;
                        if (dx2 * dx2 + dy2 * dy2 <= rangeSquared) {
                            inRange.add(pokemon);
                        }
                    }
                }
            }
        }

        return inRange;
    }

    private void spawnPokemon(float pixelX, float pixelY, Vector2 chunkPos) {
        try {
            int tileX = (int)(pixelX / World.TILE_SIZE);
            int tileY = (int)(pixelY / World.TILE_SIZE);

            Biome biome = world.getBiomeAt(tileX, tileY);
            String pokemonName = selectPokemonForBiome(biome);
            if (pokemonName == null) return;

            TextureRegion sprite = atlas.findRegion(pokemonName.toUpperCase() + "_overworld");
            if (sprite == null) {
                GameLogger.error("Failed to load sprite for " + pokemonName);
                return;
            }

            // Snap to grid
            float snappedX = Math.round(pixelX / World.TILE_SIZE) * World.TILE_SIZE;
            float snappedY = Math.round(pixelY / World.TILE_SIZE) * World.TILE_SIZE;

            WildPokemon pokemon = new WildPokemon(
                pokemonName,
                calculatePokemonLevel(snappedX, snappedY),
                (int)snappedX,
                (int)snappedY,
                sprite
            );

            // Initialize world reference and AI
            pokemon.setWorld(world);
            pokemon.getAi().enterIdleState(); // Start in idle state

            // Add to collections
            pokemonById.put(pokemon.getUuid(), pokemon);
            pokemonByChunk.computeIfAbsent(chunkPos, k -> new ArrayList<>()).add(pokemon);

            // Update collision box
            pokemon.updateBoundingBox();

            GameLogger.info(String.format(
                "Spawned %s at (%.1f,%.1f) with AI:%b World:%b",
                pokemonName, snappedX, snappedY,
                pokemon.getAi() != null,
                pokemon.getWorld() != null
            ));

        } catch (Exception e) {
            GameLogger.error("Failed to spawn Pokemon: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void update(float delta, Vector2 playerPosition) {
        spawnTimer += delta;
        if (spawnTimer >= SPAWN_CHECK_INTERVAL) {
            spawnTimer = 0;
            checkSpawns(playerPosition);
            removeExpiredPokemon();
        }

        // Update existing Pokemon
        for (WildPokemon pokemon : pokemonById.values()) {
            try {
                pokemon.update(delta, world);
            } catch (Exception e) {
                GameLogger.error("Error updating " + pokemon.getName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    public void removePokemon(UUID pokemonId) {
        WildPokemon pokemon = pokemonById.remove(pokemonId);
        if (pokemon != null) {
            Vector2 chunkPos = getChunkPosition(pokemon.getX(), pokemon.getY());
            List<WildPokemon> pokemonList = pokemonByChunk.get(chunkPos);
            if (pokemonList != null) {
                pokemonList.remove(pokemon);
                if (pokemonList.isEmpty()) {
                    pokemonByChunk.remove(chunkPos);
                }
            }
            syncedPokemon.remove(pokemonId);

            // Network update if multiplayer
            if (!gameClient.isSinglePlayer()) {
                gameClient.sendPokemonDespawn(pokemonId);
            }
        }
    }
    private Set<Vector2> getLoadedChunksAroundPlayer(Vector2 playerPixelPos) {
        Set<Vector2> loadedChunks = new HashSet<>();

        // Debug player position and conversion
        GameLogger.info(String.format(
            "Player position in pixels: (%.1f,%.1f)",
            playerPixelPos.x, playerPixelPos.y
        ));

        int playerTileX = (int)(playerPixelPos.x / TILE_SIZE);
        int playerTileY = (int)(playerPixelPos.y / TILE_SIZE);

        GameLogger.info(String.format(
            "Player position in tiles: (%d,%d)",
            playerTileX, playerTileY
        ));

        Vector2 playerChunk = getChunkPosition(playerPixelPos.x*TILE_SIZE, playerPixelPos.y*TILE_SIZE);

        GameLogger.info(String.format(
            "Player chunk position: (%.1f,%.1f)",
            playerChunk.x, playerChunk.y
        ));

        // Debug world state
        if (world == null) {
            GameLogger.error("World reference is null!");
            return loadedChunks;
        }

        Map<Vector2, Chunk> worldChunks = world.getChunks();
        GameLogger.info(String.format(
            "Total chunks in world: %d",
            worldChunks.size()
        ));

        // Log some nearby chunk positions that should be loaded
        GameLogger.info("Checking chunks in radius 1:");
        int radius = 1;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                Vector2 checkPos = new Vector2(playerChunk.x + dx, playerChunk.y + dy);
                boolean isLoaded = worldChunks.containsKey(checkPos);
                GameLogger.info(String.format(
                    "Chunk at offset (%d,%d) -> (%.1f,%.1f): %s",
                    dx, dy, checkPos.x, checkPos.y,
                    isLoaded ? "LOADED" : "NOT LOADED"
                ));

                if (isLoaded) {
                    loadedChunks.add(checkPos);
                }
            }
        }

        return loadedChunks;
    }

    private Vector2 getChunkPosition(float pixelX, float pixelY) {
        int chunkX = Math.floorDiv((int)pixelX, World.CHUNK_SIZE * TILE_SIZE);
        int chunkY = Math.floorDiv((int)pixelY, World.CHUNK_SIZE * TILE_SIZE);

        GameLogger.info(String.format(
            "Converting pixels (%.1f,%.1f) to chunk: (%d,%d) [CHUNK_SIZE=%d, TILE_SIZE=%d]",
            pixelX, pixelY, chunkX, chunkY,
            World.CHUNK_SIZE, TILE_SIZE
        ));

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
        POKEMON_SPAWNS.put(BiomeType.RAIN_FOREST, rainforestSpawns);

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
//        POKEMON_SPAWNS.put(BiomeType.CHERRY_BLOSSOM, cherrySpawns);
//
//        // Safari biome
//        Map<TimeOfDay, String[]> safariSpawns = new HashMap<>();
//        safariSpawns.put(TimeOfDay.DAY, new String[]{
//            "Tauros", "Kangaskhan", "Girafarig", "Zangoose", "Bouffalant",
//            "Miltank", "Zebstrika", "Pyroar", "Donphan", "Heliolisk"
//        });
//        safariSpawns.put(TimeOfDay.NIGHT, new String[]{
//            "Luxio", "Noctowl", "Seviper", "Persian", "Liepard",
//            "Zoroark", "Absol"
//        });
//        POKEMON_SPAWNS.put(BiomeType.SAFARI, safariSpawns);
//
//        // Swamp biome
//        Map<TimeOfDay, String[]> swampSpawns = new HashMap<>();
//        swampSpawns.put(TimeOfDay.DAY, new String[]{
//            "Croagunk", "Politoed", "Quagsire", "Lotad", "Yanma",
//            "Carnivine", "Tympole", "Froakie", "Goomy"
//        });
//        swampSpawns.put(TimeOfDay.NIGHT, new String[]{
//            "Wooper", "Stunky", "Gastrodon", "Skorupi", "Toxicroak",
//            "Croagunk", "Marshtomp"
//        });
//        POKEMON_SPAWNS.put(BiomeType.SWAMP, swampSpawns);
//
//        // Volcano biome
//        Map<TimeOfDay, String[]> volcanoSpawns = new HashMap<>();
//        volcanoSpawns.put(TimeOfDay.DAY, new String[]{
//            "Numel", "Slugma", "Torkoal", "Heatmor", "Camerupt",
//            "Magmar", "Turtonator", "Salandit"
//        });
//        volcanoSpawns.put(TimeOfDay.NIGHT, new String[]{
//            "Slugma", "Magmar", "Houndour", "Magby", "Magcargo",
//            "Torchic", "Torkoal"
//        });
//        POKEMON_SPAWNS.put(BiomeType.VOLCANO, volcanoSpawns);
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

    public Collection<WildPokemon> getAllWildPokemon() {

        return pokemonById.values();
    }



    private void sendSpawnUpdate(WildPokemon pokemon) {
        NetworkProtocol.WildPokemonSpawn spawnUpdate = new NetworkProtocol.WildPokemonSpawn();
        spawnUpdate.uuid = pokemon.getUuid();
        spawnUpdate.x = pokemon.getX();
        spawnUpdate.y = pokemon.getY();
        spawnUpdate.timestamp = System.currentTimeMillis();
        gameClient.sendPokemonSpawn(spawnUpdate);
    }

    private void sendDespawnUpdate(UUID pokemonId) {
        NetworkProtocol.PokemonDespawn despawnUpdate = new NetworkProtocol.PokemonDespawn();
        despawnUpdate.uuid = pokemonId;
        gameClient.sendPokemonDespawn(despawnUpdate.uuid);
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
    public void addPokemonToChunk(WildPokemon pokemon, Vector2 chunkPos) {
        try {
            List<WildPokemon> pokemonList = pokemonByChunk.computeIfAbsent(chunkPos, k -> new ArrayList<>());
            pokemonList.add(pokemon);
            pokemonById.put(pokemon.getUuid(), pokemon);
        } catch (Exception e) {
            GameLogger.error("Error adding Pokemon to chunk: " + e.getMessage());
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

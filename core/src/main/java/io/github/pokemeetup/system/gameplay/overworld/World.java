package io.github.pokemeetup.system.gameplay.overworld;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import io.github.pokemeetup.audio.AudioManager;
import io.github.pokemeetup.blocks.BlockManager;
import io.github.pokemeetup.managers.BiomeManager;
import io.github.pokemeetup.managers.BiomeRenderer;
import io.github.pokemeetup.managers.BiomeTransitionResult;
import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.multiplayer.network.NetworkedWorldObject;
import io.github.pokemeetup.pokemon.Pokemon;
import io.github.pokemeetup.pokemon.WildPokemon;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.data.PlayerData;
import io.github.pokemeetup.system.gameplay.SpawnPointValidator;
import io.github.pokemeetup.system.gameplay.overworld.biomes.Biome;
import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;
import io.github.pokemeetup.system.data.WorldData;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.PerlinNoise;
import io.github.pokemeetup.utils.storage.DesktopFileSystem;
import io.github.pokemeetup.utils.storage.GameFileSystem;
import io.github.pokemeetup.utils.textures.TextureManager;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class World {
    public Map<Vector2, Future<Chunk>> getLoadingChunks() {
        return loadingChunks;
    }

    public static final int WORLD_SIZE = 1600;
    public static final int TILE_SIZE = 32;
    public static final int CHUNK_SIZE = 16;
    public static final float INTERACTION_RANGE = TILE_SIZE * 1.5f;
    public static final int INITIAL_LOAD_RADIUS = 2;   // Reduced from 4
    public static final int HALF_WORLD_SIZE = WORLD_SIZE / 2;
    private static final int INITIAL_CHUNKS_PER_FRAME = 2; // Adjust as needed
    private static final float COLOR_TRANSITION_SPEED = 2.0f; // Adjust for faster/slower transitions
    public static int DEFAULT_X_POSITION = 0;
    public static int DEFAULT_Y_POSITION = 0;    // Adjust these constants for biome size and transitions
    private Map<Vector2, Chunk> chunks;
    private final GameClient gameClient;
    private Map<Vector2, Future<Chunk>> loadingChunks = new HashMap<>();
    private Queue<Vector2> initialChunkLoadQueue = new LinkedList<>();
    private ExecutorService chunkLoadExecutor = Executors.newFixedThreadPool(4);
    private BiomeManager biomeManager;
    private BiomeRenderer biomeRenderer;
    private Player player;
    private PlayerData currentPlayerData;
    private WorldObject nearestPokeball;
    private PerlinNoise biomeNoiseGenerator;
    private long lastPlayed;
    private BlockManager blockManager;
    private String name;
    private WorldData worldData;
    private PokemonSpawnManager pokemonSpawnManager;
    private long worldSeed;
    private WorldObject.WorldObjectManager objectManager;
    private Color currentWorldColor;
    private boolean initialChunksLoaded = false;
    private WeatherSystem weatherSystem;
    private WeatherAudioSystem weatherAudioSystem;
    private BiomeTransitionResult currentBiomeTransition;
    private float temperature = 20.0f; // Default temperature
    private boolean initialized = false;
    private Color previousWorldColor;
    private float colorTransitionProgress = 1.0f;

    public World(WorldData worldData, GameClient gameClient) {
        this.worldData = worldData;
        this.gameClient = gameClient;
        this.name = worldData.getName();
        this.worldSeed = worldData.getConfig().getSeed();
        this.biomeManager = new BiomeManager(this.worldSeed);
        this.biomeRenderer = new BiomeRenderer();
        this.chunks = new ConcurrentHashMap<>();
        this.loadingChunks = new ConcurrentHashMap<>();
        this.initialChunkLoadQueue = new LinkedList<>();
        this.chunkLoadExecutor = Executors.newFixedThreadPool(4);

        this.objectManager = new WorldObject.WorldObjectManager(worldSeed, gameClient);
        this.pokemonSpawnManager = new PokemonSpawnManager(this, TextureManager.pokemonoverworld, gameClient);

        // Load chunks and objects from worldData
        loadChunksFromWorldData();

        // Initialize other necessary components
        this.weatherSystem = new WeatherSystem();
        this.weatherAudioSystem = new WeatherAudioSystem(AudioManager.getInstance());
    }

    public World(String name, long seed, GameClient gameClient, BiomeManager manager) {
        this.biomeManager = manager;
        this.gameClient = gameClient;
        this.biomeRenderer = new BiomeRenderer();
        this.chunks = new ConcurrentHashMap<>();
        this.worldData = new WorldData(name);
        this.worldSeed = seed;
        this.name = name;
        this.blockManager = new BlockManager(TextureManager.items);
        this.biomeNoiseGenerator = new PerlinNoise((int) seed);

        this.weatherSystem = new WeatherSystem();
        this.weatherAudioSystem = new WeatherAudioSystem(AudioManager.getInstance());
        this.currentBiomeTransition = null;
        // New noise generators
        PerlinNoise temperatureNoiseGenerator = new PerlinNoise((int) (seed + 100));
        PerlinNoise moistureNoiseGenerator = new PerlinNoise((int) (seed + 200));
        this.pokemonSpawnManager = new PokemonSpawnManager(this, TextureManager.pokemonoverworld, gameClient);
        Map<String, NetworkedWorldObject> syncedObjects = new ConcurrentHashMap<>();
        TextureRegion treeTexture = TextureManager.tiles.findRegion("tree");
        TextureRegion pokeballTexture = TextureManager.tiles.findRegion("pokeball");  // Debug texture loading
        if (treeTexture == null) {
            GameLogger.info("Failed to load tree texture from atlas");
        }
        if (pokeballTexture == null) {
            GameLogger.info("Failed to load pokeball texture from atlas");
        }
        objectManager = new WorldObject.WorldObjectManager(worldSeed, gameClient);

        initializeChunksAroundOrigin();
    }

    private void loadChunksFromWorldData() {
        Map<Vector2, Chunk> worldChunks = worldData.getChunks();
        if (worldChunks != null) {
            this.chunks.putAll(worldChunks);
        }

        Map<Vector2, List<WorldObject>> worldObjects = worldData.getChunkObjects();
        if (worldObjects != null) {
            for (Map.Entry<Vector2, List<WorldObject>> entry : worldObjects.entrySet()) {
                Vector2 chunkPos = entry.getKey();
                List<WorldObject> objects = entry.getValue();

                objectManager.setObjectsForChunk(chunkPos, objects);
            }
        }
    }

    public void initializeWorldFromData(WorldData worldData) {
        setWorldData(worldData);
        loadChunksFromWorldData();
    }

    public boolean areAllChunksLoaded() {
        int totalChunks = (INITIAL_LOAD_RADIUS * 2 + 1) * (INITIAL_LOAD_RADIUS * 2 + 1);
        int loadedChunks = 0;

        for (int dx = -INITIAL_LOAD_RADIUS; dx <= INITIAL_LOAD_RADIUS; dx++) {
            for (int dy = -INITIAL_LOAD_RADIUS; dy <= INITIAL_LOAD_RADIUS; dy++) {
                Vector2 chunkPos = new Vector2(dx, dy);
                if (chunks.containsKey(chunkPos)) {
                    loadedChunks++;
                } else {
                    String missing = String.format("Missing chunk at: (%.1f,%.1f) - Distance from origin: %.1f",
                        chunkPos.x, chunkPos.y, chunkPos.len());
                    GameLogger.info(missing);
                }
            }
        }
        if (chunks.size() != loadedChunks) {
            GameLogger.error("Chunk count mismatch - Map size: " + chunks.size() +
                ", Counted: " + loadedChunks);
        }

        GameLogger.info("Chunks loaded: " + loadedChunks + "/" + totalChunks);
        return loadedChunks == totalChunks;
    }

    public void forceLoadMissingChunks() {
        GameLogger.info("Forcing load of any missing chunks...");
        int loaded = 0;

        for (int dx = -INITIAL_LOAD_RADIUS; dx <= INITIAL_LOAD_RADIUS; dx++) {
            for (int dy = -INITIAL_LOAD_RADIUS; dy <= INITIAL_LOAD_RADIUS; dy++) {
                Vector2 chunkPos = new Vector2(dx, dy);
                if (!chunks.containsKey(chunkPos)) {
                    try {
                        Chunk chunk = loadOrGenerateChunk(chunkPos);
                        if (chunk != null) {
                            chunks.put(chunkPos, chunk);
                            loaded++;
                        }
                    } catch (Exception e) {
                        GameLogger.error("Failed to force load chunk at " + chunkPos +
                            ": " + e.getMessage());
                    }
                }
            }
        }

        GameLogger.info("Force loaded " + loaded + " missing chunks");
    }

    public WildPokemon getNearestInteractablePokemon(Player player) {
        // Convert player position to pixels
        float playerPixelX = player.getTileX() * TILE_SIZE + (Player.FRAME_WIDTH / 2f);
        float playerPixelY = player.getTileY() * TILE_SIZE + (Player.FRAME_HEIGHT / 2f);

        float checkX = playerPixelX;
        float checkY = playerPixelY;
        float interactionDistance = TILE_SIZE * 1.5f;

        // Adjust check position based on facing direction
        switch (player.getDirection()) {
            case "up":
                checkY += interactionDistance;
                break;
            case "down":
                checkY -= interactionDistance;
                break;
            case "left":
                checkX -= interactionDistance;
                break;
            case "right":
                checkX += interactionDistance;
                break;
        }

        WildPokemon nearest = null;
        float shortestDistance = interactionDistance;

        Collection<WildPokemon> nearbyPokemon = pokemonSpawnManager.getPokemonInRange(
            checkX, checkY, interactionDistance
        );

        for (WildPokemon pokemon : nearbyPokemon) {
            float distance = Vector2.dst(
                checkX,
                checkY,
                pokemon.getX(),
                pokemon.getY()
            );

            if (distance < shortestDistance) {
                shortestDistance = distance;
                nearest = pokemon;
            }
        }

        return nearest;
    }

    public long getWorldSeed() {
        return worldSeed;
    }

    public BlockManager getBlockManager() {

        return blockManager;
    }

    private void checkLoadedChunks() {
        Iterator<Map.Entry<Vector2, Future<Chunk>>> iterator = loadingChunks.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<Vector2, Future<Chunk>> entry = iterator.next();
            Future<Chunk> future = entry.getValue();

            if (future.isDone()) {
                try {
                    Chunk chunk = future.get();
                    if (chunk != null) {
                        chunks.put(entry.getKey(), chunk);
                    }
                } catch (Exception e) {
                    GameLogger.error("Error retrieving chunk at " + entry.getKey() + ": " + e.getMessage());
                    e.printStackTrace();
                }
                iterator.remove();
            }
        }
    }

    public long getLastPlayed() {
        return lastPlayed;
    }

    public void setLastPlayed(long lastPlayed) {
        this.lastPlayed = lastPlayed;
    }

    public Player getPlayer() {
        return player;
    }

    public void setPlayer(Player player) {
        this.player = player;
        // Initialize player data
        if (player != null) {
            this.currentPlayerData = new PlayerData(player.getUsername());
            this.currentPlayerData.updateFromPlayer(player);
        }
    }

    public void saveChunkData(Vector2 chunkPos, Chunk chunk, boolean isMultiplayer) {
        if (GameFileSystem.getInstance().getDelegate() instanceof DesktopFileSystem && isMultiplayer) {
            return;
        }
        try {
            String baseDir = isMultiplayer ?
                "worlds/" + name + "/chunks/" :
                "worlds/singleplayer/" + name + "/chunks/";

            FileHandle saveDir = Gdx.files.local(baseDir);
            if (!saveDir.exists()) {
                saveDir.mkdirs();
            }

            List<WorldObject> objects = objectManager.getObjectsForChunk(chunkPos);
            List<WorldObjectData> objectDataList = objects.stream()
                .map(WorldObjectData::new)
                .collect(Collectors.toList());

            ChunkData chunkData = new ChunkData(chunk, objectDataList, isMultiplayer);

            String filename = String.format("chunk_%d_%d.json", (int) chunkPos.x, (int) chunkPos.y);
            FileHandle chunkFile = saveDir.child(filename);

            Json json = new Json();
            json.setOutputType(JsonWriter.OutputType.json);
            chunkFile.writeString(json.prettyPrint(chunkData), false);
        } catch (Exception e) {
            GameLogger.info("Error saving chunk data: " + e.getMessage());
        }
    }

    private Chunk loadChunkData(Vector2 chunkPos, boolean isMultiplayer) {
        try {
            String baseDir = isMultiplayer ?
                "worlds/" + name + "/chunks/" :
                "worlds/singleplayer/" + name + "/chunks/";

            @SuppressWarnings("DefaultLocale") String filename = String.format("chunk_%d_%d.json", (int) chunkPos.x, (int) chunkPos.y);
            FileHandle chunkFile = Gdx.files.local(baseDir + filename);

            if (!chunkFile.exists()) {
                return null;
            }

            Json json = new Json();
            ChunkData chunkData = json.fromJson(ChunkData.class, chunkFile.readString());
            chunkData.validate();

            Biome biome = biomeManager.getBiome(chunkData.biomeType);
            Chunk chunk = new Chunk(chunkData.x, chunkData.y, biome, worldSeed, biomeManager);

            // Restore tile data
            int[][] chunkTileData = chunk.getTileData();
            for (int i = 0; i < Chunk.CHUNK_SIZE; i++) {
                System.arraycopy(chunkData.tileData[i], 0, chunkTileData[i], 0, Chunk.CHUNK_SIZE);
            }

            // Restore objects
            for (WorldObjectData objData : chunkData.objects) {
                WorldObject obj = objectManager.createObject(objData.type, objData.x, objData.y);
                objectManager.addObjectToChunk(obj);
            }
            return chunk;

        } catch (Exception e) {
            GameLogger.info("Error loading chunk data: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public Map<Vector2, Chunk> getChunks() {
        return chunks;
    }

    public void updatePlayerData() {
        if (player != null && currentPlayerData != null) {
            currentPlayerData.updateFromPlayer(player);
        }
    }

    public PlayerData getPlayerData() {
        updatePlayerData();
        return currentPlayerData;
    }

    public void setPlayerData(PlayerData playerData) {
    }

    public void save() {
        if (player != null) {
            updatePlayerData();
            GameLogger.info("Saving latest player state:");
            GameLogger.info("Position: " + currentPlayerData.getX() + "," + currentPlayerData.getY());
            GameLogger.info("Inventory: " + currentPlayerData.getInventoryItems());
        }
    }

    public String getName() {
        return name;
    }

    public WorldData getWorldData() {
        return worldData;
    }

    public void setWorldData(WorldData data) {
        if (data == null) {
            throw new IllegalArgumentException("WorldData cannot be null");
        }
        this.worldData = data;
        this.name = data.getName();
        this.worldSeed = data.getConfig().getSeed();
        this.biomeManager = new BiomeManager(this.worldSeed);

        // Clear existing chunks and objects
        this.chunks.clear();

        // Load chunks and objects from worldData
        loadChunksFromWorldData();

        GameLogger.info("Set WorldData for world: " + name +
            " Time: " + data.getWorldTimeInMinutes() +
            " Played: " + data.getPlayedTime());
    }


    public Biome getBiomeAt(int worldX, int worldY) {
        BiomeTransitionResult transition = biomeManager.getBiomeAt(
            worldX * TILE_SIZE,
            worldY * TILE_SIZE
        );

        return transition.getPrimaryBiome();
    }

    private void initializeChunksAroundOrigin() {
        validateChunkState();
        GameLogger.info("Starting chunk initialization around origin");

        int totalChunks = (INITIAL_LOAD_RADIUS * 2 + 1) * (INITIAL_LOAD_RADIUS * 2 + 1);
        int loadedCount = 0;

        for (int radius = 0; radius <= INITIAL_LOAD_RADIUS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) == radius) {
                        Vector2 chunkPos = new Vector2(dx, dy);
                        loadChunkAsync(chunkPos);
                    }
                }
            }
        }
    }

    private void loadInitialChunksIncrementally() {
        int chunksLoadedThisFrame = 0;
        while (chunksLoadedThisFrame < INITIAL_CHUNKS_PER_FRAME && !initialChunkLoadQueue.isEmpty()) {
            Vector2 chunkPos = initialChunkLoadQueue.poll();
            if (!chunks.containsKey(chunkPos) && !loadingChunks.containsKey(chunkPos)) {
                loadChunkAsync(chunkPos);
            }
            chunksLoadedThisFrame++;
        }


        if (initialChunkLoadQueue.isEmpty() && loadingChunks.isEmpty()) {
            initialChunksLoaded = true;
            GameLogger.info("Initial chunk loading complete.");
        }
    }

    private Chunk loadOrGenerateChunk(Vector2 chunkPos) {
        try {
            boolean isMultiplayer = gameClient != null && !gameClient.isSinglePlayer();

            // First try to load saved biome type
            BiomeType savedBiomeType = biomeManager.loadChunkBiomeData(chunkPos, name, isMultiplayer);

            // Try to load chunk data
            Chunk chunk = loadChunkData(chunkPos, isMultiplayer);

            if (chunk == null) {
                int worldX = (int) (chunkPos.x * Chunk.CHUNK_SIZE);
                int worldY = (int) (chunkPos.y * Chunk.CHUNK_SIZE);

                // Use saved biome type if available, otherwise generate new
                Biome biome;
                if (savedBiomeType != null) {
                    biome = biomeManager.getBiome(savedBiomeType);
                    GameLogger.info(String.format(
                        "Using saved biome %s for chunk (%d,%d)",
                        savedBiomeType, (int) chunkPos.x, (int) chunkPos.y
                    ));
                } else {
                    BiomeTransitionResult biomeTransition = biomeManager.getBiomeAt(
                        worldX * TILE_SIZE,
                        worldY * TILE_SIZE
                    );
                    biome = biomeTransition.getPrimaryBiome();
                }

                if (biome == null) {
                    GameLogger.error("Null biome at " + worldX + "," + worldY);
                    biome = biomeManager.getBiome(BiomeType.PLAINS); // Fallback biome
                }

                // Create the chunk
                chunk = new Chunk(
                    (int) chunkPos.x,
                    (int) chunkPos.y, biome,
                    worldSeed,
                    biomeManager
                );

                // Now save the biome data
                if (savedBiomeType == null) {
                    // Since we didn't have saved biome data, save it now
                    biomeManager.saveChunkBiomeData(chunkPos, chunk, name, isMultiplayer);
                }

                objectManager.generateObjectsForChunk(chunkPos, chunk, biome);
                saveChunkData(chunkPos, chunk, isMultiplayer);
            }

            return chunk;

        } catch (Exception e) {
            GameLogger.error("Failed to load/generate chunk at " + chunkPos + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }


    private void updateWorldColor() {
        float hourOfDay = DayNightCycle.getHourOfDay((long) worldData.getWorldTimeInMinutes());

        // Store the previous color before updating
        if (currentWorldColor != null) {
            if (previousWorldColor == null) {
                previousWorldColor = currentWorldColor.cpy();
            } else if (colorTransitionProgress >= 1.0f) {
                previousWorldColor.set(currentWorldColor);
            }
        }

        Color targetColor = DayNightCycle.getWorldColor(hourOfDay);

        // If we have both colors, interpolate between them
        if (previousWorldColor != null && targetColor != null) {
            if (!targetColor.equals(currentWorldColor)) {
                colorTransitionProgress = 0.0f;
                currentWorldColor = new Color();
            }

            // Smooth interpolation
            colorTransitionProgress = Math.min(1.0f, colorTransitionProgress + Gdx.graphics.getDeltaTime() * COLOR_TRANSITION_SPEED);
            currentWorldColor.r = previousWorldColor.r + (targetColor.r - previousWorldColor.r) * colorTransitionProgress;
            currentWorldColor.g = previousWorldColor.g + (targetColor.g - previousWorldColor.g) * colorTransitionProgress;
            currentWorldColor.b = previousWorldColor.b + (targetColor.b - previousWorldColor.b) * colorTransitionProgress;
            currentWorldColor.a = previousWorldColor.a + (targetColor.a - previousWorldColor.a) * colorTransitionProgress;
        } else {
            currentWorldColor = targetColor;
        }
    }

private void validateChunkState() {
        if (chunks == null) {
            chunks = new ConcurrentHashMap<>();
        }
        if (loadingChunks == null) {
            loadingChunks = new ConcurrentHashMap<>();
        }
        if (initialChunkLoadQueue == null) {
            initialChunkLoadQueue = new LinkedList<>();
        }
    }

    public void loadChunkAsync(Vector2 chunkPos) {
        validateChunkState();

        if (loadingChunks.containsKey(chunkPos)) {
            GameLogger.info("Chunk already loading: " + chunkPos);
            return;
        }

        try {
            if (!gameClient.isSinglePlayer()) {
                GameLogger.info("Requesting chunk from server: " + chunkPos);
                CompletableFuture<Chunk> future = new CompletableFuture<>();
                loadingChunks.put(chunkPos, future);
                gameClient.requestChunk(chunkPos);
            } else {
                CompletableFuture<Chunk> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        GameLogger.info("Loading chunk: " + chunkPos);
                        Chunk chunk = loadOrGenerateChunk(chunkPos);
                        if (chunk != null) {
                            synchronized (chunks) {
                                chunks.put(chunkPos, chunk);
                                GameLogger.info("Loaded chunk: " + chunkPos);
                            }
                        }
                        return chunk;
                    } catch (Exception e) {
                        GameLogger.error("Error loading chunk " + chunkPos + ": " + e.getMessage());
                        e.printStackTrace();
                        return null;
                    }
                }, chunkLoadExecutor);

                loadingChunks.put(chunkPos, future);
            }
        } catch (Exception e) {
            GameLogger.error("Failed to initiate chunk load for " + chunkPos + ": " + e.getMessage());
            e.printStackTrace();
        }
    }


    public void update(float delta, Vector2 playerPosition, float viewportWidth, float viewportHeight) {
        validateChunkState();

        if (!initialChunksLoaded) {
            loadInitialChunksIncrementally();
            checkLoadedChunks();
            return;
        }

        // Update world time
        if (worldData != null) {
            worldData.updateTime(delta);
        }

        // Update render color
        updateWorldColor();

        // Update weather
        updateWeather(delta, playerPosition);

        // Calculate view bounds
        Rectangle viewBounds = ChunkManager.calculateViewBounds(
            playerPosition.x * TILE_SIZE,
            playerPosition.y * TILE_SIZE,
            viewportWidth,
            viewportHeight
        );

        // Get chunks that need to be loaded
        Set<Vector2> chunksToLoad = ChunkManager.getChunksToLoad(playerPosition, viewBounds);

        // Load new chunks
        for (Vector2 chunkPos : chunksToLoad) {
            if (!chunks.containsKey(chunkPos) && !loadingChunks.containsKey(chunkPos)) {
                loadChunkAsync(chunkPos);
            }
        }

        // Clean up chunks outside view distance
        Iterator<Map.Entry<Vector2, Chunk>> iterator = chunks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Vector2, Chunk> entry = iterator.next();
            Vector2 chunkPos = entry.getKey();
            if (!chunksToLoad.contains(chunkPos)) {
                // Save chunk data before removing
                saveChunkData(chunkPos, entry.getValue(),
                    gameClient != null && !gameClient.isSinglePlayer());
                iterator.remove();
            }
        }

        // Update game systems
        updateGameSystems(delta, playerPosition);
    }
    public void updateGameSystems(float delta, Vector2 playerPosition) {
        BiomeTransitionResult currentBiomeTransition = biomeManager.getBiomeAt(
            playerPosition.x * TILE_SIZE,
            playerPosition.y * TILE_SIZE
        );

        Biome currentBiome = currentBiomeTransition.getPrimaryBiome();

        // Handle audio based on current biome
        if (AudioManager.getInstance() != null) {
            AudioManager.getInstance().updateBiomeMusic(currentBiome.getType());
            AudioManager.getInstance().update(delta);
        }
        // Update other systems
        pokemonSpawnManager.update(delta, playerPosition);

        objectManager.update(chunks);
        checkPlayerInteractions(playerPosition);
    }

    public PokemonSpawnManager getPokemonSpawnManager() {
        return pokemonSpawnManager;
    }

    private void renderWeather(SpriteBatch batch, Rectangle viewBounds) {
        if (weatherSystem != null) {
            Vector2 cameraPosition = new Vector2(
                viewBounds.x + viewBounds.width / 2,
                viewBounds.y + viewBounds.height / 2
            );
            weatherSystem.render(batch, cameraPosition, viewBounds.width, viewBounds.height);
        }
    }

    public void render(SpriteBatch batch, Rectangle viewBounds, Player player) {
        if (chunks.isEmpty()) {
            GameLogger.error("No chunks available for rendering!");
            return;
        }

        // Set the blending function to the default before rendering
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        // Save the current batch color and set to world color
        Color prevColor = batch.getColor();
        if (currentWorldColor != null) {
            batch.setColor(currentWorldColor);
        }
        renderWeather(batch, viewBounds);

        // Render lightning if present
        if (weatherAudioSystem != null) {
            weatherAudioSystem.renderLightningEffect(batch, viewBounds.width, viewBounds.height);
        }

        // Define expanded view bounds to include a buffer for smoother transitions
        float buffer = World.TILE_SIZE * 2;

        // Sort chunks based on Y position for correct rendering order
        try {
            // Calculate expanded view bounds for smooth transitions
            Rectangle expandedBounds = getExpandedViewBounds(viewBounds);

            // Sort chunks by Y position for correct layering
            List<Map.Entry<Vector2, Chunk>> sortedChunks = getSortedChunks();

            sortedChunks.sort(Comparator.comparingDouble(entry -> entry.getKey().y));
            // === RENDER PASS 1: Ground and Terrain ===
            renderTerrainLayer(batch, sortedChunks, expandedBounds);

            // === RENDER PASS 2: Object Bases and Low Objects ===
            renderLowObjects(batch, expandedBounds);

            // === RENDER PASS 3: Characters and Mid-Layer Objects ===
            renderMidLayer(batch, player, expandedBounds);

            // === RENDER PASS 4: High Objects and Tree Tops ===
            //            renderHighObjects(batch, expandedBounds);

            // === RENDER PASS 5: Effects and Overlays ===
            renderEffects(batch, expandedBounds);

        } finally {
            // Restore original batch color
            batch.setColor(prevColor);
        }
        batch.setColor(prevColor);
    }

    private List<Map.Entry<Vector2, Chunk>> getSortedChunks() {
        List<Map.Entry<Vector2, Chunk>> sortedChunks = new ArrayList<>(chunks.entrySet());
        sortedChunks.sort(Comparator.comparingDouble(entry -> entry.getKey().y));
        return sortedChunks;
    }

    private void renderTerrainLayer(SpriteBatch batch,
                                    List<Map.Entry<Vector2, Chunk>> sortedChunks,
                                    Rectangle expandedBounds) {
        for (Map.Entry<Vector2, Chunk> entry : sortedChunks) {
            Vector2 chunkPos = entry.getKey();
            if (isChunkVisible(chunkPos, expandedBounds)) {
                Chunk chunk = entry.getValue();
                Map<BiomeRenderer.Direction, Biome> neighbors = getNeighboringBiomes(chunkPos);
                biomeRenderer.renderChunk(batch, chunk);
            }
        }
    }

    private Rectangle getExpandedViewBounds(Rectangle viewBounds) {
        float buffer = TILE_SIZE * 2;
        return new Rectangle(
            viewBounds.x - buffer,
            viewBounds.y - buffer,
            viewBounds.width + (buffer * 2),
            viewBounds.height + (buffer * 2)
        );
    }

    private void renderLowObjects(SpriteBatch batch, Rectangle expandedBounds) {
        List<WorldObject> objectsToRender = new ArrayList<>();

        // Gather visible objects
        for (Map.Entry<Vector2, Chunk> entry : chunks.entrySet()) {
            Vector2 pos = entry.getKey();
            if (isChunkVisible(pos, expandedBounds)) {
                List<WorldObject> chunkObjects = objectManager.getObjectsForChunk(pos);
                for (WorldObject obj : chunkObjects) {
                    if (obj.getType().renderType == WorldObject.ObjectType.RenderLayer.LAYERED) {
                        objectManager.renderTreeBase(batch, obj);
                    } else if (obj.getType() == WorldObject.ObjectType.POKEBALL || obj.getType() == WorldObject.ObjectType.VINES) {
                        obj.render(batch);
                    } else if (obj.getType() == WorldObject.ObjectType.CACTUS ||
                        obj.getType() == WorldObject.ObjectType.SUNFLOWER || obj.getType() == WorldObject.ObjectType.BUSH || obj.getType() == WorldObject.ObjectType.DEAD_TREE || obj.getType() == WorldObject.ObjectType.SMALL_HAUNTED_TREE) {
                        objectManager.renderObject(batch, obj);
                    }
                }
            }
        }
    }

    private void renderMidLayer(SpriteBatch batch, Player player, Rectangle expandedBounds) {
        // Render wild Pokemon below player
        renderWildPokemon(batch);

        // Check if player is under any trees
        boolean playerBehindTree = objectManager.isPlayerUnderTree(
            player.getTileX(),
            player.getTileY(),
            Player.FRAME_WIDTH,
            Player.FRAME_HEIGHT
        );

        if (playerBehindTree) {
            renderTreeTops(batch, expandedBounds);
            player.render(batch);
        } else {
            player.render(batch);
            renderTreeTops(batch, expandedBounds);
        }
    }

    private void renderEffects(SpriteBatch batch, Rectangle expandedBounds) {
        // Render any effects, particles, or overlays here
        // Currently empty - implement as needed
    }

    private void renderTreeTops(SpriteBatch batch, Rectangle expandedBounds) {
        for (Map.Entry<Vector2, Chunk> entry : chunks.entrySet()) {
            Vector2 pos = entry.getKey();
            if (isChunkVisible(pos, expandedBounds)) {
                List<WorldObject> objects = objectManager.getObjectsForChunk(pos);
                for (WorldObject obj : objects) {
                    if (obj.getType().renderType == WorldObject.ObjectType.RenderLayer.LAYERED) {
                        objectManager.renderTreeTop(batch, obj);
                    }
                }
            }
        }
    }

    private Map<BiomeRenderer.Direction, Biome> getNeighboringBiomes(Vector2 chunkPos) {
        Map<BiomeRenderer.Direction, Biome> neighbors = new EnumMap<>(BiomeRenderer.Direction.class);

        for (BiomeRenderer.Direction dir : BiomeRenderer.Direction.values()) {
            Vector2 neighborPos = new Vector2(
                chunkPos.x + (dir == BiomeRenderer.Direction.EAST ? 1 : dir == BiomeRenderer.Direction.WEST ? -1 : 0),
                chunkPos.y + (dir == BiomeRenderer.Direction.NORTH ? 1 : dir == BiomeRenderer.Direction.SOUTH ? -1 : 0)
            );

            Chunk neighborChunk = chunks.get(neighborPos);
            if (neighborChunk != null) {
                neighbors.put(dir, neighborChunk.getBiome());
            }
        }

        return neighbors;
    }

    private boolean isChunkVisible(Vector2 chunkPos, Rectangle viewBounds) {
        float chunkWorldX = chunkPos.x * CHUNK_SIZE * TILE_SIZE;
        float chunkWorldY = chunkPos.y * CHUNK_SIZE * TILE_SIZE;
        float chunkSize = CHUNK_SIZE * TILE_SIZE;

        // Create a rectangle for the chunk
        Rectangle chunkRect = new Rectangle(chunkWorldX, chunkWorldY, chunkSize, chunkSize);

        // Check for overlap with view bounds
        return viewBounds.overlaps(chunkRect);
    }

    private void sendObjectUpdate(NetworkedWorldObject object, NetworkProtocol.NetworkObjectUpdateType type) {
        NetworkProtocol.WorldObjectUpdate update = new NetworkProtocol.WorldObjectUpdate();
        update.objectId = object.getId();
        update.type = type;
        update.x = object.getX();
        update.y = object.getY();
        update.objectType = object.getType();

        gameClient.sendWorldObjectUpdate(update);
    }

    public WorldObject.WorldObjectManager getObjectManager() {
        return objectManager;
    }

    // Add to World c
    public void dispose() {
        try {
            if (worldData != null) {
                GameLogger.info("Saving final world state...");

                // Force one last time update
                worldData.updateTime(Gdx.graphics.getDeltaTime());

                // Create Json instance
                Json json = new Json();
                json.setOutputType(JsonWriter.OutputType.json);

                // Convert to JSON
                String jsonStr = json.prettyPrint(worldData);

                // Save to file
                FileHandle worldDir = Gdx.files.local("worlds/singleplayer/" + worldData.getName());
                if (!worldDir.exists()) {
                    worldDir.mkdirs();
                }

                FileHandle worldFile = worldDir.child("world.json");
                worldFile.writeString(jsonStr, false);

                GameLogger.info("World saved successfully with time values");
            }
        } catch (Exception e) {
            GameLogger.error("Error saving world: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Add this call in the update method:

    public boolean isPokemonAt(int tileX, int tileY) {
        float pixelX = tileX * TILE_SIZE;
        float pixelY = tileY * TILE_SIZE;

        // Create a collision box for the tile
        Rectangle tileBox = new Rectangle(
            pixelX,
            pixelY,
            TILE_SIZE,
            TILE_SIZE
        );
        Collection<WildPokemon> nearbyPokemon = pokemonSpawnManager.getPokemonInRange(
            pixelX + ((float) TILE_SIZE / 2),
            pixelY + ((float) TILE_SIZE / 2),
            TILE_SIZE * 2
        );

        for (WildPokemon pokemon : nearbyPokemon) {
            if (pokemon.getBoundingBox().overlaps(tileBox)) {
                return true;
            }
        }
        return false;
    }

    public void loadChunksAroundPositionSynchronously(Vector2 position, int radius) {
        int chunkX = (int) Math.floor(position.x / CHUNK_SIZE);
        int chunkY = (int) Math.floor(position.y / CHUNK_SIZE);

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                Vector2 chunkPos = new Vector2(chunkX + dx, chunkY + dy);
                if (!chunks.containsKey(chunkPos)) {
                    // Load chunk synchronously
                    Chunk chunk = loadOrGenerateChunk(chunkPos);
                    if (chunk != null) {
                        chunks.put(chunkPos, chunk);
                    }
                }
            }
        }
    }

    public boolean isPassable(int worldX, int worldY) {
        if (!isPositionLoaded(worldX, worldY)) {
            return false;
        }

        try {
            // Convert pixel coordinates to chunk coordinates
            int chunkX = Math.floorDiv(worldX, Chunk.CHUNK_SIZE);
            int chunkY = Math.floorDiv(worldY, Chunk.CHUNK_SIZE);
            Vector2 chunkPos = new Vector2(chunkX, chunkY);

            Chunk chunk = chunks.get(chunkPos);
            if (chunk == null) return false;

            int localX = Math.floorMod(worldX, Chunk.CHUNK_SIZE);
            int localY = Math.floorMod(worldY, Chunk.CHUNK_SIZE);

            String currentDirection = player != null ? player.getDirection() : "down";

            // Basic tile passability
            if (!chunk.isPassable(localX, localY)) {
                handleCollision(currentDirection);
                return false;
            }

            // Calculate pixel-based collision box
            Rectangle movementBounds = new Rectangle(
                worldX * TILE_SIZE,  // Now using actual pixel position
                worldY * TILE_SIZE,
                TILE_SIZE * 0.5f,    // Half tile collision size
                TILE_SIZE * 0.5f
            );

            // Check collisions with objects and Pokemon
            return !checkObjectCollision(movementBounds, currentDirection) &&
                !checkPokemonCollision(worldX, worldY, currentDirection);

        } catch (Exception e) {
            GameLogger.error("Error checking passability: " + e.getMessage());
            return false;
        }
    }

    public GameClient getGameClient() {
        if (gameClient == null) {
            throw new IllegalStateException("GameClient is null - World not properly initialized");
        }
        return gameClient;
    }

    private boolean isPositionLoaded(int worldX, int worldY) {
        int chunkX = Math.floorDiv(worldX, Chunk.CHUNK_SIZE);
        int chunkY = Math.floorDiv(worldY, Chunk.CHUNK_SIZE);
        return chunks.containsKey(new Vector2(chunkX, chunkY));
    }

    private void handleCollision(String direction) {
        if (player != null) {
            switch (direction) {
                case "up":
                    player.setDirection("up");
                    break;
                case "down":
                    player.setDirection("down");
                    break;
                case "left":
                    player.setDirection("left");
                    break;
                case "right":
                    player.setDirection("right");
                    break;
            }
            player.setMoving(false);
        }
    }

    private boolean checkObjectCollision(Rectangle movementBounds, String direction) {
        List<WorldObject> nearbyObjects = objectManager.getObjectsNearPosition(
            movementBounds.x + movementBounds.width / 2,
            movementBounds.y + movementBounds.height / 2
        );

        for (WorldObject obj : nearbyObjects) {
            Rectangle collisionBox = obj.getCollisionBox();
            if (collisionBox != null && collisionBox.overlaps(movementBounds)) {
                if (player != null) {
                    player.setDirection(direction);
                    player.setMoving(false);
                }
                return true;
            }
        }
        return false;
    }

    private boolean checkPokemonCollision(int worldX, int worldY, String direction) {
        if (isPokemonAt(worldX, worldY)) {
            if (player != null) {
                player.setDirection(direction);
                player.setMoving(false);
            }
            return true;
        }
        return false;
    }

    private void checkPlayerInteractions(Vector2 playerPosition) {
        // Convert player position from tile coordinates to pixel coordinates
        float playerPixelX = playerPosition.x * TILE_SIZE;
        float playerPixelY = playerPosition.y * TILE_SIZE;

        nearestPokeball = null;
        float closestDistance = Float.MAX_VALUE;

        // Check in current chunk and adjacent chunks
        int chunkX = (int) Math.floor(playerPixelX / (Chunk.CHUNK_SIZE * TILE_SIZE));
        int chunkY = (int) Math.floor(playerPixelY / (Chunk.CHUNK_SIZE * TILE_SIZE));

        // Check current and surrounding chunks
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                Vector2 chunkPos = new Vector2(chunkX + dx, chunkY + dy);
                List<WorldObject> objects = objectManager.getObjectsForChunk(chunkPos);

                if (objects != null) {
                    for (WorldObject obj : objects) {
                        if (obj.getType() == WorldObject.ObjectType.POKEBALL) {
                            // Calculate distance using pixel coordinates
                            float dx2 = playerPixelX - obj.getPixelX();
                            float dy2 = playerPixelY - obj.getPixelY();
                            float distance = (float) Math.sqrt(dx2 * dx2 + dy2 * dy2);
                            if (distance <= INTERACTION_RANGE && distance < closestDistance) {
                                closestDistance = distance;
                                nearestPokeball = obj;
                            }
                        }
                    }
                }
            }
        }
    }

    public void initializeFromServer(long seed, double worldTimeInMinutes, float dayLength) {
        try {
            GameLogger.info("Initializing world from server with seed: " + seed);

            // Create new WorldData if it doesn't exist
            if (worldData == null) {
                worldData = new WorldData(name);
                GameLogger.info("Created new WorldData instance");
            }

            // Set config and time values first
            WorldData.WorldConfig config = new WorldData.WorldConfig(seed);
            worldData.setConfig(config);
            worldData.setWorldTimeInMinutes(worldTimeInMinutes);
            worldData.setDayLength(dayLength);

            // Initialize other world components
            this.worldSeed = seed;
            this.biomeManager = new BiomeManager(seed);

            // Initialize managers
            if (objectManager == null) {
                objectManager = new WorldObject.WorldObjectManager(worldSeed, gameClient);
            }

            if (pokemonSpawnManager == null) {
                pokemonSpawnManager = new PokemonSpawnManager(this, TextureManager.pokemonoverworld, gameClient);
            }

            GameLogger.info("World initialization complete - " +
                "Time: " + worldTimeInMinutes +
                " Day Length: " + dayLength +
                " Seed: " + seed);

        } catch (Exception e) {
            GameLogger.error("Failed to initialize world from server: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("World initialization failed", e);
        }
    }

    private void renderWildPokemon(SpriteBatch batch) {
        Collection<WildPokemon> allPokemon = pokemonSpawnManager.getAllWildPokemon();

        // Sort Pokemon by Y position for correct layering
        List<WildPokemon> sortedPokemon = new ArrayList<>(allPokemon);
        sortedPokemon.sort((p1, p2) -> Float.compare(p2.getY(), p1.getY()));

        for (WildPokemon pokemon : sortedPokemon) {
            if (pokemon == null || pokemon.getAnimations() == null) {
                continue;
            }


            pokemon.render(batch);
        }
    }

    public void removeWorldObject(WorldObject obj) {
        int chunkX = (int) Math.floor(obj.getPixelX() / (Chunk.CHUNK_SIZE * TILE_SIZE));
        int chunkY = (int) Math.floor(obj.getPixelY() / (Chunk.CHUNK_SIZE * TILE_SIZE));
        Vector2 chunkPos = new Vector2(chunkX, chunkY);

        List<WorldObject> objects = objectManager.getObjectsForChunk(chunkPos);
        if (objects != null) {
            objects.remove(obj);
        }
    }

    public void spawnPlayer(Player player) {
        // Find valid spawn point
        Vector2 spawnPoint = SpawnPointValidator.findValidSpawnPoint(
            this,
            DEFAULT_X_POSITION,
            DEFAULT_Y_POSITION
        );

        // If player has Pokemon, preserve them before setting position
        List<Pokemon> currentPokemon = new ArrayList<>();
        if (player.getPokemonParty() != null) {
            currentPokemon.addAll(player.getPokemonParty().getParty());
        }

        // Set position
        player.setX(spawnPoint.x);
        player.setY(spawnPoint.y);

        // Restore Pokemon
        if (!currentPokemon.isEmpty()) {
            player.getPokemonParty().clearParty();
            for (Pokemon pokemon : currentPokemon) {
                player.getPokemonParty().addPokemon(pokemon);
            }
        }

        this.player = player;
    }

    private void updateWeather(float delta, Vector2 playerPosition) {
        // Calculate world position in pixels
        float worldX = playerPosition.x * TILE_SIZE;
        float worldY = playerPosition.y * TILE_SIZE;

        // Get current biome and temperature
        currentBiomeTransition = biomeManager.getBiomeAt(worldX, worldY);
        temperature = calculateTemperature(playerPosition);

        // Update weather systems
        float timeOfDay = (float) (worldData.getWorldTimeInMinutes() % (24 * 60)) / 60f;
        weatherSystem.update(delta, currentBiomeTransition, temperature, timeOfDay);
        weatherAudioSystem.update(delta, weatherSystem.getCurrentWeather(), weatherSystem.getIntensity());
    }

    private float calculateTemperature(Vector2 playerPosition) {
        float baseTemp = 20.0f; // Base temperature
        float timeOfDay = (float) (worldData.getWorldTimeInMinutes() % (24 * 60)) / 60f;
        float biomeTemp = currentBiomeTransition.getPrimaryBiome().getTemperature();

        // Daily temperature variation
        float timeVariation = (float) Math.sin((timeOfDay - 6) * Math.PI / 12) * 5.0f;

        return baseTemp + biomeTemp + timeVariation;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public Chunk getChunkAtPosition(float x, float y) {
        int chunkX = Math.floorDiv((int) x, Chunk.CHUNK_SIZE);
        int chunkY = Math.floorDiv((int) y, Chunk.CHUNK_SIZE);
        return chunks.get(new Vector2(chunkX, chunkY));
    }

    public WorldObject getNearestPokeball() {
        return nearestPokeball;
    }


    public static class ChunkData {
        public int x;
        public int y;
        public BiomeType biomeType;
        public int[][] tileData;
        public List<WorldObjectData> objects;
        public long lastModified;
        public boolean isMultiplayer;

        public ChunkData() {
            this.tileData = new int[Chunk.CHUNK_SIZE][Chunk.CHUNK_SIZE];
            this.objects = new ArrayList<>();
        }

        public ChunkData(Chunk chunk, List<WorldObjectData> objects, boolean isMultiplayer) {
            this.x = chunk.getChunkX();
            this.y = chunk.getChunkY();
            this.biomeType = chunk.getBiome().getType();
            this.objects = objects;
            this.isMultiplayer = isMultiplayer;
            this.lastModified = System.currentTimeMillis();

            // Deep copy tile data
            this.tileData = new int[Chunk.CHUNK_SIZE][Chunk.CHUNK_SIZE];
            int[][] sourceTileData = chunk.getTileData();
            for (int i = 0; i < Chunk.CHUNK_SIZE; i++) {
                System.arraycopy(sourceTileData[i], 0, this.tileData[i], 0, Chunk.CHUNK_SIZE);
            }
        }

        public void validate() {
            if (tileData == null) {
                tileData = new int[Chunk.CHUNK_SIZE][Chunk.CHUNK_SIZE];
            }
            if (objects == null) {
                objects = new ArrayList<>();
            }
        }
    }

    public static class WorldObjectData {
        public float x;
        public float y;
        public WorldObject.ObjectType type;

        public WorldObjectData() {
        }

        public WorldObjectData(WorldObject obj) {
            this.x = obj.getPixelX();
            this.y = obj.getPixelY();
            this.type = obj.getType();
        }
    }

}

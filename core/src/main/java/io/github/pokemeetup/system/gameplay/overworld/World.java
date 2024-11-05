package io.github.pokemeetup.system.gameplay.overworld;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
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
import io.github.pokemeetup.pokemon.WildPokemon;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.data.PlayerData;
import io.github.pokemeetup.system.gameplay.overworld.biomes.Biome;
import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;
import io.github.pokemeetup.system.data.WorldData;
import io.github.pokemeetup.system.gameplay.inventory.Item;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.TextureManager;
import io.github.pokemeetup.utils.PerlinNoise;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

public class World {
    public static final int WORLD_SIZE = 1600; // Size in tiles (can be adjusted as needed)
    public static final int TILE_SIZE = 32;
    public static final int CHUNK_SIZE = 16; // Tiles per chunk
    // Define the origin at the center
    public static final float ORIGIN_X = 0;
    public static final float ORIGIN_Y = 0;
    public static final float INTERACTION_RANGE = TILE_SIZE * 1.5f;
    public static final String SAVE_DIR = "worlds/";
    public static final int INITIAL_LOAD_RADIUS = 5;   // Reduced from 4
    private static final int CHUNK_PRELOAD_RADIUS = 8;  // Increased
    private static final int CHUNK_UNLOAD_RADIUS = 12; // Increased
    private static final int VISIBLE_CHUNKS_BUFFER = 6; // Increased
    private static final int MAX_CHUNKS_PER_FRAME = 2;  // Limit chunk loading per frame
    private static final float CHUNK_LOAD_INTERVAL = 1f / 30f; // Load chunks every 1/30th second
    // private static final int CHUNK_PRELOAD_RADIUS = 6; // Increased significantly
    private static final int VISUAL_CHUNK_RADIUS = 4; // For visible chunks
    private static final float CHUNK_LOAD_MARGIN = 2f; // Extra buffer for smoother transitions
    private static final int MAX_CONCURRENT_CHUNK_LOADS = 8; // Limit concurrent loads
    private static final float BIOME_BASE_SCALE = 0.001f; // Decreased value
    private static final float DETAIL_SCALE = 0.005f;     // Adjusted value
    private static final int OCTAVES = 5;                 // Increased from 4
    private static final float PERSISTENCE = 0.4f;        // Increased from 0.3f

    private static final float LACUNARITY = 2.0f;  // You can keep this value

    private static final String CHUNK_DATA_FILE = "chunks.json";
    private static final int CHUNK_LOAD_RADIUS = 3;
    private static final float CHUNK_UPDATE_INTERVAL = 1f / 60f; // 60fps update rate
    public static int DEFAULT_X_POSITION = 0;
    public static int DEFAULT_Y_POSITION = 0;    // Adjust these constants for biome size and transitions
    private final Map<Vector2, Chunk> chunks;
    private final BiomeManager biomeManager;
    private final Queue<Vector2> chunkLoadQueue = new LinkedList<>();
    // New noise generators
    private final PerlinNoise temperatureNoiseGenerator;
    private final PerlinNoise moistureNoiseGenerator;
    private final Map<String, NetworkedWorldObject> syncedObjects;
    private final GameClient gameClient;
    private final Map<Vector2, Future<Chunk>> loadingChunks = new ConcurrentHashMap<>();
    private final ExecutorService chunkLoaderExecutor = Executors.newFixedThreadPool(4);
    // Add these fields
    private final Set<Vector2> activeChunks = new HashSet<>();
    private BiomeRenderer biomeRenderer;
    // Add these fields
    private float chunkLoadTimer = 0;
    // Add viewport tracking
    private Rectangle lastViewport = new Rectangle();
    private Vector2 playerVelocity = new Vector2();
    private Vector2 lastPlayerPos = new Vector2();
    private Player player;
    private PlayerData currentPlayerData;
    private WorldObject nearestPokeball;
    private PerlinNoise biomeNoiseGenerator;
    private Rectangle worldBounds;   // Add to existing fields    private static final int CHUNK_LOAD_RADIUS = 3;
    private float chunkUpdateTimer = 0;
    private Rectangle currentViewport = new Rectangle();
    private Vector2 lastPlayerChunkPos = new Vector2();
    private long lastPlayed;
    private BlockManager blockManager;
    private String name;
    private WorldData worldData;
    private PokemonSpawnManager pokemonSpawnManager;
    //    private static final int DEBUG_CHUNK_RADIUS = 1; // For testing, render fewer chunks
    private long worldSeed;
    private WorldObject.WorldObjectManager objectManager;
    private PlayerData playerData; // Change from PlayerSaveData
    private List<Item> collectedItems = new ArrayList<>();
    private boolean showPokemonBounds = true; // Toggle as needed
    private Color currentWorldColor;

    public World(String name, int worldWidth, int worldHeight, long seed, GameClient gameClient, BiomeManager manager) {
        this.biomeManager = manager;
        this.gameClient = gameClient;
        this.biomeRenderer = new BiomeRenderer();
        this.chunks = new ConcurrentHashMap<>();
        this.worldSeed = seed;
        this.worldBounds = new Rectangle(0, 0, worldWidth, worldHeight);
        this.name = name;
        this.blockManager = new BlockManager(TextureManager.items);
        this.biomeNoiseGenerator = new PerlinNoise((int) seed);
        this.temperatureNoiseGenerator = new PerlinNoise((int) (seed + 100));
        this.moistureNoiseGenerator = new PerlinNoise((int) (seed + 200));
        this.syncedObjects = new ConcurrentHashMap<>();
        this.pokemonSpawnManager = new PokemonSpawnManager(this, TextureManager.pokemonoverworld);
        TextureRegion treeTexture = TextureManager.tiles.findRegion("tree");
        TextureRegion pokeballTexture = TextureManager.tiles.findRegion("pokeball");  // Debug texture loading
        if (treeTexture == null) {
            GameLogger.info("Failed to load tree texture from atlas");
        }
        if (pokeballTexture == null) {
            GameLogger.info("Failed to load pokeball texture from atlas");
        }
        objectManager = new WorldObject.WorldObjectManager(worldSeed, gameClient);
        // Verify textures are loaded
        for (Map.Entry<Integer, TextureRegion> entry : TextureManager.getAllTileTextures().entrySet()) {
            GameLogger.info("Tile type " + entry.getKey() + ": " +
                (entry.getValue() != null ? "loaded" : "null"));
        }// Load or create world data

        initializeChunksAroundOrigin();
    }    // Add setter for io.github.pokemeetup.system.data.PlayerData

    public boolean areAllChunksLoaded() {
        for (int dx = -INITIAL_LOAD_RADIUS; dx <= INITIAL_LOAD_RADIUS; dx++) {
            for (int dy = -INITIAL_LOAD_RADIUS; dy <= INITIAL_LOAD_RADIUS; dy++) {
                Vector2 chunkPos = new Vector2(dx, dy);
                if (!chunks.containsKey(chunkPos)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Gets the nearest Pokemon to the player within interaction range
     */
    public WildPokemon getNearestInteractablePokemon(Player player) {
        float playerX = player.getX() + (Player.FRAME_WIDTH / 2f);
        float playerY = player.getY() + (Player.FRAME_HEIGHT / 2f);

        // Get player's facing direction for interaction
        float checkX = playerX;
        float checkY = playerY;
        float interactionDistance = TILE_SIZE * 1.5f;

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
                pokemon.getX() + pokemon.getBoundingBox().width / 2,
                pokemon.getY() + pokemon.getBoundingBox().height / 2
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

    private boolean isCollidingWithPokemon(int worldX, int worldY) {
        // Calculate the tile's world position in pixels
        float tilePixelX = worldX * TILE_SIZE;
        float tilePixelY = worldY * TILE_SIZE;

        // Get the bounding box for this tile
        Rectangle tileRect = new Rectangle(tilePixelX, tilePixelY, TILE_SIZE, TILE_SIZE);

        // Iterate through all wild Pokémon to check for overlap
        for (WildPokemon pokemon : pokemonSpawnManager.getAllWildPokemon()) {
            if (tileRect.overlaps(pokemon.getBoundingBox())) {
                GameLogger.info("Collision detected with Pokémon: " + pokemon.getName() + " at (" + pokemon.getX() + ", " + pokemon.getY() + ")");
                return true;
            }
        }

        return false;
    }

    // World.java
    private void checkLoadedChunks() {
        Iterator<Map.Entry<Vector2, Future<Chunk>>> iterator = loadingChunks.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<Vector2, Future<Chunk>> entry = iterator.next();
            Future<Chunk> future = entry.getValue();

            if (future.isDone()) {
                try {
                    Chunk chunk = future.get();  // Retrieve the loaded chunk
                    if (chunk != null) {  // Ensure chunk is not null before putting
                        chunks.put(entry.getKey(), chunk);  // Add to main chunk map
                    } else {
                        GameLogger.error("Loaded chunk is null for position: " + entry.getKey());
                    }
                } catch (Exception e) {
                    GameLogger.error("Error retrieving chunk at " + entry.getKey() + ": " + e.getMessage());
                    e.printStackTrace();
                }
                iterator.remove();  // Remove from loading map once loaded
            }
        }
    }

    public GameClient getGameClient() {
        return gameClient;
    }


    // Asynchronous loading of a chunk

    // Add getter/setter
    public long getLastPlayed() {
        return lastPlayed;
    }

    public void setLastPlayed(long lastPlayed) {
        this.lastPlayed = lastPlayed;
    }

    public Player getPlayer() {
        return player;
    }

    // Update the save method to include chunk saving
    public void setPlayer(Player player) {
        this.player = player;
        // Initialize player data
        this.currentPlayerData = new PlayerData(player.getUsername());
        this.currentPlayerData.updateFromPlayer(player);
    }

    public void saveChunkData(Vector2 chunkPos, Chunk chunk, boolean isMultiplayer) {
        try {
            // Create base save directory based on whether it's multiplayer
            String baseDir = isMultiplayer ?
                "assets/multiplayer/worlds/" + name + "/chunks/" :
                "worlds/" + name + "/chunks/";

            FileHandle saveDir = Gdx.files.local(baseDir);
            if (!saveDir.exists()) {
                saveDir.mkdirs();
            }

            // Get objects in chunk
            List<WorldObject> objects = objectManager.getObjectsForChunk(chunkPos);
            List<WorldObjectData> objectDataList = objects.stream()
                .map(WorldObjectData::new)
                .collect(Collectors.toList());

            // Create chunk data
            ChunkData chunkData = new ChunkData(chunk, objectDataList, isMultiplayer);

            // Save to file with chunk coordinates in filename
            String filename = String.format("chunk_%d_%d.json", (int) chunkPos.x, (int) chunkPos.y);
            FileHandle chunkFile = saveDir.child(filename);

            Json json = new Json();
            json.setOutputType(JsonWriter.OutputType.json);
            chunkFile.writeString(json.prettyPrint(chunkData), false);
            //
            //            GameLogger.info("Saved chunk data: " + filename +
            //                (isMultiplayer ? " (multiplayer)" : " (single-player)"));

        } catch (Exception e) {
            GameLogger.info("Error saving chunk data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public boolean isTilePassable(int worldX, int worldY) {
        if (!isPositionLoaded(worldX, worldY)) {
            return false; // Don't allow movement into unloaded areas
        }

        int chunkX = Math.floorDiv(worldX, Chunk.CHUNK_SIZE);
        int chunkY = Math.floorDiv(worldY, Chunk.CHUNK_SIZE);
        Vector2 chunkPos = new Vector2(chunkX, chunkY);

        Chunk chunk = chunks.get(chunkPos);
        if (chunk == null) return false;

        int localX = Math.floorMod(worldX, Chunk.CHUNK_SIZE);
        int localY = Math.floorMod(worldY, Chunk.CHUNK_SIZE);

        // Check basic tile passability
        return chunk.isPassable(localX, localY);
    }

    private Chunk loadChunkData(Vector2 chunkPos, boolean isMultiplayer) {
        try {
            String baseDir = isMultiplayer ?
                "assets/multiplayer/worlds/" + name + "/chunks/" :
                "worlds/" + name + "/chunks/";

            @SuppressWarnings("DefaultLocale") String filename = String.format("chunk_%d_%d.json", (int) chunkPos.x, (int) chunkPos.y);
            FileHandle chunkFile = Gdx.files.local(baseDir + filename);

            if (!chunkFile.exists()) {
                return null;
            }

            Json json = new Json();
            ChunkData chunkData = json.fromJson(ChunkData.class, chunkFile.readString());
            chunkData.validate();

            // Create new chunk with saved data
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
                objectManager.addObjectToChunk(chunkPos, obj);
            }

            //            GameLogger.info("Loaded chunk data: " + filename +
            //                (isMultiplayer ? " (multiplayer)" : " (single-player)"));

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
        updatePlayerData(); // Always get latest state
        return currentPlayerData;
    }

    public void setPlayerData(PlayerData playerData) {
        this.playerData = playerData;
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
        GameLogger.info("Set WorldData for world: " + name +
            " Time: " + data.getWorldTimeInMinutes() +
            " Played: " + data.getPlayedTime());
    }

    public Biome getBiomeAt(int worldX, int worldY) {
        // Get biome with transitions using BiomeManager
        BiomeTransitionResult transition = biomeManager.getBiomeAt(
            worldX * TILE_SIZE,
            worldY * TILE_SIZE
        );

        // Return primary biome - transitions are handled during rendering
        return transition.getPrimaryBiome();
    }

    private void initializeChunksAroundOrigin() {
        GameLogger.info("Starting initial chunk loading...");
        int loadCount = 0;

        // Force synchronous loading of initial chunks
        for (int dx = -INITIAL_LOAD_RADIUS; dx <= INITIAL_LOAD_RADIUS; dx++) {
            for (int dy = -INITIAL_LOAD_RADIUS; dy <= INITIAL_LOAD_RADIUS; dy++) {
                Vector2 chunkPos = new Vector2(dx, dy);
                Chunk chunk = loadOrGenerateChunk(chunkPos);
                if (chunk != null) {
                    chunks.put(chunkPos, chunk);
                    loadCount++;
                }
            }
        }

        GameLogger.info("Initial chunk loading complete. Loaded " + loadCount + " chunks");
    }

    private Chunk loadOrGenerateChunk(Vector2 chunkPos) {
        try {
            boolean isMultiplayer = gameClient != null && !gameClient.isSinglePlayer();

            // First try to load from save
            Chunk chunk = loadChunkData(chunkPos, isMultiplayer);

            // If no saved chunk, generate new one
            if (chunk == null) {
                int worldX = (int) (chunkPos.x * Chunk.CHUNK_SIZE);
                int worldY = (int) (chunkPos.y * Chunk.CHUNK_SIZE);

                // Use biomeManager to get biome with transitions
                BiomeTransitionResult biomeTransition = biomeManager.getBiomeAt(
                    worldX * TILE_SIZE,
                    worldY * TILE_SIZE
                );

                Biome primaryBiome = biomeTransition.getPrimaryBiome();
                if (primaryBiome == null) {
                    GameLogger.error("Null biome at " + worldX + "," + worldY);
                    primaryBiome = biomeManager.getBiome(BiomeType.PLAINS); // Fallback biome
                }

                chunk = new Chunk(
                    (int) chunkPos.x,
                    (int) chunkPos.y,
                    primaryBiome,
                    worldSeed,
                    biomeManager  // Pass biomeManager to handle transitions
                );

                objectManager.generateObjectsForChunk(chunkPos, chunk, primaryBiome);
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
        currentWorldColor = DayNightCycle.getWorldColor(hourOfDay);
    }


    public void update(float delta, Vector2 playerPosition, float viewportWidth, float viewportHeight) {
        if (worldData != null) {
            worldData.updateTime(delta);
        }


        updateWorldColor();
        Rectangle viewBounds = ChunkManager.calculateViewBounds(
            playerPosition.x * TILE_SIZE,  // Convert to pixel coordinates
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

        // Check and complete async chunk loading
        checkLoadedChunks();

        // Remove distant chunks
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

        updateGameSystems(delta, playerPosition);
    }


    private void loadChunkAsync(Vector2 chunkPos) {
        if (!loadingChunks.containsKey(chunkPos)) {
            Future<Chunk> chunkFuture = chunkLoaderExecutor.submit(() -> {
                try {
                    return loadOrGenerateChunk(chunkPos);
                } catch (Exception e) {
                    GameLogger.error("Failed to load chunk at " + chunkPos + ": " + e.getMessage());
                    return null;
                }
            });
            loadingChunks.put(chunkPos, chunkFuture);
        }
    }

    // Add this helper method for cleaner update logic
    public void updateGameSystems(float delta, Vector2 playerPosition) {
        // Get biome with transition info for current player position
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

        // Define expanded view bounds to include a buffer for smoother transitions
        float buffer = World.TILE_SIZE * 2;
        Rectangle expandedBounds = new Rectangle(
            viewBounds.x - buffer,
            viewBounds.y - buffer,
            viewBounds.width + (buffer * 2),
            viewBounds.height + (buffer * 2)
        );

        // Sort chunks based on Y position for correct rendering order
        List<Map.Entry<Vector2, Chunk>> sortedChunks = new ArrayList<>(chunks.entrySet());
        sortedChunks.sort(Comparator.comparingDouble(entry -> entry.getKey().y));

        // Render chunks and base elements
        for (Map.Entry<Vector2, Chunk> entry : sortedChunks) {
            Vector2 chunkPos = entry.getKey();
            Chunk chunk = entry.getValue();

            if (isChunkVisible(chunkPos, viewBounds)) {
                // Gather neighboring biomes for transition
                Map<BiomeRenderer.Direction, Biome> neighbors = getNeighboringBiomes(chunkPos);
                // Render the chunk
                biomeRenderer.renderChunk(batch, chunk);
            }
        }

        // Reset the blending function after rendering chunks
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        // Render objects within the expanded view bounds
        for (Map.Entry<Vector2, Chunk> entry : chunks.entrySet()) {
            Vector2 pos = entry.getKey();
            if (isChunkVisible(pos, expandedBounds)) {
                List<WorldObject> objects = objectManager.getObjectsForChunk(pos);
                for (WorldObject obj : objects) {
                    if (obj.getType() == WorldObject.ObjectType.TREE ||
                        obj.getType() == WorldObject.ObjectType.HAUNTED_TREE ||
                        obj.getType() == WorldObject.ObjectType.SNOW_TREE) {
                        objectManager.renderTreeBase(batch, obj);
                    } else if (obj.getType() == WorldObject.ObjectType.POKEBALL) {
                        obj.render(batch);
                    }
                }
            }
        }

        // Render Pokémon below player's Y position
        Collection<WildPokemon> pokemonList = pokemonSpawnManager.getPokemonById().values();
        for (WildPokemon pokemon : pokemonList) {
            if (pokemon.getY() < player.getY()) {
                pokemon.render(batch);
            }
        }

        // Handle player rendering with tree occlusion
        if (player != null) {
            boolean playerBehindTree = objectManager.isPlayerUnderTree(
                player.getTileX(),
                player.getTileY(),
                Player.FRAME_WIDTH,
                Player.FRAME_HEIGHT
            );

            if (playerBehindTree) {
                // Render tree tops first
                renderTreeTops(batch, expandedBounds);
                // Then render player
                player.render(batch);
            } else {
                // Render player first
                player.render(batch);
                // Then render tree tops
                renderTreeTops(batch, expandedBounds);
            }
        }

        // Render Pokémon above player's Y position
        for (WildPokemon pokemon : pokemonList) {
            if (pokemon.getY() >= player.getY()) {
                pokemon.render(batch);
            }
        }

        // Restore the previous batch color
        batch.setColor(prevColor);
    }

    private void renderTreeTops(SpriteBatch batch, Rectangle expandedBounds) {
        for (Map.Entry<Vector2, Chunk> entry : chunks.entrySet()) {
            Vector2 pos = entry.getKey();
            if (isChunkVisible(pos, expandedBounds)) {
                List<WorldObject> objects = objectManager.getObjectsForChunk(pos);
                for (WorldObject obj : objects) {
                    if (obj.getType() == WorldObject.ObjectType.TREE ||
                        obj.getType() == WorldObject.ObjectType.HAUNTED_TREE ||
                        obj.getType() == WorldObject.ObjectType.SNOW_TREE) {
                        objectManager.renderTreeTop(batch, obj);
                    }
                }
            }
        }
    }



    public boolean areChunksLoaded(Vector2 playerPosition, int renderSize) {
        // Calculate the range of chunks around the playerPosition based on renderSize
        int startX = (int) ((playerPosition.x - renderSize / 2) / CHUNK_SIZE);
        int endX = (int) ((playerPosition.x + renderSize / 2) / CHUNK_SIZE);
        int startY = (int) ((playerPosition.y - renderSize / 2) / CHUNK_SIZE);
        int endY = (int) ((playerPosition.y + renderSize / 2) / CHUNK_SIZE);

        for (int x = startX; x <= endX; x++) {
            for (int y = startY; y <= endY; y++) {
                Vector2 chunkPos = new Vector2(x, y);
                if (!chunks.containsKey(chunkPos)) {
                    return false;
                }
            }
        }
        return true;
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


    private double smoothstep(double x) {
        // Improved smoothstep for better transitions
        x = MathUtils.clamp((x - (double) 0), 0.0, 1.0);
        return x * x * x * (x * (x * 6 - 15) + 10);
    }


    // In World.java

    private void sendObjectUpdate(NetworkedWorldObject object, NetworkProtocol.NetworkObjectUpdateType type) {
        NetworkProtocol.WorldObjectUpdate update = new NetworkProtocol.WorldObjectUpdate();
        update.objectId = object.getId();
        update.type = type;
        update.x = object.getX();
        update.y = object.getY();
        update.objectType = object.getType();

        gameClient.sendWorldObjectUpdate(update);
    }

    public void removeNetworkedObject(String objectId) {
        NetworkedWorldObject object = syncedObjects.remove(objectId);
        if (object != null && gameClient != null && !gameClient.isSinglePlayer()) {
            sendObjectUpdate(object, NetworkProtocol.NetworkObjectUpdateType.REMOVE);
        }
    }


    public WorldObject.WorldObjectManager getObjectManager() {
        return objectManager;
    }

    // Optional debug method to help find biomes
    public void printBiomeInfo(float playerX, float playerY) {
        int worldX = (int) playerX / TILE_SIZE;
        int worldY = (int) playerY / TILE_SIZE;
        Biome currentBiome = getBiomeAt(worldX, worldY);
        GameLogger.info("Current position: (" + worldX + ", " + worldY + ") Biome: " + currentBiome.getName());
    }

    private Biome getTransitionBiome(BiomeType type1, BiomeType type2, double noise, double threshold) {
        double transitionWidth = 0.1; // Width of transition zone
        if (Math.abs(noise - threshold) < transitionWidth) {
            // We're in the transition zone
            return noise < threshold ?
                biomeManager.getBiome(type1) :
                biomeManager.getBiome(type2);
        }
        return noise < threshold ?
            biomeManager.getBiome(type1) :
            biomeManager.getBiome(type2);
    }

    private double smoothStep(double x) {
        return x * x * (3 - 2 * x);
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
                FileHandle worldDir = Gdx.files.local("worlds/" + worldData.getName());
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

    /**
     * Checks if a tile position collides with any Pokemon
     */
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

        // Check all Pokemon in range
        Collection<WildPokemon> nearbyPokemon = pokemonSpawnManager.getPokemonInRange(
            pixelX + ((float) TILE_SIZE / 2),
            pixelY + ((float) TILE_SIZE / 2),
            TILE_SIZE * 2  // Slightly larger range for better detection
        );

        for (WildPokemon pokemon : nearbyPokemon) {
            if (pokemon.getBoundingBox().overlaps(tileBox)) {
                return true;
            }
        }
        return false;
    }

    // In World.java
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
            return false; // Don't allow movement into unloaded areas
        }

        try {
            int chunkX = Math.floorDiv(worldX, Chunk.CHUNK_SIZE);
            int chunkY = Math.floorDiv(worldY, Chunk.CHUNK_SIZE);
            Vector2 chunkPos = new Vector2(chunkX, chunkY);

            Chunk chunk = chunks.get(chunkPos);
            if (chunk == null) return false;

            int localX = Math.floorMod(worldX, Chunk.CHUNK_SIZE);
            int localY = Math.floorMod(worldY, Chunk.CHUNK_SIZE);

            // Get current direction before any collision
            String currentDirection = player != null ? player.getDirection() : "down";

            // Check basic tile passability
            if (!chunk.isPassable(localX, localY)) {
                handleCollision(currentDirection);
                return false;
            }

            // Calculate exact collision box
            float pixelX = worldX * TILE_SIZE;
            float pixelY = worldY * TILE_SIZE;
            Rectangle movementBounds = new Rectangle(
                pixelX + (TILE_SIZE * 0.25f),
                pixelY + (TILE_SIZE * 0.25f),
                TILE_SIZE * 0.5f,
                TILE_SIZE * 0.5f
            );

            // Check object collisions with proper facing
            if (checkObjectCollision(movementBounds, currentDirection)) {
                return false;
            }

            // Check Pokemon collisions
            if (checkPokemonCollision(worldX, worldY, currentDirection)) {
                return false;
            }

            return true;
        } catch (Exception e) {
            GameLogger.error("Error checking passability: " + e.getMessage());
            return false;
        }
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
            if (obj.getBoundingBox().overlaps(movementBounds)) {
                if (player != null) {
                    // Make player face the object
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

    // Add this call in the update method:

    public List<Item> getCollectedItems() {
        return collectedItems;
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
                            //
                            //                            GameLogger.info("Found pokeball at distance: " + distance +
                            //                                " position: " + obj.getPixelX() + "," + obj.getPixelY() +
                            //                                " player at: " + playerPixelX + "," + playerPixelY);

                            if (distance <= INTERACTION_RANGE && distance < closestDistance) {
                                closestDistance = distance;
                                nearestPokeball = obj;
                                //                                GameLogger.info("Set as nearest pokeball");
                            }
                        }
                    }
                }
            }
        }

        if (nearestPokeball != null) {
            //            GameLogger.info("Nearest pokeball found at: " + nearestPokeball.getPixelX() + "," + nearestPokeball.getPixelY());
        } else {
            //            GameLogger.info("No pokeball found within range");
        }
    }private void synchronizeTime(NetworkProtocol.TimeSync timeSync) {
        if (worldData != null) {
            worldData.setWorldTimeInMinutes(timeSync.worldTimeInMinutes);
            worldData.setDayLength(timeSync.dayLength);
            // Calculate time offset
            long timeOffset = System.currentTimeMillis() - timeSync.timestamp;
        }
    }

    public WorldData serializeForNetwork() {
        WorldData data = new WorldData(this.name); // Assuming `name` is a field in `World`
        data.setWorldSeed(this.getWorldData().getConfig().getSeed());
        data.setWorldTimeInMinutes(this.getWorldData().getWorldTimeInMinutes());
        data.setPlayedTime(this.getWorldData().getPlayedTime());
        // Serialize other necessary fields and chunks
        return data;
    }

    public void initializeFromServerData(WorldData serverWorldData) {
        if (serverWorldData == null) {
            throw new IllegalArgumentException("Server world data cannot be null");
        }

        GameLogger.info("Initializing world from server data: " + serverWorldData.getName());

        try {
            // Set basic world properties
            this.name = serverWorldData.getName();
            this.worldSeed = serverWorldData.getConfig().getSeed();
            this.worldData = serverWorldData;

            // Clear existing chunks if any
            this.chunks.clear();

            // Initialize chunks from server data
            for (Map.Entry<Vector2, Chunk> entry : serverWorldData.getChunks().entrySet()) {
                Vector2 chunkPos = entry.getKey();
                Chunk chunkData = entry.getValue();

                // Create biome transition for this chunk
                BiomeTransitionResult biomeTransition = biomeManager.getBiomeAt(
                    chunkPos.x * Chunk.CHUNK_SIZE * TILE_SIZE,
                    chunkPos.y * Chunk.CHUNK_SIZE * TILE_SIZE
                );

                // Create new chunk with server data
                Chunk chunk = new Chunk(
                    (int) chunkPos.x,
                    (int) chunkPos.y,
                    biomeTransition.getPrimaryBiome(),
                    worldSeed,
                    biomeManager
                );

                // Set tile data from server
                int[][] chunkTileData = chunk.getTileData();
                for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
                    System.arraycopy(chunkData.getTileData()[x], 0, chunkTileData[x], 0, Chunk.CHUNK_SIZE);
                }

                chunks.put(chunkPos, chunk);

                // Initialize objects for this chunk
                List<WorldObject> objectsData = serverWorldData.getChunkObjects(chunkPos);
                if (objectsData != null) {
                    for (WorldObject objData : objectsData) {
                        WorldObject obj = createWorldObject(objData);
                        if (obj != null) {
                            objectManager.addObjectToChunk(chunkPos, obj);
                        }
                    }
                }
            }

            // Initialize other world systems
            this.pokemonSpawnManager = new PokemonSpawnManager(this, TextureManager.pokemonoverworld);

            GameLogger.info("World initialization complete. " +
                chunks.size() + " chunks loaded, Seed: " + worldSeed);

        } catch (Exception e) {
            GameLogger.error("Failed to initialize world from server data: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("World initialization failed", e);
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


    // In your World class
    private void initializeChunk(Vector2 chunkPosVec, Chunk incomingChunkData) {
        try {
            // Convert Vector2 to integer chunk coordinates
            int chunkX = Math.round(chunkPosVec.x);
            int chunkY = Math.round(chunkPosVec.y);
            Vector2 chunkPos = new Vector2(chunkX, chunkY);

            // Check if the chunk is already loaded
            if (chunks.containsKey(chunkPos)) {
                GameLogger.info("Chunk at " + chunkPos + " is already initialized.");
                return;
            }

            Chunk chunk;

            if (incomingChunkData != null) {
                // Loading existing chunk data (e.g., from network)
                chunk = incomingChunkData;

                // Optionally, verify that the incoming chunk's position matches
                if (chunk.getChunkX() != chunkX || chunk.getChunkY() != chunkY) {
                    throw new IllegalArgumentException("Chunk position mismatch.");
                }

                GameLogger.info("Loaded existing chunk at " + chunkPos + " with biome " +
                    chunk.getBiome().getType());
            } else {
                // Generate a new chunk
                double worldX = chunkX * Chunk.CHUNK_SIZE * TILE_SIZE;
                double worldY = chunkY * Chunk.CHUNK_SIZE * TILE_SIZE;

                // Get biome transition for this chunk position
                BiomeTransitionResult biomeTransition = biomeManager.getBiomeAt((float) worldX, (float) worldY);

                // Create new chunk with biome information
                chunk = new Chunk(
                    chunkX,
                    chunkY,
                    biomeTransition.getPrimaryBiome(),
                    worldSeed,
                    biomeManager
                );

                GameLogger.info("Generated new chunk at " + chunkPos + " with biome " +
                    biomeTransition.getPrimaryBiome().getType());
            }

            // Add chunk to the world
            chunks.put(chunkPos, chunk);

        } catch (IllegalArgumentException e) {
            GameLogger.error("Invalid chunk data: " + e.getMessage());
        } catch (Exception e) {
            GameLogger.error("Failed to initialize chunk at " + chunkPosVec + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private WorldObject createWorldObject(WorldObject objData) {
        try {
            WorldObject.ObjectType type = WorldObject.ObjectType.valueOf(String.valueOf(objData.type));
            TextureRegion texture = TextureManager.getTextureForObjectType(type);

            if (texture == null) {
                GameLogger.error("No texture found for object type: " + type);
                return null;
            }

            // Convert pixel coordinates to tile coordinates
            int tileX = objData.getTileX();
            int tileY = objData.getTileY();

            // Create world object
            WorldObject obj = new WorldObject(tileX, tileY, texture, type);

            // Set any additional properties from objData
            // For example, if you have custom properties:
            if (objData.getId() != null) {
                obj.setId(objData.getId());
            }

            return obj;

        } catch (Exception e) {
            GameLogger.error("Failed to create world object: " + e.getMessage());
            return null;
        }
    }

    // Add helper method to World class for biome-based object creation
    private WorldObject createObjectForBiome(Biome biome, float x, float y, Random random) {
        WorldObject.ObjectType objectType = getObjectTypeForBiome(biome, random);
        if (objectType == null) return null;

        TextureRegion texture = TextureManager.getTextureForObjectType(objectType);
        if (texture == null) {
            GameLogger.error("No texture found for object type: " + objectType);
            return null;
        }

        // Convert pixel coordinates to tile coordinates
        int tileX = (int) (x / TILE_SIZE);
        int tileY = (int) (y / TILE_SIZE);

        return new WorldObject(tileX, tileY, texture, objectType);
    }

    public List<WorldObject> generateChunkObjects(Chunk chunk, Vector2 chunkPos, long seed) {
        List<WorldObject> objects = new ArrayList<>();
        Random random = new Random((long) (seed + chunkPos.x * 31 + chunkPos.y * 17));

        // Calculate world coordinates for this chunk
        float worldX = chunkPos.x * Chunk.CHUNK_SIZE * TILE_SIZE;
        float worldY = chunkPos.y * Chunk.CHUNK_SIZE * TILE_SIZE;

        // Generate objects based on biome
        Biome biome = chunk.getBiome();
        float objectDensity = getObjectDensityForBiome(biome);

        for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
            for (int y = 0; y < Chunk.CHUNK_SIZE; y++) {
                if (random.nextFloat() < objectDensity) {
                    WorldObject object = generateObjectForBiome(
                        biome,
                        worldX + x * TILE_SIZE,
                        worldY + y * TILE_SIZE,
                        random
                    );
                    if (object != null) {
                        objects.add(object);
                    }
                }
            }
        }

        return objects;
    }

    private float getObjectDensityForBiome(Biome biome) {
        switch (biome.getType()) {
            case FOREST:
                return 0.15f;
            case SNOW:
                return 0.1f;
            case DESERT:
                return 0.05f;
            default:
                return 0.08f;
        }
    }

    private WorldObject generateObjectForBiome(Biome biome, float x, float y, Random random) {
        WorldObject.ObjectType objectType = getObjectTypeForBiome(biome, random);
        if (objectType == null) return null;

        TextureRegion texture = TextureManager.getTextureForObjectType(objectType);
        if (texture == null) return null;

        return new WorldObject((int) x, (int) y, texture, objectType);
    }

    private WorldObject.ObjectType getObjectTypeForBiome(Biome biome, Random random) {
        switch (biome.getType()) {
            case FOREST:
                return WorldObject.ObjectType.TREE;
            case SNOW:
                return WorldObject.ObjectType.SNOW_TREE;
            case DESERT:
                return WorldObject.ObjectType.CACTUS;
            case HAUNTED:
                return WorldObject.ObjectType.HAUNTED_TREE;
            default:
                return random.nextFloat() < 0.3f ? WorldObject.ObjectType.TREE : null;
        }
    }

    // Add method to initialize from world data

    private List<WorldObject> createObjectsFromData(List<WorldData.WorldObjectData> objectDataList) {
        if (objectDataList == null) return new ArrayList<>();

        return objectDataList.stream()
            .map(data -> {
                TextureRegion texture = TextureManager.getTextureForObjectType(
                    WorldObject.ObjectType.valueOf(data.type)
                );
                return new WorldObject(
                    (int) data.x,
                    (int) data.y,
                    texture,
                    WorldObject.ObjectType.valueOf(data.type)
                );
            })
            .collect(Collectors.toList());
    }

    // Add to World.java if not already present


    public WorldObject getNearestPokeball() {
        return nearestPokeball;
    }


    private static class ChunkLoadRequest implements Comparable<ChunkLoadRequest> {
        final Vector2 position;
        final float priority;

        ChunkLoadRequest(Vector2 position, float priority) {
            this.position = position;
            this.priority = priority;
        }

        @Override
        public int compareTo(ChunkLoadRequest other) {
            return Float.compare(other.priority, this.priority); // Higher priority first
        }
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

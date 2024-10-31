package io.github.pokemeetup.system.gameplay.overworld;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import io.github.pokemeetup.audio.AudioManager;
import io.github.pokemeetup.managers.AutoSaveManager;
import io.github.pokemeetup.managers.BiomeManager;
import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.multiplayer.network.NetworkedWorldObject;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.PlayerData;
import io.github.pokemeetup.system.gameplay.overworld.biomes.Biome;
import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;
import io.github.pokemeetup.system.gameplay.overworld.biomes.TransitionBiome;
import io.github.pokemeetup.system.gameplay.overworld.multiworld.WorldData;
import io.github.pokemeetup.system.gameplay.inventory.Item;
import io.github.pokemeetup.system.gameplay.inventory.ItemManager;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.PerlinNoise;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class World {
    public static final int WORLD_SIZE = 1600; // Size in tiles
    public static final int TILE_SIZE = 32;
    private static final float BIOME_SCALE = 0.004f;

    private Player player;
    private PlayerData currentPlayerData;
    public long getWorldSeed() {
        return worldSeed;
    }

    private static final float INTERACTION_RANGE = 32f; // Adjust as needed
    private static final float BIOME_BASE_SCALE = 0.005f; // Increase for smaller biome areas
    private static final float DETAIL_SCALE = 0.01f;      // Increase for more detail variation
    private static final int OCTAVES = 6;                 // Increased octaves
    private static final float PERSISTENCE = 0.5f;
    private static final float LACUNARITY = 2.0f;         // Adjusted to 2.0
    private static final String SAVE_DIR = "assets/worlds/";
    private static final String CHUNK_DATA_FILE = "chunks.json";
    public static int DEFAULT_X_POSITION = 800;
    public static int DEFAULT_Y_POSITION = 800;    // Adjust these constants for biome size and transitions
    // New noise generators
    private final PerlinNoise temperatureNoiseGenerator;
    private final PerlinNoise moistureNoiseGenerator;
    private final Map<String, NetworkedWorldObject> syncedObjects;
    private final GameClient gameClient;
    private WorldObject nearestPokeball;
    private Map<Vector2, Chunk> chunks;
    private BiomeManager biomeManager;
    private PerlinNoise biomeNoiseGenerator;
    private TextureAtlas atlas; // Add this if you don't have it already
    private Map<Integer, TextureRegion> tileTextures;
    private Rectangle worldBounds;   // Add to existing fields
    private long lastPlayed;
    private String name;
    private WorldData worldData;
    //    private static final int DEBUG_CHUNK_RADIUS = 1; // For testing, render fewer chunks
    private AutoSaveManager autoSaveManager;
    private long worldSeed;
    private WorldObject.WorldObjectManager objectManager;
    private PlayerData playerData; // Change from PlayerSaveData
    private List<Item> collectedItems = new ArrayList<>();

    public World(String name, TextureAtlas atlas, Map<Integer, TextureRegion> tileTextures, int worldWidth, int worldHeight, long seed, GameClient gameClient) {

        this.gameClient = gameClient;
        biomeManager = new BiomeManager(atlas);
        this.chunks = new HashMap<>();
        this.worldSeed = seed;
        this.tileTextures = tileTextures;
        this.worldBounds = new Rectangle(0, 0, worldWidth, worldHeight);
        this.name = name;
        this.worldData = new WorldData(name, seed);
        this.autoSaveManager = new AutoSaveManager(this, gameClient);
        this.atlas = atlas;
        this.biomeNoiseGenerator = new PerlinNoise((int) seed);
        this.temperatureNoiseGenerator = new PerlinNoise((int) (seed + 100));
        this.moistureNoiseGenerator = new PerlinNoise((int) (seed + 200));
        this.syncedObjects = new ConcurrentHashMap<>();
        initializeTextureAtlas(atlas);
        TextureRegion treeTexture = atlas.findRegion("tree");
        TextureRegion pokeballTexture = atlas.findRegion("pokeball");  // Debug texture loading
        if (treeTexture == null) {
            GameLogger.info("Failed to load tree texture from atlas");
        }
        if (pokeballTexture == null) {
            GameLogger.info("Failed to load pokeball texture from atlas");
        }
        objectManager = new WorldObject.WorldObjectManager(atlas, worldSeed, gameClient);
        // Verify textures are loaded
        GameLogger.info("Tile textures loaded: " + tileTextures.size());
        for (Map.Entry<Integer, TextureRegion> entry : tileTextures.entrySet()) {
            GameLogger.info("Tile type " + entry.getKey() + ": " +
                (entry.getValue() != null ? "loaded" : "null"));
        }// Load or create world data
        FileHandle worldFile = Gdx.files.local("assets/worlds/" + name + "/world.json");
        if (worldFile.exists()) {
            try {
                Json json = new Json();
                WorldData.setupJson(json);
                String content = worldFile.readString();
                GameLogger.info("Loading world file content: " + content);
                this.worldData = json.fromJson(WorldData.class, content);
                if (this.worldData == null) {
                    throw new RuntimeException("Failed to deserialize world data");
                }
                GameLogger.info("World loaded successfully");
            } catch (Exception e) {
                GameLogger.info("Failed to load world, creating new: " + e.getMessage());
                this.worldData = new WorldData(name, seed);
            }
        } else {
            GameLogger.info("No existing world file found, creating new");
            this.worldData = new WorldData(name, seed);
        }
    }    // Add setter for PlayerData

    private double generateTemperatureNoise(double x, double y) {
        double noise = 0;
        double amplitude = 1;
        double frequency = 1.0;
        double maxValue = 0;

        for (int i = 0; i < OCTAVES; i++) {
            double rotatedX = x * Math.cos(0.3) - y * Math.sin(0.3);
            double rotatedY = x * Math.sin(0.3) + y * Math.cos(0.3);

            noise += amplitude * temperatureNoiseGenerator.noise(rotatedX, rotatedY);
            maxValue += amplitude;
            amplitude *= PERSISTENCE;
            frequency *= LACUNARITY;
        }

        return noise / maxValue; // Normalize to [-1, 1]
    }

    private double generateMoistureNoise(double x, double y) {
        double noise = 0;
        double amplitude = 1;
        double frequency = 1.0;
        double maxValue = 0;

        for (int i = 0; i < OCTAVES; i++) {
            double rotatedX = x * Math.cos(0.3) - y * Math.sin(0.3);
            double rotatedY = x * Math.sin(0.3) + y * Math.cos(0.3);

            noise += amplitude * moistureNoiseGenerator.noise(rotatedX, rotatedY);
            maxValue += amplitude;
            amplitude *= PERSISTENCE;
            frequency *= LACUNARITY;
        }

        return noise / maxValue; // Normalize to [-1, 1]
    }


    private double generateOctaveNoise(double x, double y) {
        double noise = 0;
        double amplitude = 1;
        double frequency = 1.0;
        double maxValue = 0;

        for (int i = 0; i < OCTAVES; i++) {
            double nx = x * frequency;
            double ny = y * frequency;
            double rotatedX = nx * Math.cos(0.3) - ny * Math.sin(0.3);
            double rotatedY = nx * Math.sin(0.3) + ny * Math.cos(0.3);

            noise += amplitude * biomeNoiseGenerator.noise(rotatedX, rotatedY);
            maxValue += amplitude;
            amplitude *= PERSISTENCE;
            frequency *= 2.5;  // Increase lacunarity beyond typical value
        }

        return noise / maxValue;
    }

    private double generateDetailNoise(int worldX, int worldY) {
        double perturbFactor = 0.01 * (Math.random() - 0.5); // Small random factor
        double nx = (worldX + perturbFactor) * DETAIL_SCALE;
        double ny = (worldY + perturbFactor) * DETAIL_SCALE;

        double warpX = biomeNoiseGenerator.noise(ny * 0.5, nx * 0.5) * 4.0;
        double warpY = biomeNoiseGenerator.noise(nx * 0.5, ny * 0.5) * 4.0;

        return biomeNoiseGenerator.noise(nx + warpX, ny + warpY);
    }

    // Optional debug method to verify distribution
    public void validateBiomeDistribution() {
        int sampleSize = 100;
        int samples = sampleSize * sampleSize;
        Map<BiomeType, Integer> counts = new HashMap<>();

        for (int x = 0; x < sampleSize; x++) {
            for (int y = 0; y < sampleSize; y++) {
                Biome biome = getBiomeAt(
                    (x - sampleSize / 2) * 100,
                    (y - sampleSize / 2) * 100
                );
                counts.merge(biome.getType(), 1, Integer::sum);
            }
        }

        GameLogger.info("\nBiome Distribution Analysis:");
        counts.forEach((type, count) -> {
            double percentage = (count * 100.0) / samples;
        });
    }

    private Biome smoothTransition(BiomeType type1, BiomeType type2, double t) {
        // Improved smooth step function
        t = t * t * (3 - 2 * t);

        // Ensure we're always returning a valid biome
        return t < 0.5 ?
            biomeManager.getBiome(type1) :
            biomeManager.getBiome(type2);
    }

    private void validateBiomeGeneration(int x, int y, Biome biome) {
        if (biome == null) {
            throw new IllegalStateException(
                String.format("Generated null biome at (%d, %d)", x, y)
            );
        }
    }

    // Add this method to World class for debugging biome distribution
    public void debugBiomeDistribution() {
        int sampleSize = 1000;
        Map<BiomeType, Integer> distribution = new HashMap<>();

        for (int i = 0; i < sampleSize; i++) {
            for (int j = 0; j < sampleSize; j++) {
                Biome biome = getBiomeAt(i, j);
                distribution.merge(biome.getType(), 1, Integer::sum);
            }
        }

        GameLogger.info("Biome Distribution Analysis:");
        distribution.forEach((type, count) -> {
            double percentage = (count * 100.0) / (sampleSize * sampleSize);
        });
    }

    private void initializeTextureAtlas(TextureAtlas atlas) {
        // Add 1px padding to prevent texture bleeding
        for (TextureAtlas.AtlasRegion region : atlas.getRegions()) {
            region.setRegionX(region.getRegionX() + 1);
            region.setRegionY(region.getRegionY() + 1);
            region.setRegionWidth(region.getRegionWidth() - 2);
            region.setRegionHeight(region.getRegionHeight() - 2);
        }
    }

    // Add getter/setter
    public long getLastPlayed() {
        return lastPlayed;
    }

    public void setLastPlayed(long lastPlayed) {
        this.lastPlayed = lastPlayed;
    }


    public void setPlayerData(PlayerData playerData) {
        this.playerData = playerData;
    }

    public Player getPlayer() {
        return player;
    }



    public void saveChunkData(Vector2 chunkPos, Chunk chunk, boolean isMultiplayer) {
        try {
            // Create base save directory based on whether it's multiplayer
            String baseDir = isMultiplayer ?
                "assets/multiplayer/worlds/" + name + "/chunks/" :
                "assets/worlds/" + name + "/chunks/";

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

            GameLogger.info("Saved chunk data: " + filename +
                (isMultiplayer ? " (multiplayer)" : " (single-player)"));

        } catch (Exception e) {
            GameLogger.info("Error saving chunk data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Chunk loadChunkData(Vector2 chunkPos, boolean isMultiplayer) {
        try {
            String baseDir = isMultiplayer ?
                "assets/multiplayer/worlds/" + name + "/chunks/" :
                "assets/worlds/" + name + "/chunks/";

            String filename = String.format("chunk_%d_%d.json", (int) chunkPos.x, (int) chunkPos.y);
            FileHandle chunkFile = Gdx.files.local(baseDir + filename);

            if (!chunkFile.exists()) {
                return null;
            }

            Json json = new Json();
            ChunkData chunkData = json.fromJson(ChunkData.class, chunkFile.readString());
            chunkData.validate();

            // Create new chunk with saved data
            Biome biome = biomeManager.getBiome(chunkData.biomeType);
            Chunk chunk = new Chunk(chunkData.x, chunkData.y, biome, biome.getTileTextures(), worldSeed);

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

            GameLogger.info("Loaded chunk data: " + filename +
                (isMultiplayer ? " (multiplayer)" : " (single-player)"));

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

    // Update the save method to include chunk saving
    public void setPlayer(Player player) {
        this.player = player;
        // Initialize player data
        this.currentPlayerData = new PlayerData(player.getUsername());
        this.currentPlayerData.updateFromPlayer(player);
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

    public Biome getBiomeAt(int worldX, int worldY) {
        // Make biomes even larger
        double noiseScale = 0.001;  // Reduced further for larger biomes

        // Get coordinates relative to world center
        double nx = (worldX - WORLD_SIZE / 2) * noiseScale;
        double ny = (worldY - WORLD_SIZE / 2) * noiseScale;

        // Primary noise for base biome distribution
        double noise = biomeNoiseGenerator.noise(nx, ny);

        // Secondary noise rotated 45 degrees for variation
        double angle = Math.PI / 4;
        double nx2 = nx * Math.cos(angle) - ny * Math.sin(angle);
        double ny2 = nx * Math.sin(angle) + ny * Math.cos(angle);
        double noise2 = biomeNoiseGenerator.noise(nx2, ny2) * 0.3;

        // Strong temperature gradient based on y-coordinate
        double temperatureGradient = (double) (worldY - WORLD_SIZE / 2) / (WORLD_SIZE / 2);

        // Combine noises
        double finalNoise = noise + noise2 + temperatureGradient;
        finalNoise = (finalNoise + 1.5) / 3.0;

        // More extreme distribution
        if (temperatureGradient > 0.3) {  // Northern regions
            return biomeManager.getBiome(BiomeType.SNOW);
        } else if (finalNoise < 0.4) {
            return biomeManager.getBiome(BiomeType.HAUNTED);
        } else if (finalNoise < 0.7) {
            return biomeManager.getBiome(BiomeType.PLAINS);
        } else {
            return biomeManager.getBiome(BiomeType.FOREST);
        }
    }

    private void loadOrGenerateChunk(Vector2 chunkPos) {
        boolean isMultiplayer = gameClient != null && !gameClient.isSinglePlayer();

        // Try to load existing chunk
        Chunk chunk = loadChunkData(chunkPos, isMultiplayer);

        if (chunk == null) {
            // Generate new chunk if no saved data exists
            int worldX = (int) (chunkPos.x * Chunk.CHUNK_SIZE);
            int worldY = (int) (chunkPos.y * Chunk.CHUNK_SIZE);
            Biome biome = getBiomeAt(worldX, worldY);

            chunk = new Chunk(
                (int) chunkPos.x,
                (int) chunkPos.y,
                biome,
                biome.getTileTextures(),
                worldSeed
            );

            // Generate objects for new chunk
            objectManager.generateObjectsForChunk(chunkPos, chunk, biome);

            // Save the newly generated chunk
            saveChunkData(chunkPos, chunk, isMultiplayer);

            GameLogger.info("Generated new chunk at " + chunkPos.x + "," + chunkPos.y +
                " with biome " + biome.getName() +
                (isMultiplayer ? " (multiplayer)" : " (single-player)"));
        }

        chunks.put(chunkPos, chunk);
    }

    private Chunk loadChunkFromSave(Vector2 chunkPos) {
        if (worldData == null) return null;

        try {
            // Look for chunk data in the saved world data
            Json json = new Json();
            FileHandle chunkFile = Gdx.files.local(SAVE_DIR + name + "/chunks/" +
                chunkPos.x + "_" + chunkPos.y + ".json");

            if (!chunkFile.exists()) return null;

            // Load chunk data
            String chunkContent = chunkFile.readString();
            ChunkData chunkData = json.fromJson(ChunkData.class, chunkContent);

            if (chunkData != null) {
                // Recreate chunk from saved data
                Biome biome = biomeManager.getBiome(chunkData.biomeType);
                Chunk chunk = new Chunk(
                    (int) chunkPos.x,
                    (int) chunkPos.y,
                    biome,
                    biome.getTileTextures(),
                    worldSeed
                );

                // Restore tile data
                if (chunkData.tileData != null) {
                    for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
                        for (int y = 0; y < Chunk.CHUNK_SIZE; y++) {
                            chunk.getTileData()[x][y] = chunkData.tileData[x][y];
                        }
                    }
                }

                // Restore any saved objects
                if (chunkData.objects != null) {
                    for (WorldObjectData objData : chunkData.objects) {
                        WorldObject obj = objectManager.createObject(
                            objData.type,
                            objData.x,
                            objData.y
                        );
                        objectManager.addObjectToChunk(chunkPos, obj);
                    }
                }

                GameLogger.info("Loaded chunk from save at " + chunkPos.x + "," + chunkPos.y);
                return chunk;
            }
        } catch (Exception e) {
            GameLogger.info("Error loading chunk: " + e.getMessage());
        }

        return null;
    }

    public void update(float delta, Vector2 playerPosition, float viewportWidth, float viewportHeight) {
        int chunksHorizontally = (int) Math.ceil(viewportWidth / (Chunk.CHUNK_SIZE * TILE_SIZE)) + 2;
        int chunksVertically = (int) Math.ceil(viewportHeight / (Chunk.CHUNK_SIZE * TILE_SIZE)) + 2;

        int playerChunkX = (int) Math.floor(playerPosition.x / (Chunk.CHUNK_SIZE * TILE_SIZE));
        int playerChunkY = (int) Math.floor(playerPosition.y / (Chunk.CHUNK_SIZE * TILE_SIZE));

        // Load or generate needed chunks
        for (int x = playerChunkX - chunksHorizontally / 2; x <= playerChunkX + chunksHorizontally / 2; x++) {
            for (int y = playerChunkY - chunksVertically / 2; y <= playerChunkY + chunksVertically / 2; y++) {
                Vector2 chunkPos = new Vector2(x, y);
                if (!chunks.containsKey(chunkPos)) {
                    loadOrGenerateChunk(chunkPos);
                }
            }
        }
        Biome currentBiome = getBiomeAt((int) playerPosition.x / TILE_SIZE, (int) playerPosition.y / TILE_SIZE);
        AudioManager.getInstance().updateBiomeMusic(currentBiome.getType());
        AudioManager.getInstance().update(delta);
        int startX = playerChunkX - chunksHorizontally / 2;
        int endX = playerChunkX + chunksHorizontally / 2;
        int startY = playerChunkY - chunksVertically / 2;
        int endY = playerChunkY + chunksVertically / 2;

        for (int x = startX; x <= endX; x++) {
            for (int y = startY; y <= endY; y++) {
                Vector2 chunkPos = new Vector2(x, y);
                if (!chunks.containsKey(chunkPos)) {
                    // Get the biome for this chunk
                    int worldX = x * Chunk.CHUNK_SIZE;
                    int worldY = y * Chunk.CHUNK_SIZE;
                    Biome biome = getBiomeAt(worldX, worldY);

                    Map<Integer, TextureRegion> biomeTileTextures = biome.getTileTextures();
                    Chunk newChunk = new Chunk(x, y, biome, biomeTileTextures, worldSeed);
                    chunks.put(chunkPos, newChunk);

                    // Generate objects specific to the biome
                    objectManager.generateObjectsForChunk(chunkPos, newChunk, biome);

                    GameLogger.info("Created new chunk at " + x + "," + y + " with biome " + biome.getName());
                }
            }
        }
        objectManager.update(chunks);
        unloadDistantChunks(playerChunkX, playerChunkY, chunksHorizontally, chunksVertically);

        checkPlayerInteractions(playerPosition);
        autoSaveManager.update(delta);
    }


    public void render(SpriteBatch batch, Rectangle viewBounds, Player player) {
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        // First render terrain and tree bases
        for (TextureRegion region : tileTextures.values()) {
            region.getTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        }
        for (Map.Entry<Vector2, Chunk> entry : chunks.entrySet()) {
            if (isChunkVisible(entry.getKey(), viewBounds)) {
                renderChunkWithoutSeams(batch, entry.getValue(), entry.getKey());
            }
        }
        for (Map.Entry<Vector2, Chunk> entry : chunks.entrySet()) {
            Vector2 pos = entry.getKey();
            if (isChunkVisible(pos, viewBounds)) {
                List<WorldObject> objects = objectManager.getObjectsForChunk(pos);
                for (WorldObject obj : objects) {
                    if (obj.getType() == WorldObject.ObjectType.TREE) {
                        objectManager.renderTreeBase(batch, obj);
                    }
                    if (obj.getType() == WorldObject.ObjectType.HAUNTED_TREE) {
                        objectManager.renderTreeBase(batch, obj);
                    }
                    if (obj.getType() == WorldObject.ObjectType.SNOW_TREE) {
                        objectManager.renderTreeBase(batch, obj);
                    } else if (obj.getType() == WorldObject.ObjectType.POKEBALL) {
                        obj.render(batch);
                    }
                }
            }
        }

        // Calculate if player is behind any tree tops
        boolean playerBehindTree = objectManager.isPlayerUnderTree(
            player.getX(),
            player.getY(),
            Player.FRAME_WIDTH,
            Player.FRAME_HEIGHT
        );

        // If player is not behind trees, render them now
        if (playerBehindTree) {
            player.render(batch);
        }

        // Render all tree tops
        for (Map.Entry<Vector2, Chunk> entry : chunks.entrySet()) {
            Vector2 pos = entry.getKey();
            if (isChunkVisible(pos, viewBounds)) {
                List<WorldObject> objects = objectManager.getObjectsForChunk(pos);
                for (WorldObject obj : objects) {
                    if (obj.getType() == WorldObject.ObjectType.TREE) {
                        objectManager.renderTreeTop(batch, obj);
                    }
                    if (obj.getType() == WorldObject.ObjectType.HAUNTED_TREE) {
                        objectManager.renderTreeTop(batch, obj);
                    }
                    if (obj.getType() == WorldObject.ObjectType.SNOW_TREE) {
                        objectManager.renderTreeTop(batch, obj);
                    }
                }
            }
        }

        // If player is behind trees, render them after tree tops
        if (!playerBehindTree) {
            player.render(batch);
        }

        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    private void renderChunkWithoutSeams(SpriteBatch batch, Chunk chunk, Vector2 chunkPos) {
        float xOffset = chunkPos.x * Chunk.CHUNK_SIZE * TILE_SIZE;
        float yOffset = chunkPos.y * Chunk.CHUNK_SIZE * TILE_SIZE;

        for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
            for (int y = 0; y < Chunk.CHUNK_SIZE; y++) {
                float worldX = xOffset + x * TILE_SIZE;
                float worldY = yOffset + y * TILE_SIZE;

                // Add a small overlap to prevent seams
                float overlap = 0.5f;
                float width = TILE_SIZE + overlap;
                float height = TILE_SIZE + overlap;

                int tileType = chunk.getTileType(x, y);
                TextureRegion tileTexture = tileTextures.get(tileType);

                if (tileTexture != null) {
                    batch.draw(tileTexture, worldX, worldY, width, height);
                }
            }
        }
    }

    private boolean isChunkVisible(Vector2 chunkPos, Rectangle viewBounds) {
        float chunkWorldX = chunkPos.x * Chunk.CHUNK_SIZE * TILE_SIZE;
        float chunkWorldY = chunkPos.y * Chunk.CHUNK_SIZE * TILE_SIZE;
        Rectangle chunkBounds = new Rectangle(chunkWorldX, chunkWorldY,
            Chunk.CHUNK_SIZE * TILE_SIZE, Chunk.CHUNK_SIZE * TILE_SIZE);

        return viewBounds.overlaps(chunkBounds);
    }

    private double[] calculateBiomeWeights(int x, int y, int size) {
        double[] weights = new double[4];
        double fx = (double) x / size;
        double fy = (double) y / size;

        // Smooth interpolation
        fx = smoothstep(fx);
        fy = smoothstep(fy);

        weights[0] = (1 - fx) * (1 - fy);
        weights[1] = fx * (1 - fy);
        weights[2] = (1 - fx) * fy;
        weights[3] = fx * fy;

        return weights;
    }

    private Biome createTransitionBiome(Map<BiomeType, Float> biomeWeights) {
        // Find the two most dominant biomes
        BiomeType primaryBiome = null;
        BiomeType secondaryBiome = null;
        float primaryWeight = 0;
        float secondaryWeight = 0;

        for (Map.Entry<BiomeType, Float> entry : biomeWeights.entrySet()) {
            if (entry.getValue() > primaryWeight) {
                secondaryBiome = primaryBiome;
                secondaryWeight = primaryWeight;
                primaryBiome = entry.getKey();
                primaryWeight = entry.getValue();
            } else if (entry.getValue() > secondaryWeight) {
                secondaryBiome = entry.getKey();
                secondaryWeight = entry.getValue();
            }
        }

        // If one biome is strongly dominant, return it directly
        if (primaryWeight > 0.75f) {
            return biomeManager.getBiome(primaryBiome);
        }

        // Create a transition biome
        TransitionBiome transitionBiome = new TransitionBiome(
            biomeManager.getBiome(primaryBiome),
            biomeManager.getBiome(secondaryBiome),
            primaryWeight
        );

        // Add tile distributions based on weights
        for (Map.Entry<Integer, Integer> entry :
            biomeManager.getBiome(primaryBiome).getTileDistribution().entrySet()) {
            transitionBiome.addTileDistribution(
                entry.getKey(),
                (int) (entry.getValue() * primaryWeight)
            );
        }

        for (Map.Entry<Integer, Integer> entry :
            biomeManager.getBiome(secondaryBiome).getTileDistribution().entrySet()) {
            transitionBiome.addTileDistribution(
                entry.getKey(),
                (int) (entry.getValue() * (1 - primaryWeight))
            );
        }

        return transitionBiome;
    }


    // In World.java

    private double smoothstep(double x) {
        // Improved smoothstep for better transitions
        x = MathUtils.clamp((x - (double) 0), 0.0, 1.0);
        return x * x * x * (x * (x * 6 - 15) + 10);
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

    public void removeNetworkedObject(String objectId) {
        NetworkedWorldObject object = syncedObjects.remove(objectId);
        if (object != null && gameClient != null && !gameClient.isSinglePlayer()) {
            sendObjectUpdate(object, NetworkProtocol.NetworkObjectUpdateType.REMOVE);
        }
    }

    public void handleNetworkObjectUpdate(NetworkProtocol.WorldObjectUpdate update) {
        switch (update.type) {
            case ADD:
                NetworkedWorldObject newObject = NetworkedWorldObject.createFromUpdate(update);
                syncedObjects.put(update.objectId, newObject);
                break;

            case UPDATE:
                NetworkedWorldObject existing = syncedObjects.get(update.objectId);
                if (existing != null) {
                    existing.updateFromNetwork(update);
                }
                break;

            case REMOVE:
                syncedObjects.remove(update.objectId);
                break;
        }
    }

    public void updateNetworkedObject(NetworkedWorldObject object) {
        syncedObjects.put(object.getId(), object);
        if (gameClient != null && !gameClient.isSinglePlayer()) {
            sendObjectUpdate(object, NetworkProtocol.NetworkObjectUpdateType.UPDATE);
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

    public boolean isPassable(int worldX, int worldY) {
        // Only check world size upper bound
        if (Math.abs(worldX) >= WORLD_SIZE || Math.abs(worldY) >= WORLD_SIZE) {
            return false;
        }

        // Use floorDiv for correct negative coordinate handling
        int chunkX = Math.floorDiv(worldX, Chunk.CHUNK_SIZE);
        int chunkY = Math.floorDiv(worldY, Chunk.CHUNK_SIZE);
        Vector2 chunkPos = new Vector2(chunkX, chunkY);

        Chunk chunk = chunks.get(chunkPos);
        if (chunk == null) {
            Biome biome = getBiomeAt(worldX, worldY);
            chunk = new Chunk(chunkX, chunkY, biome, biome.getTileTextures(), worldSeed);
            chunks.put(chunkPos, chunk);
        }

        // Use floorMod for correct local coordinates with negatives
        int localX = Math.floorMod(worldX, Chunk.CHUNK_SIZE);
        int localY = Math.floorMod(worldY, Chunk.CHUNK_SIZE);

        return chunk.isPassable(localX, localY) &&
            !objectManager.isColliding(worldX * TILE_SIZE, worldY * TILE_SIZE,
                TILE_SIZE, TILE_SIZE);
    }

    public List<Item> getCollectedItems() {
        return collectedItems;
    }

    private void checkPlayerInteractions(Vector2 playerPosition) {
        float playerX = playerPosition.x;
        float playerY = playerPosition.y;

        nearestPokeball = null;
        float closestDistance = Float.MAX_VALUE;

        // Determine which chunk the player is in
        int chunkX = (int) Math.floor(playerX / (Chunk.CHUNK_SIZE * TILE_SIZE));
        int chunkY = (int) Math.floor(playerY / (Chunk.CHUNK_SIZE * TILE_SIZE));
        Vector2 chunkPos = new Vector2(chunkX, chunkY);

        List<WorldObject> objects = objectManager.getObjectsForChunk(chunkPos);
        if (objects != null) {
            for (WorldObject obj : objects) {
                if (obj.getType() == WorldObject.ObjectType.POKEBALL) {
                    // Calculate distance to the pokeball
                    float dx = playerX - obj.getPixelX();
                    float dy = playerY - obj.getPixelY();
                    float distance = (float) Math.sqrt(dx * dx + dy * dy);

                    if (distance <= INTERACTION_RANGE && distance < closestDistance) {
                        closestDistance = distance;
                        nearestPokeball = obj;
                    }
                }
            }
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

    public WorldObject getNearestPokeball() {
        return nearestPokeball;
    }

    private Item generateRandomItem() {
        List<String> itemNames = new ArrayList<>(ItemManager.getAllItemNames());
        int index = MathUtils.random(itemNames.size() - 1);
        String itemName = itemNames.get(index);
        return ItemManager.getItem(itemName);
    }

    // Update the unloadDistantChunks method
    private void unloadDistantChunks(int playerChunkX, int playerChunkY, int chunksHorizontally, int chunksVertically) {
        int startX = playerChunkX - chunksHorizontally;
        int endX = playerChunkX + chunksHorizontally;
        int startY = playerChunkY - chunksVertically;
        int endY = playerChunkY + chunksVertically;

        // Create a list of chunks to remove
        ArrayList<Vector2> chunksToRemove = new ArrayList<>();
        for (Vector2 chunkPos : chunks.keySet()) {
            int x = (int) chunkPos.x;
            int y = (int) chunkPos.y;
            if (x < startX || x > endX || y < startY || y > endY) {
                // Save chunk before removing it
                Chunk chunk = chunks.get(chunkPos);
                if (chunk != null) {
                    boolean isMultiplayer = gameClient != null && !gameClient.isSinglePlayer();
                    saveChunkData(chunkPos, chunk, isMultiplayer);
                }
                chunksToRemove.add(chunkPos);
            }
        }

        // Remove distant chunks
        for (Vector2 chunkPos : chunksToRemove) {
            chunks.remove(chunkPos);
        }
    }

    private static class ChunkData {
        public int x;
        public int y;
        public BiomeType biomeType;
        public int[][] tileData;
        public List<WorldObjectData> objects;
        public long lastModified;
        public boolean isMultiplayer;

        // Empty constructor for serialization
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

    private static class WorldObjectData {
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

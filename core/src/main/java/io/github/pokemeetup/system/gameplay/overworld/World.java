package io.github.pokemeetup.system.gameplay.overworld;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import io.github.pokemeetup.managers.AutoSaveManager;
import io.github.pokemeetup.managers.BiomeManager;
import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.PlayerData;
import io.github.pokemeetup.system.gameplay.overworld.biomes.Biome;
import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;
import io.github.pokemeetup.system.gameplay.overworld.multiworld.WorldData;
import io.github.pokemeetup.system.inventory.Item;
import io.github.pokemeetup.system.inventory.ItemManager;
import io.github.pokemeetup.utils.PerlinNoise;

import java.util.*;

public class World {
    public static final int WORLD_SIZE = 1600; // Size in tiles
    public static final int TILE_SIZE = 32;
    private static final float INTERACTION_RANGE = 32f; // Adjust as needed
    public static int DEFAULT_X_POSITION = 800;
    public static int DEFAULT_Y_POSITION = 800;
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
    private Player player; // Add this field

    public World(String name, TextureAtlas atlas, Map<Integer, TextureRegion> tileTextures, int worldWidth, int worldHeight, long seed, GameClient gameClient) {

        this.gameClient = gameClient;
        biomeManager = new BiomeManager(atlas);
        this.chunks = new HashMap<>();
        this.worldSeed = seed;
        this.biomeNoiseGenerator = new PerlinNoise((int) seed);
        this.tileTextures = tileTextures;
        this.worldBounds = new Rectangle(0, 0, worldWidth, worldHeight);
        this.name = name;
        this.worldData = new WorldData(name, seed);
        this.autoSaveManager = new AutoSaveManager(this, gameClient);
        this.atlas = atlas;

        TextureRegion treeTexture = atlas.findRegion("tree");
        TextureRegion pokeballTexture = atlas.findRegion("pokeball");  // Debug texture loading
        if (treeTexture == null) {
            System.err.println("Failed to load tree texture from atlas");
        }
        if (pokeballTexture == null) {
            System.err.println("Failed to load pokeball texture from atlas");
        }
        objectManager = new WorldObject.WorldObjectManager(atlas, worldSeed);
        // Verify textures are loaded
        System.out.println("Tile textures loaded: " + tileTextures.size());
        for (Map.Entry<Integer, TextureRegion> entry : tileTextures.entrySet()) {
            System.out.println("Tile type " + entry.getKey() + ": " +
                (entry.getValue() != null ? "loaded" : "null"));
        }// Load or create world data
        FileHandle worldFile = Gdx.files.local("worlds/" + name + "/world.json");
        if (worldFile.exists()) {
            try {
                Json json = new Json();
                WorldData.setupJson(json);
                String content = worldFile.readString();
                System.out.println("Loading world file content: " + content);
                this.worldData = json.fromJson(WorldData.class, content);
                if (this.worldData == null) {
                    throw new RuntimeException("Failed to deserialize world data");
                }
                System.out.println("World loaded successfully");
            } catch (Exception e) {
                System.err.println("Failed to load world, creating new: " + e.getMessage());
                this.worldData = new WorldData(name, seed);
            }
        } else {
            System.out.println("No existing world file found, creating new");
            this.worldData = new WorldData(name, seed);
        }
    }    // Add setter for PlayerData

    // Add getter/setter
    public long getLastPlayed() {
        return lastPlayed;
    }

    public void setLastPlayed(long lastPlayed) {
        this.lastPlayed = lastPlayed;
    }

    public PlayerData getPlayerData() {
        return this.playerData;
    }

    public void setPlayerData(PlayerData playerData) {
        this.playerData = playerData;
    }

    public Player getPlayer() {
        return player;
    }

    public void setPlayer(Player player) {
        this.player = player;
        if (player != null && worldData != null) {
            // Load existing player data or create new
            PlayerData existingData = worldData.getPlayerData(player.getUsername());
            if (existingData != null) {
                player.setX(existingData.getX());
                player.setY(existingData.getY());
                player.setDirection(existingData.convertDirectionIntToString(existingData.getDirection()));
                player.setRunning(existingData.isWantsToRun());
            }

        }
    }

    public void save() {
        try {
            if (worldData == null) {
                System.err.println("Cannot save - world data is null");
                return;
            }

            // Update last played time
            this.lastPlayed = System.currentTimeMillis();
            worldData.updateLastPlayed();

            // Save current player data if available
            if (player != null && player.getPlayerData() != null) {
                PlayerData currentData = player.getPlayerData();
                currentData.setPosition(player.getX(), player.getY());
                currentData.setDirection(player.getDirection());
                currentData.setMoving(player.isMoving());
                currentData.setWantsToRun(player.isRunning());
                currentData.setInventory(player.getInventory().getItemNames());

                worldData.savePlayerData(player.getUsername(), currentData);
                System.out.println("Saved player data in world for: " + player.getUsername());
            }

            // Ensure world directory exists
            FileHandle worldDir = Gdx.files.local("worlds/" + name);
            if (!worldDir.exists()) {
                worldDir.mkdirs();
            }

            // Save world data
            FileHandle worldFile = worldDir.child("world.json");
            Json json = new Json();
            json.setOutputType(JsonWriter.OutputType.json);
            String jsonData = json.prettyPrint(worldData);
            worldFile.writeString(jsonData, false);
            System.out.println("Saved world: " + name);
            System.out.println("World data: " + jsonData);

            // Run auto-save if available
            if (autoSaveManager != null) {
                autoSaveManager.performAutoSave();
            }

        } catch (Exception e) {
            System.err.println("Error saving world: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public String getName() {
        return name;
    }

    public WorldData getWorldData() {
        return worldData;
    }

    public void update(float delta, Vector2 playerPosition, float viewportWidth, float viewportHeight) {
        // Calculate how many chunks are needed to cover the viewport
        int chunksHorizontally = (int) Math.ceil(viewportWidth / (Chunk.CHUNK_SIZE * TILE_SIZE)) + 2; // +2 for buffer
        int chunksVertically = (int) Math.ceil(viewportHeight / (Chunk.CHUNK_SIZE * TILE_SIZE)) + 2;

        int playerChunkX = (int) Math.floor(playerPosition.x / (Chunk.CHUNK_SIZE * TILE_SIZE));
        int playerChunkY = (int) Math.floor(playerPosition.y / (Chunk.CHUNK_SIZE * TILE_SIZE));

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

                    System.out.println("Created new chunk at " + x + "," + y + " with biome " + biome.getName());
                }
            }
        }
        objectManager.update(chunks);
        unloadDistantChunks(playerChunkX, playerChunkY, chunksHorizontally, chunksVertically);

        checkPlayerInteractions(playerPosition);
        autoSaveManager.update(delta);
    }

    public void render(SpriteBatch batch, Rectangle viewBounds, Player player) {
        // First render terrain and tree bases
        for (Map.Entry<Vector2, Chunk> entry : chunks.entrySet()) {
            Vector2 pos = entry.getKey();
            if (isChunkVisible(pos, viewBounds)) {
                // Render terrain
                entry.getValue().render(batch, TILE_SIZE);

                // Render tree bases and other objects
                List<WorldObject> objects = objectManager.getObjectsForChunk(pos);
                for (WorldObject obj : objects) {
                    if (obj.getType() == WorldObject.ObjectType.TREE) {
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
                }
            }
        }

        // If player is behind trees, render them after tree tops
        if (!playerBehindTree) {
            player.render(batch);
        }
    }

    private boolean isChunkVisible(Vector2 chunkPos, Rectangle viewBounds) {
        float chunkWorldX = chunkPos.x * Chunk.CHUNK_SIZE * TILE_SIZE;
        float chunkWorldY = chunkPos.y * Chunk.CHUNK_SIZE * TILE_SIZE;
        Rectangle chunkBounds = new Rectangle(chunkWorldX, chunkWorldY,
            Chunk.CHUNK_SIZE * TILE_SIZE, Chunk.CHUNK_SIZE * TILE_SIZE);

        return viewBounds.overlaps(chunkBounds);
    }

    public Biome getBiomeAt(int worldX, int worldY) {
        // Reduced scale for larger, more spread out biomes
        double noiseScale = 0.004; // Reduced from 0.015 for larger biomes

        double nx = worldX * noiseScale;
        double ny = worldY * noiseScale;

        // Calculate base noise
        double noise = biomeNoiseGenerator.noise(nx, ny);

        // Add rotated secondary noise for more natural transitions
        double angle = Math.PI / 4;
        double nx2 = nx * Math.cos(angle) - ny * Math.sin(angle);
        double ny2 = nx * Math.sin(angle) + ny * Math.cos(angle);
        double noise2 = biomeNoiseGenerator.noise(nx2, ny2) * 0.5;

        // Combine noises
        double finalNoise = noise + noise2;

        // Smoother transitions but still distinct biomes
        finalNoise = Math.sin(finalNoise * Math.PI) * 0.5;

        // Adjusted thresholds for more even distribution
        if (finalNoise < -0.25) {
            return biomeManager.getBiome(BiomeType.SNOW);
        } else if (finalNoise < 0) {
            return biomeManager.getBiome(BiomeType.HAUNTED);
        } else if (finalNoise < 0.25) {
            return biomeManager.getBiome(BiomeType.PLAINS);
        } else {
            return biomeManager.getBiome(BiomeType.FOREST);
        }
    }

    // Optional debug method to help find biomes
    public void printBiomeInfo(float playerX, float playerY) {
        int worldX = (int) playerX / TILE_SIZE;
        int worldY = (int) playerY / TILE_SIZE;
        Biome currentBiome = getBiomeAt(worldX, worldY);
        System.out.println("Current position: (" + worldX + ", " + worldY + ") Biome: " + currentBiome.getName());
    }

    private Biome smoothTransition(BiomeType type1, BiomeType type2, double t) {
        // Smooth step function for nicer transitions
        t = t * t * (3 - 2 * t);
        return t < 0.5 ?
            biomeManager.getBiome(type1) :
            biomeManager.getBiome(type2);
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
                chunksToRemove.add(chunkPos);
            }
        }

        // Remove distant chunks
        for (Vector2 chunkPos : chunksToRemove) {
            chunks.remove(chunkPos);
            // Optional: Dispose of chunk resources if needed
        }
    }

}

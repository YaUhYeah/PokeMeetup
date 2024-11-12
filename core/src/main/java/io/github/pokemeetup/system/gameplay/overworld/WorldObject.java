package io.github.pokemeetup.system.gameplay.overworld;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.system.gameplay.overworld.biomes.Biome;
import io.github.pokemeetup.utils.TextureManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static com.badlogic.gdx.math.MathUtils.random;

public class WorldObject {
    private static final float POKEBALL_DESPAWN_TIME = 300f;
    public ObjectType type;  // Remove static
    private float pixelX;  // Remove static
    private float pixelY;  // Remove static
    private TextureRegion texture;
    private String id;
    private boolean isDirty;
    private boolean isCollidable;
    private float spawnTime;
    private float renderOrder;
    private int tileX, tileY;
    private WorldObject attachedTo; // New field to track what object (e.g. tree) a vine is attached to


    public WorldObject(int tileX, int tileY, TextureRegion texture, ObjectType type) {
        this.id = UUID.randomUUID().toString();
        this.tileX = tileX;
        this.tileY = tileY;
        this.pixelX = tileX * World.TILE_SIZE;
        this.pixelY = tileY * World.TILE_SIZE;
        this.texture = texture;
        this.attachedTo = null; // Initialize the new field
        this.type = type;
        this.isCollidable = type.isCollidable;
        this.spawnTime = type.isPermanent ? 0 : System.currentTimeMillis() / 1000f;
        this.renderOrder = type == ObjectType.TREE ? pixelY + World.TILE_SIZE : pixelY;
        this.isDirty = true;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Map<String, Object> getSerializableData() {
        Map<String, Object> data = new HashMap<>();
        data.put("tileX", tileX);
        data.put("tileY", tileY);
        data.put("type", type.name());
        data.put("spawnTime", spawnTime);
        data.put("isCollidable", isCollidable);
        return data;
    }

    public void updateFromNetwork(NetworkProtocol.WorldObjectUpdate update) {
        this.tileX = (int) update.data.get("tileX");
        this.tileY = (int) update.data.get("tileY");
        this.pixelX = tileX * World.TILE_SIZE;
        this.pixelY = tileY * World.TILE_SIZE;
        this.spawnTime = (float) update.data.get("spawnTime");
        this.isCollidable = (boolean) update.data.get("isCollidable");
        isDirty = false;
    }

    public ObjectType getType() {
        return type;
    }

    public void setType(ObjectType type) {
        this.type = type;
    }

    public boolean isExpired() {
        if (type.isPermanent) return false;
        float currentTime = System.currentTimeMillis() / 1000f;
        return currentTime - spawnTime > POKEBALL_DESPAWN_TIME;
    }

    public boolean isUnderTree(float playerX, float playerY, float playerWidth, float playerHeight) {
        if (type != ObjectType.TREE && type != ObjectType.SNOW_TREE && type != ObjectType.HAUNTED_TREE) {
            return false;
        }

        // Define the tree's top area (the part above the 2x2 base)
        float treeBaseX = pixelX - World.TILE_SIZE; // Center of tree (2 tiles wide)
        float topY = pixelY + (World.TILE_SIZE * 2); // Start of top section (above base)

        Rectangle treeTop = new Rectangle(
            treeBaseX,
            topY,
            World.TILE_SIZE * 2, // 2 tiles wide
            World.TILE_SIZE      // 1 tile high (top part)
        );

        // Create player bounds
        Rectangle playerBounds = new Rectangle(playerX, playerY, playerWidth, playerHeight);

        // Check if player overlaps with top part
        return treeTop.overlaps(playerBounds);
    }

    public Rectangle getBoundingBox() {
        if (type == ObjectType.TREE || type == ObjectType.SNOW_TREE || type == ObjectType.HAUNTED_TREE || type == ObjectType.RAIN_TREE) {
            // Tree collision box: 2x2 tiles at the base only
            float treeBaseX = pixelX - World.TILE_SIZE; // Center the 2-tile width base
            float treeBaseY = pixelY; // Bottom of tree

            return new Rectangle(
                treeBaseX,
                treeBaseY,
                World.TILE_SIZE * 2, // 2 tiles wide
                World.TILE_SIZE * 2  // 2 tiles high (base only)
            );
        } else {
            // Regular object collision (like pokeballs)
            return new Rectangle(
                pixelX,
                pixelY,
                type.widthInTiles * World.TILE_SIZE,
                type.heightInTiles * World.TILE_SIZE
            );
        }
    }

    public float getPixelX() {
        return pixelX;
    }

    public float getPixelY() {
        return pixelY;
    }

    public TextureRegion getTexture() {
        return texture;
    }

    public void setTexture(TextureRegion texture) {
        this.texture = texture;
    }

    public WorldObject getAttachedTo() {
        return attachedTo;
    }

    public void setAttachedTo(WorldObject object) {
        this.attachedTo = object;
    }

    public int getTileX() {
        return tileX;
    }

    public void setTileX(int tileX) {
        this.tileX = tileX;
    }

    public int getTileY() {
        return tileY;
    }

    public void setTileY(int tileY) {
        this.tileY = tileY;
    }

    // Add these methods to WorldObject class
    public Rectangle getCollisionBox() {
        if (!type.isCollidable) {
            return null; // No collision for non-collidable objects
        }

        if (type == ObjectType.TREE || type == ObjectType.SNOW_TREE ||
            type == ObjectType.HAUNTED_TREE || type ==ObjectType.RAIN_TREE) {
            // Trees have 2x2 collision at base only
            return new Rectangle(
                pixelX - World.TILE_SIZE, // Center the 2-tile base
                pixelY,
                World.TILE_SIZE * 2,
                World.TILE_SIZE * 2
            );
        }

        // Standard collision box
        return new Rectangle(
            pixelX,
            pixelY,
            type.widthInTiles * World.TILE_SIZE,
            type.heightInTiles * World.TILE_SIZE
        );
    }

    public void render(SpriteBatch batch) {
        if (type.renderType == ObjectType.RenderLayer.LAYERED) {
            // Objects like trees that need layered rendering
            renderLayered(batch);
        } else if (type == ObjectType.VINES && attachedTo != null) {
            // Special rendering for vines attached to trees
            renderVine(batch);
        } else {
            // Standard rendering
            float width = type.widthInTiles * World.TILE_SIZE;
            float height = type.heightInTiles * World.TILE_SIZE;
            batch.draw(texture, pixelX, pixelY, width, height);
        }
    }

    private void renderVine(SpriteBatch batch) {
        if (attachedTo != null && (attachedTo.type == ObjectType.TREE ||
            attachedTo.type == ObjectType.SNOW_TREE ||
            attachedTo.type == ObjectType.HAUNTED_TREE ||
            attachedTo.type == ObjectType.RAIN_TREE)) {

            // Position vine at the top of the tree's canopy
            float vineX = attachedTo.pixelX - World.TILE_SIZE; // Center on tree
            float vineY = attachedTo.pixelY + (World.TILE_SIZE * 2); // Place at tree top

            batch.draw(texture,
                vineX + World.TILE_SIZE / 2, // Center on tree
                vineY,
                World.TILE_SIZE,
                World.TILE_SIZE * 2);
        }
    }

    private void renderLayered(SpriteBatch batch) {
        switch (type) {
            case TREE:
            case SNOW_TREE:
            case HAUNTED_TREE:
            case RAIN_TREE:
                float baseX = pixelX - World.TILE_SIZE;
                batch.draw(texture, baseX, pixelY,
                    World.TILE_SIZE * 2,
                    World.TILE_SIZE * 3);
                break;
        }
    }

    // Add this to your WorldObject class
    public enum ObjectType {
        // Static objects
        TREE(true, true, 2, 3, RenderLayer.LAYERED),      // 2 tiles wide, 3 tiles tall, needs layered rendering
        SNOW_TREE(true, true, 2, 3, RenderLayer.LAYERED),
        HAUNTED_TREE(true, true, 2, 3, RenderLayer.LAYERED),

        // Environmental objects
        CACTUS(true, true, 1, 2, RenderLayer.FULL),
        DEAD_TREE(true, true, 1, 2, RenderLayer.FULL),
        SMALL_HAUNTED_TREE(true, true, 1, 2, RenderLayer.FULL),
        BUSH(true, true, 3, 1, RenderLayer.FULL),
        VINES(true, false, 1, 2, RenderLayer.FULL),
        SNOW_BALL(true, true, 1, 1, RenderLayer.FULL),
        RAIN_TREE(true, true, 2, 3, RenderLayer.LAYERED),
        SUNFLOWER(true, false, 1, 2, RenderLayer.FULL),   // No collision

        // Collectible objects
        POKEBALL(false, true, 1, 1, RenderLayer.FULL);   // No collision, temporary

        public final boolean isPermanent;    // Permanent or temporary object
        public final boolean isCollidable;   // Has collision or not
        public final int widthInTiles;       // Width in tiles
        public final int heightInTiles;      // Height in tiles
        public final RenderLayer renderType; // How to render this object

        ObjectType(boolean isPermanent, boolean isCollidable,
                   int widthInTiles, int heightInTiles, RenderLayer renderType) {
            this.isPermanent = isPermanent;
            this.isCollidable = isCollidable;
            this.widthInTiles = widthInTiles;
            this.heightInTiles = heightInTiles;
            this.renderType = renderType;
        }

        public enum RenderLayer {
            FULL,     // Render entire object at once
            LAYERED   // Render in layers (e.g. tree base then top)
        }
    }

    public static class WorldObjectManager {
        private static final float POKEBALL_SPAWN_CHANCE = 0.3f; // Reduced for better balance
        private static final int MAX_POKEBALLS_PER_CHUNK = 2;
        private static final int MIN_OBJECT_SPACING = 2; // Base spacing for small objects
        private static final int MIN_TREE_SPACING = 4;   // Larger spacing for trees
        private final GameClient gameClient;
        private final Map<Vector2, List<WorldObject>> objectsByChunk = new ConcurrentHashMap<>();
        private final TextureAtlas atlas;
        private final Map<WorldObject.ObjectType, TextureRegion> objectTextures;
        private final long worldSeed;
        private final ConcurrentLinkedQueue<WorldObjectOperation> operationQueue = new ConcurrentLinkedQueue<>();

        public WorldObjectManager(long seed, GameClient gameClient) {
            this.worldSeed = seed;
            this.gameClient = gameClient;
            this.atlas = TextureManager.tiles;
            this.objectTextures = new HashMap<>();
            objectTextures.put(ObjectType.TREE, atlas.findRegion("tree"));
            objectTextures.put(ObjectType.SNOW_TREE, atlas.findRegion("snow_tree"));
            objectTextures.put(ObjectType.HAUNTED_TREE, atlas.findRegion("haunted_tree"));
            objectTextures.put(ObjectType.POKEBALL, atlas.findRegion("pokeball"));
            objectTextures.put(ObjectType.CACTUS, atlas.findRegion("desert_cactus"));
            objectTextures.put(ObjectType.BUSH, atlas.findRegion("bush"));
            objectTextures.put(ObjectType.SUNFLOWER, atlas.findRegion("sunflower"));
            objectTextures.put(ObjectType.VINES, atlas.findRegion("vines"));
            objectTextures.put(ObjectType.DEAD_TREE, atlas.findRegion("dead_tree"));
            objectTextures.put(ObjectType.SMALL_HAUNTED_TREE, atlas.findRegion("small_haunted_tree"));
            objectTextures.put(ObjectType.SNOW_BALL, atlas.findRegion("snowball"));
            objectTextures.put(ObjectType.RAIN_TREE, atlas.findRegion("rain_tree"));
        }

        private void tryPlaceVine(Chunk chunk, Vector2 chunkPos, List<WorldObject> objects, Random random) {
            // Find all trees in the current chunk
            List<WorldObject> trees = objects.stream()
                .filter(obj -> obj.getType() == ObjectType.TREE ||
                    obj.getType() == ObjectType.SNOW_TREE ||
                    obj.getType() == ObjectType.HAUNTED_TREE ||
                    obj.getType() == ObjectType.RAIN_TREE)
                .collect(Collectors.toList());

            // Chance for each tree to spawn a vine
            float vineSpawnChance = 0.3f; // 30% chance per tree

            for (WorldObject tree : trees) {
                if (random.nextFloat() < vineSpawnChance) {
                    // Create vine at tree's position
                    TextureRegion vineTexture = objectTextures.get(ObjectType.VINES);
                    if (vineTexture != null) {
                        WorldObject vine = new WorldObject(
                            tree.getTileX(),
                            tree.getTileY(),
                            vineTexture,
                            ObjectType.VINES
                        );
                        vine.setAttachedTo(tree);
                        objects.add(vine);
                    }
                }
            }
        }

        public void generateObjectsForChunk(Vector2 chunkPos, Chunk chunk, Biome biome) {
            List<WorldObject> objects = objectsByChunk.computeIfAbsent(chunkPos,
                k -> new CopyOnWriteArrayList<>());
            Random random = new Random((long) (worldSeed + chunkPos.x * 31 + chunkPos.y * 17));

            // First generate all non-vine objects
            List<String> spawnableObjects = biome.getSpawnableObjects().stream()
                .filter(obj -> obj != ObjectType.VINES)
                .map(Enum::name)
                .collect(Collectors.toList());

            // Handle object clusters
            generateObjectClusters(chunk, chunkPos, objects, biome, random);

            // Handle individual objects
            for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
                for (int y = 0; y < Chunk.CHUNK_SIZE; y++) {
                    tryPlaceObject(chunk, x, y, objects, biome, chunkPos, random, spawnableObjects);
                }
            }

            // After all other objects are placed, try to place vines on trees
            if (biome.getSpawnableObjects().contains(ObjectType.VINES)) {
                tryPlaceVine(chunk, chunkPos, objects, random);
            }
        }


        public void renderTreeBase(SpriteBatch batch, WorldObject tree) {
            // Position to center the tree on 2 tiles width
            float renderX = tree.getPixelX() - World.TILE_SIZE; // Shift left by 1 full tile to center
            float renderY = tree.getPixelY(); // Base Y position of the tree

            TextureRegion treeRegion = tree.getTexture();
            int totalWidth = treeRegion.getRegionWidth();
            int totalHeight = treeRegion.getRegionHeight();

            // Base section: lower 2/3 of the texture
            int baseHeight = (int) (totalHeight * 2 / 3f);
            int baseY = totalHeight - baseHeight;

            TextureRegion baseRegion = new TextureRegion(treeRegion, 0, baseY, totalWidth, baseHeight);

            // Render the base as 2 tiles wide and 2 tiles tall
            batch.draw(baseRegion, renderX, renderY, World.TILE_SIZE * 2, World.TILE_SIZE * 2);
        }


        public void renderObject(SpriteBatch batch, WorldObject object) {
            TextureRegion objectTexture = object.getTexture();
            float renderX = object.getPixelX();
            float renderY = object.getPixelY();

            switch (object.getType()) {
                case SUNFLOWER:
                case VINES:
                case DEAD_TREE:
                case SMALL_HAUNTED_TREE:
                    // Sunflower is 32x64 in your atlas (1x2 tiles)
                    batch.draw(objectTexture,
                        renderX, renderY,            // Position
                        World.TILE_SIZE,             // Width (1 tile)
                        World.TILE_SIZE * 2);        // Height (2 tiles)
                    break;
                case BUSH:
                    batch.draw(objectTexture,
                        renderX, renderY,            // Position
                        World.TILE_SIZE * 3,             // Width (3 tile)
                        World.TILE_SIZE * 2);        // Height (1 tile)
                    break;
                case CACTUS:
                    // Desert cactus is 16x32 in your atlas (needs scaling up to 1x2 tiles)
                    batch.draw(objectTexture,
                        renderX, renderY,            // Position
                        World.TILE_SIZE,             // Width (1 tile)
                        World.TILE_SIZE * 2);        // Height (2 tiles)
                    break;

                default:
                    // Default 1x1 rendering for other objects
                    batch.draw(objectTexture,
                        renderX, renderY,
                        World.TILE_SIZE,
                        World.TILE_SIZE);
            }
        }

        public void renderTreeTop(SpriteBatch batch, WorldObject tree) {
            // Position above the base, centered on the same X as the base
            float renderX = tree.getPixelX() - World.TILE_SIZE; // Centering to match the base
            float renderY = tree.getPixelY() + (World.TILE_SIZE * 2); // 2 tiles above base

            TextureRegion treeRegion = tree.getTexture();
            int totalWidth = treeRegion.getRegionWidth();
            int totalHeight = treeRegion.getRegionHeight();

            // Top section: upper 1/3 of the texture
            int topHeight = totalHeight - (int) (totalHeight * 2 / 3f);
            int topY = 0;

            TextureRegion topRegion = new TextureRegion(treeRegion, 0, topY, totalWidth, topHeight);

            // Render the top as 2 tiles wide and 1 tile tall
            batch.draw(topRegion, renderX, renderY, World.TILE_SIZE * 2, World.TILE_SIZE);
        }


        public List<WorldObject> getObjectsNearPosition(float x, float y) {
            List<WorldObject> nearbyObjects = new ArrayList<>();
            int searchRadius = 2; // Search in nearby chunks

            int centerChunkX = (int) Math.floor(x / (Chunk.CHUNK_SIZE * World.TILE_SIZE));
            int centerChunkY = (int) Math.floor(y / (Chunk.CHUNK_SIZE * World.TILE_SIZE));

            for (int dx = -searchRadius; dx <= searchRadius; dx++) {
                for (int dy = -searchRadius; dy <= searchRadius; dy++) {
                    Vector2 chunkPos = new Vector2(centerChunkX + dx, centerChunkY + dy);
                    List<WorldObject> chunkObjectList = objectsByChunk.get(chunkPos);

                    if (chunkObjectList != null) {
                        for (WorldObject obj : chunkObjectList) {
                            // Check if object is within reasonable distance
                            float distX = Math.abs(obj.getPixelX() - x);
                            float distY = Math.abs(obj.getPixelY() - y);

                            if (distX <= World.TILE_SIZE * 3 && distY <= World.TILE_SIZE * 3) {
                                nearbyObjects.add(obj);
                            }
                        }
                    }
                }
            }

            return nearbyObjects;
        }

        public List<WorldObject> getObjectsForChunk(Vector2 chunkPos) {
            List<WorldObject> objects = objectsByChunk.get(chunkPos);
            return objects != null ? objects : Collections.emptyList();
        }

        public void addObjectToChunk(WorldObject object) {
            int actualChunkX = (int) Math.floor(object.getPixelX() / (Chunk.CHUNK_SIZE * World.TILE_SIZE));
            int actualChunkY = (int) Math.floor(object.getPixelY() / (Chunk.CHUNK_SIZE * World.TILE_SIZE));
            Vector2 actualChunkPos = new Vector2(actualChunkX, actualChunkY);

            List<WorldObject> objects = objectsByChunk.computeIfAbsent(actualChunkPos, k -> new CopyOnWriteArrayList<>());
            objects.add(object);

        }

        public void removeObjectFromChunk(Vector2 chunkPos, String objectId) {
            operationQueue.add(new RemoveOperation(chunkPos, objectId));

            // Send network update if in multiplayer
            if (gameClient != null && !gameClient.isSinglePlayer()) {
                // Implement sendObjectRemove if not already done
                sendObjectRemove(objectId);
            }
        }

        public void update(Map<Vector2, Chunk> loadedChunks) {
            // Process all pending operations first
            WorldObjectOperation operation;
            while ((operation = operationQueue.poll()) != null) {
                switch (operation.type) {
                    case ADD:
                        AddOperation addOp = (AddOperation) operation;
                        List<WorldObject> addList = objectsByChunk.computeIfAbsent(addOp.chunkPos, k -> new CopyOnWriteArrayList<>());
                        addList.add(addOp.object);
                        break;

                    case REMOVE:
                        RemoveOperation removeOp = (RemoveOperation) operation;
                        List<WorldObject> removeList = objectsByChunk.get(removeOp.chunkPos);
                        if (removeList != null) {
                            removeList.removeIf(obj -> obj.getId().equals(removeOp.objectId));
                        }
                        break;

                    case UPDATE:
                        UpdateOperation updateOp = (UpdateOperation) operation;
                        List<WorldObject> updateList = objectsByChunk.get(updateOp.chunkPos);
                        if (updateList != null) {
                            for (WorldObject obj : updateList) {
                                if (obj.getId().equals(updateOp.update.objectId)) {
                                    obj.updateFromNetwork(updateOp.update);
                                    break;
                                }
                            }
                        }
                        break;
                }
            }


            // Update each loaded chunk's objects safely
            for (Map.Entry<Vector2, Chunk> entry : loadedChunks.entrySet()) {
                Vector2 chunkPos = entry.getKey();
                List<WorldObject> objects = objectsByChunk.computeIfAbsent(chunkPos, k -> new CopyOnWriteArrayList<>());

                // Remove expired objects safely with CopyOnWriteArrayList
                objects.removeIf(WorldObject::isExpired);

                // Check if more objects should be added based on conditions
                long pokeballCount = objects.stream()
                    .filter(obj -> obj.getType() == ObjectType.POKEBALL)
                    .count();

                if (pokeballCount < MAX_POKEBALLS_PER_CHUNK && MathUtils.random() < POKEBALL_SPAWN_CHANCE) {
                    spawnPokeball(chunkPos, objects, entry.getValue());
                }
            }

            // Remove chunks that are no longer loaded
            List<Vector2> chunksToRemove = new ArrayList<>();
            for (Vector2 chunkPos : objectsByChunk.keySet()) {
                if (!loadedChunks.containsKey(chunkPos)) {
                    chunksToRemove.add(chunkPos);
                }
            }

            // Remove each chunk safely
            for (Vector2 chunkPos : chunksToRemove) {
                objectsByChunk.remove(chunkPos);
            }
        }


        public boolean isPlayerUnderTree(float playerX, float playerY, float playerWidth, float playerHeight) {
            // Need to check all loaded chunks near the player
            int chunkX = (int) Math.floor(playerX / (Chunk.CHUNK_SIZE * World.TILE_SIZE));
            int chunkY = (int) Math.floor(playerY / (Chunk.CHUNK_SIZE * World.TILE_SIZE));

            // Check current chunk and adjacent chunks
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    Vector2 chunkPos = new Vector2(chunkX + dx, chunkY + dy);
                    List<WorldObject> objects = objectsByChunk.get(chunkPos);
                    if (objects != null) {
                        for (WorldObject obj : objects) {
                            if (obj.getType() == ObjectType.TREE ||
                                obj.getType() == ObjectType.HAUNTED_TREE ||
                                obj.getType() == ObjectType.SNOW_TREE) {
                                if (obj.isUnderTree(playerX, playerY, playerWidth, playerHeight)) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
            return false;
        }

        private boolean shouldSpawnPokeball(List<WorldObject> chunkObjects) {
            long pokeballCount = chunkObjects.stream()
                .filter(obj -> obj.getType() == ObjectType.POKEBALL)
                .count();
            return pokeballCount < MAX_POKEBALLS_PER_CHUNK &&
                new Random().nextInt(101) < POKEBALL_SPAWN_CHANCE;
        }

        private void spawnPokeball(Vector2 chunkPos, List<WorldObject> objects, Chunk chunk) {
            for (int attempts = 0; attempts < 10; attempts++) {
                int localX = random.nextInt(Chunk.CHUNK_SIZE);
                int localY = random.nextInt(Chunk.CHUNK_SIZE);

                // Only spawn on grass or sand
                int tileType = chunk.getTileType(localX, localY);
                if (tileType == Chunk.GRASS || tileType == Chunk.SAND) {

                    int worldTileX = (int) (chunkPos.x * Chunk.CHUNK_SIZE) + localX;
                    int worldTileY = (int) (chunkPos.y * Chunk.CHUNK_SIZE) + localY;

                    boolean locationClear = true;
                    for (WorldObject obj : objects) {
                        if (Math.abs(obj.getTileX() - worldTileX) < 2 &&
                            Math.abs(obj.getTileY() - worldTileY) < 2) {
                            locationClear = false;
                            break;
                        }
                    }
                    if (shouldSpawnPokeball(objects)) {
                        if (locationClear) {
                            TextureRegion pokeballTexture = objectTextures.get(WorldObject.ObjectType.POKEBALL);
                            if (pokeballTexture != null) {
                                WorldObject pokeball = new WorldObject(worldTileX, worldTileY,
                                    pokeballTexture, WorldObject.ObjectType.POKEBALL);
                                objects.add(pokeball);

                                // Send network update if in multiplayer
                                if (gameClient != null && !gameClient.isSinglePlayer()) {
                                    sendObjectSpawn(pokeball);
                                }

                                // GameLogger.info("Spawned pokeball at: " + worldTileX + "," + worldTileY);
                            }
                        }
                    }
                }
            }
        }

        private void sendObjectSpawn(WorldObject object) {
            if (gameClient == null || gameClient.isSinglePlayer()) return;

            NetworkProtocol.WorldObjectUpdate update = new NetworkProtocol.WorldObjectUpdate();
            update.objectId = object.getId();
            update.type = NetworkProtocol.NetworkObjectUpdateType.ADD;
            update.data = object.getSerializableData();

            gameClient.sendWorldObjectUpdate(update);
        }

        private void sendObjectRemove(String objectId) {
            if (gameClient == null || gameClient.isSinglePlayer()) return;

            NetworkProtocol.WorldObjectUpdate update = new NetworkProtocol.WorldObjectUpdate();
            update.objectId = objectId;  // Use objectId directly
            update.type = NetworkProtocol.NetworkObjectUpdateType.REMOVE;

            gameClient.sendWorldObjectUpdate(update);
        }


        public WorldObject createObject(WorldObject.ObjectType type, float x, float y) {
            TextureRegion texture = objectTextures.get(type);
            if (texture == null) {
                throw new IllegalStateException("No texture found for object type: " + type);
            }

            int tileX = (int) (x / World.TILE_SIZE);
            int tileY = (int) (y / World.TILE_SIZE);

            return new WorldObject(tileX, tileY, texture, type);
        }

        private void tryPlaceObject(Chunk chunk, int x, int y, List<WorldObject> objects,
                                    Biome biome, Vector2 chunkPos, Random random, List<String> spawnableObjects) {
            if (!spawnableObjects.isEmpty() && canPlaceObject(chunk, x, y, objects, biome)) {
                String objectType = spawnableObjects.get(random.nextInt(spawnableObjects.size()));
                WorldObject.ObjectType type = WorldObject.ObjectType.valueOf(objectType);

                // Check spawn chance before placing
                if (biome.shouldSpawnObject(type, random)) {
                    placeObject(chunk, x, y, objects, biome, chunkPos, random, objectType);
                }
            }
        }


        private void generateObjectClusters(Chunk chunk, Vector2 chunkPos, List<WorldObject> objects,
                                            Biome biome, Random random) {
            int clusterAttempts = Math.max(1, (int) (Chunk.CHUNK_SIZE * 5f));

            for (int i = 0; i < clusterAttempts; i++) {
                if (random.nextFloat() < 0.3f) { // Cluster formation chance
                    int centerX = random.nextInt(Chunk.CHUNK_SIZE);
                    int centerY = random.nextInt(Chunk.CHUNK_SIZE);
                    int clusterSize = random.nextInt(3) + 2; // 2-4 objects per cluster

                    // Select a random object type for the cluster
                    List<WorldObject.ObjectType> spawnableObjects = biome.getSpawnableObjects();
                    if (!spawnableObjects.isEmpty()) {
                        WorldObject.ObjectType selectedType = spawnableObjects.get(
                            random.nextInt(spawnableObjects.size()));

                        // Check spawn chance for the cluster
                        if (biome.shouldSpawnObject(selectedType, random)) {
                            generateCluster(chunk, chunkPos, objects, biome, random,
                                centerX, centerY, clusterSize);
                        }
                    }
                }
            }
        }

        private void placeObject(Chunk chunk, int x, int y, List<WorldObject> objects,
                                 ObjectType objectType, Vector2 chunkPos, boolean[][] occupiedTiles) {
            // Convert to world coordinates
            int worldTileX = (int) (chunkPos.x * Chunk.CHUNK_SIZE + x);
            int worldTileY = (int) (chunkPos.y * Chunk.CHUNK_SIZE + y);

            TextureRegion texture = objectTextures.get(objectType);
            if (texture != null) {
                WorldObject object = new WorldObject(worldTileX, worldTileY, texture, objectType);
                objects.add(object);

                // Mark tiles as occupied based on object size
                int occupyWidth = objectType.widthInTiles;
                int occupyHeight = objectType.heightInTiles;

                for (int dx = 0; dx < occupyWidth && (x + dx) < Chunk.CHUNK_SIZE; dx++) {
                    for (int dy = 0; dy < occupyHeight && (y + dy) < Chunk.CHUNK_SIZE; dy++) {
                        if (x + dx >= 0 && x + dx < Chunk.CHUNK_SIZE &&
                            y + dy >= 0 && y + dy < Chunk.CHUNK_SIZE) {
                            occupiedTiles[x + dx][y + dy] = true;
                        }
                    }
                }
            }
        }

        private ObjectType selectLargeObjectType(Biome biome, Random random) {
            List<ObjectType> validTypes = biome.getSpawnableObjects().stream()
                .filter(type -> type == ObjectType.TREE ||
                    type == ObjectType.SNOW_TREE ||
                    type == ObjectType.HAUNTED_TREE)
                .collect(Collectors.toList());

            return validTypes.isEmpty() ? null : validTypes.get(random.nextInt(validTypes.size()));
        }

        private ObjectType selectSmallObjectType(Biome biome, Random random) {
            List<ObjectType> validTypes = biome.getSpawnableObjects().stream()
                .filter(type -> type != ObjectType.TREE &&
                    type != ObjectType.SNOW_TREE &&
                    type != ObjectType.HAUNTED_TREE)
                .collect(Collectors.toList());

            return validTypes.isEmpty() ? null : validTypes.get(random.nextInt(validTypes.size()));
        }

        private boolean canPlaceSmallObject(Chunk chunk, int x, int y, boolean[][] occupiedTiles, Biome biome) {
            // Check immediate surrounding tiles
            int spacing = MIN_OBJECT_SPACING;

            // Check if out of bounds
            if (x < spacing || y < spacing ||
                x >= Chunk.CHUNK_SIZE - spacing ||
                y >= Chunk.CHUNK_SIZE - spacing) {
                return false;
            }

            // Check surrounding area
            for (int dx = -spacing; dx <= spacing; dx++) {
                for (int dy = -spacing; dy <= spacing; dy++) {
                    int checkX = x + dx;
                    int checkY = y + dy;

                    if (checkX < 0 || checkX >= Chunk.CHUNK_SIZE ||
                        checkY < 0 || checkY >= Chunk.CHUNK_SIZE) {
                        continue;
                    }

                    if (occupiedTiles[checkX][checkY]) {
                        return false;
                    }
                }
            }

            // Check tile type
            int tileType = chunk.getTileType(x, y);
            return biome.getAllowedTileTypes().contains(tileType);
        }

        private void placeSmallObjects(Chunk chunk, Vector2 chunkPos, List<WorldObject> objects,
                                       Biome biome, Random random, boolean[][] occupiedTiles) {
            for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
                for (int y = 0; y < Chunk.CHUNK_SIZE; y++) {
                    if (!occupiedTiles[x][y] && random.nextFloat() < 0.1f) { // 10% chance per tile
                        ObjectType objectType = selectSmallObjectType(biome, random);
                        if (objectType != null && canPlaceSmallObject(chunk, x, y, occupiedTiles, biome)) {
                            placeObject(chunk, x, y, objects, objectType, chunkPos, occupiedTiles);
                        }
                    }
                }
            }
        }

        private boolean canPlaceLargeObject(Chunk chunk, int x, int y, boolean[][] occupiedTiles, Biome biome) {
            // Check a 3x3 area for trees (including spacing)
            int spacing = MIN_TREE_SPACING;

            // Check if out of bounds
            if (x < spacing || y < spacing ||
                x >= Chunk.CHUNK_SIZE - spacing ||
                y >= Chunk.CHUNK_SIZE - spacing) {
                return false;
            }

            // Check surrounding area
            for (int dx = -spacing; dx <= spacing; dx++) {
                for (int dy = -spacing; dy <= spacing; dy++) {
                    int checkX = x + dx;
                    int checkY = y + dy;

                    // Skip if outside chunk bounds
                    if (checkX < 0 || checkX >= Chunk.CHUNK_SIZE ||
                        checkY < 0 || checkY >= Chunk.CHUNK_SIZE) {
                        continue;
                    }

                    // Check if tile is already occupied
                    if (occupiedTiles[checkX][checkY]) {
                        return false;
                    }

                    // Check if tile type is valid for object placement
                    int tileType = chunk.getTileType(checkX, checkY);
                    if (!biome.getAllowedTileTypes().contains(tileType)) {
                        return false;
                    }
                }
            }

            return true;
        }

        private void generateCluster(Chunk chunk, Vector2 chunkPos, List<WorldObject> objects,
                                     Biome biome, Random random, int centerX, int centerY, int clusterSize) {
            // Define cluster spread radius
            int spreadRadius = 3;

            // Keep track of placed object positions to avoid overlaps
            Set<Point> occupiedPositions = new HashSet<>();

            // Try to place objects around the center point
            for (int i = 0; i < clusterSize; i++) {
                // Try several times to find a valid position
                for (int attempts = 0; attempts < 10; attempts++) {
                    // Calculate random offset from center
                    int offsetX = random.nextInt(spreadRadius * 2) - spreadRadius;
                    int offsetY = random.nextInt(spreadRadius * 2) - spreadRadius;

                    int newX = centerX + offsetX;
                    int newY = centerY + offsetY;

                    // Check if position is within chunk bounds
                    if (newX >= 0 && newX < Chunk.CHUNK_SIZE &&
                        newY >= 0 && newY < Chunk.CHUNK_SIZE) {

                        Point position = new Point(newX, newY);

                        // Check if position is not occupied and placement is valid
                        if (!occupiedPositions.contains(position) &&
                            canPlaceObjectAt(chunk, newX, newY, biome)) {

                            // Convert to world coordinates
                            int worldTileX = (int) (chunkPos.x * Chunk.CHUNK_SIZE + newX);
                            int worldTileY = (int) (chunkPos.y * Chunk.CHUNK_SIZE + newY);

                            // Create and add the object
                            WorldObject.ObjectType objType = biome.getSpawnableObjects().get(
                                random.nextInt(biome.getSpawnableObjects().size())
                            );

                            TextureRegion texture = TextureManager.getTextureForObjectType(objType);
                            if (texture != null) {
                                WorldObject object = new WorldObject(worldTileX, worldTileY, texture, objType);
                                objects.add(object);
                                occupiedPositions.add(position);
                                break; // Successfully placed object, move to next one
                            }
                        }
                    }
                }
            }
        }// Helper class for position tracking

        private boolean canPlaceObjectAt(Chunk chunk, int localX, int localY, Biome biome) {
            // Get tile type at position
            int tileType = chunk.getTileType(localX, localY);

            // Check if tile type is allowed for object placement
            if (!biome.getAllowedTileTypes().contains(tileType)) {
                return false;
            }

            // Check surrounding tiles for minimum spacing
            int spacing = 1;
            for (int dx = -spacing; dx <= spacing; dx++) {
                for (int dy = -spacing; dy <= spacing; dy++) {
                    int checkX = localX + dx;
                    int checkY = localY + dy;

                    // Skip if outside chunk bounds
                    if (checkX < 0 || checkX >= Chunk.CHUNK_SIZE ||
                        checkY < 0 || checkY >= Chunk.CHUNK_SIZE) {
                        continue;
                    }

                    // Check tile type of surrounding tiles
                    int surroundingTile = chunk.getTileType(checkX, checkY);
                    if (!biome.getAllowedTileTypes().contains(surroundingTile)) {
                        return false;
                    }
                }
            }

            return true;
        }

        private void placeObject(Chunk chunk, int x, int y, List<WorldObject> objects,
                                 Biome biome, Vector2 chunkPos, Random random, String objectType) {
            int worldTileX = (int) ((chunkPos.x * Chunk.CHUNK_SIZE) + x);
            int worldTileY = (int) ((chunkPos.y * Chunk.CHUNK_SIZE) + y);

            WorldObject.ObjectType type = WorldObject.ObjectType.valueOf(objectType);
            TextureRegion texture = objectTextures.get(type);

            if (texture != null) {
                float scale = 0.8f + random.nextFloat() * 0.4f; // Random scale 0.8-1.2
                WorldObject object = new WorldObject(worldTileX, worldTileY, texture, type);
                // Add variation in rotation for non-tree objects
                if (type != ObjectType.TREE && type != ObjectType.SNOW_TREE
                    && type != ObjectType.HAUNTED_TREE) {
                    // Add rotation variation logic here if needed
                }
                objects.add(object);
            }
        }

        private boolean canPlaceObject(Chunk chunk, int x, int y, List<WorldObject> objects, Biome biome) {
            // Check if tile type is allowed for object placement
            int tileType = chunk.getTileType(x, y);
            if (!biome.getAllowedTileTypes().contains(tileType)) {
                return false;
            }

            // Check spacing between objects
            int minSpacing = 2; // Default spacing
            if (objects != null) {
                int worldX = (int) (chunk.getChunkX() * Chunk.CHUNK_SIZE + x);
                int worldY = (int) (chunk.getChunkY() * Chunk.CHUNK_SIZE + y);

                for (WorldObject obj : objects) {
                    int dx = Math.abs(obj.getTileX() - worldX);
                    int dy = Math.abs(obj.getTileY() - worldY);

                    // Adjust spacing based on object type
                    if (obj.getType() == ObjectType.TREE ||
                        obj.getType() == ObjectType.SNOW_TREE ||
                        obj.getType() == ObjectType.HAUNTED_TREE) {
                        minSpacing = MIN_TREE_SPACING;
                    }

                    if (dx < minSpacing && dy < minSpacing) {
                        return false;
                    }
                }
            }

            return true;
        }

        private void tryPlaceTree(Chunk chunk, int x, int y, List<WorldObject> objects,
                                  Biome biome, Vector2 chunkPos, WorldObject.ObjectType treeType) {
            if (canPlaceTree(chunk, x, y, objects, biome)) {
                int worldTileX = (int) ((chunkPos.x * Chunk.CHUNK_SIZE) + x);
                int worldTileY = (int) ((chunkPos.y * Chunk.CHUNK_SIZE) + y);

                TextureRegion texture = objectTextures.get(treeType);
                if (texture != null) {
                    WorldObject tree = new WorldObject(worldTileX, worldTileY, texture, treeType);
                    objects.add(tree);
                }
            }
        }

        private float getSpawnChance(Biome biome) {
            switch (biome.getType()) {
                case SNOW:
                    return 0.2f;
                case HAUNTED:
                    return 0.5f;
                case FOREST:
                    return 0.8f;
                case PLAINS:
                    return 0.1f;
                default:
                    return 0.15f;
            }
        }

        private boolean shouldSpawnObjectAt(Chunk chunk, int localX, int localY, Biome biome, Random random) {
            int tileType = chunk.getTileType(localX, localY);
            if (!biome.getAllowedTileTypes().contains(tileType)) {
                return false;
            }
            float spawnChance = biome.getSpawnChanceForTileType(tileType);
            return random.nextFloat() < spawnChance;
        }

        private boolean canPlaceTree(Chunk chunk, int localX, int localY, List<WorldObject> existingObjects, Biome biome) {
            int treeBaseWidth = 2;  // Base is 2x2 tiles
            int treeBaseHeight = 2;
            int treeTopHeight = 1;  // Top is 2x1 tiles
            int edgeBuffer = 1;

            // Check if tree base fits within chunk bounds
            if (localX < edgeBuffer || localY < edgeBuffer ||
                localX + treeBaseWidth > Chunk.CHUNK_SIZE - edgeBuffer ||
                localY + treeBaseHeight > Chunk.CHUNK_SIZE - edgeBuffer) {
                return false;
            }

            // Check base tiles (2x2)
            for (int x = localX; x < localX + treeBaseWidth; x++) {
                for (int y = localY; y < localY + treeBaseHeight; y++) {
                    int tileType = chunk.getTileType(x, y);
                    if (!biome.getAllowedTileTypes().contains(tileType)) {
                        return false;
                    }
                }
            }
            int chunkWorldX = chunk.getChunkX() * Chunk.CHUNK_SIZE;
            int chunkWorldY = chunk.getChunkY() * Chunk.CHUNK_SIZE;
            int treeWorldX = chunkWorldX + localX;
            int treeWorldY = chunkWorldY + localY;
            int minSpacing = MIN_TREE_SPACING;
            for (WorldObject obj : existingObjects) {
                if (obj.getType() == ObjectType.TREE ||
                    obj.getType() == ObjectType.SNOW_TREE ||
                    obj.getType() == ObjectType.HAUNTED_TREE) {
                    int dx = Math.abs(obj.getTileX() - treeWorldX);
                    int dy = Math.abs(obj.getTileY() - treeWorldY);
                    if (dx < minSpacing && dy < minSpacing) {
                        return false;
                    }
                }
            }

            return true;
        }

        private static class Point {
            final int x, y;

            Point(int x, int y) {
                this.x = x;
                this.y = y;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                Point point = (Point) o;
                return x == point.x && y == point.y;
            }

            @Override
            public int hashCode() {
                return Objects.hash(x, y);
            }
        }
    }

}

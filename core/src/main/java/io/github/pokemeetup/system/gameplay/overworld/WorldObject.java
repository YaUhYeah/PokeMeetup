package io.github.pokemeetup.system.gameplay.overworld;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.data.WorldData;
import io.github.pokemeetup.system.gameplay.overworld.biomes.Biome;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.TextureManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.badlogic.gdx.math.MathUtils.random;

public class WorldObject {
    private static final float POKEBALL_DESPAWN_TIME = 300f;
    private float pixelX;  // Remove static
    private float pixelY;  // Remove static
    private TextureRegion texture;
    public ObjectType type;  // Remove static
    private String id;
    private boolean isDirty;

    public void setPixelX(float pixelX) {
        this.pixelX = pixelX;
    }

    public void setPixelY(float pixelY) {
        this.pixelY = pixelY;
    }

    public void setTexture(TextureRegion texture) {
        this.texture = texture;
    }

    public void setType(ObjectType type) {
        this.type = type;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setDirty(boolean dirty) {
        isDirty = dirty;
    }

    public boolean isCollidable() {
        return isCollidable;
    }

    public void setCollidable(boolean collidable) {
        isCollidable = collidable;
    }

    public float getSpawnTime() {
        return spawnTime;
    }

    public void setSpawnTime(float spawnTime) {
        this.spawnTime = spawnTime;
    }

    public void setRenderOrder(float renderOrder) {
        this.renderOrder = renderOrder;
    }

    public void setTileX(int tileX) {
        this.tileX = tileX;
    }

    public void setTileY(int tileY) {
        this.tileY = tileY;
    }

    private boolean isCollidable;
    private float spawnTime;
    private float renderOrder;
    private int tileX, tileY;

    public WorldObject(int tileX, int tileY, TextureRegion texture, ObjectType type) {
        this.id = UUID.randomUUID().toString();
        this.tileX = tileX;
        this.tileY = tileY;
        this.pixelX = tileX * World.TILE_SIZE;
        this.pixelY = tileY * World.TILE_SIZE;
        this.texture = texture;
        this.type = type;
        this.isCollidable = type.isCollidable;
        this.spawnTime = type.isPermanent ? 0 : System.currentTimeMillis() / 1000f;
        this.renderOrder = type == ObjectType.TREE ? pixelY + World.TILE_SIZE : pixelY;
        this.isDirty = true;
    }    // Add network-related methods

    public String getId() {
        return id;
    }

    public boolean isDirty() {
        return isDirty;
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

    public void clearDirty() {
        isDirty = false;
    }

    public void markDirty() {
        isDirty = true;
    }

    public ObjectType getType() {
        return type;
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

    public float getRenderOrder() {
        return renderOrder;
    }

    public boolean intersects(float playerX, float playerY, float playerWidth, float playerHeight) {
        if (!isCollidable) return false;

        if (type == ObjectType.TREE) {
            // For trees: 2 tiles wide, bottom and middle tiles are collidable
            float treeBaseX = pixelX - World.TILE_SIZE; // Center the 2-tile width
            float topTileY = pixelY + (World.TILE_SIZE * 2); // Start of top tile

            // Only collide if in bottom two tiles (NOT in top tile) AND within horizontal bounds
            boolean inBottomOrMiddleTiles = playerY + playerHeight <= topTileY;

            return inBottomOrMiddleTiles &&
                playerX < treeBaseX + (World.TILE_SIZE * 2) && // Full 2-tile width
                playerX + playerWidth > treeBaseX &&
                playerY + playerHeight > pixelY; // Above base of tree
        }

        // For pokeballs and other objects
        return playerX < pixelX + World.TILE_SIZE &&
            playerX + playerWidth > pixelX &&
            playerY < pixelY + World.TILE_SIZE &&
            playerY + playerHeight > pixelY;
    }

    public void render(SpriteBatch batch) {
        if (type == ObjectType.TREE) {
            // Center the 2-tile wide tree
            float renderX = pixelX ; // Center by shifting left 1 tile
            batch.draw(texture, renderX, pixelY,
                World.TILE_SIZE * 2, // 2 tiles wide
                World.TILE_SIZE * 3); // 3 tiles high
        } else if (type == ObjectType.POKEBALL) {
            batch.draw(texture, pixelX, pixelY, World.TILE_SIZE, World.TILE_SIZE);
        }
    }

    private static final float TREE_BASE_WIDTH_RATIO = 0.5f;  // 50% of full width for collision
    private static final float TREE_BASE_HEIGHT_RATIO = 0.3f; // 30% of full height for collision

    public Rectangle getBoundingBox() {
        if (type == ObjectType.TREE || type == ObjectType.SNOW_TREE || type == ObjectType.HAUNTED_TREE) {
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

    public boolean intersects(Rectangle bounds) {
        return getBoundingBox().overlaps(bounds);
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

    public int getTileX() {
        return tileX;
    }

    public int getTileY() {
        return tileY;
    }

    public boolean canBePickedUpBy(Player player) {
        if (type != ObjectType.POKEBALL) return false;

        return player.canPickupItem(pixelX, pixelY);
    }

    public enum ObjectType {
        TREE(true, true, 2, 3),      // 2 tiles wide, 3 tiles tall
        POKEBALL(false, true, 1, 1), // 1x1 tile

        SNOW_TREE(true, true, 2, 3),
        HAUNTED_TREE(true, true, 2, 3), CACTUS(true, true, 1, 2);
        final boolean isPermanent;
        final boolean isCollidable;
        final int widthInTiles;
        final int heightInTiles;

        ObjectType(boolean isPermanent, boolean isCollidable, int widthInTiles, int heightInTiles) {
            this.isPermanent = isPermanent;
            this.isCollidable = isCollidable;
            this.widthInTiles = widthInTiles;
            this.heightInTiles = heightInTiles;
        }
    }

    public static class WorldObjectManager {
        private static final float POKEBALL_SPAWN_CHANCE = 1f;
        private static final int MAX_POKEBALLS_PER_CHUNK = 2;
        private static final float TREE_SPAWN_CHANCE = 0.15f; // Increased chance
        private static final float POKEBALL_SPAWN_INTERVAL = 10f; // Try spawning every 10 seconds
        private static final int MIN_TREE_SPACING = 4; // Increased for larger trees

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
            objectTextures.put(WorldObject.ObjectType.TREE, atlas.findRegion("tree"));
            objectTextures.put(WorldObject.ObjectType.SNOW_TREE, atlas.findRegion("snow_tree"));
            objectTextures.put(WorldObject.ObjectType.HAUNTED_TREE, atlas.findRegion("haunted_tree"));
            objectTextures.put(WorldObject.ObjectType.POKEBALL, atlas.findRegion("pokeball"));
            objectTextures.put(ObjectType.CACTUS, atlas.findRegion("desert_cactus"));
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
            if (objects != null) {
//                GameLogger.info("Found " + objects.size() + " objects in chunk " + chunkPos.x + "," + chunkPos.y);
            }
            return objects != null ? objects : Collections.emptyList();
        }

        // In WorldObject.WorldObjectManager class
        public void addObjectToChunk(Vector2 chunkPos, WorldObject object) {
            // Calculate actual chunk position based on object's pixel coordinates
            int actualChunkX = (int) Math.floor(object.getPixelX() / (Chunk.CHUNK_SIZE * World.TILE_SIZE));
            int actualChunkY = (int) Math.floor(object.getPixelY() / (Chunk.CHUNK_SIZE * World.TILE_SIZE));
            Vector2 actualChunkPos = new Vector2(actualChunkX, actualChunkY);

            // Log chunk assignment
//            GameLogger.info("Adding object to chunk: " + actualChunkPos.x + "," + actualChunkPos.y +
//                " at position: " + object.getPixelX() + "," + object.getPixelY());

            List<WorldObject> objects = objectsByChunk.computeIfAbsent(actualChunkPos, k -> new CopyOnWriteArrayList<>());
            objects.add(object);

            // Log the number of objects in chunk
//            GameLogger.info("Chunk now has " + objects.size() + " objects");
        }

        public void removeObjectFromChunk(Vector2 chunkPos, String objectId) {
            operationQueue.add(new RemoveOperation(chunkPos, objectId));

            // Send network update if in multiplayer
            if (gameClient != null && !gameClient.isSinglePlayer()) {
                // Implement sendObjectRemove if not already done
                sendObjectRemove(objectId);
            }
        }public void renderTree(SpriteBatch batch, WorldObject tree) {
            // Base position centered on two tiles
            float baseX = tree.getPixelX() - World.TILE_SIZE; // Shift left by 1 tile to center
            float baseY = tree.getPixelY(); // Tree base Y position

            TextureRegion treeRegion = tree.getTexture();
            int totalWidth = treeRegion.getRegionWidth();
            int totalHeight = treeRegion.getRegionHeight();

            // Split into base (2/3 height) and top (1/3 height)
            int baseHeight = (int) (totalHeight * 2 / 3f);
            int topHeight = totalHeight - baseHeight;

            // Draw the base (2 tiles wide x 2 tiles high)
            TextureRegion baseRegion = new TextureRegion(treeRegion, 0, totalHeight - baseHeight, totalWidth, baseHeight);
            batch.draw(baseRegion, baseX, baseY, World.TILE_SIZE * 2, World.TILE_SIZE * 2);

            // Draw the top (2 tiles wide x 1 tile high) 2 tiles above the base
            TextureRegion topRegion = new TextureRegion(treeRegion, 0, 0, totalWidth, topHeight);
            batch.draw(topRegion, baseX, baseY + World.TILE_SIZE * 2, World.TILE_SIZE * 2, World.TILE_SIZE);
        }

        public Rectangle getTreeBoundingBox(WorldObject tree) {
            // Base position centered on two tiles
            float baseX = tree.getPixelX() - (World.TILE_SIZE / 2f); // Center on two tiles
            float baseY = tree.getPixelY();

            // Set bounding box only for the trunk (base of the tree)
            float trunkWidth = World.TILE_SIZE; // Trunk spans 1 tile in width
            float trunkHeight = World.TILE_SIZE; // Collision height for the trunk only

            return new Rectangle(baseX, baseY, trunkWidth, trunkHeight);
        }

        public void handleNetworkUpdate(NetworkProtocol.WorldObjectUpdate update) {
            Vector2 chunkPos = calculateChunkPos(update);
            operationQueue.add(new UpdateOperation(chunkPos, update));
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
            // Try several times to find a valid spawn location
            for (int attempts = 0; attempts < 10; attempts++) {
                int localX = random.nextInt(Chunk.CHUNK_SIZE);
                int localY = random.nextInt(Chunk.CHUNK_SIZE);

                // Only spawn on grass or sand
                int tileType = chunk.getTileType(localX, localY);
                if (tileType == Chunk.GRASS || tileType == Chunk.SAND) {

                    int worldTileX = (int) (chunkPos.x * Chunk.CHUNK_SIZE) + localX;
                    int worldTileY = (int) (chunkPos.y * Chunk.CHUNK_SIZE) + localY;

                    // Check if location is clear of other objects
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



        private Vector2 calculateChunkPos(NetworkProtocol.WorldObjectUpdate update) {
            int tileX = (int) update.data.get("tileX");
            int tileY = (int) update.data.get("tileY");
            int chunkX = Math.floorDiv(tileX, Chunk.CHUNK_SIZE);
            int chunkY = Math.floorDiv(tileY, Chunk.CHUNK_SIZE);
            return new Vector2(chunkX, chunkY);
        }

        private WorldObject createObjectFromUpdate(NetworkProtocol.WorldObjectUpdate update) {
            ObjectType type = ObjectType.valueOf((String) update.data.get("type"));
            TextureRegion texture = objectTextures.get(type);
            return new WorldObject(
                (int) update.data.get("tileX"),
                (int) update.data.get("tileY"),
                texture,
                type
            );
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

        public void generateObjectsForChunk(Vector2 chunkPos, Chunk chunk, Biome biome) {
            List<WorldObject> objects = objectsByChunk.computeIfAbsent(chunkPos, k -> new CopyOnWriteArrayList<>());
            Random random = new Random((long) (worldSeed + chunkPos.x * 31 + chunkPos.y * 17)); // Better seed mixing

            WorldObject.ObjectType treeType = determineTreeType(biome);
            float baseSpawnChance = getSpawnChance(biome);

            // Try multiple passes for tree placement
            for (int attempts = 0; attempts < 3; attempts++) { // Multiple attempts per chunk
                for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
                    for (int y = 0; y < Chunk.CHUNK_SIZE; y++) {
                        if (shouldSpawnObjectAt(chunk, x, y, biome, random) &&
                            random.nextFloat() < baseSpawnChance) {
                            tryPlaceTree(chunk, x, y, objects, biome, chunkPos, treeType);
                        }
                    }
                }
            }
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

        // Determine tree type based on biome type
        private WorldObject.ObjectType determineTreeType(Biome biome) {
            switch (biome.getType()) {
                case SNOW:
                    return WorldObject.ObjectType.SNOW_TREE;
                case DESERT:
                    return ObjectType.CACTUS;
                case HAUNTED:
                    return WorldObject.ObjectType.HAUNTED_TREE;
                default:
                    return WorldObject.ObjectType.TREE;
            }
        }

        // Get the base spawn chance based on biome type

        private float getSpawnChance(Biome biome) {
            switch (biome.getType()) {
                case SNOW:
                    return 0.2f;      // More trees in snow
                case HAUNTED:
                    return 0.5f;      // High density in haunted
                case FOREST:
                    return 0.8f;      // Very high density in forest
                case PLAINS:
                    return 0.1f;      // Low density in plains
                default:
                    return 0.15f;
            }
        }

        // Determine if an object should spawn at a given location based on the biome and tile type
        private boolean shouldSpawnObjectAt(Chunk chunk, int localX, int localY, Biome biome, Random random) {
            int tileType = chunk.getTileType(localX, localY);

            // Check if the tile type is allowed for spawning in the current biome
            if (!biome.getAllowedTileTypes().contains(tileType)) {
                return false;
            }

            // Get the adjusted spawn chance for this specific tile type
            float spawnChance = biome.getSpawnChanceForTileType(tileType);
            return random.nextFloat() < spawnChance;
        }
        public void createObjectFromData(WorldData.WorldObjectData data) {
            if (data == null) {
                GameLogger.error("Cannot create object from null data");
                return;
            }

            try {
                // Convert world object type from string
                WorldObject.ObjectType objectType = WorldObject.ObjectType.valueOf(data.type);

                // Get appropriate texture for the object type
                TextureRegion texture = TextureManager.getTextureForObjectType(objectType);

                if (texture == null) {
                    GameLogger.error("No texture found for object type: " + objectType);
                    return;
                }

                // Convert pixel coordinates to tile coordinates
                int tileX = (int) Math.floor(data.x / World.TILE_SIZE);
                int tileY = (int) Math.floor(data.y / World.TILE_SIZE);

                // Create the world object
                WorldObject worldObject = new WorldObject(tileX, tileY, texture, objectType);

                // Set the ID if it exists in the data
                if (data.id != null) {
                    worldObject.setId(data.id);
                }

                // Calculate chunk position
                Vector2 chunkPos = new Vector2(
                    Math.floorDiv(tileX, Chunk.CHUNK_SIZE),
                    Math.floorDiv(tileY, Chunk.CHUNK_SIZE)
                );

                // Add object to the appropriate chunk
                addObjectToChunk(chunkPos, worldObject);

                GameLogger.info("Created " + objectType + " at (" + tileX + ", " + tileY + ")");

            } catch (IllegalArgumentException e) {
                GameLogger.error("Invalid object type: " + data.type);
            } catch (Exception e) {
                GameLogger.error("Failed to create world object: " + e.getMessage());
                e.printStackTrace();
            }
        }
        // Ensure trees are spaced out and only spawn on appropriate tiles
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

            // Calculate world coordinates
            int chunkWorldX = chunk.getChunkX() * Chunk.CHUNK_SIZE;
            int chunkWorldY = chunk.getChunkY() * Chunk.CHUNK_SIZE;
            int treeWorldX = chunkWorldX + localX;
            int treeWorldY = chunkWorldY + localY;

            // Check spacing from other trees
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

        public boolean isColliding(Rectangle bounds) {
            // Calculate chunk coordinates from the center of the bounds
            int chunkX = (int) Math.floor((bounds.x + bounds.width / 2) / (Chunk.CHUNK_SIZE * World.TILE_SIZE));
            int chunkY = (int) Math.floor((bounds.y + bounds.height / 2) / (Chunk.CHUNK_SIZE * World.TILE_SIZE));
            Vector2 chunkPos = new Vector2(chunkX, chunkY);

            // Check surrounding chunks to handle edge cases
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    Vector2 checkPos = new Vector2(chunkX + dx, chunkY + dy);
                    List<WorldObject> objects = objectsByChunk.get(checkPos);

                    if (objects != null) {
                        for (WorldObject obj : objects) {
                            if (obj.isCollidable && obj.getBoundingBox().overlaps(bounds)) {
                                GameLogger.info("Collision with object: " + obj.getType() +
                                    " at (" + obj.getPixelX() + ", " + obj.getPixelY() + ")");
                                return true;
                            }
                        }
                    }
                }
            }

            return false;
        }

        public void renderChunkObjects(SpriteBatch batch, Vector2 chunkPos) {
            List<WorldObject> objects = objectsByChunk.get(chunkPos);
            if (objects != null) {
                for (WorldObject obj : objects) {
                    obj.render(batch);
                }
            }
        }
    }
}

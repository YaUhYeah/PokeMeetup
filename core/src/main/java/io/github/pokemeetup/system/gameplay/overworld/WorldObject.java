package io.github.pokemeetup.system.gameplay.overworld;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.gameplay.overworld.biomes.Biome;
import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;

import java.util.*;

import static com.badlogic.gdx.math.MathUtils.random;

public class WorldObject {
    private static final float POKEBALL_DESPAWN_TIME = 300f;
    private float pixelX;  // Remove static
    private float pixelY;  // Remove static
    private TextureRegion texture;
    private ObjectType type;  // Remove static
    private String id;
    private boolean isDirty;
    private boolean isCollidable;
    private float spawnTime;
    private float renderOrder;
    private int tileX, tileY;

    public WorldObject(int tileX, int tileY, TextureRegion texture, ObjectType type) {    this.id = UUID.randomUUID().toString();    this.id = UUID.randomUUID().toString();
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
    }  public void updateFromNetwork(NetworkProtocol.WorldObjectUpdate update) {
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
        if (type != ObjectType.TREE) return false;

        float treeBaseX = pixelX - World.TILE_SIZE;
        float treeBaseY = pixelY + (World.TILE_SIZE * 2); // Top of tree position

        // Check if player is in the leafy part area
        return playerX < treeBaseX + (World.TILE_SIZE * 2) &&
            playerX + playerWidth > treeBaseX &&
            playerY + playerHeight > treeBaseY;
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
            float renderX = pixelX - World.TILE_SIZE; // Center by shifting left 1 tile
            batch.draw(texture, renderX, pixelY,
                World.TILE_SIZE * 2, // 2 tiles wide
                World.TILE_SIZE * 3); // 3 tiles high
        } else if (type == ObjectType.POKEBALL) {
            batch.draw(texture, pixelX, pixelY, World.TILE_SIZE, World.TILE_SIZE);
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
        HAUNTED_TREE(true, true, 2, 3);
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
        private static final float POKEBALL_SPAWN_CHANCE = 0.001f;
        private final GameClient gameClient;
        private static final int MAX_POKEBALLS_PER_CHUNK = 2;
        private static final float TREE_SPAWN_CHANCE = 0.15f; // Increased chance
        private static final float POKEBALL_SPAWN_INTERVAL = 10f; // Try spawning every 10 seconds
        private static final int MIN_TREE_SPACING = 4; // Increased for larger trees
        private Map<Vector2, List<WorldObject>> chunkObjects = new HashMap<>();

        private TextureAtlas atlas;
        private Map<WorldObject.ObjectType, TextureRegion> objectTextures;

        private long worldSeed;

        public WorldObjectManager(TextureAtlas atlas, long seed, GameClient gameClient) {
            this.worldSeed = seed;

            this.gameClient = gameClient;
            chunkObjects = new HashMap<>();
            objectTextures = new HashMap<>();
            this.atlas = atlas;
            objectTextures.put(WorldObject.ObjectType.TREE, atlas.findRegion("tree"));
            objectTextures.put(WorldObject.ObjectType.SNOW_TREE, atlas.findRegion("snow_tree"));
            objectTextures.put(WorldObject.ObjectType.HAUNTED_TREE, atlas.findRegion("haunted_tree"));
            objectTextures.put(WorldObject.ObjectType.POKEBALL, atlas.findRegion("pokeball"));
        }

        public void renderTreeBase(SpriteBatch batch, WorldObject tree) {
            float renderX = tree.getPixelX() - World.TILE_SIZE;
            float renderY = tree.getPixelY();

            TextureRegion treeRegion = tree.getTexture();
            int totalWidth = treeRegion.getRegionWidth();
            int totalHeight = treeRegion.getRegionHeight();

            int baseHeight = (int) (totalHeight * 2 / 3f);
            int baseY = totalHeight - baseHeight; // Start from top

            TextureRegion baseRegion = new TextureRegion(treeRegion, 0, baseY, totalWidth, baseHeight);

            batch.draw(baseRegion,
                renderX, renderY,
                World.TILE_SIZE * 2,
                World.TILE_SIZE * 2
            );
        }

        public void renderTreeTop(SpriteBatch batch, WorldObject tree) {
            float renderX = tree.getPixelX() - World.TILE_SIZE;
            float renderY = tree.getPixelY() + (World.TILE_SIZE * 2);

            TextureRegion treeRegion = tree.getTexture();
            int totalWidth = treeRegion.getRegionWidth();
            int totalHeight = treeRegion.getRegionHeight();

            int topHeight = totalHeight - (int) (totalHeight * 2 / 3f);
            int topY = 0; // Start from bottom

            TextureRegion topRegion = new TextureRegion(treeRegion, 0, topY, totalWidth, topHeight);

            batch.draw(topRegion,
                renderX, renderY,
                World.TILE_SIZE * 2,
                World.TILE_SIZE
            );
        }


        public boolean isPlayerUnderTree(float playerX, float playerY, float playerWidth, float playerHeight) {
            // Need to check all loaded chunks near the player
            int chunkX = (int) Math.floor(playerX / (Chunk.CHUNK_SIZE * World.TILE_SIZE));
            int chunkY = (int) Math.floor(playerY / (Chunk.CHUNK_SIZE * World.TILE_SIZE));

            // Check current chunk and adjacent chunks
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    Vector2 chunkPos = new Vector2(chunkX + dx, chunkY + dy);
                    List<WorldObject> objects = chunkObjects.get(chunkPos);
                    if (objects != null) {
                        for (WorldObject obj : objects) {
                            if (obj.getType() == ObjectType.TREE) {
                                if (obj.isUnderTree(playerX, playerY, playerWidth, playerHeight)) {
                                    return true;
                                }
                            } if (obj.getType() == ObjectType.HAUNTED_TREE) {
                                if (obj.isUnderTree(playerX, playerY, playerWidth, playerHeight)) {
                                    return true;
                                }
                            } if (obj.getType() == ObjectType.SNOW_TREE) {
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

        // In WorldObjectManager class:
        public List<WorldObject> getObjectsForChunk(Vector2 chunkPos) {
            return chunkObjects.getOrDefault(chunkPos, new ArrayList<>());
        }

        public void update(Map<Vector2, Chunk> loadedChunks) {
            // Update existing objects and try spawning new pokeballs
            for (Map.Entry<Vector2, Chunk> entry : loadedChunks.entrySet()) {
                Vector2 chunkPos = entry.getKey();
                List<WorldObject> objects = chunkObjects.computeIfAbsent(chunkPos, k -> new ArrayList<>());

                // Remove expired pokeballs
                objects.removeIf(WorldObject::isExpired);

                // Try to spawn new pokeballs
                long pokeballCount = objects.stream()
                    .filter(obj -> obj.getType() == ObjectType.POKEBALL)
                    .count();

                if (pokeballCount < MAX_POKEBALLS_PER_CHUNK && random() < POKEBALL_SPAWN_CHANCE) {
                    spawnPokeball(chunkPos, objects, entry.getValue());
                }
            }

            // Clean up unloaded chunks
            chunkObjects.keySet().removeIf(chunkPos -> !loadedChunks.containsKey(chunkPos));
        }

        private boolean shouldSpawnPokeball(List<WorldObject> chunkObjects) {
            long pokeballCount = chunkObjects.stream()
                .filter(obj -> obj.type == ObjectType.POKEBALL)
                .count();
            return pokeballCount < MAX_POKEBALLS_PER_CHUNK &&
                random() < POKEBALL_SPAWN_CHANCE;
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

                            System.out.println("Spawned pokeball at: " + worldTileX + "," + worldTileY);
                        }
                    }
                }
            }
        }     public void handleNetworkUpdate(NetworkProtocol.WorldObjectUpdate update) {
            Vector2 chunkPos = calculateChunkPos(update);
            List<WorldObject> objects = chunkObjects.computeIfAbsent(chunkPos, k -> new ArrayList<>());

            switch (update.type) {
                case ADD:
                    // Create new object from network data
                    WorldObject newObject = createObjectFromUpdate(update);
                    objects.add(newObject);
                    break;

                case REMOVE:
                    // Remove object by ID
                    objects.removeIf(obj -> obj.getId().equals(update.objectId));
                    break;

                case UPDATE:
                    // Update existing object
                    objects.stream()
                        .filter(obj -> obj.getId().equals(update.objectId))
                        .findFirst()
                        .ifPresent(obj -> obj.updateFromNetwork(update));
                    break;
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

        private void sendObjectRemove(WorldObject object) {
            if (gameClient == null || gameClient.isSinglePlayer()) return;

            NetworkProtocol.WorldObjectUpdate update = new NetworkProtocol.WorldObjectUpdate();
            update.objectId = object.getId();
            update.type = NetworkProtocol.NetworkObjectUpdateType.REMOVE;

            gameClient.sendWorldObjectUpdate(update);
        }

        public void addObjectToChunk(Vector2 chunkPos, WorldObject object) {
            List<WorldObject> objects = chunkObjects.computeIfAbsent(chunkPos, k -> new ArrayList<>());
            objects.add(object);

            // Send network update if in multiplayer
            if (gameClient != null && !gameClient.isSinglePlayer()) {
                sendObjectSpawn(object);
            }
        }

        public WorldObject createObject(WorldObject.ObjectType type, float x, float y) {
            TextureRegion texture = objectTextures.get(type);
            if (texture == null) {
                throw new IllegalStateException("No texture found for object type: " + type);
            }

            int tileX = (int)(x / World.TILE_SIZE);
            int tileY = (int)(y / World.TILE_SIZE);

            return new WorldObject(tileX, tileY, texture, type);
        }
        public void generateObjectsForChunk(Vector2 chunkPos, Chunk chunk, Biome biome) {
            List<WorldObject> objects = chunkObjects.computeIfAbsent(chunkPos, k -> new ArrayList<>());
            long chunkSeed = worldSeed ^ ((long) chunk.getChunkX() << 32 | (chunk.getChunkY() & 0xffffffffL));
            Random random = new Random(chunkSeed);

            // Get correct tree type based on biome
            WorldObject.ObjectType treeType;
            if (biome.getType() == BiomeType.SNOW) {
                treeType = WorldObject.ObjectType.SNOW_TREE;
            } else if (biome.getType() == BiomeType.HAUNTED) {
                treeType = WorldObject.ObjectType.HAUNTED_TREE;
            } else {
                treeType = WorldObject.ObjectType.TREE;
            }

            // Adjust tree spawn rates based on biome
            float baseSpawnChance = switch (biome.getType()) {
                case SNOW -> 0.08f;      // Fewer trees in snow
                case HAUNTED -> 0.12f;   // Medium density in haunted
                case FOREST -> 0.15f;    // Most trees in forest
                case PLAINS -> 0.05f;    // Fewest trees in plains
                default -> 0.1f;
            };

            for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
                for (int y = 0; y < Chunk.CHUNK_SIZE; y++) {
                    if (shouldSpawnObjectAt(chunk, x, y, biome, random) &&
                        random.nextFloat() < baseSpawnChance) {

                        if (canPlaceTree(chunk, x, y, objects, biome)) {
                            int worldTileX = (int) (chunkPos.x * Chunk.CHUNK_SIZE) + x;
                            int worldTileY = (int) (chunkPos.y * Chunk.CHUNK_SIZE) + y;

                            TextureRegion texture = objectTextures.get(treeType);
                            if (texture != null) {
                                objects.add(new WorldObject(worldTileX, worldTileY, texture, treeType));
                            }
                        }
                    }
                }
            }
        }

        private boolean shouldSpawnObjectAt(Chunk chunk, int localX, int localY, Biome biome, Random random) {
            int tileType = chunk.getTileType(localX, localY);

            // Check if this tile type is suitable for spawning
            if (!biome.getAllowedTileTypes().contains(tileType)) {
                return false;
            }

            // Get spawn chance with slight randomization
            float spawnChance = biome.getSpawnChanceForTileType(tileType);
            return random.nextFloat() < spawnChance;
        }


        private boolean isObjectAt(int worldX, int worldY) {
            int chunkX = (int) Math.floor((float) worldX / Chunk.CHUNK_SIZE);
            int chunkY = (int) Math.floor((float) worldY / Chunk.CHUNK_SIZE);
            Vector2 chunkPos = new Vector2(chunkX, chunkY);

            List<WorldObject> objects = chunkObjects.get(chunkPos);
            if (objects != null) {
                for (WorldObject obj : objects) {
                    if (obj.getTileX() == worldX && obj.getTileY() == worldY) {
                        return true;
                    }
                }
            }
            return false;
        }

        private WorldObject.ObjectType selectRandomObjectType(Biome biome) {
            List<WorldObject.ObjectType> objectTypes = biome.getSpawnableObjects();
            if (objectTypes.isEmpty()) return null;
            int index = random(objectTypes.size() - 1);
            return objectTypes.get(index);
        }

        private boolean canPlaceTree(Chunk chunk, int localX, int localY, List<WorldObject> existingObjects, Biome biome) {
            // Tree dimensions in tiles
            int treeWidth = 2;
            int treeHeight = 3;
            int edgeBuffer = 2; // Adjust as needed

            // Check if tree fits within the chunk boundaries with edge buffer
            if (localX < edgeBuffer || localY < edgeBuffer ||
                localX + treeWidth > Chunk.CHUNK_SIZE - edgeBuffer ||
                localY + treeHeight > Chunk.CHUNK_SIZE - edgeBuffer) {
                return false;
            }
            // Check if tree fits within the chunk boundaries

            // Check that all tiles under the tree are suitable
            for (int x = localX; x < localX + treeWidth; x++) {
                for (int y = localY; y < localY + treeHeight; y++) {
                    int tileType = chunk.getTileType(x, y);
                    // Ensure the tile type is allowed for trees in this biome
                    if (!biome.getAllowedTileTypes().contains(tileType)) {
                        return false;
                    }
                }
            }

            // Check for proximity to existing objects to maintain spacing
            int minSpacing = 2; // Minimum spacing between trees
            int chunkWorldX = chunk.getChunkX() * Chunk.CHUNK_SIZE;
            int chunkWorldY = chunk.getChunkY() * Chunk.CHUNK_SIZE;
            int treeWorldX = chunkWorldX + localX;
            int treeWorldY = chunkWorldY + localY;

            for (WorldObject obj : existingObjects) {
                if (obj.getType() == WorldObject.ObjectType.TREE) {
                    int dx = Math.abs(obj.getTileX() - treeWorldX);
                    int dy = Math.abs(obj.getTileY() - treeWorldY);
                    if (dx < minSpacing && dy < minSpacing) {
                        return false;
                    }
                }
            }

            return true;
        }


        public boolean isColliding(float x, float y, float width, float height) {
            int chunkX = (int) Math.floor(x / (Chunk.CHUNK_SIZE * World.TILE_SIZE));
            int chunkY = (int) Math.floor(y / (Chunk.CHUNK_SIZE * World.TILE_SIZE));
            Vector2 chunkPos = new Vector2(chunkX, chunkY);

            List<WorldObject> objects = chunkObjects.get(chunkPos);
            if (objects == null) return false;

            return objects.stream()
                .anyMatch(obj -> obj.isCollidable && obj.intersects(x, y, width, height));
        }

        public void renderChunkObjects(SpriteBatch batch, Vector2 chunkPos) {
            List<WorldObject> objects = chunkObjects.get(chunkPos);
            if (objects != null) {
                for (WorldObject obj : objects) {
                    obj.render(batch);
                }
            }
        }
    }
}

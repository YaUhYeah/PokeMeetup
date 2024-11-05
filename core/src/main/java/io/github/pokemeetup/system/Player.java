package io.github.pokemeetup.system;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import io.github.pokemeetup.CreatureCaptureGame;
import io.github.pokemeetup.blocks.PlaceableBlock;
import io.github.pokemeetup.pokemon.Pokemon;
import io.github.pokemeetup.pokemon.PokemonParty;
import io.github.pokemeetup.pokemon.WildPokemon;
import io.github.pokemeetup.screens.otherui.BuildModeUI;
import io.github.pokemeetup.system.data.PlayerData;
import io.github.pokemeetup.system.data.PokemonData;
import io.github.pokemeetup.system.gameplay.PlayerAnimations;
import io.github.pokemeetup.system.gameplay.inventory.Inventory;
import io.github.pokemeetup.system.gameplay.inventory.Item;
import io.github.pokemeetup.system.data.ItemData;
import io.github.pokemeetup.system.gameplay.inventory.ItemManager;
import io.github.pokemeetup.system.gameplay.overworld.PokemonSpawnManager;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.WorldObject;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.TextureManager;
import io.github.pokemeetup.utils.storage.InventoryConverter;

import java.util.*;

import static io.github.pokemeetup.system.gameplay.overworld.World.INTERACTION_RANGE;
import static io.github.pokemeetup.system.gameplay.overworld.World.TILE_SIZE;

public class Player {
    public static final int FRAME_WIDTH = 32;
    public static final int FRAME_HEIGHT = 48;
    public static final int MIN_TILE_BOUND = -5000;
    public static final int MAX_TILE_BOUND = 5000;
    private static final float MOVEMENT_INTERPOLATION_SPEED = 10f;
    private static final float POSITION_THRESHOLD = 0.1f;
    private static final float COLLISION_BOX_WIDTH_RATIO = 0.6f; // Make hitbox 60% of frame width
    private static final float COLLISION_BOX_HEIGHT_RATIO = 0.4f; // Make hitbox 40% of frame height
    private static final float COLLISION_BOX_Y_OFFSET = 8f; // Offset from bottom of sprite
    private static final float TILE_TRANSITION_TIME = 0.25f; // Time to move one tile
    private static final float COLLISION_BOX_WIDTH = 20f;  // Smaller than FRAME_WIDTH
    private static final float COLLISION_BOX_HEIGHT = 16f; // Smaller than FRAME_HEIGHT
    private static final float RUN_SPEED_MULTIPLIER = 1.75f; // More noticeable run
    private static final float PICKUP_RANGE = 43f;
    private static final float MOVE_SPEED = TILE_SIZE / TILE_TRANSITION_TIME; // Units per second
    private static final float MOVEMENT_SMOOTHING = 0.25f; // Time for smooth movement
    private static final float COLLISION_BUFFER = 4f; // Increased for better collision detection
    private static final float ACCELERATION = 800f; // Pixels per second squared
    private static final float MAX_SPEED = 200f;    // Maximum speed
    private final PlayerAnimations animations;
    private final String username;
    // Locks for Thread Safety
    private final Object movementLock = new Object();
    private PokemonSpawnManager spawnManager;
    // Game and World References
    private World world;
    // Add these fields
    private float inputBufferTimer = 0f;
    private String bufferedDirection = null;
    private Vector2 movementVector = new Vector2();
    private boolean isSnapping = false;
    private Vector2 position = new Vector2();
    // Enhanced collision detection
    private Rectangle collisionBox;
    private Rectangle nextPositionBox;
    private PokemonParty pokemonParty = new PokemonParty();
    private Inventory buildInventory = new Inventory();
    private Vector2 renderPosition = new Vector2();
    private Vector2 lastPosition = new Vector2();
    private Vector2 targetPosition = new Vector2();
    private Vector2 velocity = new Vector2();
    private Vector2 currentPosition = new Vector2();
    private Vector2 startPosition = new Vector2();
    private String direction = "down";
    private boolean isMoving = false;
    private boolean isRunning = false;
    private boolean buildMode = false;
    private boolean isColliding = false;
    private TextureRegion currentFrame;
    private Item heldBlock = null;
    private Inventory inventory = new Inventory();
    private Map<UUID, Pokemon> ownedPokemon = new HashMap<>();
    private Queue<String> queuedDirections = new LinkedList<>();
    private Queue<String> inputQueue = new LinkedList<>();
    // Movement Control Variables
    private float moveTimer = 0f;
    private float animationTimer = 0f;
    private float stateTime = 0f;
    private float x = 0f;
    private float y = 0f;
    private int tileX, tileY;
    private int targetTileX, targetTileY;
    // Collision and Rendering
    private Rectangle boundingBox;
    private boolean isMovingFlag = false;
    private boolean isRunningFlag = false;
    private BitmapFont font;
    // Player Datax
    private PlayerData playerData;
    /**
     * Loads player data from the saved state.
     *
     * @param savedData The saved player data.
     */
    private float movementProgress;
    private String queuedDirection;
    private boolean isInterpolating;
    private BuildModeUI buildModeUI;
    // Constructor with default username
    public Player(int startTileX, int startTileY, World world) {
        this(startTileX, startTileY, world, "Player");
        this.playerData = new PlayerData("Player");
    }
    public Player(String username) {
        this(0, 0, null, username); // Default position and null world

        this.playerData = new PlayerData("Player");
    }
    public Player(int startTileX, int startTileY, World world, String username) {
        this.world = world;
        this.username = username != null ? username : "Player";
        this.animations = new PlayerAnimations();
        this.spawnManager = world.getPokemonSpawnManager();

        // Initialize collision boxes
        float boxWidth = FRAME_WIDTH * COLLISION_BOX_WIDTH_RATIO;
        float boxHeight = FRAME_HEIGHT * COLLISION_BOX_HEIGHT_RATIO;

        this.collisionBox = new Rectangle(0, 0, boxWidth, boxHeight);
        this.nextPositionBox = new Rectangle(0, 0, boxWidth, boxHeight);

        // Initialize position
        initializePosition(startTileX, startTileY);

        this.playerData = new PlayerData("Player");
        // Initialize other components
        initFont();
        this.direction = "down";
        this.inventory = new Inventory();
        this.buildInventory = new Inventory();
        this.pokemonParty = new PokemonParty();
        this.ownedPokemon = new HashMap<>();

        // Load saved state if available
        initializeBuildInventory();
        initializeFromSavedState();
        this.renderPosition = new Vector2(x, y);
        this.lastPosition = new Vector2(x, y);

        GameLogger.info(String.format("Player '%s' initialized at tile (%d, %d).", username, tileX, tileY));
    }

    public void updateFromPlayerData(PlayerData data) {
        if (data == null) {
            GameLogger.error("Cannot update player from null PlayerData");
            return;
        }

        try {
            // Update basic position and movement info
            updatePositionFromData(data);
            updateMovementFromData(data);

            // Update inventory
            updateInventoryFromData(data);

            // Update Pokemon party
            updatePokemonFromData(data);

            GameLogger.info("Successfully updated player " + username + " from PlayerData");
        } catch (Exception e) {
            GameLogger.error("Error updating player from data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Helper method to update position
    private void updatePositionFromData(PlayerData data) {
        synchronized (movementLock) {
            // Convert tile coordinates to pixel coordinates
            float newX = data.getX() * World.TILE_SIZE;
            float newY = data.getY() * World.TILE_SIZE;

            // Update all position-related fields
            this.x = newX;
            this.y = newY;
            this.tileX = (int) data.getX();
            this.tileY = (int) data.getY();
            this.position.set(newX, newY);
            this.renderPosition.set(newX, newY);
            this.targetPosition.set(newX, newY);
            this.startPosition.set(newX, newY);

            // Update collision boxes
            updateCollisionBoxes();

            GameLogger.info("Updated position to: " + newX + "," + newY);
        }
    }

    // Helper method to update movement state
    private void updateMovementFromData(PlayerData data) {
        this.direction = data.getDirection() != null ? data.getDirection() : "down";
        this.isMoving = data.isMoving();
        this.isRunning = data.isWantsToRun();

        GameLogger.info("Updated movement state - Direction: " + direction +
            ", Moving: " + isMoving + ", Running: " + isRunning);
    }

    // Helper method to update inventory
    private void updateInventoryFromData(PlayerData data) {
        if (data.getInventoryItems() != null) {
            this.inventory.clear();
            for (ItemData item : data.getInventoryItems()) {
                if (item != null) {
                    this.inventory.addItem(item);
                }
            }
            GameLogger.info("Updated inventory with " + data.getInventoryItems().size() + " items");
        }
    }

    // Helper method to update Pokemon
    private void updatePokemonFromData(PlayerData data) {
        if (data.getPartyPokemon() != null) {
            // Clear current party
            this.pokemonParty.clearParty();

            // Add each Pokemon from the data
            for (PokemonData pokemonData : data.getPartyPokemon()) {
                if (pokemonData != null) {
                    Pokemon pokemon = pokemonData.toPokemon();
                    if (pokemon != null) {
                        this.pokemonParty.addPokemon(pokemon);
                        this.ownedPokemon.put(pokemon.getUuid(), pokemon);
                    }
                }
            }

            GameLogger.info("Updated Pokemon party with " +
                data.getPartyPokemon().size() + " Pokemon");
        }
    }

    // Helper method to sync current state back to PlayerData
    public void syncToPlayerData() {
        if (playerData == null) {
            playerData = new PlayerData(username);
        }

        // Update position (convert from pixels to tiles)
        playerData.setX(getTileX());
        playerData.setY(getTileY());

        // Update movement state
        playerData.setDirection(direction);
        playerData.setMoving(isMoving);
        playerData.setWantsToRun(isRunning);

        // Update inventory
        playerData.setInventoryItems(inventory.getAllItems());

        // Update Pokemon
        List<PokemonData> partyData = new ArrayList<>();
        for (Pokemon pokemon : pokemonParty.getParty()) {
            partyData.add(PokemonData.fromPokemon(pokemon));
        }
        playerData.setPartyPokemon(partyData);

        GameLogger.info("Synced current state to PlayerData for " + username);
    }

    public int getChunkX() {
        int tileX = getTileX();
        int chunkSize = World.CHUNK_SIZE;
        // Handle negative coordinates correctly
        int chunkX = (tileX >= 0) ? (tileX / chunkSize) : ((tileX + 1) / chunkSize - 1);
        return chunkX;
    }

    public int getChunkY() {
        int tileY = getTileY();
        int chunkSize = World.CHUNK_SIZE;
        int chunkY = (tileY >= 0) ? (tileY / chunkSize) : ((tileY + 1) / chunkSize - 1);
        return chunkY;
    }

    public void setBuildModeUI(BuildModeUI buildModeUI) {
        this.buildModeUI = buildModeUI;
    }

    // Add helper method to initialize build inventory
    private void initializeBuildInventory() {
        // Add default blocks to build inventory
        for (PlaceableBlock.BlockType blockType : PlaceableBlock.BlockType.values()) {
            ItemData blockItem = new ItemData(blockType.getId(), 64); // Give a stack of blocks
            buildInventory.addItem(blockItem);
        }
    }

    // Main Constructor

    // Modify the initialization method
// Update initialization method to be clear about coordinate systems
    private void initializePosition(int startTileX, int startTileY) {
        // Store tile coordinates
        this.tileX = startTileX;
        this.tileY = startTileY;

        // Convert to pixel coordinates
        this.x = tileToPixelX(startTileX);
        this.y = tileToPixelY(startTileY);

        this.boundingBox = new Rectangle(x + COLLISION_BUFFER, y + COLLISION_BUFFER, FRAME_WIDTH - (COLLISION_BUFFER * 2), FRAME_HEIGHT - (COLLISION_BUFFER * 2));
        // Set all position vectors in pixel coordinates
        this.position = new Vector2(x, y);
        this.targetPosition = new Vector2(x, y);
        this.renderPosition = new Vector2(x, y);
        this.lastPosition = new Vector2(x, y);
        this.currentPosition = new Vector2(x, y);
        this.startPosition = new Vector2(x, y);

        // Set initial target tiles (no movement yet)
        this.targetTileX = tileX;
        this.targetTileY = tileY;

        GameLogger.info(String.format("Player initialized at tile (%d, %d) [pixel pos: %.2f, %.2f]", tileX, tileY, x, y));
    }

    private void validateAndFixPosition(int tileX, int tileY) {
        // Check if we're getting world center coordinates


        // Validate against world boundaries
        tileX = MathUtils.clamp(tileX, MIN_TILE_BOUND, MAX_TILE_BOUND);
        tileY = MathUtils.clamp(tileY, MIN_TILE_BOUND, MAX_TILE_BOUND);

        // Update all position variables consistently
        this.tileX = tileX;
        this.tileY = tileY;
        this.x = tileX * World.TILE_SIZE;
        this.y = tileY * World.TILE_SIZE;

        // Update vectors
        this.position.set(x, y);
        this.currentPosition.set(x, y);
        this.targetPosition.set(x, y);
        this.startPosition.set(x, y);

        GameLogger.info(String.format("Position validated and set to tile (%d, %d), pixel (%f, %f)", tileX, tileY, x, y));
    }

    // Add these helper methods for coordinate conversion
    private float tileToPixelX(int tileX) {
        return tileX * World.TILE_SIZE;
    }

    private float tileToPixelY(int tileY) {
        return tileY * World.TILE_SIZE;
    }

    private int pixelToTileX(float pixelX) {
        return (int) Math.floor(pixelX / World.TILE_SIZE);
    }

    private int pixelToTileY(float pixelY) {
        return (int) Math.floor(pixelY / World.TILE_SIZE);
    }

    private void loadFromSavedData(PlayerData savedData) {
        if (savedData == null) return;

        // Convert saved coordinates to tile coordinates
        int loadedTileX = (int) savedData.getX();
        int loadedTileY = (int) savedData.getY();

        // Validate and fix position
        validateAndFixPosition(loadedTileX, loadedTileY);

        // Load other state
        this.direction = savedData.getDirection();
        this.isMoving = false; // Always start stationary
        this.isRunning = savedData.isWantsToRun();

        // Apply inventory data
        InventoryConverter.applyInventoryDataToPlayer(savedData, this);

        GameLogger.info(String.format("Loaded player state at tile (%d, %d)", tileX, tileY));
    }

    private void initializeFromSavedState() {
        if (world != null && world.getWorldData() != null) {
            PlayerData savedData = world.getWorldData().getPlayerData(username);
            if (savedData != null) {
                loadFromSavedData(savedData);
                GameLogger.info("Loaded saved state for player: " + username);
            } else {
                this.playerData = new PlayerData(username);
                GameLogger.info("Created new player data for: " + username);
            }
        }
    }

    private void loadPokemonData(PlayerData savedData) {
        if (savedData.getPartyPokemon() != null) {
            for (PokemonData pokemonData : savedData.getPartyPokemon()) {
                Pokemon pokemon = pokemonData.toPokemon();
                if (pokemon != null) {
                    addPokemonToParty(pokemon);
                }
            }
        }

        if (savedData.getStoredPokemon() != null) {
            for (PokemonData pokemonData : savedData.getStoredPokemon()) {
                Pokemon pokemon = pokemonData.toPokemon();
                if (pokemon != null) {
                    addPokemonToStorage(pokemon);
                }
            }
        }
    }

    /**
     * Initializes the player's position and related variables.
     */
    private void initPosition(int startTileX, int startTileY) {
        synchronized (movementLock) {
            this.tileX = startTileX;
            this.tileY = startTileY;
            this.x = startTileX * TILE_SIZE;
            this.y = startTileY * TILE_SIZE;
            this.targetTileX = startTileX;
            this.targetTileY = startTileY;
            this.position.set(startTileX * TILE_SIZE, startTileY * TILE_SIZE);
            this.targetPosition.set(position);
            updateBoundingBox();
        }
    }

    /**
     * Initializes the font used for rendering the username.
     */
    private void initFont() {
        this.font = new BitmapFont(Gdx.files.internal("Skins/default.fnt"));
        font.getData().setScale(0.8f);
        font.setColor(Color.WHITE);
    }

    /**
     * Loads the player's saved state from persistent storage.
     */
    private void loadSavedState() {
        this.playerData = new PlayerData(username);
        if (world.getWorldData() != null) {
            PlayerData savedData = world.getWorldData().getPlayerData(username);
            if (savedData != null) {
                loadFromSavedData(savedData);
                GameLogger.info("Loaded saved state for player: " + username);
            }
        }
    }

    public float getX() {
        return x;
    }

    public void setX(float x) {
        this.x = x;
    }

    public float getY() {
        return y;
    }

    public void setY(float y) {
        this.y = y;
    }

    private boolean isValidMove(int newTileX, int newTileY) {
        // Check world bounds
        if (newTileX < MIN_TILE_BOUND || newTileX > MAX_TILE_BOUND || newTileY < MIN_TILE_BOUND || newTileY > MAX_TILE_BOUND) {
            return false;
        }

        // Check if move is only one tile at a time
        int dx = Math.abs(newTileX - tileX);
        int dy = Math.abs(newTileY - tileY);
        if (dx > 1 || dy > 1) {
            return false;
        }

        return world.isPassable(newTileX, newTileY);
    }

    private boolean canMoveToPosition(int targetX, int targetY) {
        // World boundary check
        if (targetX < MIN_TILE_BOUND || targetX > MAX_TILE_BOUND || targetY < MIN_TILE_BOUND || targetY > MAX_TILE_BOUND) {
            return false;
        }

        // World tile passability check
        if (!world.isPassable(targetX, targetY)) {
            return false;
        }

        // Get nearby objects for collision checking
        float pixelX = targetX * World.TILE_SIZE;
        float pixelY = targetY * World.TILE_SIZE;

        List<WorldObject> nearbyObjects = world.getObjectManager().getObjectsNearPosition(pixelX, pixelY);

        // Check collisions with objects using the nextPositionBox
        for (WorldObject obj : nearbyObjects) {
            if (obj.getType() == WorldObject.ObjectType.TREE || obj.getType() == WorldObject.ObjectType.HAUNTED_TREE || obj.getType() == WorldObject.ObjectType.SNOW_TREE) {

                Rectangle objBounds = obj.getBoundingBox();
                // Use smaller collision area for trees
                objBounds.x += 4f;
                objBounds.width -= 8f;
                objBounds.height -= 4f; // Reduce height collision slightly

                if (nextPositionBox.overlaps(objBounds)) {
                    return false;
                }
            } else if (obj.getType() == WorldObject.ObjectType.POKEBALL) {
                Rectangle objBounds = obj.getBoundingBox();
                // Smaller collision for pokeballs
                objBounds.x += 8f;
                objBounds.y += 8f;
                objBounds.width -= 16f;
                objBounds.height -= 16f;

                if (nextPositionBox.overlaps(objBounds)) {
                    return false;
                }
            }
        }

        return true;
    }

    private void updateNextPositionBox(float nextX, float nextY) {
        float boxWidth = nextPositionBox.width;
        float boxHeight = nextPositionBox.height;

        nextPositionBox.setPosition(nextX + (FRAME_WIDTH - boxWidth) / 2, nextY + COLLISION_BUFFER);
    }  // Update Player.java update method:

    public void update(float deltaTime) {
        synchronized (movementLock) {
            if (isMoving) {
                float speed = isRunning ? RUN_SPEED_MULTIPLIER : 1.0f;
                movementProgress += (deltaTime / TILE_TRANSITION_TIME) * speed;

                if (movementProgress >= 1.0f) {
                    completeMovement();
                } else {
                    updatePosition(movementProgress);
                }
            }

            // Update animation
            stateTime += deltaTime;
            currentFrame = animations.getCurrentFrame(direction, isMoving, isRunning, stateTime);
        }
    }

    private void updateMovementVector(String direction) {
        movementVector.setZero();
        switch (direction) {
            case "up":
                movementVector.y = 1;
                break;
            case "down":
                movementVector.y = -1;
                break;
            case "left":
                movementVector.x = -1;
                break;
            case "right":
                movementVector.x = 1;
                break;
        }
    }

    // Modify the move method for more responsive controls

    private void updatePosition(float progress) {
        float smoothProgress = smoothstep(progress);

        x = MathUtils.lerp(startPosition.x, targetPosition.x, smoothProgress);
        y = MathUtils.lerp(startPosition.y, targetPosition.y, smoothProgress);

        renderPosition.set(x, y);
        position.set(x, y);

        updateCollisionBoxes();
    }

    public PlayerData getCurrentState() {
        PlayerData state = new PlayerData(username);

        // Convert current pixel position to tile coordinates
        int currentTileX = (int) Math.floor(x / World.TILE_SIZE);
        int currentTileY = (int) Math.floor(y / World.TILE_SIZE);

        state.setX(currentTileX);
        state.setY(currentTileY);
        state.setDirection(direction);
        state.setMoving(false); // Don't save movement state
        state.setWantsToRun(isRunning);

        return state;
    }

    private void updateCollisionBoxes() {
        // Position collision boxes in pixel coordinates
        collisionBox.setPosition(x + (FRAME_WIDTH - collisionBox.width) / 2f, y + COLLISION_BUFFER);

        nextPositionBox.setPosition(targetPosition.x + (FRAME_WIDTH - nextPositionBox.width) / 2f, targetPosition.y + COLLISION_BUFFER);
    }

    private void updateCollisionBox() {
        collisionBox.setPosition(x + (FRAME_WIDTH - COLLISION_BOX_WIDTH) / 2, y + COLLISION_BOX_Y_OFFSET);
    }

    private void completeMovement() {
        x = targetPosition.x;
        y = targetPosition.y;
        tileX = targetTileX;
        tileY = targetTileY;
        position.set(x, y);
        renderPosition.set(x, y);

        isMoving = false;

        // Process queued movement
        if (queuedDirection != null) {
            String nextDirection = queuedDirection;
            queuedDirection = null;
            move(nextDirection);
        }
    }

    private float smoothstep(float x) {
        x = MathUtils.clamp(x, 0f, 1f);
        return x * x * (3 - 2 * x);
    }

    /**
     * Completes the current movement, aligning the player with the grid and processing queued movements.
     */

    private void handleCollision(String direction) {
        // Set the facing direction
        this.direction = direction;
        this.isMoving = false;
    }

    public void move(String newDirection) {
        synchronized (movementLock) {
            if (isMoving) {
                // Do not queue the movement; ignore input if already moving
                return;
            }

            // Update facing direction
            direction = newDirection;

            // Calculate new target position
            int newTileX = tileX;
            int newTileY = tileY;

            switch (newDirection) {
                case "up":
                    newTileY += 1;
                    break;
                case "down":
                    newTileY -= 1;
                    break;
                case "left":
                    newTileX -= 1;
                    break;
                case "right":
                    newTileX += 1;
                    break;
                default:
                    return;
            }

            // Check if movement is possible
            if (world.isPassable(newTileX, newTileY)) {
                // Set target position
                targetTileX = newTileX;
                targetTileY = newTileY;
                targetPosition.set(tileToPixelX(newTileX), tileToPixelY(newTileY));

                // Store starting position
                startPosition.set(x, y);

                isMoving = true;
                movementProgress = 0f;
            }

        }

    }

    public void render(SpriteBatch batch) {
        if (currentFrame != null) {
            batch.draw(currentFrame, renderPosition.x, renderPosition.y, FRAME_WIDTH, FRAME_HEIGHT);
        }

        if (username != null && !username.isEmpty() && font != null && !username.equals("Player") && !username.equals("ThumbnailPlayer")) {
            font.draw(batch, username, renderPosition.x + (FRAME_WIDTH / 2f), renderPosition.y + FRAME_HEIGHT + 15);
        }
    }

    // Enhanced smooth stepping function for better movement

    public int getTileX() {
        return pixelToTileX(x);
    }

    public int getTileY() {
        return pixelToTileY(y);
    }

    public void setTileY(int tileY) {
        this.tileY = tileY;
    }

    public float getPixelX() {
        return x;
    }

    public float getPixelY() {
        return y;
    }

    /**
     * Linearly interpolates between two values.
     *
     * @param start Start value.
     * @param end   End value.
     * @param t     Interpolation factor between 0 and 1.
     * @return Interpolated value.
     */
    private float lerp(float start, float end, float t) {
        return start + (end - start) * t;
    }

    /**
     * Updates the player's bounding box for collision detection or other purposes.
     */
    private void updateBoundingBox() {
        boundingBox.setPosition(position.x + COLLISION_BUFFER / 2, position.y + COLLISION_BUFFER / 2);
    }

    /**
     * Checks for collisions with world objects at the target bounds.
     *
     * @param targetBounds The bounding box of the target position.
     * @return True if a collision is detected, false otherwise.
     */
    private boolean checkObjectCollisions(Rectangle targetBounds) {
        List<WorldObject> nearbyObjects = world.getObjectManager().getObjectsNearPosition(targetBounds.x, targetBounds.y);
        for (WorldObject obj : nearbyObjects) {
            if (obj.getBoundingBox().overlaps(targetBounds)) {
                GameLogger.info("Collision detected with object at (" + obj.getPixelX() + ", " + obj.getPixelY() + ")");
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if movement to the target tile is possible.
     */
    private boolean canMoveToTile(int targetX, int targetY) {
        // World bounds check
        if (targetX < 0 || targetY < 0 || targetX >= World.WORLD_SIZE || targetY >= World.WORLD_SIZE) {
            return false;
        }

        // Create target bounds for collision checking
        Rectangle targetBounds = new Rectangle(targetX * TILE_SIZE + COLLISION_BUFFER, targetY * TILE_SIZE + COLLISION_BUFFER, FRAME_WIDTH - (COLLISION_BUFFER * 2), FRAME_HEIGHT - (COLLISION_BUFFER * 2));

        // Check world collision
        if (!world.isPassable(targetX, targetY)) {
            return false;
        }

        // Check object collisions
        return !checkObjectCollisions(targetBounds);
    }

    /**
     * Attempts to place a block at the specified tile coordinates.
     *
     * @param tileX X-coordinate of the tile.
     * @param tileY Y-coordinate of the tile.
     * @param world The game world.
     */
// Add to Player.java
    public void tryPlaceBlock(int tileX, int tileY, World world) {
        if (!buildMode) {
            return;
        }

        // Get the currently selected item from build inventory
        ItemData selectedItem = buildInventory.getItemAt(buildModeUI.getSelectedSlot());
        if (selectedItem == null) {
            return;
        }

        // Convert item to block type
        PlaceableBlock.BlockType blockType = null;
        for (PlaceableBlock.BlockType type : PlaceableBlock.BlockType.values()) {
            if (type.getId().equals(selectedItem.getItemId())) {
                blockType = type;
                break;
            }
        }

        if (blockType == null) {
            return;
        }

        // Check if the block can be placed
        if (world.getBlockManager().placeBlock(blockType, tileX, tileY, world)) {
            // Consume one block if placement was successful
            buildModeUI.consumeBlock();
            GameLogger.info("Placed " + blockType.getId() + " at " + tileX + "," + tileY);
        }
    }

    /**
     * Retrieves the block type from the given item.
     *
     * @param item The item to convert.
     * @return The corresponding block type, or null if none matches.
     */
    private PlaceableBlock.BlockType getBlockTypeFromItem(Item item) {
        switch (item.getName()) {
            case "CraftingTable":
                return PlaceableBlock.BlockType.CRAFTING_TABLE;
            // Add more block types as needed
            default:
                return null;
        }
    }

    /**
     * Determines if the player can move to the specified tile.
     *
     * @param targetX X-coordinate of the target tile.
     * @param targetY Y-coordinate of the target tile.
     * @return True if movement is possible, false otherwise.
     */

    /**
     * Selects a block item from the build inventory.
     *
     * @param slot The inventory slot to select.
     */
    public void selectBlockItem(int slot) {
        if (!buildMode) return;

        ItemData itemData = buildInventory.getItemAt(slot);
        if (itemData != null) {
            heldBlock = ItemManager.getItem(itemData.getItemId());
            heldBlock.setCount(itemData.getCount());
        } else {
            heldBlock = null;
        }
    }

    /**
     * Attempts to pick up an item based on its position.
     *
     * @param itemX X-coordinate of the item.
     * @param itemY Y-coordinate of the item.
     * @return True if the player can pick up the item, false otherwise.
     */
    public boolean canPickupItem(float itemX, float itemY) {
        // Calculate centers of player and item
        float playerCenterX = x + (FRAME_WIDTH / 2f);
        float playerCenterY = y + (FRAME_HEIGHT / 2f);
        float itemCenterX = itemX + (TILE_SIZE / 2f);
        float itemCenterY = itemY + (TILE_SIZE / 2f);

        // Calculate distances
        float dx = itemCenterX - playerCenterX;
        float dy = itemCenterY - playerCenterY;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);

        // Check if item is in front of player based on facing direction
        boolean inCorrectDirection = false;
        switch (direction) {
            case "up":
                inCorrectDirection = dy > 0 && Math.abs(dx) < TILE_SIZE;
                break;
            case "down":
                inCorrectDirection = dy < 0 && Math.abs(dx) < TILE_SIZE;
                break;
            case "left":
                inCorrectDirection = dx < 0 && Math.abs(dy) < TILE_SIZE;
                break;
            case "right":
                inCorrectDirection = dx > 0 && Math.abs(dy) < TILE_SIZE;
                break;
        }

        boolean canPickup = distance <= INTERACTION_RANGE && inCorrectDirection;
        if (canPickup) {
            GameLogger.info("Can pickup item at distance: " + distance + " in direction: " + direction);
        }
        return canPickup;
    }

    /**
     * Checks if the item's position matches the player's current direction.
     *
     * @param itemX X-coordinate of the item.
     * @param itemY Y-coordinate of the item.
     * @return True if the direction matches, false otherwise.
     */
    private boolean directionMatchesPosition(float itemX, float itemY) {
        switch (direction) {
            case "up":
                return itemY > y;
            case "down":
                return itemY < y;
            case "left":
                return itemX < x;
            case "right":
                return itemX > x;
            default:
                return false;
        }
    }

    /**
     * Updates the PlayerData object with the current state of the player.
     */
    public void updatePlayerData() {
        playerData.setX(x);
        playerData.setY(y);
        playerData.setDirection(direction);
        playerData.setMoving(isMoving);
        playerData.setWantsToRun(isRunning);
        // Add any additional fields as necessary
    }

    /**
     * Disposes of resources used by the player.
     */
    public void dispose() {
        if (font != null) font.dispose();
        animations.dispose();
        if (currentFrame != null && currentFrame.getTexture() != null) {
            currentFrame.getTexture().dispose();
        }
    }

    /**
     * Creates a placeholder texture frame in case animation frames are missing.
     *
     * @return A TextureRegion representing the placeholder frame.
     */
    private TextureRegion createPlaceholderFrame() {
        Pixmap pixmap = new Pixmap(FRAME_WIDTH, FRAME_HEIGHT, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.MAGENTA);  // Visible color for missing frames
        pixmap.fill();
        Texture placeholderTexture = new Texture(pixmap);
        pixmap.dispose();
        return new TextureRegion(placeholderTexture);
    }

    public Vector2 getPosition() {
        return new Vector2(position);
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    // Getters and Setters

    public boolean isMoving() {
        return isMoving;
    }

    public void setMoving(boolean moving) {
        isMoving = moving;
    }

    public boolean isRunning() {
        return isRunning;
    }

    /**
     * Sets whether the player is running (speed multiplier).
     *
     * @param running True to enable running, false to disable.
     */
    public void setRunning(boolean running) {
        this.isRunning = running;
    }

    public void setRunningFlag(boolean running) {
        isRunningFlag = running;
    }

    public boolean isBuildMode() {
        return buildMode;
    }

    // Update setBuildMode method to handle UI
    public void setBuildMode(boolean buildMode) {
        this.buildMode = buildMode;
        if (buildModeUI != null) {
            if (buildMode) {
                buildModeUI.show();
                // Initialize build inventory if needed
                if (buildInventory.isEmpty()) {
                    initializeBuildInventory();
                }
            } else {
                buildModeUI.hide();
            }
        }
    }

    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public Inventory getBuildInventory() {
        return buildInventory;
    }

    public void setBuildInventory(Inventory buildInventory) {
        this.buildInventory = buildInventory;
    }

    public PokemonParty getPokemonParty() {
        return pokemonParty;
    }

    public void setPokemonParty(PokemonParty pokemonParty) {
        this.pokemonParty = pokemonParty;
    }

    public String getUsername() {
        return username;
    }

    public World getWorld() {
        return world;
    }

    // Pokemon Management

    public void setWorld(World world) {
        synchronized (movementLock) {
            this.world = world;
        }
    }

    public PokemonSpawnManager getSpawnManager() {
        return spawnManager;
    }

    public PlayerData getPlayerData() {
        return playerData;
    }

    /**
     * Adds a Pokémon to the player's party.
     *
     * @param pokemon The Pokémon to add.
     */
    public void addPokemonToParty(Pokemon pokemon) {
        if (pokemonParty.addPokemon(pokemon)) {
            ownedPokemon.put(pokemon.getUuid(), pokemon);
        }
    }

    public void setMoveTimer(float moveTimer) {
        this.moveTimer = moveTimer;
    }

    public void setMovingFlag(boolean movingFlag) {
        isMovingFlag = movingFlag;
    }

    /**
     * Adds a Pokémon to the player's storage.
     *
     * @param pokemon The Pokémon to add.
     */
    public void addPokemonToStorage(Pokemon pokemon) {
        ownedPokemon.put(pokemon.getUuid(), pokemon);
    }
}

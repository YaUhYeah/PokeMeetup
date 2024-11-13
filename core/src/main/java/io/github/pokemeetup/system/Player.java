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
import io.github.pokemeetup.blocks.PlaceableBlock;
import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.pokemon.Pokemon;
import io.github.pokemeetup.pokemon.PokemonParty;
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
import io.github.pokemeetup.utils.GameLogger;
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
    private static final float TILE_TRANSITION_TIME = 0.2f; // Time to move one tile
    private static final float COLLISION_BOX_WIDTH = 20f;  // Smaller than FRAME_WIDTH
    private static final float COLLISION_BOX_HEIGHT = 16f; // Smaller than FRAME_HEIGHT
    private static final float RUN_SPEED_MULTIPLIER = 1.75f; // More noticeable run
    private static final float PICKUP_RANGE = 43f;
    private static final float MOVE_SPEED = TILE_SIZE / TILE_TRANSITION_TIME; // Units per second
    private static final float MOVEMENT_SMOOTHING = 0.25f; // Time for smooth movement
    private static final float COLLISION_BUFFER = 4f; // Increased for better collision detection
    private static final float ACCELERATION = 800f; // Pixels per second squared
    private static final float MAX_SPEED = 200f;    // Maximum speed
    private static final long VALIDATION_INTERVAL = 1000; // ms
    private static final float DIAGONAL_MOVE_TIME = 0.15f; // Time window for diagonal input
    private static final float INPUT_BUFFER_TIME = 0.1f; // Time to buffer next move
    // Locks for Thread Safety
    private final Object movementLock = new Object();
    private final Object resourceLock = new Object();
    private PlayerAnimations animations;
    private String username;
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
    private BitmapFont font;
    // Player Datax
    private PlayerData playerData;
    private float movementProgress;
    private String queuedDirection;
    private boolean isInterpolating;
    private BuildModeUI buildModeUI;
    private GameClient gameClient;
    private boolean resourcesInitialized = false;
    private long lastValidationTime = 0;
    private String lastDirection = "down";
    private volatile boolean disposed = false;
    // Add these fields
    private float diagonalMoveTimer = 0f;

    // Constructor with default username
    public Player(int startTileX, int startTileY, World world) {
        this(startTileX, startTileY, world, "Player");
        this.playerData = new PlayerData("Player");
    }




    public Player(String username, World world) {
        this(0, 0, world, username); // Pass the world object correctly
        GameLogger.info("Creating new player: " + username);
        this.animations = new PlayerAnimations();
        this.world = world;
        this.position = new Vector2(0, 0);
        this.targetPosition = new Vector2(0, 0);
        this.renderPosition = new Vector2(0, 0);
        this.lastPosition = new Vector2(0, 0);
        this.currentPosition = new Vector2(0, 0);
        this.startPosition = new Vector2(0, 0);

        // Initialize collision boxes
        float boxWidth = FRAME_WIDTH * COLLISION_BOX_WIDTH_RATIO;
        float boxHeight = FRAME_HEIGHT * COLLISION_BOX_HEIGHT_RATIO;
        this.collisionBox = new Rectangle(0, 0, boxWidth, boxHeight);
        this.nextPositionBox = new Rectangle(0, 0, boxWidth, boxHeight);

        // Initialize components
        this.direction = "down";
        this.inventory = new Inventory();
        this.buildInventory = new Inventory();
        this.pokemonParty = new PokemonParty();
        this.ownedPokemon = new HashMap<>();
        this.playerData = new PlayerData(username);

        initFont();
        initializeBuildInventory();

        Gdx.app.postRunnable(this::initializeGraphics);
        GameLogger.info("Player initialized: " + username + " at (0,0)");
    }
    private void initializeGraphics() {
        this.animations = new PlayerAnimations();
        initFont();
    }

    private void initFont() {
        this.font = new BitmapFont(Gdx.files.internal("Skins/default.fnt"));
        font.getData().setScale(0.8f);
        font.setColor(Color.WHITE);
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

    }

    public void initializeInWorld(World world) {
        if (world == null) {
            GameLogger.error("Cannot initialize player in null world");
            return;
        }

        this.world = world;

        // Only initialize spawn manager reference, don't reset state
        this.spawnManager = world.getPokemonSpawnManager();

        // Update collision boxes without resetting position
        updateCollisionBoxes();

        GameLogger.info("Player initialized in world: " + username);
    }
    public void updateFromPlayerData(PlayerData data) {
        if (data == null) {
            GameLogger.error("Cannot update from null PlayerData");
            return;
        }

        // Update position
        this.x = data.getX();
        this.y = data.getY();
        this.direction = data.getDirection();
        this.isMoving = data.isMoving();
        this.isRunning = data.isWantsToRun();

        // Update Pokemon party
        if (data.getPartyPokemon() != null && !data.getPartyPokemon().isEmpty()) {
            if (this.pokemonParty == null) {
                this.pokemonParty = new PokemonParty();
            }

            for (PokemonData pokemonData : data.getPartyPokemon()) {
                Pokemon pokemon = pokemonData.toPokemon();
                if (pokemon != null) {
                    this.pokemonParty.addPokemon(pokemon);
                }
            }
            GameLogger.info("Loaded " + this.pokemonParty.getSize() + " Pokemon from save data");
        }

        // Update inventory
        if (data.getInventoryItems() != null) {
            InventoryConverter.applyInventoryDataToPlayer(data, this);
        }
    }

    private void initializeBuildInventory() {
        // Add default blocks to build inventory
        for (PlaceableBlock.BlockType blockType : PlaceableBlock.BlockType.values()) {
            ItemData blockItem = new ItemData(blockType.getId(), 64); // Give a stack of blocks
            buildInventory.addItem(blockItem);
        }
    }

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

    }

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


        GameLogger.info(String.format("Loaded player state at tile (%d, %d)", tileX, tileY));
    }

    private void initializeFromSavedState() {
        if (world != null && world.getWorldData() != null) {
            PlayerData savedData = world.getWorldData().getPlayerData(username);
            if (savedData != null) {
                playerData.applyToPlayer(this);
                GameLogger.info("Loaded saved state for player: " + username);
            } else {
                this.playerData = new PlayerData(username);
                GameLogger.info("Created new player data for: " + username);
            }
        }
    }

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

    public void update(float deltaTime) {
        if (!resourcesInitialized || disposed || animations == null || animations.isDisposed()) {
            initializeResources();
        }

        synchronized (movementLock) {
            // Update timers
            if (diagonalMoveTimer > 0) {
                diagonalMoveTimer -= deltaTime;
            }
            if (inputBufferTimer > 0) {
                inputBufferTimer -= deltaTime;
                if (inputBufferTimer <= 0 && bufferedDirection != null) {
                    move(bufferedDirection);
                    bufferedDirection = null;
                }
            }

            // Handle movement
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


    private void updatePosition(float progress) {
        float smoothProgress = smoothstep(progress);


        x = MathUtils.lerp(startPosition.x, targetPosition.x, smoothProgress);
        y = MathUtils.lerp(startPosition.y, targetPosition.y, smoothProgress);

        position.set(x, y);
        renderPosition.set(x, y);


        updateCollisionBoxes();
    }

    private void updateCollisionBoxes() {
        // Position collision boxes in pixel coordinates
        collisionBox.setPosition(x + (FRAME_WIDTH - collisionBox.width) / 2f, y + COLLISION_BUFFER);

        nextPositionBox.setPosition(targetPosition.x + (FRAME_WIDTH - nextPositionBox.width) / 2f, targetPosition.y + COLLISION_BUFFER);
    }

    private float smoothstep(float x) {
        x = MathUtils.clamp(x, 0f, 1f);
        return x * x * (3 - 2 * x);
    }

    public void move(String newDirection) {
        synchronized (movementLock) {
            if (isMoving) {
//                GameLogger.error("Already moving, buffering direction: " + newDirection);
                if (movementProgress > 0.7f) {
                    bufferedDirection = newDirection;
                    inputBufferTimer = INPUT_BUFFER_TIME;
                }
                return;
            }

            direction = newDirection;   // Add null check and error logging
            if (world == null) {
                GameLogger.error("Cannot move - world is null! Player: " + username);
                return;
            }

            int newTileX = getTileX();
            int newTileY = getTileY();

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

            if (world != null && world.isPassable(newTileX, newTileY)) {
                // Start movement
                targetTileX = newTileX;
                targetTileY = newTileY;
                targetPosition.set(tileToPixelX(newTileX), tileToPixelY(newTileY));
                startPosition.set(x, y);
                lastPosition.set(x, y);  // Store last position
                isMoving = true;
                movementProgress = 0f;


            }
        }
    }

    private void completeMovement() {
        x = targetPosition.x;
        y = targetPosition.y;
        tileX = targetTileX;
        tileY = targetTileY;
        position.set(x, y);
        renderPosition.set(x, y);

        isMoving = false;
        movementProgress = 0f;

        // Handle buffered input
        if (bufferedDirection != null) {
            String nextDirection = bufferedDirection;
            bufferedDirection = null;
            move(nextDirection);
        }
    }

    public void render(SpriteBatch batch) {
        synchronized (resourceLock) {
            if (!resourcesInitialized || disposed || animations == null || animations.isDisposed()) {
                initializeResources();
            }
            if (currentFrame != null) {
                batch.draw(currentFrame, renderPosition.x, renderPosition.y, FRAME_WIDTH, FRAME_HEIGHT);
            }

            if (username != null && !username.isEmpty() && font != null && !username.equals("Player") && !username.equals("ThumbnailPlayer")) {
                font.draw(batch, username, renderPosition.x - (float) FRAME_WIDTH / 2, renderPosition.y + FRAME_HEIGHT + 15);
            }
        }
    }

    public int getTileX() {
        return pixelToTileX(x);
    }

    public int getTileY() {
        return pixelToTileY(y);
    }
    private final Object inventoryLock = new Object();

    public void setTileY(int tileY) {
        this.tileY = tileY;
    }


    public void tryPlaceBlock(int tileX, int tileY, World world) {
        if (!buildMode) {
            return;
        }
        ItemData selectedItem = buildInventory.getItemAt(buildModeUI.getSelectedSlot());
        if (selectedItem == null) {
            return;
        }
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
        if (world.getBlockManager().placeBlock(blockType, tileX, tileY, world)) {
            buildModeUI.consumeBlock();
            GameLogger.info("Placed " + blockType.getId() + " at " + tileX + "," + tileY);
        }

    }

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

    public boolean canPickupItem(float itemX, float itemY) {
        float playerCenterX = x + (FRAME_WIDTH / 2f);
        float playerCenterY = y + (FRAME_HEIGHT / 2f);
        float itemCenterX = itemX + (TILE_SIZE / 2f);
        float itemCenterY = itemY + (TILE_SIZE / 2f);
        float dx = itemCenterX - playerCenterX;
        float dy = itemCenterY - playerCenterY;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
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

    public void updatePlayerData() {
        playerData.setX(x);
        playerData.setY(y);
        playerData.setDirection(direction);
        playerData.setMoving(isMoving);
        playerData.setWantsToRun(isRunning);
        playerData.setInventoryItems(inventory.getAllItems());

        // Create a fixed-size list for party Pokemon
        List<PokemonData> partyData = new ArrayList<>(Collections.nCopies(PokemonParty.MAX_PARTY_SIZE, null));

        synchronized (pokemonParty.partyLock) {  // Use the party's lock for thread safety
            List<Pokemon> currentParty = pokemonParty.getParty();

            // Log the current party state
            GameLogger.info("Converting party of size " + currentParty.size() + " to PokemonData");

            // Convert each Pokemon to PokemonData while maintaining slot positions
            for (int i = 0; i < PokemonParty.MAX_PARTY_SIZE; i++) {
                Pokemon pokemon = i < currentParty.size() ? currentParty.get(i) : null;
                if (pokemon != null) {
                    try {
                        PokemonData pokemonData = PokemonData.fromPokemon(pokemon);
                        if (pokemonData.verifyIntegrity()) {
                            partyData.set(i, pokemonData);
                            GameLogger.info("Added Pokemon to slot " + i + ": " + pokemon.getName());
                        } else {
                            GameLogger.error("Pokemon data failed integrity check at slot " + i);
                            partyData.set(i, null);
                        }
                    } catch (Exception e) {
                        GameLogger.error("Failed to convert Pokemon at slot " + i + ": " + e.getMessage());
                        partyData.set(i, null);
                    }
                }
            }
        }

        // Verify the party data before setting
        boolean hasValidPokemon = partyData.stream().anyMatch(Objects::nonNull);
        if (!hasValidPokemon) {
            GameLogger.error("No valid Pokemon found in party data!");
        }

        // Set the verified party data
        playerData.setPartyPokemon(partyData);

        // Log final state
        GameLogger.info("Updated player data with " +
            partyData.stream().filter(Objects::nonNull).count() + " Pokemon in party");
    }


    public void initializeResources() {
        synchronized (resourceLock) {
            try {
                if (resourcesInitialized && !disposed && animations != null && !animations.isDisposed()) {
                    return;
                }

                GameLogger.info("Initializing player resources");

                // Create new animations only if needed
                if (animations == null || animations.isDisposed()) {
                    animations = new PlayerAnimations();
                    GameLogger.info("Created new PlayerAnimations");
                }

                // Always get a fresh frame
                currentFrame = animations.getStandingFrame("down");
                if (currentFrame == null) {
                    throw new RuntimeException("Failed to get initial frame");
                }

                resourcesInitialized = true;
                disposed = false;
                GameLogger.info("Player resources initialized successfully");

            } catch (Exception e) {
                GameLogger.error("Failed to initialize player resources: " + e.getMessage());
                resourcesInitialized = false;
                disposed = true;
                throw new RuntimeException("Resource initialization failed", e);
            }
        }
    }


    public void validateResources() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastValidationTime > VALIDATION_INTERVAL) {
            synchronized (resourceLock) {
                if (!resourcesInitialized || disposed || animations == null || animations.isDisposed()) {
                    initializeResources();
                }
                lastValidationTime = currentTime;
            }
        }
    }

    public void dispose() {
        synchronized (resourceLock) {
            if (disposed) {
                return;
            }

            try {
                GameLogger.info("Disposing player resources");

                if (animations != null) {
                    animations.dispose();
                    animations = null;
                }

                currentFrame = null;
                resourcesInitialized = false;
                disposed = true;

                GameLogger.info("Player resources disposed successfully");

            } catch (Exception e) {
                GameLogger.error("Error disposing player resources: " + e.getMessage());
            }
        }
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

    public boolean isMoving() {
        return isMoving;
    }

    public void setMoving(boolean moving) {
        isMoving = moving;
    }

    public boolean isRunning() {
        return isRunning;
    }

    public void setRunning(boolean running) {
        this.isRunning = running;
    }

    public boolean isBuildMode() {
        return buildMode;
    }

    public void setBuildMode(boolean buildMode) {
        this.buildMode = buildMode;
        if (buildModeUI != null) {
            if (buildMode) {
                buildModeUI.show();
                if (buildInventory.isEmpty()) {
                    initializeBuildInventory();
                }
            } else {
                buildModeUI.hide();
            }
        }
    }   public Inventory getInventory() {
        synchronized (inventoryLock) {
            if (inventory == null) {
                GameLogger.error("Player inventory is null - creating new");
                inventory = new Inventory();
            }
            return inventory;
        }
    }    public void setInventory(Inventory inv) {
        synchronized (inventoryLock) {
            if (inv == null) {
                GameLogger.error("Attempt to set null inventory");
                return;
            }

            // Copy items from old inventory if it exists
            if (this.inventory != null) {
                List<ItemData> oldItems = this.inventory.getAllItems();
                for (ItemData item : oldItems) {
                    if (item != null) {
                        inv.addItem(item.copy());
                    }
                }
            }

            this.inventory = inv;
            GameLogger.info("Set player inventory with " +
                inv.getAllItems().stream().filter(Objects::nonNull).count() + " items");
        }
    }


    public Inventory getBuildInventory() {
        return buildInventory;
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

    public void setUsername(String username) {
        this.username = username;
    }

    // Pokemon Management

    public World getWorld() {
        return world;
    }

    public void setWorld(World world) {
        synchronized (movementLock) {
            this.world = world;
        }
    }

    public void setGameClient(GameClient gameClient) {
        this.gameClient = gameClient;
    }

    public PlayerData getPlayerData() {
        return playerData;
    }

}

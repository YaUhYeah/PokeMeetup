package io.github.pokemeetup.system;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Json;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.inventory.Inventory;

public class Player {
    public static final int FRAME_WIDTH = 32;
    public static final int FRAME_HEIGHT = 48;
    // Movement timing
    private static final float SPEED_TRANSITION_TIME = 0.2f; // Increased for smoother transitions
    private static final float MOVE_TIME = 0.25f;  // Base movement time
    private static final float RUN_SPEED_MULTIPLIER = 1.8f;
    // Animation frame durations (time each frame is shown)
    private static final float WALK_FRAME_TIME = MOVE_TIME / 4;  // Show all 4 frames during one movement
    private static final float RUN_FRAME_TIME = (MOVE_TIME / RUN_SPEED_MULTIPLIER) / 4;
    private static final float LERP_ALPHA = 0.2f; // Adjust for smoother/faster interpolation
    private static final String SAVE_FILE = "assets/save/player.json";
    private static final float PICKUP_RANGE = 32f;
    // New fields for username rendering
    private final String username;
    private final World world; // Reference to the game world
    private float currentSpeedMultiplier = 1.0f;
    private float speedTransitionTimer = 0f;
    private Texture placeholderTexture;
    private int tileX, tileY; // Current tile position
    private int targetTileX, targetTileY; // Target tile position
    private float x, y; // Pixel position for smooth rendering
    private String direction;
    private boolean isMoving;
    private boolean wantsToRun;
    private float moveTimer;
    private float stateTime;
    private String queuedDirection;
    private Inventory inventory;
    private Animation<TextureRegion> walkUpAnimation;
    private Animation<TextureRegion> walkDownAnimation;
    private Animation<TextureRegion> walkLeftAnimation;
    private Animation<TextureRegion> walkRightAnimation;
    private Animation<TextureRegion> runUpAnimation;
    private Animation<TextureRegion> runDownAnimation;
    private Animation<TextureRegion> runLeftAnimation;
    private Animation<TextureRegion> runRightAnimation;
    private TextureRegion currentFrame;
    private TextureRegion[] standingFrames;
    private BitmapFont font;
    private Vector2 targetPosition;
    private Vector2 currentPosition;
    private PlayerData playerData;
    private Vector2 previousPosition;

    public Player(int startTileX, int startTileY, World world, TextureAtlas atlas, String username) {
        this.targetPosition = new Vector2(startTileX, startTileY);
        this.currentPosition = new Vector2(startTileX, startTileY);
        this.previousPosition = new Vector2(startTileX, startTileY);
        this.targetTileX = startTileX;
        this.targetTileY = startTileY;
        this.x = startTileX;
        this.y = startTileY;
        this.tileX = startTileX / World.TILE_SIZE;
        this.tileY = startTileY / World.TILE_SIZE;
        this.direction = "down";
        this.isMoving = false;
        this.wantsToRun = false;
        this.moveTimer = 0;
        this.stateTime = 0;
        this.world = world;
        this.currentSpeedMultiplier = 1f;
        this.inventory = new Inventory();
        this.speedTransitionTimer = 0f;
        this.playerData = new PlayerData(username);
        playerData.updateFromPlayer(this);

        this.username = username; // Initialize username
        font = new BitmapFont(Gdx.files.internal("Skins/default.fnt"));

        font.getData().setScale(0.8f); // Optional: Adjust font size
        font.setColor(Color.WHITE);    // Optional: Set font color
        if (world != null && world.getWorldData() != null) {
            PlayerData savedData = world.getWorldData().getPlayerData(username);
            if (savedData != null) {
                System.out.println("Loading saved player state for: " + username);
                updateFromState(); // Use saved state if available
            } else {
                // Initialize with default values if no saved state
                this.x = startTileX;
                this.y = startTileY;
                this.direction = "down";
                this.isMoving = false;
                this.wantsToRun = false;
                this.inventory = new Inventory();
            }
        }
        initializeAnimations(atlas);
    }



    public void updatePlayerData() {
        if (playerData != null) {
            playerData.updateFromPlayer(this);
        }
    }

    public void setCurrentPosition(Vector2 currentPosition) {
        this.currentPosition = currentPosition;
    }

    public World getWorld() {
        return world;
    }

    public PlayerData getPlayerData() {
        return playerData;
    }

    public void savePlayerData() {
        try {
            Json json = new Json();
            PlayerData data = new PlayerData(username);
            data.updateFromPlayer(this);

            FileHandle file = Gdx.files.local(SAVE_FILE);
            file.writeString(json.toJson(data), false);
        } catch (Exception e) {
            //            System.err.println(STR."Failed to save player data: \{e.getMessage()}");
        }
    }

    public boolean canPickupItem(float itemX, float itemY) {
        // Check if player is facing the item
        float dx = itemX - x;
        float dy = itemY - y;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);

        if (distance > PICKUP_RANGE) {
            return false;
        }

        // Check if item is in the direction player is facing
        switch (direction) {
            case "up":
                return dy > 0;
            case "down":
                return dy < 0;
            case "left":
                return dx < 0;
            case "right":
                return dx > 0;
            default:
                return false;
        }
    }

    public Inventory getInventory() {
        if (inventory == null) {
            inventory = new Inventory();
        }
        return inventory;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
        // Update animation frame if needed
        updateCurrentFrame();
    }

    public boolean isRunning() {
        return wantsToRun;
    }

    public void setRunning(boolean running) {
        if (running != wantsToRun) {
            // Only update if the state is actually changing
            wantsToRun = running;
            // If we're currently moving, preserve the animation progress
            if (isMoving) {
                float progress = moveTimer / (wantsToRun ? MOVE_TIME : (MOVE_TIME / RUN_SPEED_MULTIPLIER));
                moveTimer = progress * (wantsToRun ? (MOVE_TIME / RUN_SPEED_MULTIPLIER) : MOVE_TIME);
            }
            updatePlayerData(); // Add this
        }
    }

    public String getUsername() {
        return username;
    }

    public void render(SpriteBatch batch) {
        batch.draw(currentFrame, x, y, FRAME_WIDTH, FRAME_HEIGHT);
        if (username != null && !username.isEmpty() && font != null && !username.equals("Player")) {
            GlyphLayout layout = new GlyphLayout(font, username);
            float textWidth = layout.width;
            font.draw(batch, username, x + (FRAME_WIDTH - textWidth) / 2, y + FRAME_HEIGHT + 15);

        }
    }



    public void updateFromState() {
   updatePlayerData();
    }

    public void update(float deltaTime) {
        if (isMoving) {
            moveTimer += deltaTime;
            float moveDuration = wantsToRun ? MOVE_TIME / RUN_SPEED_MULTIPLIER : MOVE_TIME;

            if (moveTimer >= moveDuration) {
                // Movement completed
                moveTimer = 0;
                isMoving = false;
                tileX = targetTileX;
                tileY = targetTileY;
                x = tileX * World.TILE_SIZE;
                y = tileY * World.TILE_SIZE;

                previousPosition.set(currentPosition);
                currentPosition.set(x, y);
                targetPosition.set(x, y);

                updatePlayerData(); // Add this
                // Check for queued movement
                if (queuedDirection != null) {
                    move(queuedDirection);
                    queuedDirection = null;
                }
            } else {
                // Smooth movement interpolation
                float progress = moveTimer / moveDuration;
                progress = smoothStep(progress);

                float startX = tileX * World.TILE_SIZE;
                float startY = tileY * World.TILE_SIZE;
                float endX = targetTileX * World.TILE_SIZE;
                float endY = targetTileY * World.TILE_SIZE;

                x = startX + (endX - startX) * progress;
                y = startY + (endY - startY) * progress;

                currentPosition.set(x, y);
                updatePlayerData(); // Add this
            }
        }

        // Interpolate between current and target positions
        if (!currentPosition.epsilonEquals(targetPosition, 0.1f)) {
            currentPosition.lerp(targetPosition, LERP_ALPHA);
            x = currentPosition.x;
            y = currentPosition.y;
            updatePlayerData(); // Add this
        }

        playerData.updateFromPlayer(this);
        updateCurrentFrame();
    }

    private void updateCurrentFrame() {
        Animation<TextureRegion> currentAnimation;

        if (isMoving) {
            if (wantsToRun) {
                switch (direction) {
                    case "up":
                        currentAnimation = runUpAnimation;
                        break;
                    case "down":
                        currentAnimation = runDownAnimation;
                        break;
                    case "left":
                        currentAnimation = runLeftAnimation;
                        break;
                    case "right":
                        currentAnimation = runRightAnimation;
                        break;
                    default:
                        currentAnimation = runDownAnimation;
                }
            } else {
                switch (direction) {
                    case "up":
                        currentAnimation = walkUpAnimation;
                        break;
                    case "down":
                        currentAnimation = walkDownAnimation;
                        break;
                    case "left":
                        currentAnimation = walkLeftAnimation;
                        break;
                    case "right":
                        currentAnimation = walkRightAnimation;
                        break;
                    default:
                        currentAnimation = walkDownAnimation;
                }
            }

            // Use moveTimer directly to ensure animation syncs with movement
            float animationTime = moveTimer * (wantsToRun ? RUN_SPEED_MULTIPLIER : 1.0f);
            currentFrame = currentAnimation.getKeyFrame(animationTime, true);
        } else {
            switch (direction) {
                case "up":
                    currentFrame = standingFrames[0];
                    break;
                case "down":
                    currentFrame = standingFrames[1];
                    break;
                case "left":
                    currentFrame = standingFrames[2];
                    break;
                case "right":
                    currentFrame = standingFrames[3];
                    break;
                default:
                    currentFrame = standingFrames[1];
            }
        }
    }



    private float lerp(float start, float end, float t) {
        return start + (end - start) * t;
    }

    private float smoothStep(float x) {
        // Improved smoothstep function
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public boolean move(String newDirection) {
        if (isMoving) {
            queuedDirection = newDirection;
            return false;
        }

        this.direction = newDirection;
        targetTileX = tileX;
        targetTileY = tileY;

        switch (direction) {
            case "up":
                targetTileY++;
                break;
            case "down":
                targetTileY--;
                break;
            case "left":
                targetTileX--;
                break;
            case "right":
                targetTileX++;
                break;
        }

        // Allow movement into negative coordinates
        if (world.isPassable(targetTileX, targetTileY)) {
            isMoving = true;
            moveTimer = 0;
            updatePlayerData(); // Add this
            return true;
        } else {
            targetTileX = tileX;
            targetTileY = tileY;
            updatePlayerData(); // Add this
            return false;
        }
    }

    private void initializeAnimations(TextureAtlas atlas) {
        if (atlas == null) {
            throw new IllegalArgumentException("TextureAtlas cannot be null");
        }

        // Arrays to store frames
        TextureRegion[] walkDownFrames = new TextureRegion[4];
        TextureRegion[] walkLeftFrames = new TextureRegion[4];
        TextureRegion[] walkRightFrames = new TextureRegion[4];
        TextureRegion[] walkUpFrames = new TextureRegion[4];
        TextureRegion[] runDownFrames = new TextureRegion[4];
        TextureRegion[] runLeftFrames = new TextureRegion[4];
        TextureRegion[] runRightFrames = new TextureRegion[4];
        TextureRegion[] runUpFrames = new TextureRegion[4];

        try {
            // Walking animations - load frames using indices 1-4
            walkDownFrames[0] = atlas.findRegion("boy_walk_down", 1);  // First frame
            walkDownFrames[1] = atlas.findRegion("boy_walk_down", 2);  // Second frame
            walkDownFrames[2] = atlas.findRegion("boy_walk_down", 3);  // Third frame
            walkDownFrames[3] = atlas.findRegion("boy_walk_down", 4);  // Fourth frame

            walkLeftFrames[0] = atlas.findRegion("boy_walk_left", 1);
            walkLeftFrames[1] = atlas.findRegion("boy_walk_left", 2);
            walkLeftFrames[2] = atlas.findRegion("boy_walk_left", 3);
            walkLeftFrames[3] = atlas.findRegion("boy_walk_left", 4);

            walkRightFrames[0] = atlas.findRegion("boy_walk_right", 1);
            walkRightFrames[1] = atlas.findRegion("boy_walk_right", 2);
            walkRightFrames[2] = atlas.findRegion("boy_walk_right", 3);
            walkRightFrames[3] = atlas.findRegion("boy_walk_right", 4);

            walkUpFrames[0] = atlas.findRegion("boy_walk_up", 1);
            walkUpFrames[1] = atlas.findRegion("boy_walk_up", 2);
            walkUpFrames[2] = atlas.findRegion("boy_walk_up", 3);
            walkUpFrames[3] = atlas.findRegion("boy_walk_up", 4);

            // Running animations - load frames using indices 1-4
            runDownFrames[0] = atlas.findRegion("boy_run_down", 1);
            runDownFrames[1] = atlas.findRegion("boy_run_down", 2);
            runDownFrames[2] = atlas.findRegion("boy_run_down", 3);
            runDownFrames[3] = atlas.findRegion("boy_run_down", 4);

            runLeftFrames[0] = atlas.findRegion("boy_run_left", 1);
            runLeftFrames[1] = atlas.findRegion("boy_run_left", 2);
            runLeftFrames[2] = atlas.findRegion("boy_run_left", 3);
            runLeftFrames[3] = atlas.findRegion("boy_run_left", 4);

            runRightFrames[0] = atlas.findRegion("boy_run_right", 1);
            runRightFrames[1] = atlas.findRegion("boy_run_right", 2);
            runRightFrames[2] = atlas.findRegion("boy_run_right", 3);
            runRightFrames[3] = atlas.findRegion("boy_run_right", 4);

            runUpFrames[0] = atlas.findRegion("boy_run_up", 1);
            runUpFrames[1] = atlas.findRegion("boy_run_up", 2);
            runUpFrames[2] = atlas.findRegion("boy_run_up", 3);
            runUpFrames[3] = atlas.findRegion("boy_run_up", 4);

            // Validate that all frames were loaded successfully
            boolean framesValid = validateFrames(walkDownFrames) && validateFrames(walkLeftFrames) &&
                validateFrames(walkRightFrames) && validateFrames(walkUpFrames) &&
                validateFrames(runDownFrames) && validateFrames(runLeftFrames) &&
                validateFrames(runRightFrames) && validateFrames(runUpFrames);

            if (!framesValid) {
                Gdx.app.error("Player", "Some animation frames failed to load. Creating placeholder frames.");
                createPlaceholderFrames(walkDownFrames, walkLeftFrames, walkRightFrames, walkUpFrames,
                    runDownFrames, runLeftFrames, runRightFrames, runUpFrames);
            }

        } catch (Exception e) {
            Gdx.app.error("Player", "Error loading animation frames: " + e.getMessage());
            createPlaceholderFrames(walkDownFrames, walkLeftFrames, walkRightFrames, walkUpFrames,
                runDownFrames, runLeftFrames, runRightFrames, runUpFrames);
        }

        // Create walking animations
        walkDownAnimation = new Animation<>(WALK_FRAME_TIME, walkDownFrames);
        walkLeftAnimation = new Animation<>(WALK_FRAME_TIME, walkLeftFrames);
        walkRightAnimation = new Animation<>(WALK_FRAME_TIME, walkRightFrames);
        walkUpAnimation = new Animation<>(WALK_FRAME_TIME, walkUpFrames);

        // Create running animations
        runDownAnimation = new Animation<>(RUN_FRAME_TIME, runDownFrames);
        runLeftAnimation = new Animation<>(RUN_FRAME_TIME, runLeftFrames);
        runRightAnimation = new Animation<>(RUN_FRAME_TIME, runRightFrames);
        runUpAnimation = new Animation<>(RUN_FRAME_TIME, runUpFrames);

        // Initialize standing frames
        standingFrames = new TextureRegion[4];
        standingFrames[0] = walkUpFrames[0];    // Up
        standingFrames[1] = walkDownFrames[0];  // Down
        standingFrames[2] = walkLeftFrames[0];  // Left
        standingFrames[3] = walkRightFrames[0]; // Right

        currentFrame = standingFrames[1]; // Start facing down
    }

    private boolean validateFrames(TextureRegion[] frames) {
        for (TextureRegion frame : frames) {
            if (frame == null) return false;
        }
        return true;
    }

    public void dispose() {
        // Dispose of placeholder texture if it exists
        if (placeholderTexture != null) {
            placeholderTexture.dispose();
            placeholderTexture = null;
        }

        // Dispose of font
        if (font != null) {
            font.dispose();
            font = null;
        }

        // Clear all references
        walkUpAnimation = null;
        walkDownAnimation = null;
        walkLeftAnimation = null;
        walkRightAnimation = null;
        runUpAnimation = null;
        runDownAnimation = null;
        runLeftAnimation = null;
        runRightAnimation = null;
        currentFrame = null;
        standingFrames = null;
    }

    // Update the createPlaceholderFrames method to store the placeholder texture
    private void createPlaceholderFrames(TextureRegion[]... frameArrays) {
        // Dispose of any existing placeholder texture
        if (placeholderTexture != null) {
            placeholderTexture.dispose();
        }

        // Create a simple colored rectangle as placeholder
        Pixmap pixmap = new Pixmap(32, 48, Pixmap.Format.RGBA8888);
        pixmap.setColor(1, 0, 1, 1); // Magenta color to make missing textures obvious
        pixmap.fill();

        placeholderTexture = new Texture(pixmap);
        TextureRegion placeholderRegion = new TextureRegion(placeholderTexture);

        pixmap.dispose();

        // Fill all frame arrays with the placeholder
        for (TextureRegion[] frames : frameArrays) {
            for (int i = 0; i < frames.length; i++) {
                if (frames[i] == null) {
                    frames[i] = placeholderRegion;
                }
            }
        }
    }

    // Getters and setters
    public int getX() {
        return (int) x;
    }

    public void setX(float x) {
        this.x = x;
    }

    public int getY() {
        return (int) y;
    }

    public void setY(float y) {
        this.y = y;
    }

    public boolean isMoving() {
        return isMoving;
    }

    public void setMoving(boolean moving) {
        isMoving = moving;
    }
}

package io.github.pokemeetup.pokemon;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.pokemon.attacks.Move;
import io.github.pokemeetup.pokemon.data.PokemonDatabase;
import io.github.pokemeetup.screens.GameScreen;
import io.github.pokemeetup.system.gameplay.PokemonAnimations;
import io.github.pokemeetup.system.gameplay.overworld.PokemonSpawnManager;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.entityai.PokemonAI;
import io.github.pokemeetup.utils.GameLogger;

import java.util.Collection;
import java.util.List;
import java.util.Random;

import static io.github.pokemeetup.system.gameplay.PokemonAnimations.IDLE_BOUNCE_DURATION;


public class WildPokemon extends Pokemon {
    private static final float SCALE = 2.0f;
    private static final float TILE_SIZE = 32f;
    private static final float MOVEMENT_DURATION = 0.75f;  // Slower, smoother movement
    private static final float MOVEMENT_SPEED = 32f;     // Pixels per second
    private static final float RENDER_SCALE = 1.5f;
    private static final float COLLISION_SCALE = 0.8f;

    private static final float FRAME_WIDTH = World.TILE_SIZE;
    private static final float FRAME_HEIGHT = World.TILE_SIZE;
    private static final float IDLE_BOUNCE_HEIGHT = 2f;
    private static final float IDLE_SWAY_AMOUNT = 0.5f; // Horizontal sway amount
    private static final float RANDOM_IDLE_INTERVAL = 3f; // Average seconds between idles
    private final PokemonAnimations animations;
    private final float width;
    private final float height;
    private final Rectangle boundingBox;
    // Add to existing fields
    private final Random random = new Random();
    private float pixelX;  // Store exact pixel positions
    private float pixelY;
    private World world;
    // Grid position (in tiles)
    private int gridX;
    private boolean isMoving;
    private Vector2 startPosition;
    private Vector2 targetPosition;
    private float movementProgress;
    private PokemonAI ai;
    private int gridY;
    // Visual position (in pixels)
    private float visualX;
    private float visualY;
    // Movement state
    private String targetDirection;
    private float x;
    private float y;
    private float movementTimer;
    private long spawnTime;
    private String direction;
    private boolean isExpired = false;
    private boolean isAddedToParty = false;
    private boolean isDespawning = false;
    private float elapsedMovementTime = 0f;
    private PokemonDespawnAnimation despawnAnimation;
    private float idleTimer = 0f;
    private float idleAnimationTime = 0;
    private boolean isIdling = false;
    private float currentMoveTime = 0f;
    private boolean isInterpolating = false;
    private float lastUpdateX;
    private float lastUpdateY;

    public WildPokemon(String name, int level, int pixelX, int pixelY, TextureRegion overworldSprite) {
        super(name, level);
        this.pixelX = pixelX;
        this.pixelY = pixelY;
        this.x = pixelX;
        this.y = pixelY;
        this.startPosition = new Vector2(pixelX, pixelY);
        this.targetPosition = new Vector2(pixelX, pixelY);
        this.direction = "down";
        this.animations = new PokemonAnimations(overworldSprite);
        this.width = World.TILE_SIZE * SCALE;
        this.height = World.TILE_SIZE * SCALE;
        float collisionWidth = TILE_SIZE * COLLISION_SCALE;
        float collisionHeight = TILE_SIZE * COLLISION_SCALE;
        this.boundingBox = new Rectangle(
            this.pixelX + (TILE_SIZE - collisionWidth) / 2f,
            this.pixelY + (height - collisionHeight) / 2f,
            collisionWidth,
            collisionHeight
        );
        GameLogger.info(String.format(
            "%s initialized at pixel position (%.1f,%.1f)",
            name, x, y
        ));
        setSpawnTime((long) (System.currentTimeMillis() / 1000f));
        this.ai = new PokemonAI(this);
    }

    public World getWorld() {
        return world;
    }

    public void setWorld(World world) {
        this.world = world;
    }

    public void updateFromNetworkUpdate(NetworkProtocol.PokemonUpdate update) {
        if (update == null) {
            GameLogger.error("Received null PokemonUpdate in updateFromNetworkUpdate");
            return;
        }

        // Update position
        this.x = update.x * World.TILE_SIZE + (World.TILE_SIZE - this.width) / 2f;
        this.y = update.y * World.TILE_SIZE + (World.TILE_SIZE - this.height) / 2f;
        updateBoundingBox();

        // Update level if it has changed
        if (this.getLevel() != update.level) {
            this.setLevel(update.level);
            GameLogger.info(getName() + " leveled up to " + this.getLevel());
        }

        // Update other attributes as needed
        // Assuming NetworkProtocol.PokemonUpdate has fields like currentHp, statusEffects, etc.

        if (update.currentHp != -1) { // Assuming -1 signifies no update
            setCurrentHp(update.currentHp);
            GameLogger.info(getName() + " HP updated to " + update.currentHp);
        }

    }

    private int calculateStat(int baseStat, int level, boolean isHp) {
        int iv = MathUtils.random(31); // Random IVs
        int ev = 0; // Start with 0 EVs

        if (isHp) {
            return ((2 * baseStat + iv + (ev / 4)) * level / 100) + level + 10;
        } else {
            return ((2 * baseStat + iv + (ev / 4)) * level / 100) + 5;
        }
    }

    public boolean isAddedToParty() {
        return isAddedToParty;
    }

    public void setAddedToParty(boolean addedToParty) {
        isAddedToParty = addedToParty;
    }

    public Rectangle getBoundingBox() {
        return boundingBox;
    }

    @Override
    public PokemonAnimations getAnimations() {
        return animations;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    public long getSpawnTime() {
        return spawnTime;
    }

    public void setSpawnTime(long spawnTime) {
        this.spawnTime = spawnTime;
    }

    @Override
    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    @Override
    public boolean isMoving() {
        return isMoving;
    }

    public void setMoving(boolean moving) {
        this.isMoving = moving;
    }

    public Vector2 getTargetPosition() {
        return targetPosition;
    }

    public void setTargetPosition(Vector2 targetPosition) {
        this.targetPosition = targetPosition;
    }

    public Vector2 getStartPosition() {
        return startPosition;
    }

    public void setStartPosition(Vector2 startPosition) {
        this.startPosition = startPosition;
    }

    public float getElapsedMovementTime() {
        return elapsedMovementTime;
    }

    public void setElapsedMovementTime(float elapsedMovementTime) {
        this.elapsedMovementTime = elapsedMovementTime;
    }

    public PokemonDespawnAnimation getDespawnAnimation() {
        return despawnAnimation;
    }

    public void setDespawnAnimation(PokemonDespawnAnimation despawnAnimation) {
        this.despawnAnimation = despawnAnimation;
    }

    // Add collision check method
    public boolean collidesWithPokemon(WildPokemon other) {
        return this != other && boundingBox.overlaps(other.getBoundingBox());
    }

    public void updateBoundingBox() {
        if (boundingBox != null) {
            float collisionWidth = World.TILE_SIZE * COLLISION_SCALE;
            float collisionHeight = World.TILE_SIZE * COLLISION_SCALE;

            // Center the collision box regardless of render size
            boundingBox.setPosition(
                x + (World.TILE_SIZE - collisionWidth) / 2f,
                y + (World.TILE_SIZE - collisionHeight) / 2f
            );
            boundingBox.setSize(collisionWidth, collisionHeight);
        }
    }

    public TextureRegion getCurrentFrame() {
        if (animations != null) {
            return animations.getCurrentFrame(direction, isMoving, Gdx.graphics.getDeltaTime());
        }
        return null;
    }

    private void updateIdleAnimation(float delta) {
        idleTimer += delta;

        // Start new idle animation
        if (!isIdling && idleTimer >= MathUtils.random(2f, 4f)) {
            isIdling = true;
            idleTimer = 0;
            idleAnimationTime = 0;
        }

        // Update current idle animation
        if (isIdling) {
            idleAnimationTime += delta;
            if (idleAnimationTime >= IDLE_BOUNCE_DURATION) {
                isIdling = false;
                idleAnimationTime = 0;
            }
        }
    }

    public void update(float delta, World world) {
        if (world == null) return;

        // Update AI first
        if (ai != null) {
            ai.update(delta, world);
        }

        // Update movement and animations
        if (isMoving) {
            updateMovement(delta);
            isIdling = false;
            idleAnimationTime = 0;
        } else {
            updateIdleAnimation(delta);
        }

        // Update animations
        if (animations != null) {
            animations.update(delta);

            // Sync animation state with movement
            if (isMoving != animations.isMoving()) {
                if (isMoving) {
                    animations.startMoving(direction);
                } else {
                    animations.stopMoving();
                }
            }
        }
    }

    private float calculateSmoothProgress(float progress) {
        // Smooth step interpolation for more natural movement
        // Hermite interpolation: 3x^2 - 2x^3
        return progress * progress * (3 - 2 * progress);
    }

    private void updatePosition(float smoothProgress) {
        // Calculate new position using smooth interpolation
        float newX = MathUtils.lerp(startPosition.x, targetPosition.x, smoothProgress);
        float newY = MathUtils.lerp(startPosition.y, targetPosition.y, smoothProgress);

        // Convert to tile coordinates for collision check
        int newTileX = (int) (newX / World.TILE_SIZE);
        int newTileY = (int) (newY / World.TILE_SIZE);

        if (world != null && world.isPassable(newTileX, newTileY) &&
            !isCollidingWithOtherPokemon(newX, newY, world)) {
            // Update position and collision box
            setX(newX);
            setY(newY);
            updateBoundingBox();
        } else {
            // Movement blocked, complete current movement
            completeMovement();
            if (ai != null) {
                ai.enterIdleState();
            }
        }
    }

    private void completeMovement() {
        isInterpolating = false;
        isMoving = false;
        currentMoveTime = 0f;

        // Ensure final position is exactly on target
        setX(targetPosition.x);
        setY(targetPosition.y);

        // Stop walking animation
        if (animations != null) {
            animations.stopMoving();
        }

        updateBoundingBox();

        GameLogger.error(String.format(
            "%s completed movement at (%.1f,%.1f)",
            getName(), x, y
        ));
    }

    private boolean isCollidingWithOtherPokemon(float newX, float newY, World world) {
        // Create temporary collision box at new position
        Rectangle tempBox = new Rectangle(
            newX + (World.TILE_SIZE - boundingBox.width) / 2f,
            newY + (World.TILE_SIZE - boundingBox.height) / 2f,
            boundingBox.width,
            boundingBox.height
        );

        Collection<WildPokemon> nearbyPokemon = world.getPokemonSpawnManager()
            .getPokemonInRange(newX, newY, World.TILE_SIZE * 2);

        for (WildPokemon other : nearbyPokemon) {
            if (other != this && other.getBoundingBox().overlaps(tempBox)) {
                return true;
            }
        }
        return false;
    }

    public boolean canMoveToTile(int newGridX, int newGridY, World world) {


        // Check if tile is passable
        if (!world.isPassable(newGridX, newGridY)) {
            return false;
        }

        // Check collision with other entities
        Rectangle targetBounds = new Rectangle(
            newGridX * TILE_SIZE + (TILE_SIZE - boundingBox.width) / 2f,
            newGridY * TILE_SIZE + (TILE_SIZE - boundingBox.height) / 2f,
            boundingBox.width,
            boundingBox.height
        );

        // Check collisions with other Pokémon
        for (WildPokemon other : world.getPokemonSpawnManager().getAllWildPokemon()) {
            if (other != this && other.getBoundingBox().overlaps(targetBounds)) {
                return false;
            }
        }

        return true;
    }

    private void updateMovement(float delta) {
        if (!isMoving || !isInterpolating) return;

        currentMoveTime += delta;
        movementProgress = Math.min(currentMoveTime / MOVEMENT_DURATION, 1.0f);

        // Use smooth step interpolation
        float smoothProgress = calculateSmoothProgress(movementProgress);

        // Calculate new position
        float newX = MathUtils.lerp(startPosition.x, targetPosition.x, smoothProgress);
        float newY = MathUtils.lerp(startPosition.y, targetPosition.y, smoothProgress);

        // Only update if position actually changed
        if (newX != lastUpdateX || newY != lastUpdateY) {
            setX(newX);
            setY(newY);
            lastUpdateX = newX;
            lastUpdateY = newY;
            updateBoundingBox();
        }

        // Check if movement is complete
        if (movementProgress >= 1.0f) {
            completeMovement();
        }
    }

    public void moveToTile(int targetTileX, int targetTileY, String newDirection) {
        if (!isMoving) {
            // Store current position as start
            startPosition.set(x, y);
            lastUpdateX = x;
            lastUpdateY = y;

            // Calculate target position in pixels
            float targetPixelX = targetTileX * World.TILE_SIZE;
            float targetPixelY = targetTileY * World.TILE_SIZE;
            targetPosition.set(targetPixelX, targetPixelY);

            // Set movement state
            this.direction = newDirection;
            this.isMoving = true;
            this.isInterpolating = true;
            this.currentMoveTime = 0f;

            // Calculate actual distance for movement duration
            float distance = Vector2.dst(startPosition.x, startPosition.y,
                targetPosition.x, targetPosition.y);

            // Adjust movement duration based on distance
            this.movementProgress = 0f;

            // Start walking animation
            animations.startMoving(direction);

            GameLogger.info(String.format(
                "%s starting movement from (%.1f,%.1f) to (%.1f,%.1f) dir:%s distance:%.1f",
                getName(), startPosition.x, startPosition.y,
                targetPosition.x, targetPosition.y, direction, distance
            ));
        }
    }

    private void chooseNewTarget(World world) {
        if (isMoving) return;

        int[] dx = {0, 0, -1, 1}; // up, down, left, right
        int[] dy = {1, -1, 0, 0};
        String[] dirs = {"up", "down", "left", "right"};

        // Try random directions
        int[] indices = {0, 1, 2, 3};
        shuffleArray(indices);

        for (int i : indices) {
            int newGridX = gridX + dx[i];
            int newGridY = gridY + dy[i];

            if (canMoveToTile(newGridX, newGridY, world)) {
                moveToTile(newGridX, newGridY, dirs[i]);
                return;
            }
        }
    }

    private void shuffleArray(int[] array) {
        for (int i = array.length - 1; i > 0; i--) {
            int j = MathUtils.random(i);
            int temp = array[i];
            array[i] = array[j];
            array[j] = temp;
        }
    }

    public void moveTo(float targetX, float targetY, String direction) {
        if (!isMoving) {
            startPosition.set(x, y);
            targetPosition = new Vector2(targetX, targetY);
            this.direction = direction;
            isMoving = true;
            elapsedMovementTime = 0f;

            if (animations != null) {
                animations.startMoving(direction);
            }

            GameLogger.info(String.format(
                "%s starting movement to (%.1f, %.1f) in direction %s",
                getName(), targetX, targetY, direction
            ));
        }
    }

    public float getX() {
        return x;
    }

    public void setX(float x) {
        this.pixelX = x;
        this.x = x;
        updateBoundingBox();
    }

    public float getY() {
        return y;
    }

    public void setY(float y) {
        this.pixelY = y;
        this.y = y;
        updateBoundingBox();
    }

    public int getGridX() {
        return gridX;
    }

    public int getGridY() {
        return gridY;
    }

    public PokemonAI getAi() {
        return ai;
    }

    public void render(SpriteBatch batch) {
        if (isDespawning) {
            if (despawnAnimation != null) {
                despawnAnimation.render(batch, getCurrentFrame(), FRAME_WIDTH, FRAME_HEIGHT);
            }
            return;
        }

        TextureRegion frame = getCurrentFrame();
        if (frame != null) {
            float renderX = x;
            float renderY = y;

            // Apply idle bounce if not moving
            if (!isMoving && isIdling) {
                float bounceOffset = IDLE_BOUNCE_HEIGHT *
                    MathUtils.sin(idleAnimationTime * MathUtils.PI2 / IDLE_BOUNCE_DURATION);
                renderY += bounceOffset;
            }

            // Scale and center the sprite
            float width = FRAME_WIDTH * RENDER_SCALE;
            float height = FRAME_HEIGHT * RENDER_SCALE;
            float offsetX = (width - FRAME_WIDTH) / 2f;
            float offsetY = (height - FRAME_HEIGHT) / 2f;

            // Draw with smooth interpolation
            batch.draw(frame,
                renderX - offsetX,
                renderY - offsetY,
                width,
                height);
        }
    }


    public boolean isExpired() {
        if (isExpired) return true;
        float currentTime = System.currentTimeMillis() / 1000f;
        return currentTime - spawnTime > PokemonSpawnManager.POKEMON_DESPAWN_TIME;
    }

    public void setExpired(boolean expired) {
        isExpired = expired;
    }


    public boolean isDespawning() {
        return isDespawning;
    }

    public void startDespawnAnimation() {
        if (!isDespawning) {
            isDespawning = true;
            despawnAnimation = new PokemonDespawnAnimation(getX(), getY());
        }
    }

    public float getMovementTimer() {
        return movementTimer;
    }

    public void setMovementTimer(float timer) {
        this.movementTimer = timer;
    }
}

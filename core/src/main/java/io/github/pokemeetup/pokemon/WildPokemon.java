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
import io.github.pokemeetup.system.gameplay.PokemonAnimations;
import io.github.pokemeetup.system.gameplay.overworld.PokemonSpawnManager;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.entityai.PokemonAI;
import io.github.pokemeetup.utils.GameLogger;

import java.util.List;

public class WildPokemon extends Pokemon {
    public static final float MOVEMENT_INTERVAL = 3.0f;
    private static final float SCALE = 2.0f;
    private static final float COLLISION_SCALE = 0.4f;
    private static final float MOVEMENT_SPEED = 2f; // Reduced for smoother movement
    private static final float ACCELERATION = 4f; // For smooth start/stop
    private static final float DECELERATION = 3f; // For smooth stopping
    private float currentSpeed = 0f;
    private float targetSpeed = 0f;

    // Add to existing variables
    private Vector2 velocity;
    private Vector2 currentPosition;
    private static final float TILE_SIZE = 32f;
    private static final float MOVEMENT_DURATION = 0.4f; // Time to move one tile
    private static final float MOVEMENT_THRESHOLD = 2f; // Distance threshold for reaching target

    // Grid position (in tiles)
    private int gridX;
    private int gridY;

    // Visual position (in pixels)
    private float visualX;
    private float visualY;

    // Movement state
    private boolean isMoving;
    private float movementProgress;
    private Vector2 startPosition;
    private Vector2 targetPosition;
    private String targetDirection;
    private final PokemonAnimations animations;
    private final float width;
    private final float height;
    private final Rectangle boundingBox;
    private float x;
    private float y;
    private float movementTimer;
    private long spawnTime;
    private String direction;
    private boolean isExpired = false;
    private PokemonAI ai;
    private boolean isAddedToParty = false;
    private boolean isDespawning = false;
    private float elapsedMovementTime = 0f;
    private PokemonDespawnAnimation despawnAnimation;


    public WildPokemon(String name, int level, int tileX, int tileY, TextureRegion overworldSprite) {
        super(name, level);
        this.gridX = tileX;
        this.gridY = tileY;
        this.visualX = tileX;
        this.visualY = tileY;
        this.startPosition = new Vector2();
        this.targetPosition = new Vector2();
        this.direction = "down";
        this.animations = new PokemonAnimations(overworldSprite);
        this.ai = new PokemonAI(this);
        this.width = World.TILE_SIZE * SCALE;
        this.height = World.TILE_SIZE * SCALE;
        // Initialize collision box
        float collisionWidth = TILE_SIZE * COLLISION_SCALE;
        float collisionHeight = TILE_SIZE * COLLISION_SCALE;
        this.boundingBox = new Rectangle(
            this.visualX + (TILE_SIZE - collisionWidth) / 2f,
            this.visualY + (height - collisionHeight) / 2f,
            collisionWidth,
            collisionHeight
        );
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

    public void update(float delta, World world) {
        if (isDespawning) {
            if (despawnAnimation != null && despawnAnimation.update(delta)) {
                isExpired = true;
            }
            return;
        }

        // Update AI
        if (ai != null) {
            ai.update(delta, world);
        }

        // Update movement
        if (isMoving) {
            movementProgress += delta / MOVEMENT_DURATION;
            if (movementProgress >= 1.0f) {
                // Complete movement
                movementProgress = 1.0f;
                visualX = targetPosition.x;
                visualY = targetPosition.y;
                gridX = Math.round(visualX / TILE_SIZE);
                gridY = Math.round(visualY / TILE_SIZE);
                isMoving = false;
                animations.stopMoving();
            } else {
                // Smooth interpolation between tiles
                float smoothProgress = calculateSmoothProgress(movementProgress);
                visualX = MathUtils.lerp(startPosition.x, targetPosition.x, smoothProgress);
                visualY = MathUtils.lerp(startPosition.y, targetPosition.y, smoothProgress);
            }

            // Update bounding box with visual position
            updateBoundingBox();
        }

        // Update animations
        if (animations != null) {
            animations.update(delta);
        }
    }

    private float calculateSmoothProgress(float progress) {
        // Smoothstep interpolation for more natural movement
        return progress * progress * (3 - 2 * progress);
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


    public void moveToTile(int newGridX, int newGridY, String newDirection) {
        if (!isMoving) {
            startPosition.set(visualX, visualY);
            targetPosition.set(newGridX * TILE_SIZE, newGridY * TILE_SIZE);
            direction = newDirection;
            targetDirection = newDirection;
            isMoving = true;
            movementProgress = 0f;
            animations.startMoving(direction);
        }
    }   private void chooseNewTarget(World world) {
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
    }public void moveTo(float targetX, float targetY, String direction) {
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

    public float getX() { return visualX; }

    public float getY() { return visualY; }

    public int getGridX() { return gridX; }

    public int getGridY() { return gridY; }

    public void updateBoundingBox() {
        if (boundingBox != null) {
            float collisionWidth = TILE_SIZE * COLLISION_SCALE;
            float collisionHeight = TILE_SIZE * COLLISION_SCALE;
            boundingBox.setPosition(
                visualX + (TILE_SIZE - collisionWidth) / 2f,
                visualY + (TILE_SIZE - collisionHeight) / 2f
            );
        }
    }

    public void render(SpriteBatch batch) {
        if (isDespawning && despawnAnimation != null) {
            despawnAnimation.render(batch, getCurrentFrame(), TILE_SIZE, TILE_SIZE);
            return;
        }

        TextureRegion frame = getCurrentFrame();
        if (frame != null) {
            batch.draw(frame, visualX, visualY, TILE_SIZE, TILE_SIZE);
        }
    }


    public boolean isExpired() {
        if (isExpired) return true;
        float currentTime = System.currentTimeMillis() / 1000f;
        return currentTime - spawnTime > PokemonSpawnManager.POKEMON_DESPAWN_TIME;
    }
    private TextureRegion getCurrentFrame() {
        if (animations == null) return null;
        return animations.getCurrentFrame(direction, isMoving, Gdx.graphics.getDeltaTime());
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


    // Getters and setters
    public void setX(float x) {
        this.x = x;
        updateBoundingBox();
    }
    public void setY(float y) {
        this.y = y;
        updateBoundingBox();
    }

    public float getMovementTimer() {
        return movementTimer;
    }

    public void setMovementTimer(float timer) {
        this.movementTimer = timer;
    }
}

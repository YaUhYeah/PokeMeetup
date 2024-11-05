package io.github.pokemeetup.pokemon;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.pokemon.Pokemon;
import io.github.pokemeetup.pokemon.attacks.Move;
import io.github.pokemeetup.pokemon.data.PokemonDatabase;
import io.github.pokemeetup.system.gameplay.PokemonAnimations;
import io.github.pokemeetup.system.gameplay.overworld.PokemonSpawnManager;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.utils.GameLogger;

public class WildPokemon extends Pokemon {
    public static final float MOVEMENT_INTERVAL = 3.0f;
    private static final float SCALE = 2.0f;
    private static final float COLLISION_SCALE = 0.65f;
    private static final float MOVEMENT_SPEED = 2f;

    private final PokemonAnimations animations;
    private final float width;
    private final float height;
    private final Rectangle boundingBox;
    private float x;
    private float y;
    private float movementTimer;
    private long spawnTime;
    private String direction;
    private boolean isMoving;
    private boolean isExpired = false;
    private boolean isAddedToParty = false;
    private boolean isDespawning = false;
    private Vector2 targetPosition;
    private Vector2 startPosition;
    private float elapsedMovementTime = 0f;
    private PokemonDespawnAnimation despawnAnimation;

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

//            if (update.statusEffects != null && !update.statusEffects.isEmpty()) {
//                // Update status effects
//                setStatusEffects(update.statusEffects);
//                GameLogger.info(getName() + " status effects updated.");
//            }

        // Update animations or other properties if necessary
        // For example, if the direction changes based on movement
    }

    public WildPokemon(String name, int level, int tileX, int tileY, TextureRegion overworldSprite) {
        super(name, level);

        // Initialize Pokemon data from database
        PokemonDatabase.BaseStats baseStats = PokemonDatabase.getStats(name);
        if (baseStats != null) {
            // Set types from database
            setPrimaryType(baseStats.primaryType);
            setSecondaryType(baseStats.secondaryType);

            // Set base stats
            Pokemon.Stats stats = getStats();
            if (stats != null) {
                stats.setHp(calculateStat(baseStats.baseHp, level, true));
                stats.setAttack(calculateStat(baseStats.baseAttack, level, false));
                stats.setDefense(calculateStat(baseStats.baseDefense, level, false));
                stats.setSpecialAttack(calculateStat(baseStats.baseSpAtk, level, false));
                stats.setSpecialDefense(calculateStat(baseStats.baseSpDef, level, false));
                stats.setSpeed(calculateStat(baseStats.baseSpeed, level, false));

                // Set current HP to max HP
                setCurrentHp(stats.getHp());
            }

            // Initialize moves from database
            if (baseStats.moves != null && !baseStats.moves.isEmpty()) {
                int moveCount = Math.min(4, baseStats.moves.size());
                for (int i = 0; i < moveCount; i++) {
                    PokemonDatabase.MoveTemplate moveTemplate = baseStats.moves.get(i);
                    Move move = new Move(
                        moveTemplate.name,
                        moveTemplate.type,
                        moveTemplate.power,
                        moveTemplate.accuracy,
                        moveTemplate.pp,
                        moveTemplate.isSpecial,
                        ""  // Description can be added if needed
                    );
                    getMoves().add(move);
                }
            }
        } else {
            // Fallback to default values if Pokemon not found in database
            GameLogger.error("Pokemon " + name + " not found in database, using default values");
            setPrimaryType(PokemonType.NORMAL);
            setCurrentHp(50); // Default HP
        }

        // Initialize movement and rendering properties
        this.spawnTime = System.currentTimeMillis() / 1000L;
        this.direction = "down";
        this.animations = new PokemonAnimations(overworldSprite);
        this.movementTimer = 0;

        // Set dimensions
        this.width = World.TILE_SIZE * SCALE;
        this.height = World.TILE_SIZE * SCALE;

        // Set position
        this.x = tileX * World.TILE_SIZE + (World.TILE_SIZE - width) / 2f;
        this.y = tileY * World.TILE_SIZE + (World.TILE_SIZE - height) / 2f;

        // Initialize bounding box
        float collisionWidth = width * COLLISION_SCALE;
        float collisionHeight = height * COLLISION_SCALE;
        this.boundingBox = new Rectangle(
            this.x + (width - collisionWidth) / 2f,
            this.y + (height - collisionHeight) / 2f,
            collisionWidth,
            collisionHeight
        );

        GameLogger.info(String.format("Initialized WildPokemon: %s (Lv.%d) at (%.1f, %.1f) with type %s",
            name, level, x, y, getPrimaryType()));
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

    public void updateBoundingBox() {
        boundingBox.setPosition(
            x + (width - boundingBox.width) / 2f,
            y + (height - boundingBox.height) / 2f
        );
    }

    public void setX(float x) {
        this.x = x;
    }

    public void setY(float y) {
        this.y = y;
    }

    public void setSpawnTime(long spawnTime) {
        this.spawnTime = spawnTime;
    }

    public void setExpired(boolean expired) {
        isExpired = expired;
    }

    public void setDespawning(boolean despawning) {
        isDespawning = despawning;
    }

    public void setTargetPosition(Vector2 targetPosition) {
        this.targetPosition = targetPosition;
    }

    public void setStartPosition(Vector2 startPosition) {
        this.startPosition = startPosition;
    }

    public void setElapsedMovementTime(float elapsedMovementTime) {
        this.elapsedMovementTime = elapsedMovementTime;
    }

    public void setDespawnAnimation(PokemonDespawnAnimation despawnAnimation) {
        this.despawnAnimation = despawnAnimation;
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

    @Override
    public String getDirection() {
        return direction;
    }

    @Override
    public boolean isMoving() {
        return isMoving;
    }

    public Vector2 getTargetPosition() {
        return targetPosition;
    }

    public Vector2 getStartPosition() {
        return startPosition;
    }

    public float getElapsedMovementTime() {
        return elapsedMovementTime;
    }

    public PokemonDespawnAnimation getDespawnAnimation() {
        return despawnAnimation;
    }

    public void update(float delta, World world) {
        if (isMoving) {
            elapsedMovementTime += delta;
            float progress = Math.min(1.0f, elapsedMovementTime * MOVEMENT_SPEED);

            if (progress >= 1.0f) {
                x = targetPosition.x;
                y = targetPosition.y;
                isMoving = false;
                updateBoundingBox();
                animations.stopMoving();
                GameLogger.info(getName() + " reached target position (" + x + ", " + y + ")");
            } else {
                x = MathUtils.lerp(startPosition.x, targetPosition.x, progress);
                y = MathUtils.lerp(startPosition.y, targetPosition.y, progress);
                updateBoundingBox();
            }
        } else {
            movementTimer += delta;
            if (movementTimer >= MOVEMENT_INTERVAL) {
                movementTimer = 0f;
                chooseNewTarget(world);
            }
        }

        animations.update(delta);
    }

    private void chooseNewTarget(World world) {
        String[] directions = {"up", "down", "left", "right"};
        direction = directions[MathUtils.random(3)];

        float newX = x;
        float newY = y;
        float moveDistance = World.TILE_SIZE;

        switch (direction) {
            case "up":
                newY += moveDistance;
                break;
            case "down":
                newY -= moveDistance;
                break;
            case "left":
                newX -= moveDistance;
                break;
            case "right":
                newX += moveDistance;
                break;
        }

        // Check if the new tile is passable and does not overlap with player or other Pokémon
        int tileX = (int) (newX / World.TILE_SIZE);
        int tileY = (int) (newY / World.TILE_SIZE);

        // Player collision check
        Rectangle targetBoundingBox = new Rectangle(
            newX + (width - boundingBox.width) / 2f,
            newY + (height - boundingBox.height) / 2f,
            boundingBox.width,
            boundingBox.height
        );

        if (!world.isPassable(tileX, tileY)) {
            GameLogger.info(getName() + " cannot move to (" + newX + ", " + newY + ") - Blocked by player or terrain.");
            return;
        }

        // Check for collisions with other Pokémon
        for (WildPokemon otherPokemon : world.getPokemonSpawnManager().getAllWildPokemon()) {
            if (otherPokemon != this && otherPokemon.getBoundingBox().overlaps(targetBoundingBox)) {
                GameLogger.info(getName() + " cannot move to (" + newX + ", " + newY + ") - Blocked by another Pokémon.");
                return;
            }
        }

        // Set the target position if the tile is valid
        startPosition = new Vector2(x, y);
        targetPosition = new Vector2(newX, newY);
        isMoving = true;
        elapsedMovementTime = 0f;
        animations.startMoving(direction);
        GameLogger.info(getName() + " moving to (" + newX + ", " + newY + ")");
    }

    public void render(SpriteBatch batch) {
        TextureRegion frame = animations.getCurrentFrame(direction, isMoving, Gdx.graphics.getDeltaTime());
        if (isDespawning && despawnAnimation != null) {
            despawnAnimation.render(batch, frame, getBoundingBox().width, getBoundingBox().height);
        } else {
            batch.draw(frame, getX(), getY(), getBoundingBox().width, getBoundingBox().height);
        }
    }

    public boolean isExpired() {
        if (isExpired) return true;
        float currentTime = System.currentTimeMillis() / 1000f;
        return currentTime - spawnTime > PokemonSpawnManager.POKEMON_DESPAWN_TIME;
    }

    public boolean isDespawning() {
        return isDespawning;
    }

    // Update the render method in WildPokemon

    // Add to WildPokemon class methods
    public void startDespawnAnimation() {
        if (!isDespawning) {
            isDespawning = true;
            despawnAnimation = new PokemonDespawnAnimation(getX(), getY());
        }
    }

    // Update the update method in WildPokemon

    public void update(float delta) {
        if (isDespawning && despawnAnimation != null) {
            if (despawnAnimation.update(delta)) {
                isExpired = true;
            }
            return;
        }

        if (isMoving) {
            elapsedMovementTime += delta;
            float progress = Math.min(1.0f, elapsedMovementTime * MOVEMENT_SPEED);

            if (progress >= 1.0f) {
                x = targetPosition.x;
                y = targetPosition.y;
                isMoving = false;
                updateBoundingBox();
                animations.stopMoving();
            } else {
                x = MathUtils.lerp(startPosition.x, targetPosition.x, progress);
                y = MathUtils.lerp(startPosition.y, targetPosition.y, progress);
                updateBoundingBox();
            }
        } else {
            movementTimer += delta;
            if (movementTimer >= MOVEMENT_INTERVAL) {
                movementTimer = 0f;
                // Note: chooseNewTarget needs world parameter, should be handled in the world update
            }
        }

        animations.update(delta);
    }

    // Getters and setters
    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getMovementTimer() {
        return movementTimer;
    }

    public void setMovementTimer(float timer) {
        this.movementTimer = timer;
    }

    public void setMoving(boolean moving) {
        this.isMoving = moving;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }
}

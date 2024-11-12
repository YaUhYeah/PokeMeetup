package io.github.pokemeetup.system.gameplay.overworld.entityai;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.pokemon.WildPokemon;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.utils.GameLogger;

public class PokemonAI {
    // Constants for AI behavior
    private static final float DECISION_INTERVAL = 2.0f;  // Time between AI decisions
    private static final float IDLE_MIN_DURATION = 1.0f;  // Minimum idle time
    private static final float IDLE_MAX_DURATION = 3.0f;  // Maximum idle time
    private static final float MOVEMENT_CHANCE = 0.7f;    // 70% chance to move
    private static final float FLEE_RANGE = 150f;         // Distance to start fleeing
    private static final float MOVE_SPEED = 15f;          // Movement speed in pixels/second

    private final WildPokemon pokemon;
    private float decisionTimer = 0;
    private float idleTimer = 0;
    private float idleDuration = 0;
    private AIState currentState = AIState.IDLE;
    private Vector2 targetPosition;
    private Vector2 startPosition;
    private float elapsedMovementTime = 0;
    private boolean isMoving = false;

    private enum AIState {
        IDLE,
        MOVING,
        FLEEING
    }

    public PokemonAI(WildPokemon pokemon) {
        this.pokemon = pokemon;
        this.targetPosition = new Vector2();
        this.startPosition = new Vector2();
    }

    public void update(float delta, World world) {
        // Update timers
        decisionTimer += delta;

        if (currentState == AIState.IDLE) {
            idleTimer += delta;
            if (idleTimer >= idleDuration) {
                makeDecision(world);
            }
        } else if (currentState == AIState.MOVING || currentState == AIState.FLEEING) {
            updateMovement(delta, world);
        }

        // Make new decisions periodically
        if (decisionTimer >= DECISION_INTERVAL) {
            makeDecision(world);
            decisionTimer = 0;
        }
    }

    private void makeDecision(World world) {
        float roll = MathUtils.random();

        if (checkForNearbyPlayer(world)) {
            enterFleeingState(world);
            return;
        }

        if (roll < MOVEMENT_CHANCE) {
            chooseNewTarget(world);
        } else {
            enterIdleState();
        }
    }

    private boolean checkForNearbyPlayer(World world) {
        if (world.getPlayer() == null) return false;

        float distanceToPlayer = Vector2.dst(
            pokemon.getX(), pokemon.getY(),
            world.getPlayer().getX(), world.getPlayer().getY()
        );

        return distanceToPlayer < FLEE_RANGE;
    }

    private void enterFleeingState(World world) {
        currentState = AIState.FLEEING;
        Vector2 awayFromPlayer = new Vector2(
            pokemon.getX() - world.getPlayer().getX(),
            pokemon.getY() - world.getPlayer().getY()
        ).nor();

        // Set flee target position
        targetPosition = new Vector2(
            pokemon.getX() + awayFromPlayer.x * World.TILE_SIZE * 3,
            pokemon.getY() + awayFromPlayer.y * World.TILE_SIZE * 3
        );

        startMovement();
    }

    private void chooseNewTarget(World world) {
        // Maximum movement range in tiles
        int maxRange = 5;

        for (int attempt = 0; attempt < 5; attempt++) {
            float targetX = pokemon.getX() + (MathUtils.random() * 2 - 1) * maxRange * World.TILE_SIZE;
            float targetY = pokemon.getY() + (MathUtils.random() * 2 - 1) * maxRange * World.TILE_SIZE;

            // Convert to tile coordinates for collision check
            int tileX = (int)(targetX / World.TILE_SIZE);
            int tileY = (int)(targetY / World.TILE_SIZE);

            if (world.isPassable(tileX, tileY)) {
                targetPosition.set(targetX, targetY);
                currentState = AIState.MOVING;
                startMovement();
                return;
            }
        }

        // If no valid position found, stay idle
        enterIdleState();
    }

    private void startMovement() {
        startPosition.set(pokemon.getX(), pokemon.getY());
        elapsedMovementTime = 0;
        isMoving = true;
        pokemon.setMoving(true);

        // Set direction based on movement
        float dx = targetPosition.x - startPosition.x;
        float dy = targetPosition.y - startPosition.y;

        if (Math.abs(dx) > Math.abs(dy)) {
            pokemon.setDirection(dx > 0 ? "right" : "left");
        } else {
            pokemon.setDirection(dy > 0 ? "up" : "down");
        }

        // Start walking animation
        pokemon.getAnimations().startMoving(pokemon.getDirection());
    }

    private void updateMovement(float delta, World world) {
        if (!isMoving) return;

        elapsedMovementTime += delta;
        float progress = Math.min(1.0f, elapsedMovementTime * MOVE_SPEED);

        // Update position with interpolation
        float newX = MathUtils.lerp(startPosition.x, targetPosition.x, progress);
        float newY = MathUtils.lerp(startPosition.y, targetPosition.y, progress);

        pokemon.setX(newX);
        pokemon.setY(newY);

        // Check if reached destination
        if (progress >= 1.0f) {
            isMoving = false;
            pokemon.setMoving(false);
            pokemon.getAnimations().stopMoving();

            if (currentState == AIState.FLEEING) {
                enterIdleState();
            } else {
                makeDecision(world);
            }
        }
    }

    private void enterIdleState() {
        currentState = AIState.IDLE;
        idleDuration = MathUtils.random(IDLE_MIN_DURATION, IDLE_MAX_DURATION);
        idleTimer = 0;
        isMoving = false;
        pokemon.setMoving(false);
        pokemon.getAnimations().stopMoving();
    }
}

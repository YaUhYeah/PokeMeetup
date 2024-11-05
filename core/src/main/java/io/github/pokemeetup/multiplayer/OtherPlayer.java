package io.github.pokemeetup.multiplayer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.pokemon.Pokemon;
import io.github.pokemeetup.pokemon.PokemonParty;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.data.PokemonData;
import io.github.pokemeetup.system.gameplay.PlayerAnimations;
import io.github.pokemeetup.system.gameplay.inventory.Inventory;
import io.github.pokemeetup.utils.GameLogger;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Represents another player in the multiplayer game.
 * Handles synchronization of position, movement, direction, and inventory based on network updates.
 */
public class OtherPlayer {
    private static final float LERP_ALPHA = 0.2f;
    private static final float ANIMATION_FRAME_DURATION = 0.25f / 4;
    private static final float RUN_ANIMATION_FRAME_DURATION = 0.1f;

    private final String username;
    private final Inventory inventory;
    private final PlayerAnimations animations;
    private final Object positionLock = new Object();
    private final Object inventoryLock = new Object();
    private final AtomicBoolean isMoving;
    private final AtomicBoolean wantsToRun;
    private Vector2 position;
    private Vector2 targetPosition;
    private String direction;
    private float stateTime;
    private BitmapFont font;

    private PokemonParty pokemonParty;
    private Map<UUID, Pokemon> ownedPokemon;

    /**
     * Constructs an OtherPlayer instance.
     *
     * @param username The username of the other player.
     * @param x        The initial X position.
     * @param y        The initial Y position.
     * @param atlas    The TextureAtlas containing player textures.
     */
    public OtherPlayer(String username, float x, float y, TextureAtlas atlas) {
        this.username = (username != null && !username.isEmpty()) ? username : "Unknown";
        this.position = new Vector2(x, y);
        this.targetPosition = new Vector2(x, y);
        this.inventory = new Inventory();
        this.direction = "down";
        this.isMoving = new AtomicBoolean(false);
        this.wantsToRun = new AtomicBoolean(false);
        this.stateTime = 0;
        this.pokemonParty = new PokemonParty();
        this.ownedPokemon = new HashMap<>();
        this.animations = new PlayerAnimations();

        GameLogger.info("Created OtherPlayer: " + this.username + " at (" + x + ", " + y + ")");
    }

    public void updatePokemon(NetworkProtocol.PokemonUpdate update) {
        // Get or create the Pokemon
        Pokemon pokemon = ownedPokemon.get(update.uuid);
        if (pokemon == null) {
            pokemon = update.data.toPokemon();
            if (pokemon != null) {
                pokemon.setUuid(update.uuid);
                ownedPokemon.put(update.uuid, pokemon);
            } else {
                GameLogger.error("Failed to create Pokemon from update data");
                return;
            }
        }

        // Update Pokemon state
        pokemon.setPosition(new Vector2(update.x, update.y));
        pokemon.setCurrentHp(update.data.getBaseHp());
        // Update other volatile stats as needzed
    }

    public void updateParty(List<PokemonData> partyData) {
        pokemonParty.clearParty();
        for (PokemonData data : partyData) {
            Pokemon pokemon = ownedPokemon.get(data.getUuid());
            if (pokemon == null) {
                pokemon = data.toPokemon();
                if (pokemon != null) {
                    pokemon.setUuid(data.getUuid());
                    ownedPokemon.put(data.getUuid(), pokemon);
                }
            }
            if (pokemon != null) {
                pokemonParty.addPokemon(pokemon);
            }
        }
    }

    /**
     * Updates the OtherPlayer's state each frame.
     *
     * @param delta The time elapsed since the last frame.
     */
    public void update(float delta) {
        synchronized (positionLock) {
            // Interpolate position smoothly towards targetPosition
            if (!position.epsilonEquals(targetPosition, 0.1f)) {
                // Increase LERP_ALPHA dynamically to reach target position faster
                float dynamicLerpAlpha = LERP_ALPHA + Math.min(0.5f, position.dst(targetPosition) * 0.01f);
                position.lerp(targetPosition, dynamicLerpAlpha);
            }
        }

        // Only advance animation state if moving
        if (isMoving.get()) {
            stateTime += delta;
        }
    }

    public void updateFromNetwork(NetworkProtocol.PlayerUpdate netUpdate) {
        if (netUpdate == null) {
            GameLogger.error("Received null PlayerUpdate for OtherPlayer: " + username);
            return;
        }

        // Synchronize position based on server-provided tile position
        synchronized (positionLock) {
            // Convert tile coordinates to pixel coordinates if necessary
            float pixelX = netUpdate.x;
            float pixelY = netUpdate.y;

            // Set target position to smoothly interpolate towards
            targetPosition.set(pixelX, pixelY);

            direction = (netUpdate.direction != null) ? netUpdate.direction : "down";
            isMoving.set(netUpdate.isMoving);
            wantsToRun.set(netUpdate.wantsToRun);
        }

        GameLogger.info("OtherPlayer " + username + " updated from network: Position=(" + netUpdate.x + ", " + netUpdate.y + "), Direction=" + direction);
    }

    /**
     * Renders the OtherPlayer on the screen.
     *
     * @param batch The SpriteBatch used for rendering.
     */
    public void render(SpriteBatch batch) {
        TextureRegion currentFrame = animations.getCurrentFrame(
            direction,
            isMoving.get(),
            wantsToRun.get(),
            stateTime
        );

        if (currentFrame == null) {
            GameLogger.error("OtherPlayer " + username + " has null currentFrame. Check PlayerAnimations.");
            return;
        }

        synchronized (positionLock) {
            batch.draw(currentFrame, position.x, position.y,
                Player.FRAME_WIDTH, Player.FRAME_HEIGHT);
        }

        renderUsername(batch);
    }

    /**
     * Renders the OtherPlayer's username above their character.
     *
     * @param batch The SpriteBatch used for rendering.
     */
    private void renderUsername(SpriteBatch batch) {
        if (username == null || username.isEmpty()) return;

        ensureFontLoaded();
        GlyphLayout layout = new GlyphLayout(font, username);
        float textWidth = layout.width;

        synchronized (positionLock) {
            font.draw(batch, username,
                position.x + (Player.FRAME_WIDTH - textWidth) / 2,
                position.y + Player.FRAME_HEIGHT + 15);
        }
    }

    /**
     * Ensures that the font is loaded and ready for rendering.
     */
    private void ensureFontLoaded() {
        if (font == null) {
            try {
                font = new BitmapFont(Gdx.files.internal("Skins/default.fnt"));
                font.getData().setScale(0.8f);
                GameLogger.error("Loaded font for OtherPlayer: " + username);
            } catch (Exception e) {
                GameLogger.error("Failed to load font for OtherPlayer: " + username + " - " + e.getMessage());
                font = new BitmapFont(); // Fallback to default font
            }
        }
    }

    /**
     * Disposes of resources used by OtherPlayer.
     */
    public void dispose() {
        if (font != null) {
            font.dispose();
            font = null;
            GameLogger.error("Disposed font for OtherPlayer: " + username);
        }
        animations.dispose();
        GameLogger.error(
            ("Disposed animations for OtherPlayer: " + username));
    }

    /**
     * Gets the current position of the OtherPlayer.
     *
     * @return A copy of the position vector.
     */
    public Vector2 getPosition() {
        synchronized (positionLock) {
            return new Vector2(position);
        }
    }

    public void setPosition(Vector2 position) {
        this.position = position;
    }

    // Getters with appropriate synchronization

    /**
     * Gets the OtherPlayer's Inventory.
     *
     * @return The Inventory instance.
     */
    public Inventory getInventory() {
        synchronized (inventoryLock) {
            return inventory;
        }
    }

    /**
     * Gets the username of the OtherPlayer.
     *
     * @return The username string.
     */
    public String getUsername() {
        return username;
    }

    /**
     * Gets the direction the OtherPlayer is facing.
     *
     * @return The direction string.
     */
    public String getDirection() {
        synchronized (positionLock) {
            return direction;
        }
    }

    /**
     * Checks if the OtherPlayer is currently moving.
     *
     * @return True if moving, false otherwise.
     */
    public boolean isMoving() {
        return isMoving.get();
    }

    /**
     * Checks if the OtherPlayer wants to run.
     *
     * @return True if wants to run, false otherwise.
     */
    public boolean isWantsToRun() {
        return wantsToRun.get();
    }

    /**
     * Gets the current X position of the OtherPlayer.
     *
     * @return The X coordinate.
     */
    public float getX() {
        synchronized (positionLock) {
            return position.x;
        }
    }

    /**
     * Sets the OtherPlayer's X position.
     *
     * @param x The new X position.
     */

    /**
     * Sets the X position of the OtherPlayer.
     *
     * @param x The new X coordinate.
     */
    public void setX(float x) {
        synchronized (positionLock) {
            this.position.x = x;
        }
    }

    /**
     * Gets the current Y position of the OtherPlayer.
     *
     * @return The Y coordinate.
     */
    public float getY() {
        synchronized (positionLock) {
            return position.y;
        }
    }

    /**
     * Sets the Y position of the OtherPlayer.
     *
     * @param y The new Y coordinate.
     */
    public void setY(float y) {
        synchronized (positionLock) {
            this.position.y = y;
        }
    }

    /**
     * Gets the target position of the OtherPlayer.
     * This is used for interpolating the player's movement.
     *
     * @return A copy of the target position Vector2.
     */
    public Vector2 getTargetPosition() {
        synchronized (positionLock) {
            return new Vector2(targetPosition);
        }
    }

    public void setTargetPosition(Vector2 targetPosition) {
        this.targetPosition = targetPosition;
    }

}

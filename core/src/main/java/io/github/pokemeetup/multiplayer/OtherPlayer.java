package io.github.pokemeetup.multiplayer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.gameplay.PlayerAnimations;
import io.github.pokemeetup.system.gameplay.inventory.Inventory;
import io.github.pokemeetup.utils.GameLogger;

import java.util.concurrent.atomic.AtomicBoolean;

public class OtherPlayer {
    private final String username;
    private final Inventory inventory;
    private final PlayerAnimations animations;
    private final Object positionLock = new Object();
    private final Object inventoryLock = new Object();
    private final AtomicBoolean isMoving;
    private final Vector2 targetPosition = new Vector2();
    private final AtomicBoolean wantsToRun;
    private Vector2 position;
    private String direction;
    private static final float INTERPOLATION_SPEED = 10f;
    private float stateTime;
    private BitmapFont font;


    public OtherPlayer(String username, float x, float y) {
        this.username = (username != null && !username.isEmpty()) ? username : "Unknown";
        this.position = new Vector2(x, y);
        this.inventory = new Inventory();
        this.direction = "down";
        this.isMoving = new AtomicBoolean(false);
        this.wantsToRun = new AtomicBoolean(false);
        this.stateTime = 0;
        this.animations = new PlayerAnimations();

        GameLogger.info("Created OtherPlayer: " + this.username + " at (" + x + ", " + y + ")");
    }

    private final Vector2 velocity = new Vector2();

    public void updateFromNetwork(NetworkProtocol.PlayerUpdate update) {
        if (update == null) return;

        targetPosition.set(update.x, update.y);
        this.direction = update.direction;
        this.isMoving.set(update.isMoving);
        this.wantsToRun.set(update.wantsToRun);

        // Calculate velocity
        if (update.isMoving) {
            float dx = update.x - position.x;
            float dy = update.y - position.y;
            float distance = Vector2.len(dx, dy);

            if (distance > 0) {
                velocity.set(dx / distance, dy / distance);
                velocity.scl(update.wantsToRun ? (float) 1.75 : 1);
            }
        } else {
            velocity.setZero();
        }
    }
    public void update(float deltaTime) {
        if (!position.epsilonEquals(targetPosition, 0.1f)) {
            interpolationProgress = Math.min(1.0f, interpolationProgress + deltaTime * INTERPOLATION_SPEED);
            position.x = MathUtils.lerp(position.x, targetPosition.x, interpolationProgress);
            position.y = MathUtils.lerp(position.y, targetPosition.y, interpolationProgress);
            isMoving.set(!position.epsilonEquals(targetPosition, 0.1f));
        } else {
            interpolationProgress = 0f;
            isMoving.set(false);
        }

        if (isMoving.get()) {
            stateTime += deltaTime;
        }
    }
    private float interpolationProgress;


    public void render(SpriteBatch batch) {
        TextureRegion currentFrame = animations.getCurrentFrame(
            direction,
            isMoving.get(),
            wantsToRun.get(),
            stateTime
        );

        if (currentFrame == null) {
            GameLogger.error("OtherPlayer " + username + " has null currentFrame");
            return;
        }

        synchronized (positionLock) {
            batch.draw(currentFrame,
                position.x,
                position.y,
                Player.FRAME_WIDTH,
                Player.FRAME_HEIGHT
            );


        }

        renderUsername(batch);
    }


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
    private void ensureFontLoaded() {
        if (font == null) {
            try {
                font = new BitmapFont(Gdx.files.internal("Skins/default.fnt"));
                font.getData().setScale(0.8f);
                GameLogger.error("Loaded font for OtherPlayer: " + username);
            } catch (Exception e) {
                GameLogger.error("Failed to load font for OtherPlayer: " + username + " - " + e.getMessage());
                font = new BitmapFont();
            }
        }
    }

    public void dispose() {

        animations.dispose();
        GameLogger.error(
            ("Disposed animations for OtherPlayer: " + username));
    }
    public Vector2 getPosition() {
        synchronized (positionLock) {
            return new Vector2(position);
        }
    }

    public void setPosition(Vector2 position) {
        this.position = position;
    }

    public Inventory getInventory() {
        synchronized (inventoryLock) {
            return inventory;
        }
    }

    public String getUsername() {
        return username;
    }

    public String getDirection() {
        synchronized (positionLock) {
            return direction;
        }
    }

    public boolean isMoving() {
        return isMoving.get();
    }

    public boolean isWantsToRun() {
        return wantsToRun.get();
    }

    public float getX() {
        synchronized (positionLock) {
            return position.x;
        }
    }

    public void setX(float x) {
        synchronized (positionLock) {
            this.position.x = x;
        }
    }

    public float getY() {
        synchronized (positionLock) {
            return position.y;
        }
    }
    public void setY(float y) {
        synchronized (positionLock) {
            this.position.y = y;
        }
    }

}

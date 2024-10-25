package io.github.pokemeetup.multiplayer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.managers.Network;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.inventory.Inventory;

public class OtherPlayer {
    private static final float LERP_ALPHA = 0.2f;
    private final String username;
    private float x, y;
    private String direction;
    private boolean isMoving;
    private boolean wantsToRun;
    private Animation<TextureRegion> runUpAnimation;
    private Animation<TextureRegion> runDownAnimation;
    private Animation<TextureRegion> runLeftAnimation;
    private Animation<TextureRegion> runRightAnimation;
    private Animation<TextureRegion> walkUpAnimation;
    private Animation<TextureRegion> walkDownAnimation;
    private Animation<TextureRegion> walkLeftAnimation;
    private Animation<TextureRegion> walkRightAnimation;
    private TextureRegion currentFrame;
    private BitmapFont font;
    private Vector2 targetPosition;
    private Vector2 currentPosition;
    private Inventory inventory;
    private float stateTime;

    public OtherPlayer(String username, float x, float y, TextureAtlas atlas) {
        this.username = username != null ? username : "Unknown";
        this.inventory = new Inventory();
        this.x = x;
        this.y = y;
        this.currentPosition = new Vector2(x, y);
        this.targetPosition = new Vector2(x, y);
        this.direction = "down";
        this.isMoving = false;
        this.wantsToRun = false;
        this.stateTime = 0;

        font = new BitmapFont();
        font = new BitmapFont(Gdx.files.internal("Fonts/pkmn.fnt"));
        font.getData().setScale(0.8f);

        initializeAnimations(atlas);
    }

    public Inventory getInventory() {
        return inventory;
    }



    public void render(SpriteBatch batch) {
        updateCurrentFrame();
        batch.draw(currentFrame, x, y, Player.FRAME_WIDTH, Player.FRAME_HEIGHT);
        if (username != null && font != null) {
            GlyphLayout layout = new GlyphLayout(font, username);
            float textWidth = layout.width;
            font.draw(batch, username, x + (Player.FRAME_WIDTH - textWidth) / 2,
                y + Player.FRAME_HEIGHT + 15);
        }
    }

    public String getUsername() {
        return username;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public String getDirection() {
        return direction;
    }

    public boolean isMoving() {
        return isMoving;
    }

    public boolean isWantsToRun() {
        return wantsToRun;
    }
    private void updateCurrentFrame() {
        Animation<TextureRegion> currentAnimation;

        // Default to down if direction is null
        if (direction == null) {
            direction = "down";
        }

        if (isMoving) {
            boolean running = wantsToRun;
            currentAnimation = switch (direction.toLowerCase()) {
                case "up" -> running ? runUpAnimation : walkUpAnimation;
                case "down" -> running ? runDownAnimation : walkDownAnimation;
                case "left" -> running ? runLeftAnimation : walkLeftAnimation;
                case "right" -> running ? runRightAnimation : walkRightAnimation;
                default -> walkDownAnimation;
            };

            stateTime += Gdx.graphics.getDeltaTime();
            currentFrame = currentAnimation.getKeyFrame(stateTime, true);
        } else {
            // Standing frames with null safety
            currentFrame = switch (direction.toLowerCase()) {
                case "up" -> walkUpAnimation.getKeyFrames()[0];
                case "down" -> walkDownAnimation.getKeyFrames()[0];
                case "left" -> walkLeftAnimation.getKeyFrames()[0];
                case "right" -> walkRightAnimation.getKeyFrames()[0];
                default -> walkDownAnimation.getKeyFrames()[0];
            };
            stateTime = 0;
        }
    }

    // Update the updateFromNetwork method to include null safety
    public void updateFromNetwork(Network.PlayerUpdate update) {
        if (update == null) return;

        this.x = update.x;
        this.y = update.y;
        this.direction = update.direction != null ? update.direction : "down";
        this.isMoving = update.isMoving;
        this.wantsToRun = update.wantsToRun;
        targetPosition.set(update.x, update.y);
    }

    private boolean validateFrames(TextureRegion[] frames) {
        for (TextureRegion frame : frames) {
            if (frame == null) return false;
        }
        return true;
    }

    private void createPlaceholderFrames(TextureRegion[]... frameArrays) {
        // Create a simple colored rectangle as placeholder
        Pixmap pixmap = new Pixmap(32, 48, Pixmap.Format.RGBA8888);
        pixmap.setColor(1, 0, 1, 1); // Magenta color for missing textures
        pixmap.fill();

        Texture placeholderTexture = new Texture(pixmap);
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
            walkDownFrames[0] = atlas.findRegion("boy_walk_down", 1);
            walkDownFrames[1] = atlas.findRegion("boy_walk_down", 2);
            walkDownFrames[2] = atlas.findRegion("boy_walk_down", 3);
            walkDownFrames[3] = atlas.findRegion("boy_walk_down", 4);

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

            // Validate that all frames were loaded successfully
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
            boolean framesValid = validateFrames(walkDownFrames) && validateFrames(walkLeftFrames) &&
                validateFrames(walkRightFrames) && validateFrames(walkUpFrames);
            float runFrameDuration = 0.1f; // Faster frame duration for running
            runDownAnimation = new Animation<>(runFrameDuration, runDownFrames);
            runLeftAnimation = new Animation<>(runFrameDuration, runLeftFrames);
            runRightAnimation = new Animation<>(runFrameDuration, runRightFrames);
            runUpAnimation = new Animation<>(runFrameDuration, runUpFrames);
            if (!framesValid) {
                Gdx.app.error("OtherPlayer", "Some animation frames failed to load. Creating placeholder frames.");
                createPlaceholderFrames(walkDownFrames, walkLeftFrames, walkRightFrames, walkUpFrames);
            }

        } catch (Exception e) {
            Gdx.app.error("OtherPlayer", "Error loading animation frames: " + e.getMessage());
            createPlaceholderFrames(walkDownFrames, walkLeftFrames, walkRightFrames, walkUpFrames);
        }

        // Create walking animations
        float frameDuration = 0.25f / 4; // Adjust based on your game's movement speed

        walkDownAnimation = new Animation<>(frameDuration, walkDownFrames);
        walkLeftAnimation = new Animation<>(frameDuration, walkLeftFrames);
        walkRightAnimation = new Animation<>(frameDuration, walkRightFrames);
        walkUpAnimation = new Animation<>(frameDuration, walkUpFrames);

        // Initialize currentFrame to a default standing frame
        currentFrame = walkDownFrames[0]; // Start facing down
    }

    public void dispose() {
        if (font != null) {
            font.dispose();
            font = null;
        }
        // Dispose other resources if needed
    }
}

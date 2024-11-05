package io.github.pokemeetup.system.gameplay;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.TextureManager;

public class PlayerAnimations {
    private final Animation<TextureRegion> walkUpAnimation;
    private final Animation<TextureRegion> walkDownAnimation;
    private final Animation<TextureRegion> walkLeftAnimation;
    private final Animation<TextureRegion> walkRightAnimation;
    private final Animation<TextureRegion> runUpAnimation;
    private final Animation<TextureRegion> runDownAnimation;
    private final Animation<TextureRegion> runLeftAnimation;
    private final Animation<TextureRegion> runRightAnimation;
    private final TextureRegion[] standingFrames;
    private Texture placeholderTexture;    public static final float BASE_MOVE_TIME = 0.25f;  // Time to move one tile when walking
    public static final float RUN_SPEED_MULTIPLIER = 1.75f;
    public static final float WALK_FRAME_DURATION = BASE_MOVE_TIME / 4; // 4 frames per movement
    public static final float RUN_FRAME_DURATION = (BASE_MOVE_TIME / RUN_SPEED_MULTIPLIER) / 4;


    public PlayerAnimations() {
        TextureRegion[][] frames = loadAnimationFrames();

        // Calculate animation durations to show all frames
        float walkFrameDuration = WALK_FRAME_DURATION;  // Time for each walking frame
        float runFrameDuration = RUN_FRAME_DURATION;    // Time for each running frame

        walkUpAnimation = new Animation<>(walkFrameDuration, frames[0]);
        walkDownAnimation = new Animation<>(walkFrameDuration, frames[1]);
        walkLeftAnimation = new Animation<>(walkFrameDuration, frames[2]);
        walkRightAnimation = new Animation<>(walkFrameDuration, frames[3]);

        // Create run animations - show all 4 frames during run cycle
        runUpAnimation = new Animation<>(runFrameDuration, frames[4]);
        runDownAnimation = new Animation<>(runFrameDuration, frames[5]);
        runLeftAnimation = new Animation<>(runFrameDuration, frames[6]);
        runRightAnimation = new Animation<>(runFrameDuration, frames[7]);

        // Set standing frames
        standingFrames = new TextureRegion[4];
        standingFrames[0] = frames[0][0]; // Up standing
        standingFrames[1] = frames[1][0]; // Down standing
        standingFrames[2] = frames[2][0]; // Left standing
        standingFrames[3] = frames[3][0]; // Right standing

        GameLogger.info("Successfully initialized player animations with adjusted timings");
    }

    public void dispose() {
        if (placeholderTexture != null) {
            placeholderTexture.dispose();
            placeholderTexture = null;
        }
    }

    /**
     * Retrieves the current animation frame based on direction and movement state.
     *
     * @param direction  The current movement direction.
     * @param isMoving   Whether the player is currently moving.
     * @param isRunning  Whether the player is running.
     * @param stateTime  The elapsed time since the movement started.
     * @return The appropriate TextureRegion for rendering.
     */

    public TextureRegion getCurrentFrame(String direction, boolean isMoving,
                                         boolean isRunning, float stateTime) {
        if (!isMoving) {
            return getStandingFrame(direction);
        }

        Animation<TextureRegion> currentAnimation = getAnimation(direction, isRunning);

        // Force the animation to loop
        return currentAnimation.getKeyFrame(stateTime, true);
    }

    /**
     * Determines the correct animation based on direction and running state.
     *
     * @param direction  The current movement direction.
     * @param isRunning  Whether the player is running.
     * @return The appropriate Animation<TextureRegion>.
     */

    private Animation<TextureRegion> getAnimation(String direction, boolean isRunning) {
        if (direction == null) {
            return walkDownAnimation;
        }

        switch (direction.toLowerCase()) {
            case "up":
                return isRunning ? runUpAnimation : walkUpAnimation;
            case "down":
                return isRunning ? runDownAnimation : walkDownAnimation;
            case "left":
                return isRunning ? runLeftAnimation : walkLeftAnimation;
            case "right":
                return isRunning ? runRightAnimation : walkRightAnimation;
            default:
                return walkDownAnimation;
        }
    }

    /**
     * Retrieves the standing frame based on direction.
     *
     * @param direction The current movement direction.
     * @return The appropriate TextureRegion for standing.
     */
    public TextureRegion getStandingFrame(String direction) {
        if (direction == null) {
            return standingFrames[1]; // Default to down
        }

        switch (direction.toLowerCase()) {
            case "up":
                return standingFrames[0];
            case "down":
                return standingFrames[1];
            case "left":
                return standingFrames[2];
            case "right":
                return standingFrames[3];
            default:
                return standingFrames[1]; // Default to down
        }
    }

    /**
     * Loads animation frames from the TextureAtlas.
     *
     * @return A 2D array of TextureRegions containing all animation frames.
     */
    private TextureRegion[][] loadAnimationFrames() {
        TextureAtlas atlas = TextureManager.boy;
        if (atlas == null) {
            throw new IllegalArgumentException("TextureAtlas cannot be null");
        }

        // Initialize frames array [8 animations][4 frames each]
        // Index mapping: 0-3: walk (up,down,left,right), 4-7: run (up,down,left,right)
        TextureRegion[][] frames = new TextureRegion[8][4];

        try {
            // Load walking animations
            loadDirectionalFrames(atlas, "boy_walk", frames, 0); // Walking frames
            // Load running animations
            loadDirectionalFrames(atlas, "boy_run", frames, 4);  // Running frames

            // Validate all frames
            validateFrames(frames);

            return frames;
        } catch (Exception e) {
            GameLogger.error("Failed to load animation frames: " + e.getMessage());
            return createPlaceholderFrames();
        }
    }

    /**
     * Loads directional frames for either walking or running animations.
     *
     * @param atlas        The TextureAtlas containing animation frames.
     * @param basePrefix   The base prefix for animation regions (e.g., "player_walk").
     * @param frames       The 2D array to store loaded frames.
     * @param startIndex   The starting index in the frames array.
     */
    private void loadDirectionalFrames(TextureAtlas atlas, String basePrefix, TextureRegion[][] frames, int startIndex) {
        String[] directions = {"up", "down", "left", "right"};

        for (int i = 0; i < directions.length; i++) {
            String prefix = basePrefix + "_" + directions[i];
            for (int frame = 0; frame < 4; frame++) {
                TextureRegion region = atlas.findRegion(prefix, frame + 1);
                if (region == null) {
                    GameLogger.error("Failed to load frame " + (frame + 1) + " for " + prefix);
                    throw new RuntimeException("Missing animation frame: " + prefix + "_" + (frame + 1));
                }
                frames[startIndex + i][frame] = region;
            }
        }
    }

    /**
     * Validates that all animation frames are loaded correctly.
     *
     * @param frames The 2D array of TextureRegions to validate.
     */
    private void validateFrames(TextureRegion[][] frames) {
        for (int i = 0; i < frames.length; i++) {
            for (int j = 0; j < frames[i].length; j++) {
                if (frames[i][j] == null) {
                    GameLogger.error("Null frame detected at position [" + i + "][" + j + "]");
                    throw new RuntimeException("Invalid animation frames: null frame detected");
                }
            }
        }
    }

    /**
     * Creates placeholder frames in case animation frames are missing.
     *
     * @return A 2D array of TextureRegions filled with placeholder textures.
     */
    private TextureRegion[][] createPlaceholderFrames() {
        TextureRegion[][] placeholderFrames = new TextureRegion[8][4];

        // Create placeholder texture
        Pixmap pixmap = new Pixmap(32, 48, Pixmap.Format.RGBA8888);
        try {
            // Create magenta placeholder for missing textures
            pixmap.setColor(1, 0, 1, 1);
            pixmap.fill();

            placeholderTexture = new Texture(pixmap);
            TextureRegion placeholder = new TextureRegion(placeholderTexture);

            // Fill all frames with placeholder
            for (int i = 0; i < 8; i++) {
                for (int j = 0; j < 4; j++) {
                    placeholderFrames[i][j] = new TextureRegion(placeholder);
                }
            }

            GameLogger.info("Created placeholder frames for missing animations");
            return placeholderFrames;

        } finally {
            pixmap.dispose(); // Always dispose pixmap
        }
    }
}

package io.github.pokemeetup.system.gameplay;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.textures.TextureManager;
public class PlayerAnimations {
    public static final float BASE_MOVE_TIME = 0.35f;
    public static final float RUN_SPEED_MULTIPLIER = 1.5f;
    public static final float WALK_FRAME_DURATION = BASE_MOVE_TIME / 4;
    public static final float RUN_FRAME_DURATION = (BASE_MOVE_TIME / RUN_SPEED_MULTIPLIER) / 4;

    private final Object stateLock = new Object();
    private TextureRegion[] standingFrames;
    private Animation<TextureRegion> walkUpAnimation;
    private Animation<TextureRegion> walkDownAnimation;
    private Animation<TextureRegion> walkLeftAnimation;
    private Animation<TextureRegion> walkRightAnimation;
    private Animation<TextureRegion> runUpAnimation;
    private Animation<TextureRegion> runDownAnimation;
    private Animation<TextureRegion> runLeftAnimation;
    private Animation<TextureRegion> runRightAnimation;
    private volatile boolean isInitialized = false;
    private volatile boolean isDisposed = false;

    public PlayerAnimations() {
        loadAnimations();
    }

    private synchronized void loadAnimations() {
        try {
            TextureAtlas atlas = TextureManager.getBoy();
            if (atlas == null ) {
                throw new RuntimeException("TextureAtlas is null or disposed");
            }

            // Log atlas regions for debugging
//            GameLogger.info("Available regions in boy atlas:");
            for (TextureAtlas.AtlasRegion region : atlas.getRegions()) {
//                GameLogger.info(region.name + " index: " + region.index);
            }

            // Initialize arrays
            standingFrames = new TextureRegion[4];

            // Load walk animations
            loadDirectionalFrames("boy_walk", atlas, true);
            // Load run animations
            loadDirectionalFrames("boy_run", atlas, false);

            isInitialized = true;
            isDisposed = false;
//            GameLogger.info("Successfully loaded all animations");

        } catch (Exception e) {
            GameLogger.error("Failed to load animations: " + e.getMessage());
            isInitialized = false;
            throw new RuntimeException("Animation loading failed", e);
        }
    }

    private void loadDirectionalFrames(String prefix, TextureAtlas atlas, boolean isWalk) {
        String[] directions = {"up", "down", "left", "right"};

        for (String direction : directions) {
            TextureRegion[] frames = new TextureRegion[4];
            String baseRegionName = prefix + "_" + direction;

            // Load all frames for this direction
            for (int i = 0; i < 4; i++) {
                TextureAtlas.AtlasRegion region = atlas.findRegion(baseRegionName, i + 1);
                if (region == null) {
                    GameLogger.error("Missing frame: " + baseRegionName + " index: " + (i + 1));
                    throw new RuntimeException("Missing animation frame");
                }
                frames[i] = new TextureRegion(region);
            }

            // Create animation for this direction
            float frameDuration = isWalk ? WALK_FRAME_DURATION : RUN_FRAME_DURATION;
            Animation<TextureRegion> animation = new Animation<>(frameDuration, frames);
            animation.setPlayMode(Animation.PlayMode.LOOP);

            // Assign animation and standing frame based on direction
            int dirIndex = getDirectionIndex(direction);
            if (isWalk) {
                assignWalkAnimation(dirIndex, animation);
                if (standingFrames[dirIndex] == null) {
                    standingFrames[dirIndex] = new TextureRegion(frames[0]); // Use first frame for standing
                }
            } else {
                assignRunAnimation(dirIndex, animation);
            }
        }
    }

    private int getDirectionIndex(String direction) {
        switch (direction.toLowerCase()) {
            case "up": return 0;
            case "down": return 1;
            case "left": return 2;
            case "right": return 3;
            default: return 1; // Default to down
        }
    }

    private void assignWalkAnimation(int index, Animation<TextureRegion> animation) {
        switch (index) {
            case 0: walkUpAnimation = animation; break;
            case 1: walkDownAnimation = animation; break;
            case 2: walkLeftAnimation = animation; break;
            case 3: walkRightAnimation = animation; break;
        }
    }

    private void assignRunAnimation(int index, Animation<TextureRegion> animation) {
        switch (index) {
            case 0: runUpAnimation = animation; break;
            case 1: runDownAnimation = animation; break;
            case 2: runLeftAnimation = animation; break;
            case 3: runRightAnimation = animation; break;
        }
    }

    public TextureRegion getCurrentFrame(String direction, boolean isMoving, boolean isRunning, float stateTime) {
        if (!isInitialized || isDisposed) {
            loadAnimations();
        }

        if (!isMoving) {
            return getStandingFrame(direction);
        }

        Animation<TextureRegion> currentAnimation = getAnimation(direction, isRunning);
        return currentAnimation.getKeyFrame(stateTime, true);
    }

    private Animation<TextureRegion> getAnimation(String direction, boolean isRunning) {
        if (direction == null) {
            return walkDownAnimation;
        }

        switch (direction.toLowerCase()) {
            case "up": return isRunning ? runUpAnimation : walkUpAnimation;
            case "down": return isRunning ? runDownAnimation : walkDownAnimation;
            case "left": return isRunning ? runLeftAnimation : walkLeftAnimation;
            case "right": return isRunning ? runRightAnimation : walkRightAnimation;
            default: return walkDownAnimation;
        }
    }

    public TextureRegion getStandingFrame(String direction) {
        if (!isInitialized || isDisposed) {
            loadAnimations();
        }

        if (direction == null) {
            return standingFrames[1]; // Default to down
        }

        switch (direction.toLowerCase()) {
            case "up": return standingFrames[0];
            case "down": return standingFrames[1];
            case "left": return standingFrames[2];
            case "right": return standingFrames[3];
            default: return standingFrames[1];
        }
    }

    public synchronized void dispose() {
        isDisposed = true;
        isInitialized = false;
    }

    public boolean isDisposed() {
        return isDisposed;
    }
}

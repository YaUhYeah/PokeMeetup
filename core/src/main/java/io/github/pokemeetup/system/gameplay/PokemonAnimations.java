package io.github.pokemeetup.system.gameplay;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.github.pokemeetup.utils.GameLogger;

public class PokemonAnimations {
    // Constants for 256x256 sprite sheet with 4x4 grid
    private static final int SPRITE_SHEET_SIZE = 256;
    private static final int FRAMES_PER_DIRECTION = 4;
    private static final int FRAME_WIDTH = SPRITE_SHEET_SIZE / FRAMES_PER_DIRECTION;  // 64
    private static final int FRAME_HEIGHT = SPRITE_SHEET_SIZE / FRAMES_PER_DIRECTION; // 64
    private static final float FRAME_DURATION = 0.15f;

    // Animations for each direction
    private Animation<TextureRegion> walkDownAnimation;  // Row 0
    private Animation<TextureRegion> walkLeftAnimation;  // Row 1
    private Animation<TextureRegion> walkRightAnimation; // Row 2
    private Animation<TextureRegion> walkUpAnimation;    // Row 3

    private final TextureRegion[] standingFrames;
    private float stateTime;
    private TextureRegion defaultFrame;
    private boolean isMoving;
    private String currentDirection;
    private boolean isInitialized;

    public PokemonAnimations(TextureRegion spriteSheet) {
        this.standingFrames = new TextureRegion[4];
        this.stateTime = 0f;

        if (spriteSheet == null) {
            GameLogger.error("Sprite sheet is null");
            createDefaultFrame();
            return;
        }

        try {
            // Verify sprite sheet dimensions
            if (spriteSheet.getRegionWidth() != SPRITE_SHEET_SIZE ||
                spriteSheet.getRegionHeight() != SPRITE_SHEET_SIZE) {
                GameLogger.error(String.format(
                    "Invalid sprite sheet dimensions. Expected %dx%d, got %dx%d",
                    SPRITE_SHEET_SIZE, SPRITE_SHEET_SIZE,
                    spriteSheet.getRegionWidth(), spriteSheet.getRegionHeight()
                ));
            }

            initializeAnimations(spriteSheet);
            isInitialized = true;
//            GameLogger.info("Successfully initialized Pokemon animations");
        } catch (Exception e) {
            GameLogger.error("Failed to initialize animations: " + e.getMessage());
            e.printStackTrace();
            createDefaultFrame();
        }
    }

    private void initializeAnimations(TextureRegion spriteSheet) {
        // Split sprite sheet into frames
        TextureRegion[][] allFrames = new TextureRegion[4][FRAMES_PER_DIRECTION];

        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < FRAMES_PER_DIRECTION; col++) {
                // Calculate frame coordinates
                int x = col * FRAME_WIDTH;
                int y = row * FRAME_HEIGHT;


                allFrames[row][col] = new TextureRegion(
                    spriteSheet,
                    x, y,
                    FRAME_WIDTH, FRAME_HEIGHT
                );

                // Store first frame of each row as standing frame
                if (col == 0) {
                    standingFrames[row] = new TextureRegion(allFrames[row][0]);
                }
            }
        }

        // Create animations for each direction
        walkDownAnimation = new Animation<>(FRAME_DURATION, allFrames[0]);
        walkLeftAnimation = new Animation<>(FRAME_DURATION, allFrames[1]);
        walkRightAnimation = new Animation<>(FRAME_DURATION, allFrames[2]);
        walkUpAnimation = new Animation<>(FRAME_DURATION, allFrames[3]);

        // Set default frame
        defaultFrame = standingFrames[0];

//        GameLogger.info("Created all animations successfully");
    }

    private void createDefaultFrame() {
        Pixmap pixmap = new Pixmap(FRAME_WIDTH, FRAME_HEIGHT, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.MAGENTA);
        pixmap.fill();
        Texture texture = new Texture(pixmap);
        pixmap.dispose();

        defaultFrame = new TextureRegion(texture);
        for (int i = 0; i < 4; i++) {
            standingFrames[i] = new TextureRegion(defaultFrame);
        }
    }

    public TextureRegion getCurrentFrame(String direction, boolean isMoving, float delta) {
        if (!isInitialized) {
            return defaultFrame;
        }

        this.isMoving = isMoving;
        this.currentDirection = direction;

        // Only update stateTime if we're moving
        if (isMoving) {
            stateTime += delta;
        }

        TextureRegion frame;
        if (isMoving) {
            Animation<TextureRegion> currentAnimation = getAnimationForDirection(direction);
            frame = currentAnimation.getKeyFrame(stateTime, true);
        } else {
            frame = getStandingFrame(direction);
        }

        // Debug log

        return frame != null ? frame : defaultFrame;
    }

    private TextureRegion getStandingFrame(String direction) {
        switch (direction.toLowerCase()) {
            case "down":
                return standingFrames[0];
            case "left":
                return standingFrames[1];
            case "right":
                return standingFrames[2];
            case "up":
                return standingFrames[3];
            default:
                return standingFrames[0]; // Default to down
        }
    }

    private Animation<TextureRegion> getAnimationForDirection(String direction) {
        switch (direction.toLowerCase()) {
            case "down":
                return walkDownAnimation;
            case "left":
                return walkLeftAnimation;
            case "right":
                return walkRightAnimation;
            case "up":
                return walkUpAnimation;
            default:
                return walkDownAnimation;
        }
    }

    public void startMoving(String direction) {
        if (!isMoving || !direction.equals(currentDirection)) {
            this.isMoving = true;
            this.currentDirection = direction;
            // Don't reset stateTime to keep animations smooth
        }
    }

    public void stopMoving() {
        this.isMoving = false;
    }

    public void update(float delta) {
        if (isMoving) {
            stateTime += delta;
        }
    }
}

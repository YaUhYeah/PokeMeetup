package io.github.pokemeetup.system.gameplay;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.github.pokemeetup.utils.GameLogger;

public class PokemonAnimations {
    private static final int FRAMES_PER_DIRECTION = 4;
    private static final int FRAME_WIDTH = 64;  // Adjust based on your spritesheet
    private static final int FRAME_HEIGHT = 64; // Adjust based on your spritesheet
    private static final float FRAME_DURATION = 0.25f;

    private Animation<TextureRegion> walkUpAnimation;
    private Animation<TextureRegion> walkDownAnimation;
    private Animation<TextureRegion> walkLeftAnimation;
    private Animation<TextureRegion> walkRightAnimation;
    private TextureRegion[] standingFrames;
    private float stateTime;
    private TextureRegion defaultFrame;
    private boolean isMoving;
    private String currentDirection;

    public PokemonAnimations(TextureRegion fullSheet) {
        if (fullSheet == null) {
            throw new IllegalArgumentException("Pokemon sprite sheet cannot be null");
        }

        GameLogger.info("Creating animations from sheet: " +
            fullSheet.getRegionWidth() + "x" + fullSheet.getRegionHeight());

        try {
            // First validate the sprite sheet dimensions
            if (fullSheet.getRegionWidth() < FRAME_WIDTH * FRAMES_PER_DIRECTION ||
                fullSheet.getRegionHeight() < FRAME_HEIGHT * 4) {
                GameLogger.error("Sprite sheet dimensions invalid: " +
                    fullSheet.getRegionWidth() + "x" + fullSheet.getRegionHeight() +
                    " (need at least " + (FRAME_WIDTH * FRAMES_PER_DIRECTION) + "x" +
                    (FRAME_HEIGHT * 4) + ")");
                createDefaultFrame(fullSheet);
                return;
            }

            // Create frame arrays
            TextureRegion[][] frames = new TextureRegion[4][FRAMES_PER_DIRECTION];

            // Split sprite sheet manually with proper bounds checking
            for (int row = 0; row < 4; row++) {
                for (int col = 0; col < FRAMES_PER_DIRECTION; col++) {
                    int x = fullSheet.getRegionX() + (col * FRAME_WIDTH);
                    int y = fullSheet.getRegionY() + (row * FRAME_HEIGHT);

                    if (x + FRAME_WIDTH <= fullSheet.getTexture().getWidth() &&
                        y + FRAME_HEIGHT <= fullSheet.getTexture().getHeight()) {

                        frames[row][col] = new TextureRegion(
                            fullSheet.getTexture(),
                            x, y,
                            FRAME_WIDTH, FRAME_HEIGHT
                        );

//                        GameLogger.info("Created frame at " + x + "," + y);
                    } else {
                        GameLogger.error("Frame out of bounds: " + x + "," + y);
                        frames[row][col] = createDefaultFrame(null);
                    }
                }
            }

            // Create animations with validation
            walkDownAnimation = createAnimation(frames[0]);
            walkLeftAnimation = createAnimation(frames[1]);
            walkRightAnimation = createAnimation(frames[2]);
            walkUpAnimation = createAnimation(frames[3]);

            // Set standing frames with validation
            standingFrames = new TextureRegion[4];
            standingFrames[0] = validateFrame(frames[3][0]); // Up
            standingFrames[1] = validateFrame(frames[0][0]); // Down
            standingFrames[2] = validateFrame(frames[1][0]); // Left
            standingFrames[3] = validateFrame(frames[2][0]); // Right

            defaultFrame = standingFrames[1]; // Use down standing frame as default

        } catch (Exception e) {
            GameLogger.error("Error creating animations: " + e.getMessage());
            createDefaultFrame(fullSheet);
        }
    }  public void startMoving(String direction) {
        this.isMoving = true;
        this.currentDirection = direction;
        // Don't reset stateTime to keep animations smooth
    }

    public void stopMoving() {
        this.isMoving = false;
        // Keep current direction for standing frame
    }

    public boolean isMoving() {
        return isMoving;
    }

    public void setDirection(String direction) {
        if (!direction.equals(this.currentDirection)) {
            this.currentDirection = direction;
            // Optional: reset stateTime when changing direction
            // this.stateTime = 0;
        }
    }



    public TextureRegion getCurrentFrame(String direction, boolean isMoving, float delta) {
        try {
            stateTime += delta;
            this.isMoving = isMoving;
            this.currentDirection = direction;

            if (!isMoving) {
                return getStandingFrame(direction);
            }

            Animation<TextureRegion> currentAnimation = getAnimationForDirection(direction);
            if (currentAnimation == null) {
                return defaultFrame;
            }

            TextureRegion frame = currentAnimation.getKeyFrame(stateTime, true);
            return frame != null ? frame : defaultFrame;

        } catch (Exception e) {
            GameLogger.error("Error getting current frame: " + e.getMessage());
            return defaultFrame;
        }
    }

    private TextureRegion validateFrame(TextureRegion frame) {
        if (frame == null || frame.getTexture() == null) {
            return defaultFrame != null ? defaultFrame : createDefaultFrame(null);
        }
        return frame;
    }

    private TextureRegion createDefaultFrame(TextureRegion original) {
        // Create a simple colored square as default frame
        Pixmap pixmap = new Pixmap(FRAME_WIDTH, FRAME_HEIGHT, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.MAGENTA);
        pixmap.fill();
        Texture texture = new Texture(pixmap);
        pixmap.dispose();

        TextureRegion frame = new TextureRegion(texture);
        if (defaultFrame == null) {
            defaultFrame = frame;
        }
        return frame;
    }

    private Animation<TextureRegion> createAnimation(TextureRegion[] frames) {
        // Validate frames before creating animation
        TextureRegion[] validFrames = new TextureRegion[frames.length];
        for (int i = 0; i < frames.length; i++) {
            validFrames[i] = validateFrame(frames[i]);
        }
        return new Animation<>(FRAME_DURATION, validFrames);
    }

    private Animation<TextureRegion> getAnimationForDirection(String direction) {
        switch (direction.toLowerCase()) {
            case "up":
                return walkUpAnimation;
            case "down":
                return walkDownAnimation;
            case "left":
                return walkLeftAnimation;
            case "right":
                return walkRightAnimation;
            default:
                return walkDownAnimation;
        }
    }

    private TextureRegion getStandingFrame(String direction) {
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
                return standingFrames[1];
        }
    }

    public void update(float delta) {
        stateTime += delta;
    }
}

package io.github.pokemeetup.system;

import com.badlogic.gdx.math.Vector2;

public class AndroidMovementController {
    private static final float DEADZONE = 10f;
    private Player player;
    private String queuedDirection = null;
    private Vector2 touchDown = new Vector2();
    private Vector2 currentTouch = new Vector2();

    public AndroidMovementController(Player player) {
        this.player = player;
    }
    private void updateMovement() {
        float dx = currentTouch.x - touchDown.x;
        float dy = currentTouch.y - touchDown.y;

        if (Math.abs(dx) < DEADZONE && Math.abs(dy) < DEADZONE) {
            queuedDirection = null;
            return;
        }

        String newDirection;
        if (Math.abs(dx) > Math.abs(dy)) {
            newDirection = dx > 0 ? "right" : "left";
        } else {
            newDirection = dy > 0 ? "up" : "down";
        }

        if (player.isMoving()) {
            // If already moving, only change direction
            player.setDirection(newDirection);
        } else {
            queuedDirection = newDirection;
        }
    }

    public void handleTouchDown(float x, float y) {
        touchDown.set(x, y);
        currentTouch.set(x, y);
        updateMovement();
    }

    public void handleTouchDragged(float x, float y) {
        currentTouch.set(x, y);
        updateMovement();
    }

    public void handleTouchUp() {
        queuedDirection = null;
    }

    public void update(float delta) {
        if (!player.isMoving() && queuedDirection != null) {
            player.move(queuedDirection);
        }
    }

}

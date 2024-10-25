package io.github.pokemeetup.system;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;

public class InputHandler extends InputAdapter {
    private final Player player;
    private final PickupActionHandler pickupHandler;
    private boolean upPressed, downPressed, leftPressed, rightPressed;

    public InputHandler(Player player,PickupActionHandler pickupHandler) {
        this.player = player;
        this.pickupHandler = pickupHandler;
    }

    @Override
    public boolean keyDown(int keycode) {
        switch (keycode) {
            case Input.Keys.UP:    upPressed = true; return true;
            case Input.Keys.DOWN:  downPressed = true; return true;
            case Input.Keys.LEFT:  leftPressed = true; return true;
            case Input.Keys.RIGHT: rightPressed = true; return true;
            case Input.Keys.Z:     player.setRunning(true); return true;
        }
        if (keycode == Input.Keys.X) {
            pickupHandler.handlePickupAction();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyUp(int keycode) {
        switch (keycode) {
            case Input.Keys.UP:    upPressed = false; return true;
            case Input.Keys.DOWN:  downPressed = false; return true;
            case Input.Keys.LEFT:  leftPressed = false; return true;
            case Input.Keys.RIGHT: rightPressed = false; return true;
            case Input.Keys.Z:     player.setRunning(false); return true;
        }
        return false;
    }

    public void update() {
        if (!player.isMoving()) {
            if (upPressed) player.move("up");
            else if (downPressed) player.move("down");
            else if (leftPressed) player.move("left");
            else if (rightPressed) player.move("right");
        }
    }
}

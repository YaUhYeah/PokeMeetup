package io.github.pokemeetup.system;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.math.Vector3;
import io.github.pokemeetup.system.battle.BattleInitiationHandler;
import io.github.pokemeetup.system.gameplay.overworld.World;

public class InputHandler extends InputAdapter {
        private final Player player;
    private final PickupActionHandler pickupHandler;
    private final BattleInitiationHandler battleInitiationHandler;
        private boolean upPressed, downPressed, leftPressed, rightPressed;

        public InputHandler(Player player, PickupActionHandler pickupHandler, BattleInitiationHandler battleInitiationHandler) {
            this.player = player;
            this.pickupHandler = pickupHandler;
            this.battleInitiationHandler = battleInitiationHandler;
        }

        @Override
        public boolean keyDown(int keycode) {
            switch (keycode) {
                case Input.Keys.W:
                case Input.Keys.UP:
                    upPressed = true;
                    return true;
                case Input.Keys.S:
                case Input.Keys.DOWN:
                    downPressed = true;
                    return true;
                case Input.Keys.A:
                case Input.Keys.LEFT:
                    leftPressed = true;
                    return true;
                case Input.Keys.D:
                case Input.Keys.RIGHT:
                    rightPressed = true;
                    return true;
                case Input.Keys.Z:
                    player.setRunning(true);
                    return true;
                case Input.Keys.X:
                    pickupHandler.handlePickupAction();
                    battleInitiationHandler.handleBattleInitiation();
                    return true;
            }
            return false;
        }@Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (player.isBuildMode()) {
            if (button == Input.Buttons.LEFT) {
                Vector3 worldCoords = new Vector3(screenX, screenY, 0);
                int tileX = (int) (worldCoords.x / World.TILE_SIZE);
                int tileY = (int) (worldCoords.y / World.TILE_SIZE);

                player.tryPlaceBlock(tileX, tileY, player.getWorld());
                return true;
            }
        }
        return false;
    }

        @Override
        public boolean keyUp(int keycode) {
            switch (keycode) {
                case Input.Keys.W:
                case Input.Keys.UP:
                    upPressed = false;
                    return true;
                case Input.Keys.S:
                case Input.Keys.DOWN:
                    downPressed = false;
                    return true;
                case Input.Keys.A:
                case Input.Keys.LEFT:
                    leftPressed = false;
                    return true;
                case Input.Keys.D:
                case Input.Keys.RIGHT:
                    rightPressed = false;
                    return true;
                case Input.Keys.Z:
                    player.setRunning(false);
                    return true;
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

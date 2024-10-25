package io.github.pokemeetup.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.pokemeetup.CreatureCaptureGame;
import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.PlayerData;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.multiworld.WorldData;
import io.github.pokemeetup.system.inventory.Item;


import java.util.ArrayList;
import java.util.List;

public class GameMenu {
    private static final float BUTTON_WIDTH = 200f;
    private static final float BUTTON_HEIGHT = 50f;
    private static final float MENU_PADDING = 20f;
    private final CreatureCaptureGame game;
    private final GameScreen gameScreen;
    private final Player player;
    private final GameClient gameClient;
    private Stage stage;
    private Skin skin;
    private Window menuWindow;
    private Table menuTable;
    private boolean isVisible;

    public GameMenu(GameScreen gameScreen, CreatureCaptureGame game, Skin skin, Player player, GameClient gameClient) {
        this.gameScreen = gameScreen;
        this.game = game;
        this.skin = skin;
        this.player = player;
        this.gameClient = gameClient;
        this.stage = new Stage(new ScreenViewport());
        createMenu();
        menuWindow.setVisible(false);  // Start with window hidden
        hide(); // Start hidden
    }

    public Stage getStage() {
        return stage;
    }

    private void createMenu() {
        menuWindow = new Window("Menu", skin);
        menuWindow.setMovable(false);

        menuTable = new Table();
        menuTable.defaults().pad(10).width(BUTTON_WIDTH).height(BUTTON_HEIGHT);

        TextButton saveButton = new TextButton("Save Game", skin);
        TextButton bagButton = new TextButton("Bag", skin);
        TextButton pokemonButton = new TextButton("Pokemon", skin);
        TextButton optionsButton = new TextButton("Options", skin);
        TextButton exitButton = new TextButton("Quit Game", skin);

        // Only implement save for now
        saveButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                saveGame();
            }
        });

        // Other buttons show "Not implemented" message
        ClickListener notImplementedListener = new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                showNotImplementedMessage();
            }
        };

        bagButton.addListener(notImplementedListener);
        pokemonButton.addListener(notImplementedListener);
        optionsButton.addListener(notImplementedListener);
        exitButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.saveGameState();
                Gdx.app.exit();
            }
        });

        menuTable.add(saveButton).row();
        menuTable.add(bagButton).row();
        menuTable.add(pokemonButton).row();
        menuTable.add(optionsButton).row();
        menuTable.add(exitButton).row();

        menuWindow.add(menuTable).pad(MENU_PADDING);
        menuWindow.pack();

        // Center the window
        menuWindow.setPosition(
            (Gdx.graphics.getWidth() - menuWindow.getWidth()) / 2,
            (Gdx.graphics.getHeight() - menuWindow.getHeight()) / 2
        );

        stage.addActor(menuWindow);
    }

    // Add these helper methods from AutoSaveManager
    private void updatePlayerData(PlayerData playerData) {
        if (player != null) {
            playerData.setPosition(player.getX(), player.getY());
            playerData.setDirection(player.getDirection());
            playerData.setMoving(player.isMoving());
            playerData.setWantsToRun(player.isRunning());

            // Update inventory
            List<String> inventoryStrings = new ArrayList<>();
            for (Item item : player.getInventory().getItems()) {
                if (item != null) {
                    inventoryStrings.add(item.getName() + ":" + item.getCount());
                }
            }
            playerData.setInventory(inventoryStrings);
        }
    }

    private void saveWorldData() {
        try {
            Json json = new Json();
            json.setUsePrototypes(false);

            FileHandle worldDir = Gdx.files.local("worlds/" + game.getCurrentWorld().getName());
            if (!worldDir.exists()) {
                worldDir.mkdirs();
            }

            WorldData worldData = game.getCurrentWorld().getWorldData();

            // Update current player data before saving
            PlayerData currentPlayer = game.getCurrentWorld().getPlayerData();
            if (currentPlayer != null) {
                updatePlayerData(currentPlayer);
                worldData.savePlayerData(currentPlayer.getUsername(), currentPlayer);
            }

            worldData.updateLastPlayed();
            FileHandle worldFile = worldDir.child("world.json");

            String jsonString = json.prettyPrint(worldData);
            worldFile.writeString(jsonString, false);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save world: " + e.getMessage(), e);
        }
    }

    private void showNotImplementedMessage() {
        com.badlogic.gdx.scenes.scene2d.ui.Dialog dialog = new com.badlogic.gdx.scenes.scene2d.ui.Dialog("Notice", skin) {
            public void result(Object obj) {
                hide();
            }
        };
        dialog.text("This feature is not yet implemented.");
        dialog.button("OK");
        dialog.show(stage);
    }

    private void saveGame() {
        try {
            if (gameClient != null && player.getWorld() != null) {
                // Get current world
                World currentWorld = player.getWorld();

                // Get current player
                if (player == null || player.getUsername() == null) {
                    throw new Exception("Invalid player state");
                }

                // Create new PlayerData with explicit username
                PlayerData playerData = new PlayerData(player.getUsername());

                // Update player state
                playerData.setPosition(player.getX(), player.getY());
                playerData.setDirection(player.getDirection());
                playerData.setMoving(player.isMoving());
                playerData.setWantsToRun(player.isRunning());

                // Update inventory - maintain item counts
                List<String> inventoryStrings = new ArrayList<>();
                for (Item item : player.getInventory().getItems()) {
                    if (item != null) {
                        // Format: "ItemName:Count"
                        inventoryStrings.add(String.format("%s:%d", item.getName(), item.getCount()));
                    }
                }
                playerData.setInventory(inventoryStrings);

                // Save based on game mode
                if (gameClient.isSinglePlayer()) {
                    // Single player save
                    WorldData worldData = currentWorld.getWorldData();
                    worldData.savePlayerData(player.getUsername(), playerData);
                    game.getWorldManager().saveWorld(worldData);
                    System.out.println("Saved single player data for: " + player.getUsername());
                } else {
                    // Multiplayer save
                    gameClient.updateLastKnownState(playerData);
                    gameClient.savePlayerState(playerData);
                    System.out.println("Saved multiplayer data for: " + player.getUsername());
                }

                // Show success dialog
                com.badlogic.gdx.scenes.scene2d.ui.Dialog dialog =
                    new com.badlogic.gdx.scenes.scene2d.ui.Dialog("Success", skin) {
                        public void result(Object obj) {
                            hide();
                        }
                    };
                dialog.text("Game saved successfully!");
                dialog.button("OK");
                dialog.show(stage);

            } else {
                System.out.println("client: " + (gameClient == null) + " world: " + (game.getCurrentWorld() == null));
                throw new Exception("Game state is invalid");
            }
        } catch (Exception e) {
            System.err.println("Save error: " + e.getMessage());
            com.badlogic.gdx.scenes.scene2d.ui.Dialog dialog =
                new com.badlogic.gdx.scenes.scene2d.ui.Dialog("Error", skin) {
                    public void result(Object obj) {
                        hide();
                    }
                };
            dialog.text("Failed to save game: " + e.getMessage());
            dialog.button("OK");
            dialog.show(stage);
        }
    }
    public void show() {
        isVisible = true;
        menuWindow.setVisible(true);  // Make sure window is visible
    }

    public void hide() {
        isVisible = false;
        menuWindow.setVisible(false); // Hide window but keep stage active
        gameScreen.resetInputProcessor();
    }

    public void render() {
        if (isVisible) {
            stage.act();
            stage.draw();
        }
    }

    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
        if (menuWindow != null) {
            menuWindow.setPosition(
                (width - menuWindow.getWidth()) / 2,
                (height - menuWindow.getHeight()) / 2
            );
        }
    }

    public boolean isVisible() {
        return isVisible;
    }

    public void dispose() {
        stage.dispose();
    }
}

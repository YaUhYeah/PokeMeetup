package io.github.pokemeetup.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.pokemeetup.CreatureCaptureGame;
import io.github.pokemeetup.audio.AudioManager;
import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.PlayerData;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.multiworld.WorldData;
import io.github.pokemeetup.utils.GameLogger;

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
    private Window optionsWindow;
    private Slider musicSlider;
    private Slider soundSlider;
    private CheckBox musicEnabled;
    private CheckBox soundEnabled;

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

    // Update the createMenu method to handle the options button

    private void createOptionsMenu() {
        optionsWindow = new Window("Options", skin);
        optionsWindow.setMovable(false);

        Table optionsTable = new Table();
        optionsTable.pad(MENU_PADDING);

        // Music volume slider
        Label musicLabel = new Label("Music Volume", skin);
        musicSlider = new Slider(0f, 1f, 0.1f, false, skin);
        musicSlider.setValue(AudioManager.getInstance().getMusicVolume());

        // Music enabled checkbox
        musicEnabled = new CheckBox(" Music Enabled", skin);
        musicEnabled.setChecked(AudioManager.getInstance().isMusicEnabled());

        // Sound volume slider
        Label soundLabel = new Label("Sound Volume", skin);
        soundSlider = new Slider(0f, 1f, 0.1f, false, skin);
        soundSlider.setValue(AudioManager.getInstance().getSoundVolume());

        // Sound enabled checkbox
        soundEnabled = new CheckBox(" Sound Enabled", skin);
        soundEnabled.setChecked(AudioManager.getInstance().isSoundEnabled());

        // Add listeners
        musicSlider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                AudioManager.getInstance().setMusicVolume(musicSlider.getValue());
                // Play a short music preview when adjusting
                if (musicEnabled.isChecked()) {
                    // Optional: Play a short music preview
                }
            }
        });

        musicEnabled.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                AudioManager.getInstance().setMusicEnabled(musicEnabled.isChecked());
            }
        });

        soundSlider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                AudioManager.getInstance().setSoundVolume(soundSlider.getValue());
                // Play a sound effect when adjusting
                if (soundEnabled.isChecked()) {
                    AudioManager.getInstance().playSound(AudioManager.SoundEffect.MENU_SELECT);
                }
            }
        });

        soundEnabled.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                AudioManager.getInstance().setSoundEnabled(soundEnabled.isChecked());
            }
        });

        // Save button
        TextButton saveButton = new TextButton("Save", skin);
        saveButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                saveAudioSettings();
                hideOptions();
            }
        });

        // Layout
        optionsTable.add(musicLabel).left().padBottom(10).row();
        optionsTable.add(musicSlider).width(200).padBottom(5).row();
        optionsTable.add(musicEnabled).left().padBottom(20).row();

        optionsTable.add(soundLabel).left().padBottom(10).row();
        optionsTable.add(soundSlider).width(200).padBottom(5).row();
        optionsTable.add(soundEnabled).left().padBottom(20).row();

        optionsTable.add(saveButton).width(100).padTop(20);

        optionsWindow.add(optionsTable);
        optionsWindow.pack();
        optionsWindow.setPosition(
            (Gdx.graphics.getWidth() - optionsWindow.getWidth()) / 2,
            (Gdx.graphics.getHeight() - optionsWindow.getHeight()) / 2
        );
        optionsWindow.setVisible(false);
        stage.addActor(optionsWindow);
    }

    private void showOptions() {
        menuWindow.setVisible(false);
        optionsWindow.setVisible(true);

        // Update slider and checkbox values to current settings
        musicSlider.setValue(AudioManager.getInstance().getMusicVolume());
        soundSlider.setValue(AudioManager.getInstance().getSoundVolume());
        musicEnabled.setChecked(AudioManager.getInstance().isMusicEnabled());
        soundEnabled.setChecked(AudioManager.getInstance().isSoundEnabled());
    }

    private void hideOptions() {
        optionsWindow.setVisible(false);
        menuWindow.setVisible(true);
    }

    // Update the resize method

    private void saveAudioSettings() {
        // Save to preferences
        Preferences prefs = Gdx.app.getPreferences("audio_settings");
        prefs.putFloat("music_volume", musicSlider.getValue());
        prefs.putFloat("sound_volume", soundSlider.getValue());
        prefs.putBoolean("music_enabled", musicEnabled.isChecked());
        prefs.putBoolean("sound_enabled", soundEnabled.isChecked());
        prefs.flush();

        // Show save confirmation
        Dialog dialog = new Dialog("Settings Saved", skin) {
            public void result(Object obj) {
                hide();
            }
        };
        dialog.text("Audio settings have been saved.");
        dialog.button("OK");
        dialog.show(stage);
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
        exitButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.performAutoSave();
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
        optionsButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                showOptions();
            }
        });

        // Create the options menu
        createOptionsMenu();
        stage.addActor(menuWindow);
    }

    // Add these helper methods from AutoSaveManager



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
        GameLogger.info("Attempting to save game");
        try {
            if (gameClient != null && player.getWorld() != null) {
                // Get current world
                World currentWorld = player.getWorld();

                // Get current player
                if (player.getUsername() == null) {
                    throw new Exception("Invalid player state");
                }

                // Create new PlayerData with explicit username
                PlayerData playerData = new PlayerData(player.getUsername());

                playerData.updateFromPlayer(player);

                // Save based on game mode
                if (gameClient.isSinglePlayer()) {
                    // Single player save
                    WorldData worldData = currentWorld.getWorldData();
                    worldData.savePlayerData(player.getUsername(), playerData);
                    game.getWorldManager().saveWorld(worldData);
//                    GameLogger.info(STR."Saved single player data for: \{player.getUsername()}");
                } else {
                    // Multiplayer save
                    gameClient.updateLastKnownState(playerData);
                    gameClient.savePlayerState(playerData);
//                    GameLogger.info(printlnSTR."Saved multiplayer data for: \{player.getUsername()}");
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
//                GameLogger.info(STR."client: \{gameClient == null} world: \{game.getCurrentWorld() == null}");
                throw new Exception("Game state is invalid");
            }
        } catch (Exception e) {
            GameLogger.info("Save error: " + e.getMessage());
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
        if (optionsWindow != null) {
            optionsWindow.setPosition(
                (width - optionsWindow.getWidth()) / 2,
                (height - optionsWindow.getHeight()) / 2
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

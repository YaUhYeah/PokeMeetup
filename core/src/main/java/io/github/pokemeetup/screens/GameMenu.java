package io.github.pokemeetup.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.pokemeetup.CreatureCaptureGame;
import io.github.pokemeetup.audio.AudioManager;
import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.data.PlayerData;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.data.WorldData;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.storage.JsonConfig;

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
        menuWindow.setVisible(false);
        hide();
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
        TextButton exitButton = new TextButton("Quit and Save to Title", skin);


        saveButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                saveGame();
            }
        });

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
                game.saveGame();
                game.setScreen(new ModeSelectionScreen(game)); // Replace with your title screen class
                AudioManager.getInstance().setMusicEnabled(false);
                gameScreen.dispose(); // Dispose of the current game screen
            }
        });


        menuTable.add(saveButton).row();
        menuTable.add(bagButton).row();
        menuTable.add(pokemonButton).row();
        menuTable.add(optionsButton).row();
        menuTable.add(exitButton).row();

        menuWindow.add(menuTable).pad(MENU_PADDING);
        menuWindow.pack();
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

        createOptionsMenu();
        stage.addActor(menuWindow);
    }

    public Stage getStage() {
        return stage;
    }

    public boolean isVisible() {
        return isVisible;
    }

    private void saveGame() {
        GameLogger.info("Attempting to save game");

        try {
            if (gameClient != null && player.getWorld() != null) {
                World currentWorld = player.getWorld();

                if (player.getUsername() == null) {
                    throw new Exception("Invalid player state");
                }

                PlayerData playerData = new PlayerData(player.getUsername());
                playerData.updateFromPlayer(player);

                if (gameClient.isSinglePlayer()) {
                    WorldData worldData = currentWorld.getWorldData();
                    worldData.savePlayerData(player.getUsername(), playerData);
                    game.getWorldManager().saveWorld(worldData);
                    playerData.updateFromPlayer(player); // Assuming this method exists
                    worldData.savePlayerData(player.getUsername(), playerData);
                    JsonConfig.saveWorldDataWithPlayer(worldData, playerData);
                    GameLogger.info("Game saved successfully");

                } else {
                    gameClient.savePlayerState(playerData);
                }

                showSaveSuccessDialog();
            } else {
                throw new Exception("Game state is invalid");
            }
        } catch (Exception e) {
            showSaveErrorDialog(e.getMessage());
        }
    }

    private void showSaveSuccessDialog() {
        Dialog dialog = new Dialog("Success", skin) {
            public void result(Object obj) {
                hide();
            }
        };
        dialog.text("Game saved successfully!");
        dialog.button("OK");
        dialog.show(stage);
    }

    private void showSaveErrorDialog(String errorMessage) {
        GameLogger.info("Save error: " + errorMessage);
        Dialog dialog = new Dialog("Error", skin) {
            public void result(Object obj) {
                hide();
            }
        };
        dialog.text("Failed to save game: " + errorMessage);
        dialog.button("OK");
        dialog.show(stage);
    }

    private void showNotImplementedMessage() {
        Dialog dialog = new Dialog("Notice", skin) {
            public void result(Object obj) {
                hide();
            }
        };
        dialog.text("This feature is not yet implemented.");
        dialog.button("OK");
        dialog.show(stage);
    }

    private void createOptionsMenu() {
        optionsWindow = new Window("Options", skin);
        optionsWindow.setMovable(false);

        Table optionsTable = new Table();
        optionsTable.pad(MENU_PADDING);

        Label musicLabel = new Label("Music Volume", skin);
        musicSlider = new Slider(0f, 1f, 0.1f, false, skin);
        musicSlider.setValue(AudioManager.getInstance().getMusicVolume());

        musicEnabled = new CheckBox(" Music Enabled", skin);
        musicEnabled.setChecked(AudioManager.getInstance().isMusicEnabled());

        Label soundLabel = new Label("Sound Volume", skin);
        soundSlider = new Slider(0f, 1f, 0.1f, false, skin);
        soundSlider.setValue(AudioManager.getInstance().getSoundVolume());

        soundEnabled = new CheckBox(" Sound Enabled", skin);
        soundEnabled.setChecked(AudioManager.getInstance().isSoundEnabled());

        setupAudioListeners();
        setupSaveButton(optionsTable); // Pass optionsTable to add the Save button
        setupCancelButton(optionsTable); // Optionally add a Cancel button

        optionsTable.add(musicLabel).left().padBottom(10).row();
        optionsTable.add(musicSlider).width(200).padBottom(5).row();
        optionsTable.add(musicEnabled).left().padBottom(20).row();
        optionsTable.add(soundLabel).left().padBottom(10).row();
        optionsTable.add(soundSlider).width(200).padBottom(5).row();
        optionsTable.add(soundEnabled).left().padBottom(20).row();

        optionsWindow.add(optionsTable);
        optionsWindow.pack();
        optionsWindow.setPosition(
            (Gdx.graphics.getWidth() - optionsWindow.getWidth()) / 2,
            (Gdx.graphics.getHeight() - optionsWindow.getHeight()) / 2
        );

        optionsWindow.setVisible(false);
        stage.addActor(optionsWindow);
    }

    private void setupAudioListeners() {
        musicSlider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                AudioManager.getInstance().setMusicVolume(musicSlider.getValue());
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
    }

    private void setupSaveButton(Table optionsTable) {
        TextButton saveButton = new TextButton("Save", skin);
        saveButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                saveAudioSettings();
                hideOptions();
            }
        });
        optionsTable.add(saveButton).width(BUTTON_WIDTH).height(BUTTON_HEIGHT).padTop(10).row();
    }

    private void setupCancelButton(Table optionsTable) {
        TextButton cancelButton = new TextButton("Cancel", skin);
        cancelButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                hideOptions();
            }
        });
        optionsTable.add(cancelButton).width(BUTTON_WIDTH).height(BUTTON_HEIGHT).padTop(10).row();
    }

    private void saveAudioSettings() {
        Preferences prefs = Gdx.app.getPreferences("audio_settings");
        prefs.putFloat("music_volume", musicSlider.getValue());
        prefs.putFloat("sound_volume", soundSlider.getValue());
        prefs.putBoolean("music_enabled", musicEnabled.isChecked());
        prefs.putBoolean("sound_enabled", soundEnabled.isChecked());
        prefs.flush();

        Dialog dialog = new Dialog("Settings Saved", skin) {
            public void result(Object obj) {
                hide();
            }
        };
        dialog.text("Audio settings have been saved.");
        dialog.button("OK");
        dialog.show(stage);
    }

    private void showOptions() {
        menuWindow.setVisible(false);
        optionsWindow.setVisible(true);
        musicSlider.setValue(AudioManager.getInstance().getMusicVolume());
        soundSlider.setValue(AudioManager.getInstance().getSoundVolume());
        musicEnabled.setChecked(AudioManager.getInstance().isMusicEnabled());
        soundEnabled.setChecked(AudioManager.getInstance().isSoundEnabled());
    }

    private void hideOptions() {
        optionsWindow.setVisible(false);
        menuWindow.setVisible(true);
    }

    public void render() {
        if (isVisible) {
            stage.act();
            stage.draw();
        }
    }

    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
        menuWindow.setPosition((width - menuWindow.getWidth()) / 2, (height - menuWindow.getHeight()) / 2);
        optionsWindow.setPosition((width - optionsWindow.getWidth()) / 2, (height - optionsWindow.getHeight()) / 2);
    }

    public void show() {
        isVisible = true;
        menuWindow.setVisible(true);
    }

    public void hide() {
        isVisible = false;
        menuWindow.setVisible(false);
    }

    public void dispose() {
        stage.dispose();
    }
}

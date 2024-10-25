package io.github.pokemeetup.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Timer;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.pokemeetup.CreatureCaptureGame;
import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.system.PlayerData;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.multiworld.WorldData;
import io.github.pokemeetup.system.gameplay.overworld.multiworld.WorldManager;
import io.github.pokemeetup.system.inventory.Inventory;

import java.text.SimpleDateFormat;
import java.util.Date;

public class WorldSelectionScreen implements Screen {
    private CreatureCaptureGame game;
    private Stage stage;
    private Skin skin;
    private WorldManager worldManager;
    private Table worldList;
    private GameClient gameClient;
    private String username;
    private BitmapFont font;

    public WorldSelectionScreen(CreatureCaptureGame game, GameClient gameClient, String username) {
        this.game = game;
        this.gameClient = gameClient;
        this.username = username;

        stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);

        skin = new Skin(Gdx.files.internal("Skins/uiskin.json"));
        worldManager = new WorldManager();
        worldManager.init();

        // Initialize font
        font = new BitmapFont(Gdx.files.internal("Fonts/pkmn.fnt"));
        font.getData().setScale(1.0f); // Adjust scale as needed

        createUI();
    }

    private void createUI() {
        Table mainTable = new Table();
        mainTable.setFillParent(true);

        // Title
        Label titleLabel = new Label("Select World", skin);
        titleLabel.setFontScale(2);
        mainTable.add(titleLabel).padBottom(20);
        mainTable.row();

        // World list
        worldList = new Table(skin);
        ScrollPane scrollPane = new ScrollPane(worldList, skin);
        mainTable.add(scrollPane).expand().fill().padBottom(20);
        mainTable.row();

        // Buttons
        Table buttonTable = new Table();
        TextButton createButton = new TextButton("Create New World", skin);
        TextButton deleteButton = new TextButton("Delete World", skin);
        TextButton backButton = new TextButton("Back", skin);

        buttonTable.add(createButton).padRight(10);
        buttonTable.add(deleteButton).padRight(10);
        buttonTable.add(backButton);

        mainTable.add(buttonTable);

        stage.addActor(mainTable);

        // Add listeners
        createButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                showCreateWorldDialog();
            }
        });

        deleteButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                showDeleteWorldDialog();
            }
        });

        backButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                game.setScreen(new ModeSelectionScreen(game));
                dispose();
            }
        });

        updateWorldList();
    }

    private void showCreateWorldDialog() {
        Dialog dialog = new Dialog("Create New World", skin) {
            @Override
            public void hide() {
                super.hide();
                remove(); // Ensure dialog is removed from stage
            }
        };

        Table content = new Table();
        content.pad(20);

        final TextField nameField = new TextField("", skin);
        final TextField seedField = new TextField("", skin);

        content.add("World Name:").padRight(10);
        content.add(nameField).width(200);
        content.row().padTop(10);
        content.add("Seed (optional):").padRight(10);
        content.add(seedField).width(200);

        // Buttons
        Table buttonTable = new Table();
        TextButton createButton = new TextButton("Create", skin);
        TextButton backButton = new TextButton("Back", skin);
        TextButton cancelButton = new TextButton("Cancel", skin);

        buttonTable.add(createButton).padRight(20);
        buttonTable.add(backButton).padRight(20);
        buttonTable.add(cancelButton);

        content.row().padTop(20);
        content.add(buttonTable).colspan(2);

        dialog.getContentTable().add(content);

        // Button Listeners
        createButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                String worldName = nameField.getText().trim();
                if (!worldName.isEmpty()) {
                    try {
                        long seed = seedField.getText().isEmpty() ?
                            System.currentTimeMillis() :
                            Long.parseLong(seedField.getText());

                        // Create and save the world
                        WorldData worldData = worldManager.createWorld(worldName, seed, 0.15f, 0.05f);

                        // Save initial player data for the world
                        saveInitialPlayerData(worldName, username);

                        dialog.hide();
                        game.setScreen(new GameScreen(game, username, gameClient,
                            World.DEFAULT_X_POSITION, World.DEFAULT_Y_POSITION, worldName));
                        dispose();
                    } catch (NumberFormatException e) {
                        showError("Invalid seed number.");
                    } catch (Exception e) {
                        showError("Failed to create world: " + e.getMessage());
                    }
                } else {
                    showError("Please enter a world name.");
                }
            }
        });

        backButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                dialog.hide();
                // Optionally, refresh the world list or perform other actions
                // For now, do nothing
            }
        });

        cancelButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                dialog.hide();
                // Optionally, navigate to another screen
            }
        });

        dialog.show(stage);
    }

    private void showDeleteWorldDialog() {
        Dialog deleteDialog = new Dialog("Delete World", skin) {
            @Override
            public void hide() {
                super.hide();
                remove(); // Ensure dialog is removed from stage
            }
        };

        Table content = new Table();
        content.pad(20);

        final TextField nameField = new TextField("", skin);

        content.add("World Name:").padRight(10);
        content.add(nameField).width(200);
        content.row().padTop(10);

        // Buttons
        Table buttonTable = new Table();
        TextButton deleteButton = new TextButton("Delete", skin);
        TextButton cancelButton = new TextButton("Cancel", skin);

        buttonTable.add(deleteButton).padRight(20);
        buttonTable.add(cancelButton);

        content.row().padTop(20);
        content.add(buttonTable).colspan(2);

        deleteDialog.getContentTable().add(content);

        // Button Listeners
        deleteButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                String worldName = nameField.getText().trim();
                if (!worldName.isEmpty()) {
                    try {
                        boolean success = worldManager.getWorlds().containsKey(worldName);
                        if (success) {
                            worldManager.deleteWorld(worldName);
                            deleteDialog.hide();
                            showInfo("World '" + worldName + "' deleted successfully.");
                            updateWorldList();
                        } else {
                            showError("World '" + worldName + "' does not exist.");
                        }
                    } catch (Exception e) {
                        showError("Error deleting world: " + e.getMessage());
                    }
                } else {
                    showError("Please enter a world name.");
                }
            }
        });

        cancelButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                deleteDialog.hide();
            }
        });

        deleteDialog.show(stage);


        cancelButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                deleteDialog.hide();
            }
        });

        deleteDialog.show(stage);
    }

    private void saveInitialPlayerData(String worldName, String username) {
        WorldData worldData = worldManager.getWorld(worldName);
        if (worldData != null) {
            // Initialize player's starting position and inventory
            PlayerData playerData = new PlayerData(username);
            worldData.addPlayer(username, playerData);
            worldManager.saveWorld(worldData);
            System.out.println("Initial player data saved for world: " + worldName + ", Username: " + username);
        } else {
            showError("World data not found for saving player data.");
        }
    }


    private void showError(String message) {
        Dialog errorDialog = new Dialog("Error", skin) {
            @Override
            public void hide() {
                super.hide();
                remove(); // Ensure dialog is removed from stage
            }
        };

        Label errorLabel = new Label(message, new Label.LabelStyle(font, Color.RED));
        errorLabel.setWrap(true);

        errorDialog.getContentTable().add(errorLabel).width(300).pad(20);
        errorDialog.button("OK", true);
        errorDialog.show(stage);
    }

    private void showInfo(String message) {
        Dialog infoDialog = new Dialog("Info", skin) {
            @Override
            public void hide() {
                super.hide();
                remove(); // Ensure dialog is removed from stage
            }
        };

        Label infoLabel = new Label(message, new Label.LabelStyle(font, Color.GREEN));
        infoLabel.setWrap(true);

        infoDialog.getContentTable().add(infoLabel).width(300).pad(20);
        infoDialog.button("OK", true);
        infoDialog.show(stage);
    }

    private void updateWorldList() {
        worldList.clear();

        for (WorldData world : worldManager.getWorlds().values()) {
            Table worldEntry = new Table(skin);
            Label nameLabel = new Label(world.getName(), skin);
            Label lastPlayedLabel = new Label(formatDate(world.getLastPlayed()), skin);

            worldEntry.add(nameLabel).expandX().left().padRight(10);
            worldEntry.add(lastPlayedLabel).right();

            // Add click listener to select the world
            worldEntry.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    selectWorld(world.getName());
                }
            });

            worldList.add(worldEntry).expandX().fillX().padBottom(5);
            worldList.row();
        }
    }

    private String formatDate(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(new Date(timestamp));
    }

    private void selectWorld(String worldName) {
        try {
            // Initialize the selected world
            game.initializeWorld(worldName, gameClient.isSinglePlayer());

            // Save initial player data if necessary
            saveInitialPlayerData(worldName, username);

            // Transition to GameScreen
            game.setScreen(new GameScreen(game, username, gameClient,
                World.DEFAULT_X_POSITION, World.DEFAULT_Y_POSITION, worldName));
            dispose();
        } catch (Exception e) {
            showError("Failed to load world: " + e.getMessage());
        }
    }

    @Override
    public void show() {

    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        stage.act(delta);
        stage.draw();
    }

    // Implement other required methods (resize, pause, resume, hide, dispose)
    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void hide() {
    }

    @Override
    public void dispose() {
        stage.dispose();
        skin.dispose();
        font.dispose();
    }
}

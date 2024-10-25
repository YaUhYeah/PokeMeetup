package io.github.pokemeetup.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Timer;
import io.github.pokemeetup.CreatureCaptureGame;
import io.github.pokemeetup.managers.ServerStatusChecker;
import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.multiplayer.client.GameClientSingleton;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.multiworld.WorldData;
import io.github.pokemeetup.system.gameplay.overworld.multiworld.WorldManager;
import io.github.pokemeetup.system.inventory.Inventory;
import io.github.pokemeetup.system.inventory.Item;

import java.io.IOException;


public class ModeSelectionScreen implements Screen {
    private CreatureCaptureGame game;
    private Stage stage;
    private TextButton singlePlayerButton;
    private TextButton multiplayerButton;
    private Label serverStatusLabel;
    private Skin skin;
    private Timer timer;
    private WorldManager worldManager;

    public ModeSelectionScreen(CreatureCaptureGame game) {
        this.game = game;
        this.stage = new Stage();
        Gdx.input.setInputProcessor(stage);

        this.worldManager = new WorldManager();
        skin = new Skin(Gdx.files.internal("Skins/uiskin.json"));

        setupUI();
        checkServerAvailability();
    }

    private void setupUI() {
        Table table = new Table();
        table.setFillParent(true);
        stage.addActor(table);

        Label titleLabel = new Label("PokeMeetup", skin);
        titleLabel.setFontScale(2);
        titleLabel.setAlignment(Align.center);

        singlePlayerButton = new TextButton("Single Player", skin);
        multiplayerButton = new TextButton("Multiplayer", skin);
        serverStatusLabel = new Label("", skin);

        table.add(titleLabel).colspan(2).padBottom(20);
        table.row();
        table.add(singlePlayerButton).width(200).height(50).padBottom(10);
        table.row();
        table.add(multiplayerButton).width(200).height(50);

        singlePlayerButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                GameClient singlePlayerClient = null;
                try {
                    singlePlayerClient = GameClientSingleton.getSinglePlayerInstance();
                    showWorldSelectionDialog(singlePlayerClient, "Player");
                } catch (IOException e) {
                    showError("Failed to initialize game client: " + e.getMessage());
                }
            }
        });

        multiplayerButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                game.setScreen(new LoginScreen(game));
                dispose();
            }
        });

        multiplayerButton.setDisabled(true);
    }

    private void updateWorldList(List<String> worldList) {
        // Refresh worlds from disk
        worldManager.refreshWorlds();

        // Update list items
        Array<String> worldNames = new Array<>();
        worldManager.getWorlds().keySet().forEach(worldNames::add);
        worldList.setItems(worldNames);
    }

    private void showWorldSelectionDialog(GameClient client, String username) {
        Dialog dialog = new Dialog("Select World", skin);
        Table contentTable = new Table();
        contentTable.setSkin(skin);
        contentTable.pad(20);

        // World list
        List<String> worldList = new List<>(skin);
        updateWorldList(worldList);

        ScrollPane scrollPane = new ScrollPane(worldList, skin);
        contentTable.add(scrollPane).width(300).height(200);
        contentTable.row();

        // Buttons
        Table buttonTable = new Table();
        TextButton selectButton = new TextButton("Select", skin);
        TextButton createButton = new TextButton("Create New", skin);
        TextButton deleteButton = new TextButton("Delete", skin);
        TextButton backButton = new TextButton("Back", skin);

        buttonTable.add(selectButton).padRight(10);
        buttonTable.add(createButton).padRight(10);
        buttonTable.add(deleteButton).padRight(10);
        buttonTable.add(backButton);

        contentTable.add(buttonTable).padTop(20);
        dialog.getContentTable().add(contentTable);

        // Button listeners
        selectButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                String selected = worldList.getSelected();
                if (selected != null) {
                    WorldData world = worldManager.getWorld(selected);
                    if (world != null) {
                        game.setScreen(new GameScreen(game,username, client,
                            World.DEFAULT_X_POSITION, World.DEFAULT_Y_POSITION, selected));
                        dialog.hide();
                        dispose();
                    }
                }
            }
        });

        createButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                dialog.hide();
                showCreateWorldDialog(client, username);
            }
        });

        deleteButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                String selected = worldList.getSelected();
                if (selected != null) {
                    showDeleteConfirmation(selected, worldList);
                }
            }
        });

        backButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                dialog.hide();
                // Return to previous screen (likely ModeSelectionScreen)
                game.setScreen(new ModeSelectionScreen(game));
                // Clean up resources if needed
                dispose();
            }
        });




        // Existing button listeners...

        // Add listener for dialog close button (X)
        dialog.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                if (dialog.isModal() && x < 0 || x > dialog.getWidth() || y < 0 || y > dialog.getHeight()) {
                    backButton.fire(new ChangeListener.ChangeEvent());
                    return true;
                }
                return false;
            }
        });

        dialog.show(stage);
    }

    private void showCreateWorldDialog(GameClient client, String username) {
        Dialog dialog = new Dialog("Create New World", skin);
        dialog.setModal(true);
        dialog.setMovable(false);

        Table content = new Table();
        content.setSkin(skin);
        content.pad(20);

        final TextField nameField = new TextField("", skin);
        final TextField seedField = new TextField("", skin);

        content.add("World Name:").padRight(10);
        content.add(nameField).width(200);
        content.row().padTop(10);
        content.add("Seed (optional):").padRight(10);
        content.add(seedField).width(200);

        // Add buttons in a row
        Table buttonTable = new Table();
        buttonTable.pad(20, 0, 0, 0);

        TextButton createButton = new TextButton("Create", skin);
        TextButton backButton = new TextButton("Back", skin);
        TextButton cancelButton = new TextButton("Cancel", skin);

        buttonTable.add(createButton).padRight(20);
        buttonTable.add(backButton).padRight(20);
        buttonTable.add(cancelButton);

        content.row();
        content.add(buttonTable).colspan(2).center();

        dialog.getContentTable().add(content);

        // Button listeners
        createButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                String worldName = nameField.getText().trim();
                if (!worldName.isEmpty()) {
                    try {
                        long seed = seedField.getText().isEmpty() ?
                            System.currentTimeMillis() :
                            Long.parseLong(seedField.getText());

                        WorldData worldData = new WorldData(worldName, System.currentTimeMillis(),
                            new WorldData.WorldConfig(seed));

                        // Ensure world directory exists
                        worldManager.ensureWorldDirectory(worldName);

                        // Create and save the world
                        worldManager.createWorld(worldName, seed, 0.15f, 0.05f);
                        game.initializeWorld(worldName, false);

                        dialog.hide();
                        game.setScreen(new GameScreen(game,username, client,
                            World.DEFAULT_X_POSITION, World.DEFAULT_Y_POSITION, worldName));
                        dispose();
                    } catch (Exception e) {
                        showError("Failed to create world: " + e.getMessage());
                    }
                } else {
                    showError("Please enter a world name");
                }
            }
        });

        backButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                dialog.hide();
                showWorldSelectionDialog(client, username);
            }
        });

        cancelButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                dialog.hide();
                // Return to mode selection screen
                game.setScreen(new ModeSelectionScreen(game));
                dispose();
            }
        });

        dialog.show(stage);
    }

    private void showDeleteConfirmation(String worldName, List<String> worldList) {
        Dialog confirm = new Dialog("Confirm Delete", skin);
        confirm.text("Are you sure you want to delete " + worldName + "?");

        // Create buttons
        TextButton yesButton = new TextButton("Yes", skin);
        TextButton noButton = new TextButton("No", skin);

        // Add listeners
        yesButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                worldManager.deleteWorld(worldName);
                Array<String> updatedNames = new Array<>();
                worldManager.getWorlds().keySet().forEach(updatedNames::add);
                worldList.setItems(updatedNames);
                confirm.hide();
            }
        });

        noButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                confirm.hide();
            }
        });

        // Add buttons to dialog
        confirm.getButtonTable().add(yesButton).padRight(20);
        confirm.getButtonTable().add(noButton);

        confirm.show(stage);
    }

    private void showError(String message) {
        Dialog error = new Dialog("Error", skin);
        error.text(message);
        error.button("OK");
        error.show(stage);
    }

    private void checkServerAvailability() {
        timer = new Timer();
        Timer.Task checkTask = new Timer.Task() {
            @Override
            public void run() {
                ServerStatusChecker.checkServerAvailability(isAvailable -> {
                    Gdx.app.postRunnable(() -> {
                        if (isAvailable) {
                            multiplayerButton.setDisabled(false);
                            serverStatusLabel.setText("");
                        } else {
                            multiplayerButton.setDisabled(true);
                            serverStatusLabel.setText("Multiplayer server is currently unavailable.");
                        }
                    });
                });
            }
        };
        timer.scheduleTask(checkTask, 0, 10); // Check every 10 seconds
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
    public void show() {
    }

    @Override
    public void dispose() {
        stage.dispose();
        skin.dispose();
    }
}

package io.github.pokemeetup.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.pokemeetup.CreatureCaptureGame;
import io.github.pokemeetup.multiplayer.client.GameClientSingleton;
import io.github.pokemeetup.system.PlayerData;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.multiworld.WorldData;

import java.text.SimpleDateFormat;
import java.util.Date;

public class WorldSelectionScreen implements Screen {
    private static final String DEFAULT_PLAYER_NAME = "Player";
    private final CreatureCaptureGame game;
    private final Stage stage;
    private final Skin skin;

    // UI Components
    private Table mainTable;
    private ScrollPane worldListScroll;
    private Table worldListTable;
    private Table infoPanel;
    private WorldData selectedWorld;

    // Buttons
    private TextButton playButton;
    private TextButton createButton;
    private TextButton deleteButton;
    private TextButton backButton;

    // Tabs
    private ButtonGroup<TextButton> tabGroup;
    private String currentTab = "All";

    public WorldSelectionScreen(CreatureCaptureGame game) {
        this.game = game;
        this.stage = new Stage(new ScreenViewport());
        this.skin = new Skin(Gdx.files.internal("Skins/uiskin.json"));

        Gdx.input.setInputProcessor(stage);
        createUI();
        refreshWorldList();
    }

    private void createUI() {
        // Main layout
        mainTable = new Table();
        mainTable.setFillParent(true);
        mainTable.pad(20);

        // Title
        Label titleLabel = new Label("Select World", skin, "title");
        mainTable.add(titleLabel).colspan(3).pad(10);
        mainTable.row();

        // Tab buttons
        Table tabTable = new Table();
        tabGroup = new ButtonGroup<>();

        String[] tabs = {"All", "Recent", "Multiplayer"};
        for (String tab : tabs) {
            TextButton tabButton = new TextButton(tab, skin, "toggle");
            tabGroup.add(tabButton);
            tabTable.add(tabButton).pad(5);

            tabButton.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    if (tabButton.isChecked()) {
                        currentTab = tab;
                        refreshWorldList();
                    }
                }
            });
        }
        tabGroup.getButtons().get(0).setChecked(true);

        mainTable.add(tabTable).colspan(3).pad(10);
        mainTable.row();

        // World list
        worldListTable = new Table();
        worldListTable.top();
        worldListScroll = new ScrollPane(worldListTable, skin);
        worldListScroll.setFadeScrollBars(false);

        // Info panel
        infoPanel = new Table(skin);
        infoPanel.background("default-pane");
        infoPanel.pad(10);

        // Layout main sections
        Table contentTable = new Table();
        contentTable.add(worldListScroll).width(400).expandY().fillY().padRight(20);
        contentTable.add(infoPanel).width(300).expandY().fillY();

        mainTable.add(contentTable).expand().fill();
        mainTable.row();

        // Bottom buttons
        Table buttonTable = new Table();

        createButton = new TextButton("Create New World", skin);
        playButton = new TextButton("Play Selected World", skin);
        deleteButton = new TextButton("Delete World", skin);
        backButton = new TextButton("Back", skin);

        playButton.setDisabled(true);
        deleteButton.setDisabled(true);

        buttonTable.add(createButton).pad(5).width(200);
        buttonTable.add(playButton).pad(5).width(200);
        buttonTable.add(deleteButton).pad(5).width(200);
        buttonTable.row();
        buttonTable.add(backButton).colspan(3).width(200).pad(5);

        mainTable.add(buttonTable).colspan(3).pad(10);

        // Add listeners
        addButtonListeners();

        stage.addActor(mainTable);
    }
// Update the createWorldEntry method in WorldSelectionScreen:

    private Table createWorldEntry(WorldData world) {
        Table entry = new Table(skin);
        entry.setBackground(skin.newDrawable("default-pane"));
        entry.pad(10);

        // Create clickable button instead of just labels
        TextButton worldButton = new TextButton(world.getName(), skin);
        worldButton.align(Align.left);
        Label timeLabel = new Label("Last played: " + formatDate(world.getLastPlayed()), skin, "small");

        entry.add(worldButton).expandX().fillX();
        entry.row();
        entry.add(timeLabel).expandX().left().padTop(5);

        // Add click listener to the button
        worldButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                // Update visual selection
                resetWorldEntryStyles();
                entry.setBackground(skin.newDrawable("default-pane", new Color(0.4f, 0.4f, 0.4f, 1))); // Highlight selected

                selectWorld(world);
            }
        });

        // Store reference to track selection
        entry.setUserObject(world);

        return entry;
    }

    // Add this helper method to reset selection styling
    private void resetWorldEntryStyles() {
        for (Actor actor : worldListTable.getChildren()) {
            if (actor instanceof Table) {
                ((Table) actor).setBackground(skin.newDrawable("default-pane"));
            }
        }
    }

    // Update the refreshWorldList method to maintain selection:
    private void refreshWorldList() {
        worldListTable.clear();
        WorldData previousSelection = selectedWorld; // Store current selection

        for (WorldData world : game.getWorldManager().getWorlds().values()) {
            if (!shouldShowWorld(world)) {
                continue;
            }

            Table worldEntry = createWorldEntry(world);

            // Restore selection highlight if this was the selected world
            if (world.equals(previousSelection)) {
                worldEntry.setBackground(skin.newDrawable("default-pane", new Color(0.4f, 0.4f, 0.4f, 1)));
                selectedWorld = world;
            }

            worldListTable.add(worldEntry).expandX().fillX().pad(5);
            worldListTable.row();
        }

        // Update button states
        playButton.setDisabled(selectedWorld == null);
        deleteButton.setDisabled(selectedWorld == null);

        // Update info panel
        updateInfoPanel();
    }

    // Add hover effect by implementing these methods in createWorldEntry:
    private void addHoverEffect(Table entry) {
        entry.addListener(new InputListener() {
            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                if (entry.getUserObject() != selectedWorld) {
                    entry.setBackground(skin.newDrawable("default-pane", new Color(0.3f, 0.3f, 0.3f, 1)));
                }
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                if (entry.getUserObject() != selectedWorld) {
                    entry.setBackground(skin.newDrawable("default-pane"));
                }
            }
        });
    }

    private void addButtonListeners() {
        createButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                showCreateWorldDialog();
            }
        });

        playButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (selectedWorld != null) {
                    loadSelectedWorld();
                }
            }
        });

        deleteButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (selectedWorld != null) {
                    showDeleteConfirmDialog();
                }
            }
        });

        backButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                game.setScreen(new ModeSelectionScreen(game));
                dispose();
            }
        });
    }



    private boolean shouldShowWorld(WorldData world) {
        switch (currentTab) {
            case "Recent":
                return (System.currentTimeMillis() - world.getLastPlayed()) < (24 * 60 * 60 * 1000);
            case "Multiplayer":
                return world.getName().equals(CreatureCaptureGame.MULTIPLAYER_WORLD_NAME);
            default:
                return true;

        }
    }

    private void selectWorld(WorldData world) {
        selectedWorld = world;
        updateInfoPanel();
        playButton.setDisabled(false);
        deleteButton.setDisabled(false);
    }

    private void updateInfoPanel() {
        infoPanel.clear();

        if (selectedWorld == null) {
            infoPanel.add(new Label("Select a world to view details", skin)).expand();
            return;
        }

        infoPanel.defaults().left().pad(5);

        infoPanel.add(new Label(selectedWorld.getName(), skin, "title")).expandX();
        infoPanel.row();
        infoPanel.add(new Label("Last played: " + formatDate(selectedWorld.getLastPlayed()), skin));
        infoPanel.row();
        infoPanel.add(new Label("World size: " + World.WORLD_SIZE + "x" + World.WORLD_SIZE, skin));
        infoPanel.row();
        infoPanel.add(new Label("Seed: " + selectedWorld.getSeed(), skin));
    }

    private String formatDate(long timestamp) {
        if (timestamp == 0) return "Never";
        return new SimpleDateFormat("MMM d, yyyy HH:mm").format(new Date(timestamp));
    }

    private void showCreateWorldDialog() {
        Dialog dialog = new Dialog("Create New World", skin) {
            @Override
            protected void result(Object object) {
                if ((Boolean) object) {
                    TextField nameField = findActor("nameField");
                    TextField seedField = findActor("seedField");

                    String worldName = nameField.getText().trim();
                    String seedText = seedField.getText().trim();

                    try {
                        long seed = seedText.isEmpty() ? System.currentTimeMillis() : Long.parseLong(seedText);
                        createNewWorld(worldName, seed);
                    } catch (NumberFormatException e) {
                        showError("Invalid seed number");
                    }
                }
            }
        };

        Table content = new Table(skin);
        content.pad(20);

        TextField nameField = new TextField("", skin);
        nameField.setName("nameField");
        nameField.setMessageText("World name");

        TextField seedField = new TextField("", skin);
        seedField.setName("seedField");
        seedField.setMessageText("Seed (optional)");

        content.add(new Label("World Name:", skin)).left();
        content.row();
        content.add(nameField).width(300).pad(5);
        content.row();
        content.add(new Label("Seed:", skin)).left();
        content.row();
        content.add(seedField).width(300).pad(5);

        dialog.getContentTable().add(content);
        dialog.button("Create", true);
        dialog.button("Cancel", false);
        dialog.show(stage);
    }

    private void createNewWorld(String name, long seed) {
        if (name.isEmpty()) {
            showError("World name cannot be empty");
            return;
        }

        try {
            WorldData world = game.getWorldManager().createWorld(name, seed, 0.15f, 0.05f);
            PlayerData playerData = new PlayerData(DEFAULT_PLAYER_NAME);
            world.savePlayerData(DEFAULT_PLAYER_NAME, playerData);
            game.getWorldManager().saveWorld(world);

            refreshWorldList();
            selectWorld(world);
        } catch (Exception e) {
            showError("Failed to create world: " + e.getMessage());
        }
    }

    private void showDeleteConfirmDialog() {
        Dialog dialog = new Dialog("Delete World", skin) {
            @Override
            protected void result(Object object) {
                if ((Boolean) object) {
                    deleteSelectedWorld();
                }
            }
        };

        dialog.text("Are you sure you want to delete '" + selectedWorld.getName() + "'?\nThis cannot be undone!");
        dialog.button("Delete", true);
        dialog.button("Cancel", false);
        dialog.show(stage);
    }

    private void deleteSelectedWorld() {
        try {
            game.getWorldManager().deleteWorld(selectedWorld.getName());
            selectedWorld = null;
            refreshWorldList();
            updateInfoPanel();
            playButton.setDisabled(true);
            deleteButton.setDisabled(true);
        } catch (Exception e) {
            showError("Failed to delete world: " + e.getMessage());
        }
    }

    private void loadSelectedWorld() {
        try {
            game.initializeWorld(selectedWorld.getName(), false);
            PlayerData playerData = selectedWorld.getPlayerData(DEFAULT_PLAYER_NAME);

            if (playerData == null) {
                playerData = new PlayerData(DEFAULT_PLAYER_NAME);
                selectedWorld.savePlayerData(DEFAULT_PLAYER_NAME, playerData);
                game.getWorldManager().saveWorld(selectedWorld);
            }

            game.setScreen(new GameScreen(
                game,
                DEFAULT_PLAYER_NAME,
                GameClientSingleton.getSinglePlayerInstance(),
                (int) playerData.getX(),
                (int) playerData.getY(),
                selectedWorld.getName()
            ));
            dispose();
        } catch (Exception e) {
            showError("Failed to load world: " + e.getMessage());
        }
    }

    private void showError(String message) {
        Dialog dialog = new Dialog("Error", skin);
        dialog.text(message);
        dialog.button("OK");
        dialog.show(stage);
    }

    @Override
    public void show() {
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0.2f, 0.2f, 0.2f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        stage.act(delta);
        stage.draw();
    }

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
    }
}

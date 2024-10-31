package io.github.pokemeetup.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.SerializationException;
import com.badlogic.gdx.utils.Timer;
import io.github.pokemeetup.CreatureCaptureGame;
import io.github.pokemeetup.multiplayer.client.GameClientSingleton;
import io.github.pokemeetup.system.gameplay.overworld.multiworld.WorldManager;
import io.github.pokemeetup.utils.GameLogger;

import java.io.IOException;

public class ModeSelectionScreen implements Screen {
    private final CreatureCaptureGame game;
    private final Stage stage;
    private final Skin skin;
    private final Timer timer;
    private BitmapFont font;

    public ModeSelectionScreen(CreatureCaptureGame game) {
        this.game = game;
        this.stage = new Stage();
        Gdx.input.setInputProcessor(stage);

        this.skin = new Skin();
        this.timer = new Timer();

        try {
            this.font = initializeSkin();
            Gdx.app.log("SkinSetup", "Successfully initialized the skin.");
        } catch (Exception e) {
            showError("Failed to initialize UI: " + e.getMessage());
            Gdx.app.error("SkinSetup", "Failed to initialize UI", e);
            return;
        }

        createUI();
    }

    private BitmapFont initializeSkin() {
        // Add BitmapFont
        BitmapFont font = new BitmapFont(); // Default font
        skin.add("default", font);

        // Define Colors
        skin.add("white", new Color(1, 1, 1, 1));
        skin.add("black", new Color(0, 0, 0, 1));
        skin.add("gray", new Color(0.8f, 0.8f, 0.8f, 1));

        // Create drawables
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        skin.add("white", new Texture(pixmap));

        // Create a TextureRegion for the window background
        TextureRegion windowBackground = new TextureRegion(new Texture(pixmap));
        skin.add("window", windowBackground);

        // Create panel-background drawable
        pixmap.setColor(new Color(0.2f, 0.2f, 0.2f, 1));
        pixmap.fill();
        skin.add("panel-background", new TextureRegionDrawable(new TextureRegion(new Texture(pixmap))));

        // Clean up the pixmap
        pixmap.dispose();

        // Create styles
        TextButton.TextButtonStyle textButtonStyle = new TextButton.TextButtonStyle();
        textButtonStyle.up = skin.newDrawable("white", Color.DARK_GRAY);
        textButtonStyle.down = skin.newDrawable("white", Color.DARK_GRAY);
        textButtonStyle.checked = skin.newDrawable("white", Color.BLUE);
        textButtonStyle.over = skin.newDrawable("white", Color.LIGHT_GRAY);
        textButtonStyle.font = skin.getFont("default");
        skin.add("default", textButtonStyle);

        Label.LabelStyle labelStyle = new Label.LabelStyle();
        labelStyle.font = skin.getFont("default");
        skin.add("default", labelStyle);

        // Create and add WindowStyle
        Window.WindowStyle windowStyle = new Window.WindowStyle();
        windowStyle.titleFont = skin.getFont("default");
        windowStyle.background = skin.newDrawable("window", new Color(0.2f, 0.2f, 0.2f, 0.8f));
        windowStyle.titleFontColor = Color.WHITE;
        skin.add("default", windowStyle);

        return font;
    }

    // Helper method to create a solid color drawable


    private void createUI() {
        // Main container table
        Table mainContainer = new Table();
        mainContainer.setFillParent(true);
        mainContainer.pad(50);

        // Add the main container to the stage
        stage.addActor(mainContainer);

        Label titleLabel = new Label("PokeMeetup", skin);
        titleLabel.setFontScale(3.0f);

        Label versionLabel = new Label("Version 1.0", skin);
        versionLabel.setColor(skin.getColor("white"));

        // Create Buttons
        TextButton singlePlayerButton = new TextButton("Single Player", skin);
        TextButton multiplayerButton = new TextButton("Multiplayer", skin);
        TextButton exitButton = new TextButton("Exit Game", skin);
        // Arrange UI elements using Table
        mainContainer.add(titleLabel).padBottom(10).row();
        mainContainer.add(versionLabel).padBottom(50).row();
        mainContainer.add(singlePlayerButton).width(300).height(70).padBottom(30).row();
        mainContainer.add(multiplayerButton).width(300).height(70).padBottom(30).row();
        mainContainer.add(exitButton).width(300).height(70).padBottom(30).row();

        // Add button listeners
        singlePlayerButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                try {
                    GameClientSingleton.getSinglePlayerInstance();
                    game.setScreen(new WorldSelectionScreen(game));
                    dispose();
                } catch (IOException e) {
                    showError("Failed to start single player mode: " + e.getMessage());
                }
            }
        });

        multiplayerButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                try {
                    game.setScreen(new LoginScreen(game));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                dispose();
            }
        });

        exitButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                Gdx.app.exit();
            }
        });
    }

    private void showError(String message) {
        try {
            Dialog dialog = new Dialog("Error", skin);
            dialog.text(message);
            dialog.button("OK");
            dialog.show(stage);
        } catch (Exception e) {
            // Fallback in case Dialog creation fails
            Gdx.app.error("DialogError", "Failed to show error dialog: " + e.getMessage());
            GameLogger.info("Error Dialog failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0, 0, 0, 1); // Clear with black
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void dispose() {
        stage.dispose();
        skin.dispose();
        font.dispose();
        if (timer != null) {
            timer.clear();
        }
    }

    // Other required Screen methods...
    @Override
    public void show() {
    }

    @Override
    public void hide() {
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }
}

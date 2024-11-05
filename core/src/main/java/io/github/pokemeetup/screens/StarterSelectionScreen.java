package io.github.pokemeetup.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.pokemeetup.CreatureCaptureGame;
import io.github.pokemeetup.pokemon.Pokemon;
import io.github.pokemeetup.pokemon.attacks.Move;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.TextureManager;

import java.util.Arrays;

public class StarterSelectionScreen implements Screen {
    private final CreatureCaptureGame game;
    private final Stage stage;
    private final SpriteBatch batch;
    private final Skin skin;
    private Screen returnScreen;  // Add this
    // Keep track of which starter is currently selected
    private Pokemon selectedStarter;
    private Table selectedCell = null; // Replace selectedStarterImage with this

    // Store references to UI elements we need to update
    private Label pokemonInfoLabel;
    private TextButton confirmButton;
    private Table lastSelectedCell = null; // Add this field to track the last selected cell

    public StarterSelectionScreen(CreatureCaptureGame game, Skin skin) {
        this.game = game;
        this.skin = skin;
        this.stage = new Stage(new ScreenViewport());
        this.batch = new SpriteBatch();

        setupUI();
        Gdx.input.setInputProcessor(stage);
    }

    public void setReturnScreen(Screen screen) {
        this.returnScreen = screen;
    }

    private void setupUI() {    GameLogger.info("Available UI regions:");
        for (TextureAtlas.AtlasRegion region : TextureManager.ui.getRegions()) {
            GameLogger.info("- " + region.name);
        }
        // Main container with background from atlas
        Table mainTable = new Table();
        mainTable.setFillParent(true);
        mainTable.setBackground(new TextureRegionDrawable(TextureManager.ui.findRegion("window")));

        // Title container
        Table titleContainer = new Table();
        titleContainer.pad(10);

        Label welcomeLabel = new Label("Welcome to Your Journey!", skin);
        welcomeLabel.setFontScale(1.2f);
        welcomeLabel.setAlignment(Align.center);
        titleContainer.add(welcomeLabel).expandX().pad(5).row();

        Label chooseLabel = new Label("Select Your First Partner", skin);
        chooseLabel.setAlignment(Align.center);
        titleContainer.add(chooseLabel).expandX().pad(5);

        mainTable.add(titleContainer).expandX().fillX().pad(10).row();

        // Starter options container
        Table starterContainer = new Table();
        starterContainer.defaults().pad(5).space(10);

        addStarterOption(starterContainer, "BULBASAUR",
            "A reliable partner with a mysterious bulb that grows on its back. Perfect for trainers who value strategy.");
        addStarterOption(starterContainer, "CHARMANDER",
            "The flame on its tail burns brightly. Known for its fierce determination and growing strength.");
        addStarterOption(starterContainer, "SQUIRTLE",
            "A water-type Pokemon that's both defensive and agile. Its shell provides excellent protection.");

        // Use ScrollPane for small screens
        ScrollPane scrollPane = new ScrollPane(starterContainer, skin);
        scrollPane.setScrollingDisabled(false, true);
        mainTable.add(scrollPane).expand().fill().pad(10).row();

        // Info panel with background from atlas
        Table infoPanel = new Table();
        infoPanel.setBackground(new TextureRegionDrawable(TextureManager.ui.findRegion("slot_normal")));

        pokemonInfoLabel = new Label("Click on a Pokemon to learn more!", skin);
        pokemonInfoLabel.setWrap(true);
        pokemonInfoLabel.setAlignment(Align.center);
        infoPanel.add(pokemonInfoLabel).width(Gdx.graphics.getWidth() * 0.8f).pad(10);

        mainTable.add(infoPanel).expandX().fillX().pad(10).height(100).row();

        // Confirm button
        confirmButton = new TextButton("Choose Pokemon!", skin);
        confirmButton.setDisabled(true);
        confirmButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (!confirmButton.isDisabled()) {
                    confirmSelection();
                }
            }
        });

        mainTable.add(confirmButton).pad(20);

        stage.addActor(mainTable);
    }

    private void addStarterOption(Table container, final String pokemonName, String description) {
        Table pokemonCell = new Table();
        pokemonCell.setBackground(new TextureRegionDrawable(TextureManager.ui.findRegion("slot_normal")));

        TextureRegion frontSprite = TextureManager.getPokemonfront().findRegion(pokemonName + "_front");
        if (frontSprite != null) {
            Image pokemonImage = new Image(frontSprite);
            pokemonImage.setScaling(Scaling.fit);

            pokemonCell.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    selectStarter(pokemonName, description);

                    // Update visual selection using the Tables directly
                    if (selectedCell != null) {
                        selectedCell.setBackground(
                            new TextureRegionDrawable(TextureManager.ui.findRegion("slot_normal"))
                        );
                    }

                    pokemonCell.setBackground(
                        new TextureRegionDrawable(TextureManager.ui.findRegion("slot_selected"))
                    );
                    selectedCell = pokemonCell;
                }

                @Override
                public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                    if (pokemonCell != selectedCell) {
                        pokemonImage.setColor(0.8f, 0.8f, 1f, 1f);
                    }
                }

                @Override
                public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                    if (pokemonCell != selectedCell) {
                        pokemonImage.setColor(Color.WHITE);
                    }
                }
            });

            pokemonCell.add(pokemonImage).size(96).pad(5).row();
        }

        Label nameLabel = new Label(pokemonName, skin);
        nameLabel.setFontScale(0.8f);
        nameLabel.setAlignment(Align.center);
        pokemonCell.add(nameLabel).pad(5).row();

        container.add(pokemonCell).width(150).height(180);

    }

    private void selectStarter(String pokemonName, String description) {
        selectedStarter = new Pokemon(pokemonName, 5);
        setupStarterPokemon(selectedStarter);

        // Enable confirm button
        confirmButton.setDisabled(false);

        // Update the info label
        pokemonInfoLabel.setText(description);
    }

    private void setupStarterPokemon(Pokemon starter) {
        switch (starter.getName()) {
            case "BULBASAUR":
                starter.setPrimaryType(Pokemon.PokemonType.GRASS);
                starter.setSecondaryType(Pokemon.PokemonType.POISON);
                starter.getMoves().add(new Move("Tackle", Pokemon.PokemonType.NORMAL, 40, 100, 35, false, "A basic tackle attack"));
                starter.getMoves().add(new Move("Growl", Pokemon.PokemonType.NORMAL, 0, 100, 40, true, "Lowers opponent's attack"));
                starter.getMoves().add(new Move("Vine Whip", Pokemon.PokemonType.GRASS, 45, 100, 25, false, "Strikes with vines"));
                break;
            case "CHARMANDER":
                starter.setPrimaryType(Pokemon.PokemonType.FIRE);
                starter.getMoves().add(new Move("Scratch", Pokemon.PokemonType.NORMAL, 40, 100, 35, false, "Scratches with sharp claws"));
                starter.getMoves().add(new Move("Growl", Pokemon.PokemonType.NORMAL, 0, 100, 40, true, "Lowers opponent's attack"));
                starter.getMoves().add(new Move("Ember", Pokemon.PokemonType.FIRE, 40, 100, 25, true, "A weak fire attack"));
                break;
            case "SQUIRTLE":
                starter.setPrimaryType(Pokemon.PokemonType.WATER);
                starter.getMoves().add(new Move("Tackle", Pokemon.PokemonType.NORMAL, 40, 100, 35, false, "A basic tackle attack"));
                starter.getMoves().add(new Move("Tail Whip", Pokemon.PokemonType.NORMAL, 0, 100, 30, true, "Lowers opponent's defense"));
                starter.getMoves().add(new Move("Water Gun", Pokemon.PokemonType.WATER, 40, 100, 25, true, "Shoots a jet of water"));
                break;
        }

        // Set base stats
        Pokemon.Stats stats = starter.getStats();
        stats.setHp(20);
        stats.setAttack(12);
        stats.setDefense(12);
        stats.setSpecialAttack(12);
        stats.setSpecialDefense(12);
        stats.setSpeed(12);
        starter.setCurrentHp(stats.getHp());
    }


    private void updatePokemonInfo(Pokemon pokemon) {
        StringBuilder info = new StringBuilder();
        info.append(pokemon.getName()).append("\n");
        info.append("Type: ").append(pokemon.getPrimaryType());
        if (pokemon.getSecondaryType() != null) {
            info.append("/").append(pokemon.getSecondaryType());
        }
        info.append("\n\nMoves:\n");

        for (Move move : pokemon.getMoves()) {
            info.append("- ").append(move.getName())
                .append(" (").append(move.getType()).append(")\n");
        }

        pokemonInfoLabel.setText(info.toString());
    }

    private void confirmSelection() {
        if (selectedStarter != null) {
            GameLogger.info("Player chose " + selectedStarter.getName() + " as their starter!");

            // Add the starter to the player's party
            game.getPlayer().getPokemonParty().addPokemon(selectedStarter);

            // Use Gdx.app.postRunnable to ensure screen transition happens on the main thread
            Gdx.app.postRunnable(() -> {
                if (returnScreen instanceof GameScreen) {
                    ((GameScreen) returnScreen).completeInitialization();
                    game.setScreen(returnScreen);
                }
            });
        }
    }

    @Override
    public void show() {
        // Add a fade-in effect
        stage.getRoot().getColor().a = 0;
        stage.getRoot().addAction(Actions.fadeIn(0.5f));
    }

    @Override
    public void render(float delta) {
        // Add a nice background color
        Gdx.gl.glClearColor(0.05f, 0.05f, 0.1f, 1);
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
        batch.dispose();
    }
}

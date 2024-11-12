package io.github.pokemeetup.screens.otherui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Scaling;
import io.github.pokemeetup.pokemon.Pokemon;
import io.github.pokemeetup.pokemon.data.PokemonDatabase;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.ResponsiveLayout;
import io.github.pokemeetup.utils.ScalingUtils;
import io.github.pokemeetup.utils.TextureManager;
import static io.github.pokemeetup.screens.LoginScreen.MIN_WIDTH;

public class StarterSelectionTable extends Table {
    private static final float BASE_TITLE_SCALE = 2.0f;
    private static final float BASE_FONT_SCALE = 1.0f;
    private final Label pokemonInfoLabel;
    private final TextButton confirmButton;
    private Pokemon selectedStarter;
    private Table selectedCell = null;
    private SelectionListener selectionListener;
    private boolean selectionMade = false;
    private float currentScale = 1.25f;

    private static final float SMALL_SCREEN_WIDTH = 800;
    private static final float MEDIUM_SCREEN_WIDTH = 1280;

    // Base sizes that will be scaled
    private static final float BASE_POKEMON_SIZE_SMALL = 80f;
    private static final float BASE_POKEMON_SIZE_MEDIUM = 120f;
    private static final float BASE_POKEMON_SIZE_LARGE = 160f;

    private static final float BASE_PADDING_SMALL = 10f;
    private static final float BASE_PADDING_MEDIUM = 15f;
    private static final float BASE_PADDING_LARGE = 20f;

    private final Skin skin;
    private final Label titleLabel;
    private static final float MIN_FONT_SCALE = 0.5f;
    public StarterSelectionTable(Skin skin) {
        this.skin = skin;
        GameLogger.info("Creating StarterSelectionTable");
        // Set minimum window size
        Gdx.graphics.setWindowedMode(
            Math.max(800, Gdx.graphics.getWidth()),
            Math.max(600, Gdx.graphics.getHeight())
        );
        // Setup main table properties
        setFillParent(true);
        setBackground(new TextureRegionDrawable(TextureManager.ui.findRegion("starter-bg")));
        setTouchable(Touchable.enabled);

        // Create main vertical container with center alignment
        Table mainContainer = new Table();
        mainContainer.center();
        mainContainer.defaults().center().pad(20);

        // Add top spacing for vertical centering
        mainContainer.add().expandY().row();

        // Title
        titleLabel = new Label("Choose Your First Partner!", skin);
        titleLabel.setFontScale(BASE_TITLE_SCALE);
        titleLabel.setAlignment(Align.center);
        mainContainer.add(titleLabel).expandX().center().padBottom(40).row();

        // Pokemon selection area
        starters = new Table();
        starters.defaults().pad(BASE_PADDING).space(40);
        starters.center();

        // Add starter options
        addStarterOption(starters, "BULBASAUR", "A reliable grass-type partner with a mysterious bulb.");
        addStarterOption(starters, "CHARMANDER", "A fierce fire-type partner with a burning tail.");
        addStarterOption(starters, "SQUIRTLE", "A sturdy water-type partner with a protective shell.");

        mainContainer.add(starters).expandX().center().padBottom(40).row();

        // Info label
        pokemonInfoLabel = new Label("Click on a Pokemon to learn more!", skin);
        pokemonInfoLabel.setWrap(true);
        pokemonInfoLabel.setAlignment(Align.center);
        pokemonInfoLabel.setFontScale(1.3f);

        Table infoContainer = new Table();
        infoContainer.add(pokemonInfoLabel).width(Gdx.graphics.getWidth() * 0.6f).pad(30);
        mainContainer.add(infoContainer).expandX().center().padBottom(30).row();

        // Confirm button
        confirmButton = new TextButton("Choose Pokemon!", skin);
        confirmButton.setDisabled(true);
        confirmButton.getLabel().setFontScale(1.5f);
        confirmButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (!confirmButton.isDisabled() && selectedStarter != null) {
                    confirmSelection();
                }
            }
        });

        mainContainer.add(confirmButton).size(BASE_BUTTON_WIDTH, BASE_BUTTON_HEIGHT).padBottom(40).row();

        // Add bottom spacing for vertical centering
        mainContainer.add().expandY().row();

        // Add main container to this table
        add(mainContainer).expand().fill();

        GameLogger.info("StarterSelectionTable setup complete");
    }


    private float getPokemonSize() {
        if (currentScale <= 0.5f) return BASE_POKEMON_SIZE_SMALL;
        if (currentScale <= 0.75f) return BASE_POKEMON_SIZE_MEDIUM;
        return BASE_POKEMON_SIZE_LARGE;
    }

    private float getPadding() {
        if (currentScale <= 0.5f) return BASE_PADDING_SMALL;
        if (currentScale <= 0.75f) return BASE_PADDING_MEDIUM;
        return BASE_PADDING_LARGE;
    }

    private float getCellSize() {
        return getPokemonSize() * 1.5f;
    }


    private ClickListener createConfirmButtonListener() {
        return new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (!confirmButton.isDisabled() && selectedStarter != null) {
                    confirmSelection();
                }
            }
        };
    }

    private ClickListener createPokemonClickListener(final String pokemonName, final String description, final Table cell) {
        return new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                GameLogger.info("Pokemon clicked: " + pokemonName);
                selectStarter(pokemonName, description, cell);
            }

            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                if (!selectionMade) {
                    cell.setColor(0.8f, 0.8f, 1f, 1f);
                }
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                if (cell != selectedCell) {
                    cell.setColor(1, 1, 1, 1);
                }
            }
        };
    }

    private void updateFontScales() {
        try {
            // Safe font scaling for title
            float titleScale = Math.max(ScalingUtils.scale(BASE_TITLE_SCALE), MIN_FONT_SCALE);
            Label titleLabel = findActor("titleLabel");
            if (titleLabel != null) {
                titleLabel.setFontScale(titleScale);
            }

            // Safe font scaling for info label
            float infoScale = Math.max(ScalingUtils.scale(BASE_FONT_SCALE), MIN_FONT_SCALE);
            if (pokemonInfoLabel != null) {
                pokemonInfoLabel.setFontScale(infoScale);
            }

            // Safe font scaling for confirm button
            float buttonScale = Math.max(ScalingUtils.scale(1.5f), MIN_FONT_SCALE);
            if (confirmButton != null) {
                confirmButton.getLabel().setFontScale(buttonScale);
            }

            // Update Pokemon name labels
            updatePokemonNameLabels();

        } catch (Exception e) {
            GameLogger.error("Error updating font scales: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void updatePokemonNameLabels() {
        float nameScale = Math.max(ScalingUtils.scale(1.4f), MIN_FONT_SCALE);
        // Find all Pokemon name labels and update their scales
        getChildren().forEach(actor -> {
            if (actor instanceof Label && actor != pokemonInfoLabel) {
                ((Label) actor).setFontScale(nameScale);
            }
        });
    }

    private Table createTitleSection() {
        Table section = new Table();
        Label title = new Label("Choose Your First Partner!", skin);
        title.setName("titleLabel");
        title.setFontScale(ResponsiveLayout.getFontScale() * 1.5f);
        section.add(title).center();
        return section;
    }private void addStarterOption(Table container, String pokemonName, String description) {
        Table cell = new Table();
        cell.setBackground(new TextureRegionDrawable(TextureManager.ui.findRegion("slot_normal")));
        cell.center(); // Center contents of the cell

        // Pokemon sprite
        TextureRegion sprite = TextureManager.getPokemonfront().findRegion(pokemonName + "_front");
        if (sprite != null) {
            Image image = new Image(sprite);
            image.setScaling(Scaling.fit);
            Vector2 imageSize = ResponsiveLayout.getElementSize(120, 120);
            cell.add(image).size(imageSize.x, imageSize.y)
                .center() // Center the image
                .pad(ResponsiveLayout.getPadding())
                .row();
        }

        // Pokemon name
        Label nameLabel = new Label(pokemonName, skin);
        nameLabel.setFontScale(ResponsiveLayout.getFontScale());
        cell.add(nameLabel).center().pad(ResponsiveLayout.getPadding());

        // Click listener
        cell.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                selectStarter(pokemonName, description, cell);
            }
        });

        // Add cell to container with proper sizing
        Vector2 cellSize = ResponsiveLayout.getElementSize(180, 200);
        container.add(cell).size(cellSize.x, cellSize.y).center();
    }

    private Table createPokemonSelectionSection() {
        Table section = new Table();

        // Create Pokemon container with proper spacing
        Table container = new Table();
        container.setName("pokemonContainer");
        container.defaults().space(ResponsiveLayout.getPadding() * 2); // Space between Pokemon

        // Add Pokemon options in a centered row
        addStarterOption(container, "BULBASAUR", "A reliable grass-type partner.");
        addStarterOption(container, "CHARMANDER", "A fierce fire-type partner.");
        addStarterOption(container, "SQUIRTLE", "A sturdy water-type partner.");

        section.add(container).center();
        return section;
    }

    private void setupStarterPokemon(Pokemon starter) {
        switch (starter.getName()) {
            case "BULBASAUR":
                starter.setPrimaryType(Pokemon.PokemonType.GRASS);
                starter.getMoves().add(PokemonDatabase.getMoveByName("Tackle"));
                starter.getMoves().add(PokemonDatabase.getMoveByName("Growl"));
                starter.setSecondaryType(Pokemon.PokemonType.POISON);
                starter.setLevel(5);
                starter.setCurrentHp(starter.getStats().getHp());
                break;

            case "CHARMANDER":
                starter.setPrimaryType(Pokemon.PokemonType.FIRE);
                starter.getMoves().add(PokemonDatabase.getMoveByName("Tackle"));
                starter.getMoves().add(PokemonDatabase.getMoveByName("Growl"));
                starter.setLevel(5);
                starter.setCurrentHp(starter.getStats().getHp());
                break;

            case "SQUIRTLE":
                starter.setPrimaryType(Pokemon.PokemonType.WATER);
                starter.getMoves().add(PokemonDatabase.getMoveByName("Tackle"));
                starter.getMoves().add(PokemonDatabase.getMoveByName("Withdraw"));
                starter.setCurrentHp(starter.getStats().getHp());
                starter.setLevel(5);
                break;
        }

        // Set base stats for all starters
        Pokemon.Stats stats = starter.getStats();
        stats.setHp(20);
        stats.setAttack(12);
        stats.setDefense(12);
        stats.setSpecialAttack(12);
        stats.setSpecialDefense(12);
        stats.setSpeed(12);
        starter.setCurrentHp(stats.getHp());
    }
    private void selectStarter(String pokemonName, String description, Table pokemonCell) {
        if (selectionMade) return;

        GameLogger.info("Selecting starter: " + pokemonName);
        selectedStarter = new Pokemon(pokemonName, 5);
        setupStarterPokemon(selectedStarter);

        if (selectedCell != null) {
            selectedCell.setBackground(new TextureRegionDrawable(TextureManager.ui.findRegion("slot_normal")));
            selectedCell.setColor(1, 1, 1, 1);
        }

        pokemonCell.setBackground(new TextureRegionDrawable(TextureManager.ui.findRegion("slot_selected")));
        selectedCell = pokemonCell;

        confirmButton.setDisabled(false);
        pokemonInfoLabel.setText(description);

        if (selectionListener != null) {
            selectionListener.onSelectionStart();
        }
    }

    private void confirmSelection() {
        if (selectedStarter != null && selectionListener != null && !selectionMade) {
            GameLogger.info("Confirming starter selection: " + selectedStarter.getName());
            selectionMade = true;
            selectionListener.onStarterSelected(selectedStarter);
        }
    }
    private final Table starters;  // Table containing Pokemon options

    private static final float BASE_POKEMON_SIZE = 160f;
    private static final float BASE_PADDING = 20f;
    private static final float BASE_BUTTON_WIDTH = 300f;
    private static final float BASE_BUTTON_HEIGHT = 80f;

    public void setSelectionListener(SelectionListener listener) {
        this.selectionListener = listener;
    }

    public void resize(int width, int height) {
        GameLogger.info("Resizing StarterSelectionTable to: " + width + "x" + height);

        // Calculate scale based on screen size
        float scaleFactor = Math.min(width / 1920f, height / 1080f);
        scaleFactor = Math.max(scaleFactor, 0.3f); // Minimum scale factor

        // Update sizes based on scale
        float pokemonSize = BASE_POKEMON_SIZE * scaleFactor;
        float buttonWidth = BASE_BUTTON_WIDTH * scaleFactor;
        float buttonHeight = BASE_BUTTON_HEIGHT * scaleFactor;
        float padding = BASE_PADDING * scaleFactor;

        // Update font scales
        titleLabel.setFontScale(BASE_TITLE_SCALE * scaleFactor);
        pokemonInfoLabel.setFontScale(1.3f * scaleFactor);
        confirmButton.getLabel().setFontScale(1.5f * scaleFactor);

        // Update Pokemon container
        starters.clear();
        starters.defaults().pad(padding).space(padding * 2);

        // Recreate Pokemon options with new sizes
        addStarterOption(starters, "BULBASAUR", "A reliable grass-type partner with a mysterious bulb.");
        addStarterOption(starters, "CHARMANDER", "A fierce fire-type partner with a burning tail.");
        addStarterOption(starters, "SQUIRTLE", "A sturdy water-type partner with a protective shell.");

        // Update info label width
        pokemonInfoLabel.setWidth(width * 0.6f);

        // Update button size
        confirmButton.setSize(buttonWidth, buttonHeight);

        // Force layout update
        invalidateHierarchy();
        validate();

        // Center the table
        setPosition((width - getWidth()) / 2, (height - getHeight()) / 2);

        GameLogger.info("StarterSelectionTable resize complete - Scale factor: " + scaleFactor);
    }





    public interface SelectionListener {
        void onStarterSelected(Pokemon starter);

        void onSelectionStart();
    }

}

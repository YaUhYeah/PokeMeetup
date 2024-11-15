package io.github.pokemeetup.screens.otherui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.actions.SequenceAction;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.viewport.FitViewport;
import io.github.pokemeetup.pokemon.Pokemon;
import io.github.pokemeetup.pokemon.WildPokemon;
import io.github.pokemeetup.pokemon.attacks.Move;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.textures.TextureManager;

import java.util.HashMap;
import java.util.List;

public class BattleTable extends Table {
    private static final float POKEMON_SCALE = 1.5f;
    private static final float PLAYER_PLATFORM_Y = 0.18f; // Adjusted value
    private static final float ENEMY_PLATFORM_Y = 0.58f;  // Adjusted value
    private static final float PLATFORM_SCALE = 1.0f;
    // Swap X positions for player and enemy
    private static final float PLAYER_PLATFORM_X = 0.15f;  // Move player to left
    private static final float ENEMY_PLATFORM_X = 0.75f;   // Move enemy to right
    private static final float CONTROLS_BOTTOM_PADDING = 120f; // Increase to avoid hotbar overlap
    private static final float BATTLE_TABLE_HEIGHT_RATIO = 0.8f;
    private static final float PLATFORM_WIDTH_RATIO = 0.25f;private static final float BUTTON_WIDTH = 160f;  // Remove UI_SCALE to make buttons bigger
    private static final float BUTTON_HEIGHT = 45f;
    private static final float BUTTON_PADDING = 12f;
    private static final float BATTLE_TABLE_WIDTH_RATIO = 0.9f;  // Increase to 90% of screen width
    private static final float HP_BAR_WIDTH = 100;    // Adjust HP bar width to match HUD

    private static final float PLATFORM_VERTICAL_OFFSET = 20f; // Adjust vertical position if needed

    // Add background color constants
    private static final float BACKGROUND_ALPHA = 0.45f; // Reduced from 0.85f for less darkness
    private static final Color BACKGROUND_COLOR = new Color(0.1f, 0.1f, 0.2f, BACKGROUND_ALPHA);


    private static final float PLAYER_X_POSITION = 0.15f;
    private static final float PLAYER_Y_POSITION = 0.1f;
    private static final float BASE_WIDTH = 800f;  // Base resolution width
    private static final float BASE_HEIGHT = 480f; // Base resolution height
    private static final float POKEMON_BASE_SIZE = 96f; // Base size for Pokemon sprites
    private static final float ENEMY_X_POSITION = 0.65f;
    private static final float ENEMY_Y_POSITION = 0.5f;
    private static final float UI_SCALE = 0.65f;

    private static final float INFO_BOX_WIDTH = 280f * UI_SCALE;  // Wider info boxes
    private static final float ANIMATION_DURATION = 0.5f;
    private static final float DAMAGE_FLASH_DURATION = 0.1f;
    private static final float HP_UPDATE_DURATION = 0.5f;
    private static final int MAX_TURN_COUNT = 20;
    private static final ObjectMap<Pokemon.PokemonType, ObjectMap<Pokemon.PokemonType, Float>> typeEffectiveness;
    private static final float PLATFORM_SHAKE_DECAY = 0.9f;
    private static final float MIN_SHAKE_INTENSITY = 0.1f;
    private static final float RUN_SUCCESS_BASE = 0.5f;
    private static final float LEVEL_FACTOR = 0.1f;
    private static final HashMap<Pokemon.PokemonType, Color> TYPE_COLORS = new HashMap<Pokemon.PokemonType, Color>() {{
        put(Pokemon.PokemonType.FIRE, new Color(1, 0.3f, 0.3f, 1));
        put(Pokemon.PokemonType.WATER, new Color(0.2f, 0.6f, 1, 1));
        put(Pokemon.PokemonType.GRASS, new Color(0.2f, 0.8f, 0.2f, 1));
        put(Pokemon.PokemonType.NORMAL, new Color(0.8f, 0.8f, 0.8f, 1));
        put(Pokemon.PokemonType.ELECTRIC, new Color(1, 0.9f, 0.3f, 1));
        put(Pokemon.PokemonType.ICE, new Color(0.6f, 0.9f, 1, 1));
        put(Pokemon.PokemonType.FIGHTING, new Color(0.8f, 0.3f, 0.2f, 1));
        put(Pokemon.PokemonType.POISON, new Color(0.6f, 0.3f, 0.6f, 1));
        put(Pokemon.PokemonType.GROUND, new Color(0.9f, 0.7f, 0.3f, 1));
        put(Pokemon.PokemonType.FLYING, new Color(0.6f, 0.6f, 1, 1));
        put(Pokemon.PokemonType.PSYCHIC, new Color(1, 0.3f, 0.6f, 1));
        put(Pokemon.PokemonType.BUG, new Color(0.6f, 0.8f, 0.3f, 1));
        put(Pokemon.PokemonType.ROCK, new Color(0.7f, 0.6f, 0.3f, 1));
        put(Pokemon.PokemonType.GHOST, new Color(0.4f, 0.3f, 0.6f, 1));
        put(Pokemon.PokemonType.DRAGON, new Color(0.5f, 0.3f, 1, 1));
        put(Pokemon.PokemonType.DARK, new Color(0.4f, 0.3f, 0.3f, 1));
        put(Pokemon.PokemonType.STEEL, new Color(0.7f, 0.7f, 0.8f, 1));
        put(Pokemon.PokemonType.FAIRY, new Color(1, 0.6f, 0.8f, 1));
    }};// Fine-tuned positioning constants// Update these constants for better positioning

    static {
        typeEffectiveness = new ObjectMap<>();
        for (Pokemon.PokemonType type : Pokemon.PokemonType.values()) {
            typeEffectiveness.put(type, new ObjectMap<>());
            for (Pokemon.PokemonType defType : Pokemon.PokemonType.values()) {
                typeEffectiveness.get(type).put(defType, 1.0f); // Default effectiveness
            }
        }

        // Normal type
        initTypeEffectiveness(Pokemon.PokemonType.NORMAL, new ObjectMap<Pokemon.PokemonType, Float>() {{
            put(Pokemon.PokemonType.ROCK, 0.5f);
            put(Pokemon.PokemonType.GHOST, 0.0f);
            put(Pokemon.PokemonType.STEEL, 0.5f);
        }});

        // Fire type
        initTypeEffectiveness(Pokemon.PokemonType.FIRE, new ObjectMap<Pokemon.PokemonType, Float>() {{
            put(Pokemon.PokemonType.FIRE, 0.5f);
            put(Pokemon.PokemonType.WATER, 0.5f);
            put(Pokemon.PokemonType.GRASS, 2.0f);
            put(Pokemon.PokemonType.ICE, 2.0f);
            put(Pokemon.PokemonType.BUG, 2.0f);
            put(Pokemon.PokemonType.ROCK, 0.5f);
            put(Pokemon.PokemonType.DRAGON, 0.5f);
            put(Pokemon.PokemonType.STEEL, 2.0f);
        }});

        // Water type
        initTypeEffectiveness(Pokemon.PokemonType.WATER, new ObjectMap<Pokemon.PokemonType, Float>() {{
            put(Pokemon.PokemonType.FIRE, 2.0f);
            put(Pokemon.PokemonType.WATER, 0.5f);
            put(Pokemon.PokemonType.GRASS, 0.5f);
            put(Pokemon.PokemonType.GROUND, 2.0f);
            put(Pokemon.PokemonType.ROCK, 2.0f);
            put(Pokemon.PokemonType.DRAGON, 0.5f);
        }});

        // Electric type
        initTypeEffectiveness(Pokemon.PokemonType.ELECTRIC, new ObjectMap<Pokemon.PokemonType, Float>() {{
            put(Pokemon.PokemonType.WATER, 2.0f);
            put(Pokemon.PokemonType.ELECTRIC, 0.5f);
            put(Pokemon.PokemonType.GRASS, 0.5f);
            put(Pokemon.PokemonType.GROUND, 0.0f);
            put(Pokemon.PokemonType.FLYING, 2.0f);
            put(Pokemon.PokemonType.DRAGON, 0.5f);
        }});

        // Grass type
        initTypeEffectiveness(Pokemon.PokemonType.GRASS, new ObjectMap<Pokemon.PokemonType, Float>() {{
            put(Pokemon.PokemonType.FIRE, 0.5f);
            put(Pokemon.PokemonType.WATER, 2.0f);
            put(Pokemon.PokemonType.GRASS, 0.5f);
            put(Pokemon.PokemonType.POISON, 0.5f);
            put(Pokemon.PokemonType.GROUND, 2.0f);
            put(Pokemon.PokemonType.FLYING, 0.5f);
            put(Pokemon.PokemonType.BUG, 0.5f);
            put(Pokemon.PokemonType.ROCK, 2.0f);
            put(Pokemon.PokemonType.DRAGON, 0.5f);
            put(Pokemon.PokemonType.STEEL, 0.5f);
        }});

        // Ice type
        initTypeEffectiveness(Pokemon.PokemonType.ICE, new ObjectMap<Pokemon.PokemonType, Float>() {{
            put(Pokemon.PokemonType.FIRE, 0.5f);
            put(Pokemon.PokemonType.WATER, 0.5f);
            put(Pokemon.PokemonType.GRASS, 2.0f);
            put(Pokemon.PokemonType.ICE, 0.5f);
            put(Pokemon.PokemonType.GROUND, 2.0f);
            put(Pokemon.PokemonType.FLYING, 2.0f);
            put(Pokemon.PokemonType.DRAGON, 2.0f);
            put(Pokemon.PokemonType.STEEL, 0.5f);
        }});

        // Fighting type
        initTypeEffectiveness(Pokemon.PokemonType.FIGHTING, new ObjectMap<Pokemon.PokemonType, Float>() {{
            put(Pokemon.PokemonType.NORMAL, 2.0f);
            put(Pokemon.PokemonType.ICE, 2.0f);
            put(Pokemon.PokemonType.POISON, 0.5f);
            put(Pokemon.PokemonType.FLYING, 0.5f);
            put(Pokemon.PokemonType.PSYCHIC, 0.5f);
            put(Pokemon.PokemonType.BUG, 0.5f);
            put(Pokemon.PokemonType.ROCK, 2.0f);
            put(Pokemon.PokemonType.GHOST, 0.0f);
            put(Pokemon.PokemonType.DARK, 2.0f);
            put(Pokemon.PokemonType.STEEL, 2.0f);
            put(Pokemon.PokemonType.FAIRY, 0.5f);
        }});
        initTypeEffectiveness(Pokemon.PokemonType.POISON, new ObjectMap<Pokemon.PokemonType, Float>() {{
            put(Pokemon.PokemonType.GRASS, 2.0f);
            put(Pokemon.PokemonType.POISON, 0.5f);
            put(Pokemon.PokemonType.GROUND, 0.5f);
            put(Pokemon.PokemonType.ROCK, 0.5f);
            put(Pokemon.PokemonType.GHOST, 0.5f);
            put(Pokemon.PokemonType.STEEL, 0.0f);
            put(Pokemon.PokemonType.FAIRY, 2.0f);
        }});

        // Ground type
        initTypeEffectiveness(Pokemon.PokemonType.GROUND, new ObjectMap<Pokemon.PokemonType, Float>() {{
            put(Pokemon.PokemonType.FIRE, 2.0f);
            put(Pokemon.PokemonType.ELECTRIC, 2.0f);
            put(Pokemon.PokemonType.GRASS, 0.5f);
            put(Pokemon.PokemonType.POISON, 2.0f);
            put(Pokemon.PokemonType.FLYING, 0.0f);
            put(Pokemon.PokemonType.BUG, 0.5f);
            put(Pokemon.PokemonType.ROCK, 2.0f);
            put(Pokemon.PokemonType.STEEL, 2.0f);
        }});

        // Flying type
        initTypeEffectiveness(Pokemon.PokemonType.FLYING, new ObjectMap<Pokemon.PokemonType, Float>() {{
            put(Pokemon.PokemonType.ELECTRIC, 0.5f);
            put(Pokemon.PokemonType.GRASS, 2.0f);
            put(Pokemon.PokemonType.FIGHTING, 2.0f);
            put(Pokemon.PokemonType.BUG, 2.0f);
            put(Pokemon.PokemonType.ROCK, 0.5f);
            put(Pokemon.PokemonType.STEEL, 0.5f);
        }});

        // Psychic type
        initTypeEffectiveness(Pokemon.PokemonType.PSYCHIC, new ObjectMap<Pokemon.PokemonType, Float>() {{
            put(Pokemon.PokemonType.FIGHTING, 2.0f);
            put(Pokemon.PokemonType.POISON, 2.0f);
            put(Pokemon.PokemonType.PSYCHIC, 0.5f);
            put(Pokemon.PokemonType.DARK, 0.0f);
            put(Pokemon.PokemonType.STEEL, 0.5f);
        }});

        // Bug type
        initTypeEffectiveness(Pokemon.PokemonType.BUG, new ObjectMap<Pokemon.PokemonType, Float>() {{
            put(Pokemon.PokemonType.FIRE, 0.5f);
            put(Pokemon.PokemonType.GRASS, 2.0f);
            put(Pokemon.PokemonType.FIGHTING, 0.5f);
            put(Pokemon.PokemonType.POISON, 0.5f);
            put(Pokemon.PokemonType.FLYING, 0.5f);
            put(Pokemon.PokemonType.PSYCHIC, 2.0f);
            put(Pokemon.PokemonType.GHOST, 0.5f);
            put(Pokemon.PokemonType.DARK, 2.0f);
            put(Pokemon.PokemonType.STEEL, 0.5f);
            put(Pokemon.PokemonType.FAIRY, 0.5f);
        }});

        // Rock type
        initTypeEffectiveness(Pokemon.PokemonType.ROCK, new ObjectMap<Pokemon.PokemonType, Float>() {{
            put(Pokemon.PokemonType.FIRE, 2.0f);
            put(Pokemon.PokemonType.ICE, 2.0f);
            put(Pokemon.PokemonType.FIGHTING, 0.5f);
            put(Pokemon.PokemonType.GROUND, 0.5f);
            put(Pokemon.PokemonType.FLYING, 2.0f);
            put(Pokemon.PokemonType.BUG, 2.0f);
            put(Pokemon.PokemonType.STEEL, 0.5f);
        }});

        // Ghost type
        initTypeEffectiveness(Pokemon.PokemonType.GHOST, new ObjectMap<Pokemon.PokemonType, Float>() {{
            put(Pokemon.PokemonType.NORMAL, 0.0f);
            put(Pokemon.PokemonType.PSYCHIC, 2.0f);
            put(Pokemon.PokemonType.GHOST, 2.0f);
            put(Pokemon.PokemonType.DARK, 0.5f);
        }});

        // Dragon type
        initTypeEffectiveness(Pokemon.PokemonType.DRAGON, new ObjectMap<Pokemon.PokemonType, Float>() {{
            put(Pokemon.PokemonType.DRAGON, 2.0f);
            put(Pokemon.PokemonType.STEEL, 0.5f);
            put(Pokemon.PokemonType.FAIRY, 0.0f);
        }});

        // Dark type
        initTypeEffectiveness(Pokemon.PokemonType.DARK, new ObjectMap<Pokemon.PokemonType, Float>() {{
            put(Pokemon.PokemonType.FIGHTING, 0.5f);
            put(Pokemon.PokemonType.PSYCHIC, 2.0f);
            put(Pokemon.PokemonType.GHOST, 2.0f);
            put(Pokemon.PokemonType.DARK, 0.5f);
            put(Pokemon.PokemonType.FAIRY, 0.5f);
        }});

        // Steel type
        initTypeEffectiveness(Pokemon.PokemonType.STEEL, new ObjectMap<Pokemon.PokemonType, Float>() {{
            put(Pokemon.PokemonType.FIRE, 0.5f);
            put(Pokemon.PokemonType.WATER, 0.5f);
            put(Pokemon.PokemonType.ELECTRIC, 0.5f);
            put(Pokemon.PokemonType.ICE, 2.0f);
            put(Pokemon.PokemonType.ROCK, 2.0f);
            put(Pokemon.PokemonType.STEEL, 0.5f);
            put(Pokemon.PokemonType.FAIRY, 2.0f);
        }});

        // Fairy type
        initTypeEffectiveness(Pokemon.PokemonType.FAIRY, new ObjectMap<Pokemon.PokemonType, Float>() {{
            put(Pokemon.PokemonType.FIRE, 0.5f);
            put(Pokemon.PokemonType.FIGHTING, 2.0f);
            put(Pokemon.PokemonType.POISON, 0.5f);
            put(Pokemon.PokemonType.DRAGON, 2.0f);
            put(Pokemon.PokemonType.DARK, 2.0f);
            put(Pokemon.PokemonType.STEEL, 0.5f);
        }});
    }

    private final Stage stage;
    private final Skin skin;
    private final Pokemon playerPokemon;
    private final Pokemon enemyPokemon;
    private final Array<Action> pendingActions = new Array<>();
    private final ShapeRenderer shapeRenderer;
    float platformHeight = 0;
    float platformWidth = 0;
    private TextureRegion battleBackground;
    private TextureRegion platformTexture;
    private float playerPlatformX, playerPlatformY;
    private float enemyPlatformX, enemyPlatformY;
    private Vector2 cameraShake;
    private float shakeDuration;
    private float shakeTimer;
    private Table battleScene;
    private Image playerPlatform;
    private Image enemyPlatform;
    private Image playerPokemonImage;
    private Image enemyPokemonImage;
    private Table actionMenu;
    private Table moveMenu;
    private ProgressBar playerHPBar;
    private ProgressBar enemyHPBar;
    private Label battleText;
    private TextButton fightButton;
    private TextButton bagButton;
    private TextButton pokemonButton;
    private TextButton runButton;
    private BattleState currentState = BattleState.INTRO;
    private BattleCallback callback;
    private float stateTimer = 0;
    private int turnCount = 0;
    private boolean isAnimating = false;
    private float currentShakeIntensity = 0;
    private PokemonHUD playerHUD;
    private PokemonHUD enemyHUD;
    private int selectedMoveIndex = 0;
    private boolean moveMenuVisible = false;
    private Table moveSelectionMenu;
    private Label powerLabel;
    private Label accuracyLabel;
    private Label descriptionLabel;
    private Label moveTypeLabel;
    private boolean initialized = false;

    public BattleTable(Stage stage, Skin skin, Pokemon playerPokemon, Pokemon enemyPokemon) {
        super();
        this.stage = stage;
        this.skin = skin;
        this.playerPokemon = playerPokemon;
        this.enemyPokemon = enemyPokemon;
        this.shapeRenderer = new ShapeRenderer();
        this.cameraShake = new Vector2();
        this.currentState = BattleState.INTRO;
        this.isAnimating = true;
        stage.setViewport(new FitViewport(BASE_WIDTH, BASE_HEIGHT));
        stage.getViewport().update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);

        try {
            initializeTextures();
            initializeUIComponents();
            initializePlatforms();
            initializePokemonSprites();
            setupHPBars();
            initializeHUDElements();
            initializeMoveMenu();
            initializeMoveLabels();
            setupContainer();

            initialized = true;
            // Sizes will be set in sizeChanged(), which is called after the actor is added to the stage
            startBattleAnimation();
        } catch (Exception e) {
            GameLogger.error("Error initializing battle table: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void initTypeEffectiveness(Pokemon.PokemonType attackType,
                                              ObjectMap<Pokemon.PokemonType, Float> effectiveness) {
        typeEffectiveness.get(attackType).putAll(effectiveness);

    }


    private static ProgressBar.ProgressBarStyle createHPBarStyle(float percentage) {
        ProgressBar.ProgressBarStyle style = new ProgressBar.ProgressBarStyle();

        // Create a more subtle background
        Pixmap bgPixmap = new Pixmap((int) HP_BAR_WIDTH, 16, Pixmap.Format.RGBA8888);
        bgPixmap.setColor(0.2f, 0.2f, 0.2f, 0.5f); // More transparent, softer background
        bgPixmap.fill();
        style.background = new TextureRegionDrawable(new TextureRegion(new Texture(bgPixmap)));
        bgPixmap.dispose();

        // Create foreground bar
        Pixmap fgPixmap = new Pixmap((int) HP_BAR_WIDTH, 14, Pixmap.Format.RGBA8888);

        // Set color based on percentage
        if (percentage > 0.5f) {
            fgPixmap.setColor(71 / 255f, 201 / 255f, 93 / 255f, 1);  // #47C95D
        } else if (percentage > 0.2f) {
            fgPixmap.setColor(255 / 255f, 217 / 255f, 0 / 255f, 1);  // #FFD900
        } else {
            fgPixmap.setColor(255 / 255f, 57 / 255f, 57 / 255f, 1);  // #FF3939
        }

        fgPixmap.fill();
        style.knob = new TextureRegionDrawable(new TextureRegion(new Texture(fgPixmap)));
        style.knobBefore = style.knob;
        fgPixmap.dispose();

        return style;
    }
    private void setupContainer() {
        setFillParent(true);
        setTouchable(Touchable.childrenOnly);
        setZIndex(100);
    }



    private Drawable createBattleBackground(float alpha) {
        Pixmap bgPixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        bgPixmap.setColor(0, 0, 0, alpha);
        bgPixmap.fill();
        TextureRegion region = new TextureRegion(new Texture(bgPixmap));
        bgPixmap.dispose();
        return new TextureRegionDrawable(region);
    }

    @Override
    protected void sizeChanged() {
        super.sizeChanged();
        if (initialized) {
            updateSizes();
            updateLayout();
            updatePokemonPositions(); // Add this call

            // Reset animations if they were playing
            if (currentState == BattleState.INTRO && isAnimating) {
                clearActions();
                resetPokemonPositions();
            }
        }
    }

    private void updateSizes() {
        float viewportWidth = stage.getViewport().getWorldWidth();
        float viewportHeight = stage.getViewport().getWorldHeight();

        // Calculate platform sizes
        float platformWidth = viewportWidth * PLATFORM_WIDTH_RATIO;
        float platformHeight = platformWidth * 0.3f; // Keep platforms relatively flat

        // Update platform sizes
        playerPlatform.setSize(platformWidth, platformHeight);
        enemyPlatform.setSize(platformWidth, platformHeight);

        // Calculate Pokemon sizes
        float pokemonSize = platformWidth * 0.8f;
        playerPokemonImage.setSize(pokemonSize, pokemonSize);
        enemyPokemonImage.setSize(pokemonSize, pokemonSize);

        // Update positions to align Pokemon with platforms
        updatePokemonPositions();
    }

    private void updateLayout() {
        if (!initialized) return;

        clear();
        Image backgroundImage = new Image(battleBackground);
        backgroundImage.setFillParent(true);
        backgroundImage.setColor(1, 1, 1, 0.5f); // Adjust alpha as needed
        backgroundImage.setTouchable(Touchable.disabled);
        addActorAt(0, backgroundImage); // Add behind other actors

        Table mainContainer = new Table();
        mainContainer.setFillParent(true);
        mainContainer.setTouchable(Touchable.childrenOnly);

        // Create a background Image with transparency

        // Add top padding to move everything down
        mainContainer.padTop(20); // Adjust the value (e.g., 20 pixels) as needed
        // Enemy section (top right)
        Table enemySection = new Table();
        enemySection.add(enemyHUD).expandX().right().pad(10).row();

        Stack enemyStack = new Stack();
        enemyStack.add(enemyPlatform);
        enemyStack.add(enemyPokemonImage);
        enemySection.add(enemyStack).expand().right().padRight(stage.getWidth() * 0.1f);

        // Player section (bottom left)
        Table playerSection = new Table();
        playerSection.add(playerHUD).expandX().left().pad(10).row();

        Stack playerStack = new Stack();
        playerStack.add(playerPlatform);
        playerStack.add(playerPokemonImage);
        playerSection.add(playerStack).expand().left().padLeft(stage.getWidth() * 0.1f);

        // Battle controls section
        Table controlSection = new Table();
        controlSection.setBackground(createTranslucentBackground(0.7f));

        // Battle text
        controlSection.add(battleText).expandX().fillX().pad(10).row();
// In updateLayout()
        controlSection.setTouchable(Touchable.childrenOnly);

        // Button row with fixed sizes
        Table buttonRow = new Table();
        buttonRow.defaults().size(BUTTON_WIDTH, BUTTON_HEIGHT).pad(BUTTON_PADDING);

        // Add buttons with explicit sizes
        buttonRow.add(fightButton).size(BUTTON_WIDTH, BUTTON_HEIGHT);
        buttonRow.add(bagButton).size(BUTTON_WIDTH, BUTTON_HEIGHT);
        buttonRow.add(pokemonButton).size(BUTTON_WIDTH, BUTTON_HEIGHT);
        buttonRow.add(runButton).size(BUTTON_WIDTH, BUTTON_HEIGHT);

        controlSection.add(buttonRow).padBottom(20);

        // Add all sections to main container
        mainContainer.add(enemySection).expand().fill().row();
        mainContainer.add(playerSection).expand().fill().row();
        mainContainer.add(controlSection).expandX().fillX().bottom().padBottom(CONTROLS_BOTTOM_PADDING);

        add(mainContainer).expand().fill();

        // Force visibility update
        updateUI();
    }

    private void updateUI() {
        if (battleText == null || actionMenu == null || moveMenu == null) {
            GameLogger.error("UI components not properly initialized");
            return;
        }

        boolean isPlayerTurn = currentState == BattleState.PLAYER_TURN;
        boolean isBattleEnded = currentState == BattleState.ENDED;
        actionMenu.setVisible(isPlayerTurn && !isAnimating);
        moveMenu.setVisible(false);
        if (fightButton != null) fightButton.setDisabled(!isPlayerTurn || isBattleEnded);
        if (bagButton != null) bagButton.setDisabled(!isPlayerTurn || isBattleEnded);
        if (pokemonButton != null) pokemonButton.setDisabled(!isPlayerTurn || isBattleEnded);
        if (runButton != null) runButton.setDisabled(!isPlayerTurn || isBattleEnded);
        updateHPBars();
        updateStatusEffects();
        updateBattleText();
    }

    private TextureRegionDrawable createTranslucentBackground(float alpha) {
        Pixmap bgPixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        bgPixmap.setColor(0, 0, 0, alpha);
        bgPixmap.fill();
        TextureRegion region = new TextureRegion(new Texture(bgPixmap));
        bgPixmap.dispose();
        return new TextureRegionDrawable(region);
    }

    @Override
    public Stage getStage() {
        return stage;
    }

    @Override
    public Skin getSkin() {
        return skin;
    }

    public void setCallback(BattleCallback callback) {
        this.callback = callback;
    }

    private void startShakeEffect(float intensity, float duration) {
        currentShakeIntensity = intensity;
        shakeDuration = duration;
        shakeTimer = 0;
    }

    private void updateShakeEffects(float delta) {
        if (shakeTimer < shakeDuration) {
            shakeTimer += delta;

            // Calculate shake offset
            float xOffset = MathUtils.random(-currentShakeIntensity, currentShakeIntensity);
            float yOffset = MathUtils.random(-currentShakeIntensity, currentShakeIntensity);
            cameraShake.set(xOffset, yOffset);

            // Apply shake to platforms and Pokemon
            playerPlatform.setPosition(
                playerPlatformX + xOffset,
                playerPlatformY + yOffset
            );
            playerPokemonImage.setPosition(
                playerPlatformX + platformTexture.getRegionWidth() / 2 - playerPokemonImage.getWidth() / 2 + xOffset,
                playerPlatformY + platformTexture.getRegionHeight() + yOffset
            );

            // Decay shake intensity
            currentShakeIntensity *= PLATFORM_SHAKE_DECAY;
            if (currentShakeIntensity < MIN_SHAKE_INTENSITY) {
                currentShakeIntensity = 0;
                resetPositions();
            }
        }
    }

    private void resetPositions() {
        playerPlatform.setPosition(playerPlatformX, playerPlatformY);
        enemyPlatform.setPosition(enemyPlatformX, enemyPlatformY);
        playerPokemonImage.setPosition(
            playerPlatformX + platformTexture.getRegionWidth() / 2f - playerPokemonImage.getWidth() / 2f,
            playerPlatformY + platformTexture.getRegionHeight()
        );
        enemyPokemonImage.setPosition(
            enemyPlatformX + platformTexture.getRegionWidth() / 2f - enemyPokemonImage.getWidth() / 2f,
            enemyPlatformY + platformTexture.getRegionHeight()
        );
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        if (playerHPBar == null || enemyHPBar == null) {
            return;
        }
        updateShakeEffects(delta);
        updateUI();

        if (!isAnimating) {
            stateTimer += delta;

            switch (currentState) {
                case INTRO:
                    if (stateTimer >= ANIMATION_DURATION) {
                        transitionToState(BattleState.PLAYER_TURN);
                    }
                    break;

                case PLAYER_TURN:
                    if (!actionMenu.isVisible() && !moveMenu.isVisible()) {
                        showActionMenu(true);
                    }
                    break;

                case ENEMY_TURN:
                    if (stateTimer >= 0.5f) {
                        executeEnemyMove();
                    }
                    break;
            }
        }
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        super.draw(batch, parentAlpha);
    }

    private void executeEnemyMove() {
        if (isAnimating || enemyPokemon.getCurrentHp() <= 0) return;

        // Simple AI - prioritize super effective moves
        Move selectedMove = null;
        float bestEffectiveness = 0f;

        for (Move move : enemyPokemon.getMoves()) {
            if (move.getPp() > 0) {
                float effectiveness = getTypeEffectiveness(
                    move.getType(),
                    playerPokemon.getPrimaryType()
                );

                if (playerPokemon.getSecondaryType() != null) {
                    effectiveness *= getTypeEffectiveness(
                        move.getType(),
                        playerPokemon.getSecondaryType()
                    );
                }

                if (effectiveness > bestEffectiveness) {
                    bestEffectiveness = effectiveness;
                    selectedMove = move;
                }
            }
        }
        if (selectedMove == null) {
            executeStruggle(enemyPokemon, playerPokemon);
            return;
        }

        executeMove(selectedMove, enemyPokemon, playerPokemon, false);
    }

    private void applyEndOfTurnEffects(Pokemon pokemon) {
        if (!pokemon.hasStatus()) return;

        switch (pokemon.getStatus()) {
            case BURNED:
                float burnDamage = pokemon.getStats().getHp() * 0.0625f;
                applyDamage(pokemon, burnDamage);
                showBattleText(pokemon.getName() + " was hurt by its burn!");
                break;

            case POISONED:
                float poisonDamage = pokemon.getStats().getHp() * 0.125f;
                applyDamage(pokemon, poisonDamage);
                showBattleText(pokemon.getName() + " was hurt by poison!");
                break;

            case BADLY_POISONED:
                float toxicDamage = pokemon.getStats().getHp() * (0.0625f * pokemon.getToxicCounter());
                applyDamage(pokemon, toxicDamage);
                pokemon.incrementToxicCounter();
                showBattleText(pokemon.getName() + " was hurt by toxic!");
                break;
        }

        updateHPBars();
    }

    private void attemptRun() {
        if (isAnimating) return;

        float runChance = calculateRunChance();
        if (MathUtils.random() < runChance) {
            showBattleText("Got away safely!");

            SequenceAction escapeSequence = Actions.sequence(
                Actions.parallel(
                    Actions.run(() -> {
                        playerPokemonImage.addAction(Actions.fadeOut(0.5f));
                    }),
                    Actions.delay(0.5f)
                ),
                Actions.parallel(
                    Actions.run(() -> {
                        battleScene.addAction(Actions.fadeOut(0.5f));
                    }),
                    Actions.delay(0.5f)
                ),
                Actions.run(() -> {
                    if (callback != null) {
                        callback.onBattleEnd(true);
                    }
                    remove();
                    dispose();
                })
            );

            addAction(escapeSequence);
        } else {
            showBattleText("Can't escape!");
            transitionToState(BattleState.ENEMY_TURN);
        }
    }

    public void dispose() {
        // Clean up resources
        if (playerPokemonImage != null) {
            playerPokemonImage.remove();
        }
        if (shapeRenderer != null) {
            shapeRenderer.dispose();
        }
        if (enemyPokemonImage != null) {
            enemyPokemonImage.remove();
        }
        if (battleScene != null) {
            battleScene.remove();
        }
        if (enemyHPBar != null && enemyHPBar.getStyle().knob instanceof TextureRegionDrawable) {
            ((TextureRegionDrawable) enemyHPBar.getStyle().knob).getRegion().getTexture().dispose();
        }
        if (playerHPBar != null && playerHPBar.getStyle().knob instanceof TextureRegionDrawable) {
            ((TextureRegionDrawable) playerHPBar.getStyle().knob).getRegion().getTexture().dispose();
        }
        // Dispose of any textures created for HP bars
        Array<Cell> cells = playerHUD.getCells();
        for (Cell cell : cells) {
            Actor actor = cell.getActor();
            if (actor instanceof ProgressBar) {
                ProgressBar bar = (ProgressBar) actor;
                Drawable knob = bar.getStyle().knob;
                if (knob instanceof TextureRegionDrawable) {
                    ((TextureRegionDrawable) knob).getRegion().getTexture().dispose();
                }
            }
        }
        clearActions();
        remove();
    }

    private float calculateRunChance() {
        float levelDiff = playerPokemon.getLevel() - enemyPokemon.getLevel();
        return RUN_SUCCESS_BASE + (levelDiff * LEVEL_FACTOR);
    }

    private void initializeMoveMenu() {
        moveMenu = new Table(skin);
        moveMenu.setBackground(createTranslucentBackground(0.8f));
        moveMenu.defaults().pad(10).size(180, 45);

        // Create move buttons grid
        Table moveGrid = new Table();
        moveGrid.defaults().pad(5);

        // Add moves
        for (int i = 0; i < playerPokemon.getMoves().size(); i++) {
            final int moveIndex = i;
            Move move = playerPokemon.getMoves().get(i);

            Table moveButton = createMoveButton(move);
            moveButton.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    if (!isAnimating && currentState == BattleState.PLAYER_TURN) {
                        selectedMoveIndex = moveIndex;
                        executeMove(move, playerPokemon, enemyPokemon, true);
                    }
                }
            });

            // Add to grid in 2x2 layout
            if (i % 2 == 0) {
                moveGrid.add(moveButton).padRight(10);
            } else {
                moveGrid.add(moveButton).row();
            }
        }

        // Add back button
        TextButton backButton = new TextButton("BACK", skin);
        backButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                showActionMenu(true);
            }
        });

        moveMenu.add(moveGrid).expand().fill().row();
        moveMenu.add(backButton).size(150, 40).pad(10);
        moveMenu.setVisible(false);  // Initially hidden
        addActor(moveMenu);
    }

    private void updateBattleText() {
        if (battleText == null) {
            GameLogger.error("Battle text not initialized");
            return;
        }

        String message = "";
        switch (currentState) {
            case INTRO:
                message = "Wild " + enemyPokemon.getName() + " appeared!";
                break;
            case PLAYER_TURN:
                message = "What will " + playerPokemon.getName() + " do?";
                break;
            case ENEMY_TURN:
                message = "Wild " + enemyPokemon.getName() + " is thinking...";
                break;
            case ENDED:
                message = playerPokemon.getCurrentHp() > 0 ? "Victory!" : "Defeat!";
                break;
            default:
                break;
        }
        battleText.setText(message);
    }


    private void initializePlatforms() {
        platformTexture = TextureManager.getBattlebacks().findRegion("battle_platform");
        if (platformTexture == null) {
            throw new RuntimeException("Failed to load battle platform texture");
        }

        playerPlatform = new Image(platformTexture);
        enemyPlatform = new Image(platformTexture);

        // Remove scaling
        playerPlatform.setScaling(Scaling.none);
        enemyPlatform.setScaling(Scaling.none);

        // Force visibility
        playerPlatform.setVisible(true);
        enemyPlatform.setVisible(true);

        // Do not add platforms directly to the stage
        // They will be added to the layout in updateLayout()
    }

    private void resetPokemonPositions() {
        if (playerPokemonImage != null && enemyPokemonImage != null) {
            float stageWidth = stage.getWidth();
            float stageHeight = stage.getHeight();

            // Position platforms
            playerPlatform.setScale(PLATFORM_SCALE);
            enemyPlatform.setScale(PLATFORM_SCALE);

            float platformWidth = platformTexture.getRegionWidth() * PLATFORM_SCALE;
            float platformHeight = platformTexture.getRegionHeight() * PLATFORM_SCALE;

            playerPlatform.setPosition(
                stageWidth * PLAYER_PLATFORM_X,
                stageHeight * PLAYER_PLATFORM_Y
            );

            enemyPlatform.setPosition(
                stageWidth * ENEMY_PLATFORM_X,
                stageHeight * ENEMY_PLATFORM_Y
            );

            // Position Pokemon with proper offset
            playerPokemonImage.setScale(POKEMON_SCALE);
            enemyPokemonImage.setScale(POKEMON_SCALE);

            playerPokemonImage.setPosition(
                playerPlatformX + (platformWidth - playerPokemonImage.getWidth()) / 2f,
                playerPlatformY + platformHeight * 0.5f + PLATFORM_VERTICAL_OFFSET
            );

            enemyPokemonImage.setPosition(
                enemyPlatformX + (platformWidth - enemyPokemonImage.getWidth()) / 2f,
                enemyPlatformY + platformHeight * 0.5f + PLATFORM_VERTICAL_OFFSET
            );
        }
    }


    private void initializePokemonSprites() {
        TextureRegion playerTexture = playerPokemon.getBackSprite();
        TextureRegion enemyTexture = enemyPokemon.getFrontSprite();

        if (playerTexture == null || enemyTexture == null) {
            throw new RuntimeException("Failed to load Pokemon sprites");
        }

        playerPokemonImage = new Image(playerTexture);
        enemyPokemonImage = new Image(enemyTexture);

        // Set base size while maintaining aspect ratio
        float playerAspect = playerTexture.getRegionWidth() / (float) playerTexture.getRegionHeight();
        float enemyAspect = enemyTexture.getRegionWidth() / (float) enemyTexture.getRegionHeight();

        playerPokemonImage.setSize(POKEMON_BASE_SIZE * playerAspect, POKEMON_BASE_SIZE);
        enemyPokemonImage.setSize(POKEMON_BASE_SIZE * enemyAspect, POKEMON_BASE_SIZE);

        // Remove scaling
        playerPokemonImage.setScaling(Scaling.none);
        enemyPokemonImage.setScaling(Scaling.none);

        // Do not add images directly to the stage
        // They will be added to the layout in updateLayout()
    }



    private void initializeHUDElements() {
        // Player HP bar
        playerHUD = new PokemonHUD(skin, playerPokemon, true);
        playerHUD.setHPBar(playerHPBar);

        // Enemy HP bar
        enemyHUD = new PokemonHUD(skin, enemyPokemon, false);
        enemyHUD.setHPBar(enemyHPBar);
    }

    private void initializeBattleScene() {
        // Main container
        Table mainContainer = new Table();
        mainContainer.setFillParent(true);
        mainContainer.setBackground(new TextureRegionDrawable(battleBackground));

        // Battle area
        Table battleArea = new Table();
        battleArea.setFillParent(true);

        // Position enemy elements
        battleArea.add(enemyHUD).expand().top().left().pad(20);
        battleArea.row();
        battleArea.add(enemyPlatform).expand().top().left().padLeft(stage.getWidth() * ENEMY_X_POSITION).padTop(stage.getHeight() * ENEMY_Y_POSITION);
        battleArea.add(enemyPokemonImage).expand().top().left().padLeft(stage.getWidth() * ENEMY_X_POSITION).padTop(stage.getHeight() * ENEMY_Y_POSITION);

        // Position player elements
        battleArea.row();
        battleArea.add(playerHUD).expand().bottom().right().pad(20);
        battleArea.row();
        battleArea.add(playerPlatform).expand().bottom().right().padRight(stage.getWidth() * PLAYER_X_POSITION).padBottom(stage.getHeight() * PLAYER_Y_POSITION);
        battleArea.add(playerPokemonImage).expand().bottom().right().padRight(stage.getWidth() * PLAYER_X_POSITION).padBottom(stage.getHeight() * PLAYER_Y_POSITION);

        // Add battle area to main container
        mainContainer.add(battleArea).expand().fill();

        // Add main container to the stage
        stage.addActor(mainContainer);
    }
    private void initializeUIComponents() {
        // Initialize battle text
        battleText = new Label("", skin);
        battleText.setWrap(true);
        battleText.setAlignment(Align.center);

        // Create button style with better visibility
        TextButton.TextButtonStyle buttonStyle = new TextButton.TextButtonStyle();
        buttonStyle.font = skin.getFont("default");
        buttonStyle.up = createButtonBackground(new Color(0.2f, 0.2f, 0.5f, 0.9f));
        buttonStyle.down = createButtonBackground(new Color(0.15f, 0.15f, 0.4f, 0.9f));
        buttonStyle.over = createButtonBackground(new Color(0.25f, 0.25f, 0.6f, 0.9f));
        buttonStyle.fontColor = Color.WHITE;

        // Initialize buttons with fixed size
        fightButton = new TextButton("FIGHT", buttonStyle);
        bagButton = new TextButton("BAG", buttonStyle);
        pokemonButton = new TextButton("POKEMON", buttonStyle);
        runButton = new TextButton("RUN", buttonStyle);

        // Enable button interaction and make text bigger
        TextButton[] buttons = {fightButton, bagButton, pokemonButton, runButton};
        for (TextButton button : buttons) {
            button.setTouchable(Touchable.enabled);
            button.getLabel().setFontScale(1.2f);
            button.setSize(BUTTON_WIDTH, BUTTON_HEIGHT);
        }

        fightButton.setTouchable(Touchable.enabled);
        bagButton.setTouchable(Touchable.enabled);
        pokemonButton.setTouchable(Touchable.enabled);
        runButton.setTouchable(Touchable.enabled);
        // Create action menu
        actionMenu = new Table();
        actionMenu.setTouchable(Touchable.enabled);
        actionMenu.defaults().size(BUTTON_WIDTH, BUTTON_HEIGHT).pad(BUTTON_PADDING);

        // Add buttons to actionMenu
        actionMenu.add(fightButton).size(BUTTON_WIDTH, BUTTON_HEIGHT);
        actionMenu.add(bagButton).size(BUTTON_WIDTH, BUTTON_HEIGHT);
        actionMenu.add(pokemonButton).size(BUTTON_WIDTH, BUTTON_HEIGHT);
        actionMenu.add(runButton).size(BUTTON_WIDTH, BUTTON_HEIGHT);

        actionMenu.setTouchable(Touchable.enabled);
        setupButtonListeners();
    }

    private void setupButtonListeners() {
        fightButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (!isAnimating && currentState == BattleState.PLAYER_TURN) {
                    GameLogger.info("Fight button clicked");
                    showMoveSelection();
                }
            }
        });

        bagButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (!isAnimating && currentState == BattleState.PLAYER_TURN) {
                    GameLogger.info("Bag button clicked");
                    handleBagButton();
                }
            }
        });

        pokemonButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (!isAnimating && currentState == BattleState.PLAYER_TURN) {
                    GameLogger.info("Pokemon button clicked");
                    handlePokemonButton();
                }
            }
        });

        runButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (!isAnimating && currentState == BattleState.PLAYER_TURN) {
                    GameLogger.info("Run button clicked");
                    attemptRun();
                }
            }
        });
    }
    private void showActionMenu(boolean show) {
        if (actionMenu != null) {
            actionMenu.setVisible(show);
            actionMenu.setTouchable(show ? Touchable.enabled : Touchable.disabled);

            // Make sure buttons are properly enabled/disabled
            if (show) {
                fightButton.setDisabled(false);
                bagButton.setDisabled(false);
                pokemonButton.setDisabled(playerPokemon.getCurrentHp() <= 0);
                runButton.setDisabled(false);

                // Refresh touchable states
                fightButton.setTouchable(Touchable.enabled);
                bagButton.setTouchable(Touchable.enabled);
                pokemonButton.setTouchable(Touchable.enabled);
                runButton.setTouchable(Touchable.enabled);
            }
        }

        if (moveMenu != null) {
            moveMenu.setVisible(!show);
            moveMenu.setTouchable(!show ? Touchable.enabled : Touchable.disabled);
        }
    }
    private void startBattleAnimation() {
        isAnimating = true;
        currentState = BattleState.INTRO;

        // Set initial off-screen positions for sprites (if needed)
        // Since we're using layout, we can animate the alpha instead

        // Create animation sequence
        SequenceAction introSequence = Actions.sequence(
            Actions.run(() -> {
                battleText.setText("Wild " + enemyPokemon.getName() + " appeared!");
            }),
            Actions.fadeIn(0.5f),
            Actions.run(() -> {
                isAnimating = false;
                currentState = BattleState.PLAYER_TURN;
                updateUI();
            })
        );

        addAction(introSequence);
    }

    private void handleButtonClick(String buttonText) {
        switch (buttonText) {
            case "FIGHT":
                showMoveSelection();
                break;
            case "BAG":
                handleBagButton();
                break;
            case "POKEMON":
                handlePokemonButton();
                break;
            case "RUN":
                attemptRun();
                break;
        }
    }
    private void updatePokemonPositions() {
        float viewportWidth = stage.getViewport().getWorldWidth();
        float viewportHeight = stage.getViewport().getWorldHeight();

        // Position platforms
        playerPlatformX = viewportWidth * PLAYER_PLATFORM_X;
        playerPlatformY = viewportHeight * PLAYER_PLATFORM_Y;
        enemyPlatformX = viewportWidth * ENEMY_PLATFORM_X;
        enemyPlatformY = viewportHeight * ENEMY_PLATFORM_Y;

        playerPlatform.setPosition(playerPlatformX, playerPlatformY);
        enemyPlatform.setPosition(enemyPlatformX, enemyPlatformY);

        // Position Pokemon on top of platforms - adjust offsets for better positioning
        playerPokemonImage.setPosition(
            playerPlatformX + (playerPlatform.getWidth() - playerPokemonImage.getWidth()) / 2,
            playerPlatformY + playerPlatform.getHeight() * 0.7f
        );

        enemyPokemonImage.setPosition(
            enemyPlatformX + (enemyPlatform.getWidth() - enemyPokemonImage.getWidth()) / 2,
            enemyPlatformY + enemyPlatform.getHeight() * 0.7f
        );
    }



    private Table createTypeIcon(Pokemon.PokemonType type) {
        Table iconContainer = new Table();
        Color typeColor = TYPE_COLORS.get(type);

        // Create circular background
        Pixmap iconPixmap = new Pixmap(30, 30, Pixmap.Format.RGBA8888);
        iconPixmap.setColor(typeColor);
        iconPixmap.fillCircle(15, 15, 15);

        TextureRegionDrawable iconBg = new TextureRegionDrawable(
            new TextureRegion(new Texture(iconPixmap)));
        iconPixmap.dispose();

        iconContainer.setBackground(iconBg);
        return iconContainer;
    }

    public void resize(int width, int height) {
        // Cancel any running animations
        clearActions();

        // Update positions without animation
        playerPokemonImage.clearActions();
        enemyPokemonImage.clearActions();

        // Update viewport
        stage.getViewport().update(width, height, true);

        // Force immediate position update
        updatePokemonPositions();

        // Reset the Pokemon to their correct positions
        if (playerPokemonImage != null && enemyPokemonImage != null) {
            playerPokemonImage.setVisible(true);
            enemyPokemonImage.setVisible(true);

            float stageWidth = stage.getWidth();
            float stageHeight = stage.getHeight();

            // Position Pokemon absolutely based on stage size
            playerPokemonImage.setPosition(
                stageWidth * 0.2f,
                stageHeight * 0.2f
            );

            enemyPokemonImage.setPosition(
                stageWidth * 0.7f,
                stageHeight * 0.6f
            );

            // Force proper scaling
            playerPokemonImage.setScale(POKEMON_SCALE);
            enemyPokemonImage.setScale(POKEMON_SCALE);
        }

        invalidate();
        validate();
    }

    // Update the updateHPBarColor method to use skin styles
    private void updateHPBarColor(ProgressBar bar, float percentage) {
        String styleKey;
        if (percentage > 0.5f) {
            styleKey = "hp-bar-green";
        } else if (percentage > 0.2f) {
            styleKey = "hp-bar-yellow";
        } else {
            styleKey = "hp-bar-red";
        }

        bar.setStyle(skin.get(styleKey, ProgressBar.ProgressBarStyle.class));
    }



    private TextureRegionDrawable createButtonBackground(Color color) {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(color);
        pixmap.fill();
        TextureRegion region = new TextureRegion(new Texture(pixmap));
        pixmap.dispose();
        return new TextureRegionDrawable(region);
    }

    private void initializeTextures() {
        // Load platform texture
        platformTexture = TextureManager.battlebacks.findRegion("battle_platform");
        if (platformTexture == null) {
            throw new RuntimeException("Failed to load battle platform texture");
        }

        // Load background texture
        battleBackground = TextureManager.battlebacks.findRegion("battle_bg_plains");
        if (battleBackground == null) {
            throw new RuntimeException("Failed to load battle background texture");
        }
    }

    private void setupHPBars() {
        // Player HP bar
        playerHPBar = new ProgressBar(0, playerPokemon.getStats().getHp(), 1, false,
            createHPBarStyle(playerPokemon.getCurrentHp() / (float) playerPokemon.getStats().getHp()));
        playerHPBar.setSize(HP_BAR_WIDTH, 8); // Reduce height
        playerHPBar.setValue(playerPokemon.getCurrentHp());

        // Enemy HP bar
        enemyHPBar = new ProgressBar(0, enemyPokemon.getStats().getHp(), 1, false,
            createHPBarStyle(enemyPokemon.getCurrentHp() / (float) enemyPokemon.getStats().getHp()));
        enemyHPBar.setSize(HP_BAR_WIDTH, 8); // Reduce height
        enemyHPBar.setValue(enemyPokemon.getCurrentHp());
    }

    private void handleBagButton() {
        // Implement bag functionality
        showNotImplementedMessage("Bag feature coming soon!");
    }

    private void handlePokemonButton() {
        // Implement Pokemon switch functionality
        showNotImplementedMessage("Pokemon switch feature coming soon!");
    }

    private void showNotImplementedMessage(String message) {
        // Show a message for unimplemented features
        battleText.setText(message);
    }


    private void createMoveMenu() {
        moveMenu = new Table();
        moveMenu.setFillParent(true);
        moveMenu.center();
        moveMenu.defaults().pad(5).size(200, 50);

        // Add moves from player's Pokemon
        for (Move move : playerPokemon.getMoves()) {
            TextButton moveButton = new TextButton(move.getName(), skin, "battle");
            moveButton.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    GameLogger.info("Move selected: " + move.getName());
                    executeMove(move, playerPokemon, enemyPokemon, true);
                }
            });
            moveMenu.add(moveButton).row();
        }

        // Add back button
        TextButton backButton = new TextButton("BACK", skin, "battle");
        backButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                showMoveMenu(false);
            }
        });
        moveMenu.add(backButton).row();

        // Initially hide move menu
        moveMenu.setVisible(false);
        stage.addActor(moveMenu);
    }

    public void showMoveMenu(boolean show) {
        GameLogger.info("Showing move menu: " + show);
        if (moveMenu == null) {
            createMoveMenu();
        }
        moveMenu.setVisible(show);
        actionMenu.setVisible(!show);
        moveMenuVisible = show;
    }
    private void showMoveSelection() {
        GameLogger.info("Showing move selection menu");
        try {
            // Hide action menu first
            if (actionMenu != null) {
                actionMenu.setVisible(false);
            }

            if (moveSelectionMenu != null) {
                moveSelectionMenu.remove();
            }

            // Initialize move menu
            moveSelectionMenu = new Table();
            moveSelectionMenu.setBackground(createTranslucentBackground(0.8f));
            moveSelectionMenu.defaults().pad(10).size(200, 50);
            moveSelectionMenu.setTouchable(Touchable.enabled);

            // Create move buttons grid
            Table moveGrid = new Table();
            moveGrid.defaults().pad(5).size(180, 45);
            moveGrid.setTouchable(Touchable.enabled);

            // Add moves
            List<Move> moves = playerPokemon.getMoves();
            for (int i = 0; i < moves.size(); i++) {
                final Move move = moves.get(i);
                final int moveIndex = i;

                Table moveButton = createMoveButton(move);
                moveButton.setTouchable(Touchable.enabled);

                moveButton.addListener(new ClickListener() {
                    @Override
                    public void clicked(InputEvent event, float x, float y) {
                        GameLogger.info("Move selected: " + move.getName());
                        selectedMoveIndex = moveIndex;
                        executeMove(move, playerPokemon, enemyPokemon, true);
                        hideMoveSelection();
                    }
                });

                // Add to grid in 2x2 layout
                if (i % 2 == 0) {
                    moveGrid.add(moveButton).padRight(10);
                } else {
                    moveGrid.add(moveButton).row();
                }
            }

            // Add back button
            TextButton backButton = new TextButton("BACK", skin);
            backButton.setTouchable(Touchable.enabled);
            backButton.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    GameLogger.info("Back button clicked");
                    hideMoveSelection();
                    showActionMenu(true);
                }
            });

            // Add everything to the menu
            moveSelectionMenu.add(moveGrid).expand().fill().pad(10).row();
            moveSelectionMenu.add(backButton).size(150, 40).pad(10);

            // Add to stage
            moveSelectionMenu.setPosition(
                (stage.getWidth() - moveSelectionMenu.getPrefWidth()) / 2,
                (stage.getHeight() - moveSelectionMenu.getPrefHeight()) / 2
            );

            stage.addActor(moveSelectionMenu);
            moveMenuVisible = true;

            GameLogger.info("Move selection menu created and shown");
        } catch (Exception e) {
            GameLogger.error("Error showing move selection: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Table createMoveInfoTable() {
        Table infoTable = new Table();
        infoTable.setBackground(new TextureRegionDrawable(TextureManager.getUi().findRegion("window")));

        // Top row with power and accuracy
        Table statsRow = new Table(skin);
        statsRow.add("Power: ").right();
        statsRow.add(powerLabel).left().padRight(20);
        statsRow.add("Accuracy: ").right();
        statsRow.add(accuracyLabel).left();

        // Move type row
        Table typeRow = new Table(skin);
        typeRow.add("Type: ").right();
        typeRow.add(moveTypeLabel).left();

        // Description spans full width
        descriptionLabel.setAlignment(Align.left);

        // Add all components to main table
        infoTable.add(statsRow).expandX().fillX().pad(5).row();
        infoTable.add(typeRow).expandX().fillX().pad(5).row();
        infoTable.add(descriptionLabel).expand().fill().pad(5);

        return infoTable;
    }

    private void updateMoveInfo(Move move) {
        if (powerLabel == null || accuracyLabel == null ||
            descriptionLabel == null || moveTypeLabel == null) {
            GameLogger.error("Move info labels not initialized");
            return;
        }

        // Update power
        String powerText = move.getPower() > 0 ? String.valueOf(move.getPower()) : "-";
        powerLabel.setText(powerText);

        // Update accuracy
        String accuracyText = move.getAccuracy() > 0 ? move.getAccuracy() + "%" : "-";
        accuracyLabel.setText(accuracyText);

        // Update type
        moveTypeLabel.setText(move.getType().toString());
        moveTypeLabel.setColor(TextureManager.getTypeColor(move.getType()));

        // Update description
        descriptionLabel.setText(move.getDescription());
    }

    private void hideMoveSelection() {
        if (moveSelectionMenu != null) {
            moveSelectionMenu.addAction(Actions.sequence(
                Actions.fadeOut(0.2f),
                Actions.removeActor()
            ));
        }
        moveMenuVisible = false;
    }

    private void initializeMoveLabels() {
        // Create labels with default style
        powerLabel = new Label("", skin);
        accuracyLabel = new Label("", skin);
        descriptionLabel = new Label("", skin);
        moveTypeLabel = new Label("", skin);

        // Configure label properties
        powerLabel.setFontScale(0.9f);
        accuracyLabel.setFontScale(0.9f);
        descriptionLabel.setFontScale(0.8f);
        moveTypeLabel.setFontScale(0.9f);

        // Enable text wrapping for description
        descriptionLabel.setWrap(true);
    }


    private Table createMoveButton(final Move move) {
        Table button = new Table();

        // Get type-based style
        Color typeColor = TYPE_COLORS.getOrDefault(move.getType(), TYPE_COLORS.get(Pokemon.PokemonType.NORMAL));
        button.setBackground(createGradientBackground(typeColor));

        // Move name
        Label nameLabel = new Label(move.getName(), new Label.LabelStyle(skin.getFont("default"), Color.WHITE));
        nameLabel.setFontScale(1.2f);

        // PP counter
        Label ppLabel = new Label(move.getPp() + "/" + move.getMaxPp(),
            new Label.LabelStyle(skin.getFont("default"), Color.WHITE));

        // Type icon
        Table typeIcon = createTypeIcon(move.getType());

        // Layout
        Table content = new Table();
        content.add(nameLabel).left().expandX().row();
        content.add(ppLabel).left().padTop(5);

        button.add(content).expand().fill().pad(10);
        button.add(typeIcon).size(30).right().pad(10);

        // Click handling
        button.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                executeMove(move, playerPokemon, enemyPokemon, true);
            }

            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                button.setColor(button.getColor().mul(1.2f));
                updateMoveInfo(move);
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                button.setColor(typeColor);
            }
        });

        return button;
    }

    private TextureRegionDrawable createGradientBackground(Color baseColor) {
        Pixmap pixmap = new Pixmap(300, 80, Pixmap.Format.RGBA8888);

        // Create gradient effect
        for (int x = 0; x < 300; x++) {
            float alpha = 0.9f;
            float factor = x / 300f;
            Color gradientColor = new Color(
                baseColor.r + (factor * 0.2f),
                baseColor.g + (factor * 0.2f),
                baseColor.b + (factor * 0.2f),
                alpha
            );
            pixmap.setColor(gradientColor);
            for (int y = 0; y < 80; y++) {
                pixmap.drawPixel(x, y);
            }
        }

        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return new TextureRegionDrawable(new TextureRegion(texture));
    }

    public void update(float delta) {
        stateTimer += delta;

        // Process any pending animations
        if (!pendingActions.isEmpty() && !isAnimating) {
            Action nextAction = pendingActions.removeIndex(0);
            addAction(nextAction);
        }

        // Update battle state
        switch (currentState) {
            case INTRO:
                updateIntroState();
                break;
            case PLAYER_TURN:
                updatePlayerTurn();
                break;
            case ENEMY_TURN:
                updateEnemyTurn();
                break;
            case ANIMATING:
                updateAnimations(delta);
                break;
            case ENDED:
                updateEndState();
                break;
        }

        // Update UI elements
        updateHPBars();
        updateStatusEffects();
    }

    private void finishMoveExecution(boolean isPlayerMove) {
        Move usedMove;
        if (isPlayerMove) {
            usedMove = playerPokemon.getMoves().get(selectedMoveIndex);
        } else {
            usedMove = enemyPokemon.getMoves().get(0);
        }
        applyEndOfTurnEffects(isPlayerMove ? playerPokemon : enemyPokemon);

        if (checkBattleEnd()) {
            return;
        }
        if (callback != null) {
            callback.onMoveUsed(
                isPlayerMove ? playerPokemon : enemyPokemon,
                usedMove,
                isPlayerMove ? enemyPokemon : playerPokemon
            );
            callback.onTurnEnd(isPlayerMove ? playerPokemon : enemyPokemon);
        }
        isAnimating = false;
        transitionToState(isPlayerMove ? BattleState.ENEMY_TURN : BattleState.PLAYER_TURN);

        playerHUD.update();
        enemyHUD.update();
    }

    private void updateEndState() {
        if (!isAnimating) {
            boolean playerWon = playerPokemon.getCurrentHp() > 0;

            SequenceAction endSequence = Actions.sequence(
                Actions.run(() -> showBattleText(
                    playerWon ? "Victory!" :
                        playerPokemon.getName() + " fainted!"
                )),
                Actions.delay(2.0f),
                Actions.parallel(
                    Actions.run(() -> {
                        playerPokemonImage.addAction(Actions.fadeOut(1.0f));
                        enemyPokemonImage.addAction(Actions.fadeOut(1.0f));
                    })
                ),
                Actions.delay(1.0f),
                Actions.run(() -> {
                    if (callback != null) {
                        callback.onBattleEnd(playerWon);
                    }
                })
            );

            addAction(endSequence);
            isAnimating = true;
        }
    }

    private void updateIntroState() {
        if (stateTimer >= ANIMATION_DURATION) {
            transitionToState(BattleState.PLAYER_TURN);
            showBattleText(generateBattleStartText());
        }
    }

    private void updatePlayerTurn() {
        if (!actionMenu.isVisible() && !isAnimating) {
            showActionMenu(true);
        }
    }

    private void updateEnemyTurn() {
        if (!isAnimating && stateTimer >= 0.5f) { // Small delay before enemy action
            executeEnemyMove();
        }
    }

    private void updateAnimations(float delta) {
        if (!isAnimating) {
            if (currentState == BattleState.PLAYER_TURN) {
                checkBattleEnd();
            } else if (currentState == BattleState.ENEMY_TURN) {
                transitionToState(BattleState.PLAYER_TURN);
            }
        }
    }

    private void executeMove(Move move, Pokemon attacker, Pokemon defender, boolean isPlayerMove) {
        isAnimating = true;

        // Create a sequence that will be executed in order
        SequenceAction moveSequence = Actions.sequence(
            // 1. Show move text
            Actions.run(() -> {
                showBattleText(attacker.getName() + " used " + move.getName() + "!");
            }),
            Actions.delay(0.5f),

            // 2. Play attack animation
            Actions.run(() -> {
                Image attackerSprite = (attacker == playerPokemon) ? playerPokemonImage : enemyPokemonImage;
                attackerSprite.addAction(Actions.sequence(
                    Actions.moveBy(0, 20, 0.1f),
                    Actions.moveBy(0, -20, 0.1f)
                ));
            }),
            Actions.delay(0.3f),

            // 3. Show damage effect
            Actions.run(() -> {
                float damage = calculateDamage(move, attacker, defender);
                applyDamage(defender, damage);

                // Flash the defender sprite
                Image defenderSprite = (defender == playerPokemon) ? playerPokemonImage : enemyPokemonImage;
                defenderSprite.addAction(Actions.sequence(
                    Actions.color(Color.RED),
                    Actions.delay(0.1f),
                    Actions.color(Color.WHITE)
                ));

                // Shake effect for powerful moves
                if (move.getPower() > 80) {
                    startShakeEffect(5f, 0.3f);
                }
            }),
            Actions.delay(0.5f),

            // 4. Show effectiveness message
            Actions.run(() -> {
                showEffectivenessMessage(move, defender);
            }),
            Actions.delay(0.5f),

            // 5. End move execution
            Actions.run(() -> {
                finishMoveExecution(isPlayerMove);
            })
        );

        // Add the sequence to the stage
        stage.addAction(moveSequence);
    }


    private void showEffectivenessMessage(Move move, Pokemon defender) {
        float effectiveness = calculateTypeEffectiveness(move.getType(), defender);

        if (effectiveness > 1.5f) {
            showBattleText("It's super effective!");
        } else if (effectiveness < 0.5f) {
            showBattleText("It's not very effective...");
        } else if (effectiveness == 0) {
            showBattleText("It had no effect...");
        }
    }

    private float calculateTypeEffectiveness(Pokemon.PokemonType moveType, Pokemon defender) {
        float effectiveness = getTypeEffectiveness(moveType, defender.getPrimaryType());

        if (defender.getSecondaryType() != null) {
            effectiveness *= getTypeEffectiveness(moveType, defender.getSecondaryType());
        }

        return effectiveness;
    }

    private float calculateDamage(Move move, Pokemon attacker, Pokemon defender) {
        float baseDamage = move.getPower() * (move.isSpecial() ?
            (float) attacker.getStats().getSpecialAttack() / defender.getStats().getSpecialDefense() :
            (float) attacker.getStats().getAttack() / defender.getStats().getDefense());

        float typeMultiplier = getTypeEffectiveness(move.getType(), defender.getPrimaryType());
        if (defender.getSecondaryType() != null) {
            typeMultiplier *= getTypeEffectiveness(move.getType(), defender.getSecondaryType());
        }

        float variation = MathUtils.random(0.85f, 1.0f);

        float statusMultiplier = attacker.getStatusModifier(move);

        return baseDamage * typeMultiplier * variation * statusMultiplier;
    }


    private void applyDamage(Pokemon target, float damage) {
        // Flash the Pokemon sprite
        Image targetSprite = target == playerPokemon ? playerPokemonImage : enemyPokemonImage;
        targetSprite.addAction(Actions.sequence(
            Actions.color(Color.RED),
            Actions.delay(DAMAGE_FLASH_DURATION),
            Actions.color(Color.WHITE)
        ));

        // Update HP with animation
        float newHP = Math.max(0, target.getCurrentHp() - damage);
        animateHPBar(target, newHP);

        // Set the final HP value
        target.setCurrentHp(newHP);
    }

    private void animateHPBar(Pokemon pokemon, float newHP) {
        ProgressBar hpBar = pokemon == playerPokemon ? playerHPBar : enemyHPBar;
        float startHP = hpBar.getValue();

        hpBar.addAction(Actions.sequence(
            Actions.run(() -> updateHPBarColor(hpBar, newHP / pokemon.getStats().getHp())),
            Actions.moveTo(startHP, newHP, HP_UPDATE_DURATION, Interpolation.smooth)
        ));
    }

    private boolean checkBattleEnd() {
        if (playerPokemon.getCurrentHp() <= 0 || enemyPokemon.getCurrentHp() <= 0 ||
            turnCount >= MAX_TURN_COUNT) {

            boolean playerWon = playerPokemon.getCurrentHp() > 0 && enemyPokemon.getCurrentHp() <= 0;
            currentState = BattleState.ENDED;
            isAnimating = true;

            // Create ending sequence
            SequenceAction endSequence = Actions.sequence(
                Actions.run(() -> {
                    if (turnCount >= MAX_TURN_COUNT) {
                        showBattleText("Battle ended in a draw!");
                    } else {
                        showBattleText(playerWon ? "Victory!" : playerPokemon.getName() + " fainted!");
                    }
                }),
                Actions.delay(2.0f),
                Actions.parallel(
                    Actions.run(() -> {
                        if (playerPokemonImage != null) {
                            playerPokemonImage.addAction(Actions.fadeOut(1.0f));
                        }
                        if (enemyPokemonImage != null) {
                            enemyPokemonImage.addAction(Actions.fadeOut(1.0f));
                        }
                    })
                ),
                Actions.delay(1.0f),
                Actions.run(() -> {
                    if (callback != null) {
                        callback.onBattleEnd(playerWon);
                    }
                    dispose();
                })
            );

            addAction(endSequence); // Add to BattleTable instead of battleScene
            return true;
        }
        return false;
    }


    private void showBattleText(String text) {
        battleText.setText(text);
        battleText.addAction(Actions.sequence(
            Actions.alpha(0),
            Actions.fadeIn(0.2f)
        ));
    }


    private void transitionToState(BattleState newState) {
        currentState = newState;
        stateTimer = 0;
        updateUI();
    }

    private String generateBattleStartText() {
        return "Wild " + enemyPokemon.getName() + " appeared!";
    }


    private void updateHPBars() {
        // Update player HP bar
        float playerHPPercent = playerPokemon.getCurrentHp() / playerPokemon.getStats().getHp();
        playerHPBar.setValue(playerPokemon.getCurrentHp());
        updateHPBarColor(playerHPBar, playerHPPercent);

        // Update enemy HP bar
        float enemyHPPercent = enemyPokemon.getCurrentHp() / enemyPokemon.getStats().getHp();
        enemyHPBar.setValue(enemyPokemon.getCurrentHp());
        updateHPBarColor(enemyHPBar, enemyHPPercent);
    }

    private void updateStatusEffects() {
        // Update player Pokemon status
        if (playerPokemon.hasStatus()) {
            playerHUD.updateStatusIcon(TextureManager.getStatusIcon(
                TextureManager.StatusCondition.valueOf(playerPokemon.getStatus().name())));
        }

        // Update enemy Pokemon status
        if (enemyPokemon.hasStatus()) {
            enemyHUD.updateStatusIcon(TextureManager.getStatusIcon(
                TextureManager.StatusCondition.valueOf(enemyPokemon.getStatus().name())));
        }
    }

    private void executeStruggle(Pokemon attacker, Pokemon defender) {
        // Struggle implementation - damages both Pokemon
        float damage = attacker.getStats().getAttack() * 0.5f;
        float recoil = damage * 0.25f;

        applyDamage(defender, damage);
        applyDamage(attacker, recoil);

        showBattleText(attacker.getName() + " used Struggle!");
    }

    private float getTypeEffectiveness(Pokemon.PokemonType attackType, Pokemon.PokemonType defendType) {
        if (attackType == null || defendType == null) {
            GameLogger.error("Null type detected in getTypeEffectiveness: attackType=" + attackType + ", defendType=" + defendType);
            return 1.0f; // Neutral effectiveness when types are unknown
        }
        ObjectMap<Pokemon.PokemonType, Float> effectivenessMap = typeEffectiveness.get(attackType);
        if (effectivenessMap == null) {
            GameLogger.error("No effectiveness data for attackType: " + attackType);
            return 1.0f; // Default to neutral effectiveness
        }
        return effectivenessMap.get(defendType, 1.0f);
    }


    private enum BattleState {
        INTRO,        // Initial battle animation
        PLAYER_TURN,  // Player selecting action
        ENEMY_TURN,   // Enemy AI selecting action
        ANIMATING,    // Move animations playing
        ENDED,        // Battle complete
        RUNNING,      // Attempting to flee
        CATCHING      // Pokeball throw animation
    }

    public interface BattleCallback {
        void onBattleEnd(boolean playerWon);

        void onTurnEnd(Pokemon activePokemon);

        void onStatusChange(Pokemon pokemon, Pokemon.Status newStatus);

        void onMoveUsed(Pokemon user, Move move, Pokemon target);
    }

    private static class PokemonHUD extends Table {
        private final Table statusContainer;
        private final Pokemon pokemon;
        private Label hpLabel;
        private ProgressBar hpBar;
        private Label nameLabel;
        private Label levelLabel;
        private Skin skin;

        public PokemonHUD(Skin skin, Pokemon pokemon, boolean isPlayer) {
            this.pokemon = pokemon;
            this.skin = skin;


            // Name and Level
            nameLabel = new Label(pokemon.getName(), skin);
            levelLabel = new Label("Lv." + pokemon.getLevel(), skin);

            // Top row
            Table topRow = new Table();
            topRow.add(nameLabel).left().expandX();
            topRow.add(levelLabel).right();

            // HP Label
            hpLabel = new Label((int) pokemon.getCurrentHp() + "/" + (int) pokemon.getStats().getHp(), skin);
            hpLabel.setFontScale(0.7f);

            statusContainer = new Table();
            statusContainer.setName("statusContainer");

            // Layout without backgrounds
            add(topRow).expandX().fillX().pad(2).row();
            add(hpLabel).expandX().pad(2);

            setVisible(true);
        }

        private Drawable createHUDBackground() {
            Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
            pixmap.setColor(0, 0, 0, 0.5f);
            pixmap.fill();
            TextureRegion region = new TextureRegion(new Texture(pixmap));
            pixmap.dispose();
            return new TextureRegionDrawable(region);
        }

        private String getHPStyleKey(float percentage) {
            if (percentage > 0.5f) {
                return "hp-bar-green";
            } else if (percentage > 0.2f) {
                return "hp-bar-yellow";
            } else {
                return "hp-bar-red";
            }
        }

        public void update() {
            levelLabel.setText("Lv." + pokemon.getLevel());

            float currentPercentage = pokemon.getCurrentHp() / (float) pokemon.getStats().getHp();
            String newStyleKey = getHPStyleKey(currentPercentage);

            hpBar.setStyle(skin.get(newStyleKey, ProgressBar.ProgressBarStyle.class));
            hpBar.setValue(pokemon.getCurrentHp());

            hpLabel.setText((int) pokemon.getCurrentHp() + "/" + (int) pokemon.getStats().getHp());
        }


        public void setHPBar(ProgressBar hpBar) {
            this.hpBar = hpBar;

            // Remove existing HP bar if present
            for (Cell<?> cell : getCells()) {
                if (cell.getActor() == hpBar) {
                    cell.setActor(null);
                }
            }

            // Add HP bar directly without any container
            add(hpBar).expandX().fillX().height(8).pad(4).row();
        }

        @Override
        public void layout() {
            super.layout();
            // Ensure proper scaling of the HUD components
            setScale(1.0f);
            invalidateHierarchy();
        }

        public void updateStatusIcon(TextureRegion statusIcon) {
            statusContainer.clear();
            if (statusIcon != null) {
                Image statusImage = new Image(statusIcon);
                statusContainer.add(statusImage).size(32).pad(2);
            }
        }
    }
}

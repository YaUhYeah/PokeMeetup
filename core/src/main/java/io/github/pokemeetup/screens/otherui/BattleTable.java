package io.github.pokemeetup.screens.otherui;

import com.badlogic.gdx.*;
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
import io.github.pokemeetup.pokemon.Pokemon;
import io.github.pokemeetup.pokemon.attacks.Move;
import io.github.pokemeetup.utils.BattleAssets;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.TextureManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BattleTable extends Table {
    public static final int HP_BAR_WIDTH = 150;
    private static final float PLAYER_PLATFORM_X = 0.2f;
    private static final float PLAYER_PLATFORM_Y = 0.2f;
    private static final float ENEMY_PLATFORM_X = 0.6f;
    private static final float ENEMY_PLATFORM_Y = 0.5f;

    private static final float POKEMON_SCALE = 2.0f;
    private static final float PLATFORM_SCALE = 1.2f;

    private static final float PLATFORM_VERTICAL_OFFSET = 30f;
    private static final float PLAYER_X_POSITION = 0.2f;
    private static final float PLAYER_Y_POSITION = 0.1f;
    private static final float ENEMY_X_POSITION = 0.7f;
    private static final float ENEMY_Y_POSITION = 0.5f;

    private static final float UI_SCALE = 0.75f;

    private static final float BUTTON_WIDTH = 160f * UI_SCALE;  // Wider buttons
    private static final float BUTTON_HEIGHT = 45f * UI_SCALE;  // Taller buttons
    private static final float BUTTON_PADDING = 12f * UI_SCALE;  // More space between buttons
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

    public BattleTable(Stage stage, Skin skin, Pokemon playerPokemon, Pokemon enemyPokemon) {
        super();
        this.stage = stage;
        this.skin = skin;
        this.playerPokemon = playerPokemon;
        this.enemyPokemon = enemyPokemon;
        this.shapeRenderer = new ShapeRenderer();
        this.cameraShake = new Vector2();
        this.currentState = BattleState.INTRO;
        this.isAnimating = true; // Start in animating state

        try {       // Initialize in proper order
            initializeTextures();
            initializeUIComponents();
            initializeMoveMenu(); // Move this up
            initializeMoveLabels();
            initializePlatforms();
            initializePokemonSprites();
            initializeHUDElements();
            setupHPBars();
            initializeBattleScene();
            setupButtonListeners();

            // Set initial visibility
            actionMenu.setVisible(false);
            moveMenu.setVisible(false);

            // Ensure proper layout
            setFillParent(true);

            // Important: Move initial positions offscreen
            if (playerPokemonImage != null) {
                playerPokemonImage.setPosition(-playerPokemonImage.getWidth() * POKEMON_SCALE,
                    playerPlatformY + PLATFORM_VERTICAL_OFFSET);
                playerPokemonImage.setVisible(true);
            }
            if (enemyPokemonImage != null) {
                enemyPokemonImage.setPosition(stage.getWidth() + enemyPokemonImage.getWidth() * POKEMON_SCALE,
                    enemyPlatformY + PLATFORM_VERTICAL_OFFSET);
                enemyPokemonImage.setVisible(true);
            }

            // Start battle sequence immediately
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

    private void showActionMenu(boolean show) {
        if (actionMenu != null) {
            actionMenu.setVisible(show);
        }

        if (moveMenu != null) {
            moveMenu.setVisible(!show);
        }

        if (show) {
            // Enable/disable buttons based on current state
            if (fightButton != null) fightButton.setDisabled(false);
            if (bagButton != null) bagButton.setDisabled(false);
            if (pokemonButton != null) {
                pokemonButton.setDisabled(playerPokemon.getCurrentHp() <= 0);
            }
            if (runButton != null) runButton.setDisabled(false);
        }
    }

    private void initializePlatforms() {
        platformTexture = TextureManager.getBattlebacks().findRegion("battle_platform");
        if (platformTexture == null) {
            throw new RuntimeException("Failed to load battle platform texture");
        }

        playerPlatform = new Image(platformTexture);
        enemyPlatform = new Image(platformTexture);

        float stageWidth = stage.getWidth();
        float stageHeight = stage.getHeight();

        // Calculate positions relative to the stage size
        playerPlatformX = stageWidth * PLAYER_PLATFORM_X;
        playerPlatformY = stageHeight * PLAYER_PLATFORM_Y;
        enemyPlatformX = stageWidth * ENEMY_PLATFORM_X;
        enemyPlatformY = stageHeight * ENEMY_PLATFORM_Y;

        // Set positions
        playerPlatform.setPosition(playerPlatformX, playerPlatformY);
        enemyPlatform.setPosition(enemyPlatformX, enemyPlatformY);

        // Adjust scaling if necessary
        playerPlatform.setScale(PLATFORM_SCALE * (stageWidth / 1280f)); // 1280f is your base width
        enemyPlatform.setScale(PLATFORM_SCALE * (stageWidth / 1280f));

        // Add to stage
        addActor(playerPlatform);
        addActor(enemyPlatform);
    }


    private void initializePokemonSprites() {
        float stageWidth = stage.getWidth();

        // Adjust sprite scales based on stage width
        float spriteScale = POKEMON_SCALE * (stageWidth / 1280f); // 1280f is your base width


        // Player Pokémon
        TextureRegion playerTexture = playerPokemon.getBackSprite();
        if (playerTexture == null) {
            throw new RuntimeException("Failed to load player Pokémon sprite");
        }

        playerPokemonImage = new Image(playerTexture);
        playerPokemonImage.setScale(spriteScale);

        float playerPokemonX = playerPlatformX + (playerPlatform.getWidth() * PLATFORM_SCALE - playerPokemonImage.getWidth() * spriteScale) / 2f;
        float playerPokemonY = playerPlatformY + PLATFORM_VERTICAL_OFFSET;

        playerPokemonImage.setPosition(playerPokemonX, playerPokemonY);
        addActor(playerPokemonImage);

        // Enemy Pokémon
        TextureRegion enemyTexture = enemyPokemon.getFrontSprite();
        if (enemyTexture == null) {
            throw new RuntimeException("Failed to load enemy Pokémon sprite");
        }

        enemyPokemonImage = new Image(enemyTexture);
        enemyPokemonImage.setScale(spriteScale);

        float enemyPokemonX = enemyPlatformX + (enemyPlatform.getWidth() * PLATFORM_SCALE - enemyPokemonImage.getWidth() * spriteScale) / 2f;
        float enemyPokemonY = enemyPlatformY + PLATFORM_VERTICAL_OFFSET;

        enemyPokemonImage.setPosition(enemyPokemonX, enemyPokemonY);
        addActor(enemyPokemonImage);
    }

    private void initializeHUDs() {
        playerHUD = new PokemonHUD(skin, playerPokemon, true);
        enemyHUD = new PokemonHUD(skin, enemyPokemon, false);

        float stageWidth = stage.getWidth();
        float stageHeight = stage.getHeight();

        playerHUD.setPosition(stageWidth * 0.55f, stageHeight * 0.1f);
        enemyHUD.setPosition(stageWidth * 0.05f, stageHeight * 0.75f);

        addActor(playerHUD);
        addActor(enemyHUD);
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
        float buttonWidth = stage.getWidth() * 0.4f; // 40% of screen width
        float buttonHeight = stage.getHeight() * 0.1f; // 10% of screen height

        // Initialize battle text
        battleText = new Label("", skin);
        battleText.setWrap(true);
        battleText.setAlignment(Align.center);

        // Create action menu
        actionMenu = new Table();
        actionMenu.defaults().pad(BUTTON_PADDING).size(BUTTON_WIDTH, BUTTON_HEIGHT);
        actionMenu.bottom().padBottom(20f);

        // Create button style
        TextButton.TextButtonStyle buttonStyle = new TextButton.TextButtonStyle();
        buttonStyle.font = skin.getFont("default");
        buttonStyle.up = createButtonBackground(new Color(0.4f, 0.4f, 0.8f, 0.8f));
        buttonStyle.down = createButtonBackground(new Color(0.3f, 0.3f, 0.7f, 0.8f));
        buttonStyle.over = createButtonBackground(new Color(0.5f, 0.5f, 0.9f, 0.8f));

        // Initialize buttons with touch enabled
        fightButton = new TextButton("FIGHT", buttonStyle);
        bagButton = new TextButton("BAG", buttonStyle);
        pokemonButton = new TextButton("POKEMON", buttonStyle);
        runButton = new TextButton("RUN", buttonStyle);

        // Enable touch for all buttons
        TextButton[] buttons = {fightButton, bagButton, pokemonButton, runButton};
        for (TextButton button : buttons) {
            button.setTouchable(Touchable.enabled);
            button.getLabel().setFontScale(UI_SCALE);
        }

        // Add button listeners
        setupButtonListeners();

        // Create button grid
        Table buttonGrid = new Table();
        buttonGrid.defaults().pad(BUTTON_PADDING);
        buttonGrid.setTouchable(Touchable.enabled);

        // Add buttons in a single row with equal spacing

        buttonGrid.add(fightButton).size(buttonWidth, buttonHeight).padRight(BUTTON_PADDING);
        buttonGrid.add(bagButton).size(buttonWidth, buttonHeight).padRight(BUTTON_PADDING);
        buttonGrid.row();
        buttonGrid.add(pokemonButton).size(buttonWidth, buttonHeight).padRight(BUTTON_PADDING);
        buttonGrid.add(runButton).size(buttonWidth, buttonHeight);
        // Position the button grid at the bottom
        actionMenu.clear();
        actionMenu.add(buttonGrid).expandX().bottom().padBottom(20f);
        actionMenu.setTouchable(Touchable.enabled);

        actionMenu.defaults().pad(BUTTON_PADDING).size(buttonWidth, buttonHeight);
        // Initialize move menu here
        initializeMoveMenu();

        // Initialize move labels
        initializeMoveLabels();

        // Create UI container
        Table uiContainer = new Table();
        uiContainer.setFillParent(true);
        uiContainer.bottom();

        uiContainer.add(battleText).expandX().fillX().pad(20f).row();
        uiContainer.add(actionMenu).expandX();

        // Add move menu to the container (ensure it's hidden initially)
        uiContainer.add(moveMenu).expand().fill();
        moveMenu.setVisible(false);

        addActor(uiContainer);
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

    private void updateHUDSize() {
        if (playerHUD != null) {
            playerHUD.setScale(0.8f); // Reduce size by 20%
        }
        if (enemyHUD != null) {
            enemyHUD.setScale(0.8f);
        }
    }

    private void updatePokemonPositions() {
        float viewportWidth = stage.getViewport().getWorldWidth();
        float viewportHeight = stage.getViewport().getWorldHeight();

        // Calculate platform positions with adjusted offsets
        playerPlatformX = viewportWidth * PLAYER_X_POSITION;
        playerPlatformY = viewportHeight * PLAYER_Y_POSITION;
        enemyPlatformX = viewportWidth * ENEMY_X_POSITION;
        enemyPlatformY = viewportHeight * ENEMY_Y_POSITION;

        if (playerPlatform != null && enemyPlatform != null) {
            // Update platform scaling and positions
            playerPlatform.setScale(PLATFORM_SCALE);
            enemyPlatform.setScale(PLATFORM_SCALE);
            playerPlatform.setPosition(playerPlatformX, playerPlatformY);
            enemyPlatform.setPosition(enemyPlatformX, enemyPlatformY);

            // Update Pokemon positions relative to platforms
            if (playerPokemonImage != null) {
                float pokemonX = playerPlatformX + (platformTexture.getRegionWidth() * PLATFORM_SCALE -
                    playerPokemonImage.getWidth() * POKEMON_SCALE) / 2f;
                float pokemonY = playerPlatformY + PLATFORM_VERTICAL_OFFSET;
                playerPokemonImage.setScale(POKEMON_SCALE);
                playerPokemonImage.setPosition(pokemonX, pokemonY);
            }

            if (enemyPokemonImage != null) {
                float pokemonX = enemyPlatformX + (platformTexture.getRegionWidth() * PLATFORM_SCALE -
                    enemyPokemonImage.getWidth() * POKEMON_SCALE) / 2f;
                float pokemonY = enemyPlatformY + PLATFORM_VERTICAL_OFFSET;
                enemyPokemonImage.setScale(POKEMON_SCALE);
                enemyPokemonImage.setPosition(pokemonX, pokemonY);
            }
        }
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

    // Update button listener setup
    private void setupButtonListeners() {
        // Create a dedicated button style with proper hit detection
        TextButton.TextButtonStyle buttonStyle = new TextButton.TextButtonStyle();
        buttonStyle.font = skin.getFont("default");
        buttonStyle.up = createButtonBackground(new Color(0.4f, 0.4f, 0.8f, 1f));
        buttonStyle.down = createButtonBackground(new Color(0.3f, 0.3f, 0.7f, 1f));
        buttonStyle.over = createButtonBackground(new Color(0.5f, 0.5f, 0.9f, 1f));

        // Update each button with new style and explicit touch handling
        TextButton[] buttons = {fightButton, bagButton, pokemonButton, runButton};
        for (TextButton button : buttons) {
            button.setStyle(buttonStyle);
            button.setTouchable(Touchable.enabled);
            button.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    if (!isAnimating && currentState == BattleState.PLAYER_TURN) {
                        handleButtonClick(button.getText().toString());
                    }
                }
            });
        }
    }

    private void createBattleControls() {
        Table buttonGrid = new Table();
        buttonGrid.setTouchable(Touchable.enabled);
        buttonGrid.defaults().pad(10).size(BUTTON_WIDTH, BUTTON_HEIGHT);

        // Ensure buttons are properly spaced and sized
        buttonGrid.add(fightButton).pad(5);
        buttonGrid.add(bagButton).pad(5).row();
        buttonGrid.add(pokemonButton).pad(5);
        buttonGrid.add(runButton).pad(5);

        // Position the button grid
        buttonGrid.setPosition(
            stage.getWidth() / 2f - buttonGrid.getPrefWidth() / 2f,
            stage.getHeight() * 0.2f
        );

        stage.addActor(buttonGrid);
    }


    private void startBattleAnimation() {
        GameLogger.info("Starting battle animation sequence");
        isAnimating = true;
        currentState = BattleState.INTRO;
        stateTimer = 0;

        // Calculate final positions
        float viewportWidth = stage.getWidth();
        float viewportHeight = stage.getHeight();

        final float playerFinalX = viewportWidth * PLAYER_X_POSITION;
        final float playerFinalY = viewportHeight * PLAYER_Y_POSITION;
        final float enemyFinalX = viewportWidth * ENEMY_X_POSITION;
        final float enemyFinalY = viewportHeight * ENEMY_Y_POSITION;

        // Create animation sequence
        SequenceAction introSequence = Actions.sequence(
            // Fade in background
            Actions.alpha(0),
            Actions.fadeIn(0.3f),

            // Move platforms into position
            Actions.parallel(
                Actions.run(() -> {
                    playerPlatform.setPosition(-playerPlatform.getWidth(), playerFinalY);
                    enemyPlatform.setPosition(viewportWidth + enemyPlatform.getWidth(), enemyFinalY);

                    playerPlatform.addAction(Actions.moveTo(playerFinalX, playerFinalY, 0.5f, Interpolation.swingOut));
                    enemyPlatform.addAction(Actions.moveTo(enemyFinalX, enemyFinalY, 0.5f, Interpolation.swingOut));
                }),
                Actions.delay(0.2f)
            ),

            // Move Pokemon into position
            Actions.parallel(
                Actions.run(() -> {
                    if (playerPokemonImage != null) {
                        playerPokemonImage.addAction(Actions.moveTo(
                            playerFinalX + (platformTexture.getRegionWidth() * PLATFORM_SCALE - playerPokemonImage.getWidth() * POKEMON_SCALE) / 2f,
                            playerFinalY + PLATFORM_VERTICAL_OFFSET,
                            0.5f,
                            Interpolation.swingOut
                        ));
                    }
                    if (enemyPokemonImage != null) {
                        enemyPokemonImage.addAction(Actions.moveTo(
                            enemyFinalX + (platformTexture.getRegionWidth() * PLATFORM_SCALE - enemyPokemonImage.getWidth() * POKEMON_SCALE) / 2f,
                            enemyFinalY + PLATFORM_VERTICAL_OFFSET,
                            0.5f,
                            Interpolation.swingOut
                        ));
                    }
                }),
                Actions.delay(0.5f)
            ),

            // Show intro text and transition to player turn
            Actions.run(() -> {
                showBattleText("Wild " + enemyPokemon.getName() + " appeared!");
                isAnimating = false;
                currentState = BattleState.PLAYER_TURN;
                updateUI();
            })
        );

        // Add sequence to stage
        addAction(introSequence);
    }

    public void resize(int width, int height) {
        // Update viewport while maintaining aspect ratio
        stage.getViewport().update(width, height, true);

        // Re-position Pokemon after resize
        updatePokemonPositions();
    }

    private ProgressBar.ProgressBarStyle createHPBarStyle(float percentage) {
        ProgressBar.ProgressBarStyle style = new ProgressBar.ProgressBarStyle();

        // Create background (dark gray)
        Pixmap bgPixmap = new Pixmap(HP_BAR_WIDTH, 16, Pixmap.Format.RGBA8888);
        bgPixmap.setColor(51 / 255f, 51 / 255f, 51 / 255f, 1); // #333333
        bgPixmap.fill();
        style.background = new TextureRegionDrawable(new TextureRegion(new Texture(bgPixmap)));
        bgPixmap.dispose();

        // Create foreground bar
        Pixmap fgPixmap = new Pixmap(HP_BAR_WIDTH, 14, Pixmap.Format.RGBA8888);

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
        float stageWidth = stage.getWidth();
        float stageHeight = stage.getHeight();

        // Calculate HP bar dimensions based on screen size
        float hpBarWidth = stageWidth * 0.25f;  // 25% of screen width
        float hpBarHeight = stageHeight * 0.02f; // 2% of screen height (adjust as needed)

        // Player HP bar
        playerHPBar = new ProgressBar(0, playerPokemon.getStats().getHp(), 1, false,
            createHPBarStyle(playerPokemon.getCurrentHp() / (float) playerPokemon.getStats().getHp()));
        playerHPBar.setSize(hpBarWidth, hpBarHeight);
        playerHPBar.setValue(playerPokemon.getCurrentHp());

        // Enemy HP bar
        enemyHPBar = new ProgressBar(0, enemyPokemon.getStats().getHp(), 1, false,
            createHPBarStyle(enemyPokemon.getCurrentHp() / (float) enemyPokemon.getStats().getHp()));
        enemyHPBar.setSize(hpBarWidth, hpBarHeight);
        enemyHPBar.setValue(enemyPokemon.getCurrentHp());

        // Make sure both are visible
        playerHPBar.setVisible(true);
        enemyHPBar.setVisible(true);

        // Add HP bars to HUDs
        playerHUD.setHPBar(playerHPBar);
        enemyHUD.setHPBar(enemyHPBar);
    }

    private void createFallbackDataboxes() {
        // Create simple colored backgrounds as fallback
        Pixmap bgPixmap = new Pixmap(200, 100, Pixmap.Format.RGBA8888);
        bgPixmap.setColor(0.2f, 0.2f, 0.2f, 0.8f);
        bgPixmap.fill();
        TextureRegionDrawable fallbackBg = new TextureRegionDrawable(
            new TextureRegion(new Texture(bgPixmap)));
        playerHUD.setBackground(fallbackBg);
        enemyHUD.setBackground(fallbackBg);
        bgPixmap.dispose();
    }


    private void initializeHUDElements() {
        float hudWidth = stage.getWidth() * 0.3f; // 30% of screen width
        float hudHeight = stage.getHeight() * 0.15f; // 15% of screen height

        Table hudContainer = new Table();
        hudContainer.setFillParent(true);

        // Position enemy HUD at top-left
        hudContainer.add(enemyHUD)
            .width(hudWidth)
            .height(hudHeight)
            .padLeft(stage.getWidth() * 0.05f)
            .padTop(stage.getHeight() * 0.05f)
            .expand()
            .left()
            .top();

        hudContainer.row();

        // Position player HUD at bottom-right
        hudContainer.add(playerHUD)
            .width(hudWidth)
            .height(hudHeight)
            .padRight(stage.getWidth() * 0.05f)
            .padBottom(stage.getHeight() * 0.05f)
            .expand()
            .right()
            .bottom();

        addActor(hudContainer);
    }


    @Override
    public void draw(Batch batch, float parentAlpha) {
        super.draw(batch, parentAlpha);

        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        // Must end batch before shape rendering
        batch.end();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setProjectionMatrix(getStage().getCamera().combined);
        shapeRenderer.setColor(Color.RED);

        stage.setKeyboardFocus(this);
        stage.setScrollFocus(this);
        for (TextButton button : new TextButton[]{fightButton, bagButton, pokemonButton, runButton}) {
            if (button != null && button.isVisible()) {
                Vector2 pos = button.localToStageCoordinates(new Vector2(0, 0));
                shapeRenderer.rect(pos.x, pos.y, button.getWidth(), button.getHeight());
            }
        }

        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        // Resume batch
        batch.begin();
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
        if (moveSelectionMenu != null) {
            moveSelectionMenu.remove();
        }
        initializeMoveLabels(); // New helper method
        moveSelectionMenu = new Table();

        moveSelectionMenu.defaults().pad(10).size(200, 50);

        // Calculate position to appear above the battle controls
        float viewportWidth = getStage().getViewport().getWorldWidth();
        float viewportHeight = getStage().getViewport().getWorldHeight();

        // Create move buttons grid (2x2)
        Table moveGrid = new Table();
        moveGrid.defaults().pad(5).size(180, 45);

        // Get button textures
        TextureRegion buttonTexture = TextureManager.getUi().findRegion("battle-buttons");
        TextButton.TextButtonStyle moveStyle = new TextButton.TextButtonStyle();
        moveStyle.up = new TextureRegionDrawable(new TextureRegion(buttonTexture, 0, 0, 192, 48));
        moveStyle.down = new TextureRegionDrawable(new TextureRegion(buttonTexture, 192, 0, 192, 48));
        moveStyle.font = skin.getFont("default");
        moveStyle.fontColor = Color.WHITE;

        // Create buttons for each move
        List<Move> moves = playerPokemon.getMoves();
        int index = 0;
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 2; col++) {
                if (index < moves.size()) {
                    Move move = moves.get(index);
                    final int moveIndex = index;

                    // Create move button with type color
                    Table moveButton = createMoveButton(move);

                    moveButton.addListener(new ClickListener() {
                        @Override
                        public void clicked(InputEvent event, float x, float y) {
                            executeMove(moves.get(moveIndex), playerPokemon, enemyPokemon, true);
                            hideMoveSelection();
                        }

                        @Override
                        public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                            // Update move info when hovering
                            updateMoveInfo(move);
                        }
                    });

                    moveGrid.add(moveButton).expand().fill();

                    if (col == 0) {
                        moveGrid.add().expandX().fill();
                    }
                } else {
                    // Empty slot
                    moveGrid.add().expand().fill();
                    if (col == 0) {
                        moveGrid.add().expandX().fill();
                    }
                }
                index++;
            }
            if (row == 0) {
                moveGrid.row();
            }
        }

        // Move info section
        Table moveInfo = new Table();
        moveInfo.setBackground(new TextureRegionDrawable(TextureManager.getUi().findRegion("window")));
        moveInfo.add(createMoveInfoTable()).expand().fill().pad(10);

        // Back button
        TextButton backButton = new TextButton("BACK", moveStyle);
        backButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                hideMoveSelection();
            }
        });

        // Add everything to the menu
        moveSelectionMenu.add(moveGrid).expand().fill().pad(10).row();
        moveSelectionMenu.add(moveInfo).expand().fill().pad(10).row();
        moveSelectionMenu.add(backButton).size(150, 40).pad(10);

        // Position the menu
        moveSelectionMenu.setPosition(
            viewportWidth * 0.5f - moveSelectionMenu.getPrefWidth() * 0.5f,
            viewportHeight * 0.3f
        );

        // Add to stage with fade-in
        moveSelectionMenu.getColor().a = 0f;
        moveSelectionMenu.addAction(Actions.fadeIn(0.2f));
        addActor(moveSelectionMenu);
        moveMenuVisible = true;
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
        private final Label hpLabel;
        private final Pokemon pokemon;
        private ProgressBar hpBar;
        private Label nameLabel;
        private Label levelLabel;
        private Skin skin;

        public PokemonHUD(Skin skin, Pokemon pokemon, boolean isPlayer) {
            this.pokemon = pokemon;

            Table topRow = new Table();
            nameLabel = new Label(pokemon.getName(), skin);
            levelLabel = new Label("Lv." + pokemon.getLevel(), skin);


            // Adjust layout and padding
            add(topRow).expandX().fillX().pad(5).row();

            // Set background for visibility
            setBackground(createHUDBackground());
            topRow.add(nameLabel).left().expandX();
            topRow.add(levelLabel).right();

            // Get initial HP percentage
            float hpPercentage = pokemon.getCurrentHp() / (float) pokemon.getStats().getHp();
            String styleKey = getHPStyleKey(hpPercentage);

            // Create HP bar with correct style
            hpBar = new ProgressBar(0, pokemon.getStats().getHp(), 1, false,
                skin.get(styleKey, ProgressBar.ProgressBarStyle.class));
            hpBar.setValue(pokemon.getCurrentHp());

            add(hpBar).expandX().fillX().pad(5).row();
            hpLabel = new Label(pokemon.getCurrentHp() + "/" + pokemon.getStats().getHp(), skin);

            hpLabel.setFontScale(0.7f);
            add(hpLabel).expandX().pad(5);
            statusContainer = new Table();
            statusContainer.setName("statusContainer");

            add(topRow).expandX().fillX().pad(2).row();
            add(hpBar).expandX().fillX().pad(2).row();
            add(hpLabel).expandX().pad(2).row();
            add(statusContainer).expandX().pad(2);

            if (!isPlayer) {
                hpLabel.setVisible(false);
            }
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

        // Add this method to PokemonHUD class
        public void updateLayout(float width, float height) {
            clearChildren();

            Table topRow = new Table();
            nameLabel = new Label(pokemon.getName(), skin);
            levelLabel = new Label("Lv." + pokemon.getLevel(), skin);

            // Scale font sizes based on HUD size
            float fontScale = height / 100f;  // Adjust divisor to change text size
            nameLabel.setFontScale(fontScale);
            levelLabel.setFontScale(fontScale);

            topRow.add(nameLabel).left().expandX();
            topRow.add(levelLabel).right();

            // Layout components with proper scaling
            add(topRow).expandX().fillX().pad(width * 0.05f).row();
            add(hpBar).expandX().fillX().height(height * 0.15f).pad(width * 0.05f).row();
            add(hpLabel).expandX().pad(width * 0.05f).row();
            add(statusContainer).expandX().pad(width * 0.05f);

            // Scale the entire HUD
            setScale(1);
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

            // Constrain health bar within info box
            float hpBarWidth = INFO_BOX_WIDTH * 0.85f; // Slightly smaller than info box
            hpBar.setSize(hpBarWidth, 12f);
            hpBar.setStyle(skin.get(newStyleKey, ProgressBar.ProgressBarStyle.class));
            hpBar.setValue(pokemon.getCurrentHp());

            // Update HP text
            hpLabel.setText(pokemon.getCurrentHp() + "/" + pokemon.getStats().getHp());
        }

        public void setHPBar(ProgressBar hpBar) {
            this.hpBar = hpBar;
            // Add the HP bar to the HUD layout
            add(hpBar).expandX().fillX().pad(5).row();
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

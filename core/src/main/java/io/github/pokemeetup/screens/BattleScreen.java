//package io.github.pokemeetup.screens;
//
//import com.badlogic.gdx.*;
//import com.badlogic.gdx.graphics.Color;
//import com.badlogic.gdx.graphics.GL20;
//import com.badlogic.gdx.graphics.Pixmap;
//import com.badlogic.gdx.graphics.Texture;
//import com.badlogic.gdx.graphics.g2d.BitmapFont;
//import com.badlogic.gdx.graphics.g2d.SpriteBatch;
//import com.badlogic.gdx.graphics.g2d.TextureAtlas;
//import com.badlogic.gdx.graphics.g2d.TextureRegion;
//import com.badlogic.gdx.math.MathUtils;
//import com.badlogic.gdx.scenes.scene2d.Actor;
//import io.github.pokemeetup.pokemon.attacks.Move;
//import com.badlogic.gdx.scenes.scene2d.InputEvent;
//import com.badlogic.gdx.scenes.scene2d.InputListener;
//import com.badlogic.gdx.scenes.scene2d.Stage;
//import com.badlogic.gdx.scenes.scene2d.actions.Actions;
//import com.badlogic.gdx.scenes.scene2d.ui.*;
//import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar.ProgressBarStyle;
//import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
//import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
//import com.badlogic.gdx.utils.Align;
//import com.badlogic.gdx.utils.Timer;
//import com.badlogic.gdx.utils.viewport.ScreenViewport;
//import io.github.pokemeetup.audio.AudioManager;
//import io.github.pokemeetup.pokemon.Pokemon;
//import io.github.pokemeetup.pokemon.WildPokemon;
//import io.github.pokemeetup.pokemon.attacks.Move;
//import io.github.pokemeetup.screens.otherui.BattleLayout;
//import io.github.pokemeetup.system.battle.BattleCompletionHandler;
//import io.github.pokemeetup.system.battle.BattleResult;
//import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;
//import io.github.pokemeetup.utils.TextureManager;
//
//import java.util.*;
//import java.util.List;
//import java.util.stream.Collectors;
//
//import static io.github.pokemeetup.utils.TextureManager.*;
//
//import io.github.pokemeetup.utils.TextureManager.PokemonType;  // Use this instead of Pokemon.PokemonType
//
//public class BattleScreen implements Screen {
//    private static final float INFO_BOX_WIDTH = 240f;
//    private static final float INFO_BOX_HEIGHT = 80f;
//    private static final float HEALTH_BAR_WIDTH = 180f;
//    private static final float MOVE_BUTTON_WIDTH = 200f;
//    private static final float MOVE_BUTTON_HEIGHT = 60f;
//    private static final float PLATFORM_OFFSET_Y = 50f;
//    private static final float MESSAGE_BOX_HEIGHT = 100f;
//    private static final AudioManager.SoundEffect CURSOR_MOVE_SOUND = AudioManager.SoundEffect.CURSOR_MOVE;
//    private static final AudioManager.SoundEffect MOVE_SELECT_SOUND = AudioManager.SoundEffect.MOVE_SELECT;
//    private static final AudioManager.SoundEffect DAMAGE_SOUND = AudioManager.SoundEffect.DAMAGE;
//    private static final AudioManager.SoundEffect CRITICAL_HIT_SOUND = AudioManager.SoundEffect.CRITICAL_HIT;
//    private static final AudioManager.SoundEffect SUPER_EFFECTIVE_SOUND = AudioManager.SoundEffect.SUPER_EFFECTIVE;
//    private static final AudioManager.SoundEffect NOT_EFFECTIVE_SOUND = AudioManager.SoundEffect.NOT_EFFECTIVE;
//    private static final AudioManager.SoundEffect BATTLE_WIN_SOUND = AudioManager.SoundEffect.BATTLE_WIN;
//    private static final float TEXT_ANIMATION_SPEED = 0.03f;
//    // Colors for HP bars
//    private static final Color HP_GREEN = new Color(0.2f, 0.92f, 0.2f, 1f);  // Bright green
//    private static final Color HP_YELLOW = new Color(1f, 0.83f, 0.0f, 1f);   // Golden yellow
//    private static final Color HP_RED = new Color(0.93f, 0.26f, 0.26f, 1f);  // Bright red
//    private static final float CURSOR_BLINK_TIME = 0.5f;
//    private static final float MOVE_TRANSITION_TIME = 0.3f;
//    private static final Color WINDOW_BG = new Color(0.2f, 0.2f, 0.2f, 0.9f);
//    private static final Color BUTTON_BG = new Color(0.3f, 0.3f, 0.3f, 1f);
//    private static final Color BUTTON_SELECTED_BG = new Color(0.4f, 0.4f, 0.6f, 1f);
//    private static final Color STATUS_BG = new Color(0.25f, 0.25f, 0.25f, 0.9f);
//    private final Stage stage;
//    private final SpriteBatch batch;
//    private final Pokemon playerPokemon;
//    private final WildPokemon wildPokemon;
//    private final BiomeType biomeType;
//    private final TextureAtlas battleAtlas;
//    private final Skin skin;
//    private final BattleLayout layout;
//    private Label descriptionLabel;
//    private Image typeIcon;
//    private Table statusIconsTable;
//    private TextureRegionDrawable normalMoveStyle;
//    private TextureRegionDrawable selectedMoveStyle;
//    // UI Components
//    private Table battleUI;
//    private Table moveButtons;
//    private Table infoBox;
//    private Table messageBox;
//    private Label battleText;
//    private Window playerPokemonWindow;
//    private Window enemyPokemonWindow;
//    private ProgressBar playerHealthBar;
//    private ProgressBar enemyHealthBar;
//    private Table moveButtonsGrid;
//
//    private Label playerPokemonName;
//    private Label enemyPokemonName;
//    private Label playerPokemonLevel;
//    private Label enemyPokemonLevel;
//    private Label playerHpLabel;
//    private Label enemyHpLabel;
//    // Battle state
//    private Queue<String> messageQueue = new LinkedList<>();
//    private boolean waitingForInput = false;
//    private boolean battleStarted = false;
//    private float textAnimationTimer = 0f;
//    private String currentMessage = "";
//    private int currentCharIndex = 0;
//    private ProgressBarStyle greenHPStyle;
//    private ProgressBarStyle yellowHPStyle;
//    private ProgressBarStyle redHPStyle;
//    private BattleState battleState = BattleState.INTRO;
//    private boolean isPlayerTurn = true;
//    // Add new fields for control handling
//    private int selectedMoveIndex = 0;
//    private float cursorBlinkTimer = 0;
//    private boolean cursorVisible = true;
//    private boolean canInput = true;
//    private float moveTransitionTimer = 0;
//    private BattleCompletionHandler completionHandler;
//    private boolean battleWon;
//    private Label moveDescriptionLabel;
//    private Image moveTypeIcon;
//    // Declare at the class level
//    private Table enemyStatusContainer;
//    private Table playerStatusContainer;
//    private Runnable battleCompletionHandler;
//    private final InputProcessor battleInputProcessor = new InputAdapter() {
//        @Override
//        public boolean keyDown(int keycode) {
//            if (!canInput) return false;
//
//            switch (keycode) {
//                case Input.Keys.UP:
//                    updateSelectedMove(-2); // Move up in grid
//                    return true;
//                case Input.Keys.DOWN:
//                    updateSelectedMove(2);  // Move down in grid
//                    return true;
//                case Input.Keys.LEFT:
//                    updateSelectedMove(-1); // Move left in grid
//                    return true;
//                case Input.Keys.RIGHT:
//                    updateSelectedMove(1);  // Move right in grid
//                    return true;
//                case Input.Keys.ENTER:
//                case Input.Keys.SPACE:
//                    if (battleState == BattleState.PLAYER_TURN) {
//                        selectCurrentMove();
//                    } else if (battleState == BattleState.MESSAGE_WAIT) {
//                        advanceMessage();
//                    }
//                    return true;
//                case Input.Keys.ESCAPE:
//                    showBattleMenu();
//                    return true;
//            }
//            return false;
//        }
//    };
//
//    public BattleScreen(Pokemon playerPokemon, WildPokemon wildPokemon,
//                        BiomeType biomeType, TextureAtlas battleAtlas, Skin skin) {
//        this.playerPokemon = playerPokemon;
//        this.wildPokemon = wildPokemon;
//        this.biomeType = biomeType;
//        this.battleAtlas = battleAtlas;
//        this.skin = skin;
//        initializeStyles();
//        if (!skin.has("default", ProgressBar.ProgressBarStyle.class)) {
//            // Create a basic style
//            ProgressBar.ProgressBarStyle progressBarStyle = new ProgressBar.ProgressBarStyle();
//            // Create background
//            progressBarStyle.background = createColoredDrawable(new Color(0.3f, 0.3f, 0.3f, 1));
//            // Create knob
//            progressBarStyle.knobBefore = createColoredDrawable(new Color(0.2f, 0.8f, 0.2f, 1));
//            skin.add("default", progressBarStyle);
//        }
//
//        this.batch = new SpriteBatch();
//        this.stage = new Stage(new ScreenViewport());
//        setupHealthBarStyles(); // Remove the 's' typo
//        setupUI();
//        initializeBattle();
//        this.layout = new BattleLayout(stage, skin);
//
//        // Add keyboard/controller input handling
//        stage.addListener(new InputListener() {
//            @Override
//            public boolean keyDown(InputEvent event, int keycode) {
//                if (!canInput) return false;
//
//                switch (keycode) {
//                    case Input.Keys.UP:
//                        selectMove(-2); // Move up in grid
//                        return true;
//                    case Input.Keys.DOWN:
//                        selectMove(2);  // Move down in grid
//                        return true;
//                    case Input.Keys.LEFT:
//                        selectMove(-1); // Move left
//                        return true;
//                    case Input.Keys.RIGHT:
//                        selectMove(1);  // Move right
//                        return true;
//                    case Input.Keys.ENTER:
//                    case Input.Keys.SPACE:
//                        confirmSelection();
//                        return true;
//                    case Input.Keys.ESCAPE:
//                        showBattleMenu();
//                        return true;
//                }
//                return false;
//            }
//        });
//
//        // Set up input processor to handle both stage and battle input
//        InputMultiplexer multiplexer = new InputMultiplexer();
//        multiplexer.addProcessor(stage);
//        multiplexer.addProcessor(battleInputProcessor);
//        Gdx.input.setInputProcessor(multiplexer);
//    }
//
//    private void confirmSelection() {
//        if (battleState == BattleState.PLAYER_TURN) {
//            // Handle move selection
//            if (selectedMoveIndex < playerPokemon.getMoves().size()) {
//                io.github.pokemeetup.pokemon.attacks.Move selectedMove = playerPokemon.getMoves().get(selectedMoveIndex);
//                if (selectedMove.getPp() > 0) {
//                    AudioManager.getInstance().playSound(MOVE_SELECT_SOUND);
//                    executeMove(selectedMove);
//                } else {
//                    showMessage("No PP left for this move!");
//                }
//            }
//        } else if (battleState == BattleState.MESSAGE_WAIT) {
//            // Handle message advancement
//            advanceMessage();
//        }
//    }
//
//    private void updateMoveDescription(Move move) {
//        if (moveDescriptionLabel != null) {
//            // Update the move description text
//            moveDescriptionLabel.setText(move.getDescription());
//        }
//
//        if (moveTypeIcon != null) {
//            // Update the type icon image
//            TextureRegion typeIconRegion = TextureManager.getTypeIcon(move.getType());
//            moveTypeIcon.setDrawable(new TextureRegionDrawable(typeIconRegion));
//        }
//    }
//
//
//    private void updateMoveSelection() {
//        // Update visual state of move buttons
//        for (int i = 0; i < moveButtonsGrid.getChildren().size; i++) {
//            Actor actor = moveButtonsGrid.getChildren().get(i);
//            if (actor instanceof Table) {
//                Table moveCell = (Table) actor;
//
//                if (i == selectedMoveIndex) {
//                    // Highlight the selected move
//                    moveCell.setBackground(selectedMoveStyle);
//                    // Update move description
//                    if (i < playerPokemon.getMoves().size()) {
//                        Move move = playerPokemon.getMoves().get(i);
//                        updateMoveDescription(move);
//                    }
//                } else {
//                    // Set normal background for unselected moves
//                    moveCell.setBackground(normalMoveStyle);
//                }
//            }
//        }
//    }
//
//    private void updatePokemonStatus(Pokemon pokemon) {
//        if (pokemon == playerPokemon) {
//            updateStatusIcons(playerPokemon, playerStatusContainer);
//        } else if (pokemon == wildPokemon) {
//            updateStatusIcons(wildPokemon, enemyStatusContainer);
//        }
//    }
//
//    // Enhance move button creation to support selection feedback
//    private Table createMoveButton(io.github.pokemeetup.pokemon.attacks.Move move) {
//        Table moveCell = new Table();
//        moveCell.setBackground(normalMoveStyle);
//        moveCell.pad(6);
//
//        // Create content container
//        Table content = new Table();
//        content.defaults().pad(2);
//
//        // Move name with proper styling
//        Label nameLabel = new Label(move.getName(), skin);
//        nameLabel.setFontScale(1.2f);
//        nameLabel.setColor(Color.WHITE);
//
//        // PP counter with smaller font
//        Label ppLabel = new Label("PP " + move.getPp() + "/" + move.getMaxPp(), skin);
//        ppLabel.setFontScale(0.9f);
//        ppLabel.setColor(Color.LIGHT_GRAY);
//
//        // Type indicator with icon
//        Table typeIndicator = new Table();
//        typeIndicator.setBackground(createColoredDrawable(TextureManager.getTypeColor(move.getType())));
//        typeIndicator.pad(2);
//
//        Image typeIconImg = new Image(TextureManager.getTypeIcon(move.getType()));
//        typeIndicator.add(typeIconImg).size(16, 16);
//
//        // Layout
//        content.add(nameLabel).left().expandX();
//        content.add(typeIndicator).right().padLeft(4).row();
//        content.add(ppLabel).right().padTop(4).colspan(2);
//
//        moveCell.add(content).grow();
//
//        // Add hover and click listeners
//        moveCell.addListener(new ClickListener() {
//            @Override
//            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
//                if (battleState == BattleState.PLAYER_TURN) {
//                    moveCell.setBackground(selectedMoveStyle);
//                    updateMoveDescription(move);
//                }
//            }
//
//            @Override
//            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
//                if (moveCell != moveButtons.getCells().get(selectedMoveIndex).getActor()) {
//                    moveCell.setBackground(normalMoveStyle);
//                }
//            }
//
//            @Override
//            public void clicked(InputEvent event, float x, float y) {
//                if (battleState == BattleState.PLAYER_TURN) {
//                    int index = moveButtons.getChildren().indexOf(moveCell, true);
//                    if (index != -1) {
//                        selectMove(index);
//                        confirmSelection();
//                    }
//                }
//            }
//        });
//
//        return moveCell;
//    }
//
//    private void setupMoveButtons() {
//        moveButtons = new Table();
//        moveButtons.setBackground(createColoredDrawable(new Color(0, 0, 0, 0.8f)));
//        moveButtons.setBounds(10, 10, Gdx.graphics.getWidth() - 20, 180);
//
//        // Create move buttons grid
//        moveButtonsGrid = new Table();
//        moveButtonsGrid.defaults().pad(6).size(MOVE_BUTTON_WIDTH, MOVE_BUTTON_HEIGHT);
//
//        // Add move buttons to grid
//        List<Move> moves = playerPokemon.getMoves();
//        for (int i = 0; i < 4; i++) {
//            if (i < moves.size()) {
//                Table moveButton = createMoveButton(moves.get(i));
//                moveButtonsGrid.add(moveButton).expand().fill();
//            } else {
//                moveButtonsGrid.add().expand().fill(); // Empty space
//            }
//
//            if (i % 2 == 1) moveButtonsGrid.row();
//        }
//
//        moveButtons.add(moveButtonsGrid).expand().fill();
//        stage.addActor(moveButtons);
//
//        // Initially hide move buttons
//        moveButtons.setVisible(false);
//    }
//
//    private void selectMove(int delta) {
//        if (battleState != BattleState.PLAYER_TURN) return;
//
//        int oldIndex = selectedMoveIndex;
//        selectedMoveIndex = calculateNewMoveIndex(selectedMoveIndex, delta, playerPokemon.getMoves().size());
//
//        if (oldIndex != selectedMoveIndex) {
//            AudioManager.getInstance().playSound(CURSOR_MOVE_SOUND);
//            updateMoveSelection();
//        }
//    }
//
//    private int calculateNewMoveIndex(int current, int delta, int maxMoves) {
//        int newIndex = current + delta;
//
//        // Handle 2x2 grid wraparound
//        if (delta == -2 || delta == 2) { // Vertical movement
//            if (newIndex < 0) newIndex += 4;
//            if (newIndex >= 4) newIndex -= 4;
//        } else { // Horizontal movement
//            if (newIndex < 0) newIndex = maxMoves - 1;
//            if (newIndex >= maxMoves) newIndex = 0;
//        }
//
//        return Math.min(newIndex, maxMoves - 1);
//    }
//
//    private void setupPokemonInfoBoxWithTypes(Window window, Pokemon pokemon, boolean isPlayer) {
//        window.setSize(INFO_BOX_WIDTH, INFO_BOX_HEIGHT);
//        window.pad(8);
//        window.setBackground(createColoredDrawable(WINDOW_BG));
//
//        Table content = new Table();
//        content.defaults().pad(2);
//
//        // Name and Level section with colored background
//        Table nameTag = new Table();
//        nameTag.setBackground(createColoredDrawable(BUTTON_BG));
//        nameTag.pad(4);
//
//        Label.LabelStyle largeLabelStyle = new Label.LabelStyle(skin.get(Label.LabelStyle.class));
//        largeLabelStyle.font.getData().setScale(1.2f);
//
//        Label nameLabel = new Label(pokemon.getName(), largeLabelStyle);
//        Label levelLabel = new Label("Lv." + pokemon.getLevel(), largeLabelStyle);
//        nameLabel.setColor(Color.WHITE);
//        levelLabel.setColor(Color.WHITE);
//
//        nameTag.add(nameLabel).left().expandX();
//        nameTag.add(levelLabel).right().padLeft(10);
//
//        // HP Section
//        Table hpRow = new Table();
//        Label hpLabel = new Label("HP", skin);
//        hpLabel.setColor(Color.WHITE);
//
//        ProgressBarStyle hpBarStyle = skin.get(ProgressBarStyle.class);
//        hpBarStyle.background.setMinHeight(12);
//        hpBarStyle.knobBefore.setMinHeight(12);
//
//        ProgressBar hpBar = new ProgressBar(0, pokemon.getStats().getHp(), 1, false, hpBarStyle);
//        hpBar.setValue(pokemon.getCurrentHp());
//        updateHealthBar(hpBar, pokemon.getCurrentHp(), pokemon.getStats().getHp());
//
//        hpRow.add(hpLabel).left().padRight(10);
//        hpRow.add(hpBar).width(HEALTH_BAR_WIDTH * 1.2f).height(12).left();
//
//        Label hpNumbers = new Label(pokemon.getCurrentHp() + "/" + pokemon.getStats().getHp(), skin);
//        hpNumbers.setColor(Color.WHITE);
//
//        // Bottom row with types and status
//        Table bottomRow = new Table();
//
//        // Type container
//        Table typeContainer = new Table();
//        typeContainer.add(createTypeElement(pokemon.getPrimaryType()));
//        if (pokemon.getSecondaryType() != null) {
//            typeContainer.add(createTypeElement(pokemon.getSecondaryType())).padLeft(4);
//        }
//
//        // Status container with simple colored background
//        Table statusContainer = new Table();
//        statusContainer.setBackground(createColoredDrawable(STATUS_BG));
//        statusContainer.pad(2);
//        updateStatusIcons(pokemon, statusContainer);
//
//        bottomRow.add(typeContainer).left().expandX();
//        bottomRow.add(statusContainer).right().padLeft(10).size(32);
//
//        // Layout
//        content.add(nameTag).expandX().fillX().padBottom(4).row();
//        content.add(hpRow).expandX().fillX().padTop(4).row();
//        content.add(hpNumbers).right().padTop(4).row();
//        content.add(bottomRow).expandX().fillX().padTop(4);
//
//        window.add(content).expand().fill();
//
//        // Store references
//        if (isPlayer) {
//            playerHealthBar = hpBar;
//            playerPokemonName = nameLabel;
//            playerHpLabel = hpNumbers;
//            playerPokemonLevel = levelLabel;
//            this.statusIconsTable = statusContainer;
//        } else {
//            enemyHealthBar = hpBar;
//            enemyPokemonName = nameLabel;
//            enemyHpLabel = hpNumbers;
//            enemyPokemonLevel = levelLabel;
//            this.statusIconsTable = statusContainer;
//        }
//    }
//
//    // Now we can implement the Pokemon info box with proper status handling
//// Add these methods to the BattleScreen class
//    private String getStatusSymbol(Pokemon.Status status) {
//        switch (status) {
//            case PARALYZED:
//                return "PAR";
//            case POISONED:
//                return "PSN";
//            case BADLY_POISONED:
//                return "TOX";
//            case BURNED:
//                return "BRN";
//            case FROZEN:
//                return "FRZ";
//            case ASLEEP:
//                return "SLP";
//            case FAINTED:
//                return "FNT";
//            case NONE:
//            default:
//                return "";
//        }
//    }
//
//    private Color getStatusColor(Pokemon.Status status) {
//        switch (status) {
//            case PARALYZED:
//                return new Color(0.9f, 0.8f, 0.2f, 1f); // Yellow
//            case POISONED:
//                return new Color(0.6f, 0.2f, 0.6f, 1f); // Purple
//            case BADLY_POISONED:
//                return new Color(0.5f, 0.1f, 0.5f, 1f); // Dark Purple
//            case BURNED:
//                return new Color(0.9f, 0.3f, 0.2f, 1f); // Red
//            case FROZEN:
//                return new Color(0.4f, 0.8f, 0.9f, 1f); // Light Blue
//            case ASLEEP:
//                return new Color(0.6f, 0.6f, 0.6f, 1f); // Gray
//            case FAINTED:
//                return new Color(0.3f, 0.3f, 0.3f, 1f); // Dark Gray
//            case NONE:
//            default:
//                return Color.WHITE;
//        }
//    }
//
//    // Helper method to convert between Pokemon.Status and TextureManager.StatusCondition
//    private TextureManager.StatusCondition convertToStatusCondition(Pokemon.Status status) {
//        switch (status) {
//            case PARALYZED:
//                return TextureManager.StatusCondition.PARALYSIS;
//            case POISONED:
//                return TextureManager.StatusCondition.POISON;
//            case BADLY_POISONED:
//                return TextureManager.StatusCondition.TOXIC;
//            case BURNED:
//                return TextureManager.StatusCondition.BURN;
//            case FROZEN:
//                return TextureManager.StatusCondition.FREEZE;
//            case ASLEEP:
//                return TextureManager.StatusCondition.SLEEP;
//            case FAINTED:
//            case NONE:
//            default:
//                return TextureManager.StatusCondition.NONE;
//        }
//    }
//
//    // Update updateStatusIcons to use both icons and fallback text
//    private void updateStatusIcons(Pokemon pokemon, Table statusContainer) {
//        statusContainer.clear();
//
//        if (pokemon.hasStatus()) {
//            TextureManager.StatusCondition statusCondition = convertToStatusCondition(pokemon.getStatus());
//            TextureRegion statusIcon = TextureManager.getStatusIcon(statusCondition);
//
//            if (statusIcon != null) {
//                // Use icon if available
//                Image iconImage = new Image(statusIcon);
//                iconImage.addAction(Actions.forever(Actions.sequence(
//                    Actions.alpha(0.5f, 0.5f),
//                    Actions.alpha(1f, 0.5f)
//                )));
//                statusContainer.add(iconImage).size(TextureManager.STATUS_ICON_WIDTH, TextureManager.STATUS_ICON_HEIGHT);
//            } else {
//                // Fallback to text
//                Label statusLabel = new Label(getStatusSymbol(pokemon.getStatus()), skin);
//                statusLabel.setColor(getStatusColor(pokemon.getStatus()));
//                statusLabel.addAction(Actions.forever(Actions.sequence(
//                    Actions.alpha(0.5f, 0.5f),
//                    Actions.alpha(1f, 0.5f)
//                )));
//                statusContainer.add(statusLabel).size(32);
//            }
//            statusContainer.setVisible(true);
//        } else {
//            statusContainer.setVisible(false);
//        }
//    }
//
//// Update createM
//
//    // Update createTypeElement to use TextureManager's type icons
//    // Update createTypeElement method to use TextureManager.PokemonType
//    private Table createTypeElement(PokemonType type) {
//        Table typeElement = new Table();
//        typeElement.pad(2);
//
//        // Get type color and icon from TextureManager
//        Color typeColor = TextureManager.getTypeColor(type);
//        typeElement.setBackground(createColoredDrawable(typeColor));
//
//        TextureRegion typeIconRegion = TextureManager.getTypeIcon(type);
//        if (typeIconRegion != null) {
//            Image typeIcon = new Image(typeIconRegion);
//            typeElement.add(typeIcon).size((float) TextureManager.TYPE_ICON_WIDTH / 2,
//                (float) TextureManager.TYPE_ICON_HEIGHT / 2).padRight(4);
//        }
//
//        Label typeName = new Label(type.name(), skin);
//        typeName.setColor(Color.WHITE);
//        typeElement.add(typeName);
//
//        // Add hover effect
//        typeElement.addListener(new InputListener() {
//            @Override
//            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
//                typeElement.setColor(typeElement.getColor().mul(1.2f));
//            }
//
//            @Override
//            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
//                typeElement.setColor(Color.WHITE);
//            }
//        });
//
//        return typeElement;
//    }
//    private void startMoveAnimation(io.github.pokemeetup.pokemon.attacks.Move move) {
//        // TODO: Implement move animation system
//        AudioManager.getInstance().playSound(MOVE_SELECT_SOUND);
//    }
//
//    private void initializeStyles() {
//        normalMoveStyle = createColoredDrawable(BUTTON_BG);
//        selectedMoveStyle = createColoredDrawable(BUTTON_SELECTED_BG);
//    }
//
//    private void updateSelectedMove(int delta) {
//        if (playerPokemon.getMoves().isEmpty()) return;
//
//        int oldIndex = selectedMoveIndex;
//        int moveCount = playerPokemon.getMoves().size();
//
//        selectedMoveIndex += delta;
//
//        // Handle wrapping for 2x2 grid
//        if (delta == -2 || delta == 2) { // Vertical movement
//            if (selectedMoveIndex < 0) selectedMoveIndex += 4;
//            if (selectedMoveIndex >= 4) selectedMoveIndex -= 4;
//        } else { // Horizontal movement
//            if (selectedMoveIndex < 0) selectedMoveIndex = moveCount - 1;
//            if (selectedMoveIndex >= moveCount) selectedMoveIndex = 0;
//        }
//
//        // Only play sound if selection actually changed
//        if (oldIndex != selectedMoveIndex) {
//            AudioManager.getInstance().playSound(CURSOR_MOVE_SOUND);
//            updateMoveDisplay();
//        }
//    }
//
//    private void selectCurrentMove() {
//        if (selectedMoveIndex >= playerPokemon.getMoves().size()) return;
//
//        io.github.pokemeetup.pokemon.attacks.Move selectedMove = playerPokemon.getMoves().get(selectedMoveIndex);
//        if (selectedMove.getPp() <= 0) {
//            showMessage("No PP left for this move!");
//            return;
//        }
//
//        AudioManager.getInstance().playSound(MOVE_SELECT_SOUND);
//        executeMove(selectedMove);
//    }
//
//    private void updateMoveDisplay() {
//        // Update move buttons visuals
//        for (int i = 0; i < moveButtons.getCells().size; i++) {
//            Cell<?> cell = moveButtons.getCells().get(i);
//            Table moveCell = (Table) cell.getActor();
//
//            if (i == selectedMoveIndex) {
//                moveCell.setBackground(selectedMoveStyle);
//                // Show move description
//                updateMoveDescription(playerPokemon.getMoves().get(i));
//            } else {
//                moveCell.setBackground(normalMoveStyle);
//            }
//        }
//    }
//
//    private void initializeBattle() {
//        battleState = BattleState.INTRO;
//        isPlayerTurn = true;
//
//        // Queue initial battle messages
//        showMessage("A wild " + wildPokemon.getName() + " appeared!");
//        showMessage("Go! " + playerPokemon.getName() + "!");
//
//        // Initial UI state
//        moveButtons.setVisible(false);
//        messageBox.setVisible(true);
//
//        // Add click listener to advance messages
//        stage.addListener(new InputListener() {
//            @Override
//            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
//                if (waitingForInput && currentCharIndex >= currentMessage.length()) {
//                    displayNextMessage();
//                    return true;
//                }
//                return false;
//            }
//        });
//    }
//
//    private void showEffectivenessMessages(float effectiveness) {
//        if (effectiveness > 1.0f) {
//            showMessage("It's super effective!");
//            AudioManager.getInstance().playSound(SUPER_EFFECTIVE_SOUND);
//        } else if (effectiveness < 1.0f && effectiveness > 0.0f) {
//            showMessage("It's not very effective...");
//            AudioManager.getInstance().playSound(NOT_EFFECTIVE_SOUND);
//        } else if (effectiveness == 0.0f) {
//            showMessage("It has no effect!");
//        }
//    }
//    // Update method to get type icon
//    private TextureRegion getTypeIcon(PokemonType type) {
//        return TextureManager.getTypeIcon(type);
//    }
//
//    private void executeEnemyTurn() {
//        battleState = BattleState.ENEMY_TURN;
//
//        // Select a random move with remaining PP
//        List<Move> availableMoves = wildPokemon.getMoves().stream()
//            .filter(move -> move.getPp() > 0)
//            .collect(Collectors.toList());
//
//        if (!availableMoves.isEmpty()) {
//            Move selectedMove = availableMoves.get(MathUtils.random(availableMoves.size() - 1));
//
//            // Show message
//            showMessage("Wild " + wildPokemon.getName() + " used " + selectedMove.getName() + "!");
//
//            // Delay before applying damage
//            Timer.schedule(new Timer.Task() {
//                @Override
//                public void run() {
//                    int damage = calculateDamage(selectedMove, wildPokemon, playerPokemon);
//                    applyDamage(playerPokemon, damage);
//
//                    if (playerPokemon.getCurrentHp() <= 0) {
//                        endBattle(false);
//                    } else {
//                        // Return to player's turn
//                        battleState = BattleState.PLAYER_TURN;
//                        moveButtons.setVisible(true);
//                        canInput = true;
//                    }
//                }
//            }, getAnimationDuration(selectedMove));
//        } else {
//            // Enemy has no moves left
//            showMessage("Wild " + wildPokemon.getName() + " has no moves left!");
//            battleState = BattleState.PLAYER_TURN;
//            moveButtons.setVisible(true);
//            canInput = true;
//        }
//    }
//
//    private float calculateTypeEffectiveness(PokemonType moveType, PokemonType defenderType) {
//        // Define effectiveness maps
//        Map<PokemonType, Map<PokemonType, Float>> typeChart = new HashMap<>();
//
//        // Initialize type chart
//        for (PokemonType type : PokemonType.values()) {
//            typeChart.put(type, new HashMap<>());
//        }
//
//        // Set up super effective (2.0x) matchups
//        setupSuperEffective(typeChart, PokemonType.FIRE,
//            PokemonType.GRASS, PokemonType.ICE, PokemonType.BUG);
//        setupSuperEffective(typeChart, PokemonType.WATER,
//            PokemonType.FIRE, PokemonType.GROUND, PokemonType.ROCK);
//        setupSuperEffective(typeChart, PokemonType.ELECTRIC,
//            PokemonType.WATER, PokemonType.FLYING);
//        setupSuperEffective(typeChart, PokemonType.GRASS,
//            PokemonType.WATER, PokemonType.GROUND, PokemonType.ROCK);
//
//        // Set up not very effective (0.5x) matchups
//        setupNotVeryEffective(typeChart, PokemonType.FIRE,
//            PokemonType.WATER, PokemonType.ROCK, PokemonType.DRAGON);
//        setupNotVeryEffective(typeChart, PokemonType.WATER,
//            PokemonType.GRASS, PokemonType.DRAGON);
//        setupNotVeryEffective(typeChart, PokemonType.ELECTRIC,
//            PokemonType.GRASS, PokemonType.DRAGON);
//
//        // Get effectiveness from chart, default to 1.0 if not specified
//        return typeChart.get(moveType).getOrDefault(defenderType, 1.0f);
//    }
//    private void setupSuperEffective(Map<PokemonType, Map<PokemonType, Float>> chart,
//                                     PokemonType attackType, PokemonType... defendTypes) {
//        for (PokemonType defendType : defendTypes) {
//            chart.get(attackType).put(defendType, 2.0f);
//        }
//    }
//
//    private void setupNotVeryEffective(Map<PokemonType, Map<PokemonType, Float>> chart,
//                                       PokemonType attackType, PokemonType... defendTypes) {
//        for (PokemonType defendType : defendTypes) {
//            chart.get(attackType).put(defendType, 0.5f);
//        }
//    }
//    // Helper methods for type chart setup
//    private void setupSuperEffective(Map<Pokemon.PokemonType, Map<Pokemon.PokemonType, Float>> chart,
//                                     Pokemon.PokemonType attackType, Pokemon.PokemonType... defendTypes) {
//        for (Pokemon.PokemonType defendType : defendTypes) {
//            chart.get(attackType).put(defendType, 2.0f);
//        }
//    }
//
//    private void setupNotVeryEffective(Map<Pokemon.PokemonType, Map<Pokemon.PokemonType, Float>> chart,
//                                       Pokemon.PokemonType attackType, Pokemon.PokemonType... defendTypes) {
//        for (Pokemon.PokemonType defendType : defendTypes) {
//            chart.get(attackType).put(defendType, 0.5f);
//        }
//    }
//
//    private String getBiomeBackground(BiomeType biome) {
//        switch (biome) {
//            case PLAINS:
//                return "battle_bg_plains";
//            case SNOW:
//                return "battle_bg_snow";
//            case FOREST:
//                return "battle_bg_forest";
//            case HAUNTED:
//                return "battle_bg_haunted";
//            default:
//                return "battle_bg_plains";
//        }
//    }
//
//    private void onMessagesComplete() {
//        waitingForInput = false;
//
//        switch (battleState) {
//            case INTRO:
//                battleState = BattleState.PLAYER_TURN;
//                messageBox.setVisible(false);
//                moveButtons.setVisible(true);
//                break;
//
//            case PLAYER_TURN:
//                moveButtons.setVisible(true);
//                messageBox.setVisible(false);
//                break;
//
//            case ENEMY_TURN:
//                moveButtons.setVisible(false);
//                messageBox.setVisible(true);
//                break;
//
//            case BATTLE_END:
//                // Battle is already ended at this point
//                break;
//        }
//    }
//
//    private int calculateExperienceGain() {
//        // Base experience varies by Pokemon species
//        int baseExp = wildPokemon.getBaseExperience();
//        float levelModifier = wildPokemon.getLevel() / 7f;  // Adjust this factor as needed
//        return Math.max(1, (int) (baseExp * levelModifier));
//    }
//
//    private void endBattle(boolean playerWon) {
//        battleState = BattleState.BATTLE_END;
//        moveButtons.setVisible(false);
//
//        if (playerWon) {
//            showMessage("You defeated the wild " + wildPokemon.getName() + "!");
//        } else {
//            showMessage("You were defeated by the wild " + wildPokemon.getName() + "!");
//        }
//        if (battleCompletionHandler != null) {
//            battleCompletionHandler.run();
//        }
//        // Delay before returning to the previous screen
//        Timer.schedule(new Timer.Task() {
//            @Override
//            public void run() {
//                // Return to the previous screen or handle post-battle logic
//                if (completionHandler != null) {
//                    BattleResult result = new BattleResult(playerWon, playerPokemon, wildPokemon);
//                    completionHandler.onBattleComplete(result);
//                }
//            }
//        }, 2.0f);
//    }
//
//    private void setupUI() {
//        setupInfoBoxes();
//        setupMessageBox();
//        setupMoveButtons();
//        layoutUI();
//    }// After applying a move that causes a status effect
//
//    private void applyStatusEffect(Pokemon target, Pokemon.Status status) {
//        target.setStatus(status);
//        updatePokemonStatus(target);
//    }
//
//    private Window createInfoBox(Pokemon pokemon, boolean isPlayer) {
//        // Create a new Window to hold the info box
//        Window window = new Window("", skin);
//        window.setSize(INFO_BOX_WIDTH, INFO_BOX_HEIGHT);
//        window.pad(8); // Add padding inside the window
//        window.setBackground(createColoredDrawable(WINDOW_BG)); // Set the background color
//
//        // Create a Table to organize the content inside the window
//        Table content = new Table();
//        content.defaults().pad(2); // Default padding for all elements inside the content table
//
//        // **1. Name and Level Section**
//        // Create a Table for the name and level with a colored background
//        Table nameTag = new Table();
//        nameTag.setBackground(createColoredDrawable(BUTTON_BG)); // Background color for the name tag
//        nameTag.pad(4); // Padding inside the name tag
//
//        // Create Labels for the Pokémon's name and level
//        Label.LabelStyle largeLabelStyle = new Label.LabelStyle(skin.get(Label.LabelStyle.class));
//        largeLabelStyle.font.getData().setScale(1.2f); // Increase font size
//
//        Label nameLabel = new Label(pokemon.getName(), largeLabelStyle);
//        Label levelLabel = new Label("Lv." + pokemon.getLevel(), largeLabelStyle);
//        nameLabel.setColor(Color.WHITE);
//        levelLabel.setColor(Color.WHITE);
//
//        // Add the name and level labels to the nameTag table
//        nameTag.add(nameLabel).left().expandX();
//        nameTag.add(levelLabel).right().padLeft(10);
//
//        // **2. HP Section**
//        // Create a Table for the HP label and HP bar
//        Table hpRow = new Table();
//        Label hpLabel = new Label("HP", skin);
//        hpLabel.setColor(Color.WHITE);
//
//        // Create a ProgressBar for the HP bar
//        ProgressBarStyle hpBarStyle = skin.get(ProgressBarStyle.class);
//        // Ensure the ProgressBar has a minimal height
//        hpBarStyle.background.setMinHeight(12);
//        hpBarStyle.knobBefore.setMinHeight(12);
//
//        ProgressBar hpBar = new ProgressBar(0, pokemon.getStats().getHp(), 1, false, hpBarStyle);
//        hpBar.setValue(pokemon.getCurrentHp());
//        updateHealthBar(hpBar, pokemon.getCurrentHp(), pokemon.getStats().getHp()); // Update the color based on HP percentage
//
//        // Add the HP label and HP bar to the hpRow table
//        hpRow.add(hpLabel).left().padRight(10);
//        hpRow.add(hpBar).width(HEALTH_BAR_WIDTH * 1.2f).height(12).left();
//
//        // Create a Label to display the current HP over the maximum HP
//        Label hpNumbers = new Label(pokemon.getCurrentHp() + "/" + pokemon.getStats().getHp(), skin);
//        hpNumbers.setColor(Color.WHITE);
//
//        // **3. Bottom Row with Types and Status**
//        Table bottomRow = new Table();
//
//        // **Type Container**
//        Table typeContainer = new Table();
//        // Add the primary type icon and name
//        typeContainer.add(createTypeElement(pokemon.getPrimaryType()));
//        // If the Pokémon has a secondary type, add it as well
//        if (pokemon.getSecondaryType() != null) {
//            typeContainer.add(createTypeElement(getTypeIcon(pokemon.getSecondaryType()))).padLeft(4);
//        }
//
//        // **Status Container**
//        Table statusContainer = new Table();
//        statusContainer.setBackground(createColoredDrawable(STATUS_BG)); // Background color for the status container
//        statusContainer.pad(2);
//        updateStatusIcons(pokemon, statusContainer); // Add status effect icons or text
//
//        // Add the typeContainer and statusContainer to the bottomRow table
//        bottomRow.add(typeContainer).left().expandX();
//        bottomRow.add(statusContainer).right().padLeft(10).size(32);
//
//        // **Layout Assembly**
//        // Add all the sections to the content table
//        content.add(nameTag).expandX().fillX().padBottom(4).row(); // Name and level
//        content.add(hpRow).expandX().fillX().padTop(4).row();      // HP bar
//        content.add(hpNumbers).right().padTop(4).row();            // HP numbers
//        content.add(bottomRow).expandX().fillX().padTop(4);        // Types and status
//
//        // Add the content table to the window
//        window.add(content).expand().fill();
//
//        // **Store References**
//        // Store references to UI components for later updates
//        if (isPlayer) {
//            playerHealthBar = hpBar;
//            playerPokemonName = nameLabel;
//            playerHpLabel = hpNumbers;
//            playerPokemonLevel = levelLabel;
//            playerStatusContainer = statusContainer; // Store status container if needed
//        } else {
//            enemyHealthBar = hpBar;
//            enemyPokemonName = nameLabel;
//            enemyHpLabel = hpNumbers;
//            enemyPokemonLevel = levelLabel;
//            enemyStatusContainer = statusContainer; // Store status container if needed
//        }
//
//        return window;
//    }
//
//    private void setupInfoBoxes() {
//        // Player Info Box
//        playerPokemonWindow = createInfoBox(playerPokemon, true);
//        playerPokemonWindow.setPosition(20, 120);
//        stage.addActor(playerPokemonWindow);
//
//        // Enemy Info Box
//        enemyPokemonWindow = createInfoBox(wildPokemon, false);
//        enemyPokemonWindow.setPosition(Gdx.graphics.getWidth() - INFO_BOX_WIDTH - 20,
//            Gdx.graphics.getHeight() - INFO_BOX_HEIGHT - 20);
//        stage.addActor(enemyPokemonWindow);
//    }
//
//    private void displayNextMessage() {
//        if (!messageQueue.isEmpty()) {
//            currentMessage = messageQueue.poll();
//            battleText.setText(currentMessage);
//            waitingForInput = true;
//        } else {
//            waitingForInput = false;
//            onMessagesComplete();
//        }
//    }
//
//    private void advanceMessage() {
//        if (waitingForInput) {
//            displayNextMessage();
//        }
//    }
//
//    private void setupPokemonInfoBox(Window window, Pokemon pokemon, boolean isPlayer) {
//        window.setSize(INFO_BOX_WIDTH, INFO_BOX_HEIGHT);
//        window.pad(8);
//        window.setBackground(new TextureRegionDrawable(TextureManager.ui.findRegion("window")));
//
//        Table content = new Table();
//        content.defaults().pad(2);
//
//        // Name background - similar to the green tag in your reference
//        Table nameTag = new Table();
//        nameTag.setBackground(new TextureRegionDrawable(TextureManager.ui.findRegion("button")));  // Use existing UI element
//        nameTag.pad(4);
//
//        // Name and Level with larger font
//        Label.LabelStyle largeLabelStyle = new Label.LabelStyle(skin.get(Label.LabelStyle.class));
//        largeLabelStyle.font.getData().setScale(1.2f);
//
//        Label nameLabel = new Label(pokemon.getName(), largeLabelStyle);
//        Label levelLabel = new Label("Lv." + pokemon.getLevel(), largeLabelStyle);
//        nameLabel.setColor(Color.WHITE);
//        levelLabel.setColor(Color.WHITE);
//
//        nameTag.add(nameLabel).left().expandX();
//        nameTag.add(levelLabel).right().padLeft(10);
//
//        // HP Bar with custom styling
//        Table hpRow = new Table();
//        Label hpLabel = new Label("HP", skin);
//        hpLabel.setColor(Color.WHITE);
//
//        // Create a larger, more visible HP bar
//        ProgressBarStyle hpBarStyle = skin.get(ProgressBarStyle.class);
//        hpBarStyle.background.setMinHeight(12);
//        hpBarStyle.knobBefore.setMinHeight(12);
//
//        ProgressBar hpBar = new ProgressBar(0, pokemon.getStats().getHp(), 1, false, hpBarStyle);
//        hpBar.setValue(pokemon.getCurrentHp());
//
//        // Set initial color based on current HP
//        updateHealthBar(hpBar, pokemon.getCurrentHp(), pokemon.getStats().getHp());
//
//        hpRow.add(hpLabel).left().padRight(10);
//        hpRow.add(hpBar).width(HEALTH_BAR_WIDTH * 1.2f).height(12).left(); // Wider bar
//
//        // HP Numbers with shadow effect
//        Label hpNumbers = new Label(pokemon.getCurrentHp() + "/" + pokemon.getStats().getHp(), skin);
//        hpNumbers.setColor(Color.WHITE);
//
//        // Layout
//        content.add(nameTag).expandX().fillX().padBottom(4).row();
//        content.add(hpRow).expandX().fillX().padTop(4).row();
//        content.add(hpNumbers).right().padTop(4);
//
//        window.add(content).expand().fill();
//
//        // Store references
//        if (isPlayer) {
//            playerHealthBar = hpBar;
//            playerPokemonName = nameLabel;
//            playerHpLabel = hpNumbers;
//            playerPokemonLevel = levelLabel;
//        } else {
//            enemyHealthBar = hpBar;
//            enemyPokemonName = nameLabel;
//            enemyHpLabel = hpNumbers;
//            enemyPokemonLevel = levelLabel;
//        }
//    }
//
//    private void updateHealthBar(ProgressBar bar, float currentHp, float maxHp) {
//        float percentage = currentHp / maxHp;
//
//        // Store current value
//        float value = bar.getValue();
//
//        // Change the style based on HP percentage
//        if (percentage > 0.5f) {
//            bar.setStyle(greenHPStyle);
//        } else if (percentage > 0.25f) {
//            bar.setStyle(yellowHPStyle);
//        } else {
//            bar.setStyle(redHPStyle);
//        }
//
//        // Restore the value after style change
//        bar.setValue(value);
//    }
//
//    private void updateHealthBars() {
//        // Update colors and values
//        updateHealthBar(playerHealthBar, playerPokemon.getCurrentHp(), playerPokemon.getStats().getHp());
//        updateHealthBar(enemyHealthBar, wildPokemon.getCurrentHp(), wildPokemon.getStats().getHp());
//
//        // Rest of your existing updateHealthBars code...
//    }
//
//    private void setupMessageBox() {
//        messageBox = new Table();
//        messageBox.setBackground(createColoredDrawable(new Color(0, 0, 0, 0.8f)));
//        messageBox.setBounds(10, 10, Gdx.graphics.getWidth() - 20, 100);
//
//        battleText = new Label("", skin);
//        battleText.setWrap(true);
//
//        messageBox.add(battleText).expand().fill().pad(10);
//        stage.addActor(messageBox);
//    }
//
//    private TextureRegionDrawable createColoredDrawable(Color color) {
//        Pixmap pixmap = new Pixmap(2, 2, Pixmap.Format.RGBA8888);
//        pixmap.setColor(color);
//        pixmap.fill();
//
//        Texture texture = new Texture(pixmap);
//        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
//
//        TextureRegion region = new TextureRegion(texture);
//        TextureRegionDrawable drawable = new TextureRegionDrawable(region);
//
//        pixmap.dispose(); // Dispose pixmap after texture creation
//
//        return drawable;
//    }
//
//
////    private void setupMoveButton(io.github.pokemeetup.pokemon.attacks.Move move, Table moveCell) {
////        // Base move container with padding
////        moveCell.pad(4);
////
////        // Create layers
////        Table content = new Table();
////
////        // Move name
////        Label nameLabel = new Label(move.getName(), skin);
////        nameLabel.setColor(Color.WHITE);
////
////        // PP counter
////        Label ppLabel = new Label(move.getPp() + "/" + move.getMaxPp(), skin);
////        ppLabel.setColor(Color.LIGHT_GRAY);
////
////        // Type icon
////        Image typeIcon = new Image(TextureManager.getTypeIcon(move.getType()));
////
////        // Layout
////        content.add(nameLabel).left().expandX();
////        content.add(typeIcon).size(24).right().padRight(4);
////        content.row();
////        content.add(ppLabel).right().padTop(4).colspan(2);
////
////        moveCell.add(content).grow();
////
////        // Selection states
////        moveCell.addListener(new ClickListener() {
////            @Override
////            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
////                // Highlight on hover
////                moveCell.setBackground(skin.getDrawable("move_selected_bg"));
////            }
////
////            @Override
////            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
////                // Remove highlight
////                moveCell.setBackground(skin.getDrawable("move_bg"));
////            }
////        });
////    }
//
//
//    // Update createMoveButton to use colored backgrounds
//
//    // Update message box setup
//
//    // Helper method to create status effect visual
//
//    private void setupHealthBarStyles() {
//        // Get base style from skin
//        ProgressBarStyle baseStyle = skin.get(ProgressBarStyle.class);
//
//        // Create new styles cloning the base style
//        greenHPStyle = new ProgressBarStyle();
//        yellowHPStyle = new ProgressBarStyle();
//        redHPStyle = new ProgressBarStyle();
//
//        // Set the background (empty part of bar) for all styles
//        greenHPStyle.background = baseStyle.background;
//        yellowHPStyle.background = baseStyle.background;
//        redHPStyle.background = baseStyle.background;
//
//        // Create the colored knobBefore (filled part of bar) for each style
//        greenHPStyle.knobBefore = createColoredDrawable(HP_GREEN);
//        yellowHPStyle.knobBefore = createColoredDrawable(HP_YELLOW);
//        redHPStyle.knobBefore = createColoredDrawable(HP_RED);
//    }
//
//    private void layoutUI() {
//        stage.addActor(playerPokemonWindow);
//        stage.addActor(enemyPokemonWindow);
//    }
//
//    private void showMessage(String message) {
//        messageQueue.offer(message);
//        if (!waitingForInput) {
//            displayNextMessage();
//        }
//    }
//
//    @Override
//    public void show() {
//
//    }
//
//    @Override
//    public void render(float delta) {
//        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
//
//        batch.begin();
//
//        // Draw background with null check
//        TextureRegion background = battleAtlas.findRegion(getBiomeBackground(biomeType));
//        if (background != null) {
//            batch.draw(background, 0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
//        }
//
//        // Draw platforms and pokemon with null checks
//        TextureRegion platform = battleAtlas.findRegion("battle_platform");
//        if (platform != null) {
//            float enemyX = Gdx.graphics.getWidth() * 0.7f;
//            float enemyY = Gdx.graphics.getHeight() * 0.6f;
//            batch.draw(platform, enemyX, enemyY);
//
//            TextureRegion enemySprite = wildPokemon.getFrontSprite();
//            if (enemySprite != null) {
//                batch.draw(enemySprite, enemyX + 20, enemyY + PLATFORM_OFFSET_Y);
//            }
//
//            float playerX = Gdx.graphics.getWidth() * 0.2f;
//            float playerY = Gdx.graphics.getHeight() * 0.2f;
//            batch.draw(platform, playerX, playerY);
//
//            TextureRegion playerSprite = playerPokemon.getBackSprite();
//            if (playerSprite != null) {
//                batch.draw(playerSprite, playerX + 20, playerY + PLATFORM_OFFSET_Y);
//            }
//        }
//
//        batch.end();
//
//        // Update UI with delta time
//        if (stage != null) {
//            stage.act(delta);
//            stage.draw();
//        }
//    }
//
//    private void validateAndLoadTextures() {
//        // Validate battle background exists for biome
//        String bgRegionName = getBiomeBackground(biomeType);
//        TextureRegion bgRegion = battleAtlas.findRegion(bgRegionName);
//        if (bgRegion == null) {
//            throw new IllegalStateException("Missing battle background texture for biome: " + biomeType);
//        }
//
//        // Validate platform texture exists
//        TextureRegion platformRegion = battleAtlas.findRegion("battle_platform");
//        if (platformRegion == null) {
//            throw new IllegalStateException("Missing battle platform texture");
//        }
//
//        // Create UI textures
//        createUITextures();
//    }
//
//    private void createUITextures() {
//        // Create health bar textures
//        greenHPStyle = new ProgressBarStyle();
//        yellowHPStyle = new ProgressBarStyle();
//        redHPStyle = new ProgressBarStyle();
//
//        greenHPStyle.background = createColoredDrawable(new Color(0.2f, 0.2f, 0.2f, 1f));
//        yellowHPStyle.background = greenHPStyle.background;
//        redHPStyle.background = greenHPStyle.background;
//
//        greenHPStyle.knobBefore = createColoredDrawable(HP_GREEN);
//        yellowHPStyle.knobBefore = createColoredDrawable(HP_YELLOW);
//        redHPStyle.knobBefore = createColoredDrawable(HP_RED);
//
//        // Create move button textures
//        normalMoveStyle = createColoredDrawable(BUTTON_BG);
//        selectedMoveStyle = createColoredDrawable(BUTTON_SELECTED_BG);
//    }
//
//    @Override
//    public void pause() {
//        Timer.instance().clear();
//
//    }
//
//    @Override
//    public void resume() {
//
//    }
//
//    @Override
//    public void hide() {
//
//    }
//
//    @Override
//    public void dispose() {
//        if (stage != null) {
//            stage.dispose();
//        }
//        if (batch != null) {
//            batch.dispose();
//        }
//
//        // Dispose health bar textures
//        disposeProgressBarStyle(greenHPStyle);
//        disposeProgressBarStyle(yellowHPStyle);
//        disposeProgressBarStyle(redHPStyle);
//
//        // Dispose button textures
//        disposeDrawable(normalMoveStyle);
//        disposeDrawable(selectedMoveStyle);
//        if (greenHPStyle != null && greenHPStyle.knobBefore instanceof TextureRegionDrawable) {
//            ((TextureRegionDrawable) greenHPStyle.knobBefore).getRegion().getTexture().dispose();
//        }
//        if (yellowHPStyle != null && yellowHPStyle.knobBefore instanceof TextureRegionDrawable) {
//            ((TextureRegionDrawable) yellowHPStyle.knobBefore).getRegion().getTexture().dispose();
//        }
//        if (redHPStyle != null && redHPStyle.knobBefore instanceof TextureRegionDrawable) {
//            ((TextureRegionDrawable) redHPStyle.knobBefore).getRegion().getTexture().dispose();
//        }
//        if (normalMoveStyle != null && normalMoveStyle.getRegion().getTexture() != null) {
//            normalMoveStyle.getRegion().getTexture().dispose();
//        }
//        if (selectedMoveStyle != null && selectedMoveStyle.getRegion().getTexture() != null) {
//            selectedMoveStyle.getRegion().getTexture().dispose();
//        }
//    }
//
//    private void disposeProgressBarStyle(ProgressBarStyle style) {
//        if (style != null) {
//            if (style.knobBefore instanceof TextureRegionDrawable) {
//                Texture tex = ((TextureRegionDrawable) style.knobBefore).getRegion().getTexture();
//                if (tex != null) tex.dispose();
//            }
//            if (style.background instanceof TextureRegionDrawable) {
//                Texture tex = ((TextureRegionDrawable) style.background).getRegion().getTexture();
//                if (tex != null) tex.dispose();
//            }
//        }
//    }
//
//    private void disposeDrawable(TextureRegionDrawable drawable) {
//        if (drawable != null && drawable.getRegion() != null) {
//            Texture tex = drawable.getRegion().getTexture();
//            if (tex != null) tex.dispose();
//        }
//    }
//
//    private void drawBackground() {
//        TextureRegion background = battleAtlas.findRegion(getBiomeBackground(biomeType));
//        batch.draw(background, 0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
//    }
//
//    private void drawPokemonAndPlatforms() {
//        // Enemy platform and Pokemon
//        float enemyX = Gdx.graphics.getWidth() * 0.7f;
//        float enemyY = Gdx.graphics.getHeight() * 0.6f;
//        batch.draw(battleAtlas.findRegion("battle_platform"), enemyX, enemyY);
//        batch.draw(wildPokemon.getFrontSprite(),
//            enemyX + 20,
//            enemyY + PLATFORM_OFFSET_Y
//        );
//
//        // Player platform and Pokemon
//        float playerX = Gdx.graphics.getWidth() * 0.2f;
//        float playerY = Gdx.graphics.getHeight() * 0.2f;
//        batch.draw(battleAtlas.findRegion("battle_platform"), playerX, playerY);
//        batch.draw(playerPokemon.getBackSprite(),
//            playerX + 20,
//            playerY + PLATFORM_OFFSET_Y
//        );
//    }
//
//    private void showBattleMenu() {
//        // Create popup menu with Run/Back options
//        Dialog battleMenu = new Dialog("", skin) {
//            @Override
//            protected void result(Object object) {
//                if ((Boolean) object) {
//                    attemptRun();
//                }
//            }
//        };
//
//        battleMenu.text("What will you do?");
//        battleMenu.button("Run", true);
//        battleMenu.button("Back", false);
//        battleMenu.show(stage);
//    }
//
//    private void attemptRun() {
//        // Calculate run success (could be based on speed stats)
//        boolean runSuccess = Math.random() < 0.75f;
//        if (runSuccess) {
//            showMessage("Got away safely!");
//            Timer.schedule(new Timer.Task() {
//                @Override
//                public void run() {
//                    endBattle(true);
//                }
//            }, 1.0f);
//        } else {
//            showMessage("Can't escape!");
//            Timer.schedule(new Timer.Task() {
//                @Override
//                public void run() {
//                    executeEnemyTurn();
//                }
//            }, 1.0f);
//        }
//    }
//
//    private void applyDamage(Pokemon target, int damage) {
//        // Subtract damage from the target's current HP
//        target.setCurrentHp(Math.max(0, target.getCurrentHp() - damage));
//
//        // Update health bars and labels
//        updateHealthBars();
//
//        // Play damage sound effect
//        AudioManager.getInstance().playSound(DAMAGE_SOUND);
//    }
//
//    @Override
//    public void resize(int width, int height) {
//        stage.getViewport().update(width, height, true);
//
//        // Update UI component sizes and positions
//        float worldWidth = stage.getViewport().getWorldWidth();
//        float worldHeight = stage.getViewport().getWorldHeight();
//
//        // Update positions and sizes using relative measurements
//        playerPokemonWindow.setSize(worldWidth * 0.3f, worldHeight * 0.1f);
//        playerPokemonWindow.setPosition(worldWidth * 0.05f, worldHeight * 0.1f);
//
//        enemyPokemonWindow.setSize(worldWidth * 0.3f, worldHeight * 0.1f);
//        enemyPokemonWindow.setPosition(worldWidth * 0.65f, worldHeight * 0.8f);
//
//        moveButtons.setBounds(worldWidth * 0.05f, worldHeight * 0.05f, worldWidth * 0.9f, worldHeight * 0.2f);
//
//        messageBox.setBounds(worldWidth * 0.05f, worldHeight * 0.05f, worldWidth * 0.9f, worldHeight * 0.2f);
//
//        // Adjust other UI components as needed
//    }
//
//    private void executeMove(Move move) {
//        canInput = false;
//        battleState = BattleState.MOVE_ANIMATION;
//        moveButtons.setVisible(false);
//
//        // Decrease PP of the move
//        move.use();
//
//        // Show message that the player used the move
//        showMessage(playerPokemon.getName() + " used " + move.getName() + "!");
//
//        // Optional: Start move animation here
//        startMoveAnimation(move);
//
//        // Delay to simulate move animation duration
//        Timer.schedule(new Timer.Task() {
//            @Override
//            public void run() {
//                // Calculate damage
//                int damage = calculateDamage(move, playerPokemon, wildPokemon);
//                applyDamage(wildPokemon, damage);
//
//                // Check for super effective or not very effective
//                float effectiveness = calculateTypeEffectiveness(move.getType(), wildPokemon.getPrimaryType());
//                showEffectivenessMessages(effectiveness);
//
//                // Check if the enemy Pokémon fainted
//                if (wildPokemon.getCurrentHp() <= 0) {
//                    endBattle(true);
//                } else {
//                    // Proceed to enemy's turn
//                    executeEnemyTurn();
//                }
//            }
//        }, getAnimationDuration(move)); // Delay equal to the move's animation duration
//    }
//
//    private int calculateDamage(Move move, Pokemon attacker, Pokemon defender) {
//        // Implement a basic damage calculation formula
//        // For example:
//        int baseDamage = move.getPower();
//        float effectiveness = calculateTypeEffectiveness(move.getType(), defender.getPrimaryType());
//        return (int) (baseDamage * effectiveness);
//    }
//
//    private float getAnimationDuration(io.github.pokemeetup.pokemon.attacks.Move move) {
//        // Different moves could have different animation durations
//        switch (move.getName().toLowerCase()) {
//            case "tackle":
//            case "scratch":
//                return 0.5f;
//            case "ember":
//            case "water gun":
//                return 1.0f;
//            case "solar beam":
//            case "hyper beam":
//                return 1.5f;
//            default:
//                return 1.0f;
//        }
//    }
//
//    public void setBattleCompletionHandler(Runnable handler) {
//        this.battleCompletionHandler = handler;
//    }
//
//    // When the battle is over
//
//    public void setBattleCompletionHandler(BattleCompletionHandler handler) {
//        this.completionHandler = handler;
//    }
//
//    private enum BattleState {
//        INTRO,
//        PLAYER_TURN,
//        ENEMY_TURN,
//        MOVE_ANIMATION,
//        MESSAGE_WAIT,
//        BATTLE_END
//    }
//
//
//}

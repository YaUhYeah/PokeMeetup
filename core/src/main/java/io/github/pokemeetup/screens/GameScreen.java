package io.github.pokemeetup.screens;

import com.badlogic.gdx.*;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.actions.SequenceAction;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.Timer;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.pokemeetup.CreatureCaptureGame;
import io.github.pokemeetup.audio.AudioManager;
import io.github.pokemeetup.chat.ChatSystem;
import io.github.pokemeetup.managers.BiomeManager;
import io.github.pokemeetup.multiplayer.OtherPlayer;
import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.multiplayer.server.ServerStorageSystem;
import io.github.pokemeetup.pokemon.Pokemon;
import io.github.pokemeetup.pokemon.PokemonParty;
import io.github.pokemeetup.pokemon.WildPokemon;
import io.github.pokemeetup.pokemon.attacks.Move;
import io.github.pokemeetup.screens.otherui.*;
import io.github.pokemeetup.system.*;
import io.github.pokemeetup.system.battle.BattleInitiationHandler;
import io.github.pokemeetup.system.data.PlayerData;
import io.github.pokemeetup.system.gameplay.inventory.Inventory;
import io.github.pokemeetup.system.data.ItemData;
import io.github.pokemeetup.system.gameplay.inventory.ItemManager;
import io.github.pokemeetup.system.gameplay.overworld.*;
import io.github.pokemeetup.system.data.WorldData;
import io.github.pokemeetup.system.gameplay.overworld.biomes.Biome;
import io.github.pokemeetup.system.gameplay.overworld.multiworld.WorldManager;
import io.github.pokemeetup.utils.textures.BattleAssets;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.storage.InventoryConverter;
import io.github.pokemeetup.utils.textures.TextureManager;
import sun.font.TextLabel;

import java.util.List;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static io.github.pokemeetup.system.gameplay.overworld.World.TILE_SIZE;

public class GameScreen implements Screen, PickupActionHandler, BattleInitiationHandler {
    private static final float TARGET_VIEWPORT_WIDTH_TILES = 24f; // Increased from 20
    private static final float UPDATE_INTERVAL = 0.1f; // 10 times per second
    private static final float CAMERA_LERP = 5.0f;// Constants for button sizes and padding
    private static final float TRANSITION_DURATION = 0.5f;
    private static final float TRANSITION_DELAY = 0.2f; // Add delay before screen switch=
    private static final float BATTLE_UI_FADE_DURATION = 0.5f;
    private static final float BATTLE_SCREEN_WIDTH = 800;
    private static final float BATTLE_SCREEN_HEIGHT = 480;
    private static final float DPAD_CENTER_SIZE = 100f;
    private static final float DEAD_ZONE = 10f;
    private static final float MOVEMENT_REPEAT_DELAY = 0.1f; // How often to trigger movement while holding
    private float ACTION_BUTTON_SIZE =80f;
    private static final float RUN_BUTTON_SIZE = 70f;
    private static final long SCREEN_INIT_TIMEOUT = 30000; // 30 seconds
    public static boolean SHOW_DEBUG_INFO = false; // Toggle flag for debug inforldName;
    private static float BUTTON_SIZE = Gdx.graphics.getWidth() * 0.12f; // Adjust as needed
    private static final float MAX_BUTTON_SIZE = 80f; // Adjust as needed

    private static float DPAD_SIZE = Gdx.graphics.getWidth() * 0.25f;
    private static float BUTTON_PADDING = Gdx.graphics.getWidth() * 0.02f;
    private static float DPAD_BUTTON_SIZE = 145f; // Larger touch targets
    private final CreatureCaptureGame game;
    private final GameClient gameClient;

    private final Vector2 BATTLE_RESOLUTION = new Vector2(800, 480);
    private final SpriteBatch uiBatch; // Add separate batch for UI
    private final float BUTTON_ALPHA = 0.7f;
    private final float JOYSTICK_SIZE = 200f;
    private final float KNOB_SIZE = 80f;
    private final ScheduledExecutorService screenInitScheduler = Executors.newSingleThreadScheduledExecutor();
    TextButton aButton;
    TextButton bButton;
    TextButton yButton;
    TextButton startButton;
    TextButton selectButton;
    TextButton xButton;
    private Vector2 joystickCenter = new Vector2();
    private Vector2 joystickCurrent = new Vector2();
    private boolean joystickActive = false;
    private int initializationTimer = 0;
    private ServerStorageSystem storageSystem;// In GameScreen class - fix input processor setup
    private WorldManager worldManager;
    private WorldData currentWorld;
    private SpriteBatch batch; // Single SpriteBatch instance
    private Stage pokemonPartyStage;
    private PokemonPartyUI pokemonPartyUI;
    private ChatSystem chatSystem;
    private Player player;
    private InputMultiplexer inputMultiplexer;
    private PokemonParty pokemonParty;
    private Table partyDisplay;
    private float stateUpdateTimer = 0;
    private GameMenu gameMenu;
    private float updateTimer = 0;
    private World world;
    private Stage uiStage;
    private Skin uiSkin;
    private BitmapFont font;
    private OrthographicCamera camera;
    private InputHandler inputHandler;
    private boolean isMultiplayer;
    private String username;
    private boolean inventoryOpen = false;
    private InventoryScreen inventoryScreen;
    private Inventory inventory;
    private Table hotbarTable;
    private BuildModeUI buildModeUI;
    private Rectangle inventoryButton;
    private Rectangle menuButton;
    private PlayerData playerData;
    private PokemonSpawnManager spawnManager;
    private ShapeRenderer shapeRenderer;
    private Skin skin;
    private Stage stage;
    private FitViewport cameraViewport;
    private boolean transitioning = false;
    private float transitionTimer = 0;
    private Table inventoryContainer;
    private TextButton closeInventoryButton;
    private WildPokemon transitioningPokemon = null;
    private boolean transitionOut = true;
    private boolean controlsInitialized = false;
    private boolean waitingForInitialization = true;
    private boolean initializationComplete = false;
    private StarterSelectionTable starterTable;
    private boolean inputBlocked = false;
    private float debugTimer = 0;
    private boolean initialized = false;
    private boolean initializedworld = false;
    private boolean starterSelectionComplete = false;
    private boolean initializationHandled = false;
    private volatile boolean isDisposing = false;
    private BattleTable battleTable;
    private BattleAssets battleAssets;
    private Skin battleSkin;
    private boolean inBattle = false;
    private Stage battleStage;
    private boolean battleInitialized = false;
    private boolean battleUIFading = false;
    private ScheduledFuture<?> screenInitTimeoutTask;
    private Table androidControls;
    private Image interactionButton;
    private float battleUIAlpha = 0f;
    private boolean inventoryInitialized = false;
    // Add this field
    private InputProcessor previousInputProcessor;
    private boolean menuVisible = false;
    private boolean isLoading = true;
    private LoadingScreen loadingScreen;
    private Table androidControlsTable;
    private ImageButton joystickKnob;
    private Table joystickBase;
    private Vector2 joystickOrigin = new Vector2();
    private Table closeButtonTable;
    private Table buttonTable;
    // Add these fields
    private Table dpadTable;
    private Table buttonsTable;
    private Rectangle upButton, downButton, leftButton, rightButton;
    private Rectangle runButton;
    private String currentDpadDirection = null;
    // In GameScreen class, update handleBattleInitiation method:
    private float movementTimer = 0f;
    private boolean isHoldingDirection = false;
    private boolean isRunPressed = false;
    // Add these fields
    private Rectangle centerButton; // For running
    private AndroidMovementController movementController;

    public GameScreen(CreatureCaptureGame game, String username, GameClient gameClient, World world) {
        GameLogger.info("GameScreen constructor called");
        try {
            this.game = game;
            this.world = world;
            this.username = username;
            this.gameClient = gameClient;
            game.setScreen(new LoadingScreen(game, this));
            this.isMultiplayer = !gameClient.isSinglePlayer();
            this.uiBatch = new SpriteBatch();
            this.uiStage = new Stage(new ScreenViewport(), uiBatch);
            this.batch = new SpriteBatch();
            this.shapeRenderer = new ShapeRenderer();
            this.skin = new Skin(Gdx.files.internal("Skins/uiskin.json"));
            this.uiSkin = this.skin;
            this.font = new BitmapFont(Gdx.files.internal("Skins/default.fnt"));
            this.pokemonPartyStage = new Stage(new ScreenViewport());
            this.stage = new Stage(new ScreenViewport());
            screenInitTimeoutTask = screenInitScheduler.schedule(() -> {
                if (!initializationComplete) {
                    GameLogger.error("Screen initialization timeout after " + (SCREEN_INIT_TIMEOUT / 1000) + " seconds");
                    Gdx.app.postRunnable(this::handleInitializationFailure);
                }
            }, SCREEN_INIT_TIMEOUT, TimeUnit.MILLISECONDS);
            if (isMultiplayer) {
                gameClient.setInitializationListener(this::handleClientInitialization);
            } else {
                completeInitialization();
            }      // Set up battle stage with specific size
            if (battleStage == null) {
                battleStage = new Stage(new FitViewport(800, 480));
            }
            this.gameMenu = new GameMenu(this, game, uiSkin, this.player, gameClient);
            gameMenu.getStage().getViewport().update(
                Gdx.graphics.getWidth(),
                Gdx.graphics.getHeight(),
                true
            );
            // Update viewport
            battleStage.getViewport().update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);

            initializeBattleAssets();
        } catch (Exception e) {
            GameLogger.error("Error during GameScreen initialization: " + e.getMessage());
            throw e;
        }
    }

    private void setupBattleInput() {
        if (battleStage != null) {
            InputMultiplexer battleMultiplexer = new InputMultiplexer();
            battleMultiplexer.addProcessor(battleStage); // Battle stage gets priority
            battleMultiplexer.addProcessor(uiStage);
            Gdx.input.setInputProcessor(battleMultiplexer);
            GameLogger.info("Battle input processors set up");
        }
    }

    private void handleClientInitialization(boolean success) {
        setupCamera();
        if (success) {
            Gdx.app.postRunnable(() -> {
                GameLogger.info("Client initialization complete - proceeding with setup");
                completeInitialization();
            });
        } else {
            Gdx.app.postRunnable(this::showReconnectDialog);
        }
    }

    private void initializeBattleAssets() {
        try {
            // Initialize battle assets first (atlas-based resources)
            battleAssets = new BattleAssets();
            battleAssets.initialize();


            // Optionally try to load the skin if it exists
            try {
                FileHandle skinFile = Gdx.files.internal("atlas/ui-gfx-atlas.json");
                if (skinFile.exists()) {
                    battleSkin = new Skin(skinFile);
                    battleSkin.addRegions(TextureManager.getUi());
                    GameLogger.info("Battle skin loaded successfully");
                } else {
                    GameLogger.info("No battle skin found - using default styles");
                    // Continue without skin - will use direct texture regions
                }
            } catch (Exception skinEx) {
                GameLogger.error("Could not load battle skin: " + skinEx.getMessage() + " - continuing without skin");
                // Continue without skin
                if (battleSkin != null) {
                    battleSkin.dispose();
                    battleSkin = null;
                }
            }

        } catch (Exception e) {
            GameLogger.error("Failed to initialize battle assets: " + e.getMessage());
            cleanup();
            throw new RuntimeException("Battle initialization failed", e);
        }
    }

    private void showReconnectDialog() {
        Dialog dialog = new Dialog("Connection Error", skin);
        dialog.text("Failed to connect to server. Would you like to retry?");
        TextButton retryButton = new TextButton("Retry", skin);
        retryButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                gameClient.connect();
                dialog.hide();
            }
        });
        dialog.getButtonTable().add(retryButton).padRight(20);
        TextButton exitButton = new TextButton("Exit", skin);
        exitButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                Gdx.app.exit();
            }
        });
        dialog.getButtonTable().add(exitButton);

        dialog.show(stage);
    }

    private void checkStarterSelection() {
        if (player == null) {
            GameLogger.error("Player is null during starter check!");
            return;
        }

        PokemonParty party = player.getPokemonParty();
        if (party == null) {
            GameLogger.error("Player's Pokémon party is null!");
            party = new PokemonParty();
            player.setPokemonParty(party);
        }

        if (party.getFirstPokemon() == null) {
            GameLogger.info("No Pokemon in party - initiating starter selection");
            initiateStarterSelection();
        } else {
            GameLogger.info("Player has Pokemon (" + party.getSize() + ") - proceeding with full initialization");
            starterSelectionComplete = true;
        }
    }

    private void handleInitializationFailure() {
        if (initializationHandled) {
            return;
        }
        initializationHandled = true;
        Dialog dialog = new Dialog("Connection Error", skin);
        dialog.text("Failed to initialize game client.\nWould you like to retry?");
        dialog.button("Retry", true).addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                retryInitialization();
            }
        });
        dialog.button("Exit", false).addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                Gdx.app.exit();
            }
        });
        dialog.show(stage);
    }

    private void retryInitialization() {
        waitingForInitialization = true;
        initializationComplete = false;

        try {
            if (gameClient != null) {
                gameClient.connect();
            }
        } catch (Exception e) {
            GameLogger.error("Failed to retry initialization: " + e.getMessage());
            handleClientInitialization(false);
        }
    }

    private void initializeWorld() {
        try {
            if (isMultiplayer) {
                this.player = this.gameClient.getActivePlayer();
            } else {
                this.player = game.getPlayer();
            }
            if (gameClient.getCurrentWorld() != null) {
                this.world = gameClient.getCurrentWorld();
                GameLogger.info("Using existing world from GameClient");
                return;
            }
            if (this.world == null) {
                String defaultWorldName = isMultiplayer ?
                    CreatureCaptureGame.MULTIPLAYER_WORLD_NAME :
                    "singleplayer_world";
                GameLogger.info("No world name provided, using default: " + defaultWorldName);
                this.world = new World(
                    defaultWorldName,
                    gameClient.getWorldSeed(),
                    gameClient,
                    new BiomeManager(gameClient.getWorldSeed())
                );
            }
            if (player != null) {
                player.initializeInWorld(world);
                world.setPlayer(player);
                player.setWorld(world);
            }
            if (gameClient.getCurrentWorld() == null) {
                gameClient.setCurrentWorld(world);
            }
            this.storageSystem = new ServerStorageSystem();
            this.worldManager = WorldManager.getInstance(storageSystem, isMultiplayer);
            try {
                worldManager.init();
                GameLogger.info("World manager initialized successfully");
            } catch (Exception e) {
                GameLogger.error("Failed to initialize world manager: " + e.getMessage());
                throw e;
            }

            GameLogger.info("World initialization complete");

        } catch (Exception e) {
            GameLogger.error("Failed to initialize world: " + e.getMessage());
            throw new RuntimeException("World initialization failed", e);
        }
    }

    private void completeInitialization() {
        GameLogger.info("completeInitialization() called");
        if (initializationComplete) {
            GameLogger.info("Initialization already completed");
            return;
        }

        try {
            // 1. Initialize ItemManager first as it's needed for inventory operations
            if (!ItemManager.isInitialized()) {
                GameLogger.info("Initializing ItemManager");
                ItemManager.initialize(TextureManager.items);
            }

            // 2. Initialize storage and world manager first
            this.storageSystem = new ServerStorageSystem();
            this.worldManager = WorldManager.getInstance(storageSystem, isMultiplayer);
            try {
                worldManager.init();
                GameLogger.info("World manager initialized successfully");
            } catch (Exception e) {
                GameLogger.error("Failed to initialize world manager: " + e.getMessage());
                throw e;
            }

            // 3. Initialize world and ensure it's valid
            initializeWorld();
            if (world == null) {
                throw new IllegalStateException("World initialization failed");
            }


            // 5. Initialize player's resources and animations
            if (player != null) {
                player.initializeResources();
                GameLogger.info("Player resources initialized");
            }
            if (isMultiplayer) {
                PlayerData playerDataFromServer = gameClient.getCurrentWorld().getWorldData().getPlayerData(username);
                if (playerDataFromServer != null) {
                    this.playerData = playerDataFromServer;
                    player.updateFromPlayerData(playerData);
                    GameLogger.info("Applied player data from server to player");
                } else {
                    GameLogger.error("Player data from server is null");
                    handleInitializationFailure();
                    return;
                }
            } else {
                WorldData worldData = world.getWorldData();
                if (worldData == null) {
                    GameLogger.error("World data is null during initialization");
                    handleInitializationFailure();
                    return;
                }

                this.currentWorld = worldData;
                PlayerData playerData = worldData.getPlayerData(username);

                if (playerData == null) {
                    playerData = new PlayerData(username);
                    worldData.savePlayerData(username, playerData);
                }

                this.playerData = playerData;
                playerData.applyToPlayer(player);
            }

            setupCamera();
            // 7. Initialize essential game systems
            this.inventory = player.getInventory();
            this.spawnManager = world.getPokemonSpawnManager();

            // 8. Check starter selection
            checkStarterSelection();

            // 9. Initialize chat system
            initializeChatSystem();

            // 10. Create input handler (needs initialized player and world)
            this.inputHandler = new InputHandler(player, this, this);

            setupGameClientListeners();
            createPartyDisplay();

            movementController = new AndroidMovementController(player);

            // 11. Setup input processors (needs all UI components)
            setupInputProcessors();

            // 12. Platform specific setup
            if (Gdx.app.getType() == Application.ApplicationType.Android) {
                initializeAndroidControls();
                initializationComplete = true;
            }

            // 13. Final world checks
            if (world != null && world.areAllChunksLoaded()) {
                initializedworld = true;
                GameLogger.info("Screen fully initialized and ready");
            }

            initializationComplete = true;
            if (screenInitTimeoutTask != null) {
                screenInitTimeoutTask.cancel(false);
            }
            initialized = true;
            GameLogger.info("Game initialization completed successfully");

        } catch (Exception e) {
            GameLogger.error("Error during initialization: " + e.getMessage());
            handleInitializationFailure();
        }
    }

    private void setupInputProcessors() {
        if (inputMultiplexer == null) {
            inputMultiplexer = new InputMultiplexer();
        }
        inputMultiplexer.clear();
        if (inBattle && battleStage != null) {
            inputMultiplexer.addProcessor(battleStage);
        } else {
            if (inventoryOpen && inventoryScreen != null) {
                inputMultiplexer.addProcessor(inventoryScreen.getStage());
            }
            if (uiStage != null) {
                inputMultiplexer.addProcessor(uiStage);
            }
            if (gameMenu != null && gameMenu.getStage() != null) {
                inputMultiplexer.addProcessor(gameMenu.getStage());
            }
            if (inputHandler != null) {
                inputMultiplexer.addProcessor(inputHandler);
            }
            if (Gdx.app.getType() == Application.ApplicationType.Android && controlsInitialized) {
                AndroidInputProcessor androidInputProcessor = new AndroidInputProcessor(
                    movementController, aButton, bButton, xButton, yButton, startButton);
                inputMultiplexer.addProcessor(androidInputProcessor);
            }
        }

        Gdx.input.setInputProcessor(inputMultiplexer);
        GameLogger.info("Input processors updated - Total processors: " + inputMultiplexer.size());
    }

    public void toggleGameMenu() {
        if (gameMenu != null) {
            if (!menuVisible) {
                gameMenu.show();
                menuVisible = true;
                // Set input focus to menu
                if (inputMultiplexer != null) {
                    inputMultiplexer.addProcessor(0, gameMenu.getStage()); // Add at highest priority
                }
            } else {
                gameMenu.hide();
                menuVisible = false;
                // Remove menu input processor
                if (inputMultiplexer != null) {
                    inputMultiplexer.removeProcessor(gameMenu.getStage());
                }
            }
        }
    }

    private void onStarterSelectionComplete(Pokemon starter) {
        if (starter == null || starterSelectionComplete) {
            GameLogger.error("Invalid starter selection or already completed");
            return;
        }

        GameLogger.info("Starter selection completed: " + starter.getName());
        starterSelectionComplete = true;

        // Clear party first
        player.getPokemonParty().clearParty();

        // Add starter Pokemon
        player.getPokemonParty().addPokemon(starter);

        // Verify party state
        if (player.getPokemonParty().getSize() == 0) {
            GameLogger.error("Failed to add starter Pokemon to party!");
        } else {
            GameLogger.info("Successfully added starter " + starter.getName() +
                " to party. Party size: " + player.getPokemonParty().getSize());
        }

        // Update player data with new party
        player.updatePlayerData();

        // Force a save to ensure the starter is persisted
        if (player.getWorld() != null && player.getWorld().getWorldData() != null) {
            player.getWorld().getWorldData().savePlayerData(player.getUsername(), player.getPlayerData());
            GameLogger.info("Saved player data with starter Pokemon");
        }

        updateSlotVisuals();

        if (starterTable != null) {
            starterTable.remove();
            starterTable = null;
        }

        setupInputProcessors();
        if (isMultiplayer) {
            gameClient.setInitialized(true);
        }

        inputBlocked = false;
        GameLogger.info("Starter selection completed - normal game input restored");
    }

    private void initiateStarterSelection() {
        if (inputMultiplexer != null) {
            inputMultiplexer.clear();
        }

        Gdx.input.setInputProcessor(uiStage);
        if (uiStage == null) {
            uiStage = new Stage(new ScreenViewport());
        }
        if (starterTable == null) {
            starterTable = new StarterSelectionTable(skin);
            ;
            starterTable.setSelectionListener(new StarterSelectionTable.SelectionListener() {
                @Override
                public void onStarterSelected(Pokemon starter) {
                    GameLogger.info("Starter selection triggered: " + (starter != null ? starter.getName() : "null"));
                    onStarterSelectionComplete(starter);
                }

                @Override
                public void onSelectionStart() {
                    inputBlocked = true;
                }
            });

            // Set up table layout
            starterTable.setFillParent(true); // This ensures the table fills the stage

            uiStage.addActor(starterTable);


            // Ensure visibility and touchability
            starterTable.setVisible(true);
            starterTable.setTouchable(Touchable.enabled);

            // Log for debugging
            GameLogger.info("Starter table created and added to stage");
        }

        // Important: Update stage viewport to match screen size
        uiStage.getViewport().update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);

        // Set input processor
        Gdx.input.setInputProcessor(uiStage);

        // Log stage and input processor state
        GameLogger.info("Stage viewport size: " + uiStage.getViewport().getScreenWidth() +
            "x" + uiStage.getViewport().getScreenHeight());
        GameLogger.info("Current input processor: " + Gdx.input.getInputProcessor().getClass().getName());
    }

    private void addAndroidMenuButton() {
        if (Gdx.app.getType() != Application.ApplicationType.Android) {
            return;
        }

        Table menuButtonTable = new Table();
        menuButtonTable.setFillParent(true);

        TextButton menuButton = new TextButton("MENU", skin);
        menuButton.getColor().a = BUTTON_ALPHA;
        menuButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                toggleGameMenu();
            }
        });

        menuButtonTable.add(menuButton).size(BUTTON_SIZE * 1.5f, BUTTON_SIZE).top().right().pad(20);
        uiStage.addActor(menuButtonTable);
    }

    private void initializeChatSystem() {
        if (chatSystem != null) {
            return;
        }

        float screenWidth = Gdx.graphics.getWidth();
        float screenHeight = Gdx.graphics.getHeight();
        float chatWidth = Math.max(ChatSystem.MIN_CHAT_WIDTH, screenWidth * 0.25f);
        float chatHeight = Math.max(ChatSystem.MIN_CHAT_HEIGHT, screenHeight * 0.3f);

        chatSystem = new ChatSystem(uiStage, skin, gameClient, username);
        chatSystem.setSize(chatWidth, chatHeight);
        chatSystem.setPosition(
            ChatSystem.CHAT_PADDING,
            screenHeight - chatHeight - ChatSystem.CHAT_PADDING
        );

        // **Ensure ChatSystem is on top by adding it last**
        chatSystem.setZIndex(Integer.MAX_VALUE); // Use maximum Z-index
        chatSystem.setVisible(true);
        chatSystem.setTouchable(Touchable.enabled);

        // **Create background with new SVG texture**
        Pixmap bgPixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        bgPixmap.setColor(0, 0, 0, 0.8f);
        bgPixmap.fill();
        TextureRegion bgTexture = new TextureRegion(new Texture(bgPixmap));
        chatSystem.setBackground(new TextureRegionDrawable(bgTexture));
        bgPixmap.dispose();

        // **Add to UI stage after all other UI elements**
        uiStage.addActor(chatSystem);
        chatSystem.toFront(); // Ensure it's the topmost actor

        // **Set Z-index to highest value**
        chatSystem.setZIndex(Integer.MAX_VALUE);

        // **Update input processors**
        setupInputProcessors();

        GameLogger.info("Chat system initialized at: " + ChatSystem.CHAT_PADDING + "," +
            (screenHeight - chatHeight - ChatSystem.CHAT_PADDING));
    }

    private void createPartyDisplay() {
        partyDisplay = new Table();
        partyDisplay.setFillParent(true);
        partyDisplay.bottom();
        partyDisplay.padBottom(20f);

        Table slotsTable = new Table();
        slotsTable.setBackground(
            new TextureRegionDrawable(TextureManager.ui.findRegion("hotbar_bg"))
        );
        slotsTable.pad(4f);

        List<Pokemon> party = player.getPokemonParty().getParty();

        for (int i = 0; i < PokemonParty.MAX_PARTY_SIZE; i++) {
            Pokemon pokemon = (party.size() > i) ? party.get(i) : null;
            Table slotCell = createPartySlotCell(i, pokemon);
            slotsTable.add(slotCell).size(64).pad(2);
        }

        partyDisplay.add(slotsTable);
        uiStage.addActor(partyDisplay);
    }

    @Override
    public void hide() {

    }

    private void ensureSaveDirectories() {
        FileHandle saveDir = Gdx.files.local("save");
        if (!saveDir.exists()) {
            saveDir.mkdirs();
        }
    }

    public World getWorld() {
        return world;
    }

    public Player getPlayer() {
        return player;
    }

    private void setupGameClientListeners() {
        gameClient.setLoginResponseListener(response -> {
            if (response.success) {
                GameLogger.info("Logged in as " + response.username);
            } else {
                GameLogger.info("Login failed: " + response.message);
            }
        });

        gameClient.setRegistrationResponseListener(response -> {
            if (response.success) {
                GameLogger.info("Registration successful for " + response.username);
            } else {
                GameLogger.info("Registration failed: " + response.message);
            }
        });
    }

    private Table createPartySlotCell(int index, Pokemon pokemon) {
        Table cell = new Table();
        boolean isSelected = index == 0;

        TextureRegionDrawable slotBg = new TextureRegionDrawable(
            TextureManager.ui.findRegion(isSelected ? "slot_selected" : "slot_normal")
        );
        cell.setBackground(slotBg);

        if (pokemon != null) {
            Table contentStack = new Table();
            contentStack.setFillParent(true);

            Image pokemonIcon = new Image(pokemon.getCurrentIconFrame(Gdx.graphics.getDeltaTime()));
            pokemonIcon.setScaling(Scaling.fit);

            contentStack.add(pokemonIcon).size(40).padTop(4).row();

            Label levelLabel = new Label("Lv." + pokemon.getLevel(), skin);
            levelLabel.setFontScale(0.8f);
            contentStack.add(levelLabel).padTop(2).row();

            ProgressBar hpBar = new ProgressBar(0, pokemon.getStats().getHp(), 1, false, skin);
            hpBar.setValue(pokemon.getCurrentHp());
            contentStack.add(hpBar).width(40).height(4).padTop(2);

            cell.add(contentStack).expand().fill();
        }

        return cell;
    }

    private void updateSlotVisuals() {
        partyDisplay.clearChildren();
        createPartyDisplay();
    }

    public PlayerData getCurrentPlayerState() {
        PlayerData currentState = new PlayerData(player.getUsername());
        // Use InventoryConverter to extract inventory data
        InventoryConverter.extractInventoryDataFromPlayer(player, currentState);
        return currentState;
    }

    // Helper methods for Pokemon party handling
    private boolean isPokemonPartyVisible() {
        return pokemonPartyUI != null && pokemonPartyUI.isVisible();
    }

    private void updateAndroidControlPositions() {
        if (Gdx.app.getType() != Application.ApplicationType.Android) {
            return;
        }

        try {
            float screenWidth = Gdx.graphics.getWidth();
            float screenHeight = Gdx.graphics.getHeight();
            float buttonSize = screenHeight * 0.1f;
            float padding = buttonSize * 0.5f;

            if (joystickCenter == null) {
                joystickCenter = new Vector2(screenWidth * 0.15f, screenHeight * 0.2f);
            } else {
                joystickCenter.set(screenWidth * 0.15f, screenHeight * 0.2f);
            }

            if (joystickCurrent == null) {
                joystickCurrent = new Vector2(joystickCenter);
            } else {
                joystickCurrent.set(joystickCenter);
            }
            if (inventoryButton == null) {
                inventoryButton = new Rectangle(
                    screenWidth - (buttonSize * 2 + padding * 2),
                    screenHeight - (buttonSize + padding),
                    buttonSize,
                    buttonSize
                );
            } else {
                inventoryButton.set(
                    screenWidth - (buttonSize * 2 + padding * 2),
                    screenHeight - (buttonSize + padding),
                    buttonSize,
                    buttonSize
                );
            }

            if (menuButton == null) {
                menuButton = new Rectangle(
                    screenWidth - (buttonSize + padding),
                    screenHeight - (buttonSize + padding),
                    buttonSize,
                    buttonSize
                );
            } else {
                menuButton.set(
                    screenWidth - (buttonSize + padding),
                    screenHeight - (buttonSize + padding),
                    buttonSize,
                    buttonSize
                );
            }

            GameLogger.info("Updated Android controls - Screen: " + screenWidth + "x" + screenHeight +
                ", Joystick at: " + joystickCenter.x + "," + joystickCenter.y);

        } catch (Exception e) {
            GameLogger.error("Error updating Android controls: " + e.getMessage());
            e.printStackTrace();

            initializeAndroidControlsSafe();
        }
    }

    private void initializeAndroidControlsSafe() {
        try {
            float screenWidth = Math.max(Gdx.graphics.getWidth(), 480); // Minimum safe width
            float screenHeight = Math.max(Gdx.graphics.getHeight(), 320); // Minimum safe height
            float buttonSize = Math.min(screenHeight * 0.1f, 64); // Limit maximum size
            float padding = buttonSize * 0.5f;

            joystickCenter = new Vector2(screenWidth * 0.15f, screenHeight * 0.2f);
            joystickCurrent = new Vector2(joystickCenter);

            inventoryButton = new Rectangle(
                screenWidth - (buttonSize * 2 + padding * 2),
                screenHeight - (buttonSize + padding),
                buttonSize,
                buttonSize
            );

            menuButton = new Rectangle(
                screenWidth - (buttonSize + padding),
                screenHeight - (buttonSize + padding),
                buttonSize,
                buttonSize
            );

            GameLogger.info("Initialized safe Android controls");
        } catch (Exception e) {
            GameLogger.error("Failed to initialize safe Android controls: " + e.getMessage());
        }
    }

    private void ensureAndroidControlsInitialized() {
        if (Gdx.app.getType() == Application.ApplicationType.Android &&
            (joystickCenter == null || joystickCurrent == null ||
                inventoryButton == null || menuButton == null)) {

            initializeAndroidControlsSafe();
        }
    }

    @Override
    public void handleBattleInitiation() {
        if (inBattle || transitioning) {
            GameLogger.info("Battle or transition already in progress");
            return;
        }

        WildPokemon nearestPokemon = world.getNearestInteractablePokemon(player);
        if (nearestPokemon == null || nearestPokemon.isAddedToParty()) {
            GameLogger.info("No valid Pokemon found for battle");
            return;
        }

        if (nearestPokemon.getLevel() >= 7) {
            GameLogger.info("Initiating battle with level " + nearestPokemon.getLevel() + " " + nearestPokemon.getName());

            if (player.getPokemonParty().getSize() == 0) {
                chatSystem.handleIncomingMessage(createSystemMessage("You need Pokémon to battle!"));
                return;
            }

            // Initialize battle components first
            try {
                // Create battle stage if needed
                if (battleStage == null) {
                    battleStage = new Stage(new FitViewport(800, 480));
                    battleStage.getViewport().update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
                    GameLogger.info("Created new battle stage with viewport: 800x480");
                }

                // Create battle table immediately to verify it works
                battleTable = new BattleTable(battleStage, battleSkin,
                    player.getPokemonParty().getFirstPokemon(),
                    nearestPokemon);

                if (battleTable == null) {
                    throw new IllegalStateException("Failed to create battle table");
                }
                battleTable.setFillParent(true); // Ensures correct sizing
                battleTable.setVisible(true);    // Ensures visibility
                battleStage.addActor(battleTable); // Adds to battleStage

                GameLogger.info("Battle table created successfully");

                // Now start transition sequence
                transitioning = true;
                inputBlocked = true;
                inBattle = true;

                // Create dark overlay
                Table overlay = new Table();
                overlay.setFillParent(true);
                overlay.setBackground(new TextureRegionDrawable(
                    TextureManager.getUi().findRegion("dark-overlay")));
                overlay.getColor().a = 0f;
                uiStage.addActor(overlay);


                // Create and execute transition sequence
                SequenceAction battleSequence = Actions.sequence(
                    Actions.fadeIn(TRANSITION_DURATION),
                    Actions.delay(TRANSITION_DURATION),
                    Actions.run(() -> {
                        GameLogger.info("Starting battle initialization...");
                        initiateBattle(nearestPokemon);

                        if (battleInitialized) {
                            inBattle = true;
                            GameLogger.info("Battle initialized successfully");
                        } else {
                            GameLogger.error("Battle initialization failed");
                            cleanup();
                        }
                        // Add table to stage
                        battleStage.addActor(battleTable);

                        // Set input processor
                        InputMultiplexer multiplexer = new InputMultiplexer(battleStage);
                        Gdx.input.setInputProcessor(multiplexer);
                        GameLogger.info("Set input processor to battle stage");

                        // Set up callback
                        battleTable.setCallback(new BattleTable.BattleCallback() {
                            @Override
                            public void onBattleEnd(boolean playerWon) {
                                GameLogger.info("Battle ended, playerWon: " + playerWon);
                                endBattle(playerWon, nearestPokemon);
                            }

                            @Override
                            public void onTurnEnd(Pokemon activePokemon) {
                                GameLogger.info("Turn ended for: " + activePokemon.getName());
                            }

                            @Override
                            public void onStatusChange(Pokemon pokemon, Pokemon.Status newStatus) {
                                GameLogger.info("Status changed for " + pokemon.getName() + ": " + newStatus);
                            }

                            @Override
                            public void onMoveUsed(Pokemon user, Move move, Pokemon target) {
                                GameLogger.info(user.getName() + " used " + move.getName());
                            }
                        });

                        battleTable.validate(); // Force layout update
                        GameLogger.info("Battle initialization complete");
                    }),
                    Actions.run(() -> {
                        // Fade in battle table
                        battleTable.getColor().a = 0f;
                        battleTable.addAction(Actions.fadeIn(TRANSITION_DURATION));
                    }),
                    Actions.delay(TRANSITION_DURATION),
                    Actions.run(() -> {
                        overlay.remove();
                        transitioning = false;
                        inputBlocked = false;
                        GameLogger.info("Battle transition complete");
                    })
                );

                overlay.addAction(battleSequence);
                GameLogger.info("Battle sequence started");

            } catch (Exception e) {
                GameLogger.error("Error during battle initiation: " + e.getMessage());
                e.printStackTrace();
                cleanup();
                inBattle = false;
                transitioning = false;
                inputBlocked = false;
            }
        } else {
            handlePokemonCapture(nearestPokemon);
        }
    }

    private void handlePokemonCapture(WildPokemon wildPokemon) {
        if (player.getPokemonParty().isFull()) {
            chatSystem.handleIncomingMessage(createSystemMessage(
                "Your party is full! Cannot capture more Pokémon!"
            ));
            return;
        }

        // For now, auto-capture low-level Pokémon
        player.getPokemonParty().addPokemon(wildPokemon);
        world.getPokemonSpawnManager().removePokemon(wildPokemon.getUuid());

        chatSystem.handleIncomingMessage(createSystemMessage(
            "Caught a wild " + wildPokemon.getName() + "!"
        ));
        updateSlotVisuals();
    }

    private void endBattle(boolean playerWon, WildPokemon wildPokemon) {
        if (battleTable != null && battleTable.hasParent()) {
            battleTable.addAction(Actions.sequence(
                Actions.fadeOut(BATTLE_UI_FADE_DURATION),
                Actions.run(() -> {
                    try {
                        // Handle battle results
                        if (playerWon) {
                            handleBattleVictory(wildPokemon);
                        } else {
                            handleBattleDefeat();
                        }

                        // Clean up battle components
                        cleanup();
                    } catch (Exception e) {
                        GameLogger.error("Error ending battle: " + e.getMessage());
                        cleanup();
                    }
                })
            ));
            battleUIFading = true;
        } else {
            cleanup();
        }
    }

    public void pauseWildPokemon(WildPokemon pokemon) {
        if (pokemon != null) {
            pokemon.setMoving(false);
            if (pokemon.getAi() != null) {
                pokemon.getAi().enterIdleState();
            }
        }
    }

    // Add this method to handle cleanup
    private void cleanup() {
        if (battleTable != null) {
            battleTable.dispose();
            battleTable = null;
        }
        if (battleStage != null) {
            battleStage.dispose();
            battleStage = null;
        }
        inBattle = false;
        transitioning = false;
        inputBlocked = false;
        setupInputProcessors();
    }

    // Helper method to revert to world state
    private void revertToWorld() {
        if (battleTable != null) {
            battleTable.remove();
            battleTable = null;
        }
        transitioning = false;
        inputBlocked = false;
        inBattle = false;
    }

    private void initiateBattle(WildPokemon wildPokemon) {
        try {
            // Pause the wild Pokemon
            if (wildPokemon != null) {
                wildPokemon.setMoving(false);
                if (wildPokemon.getAi() != null) {
                    wildPokemon.getAi().enterIdleState();
                    wildPokemon.getAi().setPaused(true);
                }
                wildPokemon.setX(wildPokemon.getX());
                wildPokemon.setY(wildPokemon.getY());
            }

            // Create new battle stage if needed
            if (battleStage == null) {
                battleStage = new Stage(new FitViewport(800, 480));
            }
            battleStage.clear(); // Clear any existing actors

            // Create and setup battle table
            battleTable = new BattleTable(battleStage, battleSkin,
                player.getPokemonParty().getFirstPokemon(),
                wildPokemon);
            battleTable.setFillParent(true);
            battleStage.addActor(battleTable);

            // Set up semi-transparent overlay
            Table overlay = new Table();
            overlay.setFillParent(true);
            Pixmap dimPixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
            dimPixmap.setColor(0, 0, 0, 0.1f);
            dimPixmap.fill();
            overlay.setBackground(new TextureRegionDrawable(new TextureRegion(new Texture(dimPixmap))));
            dimPixmap.dispose();
            battleStage.addActor(overlay);

            // Initialize battle state
            battleTable.validate();
            inBattle = true;
            battleInitialized = true;

            // Update input processors
            setupBattleInput();

            // Set up callback
            battleTable.setCallback(new BattleTable.BattleCallback() {
                @Override
                public void onBattleEnd(boolean playerWon) {
                    endBattle(playerWon, wildPokemon);
                }

                @Override
                public void onTurnEnd(Pokemon activePokemon) {
                    GameLogger.info("Turn ended for: " + activePokemon.getName());
                }

                @Override
                public void onStatusChange(Pokemon pokemon, Pokemon.Status newStatus) {
                    GameLogger.info("Status changed for " + pokemon.getName() + ": " + newStatus);
                }

                @Override
                public void onMoveUsed(Pokemon user, Move move, Pokemon target) {
                    GameLogger.info(user.getName() + " used " + move.getName());
                }
            });

            // Update viewport
            battleStage.getViewport().update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);

            GameLogger.info("Battle initialization complete");

        } catch (Exception e) {
            GameLogger.error("Failed to initialize battle: " + e.getMessage());
            e.printStackTrace();
            cleanup();
        }
    }

    private void handleBattleVictory(WildPokemon wildPokemon) {
        // Award experience
        int expGain = calculateExperienceGain(wildPokemon);
        player.getPokemonParty().getFirstPokemon().addExperience(expGain);

        // Remove the defeated Pokémon from the game world
        world.getPokemonSpawnManager().removePokemon(wildPokemon.getUuid());

        // Show victory message
        chatSystem.handleIncomingMessage(createSystemMessage(
            "Victory! " + player.getPokemonParty().getFirstPokemon().getName() +
                " gained " + expGain + " experience!"
        ));
    }

    private void handleBattleDefeat() {
        // Simply heal the party
        player.getPokemonParty().healAllPokemon();

        // Show healing message
        chatSystem.handleIncomingMessage(createSystemMessage(
            "Your Pokémon have been healed!"
        ));
    }

    private int calculateExperienceGain(WildPokemon wildPokemon) {
        // Basic experience calculation
        return (int) (wildPokemon.getBaseExperience() * wildPokemon.getLevel() / 7);
    }

    private void renderBattleTransition(float delta) {
        transitionTimer += delta;
        float progress = Math.min(1, transitionTimer / TRANSITION_DURATION);

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0, 0, 0, 1);

        float height = Gdx.graphics.getHeight() * progress / 2;
        shapeRenderer.rect(0, Gdx.graphics.getHeight() - height,
            Gdx.graphics.getWidth(), height);
        shapeRenderer.rect(0, 0, Gdx.graphics.getWidth(), height);

        shapeRenderer.end();

        if (progress >= 1) {
            transitioning = false;
            transitionTimer = 0;
        }
    }

    public void startBattle() {
    }

    private NetworkProtocol.ChatMessage createSystemMessage(String content) {
        NetworkProtocol.ChatMessage message = new NetworkProtocol.ChatMessage();
        message.sender = "System";
        message.content = content;
        message.timestamp = System.currentTimeMillis();
        message.type = NetworkProtocol.ChatType.SYSTEM;
        return message;
    }

    @Override
    public void show() {
        if (player != null) {
            player.initializeResources();
            GameLogger.info("Reinitialized player resources on screen show");
        }
        if (!initializationComplete) {
            if (uiStage != null) {
                Gdx.input.setInputProcessor(uiStage);
                GameLogger.info("Set input processor to uiStage during starter selection");
            }
            return;
        }
        if (inBattle && battleStage != null) {
            // Set up battle-specific input handling
            InputMultiplexer battleMultiplexer = new InputMultiplexer();
            battleMultiplexer.addProcessor(battleStage);
            battleMultiplexer.addProcessor(uiStage);
            if (inputHandler != null) {
                battleMultiplexer.addProcessor(inputHandler);
            }
            Gdx.input.setInputProcessor(battleMultiplexer);
            GameLogger.info("Battle input processors initialized");
        }
        if (!initialized) {
            initialized = true;
            if (isMultiplayer) {
                initializeChatSystem();
                setupGameClientListeners();
            }
            if (!starterSelectionComplete) {
                Gdx.input.setInputProcessor(uiStage);
                GameLogger.info("Set input processor to uiStage for starter selection");
            } else {
                setupInputProcessors();
            }
        }
        batch = new SpriteBatch();
        AudioManager.getInstance().stopMenuMusic();
        if (player.getPokemonParty().getSize() == 0) {
            GameLogger.info("In starter selection - setting input processor to uiStage only");
            Gdx.input.setInputProcessor(uiStage);
        } else {
            GameLogger.info("Not in starter selection - setting up normal input processors");
            setupInputProcessors();
            //            InputMultiplexer multiplexer = new InputMultiplexer();
            //            multiplexer.addProcessor(stage);
            //            multiplexer.addProcessor(inputHandler);
            //            if (Gdx.app.getType() == Application.ApplicationType.Android) {
            //                multiplexer.addProcessor(new AndroidInputProcessor());
            //                initializeAndroidControls();
            //                addAndroidMenuButton();
            //            }
            //
            //            Gdx.input.setInputProcessor(multiplexer);
        }
    }

    private void renderOtherPlayers(SpriteBatch batch, Rectangle viewBounds) {
        if (gameClient == null || gameClient.isSinglePlayer()) {
            return;
        }

        Map<String, OtherPlayer> otherPlayers = gameClient.getOtherPlayers();

        synchronized (otherPlayers) {
            // Sort players by Y position for correct depth
            List<OtherPlayer> sortedPlayers = new ArrayList<>(otherPlayers.values());
            sortedPlayers.sort((p1, p2) -> Float.compare(p2.getY(), p1.getY()));

            for (OtherPlayer otherPlayer : sortedPlayers) {
                if (otherPlayer == null) continue;

                float playerX = otherPlayer.getX();
                float playerY = otherPlayer.getY();

                // Only render if within view bounds
                if (viewBounds.contains(playerX, playerY)) {
                    otherPlayer.render(batch);
                }
            }
        }
    }

    private void setupCamera() {
        camera = new OrthographicCamera();
        float baseWidth = TARGET_VIEWPORT_WIDTH_TILES * TILE_SIZE;
        float baseHeight = baseWidth * ((float) Gdx.graphics.getHeight() / Gdx.graphics.getWidth());
        cameraViewport = new FitViewport(baseWidth, baseHeight, camera);
        cameraViewport.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), false);
        if (player != null) {
            camera.position.set(
                player.getX() + Player.FRAME_WIDTH / 2f,
                player.getY() + Player.FRAME_HEIGHT / 2f,
                0
            );
        }

        camera.update();

        GameLogger.info("Camera setup - viewport: " + baseWidth + "x" + baseHeight);
    }

    private void updateCamera() {
        if (player != null) {
            float targetX = player.getX() + Player.FRAME_WIDTH / 2f;
            float targetY = player.getY() + Player.FRAME_HEIGHT / 2f;
            float lerp = CAMERA_LERP * Gdx.graphics.getDeltaTime();
            camera.position.x += (targetX - camera.position.x) * lerp;
            camera.position.y += (targetY - camera.position.y) * lerp;

            camera.update();
        }
    }

    @Override
    public void render(float delta) {
        // Clear screen
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        if (movementController != null) {
            movementController.update(delta);
        }
        // Handle starter Pokemon case first
        if (player != null && player.getPokemonParty().getSize() == 0) {
            Gdx.gl.glClearColor(0.1f, 0.1f, 0.2f, 1);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

            uiStage.act(delta);
            uiStage.draw();

            debugTimer += delta;
            if (debugTimer >= 1.0f) {
                debugInputState();
                debugTimer = 0;
            }
            return;
        }
        batch.begin();
        batch.setProjectionMatrix(camera.combined);

        // World rendering
        if (world != null && player != null) {
            Rectangle viewBounds = new Rectangle(
                camera.position.x - (camera.viewportWidth * camera.zoom) / 2,
                camera.position.y - (camera.viewportHeight * camera.zoom) / 2,
                camera.viewportWidth * camera.zoom,
                camera.viewportHeight * camera.zoom
            );

            world.render(batch, viewBounds, player);

            if (!gameClient.isSinglePlayer()) {
                renderOtherPlayers(batch, viewBounds);

                gameClient.processChunkQueue();
            }
        }           // Debug info
        if (SHOW_DEBUG_INFO) {
            renderDebugInfo();
        }

        batch.end();
        // Handle world initialization
        if (world != null && !initializedworld) {
            if (!world.areAllChunksLoaded()) {
                initializationTimer += (int) delta;
                if (initializationTimer > 5f) {
                    GameLogger.info("Attempting to force load missing chunks...");
                    world.forceLoadMissingChunks();
                    initializationTimer = 0;
                }
            } else {
                initializedworld = true;
                GameLogger.info("All chunks successfully loaded");
            }
        }
        if (isHoldingDirection && currentDpadDirection != null) {
            movementTimer += delta;
            if (movementTimer >= MOVEMENT_REPEAT_DELAY) {
                player.move(currentDpadDirection);
                movementTimer = 0f;
            }
            player.setRunning(isRunPressed);
        }

        // Enable blending for UI elements
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        if (uiStage != null) {
            uiStage.getViewport().apply();
            uiStage.act(delta);
            uiStage.draw();
        }

// Then draw battleStage
        if (inBattle) {
            if (battleStage != null && !isDisposing) {
                battleStage.act(delta);
                if (battleTable != null && battleTable.hasParent()) {
                    battleStage.draw();
                }
            }
        }


        if (menuVisible && gameMenu != null) {
            gameMenu.render();
        }

        if (inventoryOpen && inventoryScreen != null) {
            // Enable blending for semi-transparent background
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

            // Draw dark background
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            shapeRenderer.setColor(0, 0, 0, 0.7f);
            shapeRenderer.rect(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            shapeRenderer.end();

            // Render inventory
            inventoryScreen.render(delta);

            Gdx.gl.glDisable(GL20.GL_BLEND);
        }

        // Pokemon party rendering
        if (isPokemonPartyVisible()) {
            pokemonPartyStage.act(delta);
            pokemonPartyStage.draw();
        }


        // Android controls
        if (Gdx.app.getType() == Application.ApplicationType.Android && controlsInitialized) {
            ensureAndroidControlsInitialized();
            renderAndroidControls();
        }

        Gdx.gl.glDisable(GL20.GL_BLEND);

        // Game state updates
        if (world != null && player != null) {
            float deltaTime = Gdx.graphics.getDeltaTime();
            player.update(deltaTime);
            // Camera update
            if (!inBattle && !transitioning) {
                updateCamera();
            }

            if (isMultiplayer) {
                // Other systems updates
                updateOtherPlayers(delta);

                if (gameClient != null) {
                    gameClient.tick(delta);
                }
                if (world != null) {
                    world.update(delta, new Vector2(player.getTileX(), player.getTileY()),
                        Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
                }
            } else {
                player.validateResources();
                float viewportWidthPixels = camera.viewportWidth * camera.zoom;
                float viewportHeightPixels = camera.viewportHeight * camera.zoom;
                world.update(delta,
                    new Vector2(player.getTileX(), player.getTileY()),
                    viewportWidthPixels,
                    viewportHeightPixels
                );
            }

            if (worldManager != null) {
                worldManager.checkAutoSave();
            }

            handleInput();
            updateTimer += delta;

            // Handle multiplayer updates
            if (isMultiplayer && updateTimer >= UPDATE_INTERVAL) {
                updateTimer = 0;
                NetworkProtocol.PlayerUpdate update = new NetworkProtocol.PlayerUpdate();
                update.username = player.getUsername();
                update.x = player.getX();
                update.y = player.getY();
                update.direction = player.getDirection();
                update.isMoving = player.isMoving();
                update.wantsToRun = player.isRunning();
                update.timestamp = System.currentTimeMillis();
                // Add debug log
                GameLogger.info("Sending player update: pos=(" + update.x + "," + update.y +
                    ") dir=" + update.direction + " moving=" + update.isMoving);
                assert gameClient != null;
                gameClient.sendPlayerUpdate();

                // Handle incoming updates
                Map<String, NetworkProtocol.PlayerUpdate> updates = gameClient.getPlayerUpdates();
                if (!updates.isEmpty()) {
                    synchronized (gameClient.getOtherPlayers()) {
                        for (NetworkProtocol.PlayerUpdate playerUpdate : updates.values()) {
                            if (!playerUpdate.username.equals(player.getUsername())) {
                                OtherPlayer op = gameClient.getOtherPlayers().get(playerUpdate.username);
                                if (op == null) {
                                    op = new OtherPlayer(playerUpdate.username, playerUpdate.x, playerUpdate.y);
                                    gameClient.getOtherPlayers().put(playerUpdate.username, op);
                                    GameLogger.info("Created new player: " + playerUpdate.username);
                                }
                                op.updateFromNetwork(playerUpdate);
                            }
                        }
                    }
                }
            }
        }
    }

    private String formatPlayedTime(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        seconds = seconds % 60;
        minutes = minutes % 60;

        return String.format("%02dh %02dm %02ds", hours, minutes, seconds);
    }

    private void updateOtherPlayers(float delta) {
        if (isMultiplayer) {
            Map<String, OtherPlayer> others = gameClient.getOtherPlayers();
            GameLogger.info("Number of other players: " + others.size());
            if (!others.isEmpty()) {
                for (OtherPlayer otherPlayer : others.values()) {
                    try {
                        otherPlayer.update(delta);
                    } catch (Exception e) {
                        GameLogger.error("Error updating other player: " + e.getMessage());
                    }
                }
            }
        }
    }

    public boolean isInitialized() {
        return initializedworld;
    }

    public void setInitialized(boolean initialized) {
        this.initializedworld = initialized;
    }

    private void renderDebugInfo() {
        // Remove batch.begin() and batch.end() since we're now inside an existing batch block
        batch.setProjectionMatrix(uiStage.getCamera().combined);
        font.setColor(Color.WHITE);

        float debugY = 25;

        float pixelX = player.getX();
        float pixelY = player.getY();
        int tileX = (int) Math.floor(pixelX / TILE_SIZE);
        int tileY = (int) Math.floor(pixelY / TILE_SIZE);
        Biome currentBiome = world.getBiomeAt(tileX, tileY);
        font.draw(batch, String.format("Pixels: (%d, %d)", (int) pixelX, (int) pixelY), 10, debugY);
        debugY += 20;
        font.draw(batch, String.format("Tiles: (%d, %d)", tileX, tileY), 10, debugY);
        debugY += 20;
        font.draw(batch, "Direction: " + player.getDirection(), 10, debugY);
        debugY += 20;
        font.draw(batch, "Biome: " + currentBiome.getName(), 10, debugY);
        debugY += 20;

        font.draw(batch, "Active Pokemon: " + getTotalPokemonCount(), 10, debugY);
        debugY += 20;

        String timeString = DayNightCycle.getTimeString(world.getWorldData().getWorldTimeInMinutes());
        font.draw(batch, "Time: " + timeString, 10, debugY);
        debugY += 20;

        if (!isMultiplayer) {
            long playedTimeMillis = world.getWorldData().getPlayedTime();
            String playedTimeStr = formatPlayedTime(playedTimeMillis);
            font.draw(batch, "Total Time Played: " + playedTimeStr, 10, debugY);
        }
    }

    public void prepareForDisposal() {
        isDisposing = true;
        if (gameMenu != null) {
            gameMenu.dispose();
        }
    }

    private void handleInput() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            toggleGameMenu();
            return;
        }
        if (chatSystem.isActive()) {
            return;
        }

        if (!inputBlocked || starterSelectionComplete) {
            handleGameInput();
        }
    }

    private void handleGameInput() {
        if (inBattle) {
            return;
        }
        if (inputBlocked) {
            return;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.E)) {
            if (starterSelectionComplete) {
                toggleInventory();
            }
        }

        if (player.isBuildMode()) {
            handleBuildModeInput();
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.F3)) {
            SHOW_DEBUG_INFO = !SHOW_DEBUG_INFO;
        }

        if (inventoryOpen) {
            return;
        }

        handleMovementInput();
    }

    private void handleBuildModeInput() {
        for (int i = 0; i < 9; i++) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1 + i)) {
                buildModeUI.selectSlot(i);
                break;
            }
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.B)) {
            player.setBuildMode(!player.isBuildMode());
            if (player.isBuildMode()) {
                buildModeUI.show();
                if (inventoryOpen) {
                    toggleInventory();
                }
            } else {
                buildModeUI.hide();
            }
        }
    }

    private void handleMovementInput() {
        if (Gdx.input.isKeyPressed(Input.Keys.ANY_KEY)) {
            String direction = null;
            if (Gdx.input.isKeyPressed(Input.Keys.UP)) direction = "up";
            else if (Gdx.input.isKeyPressed(Input.Keys.DOWN)) direction = "down";
            else if (Gdx.input.isKeyPressed(Input.Keys.LEFT)) direction = "left";
            else if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) direction = "right";

            if (direction != null) {
                player.move(direction);
            }
        }
    }

    private TextButton createControlButton(String text) {
        TextButton button = new TextButton(text, skin);
        button.getColor().a = BUTTON_ALPHA;

        if (text.equals("INV")) {
            button.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    toggleInventory();
                }
            });
        } else if (text.equals("MENU")) {
            button.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    toggleGameMenu();
                }
            });
        } else if (text.equals("X")) {
            button.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    handleInteractionButton();
                }
            });
        }

        return button;
    }

    private void updateJoystickPosition() {
        if (!controlsInitialized) return;

        // Get the base position in stage coordinates
        Vector2 basePos = joystickBase.localToStageCoordinates(new Vector2());
        joystickOrigin.set(
            basePos.x + JOYSTICK_SIZE / 2f,
            basePos.y + JOYSTICK_SIZE / 2f
        );

        // Center the knob
        joystickKnob.setPosition(
            joystickOrigin.x - KNOB_SIZE / 2f,
            joystickOrigin.y - KNOB_SIZE / 2f
        );
    }

    private void handleInteractionButton() {
        // Same logic as pressing X key
        if (player != null) {
            WildPokemon nearestPokemon = world.getNearestInteractablePokemon(player);
            if (nearestPokemon != null) {
                handleBattleInitiation();
                return;
            }

            WorldObject nearestPokeball = world.getNearestPokeball();
            if (nearestPokeball != null && player.canPickupItem(nearestPokeball.getPixelX(), nearestPokeball.getPixelY())) {
                handlePickupAction();
            }
        }
    }

    // Add this helper method to clean up the render method

    private void renderButtonLabels() {
        if (batch == null || font == null || inventoryButton == null || menuButton == null) {
            GameLogger.error("Required resources for button labels not initialized");
            return;
        }

        try {
            batch.begin();
            float labelScale = 1.5f;
            font.getData().setScale(labelScale);
            font.setColor(1, 1, 1, 0.9f);
            GlyphLayout invLayout = new GlyphLayout(font, "INV");
            font.draw(batch, "INV",
                inventoryButton.x + (inventoryButton.width - invLayout.width) / 2,
                inventoryButton.y + (inventoryButton.height + invLayout.height) / 2);

            GlyphLayout menuLayout = new GlyphLayout(font, "MENU");
            font.draw(batch, "MENU",
                menuButton.x + (menuButton.width - menuLayout.width) / 2,
                menuButton.y + (menuButton.height + menuLayout.height) / 2);

            font.getData().setScale(1.0f);
        } finally {
            batch.end();
        }
    }

    private void renderButton(ShapeRenderer renderer, Rectangle button) {
        if (button != null) {
            renderer.rect(button.x, button.y, button.width, button.height);
        }
    }

    private int getTotalPokemonCount() {
        if (world != null && world.getPokemonSpawnManager() != null) {
            return world.getPokemonSpawnManager().getAllWildPokemon().size();
        }
        return 0;
    }

    private void drawTriangle(float x, float y, float size, float rotation) {
        float halfSize = size / 2;
        float[] vertices = new float[6];

        // Calculate triangle points based on rotation
        float rad = rotation * MathUtils.degreesToRadians;
        float cos = MathUtils.cos(rad);
        float sin = MathUtils.sin(rad);

        vertices[0] = x + (-halfSize * cos - (-halfSize) * sin);
        vertices[1] = y + (-halfSize * sin + (-halfSize) * cos);
        vertices[2] = x + (halfSize * cos - (-halfSize) * sin);
        vertices[3] = y + (halfSize * sin + (-halfSize) * cos);
        vertices[4] = x + (0 * cos - halfSize * sin);
        vertices[5] = y + (0 * sin + halfSize * cos);

        shapeRenderer.triangle(
            vertices[0], vertices[1],
            vertices[2], vertices[3],
            vertices[4], vertices[5]
        );
    }

    private void setupInventoryUI() {
        inventoryContainer = new Table();
        inventoryContainer.setFillParent(true);
        closeInventoryButton = new TextButton("X", skin);
        closeInventoryButton.setColor(Color.RED);

        float buttonSize = Gdx.graphics.getHeight() * 0.08f;

        float padding = Gdx.graphics.getHeight() * 0.02f;
        closeInventoryButton.setSize(buttonSize, buttonSize);
        closeInventoryButton.setPosition(
            Gdx.graphics.getWidth() - buttonSize - padding,
            Gdx.graphics.getHeight() - buttonSize - padding
        );

        closeInventoryButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                closeInventory();
            }
        });
    }

    // Update inventory handling
    // Update the inventory visibility check to ensure proper initialization
    private void toggleInventory() {
        if (inventoryOpen) {
            closeInventory();
        } else {
            // Debug current state before opening
            logInventoryState("Pre-inventory open state:");

            // Create new screen if needed and force initialization
            if (inventoryScreen == null || !inventoryScreen.isInitialized()) {
                inventoryScreen = new InventoryScreen(
                    player,
                    skin,
                    gameClient,
                    inputHandler,
                    player.getInventory()
                );
                inventoryScreen.initialize(); // Add initialization method
            }

            // Force refresh inventory data
            inventoryScreen.reloadInventory();
            inventoryScreen.show();
            inventoryOpen = true;

            setupInputProcessors();

            // Verify screen state after opening
            logInventoryState("Post-inventory open state:");
        }
    }

    private void closeInventory() {
        if (inventoryScreen != null) {
            inventoryScreen.hide();
            inventoryOpen = false;

            // Update input processors
            setupInputProcessors();
        }
    }

    private void logInventoryState(String context) {
        if (player == null || player.getInventory() == null) {
            GameLogger.error(context + ": Player or inventory is null");
            return;
        }

        GameLogger.info("\n=== Inventory State: " + context + " ===");
        List<ItemData> items = player.getInventory().getAllItems();
        GameLogger.info("Total slots: " + items.size());
        GameLogger.info("Non-null items: " + items.stream().filter(Objects::nonNull).count());

        for (int i = 0; i < items.size(); i++) {
            ItemData item = items.get(i);
            if (item != null) {
                GameLogger.info(String.format("Slot %d: %s (x%d) UUID: %s",
                    i, item.getItemId(), item.getCount(), item.getUuid()));
            }
        }
        GameLogger.info("=====================================\n");
    }

    private void debugInputState() {
        InputProcessor current = Gdx.input.getInputProcessor();
        GameLogger.info("Current input processor: " + (current == null ? "null" : current.getClass().getName()));
        if (current instanceof InputMultiplexer) {
            InputMultiplexer multiplexer = (InputMultiplexer) current;
            for (int i = 0; i < multiplexer.size(); i++) {
                GameLogger.info("Processor " + i + ": " + multiplexer.getProcessors().get(i).getClass().getName());
            }
        }
    }

    @Override
    public void resize(int width, int height) {
        if (player != null) {
            player.validateResources();
        }
        if (androidControls != null) {
            androidControls.invalidateHierarchy();
        }

        BUTTON_SIZE = Math.min(height * 0.1f, MAX_BUTTON_SIZE);
        ACTION_BUTTON_SIZE = BUTTON_SIZE / 2f; // Half the size of BUTTON_SIZE
        DPAD_BUTTON_SIZE = BUTTON_SIZE * 0.8f;
        DPAD_SIZE = DPAD_BUTTON_SIZE * 3;
        BUTTON_PADDING = Math.min(width, height) * 0.02f;

        // Recreate D-pad and buttons with new sizes
        if (dpadTable != null) {
            dpadTable.clear();
            createDpad();
        }


        // Update camera viewport
        cameraViewport.update(width, height, false);

        // Add this for inventory handling
        if (inventoryScreen != null) {
            inventoryScreen.resize(width, height);

            // Reposition close button if it exists
            if (closeButtonTable != null && closeButtonTable.getParent() != null) {
                closeButtonTable.invalidate();
            }
        }  // Find and resize the StarterSelectionTable if it exists
        for (Actor actor : stage.getActors()) {
            if (actor instanceof StarterSelectionTable) {
                ((StarterSelectionTable) actor).resize(width, height);
                starterTable.resize(width, height);
                break;
            }
        }
        if (uiStage != null) {
            uiStage.getViewport().update(width, height, true);
            GameLogger.info("Stage viewport updated to: " + width + "x" + height);

            // If in starter selection, ensure table is still properly positioned
            if (starterTable != null && player.getPokemonParty().getSize() == 0) {
                starterTable.setFillParent(true);
                // Log new position
                GameLogger.info("Starter table position after resize: " +
                    starterTable.getX() + "," + starterTable.getY());
            }
        }
        if (battleStage != null) {
            battleStage.getViewport().update(width, height, true);
            if (battleTable != null) {
                // This will trigger sizeChanged() in BattleTable
                battleTable.invalidate();
                battleTable.validate();
            }
        }
        if (uiStage != null) {
            uiStage.getViewport().update(width, height, true);
        }
        if (gameMenu != null && gameMenu.getStage() != null) {
            gameMenu.getStage().getViewport().update(width, height, true);
        }
        if (pokemonPartyStage != null) {
            pokemonPartyStage.getViewport().update(width, height, true);
        }
        if (chatSystem != null) {
            float chatWidth = Math.max(ChatSystem.MIN_CHAT_WIDTH, width * 0.25f);
            float chatHeight = Math.max(ChatSystem.MIN_CHAT_HEIGHT, height * 0.3f);

            chatSystem.setSize(chatWidth, chatHeight);
            chatSystem.setPosition(
                ChatSystem.CHAT_PADDING,
                height - chatHeight - ChatSystem.CHAT_PADDING
            );
            chatSystem.resize(width, height);
        }
        if (controlsInitialized) {
            joystickCenter.set(width * 0.2f, height * 0.25f);
            joystickCurrent.set(joystickCenter);

            // Update UI positions
            if (androidControlsTable != null) {
                androidControlsTable.invalidateHierarchy();
            }
        }
        // Update Android controls
        ensureAndroidControlsInitialized();
        updateAndroidControlPositions();


        // Similarly, update action buttons if needed
        updateCamera();
        GameLogger.info("Screen resized to: " + width + "x" + height);
    }

    private ItemData generateRandomItemData() {
        List<String> itemNames = new ArrayList<>(ItemManager.getAllItemNames());
        if (itemNames.isEmpty()) {
            GameLogger.error("No items available in ItemManager to generate random item.");
            return null;
        }
        int index = MathUtils.random(itemNames.size() - 1);
        String itemName = itemNames.get(index);
        ItemData itemData = InventoryConverter.itemToItemData(ItemManager.getItem(itemName));
        if (itemData != null) {
            itemData.setCount(1);
            itemData.setUuid(UUID.randomUUID());
            return itemData;
        }
        GameLogger.error("Failed to retrieve ItemData for item: " + itemName);
        return null;
    }

    public void handlePickupAction() {
        WorldObject nearestPokeball = world.getNearestPokeball();
        if (nearestPokeball == null) {
            GameLogger.info("No pokeball found nearby");
            return;
        }
        GameLogger.info("Player position: " + player.getX() + "," + player.getY());
        GameLogger.info("Pokeball position: " + nearestPokeball.getPixelX() + "," + nearestPokeball.getPixelY());

        if (player.canPickupItem(nearestPokeball.getPixelX(), nearestPokeball.getPixelY())) {
            world.removeWorldObject(nearestPokeball);
            ItemData randomItemData = generateRandomItemData();
            if (randomItemData == null) {
                GameLogger.error("Failed to generate random item data.");
                return;
            }
            boolean added = InventoryConverter.addItemToInventory(inventory, randomItemData);
            NetworkProtocol.ChatMessage pickupMessage = new NetworkProtocol.ChatMessage();
            pickupMessage.sender = "System";
            pickupMessage.timestamp = System.currentTimeMillis();

            if (added) {
                pickupMessage.content = "You found: " + randomItemData.getItemId() + " (×" + randomItemData.getCount() + ")";
                pickupMessage.type = NetworkProtocol.ChatType.SYSTEM;
                GameLogger.info("Item added to inventory: " + randomItemData.getItemId());
            } else {
                pickupMessage.content = "Inventory full! Couldn't pick up: " + randomItemData.getItemId();
                pickupMessage.type = NetworkProtocol.ChatType.SYSTEM;
                GameLogger.info("Inventory full. Cannot add: " + randomItemData.getItemId());
            }
            chatSystem.handleIncomingMessage(pickupMessage);


            AudioManager.getInstance().playSound(AudioManager.SoundEffect.ITEM_PICKUP);
            player.updatePlayerData();
        } else {
            GameLogger.info("Cannot pick up pokeball - too far or wrong direction");
        }
    }

    private void updatePartyDisplay() {
        partyDisplay.clearChildren();
        createPartyDisplay();
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    public SpriteBatch getBatch() {
        return batch;
    }

    @Override
    public void dispose() {
        isDisposing = true;
        cleanup();
        if (gameMenu != null) {
            gameMenu.dispose();
            gameMenu = null;
        }
        if (!gameClient.isSinglePlayer()) {
            gameClient.clearCredentials();
            PlayerData finalState = getCurrentPlayerState();
            currentWorld.savePlayerData(player.getUsername(), finalState);
        }
        if (pokemonPartyStage != null) {
            pokemonPartyStage.dispose();
        }
        if (currentWorld != null) {
            currentWorld.savePlayerData(username, playerData);
        }

        try {
            if (player != null) {
                try {
                    PlayerData finalState = new PlayerData(player.getUsername());
                    finalState.updateFromPlayer(player); // Ensure all data is captured

                    WorldData worldData = world.getWorldData();
                    worldData.savePlayerData(player.getUsername(), finalState);

                    game.getWorldManager().saveWorld(worldData);

                    GameLogger.info("Player state saved successfully");
                } catch (Exception e) {
                    GameLogger.error("Error saving final state: " + e.getMessage());
                    e.printStackTrace();
                }
            }

        } catch (Exception e) {
            GameLogger.info("Error saving final state: " + e.getMessage());
            e.printStackTrace();
        }
        gameClient.dispose();
        assert player != null;
        player.dispose();
        for (OtherPlayer op : gameClient.getOtherPlayers().values()) {
            op.dispose();
        }
        disposeResources();
        ensureSaveDirectories();

    }

    private void disposeResources() {
        if (player != null) player.dispose();
        if (gameClient != null) {
            for (OtherPlayer op : gameClient.getOtherPlayers().values()) {
                if (op != null) op.dispose();
            }
        }
        if (gameClient != null) gameClient.dispose();
    }

    private void initializeAndroidControls() {
        if (Gdx.app.getType() != Application.ApplicationType.Android || controlsInitialized) {
            return;
        }

        try {
            // Main container
            androidControlsTable = new Table();
            androidControlsTable.setFillParent(true);

            // Create D-pad
            createDpad();

            // Create action buttons
            createActionButtons();

            // Add to stage
            uiStage.addActor(androidControlsTable);

            // Initialize hit boxes
            createDpadHitboxes();

            controlsInitialized = true;
            GameLogger.info("Android controls initialized");

        } catch (Exception e) {
            GameLogger.error("Failed to initialize Android controls: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private TextButton createActionButton(String label) {
        // Create a drawable for the button background
        Pixmap pixmap = new Pixmap((int) BUTTON_SIZE, (int) BUTTON_SIZE, Pixmap.Format.RGBA8888);
        pixmap.setColor(0.8f, 0.8f, 0.8f, 0.6f); // Semi-transparent light gray
        pixmap.fillCircle((int) BUTTON_SIZE / 2, (int) BUTTON_SIZE / 2, (int) BUTTON_SIZE / 2);
        TextureRegionDrawable drawable = new TextureRegionDrawable(new Texture(pixmap));
        pixmap.dispose();

        // Create button style
        TextButton.TextButtonStyle style = new TextButton.TextButtonStyle();
        style.up = drawable;
        style.down = drawable.tint(Color.DARK_GRAY);
        style.font = skin.getFont("default-font");
        style.fontColor = Color.BLACK;

        // Create the button
        TextButton button = new TextButton(label, style);

        // Adjust font size based on button size
        float fontScale = BUTTON_SIZE / 50f; // Adjust as needed
        button.getLabel().setFontScale(fontScale);

        // Center the label
        button.getLabel().setAlignment(Align.center);

        // Set button size
        button.setSize(BUTTON_SIZE, BUTTON_SIZE);

        return button;
    }
    private TextButton createColoredButton(String label, Color color, float size) {
        // Create a drawable for the button background
        Pixmap pixmap = new Pixmap((int) size, (int) size, Pixmap.Format.RGBA8888);
        pixmap.setColor(color.r, color.g, color.b, 0.8f); // Semi-transparent color
        pixmap.fillCircle((int) size / 2, (int) size / 2, (int) size / 2);
        TextureRegionDrawable drawable = new TextureRegionDrawable(new Texture(pixmap));
        pixmap.dispose();

        // Create button style
        TextButton.TextButtonStyle style = new TextButton.TextButtonStyle();
        style.up = drawable;
        style.down = drawable.tint(Color.DARK_GRAY);
        style.font = skin.getFont("default-font");
        style.fontColor = Color.WHITE;

        // Create the button
        TextButton button = new TextButton(label, style);

        // Adjust font size based on button size
        float fontScale = size / 60f; // Adjust as needed
        button.getLabel().setFontScale(fontScale);

        // Center the label
        button.getLabel().setAlignment(Align.center);

        // Set button size
        button.setSize(size, size);

        return button;
    }
    private void createActionButtons() {
        // Create buttons with specified colors and adjusted sizes
        aButton = createColoredButton("A", Color.GREEN, ACTION_BUTTON_SIZE);
        bButton = createColoredButton("B", Color.RED, ACTION_BUTTON_SIZE);
        xButton = createColoredButton("X", Color.BLUE, ACTION_BUTTON_SIZE);
        yButton = createColoredButton("Y", Color.YELLOW, ACTION_BUTTON_SIZE);
        startButton = createColoredButton("Start", Color.GRAY, ACTION_BUTTON_SIZE * 0.8f);
        selectButton = createColoredButton("Select", Color.GRAY, ACTION_BUTTON_SIZE * 0.8f);

        // Set touchable
        aButton.setTouchable(Touchable.enabled);
        bButton.setTouchable(Touchable.enabled);
        xButton.setTouchable(Touchable.enabled);
        yButton.setTouchable(Touchable.enabled);
        startButton.setTouchable(Touchable.enabled);
        selectButton.setTouchable(Touchable.enabled);

        // Position action buttons in the bottom right corner
        Table actionButtonTable = new Table();
        actionButtonTable.setFillParent(true);
        actionButtonTable.bottom().right().pad(BUTTON_PADDING * 2);

        // Arrange buttons in a compact grid
        actionButtonTable.add(startButton).size(ACTION_BUTTON_SIZE * 0.8f).pad(5).row();
        actionButtonTable.add(xButton).size(ACTION_BUTTON_SIZE).pad(5);
        actionButtonTable.add(yButton).size(ACTION_BUTTON_SIZE).pad(5);
        actionButtonTable.add(selectButton).size(ACTION_BUTTON_SIZE * 0.8f).pad(5).row();
        actionButtonTable.add(bButton).size(ACTION_BUTTON_SIZE).pad(5);
        actionButtonTable.add(aButton).size(ACTION_BUTTON_SIZE).pad(5);

        uiStage.addActor(actionButtonTable);

        // Add listeners to buttons
        addButtonListeners();
    }



    private void addButtonListeners() {
        // "A" Button Listener
        aButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                handleAButtonPress();
            }
        });

        // "B" Button Listener
        bButton.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                handleBButtonPress(true);
                return true;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                handleBButtonPress(false);
            }
        });

        // "X" Button Listener
        xButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                handleXButtonPress();
            }
        });

        // "Y" Button Listener
        yButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                handleYButtonPress();
            }
        });

        // "Start" Button Listener
        startButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                handleStartButtonPress();
            }
        });

        // "Select" Button Listener
        selectButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                handleSelectButtonPress();
            }
        });
    }
    private void handleAButtonPress() {
        // Implement interaction logic (e.g., interact with objects)
        handleInteractionButton();
    }

    private void handleBButtonPress(boolean isPressed) {
        // Implement running logic
        if (player != null) {
            player.setRunning(isPressed);
        }
    }

    private void handleXButtonPress() {
        // Implement action for 'X' button (e.g., open game menu)
        toggleGameMenu();
    }

    private void handleYButtonPress() {
        // Implement action for 'Y' button (e.g., open inventory)
        toggleInventory();
    }

    private void handleStartButtonPress() {
        // Implement action for 'Start' button (e.g., pause the game)
        toggleGameMenu();
    }

    private void handleSelectButtonPress() {

        SHOW_DEBUG_INFO=!SHOW_DEBUG_INFO;
    }

    private TextButton createInventoryButton() {
        TextButton button = new TextButton("INV", skin);
        button.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                toggleInventory();
            }
        });
        styleButton(button, Color.ORANGE);
        return button;
    }

    private TextButton createMenuButton() {
        TextButton button = new TextButton("MENU", skin);
        button.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                toggleGameMenu();
            }
        });
        styleButton(button, Color.GRAY);
        return button;
    }

    private TextButton createChatButton() {
        TextButton button = new TextButton("CHAT", skin);
        button.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                showChatDialog();
            }
        });
        styleButton(button, new Color(0.4f, 0.7f, 1f, 1f)); // Light blue
        return button;
    }

    private TextButton createDebugButton() {
        TextButton button = new TextButton("DEBUG", skin);
        button.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                SHOW_DEBUG_INFO = !SHOW_DEBUG_INFO;
                button.setColor(SHOW_DEBUG_INFO ? Color.GREEN : Color.GRAY);
            }
        });
        styleButton(button, Color.GRAY);
        return button;
    }

    private TextButton createRunButton() {
        TextButton button = new TextButton("RUN", skin);
        button.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                if (player != null) {
                    isRunPressed = true;
                    player.setRunning(true);
                }
                return true;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                if (player != null) {
                    isRunPressed = false;
                    player.setRunning(false);
                }
            }
        });
        styleButton(button, new Color(0.2f, 0.7f, 0.2f, 1f)); // Green
        return button;
    }

    private void showChatDialog() {
        if (chatSystem != null && !chatSystem.isActive()) {
            final Dialog dialog = new Dialog("Chat", skin) {
                @Override
                protected void result(Object obj) {
                    if (obj instanceof Boolean && (Boolean) obj) {
                        TextField chatInput = findActor("chatInput");
                        if (chatInput != null) {
                            String message = chatInput.getText().trim();
                            if (!message.isEmpty()) {
                                chatSystem.sendMessage(message);
                            }
                        }
                    }
                    Gdx.input.setOnscreenKeyboardVisible(false);
                }
            };

            // Create content table with padding
            Table contentTable = new Table();
            contentTable.pad(20);

            // Create and style text field
            final TextField chatInput = new TextField("", skin);
            chatInput.setName("chatInput"); // Important for finding it later

            // Style the text field
            TextField.TextFieldStyle style = new TextField.TextFieldStyle(chatInput.getStyle());
            style.background = skin.newDrawable("white", new Color(0.2f, 0.2f, 0.2f, 0.8f));
            style.fontColor = Color.WHITE;
            chatInput.setStyle(style);

            // Add text field to content
            contentTable.add(chatInput).width(400f).padBottom(20f);
            dialog.getContentTable().add(contentTable);

            // Add buttons with boolean values for result handling
            dialog.button("Send", true);
            dialog.button("Cancel", false);

            // Add enter key handler
            chatInput.addListener(new InputListener() {
                @Override
                public boolean keyDown(InputEvent event, int keycode) {
                    if (keycode == Input.Keys.ENTER) {
                        String message = chatInput.getText().trim();
                        if (!message.isEmpty()) {
                            chatSystem.sendMessage(message);
                            dialog.hide();
                        }
                        return true;
                    }
                    return false;
                }
            });

            // Text filter for valid characters
            chatInput.setTextFieldFilter(new TextField.TextFieldFilter() {
                @Override
                public boolean acceptChar(TextField textField, char c) {
                    return c >= 32; // Accept printable characters
                }
            });

            // Show dialog and focus
            dialog.show(uiStage);

            // Focus and show keyboard with slight delay to ensure proper focus
            com.badlogic.gdx.utils.Timer.schedule(new Timer.Task() {
                @Override
                public void run() {
                    uiStage.setKeyboardFocus(chatInput);
                    Gdx.input.setOnscreenKeyboardVisible(true);
                }
            }, 0.1f);
        }
    }

    private void createDpad() {
        float screenWidth = Gdx.graphics.getWidth();
        float screenHeight = Gdx.graphics.getHeight();

        // Constants for D-pad positioning and size
        float dpadSize = screenHeight * 0.3f; // Adjust size for ergonomics
        float paddingLeft = screenWidth * 0.05f; // Left padding
        float paddingBottom = screenHeight * 0.05f; // Bottom padding

        // Create D-pad touch area
        Image dpadTouchArea = new Image();
        dpadTouchArea.setSize(dpadSize, dpadSize);
        dpadTouchArea.setPosition(paddingLeft, paddingBottom);
        dpadTouchArea.setColor(1, 1, 1, 0); // Fully transparent
        dpadTouchArea.setTouchable(Touchable.enabled);

        // Add touch listener to the D-pad area
        dpadTouchArea.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                float absoluteX = event.getStageX();
                float absoluteY = event.getStageY();
                movementController.handleTouchDown(absoluteX, absoluteY);
                return true;
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer) {
                float absoluteX = event.getStageX();
                float absoluteY = event.getStageY();
                movementController.handleTouchDragged(absoluteX, absoluteY);
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                movementController.handleTouchUp();
            }
        });

        uiStage.addActor(dpadTouchArea);
    }

    private ImageButton createDpadButton(String direction) {
        // Create a drawable for the button background
        Pixmap pixmap = new Pixmap((int) DPAD_BUTTON_SIZE, (int) DPAD_BUTTON_SIZE, Pixmap.Format.RGBA8888);
        pixmap.setColor(0.7f, 0.7f, 0.7f, 0.5f); // Semi-transparent gray
        pixmap.fillCircle((int) DPAD_BUTTON_SIZE / 2, (int) DPAD_BUTTON_SIZE / 2, (int) DPAD_BUTTON_SIZE / 2);
        TextureRegionDrawable drawable = new TextureRegionDrawable(new Texture(pixmap));
        pixmap.dispose();

        // Create arrow image for the direction
        Image arrowImage = createArrowImage(direction);

        // Create button style
        ImageButton.ImageButtonStyle style = new ImageButton.ImageButtonStyle();
        style.up = drawable;
        style.imageUp = arrowImage.getDrawable();

        // Create the button
        ImageButton button = new ImageButton(style);

        return button;
    }

    private Image createArrowImage(String direction) {
        // Create a Pixmap for the arrow
        int size = (int) (DPAD_BUTTON_SIZE * 0.6f);
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.BLACK);

        // Draw an arrow pointing in the given direction
        pixmap.fillTriangle(
            size / 2, 0,
            0, size,
            size, size
        );

        Texture arrowTexture = new Texture(pixmap);
        pixmap.dispose();

        // Rotate the image based on the direction
        Image arrowImage = new Image(new TextureRegion(arrowTexture));
        switch (direction) {
            case "up":
                arrowImage.setRotation(0);
                break;
            case "down":
                arrowImage.setRotation(180);
                break;
            case "left":
                arrowImage.setRotation(270);
                break;
            case "right":
                arrowImage.setRotation(90);
                break;
        }

        return arrowImage;
    }




    private void createDpadHitboxes() {
        float screenWidth = Gdx.graphics.getWidth();
        float screenHeight = Gdx.graphics.getHeight();

        // Calculate D-pad position (bottom left area)
        float dpadCenterX = screenWidth * 0.15f;
        float dpadCenterY = screenHeight * 0.2f;
        float buttonSize = DPAD_BUTTON_SIZE; // Slightly larger hitboxes

        // Create hitboxes with proper spacing and overlap
        upButton = new Rectangle(
            dpadCenterX - buttonSize / 2,
            dpadCenterY + buttonSize / 4,
            buttonSize,
            buttonSize
        );

        downButton = new Rectangle(
            dpadCenterX - buttonSize / 2,
            dpadCenterY - buttonSize - buttonSize / 4,
            buttonSize,
            buttonSize
        );

        leftButton = new Rectangle(
            dpadCenterX - buttonSize - buttonSize / 4,
            dpadCenterY - buttonSize / 2,
            buttonSize,
            buttonSize
        );

        rightButton = new Rectangle(
            dpadCenterX + buttonSize / 4,
            dpadCenterY - buttonSize / 2,
            buttonSize,
            buttonSize
        );

        // Center/running button
        centerButton = new Rectangle(
            dpadCenterX - buttonSize / 2,
            dpadCenterY - buttonSize / 2,
            buttonSize,
            buttonSize
        );
    }


    private void styleButton(TextButton button, float r, float g, float b) {
        // Create a new style based on the existing one
        TextButton.TextButtonStyle style = new TextButton.TextButtonStyle(button.getStyle());

        // Create background drawable
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(r, g, b, 0.8f);
        pixmap.fill();
        Texture texture = new Texture(pixmap);
        pixmap.dispose();

        style.up = new TextureRegionDrawable(new TextureRegion(texture));

        // Increase text size and set color
        style.font.getData().setScale(1.2f);
        style.fontColor = Color.WHITE;

        button.setStyle(style);

        // Add press effect
        button.addAction(Actions.alpha(0.85f));
        button.addListener(new ClickListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                event.getTarget().addAction(Actions.alpha(0.7f));
                return super.touchDown(event, x, y, pointer, button);
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                event.getTarget().addAction(Actions.alpha(0.85f));
                super.touchUp(event, x, y, pointer, button);
            }
        });
    }

    private void styleButton(TextButton button, Color baseColor) {
        TextButton.TextButtonStyle style = new TextButton.TextButtonStyle(button.getStyle());

        // Create background
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(baseColor.r, baseColor.g, baseColor.b, 0.8f);
        pixmap.fill();
        Texture texture = new Texture(pixmap);
        pixmap.dispose();

        // Set up style
        style.up = new TextureRegionDrawable(new TextureRegion(texture));
        style.font = skin.getFont("default-font");
        style.font.getData().setScale(1.2f);
        style.fontColor = Color.WHITE;

        button.setStyle(style);

        // Add visual feedback for pressing
        button.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                event.getTarget().setColor(1, 1, 1, 0.6f);
                return true;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                event.getTarget().setColor(1, 1, 1, 1f);
            }
        });
    }

    private TextureRegionDrawable createBackgroundDrawable(float r, float g, float b, float a) {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(r, g, b, a);
        pixmap.fill();
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return new TextureRegionDrawable(new TextureRegion(texture));
    }


// In your initializeAndroidControls() method, add:

// In your GameScreen's update/render method, add:


    private void renderAndroidControls() {
        if (!controlsInitialized) return;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        // Draw only the base circle
        float centerX = upButton.x + upButton.width / 2;
        float centerY = leftButton.y + leftButton.height / 2;

        // Draw semi-transparent base circle
        shapeRenderer.setColor(0.2f, 0.2f, 0.2f, 0.3f);
        shapeRenderer.circle(centerX, centerY, DPAD_SIZE / 2);

        // Draw arrows only - no rectangles
        drawDpadArrow("up", upButton, centerX, centerY);
        drawDpadArrow("down", downButton, centerX, centerY);
        drawDpadArrow("left", leftButton, centerX, centerY);
        drawDpadArrow("right", rightButton, centerX, centerY);

        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private void drawDpadArrow(String direction, Rectangle bounds, float centerX, float centerY) {
        boolean isActive = currentDpadDirection != null && currentDpadDirection.equals(direction);
        float arrowSize = bounds.width * 0.3f; // Smaller arrows
        float midX = bounds.x + bounds.width / 2;
        float midY = bounds.y + bounds.height / 2;

        // Set arrow color based on state
        if (isActive) {
            shapeRenderer.setColor(1f, 1f, 1f, 0.8f); // Bright white when active
        } else {
            shapeRenderer.setColor(0.8f, 0.8f, 0.8f, 0.4f); // Dimmer when inactive
        }

        // Calculate arrow position based on direction
        float angle = 0;
        switch (direction) {
            case "up":
                angle = 0;
                break;
            case "down":
                angle = 180;
                break;
            case "left":
                angle = 270;
                break;
            case "right":
                angle = 90;
                break;
        }

        // Draw arrow triangle
        drawTriangle(midX, midY, arrowSize, angle);
    }

    private void drawDpadButton(Rectangle button, String direction, float centerX, float centerY) {
        boolean isActive = currentDpadDirection != null && currentDpadDirection.equals(direction);

        // Draw only the arrow
        float arrowSize = button.width * 0.4f; // Slightly larger arrows
        float midX = button.x + button.width / 2;
        float midY = button.y + button.height / 2;

        // Set color based on active state
        if (isActive) {
            shapeRenderer.setColor(0.8f, 0.8f, 0.8f, 0.8f); // Brighter when active
        } else {
            shapeRenderer.setColor(0.6f, 0.6f, 0.6f, 0.6f); // Dimmer when inactive
        }

        // Draw arrow based on direction
        switch (direction) {
            case "up":
                drawArrow(midX, midY, arrowSize, 0);
                break;
            case "down":
                drawArrow(midX, midY, arrowSize, 180);
                break;
            case "left":
                drawArrow(midX, midY, arrowSize, 270);
                break;
            case "right":
                drawArrow(midX, midY, arrowSize, 90);
                break;
        }
    }

    private void drawArrow(float x, float y, float size, float rotation) {
        float rad = rotation * MathUtils.degreesToRadians;
        float cos = MathUtils.cos(rad);
        float sin = MathUtils.sin(rad);

        // Triangle points for arrow
        float[] vertices = new float[6];
        float width = size * 0.5f;
        float height = size;

        // Calculate rotated points
        vertices[0] = x + (-width * cos - (-height / 2) * sin); // Left point
        vertices[1] = y + (-width * sin + (-height / 2) * cos);
        vertices[2] = x + (width * cos - (-height / 2) * sin);  // Right point
        vertices[3] = y + (width * sin + (-height / 2) * cos);
        vertices[4] = x + (0 * cos - (height / 2) * sin);       // Top point
        vertices[5] = y + (0 * sin + (height / 2) * cos);

        shapeRenderer.triangle(
            vertices[0], vertices[1],
            vertices[2], vertices[3],
            vertices[4], vertices[5]
        );
    }

    private boolean isDpadTouch(float x, float y) {
        // Calculate area around d-pad with some padding
        float padding = 40f; // Adjust this value for larger/smaller touch area
        return (upButton.contains(x, y) || downButton.contains(x, y) ||
            leftButton.contains(x, y) || rightButton.contains(x, y) ||
            isNearButton(x, y, upButton, padding) ||
            isNearButton(x, y, downButton, padding) ||
            isNearButton(x, y, leftButton, padding) ||
            isNearButton(x, y, rightButton, padding));
    }

    private boolean isNearButton(float x, float y, Rectangle button, float padding) {
        return x >= button.x - padding &&
            x <= button.x + button.width + padding &&
            y >= button.y - padding &&
            y <= button.y + button.height + padding;
    }


    public class AndroidInputProcessor extends InputAdapter {
        private final Vector2 touchPos = new Vector2();
        private final AndroidMovementController movementController;
        private final TextButton aButton;
        private final TextButton bButton;
        private final TextButton xButton;
        private final TextButton yButton;
        private final TextButton startButton;
        private int activePointer = -1;

        public AndroidInputProcessor(AndroidMovementController movementController,
                                     TextButton aButton, TextButton bButton,
                                     TextButton xButton, TextButton yButton,
                                     TextButton startButton) {
            this.movementController = movementController;
            this.aButton = aButton;
            this.bButton = bButton;
            this.xButton = xButton;
            this.yButton = yButton;
            this.startButton = startButton;// "A" Button Listener


// "B" Button Listener
            bButton.addListener(new InputListener() {
                @Override
                public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                    handleBButtonPress(true);
                    return true;
                }

                @Override
                public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                    handleBButtonPress(false);
                }
            });

// "Start" Button Listener
            startButton.addListener(new InputListener() {
                @Override
                public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                    handleStartButtonPress();
                    return true;
                }
            });

// "Select" Button Listener
            selectButton.addListener(new InputListener() {
                @Override
                public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                    handleSelectButtonPress();
                    return true;
                }
            });

        }

        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            float touchX = screenX;
            float touchY = Gdx.graphics.getHeight() - screenY; // Flip Y coordinate


            if (isTouchOnButton(touchX, touchY, xButton)) {
                handleXButtonPress();
                return true;
            }

            if (isTouchOnButton(touchX, touchY, yButton)) {
                handleYButtonPress();
                return true;
            }

            if (isTouchOnButton(touchX, touchY, startButton)) {
                handleStartButtonPress();
                return true;
            }

            // Existing D-pad handling
            if (isDpadTouch(touchX, touchY)) {
                movementController.handleTouchDown((int) touchX, (int) touchY);
                return true;
            }

            return false;
        }

        @Override
        public boolean touchDragged(int screenX, int screenY, int pointer) {
            float touchX = screenX;
            float touchY = Gdx.graphics.getHeight() - screenY;

            movementController.handleTouchDragged((int) touchX, (int) touchY);
            return true;
        }

        @Override
        public boolean touchUp(int screenX, int screenY, int pointer, int button) {
            float touchY = Gdx.graphics.getHeight() - screenY;

            if (isTouchOnButton((float) screenX, touchY, bButton)) {
                handleBButtonPress(false);
                return true;
            }

            movementController.handleTouchUp();
            return false;
        }

        private boolean isTouchOnButton(float x, float y, Actor button) {
            return x >= button.getX() && x <= button.getX() + button.getWidth()
                && y >= button.getY() && y <= button.getY() + button.getHeight();
        }



        private String getDpadDirection(float x, float y) {
            // Check each d-pad button with extended hit areas
            if (upButton.contains(x, y) || isNearButton(x, y, upButton)) return "up";
            if (downButton.contains(x, y) || isNearButton(x, y, downButton)) return "down";
            if (leftButton.contains(x, y) || isNearButton(x, y, leftButton)) return "left";
            if (rightButton.contains(x, y) || isNearButton(x, y, rightButton)) return "right";
            return null;
        }

        private boolean isNearButton(float x, float y, Rectangle button) {
            // Add some tolerance around buttons for better touch detection
            float tolerance = 20f;
            return x >= button.x - tolerance &&
                x <= button.x + button.width + tolerance &&
                y >= button.y - tolerance &&
                y <= button.y + button.height + tolerance;
        }
    }
}

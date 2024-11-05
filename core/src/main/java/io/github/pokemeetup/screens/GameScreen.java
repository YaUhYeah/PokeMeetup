package io.github.pokemeetup.screens;

import com.badlogic.gdx.*;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.pokemeetup.CreatureCaptureGame;
import io.github.pokemeetup.audio.AudioManager;
import io.github.pokemeetup.chat.ChatSystem;
import io.github.pokemeetup.managers.DatabaseManager;
import io.github.pokemeetup.multiplayer.OtherPlayer;
import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.multiplayer.server.ServerStorageSystem;
import io.github.pokemeetup.pokemon.Pokemon;
import io.github.pokemeetup.pokemon.PokemonParty;
import io.github.pokemeetup.pokemon.WildPokemon;
import io.github.pokemeetup.screens.otherui.BuildModeUI;
import io.github.pokemeetup.screens.otherui.PokemonPartyUI;
import io.github.pokemeetup.system.*;
import io.github.pokemeetup.system.battle.BattleInitiationHandler;
import io.github.pokemeetup.system.battle.BattleResult;
import io.github.pokemeetup.system.data.PlayerData;
import io.github.pokemeetup.system.gameplay.inventory.Inventory;
import io.github.pokemeetup.system.data.ItemData;
import io.github.pokemeetup.system.gameplay.inventory.ItemManager;
import io.github.pokemeetup.system.gameplay.overworld.*;
import io.github.pokemeetup.system.data.WorldData;
import io.github.pokemeetup.system.gameplay.overworld.biomes.Biome;
import io.github.pokemeetup.system.gameplay.overworld.multiworld.WorldManager;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.storage.InventoryConverter;
import io.github.pokemeetup.utils.TextureManager;

import java.util.List;
import java.util.*;

import static io.github.pokemeetup.system.Player.MAX_TILE_BOUND;
import static io.github.pokemeetup.system.Player.MIN_TILE_BOUND;
import static io.github.pokemeetup.system.gameplay.overworld.World.TILE_SIZE;

public class GameScreen implements Screen, PickupActionHandler, BattleInitiationHandler {
    public static final int WORLD_SIZE = 1600;
    public static final int WORLD_HEIGHT = 1600; // 50 chunks high
    private static final float TARGET_VIEWPORT_WIDTH_TILES = 24f; // Increased from 20
    private static final float VIEWPORT_PADDING = 3f; // Increased from 2
    private static final float DEFAULT_ZOOM = 1.1f; // Add this constant
    private static final float STATE_UPDATE_INTERVAL = 0.1f; // Update state every 0.1 seconds
    private static final float UPDATE_INTERVAL = 0.1f; // 10 times per second
    private static final int WORLD_WIDTH = 1600; // 50 chunks wide
    private static final float VIRTUAL_JOYSTICK_RADIUS = 100f;
    private static final float CAMERA_SMOOTH_SPEED = 5f;
    private static final float MIN_CAMERA_ZOOM = 0.5f;
    private static final float MAX_CAMERA_ZOOM = 2f;
    private static final float CAMERA_LERP = 5.0f; // Adjust this value to change follow speed
    // In World.java, update the camera tracking:
    private static final float CAMERA_UPDATE_INTERVAL = 0.05f;
    private static final float TRANSITION_DURATION = 0.5f;
    private static final float TRANSITION_DELAY = 0.2f; // Add delay before screen switch=
    public static boolean SHOW_DEBUG_INFO = false; // Toggle flag for debug info
    private final String worldName;
    private final CreatureCaptureGame game;
    private final ServerStorageSystem storageSystem;
    private final HashMap<String, OtherPlayer> otherPlayers = new HashMap<>();
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
    private GameClient gameClient;
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
    private Vector2 joystickCenter;
    private Vector2 joystickCurrent;
    private BuildModeUI buildModeUI;
    private boolean joystickActive;
    private Rectangle inventoryButton;
    private Rectangle menuButton;
    private Table fixedHotbarTable;
    private PlayerData playerData;
    private PokemonSpawnManager spawnManager;
    private ShapeRenderer shapeRenderer;
    private Skin skin;
    private Stage stage;
    private boolean showPokemonBounds = true; // Toggle as needed
    /**
     * Synchronizes the hotbar UI with the current inventory state.
     */
    private FitViewport cameraViewport; // Usi
    private float cameraUpdateTimer = 0;
    private boolean transitioning = false;
    private float transitionTimer = 0;
    // Add these fields to your GameScreen class if not already present
    private Table inventoryContainer;
    private TextButton closeInventoryButton;
    private WildPokemon transitioningPokemon = null; // Track the battling Pokemon
    private boolean transitionOut = true; // true = fade out, false = fade in

    public GameScreen(CreatureCaptureGame game, String username, GameClient gameClient, String worldName) {
        GameLogger.info("Starting GameScreen initialization...");

        // Initialize essential fields first
        this.game = game;
        this.worldName = worldName;
        this.username = username;
        this.gameClient = gameClient;
        this.player = game.getPlayer();
        this.world = game.getCurrentWorld();

        this.isMultiplayer = !gameClient.isSinglePlayer();
        this.storageSystem = new ServerStorageSystem();

        this.worldManager = WorldManager.getInstance(storageSystem, isMultiplayer);
        worldManager.init();

        // Initialize basic UI and rendering components
        this.skin = new Skin(Gdx.files.internal("Skins/uiskin.json"));
        this.uiSkin = this.skin;
        this.batch = new SpriteBatch();
        this.shapeRenderer = new ShapeRenderer();
        this.uiStage = new Stage(new ScreenViewport());
        this.pokemonPartyStage = new Stage(new ScreenViewport());
        this.stage = new Stage(new ScreenViewport());
        this.font = new BitmapFont(Gdx.files.internal("Skins/default.fnt"));
        setupCamera();
        initializeChatSystem();
        // Initialize inputMultiplexer and inputHandler BEFORE any early returns
        this.inputMultiplexer = new InputMultiplexer();
        this.inputHandler = new InputHandler(player, this, this);

        // Initialize game menu
        this.gameMenu = new GameMenu(this, game, uiSkin, player, gameClient);

        GameLogger.info("Checking player's Pokemon party size...");
        if (player.getPokemonParty() == null) {
            GameLogger.error("Player's Pokemon party is null!");
            return;
        }

        // Check if player needs a starter BEFORE doing any other initialization
        if (player.getPokemonParty().getSize() == 0) {
            GameLogger.info("Player has no Pokemon - transitioning to starter selection screen");
            Gdx.app.postRunnable(() -> {
                StarterSelectionScreen starterScreen = new StarterSelectionScreen(game, this.skin);
                starterScreen.setReturnScreen(this); // Add method to return to this screen
                game.setScreen(starterScreen);
            });
            return; // Early return, but essential components are initialized
        }

        // Continue with full initialization if player has Pokemon
        GameLogger.info("Player has Pokemon - continuing with full initialization");

        this.pokemonParty = new PokemonParty();

        // Initialize basic systems
        gameClient.setLocalUsername(username);

        // Set up full initialization
        GameLogger.info("Starting complete initialization...");
        completeInitialization();

        // Set up input processors last
        GameLogger.info("Setting up input processors...");
        setupInputProcessors();
        if (Gdx.app.getType() == Application.ApplicationType.Android) {
            initializeAndroidControls();
        }
    }

    private void initializeChatSystem() {
        if (chatSystem != null) {
            GameLogger.info("ChatSystem is already initialized.");
            return; // Already initialized
        }

        GameLogger.info("Initializing ChatSystem.");
        float screenWidth = Gdx.graphics.getWidth();
        float screenHeight = Gdx.graphics.getHeight();
        float chatWidth = Math.max(ChatSystem.MIN_CHAT_WIDTH, screenWidth * 0.25f);
        float chatHeight = Math.max(ChatSystem.MIN_CHAT_HEIGHT, screenHeight * 0.3f);

        chatSystem = new ChatSystem(uiStage, uiSkin, gameClient, username);
        chatSystem.setSize(chatWidth, chatHeight);
        chatSystem.setPosition(
            ChatSystem.CHAT_PADDING,
            screenHeight - chatHeight - ChatSystem.CHAT_PADDING
        );
    }


    private void onBattleComplete() {
        // Return to GameScreen
        game.setScreenWithoutDisposing(this);
        // Re-setup input processors and other necessary components
        setupInputProcessors();
    }

    private void onBattleComplete(BattleResult result) {
        if (result.isVictory()) {
            // Remove defeated Pokemon from world
            world.getPokemonSpawnManager().despawnPokemon(result.getWildPokemon().getUuid());

            // Show victory message in chat
            NetworkProtocol.ChatMessage message = new NetworkProtocol.ChatMessage();
            message.sender = "System";
            message.content = "Defeated wild " + result.getWildPokemon().getName() + "!";
            message.timestamp = System.currentTimeMillis();
            message.type = NetworkProtocol.ChatType.SYSTEM;
            chatSystem.handleIncomingMessage(message);

            // Update player Pokemon stats
            Pokemon playerPokemon = result.getPlayerPokemon();

            // Play victory sound
//            AudioManager.getInstance().playSound(AudioManager.SoundEffect.VICTORY);
        } else {
            // Handle defeat
            NetworkProtocol.ChatMessage message = new NetworkProtocol.ChatMessage();
            message.sender = "System";
            message.content = "Your " + result.getPlayerPokemon().getName() + " fainted!";
            message.timestamp = System.currentTimeMillis();
            message.type = NetworkProtocol.ChatType.SYSTEM;
            chatSystem.handleIncomingMessage(message);

            // Heal Pokemon (as if visited Pokemon Center)
//            player.getPokemonParty().healAll();

            // Play defeat sound
//            AudioManager.getInstance().playSound(AudioManager.SoundEffect.DEFEAT);
        }

        // Return to game screen
        game.setScreen(this);

        // Reset input processor
        resetInputProcessor();

        // Save game state after battle
        if (!gameClient.isSinglePlayer()) {
//            gameClient.savePlayerState(player);
        }
    }

    private void setupBasicInputProcessors() {
        inputMultiplexer.clear();

        // Add menu input processor first
        inputMultiplexer.addProcessor(new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.ESCAPE) {
                    if (gameMenu.isVisible()) {
                        gameMenu.hide();
                    } else {
                        gameMenu.show();
                    }
                    return true;
                }
                return false;
            }
        });

        // Add stages
        inputMultiplexer.addProcessor(gameMenu.getStage());
        inputMultiplexer.addProcessor(uiStage);
        inputMultiplexer.addProcessor(inputHandler);

        Gdx.input.setInputProcessor(inputMultiplexer);
    }

    void completeInitialization() {
        // Initialize world management
        // Initialize ItemManager if needed
        if (!ItemManager.isInitialized()) {
            GameLogger.info("Initializing ItemManager");
            ItemManager.initialize(TextureManager.items);
        }

        // Load world data
        WorldData worldData = world.getWorldData();
        this.currentWorld = worldData;
        if (worldData != null) {
            PlayerData playerData = worldData.getPlayerData(username);
            if (playerData != null) {
                this.playerData = playerData;
                InventoryConverter.applyInventoryDataToPlayer(playerData, player);
            } else {
                this.playerData = new PlayerData(username);
                worldData.savePlayerData(player.getUsername(), this.playerData);
            }
        }

        // Initialize UI components
        buildModeUI = new BuildModeUI(uiStage, skin, player);
        buildModeUI.hide();

        world.setPlayerData(playerData);

        // Initialize inventory and spawn manager
        this.inventory = player.getInventory();
        this.spawnManager = world.getPokemonSpawnManager();

        // Initialize game systems
        this.gameMenu = new GameMenu(this, game, uiSkin, this.player, gameClient);
        setupGameClientListeners();
//        this.chatSystem = new ChatSystem(uiStage, uiSkin, gameClient, username);
        createPartyDisplay();

        // Now set up all input processors with complete UI
        setupInputProcessors();

        // Add Android processor
        inputMultiplexer.addProcessor(new AndroidInputProcessor());
        if (Gdx.app.getType() == Application.ApplicationType.Android) {
            initializeAndroidControls();
        }
    }

    private void setupInputProcessors() {
        if (inputMultiplexer == null) {
            inputMultiplexer = new InputMultiplexer();
        }

        inputMultiplexer.clear();

        // Add stages in order of priority
        if (chatSystem != null) {
            inputMultiplexer.addProcessor(stage); // Stage contains chat UI
        }

        // Add menu processor
        inputMultiplexer.addProcessor(new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.ESCAPE && !chatSystem.isActive()) {
                    if (gameMenu != null) {
                        if (gameMenu.isVisible()) {
                            gameMenu.hide();
                        } else {
                            gameMenu.show();
                        }
                        return true;
                    }
                }
                return false;
            }
        });

        // Add other processors
        if (gameMenu != null && gameMenu.getStage() != null) {
            inputMultiplexer.addProcessor(gameMenu.getStage());
        }
        inputMultiplexer.addProcessor(uiStage);
        inputMultiplexer.addProcessor(inputHandler);

        // Add Android processor if needed
        if (Gdx.app.getType() == Application.ApplicationType.Android) {
            inputMultiplexer.addProcessor(new AndroidInputProcessor());
        }

        Gdx.input.setInputProcessor(inputMultiplexer);
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

    public OrthographicCamera getCamera() {
        return camera;
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

    private void updatePlayerMovement() {
        if (!joystickActive) return;

        float dx = joystickCurrent.x - joystickCenter.x;
        float dy = joystickCurrent.y - joystickCenter.y;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);

        if (distance > 10) { // Small dead zone
            // Determine direction
            if (Math.abs(dx) > Math.abs(dy)) {
                // Horizontal movement
                if (dx > 0) {
                    player.move("right");
                } else {
                    player.move("left");
                }
            } else {
                // Vertical movement
                if (dy > 0) {
                    player.move("up");
                } else {
                    player.move("down");
                }
            }

            // Set running based on joystick distance
            player.setRunning(distance > VIRTUAL_JOYSTICK_RADIUS * 0.7f);
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
                // Handle successful login, initialize game state
                // Potentially apply any additional data received from server
            } else {
                GameLogger.info("Login failed: " + response.message);
                // Handle login failure, possibly prompt user again
            }
        });

        gameClient.setRegistrationResponseListener(response -> {
            if (response.success) {
                GameLogger.info("Registration successful for " + response.username);
                // Handle successful registration, possibly auto-login or prompt user
            } else {
                GameLogger.info("Registration failed: " + response.message);
                // Handle registration failure, possibly prompt user again
            }
        });
    }

    public WorldData getCurrentWorld() {
        return currentWorld;
    }

    private Table createPartySlotCell(int index, Pokemon pokemon) {
        Table cell = new Table();
        boolean isSelected = index == 0; // First Pokémon is active

        // Set background based on selection
        TextureRegionDrawable slotBg = new TextureRegionDrawable(
            TextureManager.ui.findRegion(isSelected ? "slot_selected" : "slot_normal")
        );
        cell.setBackground(slotBg);

        if (pokemon != null) {
            Table contentStack = new Table();
            contentStack.setFillParent(true);

            // Center the Pokémon icon within the slot
            Image pokemonIcon = new Image(pokemon.getCurrentIconFrame(Gdx.graphics.getDeltaTime()));
            pokemonIcon.setScaling(Scaling.fit);

            // Stack the icon at the top of the cell
            contentStack.add(pokemonIcon).size(40).padTop(4).row();

            // Add level label below the icon
            Label levelLabel = new Label("Lv." + pokemon.getLevel(), skin);
            levelLabel.setFontScale(0.8f);
            contentStack.add(levelLabel).padTop(2).row();

            // Add health bar below the level label
            ProgressBar hpBar = new ProgressBar(0, pokemon.getStats().getHp(), 1, false, skin);
            hpBar.setValue(pokemon.getCurrentHp());
            contentStack.add(hpBar).width(40).height(4).padTop(2);

            cell.add(contentStack).expand().fill();
        }

        return cell;
    }

    private void updateSlotVisuals() {
        // Clear existing slots and re-create with updated visuals
        partyDisplay.clearChildren();
        createPartyDisplay(); // Re-render the slots with the updated selection
    }

    private void selectPokemon(int index) {
        if (pokemonPartyUI != null) {
            pokemonPartyUI.selectPokemon(index);
            updatePokemonPartyUI();
        }
        updateSlotVisuals(); // Refresh the slot visuals after changing selection
    }

    /**
     * Creates the hotbar UI by rendering all hotbar slots.
     */


    public PlayerData getCurrentPlayerState() {
        PlayerData currentState = new PlayerData(player.getUsername());
        // Use InventoryConverter to extract inventory data
        InventoryConverter.extractInventoryDataFromPlayer(player, currentState);
        return currentState;
    }

    private Color getSlotNumberColor(boolean isSelected) {
        return isSelected ? Color.WHITE : Color.LIGHT_GRAY;
    }

    private Color getCountBackgroundColor() {
        return new Color(0, 0, 0, 0.7f);
    }

    // Helper methods for Pokemon party handling
    private boolean isPokemonPartyVisible() {
        return pokemonPartyUI != null && pokemonPartyUI.isVisible();
    }

    private void showPokemonParty() {
        if (pokemonPartyUI == null) {
            pokemonPartyUI = new PokemonPartyUI(player.getPokemonParty(), skin);
            pokemonPartyStage.addActor(pokemonPartyUI);
        }
        pokemonPartyUI.setVisible(!pokemonPartyUI.isVisible());
        updatePokemonPartyUI();
    }

    private void hidePokemonParty() {
        if (pokemonPartyUI != null) {
            pokemonPartyUI.setVisible(false);
        }
    }

    private void updateAndroidControlPositions() {
        if (Gdx.app.getType() == Application.ApplicationType.Android) {
            float screenWidth = Gdx.graphics.getWidth();
            float screenHeight = Gdx.graphics.getHeight();
            float buttonSize = screenHeight * 0.1f;
            float padding = buttonSize * 0.5f;

            // Update joystick position
            joystickCenter.set(screenWidth * 0.15f, screenHeight * 0.2f);
            joystickCurrent.set(joystickCenter);

            // Update button positions
            inventoryButton.set(
                screenWidth - (buttonSize * 2 + padding * 2),
                screenHeight - (buttonSize + padding),
                buttonSize,
                buttonSize
            );

            menuButton.set(
                screenWidth - (buttonSize + padding),
                screenHeight - (buttonSize + padding),
                buttonSize,
                buttonSize
            );
        }
    }

    private int getCurrentPokemonIndex() {
        return pokemonPartyUI != null ? pokemonPartyUI.getSelectedIndex() : 0;
    }

    private void updatePokemonPartyUI() {
        if (pokemonPartyUI != null && pokemonPartyUI.isVisible()) {
            pokemonPartyUI.updateUI();
        }
    }

    private void updateSlotVisuals(Table cell, boolean isSelected) {
        cell.setBackground(new TextureRegionDrawable(
            TextureManager.ui.findRegion(isSelected ? "slot_selected" : "slot_normal")
        ));
    }

    // In GameScreen class
    @Override
    public void handleBattleInitiation() {
        if (transitioning) return; // Prevent multiple transitions

        WildPokemon nearestPokemon = world.getNearestInteractablePokemon(player);
        if (nearestPokemon != null && !nearestPokemon.isAddedToParty()) {
            if (nearestPokemon.getLevel() >= 7) {
                if (player.getPokemonParty().getSize() == 0) {
                    chatSystem.handleIncomingMessage(createSystemMessage("You need Pokémon to battle!"));
                    return;
                }

                // Start transition
                transitioning = true;
                transitionTimer = 0;
                transitionOut = true;
                transitioningPokemon = nearestPokemon;

            } else {
                // Handle friendly Pokemon as before
                if (!player.getPokemonParty().isFull()) {
                    player.getPokemonParty().addPokemon(nearestPokemon);
                    nearestPokemon.setAddedToParty(true);
                    world.getPokemonSpawnManager().despawnPokemon(nearestPokemon.getUuid());
                    updatePartyDisplay();
                    chatSystem.handleIncomingMessage(createSystemMessage(
                        nearestPokemon.getName() + " seems friendly and joined your party!"
                    ));
                    updateSlotVisuals();
                } else {
                    chatSystem.handleIncomingMessage(createSystemMessage(
                        "Your party is full. " + nearestPokemon.getName() + " cannot join."
                    ));
                }
            }
        }
    }

    private void renderTransitionOverlay(float progress) {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0, 0, 0, progress);

        // Draw full screen black overlay
        float screenWidth = Gdx.graphics.getWidth();
        float screenHeight = Gdx.graphics.getHeight();
        shapeRenderer.rect(0, 0, screenWidth, screenHeight);

        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private void renderWorldWithFade(float alpha) {
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        Color c = batch.getColor();
        float oldA = c.a;
        c.a *= alpha;
        batch.setColor(c);

        // Render world elements
        Rectangle viewBounds = new Rectangle(
            camera.position.x - (camera.viewportWidth * camera.zoom) / 2,
            camera.position.y - (camera.viewportHeight * camera.zoom) / 2,
            camera.viewportWidth * camera.zoom,
            camera.viewportHeight * camera.zoom
        );

        world.render(batch, viewBounds, player);

        // Restore alpha
        c.a = oldA;
        batch.setColor(c);
        batch.end();
    }

    private void renderBattleTransition(float delta) {
        transitionTimer += delta;
        float progress = Math.min(1, transitionTimer / TRANSITION_DURATION);

        // Draw black bars closing in
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0, 0, 0, 1);

        float height = Gdx.graphics.getHeight() * progress / 2;
        // Top bar
        shapeRenderer.rect(0, Gdx.graphics.getHeight() - height,
            Gdx.graphics.getWidth(), height);
        // Bottom bar
        shapeRenderer.rect(0, 0, Gdx.graphics.getWidth(), height);

        shapeRenderer.end();

        if (progress >= 1) {
            transitioning = false;
            transitionTimer = 0;
        }
    }
    // Add this helper method for better chunk visibility checking

    public void startBattle(WildPokemon pokemon) {
//        BattleScreen battleScreen = new BattleScreen(
//            player.getPokemonParty().getPokemon(0),
//            pokemon,
//            world.getBiomeAt(player.getTileX(), player.getTileY()).getType(),
//            TextureManager.battlebacks,
//            skin
//        );
//
//        battleScreen.setBattleCompletionHandler(() -> {
//            // After battle, return to the GameScreen
//            game.setScreenWithoutDisposing(this);
//            // Re-setup input processors and other necessary components
//            setupInputProcessors();
//        });
//
//        game.setScreenWithoutDisposing(battleScreen);
    }

    private WildPokemon getNearbyPokemonBelowLevel() {
        // Search for the nearest Pokémon within interaction range
        Collection<WildPokemon> nearbyPokemon = world.getPokemonSpawnManager().getAllWildPokemon();
        WildPokemon closestPokemon = null;
        float closestDistance = Float.MAX_VALUE;

        for (WildPokemon pokemon : nearbyPokemon) {
            float distance = Vector2.dst(player.getTileX(), player.getTileY(), pokemon.getX(), pokemon.getY());

            if (distance < TILE_SIZE * 1.5f && distance < closestDistance) {
                closestPokemon = pokemon;
                closestDistance = distance;
            }

        }
        return closestPokemon; // Returns null if no suitable Pokémon is found
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
        setupInputProcessors();
        batch = new SpriteBatch();
        AudioManager.getInstance().stopMenuMusic();

        // Set up input processors in correct order
        InputMultiplexer multiplexer = new InputMultiplexer();

        // Add chat stage first for chat input priority
        multiplexer.addProcessor(stage);

        // Add game input processors
        multiplexer.addProcessor(inputHandler);

        // Add Android processor if needed
        if (Gdx.app.getType() == Application.ApplicationType.Android) {
            multiplexer.addProcessor(new AndroidInputProcessor());
        }

        Gdx.input.setInputProcessor(multiplexer);
    }

    /**
     * Updates the state of other players based on network data.
     */

    private void updateOtherPlayers() {
        Map<String, NetworkProtocol.PlayerUpdate> networkPlayerUpdates = gameClient.getPlayerUpdates();
        GameLogger.info("Number of other players to update: " + networkPlayerUpdates.size());

        for (Map.Entry<String, NetworkProtocol.PlayerUpdate> entry : networkPlayerUpdates.entrySet()) {
            String username = entry.getKey();
            NetworkProtocol.PlayerUpdate netUpdate = entry.getValue();

            GameLogger.info("Processing PlayerUpdate for: " + username);

            if (!username.equals(player.getUsername()) && netUpdate != null) {
                // Get the existing OtherPlayer or create a new one
                OtherPlayer op = otherPlayers.get(username);
                if (op == null) {
                    // Create a new OtherPlayer instance
                    op = new OtherPlayer(username, netUpdate.x, netUpdate.y, TextureManager.boy);
                    otherPlayers.put(username, op);
                    GameLogger.info("Created new OtherPlayer for username: " + username);
                }

                // Update the OtherPlayer's state with the network data
                op.updateFromNetwork(netUpdate);
                GameLogger.info("Updated OtherPlayer " + username + " with position: (" + netUpdate.x + ", " + netUpdate.y + ")");
            }
        }

        // Remove OtherPlayers that are no longer present
        Set<String> playersToRemove = new HashSet<>(otherPlayers.keySet());
        playersToRemove.removeAll(networkPlayerUpdates.keySet());
        for (String username : playersToRemove) {
            OtherPlayer op = otherPlayers.remove(username);
            if (op != null) {
                op.dispose();
                GameLogger.info("Removed OtherPlayer: " + username);
            }
        }
    }

    private void setupCamera() {
        camera = new OrthographicCamera();

        // Calculate viewport size based on target tiles
        float baseWidth = TARGET_VIEWPORT_WIDTH_TILES * TILE_SIZE;
        float baseHeight = baseWidth * ((float) Gdx.graphics.getHeight() / Gdx.graphics.getWidth());

        // Initialize cameraViewport
        cameraViewport = new FitViewport(baseWidth, baseHeight, camera);

        // Now update the cameraViewport without centering the camera
        cameraViewport.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), false);

        // Set initial position
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
            // Calculate target position (center on player)
            float targetX = player.getX() + Player.FRAME_WIDTH / 2f;
            float targetY = player.getY() + Player.FRAME_HEIGHT / 2f;

            // Smooth camera follow
            float lerp = CAMERA_LERP * Gdx.graphics.getDeltaTime();
            camera.position.x += (targetX - camera.position.x) * lerp;
            camera.position.y += (targetY - camera.position.y) * lerp;

            // Debug camera position
//            GameLogger.info("Camera following - target: " + targetX + "," + targetY +
//                " actual: " + camera.position.x + "," + camera.position.y);

            camera.update();
        }
    }

    private void centerCameraOnPlayer() {
        if (player != null) {
            // Calculate the center position based on player's tile position
            float exactPlayerX = player.getX() + (Player.FRAME_WIDTH / 2f);
            float exactPlayerY = player.getY() + (Player.FRAME_HEIGHT / 2f);

            // Smoothly transition the camera's position to the player's position
            camera.position.lerp(new Vector3(exactPlayerX, exactPlayerY, 0), CAMERA_SMOOTH_SPEED * Gdx.graphics.getDeltaTime());
            camera.update();
        }
    }

    private void clampCameraPosition() {
        float halfViewportWidth = (camera.viewportWidth * camera.zoom) / 2f;
        float halfViewportHeight = (camera.viewportHeight * camera.zoom) / 2f;

        camera.position.x = MathUtils.clamp(camera.position.x, MIN_TILE_BOUND * TILE_SIZE + halfViewportWidth,
            MAX_TILE_BOUND * TILE_SIZE - halfViewportWidth);
        camera.position.y = MathUtils.clamp(camera.position.y, MIN_TILE_BOUND * TILE_SIZE + halfViewportHeight,
            MAX_TILE_BOUND * TILE_SIZE - halfViewportHeight);
    }

    public void render(float delta) {

        // Clear the screen properly first
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        float viewportWidthPixels = camera.viewportWidth * camera.zoom;
        float viewportHeightPixels = camera.viewportHeight * camera.zoom;
        if (transitioning) {
            transitionTimer += delta;
            float progress = transitionTimer / TRANSITION_DURATION;

            if (transitionOut) {
                // Fade out
                if (progress >= 1.0f) {
                    // Switch to battle screen
                    if (transitioningPokemon != null) {
                        startBattle(transitioningPokemon);
                    }
                    transitioning = false;
                    transitionTimer = 0;
                    transitioningPokemon = null;
                } else {
                    // Render world with fade out effect
                    renderWorldWithFade(1.0f - progress);
                    renderTransitionOverlay(progress);
                }
            }
        } else {
            Rectangle viewBounds = new Rectangle(
                camera.position.x - (camera.viewportWidth * camera.zoom) / 2,
                camera.position.y - (camera.viewportHeight * camera.zoom) / 2,
                camera.viewportWidth * camera.zoom,
                camera.viewportHeight * camera.zoom
            );

            // Calculate view bounds for rendering


            batch.setProjectionMatrix(camera.combined);
            batch.begin();
            // Update and render the world
            world.render(batch, viewBounds, player);
        }
        // Update world with current viewport dimensions
        world.update(delta,
            new Vector2(player.getTileX(), player.getTileY()),
            viewportWidthPixels,
            viewportHeightPixels
        );

        world.update(delta, new Vector2(player.getTileX(), player.getTileY()), camera.viewportWidth, camera.viewportHeight);

        float deltaTime = Gdx.graphics.getDeltaTime();

        worldManager.checkAutoSave();
        // Update systems
        chatSystem.update(delta);
        handleInput();
        player.update(deltaTime);
        updateCamera();


        // Update other players
        if (!gameClient.isSinglePlayer()) {
            updateOtherPlayers(deltaTime);
            renderOtherPlayers(batch);
        }

        batch.end();

        // Render UI
        uiStage.getViewport().apply();
        uiStage.act(deltaTime);
        uiStage.draw();

        if (SHOW_DEBUG_INFO) {
            renderDebugInfo();
        }

        // Menu rendering
        if (gameMenu != null) {
            gameMenu.getStage().getViewport().apply();
            gameMenu.getStage().act(deltaTime);
            gameMenu.getStage().draw();
        }
        if (transitioning) {
            renderBattleTransition(delta);
        }
        // Inventory rendering
        if (inventoryOpen && inventoryScreen != null) {
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            shapeRenderer.setColor(0, 0, 0, 0.5f);
            shapeRenderer.rect(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            shapeRenderer.end();

            inventoryScreen.render(deltaTime);

            Gdx.gl.glDisable(GL20.GL_BLEND);
        }

        if (isPokemonPartyVisible()) {
            pokemonPartyStage.act(deltaTime);
            pokemonPartyStage.draw();
        }

        if (Gdx.app.getType() == Application.ApplicationType.Android) {
            renderAndroidControls();
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
        Map<String, OtherPlayer> players = gameClient.getOtherPlayers();
        if (players == null || players.isEmpty()) {
            return;
        }

        for (OtherPlayer otherPlayer : players.values()) {
            if (otherPlayer != null) {
                otherPlayer.update(delta);
            }
        }
    }

    private void renderOtherPlayers(SpriteBatch batch) {
        Map<String, OtherPlayer> players = gameClient.getOtherPlayers();
        if (players == null || players.isEmpty()) {
            return;
        }

        // Sort players by Y position for proper layering
        List<OtherPlayer> sortedPlayers = new ArrayList<>(players.values());
        sortedPlayers.sort(Comparator.comparing(OtherPlayer::getY));

        for (OtherPlayer otherPlayer : sortedPlayers) {
            if (isPlayerInView(otherPlayer)) { // Frustum culling
                otherPlayer.render(batch);
            }
        }
    }

    private boolean isPlayerInView(OtherPlayer player) {
        // Calculate bounds based on camera
        float camLeft = camera.position.x - camera.viewportWidth / 2 * camera.zoom;
        float camRight = camera.position.x + camera.viewportWidth / 2 * camera.zoom;
        float camBottom = camera.position.y - camera.viewportHeight / 2 * camera.zoom;
        float camTop = camera.position.y + camera.viewportHeight / 2 * camera.zoom;

        return player.getX() >= camLeft && player.getX() <= camRight &&
            player.getY() >= camBottom && player.getY() <= camTop;
    }

    /**
     * Renders debug information on the screen.
     */
    private void renderDebugInfo() {
        batch.setProjectionMatrix(uiStage.getCamera().combined);
        batch.begin();
        font.setColor(Color.WHITE);

        float debugY = 25; // Start from 10 pixels above the bottom edge

        // Get raw pixel coordinates
        float pixelX = player.getX();
        float pixelY = player.getY();

        // Convert to tile coordinates correctly
        int tileX = (int) Math.floor(pixelX / TILE_SIZE);
        int tileY = (int) Math.floor(pixelY / TILE_SIZE);

        // Get the current biome
        Biome currentBiome = world.getBiomeAt(tileX, tileY);

        // Display coordinates, biome, and time
        font.draw(batch, String.format("Pixels: (%d, %d)", (int) pixelX, (int) pixelY), 10, debugY);
        debugY += 20;
        font.draw(batch, String.format("Tiles: (%d, %d)", tileX, tileY), 10, debugY);
        debugY += 20;
        font.draw(batch, "Direction: " + player.getDirection(), 10, debugY);
        debugY += 20;
        font.draw(batch, "Biome: " + currentBiome.getName(), 10, debugY);
        debugY += 20;

        // Add total Pokemon count
        font.draw(batch, "Active Pokemon: " + getTotalPokemonCount(), 10, debugY);
        debugY += 20;

        String timeString = DayNightCycle.getTimeString(world.getWorldData().getWorldTimeInMinutes());
        font.draw(batch, "Time: " + timeString, 10, debugY);
        debugY += 20;

        // Add total time played
        long playedTimeMillis = world.getWorldData().getPlayedTime();
        String playedTimeStr = formatPlayedTime(playedTimeMillis);
        font.draw(batch, "Total Time Played: " + playedTimeStr, 10, debugY);

        batch.end();
    }

    /**
     * Handles user input for the game, including toggling the inventory and debug info.
     */
    private void handleInput() {
        // First check if chat is active
        if (chatSystem != null && chatSystem.isActive()) {
            return; // Skip all game input when chat is active
        }

        // Handle game inputs only when chat is not active
        if (Gdx.input.isKeyJustPressed(Input.Keys.E)) {
            toggleInventory();
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
        // Handle build mode specific inputs
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

    private void initializeAndroidControls() {
        float screenWidth = Gdx.graphics.getWidth();
        float screenHeight = Gdx.graphics.getHeight();

        // Initialize joystick
        joystickCenter = new Vector2(screenWidth * 0.15f, screenHeight * 0.2f);
        joystickCurrent = new Vector2(joystickCenter);
        joystickActive = false;

        // Calculate button dimensions
        float buttonSize = screenHeight * 0.1f; // 10% of screen height
        float padding = buttonSize * 0.5f;

        // Initialize UI buttons with safe positioning
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


        GameLogger.info("Android controls initialized with screen dimensions: " + screenWidth + "x" + screenHeight);
    }

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

            // Draw "INV" text centered on inventory button
            GlyphLayout invLayout = new GlyphLayout(font, "INV");
            font.draw(batch, "INV",
                inventoryButton.x + (inventoryButton.width - invLayout.width) / 2,
                inventoryButton.y + (inventoryButton.height + invLayout.height) / 2);

            // Draw "MENU" text centered on menu button
            GlyphLayout menuLayout = new GlyphLayout(font, "MENU");
            font.draw(batch, "MENU",
                menuButton.x + (menuButton.width - menuLayout.width) / 2,
                menuButton.y + (menuButton.height + menuLayout.height) / 2);

            font.getData().setScale(1.0f); // Reset scale
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

    private void renderAndroidControls() {
        if (shapeRenderer == null) {
            shapeRenderer = new ShapeRenderer();
        }

        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        // Draw joystick base
        shapeRenderer.setColor(0.3f, 0.3f, 0.3f, 0.5f);
        shapeRenderer.circle(joystickCenter.x, joystickCenter.y, VIRTUAL_JOYSTICK_RADIUS);

        // Draw joystick handle at current position if active, or at center if not
        shapeRenderer.setColor(0.7f, 0.7f, 0.7f, joystickActive ? 0.7f : 0.5f);
        shapeRenderer.circle(joystickCurrent.x, joystickCurrent.y, VIRTUAL_JOYSTICK_RADIUS * 0.5f);

        // Draw buttons
        shapeRenderer.setColor(0.2f, 0.2f, 0.2f, 0.7f);
        renderButton(shapeRenderer, inventoryButton);
        renderButton(shapeRenderer, menuButton);

        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        // Draw button labels
        renderButtonLabels();
    }

    private void setupInventoryUI() {
        // Create main container
        inventoryContainer = new Table();
        inventoryContainer.setFillParent(true);

        // Create close button
        closeInventoryButton = new TextButton("X", skin);
        closeInventoryButton.setColor(Color.RED);

        // Size the button appropriately for touch
        float buttonSize = Gdx.graphics.getHeight() * 0.08f; // 8% of screen height

        // Position in top right with padding
        float padding = Gdx.graphics.getHeight() * 0.02f; // 2% of screen height
        closeInventoryButton.setSize(buttonSize, buttonSize);
        closeInventoryButton.setPosition(
            Gdx.graphics.getWidth() - buttonSize - padding,
            Gdx.graphics.getHeight() - buttonSize - padding
        );

        // Add click listener
        closeInventoryButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                closeInventory();
            }
        });
    }

    private void toggleInventory() {
        inventoryOpen = !inventoryOpen;

        if (inventoryOpen) {
            if (inventoryScreen == null) {
                inventoryScreen = new InventoryScreen(player, uiSkin, gameClient);
                setupInventoryUI(); // Set up the UI components

                // Add the close button to the inventory screen's stage
                inventoryScreen.getStage().addActor(closeInventoryButton);
            }

            // Create input processor that handles both inventory and close button
            InputMultiplexer inventoryMultiplexer = new InputMultiplexer();
            inventoryMultiplexer.addProcessor(inventoryScreen.getStage()); // Inventory stage first
            inventoryMultiplexer.addProcessor(new InputAdapter() {
                @Override
                public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                    // Convert touch coordinates to stage coordinates
                    Vector2 stageCoords = inventoryScreen.getStage().screenToStageCoordinates(
                        new Vector2(screenX, screenY)
                    );

                    // Check if touch is outside inventory area
                    if (!inventoryScreen.isOverInventory(stageCoords.x, stageCoords.y)) {
                        closeInventory();
                        return true;
                    }
                    return false;
                }
            });

            Gdx.input.setInputProcessor(inventoryMultiplexer);
        } else {
            closeInventory();
        }
    }

    private void closeInventory() {
        if (inventoryScreen != null) {
            inventoryScreen.hide();
            inventoryScreen.dispose();
            inventoryScreen = null;
        }
        inventoryOpen = false;

        // Restore game input processors
        setupInputProcessors();
    }


    /**
     * Closes the inventory screen and restores normal game input.
     */


    @Override
    public void resize(int width, int height) {
        // Update viewport without recentering the camera
        cameraViewport.update(width, height, false);
        uiStage.getViewport().update(width, height, true);
        if (pokemonPartyStage != null) {
            pokemonPartyStage.getViewport().update(width, height, true);
        }
        if (gameMenu != null && gameMenu.getStage() != null) {
            gameMenu.getStage().getViewport().update(width, height, true);
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

        // Update Android controls
        updateAndroidControlPositions();

        updateCamera();
        GameLogger.info("Screen resized to: " + width + "x" + height);
    }


    private Vector2 pixelsToTiles(float pixelX, float pixelY) {
        int tileX = (int) Math.floor(pixelX / TILE_SIZE);
        int tileY = (int) Math.floor(pixelY / TILE_SIZE);
        return new Vector2(tileX, tileY);
    }

    private Vector2 tilesToPixels(int tileX, int tileY) {
        float pixelX = tileX * TILE_SIZE;
        float pixelY = tileY * TILE_SIZE;
        return new Vector2(pixelX, pixelY);
    }
    /**
     * Updates the hotbar UI after inventory changes.
     */

    /**
     * Generates a random ItemData instance.
     *
     * @return A new ItemData object.
     */
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
            itemData.setCount(1); // Ensure count is 1
            itemData.setUuid(UUID.randomUUID());
            return itemData;
        }
        GameLogger.error("Failed to retrieve ItemData for item: " + itemName);
        return null;
    }


    /**
     * Handles item pickup actions by the player.
     */
    public void handlePickupAction() {
        WorldObject nearestPokeball = world.getNearestPokeball();
        if (nearestPokeball == null) {
            GameLogger.info("No pokeball found nearby");
            return;
        }

        // Log positions for debugging
        GameLogger.info("Player position: " + player.getX() + "," + player.getY());
        GameLogger.info("Pokeball position: " + nearestPokeball.getPixelX() + "," + nearestPokeball.getPixelY());

        if (player.canPickupItem(nearestPokeball.getPixelX(), nearestPokeball.getPixelY())) {
            // Important: Remove the object BEFORE trying to add item to inventory
            world.removeWorldObject(nearestPokeball);


            // Generate single random item
            ItemData randomItemData = generateRandomItemData();
            if (randomItemData == null) {
                GameLogger.error("Failed to generate random item data.");
                return;
            }

            // Add to inventory using InventoryConverter
            boolean added = InventoryConverter.addItemToInventory(inventory, randomItemData);

            // Create chat message for successful pickup
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

            // Handle message based on game mode
            if (gameClient.isSinglePlayer()) {
                chatSystem.handleIncomingMessage(pickupMessage);
            } else {
                gameClient.sendPrivateMessage(pickupMessage, player.getUsername());
            }

            AudioManager.getInstance().playSound(AudioManager.SoundEffect.ITEM_PICKUP);
            player.updatePlayerData();
        } else {
            GameLogger.info("Cannot pick up pokeball - too far or wrong direction");
        }
    }

    private void updatePartyDisplay() {
        // Clear existing display
        partyDisplay.clearChildren();

        // Recreate the party display
        createPartyDisplay();
    }

    @Override
    public void pause() {
        // Implement if needed
    }

    @Override
    public void resume() {
        // Implement if needed
    }

    private void resetInputProcessor() {
        // Create new input multiplexer
        inputMultiplexer = new InputMultiplexer();

        // Add processors in correct order
        inputMultiplexer.addProcessor(stage);
        inputMultiplexer.addProcessor(inputHandler);

        // If on Android, add Android processor
        if (Gdx.app.getType() == Application.ApplicationType.Android) {
            inputMultiplexer.addProcessor(new AndroidInputProcessor());
        }

        Gdx.input.setInputProcessor(inputMultiplexer);
    }

    @Override
    public void dispose() {

        batch.dispose();
        if (pokemonPartyStage != null) {
            pokemonPartyStage.dispose();
        }

        if (currentWorld != null) {
            currentWorld.savePlayerData(username, playerData);
        }

        try {
            if (player != null) {
                // Get final state using InventoryConverter
                PlayerData finalState = getCurrentPlayerState();
                GameLogger.info("Final player state before save:");
                GameLogger.info("Position: " + finalState.getX() + "," + finalState.getY());
                GameLogger.info("Inventory: " + finalState.getInventoryItems());

                // Update world data
                WorldData worldData = world.getWorldData();
                InventoryConverter.extractInventoryDataFromPlayer(player, finalState);
                worldData.savePlayerData(player.getUsername(), finalState);

                // Save to storage
                game.getWorldManager().saveWorld(worldData);

                GameLogger.info("Player state saved successfully");
            }

        } catch (Exception e) {
            GameLogger.info("Error saving final state: " + e.getMessage());
            e.printStackTrace();
        }

        // Cleanup resources
        if (gameClient != null) {
            gameClient.dispose();
        }

        // Dispose other resourc
        player.dispose();

        // Ensure other players are disposed of
        for (OtherPlayer op : otherPlayers.values()) {
            op.dispose();
        }

        // Ensure save directories are created if necessary
        ensureSaveDirectories();

        // Update player coordinates in the database when the game screen is disposed
        DatabaseManager dbManager = game.getDatabaseManager();
        dbManager.updatePlayerCoordinates(player.getUsername(), player.getTileX(), player.getTileY());
        GameLogger.info("Player coordinates updated in database.");
    }


    public class AndroidInputProcessor extends InputAdapter {
        private static final float DEAD_ZONE = 5f; // Reduced dead zone for better sensitivity

        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            if (Gdx.app.getType() != Application.ApplicationType.Android) return false;

            if (inventoryButton == null || menuButton == null || joystickCenter == null) {
                GameLogger.error("Android controls not properly initialized");
                return false;
            }

            float touchY = Gdx.graphics.getHeight() - screenY; // Flip Y for touch coordinates

            // Add logging for touch events
            GameLogger.info("Touch down at: (" + screenX + ", " + touchY + ")");

            float touchPadding = 20f;
            Rectangle paddedInvButton = new Rectangle(
                inventoryButton.x - touchPadding,
                inventoryButton.y - touchPadding,
                inventoryButton.width + touchPadding * 2,
                inventoryButton.height + touchPadding * 2
            );

            Rectangle paddedMenuButton = new Rectangle(
                menuButton.x - touchPadding,
                menuButton.y - touchPadding,
                menuButton.width + touchPadding * 2,
                menuButton.height + touchPadding * 2
            );

            // Handle UI button touches
            if (paddedInvButton.contains(screenX, touchY)) {
                GameLogger.info("Inventory button touched");
                toggleInventory();
                return true;
            }
            if (paddedMenuButton.contains(screenX, touchY)) {
                GameLogger.info("Menu button touched");
                if (gameMenu.isVisible()) gameMenu.hide();
                else gameMenu.show();
                return true;
            }

            // Handle joystick activation
            float distanceFromJoystick = Vector2.dst(screenX, touchY, joystickCenter.x, joystickCenter.y);
            if (distanceFromJoystick <= VIRTUAL_JOYSTICK_RADIUS * 1.5f) {
                GameLogger.info("Joystick activated");
                joystickActive = true;
                joystickCurrent.set(screenX, touchY);
                return true;
            }

            return false;
        }

        @Override
        public boolean touchDragged(int screenX, int screenY, int pointer) {
            if (!joystickActive) return false;

            float touchY = Gdx.graphics.getHeight() - screenY;
            joystickCurrent.set(screenX, touchY);

            float dx = joystickCurrent.x - joystickCenter.x;
            float dy = joystickCurrent.y - joystickCenter.y;
            float distance = joystickCurrent.dst(joystickCenter);

            if (distance > DEAD_ZONE) {
                // Restrict joystick to max radius
                if (distance > VIRTUAL_JOYSTICK_RADIUS) {
                    float scale = VIRTUAL_JOYSTICK_RADIUS / distance;
                    dx *= scale;
                    dy *= scale;
                    joystickCurrent.set(joystickCenter.x + dx, joystickCenter.y + dy);
                }

                // Determine movement direction
                String direction = null;
                if (Math.abs(dx) > Math.abs(dy)) {
                    direction = dx > 0 ? "right" : "left";
                } else {
                    direction = dy > 0 ? "up" : "down";
                }

                // Log direction and distance for debugging
                GameLogger.info("Moving " + direction + " with distance: " + distance);

                if (direction != null) {
                    player.move(direction);
                }
                player.setRunning(distance > VIRTUAL_JOYSTICK_RADIUS * 0.7f);
                player.setMoving(true);
            }
            return true;
        }

        @Override
        public boolean touchUp(int screenX, int screenY, int pointer, int button) {
            if (joystickActive) {
                GameLogger.info("Joystick deactivated");
                joystickActive = false;
                player.setMoving(false);
                player.setRunning(false);
                joystickCurrent.set(joystickCenter); // Reset to center
                return true;
            }
            return false;
        }
    }
}


package io.github.pokemeetup.screens;

import com.badlogic.gdx.*;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.pokemeetup.CreatureCaptureGame;
import io.github.pokemeetup.audio.AudioManager;
import io.github.pokemeetup.chat.ChatSystem;
import io.github.pokemeetup.managers.DatabaseManager;
import io.github.pokemeetup.multiplayer.OtherPlayer;
import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.multiplayer.client.GameClientSingleton;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.multiplayer.server.ServerStorageSystem;
import io.github.pokemeetup.multiplayer.server.config.ServerConnectionConfig;
import io.github.pokemeetup.system.*;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.WorldObject;
import io.github.pokemeetup.system.gameplay.overworld.multiworld.WorldData;
import io.github.pokemeetup.system.gameplay.overworld.multiworld.WorldManager;
import io.github.pokemeetup.system.gameplay.inventory.Inventory;
import io.github.pokemeetup.system.gameplay.inventory.Item;
import io.github.pokemeetup.system.gameplay.inventory.ItemManager;

import java.io.IOException;
import java.util.*;


public class GameScreen implements Screen, PickupActionHandler {
    public static final int WORLD_SIZE = 1600;
    private static final float STATE_UPDATE_INTERVAL = 0.1f; // Update state every second
    private static final float UPDATE_INTERVAL = 0.1f; // 10 times per second
    private static final int WORLD_WIDTH = 1600; // 50 chunks wide
    private static final int WORLD_HEIGHT = 1600; // 50 chunks high
    private final WorldManager worldManager;
    private final WorldData currentWorld;
    private final String worldName;
    private final CreatureCaptureGame game;
    private final ServerStorageSystem storageSystem;
    private final HashMap<String, OtherPlayer> otherPlayers = new HashMap<>();
    private ChatSystem chatSystem;
    private Player player;
    private InputMultiplexer inputMultiplexer;
    private float stateUpdateTimer = 0;
    private GameMenu gameMenu;
    private float updateTimer = 0;
    private GameClient gameClient;
    private SpriteBatch batch;
    private World world;
    private Stage uiStage;
    private Skin uiSkin;
    private BitmapFont font;
    private OrthographicCamera camera;
    private InputHandler inputHandler;
    private TextureAtlas gameAtlas;
    private boolean isMultiplayer;
    private String username;
    private boolean inventoryOpen = false;
    private InventoryScreen inventoryScreen;

    private Inventory inventory;
    private Table hotbarTable;
    private Table fixedHotbarTable;
    private PlayerData playerData;
    private PlayerDataManager playerDataManager;private ShapeRenderer shapeRenderer;
    private Skin skin;
    private Stage stage;

    public GameScreen(CreatureCaptureGame game, String username, GameClient gameClient, int initialX, int initialY, String worldName) {
        this.worldName = worldName;
        this.game = game;
        this.isMultiplayer = !gameClient.isSinglePlayer();

        this.storageSystem = new ServerStorageSystem();
        DatabaseManager dbManager = game.getDatabaseManager();
        this.gameClient = gameClient;
        gameClient.loadTextures(); // Load textures on main thread
        uiStage = new Stage(new ScreenViewport());
        if (gameClient != null) {
            gameClient.setLocalUsername(username);
            System.out.println("Set username in GameClient: " + username);
        }
        this.skin = new Skin(Gdx.files.internal("Skins/uiskin.json"));
        this.uiSkin = this.skin; // Use the same skin for UI elements
        int pixelX = initialX * World.TILE_SIZE;
        int pixelY = initialY * World.TILE_SIZE;
        this.worldManager = new WorldManager(storageSystem);
        worldManager.init();
        this.currentWorld = worldManager.getWorld(worldName);
        this.username = username;
        if (currentWorld == null) {
            throw new IllegalStateException("World " + worldName + " not found");
        }
        if (isMultiplayer) {
            try {
                ServerConnectionConfig clientConfig = ServerConnectionConfig.getInstance(); // Load client configuration

                gameClient = GameClientSingleton.getInstance(clientConfig);
                gameClient.loadTextures(); // Load textures on main thread
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            if (this.gameClient == null) {
                Gdx.app.error("GameScreen", "Failed to initialize GameClient for multiplayer.");
            }
        }
        // Initialize player with pixel coordinates
        this.uiStage = uiStage;
        batch = new SpriteBatch();
        gameAtlas = new TextureAtlas(Gdx.files.internal("atlas/game-atlas"));

        if (!ItemManager.isInitialized()) {
            throw new RuntimeException("Failed to initialize ItemManager");
        }
        if (!isMultiplayer) {
            inventory = Inventory.loadInventory();
        } else {
            inventory = new Inventory();
        }
        // Initialize camera
        camera = new OrthographicCamera();
        camera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        // Load textures

        // Debug print atlas regions
        for (TextureAtlas.AtlasRegion region : gameAtlas.getRegions()) {
            System.out.println("Found region: " + region.name + " (index: " + region.index + ")");
        }
        font = new BitmapFont(Gdx.files.internal("Skins/default.fnt"));

        font.getData().setScale(0.8f); // Optional: Adjust font size
        font.setColor(Color.WHITE);    // Optional: Set font color

        // Set up input handling
        // Create texture map for the world
        Map<Integer, TextureRegion> tileTextures = new HashMap<>();
        TextureRegion snowRegion = gameAtlas.findRegion("snow");
        TextureRegion hauntedGrassRegion = gameAtlas.findRegion("haunted_grass");
        TextureRegion snowTallGrassRegion = gameAtlas.findRegion("snow_tall_grass");
        TextureRegion hauntedTallGrassRegion = gameAtlas.findRegion("haunted_tall_grass");
        TextureRegion hauntedShroomRegion = gameAtlas.findRegion("haunted_shroom");
        TextureRegion hauntedShroomsRegion = gameAtlas.findRegion("haunted_shrooms");
        TextureRegion tallGrassRegion = gameAtlas.findRegion("tall_grass");

        TextureRegion waterRegion = gameAtlas.findRegion("water");
        TextureRegion grassRegion = gameAtlas.findRegion("grass");
        TextureRegion sandRegion = gameAtlas.findRegion("sand");
        TextureRegion rockRegion = gameAtlas.findRegion("rock");

        if (waterRegion == null || grassRegion == null || sandRegion == null || rockRegion == null) {
            throw new RuntimeException("Failed to load tile textures from atlas");
        }
        if (snowRegion == null || hauntedGrassRegion == null || snowTallGrassRegion == null ||
            hauntedTallGrassRegion == null || hauntedShroomRegion == null ||
            hauntedShroomsRegion == null || tallGrassRegion == null) {
            throw new RuntimeException("Failed to load one or more tile textures from atlas");
        }

        this.shapeRenderer = new ShapeRenderer();

        tileTextures.put(0, waterRegion);
        tileTextures.put(1, grassRegion);
        tileTextures.put(2, sandRegion);
        tileTextures.put(3, rockRegion);
        tileTextures.put(4, snowRegion);                 // SNOW
        tileTextures.put(5, hauntedGrassRegion);         // HAUNTED_GRASS
        tileTextures.put(6, snowTallGrassRegion);        // SNOW_TALL_GRASS
        tileTextures.put(7, hauntedTallGrassRegion);     // HAUNTED_TALL_GRASS
        tileTextures.put(8, hauntedShroomRegion);        // HAUNTED_SHROOM
        tileTextures.put(9, hauntedShroomsRegion);       // HAUNTED_SHROOMS
        tileTextures.put(10, tallGrassRegion);           // TALL_GRASS

        // Initialize world
        // In GameScreen

        long worldSeed = 123456789L; // Or generate/load this value
        this.world = new World(worldName, gameAtlas, tileTextures, World.WORLD_SIZE, World.WORLD_SIZE, gameClient.getWorldSeed(), gameClient);
        WorldData worldData = world.getWorldData();
        PlayerData savedPlayerData = null;

        if (worldData != null) {
//            System.out.println(STR."Attempting to load player data for: \{username}");
            savedPlayerData = worldData.getPlayerData(username);
            //          System.out.println(STR."Loaded player data: \{savedPlayerData != null ? "found" : "not found"}");
            if (savedPlayerData != null) {
                //            System.out.println(STR."Saved position: \{savedPlayerData.getX()},\{savedPlayerData.getY()}");
            }
        }

        // Use saved position if available, otherwise use initial position
        float startX = savedPlayerData != null ? savedPlayerData.getX() : initialX;
        float startY = savedPlayerData != null ? savedPlayerData.getY() : initialY;

        // Create player with the correct position
        // Initialize player with the correct position
        player = new Player((int) startX, (int) startY, world, gameAtlas, username);
        gameClient.setActivePlayer(player);
        gameClient.setCurrentWorld(world);

// Initialize player data with saved state or default if null
        PlayerData playerData = new PlayerData(username);

// If saved data exists, restore it
        if (savedPlayerData != null) {
            playerData.updateFromPlayer(player);
            worldData.savePlayerData(player.getUsername(), playerData);

            // Apply saved state to player
            player.setX((int) savedPlayerData.getX());
            player.setY((int) savedPlayerData.getY());
            player.setDirection(savedPlayerData.getDirection());
            player.setRunning(savedPlayerData.isWantsToRun());

            // Load inventory into player
            if (savedPlayerData.getInventoryItems() != null) {
                loadInventoryFromStrings(player.getInventory(), savedPlayerData.getInventoryItems());
            }
        } else {
            // If no saved data, initialize player with default or passed starting values
            playerData.updateFromPlayer(player);
            if (worldData != null) {
                worldData.savePlayerData(player.getUsername(), playerData);
            }

            System.out.println("No saved player data found. Initializing with default values.");
        }

// Set player data in world
        world.setPlayerData(playerData);
        System.out.println("Set PlayerData in world for user: " + username);

        System.out.println("Retrieved saved player data: " + (savedPlayerData != null ?
            "pos(" + savedPlayerData.getX() + "," + savedPlayerData.getY() + ")" : "null"));

        // Set direction if available
        if (savedPlayerData != null) {
            player.setDirection(savedPlayerData.getDirection());
            player.setRunning(savedPlayerData.isWantsToRun());
        }


        if (world != null) {
            world.setPlayerData(playerData);
            System.out.println("Set PlayerData in world for user: " + username);
        }
        System.out.println("GameScreen initialization complete. PlayerData null? " +
            (world.getPlayerData() == null));

        System.out.println("Final player position: " + player.getX() + "," + player.getY());
        // Initialize player data management
        this.playerDataManager = new PlayerDataManager(player, world, isMultiplayer, storageSystem);
        playerDataManager.loadPlayerState();
        createHotbarUI();
        //        WorldData = world.getWorldData();
        //        if (worldData != null) {
        //            playerData = worldData.getPlayerData(username);
        //        }
        //        if (playerData == null) {
        //            // Create new player data with default values
        //            playerData = new PlayerData(username);
        //            playerData.setX(World.DEFAULT_X_POSITION);
        //            playerData.setY(World.DEFAULT_Y_POSITION);
        //            world.getWorldData().savePlayerData(username, playerData);
        //        }
        //        world.setPlayerData(playerData);

//        System.out.println(STR."GameScreen initialization complete. PlayerData null? \{world.getPlayerData() == null}");

        //     System.out.println(STR."PlayerData set in World for user: \{player.getPlayerData().getUsername()}");
// In GameScreen
        gameMenu = new GameMenu(this, game, uiSkin, this.player, gameClient);
        // Initialize input handler
        inputHandler = new InputHandler(player, this);
        inputMultiplexer = new InputMultiplexer();
        InputMultiplexer inputMultiplexer = new InputMultiplexer();
        inputMultiplexer.addProcessor(uiStage);      // UI input
        inputMultiplexer.addProcessor(inputHandler); // Game input
        Gdx.input.setInputProcessor(inputMultiplexer);
        setupInputProcessors();
        gameClient.setLocalUsername(player.getPlayerData().getUsername());
        setupGameClientListeners();
// In constructor:
        chatSystem = new ChatSystem(uiStage, uiSkin, gameClient, username);
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
                System.out.println("Logged in as " + response.username);
                // Handle successful login, initialize game state
            } else {
                System.out.println("Login failed: " + response.message);
                // Handle login failure
            }
        });

        gameClient.setRegistrationResponseListener(response -> {
            if (response.success) {
                System.out.println("Registration successful for " + response.username);
                // Handle successful registration, possibly auto-login
            } else {
                System.out.println("Registration failed: " + response.message);
                // Handle registration failure
            }
        });


    }

    public WorldData getCurrentWorld() {
        return currentWorld;
    }

    private void loadInventoryFromStrings(Inventory inventory, List<String> itemStrings) {
        System.out.println("Loading inventory items: " + itemStrings);
        for (String itemString : itemStrings) {
            try {
                String[] parts = itemString.trim().split(":");
                if (parts.length == 2) {
                    String itemName = parts[0].trim();
                    int count = Integer.parseInt(parts[1].trim());

                    Item item = ItemManager.getItem(itemName);
                    if (item != null) {
                        // Create a new item instance to avoid sharing references
                        Item newItem = new Item(
                            item.getName(),
                            item.getName().toLowerCase(),
                            item.getIcon()
                        );
                        newItem.setCount(count);

                        boolean added = inventory.addItem(newItem);
                        System.out.println("Loaded item: " + itemName + " x" + count + " (added: " + added + ")");
                    } else {
                        System.err.println("Unknown item: " + itemName);
                    }
                } else {
                    System.err.println("Invalid item format: " + itemString);
                }
            } catch (Exception e) {
                System.err.println("Error loading item: " + itemString + " - " + e.getMessage());
            }
        }

        // Debug output final inventory state
        System.out.println("Final inventory contents:");
        for (Item item : inventory.getItems()) {
            if (item != null) {
                System.out.println("- " + item.getName() + " x" + item.getCount());
            }
        }
    }

    private Table createSlotCell(int index, Item item) {
        Table cell = new Table();
        boolean isSelected = index == player.getInventory().getSelectedIndex();

        TextureRegionDrawable slotBg = new TextureRegionDrawable(
            gameAtlas.findRegion(isSelected ? "slot_selected" : "slot_normal")
        );
        cell.setBackground(slotBg);

        Table contentStack = new Table();
        contentStack.setFillParent(true);

        // Slot number
        Label numberLabel = new Label(String.valueOf(index + 1),
            new Label.LabelStyle(font, Color.WHITE));
        numberLabel.setFontScale(0.9f);
        contentStack.add(numberLabel).top().left().pad(2);
        contentStack.row();

        // Item container
        Table itemContainer = new Table();
        if (item != null) {
            ImageButton itemIcon = new ImageButton(new TextureRegionDrawable(item.getIcon()));
            itemContainer.add(itemIcon).size(44).padBottom(6);

            if (item.getCount() > 1) {
                Table countContainer = new Table();
                countContainer.setBackground(
                    new TextureRegionDrawable(gameAtlas.findRegion("count_bubble"))
                );

                Label countLabel = new Label(
                    String.format("%dx", item.getCount()),
                    new Label.LabelStyle(font, Color.BLACK)
                );
                countLabel.setFontScale(0.8f);
                countContainer.add(countLabel).pad(2);

                Table countWrapper = new Table();
                countWrapper.add(countContainer).right().bottom();
                countWrapper.setPosition(50, 48);
                cell.addActor(countWrapper);
            }
        }

        contentStack.add(itemContainer).expand().center();
        cell.add(contentStack).grow();

        return cell;
    }private void createHotbarUI() {
        if (hotbarTable != null) {
            hotbarTable.remove();
        }

        hotbarTable = new Table();
        hotbarTable.setFillParent(true);
        hotbarTable.bottom();
        hotbarTable.padBottom(20f);

        Table slotsTable = new Table();
        slotsTable.setBackground(
            new TextureRegionDrawable(gameAtlas.findRegion("hotbar_bg"))
        );
        slotsTable.pad(4f);

        List<Item> items = player.getInventory().getItems();
        // Only show HOTBAR_SIZE slots instead of full inventory
        for (int i = 0; i < Inventory.HOTBAR_SIZE; i++) {
            Table slotCell = createSlotCell(i, items.get(i));
            slotsTable.add(slotCell).size(64).pad(2);
        }

        hotbarTable.add(slotsTable);
        uiStage.addActor(hotbarTable);
    }



    public PlayerData getCurrentPlayerState() {
        PlayerData currentState = new PlayerData(player.getUsername());
        currentState.updateFromPlayer(player);
        return currentState;
    }

    private Color getSlotNumberColor(boolean isSelected) {
        return isSelected ? Color.WHITE : Color.LIGHT_GRAY;
    }

    private Color getCountBackgroundColor() {
        return new Color(0, 0, 0, 0.7f);
    }

    private void updateSlotVisuals(Table cell, boolean isSelected) {
        cell.setBackground(new TextureRegionDrawable(
            gameAtlas.findRegion(isSelected ? "slot_selected" : "slot_normal")
        ));
    }

    private void updateGameState() {
        if (gameClient != null && player != null) {
            PlayerData currentState = new PlayerData(player.getUsername());
            currentState.updateFromPlayer(
                player
            );

            gameClient.updateLastKnownState(currentState);
        }
    }

    private void setupInputProcessors() {
        inputMultiplexer = new InputMultiplexer();

        // 1. Menu input processor
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

        // 2. Inventory hotkey processor
        inputMultiplexer.addProcessor(new InputAdapter() {
            @Override
            public boolean scrolled(float amountX, float amountY) {
                int selectedIndex = player.getInventory().getSelectedIndex();
                if (amountY > 0) {
                    selectedIndex = (selectedIndex + 1) % Inventory.INVENTORY_SIZE;
                } else if (amountY < 0) {
                    selectedIndex = (selectedIndex - 1 + Inventory.INVENTORY_SIZE) % Inventory.INVENTORY_SIZE;
                }
                player.getInventory().selectItem(selectedIndex);
                updateHotbarUI();
                return true;
            }

            @Override
            public boolean keyDown(int keycode) {
                if (keycode >= Input.Keys.NUM_1 && keycode <= Input.Keys.NUM_9) {
                    int index = keycode - Input.Keys.NUM_1;
                    player.getInventory().selectItem(index);
                    updateHotbarUI();
                    return true;
                }
                return false;
            }
        });

        // 3. UI Stage processors
        inputMultiplexer.addProcessor(uiStage);
        inputMultiplexer.addProcessor(gameMenu.getStage());

        // 4. Game input processor last
        inputMultiplexer.addProcessor(inputHandler);

        // Set the input processor
        Gdx.input.setInputProcessor(inputMultiplexer);
    }

    @Override
    public void show() {
        // Called when this screen becomes the current screen
    }

    private void sendPlayerUpdate(float delta) {
        updateTimer += delta;

        if (updateTimer >= UPDATE_INTERVAL && isMultiplayer && gameClient != null) {
            updateTimer = 0;

            // Send player update

            // Send inventory data periodically
            List<String> itemNames = player.getInventory().getItemNames();
            gameClient.sendInventoryUpdate(player.getUsername(), itemNames);
        }
    }


    private NetworkProtocol.PlayerUpdate collectPlayerUpdate() {
        NetworkProtocol.PlayerUpdate update = new NetworkProtocol.PlayerUpdate();
        update.username = player.getUsername();

        // Use raw coordinates
        update.x = player.getX();
        update.y = player.getY();

        update.direction = player.getDirection();
        update.isMoving = player.isMoving();
        update.wantsToRun = player.isRunning();

        return update;
    }


    private void updateOtherPlayers() {
        // Retrieve all other players from the GameClient
        Map<String, OtherPlayer> networkPlayers = gameClient.getOtherPlayers();

        // Debug: print how many network players are being fetched
        System.out.println("Number of other players in network: " + networkPlayers.size());

        for (Map.Entry<String, OtherPlayer> entry : networkPlayers.entrySet()) {
            String username = entry.getKey();
            OtherPlayer netUpdate = entry.getValue();

            if (!username.equals(player.getUsername()) && netUpdate != null) {
                // Get the existing player or create a new one
                OtherPlayer op = otherPlayers.get(username);
                if (op == null) {
                    // If the other player doesn't exist yet, create it
                    op = new OtherPlayer(username, netUpdate.getX(), netUpdate.getY(), gameAtlas);
                    otherPlayers.put(username, op);
                    System.out.println("Creating new OtherPlayer for username: " + username);
                } else {
                    // Update the existing player's state with the network data
                    op.updateFromNetwork(netUpdate);
                    System.out.println("Updating OtherPlayer for username: " + username);
                }
            }
        }

        // Remove any players that are no longer in the network player list
        otherPlayers.keySet().retainAll(networkPlayers.keySet());
    }


    @Override
    public void render(float delta) {
        float deltaTime = Gdx.graphics.getDeltaTime();

        chatSystem.update(delta);
        // Handle player input and update the player's local state
        handleInput();
        player.update(delta);
        player.updatePlayerData(); // Add periodic update


        // Update the camera to follow the player
        camera.position.set(
            player.getX() + (float) Player.FRAME_WIDTH / 2,
            player.getY() + (float) Player.FRAME_HEIGHT / 2,
            0
        );
        camera.update();

        // Prepare to draw the game world
        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        // Update the world based on the player's position and view bounds
        world.update(deltaTime, new Vector2(player.getX(), player.getY()), camera.viewportWidth, camera.viewportHeight);

        // Handle item collection by the player
        List<Item> collectedItems = world.getCollectedItems();
        for (Item item : collectedItems) {
            inventory.addItem(item);
            System.out.println("Added item to inventory: " + item.getName());
        }
        collectedItems.clear(); // Clear the collected items list

        // Clear the screen
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // Calculate view bounds for rendering
        Rectangle viewBounds = new Rectangle(
            camera.position.x - camera.viewportWidth / 2,
            camera.position.y - camera.viewportHeight / 2,
            camera.viewportWidth,
            camera.viewportHeight
        );

        // Multiplayer logic: update and send player state to the server
//        System.out.println(STR."multiplayer mode: \{isMultiplayer}");
//        System.out.println(STR."Is game client null: \{gameClient == null}");
        if (isMultiplayer && gameClient != null) {
            // Send player update immediately every frame
            sendPlayerUpdate(deltaTime);
            updateOtherPlayers(); // Update the state of other players

            // Periodically update the player's state and sync with the server
            stateUpdateTimer += deltaTime;
            if (stateUpdateTimer >= STATE_UPDATE_INTERVAL) {
                stateUpdateTimer = 0;

                // Create PlayerData from the current player state
                PlayerData currentState = new PlayerData(player.getUsername());
                currentState.updateFromPlayer(player);

                // Update the game client with the current state and send it to the server
                gameClient.updateLastKnownState(currentState);
                if (isMultiplayer) {
                    gameClient.sendPlayerUpdateToServer(currentState);
                }
            }
        }

        // Render the world and player
        world.render(batch, viewBounds, player);

        // Update game client (for network processing)
        gameClient.update(deltaTime);

        // Render other players in the game
        for (OtherPlayer otherPlayer : otherPlayers.values()) {
            if (otherPlayer.getTargetPosition() != null) {
                float newX = MathUtils.lerp(otherPlayer.getX(), otherPlayer.getTargetPosition().x, 0.2f);
                float newY = MathUtils.lerp(otherPlayer.getY(), otherPlayer.getTargetPosition().y, 0.2f);
                otherPlayer.setX(newX);
                otherPlayer.setY(newY);
            }
            otherPlayer.render(batch); // Render each other player
        }

        batch.end(); // Finish drawing the world

        // UI rendering
        uiStage.getViewport().apply();
        uiStage.act(delta);
        uiStage.draw();

        // Always render the game menu if active
        if (gameMenu != null) {
            gameMenu.getStage().getViewport().apply();
            gameMenu.getStage().act(delta);
            gameMenu.getStage().draw();
        }    // Render inventory after everything else if it's open
        if (inventoryOpen && inventoryScreen != null) {
            // Save current OpenGL state
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

            // Draw dark overlay with shape renderer
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            shapeRenderer.setColor(0, 0, 0, 0.5f);
            shapeRenderer.rect(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            shapeRenderer.end();

            // Render inventory
            inventoryScreen.render(delta);

            // Reset OpenGL state
            Gdx.gl.glDisable(GL20.GL_BLEND);
        }

    }
    private void handleInput() {  // Add input buffering
        if (Gdx.input.isKeyPressed(Input.Keys.ANY_KEY)) {
            String direction = null;
            if (Gdx.input.isKeyPressed(Input.Keys.W)) direction = "up";
            else if (Gdx.input.isKeyPressed(Input.Keys.S)) direction = "down";
            else if (Gdx.input.isKeyPressed(Input.Keys.A)) direction = "left";
            else if (Gdx.input.isKeyPressed(Input.Keys.D)) direction = "right";

            inputHandler.update();
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.E)) {
            toggleInventory();
        }
    }

    private void toggleInventory() {
        inventoryOpen = !inventoryOpen;

        if (inventoryOpen) {
            if (inventoryScreen == null) {
                inventoryScreen = new InventoryScreen(player, skin);
            }

            inputMultiplexer = new InputMultiplexer(
                inventoryScreen.getStage(), // Set Inventory input first
                inputHandler                // Game input second
            );
            Gdx.input.setInputProcessor(inputMultiplexer);
        } else {
            // Properly reset and nullify inventory screen when closed
            if (inventoryScreen != null) {
                inventoryScreen.dispose();
                inventoryScreen = null;
            }

            inputMultiplexer = new InputMultiplexer(
                uiStage,     // UI input
                inputHandler // Game input
            );
            Gdx.input.setInputProcessor(inputMultiplexer);
            setupInputProcessors();
        }
        updateHotbarUI();
    }


    @Override
    public void resize(int width, int height) {
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();
        if (chatSystem != null) {
            chatSystem.resize(width, height);
        }

        uiStage.getViewport().update(width, height, true);
        createHotbarUI();
        if (gameMenu != null) {
            gameMenu.resize(width, height);
        }
        if (gameMenu != null) {
            gameMenu.resize(width, height);
        }
    }

    private void updateHotbarUI() {
        createHotbarUI();
    }

    private Item generateRandomItem() {
        List<String> itemNames = new ArrayList<>(ItemManager.getAllItemNames());
        int index = MathUtils.random(itemNames.size() - 1);
        String itemName = itemNames.get(index);
        return ItemManager.getItem(itemName);
    }

    public void handlePickupAction() {
        WorldObject nearestPokeball = world.getNearestPokeball();
        if (nearestPokeball != null && nearestPokeball.canBePickedUpBy(player)) {
            world.removeWorldObject(nearestPokeball);

            // Generate single random item
            Item randomItem = generateRandomItem();
            randomItem.setCount(1); // Ensure count is 1

            // Add to inventory
            player.getInventory().addItem(randomItem);
            updateHotbarUI();

            AudioManager.getInstance().playSound(AudioManager.SoundEffect.ITEM_PICKUP);
            player.updatePlayerData();
            System.out.println("Picked up: " + randomItem.getName() + " (Count: " + randomItem.getCount() + ")");
        }
    }


    @Override
    public void pause() {
        // Implement if needed
    }

    @Override
    public void resume() {
        // Implement if needed
    }

    @Override
    public void hide() {
        // Implement if needed
    }

    public void resetInputProcessor() {
        Gdx.input.setInputProcessor(inputMultiplexer);
    }

    @Override
    public void dispose() {
        batch.dispose();if (shapeRenderer != null) {
            shapeRenderer.dispose();
        }

        try {
            if (player != null) {
                // Get final state
                PlayerData finalState = getCurrentPlayerState();
                System.out.println("Final player state before save:");
                System.out.println("Position: " + finalState.getX() + "," + finalState.getY());
                System.out.println("Inventory: " + finalState.getInventoryItems());

                // Update world data
                WorldData worldData = world.getWorldData();
                worldData.savePlayerData(player.getUsername(), finalState);

                // Save to storage
                game.getWorldManager().saveWorld(worldData);

                System.out.println("Player state saved successfully");
            }

        } catch (Exception e) {
            System.err.println("Error saving final state: " + e.getMessage());
        }

        // Cleanup resources
        batch.dispose();
        if (gameClient != null) {
            gameClient.dispose();
        }

        // Clean up other resources
        gameAtlas.dispose();
        player.dispose();
        if (gameClient != null) {
            gameClient.dispose(); // Dispose of game client if in multiplayer
        }

        // Ensure other players are disposed of
        for (OtherPlayer op : otherPlayers.values()) {
            op.dispose();
        }

        // Ensure save directories are created if necessary
        ensureSaveDirectories();
        if (gameClient != null) {
            gameClient.dispose(); // This will trigger final save
        }
        // Update player coordinates in the database when the game screen is disposed
        DatabaseManager dbManager = game.getDatabaseManager();
        dbManager.updatePlayerCoordinates(player.getUsername(), (int) player.getX(), (int) player.getY());
        System.out.println("Player coordinates updated in database.");
    }

}

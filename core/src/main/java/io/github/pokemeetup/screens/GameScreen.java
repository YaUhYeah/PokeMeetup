package io.github.pokemeetup.screens;

import com.badlogic.gdx.*;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.pokemeetup.CreatureCaptureGame;
import io.github.pokemeetup.managers.Network;
import io.github.pokemeetup.multiplayer.OtherPlayer;
import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.multiplayer.client.GameClientSingleton;
import io.github.pokemeetup.system.InputHandler;
import io.github.pokemeetup.system.PickupActionHandler;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.PlayerData;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.WorldObject;
import io.github.pokemeetup.system.gameplay.overworld.multiworld.WorldData;
import io.github.pokemeetup.system.gameplay.overworld.multiworld.WorldManager;
import io.github.pokemeetup.system.inventory.Inventory;
import io.github.pokemeetup.system.inventory.Item;
import io.github.pokemeetup.system.inventory.ItemManager;

import javax.swing.event.ChangeEvent;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GameScreen implements Screen, PickupActionHandler {
    public static final int WORLD_SIZE = 1600;
    private static final float STATE_UPDATE_INTERVAL = 1.0f; // Update state every second
    private static final float UPDATE_INTERVAL = 0.1f; // 10 times per second
    private static final int WORLD_WIDTH = 1600; // 50 chunks wide
    private static final int WORLD_HEIGHT = 1600; // 50 chunks high
    private final Player player;
    private final WorldManager worldManager;
    private final WorldData currentWorld;
    private final String worldName;
    private final CreatureCaptureGame game;
    private InputMultiplexer inputMultiplexer;
    private float stateUpdateTimer = 0;
    private GameMenu gameMenu;
    private float updateTimer = 0;
    private GameClient gameClient;
    private HashMap<String, OtherPlayer> otherPlayers = new HashMap<>();
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
    private Inventory inventory;
    private Table hotbarTable;
    private Table fixedHotbarTable;
    private PlayerData playerData;

    public GameScreen(CreatureCaptureGame game, String username, GameClient gameClient, int initialX, int initialY, String worldName) {
        this.worldName = worldName;
        this.game = game;
        this.gameClient = gameClient;
        uiStage = new Stage(new ScreenViewport());
        if (gameClient != null) {
            gameClient.setLocalUsername(username);
            System.out.println("Set username in GameClient: " + username);
        }
        uiSkin = new Skin(Gdx.files.internal("Skins/uiskin.json")); // Ensure this file exists
        this.isMultiplayer = (gameClient != null);
        int pixelX = initialX * World.TILE_SIZE;
        int pixelY = initialY * World.TILE_SIZE;
        this.worldManager = new WorldManager();
        worldManager.init();
        this.currentWorld = worldManager.getWorld(worldName);
        this.username = username;
        if (currentWorld == null) {
            throw new IllegalStateException("World " + worldName + " not found");
        }
        if (isMultiplayer) {
            try {
                this.gameClient = GameClientSingleton.getInstance();
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
            System.out.println("Initializing ItemManager");
            ItemManager.initialize(gameAtlas);
        }
        if (!ItemManager.isInitialized()) {
            throw new RuntimeException("Failed to initialize ItemManager");
        }
        ItemManager.initialize(gameAtlas);
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
        font = new BitmapFont(Gdx.files.internal("Fonts/pkmn.fnt"));

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

        // Get saved player data
        PlayerData savedPlayerData = world.getWorldData().getPlayerData(username);
        System.out.println("Retrieved saved player data: " + (savedPlayerData != null ?
            "pos(" + savedPlayerData.getX() + "," + savedPlayerData.getY() + ")" : "null"));

        // Use saved position if available, otherwise use initial position
        float startX = savedPlayerData != null ? savedPlayerData.getX() : initialX;
        float startY = savedPlayerData != null ? savedPlayerData.getY() : initialY;

        System.out.println("Creating player at position: " + startX + "," + startY);

        // Create player with correct position
        player = new Player((int) startX, (int) startY, world, gameAtlas, username);
        world.setPlayer(player);

        // Set direction if available
        if (savedPlayerData != null) {
            player.setDirection(savedPlayerData.convertDirectionIntToString(savedPlayerData.getDirection()));
            player.setRunning(savedPlayerData.isWantsToRun());
        }

        // Initialize player data
        PlayerData playerData = new PlayerData(username);
        playerData.setUsername(username);  // Ensure username is set
        playerData.setPosition(startX, startY);
        playerData.setDirection(player.getDirection());
        playerData.setWantsToRun(player.isRunning());

        // Load inventory if exists
        if (savedPlayerData != null && savedPlayerData.getInventoryItemNames() != null) {
            System.out.println("Available items before loading: " + ItemManager.getAllItemNames());
            loadInventoryFromStrings(player.getInventory(), savedPlayerData.getInventoryItemNames());
        }
        if (world != null) {
            world.setPlayerData(playerData);
            System.out.println("Set PlayerData in world for user: " + username);
        }
        System.out.println("GameScreen initialization complete. PlayerData null? " +
            (world.getPlayerData() == null));

        System.out.println("Final player position: " + player.getX() + "," + player.getY());

        createHotbarUI();
        //        WorldData worldData = world.getWorldData();
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
        if (playerData.getInventory() != null) {
            loadInventoryFromStrings(player.getInventory(), playerData.getInventory());
            System.out.println("Loaded inventory with " +
                (playerData.getInventory() != null ? playerData.getInventory().size() : 0) + " items");
        }
        System.out.println("GameScreen initialization complete. PlayerData null? " +
            (world.getPlayerData() == null));
        if (playerData.getInventory() != null) {
            loadInventoryFromStrings(player.getInventory(), playerData.getInventory());
        }
        System.out.println("PlayerData set in World for user: " + player.getPlayerData().getUsername());
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
    }


    private void ensureSaveDirectories() {
        FileHandle saveDir = Gdx.files.local("save");
        if (!saveDir.exists()) {
            saveDir.mkdirs();
        }
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
    }

    private void createHotbarUI() {
        if (hotbarTable != null) {
            hotbarTable.remove();
        }

        hotbarTable = new Table();
        hotbarTable.setFillParent(true);
        hotbarTable.bottom();
        hotbarTable.padBottom(20f);

        Table slotsTable = new Table();
        // Use custom hotbar background
        slotsTable.setBackground(
            new TextureRegionDrawable(gameAtlas.findRegion("hotbar_bg"))
        );
        slotsTable.pad(4f);

        List<Item> items = player.getInventory().getItems();
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            Table slotCell = createSlotCell(i, items.get(i));
            slotsTable.add(slotCell).size(64).pad(2);
        }

        hotbarTable.add(slotsTable);
        uiStage.addActor(hotbarTable);
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
            currentState.setPosition(player.getX(), player.getY());
            currentState.setDirection(player.getDirection());
            currentState.setMoving(player.isMoving());
            currentState.setWantsToRun(player.isRunning());
            currentState.setInventory(player.getInventory().getItemNames());

            gameClient.updateLastKnownState(currentState);
        }
    }

    private void setupInputProcessors() {
        inputMultiplexer = new InputMultiplexer();

        // 1. Menu input processor
        inputMultiplexer.addProcessor(new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.ENTER || keycode == Input.Keys.ESCAPE) {
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
        if (updateTimer >= UPDATE_INTERVAL) {
            updateTimer = 0;
            Network.PlayerUpdate update = collectPlayerUpdate();
            gameClient.sendPlayerUpdate(update);

            // Send inventory data using the new method
            List<String> itemNames = player.getInventory().getItemNames();
            gameClient.sendInventoryUpdate(player.getUsername(), itemNames);
        }
    }


    private Network.PlayerUpdate collectPlayerUpdate() {
        Network.PlayerUpdate update = new Network.PlayerUpdate();
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
        Map<String, Network.PlayerUpdate> networkPlayers = gameClient.getOtherPlayers();
        for (Map.Entry<String, Network.PlayerUpdate> entry : networkPlayers.entrySet()) {
            String username = entry.getKey();
            if (username != null && !username.equals(player.getUsername())) {
                Network.PlayerUpdate netUpdate = entry.getValue();
                OtherPlayer op = otherPlayers.get(username);

                // No need for coordinate conversion here since positions are in pixels
                if (op == null && netUpdate != null) {
                    op = new OtherPlayer(username, netUpdate.x, netUpdate.y, gameAtlas);
                    otherPlayers.put(username, op);
                }

                if (op != null && netUpdate != null) {
                    op.updateFromNetwork(netUpdate);
                }
            }
        }
        otherPlayers.keySet().retainAll(networkPlayers.keySet());
    }

    @Override
    public void render(float delta) {
        float deltaTime = Gdx.graphics.getDeltaTime();
        // Send player's state to the server        // Update
        handleInput();
        player.update(deltaTime);

        // Update camera to follow player
        camera.position.set(
            player.getX() + (float) Player.FRAME_WIDTH / 2,
            player.getY() + (float) Player.FRAME_HEIGHT / 2,
            0
        );
        camera.update();    // Render
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        if (gameClient != null) {
            // Update last known state periodically
            PlayerData currentState = new PlayerData(player.getUsername());
            currentState.setPosition(player.getX(), player.getY());
            currentState.setDirection(player.getDirection());
            currentState.setMoving(player.isMoving());
            currentState.setWantsToRun(player.isRunning());
            currentState.setInventory(player.getInventory().getItemNames());
            gameClient.updateLastKnownState(currentState);
        }
        // Update world based on player position
        world.update(deltaTime, new Vector2(player.getX(), player.getY()),
            camera.viewportWidth, camera.viewportHeight);
        // Collect items picked up by the player
        List<Item> collectedItems = world.getCollectedItems();
        for (Item item : collectedItems) {
            inventory.addItem(item);
            System.out.println("Added item to inventory: " + item.getName());
        }
        // Clear the collected items list
        collectedItems.clear();

        // Clear screen
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);


        // Calculate view bounds for rendering
        Rectangle viewBounds = new Rectangle(
            camera.position.x - camera.viewportWidth / 2,
            camera.position.y - camera.viewportHeight / 2,
            camera.viewportWidth,
            camera.viewportHeight
        );
        if (isMultiplayer) {
            sendPlayerUpdate(delta);
            updateOtherPlayers();
        }    // Update state periodically
        stateUpdateTimer += delta;
        if (stateUpdateTimer >= STATE_UPDATE_INTERVAL) {
            stateUpdateTimer = 0;
            updateGameState();
        }

        world.render(batch, viewBounds, player);
        for (OtherPlayer op : otherPlayers.values()) {
            op.render(batch);
            System.out.println("OtherPlayer " + op.getUsername() + " at position (" + op.getX() + ", " + op.getY() + ")");
        }
        batch.end();    // UI rendering with screen coordinates
        // UI rendering
        uiStage.getViewport().apply();
        uiStage.act(delta);
        uiStage.draw();

        // Menu rendering - IMPORTANT: Always render the menu stage
        if (gameMenu != null) {
            gameMenu.getStage().getViewport().apply();
            gameMenu.getStage().act(delta);
            gameMenu.getStage().draw();
        }
    }

    private void handleInput() {
        // Add input buffering
        if (Gdx.input.isKeyPressed(Input.Keys.ANY_KEY)) {
            String direction = null;
            if (Gdx.input.isKeyPressed(Input.Keys.W)) direction = "up";
            else if (Gdx.input.isKeyPressed(Input.Keys.S)) direction = "down";
            else if (Gdx.input.isKeyPressed(Input.Keys.A)) direction = "left";
            else if (Gdx.input.isKeyPressed(Input.Keys.D)) direction = "right";

            inputHandler.update();
        }
    }

    @Override
    public void resize(int width, int height) {
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();

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
        batch.dispose();
        if (gameClient != null && player != null) {
            // Ensure final state is saved
            PlayerData finalState = new PlayerData(player.getUsername());
            finalState.setPosition(player.getX(), player.getY());
            finalState.setDirection(player.getDirection());
            finalState.setMoving(player.isMoving());
            finalState.setWantsToRun(player.isRunning());
            finalState.setInventory(player.getInventory().getItemNames());

            System.out.println("GameScreen disposing - Saving final state for: " + player.getUsername());
            gameClient.updateLastKnownState(finalState);
            gameClient.savePlayerState(finalState);
        }
        gameAtlas.dispose();
        player.dispose();
        gameClient.dispose();
        for (OtherPlayer op : otherPlayers.values()) {
            op.dispose();
        }
        ensureSaveDirectories();

    }
}

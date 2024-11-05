package io.github.pokemeetup;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.TextureAtlasLoader;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import io.github.pokemeetup.audio.AudioManager;
import io.github.pokemeetup.managers.BiomeManager;
import io.github.pokemeetup.managers.DatabaseManager;
import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.multiplayer.client.GameClientSingleton;
import io.github.pokemeetup.multiplayer.server.ServerStorageSystem;
import io.github.pokemeetup.multiplayer.server.config.ServerConnectionConfig;
import io.github.pokemeetup.pokemon.PokemonParty;
import io.github.pokemeetup.pokemon.data.PokemonDatabase;
import io.github.pokemeetup.screens.LoadingScreen;
import io.github.pokemeetup.screens.ModeSelectionScreen;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.data.PlayerData;
import io.github.pokemeetup.system.data.WorldData;
import io.github.pokemeetup.system.gameplay.inventory.Inventory;
import io.github.pokemeetup.system.gameplay.inventory.ItemManager;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.WorldObject;
import io.github.pokemeetup.system.gameplay.overworld.multiworld.WorldManager;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.TextureManager;
import io.github.pokemeetup.utils.storage.JsonConfig;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import static io.github.pokemeetup.screens.GameScreen.WORLD_HEIGHT;
import static io.github.pokemeetup.system.gameplay.overworld.World.WORLD_SIZE;

public class CreatureCaptureGame extends Game {
    public static final String MULTIPLAYER_WORLD_NAME = "multiplayer_world";
    public static final long MULTIPLAYER_WORLD_SEED = 12345L;
    private final Map<String, TextureAtlas> loadedAtlases = new HashMap<>();
    private boolean isMultiplayerMode = false;
    private WorldManager worldManager;
    private GameClient gameClient;
    private BiomeManager biomeManager;
    private Player player;
    private World currentWorld;
    private String currentWorldName;
    private DatabaseManager databaseManager;
    private LoadingScreen loadingScreen;
    private boolean assetsLoaded = false;
    private AssetManager assetManager;
    private Stack<Screen> screenStack = new Stack<>();
    private Skin uiSkin;

    public CreatureCaptureGame() {
    }

    public boolean isMultiplayerMode() {
        return isMultiplayerMode;
    }

    public void setMultiplayerMode(boolean isMultiplayer) {
        this.isMultiplayerMode = isMultiplayer;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public AssetManager getAssetManager() {
        return assetManager;
    }

    @Override
    public void create() {
        // Step 1: Initialize AssetManager and queue assets
        assetManager = new AssetManager();
        queueAssets();

        // Step 2: Force synchronous loading (since we can't use render)
        GameLogger.info("Loading assets...");
        assetManager.finishLoading(); // This will block until all assets are loaded
        GameLogger.info("Assets loaded successfully");

        // Step 3: Initialize managers with loaded assets
        initializeManagers();

        // Step 4: Initialize game systems
        ServerStorageSystem serverStorageSystem = new ServerStorageSystem();
        this.worldManager = WorldManager.getInstance(serverStorageSystem, isMultiplayerMode);
        this.worldManager.init();

        // Step 5: Set initial screen
        setScreen(new ModeSelectionScreen(this));

        GameLogger.info("Game initialization complete");
    }

    public void initializeMultiplayerWorld(String worldName, long seed, WorldData serverWorldData) throws IOException {
        GameLogger.info("Initializing multiplayer world from server data: " + worldName);

        try {
            // Initialize GameClient first if needed
            if (gameClient == null) {
                gameClient = GameClientSingleton.getInstance(ServerConnectionConfig.getInstance());
                if (gameClient == null) {
                    throw new IllegalStateException("Failed to initialize GameClient");
                }
            }

            // Initialize BiomeManager with server seed
            BiomeManager biomeManager = new BiomeManager(seed);

            // Create world instance first
            World world = new World(
                worldName,
                WORLD_SIZE,
                WORLD_HEIGHT,
                seed,
                gameClient, // Now guaranteed to be initialized
                biomeManager
            );

            // Initialize player after world creation
            if (this.player == null && gameClient != null) {
                GameLogger.info("Creating new player instance...");

                // Get player position from server data or use defaults
                float playerX = serverWorldData != null && serverWorldData.getSpawnX() != 0 ?
                    serverWorldData.getSpawnX() * World.TILE_SIZE : 800;
                float playerY = serverWorldData != null && serverWorldData.getSpawnY() != 0 ?
                    serverWorldData.getSpawnY() * World.TILE_SIZE : 800;

                this.player = new Player(
                    (int) playerX,
                    (int) playerY,
                    world, // Pass the created world
                    gameClient.getLocalUsername()
                );

                // Initialize player components
                this.player.setPokemonParty(new PokemonParty());
                this.player.setInventory(new Inventory());

                GameLogger.info("Player initialized at position: " + playerX + "," + playerY);
            }

            // Set world data from server if available
            if (serverWorldData != null) {
                world.setWorldData(serverWorldData);
                GameLogger.info("Server world data applied");
            }

            // Update world state after player initialization
            world.setPlayer(this.player);
            if (this.player != null) {
                this.player.setWorld(world);
            }

            // Initialize network handlers
            initializeNetworkHandlers(world);

            // Update game state
            this.currentWorld = world;
            this.isMultiplayerMode = true;

            GameLogger.info("Multiplayer world initialization complete");

        } catch (Exception e) {
            GameLogger.error("Failed to initialize multiplayer world: " + e.getMessage());
            e.printStackTrace();
            throw new IOException("World initialization failed", e);
        }
    }

    // Update the dispose method with proper null checks
    @Override
    public void dispose() {
        try {
            saveGame();

            if (player != null) {
                player.dispose();
            }

            if (currentWorld != null) {
                try {
                    WorldData worldData = currentWorld.getWorldData();
                    if (worldData != null && player != null) {
                        PlayerData finalState = new PlayerData(player.getUsername());
                        player.updatePlayerData();
                        finalState.updateFromPlayer(player);
                        worldData.savePlayerData(player.getUsername(), finalState);
                        worldManager.saveWorld(worldData);
                    }
                } catch (Exception e) {
                    GameLogger.error("Error saving world data during dispose: " + e.getMessage());
                }
                currentWorld.dispose();
            }

            if (gameClient != null) {
                gameClient.dispose();
            }

            if (loadingScreen != null) {
                loadingScreen.dispose();
            }

            AudioManager.getInstance().dispose();

            if (assetManager != null) {
                assetManager.dispose();
            }

            // Clear loaded atlases
            loadedAtlases.clear();

        } catch (Exception e) {
            GameLogger.error("Error during game disposal: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Add helper method to safely save game state
    public void saveGame() {
        if (player == null || currentWorld == null || currentWorldName == null) {
            GameLogger.info("Cannot save game - required components are null");
            return;
        }

        try {
            WorldData worldData = worldManager.getWorld(currentWorldName);
            if (worldData != null) {
                // Update player data
                PlayerData playerData = new PlayerData(player.getUsername());
                player.updatePlayerData();
                playerData.updateFromPlayer(player);
                worldData.savePlayerData(player.getUsername(), playerData);
                GameLogger.info("PlayerData updated for " + player.getUsername());

                JsonConfig.saveWorldDataWithPlayer(worldData, playerData);
                GameLogger.info("WorldData serialized and saved for world: " + currentWorldName);
            }
        } catch (Exception e) {
            GameLogger.error("Failed to save game: " + e.getMessage());
        }
    }

    private void applyPlayerData(Player player, PlayerData playerData) {
        try {
            // Update position
            player.setX(playerData.getX());
            player.setY(playerData.getY());

            // Update inventory
            if (playerData.getInventoryItems() != null) {
                player.getInventory().setAllItems(playerData.getInventoryItems());
            }

            // Update Pokemon party if exists
            if (playerData.getPartyPokemon() != null) {
                player.setPokemonParty(player.getPokemonParty());
            }

            // Update other player stats/data
            player.setDirection(playerData.getDirection());
            player.setMoving(playerData.isMoving());
            player.setRunning(playerData.isWantsToRun());

            GameLogger.info("Applied player data successfully - Position: " +
                player.getX() + "," + player.getY());

        } catch (Exception e) {
            GameLogger.error("Error applying player data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void initializeNetworkHandlers(World world) {
        gameClient.setWorldObjectHandler(update -> {
            if (world != null && world.getObjectManager() != null) {
                world.getObjectManager().handleNetworkUpdate(update);
            }
        });

        gameClient.setPokemonUpdateHandler(update -> {
            if (world != null && world.getPokemonSpawnManager() != null) {
                world.getPokemonSpawnManager().handleNetworkUpdate(update);
            }
        });
        gameClient.setTimeSyncHandler(update -> {
            if (world.getWorldData() != null) {
                world.getWorldData().setWorldTimeInMinutes(update.worldTimeInMinutes);
                world.getWorldData().setPlayedTime(update.playedTime);
            }
        });

    }

    public World getCurrentWorld() {
        return currentWorld;
    }

    public void initializeWorld(String worldName, boolean isMultiplayer) throws IOException {
        this.currentWorldName = worldName;
        this.isMultiplayerMode = isMultiplayer;

        GameLogger.info("Initializing " + (isMultiplayer ? "multiplayer" : "singleplayer") +
            " world: " + worldName);

        // Get or create world data
        WorldData worldData = worldManager.getWorld(worldName);
        if (worldData == null) {
            if (isMultiplayer) {
                throw new IllegalStateException("Multiplayer world not found: " + worldName);
            }
            // Create new singleplayer world
            worldData = worldManager.createWorld(
                worldName,
                System.currentTimeMillis(),
                0.9f,
                0.5f
            );
        }

        // Initialize appropriate client
        gameClient = isMultiplayer ?
            GameClientSingleton.getInstance(ServerConnectionConfig.getInstance()) :
            GameClientSingleton.getSinglePlayerInstance();

        // Initialize player
        String username = isMultiplayer ? gameClient.getLocalUsername() : "Player";
        PlayerData savedPlayerData = worldData.getPlayerData(username);

        // Create world
        currentWorld = new World(worldName, WORLD_SIZE, WORLD_SIZE,
            worldData.getConfig().getSeed(), gameClient, biomeManager);

        // Initialize player
        if (savedPlayerData != null) {
            player = new Player(
                (int) savedPlayerData.getX(),
                (int) savedPlayerData.getY(),
                currentWorld, username);
            savedPlayerData.applyToPlayer(player);
        } else {
            player = new Player(
                World.DEFAULT_X_POSITION,
                World.DEFAULT_Y_POSITION,
                currentWorld,
                username
            );
        }
        currentWorld.setWorldData(worldData);
        currentWorld.setPlayer(player);
        GameLogger.info("World initialized successfully");
    }

    private void queueAssets() {
        // Queue all atlas files for loading
        String[] atlasFiles = {
            "atlas/ui-gfx-atlas",
            "atlas/back-gfx-atlas",
            "atlas/front-gfx-atlas",
            "atlas/boy-gfx-atlas",
            "atlas/tiles-gfx-atlas",
            "atlas/icon-gfx-atlas",
            "atlas/items-gfx-atlas",
            "atlas/overworld-gfx-atlas",
            "atlas/battlebacks-gfx-atlas"
        };

        // Verify and queue each atlas
        for (String path : atlasFiles) {
            if (!Gdx.files.internal(path).exists()) {
                GameLogger.error("Missing required atlas: " + path);
                throw new RuntimeException("Required atlas file not found: " + path);
            }
            assetManager.load(path, TextureAtlas.class);
        }

        // Verify pokemon.json exists
        if (!Gdx.files.internal("data/pokemon.json").exists()) {
            GameLogger.error("Missing required pokemon data file: data/pokemon.json");
            throw new RuntimeException("Required pokemon data file not found");
        }

        // Configure loading parameters
        assetManager.setLoader(TextureAtlas.class, new TextureAtlasLoader(new InternalFileHandleResolver()));

        GameLogger.info("Asset loading queued");
    }

    private void initializeManagers() {
        try {
            GameLogger.info("Initializing managers with loaded assets...");

            // Get all loaded atlases
            TextureAtlas battleAtlas = assetManager.get("atlas/battlebacks-gfx-atlas", TextureAtlas.class);
            TextureAtlas uiAtlas = assetManager.get("atlas/ui-gfx-atlas", TextureAtlas.class);
            TextureAtlas backAtlas = assetManager.get("atlas/back-gfx-atlas", TextureAtlas.class);
            TextureAtlas frontAtlas = assetManager.get("atlas/front-gfx-atlas", TextureAtlas.class);
            TextureAtlas iconAtlas = assetManager.get("atlas/icon-gfx-atlas", TextureAtlas.class);
            TextureAtlas overworldAtlas = assetManager.get("atlas/overworld-gfx-atlas", TextureAtlas.class);
            TextureAtlas itemsAtlas = assetManager.get("atlas/items-gfx-atlas", TextureAtlas.class);
            TextureAtlas boyAtlas = assetManager.get("atlas/boy-gfx-atlas", TextureAtlas.class);
            TextureAtlas tilesAtlas = assetManager.get("atlas/tiles-gfx-atlas", TextureAtlas.class);

            // Initialize managers in correct order
            TextureManager.initialize(
                battleAtlas,
                uiAtlas,
                backAtlas,
                frontAtlas,
                iconAtlas,
                overworldAtlas,
                itemsAtlas,
                boyAtlas,
                tilesAtlas
            );
            PokemonDatabase.initialize();

            ItemManager.initialize(TextureManager.items);
            AudioManager.getInstance();

            // Initialize game systems
            ServerStorageSystem serverStorageSystem = new ServerStorageSystem();

            this.worldManager = WorldManager.getInstance(serverStorageSystem, isMultiplayerMode);
            this.biomeManager = new BiomeManager(System.currentTimeMillis());
            this.worldManager.init();

            GameLogger.info("Managers initialized successfully");

        } catch (Exception e) {
            GameLogger.error("Failed to initialize managers: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to initialize game managers", e);
        }
    }

    public BiomeManager getBiomeManager() {
        return biomeManager;
    }


    public void setScreenWithoutDisposing(Screen newScreen) {
        Screen currentScreen = getScreen();
        if (currentScreen != null) {
            currentScreen.pause(); // Pause the current screen
        }
        setScreen(newScreen);
    }


    public Player getPlayer() {
        return player;
    }

    public void setPlayer(Player player) {
        this.player = player;
    }

    public WorldManager getWorldManager() {
        return worldManager;
    }
}

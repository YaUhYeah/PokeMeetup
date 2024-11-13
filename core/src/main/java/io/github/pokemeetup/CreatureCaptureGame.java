package io.github.pokemeetup;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.TextureAtlasLoader;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import io.github.pokemeetup.audio.AudioManager;
import io.github.pokemeetup.managers.BiomeManager;
import io.github.pokemeetup.managers.DatabaseManager;
import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.multiplayer.client.GameClientSingleton;
import io.github.pokemeetup.multiplayer.server.GameStateHandler;
import io.github.pokemeetup.multiplayer.server.ServerStorageSystem;
import io.github.pokemeetup.multiplayer.server.config.ServerConnectionConfig;
import io.github.pokemeetup.pokemon.data.PokemonDatabase;
import io.github.pokemeetup.screens.*;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.data.ItemData;
import io.github.pokemeetup.system.data.PlayerData;
import io.github.pokemeetup.system.data.PokemonData;
import io.github.pokemeetup.system.data.WorldData;
import io.github.pokemeetup.system.gameplay.inventory.Inventory;
import io.github.pokemeetup.system.gameplay.inventory.ItemManager;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.multiworld.WorldManager;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.TextureManager;
import io.github.pokemeetup.utils.storage.DesktopFileSystem;
import io.github.pokemeetup.utils.storage.GameFileSystem;
import io.github.pokemeetup.utils.storage.JsonConfig;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static io.github.pokemeetup.system.gameplay.overworld.World.WORLD_SIZE;

public class CreatureCaptureGame extends Game implements GameStateHandler {
    public static final String MULTIPLAYER_WORLD_NAME = "multiplayer_world";
    public static final long MULTIPLAYER_WORLD_SEED = System.currentTimeMillis();
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
    private AssetManager assetManager;

    public CreatureCaptureGame(boolean isAndroid) {
        if (
            !isAndroid) {
            GameFileSystem system = GameFileSystem.getInstance();
            DesktopFileSystem delegate = new DesktopFileSystem();
            system.setDelegate(delegate);
        }
    }

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

        assetManager = new AssetManager();
        queueAssets();
        GameLogger.info("Loading assets...");
        assetManager.finishLoading();
        initializeManagers();

        ServerStorageSystem serverStorageSystem = new ServerStorageSystem();
        this.worldManager = WorldManager.getInstance(serverStorageSystem, isMultiplayerMode);
        this.worldManager.init();

        setScreen(new ModeSelectionScreen(this));

        GameLogger.info("Game initialization complete");
    }


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
        }
    }

    public void saveGame() {
        if (player == null || currentWorld == null || currentWorldName == null) {
            GameLogger.error("Cannot save game - required objects are null");
            return;
        }

        try {
            WorldData worldData = currentWorld.getWorldData();
            if (worldData == null) {
                GameLogger.error("Cannot save game - world data is null");
                return;
            }

            // Create and validate player data
            PlayerData playerData = new PlayerData(player.getUsername());
            player.updatePlayerData();
            playerData.updateFromPlayer(player);

            if (!playerData.validateAndRepairState()) {
                GameLogger.error("Player data validation failed");
                return;
            }

            // Save player data to world
            worldData.savePlayerData(player.getUsername(), playerData);

            // Update world manager
            worldManager.saveWorld(worldData);

            GameLogger.info("Game saved successfully for world: " + currentWorldName);

        } catch (Exception e) {
            GameLogger.error("Failed to save game: " + e.getMessage());
            e.printStackTrace();
        }
    }
    public World getCurrentWorld() {
        return currentWorld;
    }

    // In CreatureCaptureGame.java
    public void initializeWorld(String worldName, boolean isMultiplayer) throws IOException {
        GameLogger.info("Starting world initialization: " + worldName + " (Multiplayer: " + isMultiplayer + ")");
        this.currentWorldName = worldName;
        this.isMultiplayerMode = isMultiplayer;

        try {
            // Set up game client first
            gameClient = isMultiplayer ?
                GameClientSingleton.getInstance(ServerConnectionConfig.getInstance()) :
                GameClientSingleton.getSinglePlayerInstance();

            // Load or create world data
            WorldData worldData = worldManager.loadAndValidateWorld(worldName);
            if (worldData == null && !isMultiplayer) {
                GameLogger.info("Creating new world: " + worldName);
                worldData = worldManager.createWorld(
                    worldName,
                    System.currentTimeMillis(),
                    0.15f,
                    0.05f
                );
            }

            if (worldData == null) {
                throw new IllegalStateException("Failed to load or create world data");
            }

            // Create world instance first
            currentWorld = new World(
                worldName,
                WORLD_SIZE,
                WORLD_SIZE,
                worldData.getConfig().getSeed(),
                gameClient,
                biomeManager
            );
            currentWorld.setWorldData(worldData);

            // Initialize world before creating player
            if (!currentWorld.initialize()) {
                throw new IllegalStateException("World initialization failed");
            }

            // Get saved player data if it exists
            String username = isMultiplayer ? gameClient.getLocalUsername() : "Player";
            PlayerData savedPlayerData = worldData.getPlayerData(username);

            // Create and initialize player
            if (savedPlayerData != null) {
                GameLogger.info(String.format("Loading saved player data - Position: (%.1f, %.1f)",
                    savedPlayerData.getX(), savedPlayerData.getY()));

                player = new Player(
                    (int)savedPlayerData.getX(),
                    (int)savedPlayerData.getY(),
                    currentWorld,  // Pass initialized world
                    username
                );

                // Initialize player resources
                player.initializeResources();

                // Ensure inventory exists
                if (player.getInventory() == null) {
                    player.setInventory(new Inventory());
                }

                // Apply saved data to initialized player
                savedPlayerData.applyToPlayer(player);

            } else {
                // Create new player
                player = new Player(
                    World.DEFAULT_X_POSITION,
                    World.DEFAULT_Y_POSITION,
                    currentWorld,
                    username
                );
                player.initializeResources();
                currentWorld.spawnPlayer(player);
            }

            // Set player in world after initialization
            currentWorld.setPlayer(player);


            if (!currentWorld.isInitialized()) {
                throw new IllegalStateException("World not properly initialized");
            }

            GameLogger.info("World initialization complete: " + worldName);

        } catch (Exception e) {
            GameLogger.error("Failed to initialize world: " + e.getMessage());
            throw new IOException("World initialization failed", e);
        }
    }

    // Add helper method for logging inventory state
    private void logInventoryState(String context) {
        if (player == null || player.getInventory() == null) {
            GameLogger.info(context + ": Inventory is null");
            return;
        }

        List<ItemData> items = player.getInventory().getAllItems();
        GameLogger.info(context + ": Inventory has " +
            items.stream().filter(Objects::nonNull).count() + " items");

        for (int i = 0; i < items.size(); i++) {
            ItemData item = items.get(i);
            if (item != null) {
            }
        }
    }

    private void queueAssets() {
        String[] atlasFiles = {
            "atlas/ui-gfx-atlas.atlas",
            "atlas/back-gfx-atlas",
            "atlas/front-gfx-atlas",
            "atlas/boy-gfx-atlas",
            "atlas/tiles-gfx-atlas",
            "atlas/icon-gfx-atlas",
            "atlas/items-gfx-atlas",
            "atlas/overworld-gfx-atlas",
            "atlas/battlebacks-gfx-atlas",
            "atlas/move-effects-gfx",
            "atlas/mountain-atlas.atlas"
        };

        for (String path : atlasFiles) {
            verifyAssetExists(path);
            assetManager.load(path, TextureAtlas.class);
        }

        // Verify required data files exist
        String[] dataFiles = {
            "Data/pokemon.json",
            "Data/biomes.json",
            "Data/moves.json"
        };
        for (String dataFile : dataFiles) {
            verifyDataFileExists(dataFile);
        }

        assetManager.setLoader(TextureAtlas.class, new TextureAtlasLoader(new InternalFileHandleResolver()));

        GameLogger.info("Asset loading queued");
    }

    private void verifyDataFileExists(String path) {
        try {
            String content = GameFileSystem.getInstance().getDelegate().readString(path);
            if (content == null || content.isEmpty()) {
                throw new RuntimeException("Empty data file: " + path);
            }
            GameLogger.info("Successfully verified data file: " + path);
        } catch (Exception e) {
            GameLogger.error("Failed to verify data file: " + path);
            throw new RuntimeException("Required data file missing: " + path, e);
        }
    }

    @Override
    public void returnToLogin(String message) {
        try {
            if (screen != null) {
                screen.dispose();
            }
            setScreen(new LoginScreen(this));
        } catch (Exception e) {
            GameLogger.error("Error returning to login: " + e.getMessage());
        }
    }

    private void verifyAssetExists(String path) {
        try {
            if (!Gdx.files.internal(path).exists()) {
                String[] alternatives = {
                    path.toLowerCase(),
                    "assets/" + path,
                    path.replace("Data/", "data/")
                };

                boolean found = false;
                for (String alt : alternatives) {
                    if (Gdx.files.internal(alt).exists()) {
                        GameLogger.info("Found asset at alternate path: " + alt);
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    throw new RuntimeException("Required asset not found: " + path +
                        " (tried multiple path variants)");
                }
            }
        } catch (Exception e) {
            GameLogger.error("Error verifying asset: " + path + " - " + e.getMessage());
            throw new RuntimeException("Asset verification failed", e);
        }
    }

    private void initializeManagers() {
        try {

            GameLogger.info("Initializing managers with loaded assets...");
            TextureAtlas battleAtlas = assetManager.get("atlas/battlebacks-gfx-atlas", TextureAtlas.class);
            TextureAtlas uiAtlas = assetManager.get("atlas/ui-gfx-atlas.atlas", TextureAtlas.class);
            TextureAtlas backAtlas = assetManager.get("atlas/back-gfx-atlas", TextureAtlas.class);
            TextureAtlas frontAtlas = assetManager.get("atlas/front-gfx-atlas", TextureAtlas.class);
            TextureAtlas iconAtlas = assetManager.get("atlas/icon-gfx-atlas", TextureAtlas.class);
            TextureAtlas overworldAtlas = assetManager.get("atlas/overworld-gfx-atlas", TextureAtlas.class);
            TextureAtlas itemsAtlas = assetManager.get("atlas/items-gfx-atlas", TextureAtlas.class);
            TextureAtlas boyAtlas = assetManager.get("atlas/boy-gfx-atlas", TextureAtlas.class);
            TextureAtlas effects = assetManager.get("atlas/move-effects-gfx", TextureAtlas.class);
            TextureAtlas mountains = assetManager.get("atlas/mountain-atlas.atlas", TextureAtlas.class);

            TextureManager.debugAtlasState("Boy", boyAtlas);

            if (!verifyAtlas(boyAtlas)) {
                throw new RuntimeException("Boy atlas verification failed");
            }

            TextureAtlas tilesAtlas = assetManager.get("atlas/tiles-gfx-atlas", TextureAtlas.class);

            TextureManager.initialize(
                battleAtlas,
                uiAtlas,
                backAtlas,
                frontAtlas,
                iconAtlas,
                overworldAtlas,
                itemsAtlas,
                boyAtlas,
                tilesAtlas, effects, mountains
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
            throw new RuntimeException("Failed to initialize game managers", e);
        }
    }

    private boolean verifyAtlas(TextureAtlas atlas) {
        if (atlas == null) {
            GameLogger.error("Boy" + " atlas is null");
            return false;
        }

        try {
            for (Texture texture : atlas.getTextures()) {
                if (texture == null) {
                    GameLogger.error("Boy" + " atlas has invalid textures");
                    return false;
                }
            }
            if (atlas.getRegions().isEmpty()) {
                GameLogger.error("Boy" + " atlas has no regions");
                return false;
            }

            return true;
        } catch (Exception e) {
            GameLogger.error("Error verifying " + "Boy" + " atlas: " + e.getMessage());
            return false;
        }
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


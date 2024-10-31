package io.github.pokemeetup;

import com.badlogic.gdx.*;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Json;
import io.github.pokemeetup.audio.AudioManager;
import io.github.pokemeetup.managers.DatabaseManager;
import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.multiplayer.client.GameClientSingleton;
import io.github.pokemeetup.multiplayer.server.ServerStorageSystem;
import io.github.pokemeetup.multiplayer.server.config.ServerConnectionConfig;
import io.github.pokemeetup.screens.GameScreen;
import io.github.pokemeetup.screens.ModeSelectionScreen;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.PlayerData;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.multiworld.WorldData;
import io.github.pokemeetup.system.gameplay.overworld.multiworld.WorldManager;
import io.github.pokemeetup.system.gameplay.inventory.ItemManager;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.TextureManager;

import java.util.HashMap;
import java.util.Map;


public class CreatureCaptureGame extends Game {
    public static final String MULTIPLAYER_WORLD_NAME = "multiplayer_world";
    private static final long MULTIPLAYER_WORLD_SEED = 12345L; // Fixed seed for multiplayer
    private Player player;
    private DatabaseManager databaseManager;
    private boolean isDisposed = false;
    private World currentWorld;
    private GameClient gameClient;
    private boolean isDisposing = false;
    private boolean multiplayerMode = false; // Flag to track multiplayer mode
    private long lastPlayed;
    private WorldManager worldManager;
    private TextureAtlas gameAtlas;
    private boolean isSaving = false; // Add this flag

    // Call this method to enable multiplayer mode
    public void enableMultiplayerMode() {
        this.multiplayerMode = true;
    }

    // Call this method to disable multiplayer mode
    public void disableMultiplayerMode() {
        this.multiplayerMode = false;
    }

    // Check if the game is running in multiplayer mode
    public boolean isMultiplayerMode() {
        return multiplayerMode;
    }

    public void create() {
        try {
            // Use AssetManager for Android compatibility
            Gdx.app.log("CreatureCaptureGame", "Starting game initialization");

            // Load atlas safely
            if (Gdx.files.internal("atlas/game-atlas").exists()) {
                gameAtlas = new TextureAtlas(Gdx.files.internal("atlas/game-atlas"));
            } else {
                throw new RuntimeException("Required atlas file not found!");
            }

            // Initialize managers
            TextureManager.initialize(gameAtlas);

            // Initialize storage based on platform
            ServerStorageSystem storageSystem;
            // Use Android-specific storage path
            storageSystem = new ServerStorageSystem();

            worldManager = new WorldManager(storageSystem);
            worldManager.init();

            // Initialize audio with safe fallback
            try {
                AudioManager.getInstance();
            } catch (Exception e) {
                Gdx.app.error("CreatureCaptureGame", "Audio initialization failed", e);
            }

            // Initialize database only if needed
            if (isMultiplayerMode()) {
                try {
                    databaseManager = new DatabaseManager();
                } catch (Exception e) {
                    Gdx.app.error("CreatureCaptureGame", "Database initialization failed", e);
                }
            }

            setScreen(new ModeSelectionScreen(this));

        } catch (Exception e) {
            Gdx.app.error("CreatureCaptureGame", "Fatal error during game initialization", e);
            throw new RuntimeException("Game initialization failed", e);
        }
    }

    public void initializeWorld(String worldName, boolean isMultiplayer) {
        try {

            Gdx.app.log("CreatureCaptureGame", "Initializing world: " + worldName);
            // For multiplayer, always use the fixed world
            if (isMultiplayer) {
                worldName = MULTIPLAYER_WORLD_NAME;
                GameLogger.info("Multiplayer mode enabled. Using world name: " + worldName);
            }

            // Initialize GameClient based on mode
            if (isMultiplayer) {
                GameLogger.info("Setting up GameClient for multiplayer.");
                ServerConnectionConfig clientConfig = ServerConnectionConfig.getInstance();
                if (clientConfig == null) {
                    throw new RuntimeException("Failed to load server configuration.");
                }
                gameClient = GameClientSingleton.getInstance(clientConfig);
                GameLogger.info("GameClient initialized for multiplayer.");
            } else {
                GameLogger.info("Setting up GameClient for singleplayer.");
                gameClient = GameClientSingleton.getSinglePlayerInstance();
                GameLogger.info("GameClient initialized for singleplayer.");
            }

            // Load textures first
            GameLogger.info("Loading TextureAtlas.");
            if (gameAtlas == null) {
                gameAtlas = new TextureAtlas(Gdx.files.internal("atlas/game-atlas"));
            }
            if (!ItemManager.isInitialized()) {
                GameLogger.info("Initializing ItemManager");
                ItemManager.initialize(gameAtlas);
            }
            GameLogger.info("TextureAtlas loaded successfully.");
            Map<Integer, TextureRegion> tileTextures = loadTileTextures(gameAtlas);
            GameLogger.info("Tile textures loaded: " + tileTextures.size());

            // Load or create WorldData
            WorldData worldData;
            if (isMultiplayer) {
                worldData = worldManager.getWorld(MULTIPLAYER_WORLD_NAME);
                if (worldData == null) {
                    GameLogger.info("Creating new multiplayer WorldData.");
                    worldData = worldManager.createWorld(
                        MULTIPLAYER_WORLD_NAME,
                        MULTIPLAYER_WORLD_SEED,
                        0.15f,
                        0.05f
                    );
                } else {
                    GameLogger.info("Loaded existing multiplayer WorldData.");
                }
            } else {
                worldData = worldManager.getWorld(worldName);
                if (worldData == null) {
                    GameLogger.info("Creating new singleplayer WorldData.");
                    worldData = worldManager.createWorld(
                        worldName,
                        System.currentTimeMillis(),
                        0.15f,
                        0.05f
                    );
                } else {
                    GameLogger.info("Loaded existing singleplayer WorldData.");
                }
            }

            // Initialize the World object first
            currentWorld = new World(
                worldData.getName(),
                gameAtlas,
                tileTextures,
                World.WORLD_SIZE,
                World.WORLD_SIZE,
                worldData.getSeed(),
                gameClient
            );

            // Load player data
            String username = isMultiplayer ? gameClient.getLocalUsername() : "Player";
            GameLogger.info("Loading player data for: " + username);
            PlayerData playerData = worldData.getPlayerData(username);

            if (playerData == null) {
                GameLogger.info("No existing player data found, creating new player data");
                playerData = new PlayerData(username);
                playerData.setX(0);
                playerData.setY(0);
                playerData.setDirection("down");
                worldData.savePlayerData(username, playerData);
            } else {
                GameLogger.info("Loaded existing player data:");
                GameLogger.info("Position: " + playerData.getX() + "," + playerData.getY());
                GameLogger.info("Direction: " + playerData.getDirection());
                GameLogger.info("Inventory: " + playerData.getInventoryItems());
            }

            // Create actual Player instance
            player = new Player(
                (int) playerData.getX(),
                (int) playerData.getY(),
                currentWorld,
                gameAtlas,
                username
            );

            // Apply saved state to player
            playerData.applyToPlayer(player);

            // Set player in world
            currentWorld.setPlayer(player);
            currentWorld.setPlayerData(playerData);

            // Update last played time
            worldData.setLastPlayed(System.currentTimeMillis());
            worldManager.saveWorld(worldData);

            GameLogger.info("World initialization complete:");
            GameLogger.info("- World: " + currentWorld.getName());
            GameLogger.info("- Player: " + player.getUsername());
            GameLogger.info("- Position: " + player.getX() + "," + player.getY());
            GameLogger.info("- Direction: " + player.getDirection());

        } catch (Exception e) {
            Gdx.app.error("CreatureCaptureGame", "World initialization failed", e);
            // On Android, show a dialog or fallback to default state
            if (Gdx.app.getType() == Application.ApplicationType.Android) {
                handleAndroidError(e);
            } else {
                throw new RuntimeException("Failed to initialize world", e);
            }
        }
    }
    private void handleAndroidError(Exception e) {
        // Handle error gracefully on Android
        Gdx.app.error("CreatureCaptureGame", "Fatal error: " + e.getMessage());
        // Maybe show an error screen or restart the game
    }
    public WorldManager getWorldManager() {
        return worldManager;
    }

    public void initializePlayer(PlayerData savedData) {
        if (savedData == null) {
            throw new IllegalArgumentException("PlayerData cannot be null");
        }

        String username = savedData.getUsername();
        int defaultX = (int) savedData.getX();
        int defaultY = (int) savedData.getY();

        player = new Player(
            defaultX,
            defaultY,
            currentWorld,
            gameAtlas,
            username
        );

        // Restore player state
        player.updateFromState();
        currentWorld.setPlayer(player);

    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    private Map<Integer, TextureRegion> loadTileTextures(TextureAtlas atlas) {
        Map<Integer, TextureRegion> tileTextures = new HashMap<>();

        // Load basic textures
        tileTextures.put(0, atlas.findRegion("water"));
        tileTextures.put(1, atlas.findRegion("grass"));
        tileTextures.put(2, atlas.findRegion("sand"));
        tileTextures.put(3, atlas.findRegion("rock"));

        // Load biome-specific textures
        tileTextures.put(4, atlas.findRegion("snow"));
        tileTextures.put(5, atlas.findRegion("haunted_grass"));
        tileTextures.put(6, atlas.findRegion("snow_tall_grass"));
        tileTextures.put(7, atlas.findRegion("haunted_tall_grass"));
        tileTextures.put(8, atlas.findRegion("haunted_shroom"));
        tileTextures.put(9, atlas.findRegion("haunted_shrooms"));
        tileTextures.put(10, atlas.findRegion("tall_grass"));

        validateTextures(tileTextures);
        return tileTextures;
    }

    private void validateTextures(Map<Integer, TextureRegion> textures) {
        for (Map.Entry<Integer, TextureRegion> entry : textures.entrySet()) {
            if (entry.getValue() == null) {
                throw new RuntimeException("Failed to load texture for tile type: " + entry.getKey());
            }
        }
    }




    public synchronized void saveGameState() {
        // Prevent recursive saves
        if (isSaving) {
            GameLogger.info("Save already in progress, skipping");
            return;
        }

        // Allow saving during disposal - remove isDisposing check
        if (isDisposed) {
            GameLogger.info("Game already disposed, cannot save");
            return;
        }

        isSaving = true;
        try {
            Screen currentScreen = getScreen();
            if (!(currentScreen instanceof GameScreen)) {
                GameLogger.info("Not saving - current screen is not GameScreen");
                return;
            }

            GameScreen gameScreen = (GameScreen) currentScreen;
            Player currentPlayer = gameScreen.getPlayer();

            if (currentPlayer == null) {
                GameLogger.info("Not saving - player is null");
                return;
            }

            PlayerData currentState = new PlayerData(currentPlayer.getUsername());
            currentState.updateFromPlayer(currentPlayer);

            // Debug print current state
            GameLogger.info("Current state to save:");
            GameLogger.info("Position: " + currentState.getX() + "," + currentState.getY());
            GameLogger.info("Direction: " + currentState.getDirection());
            GameLogger.info("Inventory: " + currentState.getInventoryItems());

            // Get current world and its data
            World currentWorld = gameScreen.getWorld();
            if (currentWorld == null) {
                GameLogger.info("Not saving - world is null");
                return;
            }

            WorldData worldData = currentWorld.getWorldData();
            if (worldData == null) {
                GameLogger.info("Not saving - world data is null");
                return;
            }

            // Save player data to world data
            worldData.savePlayerData(currentPlayer.getUsername(), currentState);

            // Update last played time
            worldData.setLastPlayed(System.currentTimeMillis());

            // Actually write to file
            String worldPath = "assets/worlds/" + worldData.getName();
            FileHandle worldDir = Gdx.files.local(worldPath);

            // Ensure directory exists
            if (!worldDir.exists()) {
                worldDir.mkdirs();
            }

            // Create backup of existing file
            FileHandle worldFile = worldDir.child("world.json");
            if (worldFile.exists()) {
                FileHandle backupFile = worldDir.child("world_backup.json");
                worldFile.copyTo(backupFile);
            }

            // Write new data
            Json json = new Json();
            json.setUsePrototypes(false);
            String jsonData = json.prettyPrint(worldData);
            worldFile.writeString(jsonData, false);

            // Verify save
            WorldData readBack = json.fromJson(WorldData.class, worldFile.readString());
            PlayerData savedPlayer = readBack.getPlayerData(currentPlayer.getUsername());



        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            isSaving = false;
        }
    }

    private boolean verifyWorldSave(WorldData worldData, PlayerData expectedState) {
        PlayerData savedState = worldData.getPlayerData(expectedState.getUsername());
        if (savedState != null) {



        } else {
            return false;
        }
        return true;
    }

    public void saveGame() {
        GameLogger.info("Attempting to save game");
        try {
            boolean isPlayerNull = (currentWorld.getPlayer() == null);
            GameLogger.info("CurrentWorld's player is null?: " + isPlayerNull);
            if (!isPlayerNull) {
                Player player = currentWorld.getPlayer();
                if (gameClient != null && player.getWorld() != null) {
                    // Get current world
                    World currentWorldInstance = player.getWorld();

                    // Get current player
                    String username = player.getUsername();
                    if (username == null || username.isEmpty()) {
                        throw new Exception("Invalid player state: Username is null or empty");
                    }

                    // Create new PlayerData with explicit username
                    PlayerData playerData = new PlayerData(username);

                    // Update player state
                    updatePlayerData(playerData);

                    // Save based on game mode
                    if (gameClient.isSinglePlayer()) {
                        // Single player save
                        WorldData worldData = currentWorldInstance.getWorldData();
                        worldData.savePlayerData(username, playerData);
                        worldManager.saveWorld(worldData);
                        GameLogger.info("Single-player data saved successfully for user: " + username);
                    } else {
                        // Multiplayer save
                        gameClient.updateLastKnownState(playerData);
                        gameClient.savePlayerState(playerData);
                        GameLogger.info("Multiplayer data saved successfully for user: " + username);
                    }

                    // Optional: Verify save
                    WorldData loadedWorldData = worldManager.getWorld(currentWorldInstance.getName());
                    if (loadedWorldData != null) {
                        boolean verification = verifyWorldSave(loadedWorldData, playerData);
                        if (verification) {
                            GameLogger.info("Save verification successful.");
                        } else {
                            GameLogger.info("Save verification failed.");
                        }
                    } else {
                        GameLogger.info("Failed to load WorldData for verification.");
                    }

                } else {
                    throw new Exception("Game state is invalid: GameClient or Player's World is null");
                }
            } else {
                GameLogger.info("Cannot save game: Player is null");
            }
        } catch (Exception e) {
            GameLogger.info("Save game error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void performAutoSave() {
        if (gameClient.isSinglePlayer()) {
            saveWorldData();
        } else {
            try {
                Screen currentScreen = getScreen();
                if (currentScreen instanceof GameScreen) {
                    GameScreen gameScreen = (GameScreen) currentScreen;

                    // Get current world
                    if (currentWorld != null) {
                        // Update and save world state
                        currentWorld.updatePlayerData();
                        WorldData worldData = currentWorld.getWorldData();

                        // Get current player state
                        Player player = currentWorld.getPlayer();
                        if (player != null) {
                            PlayerData currentState = gameScreen.getCurrentPlayerState();
                            worldData.savePlayerData(player.getUsername(), currentState);

                            // Save to disk
                            worldManager.saveWorld(worldData);

                            GameLogger.info("Game state saved successfully:");
                            GameLogger.info("Player position: " + currentState.getX() + "," + currentState.getY());
                            GameLogger.info("Inventory: " + currentState.getInventoryItems());
                        }
                    }
                }
            } catch (Exception e) {
                GameLogger.info("Failed to save game state: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void saveWorldData() {
        try {
            Json json = new Json();
            json.setUsePrototypes(false); // Important for clean serialization

            FileHandle baseDir;
            if (Gdx.app.getType() == Application.ApplicationType.Android) {
                baseDir = Gdx.files.local("worlds/");
            } else {
                baseDir = Gdx.files.local("assets/worlds/");
            }

            FileHandle worldDir = baseDir.child(player.getWorld().getName());
            if (!worldDir.exists()) {
                worldDir.mkdirs();
            }

            WorldData worldData = currentWorld.getWorldData();

            // Update current player data before saving
            PlayerData currentPlayer = currentWorld.getPlayerData();
            if (currentPlayer != null) {
                GameLogger.info("Saving current player state");
                updatePlayerData(currentPlayer);
                worldData.savePlayerData(currentPlayer.getUsername(), currentPlayer);
            } else {
                GameLogger.info("Cannot save world data: PlayerData is null");
            }

            worldData.updateLastPlayed();
            FileHandle worldFile = worldDir.child("world.json");
            FileHandle backupFile = worldDir.child("world_backup.json");

            // Create a backup if the original file exists
            if (worldFile.exists()) {
                backupFile.writeString(worldFile.readString(), false);
                GameLogger.info("Backup created at: " + backupFile.path());
            }

            String jsonString = json.prettyPrint(worldData);
            GameLogger.info("Saving world data: " + jsonString);

            worldFile.writeString(jsonString, false);
            GameLogger.info("World save complete at: " + worldFile.path());

            // Verify save
            verifyWorldSave(worldData, currentPlayer);

        } catch (Exception e) {
            GameLogger.info("Failed to save world: " + e.getMessage());
            e.printStackTrace();
        }
    }
    @Override
    public void pause() {
        super.pause();
        try {
            if (!isDisposed) {
                saveGameState();
            }
        } catch (Exception e) {
            Gdx.app.error("CreatureCaptureGame", "Error during pause", e);
        }
    }

    @Override
    public void resume() {
        super.resume();
        try {
            // Reload textures if needed
            if (gameAtlas == null) {
                gameAtlas = new TextureAtlas(Gdx.files.internal("atlas/game-atlas"));
                TextureManager.initialize(gameAtlas);
            }
        } catch (Exception e) {
            Gdx.app.error("CreatureCaptureGame", "Error during resume", e);
        }
    }
    private void updatePlayerData(PlayerData playerData) {
        // Get current player state from world
        Player player = currentWorld.getPlayer();
        if (player != null) {
            playerData.updateFromPlayer(player);
        } else {
            GameLogger.info("Cannot update PlayerData: Player is null");
        }
    }

    private void setupShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (isDisposed) {
                GameLogger.info("Game already disposed, skipping shutdown save");
                return;
            }

            GameLogger.info("Running shutdown hook - saving final state...");
            try {
                saveGameState();
                GameLogger.info("Final save completed in shutdown hook");
            } catch (Exception e) {
                GameLogger.info("Error in shutdown hook: " + e.getMessage());
                e.printStackTrace();
            }
        }));
    }

    @Override
    public void dispose() {
        if (isDisposed) {
            GameLogger.info("Already disposed, skipping");
            return;
        }

        GameLogger.info("Starting game disposal");
        isDisposing = true;
        try {
            saveGameState();
            GameLogger.info("Final save completed during disposal");
        } catch (Exception e) {
            GameLogger.info("Error during disposal save: " + e.getMessage());
            e.printStackTrace();
        } finally {
            isDisposing = false;
            isDisposed = true;
            super.dispose();
            GameLogger.info("Game disposal complete");
        }
    }
}

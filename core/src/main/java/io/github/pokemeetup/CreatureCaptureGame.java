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
        gameAtlas = new TextureAtlas(Gdx.files.internal("atlas/game-atlas"));
        TextureManager.initialize(gameAtlas);
        setupShutdownHook();
        ServerStorageSystem storageSystem = new ServerStorageSystem();
        worldManager = new WorldManager(storageSystem);
        worldManager.init();
        AudioManager.getInstance();
        // Only initialize database for multiplayer
        if (isMultiplayerMode()) {
            databaseManager = new DatabaseManager();
        }
        setScreen(new ModeSelectionScreen(this));
    }

    public void initializeWorld(String worldName, boolean isMultiplayer) {
        try {
            System.out.println("Initializing world: " + worldName + ", isMultiplayer: " + isMultiplayer);

            // For multiplayer, always use the fixed world
            if (isMultiplayer) {
                worldName = MULTIPLAYER_WORLD_NAME;
                System.out.println("Multiplayer mode enabled. Using world name: " + worldName);
            }

            // Initialize GameClient based on mode
            if (isMultiplayer) {
                System.out.println("Setting up GameClient for multiplayer.");
                ServerConnectionConfig clientConfig = ServerConnectionConfig.getInstance();
                if (clientConfig == null) {
                    throw new RuntimeException("Failed to load server configuration.");
                }
                gameClient = GameClientSingleton.getInstance(clientConfig);
                System.out.println("GameClient initialized for multiplayer.");
            } else {
                System.out.println("Setting up GameClient for singleplayer.");
                gameClient = GameClientSingleton.getSinglePlayerInstance();
                System.out.println("GameClient initialized for singleplayer.");
            }

            // Load textures first
            System.out.println("Loading TextureAtlas.");
            if (gameAtlas == null) {
                gameAtlas = new TextureAtlas(Gdx.files.internal("atlas/game-atlas"));
            }
            if (!ItemManager.isInitialized()) {
                System.out.println("Initializing ItemManager");
                ItemManager.initialize(gameAtlas);
            }
            System.out.println("TextureAtlas loaded successfully.");
            Map<Integer, TextureRegion> tileTextures = loadTileTextures(gameAtlas);
            System.out.println("Tile textures loaded: " + tileTextures.size());

            // Load or create WorldData
            WorldData worldData;
            if (isMultiplayer) {
                worldData = worldManager.getWorld(MULTIPLAYER_WORLD_NAME);
                if (worldData == null) {
                    System.out.println("Creating new multiplayer WorldData.");
                    worldData = worldManager.createWorld(
                        MULTIPLAYER_WORLD_NAME,
                        MULTIPLAYER_WORLD_SEED,
                        0.15f,
                        0.05f
                    );
                } else {
                    System.out.println("Loaded existing multiplayer WorldData.");
                }
            } else {
                worldData = worldManager.getWorld(worldName);
                if (worldData == null) {
                    System.out.println("Creating new singleplayer WorldData.");
                    worldData = worldManager.createWorld(
                        worldName,
                        System.currentTimeMillis(),
                        0.15f,
                        0.05f
                    );
                } else {
                    System.out.println("Loaded existing singleplayer WorldData.");
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
            System.out.println("Loading player data for: " + username);
            PlayerData playerData = worldData.getPlayerData(username);

            if (playerData == null) {
                System.out.println("No existing player data found, creating new player data");
                playerData = new PlayerData(username);
                playerData.setX(0);
                playerData.setY(0);
                playerData.setDirection("down");
                worldData.savePlayerData(username, playerData);
            } else {
                System.out.println("Loaded existing player data:");
                System.out.println("Position: " + playerData.getX() + "," + playerData.getY());
                System.out.println("Direction: " + playerData.getDirection());
                System.out.println("Inventory: " + playerData.getInventoryItems());
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

            System.out.println("World initialization complete:");
            System.out.println("- World: " + currentWorld.getName());
            System.out.println("- Player: " + player.getUsername());
            System.out.println("- Position: " + player.getX() + "," + player.getY());
            System.out.println("- Direction: " + player.getDirection());

        } catch (Exception e) {
            System.err.println("Failed to initialize world: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to initialize world", e);
        }
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

        System.out.println("Initializing player: " + username + " at position: (" + defaultX + ", " + defaultY + ")");

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

        System.out.println("Player initialized successfully.");
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

    private void updateLastPlayed() {
        lastPlayed = System.currentTimeMillis();
        if (currentWorld != null) {
            WorldData worldData = worldManager.getWorld(currentWorld.getName());
            if (worldData != null) {
                worldData.setLastPlayed(lastPlayed);
                worldManager.saveWorld(worldData);
                System.out.println("Last played time updated in WorldData.");
            } else {
                System.err.println("Failed to update last played time: WorldData is null.");
            }
        } else {
            System.err.println("Failed to update last played time: World is null.");
        }
    }


    public synchronized void saveGameState() {
        // Prevent recursive saves
        if (isSaving) {
            System.out.println("Save already in progress, skipping");
            return;
        }

        // Allow saving during disposal - remove isDisposing check
        if (isDisposed) {
            System.out.println("Game already disposed, cannot save");
            return;
        }

        isSaving = true;
        try {
            Screen currentScreen = getScreen();
            if (!(currentScreen instanceof GameScreen)) {
                System.out.println("Not saving - current screen is not GameScreen");
                return;
            }

            GameScreen gameScreen = (GameScreen) currentScreen;
            Player currentPlayer = gameScreen.getPlayer();

            if (currentPlayer == null) {
                System.out.println("Not saving - player is null");
                return;
            }

            // Get latest player state
            PlayerData currentState = new PlayerData(currentPlayer.getUsername());
            currentState.updateFromPlayer(currentPlayer);

            // Debug print current state
            System.out.println("Current state to save:");
            System.out.println("Position: " + currentState.getX() + "," + currentState.getY());
            System.out.println("Direction: " + currentState.getDirection());
            System.out.println("Inventory: " + currentState.getInventoryItems());

            // Get current world and its data
            World currentWorld = gameScreen.getWorld();
            if (currentWorld == null) {
                System.out.println("Not saving - world is null");
                return;
            }

            WorldData worldData = currentWorld.getWorldData();
            if (worldData == null) {
                System.out.println("Not saving - world data is null");
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

            System.out.println("Saved world data to: " + worldFile.path());
            System.out.println("Save data: " + jsonData);

            // Verify save
            WorldData readBack = json.fromJson(WorldData.class, worldFile.readString());
            PlayerData savedPlayer = readBack.getPlayerData(currentPlayer.getUsername());

            if (savedPlayer != null) {
                System.out.println("Verified saved data:");
                System.out.println("Position: " + savedPlayer.getX() + "," + savedPlayer.getY());
                System.out.println("Direction: " + savedPlayer.getDirection());
                System.out.println("Inventory: " + savedPlayer.getInventoryItems());
            } else {
                System.out.println("Warning: Could not verify save - player data not found in saved file");
            }

        } catch (Exception e) {
            System.err.println("Failed to save game state: " + e.getMessage());
            e.printStackTrace();
        } finally {
            isSaving = false;
        }
    }

    private boolean verifyWorldSave(WorldData worldData, PlayerData expectedState) {
        PlayerData savedState = worldData.getPlayerData(expectedState.getUsername());
        if (savedState != null) {
            System.out.println("Save verification:");
            System.out.println("- Saved position: " + savedState.getX() + "," + savedState.getY());
            System.out.println("- Saved inventory: " + String.join(", ", savedState.getInventoryItems()));

            // Verify position matches
            if (savedState.getX() != expectedState.getX() || savedState.getY() != expectedState.getY()) {
                System.err.println("WARNING: Position mismatch in save!");
                System.err.println("Expected: " + expectedState.getX() + "," + expectedState.getY());
                System.err.println("Actual: " + savedState.getX() + "," + savedState.getY());
            }

            // Verify inventory matches
            if (!savedState.getInventoryItems().equals(expectedState.getInventoryItems())) {
                System.err.println("WARNING: Inventory mismatch in save!");
                System.err.println("Expected: " + String.join(", ", expectedState.getInventoryItems()));
                System.err.println("Actual: " + String.join(", ", savedState.getInventoryItems()));
            }
        } else {
            System.err.println("PlayerData not found during save verification.");
            return false;
        }
        return true;
    }

    public void saveGame() {
        System.out.println("Attempting to save game");
        try {
            boolean isPlayerNull = (currentWorld.getPlayer() == null);
            System.out.println("CurrentWorld's player is null?: " + isPlayerNull);
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
                        System.out.println("Single-player data saved successfully for user: " + username);
                    } else {
                        // Multiplayer save
                        gameClient.updateLastKnownState(playerData);
                        gameClient.savePlayerState(playerData);
                        System.out.println("Multiplayer data saved successfully for user: " + username);
                    }

                    // Optional: Verify save
                    WorldData loadedWorldData = worldManager.getWorld(currentWorldInstance.getName());
                    if (loadedWorldData != null) {
                        boolean verification = verifyWorldSave(loadedWorldData, playerData);
                        if (verification) {
                            System.out.println("Save verification successful.");
                        } else {
                            System.err.println("Save verification failed.");
                        }
                    } else {
                        System.err.println("Failed to load WorldData for verification.");
                    }

                } else {
                    throw new Exception("Game state is invalid: GameClient or Player's World is null");
                }
            } else {
                System.err.println("Cannot save game: Player is null");
            }
        } catch (Exception e) {
            System.err.println("Save game error: " + e.getMessage());
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

                            System.out.println("Game state saved successfully:");
                            System.out.println("Player position: " + currentState.getX() + "," + currentState.getY());
                            System.out.println("Inventory: " + currentState.getInventoryItems());
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Failed to save game state: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void saveWorldData() {
        try {
            Json json = new Json();
            json.setUsePrototypes(false); // Important for clean serialization

            FileHandle worldDir = Gdx.files.local("assets/worlds/" + player.getWorld().getName());
            if (!worldDir.exists()) {
                System.out.println("World directory does not exist. Creating directory: " + worldDir.path());
                worldDir.mkdirs();
            }

            WorldData worldData = currentWorld.getWorldData();

            // Update current player data before saving
            PlayerData currentPlayer = currentWorld.getPlayerData();
            if (currentPlayer != null) {
                System.out.println("Saving current player state");
                updatePlayerData(currentPlayer);
                worldData.savePlayerData(currentPlayer.getUsername(), currentPlayer);
            } else {
                System.err.println("Cannot save world data: PlayerData is null");
            }

            worldData.updateLastPlayed();
            FileHandle worldFile = worldDir.child("world.json");
            FileHandle backupFile = worldDir.child("world_backup.json");

            // Create a backup if the original file exists
            if (worldFile.exists()) {
                backupFile.writeString(worldFile.readString(), false);
                System.out.println("Backup created at: " + backupFile.path());
            }

            String jsonString = json.prettyPrint(worldData);
            System.out.println("Saving world data: " + jsonString);

            worldFile.writeString(jsonString, false);
            System.out.println("World save complete at: " + worldFile.path());

            // Verify save
            verifyWorldSave(worldData, currentPlayer);

        } catch (Exception e) {
            System.err.println("Failed to save world: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void updatePlayerData(PlayerData playerData) {
        // Get current player state from world
        Player player = currentWorld.getPlayer();
        if (player != null) {
            playerData.updateFromPlayer(player);
        } else {
            System.err.println("Cannot update PlayerData: Player is null");
        }
    }

    private void setupShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (isDisposed) {
                System.out.println("Game already disposed, skipping shutdown save");
                return;
            }

            System.out.println("Running shutdown hook - saving final state...");
            try {
                saveGameState();
                System.out.println("Final save completed in shutdown hook");
            } catch (Exception e) {
                System.err.println("Error in shutdown hook: " + e.getMessage());
                e.printStackTrace();
            }
        }));
    }

    @Override
    public void dispose() {
        if (isDisposed) {
            System.out.println("Already disposed, skipping");
            return;
        }

        System.out.println("Starting game disposal");
        isDisposing = true;
        try {
            saveGameState();
            System.out.println("Final save completed during disposal");
        } catch (Exception e) {
            System.err.println("Error during disposal save: " + e.getMessage());
            e.printStackTrace();
        } finally {
            isDisposing = false;
            isDisposed = true;
            super.dispose();
            System.out.println("Game disposal complete");
        }
    }
}

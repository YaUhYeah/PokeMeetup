package io.github.pokemeetup;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.multiplayer.client.GameClientSingleton;
import io.github.pokemeetup.screens.ModeSelectionScreen;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.PlayerData;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.multiworld.WorldData;
import io.github.pokemeetup.system.gameplay.overworld.multiworld.WorldManager;
import io.github.pokemeetup.system.inventory.Item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CreatureCaptureGame extends Game {
    public static final String MULTIPLAYER_WORLD_NAME = "multiplayer_world";
    private static final long MULTIPLAYER_WORLD_SEED = 12345L; // Fixed seed for multiplayer

    private boolean isDisposed = false;
    private World currentWorld;
    private GameClient gameClient;
    private boolean isDisposing = false;

    private long lastPlayed;
    private WorldManager worldManager;

    @Override
    public void create() {
        worldManager = new WorldManager();
        worldManager.init();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (!isDisposed) {
                saveGameState();
            }
        }));

        // Add input processor for window close button
        Gdx.input.setCatchKey(Input.Keys.FORWARD_DEL, true);
        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.ESCAPE) {
                    if (!isDisposing) {
                        saveGameState();
                        Gdx.app.exit();
                    }
                    return true;
                }
                return false;
            }
        });

        setScreen(new ModeSelectionScreen(this));
    }

    public void initializeWorld(String worldName, boolean isMultiplayer) {
        try {
            // For multiplayer, always use the fixed world
            if (isMultiplayer) {
                worldName = MULTIPLAYER_WORLD_NAME;
            }

            // Get or create the appropriate GameClient
            if (gameClient == null) {
                gameClient = isMultiplayer ?
                    GameClientSingleton.getInstance() :
                    GameClientSingleton.getSinglePlayerInstance();
            }

            WorldData worldData;
            if (isMultiplayer) {
                // Handle multiplayer world
                worldData = worldManager.getWorld(MULTIPLAYER_WORLD_NAME);
                if (worldData == null) {
                    worldData = worldManager.createWorld(
                        MULTIPLAYER_WORLD_NAME,
                        MULTIPLAYER_WORLD_SEED,
                        0.15f,
                        0.05f
                    );
                }
            } else {
                // Handle single player world
                worldData = worldManager.getWorld(worldName);
                if (worldData == null) {
                    // Create default world if it doesn't exist
                    worldData = worldManager.createWorld(
                        worldName,
                        System.currentTimeMillis(),
                        0.15f,
                        0.05f
                    );
                }
            }

            // Initialize the world
            TextureAtlas gameAtlas = new TextureAtlas(Gdx.files.internal("atlas/game-atlas"));
            Map<Integer, TextureRegion> tileTextures = loadTileTextures(gameAtlas);

            currentWorld = new World(
                worldData.getName(),
                gameAtlas,
                tileTextures,
                World.WORLD_SIZE,
                World.WORLD_SIZE,
                worldData.getSeed(),
                gameClient
            );

            updateLastPlayed();
            // Create or load PlayerData
            PlayerData playerData;
            if (isMultiplayer) {
                playerData = new PlayerData(gameClient.getLocalUsername());
                // You might want to request initial player data from the server here
            } else {
                playerData = worldData.getPlayerData(worldName);
                if (playerData == null) {
                    playerData = new PlayerData(worldName);
                }
            }
            currentWorld.setPlayerData(playerData);
        } catch (Exception e) {
            Gdx.app.error("CreatureCaptureGame", "Failed to initialize world", e);
            throw new RuntimeException("Failed to initialize world: " + e.getMessage(), e);
        }
    }

    // Add this helper method

    private void saveAndDispose() {
        try {
            System.out.println("Handling game closure - saving state");
            saveGameState();
            if (currentWorld != null) {
                currentWorld.save();
            }
            dispose();
            isDisposed = true;
        } catch (Exception e) {
            System.err.println("Error during emergency save: " + e.getMessage());
            e.printStackTrace();
        }
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
            }
        }
    }

 public void saveGameState() {
    try {      if (isDisposing || isDisposed) {
        return;
    }
        if (currentWorld != null) {
            updateLastPlayed();

            // Get the current player data
            PlayerData playerData = currentWorld.getPlayerData();

            if (playerData == null) {
                System.err.println("PlayerData is null. Skipping save.");
                return;
            }

            currentWorld.save();
            // Update player data with current world state
            updatePlayerData(playerData);

            if (currentWorld.getName().equals(MULTIPLAYER_WORLD_NAME)) {
                // Multiplayer: Save player state to the server
                if (gameClient != null) {
                    gameClient.savePlayerState(playerData);
                }
            } else {
                // Single-player: Save world data locally
                WorldData worldData = worldManager.getWorld(currentWorld.getName());
                if (worldData != null) {
                    worldData.savePlayerData(playerData.getUsername(), playerData);
                    worldManager.saveWorld(worldData);
                }
            }
        }
    } catch (Exception e) {
        Gdx.app.error("CreatureCaptureGame", "Error saving game state", e);
    }
}

private void updatePlayerData(PlayerData playerData) {
    if (currentWorld.getPlayer() != null) {
        Player player = currentWorld.getPlayer();
        playerData.setPosition(player.getX(), player.getY());
        playerData.setDirection(player.getDirection());
        playerData.setMoving(player.isMoving());
        playerData.setWantsToRun(player.isRunning());

        // Update inventory
        List<String> inventoryStrings = new ArrayList<>();
        for (Item item : player.getInventory().getItems()) {
            if (item != null) {
                inventoryStrings.add(item.getName() + ":" + item.getCount());
            }
        }
        playerData.setInventory(inventoryStrings);
    }
}


    // Getters
    public World getCurrentWorld() {
        return currentWorld;
    }

    public GameClient getGameClient() {
        return gameClient;
    }

    public long getLastPlayed() {
        return lastPlayed;
    }

    public WorldManager getWorldManager() {
        return worldManager;
    }

    @Override
    public void dispose() {
        if (isDisposing || isDisposed) {
            return;
        }

        try {
            isDisposing = true;
            saveGameState();

            if (currentWorld != null) {
                currentWorld.save();
            }

            if (gameClient != null) {
                gameClient.dispose();
            }

            GameClientSingleton.dispose();
            super.dispose();
        } finally {
            isDisposed = true;
            isDisposing = false;
        }
    }
}

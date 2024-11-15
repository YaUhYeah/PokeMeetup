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
import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.multiplayer.client.GameClientSingleton;
import io.github.pokemeetup.multiplayer.server.GameStateHandler;
import io.github.pokemeetup.multiplayer.server.ServerStorageSystem;
import io.github.pokemeetup.multiplayer.server.config.ServerConfigManager;
import io.github.pokemeetup.multiplayer.server.config.ServerConnectionConfig;
import io.github.pokemeetup.pokemon.data.PokemonDatabase;
import io.github.pokemeetup.screens.*;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.data.PlayerData;
import io.github.pokemeetup.system.data.WorldData;
import io.github.pokemeetup.system.gameplay.inventory.ItemManager;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.multiworld.WorldManager;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.textures.TextureManager;
import io.github.pokemeetup.utils.storage.DesktopFileSystem;
import io.github.pokemeetup.utils.storage.GameFileSystem;
import org.w3c.dom.Text;

import java.io.IOException;

public class CreatureCaptureGame extends Game implements GameStateHandler {
    public static final String MULTIPLAYER_WORLD_NAME = "multiplayer_world";
    public static final long MULTIPLAYER_WORLD_SEED = System.currentTimeMillis();
    private boolean isMultiplayerMode = false;
    private WorldManager worldManager;
    private GameClient gameClient;
    private BiomeManager biomeManager;
    private Player player;
    private World currentWorld;
    private String currentWorldName;
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
            // Critical: Force save current state before disposal
            if (currentWorld != null && player != null) {
                // Create final state snapshot
                PlayerData finalState = player.getPlayerData();
                finalState.updateFromPlayer(player);

                // Save to world data
                currentWorld.getWorldData().savePlayerData(player.getUsername(), finalState);

                // Force save to disk
                worldManager.saveWorld(currentWorld.getWorldData());
                GameLogger.info("Final state saved during game closure");
            }

            // Normal disposal sequence
            if (screen != null) {
                screen.dispose();
            }

            if (assetManager != null) {
                assetManager.dispose();
                assetManager = null;
            }

            if (AudioManager.getInstance() != null) {
                AudioManager.getInstance().dispose();
            }

            GameLogger.info("Game disposed successfully");

        } catch (Exception e) {
            GameLogger.error("Error during game disposal: " + e.getMessage());
            try {
                if (currentWorld != null && player != null) {
                    saveGame();
                }
            } catch (Exception saveError) {
                GameLogger.error("Emergency save failed: " + saveError.getMessage());
            }
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

            // Create a snapshot of current state
            PlayerData playerData = player.getPlayerData();
            playerData.updateFromPlayer(player);

            // Verify data before saving
            if (!verifyPlayerData(playerData)) {
                GameLogger.error("Player data verification failed - aborting save");
                return;
            }

            // Save and verify
            worldData.savePlayerData(player.getUsername(), playerData);
            worldManager.saveWorld(worldData);

            // Verify save by reading it back
            WorldData verifyData = worldManager.loadAndValidateWorld(currentWorldName);
            if (verifyData != null) {
                PlayerData verifyPlayer = verifyData.getPlayerData(player.getUsername());
                if (verifyPlayer != null) {
                    GameLogger.info("Save verified successfully - Items: " +
                        verifyPlayer.getValidItemCount() + " Pokemon: " +
                        verifyPlayer.getValidPokemonCount());
                }
            }

            GameLogger.info("Game saved successfully for world: " + currentWorldName);

        } catch (Exception e) {
            GameLogger.error("Failed to save game: " + e.getMessage());
        }
    }

    private boolean verifyPlayerData(PlayerData data) {
        if (data == null) return false;

        try {
            // Verify non-null essential data
            if (data.getUsername() == null ||
                data.getInventoryItems() == null ||
                data.getPartyPokemon() == null) {
                return false;
            }

            // Verify valid counts
            if (data.getValidItemCount() + data.getValidPokemonCount() == 0) {
                GameLogger.error("Player data has no valid items or Pokemon");
            }

            return true;
        } catch (Exception e) {
            GameLogger.error("Error verifying player data: " + e.getMessage());
            return false;
        }
    }

    public World getCurrentWorld() {
        return currentWorld;
    }

    public void initializeWorld(String worldName, boolean isMultiplayer) throws IOException {
        GameLogger.info("Starting world initialization: " + worldName);
        this.currentWorldName = worldName;
        this.isMultiplayerMode = isMultiplayer;

        try {
            if (gameClient == null) {
                if (isMultiplayer) {
                    // For multiplayer, get from singleton with proper config
                    ServerConnectionConfig config = ServerConfigManager.getDefaultServerConfig();
                    gameClient = GameClientSingleton.getInstance(config);
                } else {
                    // For single player
                    gameClient = GameClientSingleton.getSinglePlayerInstance();
                }

                if (gameClient == null) {
                    throw new IllegalStateException("Failed to initialize GameClient");
                }
            }
            WorldData worldData = worldManager.loadAndValidateWorld(worldName);
            if (worldData == null) {
                GameLogger.error("Failed to load world data for: " + worldName);
                throw new IOException("Failed to load world data");
            }
            String username = isMultiplayer ? gameClient.getLocalUsername() : "Player";
            PlayerData savedPlayerData = worldData.getPlayerData(username);

            if (savedPlayerData != null) {
                GameLogger.info("Found saved player data for: " + username +
                    " Items: " + savedPlayerData.getInventoryItems().size() +
                    " Pokemon: " + savedPlayerData.getPartyPokemon().size());

                // Validate the data
                if (!savedPlayerData.validateAndRepairState()) {
                    GameLogger.error("Player data validation failed");
                    savedPlayerData = null;
                }
            }

            // Initialize world
            this.currentWorld = new World(worldName,
                worldData.getConfig().getSeed(), gameClient, biomeManager);
            this.currentWorld.setWorldData(worldData);

            // Initialize player
            if (savedPlayerData != null) {
                this.player = new Player((int) savedPlayerData.getX(),
                    (int) savedPlayerData.getY(), currentWorld, username);
                player.initializeResources();
                savedPlayerData.applyToPlayer(player);
                GameLogger.info("Restored player state - Items: " +
                    player.getInventory().getAllItems().size() +
                    " Pokemon: " + player.getPokemonParty().getSize());
            } else {
                GameLogger.info("Creating new player at default position");
                this.player = new Player(World.DEFAULT_X_POSITION,
                    World.DEFAULT_Y_POSITION, currentWorld, username);
                player.initializeResources();
            }

            currentWorld.setPlayer(player);
            GameLogger.info("World initialization complete: " + worldName);

        } catch (Exception e) {
            GameLogger.error("Failed to initialize world: " + e.getMessage());
            throw new IOException("World initialization failed", e);
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
            "atlas/haunted_biome.atlas",
            "atlas/mountain-atlas.atlas",
            "atlas/mountain_biome.atlas",
            "atlas/move_effects_gfx.atlas",
            "atlas/plains_biome.atlas",
            "atlas/ruins_biome.atlas",
            "atlas/safari_biome.atlas",
            "atlas/snow_biome.atlas",
            "atlas/cherry_blossom_biome.atlas",
            "atlas/swamp_biome.atlas",
            "atlas/volcano_biome.atlas",
            "atlas/desert_biome.atlas",
            "atlas/Forest_Biome"

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

            TextureAtlas atlasPlains = new TextureAtlas(Gdx.files.internal("atlas/plains_biome.atlas"));
            TextureAtlas atlasForest = new TextureAtlas(Gdx.files.internal("atlas/Forest_Biome"));
            TextureAtlas atlasRuins = new TextureAtlas(Gdx.files.internal("atlas/ruins_biome.atlas"));
            TextureAtlas atlasSafari = new TextureAtlas(Gdx.files.internal("atlas/safari_biome.atlas"));
            TextureAtlas atlasSnow = new TextureAtlas(Gdx.files.internal("atlas/snow_biome.atlas"));
            TextureAtlas atlasDesert = new TextureAtlas(Gdx.files.internal("atlas/desert_biome.atlas"));
            TextureAtlas atlasSwamp = new TextureAtlas(Gdx.files.internal("atlas/swamp_biome.atlas"));
            TextureAtlas atlasHaunted = new TextureAtlas(Gdx.files.internal("atlas/haunted_biome.atlas"));
            TextureAtlas atlasCherryBlossom = new TextureAtlas(Gdx.files.internal("atlas/cherry_blossom_biome.atlas"));
            TextureAtlas atlasMountains = new TextureAtlas(Gdx.files.internal("atlas/mountain_biome.atlas"));
            TextureAtlas atlasVolcano = new TextureAtlas(Gdx.files.internal("atlas/volcano_biome.atlas"));

            TextureAtlas battleAtlas = assetManager.get("atlas/battlebacks-gfx-atlas", TextureAtlas.class);
            TextureAtlas uiAtlas = assetManager.get("atlas/ui-gfx-atlas.atlas", TextureAtlas.class);
            TextureAtlas backAtlas = assetManager.get("atlas/back-gfx-atlas", TextureAtlas.class);
            TextureAtlas frontAtlas = assetManager.get("atlas/front-gfx-atlas", TextureAtlas.class);
            TextureAtlas iconAtlas = assetManager.get("atlas/icon-gfx-atlas", TextureAtlas.class);
            TextureAtlas overworldAtlas = assetManager.get("atlas/overworld-gfx-atlas", TextureAtlas.class);
            TextureAtlas itemsAtlas = assetManager.get("atlas/items-gfx-atlas", TextureAtlas.class);
            TextureAtlas boyAtlas = assetManager.get("atlas/boy-gfx-atlas", TextureAtlas.class);
            TextureAtlas effects = assetManager.get("atlas/move_effects_gfx.atlas", TextureAtlas.class);
            TextureAtlas mountains = assetManager.get("atlas/mountain-atlas.atlas", TextureAtlas.class);
            TextureAtlas tilesAtlas = assetManager.get("atlas/tiles-gfx-atlas", TextureAtlas.class);

            TextureManager.debugAtlasState("Boy", boyAtlas);

            if (!verifyAtlas(boyAtlas)) {
                throw new RuntimeException("Boy atlas verification failed");
            }

            TextureManager.initialize(
                battleAtlas,
                uiAtlas,
                backAtlas,
                frontAtlas,
                iconAtlas,
                overworldAtlas,
                itemsAtlas,
                boyAtlas,
                tilesAtlas,
                effects,
                mountains,
                atlasPlains,
                atlasRuins,
                atlasSafari,
                atlasSnow,
                atlasDesert,
                atlasSwamp,
                atlasHaunted,
                atlasCherryBlossom,
                atlasVolcano,
                atlasMountains, atlasForest
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


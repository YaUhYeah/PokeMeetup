package io.github.pokemeetup.multiplayer.server;

import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.esotericsoftware.kryonet.Server;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.sun.tools.jconsole.JConsoleContext;
import io.github.pokemeetup.CreatureCaptureGame;
import io.github.pokemeetup.chat.TeleportManager;
import io.github.pokemeetup.managers.BiomeManager;
import io.github.pokemeetup.managers.BiomeTransitionResult;
import io.github.pokemeetup.managers.DatabaseManager;
import io.github.pokemeetup.multiplayer.PlayerManager;
import io.github.pokemeetup.multiplayer.ServerPlayer;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.multiplayer.server.config.ServerConnectionConfig;
import io.github.pokemeetup.multiplayer.server.events.EventManager;
import io.github.pokemeetup.multiplayer.server.plugin.PluginManager;
import io.github.pokemeetup.multiplayer.server.storage.FileStorage;
import io.github.pokemeetup.multiplayer.server.storage.StorageSystem;
import io.github.pokemeetup.pokemon.Pokemon;
import io.github.pokemeetup.pokemon.WildPokemon;
import io.github.pokemeetup.system.data.ItemData;
import io.github.pokemeetup.system.data.PlayerData;
import io.github.pokemeetup.system.data.PokemonData;
import io.github.pokemeetup.system.data.WorldData;
import io.github.pokemeetup.system.gameplay.overworld.Chunk;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.WorldObject;
import io.github.pokemeetup.system.gameplay.overworld.biomes.Biome;
import io.github.pokemeetup.system.gameplay.overworld.multiworld.WorldManager;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.TextureManager;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static io.github.pokemeetup.CreatureCaptureGame.MULTIPLAYER_WORLD_SEED;

/**
 * The GameServer class manages the server-side operations of the multiplayer game,
 * including handling client connections, processing login and registration requests,
 * managing player states, and coordinating with various managers like PlayerManager,
 * PluginManager, and EventManager.
 */
public class GameServer {

    private final Server networkServer;
    private final ServerConnectionConfig config;
    private final WorldManager worldManager;
    private final StorageSystem storage;
    private final BiomeManager biomeManager;
    private final ServerStorageSystem storageSystem;
    private final EventManager eventManager;
    private final DatabaseManager databaseManager;
    private final ConcurrentHashMap<Integer, String> connectedPlayers;
    private final PlayerManager playerManager; // Manages player-related operations
    private final TeleportManager teleportManager;
    private PluginManager pluginManager = null;
    private WorldData multiplayerWorld;
    private volatile boolean running;

    /**
     * Constructor initializes the GameServer with the provided ServerConfig.
     *
     * @param config The server configuration containing settings like port, maxPlayers, etc.
     */
    public GameServer(ServerConnectionConfig config) {
        this.config = config;
        this.storageSystem = new ServerStorageSystem();
        this.networkServer = new Server(16384, 2048); // Initialize KryoNet Server with send and receive buffers
        this.databaseManager = new DatabaseManager(); // Initialize DatabaseManager
        this.storage = new FileStorage(config.getDataDirectory());
        this.eventManager = new EventManager();
        this.connectedPlayers = new ConcurrentHashMap<>();
        this.playerManager = new PlayerManager(databaseManager, storage, eventManager);
        this.worldManager =
            WorldManager.getInstance(storageSystem, true);


        try {
            worldManager.init();

            multiplayerWorld = worldManager.getWorld(CreatureCaptureGame.MULTIPLAYER_WORLD_NAME);
            if (multiplayerWorld == null) {
                multiplayerWorld = worldManager.createWorld(
                    CreatureCaptureGame.MULTIPLAYER_WORLD_NAME,
                    System.currentTimeMillis(),
                    0.15f,
                    0.05f
                );
                initializeWorldData(multiplayerWorld);
            }
            setupNetworkListener();
            this.pluginManager = new PluginManager(this, multiplayerWorld);
            this.teleportManager = new TeleportManager();
            this.biomeManager = new BiomeManager(multiplayerWorld.getConfig().getSeed());
        } catch (Exception e) {
            GameLogger.error("Failed to initialize game world: " + e.getMessage());
            throw new RuntimeException("Failed to initialize server world", e);
        }
    }

    public Server getNetworkServer() {
        return networkServer;
    }

    public StorageSystem getStorage() {
        return storage;
    }

    public BiomeManager getBiomeManager() {
        return biomeManager;
    }

    public ServerStorageSystem getStorageSystem() {
        return storageSystem;
    }

    public EventManager getEventManager() {
        return eventManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public ConcurrentHashMap<Integer, String> getConnectedPlayers() {
        return connectedPlayers;
    }


    public TeleportManager getTeleportManager() {
        return teleportManager;
    }

    public PluginManager getPluginManager() {
        return pluginManager;
    }

    public WorldData getMultiplayerWorld() {
        return multiplayerWorld;
    }


    /**
     * Handles username availability check requests from clients.
     */
    private void handleUsernameCheckRequest(Connection connection, NetworkProtocol.UsernameCheckRequest request) {
        try {
            GameLogger.info("Checking username availability: " + request.username);

            NetworkProtocol.UsernameCheckResponse response = new NetworkProtocol.UsernameCheckResponse();
            response.username = request.username;

            // Check if username exists directly in database
            boolean exists = databaseManager.checkUsernameExists(request.username);

            response.available = !exists;
            response.message = exists ? "Username is already taken" : "Username is available";

            GameLogger.info("Username '" + request.username + "' availability: " + response.available);
            networkServer.sendToTCP(connection.getID(), response);

        } catch (Exception e) {
            GameLogger.error("Error checking username availability: " + e.getMessage());
            NetworkProtocol.UsernameCheckResponse response = new NetworkProtocol.UsernameCheckResponse();
            response.username = request.username;
            response.available = false;
            response.message = "Error checking username availability";
            networkServer.sendToTCP(connection.getID(), response);
        }
    }

    private WorldData initializeMultiplayerWorld(String worldName, long seed) {
        GameLogger.info("Initializing multiplayer world: " + worldName + " with seed: " + seed);

        // Create WorldConfig and WorldData
        WorldData.WorldConfig config = new WorldData.WorldConfig(seed);
        WorldData worldData = new WorldData(worldName, System.currentTimeMillis(), config); // Include config

        // Initialize core chunks around spawn point
        int spawnChunkRadius = 3;  // Adjust as needed
        Vector2 spawnPoint = new Vector2(0, 0); // Starting point, can be adjusted

        for (int x = -spawnChunkRadius; x <= spawnChunkRadius; x++) {
            for (int y = -spawnChunkRadius; y <= spawnChunkRadius; y++) {
                Vector2 chunkPos = new Vector2(x, y);
                BiomeTransitionResult biomeTransition = biomeManager.getBiomeAt(
                    x * Chunk.CHUNK_SIZE * World.TILE_SIZE,
                    y * Chunk.CHUNK_SIZE * World.TILE_SIZE
                );

                Chunk chunk = new Chunk(x, y, biomeTransition.getPrimaryBiome(), seed, biomeManager);
                worldData.addChunk(chunkPos, chunk);

                // Generate and add objects for this chunk using the new config
                List<WorldObject> chunkObjects = generateChunkObjects(chunk, chunkPos, config); // Pass config
                worldData.addChunkObjects(chunkPos, chunkObjects);
            }
        }

        // Set spawn point and other world properties
        worldData.setSpawnX((int) spawnPoint.x);
        worldData.setSpawnY((int) spawnPoint.y);

        return worldData;
    }


    private void initializeWorldData(WorldData worldData) {
        try {
            // Set up spawn point (center of world)
            int spawnX = World.WORLD_SIZE / 2;
            int spawnY = World.WORLD_SIZE / 2;
            worldData.setSpawnX(spawnX);
            worldData.setSpawnY(spawnY);

            // Initialize time values
            worldData.setWorldTimeInMinutes(480.0); // Start at 8:00 AM
            worldData.setDayLength(24.0f); // 24 minutes = 1 game day
            worldData.setPlayedTime(0);

            // Generate initial chunks around spawn
            int spawnChunkRadius = 3;
            for (int x = -spawnChunkRadius; x <= spawnChunkRadius; x++) {
                for (int y = -spawnChunkRadius; y <= spawnChunkRadius; y++) {
                    Vector2 chunkPos = new Vector2(x, y);
                    BiomeTransitionResult biomeTransition = biomeManager.getBiomeAt(
                        x * Chunk.CHUNK_SIZE * World.TILE_SIZE,
                        y * Chunk.CHUNK_SIZE * World.TILE_SIZE
                    );

                    Chunk chunk = new Chunk(
                        x, y,
                        biomeTransition.getPrimaryBiome(),
                        worldData.getConfig().getSeed(),
                        biomeManager
                    );

                    worldData.addChunk(chunkPos, chunk);

                    // Generate and add objects for this chunk
                    List<WorldObject> objects = generateChunkObjects(chunk, chunkPos, worldData.getConfig());
                    worldData.addChunkObjects(chunkPos, objects);
                }
            }

            GameLogger.info("World data initialized successfully");

        } catch (Exception e) {
            GameLogger.error("Failed to initialize world data: " + e.getMessage());
            throw new RuntimeException("World initialization failed", e);
        }
    }

    private List<WorldObject> generateChunkObjects(Chunk chunk, Vector2 chunkPos, WorldData.WorldConfig config) {
        List<WorldObject> objects = new ArrayList<>();
        Random random = new Random((long) (config.getSeed() + chunkPos.x * 31 + chunkPos.y * 17)); // Use seed from config

        float worldX = chunkPos.x * Chunk.CHUNK_SIZE * World.TILE_SIZE;
        float worldY = chunkPos.y * Chunk.CHUNK_SIZE * World.TILE_SIZE;

        Biome biome = chunk.getBiome();
        float objectDensity = config.getTreeSpawnRate(); // Use the density from WorldConfig

        // Adjust density based on biome
        switch (biome.getType()) {
            case FOREST:
                objectDensity *= 1.5f;
                break;
            case DESERT:
                objectDensity *= 0.5f;
                break;
            case SNOW:
                objectDensity *= 0.8f;
                break;
        }

        for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
            for (int y = 0; y < Chunk.CHUNK_SIZE; y++) {
                if (random.nextFloat() < objectDensity) {
                    WorldObject object = generateObjectForBiome(
                        biome,
                        worldX + x * World.TILE_SIZE,
                        worldY + y * World.TILE_SIZE,
                        random
                    );
                    if (object != null) {
                        objects.add(object);
                    }
                }
            }
        }

        return objects;
    }

    private void handlePlayerUpdate(Connection connection, NetworkProtocol.PlayerUpdate update) {
        String username = connectedPlayers.get(connection.getID());
        if (username != null && username.equals(update.username)) {
            ServerPlayer player = playerManager.getPlayer(username);
            if (player != null) {
                player.updatePosition(update.x, update.y, update.direction, update.isMoving);

                // Get the current world instance to update player data
                WorldData currentWorld = worldManager.getCurrentWorld();
                if (currentWorld != null) {
                    PlayerData playerData = currentWorld.getPlayerData(username);
                    if (playerData != null) {
                        // Update player data in the world
                        playerData.setX(update.x);
                        playerData.setY(update.y);
                        currentWorld.savePlayerData(username, playerData);
                    }
                }

                // Broadcast update to other clients
                networkServer.sendToAllExceptTCP(connection.getID(), update);
            }
        }
    }


    private WorldObject generateObjectForBiome(Biome biome, float x, float y, Random random) {
        WorldObject.ObjectType objectType = getObjectTypeForBiome(biome, random);
        if (objectType == null) return null;

        TextureRegion texture = TextureManager.getTextureForObjectType(objectType);
        if (texture == null) return null;

        return new WorldObject((int) x, (int) y, texture, objectType);
    }

    private void broadcastTimeSync() {
        NetworkProtocol.TimeSync timeSync = new NetworkProtocol.TimeSync();
        timeSync.timestamp = System.currentTimeMillis();
        timeSync.dayLength = multiplayerWorld.getDayLength();
        timeSync.worldTimeInMinutes = multiplayerWorld.getWorldTimeInMinutes();
        networkServer.sendToAllTCP(timeSync);
    }

    private void handlePokemonSpawn(Connection connection, NetworkProtocol.WildPokemonSpawn spawnRequest) {
        try {
            WorldData world = worldManager.getCurrentWorld();
            if (world == null) {
                GameLogger.error("Cannot spawn Pokemon: World is null");
                return;
            }

            // Validate spawn position
            if (!isValidSpawnPosition(spawnRequest.x, spawnRequest.y)) {
                GameLogger.error("Invalid spawn position: " + spawnRequest.x + "," + spawnRequest.y);
                return;
            }

            // Create new WildPokemon instance
            WildPokemon pokemon = createWildPokemon(spawnRequest);
            if (pokemon == null) {
                GameLogger.error("Failed to create Pokemon from spawn request");
                return;
            }

            // Register the Pokemon with the world
            try {
                world.registerWildPokemon(pokemon);
            } catch (Exception e) {
                GameLogger.error("Failed to register Pokemon with world: " + e.getMessage());
                return;
            }

            // Create broadcast message
            NetworkProtocol.WildPokemonSpawn broadcastSpawn = createSpawnBroadcast(pokemon);

            // Broadcast to all clients
            try {
                networkServer.sendToAllTCP(broadcastSpawn);
                GameLogger.info("Broadcast Pokemon spawn: " + pokemon.getName() +
                    " (UUID: " + pokemon.getUuid() + ")");
            } catch (Exception e) {
                GameLogger.error("Failed to broadcast Pokemon spawn: " + e.getMessage());
                // Consider removing Pokemon from world if broadcast fails
                world.removeWildPokemon(pokemon.getUuid());
            }

        } catch (Exception e) {
            GameLogger.error("Error handling Pokemon spawn: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean isValidSpawnPosition(float x, float y) {
        // Convert to tile coordinates
        int tileX = (int) (x / World.TILE_SIZE);
        int tileY = (int) (y / World.TILE_SIZE);

        // Check world bounds
        if (tileX < 0 || tileX >= World.WORLD_SIZE ||
            tileY < 0 || tileY >= World.WORLD_SIZE) {
            return false;
        }

        // Get current world
        WorldData world = worldManager.getCurrentWorld();
        if (world == null) return false;

        // Check if tile is passable (you'll need to implement this check based on your world system)
        // Return true for now, implement actual checks based on your world system
        return true;
    }

    // Helper method to create broadcast message
    private NetworkProtocol.WildPokemonSpawn createSpawnBroadcast(WildPokemon pokemon) {
        NetworkProtocol.WildPokemonSpawn broadcast = new NetworkProtocol.WildPokemonSpawn();
        broadcast.uuid = pokemon.getUuid();
        broadcast.x = pokemon.getX();
        broadcast.y = pokemon.getY();

        // Create PokemonData for network transmission
        PokemonData pokemonData = new PokemonData();
        pokemonData.setName(pokemon.getName());
        pokemonData.setLevel(pokemon.getLevel());
        pokemonData.setPrimaryType(pokemon.getPrimaryType());
        pokemonData.setSecondaryType(pokemon.getSecondaryType());

        // Set stats
        if (pokemon.getStats() != null) {
            PokemonData.Stats stats = new PokemonData.Stats(pokemon.getStats());
            pokemonData.setStats(stats);
        }

        // Set moves
        List<PokemonData.MoveData> moves = pokemon.getMoves().stream()
            .map(PokemonData.MoveData::fromMove)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        pokemonData.setMoves(moves);

        broadcast.data = pokemonData;
        broadcast.timestamp = System.currentTimeMillis();

        return broadcast;
    }


    // Implement the Pokemon update handler
    private void handlePokemonUpdate(Connection connection, NetworkProtocol.PokemonUpdate update) {
        if (update == null || update.uuid == null) {
            GameLogger.error("Invalid Pokemon update received");
            return;
        }

        try {
            WorldData currentWorld = worldManager.getCurrentWorld();
            if (currentWorld == null) {
                GameLogger.error("Current world is null. Cannot handle PokemonUpdate");
                return;
            }

            WildPokemon wildPokemon = currentWorld.getWildPokemon(update.uuid);
            if (wildPokemon == null) {
                GameLogger.error("Received PokemonUpdate for unknown UUID: " + update.uuid);
                return;
            }

            synchronized (wildPokemon) {
                // Update position
                wildPokemon.setX(update.x);
                wildPokemon.setY(update.y);
                wildPokemon.updateBoundingBox();

                // Update movement and direction
                wildPokemon.setDirection(update.direction);
                wildPokemon.setMoving(update.isMoving);

                // Update level and stats if provided
                if (update.level > 0) {
                    wildPokemon.setLevel(update.level);
                }
                if (update.currentHp > 0) {
                    wildPokemon.setCurrentHp(update.currentHp);
                }

                // Update timestamp
                wildPokemon.setSpawnTime(update.timestamp);

                // Update additional data if provided
                if (update.data != null) {
                    updatePokemonFromData(wildPokemon, update.data);
                }
            }

            // Broadcast the update to all other clients
            NetworkProtocol.PokemonUpdate broadcastUpdate = createBroadcastUpdate(wildPokemon);
            networkServer.sendToAllExceptTCP(connection.getID(), broadcastUpdate);

            GameLogger.info("Updated and broadcast WildPokemon: " + wildPokemon.getName() +
                " UUID: " + update.uuid);

        } catch (Exception e) {
            GameLogger.error("Error handling Pokemon update: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Handle Pokemon despawn
    private void handlePokemonDespawn(Connection connection, NetworkProtocol.WildPokemonDespawn despawnRequest) {
        try {
            WorldData world = worldManager.getCurrentWorld();
            if (world == null) {
                return;
            }

            UUID pokemonId = despawnRequest.uuid;
            if (pokemonId == null) {
                return;
            }

            // Remove the Pokemon from the world
            world.removeWildPokemon(pokemonId);

            // Broadcast despawn to all clients
            networkServer.sendToAllExceptTCP(connection.getID(), despawnRequest);

            GameLogger.info("Pokemon despawned and broadcast: " + pokemonId);

        } catch (Exception e) {
            GameLogger.error("Error handling Pokemon despawn: " + e.getMessage());
        }
    }

    // Helper methods
    private WildPokemon createWildPokemon(NetworkProtocol.WildPokemonSpawn spawnRequest) {
        try {
            // Create Pokemon from the spawn data
            WildPokemon pokemon = new WildPokemon(
                spawnRequest.data.getName(),
                spawnRequest.data.getLevel(),
                (int) spawnRequest.x,
                (int) spawnRequest.y,
                TextureManager.getOverworldSprite(spawnRequest.data.getName())
            );

            // Set additional data
            pokemon.setUuid(spawnRequest.uuid != null ? spawnRequest.uuid : UUID.randomUUID());
            pokemon.setSpawnTime(System.currentTimeMillis() / 1000L);

            return pokemon;
        } catch (Exception e) {
            GameLogger.error("Error creating WildPokemon: " + e.getMessage());
            return null;
        }
    }

    private void updatePokemonFromData(WildPokemon pokemon, PokemonData data) {
        if (data == null || pokemon == null) return;

        // Update basic stats
        pokemon.setPrimaryType(data.getPrimaryType());
        pokemon.setSecondaryType(data.getSecondaryType());

        // Update other relevant data
        if (data.getStats() != null) {
            Pokemon.Stats stats = pokemon.getStats();
            stats.setHp(data.getStats().hp);
            stats.setAttack(data.getStats().attack);
            stats.setDefense(data.getStats().defense);
            stats.setSpecialAttack(data.getStats().specialAttack);
            stats.setSpecialDefense(data.getStats().specialDefense);
            stats.setSpeed(data.getStats().speed);
        }
    }

    private NetworkProtocol.PokemonUpdate createBroadcastUpdate(WildPokemon pokemon) {
        NetworkProtocol.PokemonUpdate update = new NetworkProtocol.PokemonUpdate();
        update.uuid = pokemon.getUuid();
        update.x = pokemon.getX();
        update.y = pokemon.getY();
        update.direction = pokemon.getDirection();
        update.isMoving = pokemon.isMoving();
        update.level = pokemon.getLevel();
        update.currentHp = pokemon.getCurrentHp();
        update.timestamp = System.currentTimeMillis();
        update.data = PokemonData.fromPokemon(pokemon);
        return update;
    }

    private WorldObject.ObjectType getObjectTypeForBiome(Biome biome, Random random) {
        switch (biome.getType()) {
            case FOREST:
                return WorldObject.ObjectType.TREE;
            case SNOW:
                return WorldObject.ObjectType.SNOW_TREE;
            case DESERT:
                return WorldObject.ObjectType.CACTUS;
            case HAUNTED:
                return WorldObject.ObjectType.HAUNTED_TREE;
            default:
                return random.nextFloat() < 0.3f ? WorldObject.ObjectType.TREE : null;
        }
    }

    // Add broadcast method
    public void broadcastWorldState() {
        try {
            NetworkProtocol.WorldStateUpdate update = new NetworkProtocol.WorldStateUpdate();
            update.worldData = getSerializableWorldData();
            update.timestamp = System.currentTimeMillis();

            // Send to all connected clients
            networkServer.sendToAllTCP(update);

            GameLogger.info("Broadcasted world state update to all clients");

        } catch (Exception e) {
            GameLogger.error("Failed to broadcast world state: " + e.getMessage());
        }
    }

    private WorldData getSerializableWorldData() {
        synchronized (worldManager) {
            WorldData data = worldManager.getCurrentWorld();
            // Create a copy with only necessary data for network transmission
            WorldData networkData = new WorldData(data.getName());
            networkData.setWorldSeed(data.getConfig().getSeed());
            networkData.setWorldTimeInMinutes(data.getWorldTimeInMinutes());
            networkData.setPlayedTime(data.getPlayedTime());
            // Add other necessary data
            return networkData;
        }
    }


    // In GameClient.java - Handle world state updates
    private void handleWorldStateUpdate(NetworkProtocol.WorldStateUpdate update) {
        if (multiplayerWorld != null) {
            multiplayerWorld.updateFromServerData(update.worldData);
        }
    }

    private void handleLoginRequest(Connection connection, NetworkProtocol.LoginRequest request) {
        try {
            String sessionId = String.valueOf(connection.getID());
            ServerPlayer player = playerManager.loginPlayer(request.username, request.password, sessionId);

            NetworkProtocol.LoginResponse response = new NetworkProtocol.LoginResponse();

            if (player != null) {
                response.success = true;
                response.username = player.getUsername();
                response.x = (int) player.getPosition().x;
                response.y = (int) player.getPosition().y;

                // Add world data to response
                response.worldSeed = multiplayerWorld.getConfig().getSeed();
                response.worldName = CreatureCaptureGame.MULTIPLAYER_WORLD_NAME;
                response.worldData = multiplayerWorld;  // Serialize relevant world data

                connectedPlayers.put(connection.getID(), player.getUsername());
                broadcastNewPlayer(player);
                sendExistingPlayers(connection);

            } else {
                response.success = false;
                response.message = "Login failed. Incorrect username or password.";
            }

            networkServer.sendToTCP(connection.getID(), response);

        } catch (Exception e) {
            GameLogger.error("Error during login: " + e.getMessage());
            // Send error response
        }
    }

    public PlayerManager getPlayerManager() {
        return playerManager;
    }

    /**
     * Sets up the network listener to handle incoming connections, received objects,
     * and disconnections.
     */
    private void setupNetworkListener() {
        networkServer.addListener(new Listener() {
            @Override
            public void connected(Connection connection) {
                handlePlayerConnect(connection);
            }

            @Override
            public void disconnected(Connection connection) {
                try {
                    handlePlayerDisconnect(connection);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void received(Connection connection, Object object) {
                handleNetworkMessage(connection, object);
            }
        });
    }


    private void handleChatMessage(Connection connection, NetworkProtocol.ChatMessage message) {
        // Validate message
        if (message == null || message.content == null || message.content.isEmpty()) {
            return;
        }

        // Add timestamp if not present
        if (message.timestamp == 0) {
            message.timestamp = System.currentTimeMillis();
        }

        //        GameLogger.info(STR."Server received chat message from: \{message.sender} content: \{message.content}");

        // Broadcast to all connected clients except sender
        for (Connection conn : networkServer.getConnections()) {
            if (conn.getID() != connection.getID()) {
                try {
                    networkServer.sendToTCP(conn.getID(), message);
                } catch (Exception e) {
                    //                    GameLogger.info(STR."Failed to broadcast message to client \{conn.getID()}: \{e.getMessage()}");
                }
            }
        }
    }

    private void sendExistingPlayers(Connection newConnection) {
        NetworkProtocol.PlayerPosition existingPlayers = new NetworkProtocol.PlayerPosition();
        for (ServerPlayer player : playerManager.getOnlinePlayers()) {
            NetworkProtocol.PlayerUpdate playerState = new NetworkProtocol.PlayerUpdate();
            playerState.username = player.getUsername();
            playerState.x = player.getPosition().x;
            playerState.y = player.getPosition().y;
            playerState.direction = player.getDirection();
            playerState.isMoving = player.isMoving();
            existingPlayers.players.put(player.getUsername(), playerState);
        }
        networkServer.sendToTCP(newConnection.getID(), existingPlayers);
    }


    private void broadcastNewPlayer(ServerPlayer newPlayer) {
        NetworkProtocol.PlayerJoined joinMessage = new NetworkProtocol.PlayerJoined();
        joinMessage.username = newPlayer.getUsername();
        joinMessage.x = newPlayer.getPosition().x;
        joinMessage.y = newPlayer.getPosition().y;
        joinMessage.direction = newPlayer.getDirection();
        joinMessage.isMoving = newPlayer.isMoving();
        joinMessage.inventoryItems = newPlayer.getInventoryItems().toArray(new ItemData[0]);

        // Send to all except the new player
        networkServer.sendToAllExceptTCP(
            Integer.parseInt(newPlayer.getSessionId()),
            joinMessage
        );

        GameLogger.info("Broadcasted new player join: " + newPlayer.getUsername() +
            " at (" + joinMessage.x + "," + joinMessage.y + ")");
    }

    private void sendRegistrationResponse(Connection connection, boolean success, String message) {
        NetworkProtocol.RegisterResponse response = new NetworkProtocol.RegisterResponse();
        response.success = success;
        response.message = message;
        networkServer.sendToTCP(connection.getID(), response);
    }

    private boolean isValidUsername(String username) {
        return username != null &&
            username.length() >= 3 &&
            username.length() <= 20 &&
            username.matches("^[a-zA-Z0-9_]+$");
    }

    private void handleRegisterRequest(Connection connection, NetworkProtocol.RegisterRequest request) {
        try {
            GameLogger.info("Processing registration request for username: " + request.username);

            // Basic validation
            if (request.username == null || request.username.isEmpty() ||
                request.password == null || request.password.isEmpty()) {
                sendRegistrationResponse(connection, false, "Username and password are required.");
                return;
            }

            // Validate username format
            if (!isValidUsername(request.username)) {
                sendRegistrationResponse(connection, false,
                    "Username must be 3-20 characters long and contain only letters, numbers, and underscores.");
                return;
            }

            // Check if username already exists
            if (databaseManager.checkUsernameExists(request.username)) {
                sendRegistrationResponse(connection, false, "Username already exists.");
                return;
            }

            // Attempt to register the player in database
            boolean success = databaseManager.registerPlayer(request.username, request.password);

            if (success) {
                GameLogger.info("Successfully registered new player: " + request.username);
                sendRegistrationResponse(connection, true, "Registration successful!");
            } else {
                GameLogger.error("Failed to register player: " + request.username);
                sendRegistrationResponse(connection, false, "Registration failed. Please try again.");
            }

        } catch (Exception e) {
            GameLogger.error("Error during registration: " + e.getMessage());
            e.printStackTrace();
            sendRegistrationResponse(connection, false, "An error occurred during registration.");
        }
    }


    private void saveWorldData(String worldName, WorldData worldData) {
        storageSystem.saveWorld(worldData);
    }

    private void savePlayerData(String username, PlayerData playerData) {
        storageSystem.savePlayerData(username, playerData);
    }

    // Update existing load methods
    private WorldData loadWorldData(String worldName) {
        return storageSystem.loadWorld(worldName);
    }

    private PlayerData loadPlayerData(String username) {
        return storageSystem.loadPlayerData(username);
    }


    /**
     * Starts the GameServer by initializing components, binding ports, and loading plugins.
     */
    public void start() {
        try {
            GameLogger.info("Starting server...");

            if (!isPortAvailable(config.getTcpPort())) {
                throw new IOException("TCP port " + config.getTcpPort() + " is already in use.");
            }

            if (!isPortAvailable(config.getUdpPort())) {
                throw new IOException("UDP port " + config.getUdpPort() + " is already in use.");
            }

            // Initialize components
            storage.initialize();
            GameLogger.info("Storage system initialized");

            worldManager.init();
            GameLogger.info("World manager initialized");

            // Load plugins
            pluginManager.loadPlugins();
            pluginManager.enablePlugins();
            GameLogger.info("Plugins loaded");

            // Register network classes
            NetworkProtocol.registerClasses(networkServer.getKryo());
            GameLogger.info("Network classes registered");

            // Start network server
            networkServer.start();
            networkServer.bind(config.getTcpPort(), config.getUdpPort());

            running = true;
            GameLogger.info("Server started successfully on TCP port " + config.getTcpPort() +
                " and UDP port " + config.getUdpPort());
            GameLogger.info("Maximum players: " + config.getMaxPlayers());

        } catch (Exception e) {
            GameLogger.info("Failed to start server: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Server failed to start", e);
        }
    }

    /**
     * Checks if a specific port is available for binding.
     *
     * @param port The port number to check.
     * @return True if the port is available; false otherwise.
     */
    private boolean isPortAvailable(int port) {
        try (ServerSocket ss = new ServerSocket(port)) {
            ss.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Stops the GameServer gracefully by saving all world data, disconnecting players,
     * unloading plugins, and shutting down all components.
     */
    public void stop() {
        if (!running) return;
        running = false;

        GameLogger.info("Shutting down server...");

        // Save all world data
        worldManager.getWorlds().forEach((name, world) -> {
            try {
                storage.saveWorldData(name, world);
                GameLogger.info("World data saved: " + name);
            } catch (Exception e) {
                GameLogger.info("Error saving world " + name + ": " + e.getMessage());
                e.printStackTrace();
            }
        });

        // Disconnect all connected players
        playerManager.getOnlinePlayers().forEach(player -> {
            try {
                Connection connection = networkServer.getConnections()[Integer.parseInt(player.getSessionId())];
                if (connection != null) {
                    connection.close();
                    GameLogger.info("Disconnected player: " + player.getUsername());
                }
            } catch (Exception e) {
                GameLogger.info("Error disconnecting player " + player.getUsername() + ": " + e.getMessage());
                e.printStackTrace();
            }
        });

        // Shutdown other components
        pluginManager.disablePlugins();
        eventManager.shutdown();
        storage.shutdown();
        if (networkServer != null) {
            networkServer.stop();
        }


        // Existing shutdown code...

        // Dispose of textures
        GameLogger.info("Server shutdown complete.");
    }

    private final Map<Integer, ServerConnection> activeConnections = new ConcurrentHashMap<>();

    private class ServerConnection {
        final Connection connection;
        String username;
        long connectTime;
        JConsoleContext.ConnectionState state;

        ServerConnection(Connection connection) {
            this.connection = connection;
            this.connectTime = System.currentTimeMillis();
            this.state = JConsoleContext.ConnectionState.CONNECTING;
        }
    }

    private void handlePlayerConnect(Connection connection) {
        try {
            GameLogger.info("New connection attempt from: " + connection.getRemoteAddressTCP());

            if (playerManager.getOnlinePlayers().size() >= config.getMaxPlayers()) {
                GameLogger.info("Connection rejected: Max players reached");
                NetworkProtocol.ConnectionResponse response = new NetworkProtocol.ConnectionResponse();
                response.success = false;
                response.message = "Server is full";
                connection.sendTCP(response);
                connection.close();
                return;
            }

            // Add to active connections
            ServerConnection serverConn = new ServerConnection(connection);
            activeConnections.put(connection.getID(), serverConn);

            // Send successful connection response
            NetworkProtocol.ConnectionResponse response = new NetworkProtocol.ConnectionResponse();
            response.success = true;
            response.message = "Connected successfully";
            connection.sendTCP(response);

            GameLogger.info("Connection " + connection.getID() + " established");

        } catch (Exception e) {
            GameLogger.error("Error handling connection: " + e.getMessage());
            connection.close();
        }
    }


    private void handleNetworkMessage(Connection connection, Object object) {
        try {
            if (object instanceof NetworkProtocol.LoginRequest) {
                handleLoginRequest(connection, (NetworkProtocol.LoginRequest) object);
            } else if (object instanceof NetworkProtocol.RegisterRequest) {
                handleRegisterRequest(connection, (NetworkProtocol.RegisterRequest) object);
            } else if (object instanceof NetworkProtocol.InventoryUpdate) {
                handleInventoryUpdate(connection, (NetworkProtocol.InventoryUpdate) object);
            } else if (object instanceof NetworkProtocol.ChatMessage) {
                handleChatMessage(connection, (NetworkProtocol.ChatMessage) object);
            } else if (object instanceof NetworkProtocol.UsernameCheckRequest) {
                handleUsernameCheckRequest(connection, (NetworkProtocol.UsernameCheckRequest) object);
            } else if (object instanceof NetworkProtocol.WildPokemonSpawn) {
                handlePokemonSpawn(connection, (NetworkProtocol.WildPokemonSpawn) object);
            }
            if (object instanceof NetworkProtocol.PlayerUpdate) {
                GameLogger.info("Received PlayerUpdate for " + ((NetworkProtocol.PlayerUpdate) object).username);
                handlePlayerUpdate(connection, (NetworkProtocol.PlayerUpdate) object);  // Update player position locally
            } else {
                // Handle other message types as needed
                //                System.out
                //                    .println(STR."Received unknown message type: \{object.getClass().getName()}");
            }

            // Optionally, fire network events for plugins
            // eventManager.fireEvent(new NetworkMessageEvent(connection.getID(), object));
        } catch (Exception e) {
            //            System.out
            //                .println(STR."Error handling network message: \{e.getMessage()}");
            e.printStackTrace();
        }
    }


    private void handleInventoryUpdate(Connection connection, NetworkProtocol.InventoryUpdate update) {
        String username = connectedPlayers.get(connection.getID());
        if (username == null || !username.equals(update.username)) return;

        ServerPlayer player = playerManager.getPlayer(username);
        if (player != null) {
            player.updateInventory(update);

            // Save to world data
            WorldData worldData = worldManager.getCurrentWorld();
            if (worldData != null) {
                PlayerData playerData = worldData.getPlayerData(username);
                if (playerData != null) {
                    playerData.setInventoryItems(Arrays.asList(update.inventoryItems));
                    worldData.savePlayerData(username, playerData); // FIX PLEASE LOL CODE:YEAH
                }
            }

            // Broadcast to other clients
            networkServer.sendToAllExceptTCP(connection.getID(), update);
        }
    }

    private void handlePlayerDisconnect(Connection connection) throws IOException {
        String username = connectedPlayers.remove(connection.getID());
        if (username != null) {
            // Save final state
            ServerPlayer player = playerManager.getPlayer(username);
            if (player != null) {
                PlayerData finalState = player.getData();
                WorldData worldData = worldManager.getWorld(CreatureCaptureGame.MULTIPLAYER_WORLD_NAME);
                if (worldData != null) {
                    worldData.savePlayerData(username, finalState); // FIX PLEASE LOL CODE:YEAH
                    worldManager.saveWorld(worldData);
                    GameLogger.info("Saved final state for disconnected player: " + username);
                }
            }

            // Notify other clients
            NetworkProtocol.PlayerLeft leftMessage = new NetworkProtocol.PlayerLeft();
            leftMessage.username = username;
            networkServer.sendToAllExceptTCP(connection.getID(), leftMessage);
        }
    }


    private boolean shouldSavePlayerData(ServerPlayer player) {
        // Save data every 30 seconds or when position changed significantly
        long currentTime = System.currentTimeMillis();
        if (currentTime - player.getLastSaveTime() > 30000) {
            player.updateLastSaveTime(currentTime);
            return true;
        }
        return false;
    }

    /**
     * Initiates the shutdown process of the server.
     */
    public void shutdown() {
        stop();
    }

    /**
     * Checks if the server is currently running.
     *
     * @return True if the server is running, false otherwise.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Retrieves the server configuration.
     *
     * @return The ServerConfig instance.
     */
    public ServerConnectionConfig getConfig() {
        return config;
    }

    /**
     * Retrieves the WorldManager instance.
     *
     * @return The WorldManager instance.
     */
    public WorldManager getWorldManager() {
        return worldManager;
    }
}

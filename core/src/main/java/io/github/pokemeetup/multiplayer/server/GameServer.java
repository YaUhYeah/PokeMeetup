package io.github.pokemeetup.multiplayer.server;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Json;
import com.esotericsoftware.kryonet.Server;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.minlog.Log;
import com.sun.tools.jconsole.JConsoleContext;
import io.github.pokemeetup.CreatureCaptureGame;
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
import io.github.pokemeetup.utils.PasswordUtils;
import io.github.pokemeetup.utils.TextureManager;
import io.github.pokemeetup.utils.storage.JsonConfig;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class GameServer {
    private static final long CONNECTION_TIMEOUT = 1000;
    private static final int SCHEDULER_POOL_SIZE = 3;
    private static final long AUTH_TIMEOUT = 10000;
    private static final long CLEANUP_INTERVAL = 60000; // 1 minute
    private static final int SYNC_BATCH_SIZE = 10;
    private static final float SYNC_INTERVAL = 1 / 20f; // 20Hz sync rate
    private final Map<Integer, ConnectionState> connectionStates = new ConcurrentHashMap<>();
    private final Server networkServer;
    private final ServerConnectionConfig config;
    private final WorldManager worldManager;
    private final StorageSystem storage;
    private final BiomeManager biomeManager;
    private final ServerStorageSystem storageSystem;
    private final EventManager eventManager;
    private final DatabaseManager databaseManager;
    private final ConcurrentHashMap<Integer, String> connectedPlayers;
    private final PlayerManager playerManager;
    private final ScheduledExecutorService scheduler;
    private final Queue<NetworkProtocol.PlayerUpdate> pendingUpdates = new ConcurrentLinkedQueue<>();
    private final Map<String, Integer> activeUserConnections = new ConcurrentHashMap<>(); // Map username to connection ID
    private PluginManager pluginManager = null;
    private WorldData multiplayerWorld;
    private volatile boolean running;
    private NetworkProtocol.ServerInfo serverInfo;

    public GameServer(ServerConnectionConfig config) {
        this.scheduler = Executors.newScheduledThreadPool(SCHEDULER_POOL_SIZE, r -> {
            Thread thread = new Thread(r, "GameServer-Scheduler");
            thread.setDaemon(true);
            return thread;
        });

        Log.set(Log.LEVEL_DEBUG);
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

            this.multiplayerWorld = initializeMultiplayerWorld();

            if (this.multiplayerWorld == null) {
                throw new RuntimeException("Failed to initialize multiplayer world");
            }

            setupNetworkListener();
            this.pluginManager = new PluginManager(this, multiplayerWorld);
            this.biomeManager = new BiomeManager(multiplayerWorld.getConfig().getSeed());
        } catch (Exception e) {
            GameLogger.error("Failed to initialize game world: " + e.getMessage());
            throw new RuntimeException("Failed to initialize server world", e);
        }
    }
    private WorldData initializeMultiplayerWorld() {
        try {
            // First try to load existing world
            WorldData world = worldManager.getWorld(CreatureCaptureGame.MULTIPLAYER_WORLD_NAME);

            if (world == null) {
                GameLogger.info("Creating new multiplayer world");
                // Create new world with specific seed
                world = worldManager.createWorld(
                    CreatureCaptureGame.MULTIPLAYER_WORLD_NAME,
                    System.currentTimeMillis(), // or specific seed
                    0.15f,  // tree spawn rate
                    0.05f   // pokemon spawn rate
                );

                // Initialize world data
                world.setWorldTimeInMinutes(480.0); // Start at 8:00 AM
                world.setDayLength(24.0f);          // 24 minutes per day
                world.setPlayedTime(0);

                // Set spawn point
                world.setSpawnX(500); // Adjust based on your world size
                world.setSpawnY(500);

                // Save the initialized world
                worldManager.saveWorld(world);
                GameLogger.info("Created and saved new multiplayer world");
            }

            return world;

        } catch (Exception e) {
            GameLogger.error("Failed to initialize multiplayer world: " + e.getMessage());
            throw new RuntimeException("World initialization failed", e);
        }
    }

    public StorageSystem getStorage() {
        return storage;
    }

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

    private void broadcastPlayerStates() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                List<NetworkProtocol.PlayerUpdate> updates = new ArrayList<>();
                NetworkProtocol.PlayerUpdate update;

                // Batch pending updates
                while ((update = pendingUpdates.poll()) != null && updates.size() < SYNC_BATCH_SIZE) {
                    updates.add(update);
                }

                if (updates.isEmpty()) return;

                // Create combined update packet
                NetworkProtocol.PlayerPosition position = new NetworkProtocol.PlayerPosition();
                position.players = new HashMap<>();
                updates.forEach(u -> position.players.put(u.username, u));

                // Broadcast to all connected clients
                networkServer.sendToAllTCP(position);

            } catch (Exception e) {
                GameLogger.error("Error broadcasting player states: " + e.getMessage());
            }
        }, 0, (long) (SYNC_INTERVAL * 1000), TimeUnit.MILLISECONDS);
    }

    public void initializeServerInfo() {
        serverInfo = new NetworkProtocol.ServerInfo();
        serverInfo.name = config.getServerName();
        serverInfo.motd = config.getMotd();
        serverInfo.maxPlayers = config.getMaxPlayers();
        serverInfo.version = "1.0"; // Set your version

        // Load server icon if exists
        try {
            File iconFile = new File(config.getIconPath());
            if (iconFile.exists()) {
                byte[] iconBytes = Files.readAllBytes(iconFile.toPath());
                serverInfo.iconBase64 = Base64.getEncoder().encodeToString(iconBytes);
            }
        } catch (Exception e) {
            GameLogger.error("Error loading server icon: " + e.getMessage());
        }
    }

    private void handleServerInfoRequest(Connection connection, NetworkProtocol.ServerInfoRequest request) {
        try {
            NetworkProtocol.ServerInfoResponse response = new NetworkProtocol.ServerInfoResponse();
            serverInfo.playerCount = connectedPlayers.size();
            response.serverInfo = serverInfo;
            response.timestamp = System.currentTimeMillis();

            connection.sendTCP(response);
        } catch (Exception e) {
            GameLogger.error("Error handling server info request: " + e.getMessage());
        }
    }
    private final Map<String, ServerPlayer> activePlayers = new ConcurrentHashMap<>();
    private boolean isUserAlreadyConnected(String username) {
        synchronized (activeUserConnections) {
            Integer connectionId = activeUserConnections.get(username);
            if (connectionId != null) {
                Connection existingConnection = findConnection(connectionId);
                return existingConnection != null && existingConnection.isConnected();
            }
            return false;
        }
    }
    private void handleLoginRequest(Connection connection, NetworkProtocol.LoginRequest request) {
        try {
            GameLogger.info("Processing login request for: " + request.username);

            // Check if user is already connected
            if (isUserAlreadyConnected(request.username)) {
                // Force disconnect existing connection
                forceDisconnectUser(request.username);
            }

            // Prevent rapid join attempts
            Long lastJoin = lastJoinTime.get(request.username);
            if (lastJoin != null && System.currentTimeMillis() - lastJoin < JOIN_COOLDOWN) {
                sendLoginFailure(connection, "Please wait before reconnecting");
                return;
            }

            // Store connection time
            lastJoinTime.put(request.username, System.currentTimeMillis());

            if (!authenticateUser(request.username, request.password)) {
                sendLoginFailure(connection, "Invalid credentials");
                return;
            }

            // Create or load player
            ServerPlayer player = playerManager.createOrLoadPlayer(request.username);
            if (player == null) {
                sendLoginFailure(connection, "Failed to initialize player data");
                return;
            }

            // Register new connection
            synchronized (activeUserConnections) {
                // Remove any existing connection
                Integer oldConnectionId = activeUserConnections.get(request.username);
                if (oldConnectionId != null) {
                    Connection oldConnection = findConnection(oldConnectionId);
                    if (oldConnection != null && oldConnection.isConnected()) {
                        NetworkProtocol.ForceDisconnect forceDisconnect = new NetworkProtocol.ForceDisconnect();
                        forceDisconnect.reason = "Logged in from another location";
                        oldConnection.sendTCP(forceDisconnect);
                        oldConnection.close();
                    }
                }

                activeUserConnections.put(request.username, connection.getID());
                activePlayers.put(request.username, player);
            }

            // Handle successful login
            handleSuccessfulLogin(connection, player);

        } catch (Exception e) {
            GameLogger.error("Login error: " + e.getMessage());
            sendLoginFailure(connection, "Server error occurred");
        }
    }
    private WorldData cloneWorldDataWithoutOtherPlayers(WorldData originalWorldData, String currentUsername) {
        // Use serialization-deserialization for deep copy
        try {
            Json json = JsonConfig.getInstance();
            String jsonData = json.toJson(originalWorldData);
            WorldData clonedWorldData = json.fromJson(WorldData.class, jsonData);

            // Remove other players' data
            Map<String, PlayerData> playerDataMap = clonedWorldData.getPlayersMap();
            if (playerDataMap != null) {
                PlayerData currentPlayerData = playerDataMap.get(currentUsername);
                HashMap<String, PlayerData> newPlayerDataMap = new HashMap<>();
                if (currentPlayerData != null) {
                    newPlayerDataMap.put(currentUsername, currentPlayerData);
                }
                clonedWorldData.setPlayersMap(newPlayerDataMap);
            }

            return clonedWorldData;

        } catch (Exception e) {
            GameLogger.error("Error cloning world data: " + e.getMessage());
            return null;
        }
    }
    private void forceDisconnectUser(String username) {
        synchronized (activeUserConnections) {
            Integer connectionId = activeUserConnections.get(username);
            if (connectionId != null) {
                Connection existingConnection = findConnection(connectionId);
                if (existingConnection != null && existingConnection.isConnected()) {
                    NetworkProtocol.ForceDisconnect forceDisconnect = new NetworkProtocol.ForceDisconnect();
                    forceDisconnect.reason = "Logged in from another location";
                    existingConnection.sendTCP(forceDisconnect);
                    existingConnection.close();
                }
                activeUserConnections.remove(username);
                activePlayers.remove(username);
            }
        }
    }

    private void handleSuccessfulLogin(Connection connection, ServerPlayer player) {
            try {
                // Ensure world is initialized
                if (multiplayerWorld == null) {
                    throw new RuntimeException("Multiplayer world not initialized");
                }

                // Create login response with world data
                WorldData worldDataToSend = cloneWorldDataWithoutOtherPlayers(multiplayerWorld, player.getUsername());

                // Create login response with world data
                NetworkProtocol.LoginResponse response = new NetworkProtocol.LoginResponse();
                response.success = true;
                response.username = player.getUsername();
                response.message = "Login successful";

                // Set world data
                response.worldData = worldDataToSend;

                // Set player data
                response.playerData = player.getData();
                response.x = (int) player.getPosition().x;
                response.y = (int) player.getPosition().y;
                response.timestamp = System.currentTimeMillis();

                // Send response
                connection.sendTCP(response);

                // Update server state
                connectedPlayers.put(connection.getID(), player.getUsername());
                GameLogger.info("Player logged in successfully: " + player.getUsername());

                // Send existing players to new player
                sendExistingPlayersToNewPlayer(connection);

                // Broadcast new player to others
                broadcastNewPlayerToOthers(connection, player);

                // Send current world state
                sendWorldState(connection,player.getUsername());

            } catch (Exception e) {
                GameLogger.error("Error during login completion: " + e.getMessage());
                handleLoginError(connection, player, e);
            }
        }

    private void sendExistingPlayersToNewPlayer(Connection connection) {
        try {
            NetworkProtocol.PlayerPosition existingPlayers = new NetworkProtocol.PlayerPosition();
            existingPlayers.players = new HashMap<>();

            for (Map.Entry<String, ServerPlayer> entry : activePlayers.entrySet()) {
                Integer playerConnectionId = activeUserConnections.get(entry.getKey());
                if (playerConnectionId != null && playerConnectionId != connection.getID()) {
                    ServerPlayer existingPlayer = entry.getValue();
                    NetworkProtocol.PlayerUpdate update = createPlayerUpdate(existingPlayer);
                    existingPlayers.players.put(existingPlayer.getUsername(), update);
                }
            }

            connection.sendTCP(existingPlayers);
            GameLogger.info("Sent existing players to new connection: " + connection.getID());

        } catch (Exception e) {
            GameLogger.error("Error sending existing players: " + e.getMessage());
        }
    }


    private void broadcastNewPlayerToOthers(Connection connection, ServerPlayer newPlayer) {
            try {
                NetworkProtocol.PlayerJoined joinMessage = new NetworkProtocol.PlayerJoined();
                joinMessage.username = newPlayer.getUsername();
                joinMessage.x = newPlayer.getPosition().x;
                joinMessage.y = newPlayer.getPosition().y;
                joinMessage.direction = newPlayer.getDirection();
                joinMessage.isMoving = newPlayer.isMoving();
                joinMessage.timestamp = System.currentTimeMillis();

                // Send player joined message to all except new player
                networkServer.sendToAllExceptTCP(connection.getID(), joinMessage);

                // Send system message about new player
                NetworkProtocol.ChatMessage systemMessage = new NetworkProtocol.ChatMessage();
                systemMessage.sender = "System";
                systemMessage.content = newPlayer.getUsername() + " has joined the game";
                systemMessage.type = NetworkProtocol.ChatType.SYSTEM;
                systemMessage.timestamp = System.currentTimeMillis();

                networkServer.sendToAllTCP(systemMessage);
                GameLogger.info("Broadcast new player join: " + newPlayer.getUsername());

            } catch (Exception e) {
                GameLogger.error("Error broadcasting new player: " + e.getMessage());
            }
        }

    private void sendWorldState(Connection connection, String currentUsername) {
        try {
            WorldData worldDataToSend = cloneWorldDataWithoutOtherPlayers(multiplayerWorld, currentUsername);

            NetworkProtocol.WorldStateUpdate worldUpdate = new NetworkProtocol.WorldStateUpdate();
            worldUpdate.worldData = worldDataToSend;
            worldUpdate.timestamp = System.currentTimeMillis();

            connection.sendTCP(worldUpdate);
            GameLogger.info("Sent world state to connection: " + connection.getID());

        } catch (Exception e) {
            GameLogger.error("Error sending world state: " + e.getMessage());
        }
    }


    private NetworkProtocol.PlayerUpdate createPlayerUpdate(ServerPlayer player) {
            NetworkProtocol.PlayerUpdate update = new NetworkProtocol.PlayerUpdate();
            update.username = player.getUsername();
            update.x = player.getPosition().x;
            update.y = player.getPosition().y;
            update.direction = player.getDirection();
            update.isMoving = player.isMoving();
            update.wantsToRun = player.isRunning();
            update.timestamp = System.currentTimeMillis();

            // Include inventory if available
            List<ItemData> inventory = player.getInventoryItems();
            if (inventory != null) {
                update.inventoryItems = inventory.toArray(new ItemData[0]);
            }

            return update;
        }

        private void handleLoginError(Connection connection, ServerPlayer player, Exception e) {
            GameLogger.error("Login error for " + player.getUsername() + ": " + e.getMessage());

            // Clean up any partial state
            activeUserConnections.remove(player.getUsername());
            activePlayers.remove(player.getUsername());
            connectedPlayers.remove(connection.getID());

            // Send error response
            NetworkProtocol.LoginResponse response = new NetworkProtocol.LoginResponse();
            response.success = false;
            response.message = "Server error during login process";
            connection.sendTCP(response);

            // Consider disconnecting the client
            connection.close();
        }

    private Connection findConnection(int connectionId) {
        return Arrays.stream(networkServer.getConnections())
            .filter(conn -> conn.getID() == connectionId)
            .findFirst()
            .orElse(null);
    }

    private final Map<String, Long> lastJoinTime = new ConcurrentHashMap<>();
    private static final long JOIN_COOLDOWN = 5000; // 5 seconds cooldown between join attempts

    private void handlePlayerDisconnect(Connection connection) {
        String username = null;
        for (Map.Entry<String, Integer> entry : activeUserConnections.entrySet()) {
            if (entry.getValue() == connection.getID()) {
                username = entry.getKey();
                break;
            }
        }

        if (username != null) {
            // Clean up player state
            activeUserConnections.remove(username);
            connectedPlayers.remove(connection.getID()); // Add this line
            ServerPlayer player = activePlayers.remove(username);

            if (player != null) {
                // Save final state
                try {
                    PlayerData finalState = player.getData();
                    worldManager.getCurrentWorld().savePlayerData(username, finalState);
                } catch (Exception e) {
                    GameLogger.error("Error saving disconnect state: " + e.getMessage());
                }

                // Broadcast departure
                NetworkProtocol.PlayerLeft leftMessage = new NetworkProtocol.PlayerLeft();
                leftMessage.username = username;
                leftMessage.timestamp = System.currentTimeMillis();
                networkServer.sendToAllExceptTCP(connection.getID(), leftMessage);
            }
        }
    }


    private void handlePlayerUpdate(Connection connection, NetworkProtocol.PlayerUpdate update) {
        try {
            if (update == null || update.username == null) {
                return;
            }

            String connectedUsername = connectedPlayers.get(connection.getID());
            if (!update.username.equals(connectedUsername)) {
                return;
            }

            ServerPlayer player = playerManager.getPlayer(update.username);
            if (player == null) {
                return;
            }

            // Update server-side state
            player.updatePosition(update.x, update.y, update.direction, update.isMoving);

            // Create broadcast message
            NetworkProtocol.PlayerPosition position = new NetworkProtocol.PlayerPosition();
            position.players = new HashMap<>();
            position.players.put(update.username, update);

            // Broadcast the update to all clients
            networkServer.sendToAllTCP(position);

            GameLogger.info("Broadcasting position for " + update.username +
                " to (" + update.x + "," + update.y + ")");

        } catch (Exception e) {
            GameLogger.error("Error handling player update: " + e.getMessage());
        }
    }

    private boolean validateUpdate(NetworkProtocol.PlayerUpdate update) {
        if (update == null || update.username == null) {
            return false;
        }

        ServerPlayer player = playerManager.getPlayer(update.username);
        if (player == null) {
            return false;
        }

        // More permissive movement validation for testing
        float distance = Vector2.dst(
            player.getPosition().x, player.getPosition().y,
            update.x, update.y
        );

        float maxAllowedDistance = World.TILE_SIZE * 2; // Allow up to 2 tiles of movement

        boolean valid = distance <= maxAllowedDistance;
        if (!valid) {
            GameLogger.error("Large movement detected: " + distance + " pixels");
        }

        return true; // Allow all movements during testing
    }

    private void monitorConnections() {
        scheduler.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();

            for (Connection conn : networkServer.getConnections()) {
                ConnectionState state = connectionStates.get(conn.getID());
                if (state == null) continue;

                // Check keepalive timeout
                if (now - state.lastKeepAliveReceived > CONNECTION_TIMEOUT) {
                    state.failedKeepalives++;
                    if (state.failedKeepalives >= 3) {
                        GameLogger.info("Connection " + conn.getID() + " timed out");
                        handlePlayerDisconnect(conn);
                        conn.close();
                    }
                }
            }
        }, 1000, 1000, TimeUnit.MILLISECONDS);
    }

    private WorldObject generateObjectForBiome(Biome biome, float x, float y, Random random) {
        WorldObject.ObjectType objectType = getObjectTypeForBiome(biome, random);
        if (objectType == null) return null;

        TextureRegion texture = TextureManager.getTextureForObjectType(objectType);
        if (texture == null) return null;

        return new WorldObject((int) x, (int) y, texture, objectType);
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

    private void sendLoginSuccess(Connection connection, String username) {
        if (worldManager.getCurrentWorld() == null) {
            GameLogger.error("Current world is null in sendLoginSuccess");
            // Handle the error appropriately, possibly throw an exception
            return;
        }
        NetworkProtocol.LoginResponse response = new NetworkProtocol.LoginResponse();
        response.success = true;
        response.username = username;
        response.message = "Login successful";

        // Set additional data as needed
        response.worldSeed = worldManager.getCurrentWorld().getConfig().getSeed();
        response.worldTimeInMinutes = worldManager.getCurrentWorld().getWorldTimeInMinutes();
        response.dayLength = worldManager.getCurrentWorld().getDayLength();

        response.worldName = worldManager.getCurrentWorld().getName(); // Set worldName

        // Retrieve player coordinates
        int[] coordinates = databaseManager.getPlayerCoordinates(username);
        response.x = coordinates[0];
        response.y = coordinates[1];

        response.timestamp = System.currentTimeMillis();

        connection.sendTCP(response);
        GameLogger.info("Sent login success response to " + username);
    }

    private void broadcastPlayerJoined(Connection connection, String username) {
        try {
            // Get player data
            ServerPlayer player = playerManager.getPlayer(username);
            if (player == null) {
                GameLogger.error("Cannot broadcast join - player not found: " + username);
                return;
            }

            // Create join message
            NetworkProtocol.PlayerJoined joinMessage = new NetworkProtocol.PlayerJoined();
            joinMessage.username = username;
            joinMessage.x = player.getPosition().x;
            joinMessage.y = player.getPosition().y;
            joinMessage.direction = player.getDirection();
            joinMessage.isMoving = player.isMoving();
            joinMessage.timestamp = System.currentTimeMillis();

            // Broadcast to all connected clients except the joining player
            synchronized (networkServer.getConnections()) {
                for (Connection conn : networkServer.getConnections()) {
                    if (conn.getID() != connection.getID()) {
                        try {
                            conn.sendTCP(joinMessage);
                            GameLogger.info("Sent join message to connection " + conn.getID() +
                                " for player: " + username);
                        } catch (Exception e) {
                            GameLogger.error("Failed to send join message to connection " +
                                conn.getID() + ": " + e.getMessage());
                        }
                    }
                }
            }

            // Send system message about player joining
            NetworkProtocol.ChatMessage systemMessage = new NetworkProtocol.ChatMessage();
            systemMessage.sender = "System";
            systemMessage.content = username + " has joined the game";
            systemMessage.type = NetworkProtocol.ChatType.SYSTEM;
            systemMessage.timestamp = System.currentTimeMillis();

            networkServer.sendToAllTCP(systemMessage);
            GameLogger.info("Broadcast join message for player: " + username);

        } catch (Exception e) {
            GameLogger.error("Error broadcasting player join: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean authenticateUser(String username, String password) {
        // Retrieve the user's stored password hash from the database
        String storedHash = databaseManager.getPasswordHash(username);
        if (storedHash == null) {
            GameLogger.error("Authentication failed: Username '" + username + "' does not exist.");
            return false;
        }

        // Compare the provided password with the stored hash
        return PasswordUtils.verifyPassword(password, storedHash);
    }



    private void sendLoginFailure(Connection connection, String message) {
        NetworkProtocol.LoginResponse response = new NetworkProtocol.LoginResponse();
        response.success = false;
        response.message = message;
        networkServer.sendToTCP(connection.getID(), response);
    }

    public PlayerManager getPlayerManager() {
        return playerManager;
    }

    private void setupNetworkListener() {// In setupNetworkListener()
        networkServer.addListener(new Listener() {
            @Override
            public void received(Connection connection, Object object) {
                if (object instanceof NetworkProtocol.Keepalive) {
                    // Echo keepalive back
                    connection.sendTCP(object);
                    return;
                }
                // Rest of your message handling...
            }
        });
        networkServer.addListener(new Listener() {
            @Override
            public void connected(Connection connection) {
                try {
                    GameLogger.info("New connection attempt from: " + connection.getRemoteAddressTCP());

                    // Create and store connection state
                    ConnectionState state = new ConnectionState();
                    state.lastKeepAliveReceived = System.currentTimeMillis();
                    connectionStates.put(connection.getID(), state);
                    // Check max players
                    if (playerManager.getOnlinePlayers().size() >= config.getMaxPlayers()) {
                        GameLogger.info("Connection rejected: Max players reached");
                        sendConnectionResponse(connection, false, "Server is full");
                        scheduler.schedule(() -> connection.close(), 100, TimeUnit.MILLISECONDS);
                        return;
                    }

                    // Send success response
                    NetworkProtocol.ConnectionResponse response = new NetworkProtocol.ConnectionResponse();
                    response.success = true;
                    response.message = "Connection established";
                    connection.sendTCP(response);

                    GameLogger.info("Connection " + connection.getID() + " established - awaiting authentication");

                    // Set authentication timeout
                    scheduler.schedule(() -> {
                        if (!connectedPlayers.containsKey(connection.getID())) {
                            GameLogger.info("Authentication timeout for connection: " + connection.getID());
                            connection.close();
                        }
                    }, AUTH_TIMEOUT, TimeUnit.MILLISECONDS);

                } catch (Exception e) {
                    GameLogger.error("Error handling connection: " + e.getMessage());
                    connection.close();
                }
            }

            @Override
            public void received(Connection connection, Object object) {
                GameLogger.error("Received message : " + object.toString());
                try {
                    if (object instanceof NetworkProtocol.LoginRequest) {
                        handleLoginRequest(connection, (NetworkProtocol.LoginRequest) object);
                    } else if (object instanceof NetworkProtocol.RegisterRequest) {
                        handleRegisterRequest(connection, (NetworkProtocol.RegisterRequest) object);
                    } else if (!connectedPlayers.containsKey(connection.getID())) {
                        GameLogger.error("Received unauthorized message from: " + connection.getID());
                    } else if (object instanceof NetworkProtocol.ServerInfoRequest) {
                        handleServerInfoRequest(connection, (NetworkProtocol.ServerInfoRequest) object);
                    }  else if (object instanceof NetworkProtocol.Logout) {

                        handleLogout(connection, (NetworkProtocol.Logout) object);
                    }  else {
                        handleNetworkMessage(connection, object);
                    }
                } catch (Exception e) {
                    GameLogger.error("Error handling message: " + e.getMessage());
                    e.printStackTrace();
                }
            }

            @Override
            public void disconnected(Connection connection) {
                // Remove connection state
                connectionStates.remove(connection.getID());
                handlePlayerDisconnect(connection);
            }
        });
    }

    private void handleLogout(Connection connection, NetworkProtocol.Logout logout) {
        String username = connectedPlayers.get(connection.getID());
        if (username != null && username.equals(logout.username)) {
            try {
                // Save final state
                ServerPlayer player = playerManager.getPlayer(username);
                if (player != null) {
                    PlayerData finalState = player.getData();
                    WorldData worldData = worldManager.getCurrentWorld();
                    if (worldData != null) {
                        worldData.savePlayerData(username, finalState);
                        worldManager.saveWorld(worldData);
                    }
                }

                // Clean up connection
                handlePlayerDisconnect(connection);

                // Send acknowledgment
                NetworkProtocol.LogoutResponse response = new NetworkProtocol.LogoutResponse();
                response.success = true;
                connection.sendTCP(response);

            } catch (Exception e) {
                GameLogger.error("Error handling logout: " + e.getMessage());
                NetworkProtocol.LogoutResponse response = new NetworkProtocol.LogoutResponse();
                response.success = false;
                response.message = "Error saving state";
                connection.sendTCP(response);
            }
        }
    }



    private void sendConnectionResponse(Connection connection, boolean success, String message) {
        NetworkProtocol.ConnectionResponse response = new NetworkProtocol.ConnectionResponse();
        response.success = success;
        response.message = message;

        try {
            connection.sendTCP(response);
        } catch (Exception e) {
            GameLogger.error("Error sending connection response: " + e.getMessage());
        }
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

        for (Connection conn : networkServer.getConnections()) {
            if (conn.getID() != connection.getID()) {
                try {
                    networkServer.sendToTCP(conn.getID(), message);
                } catch (Exception e) {
                }
            }
        }
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

            networkServer.start();

            initializePeriodicTasks();

            broadcastPlayerStates();

            monitorConnections();
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

    private void initializePeriodicTasks() {
        // Schedule connection cleanup
        scheduler.scheduleAtFixedRate(() -> {
            try {
                for (Connection conn : networkServer.getConnections()) {
                    ConnectionState state = connectionStates.get(conn.getID());
                    if (state != null &&
                        System.currentTimeMillis() - state.lastKeepAliveReceived > CONNECTION_TIMEOUT) {
                        handlePlayerDisconnect(conn);
                    }
                }
            } catch (Exception e) {
                GameLogger.error("Error in periodic tasks: " + e.getMessage());
            }
        }, CLEANUP_INTERVAL, CLEANUP_INTERVAL, TimeUnit.MILLISECONDS);
    }

    private boolean isPortAvailable(int port) {
        try (ServerSocket ss = new ServerSocket(port)) {
            ss.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

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
            }
        });
        pluginManager.disablePlugins();
        eventManager.shutdown();
        storage.shutdown();
        if (networkServer != null) {
            networkServer.stop();
        }

        GameLogger.info("Server shutdown complete.");
    }


    private void handleNetworkMessage(Connection connection, Object object) {
        try {
            if (object instanceof NetworkProtocol.Keepalive) {
                // Update the connection state's last keepalive time
                ConnectionState state = connectionStates.get(connection.getID());
                if (state != null) {
                    state.lastKeepAliveReceived = System.currentTimeMillis();
                }
                // Echo keepalive back to client
                connection.sendTCP(object);
                return;
            }
            if (object instanceof NetworkProtocol.PlayerUpdate) {
                NetworkProtocol.PlayerUpdate update = (NetworkProtocol.PlayerUpdate) object;
                if (validateUpdate(update)) {
                    pendingUpdates.offer(update);
                }
                handlePlayerUpdate(connection, update);
                return;
            }
            GameLogger.info("Server received message: " + object.getClass().getName());
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

        } catch (Exception e) {
            GameLogger.error("Error handling network message: " + e.getMessage());
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

    public void shutdown() {
        stop();
    }

    public ServerConnectionConfig getConfig() {
        return config;
    }

    private class ConnectionState {
        long lastKeepAliveReceived = System.currentTimeMillis();
        String username;
        boolean authenticated;
        int failedKeepalives = 0;
    }

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
}

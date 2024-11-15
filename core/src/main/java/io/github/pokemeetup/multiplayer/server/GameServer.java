package io.github.pokemeetup.multiplayer.server;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Json;
import com.esotericsoftware.kryonet.Server;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.minlog.Log;
import com.sun.org.apache.bcel.internal.generic.ObjectType;
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
import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;
import io.github.pokemeetup.system.gameplay.overworld.multiworld.WorldManager;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.PasswordUtils;
import io.github.pokemeetup.utils.textures.TextureManager;
import io.github.pokemeetup.utils.storage.JsonConfig;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class GameServer {
    private static final long CONNECTION_TIMEOUT = 1000;
    private static final int SCHEDULER_POOL_SIZE = 3;// At the start of GameServer class, update constants
    private static final int WRITE_BUFFER = 65536; // Increased for larger chunks
    private static final int OBJECT_BUFFER = 32768; // Increased for larger chunks
    private static final long AUTH_TIMEOUT = 10000;
    private static final long CLEANUP_INTERVAL = 60000; // 1 minute
    private static final int SYNC_BATCH_SIZE = 10;
    private static final float SYNC_INTERVAL = 1 / 20f; // 20Hz sync rate
    private static final long JOIN_COOLDOWN = 5000; // 5 seconds cooldown between join attempts
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
    private final Map<String, Integer> activeUserConnections = new ConcurrentHashMap<>();
    private final Map<String, ServerPlayer> activePlayers = new ConcurrentHashMap<>();
    private final Map<String, Long> lastJoinTime = new ConcurrentHashMap<>();
    private final Map<Vector2, Chunk> generatedChunks = new ConcurrentHashMap<>();
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
        this.networkServer = new Server(WRITE_BUFFER, OBJECT_BUFFER);
        NetworkProtocol.registerClasses(networkServer.getKryo());

        // Enable chunked/fragmented transfers for large packets
        networkServer.getKryo().setReferences(true);
        networkServer.getKryo().setRegistrationRequired(false);
        this.databaseManager = new DatabaseManager();
        this.storage = new FileStorage(config.getDataDirectory());
        this.eventManager = new EventManager();
        this.connectedPlayers = new ConcurrentHashMap<>();
        this.playerManager = new PlayerManager(databaseManager, storage, eventManager);
        this.worldManager =
            WorldManager.getInstance(storageSystem, true);


        try {

            this.multiplayerWorld = initializeMultiplayerWorld();

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

                world.setWorldTimeInMinutes(480.0);
                world.setDayLength(24.0f);
                world.setPlayedTime(0);

                // Set spawn point
                world.setSpawnX(0);
                world.setSpawnY(0);

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
                connectedPlayers.remove(connectionId); // Add this line
            }
        }
    }


    private void handleSuccessfulLogin(Connection connection, ServerPlayer player) {
        try {
            // Send the full world data without removing other players
            NetworkProtocol.LoginResponse response = new NetworkProtocol.LoginResponse();
            response.success = true;
            response.username = player.getUsername();
            response.message = "Login successful";

            // Set world data
            response.worldData = multiplayerWorld; // Send the shared world data

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
                    } else if (object instanceof NetworkProtocol.Logout) {

                        handleLogout(connection, (NetworkProtocol.Logout) object);
                    } else {
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
            } else if (object instanceof NetworkProtocol.ChunkRequest) {
                handleChunkRequest(connection, (NetworkProtocol.ChunkRequest) object);
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
    private int selectTileTypeFromNoise(Biome primaryBiome, Biome secondaryBiome,
                                        float noise, float elevation, float transitionFactor) {

        // Get distributions
        Map<Integer, Integer> primaryDist = primaryBiome.getTileDistribution();
        Map<Integer, Integer> secondaryDist = secondaryBiome != null ?
            secondaryBiome.getTileDistribution() : null;

        // Use noise value to select tile
        float threshold = noise;

        // Modify threshold based on elevation
        if (elevation > 0.8f) {
            // More rocks/mountains at high elevation
            threshold = Math.min(1.0f, threshold + 0.2f);
        } else if (elevation < 0.2f) {
            // More water/sand at low elevation
            threshold = Math.max(0.0f, threshold - 0.2f);
        }

        // Select tile type based on threshold and distributions

        return selectTileFromDistribution(
            threshold < transitionFactor ? Objects.requireNonNull(secondaryDist) : primaryDist,
            new Random()
        );
    }

    private int generateTransitionTile(
        Map<Integer, Integer> primaryDist,
        Map<Integer, Integer> secondaryDist,
        float transitionFactor,
        Random random,
        float worldX,
        float worldY
    ) {
        // Use noise to create natural-looking transitions
        float noise = biomeManager.getNoise(worldX / 100f, worldY / 100f);
        float adjustedFactor = transitionFactor * (0.5f + (noise * 0.5f));

        // Determine which distribution to use based on transition factor
        if (random.nextFloat() > adjustedFactor) {
            return generateBiomeTile(primaryDist, random, worldX, worldY);
        } else {
            return generateBiomeTile(secondaryDist, random, worldX, worldY);
        }
    }

    private int generateBiomeTile(
        Map<Integer, Integer> distribution,
        Random random,
        float worldX,
        float worldY
    ) {
        // Add some noise-based variation
        float noise = biomeManager.getNoise(worldX / 50f, worldY / 50f);

        // Get base tile type from distribution
        int tileType = selectTileFromDistribution(distribution, random);

        // Potentially modify tile based on noise
        if (noise > 0.7f && isValidTileVariation(tileType)) {
            return getVariationForTile(tileType, random);
        }

        return tileType;
    }

    private int selectTileFromDistribution(Map<Integer, Integer> distribution, Random random) {
        int roll = random.nextInt(100);
        int currentTotal = 0;

        for (Map.Entry<Integer, Integer> entry : distribution.entrySet()) {
            currentTotal += entry.getValue();
            if (roll < currentTotal) {
                return entry.getKey();
            }
        }

        // Fallback to first tile type if something goes wrong
        return distribution.keySet().iterator().next();
    }

    private boolean isValidTileVariation(int baseTile) {
        // Define which tiles can have variations
        switch (baseTile) {
            case Chunk.GRASS:
            case Chunk.SAND:
            case Chunk.ROCK:
                return true;
            default:
                return false;
        }
    }

    private int getVariationForTile(int baseTile, Random random) {
        // Add variation to base tiles
        switch (baseTile) {
            case Chunk.GRASS:
                return random.nextFloat() < 0.3f ? Chunk.GRASS + 1 : baseTile;
            case Chunk.SAND:
                return random.nextFloat() < 0.2f ? Chunk.SAND + 1 : baseTile;
            case Chunk.ROCK:
                return random.nextFloat() < 0.25f ? Chunk.ROCK + 1 : baseTile;
            default:
                return baseTile;
        }
    }

    private float getObjectDensityMultiplier(BiomeType biomeType) {
        switch (biomeType) {
            case FOREST:
                return 1.5f;
            case DESERT:
                return 0.3f;
            case SNOW:
                return 0.8f;
            case HAUNTED:
                return 1.2f;
            default:
                return 1.0f;
        }
    }

    private void handleChunkRequest(Connection connection, NetworkProtocol.ChunkRequest request) {
        try {
            Vector2 chunkPos = new Vector2(request.chunkX, request.chunkY);
            GameLogger.info("Processing chunk request for: " + chunkPos);

            Chunk chunk = generateNewChunk((int)chunkPos.x, (int)chunkPos.y);
            if (chunk == null) {
                GameLogger.error("Failed to generate chunk at: " + chunkPos);
                return;
            }

            // Send data in fragments
            int fragmentSize = request.fragmentSize;
            int totalFragments = (World.CHUNK_SIZE / fragmentSize) * (World.CHUNK_SIZE / fragmentSize);

            for (int i = 0; i < totalFragments; i++) {
                NetworkProtocol.ChunkDataFragment fragment = new NetworkProtocol.ChunkDataFragment();
                fragment.chunkX = request.chunkX;
                fragment.chunkY = request.chunkY;
                fragment.fragmentIndex = i;
                fragment.totalFragments = totalFragments;
                fragment.biomeType = chunk.getBiome().getType();

                // Calculate fragment bounds
                int startX = (i % (World.CHUNK_SIZE / fragmentSize)) * fragmentSize;
                int startY = (i / (World.CHUNK_SIZE / fragmentSize)) * fragmentSize;
                fragment.startX = startX;
                fragment.startY = startY;

                // Copy tile data for this fragment
                fragment.tileData = new int[fragmentSize][fragmentSize];
                for (int x = 0; x < fragmentSize; x++) {
                    for (int y = 0; y < fragmentSize; y++) {
                        if (startX + x < World.CHUNK_SIZE && startY + y < World.CHUNK_SIZE) {
                            fragment.tileData[x][y] = chunk.getTileData()[startX + x][startY + y];
                        }
                    }
                }

                connection.sendTCP(fragment);
            }

            // Send completion message
            NetworkProtocol.ChunkDataComplete complete = new NetworkProtocol.ChunkDataComplete();
            complete.chunkX = request.chunkX;
            complete.chunkY = request.chunkY;
            connection.sendTCP(complete);

        } catch (Exception e) {
            GameLogger.error("Error processing chunk request: " + e.getMessage());
        }
    }

    private void sendChunkDataSafely(Connection connection, NetworkProtocol.ChunkData data) {
        try {
            // Split large data if needed
            if (isDataTooLarge(data)) {
                sendFragmentedChunkData(connection, data);
            } else {
                connection.sendTCP(data);
            }
        } catch (Exception e) {
            GameLogger.error("Error sending chunk data: " + e.getMessage());
        }
    }

    private boolean isDataTooLarge(NetworkProtocol.ChunkData data) {
        // Estimate size based on chunk data
        int estimatedSize = 8 + // Basic fields
            (World.CHUNK_SIZE * World.CHUNK_SIZE * 4) + // Tile data
            1024; // Extra buffer for other data
        return estimatedSize > OBJECT_BUFFER;
    }

    private void sendFragmentedChunkData(Connection connection, NetworkProtocol.ChunkData data) {
        try {
            // Create fragment info
            NetworkProtocol.ChunkDataFragment fragment = new NetworkProtocol.ChunkDataFragment();
            fragment.chunkX = data.chunkX;
            fragment.chunkY = data.chunkY;
            fragment.biomeType = data.biomeType;

            // Send tile data in smaller chunks
            int fragmentSize = World.CHUNK_SIZE / 2; // Split into 4 parts

            for (int startX = 0; startX < World.CHUNK_SIZE; startX += fragmentSize) {
                for (int startY = 0; startY < World.CHUNK_SIZE; startY += fragmentSize) {
                    fragment.startX = startX;
                    fragment.startY = startY;
                    fragment.tileData = new int[fragmentSize][fragmentSize];

                    // Copy portion of tile data
                    for (int x = 0; x < fragmentSize; x++) {
                        for (int y = 0; y < fragmentSize; y++) {
                            if (startX + x < data.tileData.length &&
                                startY + y < data.tileData[0].length) {
                                fragment.tileData[x][y] = data.tileData[startX + x][startY + y];
                            }
                        }
                    }

                    connection.sendTCP(fragment);
                }
            }

            // Send completion message
            NetworkProtocol.ChunkDataComplete complete = new NetworkProtocol.ChunkDataComplete();
            complete.chunkX = data.chunkX;
            complete.chunkY = data.chunkY;
            connection.sendTCP(complete);

        } catch (Exception e) {
            GameLogger.error("Error sending fragmented chunk data: " + e.getMessage());
        }
    }

    // In GameServer.java
    private Chunk generateNewChunk(int chunkX, int chunkY) {
        try {
            // Calculate world coordinates
            float worldX = chunkX * World.CHUNK_SIZE * World.TILE_SIZE;
            float worldY = chunkY * World.CHUNK_SIZE * World.TILE_SIZE;

            // Get biome for chunk
            BiomeTransitionResult biomeTransition = biomeManager.getBiomeAt(worldX, worldY);
            if (biomeTransition == null || biomeTransition.getPrimaryBiome() == null) {
                GameLogger.error("Invalid biome transition at: " + worldX + "," + worldY);
                return null;
            }

            Biome biome = biomeTransition.getPrimaryBiome();
            GameLogger.info("Generating chunk at (" + chunkX + "," + chunkY +
                ") with biome: " + biome.getType());

            // Create new chunk
            Chunk chunk = new Chunk(chunkX, chunkY, biome, multiplayerWorld.getConfig().getSeed(), biomeManager);

            // Generate chunk tile data
            int[][] tileData = new int[World.CHUNK_SIZE][World.CHUNK_SIZE];
            Random random = new Random(multiplayerWorld.getConfig().getSeed() + (chunkX * 31L + chunkY * 17L));

            // Get tile distribution for biome
            Map<Integer, Integer> distribution = biome.getTileDistribution();

            // Fill chunk with tiles based on biome distribution
            for (int x = 0; x < World.CHUNK_SIZE; x++) {
                for (int y = 0; y < World.CHUNK_SIZE; y++) {
                    int tileType = selectTileType(distribution, random);
                    tileData[x][y] = tileType;
                }
            }

            chunk.setTileData(tileData);

            // Generate objects for chunk
            try {
                    List<io.github.pokemeetup.system.gameplay.overworld.WorldObject> objects = generateChunkObjects(chunk, new Vector2(chunkX, chunkY));
                if (objects != null && !objects.isEmpty()) {
                    Vector2 chunkPos = new Vector2(chunkX, chunkY);
                    multiplayerWorld.addChunkObjects(chunkPos, objects);
                }
            } catch (Exception e) {
                GameLogger.error("Error generating chunk objects: " + e.getMessage());
            }

            return chunk;

        } catch (Exception e) {
            GameLogger.error("Error generating chunk: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private List<io.github.pokemeetup.system.gameplay.overworld.WorldObject> generateChunkObjects(Chunk chunk, Vector2 chunkPos) {
        List<io.github.pokemeetup.system.gameplay.overworld.WorldObject> objects = new ArrayList<>();
        Random random = new Random(multiplayerWorld.getConfig().getSeed() +
            ((long)chunkPos.x * 31 + (long)chunkPos.y * 17));

        float baseObjectDensity = multiplayerWorld.getConfig().getTreeSpawnRate();
        float biomeMultiplier = getObjectDensityMultiplier(chunk.getBiome().getType());
        float density = baseObjectDensity * biomeMultiplier;

        // Get appropriate object types for this biome
        List<io.github.pokemeetup.system.gameplay.overworld.WorldObject.ObjectType> possibleTypes = new ArrayList<>();
        switch (chunk.getBiome().getType()) {
            case FOREST:
                possibleTypes.add(io.github.pokemeetup.system.gameplay.overworld.WorldObject.ObjectType.TREE);
                possibleTypes.add(io.github.pokemeetup.system.gameplay.overworld.WorldObject.ObjectType.BUSH);
                break;
            case DESERT:
                possibleTypes.add(io.github.pokemeetup.system.gameplay.overworld.WorldObject.ObjectType.CACTUS);
                possibleTypes.add(io.github.pokemeetup.system.gameplay.overworld.WorldObject.ObjectType.DEAD_TREE);
                break;
            case SNOW:
                possibleTypes.add(io.github.pokemeetup.system.gameplay.overworld.WorldObject.ObjectType.SNOW_TREE);
                break;
            case HAUNTED:
                possibleTypes.add(io.github.pokemeetup.system.gameplay.overworld.WorldObject.ObjectType.HAUNTED_TREE);
                possibleTypes.add(io.github.pokemeetup.system.gameplay.overworld.WorldObject.ObjectType.SMALL_HAUNTED_TREE);
                break;
            default:
                possibleTypes.add(io.github.pokemeetup.system.gameplay.overworld.WorldObject.ObjectType.TREE);
                possibleTypes.add(io.github.pokemeetup.system.gameplay.overworld.WorldObject.ObjectType.BUSH);
        }

        for (int x = 0; x < World.CHUNK_SIZE; x++) {
            for (int y = 0; y < World.CHUNK_SIZE; y++) {
                if (random.nextFloat() < density) {
                    // Calculate world coordinates
                    float worldX = (chunkPos.x * World.CHUNK_SIZE + x) * World.TILE_SIZE;
                    float worldY = (chunkPos.y * World.CHUNK_SIZE + y) * World.TILE_SIZE;

                    // Select random object type from possible types for this biome
                io.github.pokemeetup.system.gameplay.overworld.WorldObject.ObjectType type = possibleTypes.get(random.nextInt(possibleTypes.size()));

                    // Create WorldObject without texture - clients will add textures
                    io.github.pokemeetup.system.gameplay.overworld.WorldObject obj = new io.github.pokemeetup.system.gameplay.overworld.WorldObject(
                        (int)worldX / World.TILE_SIZE, // Convert back to tile coordinates
                        (int)worldY / World.TILE_SIZE,
                        null,  // No texture on server
                        type
                    );

                    objects.add(obj);
                }
            }
        }

        return objects;
    }

    private int selectTileType(Map<Integer, Integer> distribution, Random random) {
        int roll = random.nextInt(100);
        int total = 0;

        for (Map.Entry<Integer, Integer> entry : distribution.entrySet()) {
            total += entry.getValue();
            if (roll < total) {
                return entry.getKey();
            }
        }

        // Default to most common tile if something goes wrong
        return distribution.keySet().iterator().next();
    }

    private List<ObjectType> getObjectTypesForBiome(BiomeType biomeType) {
        List<ObjectType> types = new ArrayList<>();
        switch (biomeType) {
            case FOREST:
                types.add(ObjectType.TREE);
                types.add(ObjectType.BUSH);
                break;
            case DESERT:
                types.add(ObjectType.CACTUS);
                types.add(ObjectType.DEAD_TREE);
                break;
            case SNOW:
                types.add(ObjectType.SNOW_TREE);
                break;
            case HAUNTED:
                types.add(ObjectType.HAUNTED_TREE);
                break;
            default:
                types.add(ObjectType.TREE);
                break;
        }
        return types;
    }


    private List<WorldObject> generateChunkObjects(
        int chunkX,
        int chunkY,
        BiomeType biomeType,
        float density
    ) {
        List<WorldObject> objects = new ArrayList<>();
        Random random = new Random(multiplayerWorld.getConfig().getSeed() + (chunkX * 31L + chunkY * 17L));

        // Calculate world coordinates
        float worldX = chunkX * World.CHUNK_SIZE * World.TILE_SIZE;
        float worldY = chunkY * World.CHUNK_SIZE * World.TILE_SIZE;

        // Get appropriate object types for this biome
        List<ObjectType> possibleTypes = getObjectTypesForBiome(biomeType);

        // Generate objects
        for (int x = 0; x < World.CHUNK_SIZE; x++) {
            for (int y = 0; y < World.CHUNK_SIZE; y++) {
                if (random.nextFloat() < density) {
                    float objX = worldX + (x * World.TILE_SIZE);
                    float objY = worldY + (y * World.TILE_SIZE);

                    // Select random object type appropriate for biome
                    ObjectType type = possibleTypes.get(
                        random.nextInt(possibleTypes.size())
                    );

                    WorldObject obj = new WorldObject();
                    obj.x = objX;
                    obj.y = objY;
                    obj.type = type;
                    obj.id = UUID.randomUUID();

                    objects.add(obj);
                }
            }
        }

        return objects;
    }

    private ObjectType getAppropriateObjectType(Biome biome, Random random) {
        switch (biome.getType()) {
            case FOREST:
                return random.nextFloat() < 0.8f ? ObjectType.TREE : ObjectType.BUSH;
            case DESERT:
                return random.nextFloat() < 0.7f ? ObjectType.CACTUS : ObjectType.DEAD_TREE;
            case SNOW:
                return ObjectType.SNOW_TREE;
            case HAUNTED:
                return ObjectType.HAUNTED_TREE;
            default:
                return random.nextFloat() < 0.5f ? ObjectType.TREE :ObjectType.BUSH;
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

    public class WorldObject {
        private float x, y;
        private ObjectType type;
        private UUID id;

        // Simple enum for object types without texture dependencies

    }
    public enum ObjectType {
        TREE,
        BUSH,
        CACTUS,
        SNOW_TREE,
        HAUNTED_TREE,
        DEAD_TREE,
        POKEBALL,
        VINES;

        // Optional density multipliers per type if needed
        public float getDensityMultiplier() {
            switch (this) {
                case TREE:
                    return 1.0f;
                case BUSH:
                    return 0.7f;
                case CACTUS:
                    return 0.3f;
                case SNOW_TREE:
                    return 0.8f;
                case HAUNTED_TREE:
                    return 1.2f;
                default:
                    return 1.0f;
            }
        }
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

package io.github.pokemeetup.multiplayer.server;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.esotericsoftware.kryonet.Server;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import io.github.pokemeetup.CreatureCaptureGame;
import io.github.pokemeetup.managers.DatabaseManager;
import io.github.pokemeetup.multiplayer.PlayerManager;
import io.github.pokemeetup.multiplayer.ServerPlayer;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.multiplayer.server.config.ServerConfig;
import io.github.pokemeetup.multiplayer.server.config.ServerConnectionConfig;
import io.github.pokemeetup.multiplayer.server.events.EventManager;
import io.github.pokemeetup.multiplayer.server.plugin.PluginManager;
import io.github.pokemeetup.multiplayer.server.storage.FileStorage;
import io.github.pokemeetup.multiplayer.server.storage.StorageSystem;
import io.github.pokemeetup.system.PlayerData;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.multiworld.WorldData;
import io.github.pokemeetup.system.gameplay.overworld.multiworld.WorldManager;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
    private final PluginManager pluginManager;
    private final StorageSystem storage;
    private final ServerStorageSystem storageSystem;
    private final EventManager eventManager;
    private final DatabaseManager databaseManager;
    private final ConcurrentHashMap<Integer, String> connectedPlayers;
    private final PlayerManager playerManager; // Manages player-related operations
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
        this.worldManager = new WorldManager(storageSystem);
        this.databaseManager = new DatabaseManager(); // Initialize DatabaseManager
        this.storage = new FileStorage(config.getDataDirectory());
        this.eventManager = new EventManager();
        this.connectedPlayers = new ConcurrentHashMap<>();
        this.playerManager = new PlayerManager(databaseManager, storage, eventManager);
        this.worldManager.init();
        World gameWorld;
        try {
            // Get or create the multiplayer world
            WorldData worldData = worldManager.getWorld(CreatureCaptureGame.MULTIPLAYER_WORLD_NAME);
            if (worldData == null) {
                // Create default multiplayer world if it doesn't exist
                worldData = worldManager.createWorld(
                    CreatureCaptureGame.MULTIPLAYER_WORLD_NAME,
                    System.currentTimeMillis(), // or use a fixed seed
                    0.15f,  // tree spawn rate
                    0.05f   // pokemon spawn rate
                );
            }
            this.pluginManager = new PluginManager(this, worldData);

            // Create the World instance


        } catch (Exception e) {
            System.err.println("Failed to initialize game world: " + e.getMessage());
            throw new RuntimeException("Failed to initialize server world" + e.getMessage());
        }

        // Initialize PluginManager with the world
        setupNetworkListener();
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

    private void handlePlayerConnect(Connection connection) {
        //        System.out.println(STR."New connection: \{connection.getID()}");

        if (playerManager.getOnlinePlayers().size() >= config.getMaxPlayers()) {
            connection.close();
            System.out.println("Connection rejected: Max players reached");
        }
    }

    /**
     * Handles login requests from clients.
     *
     * @param connection The connection from which the login request was received.
     * @param request    The login request containing username and password.
     */
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
                response.worldSeed = worldManager.getCurrentWorld().getSeed();

                connectedPlayers.put(connection.getID(), player.getUsername());

                // Broadcast new player to others and send existing players to new player
                broadcastNewPlayer(player);
                sendExistingPlayers(connection);

                //                System.out.println(STR."Player logged in successfully: \{player.getUsername()} at position (\{response.x}, \{response.y})");
            } else {
                response.success = false;
                response.message = "Login failed. Incorrect username or password.";
                //   System.out.println(STR."Login failed for username: \{request.username}");
            }

            networkServer.sendToTCP(connection.getID(), response);
        } catch (Exception e) {
            System.err.println("Error during login: " + e.getMessage());
            e.printStackTrace();

            NetworkProtocol.LoginResponse response = new NetworkProtocol.LoginResponse();
            response.success = false;
            response.message = "An error occurred during login";
            networkServer.sendToTCP(connection.getID(), response);
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

        //        System.out.println(STR."Server received chat message from: \{message.sender} content: \{message.content}");

        // Broadcast to all connected clients except sender
        for (Connection conn : networkServer.getConnections()) {
            if (conn.getID() != connection.getID()) {
                try {
                    networkServer.sendToTCP(conn.getID(), message);
                } catch (Exception e) {
                    //                    System.err.println(STR."Failed to broadcast message to client \{conn.getID()}: \{e.getMessage()}");
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
        joinMessage.inventoryItemNames = newPlayer.getInventory();

        // Send to all except the new player
        networkServer.sendToAllExceptTCP(
            Integer.parseInt(newPlayer.getSessionId()),
            joinMessage
        );

        System.out.println("Broadcasted new player join: " + newPlayer.getUsername() +
            " at (" + joinMessage.x + "," + joinMessage.y + ")");
    }

    /**
     * Handles registration requests from clients.
     *
     * @param connection The connection from which the registration request was received.
     * @param request    The registration request containing desired username and password.
     */
    private void handleRegisterRequest(Connection connection, NetworkProtocol.RegisterRequest request) {
        try {
            // Attempt to register the player using PlayerManager
            boolean registrationSuccess = playerManager.registerPlayer(request.username, request.password);

            if (registrationSuccess) {
                // Successful registration
                NetworkProtocol.RegisterResponse response = new NetworkProtocol.RegisterResponse();
                response.success = true;
                response.message = "Registration successful. You can now log in.";

                networkServer.sendToTCP(connection.getID(), response);

                System.out.println("Player registered: " + request.username);
            } else {
                // Registration failed (e.g., username already taken)
                NetworkProtocol.RegisterResponse response = new NetworkProtocol.RegisterResponse();
                response.success = false;
                response.message = "Registration failed. Username already taken.";

                networkServer.sendToTCP(connection.getID(), response);
            }
        } catch (Exception e) {
            // Handle unexpected exceptions
            NetworkProtocol.RegisterResponse response = new NetworkProtocol.RegisterResponse();
            response.success = false;
            response.message = "An error occurred during registration: " + e.getMessage();

            networkServer.sendToTCP(connection.getID(), response);

            System.err.println("Error during registration: " + e.getMessage());
            e.printStackTrace();
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
            System.out.println("Starting server...");

            if (!isPortAvailable(config.getTcpPort())) {
                throw new IOException("TCP port " + config.getTcpPort() + " is already in use.");
            }

            if (!isPortAvailable(config.getUdpPort())) {
                throw new IOException("UDP port " + config.getUdpPort() + " is already in use.");
            }

            // Initialize components
            storage.initialize();
            System.out.println("Storage system initialized");

            worldManager.init();
            System.out.println("World manager initialized");

            // Load plugins
            pluginManager.loadPlugins();
            pluginManager.enablePlugins();
            System.out.println("Plugins loaded");

            // Register network classes
            NetworkProtocol.registerClasses(networkServer.getKryo());
            System.out.println("Network classes registered");

            // Start network server
            networkServer.start();
            networkServer.bind(config.getTcpPort(), config.getUdpPort());

            running = true;
            System.out.println("Server started successfully on TCP port " + config.getTcpPort() +
                " and UDP port " + config.getUdpPort());
            System.out.println("Maximum players: " + config.getMaxPlayers());

        } catch (Exception e) {
            System.err.println("Failed to start server: " + e.getMessage());
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
        try (java.net.ServerSocket ss = new java.net.ServerSocket(port)) {
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

        System.out.println("Shutting down server...");

        // Save all world data
        worldManager.getWorlds().forEach((name, world) -> {
            try {
                storage.saveWorldData(name, world);
                System.out.println("World data saved: " + name);
            } catch (Exception e) {
                System.err.println("Error saving world " + name + ": " + e.getMessage());
                e.printStackTrace();
            }
        });

        // Disconnect all connected players
        playerManager.getOnlinePlayers().forEach(player -> {
            try {
                Connection connection = networkServer.getConnections()[Integer.parseInt(player.getSessionId())];
                if (connection != null) {
                    connection.close();
                    System.out.println("Disconnected player: " + player.getUsername());
                }
            } catch (Exception e) {
                System.err.println("Error disconnecting player " + player.getUsername() + ": " + e.getMessage());
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
        System.out.println("Server shutdown complete.");
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
            }
            if (object instanceof NetworkProtocol.PlayerUpdate update) {
                //                System.out.println(STR."Received PlayerUpdate for \{update.username}");
                handlePlayerUpdate(connection, update);  // Update player position locally
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
        try {
            String username = update.username;
            ServerPlayer player = playerManager.getPlayer(username);

            if (player != null) {
                player.updateInventory(update.itemNames);
                System.out.println("Updated inventory for player: " + username);

                // Save to world data
                WorldData worldData = worldManager.getWorld(CreatureCaptureGame.MULTIPLAYER_WORLD_NAME);
                if (worldData != null) {
                    PlayerData playerData = worldData.getPlayerData(username);
                    if (playerData != null) {
                        playerData.updateFromPlayer(playerManager.getPlayer(username));
                        worldData.savePlayerData(username, playerData);
                        worldManager.saveWorld(worldData);
                    }
                }

                // Broadcast to other clients
                networkServer.sendToAllExceptTCP(connection.getID(), update);
            }
        } catch (Exception e) {
            System.err.println("Error handling inventory update: " + e.getMessage());
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
                    worldData.savePlayerData(username, finalState);
                    worldManager.saveWorld(worldData);
                    System.out.println("Saved final state for disconnected player: " + username);
                }
            }

            // Notify other clients
            NetworkProtocol.PlayerLeft leftMessage = new NetworkProtocol.PlayerLeft();
            leftMessage.username = username;
            networkServer.sendToAllExceptTCP(connection.getID(), leftMessage);
        }
    }

    /**
     * Handles player state updates sent from clients.
     *
     * @param connection The connection from which the update was received.
     * @param update     The player state update containing position, direction, and movement status.
     */
    private void handlePlayerUpdate(Connection connection, NetworkProtocol.PlayerUpdate update) {
        String username = connectedPlayers.get(connection.getID());
        if (username != null && username.equals(update.username)) {
            ServerPlayer player = playerManager.getPlayer(username);
            if (player != null) {
                player.updatePosition(update.x, update.y, update.direction, update.isMoving);

                // Use storage system instead of direct world save
                storageSystem.savePlayerData(username, player.getData());

                // Broadcast update to other clients
                networkServer.sendToAllExceptTCP(connection.getID(), update);
            }
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

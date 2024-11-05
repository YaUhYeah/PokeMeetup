package io.github.pokemeetup.multiplayer.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Json;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import io.github.pokemeetup.chat.TeleportManager;
import io.github.pokemeetup.multiplayer.OtherPlayer;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.multiplayer.server.config.ServerConnectionConfig;
import io.github.pokemeetup.pokemon.Pokemon;
import io.github.pokemeetup.pokemon.PokemonParty;
import io.github.pokemeetup.pokemon.WildPokemon;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.data.ItemData;
import io.github.pokemeetup.system.data.PlayerData;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.inventory.Inventory;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.TextureManager;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

public class GameClient {// Add to GameClient.java
    private static final float SYNC_INTERVAL = 1 / 20f; // 60 Hz update rate
    private static final int CONNECTION_TIMEOUT = 10000; // Increase timeout to 10 seconds
    private static final int MAX_RETRIES = 5; // Increase retry attempts
    private static final int RETRY_DELAY_MS = 2000; // Increase delay between retries
    private static final float SEND_INTERVAL = 1 / 20f; // 20 Hz
    private final Object connectionInitLock = new Object();
    private final boolean isSinglePlayer;
    private final Map<String, OtherPlayer> otherPlayers = new ConcurrentHashMap<>();
    private final Object connectionLock = new Object();
    private final Queue<NetworkProtocol.ChatMessage> chatMessageQueue = new ConcurrentLinkedQueue<>();
    private final Object atlasLock = new Object();
    private final ConcurrentHashMap<String, NetworkProtocol.PlayerUpdate> playerUpdates = new ConcurrentHashMap<>();
    private final Map<UUID, NetworkSyncData> syncedPokemonData = new ConcurrentHashMap<>();
    private volatile ConnectionState connectionState = ConnectionState.DISCONNECTED;
    private Player activePlayer;
    private World currentWorld;
    private volatile boolean isInitializing = false;
    private String serverIP;
    private int tcpPort;
    private int udpPort;
    private float syncTimer = 0;
    private Map<String, PlayerStateBuffer> playerStateBuffers = new ConcurrentHashMap<>();
    private PlayerData lastKnownState;  // Add this to track the latest state
    private volatile boolean isDisposing = false;
    private Client client;
    private volatile boolean isConnecting = false;
    private long worldSeed;
    private float sendTimer = 0f;
    private Consumer<NetworkProtocol.ChatMessage> chatMessageHandler;
    private LoginResponseListener loginResponseListener;
    private RegistrationResponseListener registrationResponseListener;
    private String localUsername;
    private volatile boolean shouldReconnect = true;
    private ServerConnectionConfig serverConfig;
    private Timer reconnectionTimer;
    private String currentWorldName; // Track the current world
    private boolean isConnected = false;
    // Add to existing fields
    private Map<UUID, WildPokemon> trackedWildPokemon = new ConcurrentHashMap<>();
    private UsernameCheckListener usernameCheckListener;
    private PokemonUpdateHandler pokemonUpdateHandler;
    private WorldObjectHandler worldObjectHandler;
    private WeatherUpdateHandler weatherUpdateHandler;
    private TimeSyncHandler timeSyncHandler;

    public GameClient(ServerConnectionConfig config, boolean isSinglePlayer, String serverIP, int tcpPort, int udpPort) {
        this.isSinglePlayer = isSinglePlayer;
        this.serverIP = serverIP;
        this.tcpPort = tcpPort;
        lastKnownState = new PlayerData();
        this.udpPort = udpPort;// In GameScreen constructor
        this.serverConfig = config;      // Initialize networking only if multiplayer
        // Load server configuration from file
        if (!isSinglePlayer) {
            if (serverConfig != null) {
                setServerConfig(serverConfig);  // Set the loaded config
                initializeNetworking();  // Initialize networking only if the config is loaded
            } else {
                GameLogger.info("Failed to load server config, multiplayer disabled.");
            }

        }
    }

    public void connect() throws IOException {
        if (isSinglePlayer) return;

        synchronized (connectionLock) {
            if (isInitializing) {
                GameLogger.info("Connection already initializing, skipping");
                return;
            }

            isInitializing = true;
            connectionState = ConnectionState.CONNECTING;

            try {
                // Clean up any existing connection first
                cleanupExistingConnection();

                // Create and configure new client
                client = new Client(16384, 2048);
                NetworkProtocol.registerClasses(client.getKryo());
                setupNetworkListeners();

                // Start client
                client.start();

                // Attempt connection with retries
                boolean connected = false;
                Exception lastException = null;

                for (int attempt = 1; attempt <= MAX_RETRIES && !connected; attempt++) {
                    try {
                        GameLogger.info("Connection attempt " + attempt + " to " +
                            serverConfig.getServerIP() + ":" + serverConfig.getTcpPort());

                        client.connect(CONNECTION_TIMEOUT,
                            serverConfig.getServerIP(),
                            serverConfig.getTcpPort(),
                            serverConfig.getUdpPort());

                        connected = true;
                        isConnected = true;
                        connectionState = ConnectionState.CONNECTED;
                        GameLogger.info("Successfully connected to server");

                    } catch (IOException e) {
                        lastException = e;
                        GameLogger.error("Connection attempt " + attempt + " failed: " + e.getMessage());

                        if (attempt < MAX_RETRIES) {
                            GameLogger.info("Retrying in " + (RETRY_DELAY_MS / 1000) + " seconds...");
                            Thread.sleep(RETRY_DELAY_MS);
                        }
                    }
                }

                if (!connected) {
                    String error = "Failed to connect after " + MAX_RETRIES + " attempts";
                    if (lastException != null) {
                        error += ": " + lastException.getMessage();
                    }
                    throw new IOException(error);
                }

            } catch (Exception e) {
                connectionState = ConnectionState.DISCONNECTED;
                isConnected = false;
                cleanupExistingConnection();
                throw new IOException("Connection failed: " + e.getMessage(), e);
            } finally {
                isInitializing = false;
            }
        }
    }

    // Update GameClientSingleton to use the new connect method


    private boolean isServerReachable() {
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress(
                    serverConfig.getServerIP(),
                    serverConfig.getTcpPort()),
                2000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void initializeNetworking() {
        synchronized (connectionInitLock) {
            if (connectionState != ConnectionState.DISCONNECTED) {
                GameLogger.info("Connection already in progress, current state: " + connectionState);
                return;
            }
            isServerReachable();
            try {
                connectionState = ConnectionState.CONNECTING;
                GameLogger.info("Starting network initialization...");

                // Clean up any existing connection
                cleanupExistingConnection();

                // Create and configure new client
                client = new Client(16384, 2048);
                NetworkProtocol.registerClasses(client.getKryo());
                setupNetworkListeners();

                // Start client and attempt connection
                client.start();
                GameLogger.info("Attempting connection to " + serverConfig.getServerIP() +
                    ":" + serverConfig.getTcpPort());

                // Connect with timeout
                boolean connected = attemptConnection();
                if (!connected) {
                    throw new IOException("Connection attempt timed out");
                }

                connectionState = ConnectionState.CONNECTED;
                isConnected = true;
                isInitializing = false;

                GameLogger.info("Successfully connected to server");

            } catch (Exception e) {
                GameLogger.error("Network initialization failed: " + e.getMessage());
                connectionState = ConnectionState.DISCONNECTED;
                isInitializing = false;
                isConnected = false;
                cleanupExistingConnection();
                throw new RuntimeException("Failed to initialize networking", e);
            }
        }
    }

    private boolean attemptConnection() {
        int attempts = 0;
        while (attempts < MAX_RETRIES && !isConnected && shouldReconnect) {
            try {
                client.connect(CONNECTION_TIMEOUT,
                    serverConfig.getServerIP(),
                    serverConfig.getTcpPort(),
                    serverConfig.getUdpPort());
                return true;
            } catch (IOException e) {
                attempts++;
                if (attempts < MAX_RETRIES && shouldReconnect) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Updates the last known state of the player with current data.
     * This is used for synchronization and save operations.
     *
     * @param currentState The current player state to store
     */
    public void updateLastKnownState(PlayerData currentState) {
        if (currentState == null) {
            GameLogger.error("Attempted to update last known state with null data");
            return;
        }

        synchronized (this) {
            // Create a deep copy of the state to prevent modification
            this.lastKnownState = currentState.copy();

            GameLogger.info("Updated last known state for player: " + currentState.getUsername());
            GameLogger.info("Position: " + lastKnownState.getX() + "," + lastKnownState.getY());
            GameLogger.info("Inventory items: " +
                (lastKnownState.getInventoryItems() != null ?
                    lastKnownState.getInventoryItems().size() : "null"));
        }
    }

    /**
     * Collects and returns the latest PlayerUpdate objects received from the server.
     * This method returns a snapshot of the current player updates and clears the internal storage
     * to ensure that each update is processed only once.
     *
     * @return A map of usernames to their latest PlayerUpdate.
     */
    public void sendPrivateMessage(NetworkProtocol.ChatMessage message, String recipient) {
        message.recipient = recipient; // Add recipient field to ChatMessage if not already present
        if (isSinglePlayer()) {
            // In single player, directly trigger the chat handler
            if (chatMessageHandler != null) {
                chatMessageHandler.accept(message);
            }
        } else {
            // In multiplayer, send to server with recipient information
            sendMessage(message);
        }
    }

    private void handleReceivedObject(Object object) {
//        GameLogger.info("Received object: " + object.getClass().getName());

        try {
            if (object instanceof NetworkProtocol.UsernameCheckResponse) {
                handleUsernameCheckResponse((NetworkProtocol.UsernameCheckResponse) object);
            } else if (object instanceof NetworkProtocol.WildPokemonSpawn) {
                handlePokemonSpawn((NetworkProtocol.WildPokemonSpawn) object);
            } else if (object instanceof NetworkProtocol.WildPokemonDespawn) {
                handlePokemonDespawn((NetworkProtocol.WildPokemonDespawn) object);
            } else if (object instanceof NetworkProtocol.PokemonUpdate) {
                handlePokemonUpdate((NetworkProtocol.PokemonUpdate) object);
            } else if (object instanceof NetworkProtocol.PartyUpdate) {
                handlePartyUpdate((NetworkProtocol.PartyUpdate) object);
            } else if (object instanceof NetworkProtocol.ChatMessage) {
                // Ensure messages are handled on the main thread
                Gdx.app.postRunnable(() -> {
                    handleChatMessage((NetworkProtocol.ChatMessage) object);
                });
            } else if (object instanceof NetworkProtocol.PlayerUpdate) {
                NetworkProtocol.PlayerUpdate netUpdate = (NetworkProtocol.PlayerUpdate) object;
                GameLogger.info("Client received player update for: " + netUpdate.username + " Position: (" + netUpdate.x + ", " + netUpdate.y + ")");
                handlePlayerUpdate(netUpdate);
            } else if (object instanceof NetworkProtocol.PlayerJoined) {
                handlePlayerJoined((NetworkProtocol.PlayerJoined) object);
            } else if (object instanceof NetworkProtocol.PlayerLeft) {
                handlePlayerLeft((NetworkProtocol.PlayerLeft) object);
            } else if (object instanceof NetworkProtocol.PlayerPosition) {
                handlePlayerPositions((NetworkProtocol.PlayerPosition) object);
            } else if (object instanceof NetworkProtocol.InventoryUpdate) {
                handleInventoryUpdate((NetworkProtocol.InventoryUpdate) object);
            } else if (object instanceof NetworkProtocol.LoginResponse) {
                handleLoginResponse((NetworkProtocol.LoginResponse) object);
            } else if (object instanceof NetworkProtocol.WorldObjectUpdate) {
                if (currentWorld != null) {
                    currentWorld.getObjectManager().handleNetworkUpdate((NetworkProtocol.WorldObjectUpdate) object);
                }
            }
        } catch (Exception e) {
//        GameLogger.info("Error handling network message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public Map<String, NetworkProtocol.PlayerUpdate> getPlayerUpdates() {
        // Create a shallow copy of the current updates
        Map<String, NetworkProtocol.PlayerUpdate> updatesCopy = new HashMap<>(playerUpdates);

        // Clear the internal storage to prevent re-processing the same updates
        playerUpdates.clear();

        GameLogger.info("Collected and cleared " + updatesCopy.size() + " player updates.");

        return updatesCopy;
    }

    public void setCurrentWorld(World currentWorld) {
        this.currentWorld = currentWorld;
    }

    public void setServerConfig(ServerConnectionConfig config) {
        if (config == null) {
//            logger.error("Cannot set null server config");
            return;
        }

        try {
            config.validate();
            synchronized (connectionLock) {
                this.serverConfig = config;
//                logger.info("Server config updated: {}:{}", config.getServerIP(), config.getTcpPort());
            }
        } catch (IllegalArgumentException e) {
//            logger.error("Invalid server configuration: {}", e.getMessage());
        }
    }

    public void sendWorldObjectUpdate(NetworkProtocol.WorldObjectUpdate update) {
        if (!isConnected() || client == null) {
            GameLogger.info("Cannot send world object update - not connected to server");
            return;
        }

        try {
            client.sendTCP(update);
        } catch (Exception e) {
            GameLogger.info("Failed to send world object update: " + e.getMessage());
        }
    }// In GameClient.java

    public void sendPokemonUpdate(NetworkProtocol.PokemonUpdate update) {
        if (!isConnected() || client == null || isSinglePlayer) {
            return;
        }

        try {
            // Add timestamp if not set
            if (update.timestamp == 0) {
                update.timestamp = System.currentTimeMillis();
            }

            GameLogger.info("Sending Pokemon update for UUID: " + update.uuid +
                " position: (" + update.x + "," + update.y + ")");

            client.sendTCP(update);
        } catch (Exception e) {
            GameLogger.error("Failed to send Pokemon update: " + e.getMessage());
        }
    }

    public void sendPokemonDespawn(UUID pokemonId) {
        if (!isConnected() || client == null) return;

        try {
            NetworkProtocol.WildPokemonDespawn despawnUpdate = new NetworkProtocol.WildPokemonDespawn();
            despawnUpdate.uuid = pokemonId;
            despawnUpdate.timestamp = System.currentTimeMillis();

            client.sendTCP(despawnUpdate);
            trackedWildPokemon.remove(pokemonId);
            GameLogger.info("Sent Pokemon despawn for ID: " + pokemonId);
        } catch (Exception e) {
            GameLogger.error("Failed to send Pokemon despawn: " + e.getMessage());
        }
    }

    public void connectToServer(ServerConnectionConfig config) {
        synchronized (connectionLock) {
            GameLogger.info("Connection attempt starting to: " + config.getServerIP());
            if (isInitializing) {
                GameLogger.info("Connection already initializing, skipping");
                return;
            }
            setServerConfig(config);
            shouldReconnect = true;
            initializeNetworking();
        }
    }

    // In GameClient.java
    public void disconnect() {
        synchronized (connectionLock) {
            GameLogger.info("Disconnecting client");
            shouldReconnect = false;
            if (reconnectionTimer != null) {
                reconnectionTimer.cancel();
                reconnectionTimer = null;
            }
            cleanupExistingConnection();
            isConnected = false;
            client = null;
        }
    }

    private void cleanupClient() {
        try {
            if (client != null) {
                client.stop();
                client.dispose();
                client = null;
            }
        } catch (Exception e) {
            GameLogger.info("Error cleaning up client: " + e.getMessage());
        }
    }

    private void setupNewConnection() {
        try {
            client = new Client(16384, 2048);
            NetworkProtocol.registerClasses(client.getKryo());
            setupNetworkListeners();
            client.start();

            // Attempt connection with retries
            int attempts = 0;
            boolean connected = false;

            while (attempts < MAX_RETRIES && !connected && shouldReconnect) {
                try {

                    client.connect(5000, serverConfig.getServerIP(),
                        serverConfig.getTcpPort(), serverConfig.getUdpPort());
                    connected = true;
                    isConnected = true;
                } catch (IOException e) {
                    attempts++;
                    if (attempts < MAX_RETRIES && shouldReconnect) {
                    }
                }
            }

            if (!connected) {
//                logger.error("Failed to connect after {} attempts", MAX_RETRIES);
                throw new IOException("Failed to connect to server after " + MAX_RETRIES + " attempts");
            }

        } catch (Exception e) {
//            logger.error("Failed to setup network connection: {}", e.getMessage());
            cleanupExistingConnection();
            throw new RuntimeException("Network setup failed", e);
        }
    }

    // Add better error handling in network initialization

    private void cleanupExistingConnection() {
        if (client != null) {
            try {
                if (isConnected) {
                    client.close();
                }
                client.stop();
                client = null;
                isConnected = false;
            } catch (Exception e) {
//                logger.error("Error cleaning up existing connection: {}", e.getMessage());
            }
        }
    }

    private void handlePokemonSpawn(NetworkProtocol.WildPokemonSpawn spawnData) {
        if (spawnData == null || spawnData.uuid == null || spawnData.data == null) {
            GameLogger.error("Received invalid Pokemon spawn data");
            return;
        }

        Gdx.app.postRunnable(() -> {
            try {
                // Check if Pokemon already exists
                if (trackedWildPokemon.containsKey(spawnData.uuid)) {
                    GameLogger.error("Pokemon with UUID " + spawnData.uuid + " already exists");
                    return;
                }

                // Get sprite for Pokemon
                TextureRegion overworldSprite = TextureManager.getOverworldSprite(spawnData.data.getName());
                if (overworldSprite == null) {
                    GameLogger.error("Could not load sprite for Pokemon: " + spawnData.data.getName());
                    return;
                }

                // Create new WildPokemon
                WildPokemon pokemon = new WildPokemon(
                    spawnData.data.getName(),
                    spawnData.data.getLevel(),
                    (int) spawnData.x,
                    (int) spawnData.y,
                    overworldSprite
                );

                // Set additional data
                pokemon.setUuid(spawnData.uuid);
                pokemon.setDirection("down"); // Default direction
                pokemon.setSpawnTime(spawnData.timestamp / 1000L); // Convert to seconds

                // Add to tracking
                trackedWildPokemon.put(spawnData.uuid, pokemon);
                syncedPokemonData.put(spawnData.uuid, new NetworkSyncData());

                // Add to world
                if (currentWorld != null && currentWorld.getPokemonSpawnManager() != null) {
                    currentWorld.getPokemonSpawnManager().addPokemonToChunk(
                        pokemon,
                        new Vector2(spawnData.x, spawnData.y)
                    );
                }

                GameLogger.info("Spawned new Pokemon: " + pokemon.getName() +
                    " (Level " + pokemon.getLevel() + ") at (" +
                    spawnData.x + ", " + spawnData.y + ")");

            } catch (Exception e) {
                GameLogger.error("Error handling Pokemon spawn: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    // Handle Pokemon updates from server
    private void handlePokemonUpdate(NetworkProtocol.PokemonUpdate update) {
        if (update == null || update.uuid == null) {
            GameLogger.error("Received invalid Pokemon update");
            return;
        }

        Gdx.app.postRunnable(() -> {
            try {
                WildPokemon pokemon = trackedWildPokemon.get(update.uuid);
                NetworkSyncData syncData = syncedPokemonData.get(update.uuid);

                if (pokemon == null || syncData == null) {
                    // Pokemon doesn't exist - request spawn data from server
                    requestPokemonSpawnData(update.uuid);
                    return;
                }

                // Update sync data for smooth interpolation
                syncData.targetPosition = new Vector2(update.x, update.y);
                syncData.direction = update.direction;
                syncData.isMoving = update.isMoving;
                syncData.lastUpdateTime = System.currentTimeMillis();
                syncData.interpolationProgress = 0f;

                // Update non-position attributes immediately
                pokemon.setDirection(update.direction);
                pokemon.setMoving(update.isMoving);

                // Update stats if provided
                if (update.level > 0) {
                    pokemon.setLevel(update.level);
                }
                if (update.currentHp > 0) {
                    pokemon.setCurrentHp(update.currentHp);
                }

                // Update timestamp
                pokemon.setSpawnTime(update.timestamp / 1000L);

                // Notify handler if set
                if (pokemonUpdateHandler != null) {
                    pokemonUpdateHandler.onUpdate(update);
                }

                GameLogger.info("Updated Pokemon: " + pokemon.getName() +
                    " moving to (" + update.x + ", " + update.y + ")");

            } catch (Exception e) {
                GameLogger.error("Error handling Pokemon update: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    // Handle Pokemon despawn from server
    private void handlePokemonDespawn(NetworkProtocol.WildPokemonDespawn despawnData) {
        if (despawnData == null || despawnData.uuid == null) {
            return;
        }

        Gdx.app.postRunnable(() -> {
            try {
                WildPokemon pokemon = trackedWildPokemon.remove(despawnData.uuid);
                syncedPokemonData.remove(despawnData.uuid);

                if (pokemon != null && currentWorld != null) {
                    // Start despawn animation
                    pokemon.startDespawnAnimation();

                    com.badlogic.gdx.utils.Timer.schedule(new com.badlogic.gdx.utils.Timer.Task() {
                        @Override
                        public void run() {
                            currentWorld.getPokemonSpawnManager()
                                .removePokemon(despawnData.uuid);
                        }
                    }, 1.0f); // Animation duration
                }

                GameLogger.info("Despawned Pokemon with UUID: " + despawnData.uuid);

            } catch (Exception e) {
                GameLogger.error("Error handling Pokemon despawn: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    // Update method for interpolation

    public void sendMessage(NetworkProtocol.ChatMessage message) {
        if (!isConnected() || client == null) {
            GameLogger.info("Cannot send chat message - not connected to server");
            return;
        }

        try {
            // Send via TCP to ensure delivery
            client.sendTCP(message);
//            GameLogger.info(STR."Sent chat message: \{message.content}");
        } catch (Exception e) {
//            GameLogger.info(STR."Failed to send chat message: \{e.getMessage()}");
        }
    }

    private void handlePlayerLeft(NetworkProtocol.PlayerLeft leftMsg) {
        OtherPlayer leftPlayer = otherPlayers.remove(leftMsg.username);
        if (leftPlayer != null) {
            // Send leave notification to chat
            NetworkProtocol.ChatMessage leaveNotification = new NetworkProtocol.ChatMessage();
            leaveNotification.sender = "SYSTEM";
            leaveNotification.content = leftMsg.username + " has left the game";
            leaveNotification.type = NetworkProtocol.ChatType.SYSTEM;
            leaveNotification.timestamp = System.currentTimeMillis();

            if (chatMessageHandler != null) {
                chatMessageHandler.accept(leaveNotification);
            }

            GameLogger.info("Player left: " + leftMsg.username);
            leftPlayer.dispose();
        }
    }

    public void setChatMessageHandler(Consumer<NetworkProtocol.ChatMessage> handler) {
        this.chatMessageHandler = handler;
    }

    private void handleUsernameCheckResponse(NetworkProtocol.UsernameCheckResponse response) {
        if (usernameCheckListener != null) {
            usernameCheckListener.onResponse(response);
        }
    }

    public void setPokemonUpdateHandler(PokemonUpdateHandler handler) {
        this.pokemonUpdateHandler = handler;
    }

    public void setWorldObjectHandler(WorldObjectHandler handler) {
        this.worldObjectHandler = handler;
    }

    public void setWeatherUpdateHandler(WeatherUpdateHandler handler) {
        this.weatherUpdateHandler = handler;
    }

    public void setTimeSyncHandler(TimeSyncHandler handler) {
        this.timeSyncHandler = handler;
    }

    private void handlePartyUpdate(NetworkProtocol.PartyUpdate update) {
        if (update.username != null && !update.username.equals(localUsername)) {
            OtherPlayer otherPlayer = otherPlayers.get(update.username);
            if (otherPlayer != null) {
                otherPlayer.updateParty(update.party);
            }
        }
    }

    private void handleChatMessage(NetworkProtocol.ChatMessage message) {
        // Add debug logging
//        GameLogger.info("Received chat message from: \{message.sender content: \{message.content}");

        if (chatMessageHandler != null) {
            chatMessageHandler.accept(message);
        } else {
            GameLogger.info("Chat message handler is null!");
        }
    }

    public void savePlayerState(PlayerData playerData) {   // Skip for single player
        if (isSinglePlayer) {
            return;
        }
        if (playerData == null) {
            GameLogger.info("Cannot save null io.github.pokemeetup.system.data.PlayerData");
            return;
        }

//        GameLogger.info(STR."Attempting to save player state for: \{playerData.getUsername()}");

        try {
            if (isSinglePlayer()) {
            } else {
                if (isConnected() && client != null) {
                    // Send to server
                    sendPlayerUpdateToServer(playerData);
                    // Also keep local backup
//                    GameLogger.info(STR."Sent player state to server for: \{playerData.getUsername()}");
                } else {
                    GameLogger.info("Not connected to server, saving local backup");
                }
            }
        } catch (Exception e) {
            GameLogger.info("Error during save: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public synchronized void sendPlayerUpdateToServer(PlayerData playerData) {
        if (isSinglePlayer) {
            return;
        }
        if (playerData == null) {
            GameLogger.info("Cannot save null io.github.pokemeetup.system.data.PlayerData");
            return;
        }

        try {
            if (isConnected() && client != null) {
                // Create and send PlayerUpdate
                NetworkProtocol.PlayerUpdate update = new NetworkProtocol.PlayerUpdate();
                update.username = playerData.getUsername();
                update.x = playerData.getX();
                update.y = playerData.getY();
                update.direction = playerData.getDirection();
                update.isMoving = playerData.isMoving();
                update.wantsToRun = playerData.isWantsToRun();
                update.timestamp = System.currentTimeMillis(); // Optional: For synchronization

                // Serialize to check size
                Kryo kryo = client.getKryo(); // Use the client's Kryo instance
                Output output = new Output(16384, -1); // Start with default buffer
                kryo.writeObject(output, update);
                output.flush();
                int size = output.position(); // Get the size of serialized data
                output.close();

                GameLogger.info("Serialized PlayerUpdate size: " + size + " bytes");

                if (size > 16384) {
                    GameLogger.info("PlayerUpdate size exceeds buffer limit: " + size + " bytes");
                    return;
                }

                // Send the update
                client.sendTCP(update);
                GameLogger.info("PlayerUpdate sent successfully for: " + playerData.getUsername());

                // Also send InventoryUpdate if needed
                sendInventoryUpdate(playerData.getUsername(), playerData.getInventoryItems());

                // Keep local backup
            }


        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setupNetworkListeners() {
        if (client == null) return;

        client.addListener(new Listener() {
            @Override
            public void connected(Connection connection) {
                isConnected = true;
//                logger.info("Connected to server successfully as: {}", localUsername);
            }

            @Override
            public void disconnected(Connection connection) {
                synchronized (connectionLock) {
                    isConnected = false;
//                    logger.info("Disconnected from server");

                    if (shouldReconnect) {
                        scheduleReconnection();
                    }
                }
            }

            @Override
            public void received(Connection connection, Object object) {
                try {
                    handleReceivedObject(object);
                } catch (Exception e) {
//                    GameLogger.info(STR."Error processing received object: \{e.getMessage()}");
                    e.printStackTrace();
                }
            }

        });
    }

    private void scheduleReconnection() {
        if (reconnectionTimer != null) {
            reconnectionTimer.cancel();
        }

        reconnectionTimer = new Timer();
        reconnectionTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                synchronized (connectionLock) {
                    if (!isConnected && shouldReconnect && !isInitializing) {
//                        logger.info("Attempting to reconnect...");
                        initializeNetworking();
                    }
                }
            }
        }, RETRY_DELAY_MS, RETRY_DELAY_MS * 5);
    }

    public void sendLoginRequest(String username, String password) {
        if (!isConnected()) {
            GameLogger.info("Not connected to server. Cannot send login request.");
            return;
        }

        // Create and send login request using NetworkProtocol
        NetworkProtocol.LoginRequest request = new NetworkProtocol.LoginRequest();
        request.username = username;
        request.password = password;

        client.sendTCP(request);  // Send the request to the server
    }

    // In GameClient.java
    public void sendRegisterRequest(String username, String password) {
        if (isSinglePlayer) return;
        if (!isConnected() || client == null) {
            GameLogger.error("Cannot send register request - not connected to server");
            return;
        }

        try {
            // Save username for later use
            this.localUsername = username;

            NetworkProtocol.RegisterRequest request = new NetworkProtocol.RegisterRequest();
            request.username = username;
            request.password = password;

            GameLogger.info("Sending registration request for: " + username);
            client.sendTCP(request);
        } catch (Exception e) {
            GameLogger.error("Failed to send registration request: " + e.getMessage());
            throw new RuntimeException("Failed to send registration request", e);
        }
    }

    public void checkUsernameAvailability(String username) {
        if (!isConnected() || client == null) {
            GameLogger.error("Cannot check username - not connected to server");
            return;
        }

        try {
            NetworkProtocol.UsernameCheckRequest request = new NetworkProtocol.UsernameCheckRequest();
            request.username = username;
            request.timestamp = System.currentTimeMillis();

            GameLogger.info("Sending username check request for: " + username);
            client.sendTCP(request);
        } catch (Exception e) {
            GameLogger.error("Failed to send username check request: " + e.getMessage());
            throw new RuntimeException("Failed to send username check request", e);
        }
    }

    public void setUsernameCheckListener(UsernameCheckListener listener) {
        this.usernameCheckListener = listener;
    }

    private void handleRegisterResponse(NetworkProtocol.RegisterResponse response) {
        GameLogger.info("Received registration response: success=" + response.success +
            ", message=" + response.message);

        if (registrationResponseListener != null) {
            registrationResponseListener.onResponse(response);
        } else {
            GameLogger.error("No registration response listener set");
        }
    }

    public synchronized void sendPlayerUpdate() {
        if (isSinglePlayer) {
            return;
        }
        if (localUsername == null || localUsername.isEmpty()) {
            GameLogger.info("Cannot send update: Username is null or empty");
            return;
        }
        if (activePlayer == null) {
            GameLogger.info("Cannot send update: Active player is null");
            return;
        }

        NetworkProtocol.PlayerUpdate update = new NetworkProtocol.PlayerUpdate();
        update.username = localUsername;
        update.x = activePlayer.getTileX();
        update.y = activePlayer.getTileY();
        update.direction = activePlayer.getDirection();
        update.isMoving = activePlayer.isMoving();
        update.wantsToRun = activePlayer.isRunning();
        update.timestamp = System.currentTimeMillis();

        // Serialize to check size using client's Kryo instance
        Kryo kryo = client.getKryo(); // Use the client's Kryo instance
        Output output = new Output(16384, -1); // Start with default buffer
        try {
            kryo.writeObject(output, update);
            output.flush();
            int size = output.position(); // Get the size of serialized data
            GameLogger.info("Serialized PlayerUpdate size: " + size + " bytes");
            if (size > 16384) {
                GameLogger.info("PlayerUpdate size exceeds buffer limit: " + size + " bytes");
                return;
            }
        } catch (Exception e) {
            GameLogger.info("Serialization failed for PlayerUpdate: " + e.getMessage());
            e.printStackTrace();
            return;
        } finally {
            output.close();
        }

        try {
            client.sendTCP(update);
            GameLogger.info("PlayerUpdate sent successfully for: " + localUsername);
        } catch (Exception e) {
            GameLogger.info("Failed to send PlayerUpdate: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public boolean isSinglePlayer() {
        return isSinglePlayer;
    }

    private void handleLoginResponse(NetworkProtocol.LoginResponse response) {
        if (response.success) {
            localUsername = response.username;
            worldSeed = response.worldSeed;

            // Load saved state if exists
            FileHandle saveFile = Gdx.files.local("assets/save/multiplayer_" + localUsername + ".json");
            if (saveFile.exists()) {
                try {
                    Json json = new Json();
                    PlayerData savedState = json.fromJson(PlayerData.class, saveFile.readString());
                    if (savedState != null) {
                        lastKnownState = savedState;
                    }
                } catch (Exception e) {
                    GameLogger.info("Failed to load saved state: " + e.getMessage());
                }
            }
        }

        if (loginResponseListener != null) {
            loginResponseListener.onResponse(response);
        }
    }

    public long getWorldSeed() {
        return worldSeed;
    }

    private void handlePlayerPositions(NetworkProtocol.PlayerPosition playerPosition) {
        try {
            for (Map.Entry<String, NetworkProtocol.PlayerUpdate> entry : playerPosition.players.entrySet()) {
                String username = entry.getKey();
                NetworkProtocol.PlayerUpdate netUpdate = entry.getValue();
                OtherPlayer otherPlayer = otherPlayers.get(netUpdate.username);

                // Skip own player update
                if (username != null && !username.equals(localUsername)) {
                    // Validate positions
                    netUpdate.x = Math.min(Math.max(netUpdate.x, 0),
                        World.WORLD_SIZE * World.TILE_SIZE);
                    netUpdate.y = Math.min(Math.max(netUpdate.y, 0),
                        World.WORLD_SIZE * World.TILE_SIZE);

                    otherPlayers.put(username, otherPlayer);
                }
            }

            // Remove disconnected players
            otherPlayers.keySet().retainAll(playerPosition.players.keySet());

        } catch (Exception e) {
            GameLogger.info("Error handling player positions: " + e.getMessage());
        }
    }

    private void handlePlayerJoined(NetworkProtocol.PlayerJoined joinMsg) {
        if (joinMsg.username.equals(localUsername)) return;

        // Create new other player
        OtherPlayer newPlayer = new OtherPlayer(
            joinMsg.username,
            joinMsg.x,
            joinMsg.y,
            TextureManager.boy
        );

        // Set initial state
        NetworkProtocol.PlayerUpdate initialState = new NetworkProtocol.PlayerUpdate();
        initialState.username = joinMsg.username;
        initialState.x = joinMsg.x;
        initialState.y = joinMsg.y;
        initialState.direction = joinMsg.direction;
        initialState.isMoving = joinMsg.isMoving;
        initialState.inventoryItems = joinMsg.inventoryItems;

        newPlayer.updateFromNetwork(initialState);
        otherPlayers.put(joinMsg.username, newPlayer);

        // Send join notification to chat
        NetworkProtocol.ChatMessage joinNotification = new NetworkProtocol.ChatMessage();
        joinNotification.sender = "SYSTEM";
        joinNotification.content = joinMsg.username + " has joined the game";
        joinNotification.type = NetworkProtocol.ChatType.SYSTEM;
        joinNotification.timestamp = System.currentTimeMillis();

        if (chatMessageHandler != null) {
            chatMessageHandler.accept(joinNotification);
        }

        GameLogger.info("New player joined: " + joinMsg.username +
            " at (" + joinMsg.x + "," + joinMsg.y + ")");
    }

    // Update the handlePlayerUpdate method to use correct parameter
    private void handlePlayerUpdate(NetworkProtocol.PlayerUpdate netUpdate) {
        if (netUpdate.username == null) {
            return;
        }

        if (netUpdate.username.equals(localUsername)) {
            // Ignore own updates sent back by the server
            return;
        }

        OtherPlayer otherPlayer = otherPlayers.get(netUpdate.username);
        if (otherPlayer == null) {
            // Create a new OtherPlayer if one doesn't exist for this username
            otherPlayer = new OtherPlayer(netUpdate.username, netUpdate.x, netUpdate.y, TextureManager.boy);
            otherPlayers.put(netUpdate.username, otherPlayer);
            GameLogger.info("Created OtherPlayer for " + netUpdate.username);
        }

        // Update the OtherPlayer with the data from the server
        otherPlayer.updateFromNetwork(netUpdate);
        GameLogger.info("Updated OtherPlayer " + netUpdate.username + " with position: (" + netUpdate.x + ", " + netUpdate.y + ")");
    }

    // Modify sendInventoryUpdate method
    public void sendInventoryUpdate(String username, List<ItemData> inventoryItems) {
        if (!isConnected() || client == null) return;

        try {
            NetworkProtocol.InventoryUpdate update = new NetworkProtocol.InventoryUpdate();
            update.username = username;
            update.inventoryItems = inventoryItems.toArray(new ItemData[0]);

            client.sendTCP(update);
            GameLogger.info("Sent inventory update for: " + username);
        } catch (Exception e) {
            GameLogger.error("Failed to send inventory update: " + e.getMessage());
        }
    }

    // Update handleInventoryUpdate method
    private void handleInventoryUpdate(NetworkProtocol.InventoryUpdate update) {
        if (update.username == null || update.username.equals(localUsername)) return;

        OtherPlayer otherPlayer = otherPlayers.get(update.username);
        if (otherPlayer == null) {
            otherPlayer = new OtherPlayer(update.username, 0, 0, TextureManager.boy);
            otherPlayers.put(update.username, otherPlayer);
        }

        // Update inventory
        Inventory otherInventory = otherPlayer.getInventory();
        otherInventory.setAllItems(Arrays.asList(update.inventoryItems));

        GameLogger.info("Updated inventory for player: " + update.username);
    }

    /**
     * Retrieves a map of all other players.
     *
     * @return Map of username to OtherPlayer instances.
     */
// In the GameClient class
    public Map<String, OtherPlayer> getOtherPlayers() {
        // Debug log to ensure this is working
        GameLogger.info("Returning " + otherPlayers.size() + " players from GameClient");
        return otherPlayers; // Ensure this map is populated correctly
    }

    public void setLoginResponseListener(LoginResponseListener listener) {
        this.loginResponseListener = listener;
    }

    public void setRegistrationResponseListener(RegistrationResponseListener listener) {
        this.registrationResponseListener = listener;
    }

    public String getLocalUsername() {
        return localUsername;
    }

    public void setLocalUsername(String username) {
        this.localUsername = username;
        GameLogger.info("Local username set to: " + username);
    }

    private void requestPokemonSpawnData(UUID pokemonId) {
        if (!isSinglePlayer() && isConnected()) {
            NetworkProtocol.PokemonSpawnRequest request = new NetworkProtocol.PokemonSpawnRequest();
            request.uuid = pokemonId;
            client.sendTCP(request);
        }
    }

    public boolean isConnected() {
        return isConnected && client != null;
    }

    public void dispose() {


        synchronized (connectionLock) {
            shouldReconnect = false;
            if (reconnectionTimer != null) {
                reconnectionTimer.cancel();
                reconnectionTimer = null;
            }
            cleanupExistingConnection();
        }
    }

    public void update(float delta) {
        syncTimer += delta;// Update Pokemon positions with interpolation
        for (Map.Entry<UUID, WildPokemon> entry : trackedWildPokemon.entrySet()) {
            UUID pokemonId = entry.getKey();
            WildPokemon pokemon = entry.getValue();
            NetworkSyncData syncData = syncedPokemonData.get(pokemonId);

            if (syncData != null && syncData.targetPosition != null) {
                // Calculate interpolation progress
                syncData.interpolationProgress = Math.min(1.0f,
                    syncData.interpolationProgress + delta * 5f); // Adjust speed as needed

                // Interpolate position
                float newX = MathUtils.lerp(pokemon.getX(), syncData.targetPosition.x,
                    syncData.interpolationProgress);
                float newY = MathUtils.lerp(pokemon.getY(), syncData.targetPosition.y,
                    syncData.interpolationProgress);

                // Update position
                pokemon.setX(newX);
                pokemon.setY(newY);
                pokemon.updateBoundingBox();
            }

            // Update animation state
            pokemon.update(delta);
        }
        if (syncTimer >= SYNC_INTERVAL) {
            syncTimer = 0;
            if (!isSinglePlayer) {
                if (activePlayer != null) {
                    PlayerData currentPlayerData = new PlayerData(localUsername);
                    currentPlayerData.updateFromPlayer(activePlayer);
                }
                // Removed the redundant sendPlayerUpdate call
            }
        }

        if (sendTimer >= SEND_INTERVAL) {
            sendTimer = 0;
            if (!isSinglePlayer && activePlayer != null) {
                for (OtherPlayer otherPlayer : otherPlayers.values()) {
                    otherPlayer.update(delta); // Ensure OtherPlayer objects are updated
                }
                sendPlayerUpdate(); // Optionally, if you want to send at a different interval
            }
        }
        updateLastKnownState(lastKnownState);


        // Update interpolation for other players
        long currentTime = System.currentTimeMillis();
        for (Map.Entry<String, PlayerStateBuffer> entry : playerStateBuffers.entrySet()) {
            NetworkProtocol.PlayerUpdate interpolatedState = entry.getValue().interpolateState(currentTime);
            if (interpolatedState != null) {
                OtherPlayer otherPlayer = otherPlayers.get(entry.getKey());
                if (otherPlayer != null) {
                    otherPlayer.updateFromNetwork(interpolatedState);
                }
            }
        }
    }

    // [Existing methods...]

    private enum ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED
    }

    public interface PokemonUpdateHandler {
        void onUpdate(NetworkProtocol.PokemonUpdate update);
    }

    public interface WorldObjectHandler {
        void onUpdate(NetworkProtocol.WorldObjectUpdate update);
    }

    public interface WeatherUpdateHandler {
        void onUpdate(NetworkProtocol.WeatherUpdate update);
    }

    public interface TimeSyncHandler {
        void onUpdate(NetworkProtocol.TimeSync update);
    }

    public interface UsernameCheckListener {
        void onResponse(NetworkProtocol.UsernameCheckResponse response);
    }

    public interface LoginResponseListener {
        void onResponse(NetworkProtocol.LoginResponse response);
    }


    public interface RegistrationResponseListener {
        void onResponse(NetworkProtocol.RegisterResponse response);
    }

    private static class NetworkSyncData {
        Vector2 targetPosition;
        String direction;
        boolean isMoving;
        long lastUpdateTime;
        float interpolationProgress;

        NetworkSyncData() {
            this.lastUpdateTime = System.currentTimeMillis();
            this.interpolationProgress = 0f;
        }
    }

    private static class PlayerStateBuffer {
        private static final int BUFFER_SIZE = 10;
        private Queue<NetworkProtocol.PlayerUpdate> states = new LinkedList<>();

        public void addState(NetworkProtocol.PlayerUpdate update) {
            states.offer(update);
            while (states.size() > BUFFER_SIZE) {
                states.poll();
            }
        }

        public NetworkProtocol.PlayerUpdate interpolateState(long currentTime) {
            if (states.isEmpty()) return null;

            NetworkProtocol.PlayerUpdate oldest = states.peek();
            NetworkProtocol.PlayerUpdate newest = null;

            for (NetworkProtocol.PlayerUpdate state : states) {
                if (state.timestamp <= currentTime && (newest == null || state.timestamp > newest.timestamp)) {
                    newest = state;
                }
            }

            if (newest == null) return oldest;

            // Interpolate between states
            float alpha = Math.min(1.0f, (currentTime - oldest.timestamp) / (float) (newest.timestamp - oldest.timestamp));
            NetworkProtocol.PlayerUpdate interpolated = new NetworkProtocol.PlayerUpdate();
            interpolated.username = newest.username;
            interpolated.x = lerp(oldest.x, newest.x, alpha);
            interpolated.y = lerp(oldest.y, newest.y, alpha);
            interpolated.direction = newest.direction;
            interpolated.isMoving = newest.isMoving;
            interpolated.wantsToRun = newest.wantsToRun;
            interpolated.inventoryItems = newest.inventoryItems;

            return interpolated;
        }

        private float lerp(float a, float b, float t) {
            return a + (b - a) * t;
        }
    }// Update GameClient's update method
}

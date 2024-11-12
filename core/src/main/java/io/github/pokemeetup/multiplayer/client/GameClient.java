package io.github.pokemeetup.multiplayer.client;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.FrameworkMessage;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.minlog.Log;
import io.github.pokemeetup.managers.BiomeManager;
import io.github.pokemeetup.multiplayer.OtherPlayer;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.multiplayer.server.GameStateHandler;
import io.github.pokemeetup.multiplayer.server.config.ServerConnectionConfig;
import io.github.pokemeetup.multiplayer.server.storage.FileStorage;
import io.github.pokemeetup.pokemon.WildPokemon;
import io.github.pokemeetup.server.deployment.ConnectionManager;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.data.ItemData;
import io.github.pokemeetup.system.data.PlayerData;
import io.github.pokemeetup.system.data.WorldData;
import io.github.pokemeetup.system.gameplay.overworld.Chunk;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.WorldObject;
import io.github.pokemeetup.system.gameplay.overworld.multiworld.WorldManager;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.TextureManager;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class GameClient {
    private static final int BUFFER_SIZE = 16384;
    private static final int OBJECT_BUFFER = 4096;
    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    private static final long RECONNECT_DELAY = 5000;
    private static final float SYNC_INTERVAL = 1 / 60f; // 60Hz sync rate
    private static final float INTERPOLATION_SPEED = 10f;
    private static final float UPDATE_INTERVAL = 1 / 20f;
    private static final int MAX_RETRIES = 5;
    private static final long RETRY_DELAY_MS = 2000;
    private static final float TICK_RATE = 1 / 60f;
    private static final int AUTH_TIMEOUT = 10000;
    private static final long KEEPALIVE_INTERVAL = 1000; // Every 1 second
    private static final long KEEPALIVE_TIMEOUT = 5000;  // 5 seconds threshold
    private static final int CONNECTION_TIMEOUT = 10000;
    private final AtomicBoolean isAuthenticated = new AtomicBoolean(false);
    private final AtomicBoolean shouldReconnect = new AtomicBoolean(true);
    private final AtomicBoolean isDisposing = new AtomicBoolean(false);
    private final ReentrantLock connectionLock = new ReentrantLock();
    private final ConcurrentHashMap<String, OtherPlayer> otherPlayers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, WildPokemon> trackedWildPokemon = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, NetworkSyncData> syncedPokemonData = new ConcurrentHashMap<>();
    private final BlockingQueue<NetworkProtocol.ChatMessage> chatMessageQueue = new LinkedBlockingQueue<>();
    private final Map<String, PlayerStateBuffer> playerStateBuffers = new ConcurrentHashMap<>();
    private final boolean isSinglePlayer;
    private final BiomeManager biomeManager;
    private final ConcurrentHashMap<String, NetworkProtocol.PlayerUpdate> playerUpdates = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;
    private final WorldManager worldManager;
    private final FileStorage fileStorage;
    private final Queue<Object> pendingMessages = new ConcurrentLinkedQueue<>();
    private final Preferences credentials;
    private final ScheduledExecutorService authTimer = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    private final Queue<Object> sendQueue = new ConcurrentLinkedQueue<>();
    private final GameStateHandler gameHandler;
    private volatile long lastKeepaliveReceived = System.currentTimeMillis();
    private ScheduledFuture<?> keepaliveTask;
    private volatile ConnectionState connectionState = ConnectionState.DISCONNECTED;
    private volatile boolean isInitializing = false;
    private volatile Client client;
    private ServerConnectionConfig serverConfig;
    private int reconnectAttempts = 0;
    private String localUsername;
    private Player activePlayer;
    private World currentWorld;
    private long worldSeed;
    private float syncTimer = 0;
    private PlayerData lastKnownState;
    // Callback Handlers
    private Consumer<NetworkProtocol.ChatMessage> chatMessageHandler;
    private LoginResponseListener loginResponseListener;
    private RegistrationResponseListener registrationResponseListener;
    private UsernameCheckListener usernameCheckListener;
    private PokemonUpdateHandler pokemonUpdateHandler;
    private WorldData worldData;
    private float updateAccumulator = 0;
    private float tickAccumulator = 0;
    private volatile boolean isInitialized = false;
    private volatile boolean fullyInitialized = false;
    private InitializationListener initializationListener;
    private volatile boolean isAuthenticating = false;
    private String pendingUsername;
    private String pendingPassword;
    private String currentPassword;
    private volatile long lastKeepAliveReceived;
    private ConnectionManager connectionManager;
    private volatile boolean loginRequestSent = false;
    private String pendingRegistrationUsername;
    private String pendingRegistrationPassword;

    public static final long INIT_TIMEOUT = 30000; // 30 second timeout
    private ScheduledFuture<?> initTimeoutTask;

    public GameClient(ServerConnectionConfig config, boolean isSinglePlayer, String serverIP, int tcpPort, int udpPort, GameStateHandler gameHandler) {
        this.gameHandler = gameHandler;

        Log.set(Log.LEVEL_DEBUG);
        connectionManager = new ConnectionManager(this);
        this.serverConfig = config;
        this.isSinglePlayer = isSinglePlayer;
        this.lastKnownState = new PlayerData();
        this.biomeManager = new BiomeManager(System.currentTimeMillis()); // or pass specific seed
        this.worldManager = WorldManager.getInstance(null, !isSinglePlayer);
        this.fileStorage = isSinglePlayer ? null : new FileStorage(config.getDataDirectory());

        this.credentials = Gdx.app.getPreferences("game-credentials");
        this.serverConfig = config;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.lastKnownState = new PlayerData();
        if (!isSinglePlayer) {
            setupReconnectionHandler();
            loadSavedCredentials();
            if (serverConfig != null) {
                setServerConfig(serverConfig);
            } else {
                GameLogger.info("Failed to load server config, multiplayer disabled.");
            }

        }
    }

    private void setupTimeouts() {
        // Auth timeout
        scheduler.schedule(() -> {
            if (!isAuthenticated.get() && connectionState != ConnectionState.DISCONNECTED) {
                GameLogger.error("Authentication timed out");
                handleAuthFailure();
            }
        }, AUTH_TIMEOUT, TimeUnit.MILLISECONDS);

        // Overall initialization timeout
        scheduler.schedule(() -> {
            if (!fullyInitialized && !isDisposing.get()) {
                GameLogger.error("Initialization timed out");
                cleanupFailedInit();
                handleConnectionFailure(new Exception("Initialization timeout"));
            }
        }, INIT_TIMEOUT, TimeUnit.MILLISECONDS);
    }

    public Client getClient() {
        return client;
    }

    public void setInitializationListener(InitializationListener listener) {
        this.initializationListener = listener;
        // If we're already initialized, notify immediately
        if (fullyInitialized) {
            notifyInitializationComplete(true);
        }
    }

    private void loadSavedCredentials() {
        try {
            this.localUsername = credentials.getString("username", null);
            this.currentPassword = credentials.getString("password", null); // Load password or token
            this.pendingUsername = localUsername;
            this.pendingPassword = currentPassword;
            GameLogger.info("Loaded saved credentials for: " + localUsername);
        } catch (Exception e) {
            GameLogger.error("Failed to load credentials: " + e.getMessage());
        }
    }

    public void sendLoginRequest(String username, String password) throws IOException {
        if (isAuthenticated.get()) {
            GameLogger.info("Already authenticated");
            return;
        }
        if (connectionState != ConnectionState.CONNECTED) {
            GameLogger.error("Cannot send LoginRequest - not connected");
            return;
        }

        if (!isConnected()) {
            GameLogger.error("Not connected to server");
            throw new IOException("Not connected to server");
        }

        try {
            NetworkProtocol.LoginRequest request = new NetworkProtocol.LoginRequest();
            request.username = username;
            request.password = password;
            request.timestamp = System.currentTimeMillis();

            client.sendTCP(request);
            GameLogger.info("Sent login request for: " + username);

            // Set authentication timeout
            authTimer.schedule(() -> {
                if (!isAuthenticated.get()) {
                    GameLogger.error("Authentication timeout");
                    handleConnectionFailure(new Exception("Authentication timeout"));
                }
            }, AUTH_TIMEOUT, TimeUnit.MILLISECONDS);

        } catch (Exception e) {
            GameLogger.error("Failed to send login request: " + e.getMessage());
            throw new IOException("Failed to send login request", e);
        }
    }

    private void notifyInitializationComplete(boolean success) {
        GameLogger.info("Notifying initialization complete: " + success);
        if (initializationListener != null) {
            Gdx.app.postRunnable(() -> {
                try {
                    initializationListener.onInitializationComplete(success);
                } catch (Exception e) {
                    GameLogger.error("Error notifying initialization listener: " + e.getMessage());
                }
            });
        }
    }

    private int authAttempts = 0;
    private static final int MAX_AUTH_ATTEMPTS = 3;
    private static final long AUTH_RETRY_DELAY = 2000; // 2 seconds

    public void connect() {
        synchronized (connectionLock) {
            if (connectionState != ConnectionState.DISCONNECTED) {
                GameLogger.info("Already " + connectionState.toString().toLowerCase());
                return;
            }

            try {
                // Reset state
                hasReceivedInitialJoin.set(false);

                // Clean up existing connection
                cleanupExistingConnection();

                connectionState = ConnectionState.CONNECTING;
                GameLogger.info("Starting connection attempt...");

                client = new Client(BUFFER_SIZE, OBJECT_BUFFER);
                NetworkProtocol.registerClasses(client.getKryo());

                // Add connection listener
                client.addListener(new Listener() {
                    @Override
                    public void connected(Connection connection) {
                        GameLogger.info("Raw TCP/UDP connection established");
                        // Don't set connection state here - wait for ConnectionResponse
                    }

                    @Override
                    public void received(Connection connection, Object object) {
                        handleNetworkMessage(object);
                    }

                    @Override
                    public void disconnected(Connection connection) {
                        handleDisconnect();
                    }
                });

                client.start();

                client.connect(CONNECTION_TIMEOUT,
                    serverConfig.getServerIP(),
                    serverConfig.getTcpPort(),
                    serverConfig.getUdpPort());

                GameLogger.info("Initial TCP/UDP connection established, waiting for server response");

            } catch (Exception e) {
                GameLogger.error("Connection failed: " + e.getMessage());
                handleConnectionFailure(e);
            } finally {
                isConnecting.set(false);
            }
        }
    }

    private void handleNetworkMessage(Object object) {
        try {
            if (object instanceof NetworkProtocol.ForceDisconnect) {
                handleForceDisconnect((NetworkProtocol.ForceDisconnect) object);
                return;
            }
            if (object instanceof NetworkProtocol.Keepalive) {
                lastKeepaliveReceived = System.currentTimeMillis();
                return;
            }
            if (object instanceof FrameworkMessage.KeepAlive) {
                lastKeepAliveReceived = System.currentTimeMillis();
                // Optionally log receipt
                return;
            }
            if (object instanceof NetworkProtocol.ConnectionResponse) {
                handleConnectionResponse((NetworkProtocol.ConnectionResponse) object);
                return;
            }

            if (object instanceof NetworkProtocol.LoginResponse) {
                handleLoginResponse((NetworkProtocol.LoginResponse) object);
                return;
            }

            if (!isConnected()) {
                GameLogger.info("Not connected to server");
                return;
            }

            // Handle normal messages
            handleReceivedMessage(object);
        } catch (Exception e) {
            GameLogger.error("Error handling message: " + e.getMessage());
        }
    }

    private void queueUpdate(Object update) {
        sendQueue.offer(update);
    }

    public void sendPlayerUpdate() {
        if (!isConnected() || !isAuthenticated() || activePlayer == null) return;

        float playerX = activePlayer.getX();
        float playerY = activePlayer.getY();

        GameLogger.info("sendPlayerUpdate() - activePlayer position: x=" + playerX + ", y=" + playerY);

        NetworkProtocol.PlayerUpdate update = new NetworkProtocol.PlayerUpdate();
        update.username = getLocalUsername();
        update.x = playerX;
        update.y = playerY;
        update.direction = activePlayer.getDirection();
        update.isMoving = activePlayer.isMoving();
        update.timestamp = System.currentTimeMillis();

        GameLogger.info("Sending PlayerUpdate: username=" + update.username + ", x=" + update.x + ", y=" + update.y);
        client.sendTCP(update);
    }

    private void synchronizeWorldState() {
        if (!isConnected() || !isAuthenticated.get() || isSinglePlayer) {
            return;
        }

        try {
            // Send world state request
            NetworkProtocol.WorldStateUpdate update = new NetworkProtocol.WorldStateUpdate();
            update.worldData = currentWorld.getWorldData();
            update.timestamp = System.currentTimeMillis();
            client.sendTCP(update);
        } catch (Exception e) {
            GameLogger.error("Failed to synchronize world state: " + e.getMessage());
        }
    }

    public void saveWorldState() {
        if (isSinglePlayer) {
            if (currentWorld != null) {
                worldManager.saveWorld(currentWorld.getWorldData());
            }
        } else if (isAuthenticated.get() && fileStorage != null) {
            try {
                // Save only to server storage
                fileStorage.saveWorldData(currentWorld.getWorldData().getName(), currentWorld.getWorldData());
            } catch (IOException e) {
                GameLogger.error("Failed to save world data to server: " + e.getMessage());
            }
        }
    }

    public void savePlayerState(PlayerData playerData) {
        if (isSinglePlayer) {
            if (currentWorld != null) {
                // Don't clear inventory here, just save the data
                currentWorld.getWorldData().savePlayerData(playerData.getUsername(), playerData);
                worldManager.saveWorld(currentWorld.getWorldData());
                GameLogger.info("Saved singleplayer state for: " + playerData.getUsername() +
                    " with " + playerData.getInventoryItems().size() + " items");
            }
            return;
        } else if (isAuthenticated.get()) {
            try {
                // Save to server storage
                if (fileStorage != null) {
                    fileStorage.savePlayerData(playerData.getUsername(), playerData);
                }
                // Send update to server
                sendPlayerUpdateToServer(playerData);
            } catch (IOException e) {
                GameLogger.error("Failed to save player data to server: " + e.getMessage());
            }
        }
    }

    public void sendPlayerUpdateToServer(PlayerData playerData) {
        if (isSinglePlayer || !isConnected() || !isAuthenticated()) {
            return;
        }

        try {
            NetworkProtocol.PlayerUpdate update = new NetworkProtocol.PlayerUpdate();
            update.username = playerData.getUsername();
            update.x = playerData.getX();
            update.y = playerData.getY();
            update.direction = playerData.getDirection();
            update.isMoving = playerData.isMoving();
            update.wantsToRun = playerData.isWantsToRun();
            update.timestamp = System.currentTimeMillis();

            // Convert inventory items if present
            if (playerData.getInventoryItems() != null) {
                update.inventoryItems = playerData.getInventoryItems().toArray(new ItemData[0]);
            }

            client.sendTCP(update);
            GameLogger.info("Sent player update to server for: " + playerData.getUsername() +
                " at (" + update.x + "," + update.y + ")");

        } catch (Exception e) {
            GameLogger.error("Failed to send player update: " + e.getMessage());
            handleConnectionFailure(e);
        }
    }

    public Map<String, NetworkProtocol.PlayerUpdate> getPlayerUpdates() {
        Map<String, NetworkProtocol.PlayerUpdate> updates = new HashMap<>(playerUpdates);
        playerUpdates.clear();
        return updates;
    }

    public void sendPrivateMessage(NetworkProtocol.ChatMessage message, String recipient) {
        if (isSinglePlayer) {
            // Handle single player chat locally
            if (chatMessageHandler != null) {
                chatMessageHandler.accept(message);
            }
            return;
        }

        // Add recipient information for multiplayer
        message.recipient = recipient;
        message.timestamp = System.currentTimeMillis();

        try {
            if (isConnected() && isAuthenticated()) {
                client.sendTCP(message);
                GameLogger.info("Sent private message to " + recipient);
            } else {
                GameLogger.error("Cannot send message - not connected or authenticated");
            }
        } catch (Exception e) {
            GameLogger.error("Failed to send private message: " + e.getMessage());
            handleConnectionFailure(e);
        }
    }

    public void update(float deltaTime) {
        if (!isAuthenticated.get() || connectionState != ConnectionState.CONNECTED) {
            return;
        }

        syncTimer += deltaTime;
        if (syncTimer >= SYNC_INTERVAL) {
            syncTimer = 0;
            processChatMessages();
            updatePokemonStates(deltaTime);
        }
        updateOtherPlayers(deltaTime);
        updateAccumulator += deltaTime;
        if (updateAccumulator >= UPDATE_INTERVAL) {
            updateAccumulator = 0;

            if (!isSinglePlayer && activePlayer != null && isAuthenticated()) {
//                sendPlayerUpdate();
                if (currentWorld != null) {
                    synchronizeWorldState();
                }
            }
        }

        // Update other players with interpolation
    }

    public void saveState(PlayerData playerData) {
        if (isSinglePlayer) {
            if (currentWorld != null) {
                worldManager.saveWorld(currentWorld.getWorldData());
            }
        } else if (isAuthenticated.get()) {
            // For multiplayer, only send state to server
            sendPlayerUpdateToServer(playerData);
        }
    }

    private void handleWorldStateUpdate(NetworkProtocol.WorldStateUpdate update) {
        if (update == null || update.worldData == null) return;

        Gdx.app.postRunnable(() -> {
            try {
                // Update time
                if (currentWorld != null) {
                    currentWorld.getWorldData().setWorldTimeInMinutes(update.worldData.getWorldTimeInMinutes());
                    currentWorld.getWorldData().setDayLength(update.worldData.getDayLength());
                    currentWorld.getWorldData().setPlayedTime(update.worldData.getPlayedTime());
                }

                // Update other world state
                if (update.worldData.getPlayers() != null) {
                    for (Map.Entry<String, PlayerData> entry : update.worldData.getPlayers().entrySet()) {
                        if (!entry.getKey().equals(localUsername)) {
                            updateOtherPlayerData(entry.getKey(), entry.getValue());
                        }
                    }
                }

            } catch (Exception e) {
                GameLogger.error("Error handling world state update: " + e.getMessage());
            }
        });
    }

    private void updateOtherPlayerData(String username, PlayerData data) {
        OtherPlayer otherPlayer = otherPlayers.get(username);
        if (otherPlayer == null) {
            otherPlayer = new OtherPlayer(
                username,
                data.getX(),
                data.getY()
            );
            otherPlayers.put(username, otherPlayer);
        }

        // Update position and state
        NetworkProtocol.PlayerUpdate update = new NetworkProtocol.PlayerUpdate();
        update.username = username;
        update.x = data.getX();
        update.y = data.getY();
        update.direction = data.getDirection();
        update.isMoving = data.isMoving();
        update.wantsToRun = data.isWantsToRun();
        update.timestamp = System.currentTimeMillis();

        otherPlayer.updateFromNetwork(update);
    }

    private void setupReconnectionHandler() {
        scheduler.scheduleWithFixedDelay(() -> {
            if (connectionState == ConnectionState.DISCONNECTED && shouldReconnect.get() && !isDisposing.get()) {
                attemptReconnection();
            }
        }, RECONNECT_DELAY, RECONNECT_DELAY, TimeUnit.MILLISECONDS);
    }


    public void setServerConfig(ServerConnectionConfig serverConfig) {
        this.serverConfig = serverConfig;
    }

    public boolean isSinglePlayer() {
        return isSinglePlayer;
    }

    public World getCurrentWorld() {
        return currentWorld;
    }

    public void setCurrentWorld(World world) {
        if (world == null) {
            GameLogger.error("Cannot set null world");
            return;
        }
        this.currentWorld = world;
        this.worldData = world.getWorldData();
        GameLogger.info("Set current world in GameClient");

        if (activePlayer != null) {
            currentWorld.setPlayer(activePlayer);
            GameLogger.info("Set active player in new world: " + activePlayer.getUsername());
        }
    }

    public long getWorldSeed() {
        return worldSeed;
    }

    public WorldData getWorldData() {
        return worldData;
    }

    public void sendMessage(NetworkProtocol.ChatMessage message) {
        if (isSinglePlayer) {
            if (chatMessageHandler != null) {
                chatMessageHandler.accept(message);
            }
            return;
        }

        try {
            if (!isConnected() || !isAuthenticated()) {
                GameLogger.error("Cannot send chat message - not connected or authenticated");
                return;
            }
            if (message.timestamp == 0) {
                message.timestamp = System.currentTimeMillis();
            }
            if (message.type == null) {
                message.type = NetworkProtocol.ChatType.NORMAL;
            }
            client.sendTCP(message);

            GameLogger.info("Sent chat message from " + message.sender +
                ": " + message.content);

        } catch (Exception e) {
            GameLogger.error("Failed to send chat message: " + e.getMessage());

            // Attempt reconnection if needed
            if (!isConnected()) {
                handleConnectionFailure(e);
            }

            // Still deliver message locally if handler exists
            if (chatMessageHandler != null) {
                chatMessageHandler.accept(message);
            }
        }
    }

    public NetworkProtocol.ChatMessage createSystemMessage(String content) {
        NetworkProtocol.ChatMessage message = new NetworkProtocol.ChatMessage();
        message.sender = "System";
        message.content = content;
        message.timestamp = System.currentTimeMillis();
        message.type = NetworkProtocol.ChatType.SYSTEM;
        return message;
    }

    private void handleChatMessage(NetworkProtocol.ChatMessage message) {
        if (message == null || message.content == null) {
            return;
        }

        chatMessageQueue.offer(message);

        // Process on main thread
        Gdx.app.postRunnable(() -> {
            if (chatMessageHandler != null) {
                chatMessageHandler.accept(message);
            }
        });

        GameLogger.info("Received chat message from " + message.sender +
            " of type " + message.type);
    }

    public String getLocalUsername() {
        return localUsername;
    }

    public void setLocalUsername(String localUsername) {
        this.localUsername = localUsername;
    }

    // Update dispose to act as a proper disconnect method
    public void dispose() {
        synchronized (connectionLock) {
            if (isDisposing.get()) {
                GameLogger.info("Already disposing");
                return;
            }

            if (keepaliveTask != null) {
                keepaliveTask.cancel(true);
                keepaliveTask = null;
            }

            isDisposing.set(true);
            shouldReconnect.set(false);

            try {
                if (client != null) {
                    if (client.isConnected()) {
                        client.close();
                    }
                    client.stop();
                    client = null;
                }
            } catch (Exception e) {
                GameLogger.error("Error disposing client: " + e.getMessage());
            }

            isDisposing.set(true);
            shouldReconnect.set(false);
            cleanupExistingConnection();
            GameLogger.info("GameClient disposed");
            isDisposing.set(true);
            currentPassword = null;  // Clear sensitive data

            authTimer.shutdownNow();

            // Clear initialization state
            isInitializing = false;
            fullyInitialized = false;
            isAuthenticating = false;

            // Clear all pending messages
            pendingMessages.clear();

            // Reset state flags
            isInitializing = false;
            fullyInitialized = false;

            shouldReconnect.set(false);
            isDisposing.set(true);
            if (currentWorld != null) {
                saveWorldState();
            }

            if (activePlayer != null) {
                savePlayerState(activePlayer.getPlayerData());
            }


            // Close connection
            if (client != null) {
                try {
                    if (client.isConnected()) {
                        client.close();
                    }
                    client.stop();
                    client.dispose();
                    client = null;
                } catch (Exception e) {
                    GameLogger.error("Error disposing client: " + e.getMessage());
                }
            }

            // Clear state
            synchronized (otherPlayers) {
                otherPlayers.values().forEach(player -> {
                    try {
                        player.dispose();
                    } catch (Exception e) {
                        GameLogger.error("Error disposing other player: " + e.getMessage());
                    }
                });
                otherPlayers.clear();
            }

            chatMessageQueue.clear();
            playerUpdates.clear();
            trackedWildPokemon.clear();
            syncedPokemonData.clear();

            // Reset state flags
            connectionState = ConnectionState.DISCONNECTED;
            isAuthenticated.set(false);
            isInitializing = false;

            GameLogger.info("GameClient disposed and disconnected");
        }

    }

    public void clearCredentials() {
        this.localUsername = null;
        this.currentPassword = null;
        credentials.clear();
        credentials.flush();
    }

    private void startKeepalive() {
        if (keepaliveTask != null) {
            keepaliveTask.cancel(true);
        }

        keepaliveTask = scheduler.scheduleAtFixedRate(() -> {
            if (!isConnected() || isDisposing.get()) {
                return;
            }

            try {
                // Send keepalive
                NetworkProtocol.Keepalive keepalive = new NetworkProtocol.Keepalive();
                keepalive.timestamp = System.currentTimeMillis();
                client.sendTCP(keepalive);

                // Check for timeout
                long timeSinceLastKeepalive = System.currentTimeMillis() - lastKeepaliveReceived;
                if (timeSinceLastKeepalive > KEEPALIVE_TIMEOUT) {
                    GameLogger.error("Keepalive timeout - last received: " + timeSinceLastKeepalive + "ms ago");
                    handleConnectionFailure(new Exception("Keepalive timeout"));
                }
            } catch (Exception e) {
                GameLogger.error("Failed to send keepalive: " + e.getMessage());
                handleConnectionFailure(e);
            }
        }, 0, KEEPALIVE_INTERVAL, TimeUnit.MILLISECONDS);
    }

    private void handleGameMessage(Object message) {
        try {
            if (message instanceof NetworkProtocol.PlayerUpdate) {
                handlePlayerUpdate((NetworkProtocol.PlayerUpdate) message);
            } else if (message instanceof NetworkProtocol.PlayerPosition) {
                handlePlayerPosition((NetworkProtocol.PlayerPosition) message);
            } else if (message instanceof NetworkProtocol.ChatMessage) {
                handleChatMessage((NetworkProtocol.ChatMessage) message);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void scheduleReconnection() {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            GameLogger.error("Max reconnection attempts reached");
            return;
        }

        long delay = RECONNECT_DELAY * (long) Math.pow(2, reconnectAttempts);
        reconnectAttempts++;

        scheduler.schedule(() -> {
            if (shouldReconnect.get() && !isDisposing.get()) {
                GameLogger.info("Attempting reconnection " + reconnectAttempts);
                cleanupExistingConnection();
                connect();
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    private void attemptReconnection() {
        synchronized (connectionLock) {
            if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
                GameLogger.error("Max reconnection attempts reached");
                if (chatMessageHandler != null) {
                    NetworkProtocol.ChatMessage message = createSystemMessage(
                        "Failed to reconnect after " + MAX_RECONNECT_ATTEMPTS + " attempts"
                    );
                    chatMessageHandler.accept(message);
                }
                return;
            }

            reconnectAttempts++;
            connect();
        }
    }

    private void handleReceivedMessage(Object object) {
        if (!isInitialized) {
            GameLogger.info("Received message before initialization: " + object.getClass().getName());

        }
        GameLogger.info("Received message: " + object.getClass().getName());
        try {
            if (object instanceof NetworkProtocol.LoginResponse) {
                handleLoginResponse((NetworkProtocol.LoginResponse) object);
            } else if (!isAuthenticated.get()) {
                GameLogger.info("Received message before authentication, queueing");
                pendingMessages.offer(object);
            } else if (object instanceof NetworkProtocol.ChatMessage) {
                handleChatMessage((NetworkProtocol.ChatMessage) object);
            } else if (object instanceof NetworkProtocol.PlayerUpdate) {
                handlePlayerUpdate((NetworkProtocol.PlayerUpdate) object);
            } else if (object instanceof NetworkProtocol.PlayerJoined) {
                handlePlayerJoined((NetworkProtocol.PlayerJoined) object);
            } else if (object instanceof NetworkProtocol.PlayerLeft) {
                handlePlayerLeft((NetworkProtocol.PlayerLeft) object);
            } else if (object instanceof NetworkProtocol.PlayerPosition) {
                handlePlayerPosition((NetworkProtocol.PlayerPosition) object);
            } else if (object instanceof NetworkProtocol.WildPokemonSpawn) {
                handlePokemonSpawn((NetworkProtocol.WildPokemonSpawn) object);
            } else if (object instanceof NetworkProtocol.WildPokemonDespawn) {
                handlePokemonDespawn((NetworkProtocol.WildPokemonDespawn) object);
            } else if (object instanceof NetworkProtocol.PokemonUpdate) {
                handlePokemonUpdate((NetworkProtocol.PokemonUpdate) object);
            } else if (object instanceof NetworkProtocol.WorldStateUpdate) {
                handleWorldStateUpdate((NetworkProtocol.WorldStateUpdate) object);
            }
        } catch (Exception e) {
            GameLogger.error("Error handling network message: " + e.getMessage());
        }
    }

    // Update handlePlayerPosition method
    private void handlePlayerPosition(NetworkProtocol.PlayerPosition positionMsg) {
        if (positionMsg == null || positionMsg.players == null) {
            GameLogger.error("Received empty PlayerPosition message.");
            return;
        }

        Gdx.app.postRunnable(() -> {
            try {
                synchronized (otherPlayers) {
                    for (Map.Entry<String, NetworkProtocol.PlayerUpdate> entry : positionMsg.players.entrySet()) {
                        String username = entry.getKey();
                        if (username.equals(localUsername)) continue;

                        NetworkProtocol.PlayerUpdate update = entry.getValue();
                        GameLogger.error("Received update for " + username + " at (" + update.x + "," + update.y + ")");

                        OtherPlayer otherPlayer = otherPlayers.computeIfAbsent(username,
                            k -> new OtherPlayer(username, update.x, update.y));

                        otherPlayer.updateFromNetwork(update);
                        playerUpdates.put(username, update);
                    }
                }
            } catch (Exception e) {
                GameLogger.error("Error in handlePlayerPosition: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }


    private void handleDisconnect() {
        GameLogger.info("Handling disconnect - Current state: " + connectionState);
        synchronized (connectionLock) {
            boolean wasConnected = connectionState == ConnectionState.CONNECTED ||
                connectionState == ConnectionState.AUTHENTICATED;

            connectionState = ConnectionState.DISCONNECTED;
            isConnected.set(false);
            isAuthenticated.set(false);
            loginRequestSent = false;

            if (wasConnected) {
                GameLogger.info("Lost established connection - will attempt reconnect");
                // Only attempt reconnect if we were previously connected
                if (!isDisposing.get() && shouldReconnect.get()) {
                    scheduleReconnection();
                }
            } else {
                GameLogger.info("Connection attempt failed - will retry from scratch");
                // Failed during connection attempt
                handleConnectionFailure(new Exception("Connection attempt failed"));
            }

            // **Do not dispose gameClient here**
            // Instead, mark it for disposal after pending operations are complete
        }
    }


    public void setPendingCredentials(String username, String password) {
        GameLogger.info("Setting pending credentials for: " + username);
        this.pendingUsername = username;
        this.pendingPassword = password;
        if (connectionState == ConnectionState.CONNECTED && !loginRequestSent) {
            // If we're connected but haven't sent login request, do it now
            NetworkProtocol.LoginRequest request = new NetworkProtocol.LoginRequest();
            request.username = username;
            request.password = password;
            request.timestamp = System.currentTimeMillis();
            try {
                client.sendTCP(request);
                loginRequestSent = true;
                GameLogger.info("Sent pending login request for: " + username);
            } catch (Exception e) {
                GameLogger.error("Failed to send pending login request: " + e.getMessage());
                handleConnectionFailure(e);
            }
        }
    }

    private void sendPlayerSync() {
        if (activePlayer == null) return;

        try {
            NetworkProtocol.PlayerUpdate update = new NetworkProtocol.PlayerUpdate();
            update.username = localUsername;
            update.x = activePlayer.getX();
            update.y = activePlayer.getY();
            update.direction = activePlayer.getDirection();
            update.isMoving = activePlayer.isMoving();
            update.wantsToRun = activePlayer.isRunning();
            update.timestamp = System.currentTimeMillis();

            client.sendTCP(update);
        } catch (Exception e) {
            GameLogger.error("Failed to send player sync: " + e.getMessage());
        }
    }

    private void updatePokemonStates(float deltaTime) {
        for (Map.Entry<UUID, WildPokemon> entry : trackedWildPokemon.entrySet()) {
            WildPokemon pokemon = entry.getValue();
            NetworkSyncData syncData = syncedPokemonData.get(entry.getKey());

            if (syncData != null && syncData.targetPosition != null) {
                updatePokemonPosition(pokemon, syncData, deltaTime);
            }
            pokemon.update(deltaTime);
        }
    }

    private void updatePokemonPosition(WildPokemon pokemon, NetworkSyncData syncData, float deltaTime) {
        syncData.interpolationProgress = Math.min(1.0f,
            syncData.interpolationProgress + deltaTime * INTERPOLATION_SPEED);

        float newX = lerp(pokemon.getX(), syncData.targetPosition.x, syncData.interpolationProgress);
        float newY = lerp(pokemon.getY(), syncData.targetPosition.y, syncData.interpolationProgress);

        pokemon.setX(newX);
        pokemon.setY(newY);
        pokemon.updateBoundingBox();
    }

    private float lerp(float start, float end, float alpha) {
        return start + (end - start) * alpha;
    }

    private void updateOtherPlayers(float deltaTime) {
        otherPlayers.values().forEach(player -> {
            player.update(deltaTime);
        });
    }

    public void tick(float deltaTime) {
        if (!isConnected() || isSinglePlayer) return;
        update(deltaTime);
        tickAccumulator += deltaTime;
        if (tickAccumulator >= TICK_RATE) {
            tickAccumulator = 0;

        }

        // Update other players with interpolation
        synchronized (otherPlayers) {
            otherPlayers.values().forEach(player -> player.update(deltaTime));
        }
    }

    public void connectToServer(ServerConnectionConfig config) {
        synchronized (connectionLock) {
            if (isInitializing || isConnected()) {
                GameLogger.info("Already connected or connecting, skipping connection attempt");
                return;
            }

            GameLogger.info("Starting connection attempt to: " + config.getServerIP() + ":" + config.getTcpPort());

            try {
                dispose();
                isInitializing = true;
                setServerConfig(config);

                client = new Client(BUFFER_SIZE, OBJECT_BUFFER);
                NetworkProtocol.registerClasses(client.getKryo());
                client.start();

                boolean connected = false;
                Exception lastException = null;

                for (int attempt = 1; attempt <= MAX_RETRIES && !connected; attempt++) {
                    try {
                        GameLogger.info("Connection attempt " + attempt + " to " +
                            config.getServerIP() + ":" + config.getTcpPort());

                        client.connect(CONNECTION_TIMEOUT, config.getServerIP(),
                            config.getTcpPort(), config.getUdpPort());

                        connected = true;
                        connectionState = ConnectionState.CONNECTED;
                        isConnected.set(true);
                        GameLogger.info("Successfully connected to server");

                    } catch (IOException e) {
                        lastException = e;
                        GameLogger.error("Connection attempt " + attempt + " failed: " + e.getMessage());

                        if (attempt < MAX_RETRIES) {
                            Thread.sleep(RETRY_DELAY_MS * attempt); // Exponential backoff
                        }
                    }
                }

                if (!connected) {
                    throw new IOException("Failed to connect after " + MAX_RETRIES + " attempts: " +
                        (lastException != null ? lastException.getMessage() : "Unknown error"));
                }

            } catch (Exception e) {
                GameLogger.error("Connection failed: " + e.getMessage());
                connectionState = ConnectionState.DISCONNECTED;
                isInitializing = false;
                throw new RuntimeException("Failed to connect to server: " + e.getMessage(), e);

            } finally {
                isInitializing = false;
            }
        }
    }

    private void sendRegisterRequestInternal() {
        try {
            GameLogger.info("Sending internal registration request");
            if (!isConnected()) {
                GameLogger.error("Cannot send registration - not connected");
                handleRegistrationFailure("Not connected to server");
                return;
            }

            NetworkProtocol.RegisterRequest request = new NetworkProtocol.RegisterRequest();
            request.username = pendingRegistrationUsername;
            request.password = pendingRegistrationPassword;

            client.sendTCP(request);
            GameLogger.info("Sent registration request for: " + pendingRegistrationUsername);

        } catch (Exception e) {
            GameLogger.error("Failed to send registration request: " + e.getMessage());
            handleRegistrationFailure("Failed to send registration request: " + e.getMessage());
        }
    }

    public void sendRegisterRequest(String username, String password) {
        if (isSinglePlayer) {
            GameLogger.info("Registration not needed in single player mode");
            return;
        }
        if (!isConnected() || client == null) {
            GameLogger.info("Client not connected - attempting to connect before registration");
            // Attempt to connect first
            connect();

            pendingRegistrationUsername = username;
            pendingRegistrationPassword = password;
            return;
        }
        try {
            if (username == null || username.trim().isEmpty() ||
                password == null || password.trim().isEmpty()) {
                handleRegistrationFailure("Username and password are required");
                return;
            }

            String secureUsername = username.trim();
            String securePassword = password.trim();

            NetworkProtocol.RegisterRequest request = new NetworkProtocol.RegisterRequest();
            request.username = secureUsername;
            request.password = securePassword;

            GameLogger.info("Sending registration request for: " + secureUsername);
            client.sendTCP(request);

            this.localUsername = secureUsername;

        } catch (Exception e) {
            GameLogger.error("Failed to send registration request: " + e.getMessage());
            handleRegistrationFailure("Failed to send registration request: " + e.getMessage());
        }
    }

    private void handleRegistrationFailure(String message) {
        String failedUsername = pendingRegistrationUsername;

        pendingRegistrationUsername = null;
        pendingRegistrationPassword = null;

        if (registrationResponseListener != null) {
            NetworkProtocol.RegisterResponse response = new NetworkProtocol.RegisterResponse();
            response.success = false;
            response.message = message;
            response.username = failedUsername;

            Gdx.app.postRunnable(() -> {
                registrationResponseListener.onResponse(response);
            });
        }

        GameLogger.error("Registration failed for " + failedUsername + ": " + message);
    }

    private void processChatMessages() {
        NetworkProtocol.ChatMessage message;
        while ((message = chatMessageQueue.poll()) != null) {
            final NetworkProtocol.ChatMessage finalMessage = message;
            Gdx.app.postRunnable(() -> {
                if (chatMessageHandler != null) {
                    chatMessageHandler.accept(finalMessage);
                }
            });
        }
    }

    private void handleForceLogout(NetworkProtocol.ForceLogout message) {
        GameLogger.info("Forced logout: " + message.reason);

        Gdx.app.postRunnable(() -> {
            if (gameHandler != null) {
                gameHandler.returnToLogin(message.reason);
            }
        });
    }


    private void handleForceDisconnect(NetworkProtocol.ForceDisconnect message) {
        GameLogger.info("Received force disconnect: " + message.reason);

        // Show message to user
        NetworkProtocol.ChatMessage systemMsg = createSystemMessage(message.reason);
        if (chatMessageHandler != null) {
            chatMessageHandler.accept(systemMsg);
        }

        // Clean up connection and return to login
        dispose();

        // Return to login screen
        if (loginResponseListener != null) {
            NetworkProtocol.LoginResponse response = new NetworkProtocol.LoginResponse();
            response.success = false;
            response.message = message.reason;
            loginResponseListener.onResponse(response);
        }
    }

    public boolean isInitialized() {
        return fullyInitialized;
    }

    public void setInitialized(boolean initialized) {
        this.isInitialized = initialized;
    }

    public boolean isInitializing() {
        return isInitializing;
    }

    public void initializePlayer(NetworkProtocol.LoginResponse response) {
        if (isSinglePlayer) {
            GameLogger.info("Skipping server player initialization for singleplayer");
            return;
        }

        GameLogger.info("Scheduling player initialization on the main rendering thread");

        Gdx.app.postRunnable(() -> {
            try {
                GameLogger.info("Creating new player: " + response.username);

                PlayerData playerData = currentWorld.getWorldData().getPlayerData(response.username);
                if (playerData == null) {
                    playerData = new PlayerData(response.username);
                    playerData.setX(response.x);
                    playerData.setY(response.y);
                    currentWorld.getWorldData().savePlayerData(response.username, playerData);
                }

                Player player = new Player(response.username, currentWorld);

                player.setX(response.x);
                player.setY(response.y);
                this.activePlayer = player;
                GameLogger.info("Player initialized: " + response.username + " at (" + response.x + "," + response.y + ")");

                currentWorld.setPlayer(player);

                // Notify that initialization is complete
                notifyInitializationComplete(true);
            } catch (Exception e) {
                GameLogger.error("Error initializing player on rendering thread: " + e.getMessage());
                notifyInitializationComplete(false);
            }
        });
    }


    private void cleanupExistingConnection() {
        synchronized (connectionLock) {
            try {
                if (keepaliveTask != null) {
                    keepaliveTask.cancel(true);
                    keepaliveTask = null;
                }
                if (client != null) {
                    if (client.isConnected()) {
                        client.close();
                    }
                    client.stop();
                    client = null;
                }

                connectionState = ConnectionState.DISCONNECTED;
                isConnected.set(false);
                isAuthenticated.set(false);
                lastKeepaliveReceived = System.currentTimeMillis();

                GameLogger.info("Connection cleanup complete");

            } catch (Exception e) {
                GameLogger.error("Error during cleanup: " + e.getMessage());
            }
        }
    }


    private void handleConnectionResponse(NetworkProtocol.ConnectionResponse response) {
        synchronized (connectionLock) {
            GameLogger.info("Handling connection response - Success: " + response.success);

            if (response.success) {
                // IMPORTANT: Set connection state BEFORE attempting to send login
                connectionState = ConnectionState.CONNECTED;
                isConnected.set(true);

                // Immediately attempt authentication
                if (pendingUsername != null && pendingPassword != null) {
                    GameLogger.info("Sending login request with pending credentials");
                    NetworkProtocol.LoginRequest request = new NetworkProtocol.LoginRequest();
                    request.username = pendingUsername;
                    request.password = pendingPassword;
                    request.timestamp = System.currentTimeMillis();

                    try {
                        client.sendTCP(request);
                        loginRequestSent = true;
                        GameLogger.info("Login request sent for: " + pendingUsername);
                    } catch (Exception e) {
                        GameLogger.error("Failed to send login request: " + e.getMessage());
                        handleConnectionFailure(e);
                    }
                } else {
                    GameLogger.error("No credentials available after successful connection");
                    handleConnectionFailure(new Exception("No credentials available"));
                }
            } else {
                GameLogger.error("Connection response indicated failure: " + response.message);
                handleConnectionFailure(new Exception(response.message));
            }
        }
    }

    private void sendLoginRequestInternal() {
        try {
            if (!isConnected()) {
                GameLogger.error("Cannot send login - not connected");
                return;
            }

            NetworkProtocol.LoginRequest request = new NetworkProtocol.LoginRequest();
            request.username = credentials.getString("username", null);
            request.password = credentials.getString("password", null);
            request.timestamp = System.currentTimeMillis();

            if (request.username == null || request.password == null) {
                GameLogger.error("Missing credentials for login");
                return;
            }

            GameLogger.info("Sending login request for: " + request.username);
            client.sendTCP(request);
            loginRequestSent = true;

        } catch (Exception e) {
            GameLogger.error("Failed to send login request: " + e.getMessage());
            handleConnectionFailure(e);
        }
    }

    public Player getActivePlayer() {
        return activePlayer;
    }

    private void handleAuthFailure() {
        GameLogger.error("Auth failure: " + "Authentication timeout");
        authAttempts++;

        if (authAttempts >= MAX_AUTH_ATTEMPTS) {
            handleConnectionFailure(new Exception("Max auth attempts exceeded: " + "Authentication timeout"));
            return;
        }

        // Retry after delay
        scheduler.schedule(() -> {
            if (isConnected() && !isAuthenticated.get()) {
                sendLoginRequestInternal();
            }
        }, AUTH_RETRY_DELAY, TimeUnit.MILLISECONDS);
    }

    private void handleLoginResponse(NetworkProtocol.LoginResponse response) {
        synchronized (connectionLock) {
            loginRequestSent = false;
            GameLogger.info("Handling login response: " + response.success);

            if (response.success) {
                connectionState = ConnectionState.AUTHENTICATED;
                isAuthenticated.set(true);
                localUsername = response.username;

                // Start the keepalive mechanism
                startKeepalive(); // Add this line

                // Schedule world initialization on main thread
                Gdx.app.postRunnable(() -> {
                    if (currentWorld == null) {
                        initializeWorld(response);
                    }

                    if (currentWorld == null) {
                        GameLogger.error("Failed to initialize world, currentWorld is null");
                        handleLoginFailure("Failed to initialize world");
                        return;
                    }

                    // Initialize player
                    initializePlayer(response);

                    // Set state flags AFTER initialization
                    fullyInitialized = true;
                    isInitialized = true;

                    // Notify on main thread
                    Gdx.app.postRunnable(() -> {
                        GameLogger.info("Notifying login response listener");
                        if (loginResponseListener != null) {
                            loginResponseListener.onResponse(response);
                        } else {
                            GameLogger.error("No login response listener set!");
                        }
                    });
                });
            } else {
                handleLoginFailure(response.message);
            }
        }
    }


    private void handleLoginFailure(String message) {
        GameLogger.error("Login failure: " + message);
        Gdx.app.postRunnable(() -> {
            if (loginResponseListener != null) {
                NetworkProtocol.LoginResponse failResponse = new NetworkProtocol.LoginResponse();
                failResponse.success = false;
                failResponse.message = message;
                loginResponseListener.onResponse(failResponse);
            }
        });
    }

    // Add helper method to ensure operations happen on main thread
    private void ensureMainThread(Runnable action) {
        if (Gdx.app.getType() == Application.ApplicationType.Desktop) {
            action.run();
        } else {
            Gdx.app.postRunnable(action);
        }
    }

    private void cleanupFailedInit() {
        GameLogger.info("Cleaning up failed initialization");

        // Reset auth state
        isAuthenticated.set(false);
        loginRequestSent = false;
        authAttempts = 0;

        // Clear any partial initialization
        if (currentWorld != null) {
            currentWorld = null;
        }
        if (activePlayer != null) {
            activePlayer = null;
        }

        // Reset connection state
        connectionState = ConnectionState.DISCONNECTED;

        // Cleanup connection
        cleanupExistingConnection();
    }

    private void processPendingMessages() {
        Object message;
        while ((message = pendingMessages.poll()) != null) {
            GameLogger.info("Processing queued message: " + message.getClass().getName());
            try {
                handleGameMessage(message);
            } catch (Exception e) {
                GameLogger.error("Error processing queued message: " + e.getMessage());
            }
        }
    }

    private void initializeWorld(NetworkProtocol.LoginResponse response) {
        if (isSinglePlayer) {
            GameLogger.info("Skipping server world initialization for singleplayer");
            return;
        }

        try {
            if (currentWorld == null) {
                GameLogger.info("Creating new World instance...");
                currentWorld = new World(
                    "multiplayer_world",
                    World.WORLD_SIZE,
                    World.WORLD_SIZE,
                    response.worldSeed,
                    this,
                    new BiomeManager(response.worldSeed)
                );
                GameLogger.info("World instance created successfully.");
            }

            GameLogger.info("Initializing world from server data...");
            currentWorld.initializeFromServer(
                response.worldSeed,
                response.worldTimeInMinutes,
                response.dayLength
            );
            GameLogger.info("World initialized from server.");
        } catch (Exception e) {
            GameLogger.error("Error initializing world: " + e.getMessage() + e);
            // Optionally rethrow or handle the exception as needed
        }
    }


    // In GameClient
    private void handlePlayerUpdate(NetworkProtocol.PlayerUpdate update) {
        if (update == null || update.username == null || update.username.equals(localUsername)) {
            return;
        }

        Gdx.app.postRunnable(() -> {
            synchronized (otherPlayers) {
                OtherPlayer otherPlayer = otherPlayers.get(update.username);
                if (otherPlayer == null) {
                    otherPlayer = new OtherPlayer(
                        update.username,
                        update.x,
                        update.y
                    );
                    otherPlayers.put(update.username, otherPlayer);
                    GameLogger.info("Created new player: " + update.username);
                }

                otherPlayer.updateFromNetwork(update);
                playerUpdates.put(update.username, update);
            }
        });
    }
    private final AtomicBoolean isConnecting = new AtomicBoolean(false);
    private final AtomicBoolean hasReceivedInitialJoin = new AtomicBoolean(false);

    private void handlePlayerJoined(NetworkProtocol.PlayerJoined joinMsg) {
        if (joinMsg.username.equals(localUsername)) {
            // Only process our own join message once
            if (hasReceivedInitialJoin.getAndSet(true)) {
                return;
            }
        }

        Gdx.app.postRunnable(() -> {
            synchronized (otherPlayers) {
                // Check if player already exists
                if (otherPlayers.containsKey(joinMsg.username)) {
                    GameLogger.info("Player " + joinMsg.username + " already exists, skipping duplicate PlayerJoined message");
                    return;
                }

                // Create new player instance
                if (!joinMsg.username.equals(localUsername)) {
                    OtherPlayer newPlayer = new OtherPlayer(
                        joinMsg.username,
                        joinMsg.x,
                        joinMsg.y
                    );
                    otherPlayers.put(joinMsg.username, newPlayer);

                    // Send join notification only for other players
                    NetworkProtocol.ChatMessage joinNotification = new NetworkProtocol.ChatMessage();
                    joinNotification.sender = "System";
                    joinNotification.content = joinMsg.username + " has joined the game";
                    joinNotification.type = NetworkProtocol.ChatType.SYSTEM;
                    joinNotification.timestamp = System.currentTimeMillis();

                    if (chatMessageHandler != null) {
                        chatMessageHandler.accept(joinNotification);
                    }
                }
            }
        });
    }

    private void handlePlayerLeft(NetworkProtocol.PlayerLeft leftMsg) {
        Gdx.app.postRunnable(() -> {
            OtherPlayer leftPlayer = otherPlayers.remove(leftMsg.username);
            if (leftPlayer != null) {
                leftPlayer.dispose();

                NetworkProtocol.ChatMessage leaveNotification = new NetworkProtocol.ChatMessage();
                leaveNotification.sender = "SYSTEM";
                leaveNotification.content = leftMsg.username + " has left the game";
                leaveNotification.type = NetworkProtocol.ChatType.SYSTEM;
                leaveNotification.timestamp = System.currentTimeMillis();

                if (chatMessageHandler != null) {
                    chatMessageHandler.accept(leaveNotification);
                }
            }

            playerStateBuffers.remove(leftMsg.username);
            playerUpdates.remove(leftMsg.username);
        });
    }

    private void handlePokemonSpawn(NetworkProtocol.WildPokemonSpawn spawnData) {
        if (spawnData == null || spawnData.uuid == null || spawnData.data == null) {
            GameLogger.error("Received invalid Pokemon spawn data");
            return;
        }

        Gdx.app.postRunnable(() -> {
            try {
                if (trackedWildPokemon.containsKey(spawnData.uuid)) {
                    return;
                }

                TextureRegion overworldSprite = TextureManager.getOverworldSprite(spawnData.data.getName());
                if (overworldSprite == null) {
                    GameLogger.error("Could not load sprite for Pokemon: " + spawnData.data.getName());
                    return;
                }

                WildPokemon pokemon = new WildPokemon(
                    spawnData.data.getName(),
                    spawnData.data.getLevel(),
                    (int) spawnData.x,
                    (int) spawnData.y,
                    overworldSprite
                );

                pokemon.setUuid(spawnData.uuid);
                pokemon.setDirection("down");
                pokemon.setSpawnTime(spawnData.timestamp / 1000L);

                trackedWildPokemon.put(spawnData.uuid, pokemon);
                syncedPokemonData.put(spawnData.uuid, new NetworkSyncData());

                if (currentWorld != null && currentWorld.getPokemonSpawnManager() != null) {
                    currentWorld.getPokemonSpawnManager().addPokemonToChunk(
                        pokemon,
                        new Vector2(spawnData.x, spawnData.y)
                    );
                }
            } catch (Exception e) {
                GameLogger.error("Error handling Pokemon spawn: " + e.getMessage());
            }
        });
    }

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

                    // Remove from world after animation completes
                    com.badlogic.gdx.utils.Timer.schedule(new com.badlogic.gdx.utils.Timer.Task() {
                        @Override
                        public void run() {
                            currentWorld.getPokemonSpawnManager()
                                .removePokemon(despawnData.uuid);
                        }
                    }, 1.0f); // Animation duration
                }

                GameLogger.info("Handled Pokemon despawn for UUID: " + despawnData.uuid);

            } catch (Exception e) {
                GameLogger.error("Error handling Pokemon despawn: " + e.getMessage());
            }
        });
    }

    private void handlePokemonUpdate(NetworkProtocol.PokemonUpdate update) {
        if (update == null || update.uuid == null) return;

        Gdx.app.postRunnable(() -> {
            WildPokemon pokemon = trackedWildPokemon.get(update.uuid);
            NetworkSyncData syncData = syncedPokemonData.get(update.uuid);

            if (pokemon == null || syncData == null) {
                requestPokemonSpawnData(update.uuid);
                return;
            }

            syncData.targetPosition = new Vector2(update.x, update.y);
            syncData.direction = update.direction;
            syncData.isMoving = update.isMoving;
            syncData.lastUpdateTime = System.currentTimeMillis();
            syncData.interpolationProgress = 0f;

            pokemon.setDirection(update.direction);
            pokemon.setMoving(update.isMoving);

            if (update.level > 0) pokemon.setLevel(update.level);
            if (update.currentHp > 0) pokemon.setCurrentHp(update.currentHp);
            pokemon.setSpawnTime(update.timestamp / 1000L);

            if (pokemonUpdateHandler != null) {
                pokemonUpdateHandler.onUpdate(update);
            }
        });
    }

    public void sendPokemonUpdate(NetworkProtocol.PokemonUpdate update) {
        if (!isAuthenticated.get() || connectionState != ConnectionState.CONNECTED) return;

        try {
            if (update.timestamp == 0) {
                update.timestamp = System.currentTimeMillis();
            }
            client.sendTCP(update);
        } catch (Exception e) {
            GameLogger.error("Failed to send Pokemon update: " + e.getMessage());
        }
    }

    private void requestPokemonSpawnData(UUID pokemonId) {
        if (!isAuthenticated.get() || connectionState != ConnectionState.CONNECTED) return;

        try {
            NetworkProtocol.PokemonSpawnRequest request = new NetworkProtocol.PokemonSpawnRequest();
            request.uuid = pokemonId;
            client.sendTCP(request);
        } catch (Exception e) {
            GameLogger.error("Failed to request Pokemon spawn data: " + e.getMessage());
        }
    }


    public void sendWorldObjectUpdate(NetworkProtocol.WorldObjectUpdate update) {
        if (isSinglePlayer || !isConnected() || !isAuthenticated()) {
            return;
        }

        try {
            if (update.data == null) {
                update.data = new HashMap<>();
            }

            if (!update.data.containsKey("timestamp")) {
                update.data.put("timestamp", System.currentTimeMillis());
            }

            // Send update via TCP for reliability
            client.sendTCP(update);

            GameLogger.info("Sent world object update - Type: " + update.type +
                ", ObjectID: " + update.objectId);

        } catch (Exception e) {
            GameLogger.error("Failed to send world object update: " + e.getMessage());
            handleConnectionFailure(e);
        }
    }

    // Add to existing handleReceivedMessage method
    private void handleWorldObjectUpdate(NetworkProtocol.WorldObjectUpdate update) {
        if (update == null || currentWorld == null) {
            return;
        }

        try {
            switch (update.type) {
                case ADD:
                    handleWorldObjectAdd(update);
                    break;

                case UPDATE:
                    handleWorldObjectModify(update);
                    break;

                case REMOVE:
                    handleWorldObjectRemove(update);
                    break;
            }
        } catch (Exception e) {
            GameLogger.error("Error processing world object update: " + e.getMessage());
        }
    }

    private void handleWorldObjectAdd(NetworkProtocol.WorldObjectUpdate update) {
        if (currentWorld == null || update.data == null) return;

        try {
            // Convert network data to world object
            int tileX = (int) update.data.get("tileX");
            int tileY = (int) update.data.get("tileY");
            WorldObject.ObjectType type = WorldObject.ObjectType.valueOf((String) update.data.get("type"));

            // Create new object
            TextureRegion texture = TextureManager.getTextureForObjectType(type);
            WorldObject newObject = new WorldObject(tileX, tileY, texture, type);

            // Calculate chunk position
            Vector2 chunkPos = new Vector2(
                Math.floorDiv(tileX, Chunk.CHUNK_SIZE),
                Math.floorDiv(tileY, Chunk.CHUNK_SIZE)
            );

            // Add to world
            currentWorld.getObjectManager().addObjectToChunk(newObject);

            GameLogger.info("Added world object: " + type + " at (" + tileX + "," + tileY + ")");

        } catch (Exception e) {
            GameLogger.error("Failed to handle world object add: " + e.getMessage());
        }
    }

    private void handleWorldObjectModify(NetworkProtocol.WorldObjectUpdate update) {
        if (currentWorld == null) return;

        try {
            // Find the object and update it
            //            WorldObject object = currentWorld.getObjectManager.(update.objectId);
            //            if (object != null) {
            //                object.updateFromNetwork(update);
            //                GameLogger.info("Updated world object: " + update.objectId);
            //            }
        } catch (Exception e) {
            GameLogger.error("Failed to handle world object modify: " + e.getMessage());
        }
    }

    private void handleWorldObjectRemove(NetworkProtocol.WorldObjectUpdate update) {
        if (currentWorld == null) return;

        try {
            // Calculate chunk position from the object's data
            int tileX = (int) update.data.get("tileX");
            int tileY = (int) update.data.get("tileY");

            Vector2 chunkPos = new Vector2(
                Math.floorDiv(tileX, Chunk.CHUNK_SIZE),
                Math.floorDiv(tileY, Chunk.CHUNK_SIZE)
            );

            // Remove from world
            currentWorld.getObjectManager().removeObjectFromChunk(chunkPos, update.objectId);

            GameLogger.info("Removed world object: " + update.objectId);

        } catch (Exception e) {
            GameLogger.error("Failed to handle world object remove: " + e.getMessage());
        }
    }// Add to GameClient class

    public void sendPokemonDespawn(UUID pokemonId) {
        if (!isConnected() || client == null || isSinglePlayer) {
            return;
        }

        try {
            NetworkProtocol.WildPokemonDespawn despawnUpdate = new NetworkProtocol.WildPokemonDespawn();
            despawnUpdate.uuid = pokemonId;
            despawnUpdate.timestamp = System.currentTimeMillis();

            client.sendTCP(despawnUpdate);
            trackedWildPokemon.remove(pokemonId);
            syncedPokemonData.remove(pokemonId);

            GameLogger.info("Sent Pokemon despawn for ID: " + pokemonId);
        } catch (Exception e) {
            GameLogger.error("Failed to send Pokemon despawn: " + e.getMessage());
            if (!isConnected()) {
                handleConnectionFailure(e);
            }
        }
    }

    private void handleConnectionFailure(Exception e) {
        isConnecting.set(false);
        hasReceivedInitialJoin.set(false);
        cleanupExistingConnection();

        if (!isDisposing.get() && shouldReconnect.get()) {
            scheduleReconnection();
        }
    }
    private final AtomicBoolean isMarkedForDisposal = new AtomicBoolean(false);

    public void disposeIfNeeded() {
        //        if (isMarkedForDisposal.get() && !isProcessingPendingOperations()) {
        //            dispose();
        //        }
    }


    public void setChatMessageHandler(Consumer<NetworkProtocol.ChatMessage> handler) {
        this.chatMessageHandler = handler;
    }

    public void setLoginResponseListener(LoginResponseListener listener) {
        this.loginResponseListener = listener;
    }

    public void setRegistrationResponseListener(RegistrationResponseListener listener) {
        this.registrationResponseListener = listener;
    }

    public void setUsernameCheckListener(UsernameCheckListener listener) {
        this.usernameCheckListener = listener;
    }

    public Map<String, OtherPlayer> getOtherPlayers() {
        return new HashMap<>(otherPlayers);
    }

    public boolean isAuthenticated() {
        return isAuthenticated.get();
    }

    public boolean isConnected() {
        return connectionState == ConnectionState.CONNECTED || connectionState == ConnectionState.AUTHENTICATED;
    }


    private enum ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        AUTHENTICATED
    }

    public interface InitializationListener {
        void onInitializationComplete(boolean success);
    }

    public interface LoginResponseListener {
        void onResponse(NetworkProtocol.LoginResponse response);
    }

    public interface RegistrationResponseListener {
        void onResponse(NetworkProtocol.RegisterResponse response);
    }

    public interface UsernameCheckListener {
        void onResponse(NetworkProtocol.UsernameCheckResponse response);
    }

    public interface PokemonUpdateHandler {
        void onUpdate(NetworkProtocol.PokemonUpdate update);
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
        private final Queue<NetworkProtocol.PlayerUpdate> states = new LinkedList<>();

        public void addState(NetworkProtocol.PlayerUpdate update) {
            states.offer(update);
            while (states.size() > BUFFER_SIZE) {
                states.poll();
            }
        }
    }

}

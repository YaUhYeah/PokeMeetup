package io.github.pokemeetup.multiplayer.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.Json;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import io.github.pokemeetup.multiplayer.OtherPlayer;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.multiplayer.server.config.ServerConnectionConfig;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.PlayerData;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.multiworld.WorldData;
import io.github.pokemeetup.system.gameplay.inventory.Inventory;
import io.github.pokemeetup.system.gameplay.inventory.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

public class GameClient {
    private static final float SYNC_INTERVAL = 1 / 20f; // 60 Hz update rate
    private static final int CONNECTION_TIMEOUT = 5000;
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 1000;
    private static final Logger logger = LoggerFactory.getLogger(GameClient.class);
    private static final float SEND_INTERVAL = 1 / 20f; // 20 Hz
    private final boolean isSinglePlayer;
    private final Map<String, OtherPlayer> otherPlayers = new ConcurrentHashMap<>();
    private final Object connectionLock = new Object();
    private final Queue<NetworkProtocol.ChatMessage> chatMessageQueue = new ConcurrentLinkedQueue<>();
    private final Object atlasLock = new Object();
    private TextureAtlas gameAtlas;
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

    public GameClient(ServerConnectionConfig config, boolean isSinglePlayer, String serverIP, int tcpPort, int udpPort) {
        this.isSinglePlayer = isSinglePlayer;
        this.serverIP = serverIP;
        this.tcpPort = tcpPort;
        this.udpPort = udpPort;// In GameScreen constructor
        this.serverConfig = config;      // Initialize networking only if multiplayer
        // Load server configuration from file
        if (!isSinglePlayer) {
            if (serverConfig != null) {
                setServerConfig(serverConfig);  // Set the loaded config
                initializeNetworking();  // Initialize networking only if the config is loaded
            } else {
                System.err.println("Failed to load server config, multiplayer disabled.");
            }

        }
    }// Add this to GameClient.java

    // Method to get atlas, loading if necessary
    public TextureAtlas getGameAtlas() {
        synchronized (atlasLock) {
            if (gameAtlas == null) {
                loadTextures();
                // Wait for texture loading
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return gameAtlas;
        }
    }

    // Add setter for player reference
    public void setActivePlayer(Player player) {
        this.activePlayer = player;
    }

    // Method to load textures on the main thread
    public void loadTextures() {
        Gdx.app.postRunnable(() -> {
            synchronized (atlasLock) {
                if (gameAtlas == null) {
                    try {
                        gameAtlas = new TextureAtlas(Gdx.files.internal("atlas/game-atlas"));
                        System.out.println("Loaded game atlas successfully");
                    } catch (Exception e) {
                        System.err.println("Error loading game atlas: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    public void setCurrentWorld(World currentWorld) {
        this.currentWorld = currentWorld;
    }

    public void setServerConfig(ServerConnectionConfig config) {
        if (config == null) {
            logger.error("Cannot set null server config");
            return;
        }

        try {
            config.validate();
            synchronized (connectionLock) {
                this.serverConfig = config;
                logger.info("Server config updated: {}:{}", config.getServerIP(), config.getTcpPort());
            }
        } catch (IllegalArgumentException e) {
            logger.error("Invalid server configuration: {}", e.getMessage());
        }
    }

    public void sendWorldObjectUpdate(NetworkProtocol.WorldObjectUpdate update) {
        if (!isConnected() || client == null) {
            System.err.println("Cannot send world object update - not connected to server");
            return;
        }

        try {
            client.sendTCP(update);
        } catch (Exception e) {
            System.err.println("Failed to send world object update: " + e.getMessage());
        }
    }


    public void connectToServer(ServerConnectionConfig config) {
        synchronized (connectionLock) {
            if (config == null) {
                logger.error("Cannot connect with null config");
                return;
            }

            setServerConfig(config);
            shouldReconnect = true;
            initializeNetworking();
        }
    }

    public void disconnect() {
        synchronized (connectionLock) {
            shouldReconnect = false;
            if (reconnectionTimer != null) {
                reconnectionTimer.cancel();
                reconnectionTimer = null;
            }
            cleanupExistingConnection();
            logger.info("Disconnected from server");
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
            System.err.println("Error cleaning up client: " + e.getMessage());
        }
    }

    private void initializeNetworking() {
        synchronized (connectionLock) {
            if (isInitializing || serverConfig == null) {
                logger.debug("Skipping initialization: {} {}",
                    isInitializing ? "already initializing" : "",
                    serverConfig == null ? "config is null" : "");
                return;
            }

            try {
                isInitializing = true;
                cleanupExistingConnection();
                setupNewConnection();
            } catch (Exception e) {
                logger.error("Network initialization failed: {}", e.getMessage());
            } finally {
                isInitializing = false;
            }
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
                    logger.info("Attempting connection to {}:{} (Attempt {}/{})",
                        serverConfig.getServerIP(), serverConfig.getTcpPort(),
                        attempts + 1, MAX_RETRIES);

                    client.connect(5000, serverConfig.getServerIP(),
                        serverConfig.getTcpPort(), serverConfig.getUdpPort());
                    connected = true;
                    isConnected = true;
                    logger.info("Successfully connected to server");
                } catch (IOException e) {
                    attempts++;
                    if (attempts < MAX_RETRIES && shouldReconnect) {
                        logger.warn("Connection attempt {} failed, retrying in {}ms",
                            attempts, RETRY_DELAY_MS);
                        Thread.sleep(RETRY_DELAY_MS);
                    }
                }
            }

            if (!connected) {
                logger.error("Failed to connect after {} attempts", MAX_RETRIES);
                throw new IOException("Failed to connect to server after " + MAX_RETRIES + " attempts");
            }

        } catch (Exception e) {
            logger.error("Failed to setup network connection: {}", e.getMessage());
            cleanupExistingConnection();
            throw new RuntimeException("Network setup failed", e);
        }
    }

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
                logger.error("Error cleaning up existing connection: {}", e.getMessage());
            }
        }
    }

    private void handleDisconnect(Connection connection) {
        System.out.println("Disconnected from server - Connection ID: " + connection.getID() + ", Username: " + localUsername);

        // Save state before cleanup
        if (localUsername != null && !isDisposing) {
            PlayerData disconnectState = new PlayerData(localUsername);
            saveLocalPlayerState(disconnectState);
        }

        isConnected = false;

        // Don't clear username on temporary disconnects
        cleanup();

        if (!isDisposing) {
            new Thread(() -> {
                try {
                    Thread.sleep(3000); // Wait 3 seconds before reconnecting
                    if (!isConnected && !isDisposing) {
                        System.out.println("Attempting to reconnect...");
                        initializeNetworking();
                        if (localUsername != null) {
                            sendLoginRequest(localUsername, ""); // Password handling needs to be implemented
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        }
    }

    public void sendMessage(NetworkProtocol.ChatMessage message) {
        if (!isConnected() || client == null) {
            System.err.println("Cannot send chat message - not connected to server");
            return;
        }

        try {
            // Send via TCP to ensure delivery
            client.sendTCP(message);
//            System.out.println(STR."Sent chat message: \{message.content}");
        } catch (Exception e) {
//            System.err.println(STR."Failed to send chat message: \{e.getMessage()}");
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

            System.out.println("Player left: " + leftMsg.username);
            leftPlayer.dispose();
        }
    }

    public void setChatMessageHandler(Consumer<NetworkProtocol.ChatMessage> handler) {
        this.chatMessageHandler = handler;
    }


    private void handleReceivedObject(Object object) {
//        System.out.println(STR."Received object: \{object.getClass().getName()}");

        try {
            switch (object) {
                case NetworkProtocol.ChatMessage chatMessage ->
                    // Ensure messages are handled on the main thread
                    Gdx.app.postRunnable(() -> {
                        handleChatMessage((NetworkProtocol.ChatMessage) object);
                    });
                case NetworkProtocol.PlayerUpdate netUpdate -> {
                    // System.out.println(STR."Cli//ent received player update for: \{netUpdate.username} Position: (\{netUpdate.x}, \{netUpdate.y})");

                    handlePlayerUpdate(netUpdate);
                }
                case NetworkProtocol.PlayerJoined playerJoined -> handlePlayerJoined(playerJoined);
                case NetworkProtocol.PlayerLeft playerLeft -> handlePlayerLeft(playerLeft);
                case NetworkProtocol.PlayerPosition playerPosition -> handlePlayerPositions(playerPosition);
                case NetworkProtocol.InventoryUpdate inventoryUpdate -> handleInventoryUpdate(inventoryUpdate);
                case NetworkProtocol.LoginResponse loginResponse -> handleLoginResponse(loginResponse);
                case NetworkProtocol.WorldObjectUpdate ignored -> {

                    if (currentWorld != null) {
                        currentWorld.getObjectManager().handleNetworkUpdate(
                            (NetworkProtocol.WorldObjectUpdate) object
                        );
                    }
                }
                default -> {
                }
            }
        } catch (Exception e) {
//            System.err.println(STR."Error handling network message: \{e.getMessage()}");
            e.printStackTrace();
        }
    }

    private void handleChatMessage(NetworkProtocol.ChatMessage message) {
        // Add debug logging
//        System.out.println("Received chat message from: \{message.sender content: \{message.content}");

        if (chatMessageHandler != null) {
            chatMessageHandler.accept(message);
        } else {
            System.err.println("Chat message handler is null!");
        }
    }

    private void cleanup() {
        if (!isDisposing && lastKnownState != null) {
//            System.out.println(STR."Cleanup - Saving final state for: \{localUsername}");
            saveLocalPlayerState(lastKnownState);
        }
        otherPlayers.clear();
        playerStateBuffers.clear();
        // Don't clear localUsername or lastKnownState
    }


    private float getCurrentY() {
        OtherPlayer playerState = otherPlayers.get(localUsername);
        return playerState != null ? playerState.getY() : 0;
    }

    private List<WorldData.ItemStack> convertInventoryToStacks(Inventory inventory) {
        List<WorldData.ItemStack> stacks = new ArrayList<>();
        for (Item item : inventory.getItems()) {
            if (item != null) {
                stacks.add(new WorldData.ItemStack(item.getName(), item.getCount()));
            }
        }
        return stacks;
    }

    private List<String> convertInventoryToNames(Inventory inventory) {
        List<String> itemNames = new ArrayList<>();
        for (Item item : inventory.getItems()) {
            if (item != null) {
                // Just store item name and count as a simple string format
                itemNames.add(item.getName() + ":" + item.getCount());
            }
        }
        return itemNames;
    }

    public void savePlayerState(PlayerData playerData) {   // Skip for single player
        if (isSinglePlayer) {
            return;
        }
        if (playerData == null) {
            System.err.println("Cannot save null PlayerData");
            return;
        }

//        System.out.println(STR."Attempting to save player state for: \{playerData.getUsername()}");

        try {
            if (isSinglePlayer()) {
                saveLocalPlayerState(playerData);
            } else {
                if (isConnected() && client != null) {
                    // Send to server
                    sendPlayerUpdateToServer(playerData);
                    // Also keep local backup
                    updateLastKnownState(playerData);
//                    System.out.println(STR."Sent player state to server for: \{playerData.getUsername()}");
                } else {
                    System.out.println("Not connected to server, saving local backup");
                    saveLocalPlayerState(playerData);
                }
            }
        } catch (Exception e) {
            System.err.println("Error during save: " + e.getMessage());
            e.printStackTrace();
        }
    }


    private void saveLocalPlayerState(PlayerData currentState) {
        try {
            if (currentState == null || currentState.getUsername() == null) {
                throw new IllegalStateException("Invalid state for saving");
            }

            // Get world data
            if (currentWorld == null || currentWorld.getWorldData() == null) {
                throw new IllegalStateException("No world data available for saving");
            }

            WorldData worldData = currentWorld.getWorldData();

            // Update the player data in world
            worldData.savePlayerData(currentState.getUsername(), currentState);

            // Save world data to file
            Json json = new Json();
            json.setUsePrototypes(false);  // Important for clean serialization

            String worldFileName = String.format("assets/worlds/%s/world.json", currentWorld.getName());
            FileHandle worldFile = Gdx.files.local(worldFileName);

            // Ensure directory exists
            if (!worldFile.parent().exists()) {
                worldFile.parent().mkdirs();
            }

            // Save world data with updated player info
            worldFile.writeString(json.prettyPrint(worldData), false);

            System.out.printf("Saved player state in world.json - World: %s, Player: %s, Position: (%.2f,%.2f)%n",
                currentWorld.getName(),
                currentState.getUsername(),
                currentState.getX(),
                currentState.getY());

            // Update last known state
            this.lastKnownState = currentState;

            // Update world's last played time
            worldData.updateLastPlayed();

        } catch (Exception e) {
//            System.err.println(STR."Failed to save player state: \{e.getMessage()}");
            e.printStackTrace();
        }
    }

    public void sendInventoryUpdate(String username, List<String> itemNames) {
        if (isSinglePlayer) return;
        if (!isConnected || client == null) {
            System.err.println("Cannot send inventory update - not connected to server");
            return;
        }

        try {
            // Update last known state with inventory
            if (lastKnownState != null && itemNames != null) {
                lastKnownState.updateFromPlayer(activePlayer);
                System.out.println("Updated last known state inventory during send: " + itemNames);
            }

            NetworkProtocol.InventoryUpdate update = new NetworkProtocol.InventoryUpdate();
            update.username = username;
            update.itemNames = new ArrayList<>(itemNames);
            client.sendTCP(update);  // Or UDP as needed

            System.out.println("Sent inventory update for: " + username + " - Items: " + itemNames);
        } catch (Exception e) {
            System.err.println("Failed to send inventory update: " + e.getMessage());
        }
    }

    public void updateLastKnownState(PlayerData currentState) {
        if (currentState == null || localUsername == null) return;

        if (lastKnownState == null) {
            lastKnownState = new PlayerData(localUsername);
        }

        // Deep copy the state

        lastKnownState.updateFromPlayer(activePlayer);


        // Add logging to see what state is being set
//        System.out.println(STR."Updated last known state: Position (\{lastKnownState.getX()}, \{lastKnownState.getY()}), Inventory: \{lastKnownState.getInventory()}");
    }

    public synchronized void sendPlayerUpdateToServer(PlayerData playerData) {
        if (isSinglePlayer) {
            return;
        }
        if (playerData == null) {
            System.err.println("Cannot save null PlayerData");
            return;
        }

        try {
            if (isSinglePlayer()) {
                saveLocalPlayerState(playerData);
            } else {
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

                    System.out.println("Serialized PlayerUpdate size: " + size + " bytes");

                    if (size > 16384) {
                        System.err.println("PlayerUpdate size exceeds buffer limit: " + size + " bytes");
                        return;
                    }

                    // Send the update
                    client.sendTCP(update);
                    System.out.println("PlayerUpdate sent successfully for: " + playerData.getUsername());

                    // Also send InventoryUpdate if needed
                    sendInventoryUpdate(playerData.getUsername(), playerData.getInventoryItems());

                    // Keep local backup
                    updateLastKnownState(playerData);
                } else {
                    System.out.println("Not connected to server, saving local backup");
                    saveLocalPlayerState(playerData);
                }
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
                logger.info("Connected to server successfully as: {}", localUsername);
            }

            @Override
            public void disconnected(Connection connection) {
                synchronized (connectionLock) {
                    isConnected = false;
                    logger.info("Disconnected from server");

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
//                    System.err.println(STR."Error processing received object: \{e.getMessage()}");
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
                        logger.info("Attempting to reconnect...");
                        initializeNetworking();
                    }
                }
            }
        }, RETRY_DELAY_MS, RETRY_DELAY_MS * 5);
    }

    public void restorePlayerState(Player player) {
        if (player == null) return;

        PlayerData savedState = null;
        if (isSinglePlayer) {
            // Load from world data
            World currentWorld = player.getWorld();
            if (currentWorld != null && currentWorld.getWorldData() != null) {
                savedState = currentWorld.getWorldData().getPlayerData(player.getUsername());
            }
        }

        if (savedState != null) {
            System.out.println("Restoring player state from GameClient");
            savedState.updateFromPlayer(player);
            player.updateFromState();
        }
    }

    public void sendLoginRequest(String username, String password) {
        if (!isConnected()) {
            System.out.println("Not connected to server. Cannot send login request.");
            return;
        }

        // Create and send login request using NetworkProtocol
        NetworkProtocol.LoginRequest request = new NetworkProtocol.LoginRequest();
        request.username = username;
        request.password = password;

        client.sendTCP(request);  // Send the request to the server
    }


    public void sendRegisterRequest(String username, String password) {
        if (isSinglePlayer) return;
        if (!isConnected || client == null) {
            System.err.println("Cannot send register request - not connected to server");
            return;
        }

        // Save username for later use
        this.localUsername = username;

        NetworkProtocol.RegisterRequest request = new NetworkProtocol.RegisterRequest();
        request.username = username;
        request.password = password;

        System.out.println("Sending registration request for: " + username);
        client.sendTCP(request);
    }

    private void handleRegisterResponse(NetworkProtocol.RegisterResponse response) {
        System.out.println("Received registration response: " + response.success +
            " - " + response.message);
        if (registrationResponseListener != null) {
            registrationResponseListener.onResponse(response);
        }
    }

    public synchronized void sendPlayerUpdate() {
        if (isSinglePlayer) {
            return;
        }
        if (localUsername == null || localUsername.isEmpty()) {
            System.err.println("Cannot send update: Username is null or empty");
            return;
        }
        if (activePlayer == null) {
            System.err.println("Cannot send update: Active player is null");
            return;
        }

        NetworkProtocol.PlayerUpdate update = new NetworkProtocol.PlayerUpdate();
        update.username = localUsername;
        update.x = activePlayer.getX();
        update.y = activePlayer.getY();
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
            System.out.println("Serialized PlayerUpdate size: " + size + " bytes");
            if (size > 16384) {
                System.err.println("PlayerUpdate size exceeds buffer limit: " + size + " bytes");
                return;
            }
        } catch (Exception e) {
            System.err.println("Serialization failed for PlayerUpdate: " + e.getMessage());
            e.printStackTrace();
            return;
        } finally {
            output.close();
        }

        try {
            client.sendTCP(update);
            System.out.println("PlayerUpdate sent successfully for: " + localUsername);
        } catch (Exception e) {
            System.err.println("Failed to send PlayerUpdate: " + e.getMessage());
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
                    System.err.println("Failed to load saved state: " + e.getMessage());
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
            System.err.println("Error handling player positions: " + e.getMessage());
        }
    }


    private void handlePlayerJoined(NetworkProtocol.PlayerJoined joinMsg) {
        if (joinMsg.username.equals(localUsername)) return;

        // Create new other player
        OtherPlayer newPlayer = new OtherPlayer(
            joinMsg.username,
            joinMsg.x,
            joinMsg.y,
            gameAtlas
        );

        // Set initial state
        NetworkProtocol.PlayerUpdate initialState = new NetworkProtocol.PlayerUpdate();
        initialState.username = joinMsg.username;
        initialState.x = joinMsg.x;
        initialState.y = joinMsg.y;
        initialState.direction = joinMsg.direction;
        initialState.isMoving = joinMsg.isMoving;
        initialState.inventoryItemNames = joinMsg.inventoryItemNames;

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

        System.out.println("New player joined: " + joinMsg.username +
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
            otherPlayer = new OtherPlayer(netUpdate.username, netUpdate.x, netUpdate.y, gameAtlas);
            otherPlayers.put(netUpdate.username, otherPlayer);
            System.out.println("Created OtherPlayer for " + netUpdate.username);
        }

        // Update the OtherPlayer with the data from the server
        otherPlayer.updateFromNetwork(netUpdate);
        System.out.println("Updated OtherPlayer " + netUpdate.username + " with position: (" + netUpdate.x + ", " + netUpdate.y + ")");
    }


    private void handleInventoryUpdate(NetworkProtocol.InventoryUpdate inventoryUpdate) {
        if (inventoryUpdate.username == null || inventoryUpdate.username.equals(localUsername)) {
            return; // Ignore updates for self or if username is null
        }

        OtherPlayer otherPlayer = otherPlayers.get(inventoryUpdate.username);
        if (otherPlayer == null) {
            // Create a new OtherPlayer instance if not already present
            otherPlayer = new OtherPlayer(inventoryUpdate.username, 0, 0, gameAtlas); // Default position; will be updated
            otherPlayers.put(inventoryUpdate.username, otherPlayer);
//            System.out.println(STR."Added new OtherPlayer for inventory: \{inventoryUpdate.username}");
        }

        // Update the inventory of the OtherPlayer
        otherPlayer.getInventory().loadFromStrings(inventoryUpdate.itemNames);
//        System.out.println(STR."Updated inventory for OtherPlayer: \{inventoryUpdate.username}");
    }

    /**
     * Retrieves a map of all other players.
     *
     * @return Map of username to OtherPlayer instances.
     */
// In the GameClient class
    public Map<String, OtherPlayer> getOtherPlayers() {
        // Debug log to ensure this is working
        System.out.println("Returning " + otherPlayers.size() + " players from GameClient");
        return otherPlayers; // Ensure this map is populated correctly
    }

    // [Existing methods...]

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
        System.out.println("Local username set to: " + username);
    }

    public boolean isConnected() {
        return isConnected && client != null;
    }


    public void dispose() {
        try {
            if (activePlayer != null) {
                PlayerData finalState = new PlayerData(activePlayer.getUsername());

                finalState.updateFromPlayer(activePlayer);

                savePlayerState(finalState);
            }
        } catch (Exception e) {
            System.err.println("Error during final save: " + e.getMessage());
        }

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
        syncTimer += delta;
        sendTimer += delta;
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
                sendPlayerUpdate(); // Optionally, if you want to send at a different interval
            }
        }

        for (OtherPlayer otherPlayer : otherPlayers.values()) {
            otherPlayer.update(delta); // Ensure OtherPlayer objects are updated
        }

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



    public interface LoginResponseListener {
        void onResponse(NetworkProtocol.LoginResponse response);
    }

    public interface RegistrationResponseListener {
        void onResponse(NetworkProtocol.RegisterResponse response);
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
            interpolated.inventoryItemNames = newest.inventoryItemNames;

            return interpolated;
        }

        private float lerp(float a, float b, float t) {
            return a + (b - a) * t;
        }
    }// Update GameClient's update method
}

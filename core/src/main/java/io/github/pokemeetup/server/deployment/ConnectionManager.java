package io.github.pokemeetup.server.deployment;

import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.multiplayer.server.config.ServerConnectionConfig;
import io.github.pokemeetup.utils.GameLogger;

public class ConnectionManager {
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 2000;
    private static final int CONNECTION_TIMEOUT = 5000;

    private final GameClient gameClient;
    private final Object connectionLock = new Object();
    private volatile boolean isConnecting = false;
    private int retryCount = 0;

    public boolean isConnecting() {
        return isConnecting;
    }

    private ConnectionState currentState = ConnectionState.DISCONNECTED;

    public ConnectionManager(GameClient gameClient) {
        this.gameClient = gameClient;
    }

    public boolean connect(ServerConnectionConfig config) {
        synchronized (connectionLock) {
            if (isConnecting) {
                GameLogger.info("Connection attempt already in progress");
                return false;
            }

            isConnecting = true;
            retryCount = 0;

            try {
                return attemptConnection(config);
            } finally {
                isConnecting = false;
            }
        }
    } public boolean isConnected() {
        synchronized (connectionLock) {
            return currentState == ConnectionState.CONNECTED &&
                gameClient.getClient() != null &&
                gameClient.getClient().isConnected();
        }
    }

    public ConnectionState getCurrentState() {
        return currentState;
    }



    private boolean attemptConnection(ServerConnectionConfig config) {
        while (retryCount < MAX_RETRIES) {
            try {
                GameLogger.info("Connection attempt " + (retryCount + 1) + " of " + MAX_RETRIES);

                // Clean up any existing connection
                if (gameClient.getClient() != null) {
                    gameClient.getClient().close();
                }

                // Initialize new client
                Client client = new Client(16384, 2048);
                NetworkProtocol.registerClasses(client.getKryo());

                // Add connection listener
                client.addListener(new ConnectionListener());

                // Start client and attempt connection
                client.start();
                client.connect(CONNECTION_TIMEOUT, config.getServerIP(),
                    config.getTcpPort(), config.getUdpPort());

                // Wait for connection confirmation
                if (waitForConnection()) {
                    currentState = ConnectionState.CONNECTED;
                    GameLogger.info("Successfully connected to server");
                    return true;
                }

            } catch (Exception e) {
                GameLogger.error("Connection attempt failed: " + e.getMessage());
                retryCount++;

                if (retryCount < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
            }
        }

        GameLogger.error("Failed to connect after " + MAX_RETRIES + " attempts");
        return false;
    }

    private boolean waitForConnection() {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < CONNECTION_TIMEOUT) {
            if (currentState == ConnectionState.CONNECTED) {
                return true;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private class ConnectionListener extends Listener {

        public void connected(Connection connection) {
            GameLogger.info("TCP connection established");
            // Send initial connection request
            NetworkProtocol.ConnectionRequest request = new NetworkProtocol.ConnectionRequest();
            request.timestamp = System.currentTimeMillis();
            connection.sendTCP(request);
        }


        public void received(Connection connection, Object object) {
            if (object instanceof NetworkProtocol.ConnectionResponse) {
                NetworkProtocol.ConnectionResponse response =
                    (NetworkProtocol.ConnectionResponse) object;
                if (response.success) {
                    currentState = ConnectionState.CONNECTED;
                } else {
                    GameLogger.error("Connection rejected: " + response.message);
                    currentState = ConnectionState.DISCONNECTED;
                }
            }
        }

        public void disconnected(Connection connection) {
            currentState = ConnectionState.DISCONNECTED;
            GameLogger.info("Disconnected from server");
        }
    }
}


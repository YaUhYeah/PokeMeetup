package io.github.pokemeetup.multiplayer.server;

import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import io.github.pokemeetup.managers.DatabaseManager;
import io.github.pokemeetup.managers.Network;
import io.github.pokemeetup.multiplayer.PlayerConnection;
import io.github.pokemeetup.system.PlayerData;
import io.github.pokemeetup.system.gameplay.overworld.World;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GameServer {
    private boolean isRunning = false;
    private final DatabaseManager dbManager;
    private Map<Connection, String> connectionToUsername = new ConcurrentHashMap<>();
    private Server server;
    // Map to store connected players and their states
    private Map<String, Network.PlayerUpdate> players = new ConcurrentHashMap<>();

    public GameServer() throws IOException {
        server = new Server() {
            @Override
            protected Connection newConnection() {
                return new PlayerConnection();
            }
        };
        Network.registerClasses(server.getKryo());
        server.bind(Network.PORT, Network.UDP_PORT);
        dbManager = new DatabaseManager();
        server.start();
        isRunning = true;

        server.addListener(new Listener() {
            @Override
            public void received(Connection c, Object object) {
                try {
                    // Ignore KeepAlive messages
                    if (object instanceof com.esotericsoftware.kryonet.FrameworkMessage.KeepAlive) {
                        return;
                    }

                    System.out.println("Server received an object: " + object.getClass().getName());

                    if (object instanceof Network.LoginRequest) {
                        System.out.println("Received LoginRequest from client.");
                        handleLogin(c, (Network.LoginRequest) object);
                    } else if (object instanceof Network.RegisterRequest) {
                        System.out.println("Received RegisterRequest from client.");
                        handleRegistration(c, (Network.RegisterRequest) object);
                    } else if (object instanceof Network.PlayerUpdate) {
                        System.out.println("Received PlayerUpdate from client.");
                        handlePlayerUpdate((Network.PlayerUpdate) object);
                    } else if (object instanceof Network.InventoryUpdate) {
                        System.out.println("Received InventoryUpdate from client.");
                        handleInventoryUpdate(c, (Network.InventoryUpdate) object);
                    } else {
                        System.out.println("Received unknown object: " + object.getClass().getName());
                    }
                } catch (Exception e) {
                    System.err.println("Exception in received method:");
                    e.printStackTrace();
                }
            }

            @Override
            public void disconnected(Connection c) {
                String username = connectionToUsername.get(c);
                if (username != null) {
                    connectionToUsername.remove(c);
                    players.remove(username);
                    System.out.println("Player disconnected: " + username);
                    broadcastPlayerPositions();
                } else {
                    System.out.println("Disconnected connection with no username: " + c.getRemoteAddressTCP());
                }
            }
        });
    }
    public void shutdown() {
        isRunning = false;

        // Save all online players' states
        for (Map.Entry<String, Network.PlayerUpdate> entry : players.entrySet()) {
            String username = entry.getKey();
            Network.PlayerUpdate playerData = entry.getValue();
            dbManager.updatePlayerCoordinates(username, (int)playerData.x, (int)playerData.y);
        }

        // Close all connections gracefully
        for (Connection connection : server.getConnections()) {
            connection.close();
        }

        // Shutdown the server
        try {
            server.stop();
            server.close();
        } catch (Exception e) {
            System.err.println("Error during server shutdown: " + e.getMessage());
        }

        // Close database connection
        if (dbManager != null) {
            dbManager.closeConnection();
        }

        System.out.println("Server shutdown complete");
    }
    private void handleLogin(Connection c, Network.LoginRequest request) {
        System.out.println("Handling login for username: " + request.username);
        String username = request.username;
        boolean success = dbManager.authenticatePlayer(username, request.password);
        Network.LoginResponse response = new Network.LoginResponse();
        response.success = success;
        response.isRegistrationResponse = false;

        if (success) {
            // Store the username in the connection mapping
            connectionToUsername.put(c, username);
            System.out.println("Stored username in connectionToUsername map for connection ID: " + c.getID());

            // Initialize PlayerData
            PlayerData playerData = new PlayerData(username);
            players.put(username, new Network.PlayerUpdate()); // Initialize PlayerUpdate

            int[] coordinates = dbManager.getPlayerCoordinates(username);
            response.x = coordinates[0];
            response.y = coordinates[1];
            response.message = "Login successful!";
            response.username = username; // Set the username in the response

            ((PlayerConnection) c).username = username;

            System.out.println("Player logged in: " + username);
        } else {
            response.message = "Invalid username or password.";
            System.out.println("Failed login attempt for username: " + username);
        }
        c.sendTCP(response);
    }

    private void handleInventoryUpdate(Connection connection, Network.InventoryUpdate inventoryUpdate) {
        String username = inventoryUpdate.username;

        // Update the server's record of the player's inventory
        Network.PlayerUpdate playerUpdate = players.get(username);
        if (playerUpdate != null) {
            playerUpdate.inventoryItemNames = inventoryUpdate.itemNames;
            System.out.println("Updated inventory for player: " + username);
        } else {
            // Create a new PlayerUpdate instance if it doesn't exist
            playerUpdate = new Network.PlayerUpdate();
            playerUpdate.username = username;
            playerUpdate.inventoryItemNames = inventoryUpdate.itemNames;
            players.put(username, playerUpdate);
            System.out.println("Added inventory for new player: " + username);
        }

        // Broadcast the inventory update to all other clients
        for (Connection conn : server.getConnections()) {
            if (conn != connection) {
                conn.sendUDP(inventoryUpdate);
            }
        }
    }
    public boolean isRunning() {
        return isRunning;
    }
    // Password validation method on server-side
    private String validatePassword(String password) {
        if (password.length() < 8) {
            return "Password must be at least 8 characters long.";
        }
        if (!password.matches(".*[A-Z].*")) {
            return "Password must contain at least one uppercase letter.";
        }
        if (!password.matches(".*[a-z].*")) {
            return "Password must contain at least one lowercase letter.";
        }
        if (!password.matches(".*\\d.*")) {
            return "Password must contain at least one digit.";
        }
        if (!password.matches(".*[!@#$%^&*()].*")) {
            return "Password must contain at least one special character.";
        }
        return null; // Password is valid
    }

    private void handleRegistration(Connection connection, Network.RegisterRequest request) {
        if (request.username == null || request.username.trim().isEmpty()) {
            Network.LoginResponse response = new Network.LoginResponse();
            response.success = false;
            response.message = "Username cannot be empty";
            response.isRegistrationResponse = true;
            connection.sendTCP(response);
            return;
        }

        String username = request.username.trim();
        String password = request.password;
        String validationError = validatePassword(password);

        Network.LoginResponse response = new Network.LoginResponse();
        response.isRegistrationResponse = true;
        response.username = username; // Ensure username is set in response

        if (validationError != null) {
            response.success = false;
            response.message = validationError;
            System.out.println("Registration failed for username '" + username + "': " + validationError);
        } else {
            boolean success = dbManager.registerPlayer(username, password);
            response.success = success;
            if (success) {
                response.message = "Registration successful!";
                response.username = username;
                response.x = 800; // Default spawn position
                response.y = 800;
                System.out.println("Registration successful for username: " + username);
            } else {
                response.message = "Username already exists or registration failed.";
                System.out.println("Registration failed for username: " + username);
            }
        }
        connection.sendTCP(response);
    }

    private void handlePlayerUpdate(Network.PlayerUpdate update) {
        if (update.username == null) {
            System.err.println("Received PlayerUpdate with null username");
            return;
        }

        // Store raw coordinates without scaling
        int x = (int) update.x;
        int y = (int) update.y;

        System.out.println("Storing position for " + update.username + ": (" + x + ", " + y + ")");

        players.put(update.username, update);
        dbManager.updatePlayerCoordinates(update.username, x, y);
        broadcastPlayerPositions();
    }

    private void broadcastPlayerPositions() {
        Network.PlayerPosition playerPosition = new Network.PlayerPosition();
        playerPosition.players.putAll(players);
        System.out.println("Broadcasting Player Positions: " + playerPosition.players.keySet());

        server.sendToAllTCP(playerPosition); // Using TCP for reliable delivery
    }
}

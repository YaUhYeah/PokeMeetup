package io.github.pokemeetup.multiplayer;

import io.github.pokemeetup.managers.DatabaseManager;
import io.github.pokemeetup.multiplayer.server.PlayerEvents;
import io.github.pokemeetup.multiplayer.server.events.EventManager;
import io.github.pokemeetup.multiplayer.server.storage.StorageSystem;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.PasswordUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerManager {
    private final Map<String, ServerPlayer> onlinePlayers = new ConcurrentHashMap<>();
    private final DatabaseManager databaseManager;
    private final StorageSystem storage;
    private final EventManager eventManager;

    public PlayerManager(DatabaseManager databaseManager, StorageSystem storage, EventManager eventManager) {
        this.databaseManager = databaseManager;
        this.storage = storage;
        this.eventManager = eventManager;
        GameLogger.info("PlayerManager initialized with DatabaseManager");
    }

    public ServerPlayer loginPlayer(String username, String password, String sessionId) {
        try {
            // Use DatabaseManager for authentication
            if (databaseManager.authenticatePlayer(username, password)) {
                // Load player coordinates from database
                int[] coordinates = databaseManager.getPlayerCoordinates(username);

                // Create new player instance
                ServerPlayer player = new ServerPlayer(username, sessionId,coordinates[0], coordinates[1]);
                player.updatePosition(coordinates[0], coordinates[1],"down", false);

                // Add to online players
                onlinePlayers.put(username, player);
                GameLogger.info("Player logged in successfully: " + username);
                return player;
            } else {
                GameLogger.info("Login failed for username: " + username);
                return null;
            }
        } catch (Exception e) {
            GameLogger.info("Error during login: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public boolean registerPlayer(String username, String password) {
        try {
            // Use DatabaseManager for registration
            boolean registered = databaseManager.registerPlayer(username, password);
            if (registered) {
                GameLogger.info("Player registered successfully: " + username);
            } else {
                GameLogger.info("Registration failed for username: " + username);
            }
            return registered;
        } catch (Exception e) {
            GameLogger.info("Error registering player: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public void logoutPlayer(String username) {
        ServerPlayer player = onlinePlayers.remove(username);
        if (player != null) {
            // Save final position to database
            databaseManager.updatePlayerCoordinates(
                username,
                (int) player.getPosition().x,
                (int) player.getPosition().y
            );
//            GameLogger.info(STR."Player logged out and position saved: \{username}");
        }
    }

    public ServerPlayer getPlayer(String username) {
        return onlinePlayers.get(username);
    }

    public Collection<ServerPlayer> getOnlinePlayers() {
        return onlinePlayers.values();
    }

    public String getUsernameBySessionId(String sessionId) {
        for (Map.Entry<String, ServerPlayer> entry : onlinePlayers.entrySet()) {
            if (entry.getValue().getSessionId().equals(sessionId)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public void dispose() {
        // Save all online players' positions
        for (Map.Entry<String, ServerPlayer> entry : onlinePlayers.entrySet()) {
            ServerPlayer player = entry.getValue();
            databaseManager.updatePlayerCoordinates(
                entry.getKey(),
                (int) player.getPosition().x,
                (int) player.getPosition().y
            );
        }
        onlinePlayers.clear();

        // Close database connection
        databaseManager.closeConnection();
        GameLogger.info("PlayerManager disposed");
    }
}

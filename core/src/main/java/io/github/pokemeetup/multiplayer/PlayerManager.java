package io.github.pokemeetup.multiplayer;

import io.github.pokemeetup.managers.DatabaseManager;
import io.github.pokemeetup.multiplayer.server.PlayerEvents;
import io.github.pokemeetup.multiplayer.server.events.EventManager;
import io.github.pokemeetup.multiplayer.server.storage.StorageSystem;
import io.github.pokemeetup.system.data.PlayerData;
import io.github.pokemeetup.utils.GameLogger;

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

    public ServerPlayer createOrLoadPlayer(String username) {
        try {
            int[] coordinates = databaseManager.getPlayerCoordinates(username);
            if (coordinates == null) {
                coordinates = new int[]{0, 0};
            }

            ServerPlayer player = new ServerPlayer(username, "", coordinates[0], coordinates[1]);

            try {
                PlayerData savedData = storage.loadPlayerData(username);
                if (savedData != null) {
                    player.updatePosition(
                        savedData.getX(),
                        savedData.getY(),
                        savedData.getDirection(),
                        false
                    );
                }
            } catch (Exception e) {
                GameLogger.error("Error loading player data for " + username + ": " + e.getMessage());
            }

            onlinePlayers.put(username, player);
            GameLogger.info("Player loaded/created successfully: " + username);

            eventManager.fireEvent(new PlayerEvents.PlayerLoginEvent(player));

            return player;

        } catch (Exception e) {
            GameLogger.error("Error creating/loading player: " + e.getMessage());
            return null;
        }
    }

    public ServerPlayer getPlayer(String username) {
        return onlinePlayers.get(username);
    }

    public Collection<ServerPlayer> getOnlinePlayers() {
        return onlinePlayers.values();
    }


    public void dispose() {
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

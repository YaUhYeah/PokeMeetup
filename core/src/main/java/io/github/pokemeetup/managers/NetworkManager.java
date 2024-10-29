package io.github.pokemeetup.managers;

import com.esotericsoftware.kryonet.Server;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import io.github.pokemeetup.multiplayer.ServerPlayer;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.multiplayer.server.GameServer;
import io.github.pokemeetup.multiplayer.server.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class NetworkManager {
    private static final int UPDATE_RATE = 50; // 20 updates per second
    private final Server server;
    private final GameServer gameServer;
    private final ConcurrentHashMap<Integer, String> connectionToPlayer;
    private final ScheduledExecutorService updateExecutor;

    public NetworkManager(GameServer gameServer, int port) {
        this.gameServer = gameServer;
        this.server = new Server(16384, 2048);
        this.connectionToPlayer = new ConcurrentHashMap<>();
        this.updateExecutor = Executors.newSingleThreadScheduledExecutor();

        setupServer(port);
    }

    private void setupServer(int port) {
        // Register network classes
        NetworkProtocol.registerClasses(server.getKryo());

        // Add listener for network events
        server.addListener(new Listener() {
            @Override
            public void connected(Connection connection) {
                handlePlayerConnect(connection);
            }

            @Override
            public void disconnected(Connection connection) {
                handlePlayerDisconnect(connection);
            }

            @Override
            public void received(Connection connection, Object object) {
                handleNetworkMessage(connection, object);
            }
        });

        // Start server
        try {
            server.start();
            server.bind(port, port + 1);

            // Start world state updates
            startWorldStateUpdates();

            System.out.println("Network manager started on port " + port);
        } catch (Exception e) {
            System.err.println("Failed to start network manager: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private void startWorldStateUpdates() {
        updateExecutor.scheduleAtFixedRate(() -> {
            try {
                broadcastWorldState();
            } catch (Exception e) {
                System.err.println("Error in world state update: " + e.getMessage());
            }
        }, 0, UPDATE_RATE, TimeUnit.MILLISECONDS);
    }

    private void broadcastWorldState() {
        NetworkProtocol.WorldState worldState = new NetworkProtocol.WorldState();
        worldState.timestamp = System.currentTimeMillis();
        worldState.entities = new ArrayList<>();
        worldState.players = new ArrayList<>();

        // Add entities
        for (Entity entity : gameServer.getWorldManager().getCurrentWorld().getEntities()) {
            NetworkProtocol.EntityUpdate entityUpdate = new NetworkProtocol.EntityUpdate();
            entityUpdate.entityId = entity.getId();
            entityUpdate.x = entity.getPosition().x;
            entityUpdate.y = entity.getPosition().y;
            entityUpdate.velocity = entity.getVelocity();
            entityUpdate.entityType = entity.getType().toString();
            worldState.entities.add(entityUpdate);
        }

        // Add players
        for (ServerPlayer player : gameServer.getPlayerManager().getOnlinePlayers()) {
            NetworkProtocol.PlayerState playerState = new NetworkProtocol.PlayerState();
            playerState.username = player.getUsername();
            playerState.x = player.getPosition().x;
            playerState.y = player.getPosition().y;
            playerState.direction = player.getDirection();
            playerState.isMoving = player.isMoving();
            playerState.inventory = player.getData().getInventoryItems();
            worldState.players.add(playerState);
        }

        // Broadcast to all clients
        server.sendToAllUDP(worldState);
    }

    private void handlePlayerConnect(Connection connection) {
        System.out.println("Client connected: " + connection.getID());
        // Auth will be handled when login request is received
    }

    private void handlePlayerDisconnect(Connection connection) {
        String username = connectionToPlayer.remove(connection.getID());
        if (username != null) {
            gameServer.getPlayerManager().logoutPlayer(username);
//            System.out.println(STR."Player disconnected: \{username}");
        }
    }

    private void handleNetworkMessage(Connection connection, Object message) {
        try {
            if (message instanceof NetworkProtocol.PlayerState) {
                handlePlayerUpdate(connection, (NetworkProtocol.PlayerState) message);
            } else if (message instanceof NetworkProtocol.LoginRequest) {
                handleLoginRequest(connection, (NetworkProtocol.LoginRequest) message);
            }
            // Handle other message types
        } catch (Exception e) {
            System.err.println("Error handling network message: " + e.getMessage());
        }
    }

    private void handlePlayerUpdate(Connection connection, NetworkProtocol.PlayerState update) {
        String username = connectionToPlayer.get(connection.getID());
        if (username != null && username.equals(update.username)) {
            ServerPlayer player = gameServer.getPlayerManager().getPlayer(username);
            if (player != null) {
                player.updatePosition(update.x, update.y, update.direction, update.isMoving);
            }
        }
    }

    private void handleLoginRequest(Connection connection, NetworkProtocol.LoginRequest request) {
        try {
            ServerPlayer player = gameServer.getPlayerManager().loginPlayer(request.username, request.password,String.valueOf(connection.getID()));
            if (player != null) {
                connectionToPlayer.put(connection.getID(), player.getUsername());

                NetworkProtocol.LoginResponse response = new NetworkProtocol.LoginResponse();
                response.success = true;
                response.username = player.getUsername();
                response.x = (int) player.getPosition().x;
                response.y = (int) player.getPosition().y;

                connection.sendTCP(response);
            }
        } catch (Exception e) {
            NetworkProtocol.LoginResponse response = new NetworkProtocol.LoginResponse();
            response.success = false;
            response.message = e.getMessage();
            connection.sendTCP(response);
        }
    }

    public void shutdown() {
        updateExecutor.shutdown();
        try {
            if (!updateExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                updateExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            updateExecutor.shutdownNow();
        }

        server.stop();
    }
}

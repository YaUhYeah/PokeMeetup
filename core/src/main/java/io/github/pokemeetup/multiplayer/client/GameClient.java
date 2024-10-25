package io.github.pokemeetup.multiplayer.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import io.github.pokemeetup.managers.Network;
import io.github.pokemeetup.system.PlayerData;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.multiworld.WorldData;
import io.github.pokemeetup.system.inventory.Inventory;
import io.github.pokemeetup.system.inventory.Item;
import io.github.pokemeetup.system.inventory.ItemManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GameClient {
    private final boolean isSinglePlayer;
    private PlayerData lastKnownState;  // Add this to track the latest state


    private volatile boolean isDisposing = false;
    private Client client;
    private long worldSeed;
    private LoginResponseListener loginResponseListener;
    private RegistrationResponseListener registrationResponseListener;
    private Map<String, Network.PlayerUpdate> otherPlayers = new ConcurrentHashMap<>();
    private String localUsername;
    private String currentWorldName; // Track the current world
    private boolean isConnected = false;

    public GameClient(boolean isSinglePlayer) {
        this.isSinglePlayer = isSinglePlayer;
        if (!isSinglePlayer) {
            initializeNetworking();
        }
    }

    private void initializeNetworking() {
        try {
            client = new Client(8192, 2048);
            Network.registerClasses(client.getKryo());
            client.start();
            setupNetworkListeners();
            client.connect(5000, "localhost", Network.PORT, Network.UDP_PORT);
            System.out.println("Client attempting connection to server...");
        } catch (IOException e) {
            System.err.println("Failed to initialize networking:");
            e.printStackTrace();
        }
    }

    private float getCurrentY() {
        Network.PlayerUpdate playerState = otherPlayers.get(localUsername);
        return playerState != null ? playerState.y : 0;
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

    public void savePlayerState(PlayerData playerData) {
        if (playerData == null) {
            System.err.println("Cannot save null PlayerData");
            return;
        }

        System.out.println("Attempting to save player state for: " + localUsername);

        if (localUsername == null || localUsername.trim().isEmpty()) {
            System.err.println("Cannot save - username not set in GameClient");
            Thread.dumpStack(); // Print stack trace for debugging
            return;
        }

        // Ensure PlayerData has username set
        playerData.setUsername(localUsername);

        if (isSinglePlayer()) {
            saveLocalPlayerState(playerData);
        } else {
            if (isConnected && client != null) {
                sendPlayerUpdateToServer(playerData);
            } else {
                System.out.println("Not connected to server. Saving player state locally for: " + localUsername);
                saveLocalPlayerState(playerData);
            }
        }
    }

    private void saveLocalPlayerState(PlayerData playerData) {
        try {
            if (localUsername == null || localUsername.trim().isEmpty()) {
                System.err.println("Cannot save locally - username is null or empty");
                return;
            }

            // Use last known state if current state is empty
            PlayerData stateToSave = playerData;
            if ((playerData.getInventory() == null || playerData.getInventory().isEmpty())
                && lastKnownState != null && !lastKnownState.getInventory().isEmpty()) {
                System.out.println("Using last known state as current state is empty");
                stateToSave = lastKnownState;
            }

            // Create complete save state
            PlayerData saveState = new PlayerData(localUsername);
            saveState.setPosition(stateToSave.getX(), stateToSave.getY());
            saveState.setDirection(stateToSave.getDirection());
            saveState.setMoving(stateToSave.isMoving());
            saveState.setWantsToRun(stateToSave.isWantsToRun());
            saveState.setInventory(stateToSave.getInventory());

            // Log state before saving
            System.out.println("Preparing to save state - Inventory items: " +
                (saveState.getInventory() != null ? saveState.getInventory() : "null"));

            Json json = new Json();
            String filename = "save/" + (isSinglePlayer() ? "singleplayer_" : "offlinemultiplayer_")
                + localUsername + ".json";
            FileHandle file = Gdx.files.local(filename);

            String jsonData = json.prettyPrint(saveState);
            file.writeString(jsonData, false);
            System.out.println("Successfully saved complete player state for: " + localUsername);
            System.out.println("Saved data: " + jsonData);
        } catch (Exception e) {
            System.err.println("Failed to save player state locally: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void updateLastKnownState(PlayerData currentState) {
        if (currentState == null || localUsername == null) {
            return;
        }

        // Create deep copy
        this.lastKnownState = new PlayerData(localUsername);
        this.lastKnownState.setPosition(currentState.getX(), currentState.getY());
        this.lastKnownState.setDirection(currentState.getDirection());
        this.lastKnownState.setMoving(currentState.isMoving());
        this.lastKnownState.setWantsToRun(currentState.isWantsToRun());

        if (currentState.getInventory() != null && !currentState.getInventory().isEmpty()) {
            this.lastKnownState.setInventory(new ArrayList<>(currentState.getInventory()));
            System.out.println("Stored last known state with inventory items: " + currentState.getInventory());
        }
    }


    private void sendPlayerUpdateToServer(PlayerData playerData) {
        try {
            Network.PlayerUpdate update = new Network.PlayerUpdate();
            update.username = localUsername;
            update.x = playerData.getX();
            update.y = playerData.getY();
            update.direction = playerData.convertDirectionIntToString(playerData.getDirection());
            update.isMoving = playerData.isMoving();
            update.wantsToRun = playerData.isWantsToRun();
            update.inventoryItemNames = playerData.getInventory();
            client.sendTCP(update);
            System.out.println("Sent player state update to server for: " + localUsername);
        } catch (Exception e) {
            System.err.println("Failed to send player state to server: " + e.getMessage());
            // Fall back to local save
            saveLocalPlayerState(playerData);
        }
    }

    private String getCurrentDirection() {
        Network.PlayerUpdate playerState = otherPlayers.get(localUsername);
        return playerState != null ? playerState.direction : "down";
    }

    private void handleReceivedObject(Object object) {
        try {
            if (object instanceof com.esotericsoftware.kryonet.FrameworkMessage.KeepAlive) {
                return;
            }

            if (object instanceof Network.LoginResponse) {
                Network.LoginResponse response = (Network.LoginResponse) object;
                handleLoginResponse(response);
            } else if (object instanceof Network.PlayerPosition) {
                Network.PlayerPosition playerPosition = (Network.PlayerPosition) object;
                handlePlayerPositions(playerPosition);
            } else if (object instanceof Network.PlayerUpdate) {
                Network.PlayerUpdate netUpdate = (Network.PlayerUpdate) object;
                handlePlayerUpdate(netUpdate);
            } else if (object instanceof Network.InventoryUpdate) {
                Network.InventoryUpdate inventoryUpdate = (Network.InventoryUpdate) object;
                handleInventoryUpdate(inventoryUpdate);
            } else {
                System.out.println("Received unknown object: " + object.getClass().getName());
            }
        } catch (Exception e) {
            System.err.println("Exception in handleReceivedObject:");
            e.printStackTrace();
        }
    }

    private void setupNetworkListeners() {
        client.addListener(new Listener() {
            @Override
            public void connected(Connection connection) {
                isConnected = true;
                System.out.println("Connected to server successfully as: " + localUsername);
            }

            @Override
            public void disconnected(Connection connection) {
                System.out.println("Disconnected from server - Username before cleanup: " + localUsername);
                cleanup();
                System.out.println("Cleanup complete - Username maintained: " + localUsername);

                // Try to save state locally on disconnect
                if (localUsername != null) {
                    PlayerData disconnectState = new PlayerData(localUsername);
                    // If you have access to current player state, set it here
                    saveLocalPlayerState(disconnectState);
                }
            }


            @Override
            public void received(Connection connection, Object object) {
                handleReceivedObject(object);
            }
        });
    }

    private void cleanup() {
        if (localUsername != null && lastKnownState != null) {
            System.out.println("Cleanup - Saving final state before cleanup for: " + localUsername);
            System.out.println("Last known state inventory: " +
                (lastKnownState.getInventory() != null ? lastKnownState.getInventory() : "null"));
            saveLocalPlayerState(lastKnownState);
        }
        otherPlayers.clear();
        isConnected = false;
        // Don't clear lastKnownState or localUsername
    }

    public void sendLoginRequest(String username, String password) {
        if (isSinglePlayer) return;
        if (!isConnected || client == null) {
            System.err.println("Cannot send login request - not connected to server");
            return;
        }
        Network.LoginRequest request = new Network.LoginRequest();
        request.username = username;
        request.password = password;
        client.sendTCP(request);
        System.out.println("Sent login request for username: " + username);
    }

    public void sendRegisterRequest(String username, String password) {
        if (isSinglePlayer) return;
        if (!isConnected || client == null) {
            System.err.println("Cannot send register request - not connected to server");
            return;
        }
        Network.RegisterRequest request = new Network.RegisterRequest();
        request.username = username;
        request.password = password;
        client.sendTCP(request);
        System.out.println("Sent register request for username: " + username);
    }

    public void sendPlayerUpdate(Network.PlayerUpdate update) {
        if (isSinglePlayer) return;
        if (!isConnected || client == null) {
            System.err.println("Cannot send player update - not connected to server");
            return;
        }

        try {
            // Update last known state when sending updates
            PlayerData currentState = new PlayerData(localUsername);
            currentState.setPosition(update.x, update.y);
            currentState.setDirection(update.direction);
            currentState.setMoving(update.isMoving);
            currentState.setWantsToRun(update.wantsToRun);
            if (update.inventoryItemNames != null) {
                currentState.setInventory(update.inventoryItemNames);
            }
            updateLastKnownState(currentState);

            client.sendTCP(update);
        } catch (Exception e) {
            System.err.println("Failed to send player update: " + e.getMessage());
        }
    }

    public boolean isSinglePlayer() {
        return isSinglePlayer;
    }

    public void sendInventoryUpdate(String username, List<String> itemNames) {
        if (isSinglePlayer) return;
        if (!isConnected || client == null) {
            return;
        }

        try {
            Network.InventoryUpdate update = new Network.InventoryUpdate();
            update.username = username;
            update.itemNames = new ArrayList<>(itemNames);
            client.sendUDP(update);
            System.out.println("Sent inventory update for: " + username);
        } catch (Exception e) {
            System.err.println("Failed to send inventory update: " + e.getMessage());
        }
    }

    private void handleLoginResponse(Network.LoginResponse response) {
        System.out.println("Received login response: success=" + response.success +
            ", message=" + response.message +
            ", username=" + response.username);
        if (response.success) {
            this.worldSeed = response.worldSeed;
            localUsername = response.username;
            System.out.println("Login successful for: " + localUsername);
        }

        if (loginResponseListener != null) {
            loginResponseListener.onResponse(response);
        }
    }

    public long getWorldSeed() {
        return worldSeed;
    }

    private void handlePlayerPositions(Network.PlayerPosition playerPosition) {
        try {
            for (Map.Entry<String, Network.PlayerUpdate> entry : playerPosition.players.entrySet()) {
                String username = entry.getKey();
                Network.PlayerUpdate netUpdate = entry.getValue();

                // Skip own player update
                if (username != null && !username.equals(localUsername)) {
                    // Validate positions
                    netUpdate.x = Math.min(Math.max(netUpdate.x, 0),
                        World.WORLD_SIZE * World.TILE_SIZE);
                    netUpdate.y = Math.min(Math.max(netUpdate.y, 0),
                        World.WORLD_SIZE * World.TILE_SIZE);

                    otherPlayers.put(username, netUpdate);
                }
            }

            // Remove disconnected players
            otherPlayers.keySet().retainAll(playerPosition.players.keySet());

        } catch (Exception e) {
            System.err.println("Error handling player positions: " + e.getMessage());
        }
    }

    private void handlePlayerUpdate(Network.PlayerUpdate netUpdate) {
        if (netUpdate.username != null && !netUpdate.username.equals(localUsername)) {
            // Validate positions
            netUpdate.x = Math.min(Math.max(netUpdate.x, 0),
                World.WORLD_SIZE * World.TILE_SIZE);
            netUpdate.y = Math.min(Math.max(netUpdate.y, 0),
                World.WORLD_SIZE * World.TILE_SIZE);

            otherPlayers.put(netUpdate.username, netUpdate);
            System.out.println("Updated position for player: " + netUpdate.username +
                " to (" + netUpdate.x + ", " + netUpdate.y + ")");
        }
    }

    private void handleInventoryUpdate(Network.InventoryUpdate inventoryUpdate) {
        if (inventoryUpdate.username == null ||
            inventoryUpdate.username.equals(localUsername)) {
            return;
        }

        Network.PlayerUpdate playerUpdate = otherPlayers.get(inventoryUpdate.username);
        if (playerUpdate != null) {
            playerUpdate.inventoryItemNames = inventoryUpdate.itemNames;
            System.out.println("Updated inventory for: " + inventoryUpdate.username);
        } else {
            playerUpdate = new Network.PlayerUpdate();
            playerUpdate.username = inventoryUpdate.username;
            playerUpdate.inventoryItemNames = inventoryUpdate.itemNames;
            otherPlayers.put(inventoryUpdate.username, playerUpdate);
            System.out.println("Created new player state for: " + inventoryUpdate.username);
        }
    }

    public void setLoginResponseListener(LoginResponseListener listener) {
        this.loginResponseListener = listener;
    }

    public void setRegistrationResponseListener(RegistrationResponseListener listener) {
        this.registrationResponseListener = listener;
    }

    public Map<String, Network.PlayerUpdate> getOtherPlayers() {
        return otherPlayers;
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
        if (isDisposing) return;

        try {
            isDisposing = true;
            if (!isSinglePlayer && client != null) {
                if (localUsername != null && lastKnownState != null) {
                    System.out.println("Disposing - Final save for user: " + localUsername);
                    saveLocalPlayerState(lastKnownState);
                }

                if (isConnected) {
                    client.close();
                }
                client.stop();
                client = null;
                System.out.println("GameClient disposed successfully. Username maintained: " + localUsername);
            }
        } catch (Exception e) {
            System.err.println("Error disposing GameClient: " + e.getMessage());
        } finally {
            isDisposing = false;
        }
    }

    public interface LoginResponseListener {
        void onResponse(Network.LoginResponse response);
    }

    public interface RegistrationResponseListener {
        void onResponse(Network.LoginResponse response);
    }
}

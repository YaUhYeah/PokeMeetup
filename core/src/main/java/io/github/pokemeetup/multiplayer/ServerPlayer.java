package io.github.pokemeetup.multiplayer;

import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.system.data.PlayerData;
import io.github.pokemeetup.system.gameplay.inventory.Inventory;
import io.github.pokemeetup.system.data.ItemData;
import io.github.pokemeetup.utils.GameLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class ServerPlayer {
    private final String username;
    private final String sessionId;
    private final Vector2 position;
    private final Inventory inventory;
    private String direction;
    private boolean isMoving;
    private boolean isRunning;
    private final AtomicLong lastSaveTime;
    private final Object inventoryLock = new Object();

    public ServerPlayer(String username, String sessionId, float x, float y) {
        this.username = username;
        this.sessionId = sessionId;
        this.position = new Vector2(x, y);
        this.direction = "down";
        this.isMoving = false;
        this.isRunning = false;
        this.lastSaveTime = new AtomicLong(System.currentTimeMillis());
        this.inventory = new Inventory();
        GameLogger.info("Created new ServerPlayer: " + username + " at (" + x + ", " + y + ")");
    }

    public void updateInventory(NetworkProtocol.InventoryUpdate update) {
        synchronized (inventoryLock) {
            try {
                if (update.inventoryItems != null) {
                    List<ItemData> validatedInventoryItems = validateItems(update.inventoryItems);
                    inventory.setAllItems(validatedInventoryItems);
                }

                GameLogger.info("Updated inventory for " + username);
            } catch (Exception e) {
                GameLogger.error("Failed to update inventory for " + username + ": " + e.getMessage());
            }
        }
    }

    private List<ItemData> validateItems(ItemData[] items) {
        List<ItemData> validatedItems = new ArrayList<>();
        for (ItemData item : items) {
            if (item != null) {
                // Ensure item has valid UUID
                if (item.getUuid() == null) {
                    item.setUuid(UUID.randomUUID());
                }
                // Validate count
                if (item.getCount() <= 0 || item.getCount() > 64) {
                    item.setCount(1);
                }
                validatedItems.add(item);
            } else {
                validatedItems.add(null);
            }
        }
        return validatedItems;
    }

    public PlayerData getData() {
        PlayerData data = new PlayerData(username);
        data.setX(position.x);
        data.setY(position.y);
        data.setDirection(direction);
        data.setMoving(isMoving);
        data.setWantsToRun(isRunning);

        synchronized (inventoryLock) {
            data.setInventoryItems(inventory.getAllItems());
        }

        return data;
    }

    public void updatePosition(float x, float y, String direction, boolean isMoving) {
        float oldX = position.x;
        float oldY = position.y;

        synchronized (position) {
            this.position.x = x;
            this.position.y = y;
            this.direction = direction;
            this.isMoving = isMoving;
        }

        GameLogger.info("Player " + username + " moved from (" + oldX + "," + oldY +
            ") to (" + x + "," + y + ") facing " + direction);
    }

    public Vector2 getPosition() {
        synchronized (position) {
            return new Vector2(position);
        }
    }

    public List<ItemData> getInventoryItems() {
        synchronized (inventoryLock) {
            return inventory.getAllItems();
        }
    }



    public String getUsername() {
        return username;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getDirection() {
        return direction;
    }

    public boolean isMoving() {
        return isMoving;
    }

    public boolean isRunning() {
        return isRunning;
    }

    public long getLastSaveTime() {
        return lastSaveTime.get();
    }

    public void updateLastSaveTime(long time) {
        lastSaveTime.set(time);
        GameLogger.info("Updated save time for " + username);
    }

    public Inventory getInventory() {
        return inventory;
    }
}

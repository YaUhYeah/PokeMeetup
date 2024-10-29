package io.github.pokemeetup.multiplayer;

import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.system.PlayerData;

import java.util.ArrayList;
import java.util.List;

public class ServerPlayer {
    private final String username;
    private final String sessionId;
    private final Vector2 position;
    private String direction;
    private boolean isMoving;
    private boolean isRunning;
    private long lastSaveTime;
    private List<String> inventory;

    public ServerPlayer(String username, String sessionId, float x, float y) {
        this.username = username;
        this.sessionId = sessionId;
        this.position = new Vector2(x, y);
        this.direction = "down";
        this.isMoving = false;
        this.isRunning = false;
        this.lastSaveTime = System.currentTimeMillis();
        this.inventory = new ArrayList<>();
        System.out.println("Created new ServerPlayer: " + username + " at (" + x + ", " + y + ")");
    }

    public void updatePosition(float x, float y, String direction, boolean isMoving) {
        float oldX = position.x;
        float oldY = position.y;

        this.position.x = x;
        this.position.y = y;
        this.direction = direction;
        this.isMoving = isMoving;

        System.out.println("Player " + username + " moved from (" + oldX + "," + oldY +
            ") to (" + x + "," + y + ") facing " + direction);
    }

    public void updateInventory(List<String> items) {
        this.inventory = new ArrayList<>(items);
        System.out.println("Updated inventory for " + username + ": " + items);
    }

    public PlayerData getData() {
        PlayerData data = new PlayerData(username);
        data.updateFromPlayer(this);
        return data;
    }

    public void loadFromData(PlayerData data) {
        if (data == null) {
//            System.out.println(STR."Warning: Null PlayerData for \{username}");
            return;
        }

        position.x = data.getX();
        position.y = data.getY();
        direction = data.getDirection();
        isMoving = data.isMoving();
        isRunning = data.isWantsToRun();
        if (data.getInventoryItems() != null) {
            inventory = new ArrayList<>(data.getInventoryItems());
        }

//        System.out.println(STR."Loaded data for \{username} at position (\{position.x},\{position.y})");
    }

    // Getters remain the same
    public Vector2 getPosition() { return position; }
    public String getUsername() { return username; }
    public String getSessionId() { return sessionId; }
    public String getDirection() { return direction; }
    public boolean isMoving() { return isMoving; }
    public boolean isRunning() { return isRunning; }
    public long getLastSaveTime() { return lastSaveTime; }
    public void updateLastSaveTime(long time) {
        this.lastSaveTime = time;
        System.out.println("Updated save time for " + username);
    }
    public List<String> getInventory() { return new ArrayList<>(inventory); }
}

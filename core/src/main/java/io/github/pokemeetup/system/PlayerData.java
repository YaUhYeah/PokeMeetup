package io.github.pokemeetup.system;

import io.github.pokemeetup.multiplayer.ServerPlayer;
import io.github.pokemeetup.system.gameplay.inventory.Inventory;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.InventoryConverter;

import java.util.ArrayList;
import java.util.List;

public class PlayerData {
    private String username;
    private float x, y;
    private String direction;
    private boolean isMoving;
    private boolean wantsToRun;
    private List<String> inventoryItems; // List of strings in the format "itemName:count"
    private boolean isDirty;

    public PlayerData(String username) {
        this.username = username;
        this.inventoryItems = new ArrayList<>();
        this.x = 0;
        this.y = 0;
        this.direction = "down";
        this.isMoving = false;
        this.wantsToRun = false;
        this.isDirty = false;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setX(float x) {
        this.x = x;
    }

    public void setY(float y) {
        this.y = y;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public void setMoving(boolean moving) {
        isMoving = moving;
    }

    public void setWantsToRun(boolean wantsToRun) {
        this.wantsToRun = wantsToRun;
    }

    public void setDirty(boolean dirty) {
        isDirty = dirty;
    }    public void updateFromPlayer(ServerPlayer serverPlayer) {
        this.x = serverPlayer.getPosition().x;
        this.y = serverPlayer.getPosition().y;
        this.direction = serverPlayer.getDirection();
        this.isMoving = serverPlayer.isMoving();
        this.wantsToRun = serverPlayer.isRunning();

        // Copy inventory items directly from ServerPlayer
        this.inventoryItems = new ArrayList<>(serverPlayer.getInventory());

        this.isDirty = true;
    }

    // Update from current player state
    public void updateFromPlayer(Player player) {
        this.x = player.getX();
        this.y = player.getY();
        this.direction = player.getDirection();
        this.isMoving = player.isMoving();
        this.wantsToRun = player.isRunning();

        // Serialize inventory items
        this.inventoryItems = InventoryConverter.toPlayerDataFormat(player.getInventory());

        this.isDirty = true;
    }

    // Apply data to the player
    public void applyToPlayer(Player player) {
        if (player == null) return;

        // Set position and movement
        player.setX(this.x);
        player.setY(this.y);
        player.setDirection(this.direction);
        player.setMoving(this.isMoving);
        player.setRunning(this.wantsToRun);

        // Deserialize inventory items
        Inventory inventory = new Inventory();
        InventoryConverter.fromPlayerDataFormat(this.inventoryItems, inventory);
        player.setInventory(inventory);

        GameLogger.info("Applied player data to player:");
        GameLogger.info("Position: " + x + "," + y);
        GameLogger.info("Direction: " + direction);
        GameLogger.info("Inventory items: " + inventoryItems);
    }

    // Getters and setters
    public String getUsername() {
        return username;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public String getDirection() {
        return direction;
    }

    public boolean isMoving() {
        return isMoving;
    }

    public boolean isWantsToRun() {
        return wantsToRun;
    }

    public List<String> getInventoryItems() {
        return inventoryItems;
    }

    public void setInventoryItems(List<String> inventoryItems) {
        this.inventoryItems = inventoryItems;
    }

    public boolean isDirty() {
        return isDirty;
    }

    public void clearDirty() {
        isDirty = false;
    }

    public PlayerData copy() {
        PlayerData copy = new PlayerData(this.username);
        copy.x = this.x;
        copy.y = this.y;
        copy.direction = this.direction;
        copy.isMoving = this.isMoving;
        copy.wantsToRun = this.wantsToRun;
        copy.inventoryItems = this.inventoryItems;
        copy.isDirty = this.isDirty;
        return copy;
    }
}

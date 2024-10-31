package io.github.pokemeetup.system;

import io.github.pokemeetup.multiplayer.ServerPlayer;
import io.github.pokemeetup.system.gameplay.inventory.Inventory;
import io.github.pokemeetup.system.gameplay.inventory.Item;
import io.github.pokemeetup.system.gameplay.inventory.ItemManager;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.InventoryConverter;

import java.util.ArrayList;
import java.util.List;

public class PlayerData {
    private String username;
    private float x = 0;
    private float y = 0;
    private String direction = "down"; // Default to "down"
    private boolean isMoving = false;
    private boolean wantsToRun = false;
    private List<String> inventoryItems = new ArrayList<>();
    private List<String> hotbarItems = new ArrayList<>();
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

        // Convert and store inventory items
        this.inventoryItems = InventoryConverter.toPlayerDataFormat(player.getInventory().getItems());
        GameLogger.info("Converted Inventory Items: " + this.inventoryItems);

        // Convert and store hotbar items
        this.hotbarItems = InventoryConverter.toPlayerDataFormat(player.getInventory().getHotbarItems());
        GameLogger.info("Converted Hotbar Items: " + this.hotbarItems);

        this.isDirty = true;
    }


    // Apply data to the player
    public void applyToPlayer(Player player) {
        player.setX((int) this.x);
        player.setY((int) this.y);
        player.setDirection(this.direction);
        player.setMoving(this.isMoving);
        player.setRunning(this.wantsToRun);

        InventoryConverter.applyInventoryDataToPlayer(this, player);
        GameLogger.info("Applied inventory and hotbar to player.");
    }

    public List<String> getHotbarItems() {
        return hotbarItems;
    }

    public void setHotbarItems(List<String> hotbarItems) {
        this.hotbarItems = hotbarItems;
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
    public PlayerData() {
        this.username = "";
        this.inventoryItems = new ArrayList<>();
    }
    public PlayerData copy() {
        PlayerData copy = new PlayerData(this.username);
        copy.x = this.x;
        copy.y = this.y;
        copy.direction = this.direction;
        copy.isMoving = this.isMoving;
        copy.wantsToRun = this.wantsToRun;
        copy.inventoryItems = this.inventoryItems;
        copy.hotbarItems = this.hotbarItems;
        copy.isDirty = this.isDirty;
        return copy;
    }
}

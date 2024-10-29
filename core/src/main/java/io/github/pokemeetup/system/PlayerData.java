package io.github.pokemeetup.system;

import io.github.pokemeetup.multiplayer.ServerPlayer;
import io.github.pokemeetup.system.gameplay.inventory.Inventory;
import io.github.pokemeetup.system.gameplay.inventory.Item;
import io.github.pokemeetup.system.gameplay.inventory.ItemManager;

import java.util.ArrayList;
import java.util.List;

public class PlayerData {
    private String username;
    private String hashedPassword; // Store hashed password
    private float x, y;
    private String direction;
    private boolean isMoving;
    private boolean wantsToRun;
    private List<String> inventoryItemNames;

    // Constructor
    public PlayerData(String username) {
        this.username = username;
        this.inventoryItemNames = new ArrayList<>();
        // Default values
        this.x = 0;
        this.y = 0;
        this.direction = "down";
        this.isMoving = false;
        this.wantsToRun = false;
    }
    public PlayerData() {
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

    // Updates the full state from a Player object
    public void updateFromPlayer(Player player) {
        if (player == null) return;

        // Update position and movement
        this.x = player.getX();
        this.y = player.getY();
        this.direction = player.getDirection();
        this.isMoving = player.isMoving();
        this.wantsToRun = player.isRunning();
        this.inventoryItemNames = new ArrayList<>();

        // Update inventory
        for (Item item : player.getInventory().getItems()) {
            if (item != null) {
                // Save in format: "itemName:count"
                this.inventoryItemNames.add(item.getName() + ":" + item.getCount());
            }
        }

        System.out.println("Updated player data:");
        System.out.println("Position: " + x + "," + y);
        System.out.println("Direction: " + direction);
        System.out.println("Inventory items: " + inventoryItemNames);
    } public void updateFromPlayer(ServerPlayer player) {
        if (player == null) return;

        // Update position and movement
        this.x = player.getPosition().x;
        this.y = player.getPosition().y;
        this.direction = player.getDirection();
        this.isMoving = player.isMoving();
        this.wantsToRun = player.isRunning();

        // Update inventory
        this.inventoryItemNames.clear();
        for (String item : player.getInventory()) {
            if (item != null) {
                // Save in format: "itemName:count"
                this.inventoryItemNames.add(item);
            }
        }

        System.out.println("Updated player data:");
        System.out.println("Position: " + x + "," + y);
        System.out.println("Direction: " + direction);
        System.out.println("Inventory items: " + inventoryItemNames);
    }    public String convertDirectionIntToString(int direction) {
        switch (direction) {
            case 0:
                return "up";
            case 1:
                return "down";
            case 2:
                return "left";
            case 3:
                return "right";
            default:
                return "down"; // default to down
        }
    }
    public String getHashedPassword() {
        return hashedPassword;
    }

    public void setHashedPassword(String hashedPassword) {
        this.hashedPassword = hashedPassword;
    }

    // Creates a new Player object from this data
    public void applyToPlayer(Player player) {
        if (player == null) return;

        // Set position and movement
        player.setX(this.x);
        player.setY(this.y);
        player.setDirection(this.direction);
        player.setMoving(this.isMoving);
        player.setRunning(this.wantsToRun);

        // Set inventory
        Inventory inventory = player.getInventory();

        for (String itemString : this.inventoryItemNames) {
            String[] parts = itemString.split(":");
            if (parts.length == 2) {
                String itemName = parts[0];
                int count = Integer.parseInt(parts[1]);

                Item item = ItemManager.getItem(itemName);
                if (item != null) {
                    item.setCount(count);
                    inventory.addItem(item);
                }
            }
        }player.setInventory(inventory);

        System.out.println("Applied player data to player:");
        System.out.println("Position: " + x + "," + y);
        System.out.println("Direction: " + direction);
        System.out.println("Inventory items: " + inventoryItemNames);
    }

    // Required getters/setters
    public String getUsername() { return username; }
    public float getX() { return x; }
    public float getY() { return y; }
    public String getDirection() { return direction; }
    public boolean isMoving() { return isMoving; }
    public boolean isWantsToRun() { return wantsToRun; }
    public List<String> getInventoryItems() { return inventoryItemNames; }

    // Deep copy method
    public PlayerData copy() {
        PlayerData copy = new PlayerData(this.username);
        copy.x = this.x;
        copy.y = this.y;
        copy.direction = this.direction;
        copy.isMoving = this.isMoving;
        copy.wantsToRun = this.wantsToRun;
        copy.inventoryItemNames = new ArrayList<>(this.inventoryItemNames);
        return copy;
    }
}

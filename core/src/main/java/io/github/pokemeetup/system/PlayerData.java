package io.github.pokemeetup.system;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import io.github.pokemeetup.system.inventory.Item;

import java.util.ArrayList;
import java.util.List;

public class PlayerData {
    private String username;
    private float x, y;
    private int direction;
    private boolean isMoving;
    private boolean wantsToRun;
    private List<String> inventoryItemNames;

    public PlayerData() {
        System.out.println("Creating empty PlayerData");
    }
    public PlayerData(String username) {
        System.out.println("Creating PlayerData for: " + username);
        this.username = username;
        this.inventoryItemNames = new ArrayList<>();
    }

    // Position methods
    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public void write(Json json) {
        json.writeValue("username", username);
        json.writeValue("x", x);
        json.writeValue("y", y);
        json.writeValue("direction", direction);
        json.writeValue("isMoving", isMoving);
        json.writeValue("wantsToRun", wantsToRun);
        json.writeValue("inventoryItemNames", inventoryItemNames);
    }



    public float getX() {
        return x;
    }

    public void setX(float x) {
        this.x = x;
    }

    public float getY() {
        return y;
    }

    public void setY(float y) {
        this.y = y;
    }

    public boolean isMoving() {
        return isMoving;
    }

    public void setMoving(boolean moving) {
        isMoving = moving;
    }

    public int getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        // Convert string direction to int
        switch (direction) {
            case "up":
                this.direction = 0;
                break;
            case "down":
                this.direction = 1;
                break;
            case "left":
                this.direction = 2;
                break;
            case "right":
                this.direction = 3;
                break;
            default:
                this.direction = 1; // default to down
        }
    }

    public void setDirection(int direction) {
        this.direction = direction;
    }

    public boolean isWantsToRun() {
        return wantsToRun;
    }

    public void setWantsToRun(boolean wantsToRun) {
        this.wantsToRun = wantsToRun;
    }

    public List<String> getInventoryItemNames() {
        return inventoryItemNames;
    }

    public void setInventoryItemNames(List<String> inventoryItemNames) {
        this.inventoryItemNames = inventoryItemNames;
    }

    public String convertDirectionIntToString(int direction) {
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

    public List<String> getInventory() {
        if (inventoryItemNames != null) {
            return inventoryItemNames;
        }
        return null;
    }

    // In PlayerData.java
    public void setInventory(List<String> itemStrings) {
        if (itemStrings != null) {
            this.inventoryItemNames = itemStrings;
            System.out.println("Set inventory with " + itemStrings.size() + " items: " +
                String.join(", ", itemStrings));
        } else {
            this.inventoryItemNames = new ArrayList<>();
            System.out.println("Set empty inventory");
        }
    }

    // Inventory methods


    public void setInventoryFromItems(List<Item> items) {
        List<String> itemStrings = new ArrayList<>();
        for (Item item : items) {
            if (item != null) {
                itemStrings.add(item.getName() + ":" + item.getCount());
            }
        }
        setInventory(itemStrings);
    }

    // Username getter
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}

package io.github.pokemeetup.system.gameplay.inventory;

import com.badlogic.gdx.graphics.g2d.TextureRegion;

public class Item {
    private String name;
    private String iconName; // Name of the texture region
    private transient TextureRegion icon; // Marked transient to avoid serialization

    // Constructor
    public Item(String name, String iconName, TextureRegion icon) {
        this.name = name;
        this.iconName = iconName;
        this.icon = icon;
    }
    public static final int MAX_STACK_SIZE = 64;
    private int count = 1;

    // Add getters/setters for count
    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = Math.min(count, MAX_STACK_SIZE);
    }

    public boolean canStackWith(Item other) {
        return other != null &&
            this.getName().equals(other.getName()) &&
            (this.count + other.count) <= MAX_STACK_SIZE;
    }

    public int addToStack(int amount) {
        int spaceLeft = MAX_STACK_SIZE - count;
        int amountToAdd = Math.min(amount, spaceLeft);
        count += amountToAdd;
        return amount - amountToAdd; // Return leftover amount
    }
    // Empty constructor for serialization
    public Item() {
    }

    public String getName() {
        return name;
    }

    public String getIconName() {
        return iconName;
    }

    public TextureRegion getIcon() {
        if (icon == null) {
            icon = ItemManager.getTexture(iconName);
        }
        return icon;
    } public Item copy() {
        Item newItem = new Item(getName(), getIconName(), getIcon());
        newItem.setCount(getCount());
        return newItem;
    }

}

package io.github.pokemeetup.system.gameplay.inventory.crafting;

import io.github.pokemeetup.system.gameplay.inventory.Inventory;
import io.github.pokemeetup.system.gameplay.inventory.Item;
import io.github.pokemeetup.system.gameplay.inventory.ItemManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class CraftingSystem {
    private Item heldItem;  // Currently held item
    private int heldCount;  // Count of held item
    public static final int INVENTORY_CRAFTING_SIZE = 2;
    public static final int TABLE_CRAFTING_SIZE = 3;
    private final Item[][] craftingGrid;
    private final int gridSize;

    private Map<String, CraftingRecipe> recipes;
    private Inventory playerInventory;
    private static final Map<String, CraftingRecipe> RECIPES = new HashMap<>();
    static {
        // Initialize recipes
        RECIPES.put("crafting_table", new CraftingRecipe(
                "crafting_table",
                new String[][] {
                        {"stick", "stick"},
                        {"stick", "stick"}
                    },
                    1
                ));
    }public Item getHeldItem() {
        return heldItem;
    }

    public void setHeldItem(Item item) {
        this.heldItem = item;
    }

    public Optional<Item> checkRecipe() {
        // Get the current crafting grid contents
        for (int i = 0; i < gridSize; i++) {
            for (int j = 0; j < gridSize; j++) {
                if (craftingGrid[i][j] == null) {
                    return Optional.empty();
                }
            }
        }

        // Check against known recipes
        for (CraftingRecipe recipe : RECIPES.values()) {
            if (recipe.matches(craftingGrid, gridSize)) {
                Item result = ItemManager.getItem(recipe.getResult()).copy();
                result.setCount(recipe.getCount());
                return Optional.of(result);
            }
        }

        return Optional.empty();
    }

    // Method to update the crafting grid
    public void setItemInGrid(int row, int col, Item item) {
        if (row >= 0 && row < gridSize && col >= 0 && col < gridSize) {
            craftingGrid[row][col] = item;
        }
    }

    public Inventory getPlayerInventory() {
        return playerInventory;
    }

    private boolean isUsingCraftingTable;

    public CraftingSystem(Inventory playerInventory) {
        this.playerInventory = playerInventory;
        this.recipes = new HashMap<>();
        this.gridSize = 2; // 2x2 crafting grid
        this.craftingGrid = new Item[gridSize][gridSize];
        this.heldItem = null;
        this.heldCount = 0;
        initializeRecipes();
    }
    public boolean isHoldingItem() {
        return heldItem != null;
    }

    // Get the currently held item


    // Get count of held item
    public int getHeldItemCount() {
        return heldCount;
    }

    // Decrease held item count by 1
    public void decrementHeldItem() {
        if (heldCount > 0) {
            heldCount--;
            if (heldItem != null) {
                heldItem.setCount(heldCount);
            }
            if (heldCount == 0) {
                clearHeldItem();
            }
        }
    }

    // Set an item with specific count as held
    public void setHeldItem(Item item, int count) {
        this.heldItem = item;
        this.heldCount = count;
        if (item != null) {
            item.setCount(count);
        }
    }

    public void clearHeldItem() {
        this.heldItem = null;
        this.heldCount = 0;
    }

    private void initializeRecipes() {
        // Crafting Table Recipe
        CraftingRecipe craftingTable = new CraftingRecipe(
            "crafting_table",
            new String[][] {
                {"stick", "stick"},
                {"stick", "stick"}
            },
            1
        );
        recipes.put("crafting_table", craftingTable);
    }

    public Optional<Item> tryCraft(Item[][] grid) {
        int size = isUsingCraftingTable ? TABLE_CRAFTING_SIZE : INVENTORY_CRAFTING_SIZE;

        for (CraftingRecipe recipe : recipes.values()) {
            if (recipe.matches(grid, size)) {
                return Optional.of(ItemManager.getItem(recipe.getResult()));
            }
        }
        return Optional.empty();
    }
}

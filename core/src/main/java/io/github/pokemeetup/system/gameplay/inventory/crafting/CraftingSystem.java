package io.github.pokemeetup.system.gameplay.inventory.crafting;

import io.github.pokemeetup.system.gameplay.inventory.Inventory;
import io.github.pokemeetup.system.data.ItemData;
import io.github.pokemeetup.utils.GameLogger;

import java.util.*;

public class CraftingSystem {
    private final Map<CraftingRecipeKey, CraftingResult> recipes;
    private final ItemData[] craftingSlots;
    private ItemData craftingResultSlot;
    private final Inventory inventory;
    private static final int GRID_SIZE = 4; // 2x2 grid

    public CraftingSystem(Inventory inventory) {
        this.inventory = inventory;
        this.recipes = initializeRecipes();
        this.craftingSlots = new ItemData[GRID_SIZE];
        this.craftingResultSlot = null;
    }

    private Map<CraftingRecipeKey, CraftingResult> initializeRecipes() {
        Map<CraftingRecipeKey, CraftingResult> recipeMap = new HashMap<>();

        // Recipe: 2 Wood -> 4 Sticks
        String[][] stickRecipe = {
            {"Wood", null},
            {"Wood", null}
        };
        recipeMap.put(new CraftingRecipeKey(stickRecipe), new CraftingResult("Stick", 4));

        // Recipe: 2 Sticks + 2 Wood -> Crafting Table
        String[][] craftingTableRecipe = {
            {"Stick", "Stick"},
            {"Stick", "Stick"}
        };
        recipeMap.put(new CraftingRecipeKey(craftingTableRecipe), new CraftingResult("CraftingTable", 1));

        // Add more recipes here...

        return recipeMap;
    }

    public void setItemInGrid(int index, ItemData item) {
        if (index < 0 || index >= GRID_SIZE) {
            GameLogger.error("Invalid crafting grid index: " + index);
            return;
        }

        if (item != null) {
            craftingSlots[index] = item.copy();
        } else {
            craftingSlots[index] = null;
        }

        updateCraftingResult();
    }

    public ItemData getItemFromGrid(int index) {
        if (index < 0 || index >= GRID_SIZE) {
            return null;
        }
        return craftingSlots[index] != null ? craftingSlots[index].copy() : null;
    }

    public ItemData getCraftingResult() {
        return craftingResultSlot != null ? craftingResultSlot.copy() : null;
    }

    private void updateCraftingResult() {
        // Convert crafting slots to recipe grid format
        String[][] currentGrid = new String[2][2];
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                int index = i * 2 + j;
                ItemData item = craftingSlots[index];
                currentGrid[i][j] = item != null ? item.getItemId() : null;
            }
        }

        // Check recipe
        CraftingResult result = checkRecipe(currentGrid);
        if (result != null) {
            craftingResultSlot = new ItemData(result.getItemId(), result.getCount(), UUID.randomUUID());
        } else {
            craftingResultSlot = null;
        }
    }

    public boolean craftItem() {
        if (craftingResultSlot == null) return false;

        // Try to add crafted item to inventory
        if (inventory.addItem(craftingResultSlot.copy())) {
            // Consume crafting ingredients
            consumeIngredients();
            // Clear result slot
            craftingResultSlot = null;
            return true;
        }
        return false;
    }

    private void consumeIngredients() {
        for (int i = 0; i < craftingSlots.length; i++) {
            ItemData item = craftingSlots[i];
            if (item != null) {
                if (item.getCount() > 1) {
                    item.setCount(item.getCount() - 1);
                } else {
                    craftingSlots[i] = null;
                }
            }
        }
        updateCraftingResult();
    }

    public void returnItemsToInventory() {
        // Return items from crafting grid to inventory
        for (int i = 0; i < craftingSlots.length; i++) {
            if (craftingSlots[i] != null) {
                if (inventory.addItem(craftingSlots[i].copy())) {
                    craftingSlots[i] = null;
                } else {
                    GameLogger.error("Failed to return crafting item to inventory: " + craftingSlots[i].getItemId());
                }
            }
        }
        craftingResultSlot = null;
    }

    public CraftingResult checkRecipe(String[][] grid) {
        CraftingRecipeKey key = new CraftingRecipeKey(grid);
        return recipes.get(key);
    }
}

class CraftingRecipeKey {
    private final String[][] grid;

    public CraftingRecipeKey(String[][] grid) {
        this.grid = new String[grid.length][];
        for (int i = 0; i < grid.length; i++) {
            this.grid[i] = Arrays.copyOf(grid[i], grid[i].length);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof CraftingRecipeKey)) return false;
        CraftingRecipeKey other = (CraftingRecipeKey) obj;
        if (grid.length != other.grid.length) return false;

        for (int i = 0; i < grid.length; i++) {
            if (!Arrays.equals(grid[i], other.grid[i])) return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return Arrays.deepHashCode(grid);
    }
}


package io.github.pokemeetup.system.gameplay.inventory.crafting;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class CraftingSystem {
    private final Map<CraftingRecipeKey, CraftingResult> recipes;
    private CraftingResultListener resultListener;

    public CraftingSystem() {
        this.recipes = initializeRecipes();
    }

    public void setCraftingResultListener(CraftingResultListener listener) {
        this.resultListener = listener;
    }

    private Map<CraftingRecipeKey, CraftingResult> initializeRecipes() {
        Map<CraftingRecipeKey, CraftingResult> recipeMap = new HashMap<>();

        // Example recipe for creating "Stick" from "Wood"
        String[][] recipeGrid = {
            {"Wood", null},
            {"Wood", null}
        };
        CraftingRecipeKey recipeKey = new CraftingRecipeKey(recipeGrid);
        recipeMap.put(recipeKey, new CraftingResult("Stick", 4));

        // Add more recipes as needed

        return recipeMap;
    }

    public CraftingResult checkRecipe(String[][] grid) {
        CraftingRecipeKey key = new CraftingRecipeKey(grid);
        return recipes.get(key);
    }
}

class CraftingRecipeKey {
    private final String[][] grid;

    public CraftingRecipeKey(String[][] grid) {
        // Deep copy the grid to prevent external modification
        this.grid = new String[grid.length][];
        for (int i = 0; i < grid.length; i++) {
            this.grid[i] = Arrays.copyOf(grid[i], grid[i].length);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof CraftingRecipeKey)) {
            return false;
        }
        CraftingRecipeKey other = (CraftingRecipeKey) obj;

        if (grid.length != other.grid.length) {
            return false;
        }

        for (int i = 0; i < grid.length; i++) {
            if (!Arrays.equals(grid[i], other.grid[i])) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = 1;
        for (String[] row : grid) {
            result = 31 * result + Arrays.hashCode(row);
        }
        return result;
    }
}

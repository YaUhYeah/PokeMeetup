package io.github.pokemeetup.system.gameplay.inventory.crafting;

import io.github.pokemeetup.system.gameplay.inventory.Item;

public class CraftingRecipe {
    private final String result;
    private final String[][] pattern;
    private final int count;

    public CraftingRecipe(String result, String[][] pattern, int count) {
        this.result = result;
        this.pattern = pattern;
        this.count = count;
    }

    public boolean matches(Item[][] grid, int size) {
        // Check if pattern matches grid
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (i < pattern.length && j < pattern[i].length) {
                    if (pattern[i][j] != null) {
                        if (grid[i][j] == null || !grid[i][j].getName().equals(pattern[i][j])) {
                            return false;
                        }
                    }
                } else if (grid[i][j] != null) {
                    return false;
                }
            }
        }
        return true;
    }

    public String getResult() {
        return result;
    }

    public int getCount() {
        return count;
    }
}

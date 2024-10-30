package io.github.pokemeetup.system.gameplay.inventory.crafting;

import io.github.pokemeetup.system.gameplay.inventory.Item;

public class CraftingRecipe {
    private final String result;
    private final String[][] pattern;
    private final int outputCount;

    public CraftingRecipe(String result, String[][] pattern, int outputCount) {
        this.result = result;
        this.pattern = pattern;
        this.outputCount = outputCount;
    }

    public String getResult() {
        return result;
    }

    public int getOutputCount() {
        return outputCount;
    }

    public boolean matches(Item[][] grid) {
        if (grid.length != pattern.length) return false;

        for (int i = 0; i < pattern.length; i++) {
            if (grid[i].length != pattern[i].length) return false;

            for (int j = 0; j < pattern[i].length; j++) {
                if (pattern[i][j] == null) {
                    if (grid[i][j] != null) return false;
                } else {
                    if (grid[i][j] == null || !grid[i][j].getName().equals(pattern[i][j])) {
                        return false;
                    }
                }
            }
        }

        return true;
    }
}

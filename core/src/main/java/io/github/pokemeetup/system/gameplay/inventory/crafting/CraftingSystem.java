package io.github.pokemeetup.system.gameplay.inventory.crafting;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import io.github.pokemeetup.system.gameplay.inventory.Inventory;
import io.github.pokemeetup.system.gameplay.inventory.Item;
import io.github.pokemeetup.system.gameplay.inventory.ItemManager;
import io.github.pokemeetup.utils.TextureManager;

import java.util.ArrayList;
import java.util.List;

public class CraftingSystem {
    private static final int GRID_SIZE = 2; // 2x2 crafting grid
    private final Item[][] craftingGrid;
    private final List<CraftingRecipe> recipes;
    private Item craftingResult;
    private final Inventory playerInventory;

    // Constants for textures
    private static final String SLOT_NORMAL = "slot_normal";
    private static final String SLOT_SELECTED = "slot_selected";
    private static final String HOTBAR_BG = "hotbar_bg";
    private static final String COUNT_BUBBLE = "count_bubble";

    // Held item state
    private Item heldItem;
    private int heldCount;
    private Stage stage;
    private Image itemImage;
    private Label countLabel;
    private int row;
    private int col;

    public CraftingSystem(Inventory playerInventory) {
        this.playerInventory = playerInventory;
        this.stage = stage;
        this.craftingGrid = new Item[GRID_SIZE][GRID_SIZE];
        this.recipes = new ArrayList<>();
        this.heldItem = null;
        this.heldCount = 0;
        this.craftingResult = null;
        initializeRecipes();
    }

    public void initializeVisuals(Skin skin) {
        itemImage = new Image();
        itemImage.setSize(32, 32);

        countLabel = new Label("", skin);
        countLabel.setColor(Color.WHITE);
    }

    private void initializeRecipes() {
        recipes.add(new CraftingRecipe(
            "CraftingTable",
            new String[][] {
                {"Stick", "Stick"},
                {"Stick", "Stick"}
            },
            1
        ));

        recipes.add(new CraftingRecipe(
            "Stick",
            new String[][] {
                {"Plank", null},
                {"Plank", null}
            },
            4
        ));
    }

    // Getters and setters
    public boolean isHoldingItem() {
        return heldItem != null && heldCount > 0;
    }

    public Item getHeldItem() {
        return heldItem;
    }

    public int getHeldCount() {
        return heldCount;
    }

    public int getHeldItemCount() {
        return heldCount;
    }

    public Stage getStage() {
        return stage;
    }

    public Image getItemImage() {
        return itemImage;
    }

    public Label getCountLabel() {
        return countLabel;
    }

    public int getRow() {
        return row;
    }

    public int getCol() {
        return col;
    }

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

    public Item getItemFromGrid(int row, int col) {
        if (row >= 0 && row < GRID_SIZE && col >= 0 && col < GRID_SIZE) {
            return craftingGrid[row][col];
        }
        return null;
    }

    public void setItemInGrid(int row, int col, Item item) {
        if (row >= 0 && row < GRID_SIZE && col >= 0 && col < GRID_SIZE) {
            craftingGrid[row][col] = item;
            updateCraftingResult();
        }
    }

    public void clearGrid() {
        for (int i = 0; i < GRID_SIZE; i++) {
            for (int j = 0; j < GRID_SIZE; j++) {
                craftingGrid[i][j] = null;
            }
        }
        updateCraftingResult();
    }

    public Item getCraftingResult() {
        return craftingResult;
    }

    private void updateCraftingResult() {
        craftingResult = null;
        for (CraftingRecipe recipe : recipes) {
            if (recipe.matches(craftingGrid)) {
                Item result = ItemManager.getItem(recipe.getResult());
                if (result != null) {
                    craftingResult = result.copy();
                    craftingResult.setCount(recipe.getOutputCount());
                    break;
                }
            }
        }
    }

    public void craftItem() {
        if (craftingResult != null) {
            if (playerInventory.canAddItem(craftingResult)) {
                // Consume ingredients
                for (int i = 0; i < GRID_SIZE; i++) {
                    for (int j = 0; j < GRID_SIZE; j++) {
                        if (craftingGrid[i][j] != null) {
                            craftingGrid[i][j].setCount(craftingGrid[i][j].getCount() - 1);
                            if (craftingGrid[i][j].getCount() <= 0) {
                                craftingGrid[i][j] = null;
                            }
                        }
                    }
                }

                // Add result to inventory
                playerInventory.addItem(craftingResult.copy());
                updateCraftingResult();
            }
        }
    }

    public void returnItemsToInventory() {
        for (int i = 0; i < GRID_SIZE; i++) {
            for (int j = 0; j < GRID_SIZE; j++) {
                if (craftingGrid[i][j] != null) {
                    playerInventory.addItem(craftingGrid[i][j]);
                    craftingGrid[i][j] = null;
                }
            }
        }

        if (isHoldingItem()) {
            playerInventory.addItem(heldItem);
            clearHeldItem();
        }

        updateCraftingResult();
    }

    public TextureRegionDrawable getSlotBackground(boolean selected) {
        String regionName = selected ? SLOT_SELECTED : SLOT_NORMAL;
        return new TextureRegionDrawable(TextureManager.getGameAtlas().findRegion(regionName));
    }
}

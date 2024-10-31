package io.github.pokemeetup.system.gameplay.inventory;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import io.github.pokemeetup.system.gameplay.inventory.crafting.CraftingResult;
import io.github.pokemeetup.system.gameplay.inventory.crafting.CraftingSystem;
import io.github.pokemeetup.utils.TextureManager;

public class ResultSlot extends Table {
    private final CraftingSystem craftingSystem;
    private final Image itemImage;
    private final Label countLabel;
    private Item craftedItem;
    private Item item;

    public ResultSlot(CraftingSystem craftingSystem, Skin skin) {
        this.craftingSystem = craftingSystem;

        // Use a solid color drawable for background instead of texture
        setBackground(skin.newDrawable("white", new Color(0.2f, 0.2f, 0.2f, 0.8f)));
        pad(4); // Increased padding for better visibility

        // Create image and label with proper sizing
        itemImage = new Image();
        itemImage.setSize(40, 40); // Slightly larger for better visibility

        countLabel = new Label("",skin) ;
        countLabel.setColor(Color.WHITE);

        // Use Table layout for proper positioning
        Table contentTable = new Table();
        contentTable.add(itemImage).size(40).center();

        // Position count label at bottom-right with padding
        Table countTable = new Table();
        countTable.add(countLabel).pad(2).bottom().right();

        add(contentTable).grow();
        addActor(countTable); // Add as actor to allow absolute positioning
        countTable.setPosition(getWidth() - 12, 8);

        // Setup click listener for crafting
        addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (craftedItem != null) {
                    updateVisuals();
                }
            }
        });

        updateVisuals();
    }public void setCraftingResult(CraftingResult result) {
        if (result != null) {
            // Get the crafted item from ItemManager
            item = ItemManager.getItem(result.getItemId());
            if (item != null) {
                item.setCount(result.getCount());
                updateVisuals();
            }
        } else {
            item = null;
            updateVisuals();
        }
    }

    public void setCraftedItem(Item item) {
        this.craftedItem = item;
        updateVisuals();
    }
    private void updateVisuals() {
        if (item != null && item.getCount() > 0) {
            itemImage.setDrawable(new TextureRegionDrawable(item.getIcon()));
            if (item.getCount() > 1) {
                countLabel.setText(String.valueOf(item.getCount()));
                countLabel.setVisible(true);
            } else {
                countLabel.setVisible(false);
            }
        } else {
            itemImage.setDrawable(null);
            countLabel.setVisible(false);
        }
    }
}

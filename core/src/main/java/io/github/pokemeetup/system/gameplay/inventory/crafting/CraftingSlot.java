package io.github.pokemeetup.system.gameplay.inventory.crafting;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.*;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.github.pokemeetup.system.gameplay.inventory.Item;
import io.github.pokemeetup.utils.TextureManager;

public class CraftingSlot extends Table {
    private final CraftingSystem craftingSystem;
    private final int row;
    private final int col;
    private final Image itemImage;
    private final Label countLabel;
    private Item item;
    private int count;

    public CraftingSlot(CraftingSystem craftingSystem, int row, int col, Skin skin, DragAndDrop dragAndDrop) {
        this.craftingSystem = craftingSystem;
        this.row = row;
        this.col = col;
        setBackground(new TextureRegionDrawable(
            TextureManager.getGameAtlas().findRegion("dark-overlay")
        ));
        pad(2);

        // Create item image and count label
        itemImage = new Image();
        itemImage.setSize(32, 32);

        countLabel = new Label("", skin);
        countLabel.setColor(Color.WHITE);

        // Add actors to table
        add(itemImage).size(32).center();
        add(countLabel).bottom().right();

        // Setup drag and drop
        setupDragAndDrop(dragAndDrop);

        // Initial update
        updateVisuals();
    }

    private void setupDragAndDrop(DragAndDrop dragAndDrop) {
        // Source (for dragging items out)
        dragAndDrop.addSource(new DragAndDrop.Source(this) {
            @Override
            public DragAndDrop.Payload dragStart(InputEvent event, float x, float y, int pointer) {
                if (item == null) return null;

                DragAndDrop.Payload payload = new DragAndDrop.Payload();
                payload.setObject(item);

                // Create drag visual
                Table dragActor = new Table();
                dragActor.add(new Image(item.getIcon())).size(32);
                if (count > 1) {
                    dragActor.add(new Label(String.valueOf(count), getSkin())).bottom().right();
                }

                payload.setDragActor(dragActor);

                // Clear slot
                setItem(null);

                return payload;
            }
        });

        // Target (for receiving dragged items)
        dragAndDrop.addTarget(new DragAndDrop.Target(this) {
            @Override
            public boolean drag(DragAndDrop.Source source, DragAndDrop.Payload payload, float x, float y, int pointer) {
                Item draggedItem = (Item) payload.getObject();
                return canAcceptItem(draggedItem);
            }

            @Override
            public void drop(DragAndDrop.Source source, DragAndDrop.Payload payload, float x, float y, int pointer) {
                Item draggedItem = (Item) payload.getObject();
                handleItemDrop(draggedItem);
            }
        });
    }

    private boolean canAcceptItem(Item draggedItem) {
        return item == null || (item.canStackWith(draggedItem) &&
            item.getCount() + draggedItem.getCount() <= Item.MAX_STACK_SIZE);
    }

    private void handleItemDrop(Item draggedItem) {
        if (item == null) {
            setItem(draggedItem);
        } else if (item.canStackWith(draggedItem)) {
            int newCount = Math.min(item.getCount() + draggedItem.getCount(), Item.MAX_STACK_SIZE);
            item.setCount(newCount);
            updateVisuals();
        }
        craftingSystem.setItemInGrid(row, col, item);
    }

    public void setItem(Item newItem) {
        this.item = newItem;
        if (newItem != null) {
            this.count = newItem.getCount();
        } else {
            this.count = 0;
        }
        updateVisuals();
        craftingSystem.setItemInGrid(row, col, item);
    }

    private void updateVisuals() {
        if (item != null && count > 0) {
            itemImage.setDrawable(new TextureRegionDrawable(item.getIcon()));
            if (count > 1) {
                countLabel.setText(String.valueOf(count));
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

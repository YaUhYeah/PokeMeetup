package io.github.pokemeetup.system.gameplay.inventory;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import io.github.pokemeetup.utils.TextureManager;

public class InventorySlot extends Table {
    private final Inventory inventory;
    private final int index;
    private final Image itemImage;
    private final Skin skin;
    private final DragAndDrop dragAndDrop;
    private final Stage stage;
    private Item item;  // Add this as a class field
    private int count;  // Add this as a class field// In InventorySlot class

    public void updateSlotContents() {
        Item inventoryItem = inventory.getItem(index);
        setItem(inventoryItem, inventoryItem != null ? inventoryItem.getCount() : 0);
        refreshItem(); // Refresh visuals and sync with inventory data
    }



    public InventorySlot(Inventory inventory, int index, Stage stage, Skin skin, DragAndDrop dragAndDrop) {
        this.inventory = inventory;
        this.index = index;
        this.stage = stage;
        this.skin = skin;
        this.dragAndDrop = dragAndDrop;

        TextureAtlas gameAtlas = TextureManager.getGameAtlas();
        setBackground(new TextureRegionDrawable(gameAtlas.findRegion("slot_normal")));

        Table contentStack = new Table();
        contentStack.setFillParent(true);

        itemImage = new Image();
        contentStack.add(itemImage).expand().center();
this.setTouchable(Touchable.enabled);
        add(contentStack).grow();

        setupListeners();
        updateVisuals();
    }private void refreshItem() {
        // Retrieve the item directly from inventory based on index
        this.item = inventory.getItem(index);
        this.count = item != null ? item.getCount() : 0;
        updateVisuals(); // Refresh the slot's visuals
    }
    public void setItem(Item newItem, int newCount) {
        if (newItem != null && newCount <= 0) {
            newItem = null;
            newCount = 0;
        }

        this.item = newItem;
        this.count = newCount;

        if (newItem != null) {
            newItem.setCount(newCount);
            inventory.setItem(index, newItem);
        } else {
            inventory.setItem(index, null);
            itemImage.setDrawable(null); // Explicitly clear the image if no item
        }

        refreshItem(); // Ensure the slot reflects the updated inventory state
        updateVisuals();
    }



    private void setupListeners() {
        // Similar to CraftingSlot but for inventory items
        dragAndDrop.addSource(new DragAndDrop.Source(this) {
            @Override
            public DragAndDrop.Payload dragStart(InputEvent event, float x, float y, int pointer) {
                return createDragPayload();
            }
        });

        dragAndDrop.addTarget(new DragAndDrop.Target(this) {
            @Override
            public boolean drag(DragAndDrop.Source source, DragAndDrop.Payload payload,
                                float x, float y, int pointer) {
                return canAcceptItem(payload);
            }

            @Override
            public void drop(DragAndDrop.Source source, DragAndDrop.Payload payload,
                             float x, float y, int pointer) {
                handleDrop(payload);
            }
        });

        addListener(new ClickListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                if (button == Input.Buttons.RIGHT) {
                    handleRightClick();
                } else {
                    handleLeftClick();
                }
                return true;
            }
        });
    }

    private void handleLeftClick() {
        if (!inventory.isHoldingItem() && item != null) {
            // Pick up the stack only if the item is present
            inventory.setHeldItem(item, count);
            setItem(null, 0);
        } else if (inventory.isHoldingItem()) {
            Item heldItem = inventory.getHeldItem();
            int heldCount = inventory.getHeldItemCount();

            if (item == null) {
                // Place item in an empty slot and clear the held item
                setItem(heldItem, heldCount);
                inventory.clearHeldItem();
            } else if (item.canStackWith(heldItem)) {
                // Stack items if possible
                int remaining = item.addToStack(heldCount);
                if (remaining > 0) {
                    heldItem.setCount(remaining); // Update held count if there's leftover
                } else {
                    inventory.clearHeldItem(); // Clear held if fully stacked
                }
            } else {
                // Swap if stacking isn't possible
                Item tempItem = item;
                int tempCount = count;
                setItem(heldItem, heldCount);
                inventory.setHeldItem(tempItem, tempCount);
            }
        }
    }private void handleDrop(DragAndDrop.Payload payload) {
        Item droppedItem = (Item) payload.getObject();
        Item currentItem = inventory.getItem(index);

        if (currentItem == null) {
            // Place item if slot is empty
            setItem(droppedItem, droppedItem.getCount());
            inventory.clearHeldItem();
        } else if (currentItem.canStackWith(droppedItem)) {
            // Stack items if they match
            int remaining = currentItem.addToStack(droppedItem.getCount());
            if (remaining > 0) {
                droppedItem.setCount(remaining);
                inventory.setHeldItem(droppedItem, remaining); // Keep remaining as held
            } else {
                inventory.clearHeldItem(); // Clear held if fully stacked
            }
        } else {
            // Swap items if stacking isn’t possible
            int tempCount = currentItem.getCount();
            setItem(droppedItem, droppedItem.getCount());
            inventory.setHeldItem(currentItem, tempCount); // Hold the previous item
        }

        refreshItem();  // Ensure the slot reflects the updated inventory state
        updateVisuals();
    }



    private void handleRightClick() {
        if (!inventory.isHoldingItem() && item != null) {
            // Split stack
            int splitAmount = count / 2;
            if (splitAmount > 0) {
                Item splitItem = item.copy();
                splitItem.setCount(splitAmount);
                setItem(item, count - splitAmount);
                inventory.setHeldItem(splitItem, splitAmount);
            }
        } else if (inventory.isHoldingItem()) {
            // Place single item
            Item heldItem = inventory.getHeldItem();
            if (item == null || item.canStackWith(heldItem)) {
                if (item == null) {
                    setItem(heldItem.copy(), 1);
                } else {
                    item.addToStack(1);
                }
                if (inventory.getHeldItemCount() > 1) {
                    inventory.decrementHeldItem();
                } else {
                    inventory.clearHeldItem();
                }
            }
        }
    }

    private DragAndDrop.Payload createDragPayload() {
        Item item = inventory.getItem(index);
        if (item == null) return null;

        DragAndDrop.Payload payload = new DragAndDrop.Payload();
        payload.setObject(item);

        Table content = new Table();
        Image dragImage = new Image(item.getIcon());
        content.add(dragImage).size(40);

        if (item.getCount() > 1) {
            Label countLabel = new Label(String.valueOf(item.getCount()), skin);
            content.add(countLabel).bottom().right();
        }

        payload.setDragActor(content);

        // Explicitly remove the item from the inventory slot only after confirming payload creation
        inventory.setItem(index, null);
        this.item = null; // Clear local item reference as well
        updateVisuals();

        return payload;
    }

    private boolean canAcceptItem(DragAndDrop.Payload payload) {
        Item draggedItem = (Item) payload.getObject();
        Item currentItem = inventory.getItem(index);

        return currentItem == null ||
            (currentItem.getName().equals(draggedItem.getName()) &&
                currentItem.getCount() + draggedItem.getCount() <= Item.MAX_STACK_SIZE);
    }





    // Convenience method for setting just the item (count = 1)
    public void setItem(Item newItem) {
        setItem(newItem, newItem != null ? 1 : 0);
    }

    private void updateVisuals() {
        clearChildren();  // Clear existing visuals before updating

        if (item != null && count > 0) {
            // Update item image
            itemImage.setDrawable(new TextureRegionDrawable(item.getIcon()));
            add(itemImage).expand().center();

            // Add count label if stack size > 1
            if (count > 1) {
                Label countLabel = new Label(String.valueOf(count), skin);
                countLabel.setPosition(getWidth() - countLabel.getWidth() - 5, 5);
                addActor(countLabel);
            }
        } else {
            // Clear slot visuals for empty slot
            itemImage.setDrawable(null);
            add(itemImage).expand().center();
        }
    }

}


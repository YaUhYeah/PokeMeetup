package io.github.pokemeetup.system.gameplay.inventory.crafting;

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
import io.github.pokemeetup.system.gameplay.inventory.Inventory;
import io.github.pokemeetup.system.gameplay.inventory.Item;
import io.github.pokemeetup.system.gameplay.inventory.ResultSlot;
import io.github.pokemeetup.utils.TextureManager;

import java.util.Optional;

public class CraftingSlot extends Table {
    private final CraftingSystem craftingSystem;
    private final ResultSlot resultSlot;
    private final Inventory inventory;
    private final int row, col;
    private final Image itemImage;
    private final TextureRegionDrawable normalSlot;
    private final TextureRegionDrawable selectedSlot;
    private final Stage stage;
    private final Skin skin;
    private final DragAndDrop dragAndDrop;
    private Item item;
    private int count;// In CraftingSlot class

    public CraftingSlot(Inventory inventory, int row, int col, Stage stage, Skin skin, DragAndDrop dragAndDrop) {
        this.inventory = inventory;
        this.row = row;
        this.col = col;
        this.stage = stage;
        this.skin = skin;
        this.dragAndDrop = dragAndDrop;
        this.craftingSystem = new CraftingSystem(inventory);
        this.resultSlot = new ResultSlot(craftingSystem);
        this.setTouchable(Touchable.enabled);

        // Use TextureManager for atlas access
        TextureAtlas gameAtlas = TextureManager.getGameAtlas();
        normalSlot = new TextureRegionDrawable(gameAtlas.findRegion("slot_normal"));
        selectedSlot = new TextureRegionDrawable(gameAtlas.findRegion("slot_selected"));


        setBackground(normalSlot);

        Table contentStack = new Table();
        contentStack.setFillParent(true);

        itemImage = new Image();
        contentStack.add(itemImage).expand().center();

        add(contentStack).grow();

        setupListeners();
    }

    public void updateSlotContents() {
        // Get the item from the crafting grid based on this slot's position
        Item gridItem = craftingSystem.getPlayerInventory().getItemAt(row, col);

        // If there's a new item, update the visuals
        if (gridItem != null) {
            setItem(gridItem);
        } else {
            setItem(null); // Clear slot visuals if no item
        }

        // Update the crafting result based on the current grid
        updateCraftingResult();
    }

    // Check if currently holding an item

    private void setupListeners() {
        addListener(new ClickListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                if (button == Input.Buttons.LEFT) {
                    handleLeftClick();
                } else if (button == Input.Buttons.RIGHT) {
                    handleRightClick();
                }
                return true;
            }
        });

        // Add the drag source and target to DragAndDrop instance
        dragAndDrop.addSource(new DragAndDrop.Source(this) {
            @Override
            public DragAndDrop.Payload dragStart(InputEvent event, float x, float y, int pointer) {
                return createDragPayload();
            }
        });

        dragAndDrop.addTarget(new DragAndDrop.Target(this) {
            @Override
            public boolean drag(DragAndDrop.Source source, DragAndDrop.Payload payload, float x, float y, int pointer) {
                return canAcceptItem((Item) payload.getObject());
            }

            @Override
            public void drop(DragAndDrop.Source source, DragAndDrop.Payload payload, float x, float y, int pointer) {
               handleDrop(payload);
            }
        });
    }


    private boolean canAcceptItem(Item newItem) {
        return item == null || (item.canStackWith(newItem) &&
            item.getCount() + newItem.getCount() <= Item.MAX_STACK_SIZE);
    }

    private DragAndDrop.Payload createDragPayload() {
        if (item == null) return null;

        DragAndDrop.Payload payload = new DragAndDrop.Payload();
        payload.setObject(item);

        // Create visual representation for dragging
        Table content = new Table();
        Image dragImage = new Image(item.getIcon());
        content.add(dragImage).size(40);

        if (count > 1) {
            Label countLabel = new Label(String.valueOf(count), skin);
            content.add(countLabel).bottom().right();
        }

        payload.setDragActor(content);
        payload.setValidDragActor(content);
        payload.setInvalidDragActor(content);

        // Clear slot when dragging starts
        item = null;
        count = 0;
        updateVisuals();

        return payload;
    }
    private void handleDrop(DragAndDrop.Payload payload) {
        Item droppedItem = (Item) payload.getObject();
        craftingSystem.setItemInGrid(row, col, droppedItem); // Set item in the crafting grid
        setItem(droppedItem);
    }


    private void handleLeftClick() {
        if (!craftingSystem.isHoldingItem() && item != null) {
            // Pick up whole stack
            craftingSystem.setHeldItem(item);
            setItem(null);
        } else if (craftingSystem.isHoldingItem()) {
            Item heldItem = craftingSystem.getHeldItem();

            if (item == null) {
                // Place into empty slot
                setItem(heldItem);
                craftingSystem.setHeldItem(null);
            } else if (item.canStackWith(heldItem)) {
                // Stack with existing items
                int transferred = item.addToStack(heldItem.getCount());
                if (transferred == 0) {
                    craftingSystem.setHeldItem(null);
                } else {
                    heldItem.setCount(transferred);
                }
            } else {
                // Swap items
                Item temp = item;
                setItem(heldItem);
                craftingSystem.setHeldItem(temp);
            }
        }
        updateCraftingResult();
    }

    private void handleRightClick() {
        if (!craftingSystem.isHoldingItem() && item != null) {
            // Split stack
            int amount = item.getCount();
            int splitAmount = amount / 2;
            if (splitAmount > 0) {
                Item splitItem = item.copy();
                splitItem.setCount(splitAmount);
                item.setCount(amount - splitAmount);
                craftingSystem.setHeldItem(splitItem);
                if (item.getCount() == 0) {
                    setItem(null);
                }
            }
        } else if (craftingSystem.isHoldingItem()) {
            // Place one item
            Item heldItem = craftingSystem.getHeldItem();
            if (item == null) {
                Item singleItem = heldItem.copy();
                singleItem.setCount(1);
                setItem(singleItem);
                heldItem.setCount(heldItem.getCount() - 1);
                if (heldItem.getCount() == 0) {
                    craftingSystem.setHeldItem(null);
                }
            } else if (item.canStackWith(heldItem)) {
                item.addToStack(1);
                heldItem.setCount(heldItem.getCount() - 1);
                if (heldItem.getCount() == 0) {
                    craftingSystem.setHeldItem(null);
                }
            }
        }
        updateCraftingResult();
    }

    public void setItem(Item newItem) {
        this.item = newItem;
        this.count = newItem != null ? newItem.getCount() : 0;
        updateVisuals();
        updateCraftingResult();
    }

    private void updateCraftingResult() {
        Optional<Item> result = craftingSystem.checkRecipe();
        resultSlot.setCraftedItem(result.orElse(null));
    }

    private void updateVisuals() {
        if (item != null && count > 0) {
            itemImage.setDrawable(new TextureRegionDrawable(item.getIcon()));
            if (count > 1) {
                // Add count label
                Label countLabel = new Label(String.valueOf(count), skin);
                countLabel.setPosition(getWidth() - countLabel.getWidth() - 5, 5);
                addActor(countLabel);
            }
        } else {
            itemImage.setDrawable(null);
            clearChildren();
            add(itemImage);
        }
    }
}

package io.github.pokemeetup.system.gameplay.inventory;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import io.github.pokemeetup.system.gameplay.inventory.crafting.CraftingSystem;
import io.github.pokemeetup.utils.TextureManager;

public class InventorySlot extends Table {
    private static final float HOVER_SCALE = 1.05f;
    private static final float HOVER_DURATION = 0.1f;
    private final Inventory inventory;
    private final int index;
    private final Image itemImage;
    private final Skin skin;
    private final DragAndDrop dragAndDrop;
    private final Stage stage;
    private final CraftingSystem craftingSystem; // Added CraftingSystem reference
    private final Label countLabel;
    private float originalX, originalY; // Store original position for drag operations
    private boolean isDragging = false;
    private Item draggedItem = null;
    // Add field to track cursor item display
    private Table cursorItemDisplay;
    private Label cursorCountLabel;
    private boolean isHovered = false;
    private Item item;  // Add this as a class field
    private int count;  // Add this as a class field// In InventorySlot class

    public InventorySlot(Inventory inventory, int index, Stage stage, Skin skin, DragAndDrop dragAndDrop, CraftingSystem craftingSystem) {
        this.inventory = inventory;
        this.index = index;
        this.stage = stage;
        this.skin = skin;
        this.dragAndDrop = dragAndDrop;
        this.craftingSystem = craftingSystem;  // Initialize CraftingSystem

        TextureAtlas gameAtlas = TextureManager.getGameAtlas();
        setBackground(new TextureRegionDrawable(gameAtlas.findRegion("slot_normal")));

        Table contentStack = new Table();
        contentStack.setFillParent(true);

        countLabel = new Label("", skin);
        countLabel.setColor(Color.WHITE);
        itemImage = new Image();
        contentStack.add(itemImage).expand().center();
        add(itemImage).size(32).center();
        add(countLabel).bottom().right();
        this.setTouchable(Touchable.enabled);
        add(contentStack).grow();
        // Remove old setupListeners() call and replace with these:
        setupDragAndDrop();  // Add this line to enable drag and drop

        // Add hover effect
        addListener(new InputListener() {
            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                isHovered = true;
                addAction(Actions.sequence(
                    Actions.scaleTo(HOVER_SCALE, HOVER_SCALE, HOVER_DURATION, Interpolation.smooth)
                ));
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                isHovered = false;
                addAction(Actions.sequence(
                    Actions.scaleTo(1f, 1f, HOVER_DURATION, Interpolation.smooth)
                ));
            }

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

        updateVisuals();

    }

    public int getIndex() {
        return index;
    }

    public void refreshItem() {
        Item currentItem = inventory.getItem(index);
        if (currentItem != null) {
            if (item == null || !item.getName().equals(currentItem.getName())
                || item.getCount() != currentItem.getCount()) {
                setItem(currentItem.copy());
            }
        } else if (item != null) {
            setItem(null);
        }
        updateVisuals();
    }


    private void createDragActor(DragAndDrop.Payload payload, Item dragItem) {
        Table content = new Table();
        Image dragImage = new Image(dragItem.getIcon());
        content.add(dragImage).size(40);

        if (dragItem.getCount() > 1) {
            Label dragCountLabel = new Label(String.valueOf(dragItem.getCount()), skin);
            content.add(dragCountLabel).bottom().right();
        }

        payload.setDragActor(content);
        payload.setValidDragActor(content); // Optional: show when over valid target
        payload.setInvalidDragActor(content); // Optional: show when over invalid target
    }

    private void setupListeners() {
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

    private void handleDrop(DragAndDrop.Payload payload) {
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


    private void setItem(Item newItem, int newCount) {
        if (newItem == null || newCount <= 0) {
            this.item = null;
            this.count = 0;
            inventory.setItem(index, null);
        } else {
            this.item = newItem.copy();
            this.count = Math.min(newCount, Item.MAX_STACK_SIZE);
            this.item.setCount(this.count);
            inventory.setItem(index, this.item);
        }
        updateVisuals();
    }

    public void updateSlotContents() {
        Item inventoryItem = inventory.getItem(index);
        if (inventoryItem != null) {
            setItem(inventoryItem.copy(), inventoryItem.getCount());
        } else {
            setItem(null, 0);
        }
        updateVisuals();
    }

    private void handleLeftClick() {
        if (!craftingSystem.isHoldingItem() && item != null) {
            // Pick up item
            Item pickedItem = item.copy();
            craftingSystem.setHeldItem(pickedItem, item.getCount());
            setItem(null);
        } else if (craftingSystem.isHoldingItem()) {
            Item heldItem = craftingSystem.getHeldItem();
            int heldCount = craftingSystem.getHeldItemCount();

            if (item == null) {
                // Place held item
                setItem(heldItem.copy(), heldCount);
                craftingSystem.clearHeldItem();
            } else if (item.canStackWith(heldItem)) {
                // Stack items
                int totalCount = Math.min(item.getCount() + heldCount, Item.MAX_STACK_SIZE);
                int remainingCount = (item.getCount() + heldCount) - totalCount;
                item.setCount(totalCount);

                if (remainingCount > 0) {
                    craftingSystem.setHeldItem(heldItem, remainingCount);
                } else {
                    craftingSystem.clearHeldItem();
                }
            } else {
                // Swap items
                swapItems(heldItem, heldCount);
            }
        }
        updateVisuals();
    }


    private void pickUpItem() {
        Item pickedItem = item.copy();
        craftingSystem.setHeldItem(pickedItem, item.getCount());
        setItem(null, 0);
    }

    private void placeOrSwapItem() {
        Item heldItem = craftingSystem.getHeldItem();
        int heldCount = craftingSystem.getHeldItemCount();

        if (item == null) {
            setItem(heldItem.copy(), heldCount);
            craftingSystem.clearHeldItem();
        } else if (item.canStackWith(heldItem)) {
            int totalCount = Math.min(item.getCount() + heldCount, Item.MAX_STACK_SIZE);
            int remainingCount = (item.getCount() + heldCount) - totalCount;
            item.setCount(totalCount);

            if (remainingCount > 0) {
                craftingSystem.setHeldItem(heldItem, remainingCount);
            } else {
                craftingSystem.clearHeldItem();
            }
        } else {
            swapItems(heldItem, heldCount);
        }
    }

    private void swapItems(Item heldItem, int heldCount) {
        Item tempItem = item.copy();
        int tempCount = item.getCount();
        setItem(heldItem.copy(), heldCount);
        craftingSystem.setHeldItem(tempItem, tempCount);
    }

    private void splitStack() {
        if (item.getCount() > 1) {
            int splitAmount = item.getCount() / 2;
            Item splitItem = item.copy();
            splitItem.setCount(splitAmount);
            item.setCount(item.getCount() - splitAmount);

            if (item.getCount() <= 0) {
                setItem(null, 0);
            }
            craftingSystem.setHeldItem(splitItem, splitAmount);
        }
    }

    private void placeSingleItem() {
        Item heldItem = craftingSystem.getHeldItem();
        if (item == null) {
            Item singleItem = heldItem.copy();
            singleItem.setCount(1);
            setItem(singleItem, 1);
            decreaseHeldItemCount();
        } else if (item.canStackWith(heldItem) && item.getCount() < Item.MAX_STACK_SIZE) {
            item.setCount(item.getCount() + 1);
            decreaseHeldItemCount();
        }
    }

    private void decreaseHeldItemCount() {
        if (craftingSystem.getHeldItemCount() > 1) {
            craftingSystem.setHeldItem(craftingSystem.getHeldItem(), craftingSystem.getHeldItemCount() - 1);
        } else {
            craftingSystem.clearHeldItem();
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

        inventory.setItem(index, null);
        this.item = null;
        updateVisuals();

        return payload;
    }


    private void setupDragAndDrop() {
        dragAndDrop.addSource(new DragAndDrop.Source(this) {
            @Override
            public DragAndDrop.Payload dragStart(InputEvent event, float x, float y, int pointer) {
                if (item == null) return null;

                DragAndDrop.Payload payload = new DragAndDrop.Payload();

                // Create copy of item for dragging to prevent reference issues
                draggedItem = item.copy();
                isDragging = true;

                if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)) {
                    // Handle stack splitting
                    int splitAmount = item.getCount() / 2;
                    if (splitAmount > 0) {
                        draggedItem.setCount(splitAmount);
                        item.setCount(item.getCount() - splitAmount);
                        if (item.getCount() <= 0) {
                            item = null;
                        }
                    }
                } else {
                    // Pick up whole stack
                    draggedItem.setCount(item.getCount());
                    item = null;
                }

                payload.setObject(draggedItem);

                // Create drag visual
                cursorItemDisplay = new Table();
                Image dragImage = new Image(draggedItem.getIcon());
                cursorItemDisplay.add(dragImage).size(32);

                if (draggedItem.getCount() > 1) {
                    cursorCountLabel = new Label(String.valueOf(draggedItem.getCount()), skin);
                    cursorCountLabel.setStyle(new Label.LabelStyle(skin.getFont("default"), Color.WHITE));
                    cursorItemDisplay.add(cursorCountLabel).bottom().right();
                }

                payload.setDragActor(cursorItemDisplay);
                payload.setValidDragActor(cursorItemDisplay);
                payload.setInvalidDragActor(cursorItemDisplay);

                updateVisuals();
                inventory.updateHotbarDisplay();
                return payload;
            }

            @Override
            public void dragStop(InputEvent event, float x, float y, int pointer,
                                 DragAndDrop.Payload payload, DragAndDrop.Target target) {
                if (target == null && draggedItem != null) {
                    // If dropped outside valid target, return item to original slot
                    returnItemToSlot(draggedItem);
                }
                isDragging = false;
                draggedItem = null;
                cursorItemDisplay = null;
                cursorCountLabel = null;
                updateVisuals();
                inventory.updateHotbarDisplay();
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
                handleSecureDrop((Item)payload.getObject());
            }
        });
    }    private void handleSecureDrop(Item droppedItem) {
        if (droppedItem == null) return;

        synchronized (inventory) {  // Thread safety for inventory operations
            if (item == null) {
                // Empty slot - place item directly
                item = droppedItem.copy();
                inventory.setItem(index, item);
            } else if (item.canStackWith(droppedItem)) {
                // Stack items up to max stack size
                int totalCount = item.getCount() + droppedItem.getCount();
                int maxStack = Item.MAX_STACK_SIZE;

                if (totalCount <= maxStack) {
                    // Can fit entire stack
                    item.setCount(totalCount);
                    inventory.setItem(index, item);
                } else {
                    // Partial stack
                    item.setCount(maxStack);
                    droppedItem.setCount(totalCount - maxStack);
                    inventory.setItem(index, item);
                    returnItemToSlot(droppedItem);  // Return excess to original slot
                }
            } else {
                // Swap items
                Item tempItem = item.copy();
                item = droppedItem.copy();
                returnItemToSlot(tempItem);
            }
        }

        updateVisuals();
        inventory.updateHotbarDisplay();
    } private void returnItemToSlot(Item itemToReturn) {
        if (itemToReturn == null) return;

        // First try to stack with existing items
        for (Actor actor : stage.getActors()) {
            if (actor instanceof InventorySlot) {
                InventorySlot slot = (InventorySlot) actor;
                if (slot.item != null && slot.item.canStackWith(itemToReturn)) {
                    int spaceAvailable = Item.MAX_STACK_SIZE - slot.item.getCount();
                    if (spaceAvailable > 0) {
                        int amountToAdd = Math.min(spaceAvailable, itemToReturn.getCount());
                        slot.item.setCount(slot.item.getCount() + amountToAdd);
                        itemToReturn.setCount(itemToReturn.getCount() - amountToAdd);
                        slot.updateVisuals();
                        if (itemToReturn.getCount() <= 0) {
                            return;
                        }
                    }
                }
            }
        }

        // If we still have items, find an empty slot
        for (Actor actor : stage.getActors()) {
            if (actor instanceof InventorySlot) {
                InventorySlot slot = (InventorySlot) actor;
                if (slot.item == null) {
                    slot.item = itemToReturn.copy();
                    slot.updateVisuals();
                    return;
                }
            }
        }
    }
    private void handleRightClick() {
        if (item != null && count > 1) {
            // Split single item
            Item singleItem = item.copy();
            singleItem.setCount(1);
            count--;
            item.setCount(count);

            // Try to add to first empty slot or held item
            if (craftingSystem.isHoldingItem()) {
                Item heldItem = craftingSystem.getHeldItem();
                if (heldItem.canStackWith(singleItem) &&
                    heldItem.getCount() < Item.MAX_STACK_SIZE) {
                    heldItem.setCount(heldItem.getCount() + 1);
                } else {
                    // Try to find empty slot
                    boolean placed = false;
                    for (Actor actor : stage.getActors()) {
                        if (actor instanceof InventorySlot) {
                            InventorySlot slot = (InventorySlot) actor;
                            if (slot.item == null) {
                                slot.setItem(singleItem);
                                placed = true;
                                break;
                            }
                        }
                    }
                    if (!placed) {
                        // Return item if no empty slot found
                        count++;
                        item.setCount(count);
                    }
                }
            } else {
                craftingSystem.setHeldItem(singleItem, 1);
            }
            updateVisuals();
        }
    }
    @Override
    public void act(float delta) {
        super.act(delta);
        if (isDragging && cursorItemDisplay != null) {
            // Update cursor item position
            cursorItemDisplay.setPosition(
                Gdx.input.getX() - 16,
                Gdx.graphics.getHeight() - Gdx.input.getY() - 16
            );
        }
    }
    private boolean canAcceptItem(DragAndDrop.Payload payload) {
        Item draggedItem = (Item) payload.getObject();
        Item currentItem = inventory.getItem(index);

        return currentItem == null ||
            (currentItem.getName().equals(draggedItem.getName()) &&
                currentItem.getCount() + draggedItem.getCount() <= Item.MAX_STACK_SIZE);
    }


    public void setItem(Item newItem) {
        if (newItem != null) {
            this.item = newItem.copy();
            this.count = newItem.getCount();
        } else {
            this.item = null;
            this.count = 0;
        }
        updateVisuals();
    }

    public void updateVisuals() {
        clearChildren();

        if (item != null && count > 0) {
            itemImage.setDrawable(new TextureRegionDrawable(item.getIcon()));
            add(itemImage).size(32).center();

            if (count > 1) {
                countLabel.setText(String.valueOf(count));
                addActor(countLabel);
                countLabel.setPosition(getWidth() - 8, 8);
            }

            // Update inventory data
            inventory.setItem(index, item);
        }

        // Special handling for hotbar slots (first 9)
        if (index < Inventory.HOTBAR_SIZE) {
            boolean isSelected = inventory.getSelectedIndex() == index;
            setBackground(new TextureRegionDrawable(
                TextureManager.getGameAtlas().findRegion(
                    isSelected ? "slot_selected" : "slot_normal"
                )
            ));
        }
    }

    public void refreshFromInventory() {
        Item currentItem = inventory.getItem(index);
        if (currentItem != null) {
            if (item == null || !item.getName().equals(currentItem.getName())
                || item.getCount() != currentItem.getCount()) {
                setItem(currentItem.copy());
            }
        } else if (item != null) {
            setItem(null);
        }
        updateVisuals();
    }


}


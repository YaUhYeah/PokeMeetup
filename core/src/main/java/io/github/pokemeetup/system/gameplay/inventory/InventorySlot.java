package io.github.pokemeetup.system.gameplay.inventory;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.*;
import io.github.pokemeetup.utils.TextureManager;

public class InventorySlot extends Table {
    private static final float HOVER_SCALE = 1.05f;
    private static final float HOVER_DURATION = 0.1f;

    private final Inventory inventory;
    private final int index;
    private final Image itemImage;
    private final Skin skin;
    private final Stage stage;
    private final Label countLabel;
    private final boolean isHotbarSlot;

    private Item item;
    private int count;

    public InventorySlot(Inventory inventory, int index, boolean isHotbarSlot, Stage stage, Skin skin, DragAndDrop dragAndDrop) {
        this.inventory = inventory;
        this.isHotbarSlot = isHotbarSlot;
        this.index = index;
        this.stage = stage;
        this.skin = skin;

        TextureAtlas gameAtlas = TextureManager.getGameAtlas();
        setBackground(new TextureRegionDrawable(gameAtlas.findRegion("slot_normal")));

        Table contentStack = new Table();
        contentStack.setFillParent(true);

        countLabel = new Label("", skin);
        countLabel.setColor(Color.WHITE);
        itemImage = new Image();
        contentStack.add(itemImage).expand().center();
        add(contentStack).grow();

        this.setTouchable(Touchable.enabled);

        setupListeners();

        if (dragAndDrop != null) {
            setupDragAndDrop(dragAndDrop);
        }

        updateVisuals();
    }

    private void setupListeners() {
        addListener(new InputListener() {
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

        // Add hover effect
        addListener(new InputListener() {
            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                addAction(Actions.scaleTo(HOVER_SCALE, HOVER_SCALE, HOVER_DURATION, Interpolation.smooth));
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                addAction(Actions.scaleTo(1f, 1f, HOVER_DURATION, Interpolation.smooth));
            }
        });
    }

    private void handleLeftClick() {
        if (!inventory.isHoldingItem() && item != null) {
            // Pick up item
            Item pickedItem = item.copy();
            inventory.setHeldItem(pickedItem, item.getCount());
            setItem(null, 0);
        } else if (inventory.isHoldingItem()) {
            Item heldItem = inventory.getHeldItem();
            int heldCount = inventory.getHeldItemCount();

            if (item == null) {
                // Place held item
                setItem(heldItem.copy(), heldCount);
                inventory.clearHeldItem();
            } else if (item.canStackWith(heldItem)) {
                // Stack items
                int totalCount = item.getCount() + heldCount;
                if (totalCount <= Item.MAX_STACK_SIZE) {
                    item.setCount(totalCount);
                    inventory.clearHeldItem();
                } else {
                    int remaining = totalCount - Item.MAX_STACK_SIZE;
                    item.setCount(Item.MAX_STACK_SIZE);
                    inventory.setHeldItem(heldItem.copy(), remaining);
                }
            } else {
                // Swap items
                Item tempItem = item.copy();
                int tempCount = item.getCount();
                setItem(heldItem.copy(), heldCount);
                inventory.setHeldItem(tempItem, tempCount);
            }
        }
        updateVisuals();
    }

    private void handleRightClick() {
        if (inventory.isHoldingItem()) {
            Item heldItem = inventory.getHeldItem();
            if (item == null) {
                // Place one item
                setItem(heldItem.copy(), 1);
                decreaseHeldItemCount();
            } else if (item.canStackWith(heldItem) && item.getCount() < Item.MAX_STACK_SIZE) {
                // Add one item to stack
                item.setCount(item.getCount() + 1);
                decreaseHeldItemCount();
            }
        } else if (item != null) {
            if (item.getCount() > 1) {
                // Split stack
                int halfCount = item.getCount() / 2;
                Item splitItem = item.copy();
                splitItem.setCount(halfCount);
                item.setCount(item.getCount() - halfCount);
                inventory.setHeldItem(splitItem, halfCount);
            } else {
                // Pick up single item
                inventory.setHeldItem(item.copy(), item.getCount());
                setItem(null, 0);
            }
        }
        updateVisuals();
    }

    private void decreaseHeldItemCount() {
        if (inventory.getHeldItemCount() > 1) {
            inventory.setHeldItem(inventory.getHeldItem(), inventory.getHeldItemCount() - 1);
        } else {
            inventory.clearHeldItem();
        }
    }

    private void setItem(Item newItem, int newCount) {
        if (newItem != null && newCount > 0) {
            this.item = newItem.copy();
            this.count = newCount;
            this.item.setCount(newCount);
            if (isHotbarSlot) {
                inventory.setHotbarItemAtSlot(index, this.item);
            } else {
                inventory.setItemAtSlot(index, this.item);
            }
        } else {
            this.item = null;
            this.count = 0;
            if (isHotbarSlot) {
                inventory.setHotbarItemAtSlot(index, null);
            } else {
                inventory.setItemAtSlot(index, null);
            }
        }
        updateVisuals();
    }

    public void updateVisuals() {
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


    private void setupDragAndDrop(DragAndDrop dragAndDrop) {
        // Source (dragging from this slot)
        dragAndDrop.addSource(new DragAndDrop.Source(this) {
            @Override
            public DragAndDrop.Payload dragStart(InputEvent event, float x, float y, int pointer) {
                if (item == null) return null;

                DragAndDrop.Payload payload = new DragAndDrop.Payload();
                payload.setObject(item.copy());

                // Create visual representation of the dragged item
                Image dragImage = new Image(item.getIcon());
                dragImage.setSize(32, 32);

                payload.setDragActor(dragImage);

                // Remove the item from this slot
                setItem(null, 0);

                return payload;
            }


            public void dragStop(InputEvent event, DragAndDrop.Payload payload,
                                 DragAndDrop.Target target, float x, float y, int pointer) {
                if (target == null) {
                    // Return item back to this slot if dropped outside any target
                    Item returnedItem = (Item) payload.getObject();
                    setItem(returnedItem, returnedItem.getCount());
                }
            }
        });

        // Target (dropping into this slot)
        dragAndDrop.addTarget(new DragAndDrop.Target(this) {
            @Override
            public boolean drag(DragAndDrop.Source source, DragAndDrop.Payload payload,
                                float x, float y, int pointer) {
                return true; // Accept all drops
            }

            @Override
            public void drop(DragAndDrop.Source source, DragAndDrop.Payload payload,
                             float x, float y, int pointer) {
                Item droppedItem = (Item) payload.getObject();

                if (item == null) {
                    // Place the item in this slot
                    setItem(droppedItem, droppedItem.getCount());
                } else if (item.canStackWith(droppedItem)) {
                    // Stack items
                    int totalCount = item.getCount() + droppedItem.getCount();
                    if (totalCount <= Item.MAX_STACK_SIZE) {
                        item.setCount(totalCount);
                    } else {
                        int remaining = totalCount - Item.MAX_STACK_SIZE;
                        item.setCount(Item.MAX_STACK_SIZE);

                        // Return remaining items to source slot
                        if (source.getActor() instanceof InventorySlot) {
                            ((InventorySlot) source.getActor()).setItem(droppedItem.copy(), remaining);
                        } else {
                            // Handle other source actors if necessary
                        }
                    }
                } else {
                    // Swap items
                    Item tempItem = item.copy();
                    int tempCount = item.getCount();
                    setItem(droppedItem, droppedItem.getCount());

                    // Set the original item back to source slot
                    if (source.getActor() instanceof InventorySlot) {
                        ((InventorySlot) source.getActor()).setItem(tempItem, tempCount);
                    } else {
                        // Handle other source actors if necessary
                    }
                }
            }
        });
    }
}

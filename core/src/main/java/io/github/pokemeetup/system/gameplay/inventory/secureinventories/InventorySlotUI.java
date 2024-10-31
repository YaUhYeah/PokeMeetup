package io.github.pokemeetup.system.gameplay.inventory.secureinventories;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import io.github.pokemeetup.screens.InventoryScreen;
import io.github.pokemeetup.system.gameplay.inventory.Item;
import io.github.pokemeetup.system.gameplay.inventory.ItemManager;
import io.github.pokemeetup.utils.TextureManager;

import java.util.function.Consumer;

public class InventorySlotUI extends Table {
    private final InventorySlotData slotData;
    private final Image itemImage;
    private final Label countLabel;
    private final DragAndDrop dragAndDrop;
    private final InventoryScreen inventoryScreen;

    private final Consumer<InventorySlotData> onChange;
    private final Skin skin;

    public InventorySlotUI(InventorySlotData slotData, Skin skin,
                           DragAndDrop dragAndDrop, Consumer<InventorySlotData> onChange,InventoryScreen inventoryScreen) {
        this.slotData = slotData;
        this.skin = skin;
        this.dragAndDrop = dragAndDrop;
        this.inventoryScreen = inventoryScreen;
        this.onChange = onChange;

        setBackground(new TextureRegionDrawable(
            TextureManager.getGameAtlas().findRegion("slot_normal")
        ));

        // Setup UI components
        itemImage = new Image();
        itemImage.setSize(32, 32);

        countLabel = new Label("", skin);
        countLabel.setVisible(false);

        add(itemImage).size(32).center();
        add(countLabel).bottom().right();

        setupDragAndDrop();
        setupListeners();
        updateVisuals();
    }
    private void handleLeftClick() {
        InventorySlotData heldItem = inventoryScreen.getHeldItem();
        if (heldItem == null || heldItem.isEmpty()) {
            // Player is not holding an item
            if (!slotData.isEmpty()) {
                // Pick up the item from the slot
                InventorySlotData pickedItem = slotData.copy();
                slotData.clear();
                inventoryScreen.setHeldItem(pickedItem);
                notifyChange();
                updateVisuals();
            }
        } else {
            // Player is holding an item
            if (slotData.isEmpty()) {
                // Place held item into the slot
                slotData.setItem(heldItem.getItemId(), heldItem.getCount());
                inventoryScreen.setHeldItem(null); // Clear held item
                notifyChange();
                updateVisuals();
            } else {
                // Slot has an item
                if (slotData.getItemId().equals(heldItem.getItemId()) &&
                    slotData.getCount() < Item.MAX_STACK_SIZE) {
                    // Items can stack
                    int total = slotData.getCount() + heldItem.getCount();
                    int maxStack = Item.MAX_STACK_SIZE;
                    if (total <= maxStack) {
                        // All items fit in the slot
                        slotData.setCount(total);
                        inventoryScreen.setHeldItem(null); // Clear held item
                    } else {
                        // Only some items fit
                        slotData.setCount(maxStack);
                        heldItem.setCount(total - maxStack);
                    }
                    notifyChange();
                    updateVisuals();
                } else {
                    // Swap items
                    InventorySlotData temp = slotData.copy();
                    slotData.setItem(heldItem.getItemId(), heldItem.getCount());
                    inventoryScreen.setHeldItem(temp);
                    notifyChange();
                    updateVisuals();
                }
            }
        }
    }

    private void handleRightClick() {
        InventorySlotData heldItem = inventoryScreen.getHeldItem();
        if (heldItem == null || heldItem.isEmpty()) {
            // Player is not holding an item
            if (!slotData.isEmpty()) {
                if (slotData.getCount() > 1) {
                    // Take half of the items
                    int halfCount = slotData.getCount() / 2;
                    InventorySlotData pickedItem = slotData.copy();
                    pickedItem.setCount(halfCount);
                    slotData.setCount(slotData.getCount() - halfCount);
                    inventoryScreen.setHeldItem(pickedItem);
                } else {
                    // Only one item, pick it up
                    InventorySlotData pickedItem = slotData.copy();
                    slotData.clear();
                    inventoryScreen.setHeldItem(pickedItem);
                }
                notifyChange();
                updateVisuals();
            }
        } else {
            // Player is holding an item
            if (slotData.isEmpty()) {
                // Place one item into the slot
                slotData.setItem(heldItem.getItemId(), 1);
                heldItem.setCount(heldItem.getCount() - 1);
                if (heldItem.getCount() == 0) {
                    inventoryScreen.setHeldItem(null);
                }
                notifyChange();
                updateVisuals();
            } else {
                if (slotData.getItemId().equals(heldItem.getItemId()) &&
                    slotData.getCount() < Item.MAX_STACK_SIZE) {
                    // Add one item to the slot
                    slotData.setCount(slotData.getCount() + 1);
                    heldItem.setCount(heldItem.getCount() - 1);
                    if (heldItem.getCount() == 0) {
                        inventoryScreen.setHeldItem(null);
                    }
                    notifyChange();
                    updateVisuals();
                }
            }
        }
    }


    private void setupListeners() {
        addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (event.getButton() == Input.Buttons.LEFT) {
                    handleLeftClick();
                } else if (event.getButton() == Input.Buttons.RIGHT) {
                    handleRightClick();
                }
            }
        });
    }

    private void setupDragAndDrop() {
        // Source (dragging from this slot)
        dragAndDrop.addSource(new DragAndDrop.Source(this) {
            @Override
            public DragAndDrop.Payload dragStart(InputEvent event, float x, float y, int pointer) {
                if (slotData.isEmpty()) return null;

                DragAndDrop.Payload payload = new DragAndDrop.Payload();

                // Create drag actor
                Table dragActor = new Table();
                dragActor.add(new Image(
                    ItemManager.getItem(slotData.getItemId()).getIcon()
                )).size(32);

                if (slotData.getCount() > 1) {
                    dragActor.add(new Label(
                        String.valueOf(slotData.getCount()),
                        skin
                    )).bottom().right();
                }

                payload.setDragActor(dragActor);
                payload.setObject(slotData.copy());

                // Clear source slot
                slotData.clear();
                updateVisuals();
                notifyChange();

                return payload;
            }
        });

        // Target (dropping into this slot)
        dragAndDrop.addTarget(new DragAndDrop.Target(this) {
            @Override
            public boolean drag(DragAndDrop.Source source, DragAndDrop.Payload payload,
                                float x, float y, int pointer) {
                return canAcceptDrop(payload);
            }

            @Override
            public void drop(DragAndDrop.Source source, DragAndDrop.Payload payload,
                             float x, float y, int pointer) {
                handleDrop(payload);
            }
        });
    }

    private boolean canAcceptDrop(DragAndDrop.Payload payload) {
        if (!(payload.getObject() instanceof InventorySlotData)) return false;

        InventorySlotData draggedSlot = (InventorySlotData) payload.getObject();
        return slotData.isEmpty() || (
            slotData.getItemId().equals(draggedSlot.getItemId()) &&
                slotData.getCount() + draggedSlot.getCount() <= Item.MAX_STACK_SIZE
        );
    }

    private void handleDrop(DragAndDrop.Payload payload) {
        InventorySlotData draggedSlot = (InventorySlotData) payload.getObject();

        if (slotData.isEmpty()) {
            slotData.setItem(draggedSlot.getItemId(), draggedSlot.getCount());
        } else {
            int newCount = slotData.getCount() + draggedSlot.getCount();
            if (newCount <= Item.MAX_STACK_SIZE) {
                slotData.setItem(slotData.getItemId(), newCount);
            }
        }

        updateVisuals();
        notifyChange();
    }

    private void notifyChange() {
        if (onChange != null) {
            onChange.accept(slotData);
        }
    }

    public void updateVisuals() {
        if (!slotData.isEmpty()) {
            Item item = ItemManager.getItem(slotData.getItemId());
            itemImage.setDrawable(new TextureRegionDrawable(item.getIcon()));

            if (slotData.getCount() > 1) {
                countLabel.setText(String.valueOf(slotData.getCount()));
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

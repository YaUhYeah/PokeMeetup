package io.github.pokemeetup.screens.otherui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.*;
import io.github.pokemeetup.screens.InventoryScreen;
import io.github.pokemeetup.system.data.ItemData;
import io.github.pokemeetup.system.gameplay.inventory.Inventory;
import io.github.pokemeetup.system.gameplay.inventory.Item;
import io.github.pokemeetup.system.gameplay.inventory.secureinventories.InventorySlotData;
import io.github.pokemeetup.system.gameplay.inventory.secureinventories.InventorySlotDataObserver;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.TextureManager;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

public class InventorySlotUI extends Table implements InventorySlotDataObserver {
    private final InventorySlotData slotData;
    private final Image itemImage;
    private final Label countLabel;
    private final InventoryScreen inventoryScreen;
    private final Consumer<InventorySlotData> onChange;
    private final Skin skin;

    public InventorySlotUI(InventorySlotData slotData, Skin skin, Consumer<InventorySlotData> onChange, InventoryScreen inventoryScreen) {
        this.slotData = slotData;
        this.skin = skin;
        this.inventoryScreen = inventoryScreen;
        this.onChange = onChange;
        this.setTouchable(Touchable.enabled);

        // Initialize slot background
        setBackground(new TextureRegionDrawable(TextureManager.ui.findRegion("slot_normal")));
        slotData.addObserver(this);

        // Initialize UI components
        itemImage = new Image();
        itemImage.setSize(32, 32);
        countLabel = new Label("", skin);
        countLabel.setVisible(false);

        // Layout setup
        add(itemImage).size(32).center();
        add(countLabel).bottom().right();

        // Setup input listeners and visual effects
        setupInput();
        addHoverEffects();
        updateVisuals();
    }

    private void setupInput() {
        addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                boolean isShiftPressed = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
                if (button == Input.Buttons.LEFT) {
                    if (isShiftPressed) {
                        handleShiftClick();
                    } else {
                        handleLeftClick();
                    }
                } else if (button == Input.Buttons.RIGHT) {
//                    handleRightClick();
                }
                return true; // Indicate that we have handled the event
            }
        });
    }

    private void handleLeftClick() {
        Item heldItem = inventoryScreen.getHeldItem();
        if (heldItem == null || heldItem.isEmpty()) {
            if (pickUpItem()) {
                updateVisuals();
            }
        } else {
            if (handleItemPlacement(heldItem)) {
                updateVisuals();
            }
        }
    }


    private void handleRightClick() {
        Item heldItem = inventoryScreen.getHeldItem();
        if (heldItem == null || heldItem.isEmpty()) {
            pickUpHalf(); // When no item is held, pick up half
        } else {
            placeOneItem(); // When holding an item, place one
        }
        updateVisuals();
    }


    private void placeOneItem() {
        Item heldItem = inventoryScreen.getHeldItem();
        if (heldItem == null || heldItem.isEmpty()) return;

        if (slotData.isEmpty()) {
            // Place one item in empty slot
            slotData.setItem(heldItem.getName(), 1, heldItem.getUuid());
            heldItem.setCount(heldItem.getCount() - 1);

            if (heldItem.getCount() <= 0) {
                inventoryScreen.clearHeldItem();
            }
        } else if (slotData.getItemId().equals(heldItem.getName()) &&
            slotData.getCount() < Item.MAX_STACK_SIZE) {
            // Add one to existing stack if there's room
            slotData.setItem(slotData.getItemId(), slotData.getCount() + 1, slotData.getItem().getUuid());
            heldItem.setCount(heldItem.getCount() - 1);

            if (heldItem.getCount() <= 0) {
                inventoryScreen.clearHeldItem();
            }
        }

        notifyChange();
    }

    private void handleShiftClick() {
        Item slotItem = slotData.getItem();
        if (slotItem == null || slotItem.isEmpty()) {
            return; // Nothing to shift-click
        }

        // Attempt to move the entire stack to another inventory area
//        if (inventoryScreen.transferItemToOtherInventory(slotData)) {
//            notifyChange();
//            updateVisuals();
//        }
    }

    private boolean pickUpItem() {
        if (!slotData.isEmpty()) {
            Item currentItem = slotData.getItem().copy();
            inventoryScreen.setHeldItem(currentItem);
            slotData.clear();
            notifyChange();
            return true;
        }
        return false;
    }

    private boolean handleItemPlacement(Item heldItem) {
        if (heldItem == null || heldItem.isEmpty()) return false;

        if (slotData.isEmpty()) {
            // Place item in empty slot
            slotData.setItem(heldItem.getName(), heldItem.getCount(), heldItem.getUuid());
            inventoryScreen.clearHeldItem();
            notifyChange();
            return true;
        } else if (slotData.getItemId().equals(heldItem.getName())) {
            // Try to stack items
            int totalCount = slotData.getCount() + heldItem.getCount();
            if (totalCount <= Item.MAX_STACK_SIZE) {
                slotData.setItem(heldItem.getName(), totalCount, slotData.getItem().getUuid());
                inventoryScreen.clearHeldItem();
                notifyChange();
                return true;
            } else {
                // Handle stack overflow
                int remaining = totalCount - Item.MAX_STACK_SIZE;
                slotData.setItem(heldItem.getName(), Item.MAX_STACK_SIZE, slotData.getItem().getUuid());
                heldItem.setCount(remaining);
                inventoryScreen.setHeldItem(heldItem);
                notifyChange();
                return true;
            }
        } else {
            // Swap items
            Item currentItem = slotData.getItem();
            slotData.setItem(heldItem.getName(), heldItem.getCount(), heldItem.getUuid());
            inventoryScreen.setHeldItem(currentItem);
            notifyChange();
            return true;
        }
    }

    private void pickUpHalf() {
        synchronized (inventoryScreen.getInventory()) {
            if (slotData.isEmpty()) return;

            int originalCount = slotData.getCount();
            int halfCount = Math.max(1, originalCount / 2);
            int remainingCount = originalCount - halfCount;

            GameLogger.info("Splitting stack: original=" + originalCount +
                ", taking=" + halfCount + ", remaining=" + remainingCount);

            // Create new item with new UUID
            Item newHeldItem = new Item(slotData.getItemId());
            newHeldItem.setCount(halfCount);
            newHeldItem.setUuid(UUID.randomUUID());

            // First clear the slot
            slotData.clear();

            // Then set remaining amount if any
            if (remainingCount > 0) {
                slotData.setItem(slotData.getItemId(), remainingCount, UUID.randomUUID());
            }

            // Finally set held item
            inventoryScreen.setHeldItem(newHeldItem);

            // Only notify once at the end
            inventoryScreen.getInventory().validateAndRepair();
        }
    }

    private void notifyChange() {
        if (onChange != null) {
            try {
                // Get count before
                int beforeCount = inventoryScreen.getInventory().getAllItems().stream()
                    .filter(Objects::nonNull)
                    .mapToInt(ItemData::getCount)
                    .sum();

                // Only notify once
                onChange.accept(slotData);

                // Don't update inventory again - the change notification should handle it
                // Remove: inventory.update();

                // Log for verification
                int afterCount = inventoryScreen.getInventory().getAllItems().stream()
                    .filter(Objects::nonNull)
                    .mapToInt(ItemData::getCount)
                    .sum();

                if (beforeCount != afterCount) {
                    GameLogger.info("Item count changed: " + beforeCount + " -> " + afterCount);
                }
            } catch (Exception e) {
                GameLogger.error("Error in notifyChange: " + e.getMessage());
            }
        }
    }

    private void addHoverEffects() {
        addListener(new InputListener() {
            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                setBackground(new TextureRegionDrawable(TextureManager.ui.findRegion("slot_selected")));
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                setBackground(new TextureRegionDrawable(TextureManager.ui.findRegion("slot_normal")));
            }
        });
    }

    @Override
    public void onSlotDataChanged() {
        updateVisuals();
    }

    public void updateVisuals() {
        if (!slotData.isEmpty()) {
            Item item = slotData.getItem();
            if (item != null && !item.isEmpty()) {
                itemImage.setDrawable(new TextureRegionDrawable(item.getIcon()));
                countLabel.setVisible(true);
                countLabel.setText(String.valueOf(item.getCount()));
                itemImage.setVisible(true);
            } else {
                itemImage.setDrawable(null);
                itemImage.setVisible(false);
                countLabel.setVisible(false);
            }
        } else {
            itemImage.setDrawable(null);
            itemImage.setVisible(false);
            countLabel.setVisible(false);
        }
    }
}

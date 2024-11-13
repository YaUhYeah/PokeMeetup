    package io.github.pokemeetup.screens.otherui;

    import com.badlogic.gdx.Gdx;
    import com.badlogic.gdx.Input;
    import com.badlogic.gdx.graphics.g2d.TextureAtlas;
    import com.badlogic.gdx.graphics.g2d.TextureRegion;
    import com.badlogic.gdx.scenes.scene2d.*;
    import com.badlogic.gdx.scenes.scene2d.ui.*;
    import com.badlogic.gdx.scenes.scene2d.utils.*;
    import com.badlogic.gdx.utils.Align;
    import io.github.pokemeetup.screens.InventoryScreen;
    import io.github.pokemeetup.system.data.ItemData;
    import io.github.pokemeetup.system.gameplay.inventory.Inventory;
    import io.github.pokemeetup.system.gameplay.inventory.Item;
    import io.github.pokemeetup.system.gameplay.inventory.secureinventories.InventorySlotData;
    import io.github.pokemeetup.system.gameplay.inventory.secureinventories.InventorySlotDataObserver;
    import io.github.pokemeetup.utils.GameLogger;
    import io.github.pokemeetup.utils.TextureManager;

    import java.util.UUID;

    public class InventorySlotUI extends Table implements InventorySlotDataObserver {
        private static final long CLICK_COOLDOWN = 250; // 250ms cooldown between clicks
        private static final int SLOT_SIZE = 40;
        private static final int ITEM_SIZE = 32;
        private final InventorySlotData slotData;
        private final InventoryScreen inventoryScreen;
        private final Skin skin;
        private Image itemImage;
        private Label countLabel;
        private long lastClickTime = 0;
        private boolean isProcessingClick = false;

        public InventorySlotUI(InventorySlotData slotData, Skin skin, InventoryScreen inventoryScreen) {
            this.slotData = slotData;
            this.skin = skin;
            this.inventoryScreen = inventoryScreen;
            this.setTouchable(Touchable.enabled);

            // Set the background of the slot
            setBackground(new TextureRegionDrawable(TextureManager.ui.findRegion("slot_normal")));
            slotData.addObserver(this);

            // Initialize UI components
            setupUI();

            // Setup input listeners and visual effects
            setupInput();
            addHoverEffects();
            updateVisuals();

            // Remove the redundant InputListener here
        }

        public void forceUpdate() {
            GameLogger.info("Force updating slot " + slotData.getSlotIndex());

            // Clear existing visuals
            clearVisuals();

            if (slotData != null && !slotData.isEmpty()) {
                ItemData item = slotData.getItemData();
                if (item != null) {
                    GameLogger.info("Forcing update for item: " + item.getItemId() +
                        " x" + item.getCount());
                    updateVisuals();
                }
            }

            invalidate();
            validate();
        }

        public int getSlotIndex() {
            return slotData != null ? slotData.getSlotIndex() : -1;
        }

        public InventorySlotData getSlotData() {
            return slotData;
        }

        private void setupUI() {
            clear();

            // Create stack for layering
            Stack stack = new Stack();
            stack.setSize(SLOT_SIZE, SLOT_SIZE);

            // Background (already set on table)

            // Item image
            itemImage = new Image();
            itemImage.setSize(ITEM_SIZE, ITEM_SIZE);
            Table imageContainer = new Table();
            imageContainer.add(itemImage).size(ITEM_SIZE);
            stack.add(imageContainer);

            // Count label
            countLabel = new Label("", skin);
            countLabel.setAlignment(Align.bottomRight);
            Table labelContainer = new Table();
            labelContainer.add(countLabel).expand().right().bottom().pad(4);
            stack.add(labelContainer);

            add(stack).size(SLOT_SIZE);
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
                        handleRightClick();
                    }
                    return true; // Consume the event
                }
            });
        }

        private void handleLeftClick() {
            synchronized (inventoryScreen.getInventory().getInventoryLock()) {
                try {
                    Item heldItem = inventoryScreen.getHeldItem();
                    int slotIndex = slotData.getSlotIndex();
                    Inventory inventory = inventoryScreen.getInventory();
                    ItemData slotItemData = inventory.getItemAt(slotIndex);

                    // PICKUP
                    if (heldItem == null && slotItemData != null) {
                        // Create held item from slot item data
                        Item newHeldItem = new Item(slotItemData.getItemId());
                        newHeldItem.setCount(slotItemData.getCount());
                        newHeldItem.setUuid(slotItemData.getUuid());

                        // Remove item from inventory slot
                        inventory.setItemAt(slotIndex, null);

                        // Update held item
                        inventoryScreen.setHeldItem(newHeldItem);

                        GameLogger.info("Picked up item: " + newHeldItem.getName() + " x" + newHeldItem.getCount());

                    }
                    // PLACE/STACK
                    else if (heldItem != null) {
                        // Empty slot - place
                        if (slotItemData == null) {
                            // Place held item into slot
                            ItemData newItemData = new ItemData(heldItem.getName(), heldItem.getCount(), heldItem.getUuid());
                            inventory.setItemAt(slotIndex, newItemData);

                            // Clear held item
                            inventoryScreen.setHeldItem(null);

                            GameLogger.info("Placed item: " + newItemData.getItemId() + " x" + newItemData.getCount());

                        }
                        // Stack same items
                        else if (slotItemData.getItemId().equals(heldItem.getName())) {
                            int totalCount = slotItemData.getCount() + heldItem.getCount();
                            if (totalCount <= Item.MAX_STACK_SIZE) {
                                // Stack items
                                ItemData newStack = new ItemData(slotItemData.getItemId(), totalCount, slotItemData.getUuid());
                                inventory.setItemAt(slotIndex, newStack);

                                // Clear held item
                                inventoryScreen.setHeldItem(null);

                                GameLogger.info("Stacked items to: " + newStack.getCount());

                            } else {
                                // Stack up to max, keep remaining in held item
                                int spaceLeft = Item.MAX_STACK_SIZE - slotItemData.getCount();
                                if (spaceLeft > 0) {
                                    ItemData newSlotItem = new ItemData(slotItemData.getItemId(), Item.MAX_STACK_SIZE, slotItemData.getUuid());
                                    inventory.setItemAt(slotIndex, newSlotItem);

                                    // Update held item count
                                    heldItem.setCount(heldItem.getCount() - spaceLeft);
                                    inventoryScreen.setHeldItem(heldItem);

                                    GameLogger.info("Partially stacked items, slot now has: " + newSlotItem.getCount() + ", held item remaining: " + heldItem.getCount());

                                } else {
                                    // Can't stack any more
                                }
                            }
                        }
                        // Swap different items
                        else {
                            // Swap items
                            ItemData newSlotItemData = new ItemData(heldItem.getName(), heldItem.getCount(), heldItem.getUuid());
                            inventory.setItemAt(slotIndex, newSlotItemData);

                            // Set held item to the item previously in the slot
                            Item newHeldItem = new Item(slotItemData.getItemId());
                            newHeldItem.setCount(slotItemData.getCount());
                            newHeldItem.setUuid(slotItemData.getUuid());
                            inventoryScreen.setHeldItem(newHeldItem);

                            GameLogger.info("Swapped items");

                        }
                    }

                } catch (Exception e) {
                    GameLogger.error("Error handling left click: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }

        private void handleShiftClick() {
        }



        private boolean handleRightClick() {
            if (isProcessingClick) return false;
            isProcessingClick = true;

            try {
                Inventory inventory = inventoryScreen.getInventory();
                synchronized (inventory.getInventoryLock()) {
                    Item heldItem = inventoryScreen.getHeldItem();
                    ItemData currentSlotItem = slotData.getItemData();

                    GameLogger.info("Right click - Held item: " + (heldItem != null ? heldItem.getName() + " x" + heldItem.getCount() : "null") +
                        ", Slot item: " + (currentSlotItem != null ? currentSlotItem.getItemId() + " x" + currentSlotItem.getCount() : "null"));

                    if (heldItem == null) {
                        // Split stack
                        if (currentSlotItem != null && currentSlotItem.getCount() > 1) {
                            int splitAmount = (currentSlotItem.getCount() + 1) / 2;

                            // Update current slot
                            ItemData remainingStack = new ItemData(
                                currentSlotItem.getItemId(),
                                currentSlotItem.getCount() - splitAmount,
                                currentSlotItem.getUuid()
                            );
                            inventory.setItemAt(slotData.getSlotIndex(), remainingStack);

                            // Create held item with split amount
                            Item splitStack = new Item(currentSlotItem.getItemId());
                            splitStack.setCount(splitAmount);
                            splitStack.setUuid(UUID.randomUUID());
                            inventoryScreen.setHeldItem(splitStack);

                            GameLogger.info("Split stack: Slot has " + remainingStack.getCount() +
                                ", Holding " + splitStack.getCount());
                            return true;
                        }
                    } else {
                        // Place single item
                        if (currentSlotItem == null ||
                            (currentSlotItem.getItemId().equals(heldItem.getName()) &&
                                currentSlotItem.getCount() < Item.MAX_STACK_SIZE)) {

                            if (currentSlotItem == null) {
                                // Create new stack of 1
                                ItemData newStack = new ItemData(
                                    heldItem.getName(),
                                    1,
                                    UUID.randomUUID()
                                );
                                inventory.setItemAt(slotData.getSlotIndex(), newStack);
                            } else {
                                // Add 1 to existing stack
                                ItemData updatedStack = new ItemData(
                                    currentSlotItem.getItemId(),
                                    currentSlotItem.getCount() + 1,
                                    currentSlotItem.getUuid()
                                );
                                inventory.setItemAt(slotData.getSlotIndex(), updatedStack);
                            }

                            // Update held item
                            heldItem.setCount(heldItem.getCount() - 1);
                            if (heldItem.getCount() <= 0) {
                                inventoryScreen.setHeldItem(null);
                            } else {
                                inventoryScreen.setHeldItem(heldItem);
                            }

                            GameLogger.info("Placed single item. Remaining held: " +
                                (heldItem.getCount() <= 0 ? "none" : heldItem.getCount()));
                            return true;
                        }
                    }
                }
            } finally {
                isProcessingClick = false;
            }
            return false;
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


        private void clearVisuals() {
            if (itemImage != null) {
                itemImage.setDrawable(null);
                itemImage.setVisible(false);
            }
            if (countLabel != null) {
                countLabel.setText("");
                countLabel.setVisible(false);
            }
        }

        @Override
        public void onSlotDataChanged() {
            updateVisuals();
        }

        public void updateVisuals() {
            try {
                clearVisuals();



                ItemData item = slotData.getItemData();
                if (item == null) {
                    GameLogger.error("ItemData is null for slot " + slotData.getSlotIndex());
                    return;
                }

                // Get texture
                TextureRegion texture = TextureManager.items.findRegion(item.getItemId().toLowerCase() + "_item");
                if (texture == null) {
                    texture = TextureManager.items.findRegion(item.getItemId().toLowerCase());
                    if (texture == null) {
                        GameLogger.error("Could not find texture for item: " + item.getItemId());
                        // Log available textures
                        for (TextureAtlas.AtlasRegion region : TextureManager.items.getRegions()) {
                            GameLogger.info("Available texture: " + region.name);
                        }
                        return;
                    }
                }

                if (itemImage == null) {
                    itemImage = new Image();
                    add(itemImage).size(32);
                }

                itemImage.setDrawable(new TextureRegionDrawable(texture));
                itemImage.setVisible(true);
                itemImage.setSize(32, 32);

                if (item.getCount() > 1) {
                    if (countLabel == null) {
                        countLabel = new Label("", skin);
                        add(countLabel).expand().right().bottom().pad(4);
                    }
                    countLabel.setText(String.valueOf(item.getCount()));
                    countLabel.setVisible(true);
                }

            } catch (Exception e) {
                GameLogger.error("Error updating slot visuals: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

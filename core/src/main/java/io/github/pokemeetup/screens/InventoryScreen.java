package io.github.pokemeetup.screens;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.data.ItemData;
import io.github.pokemeetup.system.gameplay.inventory.*;
import io.github.pokemeetup.system.gameplay.inventory.crafting.CraftingResult;
import io.github.pokemeetup.system.gameplay.inventory.crafting.CraftingSystem;
import io.github.pokemeetup.system.gameplay.inventory.secureinventories.InventorySlotData;
import io.github.pokemeetup.screens.otherui.InventorySlotUI;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.storage.InventoryConverter;
import io.github.pokemeetup.utils.TextureManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static io.github.pokemeetup.system.gameplay.inventory.Inventory.*;

public class InventoryScreen implements Screen {
    private static final float BACKGROUND_OPACITY = 0.5f;
    private static final int SLOT_SIZE = 40;
    private static final int PADDING = 10;

    private final Stage stage;
    private final SpriteBatch batch;
    private final ShapeRenderer shapeRenderer;
    private final Skin skin;
    private final Player player;
    private final Inventory inventory;
    private final GameClient gameClient;
    private final CraftingSystem craftingSystem;
    private final Object heldItemLock = new Object(); // Lock for heldItem synchronization
    private Group heldItemGroup;
    private Image heldItemImage;
    private Label heldItemCountLabel;
    // Changed from InventorySlotData to Item
    private Item heldItem = null; // Initially no item is held
    private List<InventorySlotData> hotbarSlots;
    private List<InventorySlotData> inventorySlots;
    // UI Components
    private Table mainTable;
    private Table craftingTable;
    private Table inventoryTable;
    private Table hotbarTable;
    private List<InventorySlotData> craftingSlots;
    private InventorySlotData craftingResultSlot;
    private InputMultiplexer inputMultiplexer;public boolean isOverInventory(float x, float y) {
        // Define the inventory bounds - adjust these values based on your layout
        float inventoryX = getStage().getWidth() * 0.1f; // 10% from left
        float inventoryY = getStage().getHeight() * 0.1f; // 10% from bottom
        float inventoryWidth = getStage().getWidth() * 0.8f; // 80% of screen width
        float inventoryHeight = getStage().getHeight() * 0.8f; // 80% of screen height

        return x >= inventoryX && x <= inventoryX + inventoryWidth &&
            y >= inventoryY && y <= inventoryY + inventoryHeight;
    }

    public InventoryScreen(Player player, Skin skin, GameClient gameClient) {
        this.player = player;
        this.skin = skin;
        this.gameClient = gameClient;
        this.stage = new Stage(new ScreenViewport());
        this.batch = new SpriteBatch();
        this.shapeRenderer = new ShapeRenderer();
// In InventoryScreen setup codes
        stage.getRoot().setColor(1, 1, 1, 0.7f);
        stage.getRoot().setZIndex(0); // Set lower than other UI elements

        // Initialize crafting system

        // Initialize held item display
        setupHeldItemDisplay();

        // Get the player's inventory
        this.inventory = player.getInventory();
        debugInitialInventoryState();
        craftingSystem = new CraftingSystem(inventory);

        // Initialize crafting slots
        craftingSlots = new ArrayList<>();
        for (int i = 0; i < 4; i++) { // 2x2 grid
            craftingSlots.add(new InventorySlotData(i));
        }
        craftingResultSlot = new InventorySlotData(-2); // Unique index for crafting result

        // Setup UI and input
        setupUI();
        setupInput();
    }
    public synchronized boolean pickUpItemFromSlot(Item item, int count) {
        synchronized (heldItemLock) {
            if (!canPickUpItem()) {
                return false;
            }

            Item newHeldItem = item.copy();
            newHeldItem.setCount(count);
            setHeldItemSafe(newHeldItem);
            return true;
        }
    }
    private synchronized void setHeldItemSafe(Item item) {
        synchronized (heldItemLock) {
            if (item != null && !item.isEmpty()) {
                this.heldItem = item.copy();
            } else {
                this.heldItem = null;
            }
            updateHeldItemDisplay();
        }
    }

    private void debugInitialInventoryState() {
        GameLogger.info("\nDEBUG - Initial State:");

        // Main Inventory
        GameLogger.info("Main Inventory:");
        List<ItemData> mainItems = inventory.getAllItems();
        for (int i = 0; i < mainItems.size(); i++) {
            ItemData item = mainItems.get(i);
            if (item != null) {
                GameLogger.info("Slot " + i + ": " + item.getItemId() + " x" + item.getCount());
            }
        }

        // Hotbar


        // Raw Inventory Array

    }

    /**

    public boolean placeHeldItemIntoSlot(InventorySlotData slotData) {
        synchronized (heldItemLock) {
            if (heldItem == null || heldItem.isEmpty()) {
                return false;
            }

            // Check if we can stack with existing item
            if (!slotData.isEmpty() && slotData.getItemId().equals(heldItem.getName())) {
                int totalCount = slotData.getCount() + heldItem.getCount();
                if (totalCount <= Item.MAX_STACK_SIZE) {
                    slotData.setItem(heldItem.getName(), totalCount);
                    setHeldItem(null);
                    return true;
                }
            } else if (slotData.isEmpty()) {
                // Place in empty slot
                slotData.setItem(heldItem.getName(), heldItem.getCount());
                setHeldItem(null);
                return true;
            }
            return false;
        }
    }

    /**
     * Checks if we can pick up an item.
     *
     * @return true if no item is currently held
     */
    // In InventoryScreen.java, add these methods:
    public boolean canPickUpItem() {
        synchronized (heldItemLock) {
            return heldItem == null || heldItem.isEmpty();
        }
    }


    private void setupHeldItemDisplay() {
        // Initialize held item image and count label
        heldItemImage = new Image();
        heldItemImage.setSize(32, 32);
        heldItemImage.setVisible(false);

        heldItemCountLabel = new Label("", skin);
        heldItemCountLabel.setVisible(false);

        // Create a Group to hold both the image and the label
        heldItemGroup = new Group();
        heldItemGroup.addActor(heldItemImage);
        heldItemGroup.addActor(heldItemCountLabel);

        // Add heldItemGroup to the stage
        stage.addActor(heldItemGroup);

        // Set to non-interactive to prevent blocking input
        heldItemGroup.setTouchable(Touchable.disabled);
    }

    private void setupUI() {
        // Main container with center alignment
        mainTable = new Table();
        mainTable.setFillParent(true);
        mainTable.center(); // Center all content

        // Create wrapper table for all content
        Table contentTable = new Table();

        // Create sections
        setupCraftingArea();
        setupInventoryArea();

        // Add sections to content table with center alignment
        contentTable.add(craftingTable).center().pad(20).row();
        contentTable.add(inventoryTable).center().pad(20).row();
        contentTable.add(hotbarTable).center().pad(20);

        // Add content table to main table
        mainTable.add(contentTable).center();

        // Add main table to stage
        stage.addActor(mainTable);
    }


    private void setupInventoryArea() {
        inventoryTable = new Table();
        inventoryTable.setBackground(createBackground());
        inventoryTable.pad(PADDING);
        inventoryTable.center();

        List<ItemData> items = inventory.getAllItems();
        inventorySlots = new ArrayList<>();

        int cols = 9;
        int rows = 3; // Corrected to 3 for 27 slots
        int slotIndex = 0;

        Table gridTable = new Table();
        gridTable.center();

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                InventorySlotData slotData = new InventorySlotData(slotIndex);
                slotData.setSlotType(InventorySlotData.SlotType.INVENTORY);

                if (slotIndex < items.size()) {
                    ItemData item = items.get(slotIndex);
                    if (item != null) {
                        slotData.setItem(item.getItemId(), item.getCount());
                    }
                }
                inventorySlots.add(slotData);

                InventorySlotUI slotUI = new InventorySlotUI(
                    slotData,
                    skin,
                    this::onSlotChanged,
                    this
                );
                gridTable.add(slotUI).size(SLOT_SIZE).space(2);

                slotIndex++;
            }
            gridTable.row();
        }

        inventoryTable.add(gridTable).center();
    }


    /**
     * Observer method called when an inventory slot changes.
     *
     * @param slotData The data of the slot that changed.
     */
    private void onSlotChanged(InventorySlotData slotData) {
        int slotIndex = slotData.getSlotIndex();

        synchronized (inventory) {
            if (slotIndex < 0 || slotIndex >= INVENTORY_SIZE) {
                GameLogger.error("Invalid slot index: " + slotIndex);
                return;
            }

            if (slotData.isEmpty()) {
                inventory.setItemAt(slotIndex, null);
            } else {
                String itemName = slotData.getItemId();
                int count = slotData.getCount();
                ItemData newItem = new ItemData(itemName, count, slotData.getItem().getUuid());
                inventory.setItemAt(slotIndex, newItem);
            }
        }
    }


    private Drawable createBackground() {
        return new TextureRegionDrawable(TextureManager.ui
            .findRegion("hotbar_bg"))
            .tint(new Color(0.2f, 0.2f, 0.2f, 0.9f));
    }
    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);
    }

    @Override
    public void render(float delta) {
        // Clear screen with semi-transparent background
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0, 0, 0, BACKGROUND_OPACITY);
        shapeRenderer.rect(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        shapeRenderer.end();

        // Update stage interactions (slots)
        stage.act(delta);

        // Draw held item above slots without affecting interactions
        updateHeldItemDisplay();

        // Draw all UI components in stage, including updated held item positioning
        stage.draw();
    }

    /**
     * Updates the visual display of the held item based on the current held item.
     */
    private void updateHeldItemDisplay() {
        synchronized (heldItemLock) {
            if (heldItem != null && heldItem.getCount() > 0) {
                heldItemImage.setVisible(true);
                heldItemCountLabel.setVisible(heldItem.getCount() > 1);

                // Get item details
                Item item = ItemManager.getItem(heldItem.getName());
                if (item != null) {
                    heldItemImage.setDrawable(new TextureRegionDrawable(item.getIcon()));
                } else {
                    GameLogger.info("Held item not found: " + heldItem.getName());
                    heldItemImage.setDrawable(null);
                    heldItemImage.setVisible(false);
                    heldItemCountLabel.setVisible(false);
                    return;
                }

                // Update the count label if more than one item
                if (heldItem.getCount() > 1) {
                    heldItemCountLabel.setText(String.valueOf(heldItem.getCount()));
                    heldItemCountLabel.setVisible(true);
                } else {
                    heldItemCountLabel.setVisible(false);
                }

                // Position held item group at the mouse cursor
                Vector2 stageCoords = stage.screenToStageCoordinates(new Vector2(Gdx.input.getX(), Gdx.input.getY()));
                heldItemGroup.setPosition(
                    stageCoords.x - heldItemImage.getWidth() / 2,
                    stageCoords.y - heldItemImage.getHeight() / 2
                );

                // Ensure held item is drawn above all other UI elements
                heldItemGroup.toFront();
            } else {
                heldItemImage.setVisible(false);
                heldItemCountLabel.setVisible(false);
            }
        }
    }


    /**
     * Gets the currently held item.
     *
     * @return The held Item, or null if none.
     */

    public Item getHeldItem() {
        synchronized (heldItemLock) {
            return heldItem;
        }
    }

    public void setHeldItem(Item heldItem) {
        synchronized (heldItemLock) {
            if (heldItem != null && !heldItem.isEmpty()) {
                this.heldItem = heldItem.copy(); // Deep copy
                GameLogger.info("Held item set to " + heldItem.getName() + " x" + heldItem.getCount() + " with UUID " + heldItem.getUuid());
            } else {
                this.heldItem = null;
            }
            updateHeldItemDisplay();
        }
    }




    /**
     * Clears the held item, setting it to null and updating the UI.
     */

    /**
     * Clears the held item, attempting to return it to the inventory.
     */
    private void setupCraftingArea() {
        craftingTable = new Table();
        craftingTable.setBackground(createBackground());
        craftingTable.pad(PADDING);

        // 2x2 crafting grid
        Table grid = new Table();
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                int slotIndex = i * 2 + j;
                InventorySlotData slotData = new InventorySlotData(slotIndex);
                slotData.setSlotType(InventorySlotData.SlotType.CRAFTING);

                InventorySlotUI slotUI = new InventorySlotUI(
                    slotData,
                    skin,
                    this::onCraftingSlotChanged,
                    this
                );
                grid.add(slotUI).size(SLOT_SIZE).space(2);
            }
            grid.row();
        }

        // Arrow image
        Image arrow = new Image(TextureManager.ui.findRegion("arrow"));

        // Result slot
        InventorySlotData resultSlotData = new InventorySlotData(-2); // Special index for result slot
        resultSlotData.setSlotType(InventorySlotData.SlotType.CRAFTING_RESULT);

        InventorySlotUI resultSlotUI = new InventorySlotUI(
            resultSlotData,
            skin,
            this::onCraftingResultChanged,
            this
        );

        craftingTable.add(grid);
        craftingTable.add(arrow).size(32, 32).pad(10);
        craftingTable.add(resultSlotUI).size(SLOT_SIZE);
    }

    public void returnHeldItem() {
        synchronized (heldItemLock) {
            synchronized (inventory) {
                if (heldItem == null || heldItem.isEmpty()) return;

                int returnAmount = heldItem.getCount();
                int beforeCount = getTotalItems();

                GameLogger.info("Returning held item: " + heldItem.getName() + " x" + returnAmount);

                try {
                    // First try stacking with existing items
                    int remaining = returnAmount;

                    for (int i = 0; i < inventory.getAllItems().size() && remaining > 0; i++) {
                        ItemData existingItem = inventory.getItemAt(i);
                        if (existingItem != null &&
                            existingItem.getItemId().equals(heldItem.getName()) &&
                            existingItem.getCount() < Item.MAX_STACK_SIZE) {

                            int space = Item.MAX_STACK_SIZE - existingItem.getCount();
                            int add = Math.min(space, remaining);

                            existingItem.setCount(existingItem.getCount() + add);
                            remaining -= add;
                        }
                    }

                    // Place remaining in first empty slot
                    if (remaining > 0) {
                        boolean placed = false;
                        for (int i = 0; i < inventory.getAllItems().size() && !placed; i++) {
                            if (inventory.getItemAt(i) == null) {
                                ItemData newItem = new ItemData(
                                    heldItem.getName(),
                                    remaining,
                                    UUID.randomUUID()
                                );
                                inventory.setItemAt(i, newItem);
                                placed = true;
                            }
                        }

                        if (!placed) {
                            GameLogger.error("Could not place remaining " + remaining + " items!");
                            return;
                        }
                    }

                    // Clear held item
                    heldItem = null;
                    updateHeldItemDisplay();

                    // Verify final count is correct
                    int afterCount = getTotalItems();
                    if (afterCount != beforeCount + returnAmount) {
                        GameLogger.error("Count mismatch after return! Before:" + beforeCount +
                            " Returned:" + returnAmount +
                            " After:" + afterCount);
                    }

                    inventory.validateAndRepair();

                } catch (Exception e) {
                    GameLogger.error("Error returning held item: " + e.getMessage());
                }
            }
        }
    }

    // Add helper method
    private int getTotalItems() {
        return inventory.getAllItems().stream()
            .filter(Objects::nonNull)
            .mapToInt(ItemData::getCount)
            .sum();
    }

    public void clearHeldItem() {
        returnHeldItem();  // Just use returnHeldItem directly
    }

    // Modify InventoryScreen's dispose method
    @Override
    public void dispose() {
        GameLogger.info("Disposing InventoryScreen, ensuring held item is returned.");
        if (heldItem != null) {
            returnHeldItem();  // Handle held item first
        }
        stage.dispose();
        batch.dispose();
        shapeRenderer.dispose();
    }
    @Override
    public void hide() {
        returnHeldItem(); // Ensure held item is returned on close
        if (gameClient != null) {
            inventory.getCraftingSystem().returnItemsToInventory();
        }
    }


    private void onCraftingSlotChanged(InventorySlotData slotData) {
        // Update the crafting system with the new grid state
        int index = slotData.getSlotIndex();
        if (index >= 0 && index < 4) { // Valid crafting grid index
            inventory.getCraftingSystem().setItemInGrid(index,
                slotData.isEmpty() ? null : InventoryConverter.itemToItemData(slotData.getItem()));
        }
    }

    private void onCraftingResultChanged(InventorySlotData slotData) {
        // Handle crafting result updates
        CraftingSystem craftingSystem = inventory.getCraftingSystem();

        if (!slotData.isEmpty()) {
            // Result slot should be read-only - revert any direct changes
            ItemData craftingResult = craftingSystem.getCraftingResult();
            if (craftingResult != null) {
                slotData.setItem(craftingResult.getItemId(), craftingResult.getCount());
            } else {
                slotData.clear();
            }
        }
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    public Inventory getInventory() {
        return inventory;
    }

    public Stage getStage() {
        return stage;
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    /**
     * Sets up input listeners for the inventory screen.
     */
    private void setupInput() {
        inputMultiplexer = new InputMultiplexer();
        inputMultiplexer.addProcessor(new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.E) {
                    // Call returnHeldItem when inventory is closed
                    Gdx.app.log("InventoryScreen", "Closing inventory, returning held item if any.");
                    returnHeldItem();
                    hide();
                    return true;
                }
                return false;
            }

            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                // Convert screen coordinates to stage coordinates
                Vector2 stageCoords = stage.screenToStageCoordinates(new Vector2(screenX, screenY));
                Actor hitActor = stage.hit(stageCoords.x, stageCoords.y, true);

                // If clicking outside any UI element and holding an item, try to drop it
                if (hitActor == null && heldItem != null && heldItem.getCount() > 0) {
                    returnHeldItem();
                    return true;
                }
                return false;
            }
        });
        inputMultiplexer.addProcessor(stage);
    }

    public InputMultiplexer getInputMultiplexer() {
        return inputMultiplexer;
    }
}

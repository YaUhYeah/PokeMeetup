
package io.github.pokemeetup.screens;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.gameplay.inventory.*;
import io.github.pokemeetup.system.gameplay.inventory.crafting.CraftingResult;
import io.github.pokemeetup.system.gameplay.inventory.crafting.CraftingResultListener;
import io.github.pokemeetup.system.gameplay.inventory.crafting.CraftingSystem;
import io.github.pokemeetup.system.gameplay.inventory.secureinventories.InventorySlotData;
import io.github.pokemeetup.system.gameplay.inventory.secureinventories.InventorySlotUI;
import io.github.pokemeetup.utils.TextureManager;

import java.util.ArrayList;
import java.util.List;

public class InventoryScreen implements Screen, CraftingResultListener {
    private InventorySlotData heldItemSlotData = null;
    private static final float BACKGROUND_OPACITY = 0.5f;
    private static final int SLOT_SIZE = 40;
    private static final int PADDING = 10;    private ResultSlot resultSlot;

    private final Stage stage;
    private final SpriteBatch batch;
    private final ShapeRenderer shapeRenderer;
    private final Skin skin;
    private final Player player;
    private final Inventory inventory;
    private final DragAndDrop dragAndDrop;
    private final GameClient gameClient;
    private final CraftingSystem craftingSystem;
    private List<InventorySlotData> hotbarSlots;
    private List<InventorySlotData> inventorySlots;

    // UI Components

    public Stage getStage() {
        return stage;
    }

    private Table mainTable;
    private Table craftingTable;
    private Table inventoryTable;
    private Table hotbarTable;// Add these fields
    private List<InventorySlotData> craftingSlots;
    private InventorySlotData craftingResultSlot;


    public InventoryScreen(Player player, Skin skin, GameClient gameClient) {
        this.player = player;
        this.skin = skin;
        this.gameClient = gameClient;
        this.stage = new Stage(new ScreenViewport());
        this.batch = new SpriteBatch();
        this.shapeRenderer = new ShapeRenderer();
        this.dragAndDrop = new DragAndDrop();

        // Get the player's inventory
        this.inventory = player.getInventory();

        // Initialize crafting slots
        craftingSlots = new ArrayList<>();
        for (int i = 0; i < 4; i++) { // 2x2 grid
            craftingSlots.add(new InventorySlotData());
        }
        craftingResultSlot = new InventorySlotData();

        // Initialize crafting system
        craftingSystem = new CraftingSystem();
        craftingSystem.setCraftingResultListener(this);

        setupUI();
        setupInput();
    }

    private void setupUI() {
        // Main container
        mainTable = new Table();
        mainTable.setFillParent(true);

        // Create sections
        setupCraftingArea();
        setupInventoryArea();
        setupHotbarArea();

        // Add to stage
        stage.addActor(mainTable);

        // Update all visuals
        updateAllSlots();
    }

    private void setupCraftingArea() {
        craftingTable = new Table();
        craftingTable.setBackground(createBackground());
        craftingTable.pad(PADDING);

        // Initialize crafting slots
        craftingSlots = new ArrayList<>();
        for (int i = 0; i < 4; i++) { // 2x2 grid
            craftingSlots.add(new InventorySlotData());
        }
        craftingResultSlot = new InventorySlotData();

        // 2x2 crafting grid
        Table grid = new Table();
        int index = 0;
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                if (index < craftingSlots.size()) {
                    InventorySlotData slotData = craftingSlots.get(index);
                    InventorySlotUI slotUI = new InventorySlotUI(
                        slotData,
                        skin,
                        dragAndDrop,
                        this::onSlotChanged,
                        this
                    );
                    grid.add(slotUI).size(SLOT_SIZE).space(2);
                    index++;
                }
            }
            grid.row();
        }

        // Arrow and result slot
        Image arrow = new Image(TextureManager.getGameAtlas().findRegion("arrow"));

        InventorySlotUI resultSlotUI = new InventorySlotUI(
            craftingResultSlot,
            skin,
            dragAndDrop,
            this::onCraftingResultTaken,
            this
        );

        craftingTable.add(grid);
        craftingTable.add(arrow).size(32, 32).pad(10);
        craftingTable.add(resultSlotUI).size(SLOT_SIZE);

        mainTable.add(craftingTable).pad(20).row();
    }

    private void setupInventoryArea() {
        inventoryTable = new Table();
        inventoryTable.setBackground(createBackground());
        inventoryTable.pad(PADDING);

        // Initialize inventory slots
        List<Item> items = inventory.getItems(); // Get items from the player's inventory
        inventorySlots = new ArrayList<>();

        int cols = 9;
        int rows = 3;
        int totalSlots = cols * rows;

        for (int i = 0; i < totalSlots; i++) {
            InventorySlotData slotData = new InventorySlotData(i); // Using slot index
            if (i < items.size()) {
                Item item = items.get(i);
                if (item != null) {
                    slotData.setItem(item.getName(), item.getCount());
                }
            }
            inventorySlots.add(slotData);
        }

        // Create UI slots
        int index = 0;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                if (index < inventorySlots.size()) {
                    InventorySlotData slotData = inventorySlots.get(index);
                    InventorySlotUI slotUI = new InventorySlotUI(
                        slotData,
                        skin,
                        dragAndDrop,
                        this::onSlotChanged,
                        this
                    );
                    inventoryTable.add(slotUI).size(SLOT_SIZE).space(2);
                    index++;
                }
            }
            inventoryTable.row();
        }

        mainTable.add(inventoryTable).pad(20).row();
    }


    public InventorySlotData getHeldItem() {
        return heldItemSlotData;
    }

    public void setHeldItem(InventorySlotData item) {
        heldItemSlotData = item;
    }

    private void setupHotbarArea() {
        hotbarTable = new Table();
        hotbarTable.setBackground(createBackground());
        hotbarTable.pad(PADDING);

        List<Item> hotbarItems = inventory.getHotbarItems(); // Assuming this method exists
        hotbarSlots = new ArrayList<>();

        for (int i = 0; i < hotbarItems.size(); i++) {
            InventorySlotData slotData = new InventorySlotData(i); // Using slot index
            Item item = hotbarItems.get(i);
            if (item != null) {
                slotData.setItem(item.getName(), item.getCount());
            }
            hotbarSlots.add(slotData);

            InventorySlotUI slot = new InventorySlotUI(
                slotData,
                skin,
                dragAndDrop,
                this::onHotbarSlotChanged,
                this
            );
            hotbarTable.add(slot).size(SLOT_SIZE).space(2);
        }

        mainTable.add(hotbarTable).pad(20);
    }

    private void onSlotChanged(InventorySlotData slotData) {
        int slotIndex = slotData.getSlotIndex();
        if (slotData.isEmpty()) {
            inventory.setItemAtSlot(slotIndex, null);
        } else {
            String itemId = slotData.getItemId();
            int count = slotData.getCount();
            Item item = ItemManager.getItem(itemId);
            if (item != null) {
                Item newItem = item.copy();
                newItem.setCount(count);
                inventory.setItemAtSlot(slotIndex, newItem);
            }
        }
        // Mark inventory as dirty for saving
        if (gameClient != null) {
            gameClient.getInventoryManager().markDirty(player.getUsername());
        }
        // Update visuals if needed
        updateAllSlots();
    }

    private void onHotbarSlotChanged(InventorySlotData slotData) {
        int slotIndex = slotData.getSlotIndex();
        if (slotData.isEmpty()) {
            inventory.setHotbarItemAtSlot(slotIndex, null);
        } else {
            String itemId = slotData.getItemId();
            int count = slotData.getCount();
            Item item = ItemManager.getItem(itemId);
            if (item != null) {
                Item newItem = item.copy();
                newItem.setCount(count);
                inventory.setHotbarItemAtSlot(slotIndex, newItem);
            }
        }
        // Mark inventory as dirty for saving
        if (gameClient != null) {
            gameClient.getInventoryManager().markDirty(player.getUsername());
        }
        // Update visuals if needed
        updateAllSlots();
    }




    private Drawable createBackground() {
        return new TextureRegionDrawable(TextureManager.getGameAtlas()
            .findRegion("hotbar_bg"))
            .tint(new Color(0.2f, 0.2f, 0.2f, 0.9f));
    }

    private void setupInput() {
        InputMultiplexer multiplexer = new InputMultiplexer(stage);
        multiplexer.addProcessor(new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.E) {
                    hide();
                    return true;
                }
                return false;
            }
        });
        Gdx.input.setInputProcessor(multiplexer);
    }


    private void onCraftingResultTaken(InventorySlotData resultSlotData) {
        if (!resultSlotData.isEmpty()) {
            // Add the crafted item to the player's inventory
            Item craftedItem = ItemManager.getItem(resultSlotData.getItemId());
            if (craftedItem != null) {
                craftedItem.setCount(resultSlotData.getCount());
                inventory.addItem(craftedItem);
            }

            // Clear crafting grid
            for (InventorySlotData slot : craftingSlots) {
                slot.clear();
            }
            craftingResultSlot.clear();

            // Update crafting
            updateCrafting();

            // Update all visuals
            updateAllSlots();
        }
    }



    private void updateCrafting() {
        String[][] grid = new String[2][2];

        int index = 0;
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                if (index < craftingSlots.size()) {
                    InventorySlotData slot = craftingSlots.get(index);
                    grid[i][j] = slot.isEmpty() ? null : slot.getItemId();
                }
                index++;
            }
        }

        CraftingResult result = craftingSystem.checkRecipe(grid);
        if (result != null) {
            craftingResultSlot.setItem(result.getItemId(), result.getCount());
        } else {
            craftingResultSlot.clear();
        }

        updateAllSlots();
    }

    private void updateAllSlots() {
        // Update all UI slots
        for (Actor actor : stage.getActors()) {
            if (actor instanceof InventorySlotUI) {
                ((InventorySlotUI) actor).updateVisuals();
            }
        }
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

        // Update and draw stage
        stage.act(delta);
        stage.draw();
    }

    @Override
    public void hide() {
        // Save inventory state when closing
        if (gameClient != null) {
            gameClient.getInventoryManager().savePlayerInventory(player.getUsername());
        }
    }

    @Override
    public void dispose() {
        stage.dispose();
        batch.dispose();
        shapeRenderer.dispose();
    }

    // Other required Screen methods...
    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void onCraftingResultChanged(CraftingResult result) {
        if (resultSlot != null) {
            resultSlot.setCraftingResult(result);
        }
    }
}

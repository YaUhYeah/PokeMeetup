package io.github.pokemeetup.screens;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.SpriteDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Timer;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.PlayerData;
import io.github.pokemeetup.system.gameplay.inventory.*;
import io.github.pokemeetup.system.gameplay.inventory.crafting.CraftingSlot;
import io.github.pokemeetup.system.gameplay.inventory.crafting.CraftingSystem;
import io.github.pokemeetup.utils.TextureManager;

import java.util.ArrayList;
import java.util.List;

public class InventoryScreen implements Screen {
    private static final float BACKGROUND_OPACITY = 0.5f;
    private static final int SLOT_SIZE = 40;
    private static final int PADDING = 10;

    private final Stage stage;
    private final SpriteBatch batch;
    private final ShapeRenderer shapeRenderer;
    private final Skin skin;
    private final Inventory inventory;
    private final CraftingSystem craftingSystem;
    private final Player player;

    // UI Components
    private final Table mainTable;
    private final Table craftingTable;
    private final Table inventoryTable;
    private final Table hotbarTable;
    private final DragAndDrop dragAndDrop;

    // Track drag and drop state
    private Item heldItem;
    private int heldItemCount;
    private boolean isDragging;

    public InventoryScreen(Player player, Skin skin) {
        this.player = player;
        this.skin = skin;
        this.inventory = player.getInventory();
        this.batch = new SpriteBatch();
        this.shapeRenderer = new ShapeRenderer();
        this.stage = new Stage(new ScreenViewport(), batch);

        // Initialize UI components
        this.mainTable = new Table();
        this.craftingTable = new Table();
        this.inventoryTable = new Table();
        this.hotbarTable = new Table();
        this.dragAndDrop = new DragAndDrop();

        // Initialize crafting system
        this.craftingSystem = new CraftingSystem(inventory);
        if (player.getInventory().getHotbarCache() != null) {
            player.getInventory().restoreHotbarFromCache();
        }
        setupUI();
        setupInput();
        loadInventoryContents();
    }

    private void setupPeriodicSync() {
        Timer.schedule(new Timer.Task() {
            @Override
            public void run() {
                if (Gdx.app.getGraphics() != null) {
                    Gdx.app.postRunnable(() -> {
                        synchronizeInventoryWithHotbar();
                        inventory.updateHotbarDisplay();
                    });
                }
            }
        }, 0, 0.1f); // Update every 100ms
    }

    private void synchronizeInventoryWithHotbar() {
        // Update both inventory and hotbar slots
        for (Actor actor : stage.getActors()) {
            if (actor instanceof InventorySlot) {
                InventorySlot slot = (InventorySlot) actor;
                slot.refreshFromInventory();
            }
        }
    }

    public Inventory getInventory() {
        return inventory;
    }

    private Texture createColoredTexture(float r, float g, float b, float a) {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(r, g, b, a);
        pixmap.fill();
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return texture;
    }

    private void setupUI() {
        // Main container setup
        Table container = new Table();
        container.setFillParent(true);

        // Don't set container background - let the ShapeRenderer handle it

        // Crafting area (top)
        setupCraftingArea(container);

        // Main inventory (middle)
        setupMainInventory(container);

        // Hotbar (bottom)
        setupHotbar(container);

        stage.addActor(container);
    }

    private void setupCraftingArea(Table container) {
        Table craftingArea = new Table();
        craftingArea.setBackground(new TextureRegionDrawable(createColoredTexture(0.2f, 0.2f, 0.2f, 0.9f)));
        craftingArea.pad(PADDING);

        // 2x2 crafting grid
        Table grid = new Table();
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                CraftingSlot slot = new CraftingSlot(craftingSystem, i, j, skin, dragAndDrop);
                // Set slot background from atlas
                slot.setBackground(new TextureRegionDrawable(TextureManager.getGameAtlas().findRegion("slot_normal")));
                grid.add(slot).size(SLOT_SIZE).space(2);
            }
            grid.row();
        }

        // Arrow and result slot
        Image arrow = new Image(new TextureRegionDrawable(TextureManager.getGameAtlas().findRegion("arrow")));
        ResultSlot resultSlot = new ResultSlot(craftingSystem, skin);
        resultSlot.setBackground(new TextureRegionDrawable(TextureManager.getGameAtlas().findRegion("slot_normal")));

        craftingArea.add(grid);
        craftingArea.add(arrow).size(32, 32).pad(10);
        craftingArea.add(resultSlot).size(SLOT_SIZE);

        container.add(craftingArea).pad(20).row();
    }

    public void updateSlotBackgrounds() {
        for (Actor actor : stage.getActors()) {
            if (actor instanceof InventorySlot) {
                InventorySlot slot = (InventorySlot) actor;
                int index = slot.getIndex();
                if (index >= Inventory.INVENTORY_SIZE - Inventory.HOTBAR_SIZE) {
                    // This is a hotbar slot
                    if (inventory.getSelectedIndex() == index) {
                        slot.setBackground(new TextureRegionDrawable(TextureManager.getGameAtlas().findRegion("slot_selected")));
                    } else {
                        slot.setBackground(new TextureRegionDrawable(TextureManager.getGameAtlas().findRegion("slot_normal")));
                    }
                }
            }
        }
    }

    private void setupMainInventory(Table container) {
        Table mainInventoryArea = new Table();
        mainInventoryArea.setBackground(new TextureRegionDrawable(createColoredTexture(0.2f, 0.2f, 0.2f, 0.9f)));
        mainInventoryArea.pad(PADDING);

        // Show all inventory slots including hotbar
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int index = row * 9 + col;
                InventorySlot slot = new InventorySlot(
                    inventory,
                    index,
                    stage,
                    skin,
                    dragAndDrop,
                    craftingSystem
                );
                slot.setBackground(new TextureRegionDrawable(TextureManager.getGameAtlas().findRegion("slot_normal")));
                mainInventoryArea.add(slot).size(SLOT_SIZE).space(2);
            }
            mainInventoryArea.row();
        }

        container.add(mainInventoryArea).pad(20).row();
    }

    private void setupHotbar(Table container) {
        Table hotbarArea = new Table();
        hotbarArea.pad(PADDING);

        // Create slots for first 9 slots (0-8)
        for (int i = 0; i < Inventory.HOTBAR_SIZE; i++) {
            InventorySlot slot = new InventorySlot(
                inventory,
                i,  // Use index 0-8
                stage,
                skin,
                dragAndDrop,
                craftingSystem
            );

            // Copy the item from the inventory
            Item item = inventory.getItem(i);
            if (item != null) {
                slot.setItem(item.copy());
            }

            hotbarArea.add(slot).size(SLOT_SIZE).space(2);
        }

        container.add(hotbarArea).pad(20);
    }

    private void loadInventoryContents() {
        // Load main inventory slots
        for (int i = 0; i < Inventory.INVENTORY_SIZE - Inventory.HOTBAR_SIZE; i++) {
            Item item = inventory.getItem(i);
            if (item != null) {
                InventorySlot slot = findSlotByIndex(i);
                if (slot != null) {
                    slot.setItem(item.copy());
                }
            }
        }

        // Load hotbar slots
        for (int i = Inventory.INVENTORY_SIZE - Inventory.HOTBAR_SIZE; i < Inventory.INVENTORY_SIZE; i++) {
            Item item = inventory.getItem(i);
            if (item != null) {
                InventorySlot slot = findHotbarSlotByIndex(i);
                if (slot != null) {
                    slot.setItem(item.copy());
                }
            }
        }
    }

    private InventorySlot findSlotByIndex(int index) {
        for (Actor actor : inventoryTable.getChildren()) {
            if (actor instanceof InventorySlot) {
                InventorySlot slot = (InventorySlot) actor;
                if (slot.getIndex() == index) {
                    return slot;
                }
            }
        }
        return null;
    }

    private InventorySlot findHotbarSlotByIndex(int index) {
        for (Actor actor : hotbarTable.getChildren()) {
            if (actor instanceof InventorySlot) {
                InventorySlot slot = (InventorySlot) actor;
                if (slot.getIndex() == index) {
                    return slot;
                }
            }
        }
        return null;
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

    public void updateAllSlots() {
        // Update main inventory slots
        for (Actor actor : inventoryTable.getChildren()) {
            if (actor instanceof InventorySlot) {
                ((InventorySlot) actor).updateVisuals();
            }
        }

        // Update hotbar slots
        for (Actor actor : hotbarTable.getChildren()) {
            if (actor instanceof InventorySlot) {
                ((InventorySlot) actor).updateVisuals();
            }
        }
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);
        loadInventoryContents();
    }


    @Override
    public void render(float delta) {
        // Enable blending for transparency
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        // Draw semi-transparent background
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0, 0, 0, BACKGROUND_OPACITY);
        shapeRenderer.rect(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        shapeRenderer.end();

        // Update and draw stage
        stage.act(delta);
        stage.draw();

        // Ensure proper state sync on every frame
        synchronizeInventoryWithHotbar();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
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

    @Override
    public void hide() {
        craftingSystem.returnItemsToInventory();
        inventory.updateHotbarDisplay();
    }

    @Override
    public void dispose() {
        stage.dispose();
        batch.dispose();
        shapeRenderer.dispose();
    }
}

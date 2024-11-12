package io.github.pokemeetup.screens;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.*;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.pokemeetup.audio.AudioManager;
import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.system.InputHandler;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.data.ItemData;
import io.github.pokemeetup.system.data.PlayerData;
import io.github.pokemeetup.system.gameplay.inventory.*;
import io.github.pokemeetup.system.gameplay.inventory.crafting.CraftingSystem;
import io.github.pokemeetup.system.gameplay.inventory.secureinventories.InventoryObserver;
import io.github.pokemeetup.system.gameplay.inventory.secureinventories.InventorySlotData;
import io.github.pokemeetup.screens.otherui.InventorySlotUI;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.storage.InventoryConverter;
import io.github.pokemeetup.utils.TextureManager;

import java.util.*;
import java.util.List;

import static io.github.pokemeetup.system.gameplay.inventory.Inventory.*;

public class InventoryScreen implements Screen, InventoryObserver {
    private static final float BACKGROUND_OPACITY = 0.5f;
    private static final int SLOT_SIZE = 40;
    private static final int PADDING = 10;

    private final Skin skin;
    private final Stage stage;
    private final SpriteBatch batch;
    private final ShapeRenderer shapeRenderer;
    private final Player player;
    private final GameClient gameClient;
    private final InputHandler inputHandler;
    private Inventory inventory;
    private boolean needsSave = false;
    private List<InventorySlotData> inventorySlots;

    private Table mainTable;
    private Table craftingTable;
    private List<InventorySlotData> craftingSlots;
    private InventorySlotData craftingResultSlot;
    private Group heldItemGroup;
    private Image heldItemImage;
    private Label heldItemCountLabel;
    private Item heldItem = null;
    private boolean initialized = false;

    public InventoryScreen(Player player, Skin skin, GameClient gameClient, InputHandler inputHandler, Inventory inventory) {
        this.player = player;
        this.skin = skin;
        this.gameClient = gameClient;
        this.inputHandler = inputHandler;
        this.inventory = inventory;

        this.stage = new Stage(new ScreenViewport());
        this.batch = new SpriteBatch();
        this.shapeRenderer = new ShapeRenderer();

        // Initialize crafting slots
        craftingSlots = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            craftingSlots.add(new InventorySlotData(i));
        }
        craftingResultSlot = new InventorySlotData(-2);

        // Initialize everything else AFTER inventory setup
        setupHeldItemDisplay();
        initializeInventorySlots();
        setupUI();
        inventory.addObserver(() -> {
            GameLogger.info("Inventory changed - reloading display");
            reloadInventory();
        });
        GameLogger.info("InventoryScreen initialized");
        setupCraftingArea();
    }

    public void initialize() {
        if (!initialized) {
            setupUI();
            updateAllSlots(); // Force initial visual update
            initialized = true;
        }
    }

    public void reloadInventory() {
        GameLogger.info("Reloading inventory...");
        if (inventory != null) {
            List<ItemData> currentItems = inventory.getAllItems();

            // Update slot data
            for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
                InventorySlotData slotData = inventorySlots.get(i);
                ItemData item = i < currentItems.size() ? currentItems.get(i) : null;

                if (item != null) {
                    slotData.setItem(item.getItemId(), item.getCount(), item.getUuid());
                } else {
                    slotData.clear();
                }
            }

            // Update all slot visuals
            updateAllSlots();
        }
    }


    public boolean isInitialized() {
        return initialized;
    }


    private void initializeInventorySlots() {
        inventorySlots = new ArrayList<>();
        List<ItemData> currentItems = inventory.getAllItems();

        GameLogger.info("InventoryScreen: Inventory has " + currentItems.size() + " slots.");
        int nonNullItemCount = 0;

        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            InventorySlotData slotData = new InventorySlotData(i);
            ItemData itemData = i < currentItems.size() ? currentItems.get(i) : null;

            if (itemData != null) {
                slotData.setItem(itemData.getItemId(), itemData.getCount(), itemData.getUuid());
                GameLogger.info("InventoryScreen: Loaded item into slot " + i + ": " +
                    itemData.getItemId() + " x" + itemData.getCount());
                nonNullItemCount++;
            }

            inventorySlots.add(slotData);
        }

        GameLogger.info("InventoryScreen: Total non-null items loaded: " + nonNullItemCount);
    }


    private void setupUI() {
        // Main container table
        mainTable = new Table();
        mainTable.setFillParent(true);
        mainTable.center();

        // Create semi-transparent dark overlay
        Pixmap bgPixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        bgPixmap.setColor(0, 0, 0, 0.25f);
        bgPixmap.fill();
        Texture bgTexture = new Texture(bgPixmap);
        TextureRegionDrawable background = new TextureRegionDrawable(new TextureRegion(bgTexture));
        mainTable.setBackground(background);
        bgPixmap.dispose();

        // Create grid for slots
        Table gridTable = new Table();
        gridTable.setName("gridTable");
        gridTable.defaults().pad(4);

        // Create slots
        int cols = 9;
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            InventorySlotUI slotUI = createSlotUI(i);
            gridTable.add(slotUI).size(40).pad(2);
            if ((i + 1) % cols == 0) {
                gridTable.row();
            }
        }

        mainTable.add(gridTable).pad(20);
        stage.addActor(mainTable);
    }


    private void setupCraftingArea() {
        craftingTable = new Table();
        craftingTable.setBackground(createBackground());
        craftingTable.pad(PADDING);

        // Create 2x2 crafting grid
        Table craftingGrid = new Table();
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 2; col++) {
                Table slotContainer = new Table();
                slotContainer.setBackground(new TextureRegionDrawable(TextureManager.ui.findRegion("slot_normal")));

                int slotIndex = row * 2 + col;
                InventorySlotData slotData = craftingSlots.get(slotIndex);
                slotData.setSlotType(InventorySlotData.SlotType.CRAFTING);

                InventorySlotUI slotUI = new InventorySlotUI(
                    slotData,
                    skin,
                    this
                );

                slotContainer.add(slotUI).size(SLOT_SIZE);
                craftingGrid.add(slotContainer).size(SLOT_SIZE + 4).pad(2);
            }
            craftingGrid.row();
        }

        // Create result slot
        Table resultContainer = new Table();
        resultContainer.setBackground(new TextureRegionDrawable(TextureManager.ui.findRegion("slot_normal")));

        craftingResultSlot.setSlotType(InventorySlotData.SlotType.CRAFTING_RESULT);
        InventorySlotUI resultSlot = new InventorySlotUI(
            craftingResultSlot,
            skin,
            this
        );

        resultContainer.add(resultSlot).size(SLOT_SIZE);

        // Add to crafting table
        craftingTable.add(craftingGrid).padRight(20);
        Image arrow = new Image(TextureManager.ui.findRegion("arrow"));
        craftingTable.add(arrow).size(32, 32).padRight(20);
        craftingTable.add(resultContainer).size(SLOT_SIZE + 4);
    }

    private void setupHeldItemDisplay() {
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


    private Drawable createBackground() {
        return new TextureRegionDrawable(TextureManager.ui
            .findRegion("hotbar_bg"))
            .tint(new Color(0.2f, 0.2f, 0.2f, 0.9f));
    }

    @Override
    public void show() {
        GameLogger.info("InventoryScreen show() called");

        // Existing code...

        // Add the inventory stage to the InputMultiplexer
        InputMultiplexer inputMultiplexer = (InputMultiplexer) Gdx.input.getInputProcessor();
        if (inputMultiplexer == null) {
            inputMultiplexer = new InputMultiplexer();
            Gdx.input.setInputProcessor(inputMultiplexer);
        }

        // Ensure the inventory stage is the first processor
        inputMultiplexer.addProcessor(0, stage);

        GameLogger.info("InventoryScreen stage added to InputMultiplexer");
    }


    private void updateAllSlots() {
        GameLogger.info("Updating all inventory slots");

        Actor gridTableActor = stage.getRoot().findActor("gridTable");
        if (gridTableActor == null) {
            // Try finding through mainTable
            if (mainTable != null) {
                for (Actor child : mainTable.getChildren()) {
                    if (child instanceof Table) {
                        gridTableActor = child;
                        break;
                    }
                }
            }
        }

        if (gridTableActor instanceof Table) {
            Table gridTable = (Table) gridTableActor;
            for (Actor actor : gridTable.getChildren()) {
                if (actor instanceof InventorySlotUI) {
                    InventorySlotUI slotUI = (InventorySlotUI) actor;
                    slotUI.forceUpdate();
                    GameLogger.info("Updated slot " + slotUI.getSlotIndex());
                }
            }
        } else {
            GameLogger.error("Could not find gridTable in stage hierarchy");
            for (Actor actor : stage.getActors()) {
                if (actor instanceof InventorySlotUI) {
                    ((InventorySlotUI) actor).forceUpdate();
                }
            }
        }
    }

    private InventorySlotUI createSlotUI(int index) {
        InventorySlotData slotData = inventorySlots.get(index);
        InventorySlotUI slotUI = new InventorySlotUI(
            slotData,
            skin,
            this
        );
        slotUI.setName("slot_" + index); // Also name the slot UIs
        return slotUI;
    }

    @Override
    public void onInventoryChanged() {
        GameLogger.info("Inventory changed - reloading display");
        reloadInventory();
    }


    @Override
    public void render(float delta) {
        // Don't clear the screen - let the game world show through
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        stage.act(delta);
        stage.draw();

        // Update held item position if needed
        if (heldItemGroup != null && heldItemGroup.isVisible()) {
            float x = Gdx.input.getX() - 16;
            float y = Gdx.graphics.getHeight() - Gdx.input.getY() - 16;
            heldItemGroup.setPosition(x, y);
        }

        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private void updateHeldItemDisplay() {
        if (heldItemGroup == null) {
            heldItemGroup = new Group();
            stage.addActor(heldItemGroup);
        }

        // Clear existing contents
        heldItemGroup.clear();

        if (heldItem != null) {
            // Create image
            TextureRegion texture = TextureManager.items.findRegion(heldItem.getName().toLowerCase() + "_item");
            if (texture == null) {
                texture = TextureManager.items.findRegion(heldItem.getName().toLowerCase());
            }

            if (texture != null) {
                heldItemImage = new Image(texture);
                heldItemImage.setSize(32, 32);
                heldItemGroup.addActor(heldItemImage);

                if (heldItem.getCount() > 1) {
                    heldItemCountLabel = new Label(String.valueOf(heldItem.getCount()), skin);
                    heldItemGroup.addActor(heldItemCountLabel);
                }

                heldItemGroup.setVisible(true);
                heldItemGroup.toFront(); // Add this line
                GameLogger.info("Updated held item display: " + heldItem.getName() + " x" + heldItem.getCount());
            } else {
                heldItemGroup.setVisible(false);
                GameLogger.info("Hiding held item display");
            }
        }
    }

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
    public void hide() {
        GameLogger.info("InventoryScreen hide() called");

        // Remove the inventory stage from the InputMultiplexer
        InputProcessor inputProcessor = Gdx.input.getInputProcessor();
        if (inputProcessor instanceof InputMultiplexer) {
            InputMultiplexer inputMultiplexer = (InputMultiplexer) inputProcessor;
            inputMultiplexer.removeProcessor(stage);
        }

        GameLogger.info("InventoryScreen stage removed from InputMultiplexer");
    }

    @Override
    public void dispose() {
        stage.dispose();
        batch.dispose();
        shapeRenderer.dispose();
    }

    public Item getHeldItem() {
        return heldItem;
    }

    public void setHeldItem(Item item) {
        GameLogger.info("Setting held item: " + (item != null ? item.getName() + " x" + item.getCount() : "null"));
        this.heldItem = item;
        updateHeldItemDisplay();
    }

    public Stage getStage() {
        return stage;
    }

    public Inventory getInventory() {
        return inventory;
    }
}

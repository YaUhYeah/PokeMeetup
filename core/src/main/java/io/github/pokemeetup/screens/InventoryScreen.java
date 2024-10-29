package io.github.pokemeetup.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.gameplay.inventory.Inventory;
import io.github.pokemeetup.system.gameplay.inventory.InventorySlot;
import io.github.pokemeetup.system.gameplay.inventory.Item;
import io.github.pokemeetup.system.gameplay.inventory.ResultSlot;
import io.github.pokemeetup.system.gameplay.inventory.crafting.CraftingSlot;
import io.github.pokemeetup.system.gameplay.inventory.crafting.CraftingSystem;

import java.util.List;

public class InventoryScreen implements Screen {
    private static final int SLOT_SIZE = 40;
    private static final int PADDING = 10;

    private final Stage stage;
    private final Skin skin;
    private final Player player;
    private final CraftingSystem craftingSystem;
    private final Table craftingTable;
    private final DragAndDrop dragAndDrop;
    private Table inventoryTable;
    private SpriteBatch batch;


    public InventoryScreen(Player player, Skin skin) {
        this.player = player;
        this.skin = skin;
        this.stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage); // Directly after stage initialization
        this.dragAndDrop = new DragAndDrop();
        this.craftingSystem = new CraftingSystem(player.getInventory());


        this.batch = new SpriteBatch(); // Initialize the SpriteBatch here
        // Dark overlay background
        Table background = new Table();
        background.setFillParent(true);
        background.setBackground(skin.newDrawable("white", new Color(0, 0, 0, 0.7f)));
//        stage.addActor(background);

        // Main layout
        Table mainTable = new Table();
        mainTable.setFillParent(true);
        mainTable.center();
        mainTable.pad(PADDING); // Enable expansion for dynamic resizing

        // Initialize components
        inventoryTable = createInventoryTable();
        craftingTable = createCraftingTable();

        // Add to main layout
        mainTable.add(craftingTable).pad(PADDING).top().expand();
        mainTable.add(inventoryTable).pad(PADDING).top().expand();

        stage.addActor(mainTable);

        // Escape key handler
        stage.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.E) {
                    hide();
                    return true;
                }
                return false;
            }
        });
    }

    private Table createInventoryTable() {
        System.out.println("Creating inventory table...");
        Table table = new Table();
        table.defaults().size(SLOT_SIZE).space(2);

        List<Item> items = player.getInventory().getItems();

        for (int i = 0; i < items.size(); i++) {
            InventorySlot slot = new InventorySlot(player.getInventory(), i, stage, skin, dragAndDrop);
            slot.updateSlotContents();  // Load item into slot
            table.add(slot);
            System.out.println("Added item to slot " + i + ": " + (items.get(i) != null ? items.get(i).getName() : "empty"));
            if ((i + 1) % Inventory.HOTBAR_SIZE == 0) table.row();
        }

        return table;
    }



    private void refreshInventoryTable() {
        System.out.println("refreshInventoryTable called");
        inventoryTable.clear();  // Clear all existing slots
        inventoryTable = createInventoryTable(); // Recreate the table with updated items

        // Force the display to reload all items from the player's inventory
        List<Item> items = player.getInventory().getItems();
        for (int i = 0; i < items.size(); i++) {
            InventorySlot slot = new InventorySlot(player.getInventory(), i, stage, skin, dragAndDrop);
            slot.updateSlotContents();
            inventoryTable.add(slot);
            System.out.println("Added item to slot " + i + ": " + (items.get(i) != null ? items.get(i).getName() : "empty"));
            if ((i + 1) % Inventory.HOTBAR_SIZE == 0) inventoryTable.row();
        }

    }


    private Table createCraftingTable() {
        Table table = new Table();
        table.defaults().size(SLOT_SIZE).space(2);

        // 3x3 crafting grid
        for (int row = 0; row < 3; row++) {
            table.row();
            for (int col = 0; col < 3; col++) {
                CraftingSlot slot = new CraftingSlot(player.getInventory(), row, col, stage, skin, dragAndDrop);
                table.add(slot);
            }
        }

        // Result slot
        table.row().padTop(10);
        ResultSlot resultSlot = new ResultSlot(craftingSystem);
        table.add(resultSlot).colspan(3).center();

        return table;
    }


    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage); // Ensure input is set here once
        refreshInventoryTable();
    }


    @Override
    public void render(float delta) {
        Gdx.input.setInputProcessor(stage);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        // Update cursor position for held item
        float mouseX = Gdx.input.getX();
        float mouseY = Gdx.graphics.getHeight() - Gdx.input.getY();
        player.getInventory().updateHeldItemPosition(mouseX, mouseY);

        // Standard rendering
        stage.act(delta);
        stage.draw();

        // Begin batch for rendering the held item following the cursor
        batch.begin();
        player.getInventory().renderHeldItem(batch); // Use the batch for drawing the held item
        batch.end();

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
        if (stage != null) {
            stage.unfocusAll();
            stage.clear();
        }
    }

    @Override
    public void dispose() {
        stage.dispose();
        batch.dispose(); // Dispose of SpriteBatch to prevent memory leaks
    }


    public Stage getStage() {
        return stage;
    }
}

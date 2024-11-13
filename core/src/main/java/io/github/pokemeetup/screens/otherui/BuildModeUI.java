package io.github.pokemeetup.screens.otherui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import io.github.pokemeetup.blocks.PlaceableBlock;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.data.ItemData;
import io.github.pokemeetup.system.gameplay.inventory.Item;
import io.github.pokemeetup.system.gameplay.inventory.ItemManager;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.textures.TextureManager;

import java.util.HashMap;
import java.util.Map;

public class BuildModeUI {
    private static final float SLOT_SIZE = 40f;
    private static final int HOTBAR_SLOTS = 9;

    private final Stage stage;
    private final Skin skin;
    private final Player player;
    private final Table mainTable;
    private final Table hotbarTable;
    private int selectedSlot = 0;

    // Map to track available blocks
    private final Map<Integer, PlaceableBlock.BlockType> slotBlockMap;

    public BuildModeUI(Stage stage, Skin skin, Player player) {
        this.stage = stage;
        this.skin = skin;
        this.player = player;

        this.mainTable = new Table();
        this.mainTable.setFillParent(true);
        this.mainTable.bottom();
        this.mainTable.pad(20);

        this.hotbarTable = new Table();
        this.slotBlockMap = initializeBlockMap();

        // Initialize build inventory with blocks
        initializeBuildInventory();

        updateHotbarContent();
        this.mainTable.add(hotbarTable).expandX().bottom();
        stage.addActor(mainTable);

        mainTable.setVisible(false); // Start hidden
    }

    private Map<Integer, PlaceableBlock.BlockType> initializeBlockMap() {
        Map<Integer, PlaceableBlock.BlockType> map = new HashMap<>();
        map.put(0, PlaceableBlock.BlockType.CRAFTING_TABLE);
        map.put(1, PlaceableBlock.BlockType.CHEST);
        return map;
    }

    private void initializeBuildInventory() {
        // Clear existing inventory
        player.getBuildInventory().clear();

        // Add block items to inventory
        for (Map.Entry<Integer, PlaceableBlock.BlockType> entry : slotBlockMap.entrySet()) {
            PlaceableBlock.BlockType blockType = entry.getValue();
            Item blockItem = ItemManager.getItem(blockType.getId());

            if (blockItem != null) {
                ItemData itemData = new ItemData(blockType.getId(), 64); // Give a stack of blocks
                player.getBuildInventory().setItemAt(entry.getKey(), itemData);
                GameLogger.info("Added " + blockType.getId() + " to build inventory slot " + entry.getKey());
            }
        }
    }// In BuildModeUI class
    public void clearSelectedSlot() {
        if (selectedSlot >= 0 && selectedSlot < HOTBAR_SLOTS) {
            player.getBuildInventory().removeItemAt(selectedSlot);
            updateHotbarContent();
        }
    }

    public int getSelectedSlot() {
        return selectedSlot;
    }

    private void updateHotbarContent() {
        hotbarTable.clear();
        hotbarTable.setBackground(new TextureRegionDrawable(
            TextureManager.ui.findRegion("hotbar_bg")
        ));
        hotbarTable.pad(4);

        for (int i = 0; i < HOTBAR_SLOTS; i++) {
            final int slotIndex = i;
            Table slotCell = createSlotCell(i);

            // Add click listener
            slotCell.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    selectSlot(slotIndex);
                    event.stop();
                }
            });

            hotbarTable.add(slotCell).size(SLOT_SIZE).pad(2);
        }
    }

    private Table createSlotCell(int index) {
        Table slotCell = new Table();
        slotCell.setBackground(new TextureRegionDrawable(
            TextureManager.ui.findRegion(index == selectedSlot ? "slot_selected" : "slot_normal")
        ));

        ItemData item = player.getBuildInventory().getItemAt(index);
        if (item != null) {
            TextureRegion itemTexture = TextureManager.items.findRegion(item.getItemId().toLowerCase());
            if (itemTexture != null) {
                Image itemIcon = new Image(itemTexture);
                slotCell.add(itemIcon).size(32).center();

                // Add count label if more than 1
                if (item.getCount() > 1) {
                    Label countLabel = new Label(String.valueOf(item.getCount()), skin);
                    countLabel.setColor(Color.WHITE);
                    Table countContainer = new Table();
                    countContainer.add(countLabel).pad(2);
                    slotCell.add(countContainer).expand().bottom().right();
                }
            }
        }

        return slotCell;
    }

    public void selectSlot(int index) {
        if (index >= 0 && index < HOTBAR_SLOTS) {
            selectedSlot = index;
            PlaceableBlock.BlockType blockType = slotBlockMap.get(index);

            if (blockType != null) {
                ItemData selectedItem = player.getBuildInventory().getItemAt(index);
                if (selectedItem != null && selectedItem.getCount() > 0) {
                    player.selectBlockItem(index);
                    GameLogger.info("Selected block: " + blockType.getId());
                }
            }

            updateHotbarContent();
        }
    }

    public void show() {
        mainTable.setVisible(true);
    }

    public void hide() {
        mainTable.setVisible(false);
    }

    public void update() {
        updateHotbarContent();
    }

    public boolean consumeBlock() {
        ItemData selectedItem = player.getBuildInventory().getItemAt(selectedSlot);
        if (selectedItem != null && selectedItem.getCount() > 0) {
            selectedItem.setCount(selectedItem.getCount() - 1);
            if (selectedItem.getCount() <= 0) {
                player.getBuildInventory().removeItemAt(selectedSlot);
            }
            updateHotbarContent();
            return true;
        }
        return false;
    }
}

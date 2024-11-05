package io.github.pokemeetup.screens.otherui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;

public class BattleLayout {
    private static final float BATTLE_AREA_HEIGHT_RATIO = 0.6f; // 60% of screen for battle area
    private static final float INFO_AREA_HEIGHT_RATIO = 0.25f;  // 25% of screen for info boxes
    private static final float CONTROLS_HEIGHT_RATIO = 0.15f;   // 15% of screen for controls

    // Minimum sizes to prevent UI elements from becoming too small
    private static final float MIN_MOVE_BUTTON_SIZE = 120f;
    private static final float MIN_INFO_BOX_WIDTH = 200f;
    private static final float MIN_INFO_BOX_HEIGHT = 80f;

    private final Table mainTable;
    private final Table battleArea;
    private final Table infoArea;
    private final Table controlsArea;
    private final Window playerInfoBox;
    private final Window enemyInfoBox;
    private final Table moveButtonsGrid;
    private final Table messageBox;

    public BattleLayout(Stage stage, Skin skin) {
        // Main container that fills the screen
        mainTable = new Table();
        mainTable.setFillParent(true);
        stage.addActor(mainTable);

        // Battle area (where Pokemon sprites appear)
        battleArea = new Table();
        battleArea.setBackground(createColoredDrawable(new Color(0, 0, 0, 0.1f)));

        // Info area (contains Pokemon info boxes)
        infoArea = new Table();

        // Controls area (move buttons and message box)
        controlsArea = new Table();
        controlsArea.setBackground(createColoredDrawable(new Color(0, 0, 0, 0.8f)));

        // Set up info boxes
        playerInfoBox = createInfoBox(skin, true);
        enemyInfoBox = createInfoBox(skin, false);

        // Set up move buttons grid
        moveButtonsGrid = createMoveButtonsGrid(skin);

        // Set up message box
        messageBox = createMessageBox(skin);

        layoutComponents();
    }

    private void layoutComponents() {
        // Main layout
        mainTable.clear();
        mainTable.add(battleArea).height(Value.percentHeight(BATTLE_AREA_HEIGHT_RATIO, mainTable)).growX().row();
        mainTable.add(infoArea).height(Value.percentHeight(INFO_AREA_HEIGHT_RATIO, mainTable)).growX().row();
        mainTable.add(controlsArea).height(Value.percentHeight(CONTROLS_HEIGHT_RATIO, mainTable)).growX();

        // Info area layout
        infoArea.clear();
        infoArea.add(enemyInfoBox).pad(10).expandX().width(Value.percentWidth(0.45f, infoArea));
        infoArea.add(playerInfoBox).pad(10).expandX().width(Value.percentWidth(0.45f, infoArea));

        // Controls area layout - stack move buttons and message box
        Stack controlStack = new Stack();
        controlStack.add(moveButtonsGrid);
        controlStack.add(messageBox);
        controlsArea.clear();
        controlsArea.add(controlStack).grow().pad(10);

        // Initially show message box, hide move buttons
        messageBox.setVisible(true);
        moveButtonsGrid.setVisible(false);
    }
    private static final Color WINDOW_BG = new Color(0.2f, 0.2f, 0.2f, 0.9f);

    private Window createInfoBox(Skin skin, boolean isPlayer) {
        Window infoBox = new Window("", skin);
        infoBox.setBackground(createColoredDrawable(WINDOW_BG));
        infoBox.padLeft(10).padRight(10);

        // Info box content setup here...

        return infoBox;
    }

    private Table createMoveButtonsGrid(Skin skin) {
        Table grid = new Table();
        grid.defaults().pad(5).minSize(MIN_MOVE_BUTTON_SIZE);

        // 2x2 grid setup for move buttons
        for (int i = 0; i < 4; i++) {
            grid.add(createMoveButton(skin)).expand().fill();
            if (i % 2 == 1) grid.row();
        }

        return grid;
    }
    private static final Color BUTTON_BG = new Color(0.3f, 0.3f, 0.3f, 1f);
    private static final Color BUTTON_SELECTED_BG = new Color(0.4f, 0.4f, 0.6f, 1f);
    private static final Color STATUS_BG = new Color(0.25f, 0.25f, 0.25f, 0.9f);

    private Button createMoveButton(Skin skin) {
        Button button = new Button(skin);
        button.setBackground(createColoredDrawable(BUTTON_BG));

        // Add hover effect
        button.addListener(new InputListener() {
            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                button.setBackground(createColoredDrawable(BUTTON_SELECTED_BG));
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                button.setBackground(createColoredDrawable(BUTTON_BG));
            }
        });

        return button;
    }
    private TextureRegionDrawable createColoredDrawable(Color color) {
        Pixmap pixmap = new Pixmap(2, 2, Pixmap.Format.RGBA8888);
        pixmap.setColor(color);
        pixmap.fill();

        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);

        TextureRegion region = new TextureRegion(texture);
        TextureRegionDrawable drawable = new TextureRegionDrawable(region);

        pixmap.dispose(); // Dispose pixmap after texture creation

        return drawable;
    }
    private Table createMessageBox(Skin skin) {
        Table msgBox = new Table();
        msgBox.setBackground(createColoredDrawable(WINDOW_BG));
        msgBox.pad(10);

        Label messageLabel = new Label("", skin);
        messageLabel.setWrap(true);
        msgBox.add(messageLabel).expand().fill();

        return msgBox;
    }

    public void resize(int width, int height) {
        mainTable.invalidateHierarchy();
        mainTable.pack();
    }

    public void showMoveButtons(boolean show) {
        moveButtonsGrid.setVisible(show);
        messageBox.setVisible(!show);
    }
}

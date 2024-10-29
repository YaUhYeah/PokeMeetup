    package io.github.pokemeetup.system.gameplay.inventory;

    import com.badlogic.gdx.graphics.g2d.TextureAtlas;
    import com.badlogic.gdx.scenes.scene2d.InputEvent;
    import com.badlogic.gdx.scenes.scene2d.Touchable;
    import com.badlogic.gdx.scenes.scene2d.ui.Image;
    import com.badlogic.gdx.scenes.scene2d.ui.Table;
    import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
    import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
    import io.github.pokemeetup.system.gameplay.inventory.crafting.CraftingSystem;
    import io.github.pokemeetup.utils.TextureManager;

    import static javax.swing.text.StyleConstants.setBackground;

    public class ResultSlot extends Table {
        private final CraftingSystem craftingSystem;
        private final Image itemImage;
        private Item craftedItem;

        public ResultSlot(CraftingSystem craftingSystem) {
            this.craftingSystem = craftingSystem;
            TextureAtlas gameAtlas = TextureManager.getGameAtlas();

            this.setTouchable(Touchable.enabled);
            setBackground(new TextureRegionDrawable(gameAtlas.findRegion("slot_normal")));

            itemImage = new Image();
            add(itemImage).grow();

            addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    if(craftedItem != null) {
                        Item heldItem = craftingSystem.getHeldItem();

                        if(heldItem == null) {
                            craftingSystem.setHeldItem(craftedItem);
                            craftedItem = null;
                            updateVisuals();
                        }
                        else if(heldItem.canStackWith(craftedItem)) {
                            heldItem.addToStack(craftedItem.getCount());
                            craftedItem = null;
                            updateVisuals();
                        }
                    }
                }
            });
        }

        public void setCraftedItem(Item item) {
            this.craftedItem = item;
            updateVisuals();
        }

        private void updateVisuals() {
            if(craftedItem != null) {
                itemImage.setDrawable(new TextureRegionDrawable(craftedItem.getIcon()));
            } else {
                itemImage.setDrawable(null);
            }
        }
    }

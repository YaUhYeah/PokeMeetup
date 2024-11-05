package io.github.pokemeetup.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import io.github.pokemeetup.CreatureCaptureGame;

// Add a simple LoadingScreen class
public class LoadingScreen implements Screen {
    private final CreatureCaptureGame game;
    private final ShapeRenderer shapeRenderer;
    private final SpriteBatch batch;
    private final BitmapFont font;

    public LoadingScreen(CreatureCaptureGame game) {
        this.game = game;
        this.shapeRenderer = new ShapeRenderer();
        this.batch = new SpriteBatch();
        this.font = new BitmapFont();
    }

    @Override
    public void show() {

    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        float progress = game.getAssetManager().getProgress();

        // Draw loading bar
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0.3f, 0.3f, 0.3f, 1);
        shapeRenderer.rect(100, Gdx.graphics.getHeight()/2 - 10, Gdx.graphics.getWidth() - 200, 20);
        shapeRenderer.setColor(0, 1, 0, 1);
        shapeRenderer.rect(100, Gdx.graphics.getHeight()/2 - 10, (Gdx.graphics.getWidth() - 200) * progress, 20);
        shapeRenderer.end();

        // Draw loading text
        batch.begin();
        font.draw(batch, "Loading... " + (int)(progress * 100) + "%",
            Gdx.graphics.getWidth()/2 - 50, Gdx.graphics.getHeight()/2 + 50);
        batch.end();
    }

    @Override
    public void resize(int width, int height) {

    }

    @Override
    public void pause() {

    }

    @Override
    public void resume() {

    }

    @Override
    public void hide() {

    }

    // Implement other Screen methods...

    @Override
    public void dispose() {
        shapeRenderer.dispose();
        batch.dispose();
        font.dispose();
    }
}

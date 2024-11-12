package io.github.pokemeetup.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.pokemeetup.CreatureCaptureGame;
import io.github.pokemeetup.utils.GameLogger;

public class LoadingScreen implements Screen {
    private final CreatureCaptureGame game;
    private Screen nextScreen;
    private boolean initialized = false;
    private float progress = 0;
    private final SpriteBatch batch;
    private final BitmapFont font;
    private boolean disposed = false;

    public LoadingScreen(CreatureCaptureGame game, Screen nextScreen) {
        this.game = game;
        this.nextScreen = nextScreen;
        this.batch = new SpriteBatch();
        this.font = new BitmapFont();
    }

    public void setNextScreen(Screen screen) {
        this.nextScreen = screen;
    }

    @Override
    public void show() {

    }

    @Override
    public void render(float delta) {
        if (disposed) return;

        // Clear screen
        Gdx.gl.glClearColor(0.1f, 0.1f, 0.1f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // Update progress
        progress = Math.min(1, progress + delta * 0.5f);

        // Draw loading info
        batch.begin();
        font.draw(batch, "Loading... " + (int)(progress * 100) + "%",
            20, Gdx.graphics.getHeight() - 20);
        batch.end();

        // Check for transition
        if (nextScreen != null && progress >= 1) {
            GameLogger.info("Loading complete, transitioning to next screen");
            disposed = true;
            game.setScreen(nextScreen);
            dispose();
        }
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

    @Override
    public void dispose() {
        if (!disposed) {
            disposed = true;
            batch.dispose();
            font.dispose();
        }
    }
}

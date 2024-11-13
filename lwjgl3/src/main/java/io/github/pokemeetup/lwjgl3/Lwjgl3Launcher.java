package io.github.pokemeetup.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Window;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowListener;
import io.github.pokemeetup.CreatureCaptureGame;
import io.github.pokemeetup.utils.GameLogger;

public class Lwjgl3Launcher {
    private static CreatureCaptureGame game;
    private static Lwjgl3Application app;

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration configuration = getDefaultConfiguration();

        configuration.setWindowListener(new Lwjgl3WindowListener() {
            @Override
            public void created(Lwjgl3Window window) {
            }

            @Override
            public void iconified(boolean isIconified) {}

            @Override
            public void maximized(boolean isMaximized) {}

            @Override
            public void focusLost() {}

            @Override
            public void focusGained() {}

            @Override
            public boolean closeRequested() {
                if (game != null) {
                    GameLogger.info("Window close requested, saving final state...");
                    try {
                        game.saveGame();
                        GameLogger.info("Game saved successfully before close");
                    } catch (Exception e) {
                        GameLogger.error("Failed to save during shutdown: " + e.getMessage());
                    }
                }
                return true;
            }

            @Override
            public void filesDropped(String[] files) {}

            @Override
            public void refreshRequested() {}
        });

        // Create and store game instance
        game = new CreatureCaptureGame(false);

        // Create and store application
        app = new Lwjgl3Application(game, configuration);
    }

    private static Lwjgl3ApplicationConfiguration getDefaultConfiguration() {
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("PokeMeetup");
        configuration.useVsync(true);
        configuration.setForegroundFPS(Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate + 1);
        configuration.setWindowedMode(800, 600);
        configuration.setWindowIcon("libgdx128.png", "libgdx64.png", "libgdx32.png", "libgdx16.png");
        return configuration;
    }
}

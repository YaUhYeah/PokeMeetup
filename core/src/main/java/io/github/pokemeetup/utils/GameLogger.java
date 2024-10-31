package io.github.pokemeetup.utils;

import com.badlogic.gdx.Gdx;

public class GameLogger {
    public static void info(String message) {
        if (Gdx.app != null) {
            Gdx.app.log("Game", message);
        } else {
            GameLogger.info(message);
        }
    }

    public static void error(String message) {
        if (Gdx.app != null) {
            Gdx.app.error("Game", message);
        } else {
            GameLogger.info(message);
        }
    }
}

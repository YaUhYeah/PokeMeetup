package io.github.pokemeetup.utils;

import com.badlogic.gdx.Gdx;

public class GameLogger {
    public static boolean isDebugEnabled = true;

    public static void info(String message) {
        if (isDebugEnabled) {
            if (Gdx.app != null) {
                Gdx.app.log("Game", message);
            } else {
            System.out.println(message);
            }
        }
    }

    public static void error(String message) {
        if (isDebugEnabled) {
            if (Gdx.app != null) {
                Gdx.app.error("Game", message);
            } else {
                System.out.println(message);
            }
        }
    }
}

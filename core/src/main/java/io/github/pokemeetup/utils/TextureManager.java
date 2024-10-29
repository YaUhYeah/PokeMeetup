package io.github.pokemeetup.utils;

import com.badlogic.gdx.graphics.g2d.TextureAtlas;

public class TextureManager {
    private static TextureAtlas gameAtlas;

    public static void initialize(TextureAtlas atlas) {
        gameAtlas = atlas;
    }

    public static TextureAtlas getGameAtlas() {
        if (gameAtlas == null) {
            throw new IllegalStateException("TextureAtlas not initialized!");
        }
        return gameAtlas;
    }
}

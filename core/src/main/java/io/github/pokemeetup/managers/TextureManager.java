package io.github.pokemeetup.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import static io.github.pokemeetup.utils.TextureManager.getGameAtlas;

public class TextureManager {
    private static Map<String, TextureRegion> additionalRegions = new HashMap<>();

    public static void addRegion(String name, TextureRegion region) {
        additionalRegions.put(name, region);
    }

    public static TextureRegion getRegion(String name) {
        TextureRegion region = getGameAtlas().findRegion(name);
        if (region == null) {
            region = additionalRegions.get(name);
        }
        return region;
    }
}

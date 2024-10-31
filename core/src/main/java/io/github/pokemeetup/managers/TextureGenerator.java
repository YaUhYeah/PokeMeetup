package io.github.pokemeetup.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.github.pokemeetup.utils.GameLogger;

import java.nio.ByteBuffer;

public class TextureGenerator {
    private static final String WHITE_PIXEL_PATH = "atlas/white_pixel.png";

    public static void generateWhitePixel() {
        try {
            // Create 1x1 white pixel
            Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
            pixmap.setColor(Color.WHITE);
            pixmap.fill();

            // Save to file
            ByteBuffer pixels = pixmap.getPixels();
            com.badlogic.gdx.files.FileHandle file = Gdx.files.local(WHITE_PIXEL_PATH);
            file.parent().mkdirs(); // Ensure directory exists
            PixmapIO.writePNG(file, pixmap);

            GameLogger.info("White pixel texture generated successfully at: " + WHITE_PIXEL_PATH);

            pixmap.dispose();
        } catch (Exception e) {
            GameLogger.info("Failed to generate white pixel texture: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Add this to your TextureManager
    public static TextureRegion getOrCreateWhitePixel(TextureAtlas atlas) {
        TextureRegion whitePixel = atlas.findRegion("white");
        if (whitePixel == null) {
            // Create white pixel texture on the fly
            Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
            pixmap.setColor(Color.WHITE);
            pixmap.fill();

            Texture texture = new Texture(pixmap);
            whitePixel = new TextureRegion(texture);

            pixmap.dispose();

            // Store it in TextureManager for future use
            TextureManager.addRegion("white", whitePixel);
        }
        return whitePixel;
    }
}

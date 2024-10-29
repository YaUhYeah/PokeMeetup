package io.github.pokemeetup.utils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TextureGenerator {
    public static void main(String[] args) {
        generateSkinAssets();
    }

    public static void generateSkinAssets() {
        try {
            // Create directories
            String skinDir = "assets/Skins";
            Files.createDirectories(Paths.get(skinDir));

            // Generate base white texture
            generateWhiteTexture(skinDir + "/uiskin.png");

            // Generate atlas file
            generateAtlasFile(skinDir + "/uiskin.atlas");

            // Generate skin file
            generateSkinFile(skinDir + "/skin.json");

            System.out.println("Generated skin assets successfully!");

        } catch (IOException e) {
            System.err.println("Failed to generate skin assets: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void generateWhiteTexture(String path) throws IOException {
        BufferedImage image = new BufferedImage(3, 3, BufferedImage.TYPE_INT_ARGB);

        // Fill with white pixels
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                image.setRGB(x, y, 0xFFFFFFFF);
            }
        }

        ImageIO.write(image, "PNG", new File(path));
    }

    private static void generateAtlasFile(String path) throws IOException {
        String content =
            "uiskin.png\n" +
                "size: 3, 3\n" +
                "format: RGBA8888\n" +
                "filter: Nearest,Nearest\n" +
                "repeat: none\n" +
                "white\n" +
                "  rotate: false\n" +
                "  xy: 1, 1\n" +
                "  size: 1, 1\n" +
                "  orig: 1, 1\n" +
                "  offset: 0, 0\n" +
                "  index: -1\n";

        Files.writeString(Path.of(path), content);
    }

    private static void generateSkinFile(String path) throws IOException {
        String content = "{\n" +
            "  \"com.badlogic.gdx.graphics.g2d.BitmapFont\": {\n" +
            "    \"default-font\": { \"file\": \"Fonts/pkmn.fnt\" }\n" +
            "  },\n" +
            "  \"com.badlogic.gdx.graphics.Color\": {\n" +
            "    \"white\": { \"r\": 1, \"g\": 1, \"b\": 1, \"a\": 1 },\n" +
            "    \"gray\": { \"r\": 0.5, \"g\": 0.5, \"b\": 0.5, \"a\": 1 },\n" +
            "    \"black\": { \"r\": 0, \"g\": 0, \"b\": 0, \"a\": 1 },\n" +
            "    \"transparent\": { \"r\": 0, \"g\": 0, \"b\": 0, \"a\": 0.5 }\n" +
            "  },\n" +
            "  \"com.badlogic.gdx.scenes.scene2d.ui.Skin$TintedDrawable\": {\n" +
            "    \"button-up\": { \"name\": \"white\", \"color\": { \"r\": 0.3, \"g\": 0.3, \"b\": 0.3, \"a\": 1 } },\n" +
            "    \"button-down\": { \"name\": \"white\", \"color\": { \"r\": 0.1, \"g\": 0.1, \"b\": 0.1, \"a\": 1 } },\n" +
            "    \"button-over\": { \"name\": \"white\", \"color\": { \"r\": 0.4, \"g\": 0.4, \"b\": 0.4, \"a\": 1 } },\n" +
            "    \"window-bg\": { \"name\": \"white\", \"color\": { \"r\": 0.1, \"g\": 0.1, \"b\": 0.1, \"a\": 0.9 } },\n" +
            "    \"select-box\": { \"name\": \"white\", \"color\": { \"r\": 0.3, \"g\": 0.3, \"b\": 0.3, \"a\": 1 } }\n" +
            "  },\n" +
            "  \"com.badlogic.gdx.scenes.scene2d.ui.TextButton$TextButtonStyle\": {\n" +
            "    \"default\": {\n" +
            "      \"up\": \"button-up\",\n" +
            "      \"down\": \"button-down\",\n" +
            "      \"over\": \"button-over\",\n" +
            "      \"font\": \"default-font\",\n" +
            "      \"fontColor\": \"white\"\n" +
            "    }\n" +
            "  },\n" +
            "  \"com.badlogic.gdx.scenes.scene2d.ui.Label$LabelStyle\": {\n" +
            "    \"default\": {\n" +
            "      \"font\": \"default-font\",\n" +
            "      \"fontColor\": \"white\"\n" +
            "    }\n" +
            "  },\n" +
            "  \"com.badlogic.gdx.scenes.scene2d.ui.Window$WindowStyle\": {\n" +
            "    \"default\": {\n" +
            "      \"titleFont\": \"default-font\",\n" +
            "      \"background\": \"window-bg\",\n" +
            "      \"titleFontColor\": \"white\"\n" +
            "    },\n" +
            "    \"dialog\": {\n" +
            "      \"titleFont\": \"default-font\",\n" +
            "      \"background\": \"window-bg\",\n" +
            "      \"titleFontColor\": \"white\"\n" +
            "    }\n" +
            "  }\n" +
            "}\n";

        Files.writeString(Path.of(path), content);
    }
}

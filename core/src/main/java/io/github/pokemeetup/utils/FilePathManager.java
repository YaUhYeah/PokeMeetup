package io.github.pokemeetup.utils;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import io.github.pokemeetup.utils.GameLogger;

public class FilePathManager {
    private static String basePath = "";
    private static boolean isAndroid = false;
    public static void initialize(String internalPath) {
        isAndroid = true;
        basePath = internalPath;
        GameLogger.info("FilePathManager initialized with base path: " + basePath);
    }

    public static FileHandle getAssetFile(String path) {
        if (isAndroid) {
            return Gdx.files.internal(path);
        }
        return Gdx.files.internal(path);
    }

    public static FileHandle getLocalFile(String path) {
        if (isAndroid) {
            return new FileHandle(basePath + path);
        }
        return Gdx.files.local(path);
    }

    public static String getWorldPath(String worldName) {
        return basePath + "worlds/" + worldName;
    }

    public static String getSavePath() {
        return basePath + "save";
    }

    public static String getConfigPath() {
        return basePath + "configs";
    }

    public static String getAtlasPath() {
        return basePath + "atlas";
    }

    public static void ensureDirectoryExists(String relativePath) {
        String fullPath = basePath + relativePath;
        java.io.File directory = new java.io.File(fullPath);
        if (!directory.exists()) {
            boolean created = directory.mkdirs();
            GameLogger.info("Created directory " + relativePath + ": " + created);
        }
    }

    public static boolean exists(String path) {
        if (isAndroid) {
            return new java.io.File(basePath + path).exists();
        }
        return Gdx.files.local(path).exists();
    }

    public static void copyAssetToLocal(String assetPath, String localPath) {
        if (!isAndroid) return;

        try {
            FileHandle asset = Gdx.files.internal(assetPath);
            FileHandle local = getLocalFile(localPath);

            if (!local.exists()) {
                asset.copyTo(local);
                GameLogger.info("Copied asset " + assetPath + " to " + localPath);
            }
        } catch (Exception e) {
            GameLogger.error("Failed to copy asset " + assetPath + ": " + e.getMessage());
        }
    }

    public static String getBasePath() {
        return basePath;
    }

    public static boolean isAndroid() {
        return isAndroid;
    }
}

package io.github.pokemeetup.utils.storage;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import io.github.pokemeetup.system.data.WorldData;
import io.github.pokemeetup.utils.GameLogger;

import java.util.concurrent.locks.ReentrantLock;

public class SaveSystem {
    private static final String SAVE_DIR = "worlds/";
    private static final String BACKUP_SUFFIX = "_backup";
    private static final String TEMP_SUFFIX = "_temp";
    private final ReentrantLock saveLock = new ReentrantLock();
    private final Json json;

    public SaveSystem() {
        this.json = JsonConfig.getInstance();
        initializeSaveDirectory();
    }

    private void initializeSaveDirectory() {
        FileHandle saveDir = Gdx.files.local(SAVE_DIR);
        if (!saveDir.exists()) {
            saveDir.mkdirs();
        }
    }

    public void saveWorld(WorldData world) {
        if (world == null || !world.isDirty()) return;

        saveLock.lock();
        try {
            String worldPath = SAVE_DIR + world.getName();
            FileHandle worldDir = Gdx.files.local(worldPath);
            worldDir.mkdirs();

            // Create backup of existing save
            FileHandle worldFile = worldDir.child("world.json");
            if (worldFile.exists()) {
                createBackup(world.getName());
            }

            // Save to temporary file first
            FileHandle tempFile = worldDir.child("world" + TEMP_SUFFIX + ".json");
            String jsonData = json.prettyPrint(world);
            tempFile.writeString(jsonData, false);

            // Verify the save
            if (verifySave(tempFile, world)) {
                // Replace the old file with the new one
                if (worldFile.exists()) {
                    worldFile.delete();
                }
                tempFile.moveTo(worldFile);
                world.clearDirtyFlag();
                GameLogger.info("Successfully saved world: " + world.getName());
            } else {
                GameLogger.error("Save verification failed for: " + world.getName());
                restoreBackup(world.getName());
            }
        } catch (Exception e) {
            GameLogger.error("Failed to save world: " + world.getName());
            GameLogger.error("Error details: " + e.getMessage());
            restoreBackup(world.getName());
        } finally {
            saveLock.unlock();
        }
    }

    public WorldData loadWorld(String worldName) {
        saveLock.lock();
        try {
            FileHandle worldFile = Gdx.files.local(SAVE_DIR + worldName + "/world.json");
            if (!worldFile.exists()) {
                return null;
            }

            String content = worldFile.readString();
            WorldData world = WorldData.fromJson(content);

            if (world != null) {
                GameLogger.info("Successfully loaded world: " + worldName);
                return world;
            } else {
                GameLogger.error("Failed to parse world data: " + worldName);
                return null;
            }
        } catch (Exception e) {
            GameLogger.error("Error loading world: " + worldName);
            GameLogger.error("Error details: " + e.getMessage());
            return null;
        } finally {
            saveLock.unlock();
        }
    }

    private void createBackup(String worldName) {
        FileHandle worldFile = Gdx.files.local(SAVE_DIR + worldName + "/world.json");
        FileHandle backupFile = Gdx.files.local(SAVE_DIR + worldName + "/world" + BACKUP_SUFFIX + ".json");

        if (worldFile.exists()) {
            if (backupFile.exists()) {
                backupFile.delete();
            }
            worldFile.copyTo(backupFile);
        }
    }

    private void restoreBackup(String worldName) {
        FileHandle backupFile = Gdx.files.local(SAVE_DIR + worldName + "/world" + BACKUP_SUFFIX + ".json");
        FileHandle worldFile = Gdx.files.local(SAVE_DIR + worldName + "/world.json");

        if (backupFile.exists()) {
            if (worldFile.exists()) {
                worldFile.delete();
            }
            backupFile.copyTo(worldFile);
            GameLogger.info("Restored backup for world: " + worldName);
        }
    }

    private boolean verifySave(FileHandle file, WorldData originalWorld) {
        try {
            String content = file.readString();
            WorldData loadedWorld = WorldData.fromJson(content);

            if (loadedWorld == null) {
                return false;
            }

            // Basic verification
            return loadedWorld.getName().equals(originalWorld.getName()) &&
                loadedWorld.getLastPlayed() == originalWorld.getLastPlayed() &&
                loadedWorld.getConfig().getSeed() == originalWorld.getConfig().getSeed();
        } catch (Exception e) {
            return false;
        }
    }

    public void deleteWorld(String worldName) {
        saveLock.lock();
        try {
            FileHandle worldDir = Gdx.files.local(SAVE_DIR + worldName);
            if (worldDir.exists()) {
                worldDir.deleteDirectory();
                GameLogger.info("Deleted world: " + worldName);
            }
        } finally {
            saveLock.unlock();
        }
    }
}

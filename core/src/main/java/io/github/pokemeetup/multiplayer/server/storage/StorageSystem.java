package io.github.pokemeetup.multiplayer.server.storage;

import io.github.pokemeetup.system.PlayerData;
import io.github.pokemeetup.system.gameplay.overworld.multiworld.WorldData;
import java.io.IOException;

public interface StorageSystem {
    void initialize() throws IOException;
    void savePlayerData(String username, PlayerData data) throws IOException;
    PlayerData loadPlayerData(String username);
    void saveWorldData(String worldName, WorldData data) throws IOException;
    WorldData loadWorldData(String worldName);
    void clearCache();
    void shutdown();
}
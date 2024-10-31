package io.github.pokemeetup.multiplayer.client;


import io.github.pokemeetup.multiplayer.server.config.ClientConfig;
import io.github.pokemeetup.multiplayer.server.config.ServerConfig;
import io.github.pokemeetup.multiplayer.server.config.ServerConnectionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class GameClientSingleton {
    private static GameClient instance;
    private static final Object lock = new Object();

    private GameClientSingleton() {}

    public static GameClient getInstance(ServerConnectionConfig config) throws IOException {
        synchronized (lock) {
            if (instance == null) {
                instance = new GameClient(config, false,
                    config.getServerIP(),
                    config.getTcpPort(),
                    config.getUdpPort());
            } else if (!instance.isConnected()) {
                // If instance exists but not connected, recreate with new config
                instance.dispose();
                instance = new GameClient(config, false,
                    config.getServerIP(),
                    config.getTcpPort(),
                    config.getUdpPort());
            }
            return instance;
        }
    }  public static synchronized GameClient getSinglePlayerInstance() throws IOException {
        if (instance == null) {
            instance = new GameClient(ServerConnectionConfig.getDefault(),true, "localhost", 0, 0); // Ports 0 for single player
        }
        return instance;
    }

    public static synchronized void dispose() {
       synchronized (lock) {
            if (instance != null) {
                instance.dispose();
                instance = null;
            }
        }
    }
}

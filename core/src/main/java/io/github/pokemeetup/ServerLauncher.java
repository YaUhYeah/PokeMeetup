package io.github.pokemeetup;

import com.badlogic.gdx.utils.Json;
import io.github.pokemeetup.multiplayer.server.GameServer;
import io.github.pokemeetup.system.servers.ServerConfig;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ServerLauncher {
    private static final String CONFIG_DIR = "config";
    private static final String CONFIG_FILE = "server-config.json";

    public static void main(String[] args) {
        try {
            // Ensure config directory exists
            createConfigDirectory();

            // Load or create config
            ServerConfig config = loadServerConfig();

            // Start server with config
            GameServer server = new GameServer();

            // Add shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutting down server...");
                server.shutdown();
            }));

            System.out.println("Server running on port " + config.getPort());
        } catch (Exception e) {
            System.err.println("Critical server error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void createConfigDirectory() throws IOException {
        Path configPath = Paths.get(CONFIG_DIR);
        if (!Files.exists(configPath)) {
            Files.createDirectories(configPath);
        }
    }

    private static ServerConfig loadServerConfig() {
        Json json = new Json();
        File configFile = new File(CONFIG_DIR, CONFIG_FILE);

        if (!configFile.exists()) {
            ServerConfig defaultConfig = new ServerConfig();
            defaultConfig.setPort(54555);
            defaultConfig.setMaxPlayers(20);
            defaultConfig.setWorldSeed(System.currentTimeMillis());

            try {
                Files.writeString(configFile.toPath(), json.prettyPrint(defaultConfig));
            } catch (IOException e) {
                System.err.println("Warning: Could not save default config: " + e.getMessage());
            }
            return defaultConfig;
        }

        try {
            return json.fromJson(ServerConfig.class, Files.readString(configFile.toPath()));
        } catch (Exception e) {
            System.err.println("Error loading config, using defaults: " + e.getMessage());
            return new ServerConfig();
        }
    }
}

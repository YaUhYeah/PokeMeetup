package io.github.pokemeetup.server.deployment;

import com.badlogic.gdx.utils.Json;
import io.github.pokemeetup.multiplayer.server.config.ServerConnectionConfig;
import io.github.pokemeetup.multiplayer.server.GameServer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

public class ServerLauncher {
    private static final Logger logger = Logger.getLogger(ServerLauncher.class.getName());

    public static void main(String[] args) {
        try {
            // Load server config
            ServerConnectionConfig config = loadServerConfig();

            // Create and start server
            GameServer server = new GameServer(config);
            server.start();

            // Add shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutting down server...");
                server.shutdown();
            }));

            logger.info("Server started successfully on port " + config.getTcpPort());
            logger.info("Max players: " + config.getMaxPlayers());

        } catch (Exception e) {
            logger.severe("Failed to start server: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static ServerConnectionConfig loadServerConfig() throws Exception {
        Path configFile = Paths.get("config", "server.json");

        // Create default config if it doesn't exist
        if (!Files.exists(configFile)) {
            Files.createDirectories(configFile.getParent());

            ServerConnectionConfig defaultConfig = new ServerConnectionConfig(
                "0.0.0.0", // Listen on all interfaces
                54555,     // Default TCP port
                54556,     // Default UDP port
                "Pokemon Meetup Server",
                true,      // Allow new registrations
                100        // Max players
            );

            Json json = new Json();
            Files.writeString(configFile, json.prettyPrint(defaultConfig));
            return defaultConfig;
        }

        // Load existing config
        Json json = new Json();
        return json.fromJson(ServerConnectionConfig.class, Files.readString(configFile));
    }


}

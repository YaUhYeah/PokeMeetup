package io.github.pokemeetup.server.deployment;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import io.github.pokemeetup.multiplayer.server.GameServer;
import io.github.pokemeetup.multiplayer.server.ServerStorageSystem;
import io.github.pokemeetup.multiplayer.server.config.ServerConnectionConfig;
import io.github.pokemeetup.system.gameplay.overworld.multiworld.WorldManager;
import org.h2.tools.Server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

public class ServerLauncher {
    private static final Logger logger = Logger.getLogger(ServerLauncher.class.getName());
    private static final String DEFAULT_CONFIG_PATH = "config/server.json";

    public static void main(String[] args) {
        Server h2Server = null;
        try {
            // Start H2 TCP Server on port 9101 with base directory './data'
            h2Server = Server.createTcpServer(
                "-tcpPort", "9101",
                "-tcpAllowOthers",
                "-ifNotExists",
                "-baseDir", "./data" // Ensure this matches DB_PATH in DatabaseManager
            ).start();

            if (h2Server.isRunning(true)) {
                logger.info("H2 Server started and listening on port 9101");
            }

            // Load server config
            ServerConnectionConfig config = loadServerConfig();
            logger.info("Loaded server configuration successfully");

            // Create server storage system
            ServerStorageSystem storage = new ServerStorageSystem();

            // Initialize WorldManager with server storage
            WorldManager worldManager = WorldManager.getInstance(storage, true);
            worldManager.init();

            // Create and start server
            GameServer server = new GameServer(config);
            server.start();

            // Add shutdown hook
            Server finalH2Server = h2Server;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutting down server...");
                server.shutdown();
                finalH2Server.stop();
                logger.info("H2 Server stopped.");
            }));

        } catch (Exception e) {
            logger.severe("Failed to start server: " + e.getMessage());
            e.printStackTrace();
            if (h2Server != null) {
                h2Server.stop();
            }
            System.exit(1);
        }
    }

    private static ServerConnectionConfig loadServerConfig() {
        Path configFile = Paths.get(DEFAULT_CONFIG_PATH);
        Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create();

        // Create default config if it doesn't exist or is corrupted
        ServerConnectionConfig defaultConfig = new ServerConnectionConfig(
            "0.0.0.0",              // Listen on all interfaces
            54555,                  // Default TCP port
            54556,                  // Default UDP port
            "Pokemon Meetup Server", // Server name
            true,                   // Allow new registrations
            100                    // ata directory (ensure consistency)
        );

        try {
            if (!Files.exists(configFile)) {
                Files.createDirectories(configFile.getParent());
                writeConfig(configFile, defaultConfig, gson);
                logger.info("Created new default server configuration");
                return defaultConfig;
            }

            // Try to load existing config
            String jsonContent = Files.readString(configFile);
            try {
                ServerConnectionConfig config = gson.fromJson(jsonContent, ServerConnectionConfig.class);
                if (isValidConfig(config)) {
                    // Ensure server listens on all interfaces
                    config.setServerIP("0.0.0.0");
                    return config;
                }
            } catch (JsonSyntaxException e) {
                logger.warning("Invalid config file format, creating backup and using defaults");
                createConfigBackup(configFile);
            }

            // If we get here, config was invalid - write default
            writeConfig(configFile, defaultConfig, gson);
            logger.info("Reset to default server configuration");
            return defaultConfig;

        } catch (Exception e) {
            logger.severe("Error handling server config: " + e.getMessage());
            // Fall back to default config if all else fails
            return defaultConfig;
        }
    }

    private static boolean isValidConfig(ServerConnectionConfig config) {
        return config != null &&
            config.getTcpPort() > 0 &&
            config.getUdpPort() > 0 &&
            config.getMaxPlayers() > 0 &&
            config.getServerName() != null &&
            config.getDataDirectory() != null;
    }

    private static void writeConfig(Path configFile, ServerConnectionConfig config, Gson gson) throws IOException {
        String jsonConfig = gson.toJson(config);
        Files.writeString(configFile, jsonConfig);
    }

    private static void createConfigBackup(Path configFile) {
        try {
            Path backupFile = configFile.resolveSibling("server.json.bak." + System.currentTimeMillis());
            Files.copy(configFile, backupFile);
            logger.info("Created config backup: " + backupFile);
        } catch (IOException e) {
            logger.warning("Failed to create config backup: " + e.getMessage());
        }
    }
}

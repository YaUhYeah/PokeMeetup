package io.github.pokemeetup.multiplayer.server.config;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import io.github.pokemeetup.multiplayer.server.config.ClientConfig;
import io.github.pokemeetup.utils.GameLogger;

public class ClientConfigLoader {
    /**
     * Loads the client configuration from a JSON file.
     *
     * @param configFilePath The path to the client configuration file.
     * @return The loaded ClientConfig object, or null if loading fails.
     */
    public static ClientConfig loadConfig(String configFilePath) {
        Json json = new Json();
        try {
            FileHandle fileHandle = Gdx.files.internal(configFilePath);

            if (!fileHandle.exists()) {
                GameLogger.info("Client configuration file not found at: " + configFilePath);
                return null;
            }

            // Read the JSON file as a string
            String jsonString = fileHandle.readString();

            // Deserialize JSON to ClientConfig object
            ClientConfig config = json.fromJson(ClientConfig.class, jsonString);

            // Validate the loaded configuration
            if (validateClientConfig(config)) {
                // Set the singleton instance
                ClientConfig.setInstance(config);
                GameLogger.info("Client configuration loaded successfully.");
                return config;
            } else {
                GameLogger.info("Invalid client configuration.");
                return null;
            }
        } catch (Exception e) {
            GameLogger.info("Failed to load client configuration: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Validates the client configuration.
     *
     * @param config The ClientConfig object to validate.
     * @return True if the configuration is valid; false otherwise.
     */
    private static boolean validateClientConfig(ClientConfig config) {
        if (config == null) {
            GameLogger.info("ClientConfig is null.");
            return false;
        }
        if (config.getServerIP() == null || config.getServerIP().trim().isEmpty()) {
            GameLogger.info("serverIP is missing or empty in ClientConfig.");
            return false;
        }
        if (config.getTcpPort() <= 0 || config.getTcpPort() > 65535) {
            GameLogger.info("tcpPort is invalid in ClientConfig: " + config.getTcpPort());
            return false;
        }
        if (config.getUdpPort() <= 0 || config.getUdpPort() > 65535) {
            GameLogger.info("udpPort is invalid in ClientConfig: " + config.getUdpPort());
            return false;
        }
        return true;
    }
}

// ServerConfigManager.java
package io.github.pokemeetup.multiplayer.server.config;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;


public class ServerConfigManager {
    private static ServerConfigManager instance;
    private Array<ServerConnectionConfig> servers;

    private static final String CONFIG_DIR = "configs";
    private static final String CONFIG_FILE = "servers.json";

    private ServerConfigManager() {
        servers = new Array<>();
        ensureConfigDirectory();
        loadServers();
        if (servers.isEmpty()) {
            addDefaultServer();
            saveServers();
        }
    }

    public static synchronized ServerConfigManager getInstance() {
        if (instance == null) {
            instance = new ServerConfigManager();
        }
        return instance;
    }

    public Array<ServerConnectionConfig> getServers() {
        return servers;
    }

    public void addServer(ServerConnectionConfig config) {
        if (!servers.contains(config, false)) {
            servers.add(config);
            saveServers();
            System.out.println("Added server: " + config.getServerName());
        }
    }

    private void ensureConfigDirectory() {
        try {
            FileHandle dir = Gdx.files.local(CONFIG_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }
        } catch (Exception e) {
            System.err.println("Failed to create config directory: " + e.getMessage());
        }
    }
    private void loadServers() {
        try {
            FileHandle file = Gdx.files.local(CONFIG_DIR + "/" + CONFIG_FILE);
            if (file.exists()) {
                Json json = new Json();
                String fileContent = file.readString();
                System.out.println("Loading servers from: " + file.path());
                System.out.println("File content: " + fileContent);

                @SuppressWarnings("unchecked")
                Array<ServerConnectionConfig> loadedServers = json.fromJson(Array.class,
                    ServerConnectionConfig.class, fileContent);

                if (loadedServers != null && loadedServers.size > 0) {
                    servers = loadedServers;
                    System.out.println("Loaded " + servers.size + " servers");
                }
            }
        } catch (Exception e) {
            System.err.println("Error loading servers: " + e.getMessage());
            e.printStackTrace();
        }
    }


    private void addDefaultServer() {
        servers.add(new ServerConnectionConfig(
            "127.0.0.1",
            55555,
            55556,
            "Local Server",
            true,
            100
        ));
    }
    public void saveConfigurations() {
        try {
            FileHandle configFile = Gdx.files.local("configs/servers.json");
            Json json = new Json();
            json.setOutputType(JsonWriter.OutputType.json);
            configFile.writeString(json.prettyPrint(servers), false);
        } catch (Exception e) {
//            System.out.println(STR."error saving server configuration: \{e.getMessage()}");
        }
    }    public void removeServer(ServerConnectionConfig server) {
        if (servers.removeValue(server, false)) {
            saveServers();
            System.out.println("Removed server: " + server.getServerName());
        }
    }

    private void saveServers() {
        try {
            FileHandle file = Gdx.files.local(CONFIG_DIR + "/" + CONFIG_FILE);
            Json json = new Json();
            json.setOutputType(JsonWriter.OutputType.json);

            // Create parent directories if they don't exist
            file.parent().mkdirs();

            String jsonStr = json.prettyPrint(servers);
            file.writeString(jsonStr, false);
            System.out.println("Saved " + servers.size + " servers to: " + file.path());
            System.out.println("Content: " + jsonStr);
        } catch (Exception e) {
            System.err.println("Error saving servers: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
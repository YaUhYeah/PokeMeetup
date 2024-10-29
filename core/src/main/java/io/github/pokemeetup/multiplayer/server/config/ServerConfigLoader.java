package io.github.pokemeetup.multiplayer.server.config;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;

public class ServerConfigLoader {

    // Use TypeToken to load a list of ServerConnectionConfig
    public static List<ServerConnectionConfig> loadConfig(String path) throws IOException, JsonSyntaxException {
        Gson gson = new Gson();
        try (FileReader reader = new FileReader(path)) {
            // Define the type for a List of ServerConnectionConfig
            Type listType = new TypeToken<List<ServerConnectionConfig>>() {}.getType();
            return gson.fromJson(reader, listType);  // Deserialize as List
        }
    }
}

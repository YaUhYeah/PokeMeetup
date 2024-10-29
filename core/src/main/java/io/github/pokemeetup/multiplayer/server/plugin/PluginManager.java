package io.github.pokemeetup.multiplayer.server.plugin;

import com.badlogic.gdx.utils.Json;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.pokemeetup.multiplayer.server.GameServer;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.multiworld.WorldData;
import io.github.pokemeetup.system.servers.PluginContext;

import java.io.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;

public class PluginManager {
    private static final Logger logger = Logger.getLogger(PluginManager.class.getName());
    private final Map<String, ServerPlugin> loadedPlugins = new ConcurrentHashMap<>();
    private final Map<String, PluginConfig> pluginConfigs = new ConcurrentHashMap<>();
    private final Path pluginsDir;
    private final GameServer server;
    private final WorldData gameWorld;

    public PluginManager(GameServer server, WorldData gameWorld) {
        this.server = server;
        this.gameWorld = gameWorld;
        this.pluginsDir = Paths.get("plugins");
        createPluginDirectory();
    }

    private void createPluginDirectory() {
        try {
            Files.createDirectories(pluginsDir);
        } catch (IOException e) {
            logger.severe("Failed to create plugins directory: " + e.getMessage());
            throw new RuntimeException("Failed to create plugins directory", e);
        }
    }

    public void loadPlugins() {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(pluginsDir, "*.jar")) {
            for (Path jarPath : stream) {
                loadPlugin(jarPath);
            }
        } catch (IOException e) {
            logger.severe("Error loading plugins: " + e.getMessage());
        }
    }

    private void loadPlugin(Path jarPath) {
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            // Load plugin.yml
            JarEntry configEntry = jarFile.getJarEntry("plugin.yml");
            if (configEntry == null) {
                throw new IllegalStateException("Missing plugin.yml in " + jarPath.getFileName());
            }

            // Load and parse config
            PluginConfig config = loadPluginConfig(jarFile.getInputStream(configEntry));

            // Validate dependencies
            validateDependencies(config);

            // Create isolated classloader for plugin
            URLClassLoader classLoader = new URLClassLoader(
                new URL[]{jarPath.toUri().toURL()},
                getClass().getClassLoader()
            );

            // Load main plugin class
            Class<?> mainClass = Class.forName(config.getMainClass(), true, classLoader);
            if (!ServerPlugin.class.isAssignableFrom(mainClass)) {
                throw new IllegalStateException("Plugin main class must implement ServerPlugin interface");
            }

            ServerPlugin plugin = (ServerPlugin) mainClass.getDeclaredConstructor().newInstance();

            // Create plugin context and initialize
            Map<String, Object> pluginConfig = loadPluginConfig(plugin.getId());
            PluginContext context = new PluginContext(gameWorld, pluginConfig);
            plugin.onLoad(context);

            // Store loaded plugin
            loadedPlugins.put(plugin.getId(), plugin);
            pluginConfigs.put(plugin.getId(), config);

            logger.info("Successfully loaded plugin: " + config.getName() + " v" + config.getVersion());

        } catch (Exception e) {
            logger.severe("Failed to load plugin " + jarPath.getFileName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void validateDependencies(PluginConfig config) {
        for (String dependency : config.getDependencies()) {
            if (!loadedPlugins.containsKey(dependency)) {
                throw new IllegalStateException("Missing required dependency: " + dependency);
            }
        }
    }

    private PluginConfig loadPluginConfig(InputStream input) {
        try {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (Reader reader = new InputStreamReader(input)) {
                return gson.fromJson(reader, PluginConfig.class);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load plugin config", e);
        }
    }

    public void enablePlugins() {
        List<String> enableOrder = calculateEnableOrder();
        for (String pluginId : enableOrder) {
            ServerPlugin plugin = loadedPlugins.get(pluginId);
            try {
                plugin.onEnable();
                logger.info("Enabled plugin: " + pluginId);
            } catch (Exception e) {
                logger.severe("Failed to enable plugin " + pluginId + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private List<String> calculateEnableOrder() {
        // Simple topological sort for dependencies
        Map<String, Set<String>> graph = new HashMap<>();
        for (var entry : pluginConfigs.entrySet()) {
            graph.put(entry.getKey(), new HashSet<>(entry.getValue().getDependencies()));
        }

        List<String> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();

        for (String plugin : graph.keySet()) {
            if (!visited.contains(plugin)) {
                visitPlugin(plugin, graph, visited, new HashSet<>(), result);
            }
        }

        return result;
    }

    private void visitPlugin(String plugin, Map<String, Set<String>> graph,
                             Set<String> visited, Set<String> visiting, List<String> result) {
        visiting.add(plugin);

        for (String dep : graph.getOrDefault(plugin, Set.of())) {
            if (visiting.contains(dep)) {
                throw new IllegalStateException("Circular dependency detected: " + plugin + " -> " + dep);
            }
            if (!visited.contains(dep)) {
                visitPlugin(dep, graph, visited, visiting, result);
            }
        }

        visiting.remove(plugin);
        visited.add(plugin);
        result.add(plugin);
    }

    public void disablePlugins() {
        List<String> disableOrder = new ArrayList<>(loadedPlugins.keySet());
        Collections.reverse(disableOrder);  // Disable in reverse order

        for (String pluginId : disableOrder) {
            try {
                ServerPlugin plugin = loadedPlugins.get(pluginId);
                plugin.onDisable();
                logger.info("Disabled plugin: " + pluginId);
            } catch (Exception e) {
                logger.severe("Error disabling plugin " + pluginId + ": " + e.getMessage());
            }
        }
        loadedPlugins.clear();
        pluginConfigs.clear();
    }

    // Public API
    public ServerPlugin getPlugin(String id) {
        return loadedPlugins.get(id);
    }

    public Collection<ServerPlugin> getPlugins() {
        return Collections.unmodifiableCollection(loadedPlugins.values());
    }

    public void savePluginConfig(String pluginId, Map<String, Object> config) {
        Path configPath = pluginsDir.resolve(pluginId + ".json");
        try {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(configPath, gson.toJson(config));
        } catch (IOException e) {
            logger.severe("Failed to save config for plugin " + pluginId + ": " + e.getMessage());
        }
    }

    public Map<String, Object> loadPluginConfig(String pluginId) {
        Path configPath = pluginsDir.resolve(pluginId + ".json");
        if (!Files.exists(configPath)) {
            ServerPlugin plugin = loadedPlugins.get(pluginId);
            return plugin != null ? new HashMap<>() : Map.of();
        }

        try {
            Gson gson = new GsonBuilder().create();
            String json = Files.readString(configPath);
            @SuppressWarnings("unchecked")
            Map<String, Object> config = gson.fromJson(json, Map.class);
            return config != null ? config : new HashMap<>();
        } catch (IOException e) {
            logger.severe("Failed to load config for plugin " + pluginId + ": " + e.getMessage());
            return new HashMap<>();
        }
    }
}

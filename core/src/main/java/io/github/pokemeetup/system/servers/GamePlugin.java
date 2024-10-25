package io.github.pokemeetup.system.servers;

public interface GamePlugin {
    String getPluginId();
    void onEnable(PluginContext context);
    void onDisable();
}

package io.github.pokemeetup.system.servers;

import io.github.pokemeetup.system.gameplay.overworld.World;

import java.util.Map;

public class PluginContext {
    private World world;
    private Map<String, Object> config;

    public PluginContext(World world, Map<String, Object> config) {
        this.world = world;
        this.config = config;
    }

    public World getWorld() { return world; }
    public Map<String, Object> getConfig() { return config; }
}

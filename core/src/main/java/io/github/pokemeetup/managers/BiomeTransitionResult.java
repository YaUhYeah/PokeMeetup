package io.github.pokemeetup.managers;

import io.github.pokemeetup.system.gameplay.overworld.biomes.Biome;

import java.util.HashMap;
import java.util.Map;

public class BiomeTransitionResult {
    private final Biome primaryBiome;
    private final Biome secondaryBiome;
    private final float transitionFactor;
    private final float mountainInfluence;
    private final float temperature;
    private final float moisture;

    public BiomeTransitionResult(Biome primaryBiome, Biome secondaryBiome,
                                 float transitionFactor, float mountainInfluence,
                                 float temperature, float moisture) {
        this.primaryBiome = primaryBiome;
        this.secondaryBiome = secondaryBiome;
        this.transitionFactor = transitionFactor;
        this.mountainInfluence = mountainInfluence;
        this.temperature = temperature;
        this.moisture = moisture;
    }

    public Biome getPrimaryBiome() {
        return primaryBiome;
    }

    public Biome getSecondaryBiome() {
        return secondaryBiome;
    }

    public float getTransitionFactor() {
        return transitionFactor;
    }

    public float getMountainInfluence() {
        return mountainInfluence;
    }

    public float getTemperature() {
        return temperature;
    }

    public float getMoisture() {
        return moisture;
    }

    public Map<String, Object> getDebugInfo() {
        Map<String, Object> debug = new HashMap<>();
        debug.put("primaryBiome", primaryBiome.getName());
        debug.put("secondaryBiome", secondaryBiome != null ? secondaryBiome.getName() : "none");
        debug.put("transitionFactor", transitionFactor);
        debug.put("mountainInfluence", mountainInfluence);
        debug.put("temperature", temperature);
        debug.put("moisture", moisture);
        return debug;
    }
}

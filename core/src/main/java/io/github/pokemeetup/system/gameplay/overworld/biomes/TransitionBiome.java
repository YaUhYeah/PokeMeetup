package io.github.pokemeetup.system.gameplay.overworld.biomes;

import io.github.pokemeetup.system.gameplay.overworld.WorldObject;

import java.util.Map;

public class TransitionBiome extends Biome {
    private final Biome primaryBiome;
    private final Biome secondaryBiome;
    private final float transitionFactor;
    private final BiomeType dominantType;

    public TransitionBiome(Biome primaryBiome, Biome secondaryBiome, float transitionFactor) {
        // Use the primary biome's type as the dominant type for this transition
        super("Transition-" + primaryBiome.getType() + "-" + secondaryBiome.getType(),
            primaryBiome.getType());

        this.primaryBiome = primaryBiome;
        this.secondaryBiome = secondaryBiome;
        this.transitionFactor = transitionFactor;
        this.dominantType = primaryBiome.getType();

        mergeBiomeProperties();
    }

    private void mergeBiomeProperties() {
        // Merge allowed tile types with weights based on transition factor
        for (Integer tileType : primaryBiome.getAllowedTileTypes()) {
            getAllowedTileTypes().add(tileType);
            float weight = primaryBiome.getSpawnChanceForTileType(tileType) * transitionFactor;
            setSpawnChanceForTileType(tileType, weight);
        }

        for (Integer tileType : secondaryBiome.getAllowedTileTypes()) {
            getAllowedTileTypes().add(tileType);
            float weight = secondaryBiome.getSpawnChanceForTileType(tileType) * (1 - transitionFactor);
            float existingWeight = getSpawnChanceForTileType(tileType);
            setSpawnChanceForTileType(tileType, existingWeight + weight);
        }

        // Merge spawnable objects
        getSpawnableObjects().addAll(primaryBiome.getSpawnableObjects());
        getSpawnableObjects().addAll(secondaryBiome.getSpawnableObjects());

        // Merge tile textures
        getTileTextures().putAll(primaryBiome.getTileTextures());
        getTileTextures().putAll(secondaryBiome.getTileTextures());

        // Merge tile distributions with weighted values
        mergeTileDistributions();
    }

    private void mergeTileDistributions() {
        Map<Integer, Integer> distribution = getTileDistribution();

        // Add primary biome distributions with weight
        for (Map.Entry<Integer, Integer> entry : primaryBiome.getTileDistribution().entrySet()) {
            int weight = (int)(entry.getValue() * transitionFactor);
            distribution.put(entry.getKey(), weight);
        }

        // Add secondary biome distributions with weight
        for (Map.Entry<Integer, Integer> entry : secondaryBiome.getTileDistribution().entrySet()) {
            int weight = (int)(entry.getValue() * (1 - transitionFactor));
            distribution.merge(entry.getKey(), weight, Integer::sum);
        }
    }

    public void addTileDistribution(int tileType, int weight) {
        getTileDistribution().merge(tileType, weight, Integer::sum);
    }

    @Override
    public float getSpawnChanceForTileType(int tileType) {
        float primaryChance = primaryBiome.getSpawnChanceForTileType(tileType);
        float secondaryChance = secondaryBiome.getSpawnChanceForTileType(tileType);
        return primaryChance * transitionFactor + secondaryChance * (1 - transitionFactor);
    }

    @Override
    public BiomeType getType() {
        return dominantType;
    }
}

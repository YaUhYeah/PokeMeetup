package io.github.pokemeetup.utils;

import java.util.HashMap;
import java.util.Map;

public class TileType {
    public static final int WATER = 0;
    public static final int GRASS = 1;
    public static final int SAND = 2;
    public static final int ROCK = 3;
    public static final int SNOW = 4;
    public static final int HAUNTED_GRASS = 5;
    public static final int SNOW_TALL_GRASS = 6;
    public static final int HAUNTED_TALL_GRASS = 7;
    public static final int HAUNTED_SHROOM = 8;
    public static final int HAUNTED_SHROOMS = 9;
    public static final int TALL_GRASS = 10;
    public static final int FOREST_GRASS = 11;
    public static final int FOREST_TALL_GRASS = 12;
    public static final int RAIN_FOREST_GRASS = 13;
    public static final int RAIN_FOREST_TALL_GRASS = 14;
    public static final int DESERT_SAND = 15;
    public static final int DESERT_ROCKS = 16;
    public static final int DESERT_GRASS = 17;
    // Add other tile types as needed

    private static final Map<Integer, String> tileTypeNames = new HashMap<>();

    static {
        tileTypeNames.put(WATER, "water");
        tileTypeNames.put(GRASS, "grass");
        tileTypeNames.put(SAND, "sand");
        tileTypeNames.put(ROCK, "rock");
        tileTypeNames.put(SNOW, "snow");
        tileTypeNames.put(HAUNTED_GRASS, "haunted_grass");
        tileTypeNames.put(SNOW_TALL_GRASS, "snow_tall_grass");
        tileTypeNames.put(HAUNTED_TALL_GRASS, "haunted_tall_grass");
        tileTypeNames.put(HAUNTED_SHROOM, "haunted_shroom");
        tileTypeNames.put(HAUNTED_SHROOMS, "haunted_shrooms");
        tileTypeNames.put(TALL_GRASS, "tall_grass");
        tileTypeNames.put(FOREST_GRASS, "forest_grass");
        tileTypeNames.put(FOREST_TALL_GRASS, "forest_tall_grass");
        tileTypeNames.put(RAIN_FOREST_GRASS, "rain_forest_grass");
        tileTypeNames.put(RAIN_FOREST_TALL_GRASS, "rain_forest_tall_grass");
        tileTypeNames.put(DESERT_SAND   , "desert_sand");
        tileTypeNames.put(DESERT_ROCKS, "desert_rock");
        tileTypeNames.put(DESERT_GRASS, "desert_grass");
        // Add other tile types as needed
    }

    public static Map<Integer, String> getTileTypeNames() {
        return tileTypeNames;
    }
}

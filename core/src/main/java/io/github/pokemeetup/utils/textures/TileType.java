package io.github.pokemeetup.utils.textures;

import java.util.HashMap;
import java.util.Map;

public class TileType {
    // Base terrain types
    public static final int WATER = 0;
    public static final int GRASS = 1;
    public static final int SAND = 2;
    public static final int ROCK = 3;
    public static final int SNOW = 4;

    // Special terrain types
    public static final int HAUNTED_GRASS = 5;
    public static final int SNOW_TALL_GRASS = 6;
    public static final int HAUNTED_TALL_GRASS = 7;
    public static final int HAUNTED_SHROOM = 8;
    public static final int HAUNTED_SHROOMS = 9;
    public static final int TALL_GRASS = 10;

    // Forest types
    public static final int FOREST_GRASS = 11;
    public static final int FOREST_TALL_GRASS = 12;

    // Rain forest types
    public static final int RAIN_FOREST_GRASS = 13;
    public static final int RAIN_FOREST_TALL_GRASS = 14;

    // Desert types
    public static final int DESERT_SAND = 15;
    public static final int DESERT_ROCKS = 16;
    public static final int DESERT_GRASS = 17;

    // Decorative types
    public static final int FLOWER_1 = 18;
    public static final int FLOWER_2 = 19;
    public static final int FLOWER = 20;
    public static final int TALL_GRASS_2 = 21;
    public static final int GRASS_2 = 22;
    public static final int GRASS_3 = 23;
    public static final int TALL_GRASS_3 = 24;

    // Mountain Base Types
    public static final int MOUNTAIN_WALL_VERTICAL = 25;
    public static final int MOUNTAIN_WALL_HORIZONTAL = 26;
    public static final int MOUNTAIN_CORNER_INNER_TOPLEFT = 27;
    public static final int MOUNTAIN_CORNER_INNER_BOTTOMRIGHT = 30;

    // Mountain Features
    public static final int MOUNTAIN_STAIRS_LEFT = 31; // Left side stairs
    public static final int MOUNTAIN_STAIRS_RIGHT = 32; // Right side stairs
    public static final int MOUNTAIN_STAIRS_CENTER = 35;

    // Mountain Snow Variants
    public static final int MOUNTAIN_SNOW_BASE = 36;

    // Mountain Tiles
    public static final int MOUNTAIN_BASE = 25;       // Base mountain tile
    public static final int MOUNTAIN_PEAK = 26;       // Mountain peak
    public static final int MOUNTAIN_SLOPE_LEFT = 27; // Left slope
    public static final int MOUNTAIN_SLOPE_RIGHT = 28; // Right slope
    public static final int MOUNTAIN_WALL = 29;       // Mountain wall
    public static final int MOUNTAIN_STAIRS = 30;     // Standard stairs
    public static final int MOUNTAIN_CORNER_TL = 33;  // Top-left corner
    public static final int MOUNTAIN_CORNER_TR = 34;  // Top-right corner
    public static final int MOUNTAIN_CORNER_BL = 35;  // Bottom-left corner
    public static final int MOUNTAIN_CORNER_BR = 36;  // Bottom-right corner
    // Mountain Corner Types
    public static final int MOUNTAIN_CORNER_OUTER_TOPLEFT = 39;
    public static final int MOUNTAIN_CORNER_OUTER_BOTTOMRIGHT = 42;

    // Mountain Base Components
    public static final int MOUNTAIN_BASE_EDGE = 38;      // Basic mountain edge tile
    public static final int MOUNTAIN_PATH = 40;           // Walkable mountain path

    // Mountain Edges
    public static final int MOUNTAIN_EDGE_LEFT = 47;      // Left edge
    public static final int MOUNTAIN_EDGE_RIGHT = 48;     // Right edge
    public static final int MOUNTAIN_EDGE_TOP = 49;       // Top edge
    public static final int MOUNTAIN_EDGE_BOTTOM = 50;    // Bottom edge

    private static final Map<Integer, String> tileTypeNames = new HashMap<>();
    private static final Map<Integer, String> mountainTileNames = new HashMap<>();

    static {
        // Initialize base terrain names
        tileTypeNames.put(WATER, "water");
        tileTypeNames.put(GRASS, "grass");
        tileTypeNames.put(SAND, "sand");
        tileTypeNames.put(ROCK, "rock");
        tileTypeNames.put(SNOW, "snow");

        // Initialize special terrain names
        tileTypeNames.put(HAUNTED_GRASS, "haunted_grass");
        tileTypeNames.put(SNOW_TALL_GRASS, "snow_tall_grass");
        tileTypeNames.put(HAUNTED_TALL_GRASS, "haunted_tall_grass");
        tileTypeNames.put(HAUNTED_SHROOM, "haunted_shroom");
        tileTypeNames.put(HAUNTED_SHROOMS, "haunted_shrooms");
        tileTypeNames.put(TALL_GRASS, "tall_grass");

        // Initialize forest names
        tileTypeNames.put(FOREST_GRASS, "forest_grass");
        tileTypeNames.put(FOREST_TALL_GRASS, "forest_tall_grass");

        // Initialize rain forest names
        tileTypeNames.put(RAIN_FOREST_GRASS, "rain_forest_grass");
        tileTypeNames.put(RAIN_FOREST_TALL_GRASS, "rain_forest_tall_grass");

        // Initialize desert names
        tileTypeNames.put(DESERT_SAND, "desert_sand");
        tileTypeNames.put(DESERT_ROCKS, "desert_rock");
        tileTypeNames.put(DESERT_GRASS, "desert_grass");

        // Initialize decorative names with indices where needed
        tileTypeNames.put(FLOWER, "flower");
        tileTypeNames.put(FLOWER_1, "flower");
        tileTypeNames.put(FLOWER_2, "flower");
        tileTypeNames.put(TALL_GRASS_2, "tall_grass");
        tileTypeNames.put(TALL_GRASS_3, "tall_grass");
        tileTypeNames.put(GRASS_2, "grass");
        tileTypeNames.put(GRASS_3, "grass");
        tileTypeNames.put(MOUNTAIN_BASE_EDGE, "tile038");
        tileTypeNames.put(MOUNTAIN_WALL, "mountainBASEMIDDLE");
        tileTypeNames.put(MOUNTAIN_PEAK, "mountaintopRIGHT");
        tileTypeNames.put(MOUNTAIN_PATH, "tile081");
        tileTypeNames.put(MOUNTAIN_STAIRS, "mountainstairsMiddle");
        tileTypeNames.put(MOUNTAIN_STAIRS_LEFT, "mountainstairsLEFT");  // Left stairs
        tileTypeNames.put(MOUNTAIN_STAIRS_RIGHT, "mountainstarsRIGHT"); // Right stairs
        tileTypeNames.put(MOUNTAIN_CORNER_TL, "mountainTOPLEFT"); // Top-left corner
        tileTypeNames.put(MOUNTAIN_CORNER_TR, "mountaintopRIGHT"); // Top-right corner
        tileTypeNames.put(MOUNTAIN_CORNER_BL, "mountainBASELEFT"); // Bottom-left corner
        tileTypeNames.put(MOUNTAIN_CORNER_BR, "mountainbaseRIGHT"); // Bottom-right corner
        tileTypeNames.put(MOUNTAIN_EDGE_LEFT, "mountainTOPLEFT");
        tileTypeNames.put(MOUNTAIN_EDGE_RIGHT, "mountaintopRIGHT");
        tileTypeNames.put(MOUNTAIN_EDGE_TOP, "tile050");
        tileTypeNames.put(MOUNTAIN_EDGE_BOTTOM, "tile051");
        // Add mountain tiles to main tile names
        tileTypeNames.putAll(mountainTileNames);
    }

    public static Map<Integer, String> getTileTypeNames() {
        return tileTypeNames;
    }

    public static Map<Integer, String> getMountainTileNames() {
        return mountainTileNames;
    }

    public static boolean isMountainTile(int tileType) {
        return tileType >= MOUNTAIN_WALL_VERTICAL && tileType <= MOUNTAIN_CORNER_OUTER_BOTTOMRIGHT;
    }

    public static boolean isPassableMountainTile(int tileType) {
        return tileType == MOUNTAIN_STAIRS_LEFT ||
            tileType == MOUNTAIN_STAIRS_RIGHT ||
            tileType == MOUNTAIN_STAIRS_CENTER ||
            tileType == MOUNTAIN_SNOW_BASE;
    }

    public static boolean isPassableTile(int tileType) {
        // Basic terrain types that are passable
        if (tileType == GRASS || tileType == SAND  ||tileType==SNOW_TALL_GRASS||tileType == SNOW ||tileType==GRASS_3||tileType==FOREST_TALL_GRASS|| tileType == HAUNTED_SHROOM || tileType == HAUNTED_SHROOMS || tileType == MOUNTAIN_STAIRS ||
            tileType == HAUNTED_GRASS || tileType == HAUNTED_TALL_GRASS || tileType == FOREST_GRASS || tileType == RAIN_FOREST_TALL_GRASS ||
            tileType == RAIN_FOREST_GRASS || tileType == DESERT_SAND || tileType == DESERT_GRASS || tileType == FLOWER_2 || tileType == GRASS_2 || tileType == TALL_GRASS || tileType == TALL_GRASS_2 || tileType == TALL_GRASS_3 || tileType == FLOWER_1 || tileType == FLOWER) {
            return true;
        }
        // Mountain types that are passable (stairs and paths)
        if (tileType == MOUNTAIN_STAIRS_LEFT || tileType == MOUNTAIN_STAIRS_RIGHT || tileType == MOUNTAIN_PATH ||
            tileType == MOUNTAIN_STAIRS_CENTER) {
            return true;
        }

        return false;
    }

    public static boolean isMountainCorner(int tileType) {
        return (tileType >= MOUNTAIN_CORNER_INNER_TOPLEFT && tileType <= MOUNTAIN_CORNER_INNER_BOTTOMRIGHT) ||
            (tileType >= MOUNTAIN_CORNER_OUTER_TOPLEFT && tileType <= MOUNTAIN_CORNER_OUTER_BOTTOMRIGHT);
    }

}

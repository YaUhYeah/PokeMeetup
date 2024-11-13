package io.github.pokemeetup.utils.textures;

import java.util.HashMap;
import java.util.Map;

public class TileType {
    // Haunted Biome Base Types (5000-5099)
    public static final int HAUNTED_GROUND = 5020;        // Ground tiles
    public static final int HAUNTED_PATH = 5021;          // Walkable paths
    public static final int HAUNTED_WATER = 5030;         // Water/fog effects

    // Haunted Biome Vegetation (5100-5199)
    public static final int HAUNTED_TREE_DARK = 5110;     // Dark trees
    public static final int HAUNTED_TREE_DEAD = 5111;     // Dead trees
    public static final int HAUNTED_BUSH = 5120;          // Haunted bushes
    public static final int HAUNTED_VEGETATION = 5130;    // General vegetation

    // Haunted Biome Structures (5400-5499)
    public static final int HAUNTED_WALL = 5430;          // Stone walls
    public static final int HAUNTED_PLATFORM = 5420;      // Platforms
    public static final int HAUNTED_HOUSE = 5440;         // Haunted houses

    // Haunted Special Features (5500-5599)
    public static final int HAUNTED_MUSHROOM = 5500;      // Glowing mushrooms

    // Haunted Decorative (5600-5699)
    public static final int HAUNTED_DECORATION = 5600;    // General decorations


    // Base terrain type
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
    public static final int MOUNTAIN_SNOW_WALL = 37;
    public static final int MOUNTAIN_SNOW_CORNER_INNER = 38;

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
    public static final int HAUNTED_WALL_STONE = 5431;     // Updated to match new mapping
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
    public static final int FOREST_GROUND = 3020;           // Base ground
    public static final int FOREST_GROUND_DECORATED = 3021; // Decorated ground
    public static final int FOREST_PATH = 3022;            // Forest paths
    public static final int FOREST_WATER = 3030;           // Water features

    // Forest Biome Vegetation (3100-3199)
    public static final int FOREST_TREE_LARGE = 3110;      // Large trees
    public static final int FOREST_FLOWER = 3100;          // Basic flowers
    public static final int FOREST_FLOWER_TALL = 3101;     // Tall flowers
    public static final int SAFARI_GROUND_GRASS = 8020;     // Grass ground
    public static final int SAFARI_GROUND_DIRT = 8021;      // Dirt ground
    public static final int SAFARI_PATH = 8022;             // Safari paths

    // Safari Biome Features (8100-8199)
    public static final int SAFARI_TREE_LARGE = 8110;       // Large trees
    public static final int SAFARI_ROCK = 8200;             // Rock formations
    public static final int SAFARI_ROCK_SMALL = 8201;       // Small rocks
    public static final int SAFARI_ROCK_BORDER = 8202;      // Border rocks
    public static final int SAFARI_VEGETATION_SMALL = 8130;  // Small plants
    public static final int SAFARI_VEGETATION_DENSE = 8131;  // Dense vegetation

    // Volcano Biome Base Types (10000-10099)
    public static final int VOLCANO_GROUND_ROCK = 10020;    // Rocky ground
    public static final int VOLCANO_GROUND_LAVA = 10021;    // Lava ground
    public static final int VOLCANO_BRIDGE = 10030;         // Bridges

    // Volcano Biome Features (10100-10199)
    public static final int VOLCANO_ROCK_FORMATION = 10200;  // Large rock formations
    public static final int VOLCANO_ROCK_SMALL = 10201;     // Small rocks
    public static final int VOLCANO_ROCK_EDGE = 10202;      // Edge rocks

    // Volcano Special Features (10300-10399)
    public static final int VOLCANO_LAVA_POOL = 10300;      // Lava pools
    public static final int VOLCANO_LAVA_FLOW = 10301;      // Flowing lava
    public static final int VOLCANO_PLATFORM = 10320;       // Platforms
    public static final int FOREST_VEGETATION_SMALL = 3130; // Small vegetation
    public static final int CHERRY_GROUND = 7020;             // Base ground
    public static final int CHERRY_GROUND_DETAIL = 7021;      // Detailed ground
    public static final int CHERRY_WATER = 7030;              // Water features

    // Cherry Blossom Trees (7100-7199)
    public static final int CHERRY_TREE_GREEN = 7110;         // Green trees
    public static final int CHERRY_TREE_PINK = 7111;          // Pink trees
    public static final int CHERRY_TREE_SMALL = 7112;         // Small trees

    // Cherry Structures (7400-7499)
    public static final int CHERRY_PLATFORM_WOOD = 7420;      // Wooden platforms
    public static final int CHERRY_PLATFORM_STONE = 7421;     // Stone platforms
    public static final int CHERRY_PLANTER = 7430;            // Decorative planters
    public static final int CHERRY_BENCH = 7440;              // Benches

    // Cherry Features (7200-7299)
    public static final int CHERRY_ROCK = 7200;               // Rock formations
    public static final int CHERRY_DECORATION = 7600;         // Decorations
    public static final int FOREST_BUSH = 3120;            // Forest bushes
    public static final int SWAMP_GROUND_WATER = 9020;     // Water-covered ground
    public static final int SWAMP_GROUND_GREEN = 9021;     // Mossy ground
    public static final int SWAMP_GROUND_BROWN = 9022;     // Muddy ground

    // Swamp Biome Vegetation (9100-9199)
    public static final int SWAMP_TREE_MANGROVE = 9110;    // Mangrove trees
    public static final int SWAMP_ROOTS = 9120;            // Root systems
    public static final int SWAMP_VEGETATION = 9130;       // Swamp vegetation

    // Swamp Structures (9400-9499)
    public static final int SWAMP_PLATFORM = 9420;         // Wooden platforms

    private static final Map<Integer, String> tileTypeNames = new HashMap<>();
    private static final Map<Integer, String> mountainTileNames = new HashMap<>();

    static {
        // Initialize base terrain names
        tileTypeNames.put(WATER, "water");
        tileTypeNames.put(SWAMP_GROUND_WATER, "swamp_ground_water");
        tileTypeNames.put(SWAMP_GROUND_GREEN, "swamp_ground_green");
        tileTypeNames.put(SWAMP_GROUND_BROWN, "swamp_ground_brown");
        tileTypeNames.put(SWAMP_TREE_MANGROVE, "swamp_tree_mangrove");
        tileTypeNames.put(SWAMP_ROOTS, "swamp_roots");
        tileTypeNames.put(SWAMP_VEGETATION, "swamp_vegetation");
        tileTypeNames.put(SWAMP_PLATFORM, "swamp_platform");
        tileTypeNames.put(GRASS, "grass");
        tileTypeNames.put(SAND, "sand");
        tileTypeNames.put(ROCK, "rock");
        tileTypeNames.put(FOREST_GROUND, "forest_ground");
        tileTypeNames.put(FOREST_GROUND_DECORATED, "forest_ground_decorated");
        tileTypeNames.put(FOREST_PATH, "forest_path");
        tileTypeNames.put(FOREST_WATER, "forest_water");
        tileTypeNames.put(VOLCANO_GROUND_ROCK, "volcano_ground_rock");
        tileTypeNames.put(VOLCANO_GROUND_LAVA, "volcano_ground_lava");
        tileTypeNames.put(VOLCANO_BRIDGE, "volcano_bridge");
        tileTypeNames.put(VOLCANO_ROCK_FORMATION, "volcano_rock_formation");
        tileTypeNames.put(VOLCANO_ROCK_SMALL, "volcano_rock_small");
        tileTypeNames.put(VOLCANO_ROCK_EDGE, "volcano_rock_edge");
        tileTypeNames.put(VOLCANO_LAVA_POOL, "volcano_lava_pool");
        tileTypeNames.put(HAUNTED_WALL_STONE, "haunted_wall_stone");
        tileTypeNames.put(VOLCANO_LAVA_FLOW, "volcano_lava_flow");
        tileTypeNames.put(VOLCANO_PLATFORM, "volcano_platform");
        tileTypeNames.put(SAFARI_GROUND_GRASS, "safari_ground_grass");
        tileTypeNames.put(SAFARI_GROUND_DIRT, "safari_ground_dirt");
        tileTypeNames.put(SAFARI_PATH, "safari_path");
        tileTypeNames.put(SAFARI_TREE_LARGE, "safari_tree_large");
        tileTypeNames.put(SAFARI_ROCK, "safari_rock");
        tileTypeNames.put(SAFARI_ROCK_SMALL, "safari_rock_small");
        tileTypeNames.put(SAFARI_ROCK_BORDER, "safari_rock_border");
        tileTypeNames.put(SAFARI_VEGETATION_SMALL, "safari_vegetation_small");
        tileTypeNames.put(SAFARI_VEGETATION_DENSE, "safari_vegetation_dense");
        tileTypeNames.put(FOREST_TREE_LARGE, "forest_tree_large");
        tileTypeNames.put(FOREST_FLOWER, "forest_flower");
        tileTypeNames.put(FOREST_FLOWER_TALL, "forest_flower_tall");
        tileTypeNames.put(FOREST_VEGETATION_SMALL, "forest_vegetation_small");
        // Add to tileTypeNames map
        tileTypeNames.put(CHERRY_GROUND, "cherry_ground");
        tileTypeNames.put(CHERRY_GROUND_DETAIL, "cherry_ground_detail");
        tileTypeNames.put(CHERRY_WATER, "cherry_water");
        tileTypeNames.put(CHERRY_TREE_GREEN, "cherry_tree_green");
        tileTypeNames.put(CHERRY_TREE_PINK, "cherry_tree_pink");
        tileTypeNames.put(CHERRY_TREE_SMALL, "cherry_tree_small");
        tileTypeNames.put(CHERRY_PLATFORM_WOOD, "cherry_platform_wood");
        tileTypeNames.put(CHERRY_PLATFORM_STONE, "cherry_platform_stone");
        tileTypeNames.put(CHERRY_PLANTER, "cherry_planter");
        tileTypeNames.put(CHERRY_BENCH, "cherry_bench");
        tileTypeNames.put(CHERRY_ROCK, "cherry_rock");
        tileTypeNames.put(CHERRY_DECORATION, "cherry_decoration");
        tileTypeNames.put(FOREST_BUSH, "forest_bush");
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

        tileTypeNames.put(HAUNTED_GROUND, "haunted_ground");
        tileTypeNames.put(HAUNTED_PATH, "haunted_path");
        tileTypeNames.put(HAUNTED_WATER, "haunted_water");
        tileTypeNames.put(HAUNTED_TREE_DARK, "haunted_tree_dark");
        tileTypeNames.put(HAUNTED_TREE_DEAD, "haunted_tree_dead");
        tileTypeNames.put(HAUNTED_BUSH, "haunted_bush");
        tileTypeNames.put(HAUNTED_VEGETATION, "haunted_vegetation");
        tileTypeNames.put(HAUNTED_WALL, "haunted_wall_stone");
        tileTypeNames.put(HAUNTED_PLATFORM, "haunted_platform");
        tileTypeNames.put(HAUNTED_HOUSE, "haunted_house_wood");
        tileTypeNames.put(HAUNTED_MUSHROOM, "haunted_mushroom");
        tileTypeNames.put(HAUNTED_DECORATION, "haunted_decoration");
        // Initialize rain forest names
        tileTypeNames.put(RAIN_FOREST_GRASS, "rain_forest_grass");
        tileTypeNames.put(RAIN_FOREST_TALL_GRASS, "rain_forest_tall_grass");

        // Initialize desert names
        tileTypeNames.put(DESERT_SAND, "desert_sand");
        tileTypeNames.put(DESERT_ROCKS, "desert_rock");
        tileTypeNames.put(DESERT_GRASS, "desert_grass");

        // Initialize decorative names with indices where needed
        tileTypeNames.put(FLOWER, "flower");
        tileTypeNames.put(FLOWER_1, "flower_1");
        tileTypeNames.put(FLOWER_2, "flower_2");
        tileTypeNames.put(TALL_GRASS_2, "tall_grass_2");
        tileTypeNames.put(TALL_GRASS_3, "tall_grass_3");
        tileTypeNames.put(GRASS_2, "grass_2");
        tileTypeNames.put(GRASS_3, "grass_3");
        tileTypeNames.put(MOUNTAIN_BASE_EDGE, "tile038");
        tileTypeNames.put(MOUNTAIN_WALL, "mountainBASEMIDDLE");
        tileTypeNames.put(MOUNTAIN_PEAK, "tile0118");
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
        // Base terrain types that are always passable
        if (tileType == GRASS || tileType == SAND || tileType == SNOW ||
            tileType == GRASS_2 || tileType == GRASS_3) {
            return true;
        }

        // Vegetation that is passable
        if (tileType == TALL_GRASS || tileType == TALL_GRASS_2 || tileType == TALL_GRASS_3 ||
            tileType == SNOW_TALL_GRASS || tileType == FOREST_TALL_GRASS ||
            tileType == RAIN_FOREST_TALL_GRASS || tileType == FLOWER || tileType == FLOWER_1 ||
            tileType == FLOWER_2) {
            return true;
        }

        // Haunted biome passable tiles
        if (tileType == HAUNTED_GRASS || tileType == HAUNTED_TALL_GRASS ||
            tileType == HAUNTED_GROUND || tileType == HAUNTED_PATH ||
            tileType == HAUNTED_MUSHROOM || tileType == HAUNTED_DECORATION ||
            tileType == HAUNTED_PLATFORM) {
            return true;
        }

        // Forest biome passable tiles
        if (tileType == FOREST_GROUND || tileType == FOREST_PATH ||
            tileType == FOREST_GROUND_DECORATED || tileType == FOREST_FLOWER ||
            tileType == FOREST_FLOWER_TALL || tileType == FOREST_VEGETATION_SMALL) {
            return true;
        }

        // Safari biome passable tiles
        if (tileType == SAFARI_GROUND_GRASS || tileType == SAFARI_GROUND_DIRT ||
            tileType == SAFARI_PATH || tileType == SAFARI_VEGETATION_SMALL) {
            return true;
        }

        // Volcano biome passable tiles
        if (tileType == VOLCANO_GROUND_ROCK || tileType == VOLCANO_BRIDGE ||
            tileType == VOLCANO_PLATFORM) {
            return true;
        }

        // Swamp biome passable tiles
        if (tileType == SWAMP_GROUND_GREEN || tileType == SWAMP_GROUND_BROWN ||
            tileType == SWAMP_PLATFORM || tileType == SWAMP_VEGETATION) {
            return true;
        }

        // Cherry blossom biome passable tiles
        if (tileType == CHERRY_GROUND || tileType == CHERRY_GROUND_DETAIL ||
            tileType == CHERRY_PLATFORM_WOOD || tileType == CHERRY_PLATFORM_STONE ||
            tileType == CHERRY_BENCH) {
            return true;
        }

        // Desert passable tiles
        if (tileType == DESERT_SAND || tileType == DESERT_GRASS) {
            return true;
        }

        // Mountain types that are passable (stairs and paths)
        if (tileType == MOUNTAIN_STAIRS_LEFT || tileType == MOUNTAIN_STAIRS_RIGHT ||
            tileType == MOUNTAIN_PATH || tileType == MOUNTAIN_STAIRS_CENTER) {
            return true;
        }

        return false;
    }

    public static boolean isMountainCorner(int tileType) {
        return (tileType >= MOUNTAIN_CORNER_INNER_TOPLEFT && tileType <= MOUNTAIN_CORNER_INNER_BOTTOMRIGHT) ||
            (tileType >= MOUNTAIN_CORNER_OUTER_TOPLEFT && tileType <= MOUNTAIN_CORNER_OUTER_BOTTOMRIGHT);
    }

}

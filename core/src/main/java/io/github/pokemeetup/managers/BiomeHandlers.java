package io.github.pokemeetup.managers;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.Array;
import io.github.pokemeetup.utils.textures.TilesetSplitter;

import java.util.*;
import java.util.stream.Collectors;

public class BiomeHandlers {
    private static final int TILE_WIDTH = 32;
    private static final int TILE_HEIGHT = 32;

    public interface BiomeHandler {
        Map<String, TextureRegion> getAllTiles(TextureAtlas atlas);

        Map<String, TextureRegion> getTilesByType(TextureAtlas atlas, Enum<?> type);
    }

    // Plains Biome Handler
    public static class PlainsBiomeHandler implements BiomeHandler {
        public static final TilesetSplitter.TileNamingStrategy PLAINS_NAMING = (pos, base) -> {
            // Top section - Vegetation (rows 0-1)
            if (pos.row <= 1) {
                if (pos.col <= 2) return "plains_grass_" + pos.row + "_" + pos.col;
                return "plains_flowers_" + pos.row + "_" + (pos.col - 3);
            }

            // Ground and water sections (rows 2-3)
            if (pos.row <= 3) {
                if (pos.col <= 1) return "plains_ground_" + (pos.row - 2) + "_" + pos.col;
                if (pos.col == 2) return "plains_water_" + (pos.row - 2);
                return "plains_ground_decorated_" + (pos.row - 2) + "_" + (pos.col - 3);
            }

            // Trees section (rows 4-5)
            if (pos.row <= 5) {
                return "plains_tree_" + (pos.row - 4) + "_" + pos.col;
            }

            // Rock formations (rows 6-8)
            if (pos.row <= 8) {
                return "plains_rock_" + (pos.row - 6) + "_" + pos.col;
            }

            // Bridge and fence section (rows 9-11)
            if (pos.row <= 11) {
                if (pos.col <= 1) return "plains_bridge_" + (pos.row - 9) + "_" + pos.col;
                return "plains_fence_" + (pos.row - 9) + "_" + (pos.col - 2);
            }

            // Decorations (remaining rows)
            return "plains_decoration_" + (pos.row - 12) + "_" + pos.col;
        };

        public static Map<String, TextureRegion> getPlainsTiles(TextureAtlas atlas) {
            return TilesetSplitter.splitTilesetCustomNaming(
                atlas,
                "Plains_Biome",
                TILE_WIDTH,
                TILE_HEIGHT,
                0,
                PLAINS_NAMING
            );
        }

        // Utility methods
        private static Map<String, TextureRegion> filterTilesByPrefix(Map<String, TextureRegion> tiles, String prefix) {
            return tiles.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(prefix))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }

        @Override
        public Map<String, TextureRegion> getAllTiles(TextureAtlas atlas) {
            return TilesetSplitter.splitTilesetCustomNaming(
                atlas,
                "Plains_Biome",
                TILE_WIDTH,
                TILE_HEIGHT,
                0,
                PLAINS_NAMING
            );
        }

        @Override
        public Map<String, TextureRegion> getTilesByType(TextureAtlas atlas, Enum<?> type) {
            return filterTilesByPrefix(getAllTiles(atlas), "plains_" + type.name().toLowerCase());
        }

        public enum PlainsTileType {
            GRASS,
            FLOWERS,
            GROUND,
            WATER,
            TREE,
            ROCK,
            BRIDGE,
            FENCE,
            DECORATION
        }

        public static class DesertBiomeHandler implements BiomeHandler {
            private static final TilesetSplitter.TileNamingStrategy DESERT_NAMING = (pos, base) -> {
                if (pos.row <= 1) {
                    if (pos.col <= 2) return "desert_vegetation_" + pos.row + "_" + pos.col;
                    return "desert_flower_" + pos.row + "_" + (pos.col - 3);
                }
                if (pos.row <= 3) {
                    return "desert_ground_" + (pos.row - 2) + "_" + pos.col;
                }
                if (pos.row <= 5) {
                    return "desert_rock_" + (pos.row - 4) + "_" + pos.col;
                }
                if (pos.row <= 6) {
                    return "desert_platform_" + pos.col;
                }
                if (pos.row <= 8) {
                    if (pos.col <= 1) return "desert_cactus_" + (pos.row - 7) + "_" + pos.col;
                    return "desert_rock_small_" + (pos.row - 7) + "_" + (pos.col - 2);
                }
                if (pos.row <= 10) {
                    return "desert_fence_" + (pos.row - 9) + "_" + pos.col;
                }
                if (pos.row <= 14) {
                    return "desert_building_" + (pos.row - 11) + "_" + pos.col;
                }
                if (pos.row <= 16) {
                    if (pos.col <= 1) return "desert_window_" + (pos.row - 15) + "_" + pos.col;
                    return "desert_decoration_" + (pos.row - 15) + "_" + (pos.col - 2);
                }
                return "desert_awning_" + (pos.row - 17) + "_" + pos.col;
            };

            @Override
            public Map<String, TextureRegion> getAllTiles(TextureAtlas atlas) {
                return TilesetSplitter.splitTilesetCustomNaming(
                    atlas,
                    "Desert_Biome",
                    TILE_WIDTH,
                    TILE_HEIGHT,
                    0,
                    DESERT_NAMING
                );
            }

            @Override
            public Map<String, TextureRegion> getTilesByType(TextureAtlas atlas, Enum<?> type) {
                return filterTilesByPrefix(getAllTiles(atlas), "desert_" + type.name().toLowerCase());
            }

            public enum DesertTileType {
                VEGETATION_TOP,
                SAND_GROUND,
                ROCK_FORMATION,
                PLATFORM,
                CACTUS,
                FENCE,
                BUILDING_WOOD,
                WINDOW,
                ROOF,
                DECORATION,
                AWNING
            }
        }
        // Replace the existing PlainsTileset class with this proper implementation:

        // Cherry Blossom Biome
        public static class CherryBlossomBiomeHandler implements BiomeHandler {
            private static final TilesetSplitter.TileNamingStrategy CHERRY_BLOSSOM_NAMING = (pos, base) -> {
                if (pos.row <= 2) {
                    return "cherry_tree_green_" + pos.row + "_" + pos.col;
                }
                if (pos.row <= 5) {
                    return "cherry_tree_pink_" + (pos.row - 3) + "_" + pos.col;
                }
                if (pos.row <= 7) {
                    if (pos.col <= 2) return "cherry_tree_small_" + (pos.row - 6) + "_" + pos.col;
                    return "cherry_ground_detail_" + (pos.row - 6) + "_" + (pos.col - 3);
                }
                if (pos.row <= 9) {
                    return "cherry_rock_" + (pos.row - 8) + "_" + pos.col;
                }
                if (pos.row <= 10) {
                    return "cherry_water_" + pos.col;
                }
                if (pos.row <= 12) {
                    return "cherry_platform_wood_" + (pos.row - 11) + "_" + pos.col;
                }
                if (pos.row <= 14) {
                    return "cherry_platform_stone_" + (pos.row - 13) + "_" + pos.col;
                }
                if (pos.row <= 16) {
                    return "cherry_decoration_" + (pos.row - 15) + "_" + pos.col;
                }
                if (pos.row <= 18) {
                    return "cherry_planter_" + (pos.row - 17) + "_" + pos.col;
                }
                return "cherry_bench_" + (pos.row - 19) + "_" + pos.col;
            };

            @Override
            public Map<String, TextureRegion> getAllTiles(TextureAtlas atlas) {
                return TilesetSplitter.splitTilesetCustomNaming(
                    atlas,
                    "CherryBlossom_Biome",
                    TILE_WIDTH,
                    TILE_HEIGHT,
                    0,
                    CHERRY_BLOSSOM_NAMING
                );
            }

            @Override
            public Map<String, TextureRegion> getTilesByType(TextureAtlas atlas, Enum<?> type) {
                return filterTilesByPrefix(getAllTiles(atlas), "cherry_" + type.name().toLowerCase());
            }

            public enum CherryBlossomTileType {
                TREE_GREEN,
                TREE_PINK,
                TREE_SMALL,
                ROCK_FORMATION,
                WATER,
                GROUND,
                PLATFORM_WOOD,
                PLATFORM_STONE,
                DECORATION,
                PLANTER,
                BENCH
            }
        }

        // Add the new Mountain biome handler
        public static class MountainBiomeHandler implements BiomeHandler {
            private static final TilesetSplitter.TileNamingStrategy MOUNTAIN_NAMING = (pos, base) -> {
                // Top grass and vegetation (rows 0-2)
                if (pos.row <= 2) {
                    if (pos.col <= 1) return "mountain_grass_" + pos.row + "_" + pos.col;
                    return "mountain_tallgrass_" + pos.row + "_" + (pos.col - 2);
                }

                // Rock formations and caves (rows 3-6)
                if (pos.row <= 6) {
                    return "mountain_rock_" + (pos.row - 3) + "_" + pos.col;
                }

                // Ground variations (rows 7-9)
                if (pos.row <= 9) {
                    if (pos.col <= 1) return "mountain_ground_" + (pos.row - 7) + "_" + pos.col;
                    return "mountain_ground_detail_" + (pos.row - 7) + "_" + (pos.col - 2);
                }

                // Water and waterfalls (rows 10-12)
                if (pos.row <= 12) {
                    if (pos.col <= 1) return "mountain_water_" + (pos.row - 10) + "_" + pos.col;
                    return "mountain_waterfall_" + (pos.row - 10) + "_" + (pos.col - 2);
                }

                // Cliff faces and platforms (rows 13-16)
                if (pos.row <= 16) {
                    if (pos.col <= 2) return "mountain_cliff_" + (pos.row - 13) + "_" + pos.col;
                    return "mountain_platform_" + (pos.row - 13) + "_" + (pos.col - 3);
                }

                // Trees and vegetation (remaining rows)
                if (pos.row <= 20) {
                    return "mountain_tree_" + (pos.row - 17) + "_" + pos.col;
                }

                return "mountain_decoration_" + (pos.row - 21) + "_" + pos.col;
            };

            @Override
            public Map<String, TextureRegion> getAllTiles(TextureAtlas atlas) {
                return TilesetSplitter.splitTilesetCustomNaming(
                    atlas,
                    "Mountain_Biome",
                    TILE_WIDTH,
                    TILE_HEIGHT,
                    0,
                    MOUNTAIN_NAMING
                );
            }

            @Override
            public Map<String, TextureRegion> getTilesByType(TextureAtlas atlas, Enum<?> type) {
                return filterTilesByPrefix(getAllTiles(atlas), "mountain_" + type.name().toLowerCase());
            }

            public enum MountainTileType {
                GRASS_TOP,
                TALL_GRASS,
                ROCK_FORMATION,
                CAVE,
                CLIFF_FACE,
                WATERFALL,
                PLATFORM,
                GROUND,
                WATER,
                DECORATION,
                TREES
            }
        }

        // Safari Biome Handler
        public static class SafariTilesetHandler {
            public static final TilesetSplitter.TileNamingStrategy SAFARI_NAMING = (pos, base) -> {
                // Top section - Grass variations (rows 0-2)
                if (pos.row <= 2) {
                    return "safari_grass_" + pos.row + "_" + pos.col;
                }

                // Ground variations (rows 3-6)
                if (pos.row <= 6) {
                    return "safari_ground_" + (pos.row - 3) + "_" + pos.col;
                }

                // Rock formations (rows 7-9)
                if (pos.row <= 9) {
                    return "safari_rock_" + (pos.row - 7) + "_" + pos.col;
                }

                // Trees section (rows 10-15)
                if (pos.row <= 15) {
                    return "safari_tree_" + (pos.row - 10) + "_" + pos.col;
                }

                // Building and fence section (remaining rows)
                if (isBuilding(pos)) {
                    return "safari_building_" + (pos.row - 16) + "_" + pos.col;
                }
                if (isFence(pos)) {
                    return "safari_fence_" + (pos.row - 16) + "_" + pos.col;
                }

                return "safari_decoration_" + (pos.row - 16) + "_" + pos.col;
            };

            private static boolean isBuilding(TilesetSplitter.TilePosition pos) {
                return pos.row >= 16 && pos.row <= 18 && pos.col <= 2;
            }

            private static boolean isFence(TilesetSplitter.TilePosition pos) {
                return pos.row >= 16 && pos.col >= 3;
            }

            public static Map<String, TextureRegion> getSafariTiles(TextureAtlas atlas) {
                return TilesetSplitter.splitTilesetCustomNaming(
                    atlas,
                    "Safari_Biome",
                    TILE_WIDTH,
                    TILE_HEIGHT,
                    0,
                    SAFARI_NAMING
                );
            }

            public enum SafariTileType {
                GRASS,
                GROUND,
                ROCK_FORMATION,
                TREE,
                BUILDING,
                FENCE,
                DECORATION
            }
        }

        public static class RuinsBiomeHandler implements BiomeHandler {
            private static final TilesetSplitter.TileNamingStrategy RUINS_NAMING = (pos, base) -> {
                if (pos.row <= 2) {
                    if (pos.col <= 1) return "ruins_wall_decorated_" + pos.row + "_" + pos.col;
                    if (pos.col == 2) return "ruins_door_" + pos.row;
                    return "ruins_window_" + pos.row + "_" + (pos.col - 3);
                }
                if (pos.row >= 3 && pos.row <= 4) {
                    return "ruins_octagon_" + (pos.row - 3) + "_" + pos.col;
                }
                if (pos.row == 5) {
                    return "ruins_tree_" + pos.col;
                }
                if (pos.row >= 6) {
                    if (pos.col <= 2) return "ruins_rock_" + (pos.row - 6) + "_" + pos.col;
                    return "ruins_ground_" + (pos.row - 6) + "_" + pos.col;
                }
                return base + "_unknown_" + pos.row + "_" + pos.col;
            };

            @Override
            public Map<String, TextureRegion> getAllTiles(TextureAtlas atlas) {
                return TilesetSplitter.splitTilesetCustomNaming(
                    atlas,
                    "Ruins_Biome",
                    TILE_WIDTH,
                    TILE_HEIGHT,
                    0,
                    RUINS_NAMING
                );
            }

            @Override
            public Map<String, TextureRegion> getTilesByType(TextureAtlas atlas, Enum<?> type) {
                return filterTilesByPrefix(getAllTiles(atlas), "ruins_" + type.name().toLowerCase());
            }

            public enum RuinsTileType {
                WALL_DECORATED,
                WALL_PLAIN,
                DOOR,
                WINDOW,
                OCTAGON_PLATFORM,
                STAIRS,
                TREE,
                ROCK_FORMATION,
                GROUND_DETAIL,
                VEGETATION
            }
        }
        public static class HauntedBiomeHandler implements BiomeHandler {
            private static final TilesetSplitter.TileNamingStrategy HAUNTED_NAMING = (pos, base) -> {
                // Trees and vegetation (rows 0-3)
                if (pos.row <= 3) {
                    if (pos.col <= 1) {
                        return "haunted_tree_dark_" + pos.row + "_" + pos.col;
                    }
                    return "haunted_tree_dead_" + pos.row + "_" + (pos.col - 2);
                }

                // Ground and paths (rows 4-5)
                if (pos.row <= 5) {
                    if (pos.col <= 1) {
                        return "haunted_ground_" + (pos.row - 4) + "_" + pos.col;
                    }
                    return "haunted_path_" + (pos.row - 4) + "_" + (pos.col - 2);
                }

                // Walls and platforms (rows 6-8)
                if (pos.row <= 8) {
                    if (pos.col <= 1) {
                        return "haunted_wall_stone_" + (pos.row - 6) + "_" + pos.col;
                    }
                    return "haunted_platform_" + (pos.row - 6) + "_" + (pos.col - 2);
                }

                // Mushrooms (rows 9-10)
                if (pos.row <= 10) {
                    return "haunted_mushroom_" + (pos.row - 9) + "_" + pos.col;
                }

                // Decorations (remaining rows)
                return "haunted_decoration_" + (pos.row - 11) + "_" + pos.col;
            };

            @Override
            public Map<String, TextureRegion> getAllTiles(TextureAtlas atlas) {
                return TilesetSplitter.splitTilesetCustomNaming(
                    atlas,
                    "Haunted_Biome",
                    TILE_WIDTH,
                    TILE_HEIGHT,
                    0,
                    HAUNTED_NAMING
                );
            }

            @Override
            public Map<String, TextureRegion> getTilesByType(TextureAtlas atlas, Enum<?> type) {
                return filterTilesByPrefix(getAllTiles(atlas), "haunted_" + type.name().toLowerCase());
            }

            public enum HauntedTileType {
                TREE_DARK,
                TREE_DEAD,
                GROUND,
                PATH,
                WALL_STONE,
                PLATFORM,
                MUSHROOM,
                DECORATION
            }
        }
        public static class VolcanoBiomeHandler implements BiomeHandler {
            private static final TilesetSplitter.TileNamingStrategy VOLCANO_NAMING = (pos, base) -> {
                // Top rock formations (rows 0-4)
                if (pos.row <= 4) {
                    return "volcano_rock_formation_" + pos.row + "_" + pos.col;
                }

                // Small rock formations and lava pools (rows 5-7)
                if (pos.row <= 7) {
                    if (pos.col <= 1) {
                        return "volcano_rock_small_" + (pos.row - 5) + "_" + pos.col;
                    }
                    return "volcano_lava_pool_" + (pos.row - 5) + "_" + (pos.col - 2);
                }

                // Lava flows and rock edges (rows 8-11)
                if (pos.row <= 11) {
                    if (pos.col % 2 == 0) {
                        return "volcano_rock_edge_" + (pos.row - 8) + "_" + (pos.col / 2);
                    }
                    return "volcano_lava_flow_" + (pos.row - 8) + "_" + ((pos.col - 1) / 2);
                }

                // Ground variations (rows 12-14)
                if (pos.row <= 14) {
                    return "volcano_ground_" + (pos.row - 12) + "_" + pos.col;
                }

                // Wooden platforms/bridges (remaining rows)
                return "volcano_bridge_" + (pos.row - 15) + "_" + pos.col;
            };

            @Override
            public Map<String, TextureRegion> getAllTiles(TextureAtlas atlas) {
                return TilesetSplitter.splitTilesetCustomNaming(
                    atlas,
                    "Volcano_Biome",
                    TILE_WIDTH,
                    TILE_HEIGHT,
                    0,
                    VOLCANO_NAMING
                );
            }

            @Override
            public Map<String, TextureRegion> getTilesByType(TextureAtlas atlas, Enum<?> type) {
                return filterTilesByPrefix(getAllTiles(atlas), "volcano_" + type.name().toLowerCase());
            }

            public enum VolcanoTileType {
                ROCK_FORMATION,
                ROCK_SMALL,
                LAVA_POOL,
                ROCK_EDGE,
                LAVA_FLOW,
                GROUND,
                BRIDGE
            }
        }
        public static class SnowBiomeHandler implements BiomeHandler {
            private static final TilesetSplitter.TileNamingStrategy SNOW_NAMING = (pos, base) -> {
                if (pos.row <= 1) {
                    return "snow_tree_snowy_" + pos.row + "_" + pos.col;
                }
                if (pos.row <= 3) {
                    return "snow_tree_pine_" + (pos.row - 2) + "_" + pos.col;
                }
                if (pos.row <= 5) {
                    return "snow_rock_" + (pos.row - 4) + "_" + pos.col;
                }
                if (pos.row <= 7) {
                    return "snow_cave_" + (pos.row - 6) + "_" + pos.col;
                }
                if (pos.row <= 9) {
                    return "snow_ice_" + (pos.row - 8) + "_" + pos.col;
                }
                if (pos.row == 10) {
                    return "snow_ground_" + pos.col;
                }
                return "snow_cliff_" + (pos.row - 11) + "_" + pos.col;
            };

            @Override
            public Map<String, TextureRegion> getAllTiles(TextureAtlas atlas) {
                return TilesetSplitter.splitTilesetCustomNaming(
                    atlas,
                    "Snow_Biome",
                    TILE_WIDTH,
                    TILE_HEIGHT,
                    0,
                    SNOW_NAMING
                );
            }

            @Override
            public Map<String, TextureRegion> getTilesByType(TextureAtlas atlas, Enum<?> type) {
                return filterTilesByPrefix(getAllTiles(atlas), "snow_" + type.name().toLowerCase());
            }

            public enum SnowTileType {
                TREE_SNOWY,
                TREE_PINE,
                ROCK,
                CAVE,
                ICE_FORMATION,
                GROUND,
                CLIFF,
                DECORATION
            }
        }public static class ForestBiomeHandler implements BiomeHandler {
            private static final TilesetSplitter.TileNamingStrategy FOREST_NAMING = (pos, base) -> {
                // Top section - Small vegetation and flowers (rows 0-2)
                if (pos.row <= 2) {
                    if (pos.col <= 2) {
                        return "forest_flower_" + pos.row + "_" + pos.col;
                    }
                    return "forest_vegetation_small_" + pos.row + "_" + (pos.col - 3);
                }

                // Middle section - Tall flowers/plants (rows 3-5)
                if (pos.row <= 5) {
                    return "forest_flower_tall_" + (pos.row - 3) + "_" + pos.col;
                }

                // Ground variations (rows 6-8)
                if (pos.row <= 8) {
                    if (pos.col <= 1) {
                        return "forest_ground_" + (pos.row - 6) + "_" + pos.col;
                    }
                    return "forest_ground_decorated_" + (pos.row - 6) + "_" + (pos.col - 2);
                }

                // Large trees section (remaining rows)
                return "forest_tree_large_" + (pos.row - 9) + "_" + pos.col;
            };

            @Override
            public Map<String, TextureRegion> getAllTiles(TextureAtlas atlas) {
                return TilesetSplitter.splitTilesetCustomNaming(
                    atlas,
                    "Forest_Biome",
                    TILE_WIDTH,
                    TILE_HEIGHT,
                    0,
                    FOREST_NAMING
                );
            }

            @Override
            public Map<String, TextureRegion> getTilesByType(TextureAtlas atlas, Enum<?> type) {
                return filterTilesByPrefix(getAllTiles(atlas), "forest_" + type.name().toLowerCase());
            }

            public enum ForestTileType {
                FLOWER,
                VEGETATION_SMALL,
                FLOWER_TALL,
                GROUND,
                GROUND_DECORATED,
                TREE_LARGE
            }
        }

        // Swamp Biome
        public static class SwampBiomeHandler implements BiomeHandler {
            private static final TilesetSplitter.TileNamingStrategy SWAMP_NAMING = (pos, base) -> {
                if (pos.row <= 1) {
                    return "swamp_tree_mangrove_" + pos.row + "_" + pos.col;
                }
                if (pos.row <= 3) {
                    return "swamp_roots_" + (pos.row - 2) + "_" + pos.col;
                }
                if (pos.row <= 5) {
                    String groundType = pos.row == 3 ? "green" : pos.row == 4 ? "brown" : "water";
                    return "swamp_ground_" + groundType + "_" + pos.col;
                }
                if (pos.row <= 7) {
                    return "swamp_platform_" + (pos.row - 6) + "_" + pos.col;
                }
                return "swamp_vegetation_" + (pos.row - 8) + "_" + pos.col;
            };

            public Map<String, TextureRegion> getAllTiles(TextureAtlas atlas) {
                return TilesetSplitter.splitTilesetCustomNaming(
                    atlas,
                    "Swamp_Biome",
                    TILE_WIDTH,
                    TILE_HEIGHT,
                    0,
                    SWAMP_NAMING
                );
            }

            public Map<String, TextureRegion> getTilesByType(TextureAtlas atlas, Enum<?> type) {
                return filterTilesByPrefix(getAllTiles(atlas), "swamp_" + type.name().toLowerCase());
            }

            public enum SwampTileType {
                TREE_MANGROVE,
                ROOT_SYSTEM,
                GROUND_GREEN,
                GROUND_BROWN,
                GROUND_WATER,
                PLATFORM,
                VEGETATION
            }
        }
    }
}

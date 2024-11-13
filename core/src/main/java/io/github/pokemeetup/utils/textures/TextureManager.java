package io.github.pokemeetup.utils.textures;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.github.pokemeetup.managers.BiomeHandlers;
import io.github.pokemeetup.pokemon.Pokemon;
import io.github.pokemeetup.system.gameplay.overworld.WorldObject;
import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;
import io.github.pokemeetup.utils.GameLogger;

import java.util.HashMap;
import java.util.Map;

import static io.github.pokemeetup.utils.textures.TileType.*;

public class TextureManager {
    public static final int TYPE_ICON_WIDTH = 64;
    public static final int TYPE_ICON_HEIGHT = 38; // Adjust based on your spritesheet
    public static final int STATUS_ICON_WIDTH = 44;
    public static final int STATUS_ICON_HEIGHT = 16;
    private static final Map<Integer, TextureRegion> tileTextures = new HashMap<>();
    private static final Map<BiomeType, Map<Integer, TextureRegion>> biomeTileTextures = new HashMap<>();
    private static final Map<Pokemon.PokemonType, TextureRegion> typeIcons = new HashMap<>();
    private static final Map<StatusCondition, TextureRegion> statusIcons = new HashMap<>();
    private static final Map<Pokemon.PokemonType, Color> TYPE_COLORS = new HashMap<>();
    private static final Map<StatusCondition, Color> STATUS_COLORS = new HashMap<>();
    public static TextureAtlas ui;
    public static TextureAtlas pokemonback;
    public static TextureAtlas pokemonfront;
    public static TextureAtlas pokemonicon;
    public static TextureAtlas pokemonoverworld;
    public static TextureAtlas items;
    public static TextureAtlas boy;
    public static TextureAtlas tiles;
    public static TextureAtlas battlebacks;
    public static TextureAtlas mountains;
    public static TextureAtlas effects;
    public static TextureAtlas atlasPlains;
    public static TextureAtlas atlasForest;
    public static TextureAtlas atlasMountains;
    public static TextureAtlas atlasRuins;
    public static TextureAtlas atlasSafari;
    public static TextureAtlas atlasSnow;
    public static TextureAtlas atlasDesert;
    public static TextureAtlas atlasSwamp;
    public static TextureAtlas atlasHaunted;
    public static TextureAtlas atlasCherryBlossom;
    public static TextureAtlas atlasVolcano;

    private static boolean usingFallbackSystem = false;
    private static Texture whitePixel;private static void validateHauntedTextures() {
        // List of all expected texture IDs
        int[] expectedIds = {
            5110, 5111, 5112,  // Trees
            5420, 5421, 5422,  // Platforms
            5430, 5431, 5432,  // Walls
            5500, 5501, 5502,  // Mushrooms
            5600, 5601, 5602   // Decorations
        };

        for (int id : expectedIds) {
            if (tileTextures.get(id) == null) {
                GameLogger.error("Missing haunted texture for ID: " + id);
            }
        }
    }

    static {
        // Initialize status colors
        STATUS_COLORS.put(StatusCondition.NONE, Color.WHITE);
        STATUS_COLORS.put(StatusCondition.SLEEP, Color.GRAY);
        STATUS_COLORS.put(StatusCondition.POISON, new Color(0.627f, 0.439f, 0.627f, 1));
        STATUS_COLORS.put(StatusCondition.BURN, new Color(0.940f, 0.501f, 0.376f, 1));
        STATUS_COLORS.put(StatusCondition.FREEZE, new Color(0.564f, 0.815f, 0.940f, 1));
        STATUS_COLORS.put(StatusCondition.PARALYSIS, new Color(0.972f, 0.815f, 0.376f, 1));
        STATUS_COLORS.put(StatusCondition.TOXIC, new Color(0.5f, 0.1f, 0.5f, 1));
        STATUS_COLORS.put(StatusCondition.CONFUSION, new Color(0.940f, 0.376f, 0.564f, 1));
    }

    static {
        // Update type color mappings
        // Initialize type colors
        TYPE_COLORS.put(Pokemon.PokemonType.NORMAL, new Color(0.658f, 0.658f, 0.658f, 1));    // A8A878
        TYPE_COLORS.put(Pokemon.PokemonType.FIGHTING, new Color(0.752f, 0.470f, 0.470f, 1));  // C03028
        TYPE_COLORS.put(Pokemon.PokemonType.FLYING, new Color(0.658f, 0.564f, 0.940f, 1));    // A890F0
        TYPE_COLORS.put(Pokemon.PokemonType.POISON, new Color(0.627f, 0.439f, 0.627f, 1));    // A040A0
        TYPE_COLORS.put(Pokemon.PokemonType.GROUND, new Color(0.878f, 0.752f, 0.470f, 1));    // E0C068
        TYPE_COLORS.put(Pokemon.PokemonType.ROCK, new Color(0.752f, 0.658f, 0.439f, 1));      // B8A038
        TYPE_COLORS.put(Pokemon.PokemonType.BUG, new Color(0.658f, 0.752f, 0.439f, 1));       // A8B820
        TYPE_COLORS.put(Pokemon.PokemonType.GHOST, new Color(0.439f, 0.439f, 0.627f, 1));     // 705898
        TYPE_COLORS.put(Pokemon.PokemonType.STEEL, new Color(0.752f, 0.752f, 0.815f, 1));     // B8B8D0
        TYPE_COLORS.put(Pokemon.PokemonType.FIRE, new Color(0.940f, 0.501f, 0.376f, 1));      // F08030
        TYPE_COLORS.put(Pokemon.PokemonType.WATER, new Color(0.376f, 0.564f, 0.940f, 1));     // 6890F0
        TYPE_COLORS.put(Pokemon.PokemonType.GRASS, new Color(0.470f, 0.815f, 0.376f, 1));     // 78C850
        TYPE_COLORS.put(Pokemon.PokemonType.ELECTRIC, new Color(0.972f, 0.815f, 0.376f, 1));  // F8D030
        TYPE_COLORS.put(Pokemon.PokemonType.PSYCHIC, new Color(0.940f, 0.376f, 0.564f, 1));   // F85888
        TYPE_COLORS.put(Pokemon.PokemonType.ICE, new Color(0.564f, 0.815f, 0.940f, 1));       // 98D8D8
        TYPE_COLORS.put(Pokemon.PokemonType.DRAGON, new Color(0.439f, 0.376f, 0.940f, 1));    // 7038F8
        TYPE_COLORS.put(Pokemon.PokemonType.DARK, new Color(0.439f, 0.376f, 0.376f, 1));      // 705848
        TYPE_COLORS.put(Pokemon.PokemonType.FAIRY, new Color(0.940f, 0.627f, 0.940f, 1));     // F0B6BC
        TYPE_COLORS.put(Pokemon.PokemonType.UNKNOWN, new Color(0.470f, 0.470f, 0.470f, 1));   // 68A090

    }

    public static TextureAtlas getEffects() {
        return effects;
    }

    private static void createFallbackIcons() {
        // Create colored rectangle icons for types
        for (Pokemon.PokemonType type : Pokemon.PokemonType.values()) {
            Color color = TYPE_COLORS.get(type);
            TextureRegion icon = createColoredIcon(color, TYPE_ICON_WIDTH, TYPE_ICON_HEIGHT, type.name());
            typeIcons.put(type, icon);
        }

        // Create colored rectangle icons for statuses
        for (StatusCondition status : StatusCondition.values()) {
            if (status != StatusCondition.NONE) {
                Color color = STATUS_COLORS.get(status);
                TextureRegion icon = createColoredIcon(color, STATUS_ICON_WIDTH, STATUS_ICON_HEIGHT, status.name());
                statusIcons.put(status, icon);
            }
        }
    }

    public static TextureRegion getTextureForObjectType(WorldObject.ObjectType objectType) {
        // Return the corresponding texture based on object type
        switch (objectType) {
            case TREE:
                return tiles.findRegion("tree");
            case SNOW_TREE:
                return tiles.findRegion("snow_tree");
            case HAUNTED_TREE:
                return tiles.findRegion("haunted_tree");
            case POKEBALL:
                return items.findRegion("pokeball");
            case CACTUS:
                return tiles.findRegion("desert_cactus");
            case SUNFLOWER:
                return tiles.findRegion("sunflower");
            case VINES:
                return tiles.findRegion("vines");
            case SNOW_BALL:
                return tiles.findRegion("snowball");
            case RAIN_TREE:
                return tiles.findRegion("rain_tree");
            case BUSH:
                return tiles.findRegion("bush");
            case DEAD_TREE:
                return tiles.findRegion("dead_tree");
            case SMALL_HAUNTED_TREE:
                return tiles.findRegion("small_haunted_tree");
            default:
                GameLogger.error("Missing texture for object type: " + objectType);
                return null; // or a default texture if you prefer
        }
    }

    private static TextureRegion createColoredIcon(Color color, int width, int height, String text) {
        Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        pixmap.setColor(color);
        pixmap.fillRectangle(0, 0, width, height);

        // Add a border
        pixmap.setColor(Color.WHITE);
        pixmap.drawRectangle(0, 0, width, height);

        Texture texture = new Texture(pixmap);
        pixmap.dispose();

        return new TextureRegion(texture);
    }

    private static void loadTypeAndStatusIcons() {
        TextureRegion typesSheet = ui.findRegion("types");
        TextureRegion statusSheet = ui.findRegion("statuses");

        if (typesSheet == null || statusSheet == null) {
            GameLogger.info("Sprite sheets not found, using fallback system");
            usingFallbackSystem = true;
            createFallbackIcons();
            return;
        }

        TextureRegion[][] typeFrames = typesSheet.split(TYPE_ICON_WIDTH, TYPE_ICON_HEIGHT);

        // Map each type to its icon


        // Split the status sheet into individual icons
        TextureRegion[][] statusFrames = statusSheet.split(STATUS_ICON_WIDTH, STATUS_ICON_HEIGHT);

        // Map each status to its icon
        for (StatusCondition status : StatusCondition.values()) {
            if (status != StatusCondition.NONE && (status.getIndex() - 1) < statusFrames.length) { // Corrected condition
                statusIcons.put(status, statusFrames[status.getIndex() - 1][0]); // Corrected access
            } else if (status != StatusCondition.NONE) {
                GameLogger.error("Missing status icon for: " + status.name());
            }
        }

        boolean hasAllIcons = true;
        for (Pokemon.PokemonType type : Pokemon.PokemonType.values()) {
            if (!typeIcons.containsKey(type)) {
                hasAllIcons = false;
                break;
            }
        }

        for (StatusCondition status : StatusCondition.values()) {
            if (status != StatusCondition.NONE && !statusIcons.containsKey(status)) {
                hasAllIcons = false;
                break;
            }
        }

        if (!hasAllIcons) {
            GameLogger.info("Missing icons detected, using fallback system");
            usingFallbackSystem = true;
            createFallbackIcons();
        }
    }

    public static TextureRegion getStatusIcon(StatusCondition status) {
        return statusIcons.get(status);
    }

    // Helper method to get type color
    public static Color getTypeColor(Pokemon.PokemonType type) {
        return TYPE_COLORS.getOrDefault(type, Color.WHITE);
    }

    public static TextureRegion getTypeIcon(Pokemon.PokemonType type) {
        return typeIcons.get(type);
    }

    public static TextureRegion getOverworldSprite(String name) {
        if (name == null) {
            GameLogger.error("Attempted to get overworld sprite with null name.");
            return null;
        }

        // Normalize the name to lowercase to ensure consistency
        String normalizedName = name.toUpperCase();

        TextureRegion sprite = pokemonoverworld.findRegion(normalizedName + "_overworld");
        if (sprite == null) {
            GameLogger.error("Overworld sprite for Pokémon '" + name + "' not found.");
            return null;
        }
        return sprite;
    }

    public static TextureRegion getDefaultOverworldSprite() {
        return pokemonoverworld.findRegion("default_pokemon_overworld");
    }

    public static TextureAtlas getUi() {
        return ui;
    }

    public static TextureAtlas getPokemonback() {
        return pokemonback;
    }

    public static TextureAtlas getPokemonfront() {
        return pokemonfront;
    }

    public static TextureAtlas getPokemonicon() {
        return pokemonicon;
    }

    public static TextureAtlas getPokemonoverworld() {
        return pokemonoverworld;
    }

    public static TextureAtlas getItems() {
        return items;
    }

    public static TextureAtlas getBoy() {
        try {
            if (boy == null) {
                GameLogger.error("Boy atlas is null");
                return null;
            }

            // Verify atlas textures
            for (Texture texture : boy.getTextures()) {
                if (texture == null) {
                    GameLogger.error("Boy atlas texture is null or disposed");
                    return null;
                }
            }

            // Verify some key regions
            String[] testRegions = {
                "boy_walk_down",
                "boy_walk_up",
                "boy_run_down",
                "boy_run_up"
            };

            for (String regionName : testRegions) {
                TextureAtlas.AtlasRegion region = boy.findRegion(regionName, 1);
                if (region == null || region.getTexture() == null) {
                    GameLogger.error("Critical region missing or invalid: " + regionName);
                    return null;
                }
            }

            return boy;
        } catch (Exception e) {
            GameLogger.error("Error accessing boy atlas: " + e.getMessage());
            return null;
        }
    }

    // Add this debug method
    public static void debugBoyAtlas() {
        if (boy == null) {
            GameLogger.error("Boy atlas is null!");
            return;
        }

    }

    public static void debugAtlasState(String name, TextureAtlas atlas) {
        if (atlas == null) {
            return;
        }

        try {
        } catch (Exception e) {
        }
    }

    public static void reloadAtlas() {
        try {
            if (boy != null) {
                boy.dispose();
            }
            // Assuming your atlas file path
            boy = new TextureAtlas(Gdx.files.internal("atlas/boy-gfx-atlas"));
            GameLogger.info("Boy atlas reloaded");
            debugAtlasState("Boy", boy);
        } catch (Exception e) {
            GameLogger.error("Failed to reload boy atlas: " + e.getMessage());
        }
    }

    public static TextureAtlas getBattlebacks() {
        return battlebacks;
    }

    public static TextureAtlas getTiles() {
        return tiles;
    }

    public static void debugPokemonAtlas() {
    }

    public static Texture getWhitePixel() {
        if (whitePixel == null) {
            // Create on demand if not initialized
            Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
            pixmap.setColor(Color.WHITE);
            pixmap.fill();
            whitePixel = new Texture(pixmap);
            pixmap.dispose();
        }
        return whitePixel;
    }

    public static void initialize(TextureAtlas battlebacks, TextureAtlas ui,
                                  TextureAtlas pokemonback, TextureAtlas pokemonfront, TextureAtlas pokemonicon,
                                  TextureAtlas pokemonoverworld, TextureAtlas items, TextureAtlas boy,
                                  TextureAtlas tiles, TextureAtlas effects, TextureAtlas mountains,
                                  TextureAtlas plains, TextureAtlas ruins, TextureAtlas safari,
                                  TextureAtlas snow, TextureAtlas desert, TextureAtlas swamp,
                                  TextureAtlas haunted, TextureAtlas cherryBlossom,
                                  TextureAtlas volcano, TextureAtlas atlasMountains, TextureAtlas atlasForest) {

        TextureManager.effects = effects;
        TextureManager.battlebacks = battlebacks;
        TextureManager.ui = ui;
        TextureManager.pokemonback = pokemonback;
        TextureManager.pokemonfront = pokemonfront;
        TextureManager.pokemonicon = pokemonicon;
        TextureManager.pokemonoverworld = pokemonoverworld;
        TextureManager.items = items;
        TextureManager.boy = boy;
        TextureManager.tiles = tiles;
        TextureManager.mountains = mountains;
        TextureManager.atlasForest = atlasForest;

        // Create white pixel texture
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        whitePixel = new Texture(pixmap);
        pixmap.dispose();
        // Initialize new atlas variables
        TextureManager.atlasPlains = plains;
        TextureManager.atlasRuins = ruins;
        TextureManager.atlasSafari = safari;
        TextureManager.atlasSnow = snow;
        TextureManager.atlasDesert = desert;
        TextureManager.atlasSwamp = swamp; TextureManager.atlasHaunted = haunted;
        if (haunted == null) {
            GameLogger.error("Haunted atlas failed to initialize!");
        } else {
            GameLogger.info("Haunted atlas initialized with " + haunted.getRegions().size + " regions");
        }
        TextureManager.atlasCherryBlossom = cherryBlossom;
        TextureManager.atlasMountains = atlasMountains;
        TextureManager.atlasVolcano = volcano;
        if (haunted != null) {
            GameLogger.info("=== Haunted Atlas Contents ===");
            for (TextureAtlas.AtlasRegion region : haunted.getRegions()) {
                GameLogger.info(region.name + " [" + region.getRegionWidth() + "x" + region.getRegionHeight() + "]");
            }
            GameLogger.info("============================");
        }
        // Initialize biome textures

        loadTypeAndStatusIcons();
        loadCentralTileTextures();

        GameLogger.info("=== Initializing Texture Manager ===");
        debugAtlas("tiles", tiles);
        debugAtlas("ui", ui);
        debugAtlas("boy", boy);
        debugAtlas("plains", plains);
        debugAtlas("ruins", ruins);
        debugAtlas("safari", safari);
        debugAtlas("snow", snow);
        debugAtlas("desert", desert);
        debugAtlas("swamp", swamp);
        debugAtlas("haunted", haunted);
        debugAtlas("cherryBlossom", cherryBlossom);
        debugAtlas("volcano", volcano);
        debugPokemonAtlas();
        GameLogger.info("=== Texture Manager Initialization Complete ===");
    }


    private static void loadHauntedBiomeTextures() {
        if (atlasHaunted == null) {
            GameLogger.error("Haunted atlas is null!");
            return;
        }

        BiomeHandlers.PlainsBiomeHandler.HauntedBiomeHandler handler = new BiomeHandlers.PlainsBiomeHandler.HauntedBiomeHandler();
        Map<String, TextureRegion> hauntedTiles = handler.getAllTiles(atlasHaunted);

        // Map the split tiles to our tile IDs
        for (Map.Entry<String, TextureRegion> entry : hauntedTiles.entrySet()) {
            String key = entry.getKey();
            TextureRegion region = entry.getValue();

            if (key.startsWith("haunted_tree_dark_")) {
                int index = extractIndex(key);
                tileTextures.put(5110 + index, region);
            } else if (key.startsWith("haunted_platform_")) {
                int index = extractIndex(key);
                tileTextures.put(5420 + index, region);
            } else if (key.startsWith("haunted_wall_stone_")) {
                int index = extractIndex(key);
                tileTextures.put(5430 + index, region);
            } else if (key.startsWith("haunted_mushroom_")) {
                int index = extractIndex(key);
                tileTextures.put(5500 + index, region);
            } else if (key.startsWith("haunted_decoration_")) {
                int index = extractIndex(key);
                tileTextures.put(5600 + index, region);
            }
        }
    }private static int extractIndex(String tileName) {
        String[] parts = tileName.split("_");
        if (parts.length >= 4) {
            try {
                int row = Integer.parseInt(parts[parts.length - 2]);
                int col = Integer.parseInt(parts[parts.length - 1]);
                return row * 10 + col;
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }
    private static void loadVolcanoBiomeTextures() {
        // Ground and Base Tiles
        tileTextures.put(VOLCANO_GROUND_ROCK, atlasVolcano.findRegion("volcano_ground_rock_0_0"));
        tileTextures.put(VOLCANO_GROUND_ROCK, atlasVolcano.findRegion("volcano_ground_rock_0_1"));
        tileTextures.put(VOLCANO_GROUND_LAVA, atlasVolcano.findRegion("volcano_ground_lava_0_0"));
        tileTextures.put(VOLCANO_BRIDGE, atlasVolcano.findRegion("volcano_bridge_0_0"));

        // Rock Formations
        tileTextures.put(VOLCANO_ROCK_FORMATION, atlasVolcano.findRegion("volcano_rock_formation_0_0"));
        tileTextures.put(VOLCANO_ROCK_FORMATION, atlasVolcano.findRegion("volcano_rock_formation_1_0"));
        tileTextures.put(VOLCANO_ROCK_SMALL, atlasVolcano.findRegion("volcano_rock_small_0_0"));
        tileTextures.put(VOLCANO_ROCK_EDGE, atlasVolcano.findRegion("volcano_rock_edge_0_0"));

        // Lava Features
        tileTextures.put(VOLCANO_LAVA_POOL, atlasVolcano.findRegion("volcano_lava_pool_0_0"));
        tileTextures.put(VOLCANO_LAVA_FLOW, atlasVolcano.findRegion("volcano_lava_flow_0_0"));
        tileTextures.put(VOLCANO_PLATFORM, atlasVolcano.findRegion("volcano_platform_0_0"));
    }

    private static void loadForestBiomeTextures() {
        // Ground Types
        tileTextures.put(FOREST_GROUND, atlasForest.findRegion("forest_ground_0_0"));
        tileTextures.put(FOREST_GROUND, atlasForest.findRegion("forest_ground_0_1"));
        tileTextures.put(FOREST_GROUND_DECORATED, atlasForest.findRegion("forest_ground_decorated_0_0"));
        tileTextures.put(FOREST_GROUND_DECORATED, atlasForest.findRegion("forest_ground_decorated_0_1"));
        tileTextures.put(FOREST_PATH, atlasForest.findRegion("forest_path_0_0"));

        // Flora
        tileTextures.put(FOREST_FLOWER, atlasForest.findRegion("forest_flower_0_0"));
        tileTextures.put(FOREST_FLOWER, atlasForest.findRegion("forest_flower_0_1"));
        tileTextures.put(FOREST_FLOWER, atlasForest.findRegion("forest_flower_0_2"));
        tileTextures.put(FOREST_FLOWER_TALL, atlasForest.findRegion("forest_flower_tall_0_0"));
        tileTextures.put(FOREST_FLOWER_TALL, atlasForest.findRegion("forest_flower_tall_0_1"));
        tileTextures.put(FOREST_FLOWER_TALL, atlasForest.findRegion("forest_flower_tall_0_2"));

        // Trees and Vegetation
        tileTextures.put(FOREST_TREE_LARGE, atlasForest.findRegion("forest_tree_large_0_0"));
        tileTextures.put(FOREST_TREE_LARGE, atlasForest.findRegion("forest_tree_large_0_1"));
        tileTextures.put(FOREST_TREE_LARGE, atlasForest.findRegion("forest_tree_large_1_0"));
        tileTextures.put(FOREST_TREE_LARGE, atlasForest.findRegion("forest_tree_large_1_1"));
        tileTextures.put(FOREST_VEGETATION_SMALL, atlasForest.findRegion("forest_vegetation_small_0_0"));
        tileTextures.put(FOREST_VEGETATION_SMALL, atlasForest.findRegion("forest_vegetation_small_0_1"));
    }

    private static void loadCherryBlossomBiomeTextures() {
        // Ground Types
        tileTextures.put(CHERRY_GROUND, atlasCherryBlossom.findRegion("cherry_ground_0_0"));
        tileTextures.put(CHERRY_GROUND_DETAIL, atlasCherryBlossom.findRegion("cherry_ground_detail_0_0"));
        tileTextures.put(CHERRY_GROUND_DETAIL, atlasCherryBlossom.findRegion("cherry_ground_detail_0_1"));
        tileTextures.put(CHERRY_WATER, atlasCherryBlossom.findRegion("cherry_water_0"));

        // Trees
        tileTextures.put(CHERRY_TREE_GREEN, atlasCherryBlossom.findRegion("cherry_tree_green_0_0"));
        tileTextures.put(CHERRY_TREE_GREEN, atlasCherryBlossom.findRegion("cherry_tree_green_1_0"));
        tileTextures.put(CHERRY_TREE_GREEN, atlasCherryBlossom.findRegion("cherry_tree_green_2_0"));
        tileTextures.put(CHERRY_TREE_PINK, atlasCherryBlossom.findRegion("cherry_tree_pink_0_0"));
        tileTextures.put(CHERRY_TREE_PINK, atlasCherryBlossom.findRegion("cherry_tree_pink_1_0"));
        tileTextures.put(CHERRY_TREE_PINK, atlasCherryBlossom.findRegion("cherry_tree_pink_2_0"));
        tileTextures.put(CHERRY_TREE_SMALL, atlasCherryBlossom.findRegion("cherry_tree_small_0_0"));
        tileTextures.put(CHERRY_TREE_SMALL, atlasCherryBlossom.findRegion("cherry_tree_small_1_0"));

        // Structures
        tileTextures.put(CHERRY_PLATFORM_WOOD, atlasCherryBlossom.findRegion("cherry_platform_wood_0_0"));
        tileTextures.put(CHERRY_PLATFORM_WOOD, atlasCherryBlossom.findRegion("cherry_platform_wood_1_0"));
        tileTextures.put(CHERRY_PLATFORM_STONE, atlasCherryBlossom.findRegion("cherry_platform_stone_0_0"));
        tileTextures.put(CHERRY_PLATFORM_STONE, atlasCherryBlossom.findRegion("cherry_platform_stone_1_0"));
        tileTextures.put(CHERRY_PLANTER, atlasCherryBlossom.findRegion("cherry_planter_0_0"));
        tileTextures.put(CHERRY_BENCH, atlasCherryBlossom.findRegion("cherry_bench_0_0"));

        // Features
        tileTextures.put(CHERRY_ROCK, atlasCherryBlossom.findRegion("cherry_rock_0_0"));
        tileTextures.put(CHERRY_ROCK, atlasCherryBlossom.findRegion("cherry_rock_1_0"));
        tileTextures.put(CHERRY_DECORATION, atlasCherryBlossom.findRegion("cherry_decoration_0_0"));
    }

    private static void loadSafariBiomeTextures() {
        // Ground Types
        tileTextures.put(SAFARI_GROUND_GRASS, atlasSafari.findRegion("safari_ground_grass_0"));
        tileTextures.put(SAFARI_GROUND_GRASS, atlasSafari.findRegion("safari_ground_grass_1"));
        tileTextures.put(SAFARI_GROUND_DIRT, atlasSafari.findRegion("safari_ground_dirt_0"));
        tileTextures.put(SAFARI_GROUND_DIRT, atlasSafari.findRegion("safari_ground_dirt_1"));

        // Rock Formations
        tileTextures.put(SAFARI_ROCK, atlasSafari.findRegion("safari_rock_0_0"));
        tileTextures.put(SAFARI_ROCK, atlasSafari.findRegion("safari_rock_0_1"));
        tileTextures.put(SAFARI_ROCK, atlasSafari.findRegion("safari_rock_0_2"));
        tileTextures.put(SAFARI_ROCK_BORDER, atlasSafari.findRegion("safari_rock_border_0_0"));
        tileTextures.put(SAFARI_ROCK_BORDER, atlasSafari.findRegion("safari_rock_border_0_1"));

        // Vegetation
        tileTextures.put(SAFARI_TREE_LARGE, atlasSafari.findRegion("safari_tree_large_0_0"));
        tileTextures.put(SAFARI_TREE_LARGE, atlasSafari.findRegion("safari_tree_large_0_1"));
        tileTextures.put(SAFARI_VEGETATION_SMALL, atlasSafari.findRegion("safari_vegetation_small_0_0"));
        tileTextures.put(SAFARI_VEGETATION_DENSE, atlasSafari.findRegion("safari_vegetation_dense_0_0"));
    }

    private static void loadSwampBiomeTextures() {
        // Ground Types
        tileTextures.put(SWAMP_GROUND_WATER, atlasSwamp.findRegion("swamp_ground_water_0_0"));
        tileTextures.put(SWAMP_GROUND_GREEN, atlasSwamp.findRegion("swamp_ground_green_0_0"));
        tileTextures.put(SWAMP_GROUND_BROWN, atlasSwamp.findRegion("swamp_ground_brown_0_0"));

        // Vegetation
        tileTextures.put(SWAMP_TREE_MANGROVE, atlasSwamp.findRegion("swamp_tree_mangrove_0_0"));
        tileTextures.put(SWAMP_TREE_MANGROVE, atlasSwamp.findRegion("swamp_tree_mangrove_0_1"));
        tileTextures.put(SWAMP_ROOTS, atlasSwamp.findRegion("swamp_roots_0_0"));
        tileTextures.put(SWAMP_VEGETATION, atlasSwamp.findRegion("swamp_vegetation_0_0"));

        // Structures
        tileTextures.put(SWAMP_PLATFORM, atlasSwamp.findRegion("swamp_platform_0_0"));
        tileTextures.put(SWAMP_PLATFORM, atlasSwamp.findRegion("swamp_platform_0_1"));
    }
    private static void loadAllBiomeTextures() {
        // Haunted Biome
        if (atlasHaunted != null) {
            BiomeHandlers.PlainsBiomeHandler.HauntedBiomeHandler hauntedHandler = new BiomeHandlers.PlainsBiomeHandler.HauntedBiomeHandler();
            loadTilesForBiome(hauntedHandler.getAllTiles(atlasHaunted), "haunted", 5000);
        }

        // Safari Biome
        if (atlasSafari != null) {
            BiomeHandlers.PlainsBiomeHandler.SafariTilesetHandler safariHandler = new BiomeHandlers.PlainsBiomeHandler.SafariTilesetHandler();
            loadTilesForBiome(safariHandler.getSafariTiles(atlasSafari), "safari", 8000);
        }

        // Snow Biome
        if (atlasSnow != null) {
            BiomeHandlers.PlainsBiomeHandler.SnowBiomeHandler snowHandler = new BiomeHandlers.PlainsBiomeHandler.SnowBiomeHandler();
            loadTilesForBiome(snowHandler.getAllTiles(atlasSnow), "snow", 4000);
        }

        // Forest Biome
        if (atlasForest != null) {
            BiomeHandlers.PlainsBiomeHandler.ForestBiomeHandler forestHandler = new BiomeHandlers.PlainsBiomeHandler.ForestBiomeHandler();
            loadTilesForBiome(forestHandler.getAllTiles(atlasForest), "forest", 3000);
        }

        // Volcano Biome
        if (atlasVolcano != null) {
            BiomeHandlers.PlainsBiomeHandler.VolcanoBiomeHandler volcanoHandler = new BiomeHandlers.PlainsBiomeHandler.VolcanoBiomeHandler();
            loadTilesForBiome(volcanoHandler.getAllTiles(atlasVolcano), "volcano", 10000);
        }

        // Cherry Blossom Biome
        if (atlasCherryBlossom != null) {
            BiomeHandlers.PlainsBiomeHandler.CherryBlossomBiomeHandler cherryHandler = new BiomeHandlers.PlainsBiomeHandler.CherryBlossomBiomeHandler();
            loadTilesForBiome(cherryHandler.getAllTiles(atlasCherryBlossom), "cherry", 7000);
        }

        // Swamp Biome
        if (atlasSwamp != null) {
            BiomeHandlers.PlainsBiomeHandler.SwampBiomeHandler swampHandler = new BiomeHandlers.PlainsBiomeHandler.SwampBiomeHandler();
            loadTilesForBiome(swampHandler.getAllTiles(atlasSwamp), "swamp", 9000);
        }
    }

    private static void loadTilesForBiome(Map<String, TextureRegion> biomeTiles, String prefix, int baseOffset) {
        for (Map.Entry<String, TextureRegion> entry : biomeTiles.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(prefix + "_")) {
                String[] parts = key.split("_");
                if (parts.length >= 4) {
                    try {
                        String type = parts[1];
                        int row = Integer.parseInt(parts[parts.length - 2]);
                        int col = Integer.parseInt(parts[parts.length - 1]);
                        int typeOffset = getTileTypeOffset(type);
                        int finalId = baseOffset + typeOffset + (row * 10) + col;
                        tileTextures.put(finalId, entry.getValue());
                    } catch (NumberFormatException e) {
                        GameLogger.error("Failed to parse indices for tile: " + key);
                    }
                }
            }
        }
    }

    private static int getTileTypeOffset(String type) {
        switch (type) {
            case "tree": return 110;
            case "ground": return 20;
            case "wall": return 430;
            case "platform": return 420;
            case "decoration": return 600;
            case "rock": return 200;
            case "water": return 30;
            case "vegetation": return 130;
            case "mushroom": return 500;
            default: return 0;
        }
    }
    private static void loadCentralTileTextures() {
        // Ensure tiles atlas is loaded
        if (tiles == null) {
            GameLogger.error("Tiles atlas is not initialized!");
            return;
        }loadAllBiomeTextures();

        // Map tile type IDs to their corresponding texture regions
        tileTextures.put(WATER, tiles.findRegion("water"));
        tileTextures.put(GRASS, tiles.findRegion("grass"));
        tileTextures.put(SAND, tiles.findRegion("sand"));
        tileTextures.put(ROCK, tiles.findRegion("rock"));
        tileTextures.put(SNOW, tiles.findRegion("snow"));
        tileTextures.put(HAUNTED_GRASS, tiles.findRegion("haunted_grass"));
        tileTextures.put(SNOW_TALL_GRASS, tiles.findRegion("snow_tall_grass"));
        tileTextures.put(HAUNTED_TALL_GRASS, tiles.findRegion("haunted_tall_grass"));
        tileTextures.put(HAUNTED_SHROOM, tiles.findRegion("haunted_shroom"));
        tileTextures.put(HAUNTED_SHROOMS, tiles.findRegion("haunted_shrooms"));
        tileTextures.put(TALL_GRASS, tiles.findRegion("tall_grass"));
        tileTextures.put(FOREST_GRASS, tiles.findRegion("forest_grass"));
        tileTextures.put(FOREST_TALL_GRASS, tiles.findRegion("forest_tall_grass"));
        tileTextures.put(RAIN_FOREST_GRASS, tiles.findRegion("rain_forest_grass"));
        tileTextures.put(RAIN_FOREST_TALL_GRASS, tiles.findRegion("rain_forest_tall_grass"));
        tileTextures.put(DESERT_SAND, tiles.findRegion("desert_sand"));
        tileTextures.put(DESERT_ROCKS, tiles.findRegion("desert_rock"));
        tileTextures.put(DESERT_GRASS, tiles.findRegion("desert_grass"));
        tileTextures.put(GRASS_2, tiles.findRegion("grass", 2));
        tileTextures.put(FLOWER, tiles.findRegion("flower"));
        tileTextures.put(FLOWER_1, tiles.findRegion("flower", 1));
        tileTextures.put(TALL_GRASS_2, tiles.findRegion("tall_grass", 2));
        tileTextures.put(TALL_GRASS_3, tiles.findRegion("tall_grass", 3));
        tileTextures.put(FLOWER_2, tiles.findRegion("flower", 2));
        tileTextures.put(GRASS_3, tiles.findRegion("grass", 3));
        tileTextures.put(TileType.MOUNTAIN_WALL, tiles.findRegion("mountainBASEMIDDLE"));
        tileTextures.put(TileType.MOUNTAIN_CORNER_TL, tiles.findRegion("mountainTOPLEFT")); // Top-left corner
        tileTextures.put(TileType.MOUNTAIN_CORNER_TR, tiles.findRegion("mountaintopRIGHT")); // Top-right corner
        tileTextures.put(TileType.MOUNTAIN_CORNER_BL, tiles.findRegion("mountainBASELEFT")); // Bottom-left corner
        tileTextures.put(TileType.MOUNTAIN_CORNER_BR, tiles.findRegion("mountainbaseRIGHT")); // Bottom-right corner
        tileTextures.put(TileType.MOUNTAIN_SLOPE_LEFT, tiles.findRegion("tile080"));
        tileTextures.put(TileType.MOUNTAIN_SLOPE_RIGHT, tiles.findRegion("tile046"));
        tileTextures.put(TileType.MOUNTAIN_STAIRS, tiles.findRegion("mountainstairsMiddle"));
        tileTextures.put(TileType.MOUNTAIN_PATH, tiles.findRegion("tile081"));
        tileTextures.put(TileType.MOUNTAIN_BASE_EDGE, tiles.findRegion("tile038"));
        tileTextures.put(TileType.MOUNTAIN_STAIRS_LEFT, tiles.findRegion("mountainstairsLEFT")); // Left stairs
        tileTextures.put(TileType.MOUNTAIN_STAIRS_RIGHT, tiles.findRegion("mountainstarsRIGHT")); // Right stairs
        tileTextures.put(TileType.MOUNTAIN_PEAK, tiles.findRegion("tile0118"));
        tileTextures.put(TileType.MOUNTAIN_BASE, tiles.findRegion("mountainBASEMIDDLE"));
        tileTextures.put(TileType.MOUNTAIN_EDGE_LEFT, tiles.findRegion("MOUNTAINMIDDLELEFT"));
        tileTextures.put(TileType.MOUNTAIN_EDGE_RIGHT, tiles.findRegion("mountainMIDDLERIGHT"));
        tileTextures.put(TileType.MOUNTAIN_EDGE_TOP, tiles.findRegion("tile029"));
        tileTextures.put(TileType.MOUNTAIN_EDGE_BOTTOM, tiles.findRegion("tile089"));

        // Add other tile types as needed
        for (TextureRegion texture : tileTextures.values()) {
            if (texture != null && texture.getTexture() != null) {
                texture.getTexture().setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            }
        }

        // Debug log to verify all tile textures are loaded
        for (Map.Entry<Integer, TextureRegion> entry : tileTextures.entrySet()) {
            if (entry.getValue() == null) {
            } else {
            }
        }
    }

    public static String getTextureNameForBiome(int tileType, BiomeType biomeType) {
        String tileName = TileType.getTileTypeNames().get(tileType);
        // Since tileName already includes biome information, return it directly
        return tileName;
    }

    public static TextureRegion getTileTexture(int tileType) {
        return tileTextures.get(tileType);
    }

//    private static void loadBiomeTileTextures() {
//        // For each biome, load its tile textures
//        for (BiomeType biomeType : BiomeType.values()) {
//            Map<Integer, TextureRegion> tileTextures = new HashMap<>();
//
//            // Load textures for each tile type
//            for (Map.Entry<Integer, String> tileEntry : TileType.getTileTypeNames().entrySet()) {
//                int tileType = tileEntry.getKey();
//
//                // Get the texture name for the biome (now just the tileName)
//                String regionName = getTextureNameForBiome(tileType, biomeType);
//                TextureRegion region = tiles.findRegion(regionName);
//
//                if (region != null) {
//                    tileTextures.put(tileType, region);
//                    GameLogger.info("Loaded texture for biome " + biomeType + ", tile type " + tileType + ": " + regionName);
//                } else {
//                    GameLogger.error("Missing texture for tile type: " + regionName + " in biome: " + biomeType);
//                }
//            }
//
//            biomeTileTextures.put(biomeType, tileTextures);
//        }
//    }

    public static Map<Integer, TextureRegion> getAllTileTextures() {
        return tileTextures;
    }

    // Remove the internal PokemonType enum and use Pokemon.PokemonType instead

    private static void debugAtlas(String name, TextureAtlas atlas) {
        if (atlas == null) {
            GameLogger.error(name + " atlas is null!");
            return;
        }
        GameLogger.info(name + " atlas regions:");
        for (TextureAtlas.AtlasRegion region : atlas.getRegions()) {
            GameLogger.info("  - " + region.name +
                " (x=" + region.getRegionX() +
                ", y=" + region.getRegionY() +
                ", w=" + region.getRegionWidth() +
                ", h=" + region.getRegionHeight() + ")");
        }
    }


    // Enum for all status conditions
    public enum StatusCondition {
        NONE(0),
        SLEEP(1),
        POISON(2),
        BURN(3),
        FREEZE(4),
        PARALYSIS(5),
        TOXIC(6),    // Bad poison
        CONFUSION(7);

        private final int index;

        StatusCondition(int index) {
            this.index = index;
        }

        public int getIndex() {
            return index;
        }
    }
}



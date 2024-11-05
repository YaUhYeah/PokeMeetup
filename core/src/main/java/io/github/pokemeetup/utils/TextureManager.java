package io.github.pokemeetup.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.github.pokemeetup.pokemon.Pokemon.PokemonType;
import io.github.pokemeetup.system.gameplay.overworld.WorldObject;
import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;
import io.github.pokemeetup.utils.TextureManager.StatusCondition;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class TextureManager {
    // Add constants for frame sizes in the sprite sheets
    public static final int TYPE_ICON_WIDTH = 64;
    public static final int TYPE_ICON_HEIGHT = 38; // Adjust based on your spritesheet
    public static final int STATUS_ICON_WIDTH = 44;
    public static final int STATUS_ICON_HEIGHT = 16; // Adjust based on your spritesheet
    // Centralized tileTextures map
    private static final Map<Integer, TextureRegion> tileTextures = new HashMap<>();
    private static final Map<BiomeType, Map<Integer, TextureRegion>> biomeTileTextures = new HashMap<>();
    // Add maps for types and statuses
    private static final Map<PokemonType, TextureRegion> typeIcons = new HashMap<>();
    private static final Map<StatusCondition, TextureRegion> statusIcons = new HashMap<>();
    private static final Map<PokemonType, Color> TYPE_COLORS = new HashMap<>();
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
    private static boolean usingFallbackSystem = false;

    static {
        // Initialize type colors
        TYPE_COLORS.put(PokemonType.NORMAL, new Color(0.658f, 0.658f, 0.658f, 1));    // A8A878
        TYPE_COLORS.put(PokemonType.FIGHTING, new Color(0.752f, 0.470f, 0.470f, 1));  // C03028
        TYPE_COLORS.put(PokemonType.FLYING, new Color(0.658f, 0.564f, 0.940f, 1));    // A890F0
        TYPE_COLORS.put(PokemonType.POISON, new Color(0.627f, 0.439f, 0.627f, 1));    // A040A0
        TYPE_COLORS.put(PokemonType.GROUND, new Color(0.878f, 0.752f, 0.470f, 1));    // E0C068
        TYPE_COLORS.put(PokemonType.ROCK, new Color(0.752f, 0.658f, 0.439f, 1));      // B8A038
        TYPE_COLORS.put(PokemonType.BUG, new Color(0.658f, 0.752f, 0.439f, 1));       // A8B820
        TYPE_COLORS.put(PokemonType.GHOST, new Color(0.439f, 0.439f, 0.627f, 1));     // 705898
        TYPE_COLORS.put(PokemonType.STEEL, new Color(0.752f, 0.752f, 0.815f, 1));     // B8B8D0
        TYPE_COLORS.put(PokemonType.FIRE, new Color(0.940f, 0.501f, 0.376f, 1));      // F08030
        TYPE_COLORS.put(PokemonType.WATER, new Color(0.376f, 0.564f, 0.940f, 1));     // 6890F0
        TYPE_COLORS.put(PokemonType.GRASS, new Color(0.470f, 0.815f, 0.376f, 1));     // 78C850
        TYPE_COLORS.put(PokemonType.ELECTRIC, new Color(0.972f, 0.815f, 0.376f, 1));  // F8D030
        TYPE_COLORS.put(PokemonType.PSYCHIC, new Color(0.940f, 0.376f, 0.564f, 1));   // F85888
        TYPE_COLORS.put(PokemonType.ICE, new Color(0.564f, 0.815f, 0.940f, 1));       // 98D8D8
        TYPE_COLORS.put(PokemonType.DRAGON, new Color(0.439f, 0.376f, 0.940f, 1));    // 7038F8
        TYPE_COLORS.put(PokemonType.DARK, new Color(0.439f, 0.376f, 0.376f, 1));      // 705848
        TYPE_COLORS.put(PokemonType.FAIRY, new Color(0.940f, 0.627f, 0.940f, 1));     // F0B6BC
        TYPE_COLORS.put(PokemonType.UNKNOWN, new Color(0.470f, 0.470f, 0.470f, 1));   // 68A090

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

    private static void createFallbackIcons() {
        // Create colored rectangle icons for types
        for (PokemonType type : PokemonType.values()) {
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

        // Split the type sheet into individual icons
        TextureRegion[][] typeFrames = typesSheet.split(TYPE_ICON_WIDTH, TYPE_ICON_HEIGHT);

        // Map each type to its icon
        for (PokemonType type : PokemonType.values()) {
            if (type.getIndex() < typeFrames.length) { // Corrected condition
                typeIcons.put(type, typeFrames[type.getIndex()][0]); // Corrected access
                GameLogger.info("Loaded type icon: " + type.name());
            } else {
                GameLogger.error("Missing type icon for: " + type.name());
            }
        }

        // Split the status sheet into individual icons
        TextureRegion[][] statusFrames = statusSheet.split(STATUS_ICON_WIDTH, STATUS_ICON_HEIGHT);

        // Map each status to its icon
        for (StatusCondition status : StatusCondition.values()) {
            if (status != StatusCondition.NONE && (status.getIndex() - 1) < statusFrames.length) { // Corrected condition
                statusIcons.put(status, statusFrames[status.getIndex() - 1][0]); // Corrected access
                GameLogger.info("Loaded status icon: " + status.name());
            } else if (status != StatusCondition.NONE) {
                GameLogger.error("Missing status icon for: " + status.name());
            }
        }

        boolean hasAllIcons = true;
        for (PokemonType type : PokemonType.values()) {
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
    public static Color getTypeColor(PokemonType type) {
        return TYPE_COLORS.getOrDefault(type, Color.WHITE);
    }

    public static TextureRegion getTypeIcon(PokemonType type) {
        return typeIcons.get(type);
    }

    public static TextureRegion getOverworldSprite(String name) {
        if (name == null) {
            GameLogger.error("Attempted to get overworld sprite with null name.");
            return null;
        }

        // Normalize the name to lowercase to ensure consistency
        String normalizedName = name.toUpperCase();

        TextureRegion sprite = pokemonoverworld.findRegion(normalizedName+"_overworld");
        if (sprite == null) {
            GameLogger.error("Overworld sprite for Pokémon '" + name + "' not found.");
            return null;
        }
        return sprite;
    }

    /**
     * Retrieves the default overworld sprite for unknown Pokémon.
     *
     * @return The default TextureRegion.
     */
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
        return boy;
    }

    public static TextureAtlas getBattlebacks() {
        return battlebacks;
    }

    public static TextureAtlas getTiles() {
        return tiles;
    }
    public static void debugPokemonAtlas() {
        if (pokemonoverworld == null) {
            GameLogger.error("Pokemon overworld atlas is null!");
            return;
        }

        GameLogger.info("=== Pokemon Overworld Atlas Contents ===");
        for (TextureAtlas.AtlasRegion region : pokemonoverworld.getRegions()) {
            GameLogger.info(String.format("Region: %s (x=%d, y=%d, w=%d, h=%d)",
                region.name,
                region.getRegionX(),
                region.getRegionY(),
                region.getRegionWidth(),
                region.getRegionHeight()));
        }
        GameLogger.info("=====================================");
    }

    public static void initialize(TextureAtlas battlebacks, TextureAtlas ui,
                                  TextureAtlas pokemonback, TextureAtlas pokemonfront, TextureAtlas pokemonicon,
                                  TextureAtlas pokemonoverworld, TextureAtlas items, TextureAtlas boy,
                                  TextureAtlas tiles) {

        // Assign atlases
        TextureManager.battlebacks = battlebacks;
        TextureManager.ui = ui;
        TextureManager.pokemonback = pokemonback;
        TextureManager.pokemonfront = pokemonfront;
        TextureManager.pokemonicon = pokemonicon;
        TextureManager.pokemonoverworld = pokemonoverworld;
        TextureManager.items = items;
        TextureManager.boy = boy;
        TextureManager.tiles = tiles;

        // Initialize central tileTextures map
        loadTypeAndStatusIcons();
        loadCentralTileTextures();

        // Debug atlas loading
        GameLogger.info("=== Initializing Texture Manager ===");
        debugAtlas("tiles", tiles);
        debugAtlas("ui", ui);
        debugAtlas("boy", boy);
        debugPokemonAtlas(); // Add this line to debug Pokemon textures
        GameLogger.info("=== Texture Manager Initialization Complete ===");
    }

    private static void loadCentralTileTextures() {
        // Ensure tiles atlas is loaded
        if (tiles == null) {
            GameLogger.error("Tiles atlas is not initialized!");
            return;
        }

        // Map tile type IDs to their corresponding texture regions
        tileTextures.put(TileType.WATER, tiles.findRegion("water"));
        tileTextures.put(TileType.GRASS, tiles.findRegion("grass"));
        tileTextures.put(TileType.SAND, tiles.findRegion("sand"));
        tileTextures.put(TileType.ROCK, tiles.findRegion("rock"));
        tileTextures.put(TileType.SNOW, tiles.findRegion("snow"));
        tileTextures.put(TileType.HAUNTED_GRASS, tiles.findRegion("haunted_grass"));
        tileTextures.put(TileType.SNOW_TALL_GRASS, tiles.findRegion("snow_tall_grass"));
        tileTextures.put(TileType.HAUNTED_TALL_GRASS, tiles.findRegion("haunted_tall_grass"));
        tileTextures.put(TileType.HAUNTED_SHROOM, tiles.findRegion("haunted_shroom"));
        tileTextures.put(TileType.HAUNTED_SHROOMS, tiles.findRegion("haunted_shrooms"));
        tileTextures.put(TileType.TALL_GRASS, tiles.findRegion("tall_grass"));
        tileTextures.put(TileType.FOREST_GRASS, tiles.findRegion("forest_grass"));
        tileTextures.put(TileType.FOREST_TALL_GRASS, tiles.findRegion("forest_tall_grass"));
        tileTextures.put(TileType.RAIN_FOREST_GRASS, tiles.findRegion("rain_forest_grass"));
        tileTextures.put(TileType.RAIN_FOREST_TALL_GRASS, tiles.findRegion("rain_forest_tall_grass"));
        tileTextures.put(TileType.DESERT_SAND, tiles.findRegion("desert_sand"));
        tileTextures.put(TileType.DESERT_ROCKS, tiles.findRegion("desert_rock"));
        tileTextures.put(TileType.DESERT_GRASS, tiles.findRegion("desert_grass"));
        // Add other tile types as needed
        for (TextureRegion texture : tileTextures.values()) {
            if (texture != null && texture.getTexture() != null) {
                texture.getTexture().setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            }
        }

        // Debug log to verify all tile textures are loaded
        for (Map.Entry<Integer, TextureRegion> entry : tileTextures.entrySet()) {
            if (entry.getValue() == null) {
                GameLogger.error("Missing texture for tile type: " + entry.getKey());
            } else {
                GameLogger.info("Loaded texture for tile type: " + entry.getKey() + " (" + entry.getValue().getTexture() + ")");
            }
        }
    }

    public static TextureRegion getTileTextureForBiome(int tileType, BiomeType biomeType) {
        Map<Integer, TextureRegion> tileTextures = biomeTileTextures.get(biomeType);
        if (tileTextures != null) {
            TextureRegion region = tileTextures.get(tileType);
            if (region != null) {
                return region;
            }
        }
        // Return a default texture if not found
        return tiles.findRegion("default_tile");
    }

    public static String getTextureNameForBiome(int tileType, BiomeType biomeType) {
        String tileName = TileType.getTileTypeNames().get(tileType);
        // Since tileName already includes biome information, return it directly
        return tileName;
    }

    public static TextureRegion getTileTexture(int tileType) {
        return tileTextures.get(tileType);
    }

    public static Map<Integer, TextureRegion> getAllTileTextures() {
        return tileTextures;
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

    // Enum for all Pokemon types including ???
    public enum PokemonType {
        NORMAL(0),
        FIGHTING(1),
        FLYING(2),
        POISON(3),
        GROUND(4),
        ROCK(5),
        BUG(6),
        GHOST(7),
        STEEL(8),
        FIRE(9),
        WATER(10),
        GRASS(11),
        ELECTRIC(12),
        PSYCHIC(13),
        ICE(14),
        DRAGON(15),
        DARK(16),
        FAIRY(17),
        UNKNOWN(18); // ??? type

        private final int index;

        PokemonType(int index) {
            this.index = index;
        }

        public int getIndex() {
            return index;
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

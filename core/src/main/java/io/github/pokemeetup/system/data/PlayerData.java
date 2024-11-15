package io.github.pokemeetup.system.data;

import io.github.pokemeetup.pokemon.Pokemon;
import io.github.pokemeetup.pokemon.PokemonParty;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.gameplay.inventory.Inventory;
import io.github.pokemeetup.system.gameplay.inventory.Item;
import io.github.pokemeetup.system.gameplay.inventory.ItemManager;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.utils.GameLogger;

import java.util.*;

import static io.github.pokemeetup.pokemon.PokemonParty.MAX_PARTY_SIZE;

public class PlayerData {
    private String username;
    private float x;
    private float y;
    private String direction;
    private boolean isMoving;
    private boolean wantsToRun;
    // Inventory and Pokemon data
    private List<ItemData> inventoryItems;
    private List<PokemonData> partyPokemon;
    private List<PokemonData> storedPokemon;
    public PlayerData() {
        this.direction = "down";
        this.inventoryItems = new ArrayList<>();
        this.partyPokemon = new ArrayList<>();
        this.storedPokemon = new ArrayList<>();
    }

    public PlayerData(String username) {
        this();
        this.username = username;
    }

    public boolean verifyIntegrity() {
        if (this.username == null || this.username.isEmpty()) {
            GameLogger.error("PlayerData integrity check failed: username is null or empty");
            return false;
        }
        if (this.partyPokemon != null) {
            for (PokemonData pokemon : this.partyPokemon) {
                if (pokemon != null && !pokemon.verifyIntegrity()) {
                    GameLogger.error("Invalid PokemonData in party: " + pokemon);
                    return false;
                }
            }
        }
        // You can add more checks for inventoryItems if necessary
        return true;
    }


    public void validateInventory() {
        if (inventoryItems == null) {
            inventoryItems = new ArrayList<>(Inventory.INVENTORY_SIZE);
            for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
                inventoryItems.add(null);
            }
        }

        // Validate each item
        for (int i = 0; i < inventoryItems.size(); i++) {
            ItemData item = inventoryItems.get(i);
            if (item != null) {
                if (!item.isValid() || ItemManager.getItem(item.getItemId()) == null) {
                    GameLogger.error("Removing invalid item at slot " + i + ": " + item.getItemId());
                    inventoryItems.set(i, null);
                } else if (item.getUuid() == null) {
                    item.setUuid(UUID.randomUUID());
                }
            }
        }
    }

    public void updateFromPlayer(Player player) {
        if (player == null) {
            GameLogger.error("Cannot update from null player");
            return;
        }

        try {
            // Update basic info
            this.x = player.getTileX();
            this.y = player.getTileY();
            this.direction = player.getDirection();
            this.isMoving = player.isMoving();
            this.wantsToRun = player.isRunning();

            // Initialize fixed-size lists with nulls
            this.inventoryItems = new ArrayList<>(Collections.nCopies(Inventory.INVENTORY_SIZE, null));
            this.partyPokemon = new ArrayList<>(Collections.nCopies(PokemonParty.MAX_PARTY_SIZE, null));

            // Update inventory items maintaining slot positions
            if (player.getInventory() != null) {
                List<ItemData> items = player.getInventory().getAllItems();
                for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
                    if (i < items.size() && items.get(i) != null) {
                        ItemData itemData = items.get(i);
                        if (validateItemData(itemData)) {
                            this.inventoryItems.set(i, itemData.copy());
                        }
                    }
                }
            }

            // Update Pokemon party maintaining slot positions
            if (player.getPokemonParty() != null) {
                List<Pokemon> currentParty = player.getPokemonParty().getParty();
                for (int i = 0; i < PokemonParty.MAX_PARTY_SIZE; i++) {
                    if (i < currentParty.size() && currentParty.get(i) != null) {
                        Pokemon pokemon = currentParty.get(i);
                        try {
                            PokemonData pokemonData = PokemonData.fromPokemon(pokemon);
                            if (pokemonData.verifyIntegrity()) {
                                this.partyPokemon.set(i, pokemonData);
                            }
                        } catch (Exception e) {
                            GameLogger.error("Failed to convert Pokemon at slot " + i + ": " + e.getMessage());
                        }
                    }
                }
            }

            // Log the update results
            long validItems = inventoryItems.stream().filter(Objects::nonNull).count();
            long validPokemon = partyPokemon.stream().filter(Objects::nonNull).count();

            GameLogger.info(String.format("Updated PlayerData from player %s - Items: %d/%d, Pokemon: %d/%d",
                player.getUsername(),
                validItems, Inventory.INVENTORY_SIZE,
                validPokemon, PokemonParty.MAX_PARTY_SIZE));

        } catch (Exception e) {
            GameLogger.error("Error updating PlayerData: " + e.getMessage());
            e.printStackTrace();
        }
    }
    private float tileToPixelX(int tileX) {
        return tileX * World.TILE_SIZE;
    }

    private float tileToPixelY(int tileY) {
        return tileY * World.TILE_SIZE;
    }


    public int getValidItemCount() {
        if (inventoryItems == null) return 0;
        return (int) inventoryItems.stream()
            .filter(item -> item != null && item.isValid())
            .count();
    }

    public int getValidPokemonCount() {
        if (partyPokemon == null) return 0;
        return (int) partyPokemon.stream()
            .filter(pokemon -> pokemon != null && pokemon.verifyIntegrity())
            .count();
    }

    public void applyToPlayer(Player player) {
        if (player == null) return;

        GameLogger.info("Applying PlayerData to player: " + this.username);

        // Count VALID items/pokemon, not just slots
        int validItems = getValidItemCount();
        int validPokemon = getValidPokemonCount();
        GameLogger.info("Initial PlayerData state - Valid Items: " + validItems +
            " Valid Pokemon: " + validPokemon);

        try {
            // Validate without clearing valid data
            if (validateAndRepairState()) {
                GameLogger.info("Data was repaired during validation");
            }

            // Apply basic attributes
            player.setX(x * World.TILE_SIZE);
            player.setY(y * World.TILE_SIZE);
            player.setDirection(direction);
            player.setMoving(isMoving);
            player.setRunning(wantsToRun);

            // Only clear if we're actually going to add items
            if (validItems > 0) {
                player.getInventory().clear();
                for (ItemData item : inventoryItems) {
                    if (item != null && item.isValid()) {
                        player.getInventory().addItem(item.copy());
                        GameLogger.info("Restored item: " + item.getItemId() + " x" + item.getCount());
                    }
                }
            }

            // Only clear if we're actually going to add Pokemon
            if (validPokemon > 0) {
                player.getPokemonParty().clearParty();
                for (PokemonData pokemonData : partyPokemon) {
                    if (pokemonData != null && pokemonData.verifyIntegrity()) {
                        Pokemon pokemon = pokemonData.toPokemon();
                        player.getPokemonParty().addPokemon(pokemon);
                        GameLogger.info("Restored Pokemon: " + pokemon.getName());
                    }
                }
            }

            // Log final valid counts
            GameLogger.info("Final player state - Items: " + player.getInventory().getAllItems().size() +
                " Pokemon: " + player.getPokemonParty().getSize());

        } catch (Exception e) {
            GameLogger.error("Error applying PlayerData: " + e.getMessage());
            e.printStackTrace();
        }
    }
    public boolean validateAndRepairState() {
        boolean wasRepaired = false;
        GameLogger.info("Starting PlayerData validation for user: " + username);

        try {
            // Don't initialize new arrays if they exist and have valid items
            if (inventoryItems == null || inventoryItems.isEmpty()) {
                inventoryItems = new ArrayList<>(Collections.nCopies(Inventory.INVENTORY_SIZE, null));
                wasRepaired = true;
            }

            if (partyPokemon == null || partyPokemon.isEmpty()) {
                partyPokemon = new ArrayList<>(Collections.nCopies(PokemonParty.MAX_PARTY_SIZE, null));
                wasRepaired = true;
            }

            // Only remove invalid items/pokemon, keep valid ones
            for (int i = 0; i < inventoryItems.size(); i++) {
                ItemData item = inventoryItems.get(i);
                if (item != null && !item.isValid()) {
                    inventoryItems.set(i, null);
                    wasRepaired = true;
                }
            }

            for (int i = 0; i < partyPokemon.size(); i++) {
                PokemonData pokemon = partyPokemon.get(i);
                if (pokemon != null && !pokemon.verifyIntegrity()) {
                    partyPokemon.set(i, null);
                    wasRepaired = true;
                }
            }

            // Log valid counts
            GameLogger.info("Validation complete - Items: " + getValidItemCount() +
                ", Pokemon: " + getValidPokemonCount());

            return wasRepaired;

        } catch (Exception e) {
            GameLogger.error("Error during PlayerData validation: " + e.getMessage());
            return true;
        }
    }
    private boolean isValidItem(ItemData item) {
        if (item == null) return false;
        try {
            return item.getItemId() != null &&
                !item.getItemId().isEmpty() &&
                item.getCount() > 0 &&
                item.getCount() <= Item.MAX_STACK_SIZE &&
                item.getUuid() != null &&
                ItemManager.getItem(item.getItemId()) != null;
        } catch (Exception e) {
            GameLogger.error("Error validating item: " + e.getMessage());
            return false;
        }
    }


    private boolean validateItemData(ItemData item) {
        return item != null &&
            item.getItemId() != null &&
            !item.getItemId().isEmpty() &&
            item.getCount() > 0 &&
            item.getCount() <= Item.MAX_STACK_SIZE &&
            ItemManager.getItem(item.getItemId()) != null &&
            item.getUuid() != null;
    }

    private boolean validatePokemonData(PokemonData pokemon) {
        return pokemon != null &&
            pokemon.getName() != null &&
            !pokemon.getName().isEmpty() &&
            pokemon.getLevel() > 0 &&
            pokemon.getLevel() <= 100 &&
            pokemon.verifyIntegrity();
    }
    public PlayerData copy() {
        PlayerData copy = new PlayerData(this.username);

        copy.setX(this.x);
        copy.setY(this.y);
        copy.setDirection(this.direction);
        copy.setMoving(this.isMoving);
        copy.setWantsToRun(this.wantsToRun);

        // Deep copy inventory items
        if (this.inventoryItems != null) {
            List<ItemData> inventoryCopy = new ArrayList<>();
            for (ItemData item : this.inventoryItems) {
                if (item != null) {
                    inventoryCopy.add(item.copy());
                } else {
                    inventoryCopy.add(null);
                }
            }
            copy.setInventoryItems(inventoryCopy);
        }

        // Deep copy Pokemon party
        if (this.partyPokemon != null) {
            List<PokemonData> partyCopy = new ArrayList<>();
            for (PokemonData pokemon : this.partyPokemon) {
                if (pokemon != null) {
                    partyCopy.add(pokemon.copy());
                } else {
                    partyCopy.add(null);
                }
            }
            copy.setPartyPokemon(partyCopy);
        }

        return copy;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public float getX() {
        return x;
    }

    public void setX(float x) {
        this.x = x;
    }

    public float getY() {
        return y;
    }

    public void setY(float y) {
        this.y = y;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public boolean isMoving() {
        return isMoving;
    }

    public void setMoving(boolean moving) {
        isMoving = moving;
    }

    public boolean isWantsToRun() {
        return wantsToRun;
    }

    public void setWantsToRun(boolean wantsToRun) {
        this.wantsToRun = wantsToRun;
    }

    public List<ItemData> getInventoryItems() {
        return inventoryItems;
    }

    public void setInventoryItems(List<ItemData> items) {
        this.inventoryItems = new ArrayList<>(items);
    }

    public List<PokemonData> getPartyPokemon() {
        return partyPokemon;
    }

    public void setPartyPokemon(List<PokemonData> partyPokemon) {
        this.partyPokemon = partyPokemon;
    }

    public List<PokemonData> getStoredPokemon() {
        return storedPokemon;
    }

    public void setStoredPokemon(List<PokemonData> storedPokemon) {
        this.storedPokemon = storedPokemon;
    }

    @Override
    public String toString() {
        return "io.github.pokemeetup.system.data.PlayerData{" +
            "username='" + username + '\'' +
            ", position=(" + x + "," + y + ")" +
            ", direction='" + direction + '\'' +
            ", inventory=" + (inventoryItems != null ? inventoryItems.size() : "null") + " items" +
            ", party=" + (partyPokemon != null ? partyPokemon.size() : "null") + " pokemon" +
            '}';
    }
}

package io.github.pokemeetup.system.data;

import io.github.pokemeetup.pokemon.Pokemon;
import io.github.pokemeetup.pokemon.PokemonParty;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.gameplay.inventory.Inventory;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.utils.GameLogger;

import java.util.ArrayList;
import java.util.List;

public class PlayerData {
    // Core player data
    private String username;
    private float x;
    private float y;
    private String direction;
    private boolean isMoving;
    private boolean wantsToRun;

    // Inventory and Pokemon data
    private List<ItemData> inventoryItems;
    private List<PokemonData> partyPokemon;
    private List<PokemonData> storedPokemon; // For Pokemon storage system

    // Simple constructor
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

    // Method to update state from a Player instance
    @SuppressWarnings("DefaultLocale")
    public void updateFromPlayer(Player player) {
        if (player == null) {
            GameLogger.error("Cannot update from null player");
            return;
        }

        try {
            // Update basic data
            this.x = player.getTileX();
            this.y = player.getTileY();
            this.direction = player.getDirection();
            this.isMoving = player.isMoving();
            this.wantsToRun = player.isRunning();

            // Update inventory - deep copy
            updateInventory(player.getInventory());

            // Update Pokemon party and storage - deep copy
            updatePokemon(player.getPokemonParty());

            GameLogger.info(String.format("Updated io.github.pokemeetup.system.data.PlayerData for %s at (%.2f, %.2f)",
                username, x, y));

        } catch (Exception e) {
            GameLogger.error("Error updating io.github.pokemeetup.system.data.PlayerData: " + e.getMessage());
        }
    }    private float tileToPixelX(int tileX) {
        return tileX * World.TILE_SIZE;
    }

    private float tileToPixelY(int tileY) {
        return tileY * World.TILE_SIZE;
    }

    // Apply saved data to a Player instance
    @SuppressWarnings("DefaultLocale")
    public void applyToPlayer(Player player) {
        if (player == null) return;

        try {
            // Set basic data
            player.setX(tileToPixelX((int) x));
            player.setY(tileToPixelY((int) y));
            player.setDirection(direction);
            player.setMoving(isMoving);
            player.setRunning(wantsToRun);

            // Apply inventory
            if (inventoryItems != null) {
                player.getInventory().getAllItems().clear();
                for (ItemData item : inventoryItems) {
                    if (item != null) {
                        player.getInventory().addItem(item.copy());
                    }
                }
            }

            // Apply Pokemon party
            if (partyPokemon != null) {
                player.getPokemonParty().clearParty();
                for (PokemonData pokemonData : partyPokemon) {
                    if (pokemonData != null) {
                        Pokemon pokemon = pokemonData.toPokemon();
                        player.getPokemonParty().addPokemon(pokemon);
                    }
                }
            }

            GameLogger.info(String.format("Applied io.github.pokemeetup.system.data.PlayerData to %s at (%.2f, %.2f)",
                username, x, y));

        } catch (Exception e) {
            GameLogger.error("Error applying io.github.pokemeetup.system.data.PlayerData: " + e.getMessage());
        }
    }

    private void updateInventory(Inventory inventory) {
        if (inventory == null) return;

        inventoryItems = new ArrayList<>();
        for (ItemData item : inventory.getAllItems()) {
            if (item != null) {
                inventoryItems.add(item.copy());
            } else {
                inventoryItems.add(null); // Preserve slot positions
            }
        }
    }

    public void setPartyPokemon(List<PokemonData> partyPokemon) {
        this.partyPokemon = partyPokemon;
    }

    public void setStoredPokemon(List<PokemonData> storedPokemon) {
        this.storedPokemon = storedPokemon;
    }

    public void updatePokemon(PokemonParty party) {
        if (party == null) return;

        partyPokemon = new ArrayList<>();
        for (Pokemon pokemon : party.getParty()) {
            if (pokemon != null) {
                partyPokemon.add(PokemonData.fromPokemon(pokemon));
            }
        }
    }

    // Create a deep copy of this io.github.pokemeetup.system.data.PlayerData
    public PlayerData copy() {
        PlayerData copy = new PlayerData(this.username);
        copy.x = this.x;
        copy.y = this.y;
        copy.direction = this.direction;
        copy.isMoving = this.isMoving;
        copy.wantsToRun = this.wantsToRun;

        // Deep copy inventories
        if (this.inventoryItems != null) {
            copy.inventoryItems = new ArrayList<>();
            for (ItemData item : this.inventoryItems) {
                copy.inventoryItems.add(item != null ? item.copy() : null);
            }
        }

        // Deep copy Pokemon
        if (this.partyPokemon != null) {
            copy.partyPokemon = new ArrayList<>();
            for (PokemonData pokemon : this.partyPokemon) {
                copy.partyPokemon.add(pokemon != null ? pokemon.copy() : null);
            }
        }

        if (this.storedPokemon != null) {
            copy.storedPokemon = new ArrayList<>();
            for (PokemonData pokemon : this.storedPokemon) {
                copy.storedPokemon.add(pokemon != null ? pokemon.copy() : null);
            }
        }

        return copy;
    }

    // Standard getters and setters
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public float getX() { return x; }
    public void setX(float x) { this.x = x; }

    public float getY() { return y; }
    public void setY(float y) { this.y = y; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public boolean isMoving() { return isMoving; }
    public void setMoving(boolean moving) { isMoving = moving; }

    public boolean isWantsToRun() { return wantsToRun; }
    public void setWantsToRun(boolean wantsToRun) { this.wantsToRun = wantsToRun; }

    public List<ItemData> getInventoryItems() { return inventoryItems; }
    public void setInventoryItems(List<ItemData> items) {
        this.inventoryItems = new ArrayList<>(items);
    }

    public List<PokemonData> getPartyPokemon() { return partyPokemon; }


    public List<PokemonData> getStoredPokemon() { return storedPokemon; }


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

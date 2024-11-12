package io.github.pokemeetup.system.data;

import io.github.pokemeetup.pokemon.Pokemon;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.gameplay.inventory.Inventory;
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
        this.x = player.getTileX();
        this.y = player.getTileY();
        this.direction = player.getDirection();
        this.isMoving = player.isMoving();
        this.wantsToRun = player.isRunning();

        // Deep copy inventory items
        if (player.getInventory() != null) {
            List<ItemData> currentItems = player.getInventory().getAllItems();
            this.inventoryItems = new ArrayList<>(Inventory.INVENTORY_SIZE);

            for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
                ItemData item = i < currentItems.size() ? currentItems.get(i) : null;
                if (item != null) {
                    this.inventoryItems.add(item.copy());
                } else {
                    this.inventoryItems.add(null);
                }
            }

            GameLogger.info("Updated PlayerData inventory with " +
                inventoryItems.stream().filter(Objects::nonNull).count() + " non-null items");
        }
        if (player.getPokemonParty() != null) {
            this.partyPokemon = new ArrayList<>();
            for (Pokemon pokemon : player.getPokemonParty().getParty()) {
                this.partyPokemon.add(pokemon != null ? PokemonData.fromPokemon(pokemon) : null);
            }
        }
    }

    private float tileToPixelX(int tileX) {
        return tileX * World.TILE_SIZE;
    }

    private float tileToPixelY(int tileY) {
        return tileY * World.TILE_SIZE;
    }

    public void applyToPlayer(Player player) {
        if (player == null) return;

        try {
            player.setX(tileToPixelX((int) x));
            player.setY(tileToPixelY((int) y));
            player.setDirection(direction);
            player.setMoving(isMoving);
            player.setRunning(wantsToRun);

            if (partyPokemon != null) {
                player.getPokemonParty().clearParty();
                for (PokemonData pokemonData : partyPokemon) {
                    if (pokemonData != null && pokemonData.verifyIntegrity()) {
                        Pokemon pokemon = pokemonData.toPokemon();
                        player.getPokemonParty().addPokemon(pokemon);
                    } else {
                        GameLogger.error("Skipping invalid PokemonData in party");
                        player.getPokemonParty().addPokemon(null); // Preserve party slot
                    }
                }
            }

            GameLogger.info(String.format("Applied PlayerData to %s at (%.2f, %.2f)",
                username, x, y));

        } catch (Exception e) {
            GameLogger.error("Error applying PlayerData: " + e.getMessage());
        }
    }

    public boolean validateAndRepairState() {
        boolean wasRepaired = false;

        // Validate inventory
        if (inventoryItems == null) {
            inventoryItems = new ArrayList<>(Inventory.INVENTORY_SIZE);
            for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
                inventoryItems.add(null);
            }
            wasRepaired = true;
        }
        if (partyPokemon == null) {
            partyPokemon = new ArrayList<>(MAX_PARTY_SIZE);
            for (int i = 0; i < MAX_PARTY_SIZE; i++) {
                partyPokemon.add(null);
            }
            wasRepaired = true;
        } else if (partyPokemon.size() != MAX_PARTY_SIZE) {
            List<PokemonData> adjustedParty = new ArrayList<>(MAX_PARTY_SIZE);
            for (int i = 0; i < MAX_PARTY_SIZE; i++) {
                adjustedParty.add(i < partyPokemon.size() ? partyPokemon.get(i) : null);
            }
            partyPokemon = adjustedParty;
            wasRepaired = true;
        }
        if (direction == null) {
            direction = "down";
            wasRepaired = true;
        }

        if (username == null) {
            username = "Player";
            wasRepaired = true;
        }

        return wasRepaired;
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

package io.github.pokemeetup.pokemon.attacks;

import io.github.pokemeetup.pokemon.Pokemon;
import io.github.pokemeetup.utils.TextureManager;

public class Move {
    private String name;
    private Pokemon.PokemonType type;
    private int power;
    private int accuracy;
    private int pp;
    private int maxPp;
    private boolean isSpecial;
    private String description;

    public Move(String name, Pokemon.PokemonType type, int power, int accuracy, int pp, boolean isSpecial, String description) {
        this.name = name;
        this.type = type;
        this.power = power;
        this.accuracy = accuracy;
        this.pp = pp;
        this.maxPp = pp;
        this.isSpecial = isSpecial;
        this.description = description;
    }

    public boolean use() {
        if (pp > 0) {
            pp--;
            return true;
        }
        return false;
    }

    public void restore() {
        pp = maxPp;
    }

    // Getters
    public String getName() { return name; }
    public Pokemon.PokemonType getType() { return type; }
    public int getPower() { return power; }
    public int getAccuracy() { return accuracy; }
    public int getPp() { return pp; }
    public int getMaxPp() { return maxPp; }
    public boolean isSpecial() { return isSpecial; }
    public String getDescription() { return description; }
}

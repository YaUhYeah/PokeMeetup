package io.github.pokemeetup.chat;

import io.github.pokemeetup.multiplayer.client.GameClient;

// Base Command Interface
public interface Command {
    String getName();
    String[] getAliases();
    String getDescription();
    String getUsage();
    boolean isMultiplayerOnly();
    void execute(String args, GameClient gameClient, ChatSystem chatSystem);
}
